package com.linkgrab.app.ui.coloros

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ColorOS 17 的圆角形状：**Lp 超椭圆**，不是圆角矩形，也不是贝塞尔连续曲率。
 *
 * 曲线方程 `|x/r|^w + |y/r|^w = 1`，w 就是 [cornerWeight]。w=2 退化成正圆，
 * w=2.5 是 ColorOS「凝光」那档观感——直边和圆角的过渡比正圆更平缓，比 iOS 的
 * 连续曲率更"方"一些。
 *
 * 采样方式（照 COUI-Kit `CouiShape` 的做法）：半角参数取 `a ∈ [0, 0.5^(1/w)]`，
 * 对应 `b = (1 - a^w)^(1/w)`；`a = b = 0.5^(1/w)` 正好是 45° 点，把半角拆成两段。
 * 用折线逼近，分段数按"相对原曲线误差 ≤ 0.05px"自动算，36px 半径约 15 段/半角。
 */
@Immutable
class CouiShape(
    private val radius: Dp,
    private val cornerWeight: Float = CouiCornerWeight,
) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val minSide = size.minDimension
        if (minSide <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        val r = (radius.value * density.density).coerceIn(0f, minSide / 2f)
        if (r <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))
        return Outline.Generic(buildPath(size.width, size.height, r, cornerWeight))
    }

    override fun equals(other: Any?): Boolean =
        other is CouiShape && other.radius == radius && other.cornerWeight == cornerWeight

    override fun hashCode(): Int = 31 * radius.hashCode() + cornerWeight.hashCode()

    override fun toString(): String = "CouiShape(radius=$radius, weight=$cornerWeight)"
}

/** ColorOS 默认圆角权重。2.5 是系统值，小于 2 会被拒（和轮廓光 shader 共用边界）。 */
const val CouiCornerWeight = 2.5f

/** 误差上限（像素）：折线相对超椭圆真值的最大偏差。0.05 在 3x 屏上看不出折角。 */
private const val MAX_SAGITTA_PX = 0.05

private fun buildPath(width: Float, height: Float, r: Float, weight: Float): Path {
    val w = weight.toDouble()
    val diagonal = 0.5.pow(1.0 / w)
    // 单段弦长 h 时，圆弧塌陷量约 h²/(8r)；超椭圆用 (w-1) 放大，故分段数取 sqrt 形式。
    val segments = ceil(
        sqrt(r * (w - 1.0) * diagonal / (4.0 * MAX_SAGITTA_PX)),
    ).toInt().coerceAtLeast(1)
    val halfCorner = DoubleArray(segments + 1) { index ->
        val a = diagonal * index / segments
        (1.0 - a.pow(w)).pow(1.0 / w)
    }
    val path = Path()
    path.moveTo(r, 0f)
    for (corner in 0 until 4) {
        for (index in 0..segments * 2) {
            val firstHalf = index <= segments
            val halfIndex = if (firstHalf) index else segments * 2 - index
            val a = (diagonal * halfIndex / segments * r).toFloat()
            val b = (halfCorner[halfIndex] * r).toFloat()
            val x = if (firstHalf) a else b
            val y = if (firstHalf) b else a
            when (corner) {
                0 -> path.lineTo(width - r + x, r - y)
                1 -> path.lineTo(width - r + y, height - r + x)
                2 -> path.lineTo(r - x, height - r + y)
                else -> path.lineTo(r - y, r - x)
            }
        }
    }
    path.close()
    return path
}

/** 统一入口，和 `hyperSquircle(radius)` 对称。 */
@androidx.compose.runtime.Composable
fun couiSquircle(radius: Dp, cornerWeight: Float = CouiCornerWeight): Shape =
    CouiShape(radius, cornerWeight)

/**
 * ColorOS 圆角档位。比 HyperOS 那套（26/20/16/12）小一档：
 * 卡片 12dp、按钮 22dp（44dp 高的胶囊）、弹层 12dp、小件 8dp。
 */
object CouiCorners {
    /** 大卡片 / 模态（coui 的 Dialog 一档）。 */
    val Large = 18.dp

    /** 常规卡片：coui CardDefaults.CornerRadius = 12dp。 */
    val Card = 12.dp

    /** 行内小卡片 / 缩略图。 */
    val Small = 10.dp

    /** 标签、小色块。 */
    val XSmall = 8.dp

    /** 主按钮：coui ButtonDefaults 高 44dp、圆角 22dp，即胶囊。 */
    val Button = 22.dp
}
