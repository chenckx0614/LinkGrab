package com.linkgrab.app.ui.coloros

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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

/**
 * 凝光环境光：页面顶部落一层极淡的主色，越往下越散。
 *
 * ColorOS 的界面不是纯平色块，顶部总有一点"光从上面打下来"的感觉。
 * 亮度压得很低（暗 10% / 亮 6%），只在大面积留白和卡片边缘看得出来。
 *
 * 用法：接在页面底色之后、内容之前——`background(color).couiAmbientGlow(dark)`。
 */
fun Modifier.couiAmbientGlow(dark: Boolean): Modifier = drawBehind {
    val tint = if (dark) Color(0xFF247CFF) else Color(0xFF0066FF)
    val alpha = if (dark) 0.10f else 0.06f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(tint.copy(alpha = alpha), Color.Transparent),
            center = Offset(size.width * 0.5f, -size.height * 0.08f),
            radius = size.width * 1.15f,
        ),
    )
}
