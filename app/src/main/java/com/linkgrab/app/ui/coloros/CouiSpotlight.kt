package com.linkgrab.app.ui.coloros

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch

/**
 * 凝光跟手光：手指按下时在触点唤起一片柔光，拖动时光斑跟着走，松手扩散、淡出。
 *
 * ColorOS 17 的原版（`CouiSpotlight`）在 API 33 上用 RuntimeShader 做亮度混合，还叠了
 * 环境光、漫反射和噪声；这里退到 Compose 的径向渐变——中心一片白光，边缘化到全透明，
 * 深色下用 PLUS 叠色提亮，浅色下直接盖一层淡白。观感上保留了"指尖有光"这件事。
 *
 * 只观察触摸、不消费事件，所以放在 `clickable` 外面不会抢点击，也不会挡住滚动。
 */
class CouiLightState internal constructor() {
    internal var center by mutableStateOf(Offset.Zero)
    internal val intensity = Animatable(0f)
    internal val spread = Animatable(0f)
}

@Composable
fun rememberCouiLightState(): CouiLightState = remember { CouiLightState() }

/**
 * @param strength 光斑上限强度，按钮给 1，列表行给 0.55（行面积大，太亮会刺眼）
 */
@Composable
fun Modifier.couiTouchLight(
    dark: Boolean,
    strength: Float = 1f,
    enabled: Boolean = true,
    state: CouiLightState = rememberCouiLightState(),
): Modifier {
    if (!enabled) return this
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(enabled) {
            awaitPointerEventScope {
                while (true) {
                    // Initial 阶段只是"看一眼"，不消费，点击和滚动照旧
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    state.center = down.position
                    scope.launch { state.intensity.animateTo(1f, CouiMotion.press(candy = false)) }
                    scope.launch { state.spread.animateTo(0.42f, CouiMotion.press(candy = false)) }

                    var held = true
                    while (held) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            held = false
                        } else {
                            state.center = change.position
                            held = change.pressed
                        }
                    }

                    // 松手：光圈扩散到控件之外，同时淡掉
                    scope.launch { state.intensity.animateTo(0f, CouiMotion.release(candy = false)) }
                    scope.launch { state.spread.animateTo(1f, CouiMotion.release(candy = false)) }
                }
            }
        }
        .drawWithContent {
            val alpha = state.intensity.value * strength
            if (alpha > 0.002f) {
                val core = 26.dp.toPx()
                // 终态半径跟着控件大小走：小按钮收得紧，整行铺得开
                val far = size.minDimension.coerceAtLeast(1f) * 1.45f
                val radius = (core + (far - core) * state.spread.value).coerceAtLeast(1f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha * if (dark) 0.32f else 0.46f),
                            Color.White.copy(alpha = alpha * if (dark) 0.10f else 0.14f),
                            Color.Transparent,
                        ),
                        center = state.center,
                        radius = radius,
                    ),
                    // 深色下用叠加，白光只提亮不去盖住底色（胶囊按钮不会被打灰）
                    blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
                )
            }
            drawContent()
        }
}

/**
 * 凝光高光：表面顶部一层很淡的白，向下化开。
 *
 * ColorOS 的按钮是"半透明材质"的观感，纯色填充少了这一层就显得很平。
 * 画在内容之下（`drawContent` 之前），不会糊住文字。
 */
fun Modifier.couiSheen(
    color: Color = Color.White,
    heightFraction: Float = 0.62f,
    alpha: Float = 0.16f,
): Modifier = drawWithContent {
    if (alpha > 0.001f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                startY = 0f,
                endY = size.height * heightFraction,
            ),
        )
    }
    drawContent()
}

// MARK: - Demo-ish helpers

/**
 * ColorOS 的"凝光卡片"：底色 + 轮廓光 + 跟手光，一键组合。
 *
 * 和 [CouiCard] 的区别是这里可点：光在按下时亮起来，卡片本身不缩放。
 */
@Composable
fun CouiLitCard(
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = CouiDimens.CardCorner,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = isInDarkTheme()
    val shape = couiSquircle(corner)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(shape)
            .background(couiSurface())
            .couiSheen()
            .couiContour(
                shape = shape,
                color = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
                width = 1.dp,
            )
            .couiTouchLight(dark = dark)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
