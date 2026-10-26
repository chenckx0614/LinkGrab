package com.linkgrab.app.data.repository

import android.content.Context
import com.linkgrab.app.data.api.BilibiliParser
import com.linkgrab.app.data.api.CronetFetcher
import com.linkgrab.app.data.api.CronetPage
import com.linkgrab.app.data.api.DouyinParser
import com.linkgrab.app.data.api.GenericWebParser
import com.linkgrab.app.data.api.WebViewParser
import com.linkgrab.app.data.api.WeiboParser
import com.linkgrab.app.data.api.XhsParser
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
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

    private companion object {
        val DOUYIN_HOST_PATTERN = """(douyin\.com|v\.douyin\.com|iesdouyin\.com)/""".toRegex()
        val XHS_HOST_PATTERN = """(xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)/""".toRegex()
        val BILIBILI_HOST_PATTERN = """(bilibili\.com|b23\.tv|bili2233\.cn)/""".toRegex()
        val WEIBO_HOST_PATTERN = """(weibo\.com|weibo\.cn|t\.cn)/""".toRegex()
        val HTTP_PATTERN = """^https?://""".toRegex()
        val KNOWN_URL_PATTERNS = listOf(
            """https?://(?:v\.douyin\.com|(?:\w+\.)?douyin\.com|(?:\w+\.)?iesdouyin\.com)(?:/[^\s\u3000\"'<>()（）【】\[\]{}]*)?""".toRegex(),
            """https?://(?:(?:www\.)?xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)(?:/[^\s\u3000\"'<>()（）【】\[\]{}]*)?""".toRegex(),
            """https?://(?:(?:www\.|m\.)?bilibili\.com|b23\.tv|bili2233\.cn)(?:/[^\s\u3000\"'<>()（）【】\[\]{}]*)?""".toRegex(),
            """https?://(?:(?:www\.|m\.)?weibo\.com|m\.weibo\.cn|weibo\.cn|t\.cn)(?:/[^\s\u3000\"'<>()（）【】\[\]{}]*)?""".toRegex(),
        )
        val GENERIC_URL_PATTERN = """https?://[^\s\u3000\"'<>()（）【】\[\]{}]+""".toRegex()

        /**
         * 笔记页的 SSR 数据标志。Shield 拦下来的页面（登录页 / 验证码页 / 404 页）
         * 都不会带这个字段，用它判定「这一发是否真的拿到了笔记数据」。
         */
        fun hasNotePayload(html: String): Boolean = "noteDetailMap" in html
    }

    private val douyinParser = DouyinParser()
    private val xhsParser = XhsParser()
    private val bilibiliParser = BilibiliParser()
    private val weiboParser = WeiboParser()
    private val genericWebParser = GenericWebParser()
    private val legacyParser = WebViewParser(context)
    private val cronetFetcher = CronetFetcher(context)

    /**
     * Detect which platform the URL belongs to.
     *
     * 任何 http(s) 链接都会落到 [Platform.WEB]：通用网页抓取（og:video / <video> /
     * 大图）本身就是一个功能，不再是「不支持的链接」。
     */
    fun detectPlatform(url: String): Platform {
        val cleanUrl = extractUrl(url).lowercase()
        return when {
            DOUYIN_HOST_PATTERN.containsMatchIn(cleanUrl) -> Platform.DOUYIN
            // 小红书域名必须覆盖短链变体：xhslink.com / xhslink.cn / xslink.com / xhs.link。
            // 缺少任一变体时，App 内复制的分享文案会被判成"不支持的链接"。
            XHS_HOST_PATTERN.containsMatchIn(cleanUrl) -> Platform.XIAOHONGSHU
            BILIBILI_HOST_PATTERN.containsMatchIn(cleanUrl) -> Platform.BILIBILI
            WEIBO_HOST_PATTERN.containsMatchIn(cleanUrl) -> Platform.WEIBO
            HTTP_PATTERN.containsMatchIn(cleanUrl) -> Platform.WEB
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
                if (native.isSuccess) native
                else {
                    val nativeMessage = native.exceptionOrNull()?.message.orEmpty()
                    val fallback = legacyParser.parse(cleanUrl)
                    val fallbackResult = fallback.getOrNull()
                    if (fallback.isSuccess && fallbackResult?.type == MediaType.VIDEO && "图集" in nativeMessage) {
                        // 原生解析已确认这是图集（接口被风控打薄）——降级链拿到的
                        // 只可能是图集自带的预览视频。以 VIDEO 返回的话结果页只能
                        // 看一张封面（用户反馈的「抖音解析后只显示封面」就是这条
                        // 路径），宁可失败也别给假结果。
                        native
                    } else if (fallback.isSuccess) fallback else native
                }
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
                //
                // 光有 Chrome 的 TLS 指纹还不够：Shield 还会比对 UA 与 sec-ch-ua。
                // UA 自称 Chrome 却不发 Client Hints、或两者版本号对不上，都会被
                // 判为伪造客户端 302 到登录页。头统一由 CronetFetcher.browserHeaders
                // 生成（详见那里的实测记录）。

                val cookie = runCatching { xhsParser.sessionCookieHeader() }.getOrNull()
                fun headers(userAgent: String): Map<String, String> =
                    CronetFetcher.browserHeaders(userAgent) +
                        (cookie?.let { mapOf("Cookie" to it) } ?: emptyMap())

                /**
                 * 取第一个真正含 SSR 数据的结果：先桌面 UA，拿不到再试移动端。
                 *
                 * 串行而不是并行：实测（2026-10，两条真实分享链接）桌面 UA 稳定拿到
                 * SSR，移动端 UA 反而会被重定向到无数据的移动端壳页。并行等于每次
                 * 解析都多发一个注定失败的请求，白白增加被风控计数的机会。
                 * 移动端仍保留为兜底 —— 个别笔记确实只对移动端下发完整数据。
                 */
                suspend fun race(url: String): CronetPage? {
                    for (ua in listOf(CronetFetcher.DESKTOP_CHROME_UA, CronetFetcher.MOBILE_CHROME_UA)) {
                        val page = runCatching { cronetFetcher.getPage(url, headers(ua)) }.getOrNull()
                        if (page?.html?.let(::hasNotePayload) == true) return page
                    }
                    return null
                }

                // 短链先展开（OkHttp 这一环不做指纹检测，最便宜），展开不成也让
                // Cronet 自己跟重定向 —— getPage 会带回最终 URL。
                val expanded = runCatching { xhsParser.expandShareLink(cleanUrl) }.getOrDefault(cleanUrl)
                val candidates = listOf(expanded, cleanUrl)
                    .filter { xhsParser.isNoteUrl(it) || it == cleanUrl }
                    .distinct()

                var lastFinalUrl: String? = null
                for (start in candidates) {
                    val page = race(start)
                    if (page != null) {
                        xhsParser.parseHtml(page.html)?.let { return@withContext Result.success(it) }
                    }
                    lastFinalUrl = page?.finalUrl ?: lastFinalUrl

                    // 小红书同一笔记有 explore / discovery/item 两套 SSR 页面。
                    // 某些网络环境会只拦其中一套，切换页面入口但保留原始签名参数，
                    // 比直接重试同一个 URL 更有效。
                    val alternate = xhsParser.alternateNoteUrl(start) ?: continue
                    race(alternate)?.let { alt ->
                        xhsParser.parseHtml(alt.html)?.let { return@withContext Result.success(it) }
                    }
                }

                val noteUrl = candidates.firstOrNull() ?: cleanUrl

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
                else Result.failure(
                    Exception(
                        buildString {
                            append("小红书解析失败")
                            append("（Cronet ${if (cronetFetcher.available) "已启用" else "不可用"}，")
                            append("Chromium ${CronetFetcher.chromiumMajor}）")
                            lastFinalUrl?.let { append("；最终落地页：${it.take(60)}") }
                            append("；WebView：${viaWeb?.exceptionOrNull()?.message ?: "无结果"}")
                            append("；直连：${native.exceptionOrNull()?.message ?: "无结果"}")
                        }
                    )
                )
            }
            Platform.BILIBILI -> bilibiliParser.parse(cleanUrl)
            Platform.WEIBO -> weiboParser.parse(cleanUrl)
            Platform.WEB -> genericWebParser.parse(cleanUrl)
            Platform.UNKNOWN -> Result.failure(Exception("没有识别到可解析的链接"))
        }
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
        for (pattern in KNOWN_URL_PATTERNS) {
            pattern.find(trimmed)?.let { return cleanUrl(it.value) }
        }

        // Generic URL fallback：更宽松，但只取到常见结束标点/分隔符之前。
        GENERIC_URL_PATTERN.find(trimmed)?.let { return cleanUrl(it.value) }

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
