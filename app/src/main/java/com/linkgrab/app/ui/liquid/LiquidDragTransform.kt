package com.linkgrab.app.ui.liquid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * 按压形变数据：整体生长 + 沿拖动方向拉伸 + tanh 阻尼跟手位移。
 * 学习自 Nexio 课程表（gitee.com/com_haooz_account/hyper_schedule，AGPL-3.0）
 * 的 computeDragTransform（DropdownPanelDragSelect.kt），在其 backdrop 2.0.0
 * 依赖上重新实现。
 */
data class LiquidDragTransform(
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
)

/** 沿拖动方向的拉伸强度（每单位归一化位移的额外缩放）。 */
private const val DragStretchRatio = 0.045f

private const val AspectPenaltyFloor = 0.97f

/**
 * 竖向行程基准相对横向的缩放（<1 = 更容易吃满）。
 * 菜单面板只有几行高，竖向行程天然比横向短，用同一基准时竖拖明显迟钝。
 */
private const val VerticalDragRefScale = 0.6f

/**
 * 计算液态控件按压/拖动的形变。
 *
 * @param pressProgress 按压进度 0..1（生长：高 +4dp 的等比放大）
 * @param offset 手指相对按下点的当前位移
 * @param fraction 容器展开进度 0..1。菜单面板这类「收起是小圆、展开是高面板」
 *   的容器变换要传它，用来按进度收紧竖向行程基准；普通按钮传默认 0 即可
 *   （此时 refY == minDim，等价于旧行为）。
 * @param shapeAspectRatio 长宽比惩罚基准。接近方形的控件传 null（用实测宽高比）；
 *   天生长条（整宽按钮）传 1f，否则纵向形变会被宽高比惩罚砍没。
 */
fun computeLiquidDragTransform(
    width: Float,
    height: Float,
    pressProgress: Float,
    offset: Offset,
    density: Density,
    fraction: Float = 0f,
    shapeAspectRatio: Float? = null,
): LiquidDragTransform {
    val selfH = height.coerceAtLeast(1f)
    val minDim = min(width, selfH).coerceAtLeast(1f)
    val pressScale = 1f + with(density) { 4f.dp.toPx() } / selfH * pressProgress.coerceAtLeast(0f)
    val refY = minDim * (1f - (1f - VerticalDragRefScale) * fraction.coerceIn(0f, 1f))
    val penaltyX = (shapeAspectRatio ?: width / selfH).coerceAtMost(AspectPenaltyFloor)
    val penaltyY = (shapeAspectRatio?.let { 1f / it } ?: (selfH / width)).coerceAtMost(AspectPenaltyFloor)
    val angle = atan2(offset.y, offset.x)
    // 起始跟手斜率 0.08：位移跟手但大幅阻尼，越拖越「黏」，不会飞出去
    return LiquidDragTransform(
        scaleX = pressScale + DragStretchRatio * abs(cos(angle) * offset.x / minDim) * penaltyX,
        scaleY = pressScale + DragStretchRatio * abs(sin(angle) * offset.y / refY) * penaltyY,
        translationX = minDim * tanh(0.08f * offset.x / minDim),
        translationY = minDim * tanh(0.08f * offset.y / minDim),
    )
}
