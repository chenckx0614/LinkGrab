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
import com.linkgrab.app.ui.theme.HyperBackgroundDark
import com.linkgrab.app.ui.theme.HyperBackgroundLight
import com.linkgrab.app.ui.theme.HyperBlueDark
import com.linkgrab.app.ui.theme.HyperBlueLight
import com.linkgrab.app.ui.theme.HyperSecondarySurfaceDark
import com.linkgrab.app.ui.theme.HyperSecondarySurfaceLight
import com.linkgrab.app.ui.theme.HyperSurfaceDark
import com.linkgrab.app.ui.theme.HyperSurfaceLight
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
 * - TopAppBar：折叠态 52dp，标题 20sp Medium，导航图标 16dp 起始内边距
 * - Card：圆角 20dp 连续曲率，无描边、无投影，只靠底色分层
 * - 偏好行：最小高度 56dp，标题 17sp Medium，摘要 14sp，四边内边距 16dp
 * - 小标题（SmallTitle）：14sp Medium，#8F9CAE，自带 12dp 水平留白
 * - 下拉面板：圆角 25dp，行高 56dp，字号 15.6sp
 * - 圆角：全部走连续曲率（squircle），见 HyperShape.kt
 * - 点击：矩形叠色 10%，形状不缩放，见 HyperIndication.kt
 * - 主色：亮 #3482FF / 暗 #277AF7（miuix 官方色板）
 * - 弹簧：folmeSpring(damping, response)，stiffness = (2π / response)²
 * ========================================================= */

// MARK: - Motion

/** miuix 的 folmeSpring：用「阻尼比 + 响应时间」描述弹簧，比裸 stiffness 更好对齐。 */
fun <T> folmeSpring(damping: Float, response: Float): SpringSpec<T> {
    val omega = (2.0 * PI / response).toFloat()
    return spring(dampingRatio = damping, stiffness = omega * omega)
}

/** 点按反馈：miuix `SinkFeedback(0.94f, spring(0.8f, 600f))` 的原始参数。 */
val HyperPressSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 600f)

/** 按下时收缩到的比例，miuix 是 0.94（不是 0.96）。 */
const val HYPER_SINK_SCALE = 0.94f

/** 菜单/浮层展开：轻微过冲，落位快。 */
val HyperExpandSpring = spring<Float>(dampingRatio = 0.8f, stiffness = ((2 * PI / 0.32) * (2 * PI / 0.32)).toFloat())

// MARK: - Colors

@Composable
fun hyperPrimary(): Color = if (isInDarkTheme()) HyperBlueDark else HyperBlueLight

/** 页面底色：miuix surface，亮 #F4F4F4 / 暗纯黑。 */
@Composable
fun hyperBackground(): Color = if (isInDarkTheme()) HyperBackgroundDark else HyperBackgroundLight

/** 卡片底色：miuix surfaceContainer，亮白 / 暗 #242424。 */
@Composable
fun hyperSurface(): Color = if (isInDarkTheme()) HyperSurfaceDark else HyperSurfaceLight

/** 次级容器：按钮底、输入框底、按下的行。 */
@Composable
fun hyperSurfaceVariant(): Color =
    if (isInDarkTheme()) HyperSecondarySurfaceDark else HyperSecondarySurfaceLight

@Composable
fun hyperTitle(): Color = if (isInDarkTheme()) Color(0xE6FFFFFF) else Color.Black

/** 摘要文字：miuix 用 60% 黑 / 50% 白。 */
@Composable
fun hyperSummary(): Color = if (isInDarkTheme()) Color(0x80FFFFFF) else Color(0x99000000)

/** 分组小标题色：miuix SmallTitle 写死的 #8F9CAE，深浅主题同值。 */
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
    val RowVertical = 16.dp

    /** 顶栏图标按钮热区（miuix IconButton 约 40dp） */
    val IconButton = 40.dp

    val MenuWidth = 200.dp
    val MenuCorner = 25.dp
    val MenuItemHeight = 56.dp
}

// MARK: - Card

/**
 * miuix Card：圆角 20dp 连续曲率，无描边、无投影。白卡从 #F7F8FA 的页面底色
 * 上浮起来靠的是色差本身，加描边或投影都会立刻变成「Material 卡片」。
 */
@Composable
fun HyperCard(
    modifier: Modifier = Modifier,
    corner: Dp = HyperDimens.CardCorner,
    content: @Composable ColumnScope.() -> Unit,
) {
    // miuix 的 Card 就是一块连续曲率的纯色面：没有描边、没有投影。
    // 卡片和页面底色差本身就是分层，描边反而是 HyperOS 里不存在的东西。
    val shape = hyperSquircle(corner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(hyperSurface()),
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
 * miuix 偏好行（BasicComponent）：最小 56dp，标题 17sp Medium、摘要 14sp，
 * 四边 16dp 内边距。
 *
 * 按下**不缩放**：MIUI 的整行按压是叠一层 10% 前景色，形状和位置都不动
 * （见 HyperIndication）。之前写成整行缩 0.96 是错的，那是 iOS 的手法。
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
    val titleColor = hyperTitle()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HyperDimens.RowMinHeight)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = HyperIndication(titleColor),
                        role = Role.Button,
                        onClick = onClick,
                    )
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

// MARK: - Buttons

/** miuix 主按钮：蓝底白字，连续曲率圆角 16dp，最小高 40dp，17sp Medium。 */
@Composable
fun HyperPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val base = hyperPrimary()
    val shape = hyperSquircle(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (enabled) base else base.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = HyperIndication(Color.White),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            // miuix ButtonDefaults.InsideMargin = (16dp, 13dp)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 17.sp,
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
    val title = hyperTitle()
    val shape = hyperSquircle(16.dp)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(hyperSurfaceVariant())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = HyperIndication(title),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

