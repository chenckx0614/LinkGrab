package com.linkgrab.app.data.repository

import android.content.Context
import com.linkgrab.app.data.api.CronetFetcher
import com.linkgrab.app.data.api.DouyinParser
import com.linkgrab.app.data.api.WebViewParser
import com.linkgrab.app.data.api.XhsParser
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parse pipeline (v2):
 *
 * 1. Native parsers ported from wujunwei928/parse-video-py — fast, direct
 *    HTTP, no third-party web service, and they expose Live Photo streams.
 * 2. Legacy chain (Ucmao API → direct HTML → third-party WebView pages) is
 *    kept as a fallback for edge cases the native parsers cannot handle.
 */
class ParseRepository(context: Context) {

    private val douyinParser = DouyinParser()
    private val xhsParser = XhsParser()
    private val legacyParser = WebViewParser(context)
    private val cronetFetcher = CronetFetcher(context)

    /**
     * Detect which platform the URL belongs to.
     */
    fun detectPlatform(url: String): Platform {
        val cleanUrl = extractUrl(url).lowercase()
        return when {
            """(douyin\.com|v\.douyin\.com|iesdouyin\.com)/""".toRegex().containsMatchIn(cleanUrl) -> Platform.DOUYIN
            // 小红书域名必须覆盖短链变体：xhslink.com / xhslink.cn / xslink.com / xhs.link。
            // 缺少任一变体时，App 内复制的分享文案会被判成"不支持的链接"。
            """(xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)/""".toRegex()
                .containsMatchIn(cleanUrl) -> Platform.XIAOHONGSHU
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Parse media from a share URL. Native parser first, legacy chain as fallback.
     */
    suspend fun parse(url: String): Result<MediaResult> {
        val cleanUrl = extractUrl(url)
        return when (detectPlatform(cleanUrl)) {
            Platform.DOUYIN -> {
                val native = douyinParser.parse(cleanUrl)
                if (native.isSuccess) native else parseWithFallback(native) { legacyParser.parse(cleanUrl) }
            }
            Platform.XIAOHONGSHU -> withContext(Dispatchers.IO) {
                // 注意：整个 XHS 分支必须在 IO 线程 —— expandShareLink 是阻塞
                // 网络请求，在主线程协程里调用会抛 NetworkOnMainThreadException。
                //
                // 小红书 Shield 按 TLS 指纹识别客户端：OkHttp / HttpURLConnection
                // 访问笔记页会被 302 到登录页。主通道采用高星项目
                // JoeanAmier/XHS-Downloader 的方案：用 Chrome 同源的 TLS 指纹
                // （其 curl_cffi impersonate="chrome*" / Android 侧的 Cronet）
                // 直接 GET 笔记页，不签名、不需要 Cookie，从 SSR 取数据。
                val noteUrl = xhsParser.expandShareLink(cleanUrl)

                // 主通道：Cronet（Chrome TLS）直取笔记页 HTML → 复用同一份 SSR 解析。
                // 必须携带 a1/webId 匿名会话 Cookie：小红书对无 Cookie 请求会返回
                // 验证壳页，之前主通道没带它，是「偶尔解析失败」的主因之一。
                // 桌面 UA 拿不到内容时再用移动端 Chrome UA 重试一次（部分笔记只对
                // 移动端渲染完整 SSR 数据）。
                suspend fun cronetParse(userAgent: String): MediaResult? = runCatching {
                    cronetFetcher.getHtml(
                        noteUrl,
                        cronetFetcher.browserHeaders(userAgent) +
                            ("Cookie" to xhsParser.sessionCookieHeader()),
                    )?.let { html -> xhsParser.parseHtml(html) }
                }.getOrNull()

                val viaCronet = cronetParse(CronetFetcher.DESKTOP_CHROME_UA)
                    ?: cronetParse(CronetFetcher.MOBILE_CHROME_UA)
                if (viaCronet != null) return@withContext Result.success(viaCronet)

                // 兜底 1：WebView 直载笔记页（同样走 Chromium 网络栈，但会执行 JS）
                val viaWeb = runCatching { legacyParser.parseXhsNoteInWebView(noteUrl) }.getOrNull()
                if (viaWeb != null && viaWeb.isSuccess) return@withContext viaWeb

                // 兜底 2：OkHttp 直连（部分网络环境未被指纹墙拦截时可用，更快）
                val native = xhsParser.parse(noteUrl)
                if (native.isSuccess) return@withContext native

                // 兜底 3：旧降级链
                val legacy = runCatching { legacyParser.parse(noteUrl) }.getOrNull()
                    ?: Result.failure(Exception("解析失败"))
                if (legacy.isSuccess) legacy
                else Result.failure(Exception(
                    "小红书解析失败：Cronet通道无结果（${if (cronetFetcher.available) "已启用" else "不可用"}）；" +
                        "WebView通道：${viaWeb?.exceptionOrNull()?.message ?: "无结果"}；" +
                        "直连：${native.exceptionOrNull()?.message ?: "无结果"}"
                ))
            }
            Platform.UNKNOWN -> Result.failure(Exception("不支持的链接格式，请输入抖音或小红书链接"))
        }
    }

    /**
     * 降级链的失败信息往往更含糊（例如它自己的平台识别不认识某个短链变体时，
     * 会报「不支持的链接格式」），而原生解析的错误才是真实原因。
     * 降级也失败时优先回传原生错误，别让它被盖掉。
     */
    private inline fun parseWithFallback(
        native: Result<MediaResult>,
        legacy: () -> Result<MediaResult>,
    ): Result<MediaResult> {
        val fallback = legacy()
        return if (fallback.isSuccess) fallback else native
    }

    /**
     * Extract URL from text that may contain share口令 (password/command text).
     *
     * Examples of input:
     *   "7.89 Lhb:/ 复制打开抖音，看看【xxx】的作品 https://v.douyin.com/iRNBho6u/"
     *   "3.27 Hhb:/ https://www.xiaohongshu.com/explore/xxx?xsec_token=abc"
     *   "https://v.douyin.com/iRNBho6u/"
     *   "复制这条信息，打开【小红书】App查看【...】（ https://xhslink.com/a/xxx ）"
     */
    private fun extractUrl(text: String): String {
        val trimmed = text.trim()

        // 优先匹配已知的平台域名。允许 URL 被中文括号、方括号、引号包裹。
        val knownPatterns = listOf(
            // 抖音
            """https?://(?:v\.douyin\.com|(?:\w+\.)?douyin\.com|(?:\w+\.)?iesdouyin\.com)(?:/[^\s\u3000"'<>()（）【】\[\]{}]*)?""",
            // 小红书（含短链变体）
            """https?://(?:(?:www\.)?xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)(?:/[^\s\u3000"'<>()（）【】\[\]{}]*)?""",
        )
        for (pattern in knownPatterns) {
            pattern.toRegex().find(trimmed)?.let { return cleanUrl(it.value) }
        }

        // Generic URL fallback：更宽松，但只取到常见结束标点/分隔符之前。
        val genericPattern = """https?://[^\s\u3000"'<>()（）【】\[\]{}]+""".toRegex()
        genericPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // No URL found, return as-is
        return trimmed
    }

    /**
     * Clean up extracted URL: remove trailing punctuation/brackets that's not part of the URL.
     * Also decode common HTML entities and strip U+002F escapes left by some share texts.
     */
    private fun cleanUrl(url: String): String {
        return url
            .trim()
            .trimEnd(
                '.', ',', ';', '!', '?', ')', ']', '}', '。', '，', '；', '！', '？', '）', '】', '」', '』',
                ':', '：', '、',
            )
            .replace("\\u002F", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
    }
}
