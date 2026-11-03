package com.linkgrab.app.ui.coloros

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
 * ColorOS 的顶栏按钮与菜单。
 *
 * 参考 coui 的 `IconButtonDefaults`（最小 48dp、超椭圆，这里收到 44dp 以适配 52dp 的顶栏）
 * 与 `secondaryContainer`（8% 黑 / 15% 白）——ColorOS 的按钮是"半透明材质"，
 * 底下永远透一点页面颜色，所以给一层极淡描边把轮廓勾出来（凝光）。
 *
 * 菜单取 coui `ListPopup`：宽 178~232（这里 210）、圆角 12dp、行高 44dp、16sp。
 * 浮层底色用不透明的 surface，不用半透明——面板叠在内容上，半透明会脏。
 */
@Composable
internal fun couiMenuStyle(): SystemMenuStyle {
    val dark = isInDarkTheme()
    val panelShape = couiSquircle(12.dp)
    val itemShape = couiSquircle(8.dp)
    return remember(dark) {
        SystemMenuStyle(
            buttonSize = 44.dp,
            buttonShape = CircleShape,
            buttonBackground = if (dark) Color(0x26FFFFFF) else Color(0x14000000),
            buttonBorder = if (dark) Color(0x1FFFFFFF) else Color(0x1A000000),
            iconSize = 24.dp,
            iconTint = if (dark) Color(0xE6FFFFFF) else Color(0xE6000000),
            pressTint = if (dark) Color(0x33FFFFFF) else Color(0x1F000000),
            panelWidth = 210.dp,
            panelShape = panelShape,
            panelBackground = if (dark) Color(0xFF333333) else Color.White,
            panelBorder = if (dark) Color(0x1AFFFFFF) else Color(0x0D000000),
            panelShadow = 12.dp,
            panelPadVertical = 6.dp,
            itemShape = itemShape,
            itemHeight = 44.dp,
            itemPadHorizontal = 6.dp,
            itemTextPadHorizontal = 12.dp,
            itemTextSize = 16.sp,
            itemTextWeight = FontWeight.Normal,
            itemTextColor = if (dark) Color(0xE6FFFFFF) else Color(0xE6000000),
            enter = spring(dampingRatio = 0.8f, stiffness = 322.27f, visibilityThreshold = 0.001f),
            exit = spring(dampingRatio = 1f, stiffness = 438.65f, visibilityThreshold = 0.001f),
        )
    }
}
