package com.linkgrab.app.data.repository

import android.content.Context
import com.linkgrab.app.data.api.DouyinParser
import com.linkgrab.app.data.api.WebViewParser
import com.linkgrab.app.data.api.XhsParser
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.Platform

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
            Platform.XIAOHONGSHU -> {
                val native = xhsParser.parse(cleanUrl)
                if (native.isSuccess) native else parseWithFallback(native) { legacyParser.parse(cleanUrl) }
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
