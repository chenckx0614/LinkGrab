package com.linkgrab.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.linkgrab.app.ui.theme.IOSPurple
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryBackground
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
        Icons.Default.AutoAwesome,
        "欢迎使用 LinkGrab",
        "把链接里的图片、视频和实况照片，干净完整地保存到你的设备。",
        IOSBlue,
    ),
    GuidePage(
        Icons.AutoMirrored.Filled.OpenInNew,
        "从分享开始",
        "复制抖音或小红书的分享链接，回到 LinkGrab 粘贴即可。",
        IOSPurple,
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
 * 前几页介绍用法，最后一页让用户挑一种底栏样式（之后在设置里也能换）。
 * 翻页用手势驱动的「淡入淡出 + 轻微缩放」，不做整页横移，避免页面在
 * 屏幕边缘被硬生生切断。
 */
@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onFinished: () -> Unit,
) {
    // 介绍页 + 最后一张底栏选择页
    val pageCount = pages.size + 1
    val pager = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pageCount - 1
    var selectedStyle by remember { mutableIntStateOf(0) }

    // 主题色随页面平滑过渡，背景光晕才不会在翻页时突然跳色。
    val targetAccent = if (last) iosBlue() else pages[pager.currentPage].accent
    val accent by animateColorAsState(
        targetValue = targetAccent,
        animationSpec = tween(650),
        label = "guideAccent",
    )

    IOS26Canvas {
        Box(Modifier.fillMaxSize()) {
            GuideAurora(accent)

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 26.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    beyondViewportPageCount = 1,
                ) { index ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .guidePageTransition(pager, index),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index < pages.size) {
                            GuideIntroPage(pages[index])
                        } else {
                            GuideStylePage(selectedStyle) { selectedStyle = it }
                        }
                    }
                }

                // ---- 页面指示器：选中项拉长成小胶囊 ----
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 16.dp),
                ) {
                    repeat(pageCount) { index ->
                        val active = index == pager.currentPage
                        val w by animateDpAsState(
                            targetValue = if (active) 22.dp else 7.dp,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                            label = "indicatorWidth",
                        )
                        val color by animateColorAsState(
                            targetValue = if (active) accent else iosSecondaryLabel().copy(alpha = 0.28f),
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

/**
 * 单页的转场：不做完整横移，而是「反向滞后 + 淡入淡出 + 轻微缩放」。
 *
 * 页面在离开中心 0.7 个页宽之前就已经完全透明，所以 Pager 的边缘裁剪
 * 永远发生在看不见的地方，不会出现半截内容被切掉观感。
 */
private fun Modifier.guidePageTransition(pager: PagerState, index: Int): Modifier =
    graphicsLayer {
        val distance = ((pager.currentPage - index) + pager.currentPageOffsetFraction)
            .coerceIn(-1f, 1f)
        val amount = kotlin.math.abs(distance)
        // 位移只跟随 18%，其余靠缩放与透明度消化，形成柔和的叠加换页。
        translationX = distance * size.width * 0.18f
        alpha = (1f - amount / 0.70f).coerceIn(0f, 1f)
        val scale = 1f - 0.12f * amount
        scaleX = scale
        scaleY = scale
    }

/**
 * 背景装饰：两团缓慢漂移的柔光 + 底部压暗。颜色跟随当前页主题色平滑过渡。
 */
@Composable
private fun GuideAurora(accent: Color) {
    val base = iosBackground()
    val isDark = isInDarkTheme()
    val infinite = rememberInfiniteTransition(label = "guideAurora")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auroraDrift",
    )

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val strong = if (isDark) 0.30f else 0.24f
                val soft = if (isDark) 0.20f else 0.15f

                // 右上主光团
                drawRect(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = strong),
                        0.45f to accent.copy(alpha = strong * 0.34f),
                        1.0f to Color.Transparent,
                        center = Offset(w * (0.88f - 0.16f * drift), h * (0.15f + 0.07f * drift)),
                        radius = w * 0.92f,
                    )
                )
                // 左侧副光团，反向漂移，制造流动感
                drawRect(
                    Brush.radialGradient(
                        0.0f to accent.copy(alpha = soft),
                        0.5f to accent.copy(alpha = soft * 0.30f),
                        1.0f to Color.Transparent,
                        center = Offset(w * (0.08f + 0.18f * drift), h * (0.58f - 0.08f * drift)),
                        radius = w * 0.85f,
                    )
                )
                // 底部压回页面底色，保证文字区域对比度
                drawRect(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to base.copy(alpha = 0.62f),
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
        GuideHero(page)
        Spacer(Modifier.height(38.dp))
        Text(
            page.title,
            fontSize = 29.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.8).sp,
        )
        Text(
            page.detail,
            fontSize = 16.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
            lineHeight = 25.sp,
            modifier = Modifier.padding(start = 4.dp, top = 13.dp, end = 4.dp),
        )
    }
}

/**
 * 主视觉：缓慢上下浮动的圆角方块，外面套一层呼吸的光环。
 */
@Composable
private fun GuideHero(page: GuidePage) {
    val infinite = rememberInfiniteTransition(label = "guideHero")
    val float by infinite.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroFloat",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "heroPulse",
    )

    val topColor = lerp(lerp(page.accent, Color.White, 0.34f), Color.White, 0.30f)
    val midColor = lerp(page.accent, Color.White, 0.22f)

    Box(
        modifier = Modifier
            .size(212.dp)
            .padding(top = float.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 呼吸光环
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            0.0f to page.accent.copy(alpha = 0.32f * pulse),
                            0.42f to page.accent.copy(alpha = 0.16f * pulse),
                            1.0f to Color.Transparent,
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension / 2f,
                        )
                    )
                }
        )
        Box(
            Modifier
                .size(128.dp)
                .shadow(
                    elevation = 26.dp,
                    shape = RoundedCornerShape(38.dp),
                    ambientColor = page.accent.copy(alpha = 0.32f),
                    spotColor = page.accent.copy(alpha = 0.26f),
                )
                .clip(RoundedCornerShape(38.dp))
                .background(
                    Brush.verticalGradient(
                        0f to topColor,
                        0.5f to midColor,
                        1f to page.accent,
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(38.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

/** 最后一页：底栏样式选择。 */
@Composable
private fun GuideStylePage(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        Text(
            "选择底栏样式",
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            textAlign = TextAlign.Center,
            letterSpacing = (-0.7).sp,
        )
        Text(
            "之后也可以在「设置」里随时更换",
            fontSize = 14.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
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
        targetValue = if (selected) iosBlue() else iosSecondaryLabel().copy(alpha = 0.14f),
        label = "cardStroke",
    )
    val bg by animateColorAsState(
        targetValue = if (selected) iosBlue().copy(alpha = 0.10f) else iosSecondaryBackground(),
        label = "cardBg",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, stroke, RoundedCornerShape(20.dp))
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
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(iosBlue()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
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
