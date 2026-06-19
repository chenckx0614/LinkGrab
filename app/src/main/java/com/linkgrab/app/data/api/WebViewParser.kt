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
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * Multi-strategy parser:
 * - Douyin: Ucmao API → Direct HTML → WebView(peanutdl)
 * - Xiaohongshu: WebView(tools.emmmm.dev) → Direct HTML
 */
class WebViewParser(private val context: Context) {

    private val ucmaoParser = UcmaoParser()

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

    // ==================== Xiaohongshu ====================

    private suspend fun parseXiaohongshu(url: String): Result<MediaResult> {
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
            val directResult = parseXiaohongshuDirect(url)
            if (directResult.isSuccess) directResult
            else Result.failure(Exception("解析小红书失败: ${e.message}"))
        }
    }

    // ==================== Direct HTML Parsing ====================

    private suspend fun parseDouyinDirect(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://www.douyin.com/")
                .build()
            val response = client.newCall(request).execute()
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

    private suspend fun parseXiaohongshuDirect(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://www.xiaohongshu.com/")
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: throw Exception("空响应")

            val imagePattern = Pattern.compile("""https?://[^\s"'<>]+\.(?:jpg|jpeg|png|webp)[^\s"'<>]*""")
            val matcher = imagePattern.matcher(html)
            val images = mutableListOf<String>()
            while (matcher.find()) {
                val imgUrl = matcher.group()
                if (!imgUrl.contains("avatar") && !imgUrl.contains("icon") && !imgUrl.contains("logo")
                    && !imgUrl.contains("emoji") && !imgUrl.contains("sticker")
                    && !imgUrl.contains("180x180") && !imgUrl.contains("thumbnail")) {
                    images.add(imgUrl)
                }
            }
            if (images.isNotEmpty()) {
                return@withContext Result.success(MediaResult(type = MediaType.IMAGE, title = "小红书笔记", images = images.distinct().take(20), source = "xiaohongshu"))
            }
            Result.failure(Exception("无法从页面提取图片链接"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== WebView ====================

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun parseWithWebView(
        pageUrl: String, inputSelector: String, urlToInput: String,
        buttonSelector: String, resultJs: String,
    ): Result<MediaResult> = suspendCancellableCoroutine { cont ->
        val handler = Handler(Looper.getMainLooper())
        var webView: WebView? = null

        val timeout = Runnable {
            if (cont.isActive) {
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
                            view?.evaluateJavascript("""(function(){
                                var i=document.querySelector('$inputSelector');
                                if(i){i.value='$urlToInput';i.dispatchEvent(new Event('input',{bubbles:true}));return 'ok';}
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
                                                if (cont.isActive) cont.resume(parsed)
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
