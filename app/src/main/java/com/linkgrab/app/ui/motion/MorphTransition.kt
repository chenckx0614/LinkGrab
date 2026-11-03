package com.linkgrab.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.pow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 「卡片 ⇄ 全屏」连贯形变，fork 自 HaoZai000/NexioSchedule（Apache-2.0）并裁剪：
 * 去掉了预测性返回手势（本应用没有 navigationevent 基础设施）与平板内嵌模式，
 * 保留原始的时间轴与位移/缩放/圆角插值曲线——展开先冲到位、收起贴身回卡片。
 */

/** 展开：缩放与裁切同步，位移稍快（五阶，让卡片先「冲」到位）。 */
private val MorphOpenEase = Easing { fraction -> 1f - (1f - fraction).pow(4.5f) }

/** 收起：起步快、末段缓。 */
private val MorphExitEase = Easing { fraction -> 1f - (1f - fraction).pow(3f) }

/** 位移展开：比缩放更陡。 */
private val MorphTransOpenEase = Easing { fraction -> 1f - (1f - fraction).pow(5f) }

/** 位移收起：二次曲线，回卡片时更贴身。 */
private val MorphTransExitEase = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

/** 展开时长：缩放与裁切同步，位移稍快。 */
private const val MORPH_OPEN_MILLIS = 540
private const val TRANS_OPEN_MILLIS = 500

/** 收起时长。 */
private const val MORPH_EXIT_MILLIS = 320
private const val TRANS_EXIT_MILLIS = 320

/** 卡片 → 全屏形变的一帧状态。 */
data class MorphAnimState(
    val bgAlpha: Float,
    val snapshotAlpha: Float,
    val contentAlpha: Float,
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val clipBottom: Float,
    val progress: Float,
)

/**
 * 裁切形状。宽度取当次回调的 size，别在构造时捕获外部宽度（旋转/分屏会变）。
 */
private class MorphClipShape(
    private val screenCornerRadiusPx: ScreenCornerRadiiPx,
    private val startCornerRadiusPx: Float,
    private val animState: State<MorphAnimState>,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val s = animState.value
        val radiusPx = when {
            s.progress >= 1f -> ScreenCornerRadiiPx.Zero
            s.progress <= 0.7f -> screenCornerRadiusPx.interpolateFrom(startCornerRadiusPx, s.progress / 0.7f)
            else -> screenCornerRadiusPx
        }
        // 圆角补偿：页面缩放中，裁切框在内容坐标系里要除回 scale
        val compensate = 1f / s.scale
        // 展开到底时按实测高度裁：窗口尺寸变化后 clipBottom 可能小于实际布局高度
        val bottom = if (s.progress >= 1f) size.height else s.clipBottom
        return radiusPx.scaled(compensate).toShape(density).createOutline(
            Size(size.width, bottom),
            layoutDirection,
            density,
        )
    }
}

/**
 * 形变控制器。宿主读 [state] / [clipShape] 画页面，用 [close] 播退出形变。
 */
class MorphTransition internal constructor(
    val state: State<MorphAnimState>,
    val clipShape: Shape,
    val animating: State<Boolean>,
    private val onClose: (onBeforeFinish: (() -> Unit)?) -> Unit,
) {
    /**
     * 播放退出形变，结束后回调 [onBeforeFinish] 再走 [onBack]。
     * 没有卡片边界时直接返回（不播形变）。
     */
    fun close(onBeforeFinish: (() -> Unit)? = null) = onClose(onBeforeFinish)
}

/**
 * 记住一套「卡片 ⇄ 全屏」形变。
 *
 * @param cardLeft 源卡片左边缘（px，窗口坐标）
 * @param cardTop 源卡片上边缘（px）
 * @param cardWidth 源卡片宽（px）；为 0 表示没有可交叉的卡片，不播形变直接回
 * @param cardHeight 源卡片高（px）
 * @param screenWidth 屏幕宽（px）
 * @param screenHeight 屏幕高（px）
 * @param screenCornerRadius 屏幕物理圆角（px），形变到位时按它裁切
 * @param startCornerRadiusDp 源卡片的圆角（dp），形变起点用它
 * @param onBack 收起动画播完后回调（宿主据此真正关掉浮层）
 */
