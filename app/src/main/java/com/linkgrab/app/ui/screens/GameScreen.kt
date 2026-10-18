package com.linkgrab.app.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 让站点用「当前真实的 innerHeight」重算 CSS 变量，等价于浏览器里转一下屏。
 *
 * 文叔叔手机版的整页高度是 `height: calc(var(--vh) * 100)`，而 `--vh` 只在
 * `<head>` 的内联脚本里算一次（`window.innerHeight * 0.01`），之后只监听
 * `resize`。WebView 尺寸还没定、或命中缓存先跑脚本时 innerHeight 可能是 0，
 * 算出来 `--vh: 0px` → 整页高度塌成 0，只剩客服球 / ☰ / 打开App 横幅这些
 * fixed 元素，表现就是「显示不全、加载不全」。dispatch 一次 resize 即可救回。
 */
private const val DispatchResizeJs =
    "(function(){window.dispatchEvent(new Event('resize'));})();"

/**
 * 奶蛙消消乐（工具箱 → 应用内 WebView 游戏页）。
 * 游戏页用原始配方（透明底、无 viewport 干预），实测最稳。
 */
@Composable
fun GameScreen(onBack: () -> Unit) =
    WebPageScreen(url = GameUrl, desktop = false, transparentBackground = true, onBack = onBack)

/**
 * 工具箱网页类工具（应用内打开）。
 * [desktop] = true 时用桌面版 UA（文叔叔等站点按 UA 出 PC 页，内容完整）。
 */
@Composable
fun ToolWebScreen(url: String, onBack: () -> Unit, desktop: Boolean = false) =
    WebPageScreen(url = url, desktop = desktop, transparentBackground = false, onBack = onBack)

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
 * - **等 WebView 量出尺寸再 loadUrl**：边长为 0 时开跑，站点按
 *   `window.innerHeight` 算出的 `--vh` 会是 0，整页高度塌陷（文叔叔就是这么
 *   表现的：只剩客服球 / ☰ / 打开App 横幅）。
 * - 页面加载完成后补发一次 `resize`，让站点用真实 innerHeight 重算一次
 *   `--vh`——命中缓存、首帧尺寸未定时也能自动纠正。
 * - 页面加载完成后整体淡入，首帧不闪；淡入结束摘掉 alpha 合成层。
 * - 主框架加载失败或 10 秒超时进入失败态（重试 / 浏览器打开），不再无限转圈。
 * - 工具页用标准手机 Chrome UA（去掉 WebView 标记），避免被站点识别为空壳/限制渲染。
 * - WebChromeClient + 三方 Cookie：缺了它们 alert/file/登录态会静默失效；
 *   文件选择器单独接 onShowFileChooser，否则「选择文件」点了没反应。
 */
