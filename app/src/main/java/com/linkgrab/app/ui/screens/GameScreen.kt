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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
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
 * 视口自愈脚本（每次按真实视图宽度生成）。
 *
 * 线上实测：文叔叔手机页是**响应式**的——`html` 的 font-size 由 JS 按
 * `innerWidth / 10` 算出来（390 → 39px，800 → 54px，带 54 上限），
 * 所以只要 WebView 报给页面的 `innerWidth` 是对的，整页就完整。
 *
 * 但 WebView 有两条独立的坑会毁掉这个前提：
 *  1. **初始布局视口可能是宽视口**（980 或旧尺寸）。页面脚本在那时候算
 *     rootFont，于是整页按错误宽度排版 —— 390 的屏里只画得下左半边，
 *     就是用户看到的「显示不全 / 图片列表缺一半」。页面自己的 resize
 *     监听能救，但只有真的发生 resize 才会重算。
 *  2. **固定宽设计的站点**（内容比视口宽）在 WebView 里不会像手机浏览器
 *     那样 shrink-to-fit，同样只显示左边一截。
 *
 * 对策：
 *  - `innerWidth` 明显大于视图宽（>8%）→ 把 viewport meta 强制改成
 *    `width=<视图宽>, initial-scale=1`，把布局视口拉回真实宽度；
 *  - 内容比视口宽 → 按浏览器 shrink-to-fit 的算法缩放
 *    （`width=<内容宽>, initial-scale=视口/内容宽`）；
 *  - 改完补发一次 `resize`，让站点的 rootFont / --vh 重算。
 * 两条都不成立（绝大多数正常页面）时脚本什么都不做。
 */
private fun buildFitViewportJs(cssWidth: Int): String = """
    (function () {
      try {
        var d = document.documentElement;
        var iw = window.innerWidth;
        if (!iw) return;
        var m = document.querySelector('meta[name="viewport"]');
        if (!m) {
          m = document.createElement('meta');
          m.setAttribute('name', 'viewport');
          document.head.appendChild(m);
        }
        if (iw > $cssWidth * 1.08) {
          m.setAttribute('content', 'width=$cssWidth, initial-scale=1');
        } else {
          var cw = Math.max(d.scrollWidth || 0, (document.body && document.body.scrollWidth) || 0);
          if (cw > iw + 8 && (window.__lgFitTries || 0) < 2) {
            m.setAttribute('content', 'width=' + Math.round(cw) + ', initial-scale=' + (iw / cw).toFixed(4));
            window.__lgFitTries = (window.__lgFitTries || 0) + 1;
          } else {
            return;
          }
        }
        setTimeout(function () { window.dispatchEvent(new Event('resize')); }, 120);
      } catch (e) { }
    })();
""".trimIndent()

/**
 * 「页面诊断」注入的脚本：把网页**自己**量到的视口/文档尺寸、CSS 变量、
 * 图片加载数、DOM 节点数一起回传。显示不全时凭这一屏就能判断是
 * 被裁（文档宽 > 视口宽）、没加载（图片 ok 数少）、还是内核不支持。
 */
private val DiagnosticJs = """
    (function () {
      try {
        var d = document.documentElement, b = document.body;
        var imgs = Array.prototype.slice.call(document.images);
        var ok = imgs.filter(function (i) { return i.complete && i.naturalWidth > 0; }).length;
        var cs = getComputedStyle(d);
        var vh = cs.getPropertyValue('--vh').trim();
        var lines = [];
        lines.push('url ' + location.href);
        lines.push('viewport ' + window.innerWidth + ' x ' + window.innerHeight + ' @' + (window.devicePixelRatio || 1));
        lines.push('doc ' + d.scrollWidth + ' x ' + d.scrollHeight);
        lines.push('body ' + (b ? b.scrollWidth + ' x ' + b.scrollHeight : 'null'));
        lines.push('root font ' + cs.fontSize + '   --vh ' + (vh || 'unset'));
        lines.push('images ' + ok + '/' + imgs.length + ' loaded');
        lines.push('nodes ' + d.getElementsByTagName('*').length +
          '   #app children ' + ((document.querySelector('#app') || {}).childElementCount || 0));
        lines.push('title ' + document.title);
        lines.push('ua ' + navigator.userAgent);
        return lines.join('\n');
      } catch (e) {
        return 'ERR ' + e;
      }
    })();
""".trimIndent()

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
 * 防白屏 / 防掉帧 / 防卡死 / 防「显示不全」：
 * - WebView **延迟到转场结束后再挂载**（转场期间每帧 measure 全屏自绘大户必掉帧）。
 * - **等 WebView 量出尺寸再 loadUrl**：边长为 0 时开跑，站点按
 *   `window.innerHeight` 算出的 `--vh` 会是 0，整页高度塌陷。
 * - **宽页面缩小适配**（[FitViewportJs]）：文叔叔这类固定 750px 设计的站点，
 *   手机浏览器会 shrink-to-fit，WebView 只会显示左边一半 —— 实测 390 宽只
 *   露出半屏。加载完注入 viewport meta 让整页缩进屏幕。
 * - 页面加载完成后整体淡入，首帧不闪；淡入结束摘掉 alpha 合成层。
 * - 主框架加载失败或 10 秒超时进入失败态（重试 / 浏览器打开），不再无限转圈。
 * - 工具页用标准手机 Chrome UA（去掉 WebView 标记），避免被站点识别为空壳/限制渲染。
 * - WebChromeClient + 三方 Cookie：缺了它们 alert/file/登录态会静默失效；
 *   文件选择器单独接 onShowFileChooser，否则「选择文件」点了没反应。
 * - 右上角菜单的玻璃采样「打开菜单那一刻的页面截图」（CanvasBackdrop），
 *   绝不把活的 WebView 录进采样层 —— 那会让网页闪、缺块。
 */
