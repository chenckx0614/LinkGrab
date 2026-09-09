package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * HarmonyOS 7 风格悬浮底栏。
 *
 * 严格对照华为《底部页签》与《沉浸光感》设计规范：
 *
 * 1. **固定宽度居中，不撑满屏幕**：悬浮式页签是胶囊容器，3 个 Tabs 固定
 *    248vp、2 个 168vp（窗口 <600vp 断点）。撑满宽度是平铺式 / HyperOS 的做法。
 * 2. **容器高度 56vp、胶囊圆角**（圆角 = 高的一半）。
 * 3. **THIN 材质**：规范明确「组件在底部悬浮时推荐使用 THIN 枚举参数」，
 *    轻薄模糊即可，不是厚重的毛玻璃。
 * 4. **渐变颜色蒙层**：规范「底部采用渐变颜色增强界面空间感，渐变颜色层
 *    建议较底部内容最顶边高 16vp，颜色需与页面背景色保持一致」。
 * 5. **选中态 = 品牌高亮色**，没有灰色遮罩胶囊。规范「被激活的页签应有
 *    明确的视觉反馈，如使用品牌高亮色显示」。灰色椭圆遮罩是 HyperOS4 的
 *    标志性元素，鸿蒙悬浮式靠颜色区分选中。
 * 6. **光感形变 = 照亮容器边缘**：规范「向周围材质表面投射光晕，照亮容器
 *    边缘，光照强度随距离衰减，同时使容器向手指位置形变」。所以光效是沿
 *    容器边缘流转的环境光，不是从中心扩散的大圆光晕。
 *
 * 性能沿用零重组思路：Animatable + snapshotFlow 事件驱动，组合期不读按压状态。
 */
