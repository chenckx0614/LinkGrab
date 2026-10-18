package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.R
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val AuroraPurple = Color(0xFF9698E7)
private val AuroraPink = Color(0xFFEEC1DD)
private val AuroraLavender = Color(0xFFD1D0EF)
private val AuroraBase = Color(0xFFFCF8FB)
private val InkText = Color(0xFF5A5C8F)

/** 推进转场曲线（与主工程 iOS push 同款的缓出感）。 */
private val GuidePushEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * 3.0 引导页（四页推进式）：
 * 0) 欢迎：弥散渐变流动背景 + 大大的「3」+ 磨砂玻璃「下一步」；
 * 1) 解析功能：解析页实拍图 +「这里可以进行解析」；
 * 2) 历史记录：历史页实拍图 +「这里可以看历史解析记录」；
 * 3) 底栏样式：四种样式可视化选择 +「完成」。
 * 每页点「下一步」都是同一套向左推开、新页从右滑入的推进动画。
 * 点「完成」：背景整体模糊 + 白纱遮罩从中心慢慢展开，选中的样式卡片
 * 向下沉降交给真底栏（AppNavigation 会按卡片位置让底栏"落"到底部）。
 */
@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onFinished: (Rect?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()

    var page by remember { mutableStateOf(0) }

    // ---- 首页入场：背景淡入 → 「3」弹性放大 → 文案上浮（错峰） ----
    val bgAlpha = remember { Animatable(0f) }
    val bigScale = remember { Animatable(0.35f) }
    val bigAlpha = remember { Animatable(0f) }
    val titleProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { bgAlpha.animateTo(1f, tween(550)) }
        delay(180)
        launch { bigAlpha.animateTo(1f, tween(350)) }
        bigScale.animateTo(
            1f,
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    LaunchedEffect(Unit) {
        delay(420)
        titleProgress.animateTo(1f, tween(520))
    }

    // ---- 收尾：模糊 + 白纱展开 + 样式卡片沉降，然后交棒给主界面 ----
    var finishing by remember { mutableStateOf(false) }
    val finishProgress = remember { Animatable(0f) }
    // 各样式卡片的屏幕位置（仅收尾前记录，沉降开始后不再刷新）
    val cardRects = remember { mutableMapOf<Int, Rect>() }
    LaunchedEffect(finishing) {
        if (finishing) {
            finishProgress.animateTo(1f, tween(1050, easing = GuidePushEasing))
            onFinished(cardRects[bottomBarStyle])
        }
    }

    fun goNext() {
        if (finishing) return
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        page++
    }

    // ---- 流动渐变：三个色团各自以不同周期缓慢游走 ----
    val aurora = rememberInfiniteTransition(label = "aurora")
    val t1 by aurora.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Restart),
        label = "t1",
    )
    val t2 by aurora.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Restart),
        label = "t2",
    )
    val t3 by aurora.animateFloat(
        0f, (2f * Math.PI).toFloat(),
        infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart),
        label = "t3",
    )

    Box(Modifier.fillMaxSize()) {
        // 背景层：独立一层，收尾时整体高斯模糊
        Box(
            Modifier
                .fillMaxSize()
                .alpha(bgAlpha.value)
                .graphicsLayer {
                    val p = finishProgress.value
                    renderEffect = if (p > 0f) {
                        BlurEffect(46f * p, 46f * p, TileMode.Decal)
                    } else null
                }
                .drawBehind {
                    drawRect(AuroraBase)
                    fun blob(cx: Float, cy: Float, radius: Float, color: Color) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(color, color.copy(alpha = 0f)),
                                center = Offset(cx, cy),
                                radius = radius,
                            ),
                            radius = radius,
                            center = Offset(cx, cy),
                        )
                    }
                    val w = size.width
                    val h = size.height
                    blob(
                        w * (0.5f + 0.16f * cos(t1)), h * (0.42f + 0.12f * sin(t1)),
                        w * 0.85f, AuroraPurple.copy(alpha = 0.72f),
                    )
                    blob(
                        w * (0.84f + 0.08f * cos(t2)), h * (0.18f + 0.1f * sin(t2)),
                        w * 0.62f, AuroraPink.copy(alpha = 0.85f),
                    )
                    blob(
                        w * (0.12f + 0.1f * cos(t3)), h * (0.8f + 0.08f * sin(t3)),
                        w * 0.68f, AuroraPink.copy(alpha = 0.5f),
                    )
                    blob(
                        w * (0.4f + 0.14f * sin(t2)), h * (0.92f + 0.05f * cos(t1)),
                        w * 0.55f, AuroraLavender.copy(alpha = 0.75f),
                    )
                }
        )

        // 白纱遮罩：从中心慢慢展开的一圈柔光
        if (finishing) {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val p = finishProgress.value
                        if (p > 0f) {
                            val c = Offset(size.width / 2f, size.height * 0.42f)
                            val r = size.width * (0.15f + 1.25f * p)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.55f * p),
                                        Color.White.copy(alpha = 0.30f * p),
                                    ),
                                    center = c,
                                    radius = r,
                                ),
                                radius = r,
                                center = c,
                            )
                        }
                    }
            )
        }

        // 页面内容：页与页之间向左推开 / 从右滑入
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally(tween(560, easing = GuidePushEasing)) { it } +
                    fadeIn(tween(420))) togetherWith
                    (slideOutHorizontally(tween(560, easing = GuidePushEasing)) { -it / 2 } +
                        fadeOut(tween(360)))
            },
            label = "guidePages",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - 0.6f * finishProgress.value },
        ) { p ->
            when (p) {
                0 -> WelcomePage(
                    bigScale = bigScale.value,
                    bigAlpha = bigAlpha.value,
                    footerProgress = titleProgress.value,
                    onNext = { goNext() },
                )
                1 -> FeaturePage(
                    imageRes = R.drawable.guide_parse,
                    title = "这里可以进行解析",
                    subtitle = "图片、视频，粘贴链接就能保存",
                    pageIndex = 1,
                    enabled = !finishing,
                    onNext = { goNext() },
                )
                2 -> FeaturePage(
                    imageRes = R.drawable.guide_history,
                    title = "这里可以看历史解析记录",
                    subtitle = "随时回看或再次下载已保存的内容",
                    pageIndex = 2,
                    enabled = !finishing,
                    onNext = { goNext() },
                )
                else -> StylePickPage(
                    selected = bottomBarStyle,
                    finishProgress = finishProgress.value,
                    enabled = !finishing,
                    onSelect = { index ->
                        if (!finishing) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(index)
                        }
                    },
                    onRect = { index, rect -> if (!finishing) cardRects[index] = rect },
                    onFinish = {
                        if (!finishing) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            finishing = true
                        }
                    },
                )
            }
        }
    }
}

