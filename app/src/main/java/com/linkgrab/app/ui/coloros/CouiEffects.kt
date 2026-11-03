package com.linkgrab.app.ui.coloros

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 「凝光」轮廓光：沿形状边界画一圈内描边，左上角最亮、右下角几乎透明。
 *
 * ColorOS 17 的原版（`CouiContour`）用着色器把轮廓光做成跟随、可呼吸的；
 * 这里退到 Compose 能做的程度——一条按 135° 走的渐变描边。选中态给主色，
 * 普通卡片给一层极淡的白，深色下卡片才立得起来。
 *
 * 描边落在 shape 内侧（内容已经 clip 过），不会把布局撑大 1px。
 */
fun Modifier.couiContour(
    shape: Shape,
    color: Color = Color.White,
    width: Dp = 1.dp,
    enabled: Boolean = true,
): Modifier = if (!enabled) this else drawWithContent {
    drawContent()
    if (color.alpha <= 0.001f) return@drawWithContent
    val path = shape.createOutline(size, layoutDirection, this).toPath()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                color,
                color.copy(alpha = color.alpha * 0.12f),
                color.copy(alpha = color.alpha * 0.55f),
            ),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = width.toPx()),
    )
}

/** 超椭圆 / 圆角矩形 / 直角，统一转 Path，供描边使用。 */
internal fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Rectangle -> Path().apply { addRect(rect) }
}
