package com.linkgrab.app.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.onClick as onSemanticsClick

/**
 * 系统主题（HyperOS / ColorOS）的顶栏按钮与下拉菜单。
 *
 * 液态玻璃那套是 iOS 的材质语言：采样背景、模糊、描边、形变。换到系统主题时，
 * 按钮退回「一块实底圆钮 + 图标」，菜单退回「独立浮层从按钮下方弹出」——
 * 这也是两个系统真实的做法（miuix 与 coui 的 TopAppBar 都不管按钮外观，
 * 调用方传进去的就是各自系统的圆钮）。
 *
 * 交互仍沿用液态玻璃版的**跟手滑选**（[dropdownPanelDragSelect]）：手指按下后
 * 可以直接滑到某一项松手选中，入口、命中测试、触觉全部复用，只换皮。
 */

/** 按钮与面板之间的间距。 */
private val SystemMenuGap = 8.dp

/** miuix `ListPopupDefaults.SinOutEasing`：sin(π/2·t)。 */
private val SinOutEasing: Easing = Easing { fraction ->
    kotlin.math.sin((fraction * Math.PI / 2).toFloat())
}

/** HyperOS 菜单弹出：miuix `ListPopupDefaults.FractionAnimationSpec`。 */
private val HyperMenuFractionSpec = spring(dampingRatio = 0.82f, stiffness = 362.5f, visibilityThreshold = 0.0001f)

/** ColorOS 菜单出场缩放：coui `ListPopupDefaults.FractionAnimationSpec`（response 0.35 / bounce 0.2）。 */
private val CouiMenuEnterSpec = spring(dampingRatio = 0.8f, stiffness = 322.27f, visibilityThreshold = 0.0001f)

/** ColorOS 菜单退场缩放：coui `FractionExitAnimationSpec`（response 0.3 / bounce 0）。 */
private val CouiMenuExitScaleSpec = spring(dampingRatio = 1f, stiffness = 438.65f, visibilityThreshold = 0.0001f)

/** ColorOS 菜单退场 alpha（主控卸载时机）：coui `AlphaExitAnimationSpec`（response 0.25 / bounce 0）。 */
private val CouiMenuExitAlphaSpec = spring(dampingRatio = 1f, stiffness = 631.65f, visibilityThreshold = 0.0001f)

/** 一套系统菜单的视觉参数。 */
internal data class SystemMenuStyle(
    val buttonSize: Dp,
    val buttonShape: Shape,
    val buttonBackground: Color,
    val buttonBorder: Color,
    val iconSize: Dp,
    val iconTint: Color,
    val pressTint: Color,
    val panelWidth: Dp,
    val panelShape: Shape,
    val panelBackground: Color,
    val panelBorder: Color,
    val panelShadow: Dp,
    val panelPadVertical: Dp,
    val itemShape: Shape,
    val itemHeight: Dp,
    val itemPadHorizontal: Dp,
    val itemTextPadHorizontal: Dp,
    val itemTextSize: TextUnit,
    val itemTextWeight: FontWeight,
    val itemTextColor: Color,
    val enter: SpringSpec<Float>,
    val exit: SpringSpec<Float>,
)

/**
 * 系统主题的顶栏圆钮：实底、无模糊、按压缩小一点点。
 *
 * @param selected 用于菜单触发钮在展开时保持按下态（系统里按钮不会消失）
 */
@Composable
internal fun SystemBarButton(
    onClick: () -> Unit,
    style: SystemMenuStyle,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
    iconSize: Dp = style.iconSize,
    buttonSize: Dp = style.buttonSize,
    tint: Color = style.iconTint,
    iconOffset: DpOffset = DpOffset.Zero,
    /** 深色图片/播放器上要换成本身就要透的底，否则白钮白箭头互相看不见 */
    backgroundOverride: Color? = null,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 按压就是叠一层前景色（miuix / coui 都是这么做的），形状不动
    val baseBackground = backgroundOverride ?: style.buttonBackground
    val background by animateColorAsState(
        targetValue = if (pressed) style.pressTint.compositeOver(baseBackground)
        else baseBackground,
        animationSpec = spring(dampingRatio = 1f, stiffness = 900f),
        label = "systemBarButtonPress",
    )
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(style.buttonShape)
            .background(background)
            .then(
                if (style.buttonBorder.alpha > 0.001f) {
                    Modifier.border(1.dp, style.buttonBorder, style.buttonShape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize),
                tint = tint,
            )
        } else {
            // 返回键：与液态玻璃版同一条 chevron 路径，只换外壳
            BackChevron(
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .offset(x = iconOffset.x, y = iconOffset.y),
            )
        }
    }
}

