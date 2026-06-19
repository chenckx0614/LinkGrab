package com.linkgrab.app.data.repository

import android.content.Context
import com.linkgrab.app.data.api.WebViewParser
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.Platform

class ParseRepository(context: Context) {

    private val webViewParser = WebViewParser(context)

    /**
     * Detect which platform the URL belongs to
     */
    fun detectPlatform(url: String): Platform {
        val cleanUrl = extractUrl(url)
        return webViewParser.detectPlatform(cleanUrl)
    }

    /**
     * Parse media from share URL
     */
    suspend fun parse(url: String): Result<MediaResult> {
        val cleanUrl = extractUrl(url)
        return webViewParser.parse(cleanUrl)
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
        val xhsPattern = """https?://(?:www\.xiaohongshu\.com|xhslink\.com|xslink\.com)[^\s"'<>]*""".toRegex()
        xhsPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // Generic URL fallback
        val genericPattern = """https?://[^\s"'<>]+""".toRegex()
        genericPattern.find(trimmed)?.let { return cleanUrl(it.value) }

        // No URL found, return as-is
        return trimmed
    }

    /**
     * Clean up extracted URL: remove trailing punctuation that's not part of the URL
     */
    private fun cleanUrl(url: String): String {
        return url.trimEnd(
            '.', ',', ';', '!', '?', ')', ']', '}', '。', '，', '；', '！', '？', '）'
        )
    }
}
