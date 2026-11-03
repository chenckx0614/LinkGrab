package com.linkgrab.app.ui.coloros

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.isInDarkTheme

// MARK: - Colors
//
// 取值全部来自 suqi8/coui `theme/Colors.kt`（ColorOS 16 默认色板）。
// 注意深色卡片是 **半透明白**（#1AFFFFFF）叠在纯黑页面上，不是不透明灰：
// 这样层级靠透明度叠出来，嵌套时不会一层比一层死。

@Composable
fun couiPrimary(): Color = if (isInDarkTheme()) Color(0xFF247CFF) else Color(0xFF0066FF)

/** 页面底：surface，亮 #F0F1F2 / 暗纯黑。 */
@Composable
fun couiBackground(): Color = if (isInDarkTheme()) Color.Black else Color(0xFFF0F1F2)

/** 卡片：surfaceContainer，亮白 / 暗 10% 白。 */
@Composable
fun couiSurface(): Color = if (isInDarkTheme()) Color(0x1AFFFFFF) else Color.White

/** 按下态：surfaceContainerHigh，亮 #E6E6E6 / 暗 20% 白。 */
@Composable
fun couiSurfaceHigh(): Color = if (isInDarkTheme()) Color(0x33FFFFFF) else Color(0xFFE6E6E6)

/** 次级容器（输入框底、分组底、未选中标签）：secondaryContainer，亮 8% 黑 / 暗 15% 白。 */
@Composable
fun couiSurfaceVariant(): Color = if (isInDarkTheme()) Color(0x26FFFFFF) else Color(0x14000000)

@Composable
fun couiTitle(): Color = if (isInDarkTheme()) Color(0xE6FFFFFF) else Color(0xE6000000)

/** 摘要：onSurfaceVariant，亮 54% 黑 / 暗 54% 白。 */
@Composable
fun couiSummary(): Color = if (isInDarkTheme()) Color(0x8AFFFFFF) else Color(0x8A000000)

/** 更弱的一档：onSurfaceVariantSummary，亮 40% 黑 / 暗 40% 白。 */
@Composable
fun couiTertiary(): Color = if (isInDarkTheme()) Color(0x66FFFFFF) else Color(0x66000000)

/** 分组小标题：coui SmallTitle 用的是 onBackgroundVariant，和摘要同色。 */
@Composable
fun couiSectionColor(): Color = if (isInDarkTheme()) Color(0x8AFFFFFF) else Color(0x8A000000)

@Composable
fun couiDivider(): Color = if (isInDarkTheme()) Color(0x33FFFFFF) else Color(0x1F000000)

@Composable
fun couiError(): Color = if (isInDarkTheme()) Color(0xFFFF6C61) else Color(0xFFDB382C)

// MARK: - Dimens

object CouiDimens {
    /** coui TopAppBar 折叠态高度。 */
    val TopBarHeight = 52.dp

    /** 页面水平留白。 */
    val PageHorizontal = 16.dp

    /** 卡片圆角：coui CardDefaults.CornerRadius = 12dp。 */
    val CardCorner = 12.dp

    /** 卡片内边距。 */
    val CardInner = 16.dp

    /** 列表行最小高度：coui BasicComponentDefaults.MinHeight = 48dp（比 miuix 的 56 紧凑）。 */
    val RowMinHeight = 48.dp
    val RowHorizontal = 16.dp
    val RowVertical = 10.dp

    /** 主按钮：高 44dp，圆角 22dp（胶囊）。 */
    val ButtonHeight = 44.dp
    val ButtonCorner = 22.dp
    val ButtonHorizontal = 12.dp

    /** 下拉面板：coui ListPopup 宽 178~232、圆角 12dp。 */
    val MenuWidth = 210.dp
    val MenuCorner = 12.dp
    val MenuItemHeight = 44.dp

    /** 分组小标题上下的呼吸。 */
    val SectionTop = 12.dp
    val SectionBottom = 6.dp
}

