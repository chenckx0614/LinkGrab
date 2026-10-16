package com.linkgrab.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.linkgrab.app.ui.liquid.LiquidGlassBackButton
import com.linkgrab.app.ui.theme.iosBackground
import kotlinx.coroutines.delay

/**
 * 奶蛙消消乐（工具箱 → 应用内 WebView 游戏页）。
 *
 * 不跳浏览器：游戏直接长在页面里，返回键优先走网页内后退
 * （游戏内的关卡/弹层），退无可退再退出本页。
 *
 * 两处防白屏 / 防掉帧：
 * - WebView **延迟到转场结束后再挂载**。转场是布局层位移（每帧重新
 *   measure 两个页面），WebView 又是每帧全屏自绘的大户，边滑边渲染
 *   必掉帧；转场期间先显示应用底色占位。
 * - WebView 背景透明 + 页面加载完成后整体淡入，首帧不再闪白。
 */
@Composable
fun GameScreen(onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 转场（约 320ms）结束后才真正挂载 WebView
    var ready by remember { mutableStateOf(false) }
    // 网页首帧渲染完成后才显示 WebView，避免白底闪一下
    var pageLoaded by remember { mutableStateOf(false) }
    // 退出时的 WebView 截图：转场期间用它替代真 WebView 参与渲染
    var leavingSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val webViewAlpha by animateFloatAsState(
        targetValue = if (pageLoaded) 1f else 0f,
        animationSpec = tween(200),
        label = "gameWebViewAlpha",
    )

    LaunchedEffect(Unit) {
        delay(500) // 覆盖 push 转场窗口（440ms），转场画完才让 WebView 上场
        ready = true
    }

    /**
     * 退出页面前把 WebView 画成一张静态截图，然后**从组合里移除 WebView 本体**。
     *
     * 根因：pop 转场是布局层位移（每帧重新 measure 两个页面），WebView 哪怕
     * onPause 了也还在布局树里每帧参与 measure/layout——比不移除更糟（2.5.33
     * 实测反而更卡）。截图方案下转场期间只剩一张 GPU 纹理 Image：不掉帧，
     * 游戏画面也原地保留（不闪）。
     */
    fun exitPage() {
        val wv = webView
        if (wv != null && wv.width > 0 && wv.height > 0) {
            try {
                val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                wv.draw(Canvas(bitmap))
                leavingSnapshot = bitmap.asImageBitmap()
            } catch (_: Exception) {
                // 截图失败就走纯背景退出，不阻塞返回
            }
        }
        wv?.onPause()
        ready = false // 立刻摘掉 AndroidView，WebView 彻底退出转场布局
        onBack()
    }

    // 网页内有历史就先退网页历史（关弹层/退关卡），没有才退出去
    BackHandler(enabled = true) {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else exitPage()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(iosBackground())
    ) {
        val snapshot = leavingSnapshot
        when {
            // 退出转场中：静态截图替身，轻量渲染
            snapshot != null -> Image(
                bitmap = snapshot,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            ready -> AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(webViewAlpha),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        // 游戏存档走 localStorage，必须开 DOM storage
                        settings.domStorageEnabled = true
                        // BGM/音效可能不带用户手势就播，别拦
                        settings.mediaPlaybackRequiresUserGesture = false
                        // 透明底：加载中透出下面的应用底色，不闪白
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                pageLoaded = true
                            }
                        }
                        loadUrl("https://chenckx0614.github.io/naiwaxxl/")
                        webView = this
                    }
                },
                onRelease = { wv ->
                    wv.stopLoading()
                    wv.destroy()
                },
            )
            else ->
                // 转场空窗期的视觉锚点：一个安静的加载点，避免纯色一闪而过
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                    strokeWidth = 3.dp,
                )
        }

        LiquidGlassBackButton(
            onClick = { exitPage() },
            backdrop = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(42.dp),
        )
    }
}
