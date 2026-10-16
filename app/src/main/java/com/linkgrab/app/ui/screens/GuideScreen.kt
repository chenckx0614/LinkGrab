package com.linkgrab.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private val AuroraPurple = Color(0xFF9698E7)
private val AuroraPink = Color(0xFFEEC1DD)
private val AuroraLavender = Color(0xFFD1D0EF)
private val AuroraBase = Color(0xFFFCF8FB)

/**
 * 3.0 欢迎页（引导页重构）：
 * - 弥散渐变流动背景（#9698E7 / #EEC1DD / #D1D0EF 漂浮在 #FCF8FB 底上）；
 * - 中央大大的「3」，下面 LinkGrab 标题；
 * - 底部「下一步」，点击后整页放大淡出，引导进入主界面。
 * 展示逻辑是版本化的（guide_shown < 300 就展示）：3.0 起每人看一次，
 * 之后不再出现。
 */
@Composable
fun GuideScreen(
    viewModel: MainViewModel,
    onFinished: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // ---- 入场：背景淡入 → 「3」弹性放大 → 文案上浮 → 按钮浮现（错峰） ----
    val bgAlpha = remember { Animatable(0f) }
    val bigScale = remember { Animatable(0.35f) }
    val bigAlpha = remember { Animatable(0f) }
    val titleProgress = remember { Animatable(0f) }
    val buttonAlpha = remember { Animatable(0f) }
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
        delay(260)
        buttonAlpha.animateTo(1f, tween(450))
    }

    // ---- 离场：整页放大 + 淡出，把人「送进」主界面 ----
    var leaving by remember { mutableStateOf(false) }
    val leaveProgress = remember { Animatable(0f) }
    LaunchedEffect(leaving) {
        if (leaving) {
            leaveProgress.animateTo(1f, tween(460))
            onFinished(null)
        }
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

    Box(
        Modifier
            .fillMaxSize()
            .alpha(bgAlpha.value)
            .graphicsLayer {
                scaleX = 1f + 0.12f * leaveProgress.value
                scaleY = 1f + 0.12f * leaveProgress.value
                alpha = 1f - leaveProgress.value
            }
            .drawBehind {
                // 底色
                drawRect(AuroraBase)
                // 柔和色团：radialGradient 从纯色衰减到透明，叠出弥散效果
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
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 中央：大大的 3
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
                            scaleX = bigScale.value
                            scaleY = bigScale.value
                            alpha = bigAlpha.value
                        },
                )
            }

            // 标题区：随 titleProgress 上浮 + 淡入
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = titleProgress.value
                    translationY = (1f - titleProgress.value) * 56f
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

            // 下一步
            Box(
                Modifier
                    .padding(bottom = 54.dp, top = 40.dp)
                    .alpha(buttonAlpha.value)
                    .graphicsLayer {
                        val press = if (leaving) 0.94f else 1f
                        scaleX = press
                        scaleY = press
                    }
                    .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                    .clickable {
                        if (!leaving) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            leaving = true
                        }
                    }
                    .padding(horizontal = 64.dp, vertical = 15.dp),
            ) {
                Text(
                    "下一步",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AuroraPurple,
                )
            }
        }
    }
}
