package com.linkgrab.app.ui.designsystem

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** Samples only the supplied content layer. Chrome stays outside that layer. */
@Composable
fun SoftGlassBlur(
    backdrop: Backdrop,
    height: Dp,
    tint: Color,
    modifier: Modifier = Modifier,
    fromBottom: Boolean = false,
    alpha: () -> Float = { 1f },
) {
    val fadeStart = if (fromBottom) 0.67f else 0.88f
    val tintIntensity = if (fromBottom) 0.40f else 0.20f
    val mask = remember(fromBottom, fadeStart) {
        val stops = arrayOf(0f to Color.White, fadeStart to Color.White, 1f to Color.Transparent)
        Brush.verticalGradient(*if (fromBottom) stops.reversedArray().map { (position, color) -> 1f - position to color }.toTypedArray() else stops)
    }
    val progressive = remember { if (Build.VERSION.SDK_INT >= 33) ProgressiveEffect.create() else null }
    val effects: BackdropEffectScope.() -> Unit = remember(progressive, fromBottom, tint) {
        val effect: BackdropEffectScope.() -> Unit = {
            if (progressive != null && Build.VERSION.SDK_INT >= 33 && size.width > 0 && size.height > 0) {
                val effect = runCatching {
                    progressive.effect(size.width, size.height, 12.dp.toPx(), fromBottom, tint, tintIntensity, fadeStart)
                }.getOrNull()
                if (effect != null) renderEffect = effect else blur(12.dp.toPx(), TileMode.Mirror)
            } else {
                blur(12.dp.toPx(), TileMode.Mirror)
            }
            padding = 0f
        }
        effect
    }
    Box(
        modifier.fillMaxWidth().height(height)
            .graphicsLayer {
                this.alpha = alpha()
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .then(if (progressive == null) Modifier.drawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            } else Modifier)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = effects,
                highlight = { null },
                shadow = { null },
                onDrawSurface = { if (progressive == null) drawRect(tint.copy(alpha = tintIntensity)) },
            ),
    )
}

@RequiresApi(33)
private class ProgressiveEffect private constructor() {
    private val blurPass = RuntimeShader(PROGRESSIVE_BLUR_SHADER)
    private val denoisePass = RuntimeShader(PROGRESSIVE_DENOISE_SHADER)

    fun effect(
        width: Float,
        height: Float,
        radius: Float,
        fromBottom: Boolean,
        tint: Color,
        tintIntensity: Float,
        fadeStart: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        fun pass(shader: RuntimeShader): RenderEffect {
            shader.setFloatUniform("contentOrigin", 0f, 0f)
            shader.setFloatUniform("contentSize", width, height)
            shader.setFloatUniform("maxRadius", radius)
            shader.setFloatUniform("fromBottom", if (fromBottom) 1f else 0f)
            return RenderEffect.createRuntimeShaderEffect(shader, "content")
        }
        blurPass.setFloatUniform("edgeFadeStart", fadeStart)
        blurPass.setColorUniform("tint", tint.toArgb())
        blurPass.setFloatUniform("tintIntensity", tintIntensity)
        return RenderEffect.createChainEffect(pass(denoisePass), pass(blurPass)).asComposeRenderEffect()
    }

    companion object {
        fun create(): ProgressiveEffect? = runCatching { ProgressiveEffect() }.getOrNull()
    }
}
