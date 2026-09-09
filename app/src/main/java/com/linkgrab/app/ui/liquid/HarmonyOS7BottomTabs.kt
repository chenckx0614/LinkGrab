package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

/**
 * HarmonyOS 7 风格悬浮底栏。
 *
 * 参考鸿蒙 7 官方「沉浸光感」底部页签规范设计：
 * - 悬浮胶囊容器（barFloatingStyle），不撑满屏幕底部，与内容有 36vp 级轻盈边距感。
 * - 沉浸光感材质：背板/渐变模糊 + 自适应材质层级，背景内容可透出来。
 * - 光感形变交互：按压位置作为动态光源，向外投射轻微光晕，同时容器轻微下凹。
 * - 弹跳反馈：切换选中态时遮罩带轻微 overshoot 的弹簧动画。
 *
 * 性能沿用 HyperOS4 的零重组思路：所有动画目标均由 Animatable + snapshotFlow
 * 在 draw/layout 阶段读取，组合期不读取按压/选中状态，避免 drawBackdrop 节点重建。
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

    // 胶囊容器：半透明白/灰底 + 浅色描边，比 HyperOS4 更亮的玻璃感。
    val containerColor =
        if (isLightTheme) Color.White.copy(alpha = 0.48f)
        else Color(0xFF2C2C2E).copy(alpha = 0.55f)
    val strokeColor =
        if (isLightTheme) Color.White.copy(alpha = 0.70f)
        else Color.White.copy(alpha = 0.12f)

    // 选中遮罩：鸿蒙默认是浅灰胶囊，按压时轻微加深。
    val maskNormalColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.10f)
        else Color.White.copy(alpha = 0.12f)
    val maskPressedColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.20f)
        else Color.White.copy(alpha = 0.22f)

    // 光晕颜色：使用系统蓝的高亮低透明版本，契合「光感」描述。
    val glowColor =
        if (isLightTheme) Color(0xFF0088FF).copy(alpha = 0.18f)
        else Color(0xFF4CADFF).copy(alpha = 0.16f)

    var pressedIndex by remember { mutableStateOf<Int?>(null) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidthPx = constraints.maxWidth.toFloat() / tabsCount
        val maskInset = 5.dp
        val maskInsetPx = with(density) { maskInset.toPx() }
        val maskWidth = with(density) { (tabWidthPx - maskInsetPx * 2).toDp() }

        val selectedIndexState by rememberUpdatedState(selectedTabIndex())

        val initialPosition = remember {
            val i = selectedTabIndex()
            if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
        }
        val maskPosition = remember { Animatable(initialPosition) }
        val maskScale = remember { Animatable(1f) }
        val maskPress = remember { Animatable(0f) }
        val glowRadius = remember { Animatable(0f) }
        val containerPress = remember { Animatable(0f) }

        // 选中/按压位置驱动遮罩滑动。使用弹跳 spring：dampingRatio 略低于 1，
        // 让切换时产生鸿蒙标志性的轻微 overshoot「弹」一下。
        LaunchedEffect(Unit) {
            snapshotFlow {
                val i = pressedIndex ?: selectedIndexState
                if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
            }.collectLatest { target ->
                maskPosition.animateTo(
                    target,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = 380f,
                    ),
                )
            }
        }

        // 按压状态驱动遮罩缩放/加深、光晕扩散、容器下凹。
        LaunchedEffect(Unit) {
            snapshotFlow { pressedIndex != null }.collectLatest { pressed ->
                launch {
                    maskScale.animateTo(
                        if (pressed) 0.90f else 1f,
                        spring(dampingRatio = 0.72f, stiffness = 480f),
                    )
                }
                launch { maskPress.animateTo(if (pressed) 1f else 0f, tween(160)) }
                launch {
                    glowRadius.animateTo(
                        if (pressed) 1f else 0f,
                        tween(220),
                    )
                }
                launch { containerPress.animateTo(if (pressed) 1f else 0f, tween(140)) }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .graphicsLayer {
                    // 按压时整体轻微下凹，模拟光感形变。
                    val press = containerPress.value
                    scaleX = 1f - 0.015f * press
                    scaleY = 1f - 0.015f * press
                    alpha = 0.98f + 0.02f * (1f - press)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    // 沉浸光感：较强的背板模糊，不使用 iOS27 的 lens/vibrancy，
                    // 保持鸿蒙风格干净、通透的毛玻璃。
                    effects = { blur(20.dp.toPx()) },
                    highlight = { null },
                    shadow = { null },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .border(1.dp, strokeColor, RoundedCornerShape(32.dp)),
        ) {
            // ---- 光晕层：从按压标签中心向外扩散的径向光晕 ----
            if (glowRadius.value > 0.001f) {
                val pressed = pressedIndex ?: selectedIndexState
                val positionFloat = if (isLtr) pressed.toFloat() else (tabsCount - 1 - pressed).toFloat()
                val centerX = positionFloat * tabWidthPx + tabWidthPx / 2f
                val radius = tabWidthPx * (0.45f + glowRadius.value * 0.65f)
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.0f to glowColor,
                                    0.65f to glowColor.copy(alpha = glowColor.alpha * 0.35f),
                                    1.0f to Color.Transparent,
                                    center = Offset(centerX, size.height / 2f),
                                    radius = radius,
                                ),
                                radius = radius,
                                center = Offset(centerX, size.height / 2f),
                            )
                        }
                )
            }

            // ---- 选中/按压遮罩：椭圆胶囊，跟随位置并弹跳 ----
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (maskPosition.value * tabWidthPx + maskInsetPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .padding(vertical = maskInset)
                    .width(maskWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleX = maskScale.value
                        scaleY = maskScale.value
                    }
                    .clip(RoundedCornerShape(27.dp))
                    .drawBehind {
                        val color = androidx.compose.ui.graphics.lerp(
                            maskNormalColor,
                            maskPressedColor,
                            maskPress.value
                        )
                        drawRect(color)
                        // 按压时在遮罩边缘叠加一层极淡的高光，模拟光感照亮边缘。
                        if (maskPress.value > 0.01f) {
                            drawRect(
                                glowColor.copy(alpha = glowColor.alpha * 0.4f * maskPress.value),
                                size = size.copy(height = 2.5f),
                            )
                        }
                    },
            )

            // ---- 标签内容 ----
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

    // 鸿蒙 7 底部页签：选中态使用品牌高亮色，未选中态降低透明度。
    val activeColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF4CADFF)
    val inactiveColor =
        if (isLightTheme) Color(0xFF1A1A1A).copy(alpha = 0.50f)
        else Color.White.copy(alpha = 0.52f)
    // 选中/未选中的颜色变化也改用 Animatable + snapshotFlow 的父级思路不容易
    // 作用到子项；这里颜色变化幅度小、频率低，且 animateColorAsState 重组范围
    // 仅限这个 Column 内部，对底栏整体性能影响可忽略。
    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "harmonyContentColor",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    LaunchedEffect(isPressed) { onPress(isPressed) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(27.dp))
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.15).sp,
                lineHeight = 12.sp,
            )
        }
    }
}
