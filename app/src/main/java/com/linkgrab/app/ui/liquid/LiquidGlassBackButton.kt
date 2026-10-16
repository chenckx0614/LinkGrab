package com.linkgrab.app.ui.liquid

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 顶栏液态玻璃圆形按钮，移植自 Nexio 课程表（gitee.com/com_haooz_account/
 * hyper_schedule，AGPL-3.0）的 LiquidTopBarButton，在本项目 backdrop 2.0.0 上实现。
 *
 * 与旧版 IOS26BackButton（纯色圆形 + collectIsPressed 缩放）的区别：
 * 1. **真玻璃**：实时采样 [backdrop]（页面内容录制层），vibrancy + blur(8) + lens 折射；
 * 2. **按压生长 + 沿拖动方向拉伸**：pressProgress 驱动整体 +4dp 生长，
 *    手指拖动时沿位移方向做 2dp 拉伸（scaleX/scaleY 分离），tanh 阻尼跟手位移；
 * 3. **阴影同步变形**：整圈投影放在外层 graphicsLayer，拉伸时阴影跟着变；
 * 4. **触摸点高光**：InteractiveHighlight 的指尖光晕 + 高光跟随；
 * 5. **触感反馈**：点击触发 Confirm 触觉。
 *
 * [icon] 传 null 时画 iOS 风格的 chevron.left（返回按钮用）；传图标则是普通的
 * 顶栏图标按钮 —— 与右上角菜单的收起态刻意做成同一套玻璃质感。
 *
 * [backdrop] 为 null 时退回半透明实色（没有采样源的场景）。
 */
