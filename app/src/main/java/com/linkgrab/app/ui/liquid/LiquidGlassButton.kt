package com.linkgrab.app.ui.liquid

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch

/**
 * 真·Liquid Glass 交互按钮，行为对齐 Apple `.glassEffect(.regular.interactive())`：
 *
 * - 材质：实时采样 [backdrop]，blur + 玻璃底色；默认 Highlight 提供常驻的
 *   镜面边缘光（rim light），玻璃有「体积」，而不是一层平的半透明色。
 * - 按下：整块玻璃轻微**放大**（Apple 规范是 grow，不是缩小），同时挂
 *   lens 让边缘折射增强、内阴影加深，光被「压」进玻璃；
 * - 触摸点照亮（illumination from touch point）：加色径向光晕从指尖扩散；
 * - 松手：欠阻尼弹簧回弹，带一次过冲的果冻感 + 轻微触感反馈。
 *
 * 性能：press 进度只在 drawBackdrop 的各 lambda（绘制阶段）读取，全程零重组；
 * lens/内阴影/阴影静止时完全不挂，平时只有一次 blur 采样。
 */
@Composable
fun LiquidGlassButton(
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    tint: Color = Color.White.copy(alpha = 0.12f),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val touch = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(10f.dp.toPx())
                    val p = press.value
                    if (p > 0.01f) {
                        lens(10f.dp.toPx() * p, 14f.dp.toPx() * p)
                    }
                },
                layerBlock = {
                    // Apple 规范：按压时玻璃轻微「生长」，松手弹回
                    val s = 1f + 0.06f * press.value
                    scaleX = s
                    scaleY = s
                },
                shadow = {
                    val p = press.value
                    if (p > 0.01f) Shadow(alpha = p) else null
                },
                innerShadow = {
                    val p = press.value
                    if (p > 0.01f) InnerShadow(radius = 6f.dp * p, alpha = p) else null
                },
                onDrawSurface = {
                    drawRect(tint)
                    val p = press.value
                    if (p > 0.01f) {
                        // 触摸点照亮：加色径向光晕，被 layerBlock 的缩放一起带走，
                        // 视觉上是玻璃被指尖压亮
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.35f * p),
                                    Color.Transparent,
                                ),
                                center = touch.value,
                                radius = size.minDimension * 1.1f,
                            ),
                            blendMode = BlendMode.Plus,
                        )
                    }
                },
            )
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pos ->
                        touch.value = pos
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch { press.animateTo(1f, spring(1f, 1200f, 0.001f)) }
                        val released = try {
                            awaitRelease()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        // 欠阻尼弹簧：回弹带果冻过冲
                        scope.launch { press.animateTo(0f, spring(0.5f, 320f, 0.001f)) }
                        if (released) onClick()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
