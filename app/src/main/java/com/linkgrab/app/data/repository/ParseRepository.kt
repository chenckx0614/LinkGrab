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
            """(xiaohongshu\.com|xhslink\.com|xslink\.com)/""".toRegex().containsMatchIn(cleanUrl) -> Platform.XIAOHONGSHU
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
                if (native.isSuccess) native else legacyParser.parse(cleanUrl)
            }
            Platform.XIAOHONGSHU -> {
                val native = xhsParser.parse(cleanUrl)
                if (native.isSuccess) native else legacyParser.parse(cleanUrl)
            }
            Platform.UNKNOWN -> Result.failure(Exception("不支持的链接格式，请输入抖音或小红书链接"))
        }
    }

    /**
     * Extract URL from text that may contain share口令 (password/command text).
     *
     * Examples of input:
     *   "7.89 Lhb:/ 复制打开抖音，看看【xxx】的作品 https://v.douyin.com/iRNBho6u/"
     *   "3.27 Hhb:/ https://www.xiaohongshu.com/explore/xxx?xsec_token=abc"
     *   "https://v.douyin.com/iRNBho6u/"
     */
    private fun extractUrl(text: String): String {
        val trimmed = text.trim()

        // Try to find douyin URL first (v.douyin.com short links)
        val douyinPattern = """https?://(?:v\.douyin\.com|www\.douyin\.com|iesdouyin\.com)[^\s"'<>]*""".toRegex()
        douyinPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // Try to find xiaohongshu URL (including xhslink)
        val xhsPattern = """https?://(?:www\.xiaohongshu\.com|xhslink\.com|xhslink\.cn|xslink\.com)[^\s"'<>]*""".toRegex()
        xhsPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // Generic URL fallback
        val genericPattern = """https?://[^\s"'<>]+""".toRegex()
        genericPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // No URL found, return as-is
        return trimmed
    }

    /**
     * Clean up extracted URL: remove trailing punctuation that's not part of the URL.
     */
    private fun cleanUrl(url: String): String {
        return url.trimEnd(
            '.', ',', ';', '!', '?', ')', ']', '}', '。', '，', '；', '！', '？', '）'
        )
    }
}