@Composable
fun LiquidGlassTopBarButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    iconSize: Dp = 24.dp,
    iconOffset: DpOffset = DpOffset.Zero,
    buttonSize: Dp = 42.dp,
    darkBackground: Boolean = false,
    iconTint: Color = Color.Unspecified,
    performHapticFeedback: Boolean = true,
    enabled: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isLightTheme = !isInDarkTheme()

    // 与 Nexio 顶栏按钮同色：收起态与右上角菜单是同一颗玻璃，形变时不能突变
    val containerColor = when {
        darkBackground -> Color.White.copy(alpha = 0.12f)
        isLightTheme -> Color(0xFFFAFAFA).copy(alpha = 0.76f)
        else -> Color(0xFF242424).copy(alpha = 0.84f)
    }
    val edgeStrokeColor = when {
        darkBackground -> Color.White.copy(alpha = 0.22f)
        isLightTheme -> Color.White.copy(alpha = 0.50f)
        else -> Color.White.copy(alpha = 0.16f)
    }
    val iconColor = if (iconTint != Color.Unspecified) {
        iconTint
    } else when {
        darkBackground -> Color.White.copy(alpha = 0.92f)
        isLightTheme -> Color.Black.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.85f)
    }
    val shadowArgb = if (isLightTheme) 0x12000000 else 0x20000000

    val interactionSource = remember { MutableInteractionSource() }
    // drawBackdrop 靠引用比较 shape/effects/onDrawSurface：lambda 每次新建会让节点
    // 每帧 update → 重新录制采样层。这里全部 remember 住。
    val shapeBlock: () -> androidx.compose.ui.graphics.Shape = remember { { CircleShape } }
    val effects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember {
        {
            vibrancy()
            blur(8.dp.toPx())
            lens(8f.dp.toPx(), 24f.dp.toPx())
        }
    }
    val onDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(containerColor) { { drawRect(containerColor) } }

    Box(
        modifier = modifier
            .wrapContentSize()
            .size(buttonSize)
            .graphicsLayer {
                clip = false
                // size 在首帧测量前可能为 0，钳到 1px 避免除零产生 NaN/Infinity
                val selfHeight = size.height.coerceAtLeast(1f)
                val maxDim = size.maxDimension.coerceAtLeast(1f)
                val minDim = max(1f, size.minDimension)
                val progress = interactiveHighlight.pressProgress.coerceAtLeast(0f)
                val scale = lerp(1f, 1f + 4f.dp.toPx() / selfHeight, progress)
                val offset = interactiveHighlight.offset
                // 沿拖动方向的最大拉伸：2dp/高度（42dp 按钮约 4.8%）
                val maxDragScale = 2f.dp.toPx() / selfHeight
                val offsetAngle = atan2(offset.y, offset.x)
                scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / maxDim)
                scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / maxDim)
                // tanh 阻尼跟手：起始约 8%，越拖越黏
                translationX = minDim * kotlin.math.tanh(0.08f * offset.x / minDim)
                translationY = minDim * kotlin.math.tanh(0.08f * offset.y / minDim)
            }
            .drawBehind {
                // 整圈投影（外圆减内圆 + 模糊）：随外层 graphicsLayer 一起变形
                val blurRadius = with(density) { 10f.dp.toPx() }
                val shadowSpread = with(density) { 2f.dp.toPx() }
                val outerRadius = size.minDimension / 2f + shadowSpread
                val innerRadius = size.minDimension / 2f
                val path = Path().apply {
                    addCircle(center.x, center.y, outerRadius, Path.Direction.CW)
                    addCircle(center.x, center.y, innerRadius, Path.Direction.CCW)
                }
                val paint = Paint().apply {
                    color = android.graphics.Color.argb(
                        (android.graphics.Color.alpha(shadowArgb) * 3.2f)
                            .coerceAtMost(255f).toInt(),
                        android.graphics.Color.red(shadowArgb),
                        android.graphics.Color.green(shadowArgb),
                        android.graphics.Color.blue(shadowArgb),
                    )
                    maskFilter = BlurMaskFilter(
                        blurRadius.coerceAtLeast(0.1f),
                        BlurMaskFilter.Blur.NORMAL,
                    )
                }
                drawIntoCanvas { canvas -> canvas.nativeCanvas.drawPath(path, paint) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    // 按压视觉由 interactiveHighlight 提供，不要默认涟漪
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        if (performHapticFeedback) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                        }
                        onClick()
                    },
                )
                .then(
                    if (backdrop != null) {
                        Modifier
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = shapeBlock,
                                effects = effects,
                                highlight = null,
                                shadow = null,
                                layerBlock = { alpha = 1f },
                                onDrawSurface = onDrawSurface,
                            )
                            .border(1.dp, edgeStrokeColor, CircleShape)
                    } else {
                        Modifier
                            .clip(CircleShape)
                            .drawBehind { drawRect(containerColor) }
                            .border(1.dp, edgeStrokeColor, CircleShape)
                    }
                )
                .then(interactiveHighlight.modifier)
                .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
                .zIndex(0f),
        )
        // 图标层：zIndex(1f) 只保证叠在玻璃之上，仍会跟随外层 graphicsLayer 一起
        // 生长/拉伸/位移 —— 这正是 iOS 整体缩放的观感。
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(iconSize)
                    .offset(iconOffset.x, iconOffset.y)
                    .zIndex(1f),
                tint = iconColor,
            )
        } else {
            val chevronW = with(density) { 11.dp.toPx() }
            val chevronH = with(density) { 17.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .offset(iconOffset.x, iconOffset.y)
                    .zIndex(1f),
            ) {
                val left = (size.width - chevronW) / 2f - with(density) { 1.dp.toPx() }
                val top = (size.height - chevronH) / 2f
                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(left + chevronW * 0.85f, top)
                        lineTo(left + chevronW * 0.15f, top + chevronH / 2f)
                        lineTo(left + chevronW * 0.85f, top + chevronH)
                    },
                    color = iconColor,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

/**
 * 返回按钮：[LiquidGlassTopBarButton] 的返回形态（画 iOS chevron.left）。
 *
 * [backdrop] 为 null 时退回半透明实色（无采样源的场景，如预览页可用专用层）。
 */
@Composable
fun LiquidGlassBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 42.dp,
    darkBackground: Boolean = false,
    performHapticFeedback: Boolean = false,
    enabled: Boolean = true,
) {
    LiquidGlassTopBarButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        icon = null,
        contentDescription = "返回",
        buttonSize = buttonSize,
        darkBackground = darkBackground,
        // 返回是高频操作，默认不震（沿用历史行为）
        performHapticFeedback = performHapticFeedback,
        enabled = enabled,
    )
}
