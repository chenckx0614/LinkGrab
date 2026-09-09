package com.linkgrab.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOSBlue
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.ui.theme.IOSOrange
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.linkgrab.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch

private data class GuidePage(
    val icon: ImageVector,
    val title: String,
    val detail: String,
    val accent: Color,
)

private val pages = listOf(
    GuidePage(
        Icons.AutoMirrored.Filled.OpenInNew,
        "从分享开始",
        "复制抖音或小红书的分享链接，回到 LinkGrab 粘贴。",
        IOSBlue,
    ),
    GuidePage(
        Icons.Default.PhotoLibrary,
        "清晰的结果",
        "图片和视频以清晰预览呈现，实况照片也能单独保存。",
        IOSGreen,
    ),
    GuidePage(
        Icons.Default.Download,
        "保存到设备",
        "内容会存入系统相册或音乐资料库，随时可以再次查看。",
        IOSOrange,
    ),
)

private data class BarStyleOption(
    val id: Int,
    val name: String,
    val desc: String,
)

private val barStyles = listOf(
    BarStyleOption(0, "iOS 27", "液态玻璃胶囊，选中项带折射光效"),
    BarStyleOption(1, "HyperOS 4", "毛玻璃胶囊，选中项有椭圆遮罩"),
    BarStyleOption(2, "HarmonyOS 7", "悬浮窄胶囊，按压有光感形变"),
)

/**
 * 首次启动引导页。
 *
 * 前三页介绍用法，最后一页让用户挑一种底栏样式（之后在设置里也能换）。
 */
@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onFinished: () -> Unit,
) {
    // 3 张介绍页 + 1 张底栏选择页
    val pageCount = pages.size + 1
    val pager = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pageCount - 1
    var selectedStyle by remember { mutableIntStateOf(0) }

    IOS26Canvas {
        Box(Modifier.fillMaxSize()) {
            // 背景装饰光晕：跟随当前页的主题色，柔和地铺在内容后面。
            val accent = if (last) iosBlue() else pages.getOrNull(pager.currentPage)?.accent ?: IOSBlue
            GuideAmbientGlow(accent)

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
                    if (index < pages.size) {
                        GuideIntroPage(pages[index])
                    } else {
                        GuideStylePage(selectedStyle) { selectedStyle = it }
                    }
                }

                // ---- 页面指示器：选中项拉长成小胶囊 ----
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 18.dp),
                ) {
                    repeat(pageCount) { index ->
                        val active = index == pager.currentPage
                        val w by animateDpAsState(
                            targetValue = if (active) 22.dp else 7.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                            label = "indicatorWidth",
                        )
                        val color by animateColorAsState(
                            targetValue = if (active) iosBlue() else iosSecondaryLabel().copy(alpha = 0.28f),
                            label = "indicatorColor",
                        )
                        Box(
                            Modifier
                                .size(width = w, height = 7.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                    }
                }

                IOS26Button(
                    if (last) "开始使用" else "继续",
                    {
                        if (last) {
                            viewModel.setBottomBarStyle(selectedStyle)
                            onFinished()
                        } else {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
                if (!last) {
                    Text(
                        "跳过",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(16.dp),
                        fontSize = 15.sp,
                    )
                } else {
                    // 最后一页留一个与按钮等距的空位，避免按钮贴底跳动
                    Spacer(Modifier.height(47.dp))
                }
            }
        }
    }
}

/** 背景装饰：一层很淡的径向光晕，给页面一点纵深。 */
@Composable
private fun GuideAmbientGlow(accent: Color) {
    val isLight = !isInDarkTheme()
    val base = iosBackground()
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = if (isLight) 0.16f else 0.20f),
                        0.55f to accent.copy(alpha = if (isLight) 0.05f else 0.07f),
                        1.0f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.32f),
                        radius = size.width * 0.95f,
                    )
                )
                // 底部再压一层页面底色，保证文字区域的对比度
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to base.copy(alpha = 0.55f),
                        1f to base,
                    )
                )
            }
    )
}

@Composable
private fun GuideIntroPage(page: GuidePage) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        // 图标卡：大圆角方形 + 主题色淡底 + 描边
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(page.accent.copy(alpha = 0.14f))
                    .border(1.dp, page.accent.copy(alpha = 0.22f), RoundedCornerShape(34.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = page.accent,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        Text(
            page.title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.7).sp,
        )
        Text(
            page.detail,
            fontSize = 16.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(start = 8.dp, top = 14.dp, end = 8.dp),
        )
    }
}

/** 最后一页：底栏样式选择。 */
@Composable
private fun GuideStylePage(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
    ) {
        Text(
            "选择底栏样式",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.6).sp,
        )
        Text(
            "之后也可以在「设置」里随时更换",
            fontSize = 14.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            barStyles.forEach { option ->
                GuideStyleCard(
                    option = option,
                    selected = selected == option.id,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

@Composable
private fun GuideStyleCard(
    option: BarStyleOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val stroke by animateColorAsState(
        targetValue = if (selected) iosBlue() else iosSecondaryLabel().copy(alpha = 0.16f),
        label = "cardStroke",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) iosBlue().copy(alpha = 0.08f) else Color.Transparent,
        label = "cardBg",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, stroke, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) iosBlue() else iosLabel(),
                    letterSpacing = (-0.3).sp,
                )
                Text(
                    option.desc,
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 3.dp, end = 8.dp),
                )
            }
            // 选中打勾
            if (selected) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(iosBlue()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        MiniBarPreview(style = option.id, selected = selected)
    }
}

/**
 * 卡片里的迷你底栏示意图，用来区分三种风格的视觉特征：
 * - iOS 27：撑满的玻璃胶囊 + 选中项蓝色圆胶囊
 * - HyperOS 4：撑满的毛玻璃胶囊 + 选中项灰色椭圆遮罩
 * - HarmonyOS 7：居中的窄胶囊 + 选中项图标变蓝
 */
@Composable
private fun MiniBarPreview(style: Int, selected: Boolean) {
    val isLight = !isInDarkTheme()
    val accent = if (isLight) Color(0xFF0088FF) else Color(0xFF4CADFF)
    val glass = if (isLight) Color.White.copy(alpha = 0.62f) else Color(0xFF2C2C2E).copy(alpha = 0.62f)
    val stroke = if (isLight) Color(0xFF1A1A1A).copy(alpha = 0.10f) else Color.White.copy(alpha = 0.14f)
    val dot = if (isLight) Color(0xFF1A1A1A).copy(alpha = 0.30f) else Color.White.copy(alpha = 0.34f)
    val corner = if (style == 2) 22.dp else 18.dp

    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .then(if (style == 2) Modifier.fillMaxWidth(0.74f) else Modifier.fillMaxWidth())
                .height(40.dp)
                .clip(RoundedCornerShape(corner))
                .background(glass)
                .border(1.dp, stroke, RoundedCornerShape(corner))
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { i ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val isActive = i == 1
                        // 选中态底层：三种风格各不相同
                        when {
                            !isActive -> Unit
                            style == 0 -> Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(accent)
                            )
                            style == 1 -> Box(
                                Modifier
                                    .size(width = 30.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isLight) Color(0xFF3C3C43).copy(alpha = 0.20f)
                                        else Color.White.copy(alpha = 0.20f)
                                    )
                            )
                            else -> Unit // HarmonyOS 7 靠图标颜色区分
                        }
                        // 图标位（用小圆点示意）
                        Box(
                            Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !isActive -> dot
                                        style == 0 -> Color.White.copy(alpha = 0.9f)
                                        else -> accent
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
