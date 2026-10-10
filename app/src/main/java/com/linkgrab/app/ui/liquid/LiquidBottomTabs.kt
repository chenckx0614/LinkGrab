package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Liquid-glass bottom tab bar ported from Kyant0/AndroidLiquidGlass
 * (catalog/components/LiquidBottomTabs.kt), adapted for LinkGrab theming.
 *
 * Structure:
 * 1. A full-width glass capsule container (vibrancy + blur + lens).
 * 2. An invisible duplicate of the tab content recorded into its own
 *    [Backdrop] layer and tinted with the accent color — this is what makes
 *    the selected tab render in blue through the capsule refraction.
 * 3. The draggable selection capsule sampling both backdrops
 *    ([rememberCombinedBackdrop]), with velocity-driven squash & stretch
 *    and an interactive additive highlight.
 */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(selectIndex: (Int) -> Unit) -> Unit
) {
    val isLightTheme = !isInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        // 这里原先还有一条 offsetAnimation：拖动时每帧读取它会让整个底栏
        // 重新组合，而实际收益只是几像素的横向弹性。把这条高频状态移除，
        // 胶囊本身的 Animatable 仍然负责主要的跟手动画。
        val panelOffset = 0f

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        // NOTE: do NOT key this remember on the `selectedTabIndex` lambda — the
        // lambda is recreated on every recomposition, which would orphan the
        // state object observed by the snapshotFlow below and silently break
        // the tap-driven selection animation (drag still worked because it
        // drives the animation directly).
        var currentIndex by remember {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {
                    // 不在这里改 currentIndex：拖拽手势自己的 press() 和
                    // LaunchedEffect(currentIndex) 的 animateToValue() 会同时
                    // 修改同一组 Animatable，导致胶囊/光晕来回跳。
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    // 拖拽收尾的 snap 仍属于拖拽驱动，保留 chromaticAberration。
                    animateToValue(targetIndex.toFloat(), fromDrag = true)
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                }
            )
        }
        // selectedTabIndex 是个 lambda，每次重组都是新实例；拿它当
        // LaunchedEffect 的 key 会让这个 effect 反复重启，观察被打断。
        // 用 rememberUpdatedState 包一层，effect 只启动一次。
        val selectedIndex by androidx.compose.runtime.rememberUpdatedState(selectedTabIndex())
        LaunchedEffect(Unit) {
            snapshotFlow { selectedIndex }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        // 点标签时改内部 currentIndex —— 这样选中胶囊（那块玻璃「遮罩」）
        // 才会走 animateToValue 滑过去。之前标签的 onClick 直接调导航，
        // 绕过了这里，所以点击没有滑动动画（只有拖拽有）。
        val selectTab: (Int) -> Unit = { index ->
            currentIndex = index.fastCoerceIn(0, tabsCount - 1)
        }
        val tabContent = content
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    // 点击路径用 0.5 的按压峰值：lens/阴影半径减半，
                    // 点击转场中的 AGSL 采样成本大幅下降，视觉几乎无差。
                    dampedDragAnimation.animateToValue(index.toFloat(), pressPeak = 0.5f)
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        // ---- Layer 1: visible glass container with the plain tab content ----
        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // 静止时不挂 lens：lens(0,0) 仍会走 AGSL shader 路径，
                        // 每帧白付采样成本。vibrancy/blur 轻量，保持玻璃通透感。
                        blur(6f.dp.toPx())
                        if (progress > 0.01f) {
                            lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                        }
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(62f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = { tabContent(selectTab) }
        )

        // ---- Layer 2: invisible tinted duplicate recorded into tabsBackdrop ----
        // 这一层是 Layer 3 胶囊玻璃的「模糊底」：必须保留 drawBackdrop 的
        // blur，否则胶囊位置采样不到模糊内容、玻璃效果就没了。
        // 但 onDrawSurface 的底色必须压到极低 alpha —— 它会被下面整层的
        // ColorFilter.tint 一起染成蓝色，alpha 一高就是那块蓝色遮罩。
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(6f.dp.toPx())
                        // 静止时不挂 lens：lens(0,0) 仍会走 AGSL shader 路径，
                        // 在转场动画中白付每帧采样成本。
                        if (progress > 0.01f) {
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        }
                    },
                    highlight = { null },
                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.06f)) }
                )
                .height(54f.dp)
                .fillMaxWidth()
                .padding(horizontal = 4f.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor.copy(alpha = 0.85f))),
            verticalAlignment = Alignment.CenterVertically,
            content = { tabContent(selectTab) }
        )

        // ---- Layer 3: the draggable selection capsule ----
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        // chromaticAberration 的 lens 是这条路径上最贵的
                        // 效果，静止时必须完全摘掉，不能传 0 半径；
                        // 点击切换也直接跳过（只留给拖拽），点击是最频繁的
                        // 操作，省下的 AGSL 采样对帧率最明显。
                        if (progress > 0.01f) {
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = dampedDragAnimation.dragDriven
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0.01f) Highlight.Default.copy(alpha = progress) else null
                    },
                    shadow = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0.01f) Shadow(alpha = progress) else null
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        if (progress > 0.01f) {
                            InnerShadow(
                                radius = 8f.dp * progress,
                                alpha = progress
                            )
                        } else null
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
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(54f.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
