package com.linkgrab.app.ui.motion

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.capsule.ContinuousRoundedRectangle

/**
 * 屏幕物理四角圆角（px），fork 自 HaoZai000/NexioSchedule（Apache-2.0）。
 * 卡片⇄全屏形变在展开到位时按它裁切，让页面圆角贴住硬件圆角。
 */
@Immutable
data class ScreenCornerRadiiPx(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomRight: Float = 0f,
    val bottomLeft: Float = 0f,
) {
    val maxRadius: Float get() = maxOf(topLeft, topRight, bottomRight, bottomLeft)

    fun scaled(scale: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        topLeft * scale, topRight * scale, bottomRight * scale, bottomLeft * scale,
    )

    fun coerceAtLeast(minRadius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        maxOf(topLeft, minRadius),
        maxOf(topRight, minRadius),
        maxOf(bottomRight, minRadius),
        maxOf(bottomLeft, minRadius),
    )

    /** 每个角从同一个起点半径插值。 */
    fun interpolateFrom(startRadius: Float, fraction: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
        startRadius + (topLeft - startRadius) * fraction,
        startRadius + (topRight - startRadius) * fraction,
        startRadius + (bottomRight - startRadius) * fraction,
        startRadius + (bottomLeft - startRadius) * fraction,
    )

    companion object {
        val Zero = ScreenCornerRadiiPx()

        fun uniform(radius: Float): ScreenCornerRadiiPx = ScreenCornerRadiiPx(
            radius, radius, radius, radius,
        )
    }
}

/** 屏幕四角读取；读不到的角为 0（不裁切）。 */
@Composable
fun rememberScreenCornerRadiiPx(): ScreenCornerRadiiPx {
    val context = LocalContext.current
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    return remember(context, density, containerSize) {
        try {
            if ((context as? Activity)?.isInMultiWindowMode == true) {
                ScreenCornerRadiiPx.uniform(20f * density.density)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val bounds = wm?.currentWindowMetrics?.bounds
                val maxBounds = wm?.maximumWindowMetrics?.bounds
                // 小窗兜底：窗口任一维度明显小于屏幕（分屏 / ROM 自家小窗都算）→ 固定 20dp
                val isSmallWindow = bounds != null && maxBounds != null &&
                    (bounds.width() < maxBounds.width() * SMALL_WINDOW_RATIO ||
                        bounds.height() < maxBounds.height() * SMALL_WINDOW_RATIO)
                if (isSmallWindow) {
                    ScreenCornerRadiiPx.uniform(20f * density.density)
                } else {
                    val insets = wm?.currentWindowMetrics?.windowInsets
                    fun radius(position: Int): Float =
                        insets?.getRoundedCorner(position)?.radius?.toFloat() ?: 0f
                    ScreenCornerRadiiPx(
                        topLeft = radius(RoundedCorner.POSITION_TOP_LEFT),
                        topRight = radius(RoundedCorner.POSITION_TOP_RIGHT),
                        bottomRight = radius(RoundedCorner.POSITION_BOTTOM_RIGHT),
                        bottomLeft = radius(RoundedCorner.POSITION_BOTTOM_LEFT),
                    )
                }
            } else {
                ScreenCornerRadiiPx.Zero
            }
        } catch (_: Exception) {
            ScreenCornerRadiiPx.Zero
        }
    }
}

/** 窗口尺寸 / 屏幕尺寸低于此比例视为小窗（留 5% 余量，防全屏下的取整误差误判） */
private const val SMALL_WINDOW_RATIO = 0.95f

/** 硬件圆角贴物理边缘（含 RTL）。 */
fun ScreenCornerRadiiPx.toShape(density: Density): Shape = PhysicalScreenCornerShape(
    with(density) {
        ContinuousRoundedRectangle(
            topStart = topLeft.toDp(),
            topEnd = topRight.toDp(),
            bottomEnd = bottomRight.toDp(),
            bottomStart = bottomLeft.toDp(),
        )
    }
)

@Immutable
private data class PhysicalScreenCornerShape(
    val delegate: ContinuousRoundedRectangle,
) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        delegate.createOutline(size, LayoutDirection.Ltr, density)
}

/** 追加同一套连续四角轮廓。 */
fun Path.addScreenCornerRect(width: Float, height: Float, corners: ScreenCornerRadiiPx) {
    val density = Density(1f)
    addOutline(
        corners.toShape(density).createOutline(
            androidx.compose.ui.geometry.Size(width, height),
            LayoutDirection.Ltr,
            density,
        )
    )
}
