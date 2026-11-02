package com.linkgrab.app.ui.hyper

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlin.math.PI

/* =========================================================
 * HyperOS (MIUI) design layer
 *
 * 规格来源：gitee.com/com_haooz_account/hyper_schedule 的 miuix 组件库
 * （top.yukonga.miuix.kmp，Apache-2.0）。这里不引入该依赖（它按 Compose
 * 1.12 / Kotlin 2.4 构建，与本项目的 BOM 2025.06 / Kotlin 2.3 二进制
 * 不兼容），而是把它真正的度量、配色、弹簧参数照搬过来自绘：
 *
 * - TopAppBar：折叠态 52dp，标题 17sp Medium，导航图标 16dp 起始内边距
 * - Card：圆角 20dp，无描边，浅色靠 1dp 极淡投影分层
 * - 偏好行：最小高度 56dp，标题 16sp，摘要 13sp，左右内边距 16dp
 * - 小标题（SmallTitle）：14sp Medium，#8F9CAE
 * - 主色：亮 #3482FF / 暗 #277AF7（miuix 官方色板）
 * - 弹簧：folmeSpring(damping, response)，stiffness = (2π / response)²
 * ========================================================= */

// MARK: - Motion

/** miuix 的 folmeSpring：用「阻尼比 + 响应时间」描述弹簧，比裸 stiffness 更好对齐。 */
fun <T> folmeSpring(damping: Float, response: Float): SpringSpec<T> {
    val omega = (2.0 * PI / response).toFloat()
    return spring(dampingRatio = damping, stiffness = omega * omega)
}

/** 点按反馈：miuix SinkFeedback 的收缩量级（约 4%），到位干脆不回弹。 */
val HyperPressSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 1200f)

/** 菜单/浮层展开：轻微过冲，落位快。 */
val HyperExpandSpring = spring<Float>(dampingRatio = 0.8f, stiffness = ((2 * PI / 0.32) * (2 * PI / 0.32)).toFloat())

// MARK: - Colors

@Composable
fun hyperPrimary(): Color = if (isInDarkTheme()) Color(0xFF277AF7) else Color(0xFF3482FF)

/** 页面底色：HyperOS 设置页的浅灰 / 深色 #191919。 */
@Composable
fun hyperBackground(): Color = if (isInDarkTheme()) Color(0xFF191919) else Color(0xFFF7F8FA)

/** 卡片底色。 */
@Composable
fun hyperSurface(): Color = if (isInDarkTheme()) Color(0xFF242424) else Color.White

/** 次级容器：按钮底、输入框底、按下的行。 */
@Composable
fun hyperSurfaceVariant(): Color = if (isInDarkTheme()) Color(0xFF303030) else Color(0xFFF0F0F0)

@Composable
fun hyperTitle(): Color = if (isInDarkTheme()) Color(0xE6FFFFFF) else Color(0xFF191919)

/** 摘要文字：miuix 用 60% 黑 / 50% 白。 */
@Composable
fun hyperSummary(): Color = if (isInDarkTheme()) Color(0x80FFFFFF) else Color(0x99000000)

/** 分组小标题色，miuix SmallTitle 的固定值。 */
@Composable
fun hyperSectionColor(): Color = Color(0xFF8F9CAE)

@Composable
fun hyperDivider(): Color = if (isInDarkTheme()) Color(0xFF393939) else Color(0xFFE0E0E0)

@Composable
fun hyperError(): Color = if (isInDarkTheme()) Color(0xFFF12522) else Color(0xFFE94634)

// MARK: - Dimens

object HyperDimens {
    /** miuix TopAppBar 折叠态高度 */
    val TopBarHeight = 52.dp

    /** 列表/页面的水平留白：MIUI 设置页是 16dp */
    val PageHorizontal = 16.dp

    /** 大标题到首张卡片的间距 */
    val PageHeaderBottom = 12.dp

    val CardCorner = 20.dp

    /** 偏好行最小高度 */
    val RowMinHeight = 56.dp
    val RowHorizontal = 16.dp
    val RowVertical = 12.dp

    /** 顶栏图标按钮热区（miuix IconButton 约 40dp） */
    val IconButton = 40.dp

    val MenuWidth = 208.dp
    val MenuCorner = 20.dp
    val MenuItemHeight = 48.dp
}

