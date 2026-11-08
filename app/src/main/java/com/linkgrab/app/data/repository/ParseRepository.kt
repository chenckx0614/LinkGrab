package com.linkgrab.app.data.repository

import android.content.Context
import com.linkgrab.app.data.api.BilibiliParser
import com.linkgrab.app.data.api.CronetFetcher
import com.linkgrab.app.data.api.CronetPage
import com.linkgrab.app.data.api.DouyinParser
import com.linkgrab.app.data.api.UcmaoParser
import com.linkgrab.app.data.api.GenericWebParser
import com.linkgrab.app.data.api.WebViewParser
import com.linkgrab.app.data.api.WeiboParser
import com.linkgrab.app.data.api.XhsParser
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

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

        /** 抖音两条通道都失败后，第三方接口兜底的时间预算。 */
        const val DOUYIN_FALLBACK_BUDGET_MS = 8_000L

        /**
         * 原生通道「还没有任何结论」时最多等多久再起 WebView。
         *
         * 正常情况下这个分支根本走不到 —— 原生第一层被风控打空会立刻发信号，
         * 我们马上就起 WebView。只有网络本身慢（原生还卡在等响应）才会等满，
         * 那种情况下 WebView 也一样慢，所以不必提前抢跑。
         */
        const val DOUYIN_PROBE_WAIT_MS = 4_000L

        /**
         * 原生通道的总预算。超过就按失败处理，不让它把用户时间拖到十几秒 ——
         * 它后面还有 HTML SSR 完整重试这种长尾路径。
         */
        const val DOUYIN_NATIVE_TOTAL_BUDGET_MS = 10_000L

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
    private val ucmaoParser = UcmaoParser()
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
            Platform.DOUYIN -> parseDouyin(cleanUrl)
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

    /**
     * 抖音解析：原生 HTTP 通道与 WebView 真浏览器通道并行赛跑。
     *
     * ① 原生 HTTP 一条 slidesinfo 就能出结果，通常 1 秒内；
     * ② WebView 要装页面 + 跑 JS，慢，但被风控时最稳。
     *
     * 什么时候起 WebView 不再靠「等 N 秒没结果就猜它失败」——那个猜测两头都错：
     * 猜早了（1.5s）正常链接也被拖上 WebView（反而更慢），猜晚了风控链接就得干等
     * 原生把重试跑完。现在由原生自己在**第一层被风控打空的那一刻**发信号，WebView
     * 立刻起跑，两边谁先出结果用谁。
     *
     * 独立作用域是为了**输家立刻收掉**：协程取消阻塞中的 OkHttp 调用不会即时
     * 生效，若挂在调用方的作用域里，WebView 3 秒出结果也会被原生那条 12 秒
     * 读超时拖到 12 秒才返回。
     */
    private suspend fun parseDouyin(cleanUrl: String): Result<MediaResult> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val fastPathMissed = CompletableDeferred<Unit>()
            val native = scope.async {
                douyinParser.parse(cleanUrl) {
                    if (!fastPathMissed.isCompleted) fastPathMissed.complete(Unit)
                }
            }

            // 三种早期结局：0 = 原生已成功，1 = 原生第一层被截，2 = 原生已失败。
            // 一个都没等到就说明是网络本身慢（原生还卡在等响应上），此时起 WebView
            // 同样慢，先让它继续跑，最多再等 DOUYIN_PROBE_WAIT_MS。
            val early = withTimeoutOrNull(DOUYIN_PROBE_WAIT_MS) {
                select<Int> {
                    native.onAwait { if (it.isSuccess) 0 else 2 }
                    fastPathMissed.onAwait { 1 }
                }
            }
            if (early == 0) return native.await()
            if (early == 2) {
                val done = runCatching { native.await() }.getOrNull()
                if (done != null) {
                    if (done.isSuccess) return done
                    // 链接里根本没有作品 ID：WebView 也救不了，直接返回比白起一个
                    // 隐藏 WebView 更快。其它失败原因仍然让 WebView 试一把。
                    if (done.exceptionOrNull()?.message?.contains("作品 ID") == true) return done
                }
            }

            val web = scope.async {
                runCatching { legacyParser.parseDouyinInWebView(cleanUrl) }.getOrNull()
            }
            val results = Channel<MediaResult?>(Channel.UNLIMITED)
            // 原生通道的最终结论存这里。不能在收尾时再 await 一次 —— 那条协程
            // 已经被预算放弃，await 会重新挂起，等于把刚省下的时间又还回去。
            val nativeOutcome = AtomicReference<Result<MediaResult>?>(null)
            scope.launch {
                // 原生到点还没结论就按失败算，别让它把用户的时间拖到十几秒
                val r = withTimeoutOrNull(DOUYIN_NATIVE_TOTAL_BUDGET_MS) { native.await() }
                val outcome = r ?: Result.failure(Exception("抖音解析超时"))
                nativeOutcome.set(outcome)
                results.send(outcome.getOrNull())
            }
            scope.launch { results.send(runCatching { web.await() }.getOrNull()) }
            var received = 0
            var winner: MediaResult? = null
            while (winner == null && received < 2) {
                winner = results.receive()
                received++
            }
            if (winner != null) return Result.success(winner)

            // 两条通道都没成功 → 第三方接口兜底。
            // 只打 ucmao 一家，不再走 legacyParser.parse 的整条链 —— 那条链
            // 开头的 WebView 通道刚刚已经失败过，再跑一遍等于把 12 秒超时又付一次。
            val nativeResult = nativeOutcome.get()
                ?: Result.failure(Exception("抖音解析超时"))
            val nativeMessage = nativeResult.exceptionOrNull()?.message.orEmpty()
            val fallback = runCatching {
                withTimeoutOrNull(DOUYIN_FALLBACK_BUDGET_MS) { ucmaoParser.parse(cleanUrl) }
            }.getOrNull()
            val fallbackResult = fallback?.getOrNull()
            return if (fallback != null && fallback.isSuccess &&
                fallbackResult?.type == MediaType.VIDEO && "图集" in nativeMessage
            ) {
                // 原生已确认这是图集（接口被风控打薄）——降级链拿到的只可能是
                // 图集自带的预览视频，宁可失败也别给「点不动的下载视频」。
                nativeResult
            } else if (fallback?.isSuccess == true) {
                fallback
            } else {
                nativeResult
            }
        } finally {
            scope.cancel()
        }
    }
}