/**
 * 独立浮层式下拉菜单：按钮常驻，面板从按钮下方弹出。
 *
 * 两套动效各自照搬原库：
 * - HyperOS（hyperReveal=true）＝ miuix `ListPopup`：spring(0.82, 362.5) 驱动
 *   「缩放 0.15→1 + 从锚点侧向下的裁剪揭示（面板形状的超椭圆带）」，alpha
 *   tween 200ms 入 / 150ms 出（alpha 是退场主控时机）。
 * - ColorOS ＝ coui `ListPopup`：真 0→1 缩放，进 spring(0.8, 322.27)（response
 *   0.35 / bounce 0.2）；退场不对称——缩放 spring(1, 438.65)（response 0.3 /
 *   bounce 0），alpha spring(1, 631.65)（response 0.25 / bounce 0）主控。
 *
 * 和液态玻璃版的区别是「不形变」——玻璃版是同一块玻璃从 42dp 长成 200dp，
 * 系统版是两颗独立的东西：按钮不动，菜单是另一个浮层。
 *
 * @param hyperReveal 当前是否为类 HyperOS 主题（决定走哪套弹出动效与是否裁剪揭示）
 */
@Composable
internal fun SystemDropdownMenu(
    show: Boolean,
    style: SystemMenuStyle,
    modifier: Modifier = Modifier,
    fraction: Animatable<Float, *> = remember { Animatable(0f) },
    hyperReveal: Boolean = false,
    onExpand: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    triggerIcon: ImageVector? = null,
    triggerIconSize: Dp = 22.dp,
    triggerContentDescription: String? = null,
    offsetPx: () -> Offset = { Offset.Zero },
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val dragSelectState = remember { DropdownPanelDragSelectState() }
    val menuAlpha = remember { Animatable(0f) }

    LaunchedEffect(show, hyperReveal) {
        if (show) {
            launch {
                fraction.animateTo(1f, if (hyperReveal) HyperMenuFractionSpec else CouiMenuEnterSpec)
            }
            if (hyperReveal) {
                menuAlpha.animateTo(1f, tween(durationMillis = 200))
            } else {
                menuAlpha.animateTo(1f, CouiMenuEnterSpec)
            }
        } else {
            launch {
                fraction.animateTo(0f, if (hyperReveal) HyperMenuFractionSpec else CouiMenuExitScaleSpec)
            }
            if (hyperReveal) {
                // alpha 是退场主控：150ms 淡出完就算收起
                menuAlpha.animateTo(0f, tween(durationMillis = 150))
            } else {
                menuAlpha.animateTo(0f, CouiMenuExitAlphaSpec)
            }
        }
    }

    // 外层就占「一颗按钮」的尺寸：面板用 requiredWidth 突破布局约束，
    // 展开时不会把顶栏标题挤走（液态玻璃版因为要形变，做不到这点）。
    Box(modifier = modifier.padding(LiquidGlassDropdownShadowPadding)) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(style.buttonSize),
        ) {
        val p = fraction.value
        val a = menuAlpha.value
        if (p > 0.002f || a > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = style.buttonSize + SystemMenuGap)
                    .requiredWidth(style.panelWidth)
                    .wrapContentHeight(unbounded = true)
                    .graphicsLayer {
                        alpha = a.coerceIn(0f, 1f)
                        // HyperOS：0.15→1 的大力缩放；ColorOS：真 0→1
                        val s = if (hyperReveal) 0.15f + 0.85f * p else p.coerceIn(0f, 1f)
                        scaleX = s
                        scaleY = s
                        // 从右上方（按钮所在角）长出来
                        transformOrigin = TransformOrigin(1f, 0f)
                        translationX = offsetPx().x
                        translationY = offsetPx().y
                    }
                    .shadow(style.panelShadow, style.panelShape, clip = false)
                    .clip(style.panelShape)
                    .background(style.panelBackground)
                    .then(
                        if (style.panelBorder.alpha > 0.001f) {
                            Modifier.border(0.5.dp, style.panelBorder, style.panelShape)
                        } else {
                            Modifier
                        },
                    )
                    // HyperOS 的方向性裁剪揭示：可见带从锚点侧（上缘）向下生长，
                    // 带本身用面板形状裁出，四个角始终跟着面板圆角走
                    .then(
                        if (hyperReveal) {
                            Modifier.panelClipReveal(style.panelShape) { fraction.value }
                        } else {
                            Modifier
                        },
                    )
                    // 菜单项靠它把 boundsInRoot 换算成面板局部坐标
                    .onGloballyPositioned { dragSelectState.panelTopInRoot = it.boundsInRoot().top }
                    .dropdownPanelDragSelect(
                        state = dragSelectState,
                        fraction = { fraction.value },
                        hapticFeedback = haptic,
                    ),
            ) {
                Column(Modifier.padding(vertical = style.panelPadVertical)) {
                    CompositionLocalProvider(LocalDropdownPanelDragSelect provides dragSelectState) {
                        content()
                    }
                }
            }
        }

        // 触发钮常驻：系统里点开菜单后按钮还在，不会像玻璃版那样化进面板。
        // 已展开时再点它就是收起（系统菜单也是这么响应的）。
        SystemBarButton(
            onClick = { if (show) onDismiss?.invoke() else onExpand?.invoke() },
            style = style,
            modifier = Modifier.align(Alignment.TopEnd),
            icon = triggerIcon,
            contentDescription = triggerContentDescription,
            iconSize = triggerIconSize,
            enabled = if (show) onDismiss != null else onExpand != null,
        )
        }
    }
}