// MARK: - Card

/**
 * miuix Card：圆角 20dp，无描边。浅色模式下用 1dp 极淡投影把白卡从
 * #F7F8FA 背景上提起来（HyperOS 真实卡片几乎看不到阴影，只靠色差）。
 */
@Composable
fun HyperCard(
    modifier: Modifier = Modifier,
    corner: Dp = HyperDimens.CardCorner,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val surface = hyperSurface()
    val dark = isInDarkTheme()
    Column(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                // 深色下补 1px 亮描边分层，浅色靠底色差，都不加投影
                if (dark) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.06f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                    )
                }
            }
            .background(surface),
        content = content,
    )
}

// MARK: - Section title

/** miuix SmallTitle：14sp Medium，#8F9CAE，左留白 28dp（页面 16dp + 12dp）。 */
@Composable
fun HyperSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = hyperSectionColor(),
        letterSpacing = 0.sp,
    )
}

// MARK: - Rows

/**
 * miuix 偏好行：最小 56dp，标题 16sp、摘要 13sp，右侧跟控件或箭头。
 * 按压时整行做 4% 收缩 + 底色加深（SinkFeedback），不给涟漪。
 */
@Composable
fun HyperRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDisclosure: Boolean = false,
    trailing: @Composable () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null) 0.96f else 1f,
        animationSpec = HyperPressSpring,
        label = "hyperRowScale",
    )
    val pressedTint by animateColorAsState(
        targetValue = if (pressed && onClick != null) hyperSurfaceVariant() else Color.Transparent,
        animationSpec = tween(120),
        label = "hyperRowTint",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperDimens.RowMinHeight)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(pressedTint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = source, indication = null, role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = HyperDimens.RowHorizontal, vertical = HyperDimens.RowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = hyperTitle(),
                letterSpacing = 0.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(top = 2.dp),
                    fontSize = 13.sp,
                    color = hyperSummary(),
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
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(20.dp),
                tint = hyperSummary(),
            )
        }
    }
}

/** 卡片内的 0.5dp 分隔线，左侧从文字起始位置开始（miuix 的 inset divider）。 */
@Composable
fun HyperDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = HyperDimens.RowHorizontal)
            .height(0.5.dp)
            .background(hyperDivider()),
    )
}

// MARK: - Icon button

/**
 * 顶栏图标按钮（返回 / 更多 / 搜索共用）：40dp 热区，24dp 图标，
 * 按下时缩到 0.92 并浮出一层 6% 中性底，松手弹回。
 * 与 iOS 主题的液态玻璃圆钮刻意区分：HyperOS 顶栏按钮没有玻璃、没有描边。
 */
@Composable
fun HyperIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = hyperTitle(),
    iconSize: Dp = 24.dp,
    buttonSize: Dp = HyperDimens.IconButton,
    enabled: Boolean = true,
    haptic: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = HyperPressSpring,
        label = "hyperIconScale",
    )
    val pressedBg by animateColorAsState(
        targetValue = if (pressed) hyperTitle().copy(alpha = 0.06f) else Color.Transparent,
        animationSpec = tween(120),
        label = "hyperIconBg",
    )
    val hapticFeedback = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(pressedBg)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/** 返回按钮：HyperOS 用纯箭头，不放圆底。 */
@Composable
fun HyperBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = hyperTitle(),
) {
    HyperIconButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        onClick = onClick,
        contentDescription = "返回",
        modifier = modifier,
        tint = tint,
        enabled = enabled,
    )
}

// MARK: - Top bar

/**
 * HyperOS 吸顶栏。
 *
 * 与 iOS 版的差别是刻意的：
 * - 没有液态玻璃、没有折射、没有大范围渐隐条；
 * - 滚动到位后是一块 92% 不透明的 surface 底 + 内容模糊，底部一条 0.5dp 分隔线；
 * - 标题只在滚动后淡入（顶部时大标题还在页面里），并带 4dp 上浮；
 * - 返回在左、操作在右，热区 40dp，图标 24dp。
 */
