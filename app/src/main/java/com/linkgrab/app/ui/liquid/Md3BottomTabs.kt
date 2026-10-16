package com.linkgrab.app.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Material Design 3 Navigation Bar（M3 Expressive）。
 *
 * 按 Google 官方规范还原（m3.material.io/components/navigation-bar）：
 * 1. **容器**：全宽、68dp 高（规范 80dp，实测在本机其他 56dp 悬浮风格旁
 *    显得笨高，收紧为 68dp）、不透明 —— 颜色取 `surface-container`
 *    （浅色 #F3EDF7 / 深色 #211F26），无边框无投影（M3 用色调面代替阴影）。
 *    与 iOS/鸿蒙的悬浮胶囊完全不同的形态：贴边、通栏、实底。
 * 2. **激活指示器（active indicator）**：64×32dp 全圆角药丸，颜色
 *    `secondary-container`（浅色 #E8DEF8 / 深色 #4A4458），位置随选中项
 *    滑动（M3 Expressive 的 spatial expression）。
 * 3. **图标** 24dp：选中态 `on-secondary-container`（浅色 #1D192B），
 *    未选中 `on-surface-variant`（浅色 #49454F / 深色 #CAC4D0）。
 * 4. **标签** label-medium 12sp / 500：选中 `on-surface`（#1D1B20），
 *    未选中 `on-surface-variant`。
 * 5. **按压反馈**：MD 标准 ripple（bounded），而非 iOS 高光。
 *
 * 性能沿用零重组思路：指示器位移用 Animatable + snapshotFlow 驱动，
 * 组合期不读动画值，动画过程只有 layout/draw 失效。
 */
@Composable
fun Md3BottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop?, // MD3 是实底容器，不用 backdrop；签名对齐其他风格
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(selectIndex: (Int) -> Unit) -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    // M3 baseline 配色（surface-container / secondary-container / on-* 系列）
    val containerColor = if (isLightTheme) Color(0xFFF3EDF7) else Color(0xFF211F26)
    val indicatorColor = if (isLightTheme) Color(0xFFE8DEF8) else Color(0xFF4A4458)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidthPx = constraints.maxWidth.toFloat() / tabsCount

        val selectedIndexState by rememberUpdatedState(selectedTabIndex())

        // 指示器滑动：按压其他标签时不跟（MD3 指示器只跟随选中态，与
        // iOS/HyperOS 的「按压跟随」是两套语言，这里保持 MD 的克制）。
        val initialPosition = remember {
            val i = selectedTabIndex()
            if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
        }
        val indicatorPosition = remember { Animatable(initialPosition) }
        val indicatorScale = remember { Animatable(1f) }

        LaunchedEffect(Unit) {
            snapshotFlow {
                val i = selectedIndexState
                if (isLtr) i.toFloat() else (tabsCount - 1 - i).toFloat()
            }.collectLatest { target ->
                // M3 Expressive spatial spring：指示器轻微收拢后弹性滑到新位置
                indicatorScale.snapTo(0.85f)
                launch {
                    indicatorScale.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 600f))
                }
                indicatorPosition.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 500f))
            }
        }

        // 指示器：64×32dp 药丸，水平滑动
        Box(
            Modifier
                .offset {
                    IntOffset(
                        x = (indicatorPosition.value * tabWidthPx + (tabWidthPx - with(density) { 64.dp.toPx() }) / 2f)
                            .roundToInt(),
                        y = 0,
                    )
                }
                .padding(top = 8.dp) // 指示器垂直位置：顶部 8dp 起，图标区垂直居中
                .width(64.dp)
                .height(32.dp)
                .graphicsLayer {
                    val s = indicatorScale.value
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(16.dp)) // 全圆角 = 高度一半
                .background(indicatorColor),
        )

        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            content = { content { index -> onTabSelected(index) } },
        )
    }
}

/**
 * MD3 导航目的地：图标（选中时置于药丸指示器内）+ label-medium 标签。
 * 按压反馈是 MD 标准 bounded ripple。
 */
@Composable
fun Md3TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    val activeIconColor = if (isLightTheme) Color(0xFF1D192B) else Color(0xFFE8DEF8)
    val inactiveColor = if (isLightTheme) Color(0xFF49454F) else Color(0xFFCAC4D0)
    val activeLabelColor = if (isLightTheme) Color(0xFF1D1B20) else Color(0xFFE6E1E5)

    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图标区：32dp 高，与容器内的药丸指示器对齐（顶部 8dp 偏移相同）
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) activeIconColor else inactiveColor,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) activeLabelColor else inactiveColor,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
