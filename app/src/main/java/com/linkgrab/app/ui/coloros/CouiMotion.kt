package com.linkgrab.app.ui.coloros

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * ColorOS 的弹簧参数化（`folme` 那套）：
 *
 * ```
 * stiffness = (2π / response)²
 * dampingRatio = 1 - bounce
 * ```
 *
 * [response] 单位是秒，语义是"这一下大约走多久"，比直接调 stiffness 直观；
 * [bounce] 0 表示不回弹。suqi8/coui 与 COUI-Kit 用的都是这个公式。
 */
fun <T> couiSpring(
    response: Float,
    bounce: Float = 0f,
    visibilityThreshold: T? = null,
): SpringSpec<T> {
    val angular = 2.0 * Math.PI / response
    return spring(
        dampingRatio = (1f - bounce).coerceIn(0.05f, 1f),
        stiffness = (angular * angular).toFloat(),
        visibilityThreshold = visibilityThreshold,
    )
}

/** ColorOS 的动效参数表，取值来自 coui 源码。 */
object CouiMotion {
    /** 弹层位移进入：spring(damping 0.8, stiffness 322.27)。 */
    val PopupEnter = spring<Float>(dampingRatio = 0.8f, stiffness = 322.27f, visibilityThreshold = 0.0001f)

    /** 弹层位移退出：spring(damping 1, stiffness 438.65)。 */
    val PopupExit = spring<Float>(dampingRatio = 1f, stiffness = 438.65f, visibilityThreshold = 0.0001f)

    /** 透明度进入：folmeSpring(damping 0.8, response 0.35)。 */
    fun <T> fadeIn(): SpringSpec<T> = couiSpring(response = 0.35f, bounce = 0.2f)

    /** 透明度退出：folmeSpring(damping 1, response 0.25)。 */
    fun <T> fadeOut(): SpringSpec<T> = couiSpring(response = 0.25f, bounce = 0f)

    /** 按压按下：response 0.25（长条 0.3），不回弹。 */
    fun press(candy: Boolean): SpringSpec<Float> =
        couiSpring(response = if (candy) 0.3f else 0.25f, bounce = 0f)

    /** 按压松开：response 0.5，回弹 0.6（长条 0.5）。 */
    fun release(candy: Boolean): SpringSpec<Float> =
        couiSpring(response = 0.5f, bounce = if (candy) 0.5f else 0.6f)

    /** 开关拇指滑移。 */
    val Toggle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 400f)
}

/**
 * 把 ColorOS 的按压缩放挂到控件上。
 *
 * 目标缩放不是定值，而是按**控件面积**和**宽高比**算出来的（见 [CouiPressMotion]）：
 * 大块缩得少、小块缩得多，长条形按钮取面积与比例的均值。手感上比统一 0.96 更接近系统。
 *
 * [interactionSource] 必须是控件 clickable 用的那一个，否则收不到按压状态。
 */
fun Modifier.couiPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
): Modifier = composed {
    if (!enabled) return@composed this
    val pressed by interactionSource.collectIsPressedAsState()
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed, size) {
        if (pressed) {
            scale.animateTo(
                CouiPressMotion.target(size, density),
                CouiMotion.press(CouiPressMotion.isCandy(size)),
            )
        } else {
            scale.animateTo(1f, CouiMotion.release(CouiPressMotion.isCandy(size)))
        }
    }
    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            // 在 layer 里读 state，动画帧不触发重组
            val value = scale.value
            scaleX = value
            scaleY = value
        }
}

/**
 * ColorOS 按压缩放的目标值，逐行对应 COUI-Kit `CouiPressMotion`。
 *
 * ```
 * 面积 20dp² → 150dp² 映射到 0.85 → 0.97
 * 宽高比 2 → 15 映射到 0.85 → 1.00（只有 aspect > 2 的长条才参与，和面积值取均值）
 * ```
 *
 * 中间用 `PathInterpolator(0,0,0,1)` 那条曲线（y = 3x^(2/3) - 2x）压一道，
 * 低段快、高段缓。
 */
object CouiPressMotion {

    fun target(size: IntSize, density: Float): Float {
        if (size.width <= 0 || size.height <= 0) return 1f
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val minArea = 20f * density * 20f * density
        val maxArea = 150f * density * 150f * density
        val areaProgress = ((width * height - minArea) / (maxArea - minArea)).coerceIn(0f, 1f)
        val areaScale = 0.85f + 0.12f * decelerate(areaProgress)
        val aspect = max(width, height) / min(width, height)
        if (aspect <= 2f) return areaScale
        val aspectScale = 0.85f + 0.15f * decelerate(((aspect - 2f) / 13f).coerceIn(0f, 1f))
        return (areaScale + aspectScale) * 0.5f
    }

    /** 宽高比超过 2（长条 / 胶囊），决定用哪组弹簧。 */
    fun isCandy(size: IntSize): Boolean {
        if (size.width <= 0 || size.height <= 0) return false
        return max(size.width, size.height).toFloat() / min(size.width, size.height) > 2f
    }

    private fun decelerate(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val t = x.toDouble().pow(1.0 / 3.0)
        return (3.0 * t * t - 2.0 * t * t * t).toFloat().coerceIn(0f, 1f)
    }
}
