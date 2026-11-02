package com.linkgrab.app.ui.hyper

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 卡片入场：上浮 + 微缩 + 从散焦里聚焦，参数照抄 hyper_schedule 的 `CardRevealSpec`。
 *
 * 几个刻意的设定：
 * - **只做透明/位移/缩放，不换尺寸**：用 `AnimatedVisibility` 收拢高度会让外层
 *   `wrapContentHeight` 逐帧变高，列表会抖。
 * - **稳态零开销**：停稳后 `graphicsLayer` 整块撤掉，静态只剩卡片本身。
 * - **进度只在绘制期读**：`graphicsLayer` 的 lambda 里读，整段零重组。
 * - **错开靠 index**：第一张等 120ms 落地，之后每张晚 56ms，眼睛看到的是「依次浮上来」
 *   而不是一整块闪出来。
 */
object HyperRevealSpec {
    const val ENTER_MILLIS = 260
    const val EXIT_MILLIS = 200
    const val START_DELAY_MILLIS = 120L
    const val STEP_DELAY_MILLIS = 56L

    /** 起始下移量，字面意义的「从下面浮上来一点」。 */
    const val OFFSET_DP = 8f

    /** 起始缩放，只收一点点，大了就成弹跳。 */
    const val SCALE_FROM = 0.97f

    /** 散焦幅度：入场从 12dp 糊到 0。 */
    const val BLUR_DP = 12f

    fun scaleOf(progress: Float): Float = SCALE_FROM + (1f - SCALE_FROM) * progress
}

/** 入场进度，0 = 完全隐藏，1 = 完全呈现。 */
class HyperRevealState internal constructor(
    private val progress: Animatable<Float, *>,
    private val animating: State<Boolean>,
) {
    internal val value: Float get() = progress.value
    val isAnimating: Boolean get() = animating.value
}

/**
 * 播一次入场。`index` 决定错开时间，同一屏内按列表顺序传 0、1、2…
 *
 * 只在进入组合时播一次；之后重组不再播（所以滚动回列表不会闪）。
 */
@Composable
fun rememberHyperRevealState(
    index: Int = 0,
    enabled: Boolean = true,
): HyperRevealState {
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    // 初值就按「在动画中」算：否则延迟那段时间图层还没挂上，卡片会先以
    // alpha = 1 完整闪一下，等延迟结束再消失重放，看起来像抽帧。
    val animating = remember { mutableStateOf(enabled) }
    LaunchedEffect(Unit) {
        if (!enabled) return@LaunchedEffect
        delay(HyperRevealSpec.START_DELAY_MILLIS + index * HyperRevealSpec.STEP_DELAY_MILLIS)
        animating.value = true
        progress.animateTo(1f, tween(HyperRevealSpec.ENTER_MILLIS, easing = FastOutSlowInEasing))
        animating.value = false
    }
    return HyperRevealState(progress, animating)
}

/**
 * 把进度铺成图层。停稳后返回空 Modifier，静态不承担任何图层开销。
 *
 * 传 `alwaysLayer = true` 可以强制保留图层（调用方还要在这层上叠别的绘制时用）。
 */
fun Modifier.hyperRevealLayer(
    state: HyperRevealState,
    alwaysLayer: Boolean = false,
): Modifier {
    // animating 只会在起止各翻转一次，读它不会逐帧重组
    if (!state.isAnimating && !alwaysLayer) return this
    val supportsBlur = Build.VERSION.SDK_INT >= 31
    return this.graphicsLayer {
        // 绘制期读进度：不订阅状态，不触发重组
        val p = state.value
        alpha = p
        translationY = (1f - p) * HyperRevealSpec.OFFSET_DP.dp.toPx()
        val scale = HyperRevealSpec.scaleOf(p)
        scaleX = scale
        scaleY = scale
        val blurPx = HyperRevealSpec.BLUR_DP.dp.toPx() * (1f - p)
        renderEffect = if (supportsBlur && p < 0.99f && blurPx > 0.01f) {
            BlurEffect(blurPx, blurPx, TileMode.Decal)
        } else {
            null
        }
    }
}