/** 第 0 页：大大的 3 + LinkGrab 标题。 */
@Composable
private fun WelcomePage(
    bigScale: Float,
    bigAlpha: Float,
    footerProgress: Float,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "3",
                fontSize = 210.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(
                        color = AuroraPurple.copy(alpha = 0.45f),
                        blurRadius = 48f,
                    ),
                ),
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = bigScale
                        scaleY = bigScale
                        alpha = bigAlpha
                    },
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = footerProgress
                translationY = (1f - footerProgress) * 56f
            },
        ) {
            Text(
                "LinkGrab",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                style = TextStyle(
                    shadow = Shadow(
                        color = AuroraPurple.copy(alpha = 0.4f),
                        blurRadius = 24f,
                    ),
                ),
            )
            Text(
                "3.0 全新版本 · 更轻 更快 更强",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        PageFooter(
            pageIndex = 0,
            label = "下一步",
            entrance = footerProgress,
            enabled = footerProgress > 0.9f,
            onClick = onNext,
        )
    }
}

/** 第 1 / 2 页：功能实拍图 + 说明文案。 */
@Composable
private fun FeaturePage(
    imageRes: Int,
    title: String,
    subtitle: String,
    pageIndex: Int,
    enabled: Boolean,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 30.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 30.dp,
                            shape = RoundedCornerShape(26.dp),
                            ambientColor = AuroraPurple.copy(alpha = 0.4f),
                            spotColor = AuroraPurple.copy(alpha = 0.4f),
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(10.dp),
                ) {
                    Image(
                        painter = painterResource(imageRes),
                        contentDescription = title,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp)),
                    )
                }
                Text(
                    title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = AuroraPurple.copy(alpha = 0.45f),
                            blurRadius = 26f,
                        ),
                    ),
                    modifier = Modifier.padding(top = 30.dp),
                )
                Text(
                    subtitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        PageFooter(pageIndex = pageIndex, label = "下一步", enabled = enabled, onClick = onNext)
    }
}

