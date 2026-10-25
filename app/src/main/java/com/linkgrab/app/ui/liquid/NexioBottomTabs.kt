package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Nexio（HyperOS4 风格）液态玻璃底部导航。
 *
 * 交互与动画学习自开源项目 Nexio 课程表（gitee.com/com_haooz_account/hyper_schedule，
 * AGPL-3.0）的 LiquidBottomTabs，在本项目依赖（backdrop 2.0.0）上重新实现。
 *
 * 三层结构 + 统一手势层：
 * 1. **可见层**：玻璃胶囊容器（vibrancy + blur 8dp + lens），按压时整板沿
 *    拖动方向轻微偏移（panelOffset 视差）。
 * 2. **透明捕获层**：标签内容以 accent 色渲染进独立 backdrop 层（alpha=0），
 *    按压时放大 1.2x，只透过玻璃滑块的折射可见 —— 这就是「选中项变实色」的来源。
 * 3. **玻璃滑块**：胶囊选中指示器，采样 背景+捕获层 双 backdrop，
 *    带色散 lens（chromaticAberration）、按压高光/投影/内阴影，
 *    随拖动速度做 squash & stretch 形变。
 *
 * 手势（统一层处理，标签本身不可点）：
 * - 按下标签 → 胶囊飞过去并保持按压高光
 * - 按住拖动 → 1:1 跟手，面板视差偏移
 * - 松手 → 吸附最近标签并提交选中（立即回调，不等 snapshotFlow 下一拍）
 *
 * 边缘光效：原版用自研 EdgeLight 着色器，这里以 1dp 浅色描边等效（效果接近
 * EdgeLight.Subtle），避免引入整套 AGSL 着色器栈。
 */
internal val LocalNexioBottomTabScale = staticCompositionLocalOf { { 1f } }

