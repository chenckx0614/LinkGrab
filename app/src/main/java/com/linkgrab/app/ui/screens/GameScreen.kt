package com.linkgrab.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linkgrab.app.ui.liquid.LiquidGlassBackButton
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenu
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenuItem
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownShadowPadding
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import kotlinx.coroutines.delay

/** 消消乐地址（GitHub Pages）。 */
const val GameUrl = "https://chenckx0614.github.io/naiwaxxl/"

/**
 * 奶蛙消消乐（工具箱 → 应用内 WebView 游戏页）。
 * 游戏页用原始配方（透明底、无 viewport 干预），实测最稳。
 */
@Composable
fun GameScreen(onBack: () -> Unit) = WebPageScreen(url = GameUrl, desktop = false, onBack = onBack)

/**
 * 工具箱网页类工具（应用内打开）。
 * [desktop] = true 时用桌面版 UA（文叔叔等站点按 UA 出 PC 页，内容完整）。
 */
@Composable
fun ToolWebScreen(url: String, onBack: () -> Unit, desktop: Boolean = false) =
    WebPageScreen(url = url, desktop = desktop, onBack = onBack)

/**
 * 通用应用内网页页（工具箱网页类工具共用）。
 *
 * 顶栏：返回 + 网页标题 + 右上角液态玻璃菜单（刷新 / 使用浏览器打开）。
 * 顶栏高度固定：菜单挂在与页面同层的全屏覆盖层里，展开不撑动顶栏；
 * 覆盖层用 topInset 显式定位并减去菜单自带的 24dp 阴影内边距，
 * 保证收起态按钮与返回键在**同一条直线上**（对齐 ResultScreen 的配方）。
 *
 * 防白屏 / 防掉帧 / 防卡死：
 * - WebView **延迟到转场结束后再挂载**（转场期间每帧 measure 全屏自绘大户必掉帧）。
 * - 页面加载完成后整体淡入，首帧不闪。
 * - 主框架加载失败或 10 秒超时进入失败态（重试 / 浏览器打开），不再无限转圈。
 * - UA 去掉 WebView 标记；桌面模式出 PC 页（文叔叔移动网页面本身就是精简落地页）。
 * - WebChromeClient + 三方 Cookie：缺了它们 alert/file/登录态会静默失效。
 */
