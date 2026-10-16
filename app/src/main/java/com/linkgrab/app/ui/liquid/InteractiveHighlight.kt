package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Ported from Kyant0/AndroidLiquidGlass (catalog/utils/InteractiveHighlight.kt),
 * with the RuntimeShader path replaced by an equivalent radial-gradient brush
 * so it works against the published backdrop artifact without internal APIs.
 *
 * Renders a soft additive highlight that follows the touch position while
 * the capsule is pressed.
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    /**
     * 光晕半径的额外缩放，逐帧现读（例如随展开进度收缩）。
     * 学习自 Nexio：菜单面板展开后要把光晕收小，否则整块玻璃被冲白。
     */
    val radiusScale: () -> Float = { 1f },
    /**
     * 光晕半径上限。取 min(size.minDimension, 本值)：面板再大也不超过这个半径，
     * 收起态的小圆按钮则自动退回自身尺寸。null = 不封顶（用 minDimension）。
     */
    val radiusBaseDp: Dp? = null,
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier =
        Modifier.drawWithContent {
            val progress = pressProgressAnimation.value
            if (progress > 0f) {
                drawRect(
                    Color.White.copy(0.08f * progress),
                    blendMode = BlendMode.Plus
                )
                val center = position(size, positionAnimation.value)
                    .let { Offset(it.x.coerceIn(0f, size.width), it.y.coerceIn(0f, size.height)) }
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(0.15f * progress),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = (radiusBaseDp?.let { min(size.minDimension, it.toPx()) }
                            ?: size.minDimension) * 1.5f * radiusScale(),
                    ),
                    blendMode = BlendMode.Plus,
                )
            }

            drawContent()
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                }
            ) { change, _ ->
                // AwaitPointerEventScope 是 restricted suspension，不能直接 snapTo；
                // 用已有的 animationScope 派发（主线程顺序执行，开销极小）
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