/** 底部导航 tab：纯视觉；手势由 [NexioBottomTabs] 统一层处理。 */
@Composable
fun RowScope.NexioBottomTab(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val scale = LocalNexioBottomTabScale.current
    val isLightTheme = !isInDarkTheme()
    val contentColor =
        if (isLightTheme) Color(0xFF1A1A1A).copy(alpha = 0.75f)
        else Color.White.copy(alpha = 0.75f)
    // 排版：图标走固定 24dp 方盒居中，文字固定行高并去掉字体自带的上下留白，
    // 两者再整体垂直居中 —— 之前直接 Icon+Text 叠放时，字体的 ascent/descent
    // 留白会把文字往下顶，图标相对文字看起来就不在一条中轴上（"有点斜"）。
    Column(
        modifier
            .clip(Capsule())
            .semantics { role = Role.Tab }
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            fontSize = 11.sp,
            // 固定行高 + 去掉 font padding：文字块高度确定，中点才和图标对齐
            lineHeight = 12.sp,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun NexioBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    containerHeight: Dp = 56.dp,
    highlightHeight: Dp = 48.dp,
    selectorHeight: Dp = 48.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val isLightTheme = !isInDarkTheme()
    val accentColor = if (isLightTheme) Color.Black else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.6f)
        else Color(0xFF242424).copy(0.6f)
    // EdgeLight.Subtle 等效：细亮描边
    val edgeStrokeColor =
        if (isLightTheme) Color.White.copy(alpha = 0.5f)
        else Color.White.copy(alpha = 0.16f)

    val tabsBackdrop = rememberLayerBackdrop()

    // 必须给容器显式高度：手势层用 fillMaxSize()，不显式限高会被它撑成
    // 父级全高，CenterStart 对齐下可见胶囊就跑到屏幕正中（v2.5.13 的 bug）。
    BoxWithConstraints(
        modifier.height(containerHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val viewConfiguration = LocalViewConfiguration.current
        val padPx = with(density) { 4.dp.toPx() }
        val tabWidth = (constraints.maxWidth.toFloat() - padPx * 2f) / tabsCount
        val maxIndex = (tabsCount - 1).toFloat()

        // 拖动视差：面板沿拖动方向轻微偏移，松手弹性回正。
        // derivedStateOf 只在 draw/layout lambda 里读，不触发重组。
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val ltrSign = if (isLtr) 1f else -1f
        val animationScope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        // 不能用 selectedTabIndex lambda 做 remember key：每次重组都是新实例，
        // currentIndex 会被冲掉导致点 tab 无效
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        // 由本控件发起、等待宿主确认的目标页；-1 表示没有在途的选择。
        //
        // 为什么需要它：宿主是 HorizontalPager，翻页途中 currentPage 会**依次经过
        // 中间的页**——从首页滑到「我的」会先经过「工具箱」。这些中间值一旦回灌
        // 进来，底栏就会把 currentIndex 改成中间页，再反过来通知宿主切到中间页，
        // 于是页面被拽回中间（v2.5.15 修的 bug：只在滑到最左/最右时出现，因为
        // 相邻两页之间不存在会被经过的中间页）。
        // 发起选择后只认目标本身，抵达后才解除锁定。
        var pendingTarget by remember { mutableIntStateOf(-1) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..maxIndex,
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {},
                onDrag = { _, _ -> },
            )
        }
        val latestSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        // 保险丝：万一宿主因为被打断等原因始终没抵达目标，超时后自动解锁，
        // 避免 pendingTarget 永久屏蔽宿主的外部选中。
        LaunchedEffect(pendingTarget) {
            if (pendingTarget < 0) return@LaunchedEffect
            delay(1200)
            pendingTarget = -1
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestSelectedTabIndex() }
                .collectLatest { index ->
                    // 在途期间忽略宿主回灌的中间页，只等目标页抵达。
                    if (pendingTarget >= 0) {
                        if (index == pendingTarget) pendingTarget = -1
                        return@collectLatest
                    }
                    if (index != currentIndex) {
                        currentIndex = index
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    // 这里不要再调 onTabSelected：本控件发起的选择已在手势里直接
                    // 通知过宿主，宿主自己发起的变更回传过去只是无意义的回声，
                    // 而且会和手势里那次并发翻页、把页面拽回中途的某一页。
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        // ---- 可见层：不缩放（只缩放下面的捕获层，滑块外的放大不可见）----
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    highlight = null,
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .border(1.dp, edgeStrokeColor, Capsule())
                .then(interactiveHighlight.modifier)
                .height(containerHeight)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // ---- 捕获层（透明）：按压时放大，只通过玻璃滑块的 backdrop 透出 ----
        CompositionLocalProvider(
            LocalNexioBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(8.dp.toPx())
                            lens(
                                24.dp.toPx() * progress,
                                24.dp.toPx() * progress,
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .then(interactiveHighlight.modifier)
                    .height(highlightHeight)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        // ---- 玻璃滑块（纯视觉 + 手势层在其上统一处理）----
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8.dp * progress,
                            alpha = progress,
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.06f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .then(interactiveHighlight.gestureModifier)
                .height(selectorHeight)
                .fillMaxWidth(1f / tabsCount),
        )

        // ---- 统一手势层：按下 tab / 拖胶囊 / 松手选中 都在这里 ----
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(tabsCount, tabWidth, isLtr, padPx) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downX = down.position.x
                        // 新手势接管一切：解锁上一轮可能没等到确认的 pendingTarget，
                        // 避免它永久屏蔽宿主的外部选中。
                        pendingTarget = -1

                        val capsuleCenterX = if (isLtr) {
                            padPx + (dampedDragAnimation.value + 0.5f) * tabWidth
                        } else {
                            size.width - padPx - (dampedDragAnimation.value + 0.5f) * tabWidth
                        }
                        val capsuleHalf = tabWidth * 0.55f
                        var dragging = abs(downX - capsuleCenterX) <= capsuleHalf
                        var pressedTab = -1
                        // 拖动目标自己记账，不读 dampedDragAnimation.targetValue：
                        // 后者要等 animateTo 的协程真正启动后才会更新，快速拖动时
                        // 读到的是上一帧的旧值，位移被吃掉一截，松手就吸附到了中间页。
                        var dragTarget = dampedDragAnimation.value

                        if (dragging) {
                            dampedDragAnimation.press()
                        } else {
                            pressedTab = if (isLtr) {
                                floor((downX - padPx) / tabWidth).toInt()
                            } else {
                                floor((size.width - padPx - downX) / tabWidth).toInt()
                            }.fastCoerceIn(0, tabsCount - 1)
                            // 按下 tab：胶囊飞过去，保持按压
                            dampedDragAnimation.animateToValueKeepingPress(pressedTab.toFloat())
                        }

                        var lastX = downX
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                val target = if (dragging) {
                                    dragTarget.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                                } else {
                                    pressedTab.fastCoerceIn(0, tabsCount - 1)
                                }
                                // 锁定目标：宿主翻页途中经过的中间页一律不认，
                                // 否则页面会被拽回中途（见 pendingTarget 注释）。
                                // 只有宿主确实会变时才上锁 —— 目标就是宿主当前页的话
                                // 它不会发出新值，锁就永远解不开了。
                                if (target != latestSelectedTabIndex()) pendingTarget = target
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                currentIndex = target
                                dampedDragAnimation.animateToValue(target.toFloat())
                                dampedDragAnimation.release()
                                // 立即通知宿主切页，不等 snapshotFlow 下一拍，减少底栏与页面错帧
                                onTabSelected(target)
                                animationScope.launch {
                                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                                }
                                break
                            }

                            val dx = change.position.x - lastX
                            val totalDx = change.position.x - downX

                            // 点 tab 后继续滑 → 接管为拖动
                            if (!dragging && abs(totalDx) > touchSlop) {
                                dragging = true
                                // 从「按下时飞向的那个 tab」接管，保证和手指实际位置对齐；
                                // 直接按在胶囊上时则从胶囊当前视觉位置继续。
                                dragTarget =
                                    if (pressedTab >= 0) pressedTab.toFloat()
                                    else dampedDragAnimation.value
                                lastX = change.position.x
                            }

                            if (dragging && abs(dx) > 0.01f) {
                                lastX = change.position.x
                                dragTarget = (dragTarget + dx / tabWidth * ltrSign)
                                    .fastCoerceIn(0f, maxIndex)
                                dampedDragAnimation.updateValue(dragTarget)
                                animationScope.launch {
                                    offsetAnimation.snapTo(offsetAnimation.value + dx)
                                }
                            } else {
                                lastX = change.position.x
                            }

                            change.consume()
                        }
                    }
                },
        )
    }
}
