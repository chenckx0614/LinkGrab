package com.linkgrab.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Multi-strategy parser:
 * - Douyin: Ucmao API → Direct HTML → WebView(peanutdl)
 * - Xiaohongshu: Direct HTML (fast, 1-2s) → WebView 直载笔记页（TLS 指纹墙兜底）
 *   → WebView(tools.emmmm.dev, legacy fallback)
 */
class WebViewParser(private val context: Context) {

    private val ucmaoParser = UcmaoParser()
    private val xhsNativeParser = XhsParser()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private val DOUYIN_PATTERN = Pattern.compile(
            """(https?://)?(www\.)?(douyin\.com|v\.douyin\.com|iesdouyin\.com)/.+"""
        )
        private val XIAOHONGSHU_PATTERN = Pattern.compile(
            // 注意必须覆盖全部短链变体（xhslink.cn / xhs.link），否则 App 内复制的
            // 分享文案会在降级链这里被误判成「不支持的链接格式」，把原生解析的
            // 真实错误盖住。
            """(https?://)?(www\.)?(xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)/.+"""
        )
    }

    fun detectPlatform(url: String): Platform {
        val normalizedUrl = url.lowercase().trim()
        return when {
            DOUYIN_PATTERN.matcher(normalizedUrl).matches() -> Platform.DOUYIN
            XIAOHONGSHU_PATTERN.matcher(normalizedUrl).matches() -> Platform.XIAOHONGSHU
            else -> Platform.UNKNOWN
        }
    }

    suspend fun parse(url: String): Result<MediaResult> {
        val platform = detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            return Result.failure(Exception("不支持的链接格式，请输入抖音或小红书链接"))
        }
        return when (platform) {
            Platform.DOUYIN -> parseDouyin(url)
            Platform.XIAOHONGSHU -> parseXiaohongshu(url)
            else -> Result.failure(Exception("不支持的平台"))
        }
    }

    // ==================== Douyin ====================

    private suspend fun parseDouyin(url: String): Result<MediaResult> {
        // Strategy 0: WebView 真浏览器直载分享页 —— 原生接口被风控打薄时的
        // 权威通道。Chromium 网络栈与真实浏览器一致（小红书同款修法），
        // 图集 / 视频通吃；页面还会下发 ttwid，回填给原生解析器后，
        // 之后每次抖音解析的原生通道风控概率都会大降。
        try {
            parseDouyinInWebView(url)?.let { return Result.success(it) }
        } catch (_: Exception) {
        }

        val ucmaoResult = ucmaoParser.parse(url)
        if (ucmaoResult.isSuccess) return ucmaoResult

        val directResult = parseDouyinDirect(url)
        if (directResult.isSuccess) return directResult

        return try {
            parseWithWebView(
                pageUrl = "https://peanutdl.com/zh/douyin",
                inputSelector = """input[type="text"]""",
                urlToInput = url,
                buttonSelector = """button.MuiButton-containedPrimary""",
                resultJs = """(function(){
                    var vs=document.querySelectorAll('video source,video');
                    var urls=[];
                    vs.forEach(function(v){var s=v.src||v.getAttribute('src');if(s)urls.push(s);});
                    var links=document.querySelectorAll('a[href]');
                    links.forEach(function(a){
                        var h=a.href||'';
                        if(h.includes('.mp4')||h.includes('download'))urls.push(h);
                    });
                    var mt=document.body.innerText.match(/https?:\/\/[^\s"']+\.mp4[^\s"']*/g)||[];
                    urls=urls.concat(mt);
                    return JSON.stringify({t:'v',u:urls.slice(0,5)});
                })()""".trimIndent()
            )
        } catch (e: Exception) {
            Result.failure(Exception("解析抖音失败: ${ucmaoResult.exceptionOrNull()?.message ?: e.message}"))
        }
    }

    /**
     * WebView（真 Chromium 网络栈）直载抖音分享页 —— 原生接口被风控打薄时的
     * 权威通道。桌面 UA 下分享页 SSR 自带 window._ROUTER_DATA（图集全量
     * images 都在里面）；WebView 会自动收下 ttwid cookie，成功后回填给
     * DouyinParser，之后原生请求不再裸奔。短链直接丢进去，302 自己跟。
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun parseDouyinInWebView(url: String): MediaResult? = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var resumed = false

        // 分享页内嵌的 _ROUTER_DATA 原始字面量（SSR 直出）
        val extractRouterJs = """
            (function(){
              try {
                var m = document.documentElement.innerHTML.match(/window\._ROUTER_DATA\s*=\s*([\s\S]*?)<\/script>/);
                if (m && m[1]) return m[1];
              } catch (e) {}
              return null;
            })()
        """.trimIndent()

        // _ROUTER_DATA 落空时的兜底：直接在渲染后的 HTML 里正则抓图集
        val extractAlbumJs = """
            (function(){
              var html = document.documentElement.innerHTML;
              var re = /"download_url_list"\s*:\s*\[\s*"(https?:[^"]+)"\s*\]/g;
              var urls = [], m;
              while ((m = re.exec(html)) !== null) {
                var u = m[1].replace(/\\u002F/g, '/').replace(/\\\//g, '/').replace(/\\u0026/g, '&');
                if (urls.indexOf(u) < 0) urls.push(u);
              }
              if (!urls.length) return null;
              return JSON.stringify({t:'i', u: urls});
            })()
        """.trimIndent()

        val timeout = Runnable {
            if (!resumed && cont.isActive) {
                resumed = true
                handler.post { webView?.destroy() }
                cont.resume(null)
            }
        }
        handler.postDelayed(timeout, 25_000)

        fun finish(result: MediaResult?) {
            if (resumed || !cont.isActive) return
            resumed = true
            handler.removeCallbacks(timeout)
            handler.post { webView?.destroy() }
            cont.resume(result)
        }

        /** 把 WebView 会话里的 ttwid 回填给原生解析器（两个域都试）。 */
        fun harvestTtwid() {
            try {
                for (domain in listOf("https://www.iesdouyin.com/", "https://www.douyin.com/")) {
                    val cookie = android.webkit.CookieManager.getInstance().getCookie(domain) ?: continue
                    Regex("ttwid=([^;]+)").find(cookie)?.groupValues?.get(1)
                        ?.let { DouyinParser.injectTtwid(it) }
                }
            } catch (_: Exception) {
            }
        }

        fun tryAlbumRegex(view: WebView?) {
            if (view == null) { finish(null); return }
            view.evaluateJavascript(extractAlbumJs) { raw ->
                val parsed = raw?.takeIf { it != "null" }?.let { parseWebViewResult(it) }
                if (parsed != null && parsed.isSuccess) finish(parsed.getOrNull()) else finish(null)
            }
        }

        fun tryExtract(view: WebView?, attempt: Int) {
            if (resumed || view == null) return
            view.evaluateJavascript(extractRouterJs) { encoded ->
                val literal = encoded?.takeIf { it != "null" }?.let {
                    runCatching { json.decodeFromString<String>(it) }.getOrNull()
                }
                val parsed = literal?.let { parseDouyinRouterData(it) }
                when {
                    parsed != null -> {
                        harvestTtwid()
                        finish(parsed)
                    }
                    attempt < 5 -> handler.postDelayed({ tryExtract(view, attempt + 1) }, 900)
                    else -> tryAlbumRegex(view)
                }
            }
        }

        handler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        handler.postDelayed({ tryExtract(view, 0) }, 1_000)
                    }
                }
                loadUrl(url)
            }
        }

        cont.invokeOnCancellation {
            resumed = true
            handler.removeCallbacks(timeout)
            handler.post { webView?.destroy() }
        }
    }

    /** 从 _ROUTER_DATA 字面量里找出作品 item（扫全部 loaderData 键找 item_list）。 */
    private fun parseDouyinRouterData(literal: String): MediaResult? {
        return try {
            val loaderData = json.parseToJsonElement(literal)
                .jsonObject["loaderData"]?.jsonObject ?: return null
            var item: JsonObject? = null
            for ((_, value) in loaderData) {
                val list = (value as? JsonObject)?.get("videoInfoRes")
                    ?.jsonObject?.get("item_list")?.jsonArray ?: continue
                item = list.firstOrNull() as? JsonObject
                if (item != null) break
            }
            item?.let { douyinDetailToMediaResult(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** 抖音作品 detail → MediaResult：图集优先，视频兜底（画质档位由原生通道负责）。 */
    private fun douyinDetailToMediaResult(detail: JsonObject): MediaResult? {
        val desc = (detail["desc"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val images = mutableListOf<String>()
        val livePhotos = mutableMapOf<Int, List<String>>()
        detail["images"]?.jsonArray?.forEachIndexed { index, el ->
            val img = el as? JsonObject ?: return@forEachIndexed
            val url = pickDouyinImageUrl(img) ?: return@forEachIndexed
            images.add(url)
            // Live Photo：images[].video.play_addr
            val liveUrls = img["video"]?.jsonObject?.get("play_addr")
                ?.jsonObject?.get("url_list")?.jsonArray
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?.filter { it.isNotEmpty() }
                ?.map { it.replace("playwm", "play") }
                .orEmpty()
            if (liveUrls.isNotEmpty()) livePhotos[index] = liveUrls
        }
        if (images.isNotEmpty()) {
            val distinct = images.distinct()
            return MediaResult(
                type = MediaType.IMAGE,
                title = desc ?: "抖音图文",
                images = distinct,
                coverUrl = distinct.first(),
                livePhotos = livePhotos,
                source = "douyin",
            ).withSecureUrls()
        }
        val playUrl = detail["video"]?.jsonObject?.get("play_addr")
            ?.jsonObject?.get("url_list")?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull { it.isNotEmpty() }
            ?.replace("playwm", "play") ?: return null
        return MediaResult(
            type = MediaType.VIDEO,
            title = desc ?: "抖音视频",
            videoUrl = playUrl,
            source = "douyin",
        ).withSecureUrls()
    }

    /** 图集单张图地址：download_url_list（无水印原图）优先，其次 url_list；非 webp 优先。 */
    private fun pickDouyinImageUrl(img: JsonObject): String? {
        for (key in listOf("download_url_list", "url_list")) {
            val urls = img[key] as? kotlinx.serialization.json.JsonArray ?: continue
            val candidates = urls.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotEmpty() }
                .map { it.replace("\\u002F", "/").replace("\\/", "/").replace("\\u0026", "&") }
            candidates.firstOrNull { !it.substringBefore('?').endsWith(".webp", ignoreCase = true) }
                ?.let { return it }
            candidates.firstOrNull()?.let { return it }
        }
        return null
    }

    // ==================== Xiaohongshu ====================

    private suspend fun parseXiaohongshu(url: String): Result<MediaResult> {
        // 每个通道的失败原因都收集起来，最终失败时一次性展示，
        // 避免层层降级把真实原因掩盖（v2.3.5 之前的教训）。
        val errors = mutableListOf<String>()

        // Strategy 1: WebView 直载笔记页 —— Shield 的 TLS 指纹墙只拦 HTTP 库，
        // WebView 的 Chromium 网络栈与真实浏览器一致，最可靠。
        try {
            loadXhsNoteViaWebView(url)?.let { return Result.success(it) }
            errors.add("WebView直载: 未取得笔记数据")
        } catch (e: Exception) {
            errors.add("WebView直载: ${e.message}")
        }

        // Strategy 2: Direct HTTP（部分网络环境下未被指纹墙拦截时可用，最快）
        parseXiaohongshuDirect(url)
            .onSuccess { return Result.success(it) }
            .onFailure { errors.add("直连: ${it.message}") }

        // Strategy 3: 渲染后 DOM 刮图（不依赖 state 结构）
        try {
            loadXhsPageInWebView(url).onSuccess { return Result.success(it) }
            errors.add("WebView渲染: 未刮到图片")
        } catch (e: Exception) {
            errors.add("WebView渲染: ${e.message}")
        }

        // Strategy 4: WebView with tools.emmmm.dev（历史方案，最后兜底）
        return try {
            parseWithWebView(
                pageUrl = "https://tools.emmmm.dev/xiaohongshu?lang=zh-Hans",
                inputSelector = """input[type="text"], input[type="url"], textarea""",
                urlToInput = url,
                buttonSelector = """button[type="submit"], button""",
                resultJs = """(function(){
                    var is=document.querySelectorAll('img[src*="xhscdn"],img[src*="xiaohongshu"],img[src*="sns"]');
                    var urls=[];
                    is.forEach(function(i){
                        var s=i.src||i.getAttribute('src');
                        if(s&&!s.includes('avatar')&&!s.includes('icon')&&!s.includes('logo'))urls.push(s);
                    });
                    return JSON.stringify({t:'i',u:urls.slice(0,20)});
                })()""".trimIndent()
            )
        } catch (e: Exception) {
            errors.add("第三方: ${e.message}")
            Result.failure(Exception("小红书各通道均失败 —— ${errors.joinToString("；")}"))
        }
    }

    /**
     * 用 WebView（Chromium 网络栈，TLS 指纹与真实浏览器一致）直接加载笔记页，
     * 从页面里抠出 `window.__INITIAL_STATE__` 的原始字面量，交给 XhsParser 解析。
     *
     * 背景：小红书 Shield 会做 TLS 指纹检测，OkHttp / HttpURLConnection 这类
     * 客户端访问笔记页会被 302 到登录页（实测 curl 能过、urllib 被拦），
     * WebView 的网络栈与用户浏览器相同，可以正常拿到 SSR 数据。
     * 桌面 UA 下 SSR HTML 自带完整笔记数据（图集 / 视频 / 实况都在 state 里）。
     */
    /**
     * 公开入口：WebView 直载小红书笔记页并解析（ParseRepository 的 XHS 主通道）。
     *
     * 前置关键步骤：先把短链展开成带 xsec_token 的笔记直链再交给 WebView。
     * 短链跳转环节对部分客户端会「降级」到信息流页（什么笔记都拿不到），
     * 而展开这一步用普通 HTTP 请求从未失败过——让 WebView 直接打开直链，
     * 跳过这个不确定因素。
     */
    suspend fun parseXhsNoteInWebView(url: String): Result<MediaResult> {
        val result = loadXhsNoteViaWebView(url)
        return if (result != null) Result.success(result)
        else Result.failure(Exception("内置浏览器未能取得笔记数据（可能被重定向到登录页或信息流页）"))
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadXhsNoteViaWebView(url: String): MediaResult? =
        suspendCancellableCoroutine { cont ->
            // 短链先展开，WebView 直接打开带令牌的笔记直链
            val target = xhsNativeParser.expandShareLink(url)

            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null
            var resumed = false

            val extractStateJs = """
                (function(){
                  try {
                    var m = document.documentElement.innerHTML.match(/window\.__INITIAL_STATE__\s*=\s*([\s\S]*?)<\/script>/);
                    if (m && m[1]) return m[1];
                  } catch (e) {}
                  return null;
                })()
            """.trimIndent()

            val timeout = Runnable {
                if (!resumed && cont.isActive) {
                    resumed = true
                    handler.post { webView?.destroy() }
                    cont.resume(null)
                }
            }
            handler.postDelayed(timeout, 22_000)

            fun finish(result: MediaResult?) {
                if (resumed || !cont.isActive) return
                resumed = true
                handler.removeCallbacks(timeout)
                handler.post { webView?.destroy() }
                cont.resume(result)
            }

            /**
             * 兜底：页面里抠不到 __INITIAL_STATE__（或 state 里没媒体）时，
             * 注入 JS 借页面自带的签名器 window._webmsxyw 调官方 feed 接口。
             * evaluateJavascript 不 await Promise，脚本把结果写进
             * window.__lgFeedResult，这里轮询读取。
             */
            fun tryFeedApi(view: WebView?, attempt: Int) {
                if (resumed || view == null) return
                if (attempt == 0) {
                    view.evaluateJavascript(xhsNativeParser.feedApiInjectedJs()) { }
                    handler.postDelayed({ tryFeedApi(view, 1) }, 1_200)
                    return
                }
                view.evaluateJavascript(xhsNativeParser.feedApiResultJs()) { encoded ->
                    val raw = encoded?.takeIf { it != "null" }?.let {
                        runCatching { json.decodeFromString<String>(it) }.getOrNull()
                    }
                    val parsed = raw?.let { xhsNativeParser.parseFeedApiJson(it) }
                    val usable = parsed != null && (parsed.images.isNotEmpty() || !parsed.videoUrl.isNullOrEmpty())
                    // 页面根本不在笔记路径（被重定向到登录页等）或页面没有签名器
                    // ——这种重试也不会变，直接结束
                    val fatal = raw != null &&
                        (raw.startsWith("ERR:no_note_id") || raw.startsWith("ERR:no_signer"))
                    when {
                        usable -> finish(parsed)
                        fatal -> finish(null)
                        attempt < 6 -> handler.postDelayed({ tryFeedApi(view, attempt + 1) }, 1_200)
                        else -> finish(null)
                    }
                }
            }

            fun tryExtract(view: WebView?, attempt: Int) {
                if (resumed || view == null) return
                view.evaluateJavascript(extractStateJs) { encoded ->
                    val rawLiteral = encoded?.takeIf { it != "null" }?.let {
                        runCatching { json.decodeFromString<String>(it) }.getOrNull()
                            ?: it.trim().removeSurrounding("\"")
                    }
                    val parsed = rawLiteral?.let { xhsNativeParser.parseStateLiteral(it) }
                    if (parsed != null) {
                        finish(parsed)
                    } else if (attempt < 5) {
                        handler.postDelayed({ tryExtract(view, attempt + 1) }, 700)
                    } else {
                        // SSR 这条路到头了，换官方 feed 接口（页面内签名）
                        tryFeedApi(view, 0)
                    }
                }
            }

            handler.post {
                webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            handler.postDelayed({ tryExtract(view, 0) }, 800)
                        }
                    }
                    loadUrl(target)
                }
            }

            cont.invokeOnCancellation {
                resumed = true
                handler.removeCallbacks(timeout)
                handler.post { webView?.destroy() }
            }
        }

    /**
     * 直接在 WebView 中加载小红书页面，等待 JS 渲染后提取图片
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadXhsPageInWebView(url: String): Result<MediaResult> = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var resumed = false

        val timeout = Runnable {
            if (!resumed && cont.isActive) {
                resumed = true
                handler.post { webView?.destroy() }
                cont.resume(Result.failure(Exception("解析超时")))
            }
        }
        handler.postDelayed(timeout, 12_000)

        val extractJs = """(function(){
            var urls = [];
            // 方法1: 所有 xhscdn 图片
            document.querySelectorAll('img').forEach(function(img) {
                var src = img.src || img.getAttribute('src') || '';
                if (src && (src.includes('xhscdn') || src.includes('sns-webpic') || src.includes('ci.xiaohongshu'))
                    && !src.includes('avatar') && !src.includes('icon') && !src.includes('logo')
                    && !src.includes('fe-platform') && !src.includes('fe-static')) {
                    urls.push(src);
                }
            });
            // 方法2: swiper 轮播图
            document.querySelectorAll('.swiper-slide img, [class*="slide"] img').forEach(function(img) {
                var src = img.src || img.getAttribute('src') || '';
                if (src && !urls.includes(src) && (src.includes('xhscdn') || src.includes('sns-webpic'))) urls.push(src);
            });
            // 方法3: 背景图
            document.querySelectorAll('[style*="background-image"]').forEach(function(el) {
                var style = el.getAttribute('style') || '';
                var match = style.match(/url\("?([^")\s]+)"?\)/);
                if (match && match[1] && (match[1].includes('xhscdn') || match[1].includes('sns-webpic')) && !urls.includes(match[1])) urls.push(match[1]);
            });
            var title = document.title || '小红书笔记';
            return JSON.stringify({title: title, images: urls});
        })()""".trimIndent()

        fun tryExtract(attempt: Int, view: WebView?) {
            if (resumed || view == null) return
            view.evaluateJavascript(extractJs) { result ->
                val mediaResult = parseXhsResultJson(result)
                if (mediaResult != null && mediaResult.images.isNotEmpty()) {
                    if (!resumed && cont.isActive) {
                        resumed = true
                        handler.removeCallbacks(timeout)
                        handler.post { webView?.destroy() }
                        cont.resume(Result.success(mediaResult))
                    }
                } else if (attempt < 4) {
                    handler.postDelayed({ tryExtract(attempt + 1, view) }, 650)
                } else {
                    if (!resumed && cont.isActive) {
                        resumed = true
                        handler.removeCallbacks(timeout)
                        handler.post { webView?.destroy() }
                        cont.resume(Result.failure(Exception("未找到图片")))
                    }
                }
            }
        }

        handler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        handler.postDelayed({ tryExtract(0, view) }, 900)
                    }
                }
                loadUrl(url)
            }
        }

        cont.invokeOnCancellation {
            resumed = true
            handler.removeCallbacks(timeout)
            handler.post { webView?.destroy() }
        }
    }

    private fun parseXhsResultJson(jsonStr: String?): MediaResult? {
        if (jsonStr == null) return null
        val clean = jsonStr.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        return try {
            val titleMatch = """"title"\s*:\s*"([^"]*?)"""".toRegex().find(clean)
            val imagesMatch = """"images"\s*:\s*\[([^\]]*)]""".toRegex().find(clean)
            val title = titleMatch?.groupValues?.get(1) ?: "小红书笔记"
            val imagesStr = imagesMatch?.groupValues?.get(1) ?: ""
            val images = """"([^"]+)"""".toRegex().findAll(imagesStr).map { it.groupValues[1] }.toList()
                .filter { it.contains("xhscdn") || it.contains("sns-webpic") || it.contains("ci.xiaohongshu") }

            if (images.isNotEmpty()) {
                MediaResult(
                    type = MediaType.IMAGE,
                    title = title,
                    images = images.distinct().take(20),
                    source = "xiaohongshu",
                ).withSecureUrls()
            } else null
        } catch (e: Exception) { null }
    }

    // ==================== Direct HTML Parsing ====================

    private suspend fun parseDouyinDirect(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://www.douyin.com/")
                .build()
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("空响应")

            // 图文（note）页优先提取图集：`download_url_list` 只出现在图集的
            // images[] 里，视频页没有这个字段。不先拦下来的话，图集里实况/
            // 预览视频的 mp4 / playAddr 会被下面的正则误判成视频，
            // 用户在结果页就只能以视频方式看到一张封面。
            run {
                val albumPattern = Pattern.compile("\"download_url_list\"\\s*:\\s*\\[\"(https?://[^\"]+)\"")
                val albumMatcher = albumPattern.matcher(html)
                val images = mutableListOf<String>()
                while (albumMatcher.find()) {
                    var imageUrl = albumMatcher.group(1) ?: continue
                    imageUrl = imageUrl.replace("\\u002F", "/").replace("\\/", "/").replace("\\u0026", "&")
                    images.add(imageUrl)
                }
                if (images.isNotEmpty()) {
                    val distinct = images.distinct()
                    return@withContext Result.success(
                        MediaResult(
                            type = MediaType.IMAGE,
                            title = "抖音图文",
                            images = distinct,
                            coverUrl = distinct.first(),
                            source = "douyin",
                        ).withSecureUrls()
                    )
                }
            }

            val patterns = listOf(
                Pattern.compile("""playAddr.*?"url_list":\s*\["(https?://[^"]+)""""),
                Pattern.compile("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*"""),
                Pattern.compile("""video_url["\s:]+["']?(https?://[^"'\s<>]+)"""),
            )
            for (pattern in patterns) {
                val matcher = pattern.matcher(html)
                if (matcher.find()) {
                    var videoUrl = matcher.group(1) ?: matcher.group()
                    videoUrl = videoUrl.replace("\\u002F", "/").replace("\\/", "/").replace("\\u0026", "&")
                    return@withContext Result.success(MediaResult(type = MediaType.VIDEO, title = "抖音视频", videoUrl = videoUrl, source = "douyin"))
                }
            }
            Result.failure(Exception("无法从页面提取视频链接"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 直接请求小红书页面提取图片（快速，1-2秒）
     */
    private suspend fun parseXiaohongshuDirect(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("空响应")

            val images = mutableListOf<String>()

            // Fast path: social preview images are usually available before the app state.
            val ogPattern = Pattern.compile("""<meta[^>]+(?:property|name)=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"']og:image[\"']""", Pattern.CASE_INSENSITIVE)
            val ogMatcher = ogPattern.matcher(html)
            while (ogMatcher.find()) {
                val image = ogMatcher.group(1) ?: ogMatcher.group(2) ?: continue
                if (isContentImage(image)) images.add(image.replace("\\u002F", "/"))
            }

            // Method 1: Extract from __INITIAL_STATE__ JSON
            val statePattern = Pattern.compile("""window\.__INITIAL_STATE__\s*=\s*(\{.*?\})\s*""", Pattern.DOTALL)
            val stateMatcher = statePattern.matcher(html)
            if (stateMatcher.find()) {
                val stateJson = stateMatcher.group(1)?.replace("\\u002F", "/")?.replace("\\u0026", "&") ?: ""
                val imagePattern = Pattern.compile(""""urlDefault"\s*:\s*"([^"]+)"""")
                val imgMatcher = imagePattern.matcher(stateJson)
                while (imgMatcher.find()) {
                    val imgUrl = imgMatcher.group(1) ?: continue
                    if (isContentImage(imgUrl)) images.add(imgUrl)
                }
            }

            // Method 2: Extract from HTML meta tags
            if (images.isEmpty()) {
                val metaPattern = Pattern.compile("""<meta[^>]*content="(https?://[^"]*(?:xhscdn|xiaohongshu|sns)[^"]*\.(?:jpg|jpeg|png|webp)[^"]*)"[^>]*>""")
                val metaMatcher = metaPattern.matcher(html)
                while (metaMatcher.find()) {
                    val imgUrl = metaMatcher.group(1) ?: continue
                    if (isContentImage(imgUrl)) images.add(imgUrl)
                }
            }

            // Method 3: Extract all xhs CDN image URLs
            if (images.isEmpty()) {
                val cdnPattern = Pattern.compile("""https?://[a-z0-9.-]*xhscdn\.com/[^\s"'<>\\]+\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?""")
                val cdnMatcher = cdnPattern.matcher(html)
                while (cdnMatcher.find()) {
                    val imgUrl = cdnMatcher.group() ?: continue
                    if (isContentImage(imgUrl)) images.add(imgUrl)
                }
            }

            if (images.isNotEmpty()) {
                val title = extractTitle(html)
                return@withContext Result.success(
                    MediaResult(
                        type = MediaType.IMAGE,
                        title = title,
                        images = images.distinct().take(20),
                        source = "xiaohongshu"
                    ).withSecureUrls()
                )
            }

            Result.failure(Exception("无法提取图片"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isContentImage(url: String): Boolean {
        val lower = url.lowercase()
        return !lower.contains("avatar") &&
                !lower.contains("icon") &&
                !lower.contains("logo") &&
                !lower.contains("emoji") &&
                !lower.contains("sticker") &&
                !lower.contains("180x180") &&
                !lower.contains("thumbnail") &&
                !lower.contains("fe-platform") &&
                !lower.contains("fe-static") &&
                !lower.contains("fe-video")
    }

    private fun extractTitle(html: String): String {
        val titlePattern = Pattern.compile("""<title>([^<]+)</title>""")
        val matcher = titlePattern.matcher(html)
        return if (matcher.find()) {
            matcher.group(1)?.replace(" - 小红书", "")?.trim() ?: "小红书笔记"
        } else {
            "小红书笔记"
        }
    }

    // ==================== WebView Fallback ====================

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun parseWithWebView(
        pageUrl: String,
        inputSelector: String,
        urlToInput: String,
        buttonSelector: String,
        resultJs: String,
    ): Result<MediaResult> = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var resumed = false

        val timeout = Runnable {
            if (!resumed && cont.isActive) {
                resumed = true
                handler.post { webView?.destroy() }
                cont.resume(Result.failure(Exception("解析超时")))
            }
        }
        handler.postDelayed(timeout, 15_000)

        handler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                            handler.postDelayed({
                            val escapedUrl = urlToInput.replace("'", "\\'")
                            view?.evaluateJavascript("""(function(){
                                var i=document.querySelector('$inputSelector');
                                if(i){i.value='$escapedUrl';i.dispatchEvent(new Event('input',{bubbles:true}));return 'ok';}
                                return 'no_input';
                            })()""") {
                                    handler.postDelayed({
                                    view?.evaluateJavascript("""(function(){
                                        var b=document.querySelector('$buttonSelector');
                                        if(b){b.click();return 'ok';}
                                        return 'no_button';
                                    })()""") {
                                        handler.postDelayed({
                                            view?.evaluateJavascript(resultJs) { result ->
                                                handler.removeCallbacks(timeout)
                                                val parsed = parseWebViewResult(result)
                                                handler.post { webView?.destroy() }
                                                if (!resumed && cont.isActive) {
                                                    resumed = true
                                                    cont.resume(parsed)
                                                }
                                            }
                                        }, 2_000)
                                    }
                        }, 350)
                            }
                        }, 1_000)
                    }
                }
                loadUrl(pageUrl)
            }
        }

        cont.invokeOnCancellation {
            resumed = true
            handler.removeCallbacks(timeout)
            handler.post { webView?.destroy() }
        }
    }

    private fun parseWebViewResult(jsonStr: String?): Result<MediaResult> {
        if (jsonStr == null) return Result.failure(Exception("无返回数据"))
        val clean = jsonStr.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        return try {
            val typeMatch = """"t"\s*:\s*"([^"]+)"""".toRegex().find(clean)
            val urlsMatch = """"u"\s*:\s*\[([^\]]*)]""".toRegex().find(clean)
            val type = typeMatch?.groupValues?.get(1) ?: "none"
            val urlsStr = urlsMatch?.groupValues?.get(1) ?: ""
            val urls = """"([^"]+)"""".toRegex().findAll(urlsStr).map { it.groupValues[1] }.toList()

            when {
                type == "v" && urls.isNotEmpty() -> Result.success(
                    MediaResult(type = MediaType.VIDEO, title = "视频", videoUrl = urls.first(), source = "webview").withSecureUrls()
                )

                type == "i" && urls.isNotEmpty() -> Result.success(
                    MediaResult(type = MediaType.IMAGE, title = "图片", images = urls, source = "webview").withSecureUrls()
                )
                else -> Result.failure(Exception("未找到媒体内容"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("解析返回数据失败"))
        }
    }
}
