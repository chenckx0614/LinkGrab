package com.linkgrab.app.ui.hyper

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * 连续曲率圆角（squircle），照抄 hyper_schedule fork 后 miuix 用的那套几何。
 *
 * 上游 miuix 0.9.3 用的是 `miuix-squircle`，这个 fork 把它整体换成了
 * `com.kyant.capsule` 的 G2 连续曲率，所有卡片 / 按钮 / 面板都走这一套，
 * 于是圆角和直边之间不再有「曲率突变」的接缝——这正是 MIUI 卡片看起来
 * 比 RoundedCornerShape 更软的原因。
 *
 * 参数来自 `G2ContinuityProfile.RoundedRectangle`：
 * - 曲率过渡区是半径的 1.5287 倍（extendedFraction 0.5287）
 * - 90° 拐角里圆弧只占 5/9，剩下两边各由一段三次贝塞尔收进直边
 * - 圆弧半径缩到 0.9318 倍，圆心往角内挪，保证 G2 连续
 *
 * 四角同半径，所以每角都是同一个形状的镜像，下面按顺时针把四个角写全。
 */
class HyperSquircleShape(
    private val cornerRadius: Dp,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        // 圆角吃满半高（图标圆钮、胶囊）：连续曲率退化成标准圆，直接交给圆形。
        val requested = with(density) { cornerRadius.toPx() }
        val half = min(w, h) * 0.5f
        if (requested >= half - 0.5f) {
            return RoundedCornerShape(50).createOutline(size, layoutDirection, density)
        }

        // 过渡区长度是 1.5287 倍半径，两个角各占一侧，不能加起来超过短边的一半
        val r = min(requested, half / EXTENDED_TOTAL)
        if (r <= 0.5f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val path = Path()
        val e = EXTENDED_TOTAL * r      // 曲率过渡区起点到角点的距离
        val a1y = 1.0758099f * r        // 贝塞尔第一控制点
        val a1x = 0.0761790f * r        // 贝塞尔终点（圆弧起点）
        val a1yy = 0.6330779f * r
        val b1x = 0.8423731f * r        // 圆弧终点之后的第一控制点
        val b2x = 1.0758099f * r
        val arcC = 0.9517684f * r       // 圆弧圆心（相对角点）
        val arcR = 0.9317906f * r

        // 左上：竖边 (0, e) → 顶边 (e, 0)
        path.moveTo(0f, e)
        path.cubicTo(0f, a1y, 0f, r, a1x, a1yy)
        path.arcTo(Rect(arcC - arcR, arcC - arcR, arcC + arcR, arcC + arcR), 200f, 50f, false)
        path.cubicTo(b1x, 0f, b2x, 0f, e, 0f)

        // 右上：顶边 (w - e, 0) → 竖边 (w, e)
        path.lineTo(w - e, 0f)
        path.cubicTo(w - b2x, 0f, w - b1x, 0f, w - a1x, a1yy)
        path.arcTo(
            Rect(w - arcC - arcR, arcC - arcR, w - arcC + arcR, arcC + arcR),
            290f, 50f, false,
        )
        path.cubicTo(w, r, w, a1y, w, e)

        // 右下：竖边 (w, h - e) → 底边 (w - e, h)
        path.lineTo(w, h - e)
        path.cubicTo(w, h - a1y, w, h - r, w - a1x, h - a1yy)
        path.arcTo(
            Rect(w - arcC - arcR, h - arcC - arcR, w - arcC + arcR, h - arcC + arcR),
            20f, 50f, false,
        )
        path.cubicTo(w - b1x, h, w - b2x, h, w - e, h)

        // 左下：底边 (e, h) → 竖边 (0, h - e)
        path.lineTo(e, h)
        path.cubicTo(b2x, h, b1x, h, a1x, h - a1yy)
        path.arcTo(
            Rect(arcC - arcR, h - arcC - arcR, arcC + arcR, h - arcC + arcR),
            110f, 50f, false,
        )
        path.cubicTo(0f, h - r, 0f, h - a1y, 0f, h - e)

        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is HyperSquircleShape && other.cornerRadius == cornerRadius)

    override fun hashCode(): Int = cornerRadius.hashCode()

    private companion object {
        /** extendedFraction + 1，见 G2ContinuityProfile.RoundedRectangle。 */
        const val EXTENDED_TOTAL = 1.5286651f
    }
}

/** 连续曲率圆角的便捷入口，替换 `RoundedCornerShape(dp)` 用。 */
fun hyperSquircle(corner: Dp = 20.dp): Shape = HyperSquircleShape(corner)