@Composable
fun WebPageScreen(
    url: String,
    desktop: Boolean,
    transparentBackground: Boolean = false,
    onBack: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
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
    val density = LocalDensity.current
    // 页面诊断（菜单 →「页面诊断」）：把网页自己报告的视口/文档/图片状态读出来，
    // 显示不全时用户截一张图就能定位到底是「被裁」「没加载」还是「内核不支持」。
    var diagnostics by remember { mutableStateOf<String?>(null) }
    // WebView 在 root 里的顶边（菜单玻璃截图要按它对齐）
    val webViewTopPx = remember { floatArrayOf(0f) }
    val screenWidthPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    // 菜单玻璃的采样源 = 打开菜单那一刻的**静态网页截图**。
    //
    // 绝不能采样活的 WebView：WebView 走 Chromium 自己的 functor 合成，
    // 被录进 layerBackdrop / GraphicsLayer 时画面会闪、会缺块（3.0.6~3.0.13
    // 反复踩的坑）。用截图做 CanvasBackdrop：玻璃里是真实的页面模糊，
    // 而且截图是死图，永远不会干扰网页自身的绘制。
    val menuShadow = remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val glassFallback = iosBackground()
    val menuBackdrop = rememberCanvasBackdrop {
        val shot = menuShadow.value
        if (shot != null) {
            drawImage(shot, topLeft = Offset.Zero)
        } else {
            drawRect(glassFallback)
        }
    }

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
     * 截「菜单面板后面那一块」网页，给玻璃当采样源（见 [menuBackdrop]）。
     *
     * 面板固定贴在右上角：宽 200dp、右边距 16dp、顶边 topInset + 8dp；
     * 面板上半截压在本应用顶栏上（网页从 topInset + 58dp 才开始），所以先把
     * 顶栏底色铺满，再把网页内容按位移画进下半截。只截 200×380dp 的小图，
     * 不做全屏截图，避免点开菜单时卡一下。
     */
    fun captureMenuShadow() {
        val wv = webView ?: return
        if (wv.width <= 0 || wv.height <= 0) return
        try {
            with(density) {
                val wPx = 200.dp.toPx()
                val hPx = 380.dp.toPx()
                val left = screenWidthPx - 16.dp.toPx() - wPx
                val top = topInset.toPx() + 8.dp.toPx()
                val wvTop = if (webViewTopPx[0] > 0f) {
                    webViewTopPx[0]
                } else {
                    top + 58.dp.toPx()
                }

                val bitmap = Bitmap.createBitmap(
                    wPx.toInt().coerceAtLeast(1),
                    hPx.toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bitmap)
                canvas.drawColor(glassFallback.toArgb())
                canvas.save()
                canvas.clipRect(0f, (wvTop - top).coerceAtLeast(0f), wPx, hPx)
                canvas.translate(-left, wvTop - top)
                wv.draw(canvas)
                canvas.restore()
                menuShadow.value = bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            // 截图失败就退回纯底色玻璃，不影响菜单可用
            android.util.Log.w("LinkGrabWeb", "menu glass snapshot failed: ${e.message}")
        }
    }

    /**
     * 对页面做两件「浏览器会做、WebView 不会做」的事（见 [buildFitViewportJs]）：
     * 把布局视口拉回真实视图宽度，以及让超宽页面缩进屏幕。
     * 之后补发 resize，让站点按新宽度重算 rootFont / --vh。
     */
    fun applyViewportFixes(target: WebView) {
        if (target.width <= 0) return
        val cssWidth = (target.width / density.density).toInt().coerceAtLeast(1)
        target.evaluateJavascript(buildFitViewportJs(cssWidth), null)
        target.evaluateJavascript(DispatchResizeJs, null)
    }

    /**
     * 把网页自己报告的视口/文档/图片状态读出来。
     * 用在「打开后显示不全」这类只能靠现场数据定位的问题上。
     */
    fun runDiagnostics() {
        val wv = webView ?: return
        diagnostics = "读取中…"
        val engine = runCatching {
            Regex("Chrome/[0-9.]+")
                .find(WebSettings.getDefaultUserAgent(context))
                ?.value
        }.getOrNull() ?: "未知内核"
        val cssWidth = if (wv.width > 0) {
            (wv.width / density.density).toInt()
        } else {
            0
        }
        wv.evaluateJavascript(DiagnosticJs) { raw ->
            val text = runCatching {
                org.json.JSONTokener(raw).nextValue() as String
            }.getOrNull() ?: raw
            diagnostics = "内核 $engine\n视图 ${cssWidth}dp × ${wv.height}px\n$text"
        }
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
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    retry()
                                }
                                .padding(horizontal = 28.dp, vertical = 10.dp),
                        )
                        Text(
                            "浏览器打开",
                            color = iosBlue(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    openInBrowser()
                                }
                                .padding(horizontal = 28.dp, vertical = 10.dp),
                        )
                        }
                    }
                    ready -> key(reloadKey) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                // 淡入期间才挂 alpha 层，稳定后摘掉（见 fadeFinished）
                                .then(if (fadeFinished) Modifier else Modifier.alpha(webViewAlpha))
                                // WebView 在 root 里的顶边：菜单玻璃截图按它对齐网页内容
                                .onGloballyPositioned { webViewTopPx[0] = it.positionInRoot().y },
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
                                        // 没有 viewport meta 的页面靠 overview 铺满宽度；
                                        // 带 meta 的窄页面不受影响。固定宽设计的站点
                                        // （文叔叔 750px）靠上面注入的 FitViewportJs 收尾。
                                        settings.loadWithOverviewMode = true
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
                                            // 视口自愈 + rootFont/--vh 重算，错峰多跑几次：
                                            // SPA 是异步渲染的，晚一步画出来的内容也要覆盖到
                                            applyViewportFixes(view)
                                            view.postDelayed(
                                                { runCatching { applyViewportFixes(view) } },
                                                400,
                                            )
                                            view.postDelayed(
                                                { runCatching { applyViewportFixes(view) } },
                                                1200,
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
                                                    // 重新自愈视口并让站点重算 rootFont / --vh
                                                    applyViewportFixes(v as WebView)
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
                    // 采样「打开菜单那一刻的页面截图」，不采样活 WebView：
                    // 玻璃里是真的页面模糊，网页自身完全不受影响（不闪、不缺块）
                    backdrop = menuBackdrop,
                    fraction = menuFraction,
                    triggerIcon = Icons.Default.MoreVert,
                    triggerContentDescription = "更多",
                    onDismiss = { menuVisible = false },
                    onExpand = {
                        captureMenuShadow()
                        menuVisible = true
                    },
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
                    LiquidGlassDropdownMenuItem(
                        text = "页面诊断",
                        onClick = {
                            menuVisible = false
                            runDiagnostics()
                        },
                        icon = {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = null,
                                tint = iosLabel(),
                                modifier = Modifier.size(IOS26IconSize.MenuItem),
                            )
                        },
                    )
                }
            }
        }

        // ---- 页面诊断面板：网页自己报的视口/文档尺寸，截图即可定位显示不全 ----
        diagnostics?.let { report ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { diagnostics = null },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(iosBackground())
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { /* 吃掉点击，避免点到面板就关掉 */ }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "页面诊断",
                            color = iosLabel(),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "关闭",
                            color = iosBlue(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    diagnostics = null
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Text(
                        "doc 宽 > viewport 宽 = 页面被裁；images 未全加载 = 资源没下来",
                        color = iosSecondaryLabel(),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    SelectionContainer {
                        Text(
                            report,
                            color = iosLabel(),
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}