// MARK: - Card

/**
 * ColorOS 卡片：超椭圆 12dp、无投影，深色下顶边有一条极淡的白色高光。
 *
 * 按压走**叠色**（coui `PressFeedbackType.Tint`，默认值），填充色向
 * [couiSurfaceHigh] 过渡，形状不缩放——这点和 miuix 一样，和 COUI-Kit 的
 * 按面积缩放不同。缩放留给按钮，见 [Modifier.couiPressScale]。
 */
@Composable
fun CouiCard(
    modifier: Modifier = Modifier,
    corner: Dp = CouiDimens.CardCorner,
    contour: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = couiSquircle(corner)
    val dark = isInDarkTheme()
    Column(
        modifier = modifier
            .clip(shape)
            .background(couiSurface())
            .then(
                if (contour != null) {
                    Modifier.couiContour(shape = shape, color = contour, width = 1.dp)
                } else if (dark) {
                    // 深色下卡片是半透明白，没有描边就是一坨透明，加一条顶边高光立住
                    Modifier.couiContour(
                        shape = shape,
                        color = Color.White.copy(alpha = 0.08f),
                        width = 1.dp,
                    )
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

// MARK: - Section title

/** coui SmallTitle：12sp Medium、onBackgroundVariant、页面 16dp + 12dp 缩进。 */
@Composable
fun CouiSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(
            start = 12.dp,
            top = CouiDimens.SectionTop,
            bottom = CouiDimens.SectionBottom,
        ),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = couiSectionColor(),
        letterSpacing = 0.sp,
    )
}

// MARK: - Row

/**
 * coui 列表行：最小 48dp、标题 17sp Medium、摘要 14sp、四边 16/10。
 *
 * 按压叠色：在行底下垫一层 [couiSurfaceHigh] 的渐显，用 `CouiMotion.fadeIn/Out`
 * 那对弹簧。不做缩放——列表行缩放会让整块版面跟着抖。
 */
@Composable
fun CouiRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDisclosure: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed) CouiMotion.fadeIn() else CouiMotion.fadeOut(),
        label = "couiRowTint",
    )
    val titleColor = couiTitle()
    val tintColor = couiSurfaceHigh()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CouiDimens.RowMinHeight)
            .background(tintColor.copy(alpha = tintColor.alpha * tint))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = CouiDimens.RowHorizontal, vertical = CouiDimens.RowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = titleColor,
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 14.sp,
                    color = couiSummary(),
                    letterSpacing = 0.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
        if (showDisclosure) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
                tint = couiTertiary(),
            )
        }
    }
}

// MARK: - Button

/** coui 主按钮的圆角：44dp 高配 22dp 半径，正好是胶囊。 */
@Composable
fun couiButtonShape(height: Dp = CouiDimens.ButtonHeight): Shape =
    RoundedCornerShape(height / 2)

// MARK: - Buttons

/**
 * coui 主按钮：蓝底白字、高 44dp、胶囊、左右 12dp，字 16sp Medium。
 *
 * 按压走 ColorOS 17 的按面积缩放（[Modifier.couiPressScale]）——同一个库的
 * 卡片是叠色、按钮是缩放，两代行为并存，这里各归各位。
 */
@Composable
fun CouiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = couiButtonShape()
    val base = if (enabled) couiPrimary() else couiPrimary().copy(alpha = 0.30f)
    Box(
        modifier = modifier
            .couiPressScale(interaction, enabled)
            .clip(shape)
            .background(base)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = CouiDimens.ButtonHorizontal, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}

/** coui 次要按钮：8% 底 + 主色字，同样 44dp 胶囊。 */
@Composable
fun CouiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val shape = couiButtonShape()
    Box(
        modifier = modifier
            .couiPressScale(interaction, enabled)
            .clip(shape)
            .background(couiSurfaceVariant())
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = CouiDimens.ButtonHorizontal, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) couiPrimary() else couiPrimary().copy(alpha = 0.4f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}
