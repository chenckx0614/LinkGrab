package com.linkgrab.app.ui.hyper

import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.liquid.SystemMenuStyle
import com.linkgrab.app.ui.theme.isInDarkTheme

/**
 * HyperOS 的顶栏按钮与菜单。
 *
 * 参考 miuix 的 `IconButtonDefaults`：最小 40dp、圆角 40dp（正圆）、
 * 内容居中；颜色取 `surfaceContainer`（白 / #242424），没有描边——
 * HyperOS 的按钮靠底色和白卡分层，不画边框。
 *
 * 菜单是 miuix `ListPopup` 的规格：宽 200dp、连续曲率圆角 16dp（ListPopupContent
 * 的 cornerRadius）、行高 48dp、15.6sp Medium、无投影（系统靠底色差，这里留 6dp 极淡投影托住浮层）。
 */
@Composable
internal fun hyperMenuStyle(): SystemMenuStyle {
    val dark = isInDarkTheme()
    val panelShape = remember { hyperSquircle(16.dp) }
    val itemShape = remember { hyperSquircle(12.dp) }
    return remember(dark) {
        SystemMenuStyle(
            buttonSize = 40.dp,
            buttonShape = CircleShape,
            buttonBackground = if (dark) Color(0xFF242424) else Color.White,
            buttonBorder = Color.Transparent,
            iconSize = 24.dp,
            iconTint = if (dark) Color(0xE6FFFFFF) else Color(0xFF191919),
            pressTint = if (dark) Color(0x1AFFFFFF) else Color(0x14000000),
            panelWidth = 200.dp,
            panelShape = panelShape,
            panelBackground = if (dark) Color(0xFF242424) else Color.White,
            panelBorder = Color.Transparent,
            panelShadow = 6.dp,
            panelPadVertical = 6.dp,
            itemShape = itemShape,
            itemHeight = 48.dp,
            itemPadHorizontal = 6.dp,
            itemTextPadHorizontal = 14.dp,
            itemTextSize = 15.6.sp,
            itemTextWeight = FontWeight.Medium,
            itemTextColor = if (dark) Color(0xE6FFFFFF) else Color(0xFF191919),
            enter = spring(dampingRatio = 0.86f, stiffness = 520f, visibilityThreshold = 0.001f),
            exit = spring(dampingRatio = 1f, stiffness = 700f, visibilityThreshold = 0.001f),
        )
    }
}
