package com.linkgrab.app.data.api

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Multi-site xiaohongshu parser.
 * Tries multiple parsing sites in parallel and returns the first successful result.
 */
class XhsMultiSiteParser(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Parse xiaohongshu link using multiple sites.
     * Tries direct HTTP first (fast), then WebView with multiple sites.
     */
    suspend fun parse(url: String): Result<MediaResult> {
        // Strategy 1: Direct HTTP (fastest, ~1-2s)
        val directResult = tryDirectHttp(url)
        if (directResult.isSuccess) return directResult

        // Strategy 2: Try multiple sites via WebView in sequence
        val sites = listOf(
            SiteConfig(
                name = "qsy.jyblog",
                url = "https://qsy.jyblog.cn/",
                inputSelector = """input[type="text"]""",
                buttonSelector = """button[type="submit"], button.btn""",
                extractJs = """(function(){
                    var urls = [];
                    document.querySelectorAll('img').forEach(function(img) {
                        var src = img.src || '';
                        if (src && !src.includes('icon') && !src.includes('logo') && !src.includes('favicon')
                            && !src.includes('xhs.ico') && !src.includes('douyin.png') && !src.includes('kuaishou.ico')
                            && !src.includes('bili.png') && !src.includes('weibo.png')
                            && (src.includes('xhscdn') || src.includes('sns-webpic') || src.includes('.jpg') || src.includes('.png') || src.includes('.webp'))) {
                            urls.push(src);
                        }
                    });
                    return JSON.stringify({t:'i', u:urls.slice(0,20)});
                })()""",
            ),
            SiteConfig(
                name = "hellotik",
                url = "https://www.hellotik.app/zh/rednote",
                inputSelector = """input[type="text"]""",
                buttonSelector = """button[type="submit"]""",
                extractJs = """(function(){
                    var urls = [];
                    document.querySelectorAll('img').forEach(function(img) {
                        var src = img.src || '';
                        if (src && (src.includes('xhscdn') || src.includes('sns-webpic') || src.includes('xiaohongshu'))
                            && !src.includes('avatar') && !src.includes('icon') && !src.includes('logo')) {
                            urls.push(src);
                        }
                    });
                    return JSON.stringify({t:'i', u:urls.slice(0,20)});
                })()""",
            ),
        )

        // Try each site, return first success
        for (site in sites) {
            try {
                val result = tryWebViewSite(url, site)
                if (result.isSuccess) return result
            } catch (e: Exception) {
                // Continue to next site
            }
        }

        // Strategy 3: Direct xiaohongshu page load via WebView
        return try {
            loadXhsPageDirectly(url)
        } catch (e: Exception) {
            Result.failure(Exception("所有解析方式均失败: ${e.message}"))
        }
    }

    /**
     * Try direct HTTP request to extract images.
     * Looks for sns-webpic CDN URLs which are the actual content images.
     */
    private suspend fun tryDirectHttp(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val response = httpClient.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("空响应")

            val images = mutableListOf<String>()

            // 1. Look for sns-webpic CDN URLs (actual content images)
            val webpicPattern = Pattern.compile("""https?://sns-webpic[a-z0-9.-]*\.xhscdn\.com/[^\s"'<>\\]+""")
            val webpicMatcher = webpicPattern.matcher(html)
            while (webpicMatcher.find()) {
                val imgUrl = webpicMatcher.group() ?: continue
                // Skip watermarked versions (contain !nd_dft_)
                if (!imgUrl.contains("!nd_dft_")) {
                    images.add(imgUrl)
                }
            }

            // 2. Try __INITIAL_STATE__
            if (images.isEmpty()) {
                val statePattern = Pattern.compile("""window\.__INITIAL_STATE__\s*=\s*(\{.*?\})\s*""", Pattern.DOTALL)
                val stateMatcher = statePattern.matcher(html)
                if (stateMatcher.find()) {
                    val stateJson = stateMatcher.group(1)?.replace("\\u002F", "/")?.replace("\\u0026", "&") ?: ""
                    val imgPattern = Pattern.compile(""""urlDefault"\s*:\s*"([^"]+)"""")
                    val imgMatcher = imgPattern.matcher(stateJson)
                    while (imgMatcher.find()) {
                        val imgUrl = imgMatcher.group(1)?.replace("\\u002F", "/") ?: continue
                        if (isContentImage(imgUrl)) images.add(imgUrl)
                    }
                }
            }

            // 3. Try generic xhscdn URLs
            if (images.isEmpty()) {
                val cdnPattern = Pattern.compile("""https?://[a-z0-9.-]*xhscdn\.com/[^\s"'<>\\]+""")
                val cdnMatcher = cdnPattern.matcher(html)
                while (cdnMatcher.find()) {
                    val imgUrl = cdnMatcher.group() ?: continue
                    if (isContentImage(imgUrl) && !imgUrl.contains("!nd_dft_")) {
                        images.add(imgUrl)
                    }
                }
            }

            if (images.isNotEmpty()) {
                val title = extractTitle(html)
                Result.success(MediaResult(type = MediaType.IMAGE, title = title, images = images.distinct().take(20), source = "xiaohongshu"))
            } else {
                Result.failure(Exception("无法提取图片"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Try a parsing site via WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun tryWebViewSite(url: String, site: SiteConfig): Result<MediaResult> = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null
        var resumed = false

        val timeout = Runnable {
            if (!resumed && cont.isActive) {
                resumed = true
                handler.post { webView?.destroy() }
                cont.resume(Result.failure(Exception("${site.name} 超时")))
            }
        }
        handler.postDelayed(timeout, 15_000)

        handler.post {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                        super.onPageFinished(view, loadedUrl)
                        handler.postDelayed({
                            val escapedUrl = url.replace("'", "\\'")
                            view?.evaluateJavascript("""(function(){
                                var i=document.querySelector('${site.inputSelector}');
                                if(i){i.value='$escapedUrl';i.dispatchEvent(new Event('input',{bubbles:true}));return 'ok';}
                                return 'no_input';
                            })()""") {
                                handler.postDelayed({
                                    view?.evaluateJavascript("""(function(){
                                        var b=document.querySelector('${site.buttonSelector}');
                                        if(b){b.click();return 'ok';}
                                        return 'no_button';
                                    })()""") {
                                        handler.postDelayed({
                                            view?.evaluateJavascript(site.extractJs) { result ->
                                                handler.removeCallbacks(timeout)
                                                val parsed = parseResultJson(result)
                                                handler.post { webView?.destroy() }
                                                if (!resumed && cont.isActive) {
                                                    resumed = true
                                                    cont.resume(parsed)
                                                }
                                            }
                                        }, 6_000)
                                    }
                                }, 1_000)
                            }
                        }, 3_000)
                    }
                }
                loadUrl(site.url)
            }
        }

        cont.invokeOnCancellation {
            resumed = true
            handler.removeCallbacks(timeout)
            handler.post { webView?.destroy() }
        }
    }

    /**
     * Load xiaohongshu page directly in WebView.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun loadXhsPageDirectly(url: String): Result<MediaResult> = suspendCancellableCoroutine { cont ->
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
            document.querySelectorAll('img').forEach(function(img) {
                var src = img.src || '';
                if (src && (src.includes('xhscdn') || src.includes('sns-webpic') || src.includes('ci.xiaohongshu'))
                    && !src.includes('avatar') && !src.includes('icon') && !src.includes('logo')
                    && !src.includes('fe-platform') && !src.includes('fe-static')) {
                    urls.push(src);
                }
            });
            document.querySelectorAll('.swiper-slide img, [class*="slide"] img').forEach(function(img) {
                var src = img.src || '';
                if (src && !urls.includes(src) && (src.includes('xhscdn') || src.includes('sns-webpic'))) urls.push(src);
            });
            var title = document.title || '小红书笔记';
            return JSON.stringify({title: title, images: urls});
        })()""".trimIndent()

        fun tryExtract(attempt: Int, view: WebView?) {
            if (resumed || view == null) return
            view.evaluateJavascript(extractJs) { result ->
                val clean = result?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"")?.replace("\\\\", "\\") ?: ""
                val imagesMatch = """"images"\s*:\s*\[([^\]]*)]""".toRegex().find(clean)
                val imagesStr = imagesMatch?.groupValues?.get(1) ?: ""
                val images = """"([^"]+)"""".toRegex().findAll(imagesStr).map { it.groupValues[1] }.toList()
                    .filter { it.contains("xhscdn") || it.contains("sns-webpic") || it.contains("ci.xiaohongshu") }

                if (images.isNotEmpty()) {
                    val titleMatch = """"title"\s*:\s*"([^"]*?)"""".toRegex().find(clean)
                    val title = titleMatch?.groupValues?.get(1) ?: "小红书笔记"
                    if (!resumed && cont.isActive) {
                        resumed = true
                        handler.removeCallbacks(timeout)
                        handler.post { webView?.destroy() }
                        cont.resume(Result.success(MediaResult(type = MediaType.IMAGE, title = title, images = images.distinct().take(20), source = "xiaohongshu")))
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

    // ==================== Helpers ====================

    private fun parseResultJson(jsonStr: String?): Result<MediaResult> {
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

    private fun isContentImage(url: String): Boolean {
        val lower = url.lowercase()
        return !lower.contains("avatar") && !lower.contains("icon") && !lower.contains("logo")
                && !lower.contains("emoji") && !lower.contains("sticker")
                && !lower.contains("180x180") && !lower.contains("thumbnail")
                && !lower.contains("fe-platform") && !lower.contains("fe-static") && !lower.contains("fe-video")
    }

    private fun extractTitle(html: String): String {
        val titlePattern = Pattern.compile("""<title>([^<]+)</title>""")
        val matcher = titlePattern.matcher(html)
        return if (matcher.find()) matcher.group(1)?.replace(" - 小红书", "")?.trim() ?: "小红书笔记"
        else "小红书笔记"
    }
}

data class SiteConfig(
    val name: String,
    val url: String,
    val inputSelector: String,
    val buttonSelector: String,
    val extractJs: String,
)