@Composable
fun WebPageScreen(url: String, desktop: Boolean, onBack: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // 转场（约 320ms）结束后才真正挂载 WebView
    var ready by remember { mutableStateOf(false) }
    // 网页首帧渲染完成后才显示 WebView，避免闪一下
    var pageLoaded by remember { mutableStateOf(false) }
    // 主框架加载失败 / 超时，给重试出口
    var loadFailed by remember { mutableStateOf(false) }
    // 重试计数：key 变化强制重建 WebView
    var reloadKey by remember { mutableStateOf(0) }
    // 顶栏标题：取网页自己的 <title>
    var pageTitle by remember { mutableStateOf("") }
    // 右上角菜单
    var menuVisible by remember { mutableStateOf(false) }
    val menuFraction = remember { Animatable(0f) }
    // 退出时的 WebView 截图：转场期间用它替代真 WebView 参与渲染
    var leavingSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val webViewAlpha by animateFloatAsState(
        targetValue = if (pageLoaded) 1f else 0f,
        animationSpec = tween(200),
        label = "webPageAlpha",
    )
    // 顶栏统一用 topInset 显式定位（返回键 / 菜单按钮共用同一套坐标运算）
    val topInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    // 菜单的玻璃采样源：挂在内容列上，常挂不摘。
    // 之前随菜单开合挂/摘——WebView 是全屏自绘大户，每次重挂都会强制它
    // 整帧重绘，肉眼看就是"打开菜单闪一下"；现在常驻采样，菜单展开即可模糊。
    val pageBackdrop = rememberLayerBackdrop()

    LaunchedEffect(Unit) {
        delay(500) // 覆盖 push 转场窗口（440ms），转场画完才让 WebView 上场
        ready = true
    }

    // 超时兜底：10 秒还没加载完就判失败，不再无限转圈
    LaunchedEffect(reloadKey, ready) {
        if (!ready) return@LaunchedEffect
        delay(10_000)
        if (!pageLoaded && !loadFailed) loadFailed = true
    }

    fun retry() {
        loadFailed = false
        pageLoaded = false
        pageTitle = ""
        reloadKey++
    }

    fun openInBrowser() {
        context.startActivity(
            android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(url),
            )
        )
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
        Column(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            // ---- 顶栏：高度固定，菜单在覆盖层里展开，不影响这里 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidGlassBackButton(
                    onClick = { exitPage() },
                    backdrop = null,
                )
                Text(
                    pageTitle.ifBlank { "网页" },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
                    color = iosLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
                // 占位：让标题视觉居中（右边是 42dp 菜单按钮）
                Spacer(Modifier.size(42.dp))
            }

            // ---- 内容区 ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
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
                    // 加载失败 / 超时：给出重试与浏览器出口，不再干等
                    loadFailed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text(
                            "网页加载失败",
                            color = iosLabel(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "检查网络后重试，或用浏览器打开",
                            color = iosSecondaryLabel(),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Row(Modifier.padding(top = 18.dp)) {
                            Text(
                                "重试",
                                color = iosBlue(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { retry() }
                                    .padding(horizontal = 28.dp, vertical = 10.dp),
                            )
                            Text(
                                "浏览器打开",
                                color = iosBlue(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { openInBrowser() }
                                    .padding(horizontal = 28.dp, vertical = 10.dp),
                            )
                        }
                    }
                    ready -> key(reloadKey) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(webViewAlpha),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    // 游戏存档走 localStorage，必须开 DOM storage
                                    settings.domStorageEnabled = true
                                    settings.databaseEnabled = true
                                    // 站点里 http 资源混在 https 页面时别拦
                                    settings.mixedContentMode =
                                        WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    // 没有 WebChromeClient 时 alert/confirm/file
                                    // 全部静默失效，部分站点会因此卡住；
                                    // console 日志转发到 Logcat 方便排查（tag: LinkGrabWeb）
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onConsoleMessage(
                                            consoleMessage: android.webkit.ConsoleMessage?,
                                        ): Boolean {
                                            android.util.Log.d(
                                                "LinkGrabWeb",
                                                "console: ${consoleMessage?.message()} " +
                                                    "(${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})",
                                            )
                                            return super.onConsoleMessage(consoleMessage)
                                        }
                                    }
                                    // 三方 Cookie：站点登录态/统计依赖
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance()
                                        .setAcceptThirdPartyCookies(this, true)
                                    if (desktop) {
                                        // 桌面模式：PC 页内容完整（文叔叔移动网页面
                                        // 本身就是精简落地页），整页缩放 + 可双指缩放
                                        settings.userAgentString =
                                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" +
                                                " AppleWebKit/537.36 (KHTML, like Gecko)" +
                                                " Chrome/124.0.0.0 Safari/537.36"
                                        settings.useWideViewPort = true
                                        settings.loadWithOverviewMode = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        settings.textZoom = 100
                                        // 之前用移动 UA 装过旧版的设备可能缓存了
                                        // 移动页，工具页一律绕过缓存重新拉
                                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                        clearCache(true)
                                        setBackgroundColor(android.graphics.Color.WHITE)
                                    } else {
                                        // 游戏模式：原始配方（3.0.6 之前一直很稳），
                                        // 透明底 + 不碰 viewport，别再引入闪屏
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView,
                                            request: WebResourceRequest,
                                        ): Boolean {
                                            // http/https 留在应用内；其他协议
                                            // （mailto / intent / tel 等）交给系统
                                            val scheme = request.url.scheme?.lowercase()
                                            if (scheme == "http" || scheme == "https") return false
                                            return try {
                                                context.startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        request.url,
                                                    )
                                                )
                                                true
                                            } catch (_: Exception) {
                                                true
                                            }
                                        }

                                        override fun onPageFinished(
                                            view: WebView,
                                            url: String?,
                                        ) {
                                            pageLoaded = true
                                            pageTitle = view.title ?: ""
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError,
                                        ) {
                                            // 只认主框架的硬错误；重导向打断加载会报
                                            // ABORTED/UNKNOWN（-1/-9），页面其实没事，
                                            // 不能误判成失败——真连不上还有 10s 超时兜底
                                            if (!request.isForMainFrame) return
                                            val hard = error.errorCode in intArrayOf(
                                                WebViewClient.ERROR_HOST_LOOKUP,
                                                WebViewClient.ERROR_CONNECT,
                                                WebViewClient.ERROR_TIMEOUT,
                                                WebViewClient.ERROR_IO,
                                                WebViewClient.ERROR_BAD_URL,
                                                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                                            )
                                            if (!hard) return
                                            android.util.Log.w(
                                                "LinkGrabWeb",
                                                "main frame error: ${error.errorCode} ${error.description}",
                                            )
                                            loadFailed = true
                                        }

                                        override fun onRenderProcessGone(
                                            view: WebView,
                                            detail: android.webkit.RenderProcessGoneDetail,
                                        ): Boolean {
                                            // 渲染进程崩溃：部分站点会触发某些内核的
                                            // GPU 崩溃，症状就是整页白屏但应用不崩。
                                            // 自救：重置状态重建 WebView，而不是留白屏。
                                            android.util.Log.w("LinkGrabWeb", "render process gone, rebuilding")
                                            retry()
                                            return true
                                        }
                                    }
                                    loadUrl(url)
                                    // Debug 包开远程调试：PC Chrome 打开
                                    // chrome://inspect 即可看页面实际渲染与报错
                                    if (com.linkgrab.app.BuildConfig.DEBUG) {
                                        WebView.setWebContentsDebuggingEnabled(true)
                                    }
                                    webView = this
                                }
                            },
                            onRelease = { wv ->
                                wv.stopLoading()
                                wv.destroy()
                            },
                        )
                    }
                    else ->
                        // 转场空窗期的视觉锚点：一个安静的加载点，避免纯色一闪而过
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(28.dp),
                            strokeWidth = 3.dp,
                        )
                }
            }
        }

        // ---- 菜单覆盖层：全屏、约束宽松，展开不撑动顶栏 ----
        // 定位与 ResultScreen 同配方：top 减去菜单自带 24dp 阴影内边距，
        // 收起态按钮中心正好与返回键中心同一条直线。
        Box(
            Modifier
                .matchParentSize()
                .padding(
                    top = (topInset + 8.dp - LiquidGlassDropdownShadowPadding).coerceAtLeast(0.dp),
                ),
            contentAlignment = Alignment.TopEnd,
        ) {
            // 菜单自带 24dp 内边距：右缘想停在 16dp 处，需要向右补 8dp
            Box(Modifier.offset(x = 8.dp)) {
                LiquidGlassDropdownMenu(
                    show = menuVisible,
                    backdrop = pageBackdrop,
                    fraction = menuFraction,
                    triggerIcon = Icons.Default.MoreVert,
                    triggerContentDescription = "更多",
                    onDismiss = { menuVisible = false },
                    onExpand = { menuVisible = true },
                ) {
                    LiquidGlassDropdownMenuItem(
                        text = "刷新",
                        onClick = {
                            menuVisible = false
                            webView?.reload()
                        },
                        icon = {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = iosLabel(),
                                modifier = Modifier.size(IOS26IconSize.MenuItem),
                            )
                        },
                    )
                    LiquidGlassDropdownMenuItem(
                        text = "使用浏览器打开",
                        onClick = {
                            menuVisible = false
                            openInBrowser()
                        },
                        icon = {
                            Icon(
                                Icons.Default.OpenInBrowser,
                                contentDescription = null,
                                tint = iosLabel(),
                                modifier = Modifier.size(IOS26IconSize.MenuItem),
                            )
                        },
                    )
                }
            }
        }
    }
}