/** 第 3 页：底栏样式选择（2×2 网格）+ 完成。 */
@Composable
private fun StylePickPage(
    selected: Int,
    finishProgress: Float,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onRect: (Int, Rect) -> Unit,
    onFinish: () -> Unit,
) {
    val styles = listOf(
        Triple("iOS 27", R.drawable.bottom_style_ios27, 0),
        Triple("HyperOS 4", R.drawable.bottom_style_hyperos4, 1),
        Triple("HarmonyOS 7", R.drawable.bottom_style_harmonyos7, 2),
        Triple("Material 3", R.drawable.bottom_style_material3, 3),
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 26.dp, vertical = 16.dp),
            ) {
                Text(
                    "选择一个底栏样式",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    style = TextStyle(
                        shadow = Shadow(
                            color = AuroraPurple.copy(alpha = 0.45f),
                            blurRadius = 26f,
                        ),
                    ),
                )
                Text(
                    "进入后可以随时在「我的」里更换",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(18.dp))
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                ) {
                    styles.forEach { (name, res, index) ->
                        StyleTile(
                            name = name,
                            imageRes = res,
                            index = index,
                            selected = selected == index,
                            finishProgress = finishProgress,
                            modifier = Modifier.fillMaxWidth(),
                            onSelect = { onSelect(index) },
                            onRect = onRect,
                        )
                    }
                }
            }
        }

        PageFooter(pageIndex = 3, label = "完成", enabled = enabled, onClick = onFinish)
    }
}

/** 单个样式卡：实拍图（居中裁切放大底栏部分）+ 名称，选中描蓝边。 */
@Composable
private fun StyleTile(
    name: String,
    imageRes: Int,
    index: Int,
    selected: Boolean,
    finishProgress: Float,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
    onRect: (Int, Rect) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .onGloballyPositioned { coords -> onRect(index, coords.boundsInRoot()) }
            .graphicsLayer {
                // 收尾：选中的这张原地淡出，位置直接交给真底栏
                if (selected && finishProgress > 0f) {
                    val p = finishProgress
                    alpha = 1f - p
                }
            }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.72f))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) iosBlue() else Color.White.copy(alpha = 0.8f),
                shape,
            )
            .clickable(onClick = onSelect)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkText,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
        )
    }
}

/** 页脚：进度圆点 + 磨砂玻璃按钮。 */
@Composable
private fun PageFooter(
    pageIndex: Int,
    label: String,
    entrance: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(bottom = 54.dp, top = 26.dp)
            .graphicsLayer {
                alpha = entrance
                translationY = (1f - entrance) * 30f
            },
    ) {
        Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 22.dp),
        ) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (i == pageIndex) Color.White.copy(alpha = 0.95f)
                            else Color.White.copy(alpha = 0.45f)
                        )
                        .size(width = if (i == pageIndex) 18.dp else 6.dp, height = 6.dp)
                )
            }
        }
        GlassButton(label = label, enabled = enabled, onClick = onClick)
    }
}

/** 玻璃按钮：白色胶囊 + 蓝字，与主界面按钮一致。 */
@Composable
private fun GlassButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .background(Color.White.copy(alpha = 0.9f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 64.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosBlue(),
        )
    }
}