@Composable
fun WebPageScreen(
    url: String,
    desktop: Boolean,
    transparentBackground: Boolean = false,
    onBack: () -> Unit,
) {
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
    val menuExpandedOrAnimating by remember { derivedStateOf { menuVisible || menuFraction.value > 0.01f } }
    // 退出时的 WebView 截图：转场期间用它替代真 WebView 参与渲染
    var leavingSnapshot by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val webViewAlpha by animateFloatAsState(
        targetValue = if (pageLoaded) 1f else 0f,
        animationSpec = tween(200),
        label = "webPageAlpha",
    )
    // 淡入结束后摘掉 alpha 修饰符：WebView 长期处在 alpha 合成层里容易出现
    // 整块灰/白、局部不刷新的画面问题，稳定之后让它直接画在父层上。
    var fadeFinished by remember { mutableStateOf(false) }
    LaunchedEffect(pageLoaded) {
        if (pageLoaded) {
            delay(260) // 略大于 200ms 的淡入
            fadeFinished = true
        }
    }
    // 文件选择回调（WebChromeClient.onShowFileChooser）。
    // 没有它，网页里的 <input type="file"> 点了没任何反应——文叔叔的核心动作
    // 就是「选文件上传」，缺这一环整个工具等于不可用。
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback == null) return@rememberLauncherForActivityResult
        val data = result.data
        val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else {
            null
        }
        callback.onReceiveValue(uris)
    }
    // 顶栏统一用 topInset 显式定位（返回键 / 菜单按钮共用同一套坐标运算）
    val topInset = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    // 菜单的玻璃采样源：只采顶栏，不采 WebView。
    // WebView 是硬件视图，把它录进 layerBackdrop 会导致画面闪 / 显示不全。
    val headerBackdrop = rememberLayerBackdrop()

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
        fadeFinished = false
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

    // 网页内有历史就先退网页历史（关弹层/退关卡），菜单开着先关菜单，没有才退出去
    BackHandler(enabled = true) {
        when {
            menuVisible -> menuVisible = false
            else -> {
                val wv = webView
                if (wv != null && wv.canGoBack()) wv.goBack() else exitPage()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(iosBackground())
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---- 顶栏：高度固定，菜单在覆盖层里展开，不影响这里 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                    .layerBackdrop(headerBackdrop),
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
                    // 工具页垫一层实色白底：WebView 还没出首帧 / 局部没画到时，
                    // 露出的是白纸而不是应用背景色，观感上不会像「缺了一块」。
                    .background(if (transparentBackground) Color.Transparent else Color.White)
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
                                // 淡入期间才挂 alpha 层，稳定后摘掉（见 fadeFinished）
                                .then(if (fadeFinished) Modifier else Modifier.alpha(webViewAlpha)),
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

                                        override fun onShowFileChooser(
                                            webView: WebView,
                                            filePathCallback: ValueCallback<Array<Uri>>,
                                            fileChooserParams: android.webkit.WebChromeClient.FileChooserParams,
                                        ): Boolean {
                                            // 不接这个回调，网页里的 <input type=file>
                                            // 点了毫无反应（WebView 默认直接吞掉）
                                            fileChooserCallback?.onReceiveValue(null)
                                            fileChooserCallback = filePathCallback
                                            return try {
                                                fileChooserLauncher.launch(
                                                    fileChooserParams.createIntent()
                                                )
                                                true
                                            } catch (_: Exception) {
                                                // 没有可用的文件选择器：把回调置空，
                                                // 否则网页那边会一直等，无法再点第二次
                                                fileChooserCallback = null
                                                false
                                            }
                                        }
                                    }
                                    // 三方 Cookie：站点登录态/统计依赖
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance()
                                        .setAcceptThirdPartyCookies(this, true)
                                    if (desktop) {
                                        // 桌面模式：PC 页内容完整，整页缩放 + 可双指缩放
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
                                        settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                        clearCache(true)
                                        setBackgroundColor(android.graphics.Color.WHITE)
                                    } else if (transparentBackground) {
                                        // 游戏模式：原始配方（3.0.6 之前一直很稳），
                                        // 透明底 + 不碰 viewport，别再引入闪屏
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    } else {
                                        // 工具页手机模式：标准手机 Chrome UA（不带 wv 标记），
                                        // 白底 + 设备宽度 viewport。
                                        settings.userAgentString =
                                            "Mozilla/5.0 (Linux; Android 14; SM-S918B)" +
                                                " AppleWebKit/537.36 (KHTML, like Gecko)" +
                                                " Chrome/124.0.0.0 Mobile Safari/537.36"
                                        settings.useWideViewPort = true
                                        // 不跟 overviewMode：它会在内容比视口宽时把整页
                                        // 缩到屏宽（文字变米粒、右边留白，像「显示不全」）。
                                        // 站点自己带 width=device-width，按 1:1 排版最稳。
                                        settings.loadWithOverviewMode = false
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        settings.textZoom = 100
                                        // 多窗口必须关：开着却不实现 onCreateWindow，
                                        // window.open / target=_blank 会被静默吞掉，
                                        // 链接点了没反应。关掉后同一 WebView 内打开。
                                        settings.setSupportMultipleWindows(false)
                                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                                        setBackgroundColor(android.graphics.Color.WHITE)
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
                                            // 站点按 window.innerHeight 算的 --vh 只算一次；
                                            // 这里补发 resize，让它用真实视口高度重算。
                                            // 首帧尺寸未定 / 命中缓存时算错的 --vh 会把
                                            // 整页高度压成 0（只剩固定的客服球/横幅）。
                                            view.evaluateJavascript(DispatchResizeJs, null)
                                            view.postDelayed(
                                                {
                                                    try {
                                                        view.evaluateJavascript(DispatchResizeJs, null)
                                                    } catch (_: Exception) {
                                                        // WebView 已销毁，忽略
                                                    }
                                                },
                                                350,
                                            )
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
                                    // ---- 加载闸门 ----
                                    // 必须等 WebView 量出真实尺寸再 loadUrl：
                                    // 文叔叔手机版整页高度是 calc(var(--vh)*100)，
                                    // --vh 在 <head> 里用 window.innerHeight 算一次。
                                    // 视图还是 0×0 就开始加载 → --vh = 0 → 整页高度塌成
                                    // 0，只剩客服球/☰/打开App 横幅这些 fixed 元素，
                                    // 表现就是「显示不全」。等尺寸出来再加载，从源头避免。
                                    var loadStarted = false
                                    fun startLoad() {
                                        if (loadStarted) return
                                        loadStarted = true
                                        this@apply.loadUrl(url)
                                    }
                                    addOnLayoutChangeListener(
                                        object : View.OnLayoutChangeListener {
                                            override fun onLayoutChange(
                                                v: View,
                                                left: Int,
                                                top: Int,
                                                right: Int,
                                                bottom: Int,
                                                oldLeft: Int,
                                                oldTop: Int,
                                                oldRight: Int,
                                                oldBottom: Int,
                                            ) {
                                                val w = right - left
                                                val h = bottom - top
                                                if (w <= 0 || h <= 0) return
                                                if (!loadStarted) {
                                                    startLoad()
                                                } else if (w != oldRight - oldLeft ||
                                                    h != oldBottom - oldTop
                                                ) {
                                                    // 视口变了（旋屏 / 输入法 / 重新布局）：
                                                    // 让站点重算 --vh 与整页高度
                                                    (v as WebView)
                                                        .evaluateJavascript(DispatchResizeJs, null)
                                                }
                                            }
                                        }
                                    )
                                    // 兜底：布局回调没来（极端情况）也别让页面干等着
                                    post {
                                        if (width > 0 && height > 0) startLoad()
                                    }
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

        // 点菜单外部关闭：捕获全屏点击，铺在菜单下面
        if (menuExpandedOrAnimating) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { menuVisible = false },
            )
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
                    backdrop = headerBackdrop,
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