@Composable
fun rememberMorphTransition(
    cardLeft: Float,
    cardTop: Float,
    cardWidth: Float,
    cardHeight: Float,
    screenWidth: Float,
    screenHeight: Float,
    screenCornerRadius: ScreenCornerRadiiPx,
    startCornerRadiusDp: Float,
    onBack: () -> Unit,
): MorphTransition {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val hasCardBounds = cardWidth > 0f && cardHeight > 0f && screenWidth > 0f

    val animProgress = remember { Animatable(if (hasCardBounds) 0f else 1f) }
    val animTransY = remember { Animatable(if (hasCardBounds) 0f else 1f) }
    val scaleProgress = remember { Animatable(if (hasCardBounds) 0f else 1f) }
    val animatingState = remember { mutableStateOf(false) }

    val startCornerRadiusPx = startCornerRadiusDp * density.density

    val currentOnBack by rememberUpdatedState(onBack)

    fun playClose(onBeforeFinish: (() -> Unit)?) {
        if (!hasCardBounds) {
            onBeforeFinish?.invoke()
            currentOnBack()
            return
        }
        if (animatingState.value) return
        animatingState.value = true
        scope.launch {
            coroutineScope {
                launch { scaleProgress.animateTo(0f, tween(MORPH_EXIT_MILLIS, easing = MorphExitEase)) }
                launch { animProgress.animateTo(0f, tween(MORPH_EXIT_MILLIS, easing = MorphExitEase)) }
                launch { animTransY.animateTo(0f, tween(TRANS_EXIT_MILLIS, easing = MorphTransExitEase)) }
            }
            onBeforeFinish?.invoke()
            currentOnBack()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCardBounds) return@LaunchedEffect
        animatingState.value = true
        coroutineScope {
            launch { scaleProgress.animateTo(1f, tween(MORPH_OPEN_MILLIS, easing = MorphOpenEase)) }
            launch { animProgress.animateTo(1f, tween(MORPH_OPEN_MILLIS, easing = MorphOpenEase)) }
            launch { animTransY.animateTo(1f, tween(TRANS_OPEN_MILLIS, easing = MorphTransOpenEase)) }
        }
        animatingState.value = false
    }

    // ⚠ 宿主传进来的几何值都是普通 Float，直接捕获进 derivedStateOf 会被冻结在首次组合的值上
    val cardLeftState = rememberUpdatedState(cardLeft)
    val cardTopState = rememberUpdatedState(cardTop)
    val cardWidthState = rememberUpdatedState(cardWidth)
    val cardHeightState = rememberUpdatedState(cardHeight)
    val screenWidthState = rememberUpdatedState(screenWidth)
    val screenHeightState = rememberUpdatedState(screenHeight)

    val animState = remember {
        derivedStateOf {
            val cardLeft = cardLeftState.value
            val cardTop = cardTopState.value
            val cardWidth = cardWidthState.value
            val cardHeight = cardHeightState.value
            val screenWidth = screenWidthState.value
            val screenHeight = screenHeightState.value

            if (!(cardWidth > 0f && cardHeight > 0f && screenWidth > 0f)) {
                MorphAnimState(0f, 0f, 1f, 0f, 0f, 1f, screenHeight, 1f)
            } else {
                val p = animProgress.value
                val ty = animTransY.value
                val bgAlpha = (p * 0.5f).coerceIn(0f, 0.5f)
                val snapAlpha = (1f - p * 3f).coerceIn(0f, 1f)
                val contAlpha = ((p - 0.1f) / 0.5f).coerceIn(0f, 1f)
                // coerceAtLeast 防止窄卡片时 scale 变负翻转
                val cardRatio = cardWidth / screenWidth
                val scale = (cardRatio + (1f - cardRatio) * scaleProgress.value).coerceAtLeast(0.05f)

                val translationX = (cardLeft + cardWidth / 2f - screenWidth / 2f) * (1f - p)

                // 可见区域中心 Y 直接沿曲线插值
                val cardCenterY = cardTop + cardHeight / 2f
                val screenCenterY = screenHeight / 2f
                val targetCenterY = cardCenterY + (screenCenterY - cardCenterY) * ty
                val translationY = targetCenterY - screenHeight / 2f * (1f - scale) -
                    (cardHeight + (screenHeight - cardHeight) * p) / 2f

                // 裁切跟随当前展开高度，换算回内容坐标系
                val morphClip = cardHeight + (screenHeight - cardHeight) * p
                val clipBottom = morphClip / scale

                MorphAnimState(
                    bgAlpha = bgAlpha,
                    snapshotAlpha = snapAlpha,
                    contentAlpha = contAlpha,
                    translationX = translationX,
                    translationY = translationY,
                    scale = scale,
                    clipBottom = clipBottom,
                    progress = p,
                )
            }
        }
    }

    val clipShape = remember(screenCornerRadius, startCornerRadiusPx) {
        MorphClipShape(screenCornerRadius, startCornerRadiusPx, animState)
    }

    return MorphTransition(
        state = animState,
        clipShape = clipShape,
        animating = animatingState,
        onClose = { playClose(it) },
    )
}

/**
 * 按当前形变状态裁剪页面内容 —— 必须在**绘制阶段**做。
 */
fun Modifier.morphClip(morph: MorphTransition): Modifier = drawWithContent {
    // MorphClipShape 只读 size / density，不读布局方向
    val outline = morph.clipShape.createOutline(size, LayoutDirection.Ltr, this)
    when (outline) {
        is Outline.Rectangle -> {
            val r = outline.rect
            if (r.left <= 0f && r.top <= 0f && r.right >= size.width && r.bottom >= size.height) {
                drawContent()
            } else {
                clipRect(r.left, r.top, r.right, r.bottom) { this@drawWithContent.drawContent() }
            }
        }
        is Outline.Rounded -> clipPath(Path().apply { addRoundRect(outline.roundRect) }) {
            this@drawWithContent.drawContent()
        }
        is Outline.Generic -> clipPath(outline.path) { this@drawWithContent.drawContent() }
    }
}