/**
 * miuix `popupClipReveal` 的移植：可见带从上缘向下生长（本 app 菜单只在锚点下方弹出），
 * 带的形状用面板自己的 [shape] 在「宽 × 已见高度」上生成 outline 裁出，
 * 四个角随面板圆角走；spring 过冲部分钳到 1，防止揭示路径越裁越大。
 */
private fun Modifier.panelClipReveal(shape: Shape, fraction: () -> Float): Modifier =
    drawWithContent {
        val progress = fraction().coerceIn(0f, 1f)
        if (progress <= 0f) return@drawWithContent
        if (progress >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val bandHeight = size.height * progress
        val outline = shape.createOutline(
            Size(size.width, bandHeight),
            layoutDirection,
            this,
        )
        val bandPath = when (outline) {
            is Outline.Generic -> outline.path
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        }
        clipPath(bandPath) {
            this@drawWithContent.drawContent()
        }
    }

/**
 * HyperOS 菜单的背景压暗：miuix `ListPopup` 弹出时整窗盖一层 windowDimming
 * （亮 30% / 暗 60% 黑），tween 300ms 入 / 150ms 出，缓动 SinOutEasing。
 * ColorOS 的浮层不压暗（coui `OverlayListPopup` 默认 enableWindowDim=false），
 * 所以非 HyperOS 主题这里什么都不画。
 */
@Composable
internal fun HyperMenuDim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!com.linkgrab.app.ui.theme.isHyperOsStyle()) return
    val dark = isInDarkTheme()
    val dim = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            dim.animateTo(1f, tween(durationMillis = 300, easing = SinOutEasing))
        } else {
            dim.animateTo(0f, tween(durationMillis = 150, easing = SinOutEasing))
        }
    }
    if (dim.value > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim.value }
                .background(Color.Black.copy(alpha = if (dark) 0.6f else 0.3f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss,
                ),
        )
    }
}

/** 系统主题的菜单行：没有玻璃高光，按下就是一层实底。 */
@Composable
internal fun SystemMenuItem(
    text: String,
    onClick: () -> Unit,
    style: SystemMenuStyle,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = style.itemPadHorizontal)
            .clip(style.itemShape)
            // 登记纵向区间供面板命中测试 + 绘制跟手高亮
            .dropdownPanelEntry(enabled = true, action = onClick)
            .semantics {
                role = Role.Button
                onSemanticsClick {
                    onClick()
                    true
                }
            }
            .heightIn(min = style.itemHeight)
            .padding(horizontal = style.itemTextPadHorizontal),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.size(10.dp))
            }
            Text(
                text = text,
                fontSize = style.itemTextSize,
                fontWeight = style.itemTextWeight,
                color = style.itemTextColor,
            )
        }
    }
}

/**
 * 返回箭头：与 LiquidGlassTopBarButton 里那条 chevron 逐点一致
 * （24dp 方框内宽 10dp、高 17dp、笔画 2.2dp、整体左移 1dp），只是外壳换成实底圆钮。
 */
@Composable
private fun BackChevron(tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val unit = size.width / 24f
        val chevronW = 10f * unit
        val chevronH = 17f * unit
        val left = (size.width - chevronW) / 2f - 1f * unit
        val top = (size.height - chevronH) / 2f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(left + chevronW * 0.85f, top)
            lineTo(left + chevronW * 0.15f, top + chevronH / 2f)
            lineTo(left + chevronW * 0.85f, top + chevronH)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.2f * unit,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
