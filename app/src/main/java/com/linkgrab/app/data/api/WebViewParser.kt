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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Multi-strategy parser:
 * - Douyin: Ucmao API → Direct HTML → WebView(peanutdl)
 * - Xiaohongshu: Direct HTML (fast, 1-2s) → WebView(tools.emmmm.dev, fallback)
 */
class WebViewParser(private val context: Context) {

    private val ucmaoParser = UcmaoParser()
    private val xhsMultiSiteParser = XhsMultiSiteParser(context)

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
            """(https?://)?(www\.)?(xiaohongshu\.com|xhslink\.com|xslink\.com)/.+"""
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

    // ==================== Xiaohongshu (multi-site) ====================

    private suspend fun parseXiaohongshu(url: String): Result<MediaResult> {
        // Use multi-site parser: tries multiple sites and picks the fastest
        return xhsMultiSiteParser.parse(url)
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
        handler.postDelayed(timeout, 20_000)

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
                    handler.postDelayed({ tryExtract(attempt + 1, view) }, 2_500)
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
                        handler.postDelayed({ tryExtract(0, view) }, 4_000)
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
                MediaResult(type = MediaType.IMAGE, title = title, images = images.distinct().take(20), source = "xiaohongshu")
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
                    )
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
        handler.postDelayed(timeout, 25_000)

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
                                        }, 8_000)
                                    }
                                }, 1_000)
                            }
                        }, 3_000)
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
                type == "v" && urls.isNotEmpty() -> Result.success(MediaResult(type = MediaType.VIDEO, title = "视频", videoUrl = urls.first(), source = "webview"))
                type == "i" && urls.isNotEmpty() -> Result.success(MediaResult(type = MediaType.IMAGE, title = "图片", images = urls, source = "webview"))
                else -> Result.failure(Exception("未找到媒体内容"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("解析返回数据失败"))
        }
    }
}
