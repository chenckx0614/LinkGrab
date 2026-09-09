package com.linkgrab.app.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.Capsule
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlin.math.roundToInt

/**
 * HyperOS 4 style floating capsule bottom bar.
 *
 * 参考图还原要点（与 iOS27 液态玻璃版的区别）:
 * 1. 容器是**毛玻璃胶囊** —— backdrop 取样 + 弱模糊(14dp)，半透明白底，
 *    背景内容能透出来；没有液态玻璃的 lens 折射和 vibrancy。
 * 2. 选中项背后是**浅灰色椭圆遮罩**(圆角 27dp 的小胶囊)，跟随选中位置
 *    滑动 —— 这是 HyperOS4 的标志性元素，不是底部指示线。
 * 3. 胶囊边缘有一圈浅色描边(顶部亮边)。
 * 4. 按下时在标签中心出现一个**深灰色圆形**涟漪(参考图二)。
 * 5. 图标/文字都是深色，选中与未选中只有轻微透明度差异，
 *    区分主要靠遮罩而不是颜色。
 */
@Composable
fun HyperOS4BottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(selectIndex: (Int) -> Unit) -> Unit
) {
    val isLightTheme = !isInDarkTheme()

    // 毛玻璃容器：半透明白 + 边缘亮描边(浅色模式)
    val containerColor =
        if (isLightTheme) Color.White.copy(alpha = 0.55f)
        else Color(0xFF2C2C2E).copy(alpha = 0.65f)
    val strokeColor =
        if (isLightTheme) Color.White.copy(alpha = 0.65f)
        else Color.White.copy(alpha = 0.10f)
    // 选中遮罩：浅灰椭圆
    val maskColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.12f)
        else Color.White.copy(alpha = 0.14f)

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidthPx = constraints.maxWidth.toFloat() / tabsCount
        val maskInset = 5.dp
        val maskInsetPx = with(density) { maskInset.toPx() }
        val maskWidth = with(density) { (tabWidthPx - maskInsetPx * 2).toDp() }

        val selectedIndex = selectedTabIndex()
        val maskPosition by animateFloatAsState(
            targetValue =
                if (isLtr) selectedIndex.toFloat()
                else (tabsCount - 1 - selectedIndex).toFloat(),
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
            label = "maskSlide",
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    // 弱模糊即可，HyperOS4 的毛玻璃没有液态玻璃那么强的折射。
                    // 默认 highlight/shadow 会画出多余的描边与投影，必须关掉。
                    effects = { blur(14.dp.toPx()) },
                    highlight = { null },
                    shadow = { null },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .border(1.dp, strokeColor, RoundedCornerShape(32.dp)),
        ) {
            // ---- 选中项背后的浅灰椭圆遮罩(滑动) ----
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            x = (maskPosition * tabWidthPx + maskInsetPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .padding(vertical = maskInset)
                    .width(maskWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(27.dp))
                    .background(maskColor),
            )

            // ---- 标签内容 ----
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                content = { content { index -> onTabSelected(index) } },
            )
        }
    }
}

@Composable
fun HyperOS4TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    // 参考图里两个标签的图标/文字都是深色，区别只在遮罩；
    // 未选中仅轻微变淡。
    val activeColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    val inactiveColor =
        if (isLightTheme) Color(0xFF1A1A1A).copy(alpha = 0.55f)
        else Color.White.copy(alpha = 0.60f)
    val contentColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "contentColor",
    )

    // 按下涟漪：标签中心的深灰圆(参考图二)
    val pressColor =
        if (isLightTheme) Color(0xFF3C3C43).copy(alpha = 0.20f)
        else Color.White.copy(alpha = 0.22f)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(120),
        label = "pressAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(27.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 按下时中心出现的深灰圆，尺寸刚好包住图标+文字
        Box(
            Modifier
                .size(56.dp)
                .alpha(pressAlpha)
                .clip(CircleShape)
                .background(pressColor),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.15).sp,
                lineHeight = 12.sp,
            )
        }
    }
}