@Composable
fun HyperTopBar(
    title: String,
    backdrop: Backdrop?,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(200),
        label = "hyperTopBarProgress",
    )
    val surface = hyperSurface()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + HyperDimens.TopBarHeight),
    ) {
        // 底色层：模糊 + 92% surface。不开 blur 时也不至于露出内容。
        Box(
            Modifier
                .fillMaxWidth()
                .height(topInset + HyperDimens.TopBarHeight)
                .graphicsLayer { alpha = progress }
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { androidx.compose.ui.graphics.RectangleShape },
                            highlight = { null },
                            shadow = { null },
                            // effects / onDrawSurface 的 lambda 不能引用每帧变化的值：
                            // drawBackdrop 按引用比较，换引用会整块重录采样层。可见性
                            // 统一交给外面那层 graphicsLayer(alpha = progress)。
                            effects = { blur(16f.dp.toPx()) },
                            onDrawSurface = { drawRect(surface.copy(alpha = 0.92f)) },
                        )
                    } else {
                        Modifier.background(surface.copy(alpha = 0.92f * progress))
                    },
                ),
        )
        // 底部发丝线：随进度淡入，滚动后把顶栏和内容分开
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(0.5.dp)
                .graphicsLayer { alpha = progress }
                .background(hyperDivider()),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(HyperDimens.TopBarHeight)
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (onBack != null) {
                HyperBackButton(onClick = onBack)
            } else {
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onBack != null) 4.dp else 10.dp)
                    .graphicsLayer {
                        alpha = progress
                        translationY = (1f - progress) * 4.dp.toPx()
                    },
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = hyperTitle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            actions()
        }
    }
}

// MARK: - Buttons

/** miuix 主按钮：蓝底白字，圆角 16dp，最小高 40dp。 */
@Composable
fun HyperPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = HyperPressSpring,
        label = "hyperBtnScale",
    )
    val haptic = LocalHapticFeedback.current
    val base = hyperPrimary()
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) base else base.copy(alpha = 0.35f))
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** miuix 次级按钮：浅灰底深字，同尺寸。 */
@Composable
fun HyperSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = HyperPressSpring,
        label = "hyperSecondBtnScale",
    )
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(hyperSurfaceVariant())
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = hyperTitle(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Dropdown menu

/** 供菜单项复用：HyperOS 面板里的项点击后要先收起面板，再执行动作。 */
val LocalHyperMenuSelect = staticCompositionLocalOf<((() -> Unit) -> Unit)?> { null }

/**
 * HyperOS「更多」菜单：右上角三点图标钮 + 从按钮右上方缩放展开的白卡。
 *
 * 与 iOS 版容器变换（42dp 圆钮长成 200dp 面板）不同，这里是两条独立的东西：
 * 按钮一直在，面板浮在它下方，scale 0.86→1 + alpha 0→1，锚点在右上角，
 * 收起用 140ms 淡出，不做回弹。
 */
@Composable
fun HyperMenuTrigger(
    show: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    triggerIcon: ImageVector = Icons.Default.MoreVert,
    triggerContentDescription: String? = null,
    onExpand: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(show) {
        if (show) {
            progress.animateTo(1f, HyperExpandSpring)
        } else {
            progress.animateTo(0f, tween(140))
        }
    }
    val p = progress.value
    Box(modifier) {
        HyperIconButton(
            icon = triggerIcon,
            onClick = { if (show) onDismiss() else onExpand?.invoke() },
            contentDescription = triggerContentDescription,
        )
        if (p > 0.001f) {
            CompositionLocalProvider(
                LocalHyperMenuSelect provides { action ->
                    onDismiss()
                    action()
                },
            ) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = 46.dp + ((1f - p) * 6).dp)
                        .width(HyperDimens.MenuWidth)
                        .graphicsLayer {
                            alpha = p
                            scaleX = 0.86f + 0.14f * p
                            scaleY = 0.86f + 0.14f * p
                            transformOrigin = TransformOrigin(1f, 0f)
                        }
                        .clip(RoundedCornerShape(HyperDimens.MenuCorner))
                        .background(hyperSurface())
                        .padding(vertical = 8.dp),
                    content = content,
                )
            }
        }
    }
}

/** 菜单项：48dp 行高，左图标右文字，点击后由面板收起。 */
@Composable
fun HyperMenuRow(
    text: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HyperDimens.MenuItemHeight)
            .clickable(role = Role.Button) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = hyperTitle(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