@Composable
fun HarmonyOS7BottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(
        selectIndex: (Int) -> Unit,
        reportPress: (index: Int, pressed: Boolean) -> Unit,
    ) -> Unit
) {
    val isLightTheme = !isInDarkTheme()

    // 规范：底部悬浮使用 THIN 材质档位 —— 轻薄、通透。
    val containerColor =
        if (isLightTheme) Color.White.copy(alpha = 0.38f)
        else Color(0xFF2C2C2E).copy(alpha = 0.45f)

    // 光效颜色：规范「建议使用低透明度、高亮度的色彩」。
    val edgeGlowColor =
        if (isLightTheme) Color(0xFF66B8FF)
        else Color(0xFF5CB3FF)

    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

        // 规范：悬浮式固定宽度（2 Tabs 168vp / 3 Tabs 248vp），居中，不撑满。
        val barWidth = remember(tabsCount) { (8 + tabsCount * 80).dp }
        val barHeight = 56.dp
        val barCorner = 28.dp // 胶囊圆角 = 高的一半

        val tabWidthPx = with(density) { barWidth.toPx() } / tabsCount
        val cornerRadiusPx = with(density) { barCorner.toPx() }
        val barHeightPx = with(density) { barHeight.toPx() }
        val edgeStrokePx = with(density) { 2.dp.toPx() }

        // 指尖弥散光的半径：要能覆盖整个胶囊容器并在内部散开，而不是一个
        // 只罩住单个标签的小圆斑。取容器宽度的 1.15 倍，配合容器的 clip，
        // 视觉上就是光从指尖向整个控件内部扩散、并在边缘衰减。
        val barWidthPx = with(density) { barWidth.toPx() }
        val glowRadiusPx = barWidthPx * 1.15f

        // 两个渐变对象都缓存为固定实例（不随动画变化），动画只改
        // translate 位移与 alpha，避免每帧分配 Brush + Color。
        val glowBrush = remember(edgeGlowColor, glowRadiusPx) {
            Brush.radialGradient(
                0.0f to edgeGlowColor.copy(alpha = 0.32f),
                0.25f to edgeGlowColor.copy(alpha = 0.20f),
                0.55f to edgeGlowColor.copy(alpha = 0.08f),
                0.8f to edgeGlowColor.copy(alpha = 0.02f),
                1.0f to Color.Transparent,
                center = Offset(glowRadiusPx, glowRadiusPx),
                radius = glowRadiusPx,
            )
        }
        // 边缘光的亮度分布固定（中心偏亮、两端衰减），整体强度交给 alpha。
        val edgeBrush = remember(edgeGlowColor, tabWidthPx) {
            Brush.radialGradient(
                0.0f to edgeGlowColor.copy(alpha = 0.50f),
                0.45f to edgeGlowColor.copy(alpha = 0.26f),
                1.0f to Color.Transparent,
                center = Offset(tabWidthPx * 1.5f, barHeightPx / 2f),
                radius = tabWidthPx * 1.6f,
            )
        }

        // 初始就落在选中项中心，避免首帧多余的位移动画。
        val glowCenterPx = remember {
            val i = selectedTabIndex()
            val pos = if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
            Animatable(pos * tabWidthPx + tabWidthPx / 2f)
        }
        val glowStrength = remember { Animatable(0f) }
        val containerPress = remember { Animatable(0f) }

        val selectedIndexState by rememberUpdatedState(selectedTabIndex())

        // 光效中心跟随按压的标签；松开后回落到选中项。
        // 高刚度 spring：按压到别的标签时光点几乎立即跟过去（「跟着的光效」），
        // 又保留一点点平滑不至于生硬跳变。
        LaunchedEffect(Unit) {
            snapshotFlow {
                val i = pressedIndex ?: selectedIndexState
                val pos = if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
                pos * tabWidthPx + tabWidthPx / 2f
            }.collectLatest { target ->
                glowCenterPx.animateTo(target, spring(stiffness = 1200f, dampingRatio = 0.9f))
            }
        }

        // 按压驱动光效强度与容器形变。
        LaunchedEffect(Unit) {
            snapshotFlow { pressedIndex != null }.collectLatest { pressed ->
                launch { glowStrength.animateTo(if (pressed) 1f else 0f, tween(200)) }
                launch { containerPress.animateTo(if (pressed) 1f else 0f, tween(140)) }
            }
        }

        Box(
            Modifier
                .width(barWidth)
                .height(barHeight)
                .graphicsLayer {
                    // 规范「使容器向手指位置形变」—— 轻微下沉，幅度克制。
                    val press = containerPress.value
                    scaleX = 1f - 0.010f * press
                    scaleY = 1f - 0.010f * press
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    // THIN 材质：轻薄模糊，保留通透感。
                    effects = { blur(12.dp.toPx()) },
                    highlight = { null },
                    shadow = { null },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .clip(RoundedCornerShape(barCorner))
                // 光感形变：指尖弥散光 + 沿容器边缘流转的光。
                // 性能关键：
                // 1) 全部在 draw 阶段读取 Animatable.value —— 只触发 draw 失效，
                //    零重组（若在组合期用 if 判断，每帧都会重组整个底栏）。
                // 2) brush 由 remember 缓存为固定实例，配合 translate 移动，
                //    每帧零对象分配，避免 GC 抖动。
                .drawBehind {
                    val strength = glowStrength.value
                    if (strength > 0.001f) {
                        val cx = glowCenterPx.value
                        val cy = size.height / 2f
                        // 指尖弥散光：圆心钉在指尖，半径随按压进度从小扩散到
                        // 覆盖整个容器 —— 视觉上就是光从指尖在控件内部散开。
                        // 渐变中心始终等于 drawCircle 的 center，所以绘制半径
                        // 变化时取到的仍是渐变的中心部分（最亮处），扩散自然。
                        // 容器自身的 clip 裁掉超出胶囊的部分。
                        translate(cx - glowRadiusPx, cy - glowRadiusPx) {
                            drawCircle(
                                brush = glowBrush,
                                radius = glowRadiusPx * (0.40f + 0.60f * strength),
                                center = Offset(glowRadiusPx, glowRadiusPx),
                                alpha = strength,
                            )
                        }
                        // 边缘光：光扩散到控件边界时照亮容器边缘。
                        drawRoundRect(
                            brush = edgeBrush,
                            topLeft = Offset(1f, 1f),
                            size = Size(size.width - 2f, size.height - 2f),
                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                            style = Stroke(width = edgeStrokePx),
                            alpha = strength,
                        )
                    }
                },
        ) {
            // ---- 标签内容：选中态靠品牌高亮色，没有灰色遮罩胶囊 ----
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    content(
                        { index -> onTabSelected(index) },
                        { index, pressed -> pressedIndex = if (pressed) index else null },
                    )
                },
            )
        }
    }
}

@Composable
fun HarmonyOS7TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPress: (pressed: Boolean) -> Unit = {},
    onClick: () -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    // 规范：激活页签用品牌高亮色，未激活靠降低透明度弱化。
    val activeColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF4CADFF)
    val inactiveColor =
        if (isLightTheme) Color(0xFF1A1A1A).copy(alpha = 0.45f)
        else Color.White.copy(alpha = 0.48f)
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "harmonyContentColor",
    )

    // 规范：切换时选中态有细微的弹跳动效。
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            bounce.animateTo(0.86f, tween(90))
            bounce.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 520f),
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { onPress(isPressed) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer {
                scaleX = bounce.value
                scaleY = bounce.value
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                lineHeight = 11.sp,
            )
        }
    }
}
