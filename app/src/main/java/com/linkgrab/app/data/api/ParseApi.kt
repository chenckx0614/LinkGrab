package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

class ParseApi {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        // URL patterns
        private val DOUYIN_PATTERN = Pattern.compile(
            """(https?://)?(www\.)?(douyin\.com|v\.douyin\.com|iesdouyin\.com)/.+"""
        )
        private val XIAOHONGSHU_PATTERN = Pattern.compile(
            """(https?://)?(www\.)?(xiaohongshu\.com|xhslink\.com|xslink\.com)/.+"""
        )
    }

    /**
     * Detect platform from URL
     */
    fun detectPlatform(url: String): Platform {
        val normalizedUrl = url.lowercase().trim()
        return when {
            DOUYIN_PATTERN.matcher(normalizedUrl).matches() -> Platform.DOUYIN
            XIAOHONGSHU_PATTERN.matcher(normalizedUrl).matches() -> Platform.XIAOHONGSHU
            else -> Platform.UNKNOWN
        }
    }

    /**
     * Parse media from URL
     */
    suspend fun parse(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val platform = detectPlatform(url)

            // Step 1: Resolve short URL to get the real URL
            val realUrl = resolveShortUrl(url)

            // Step 2: Parse based on platform
            val result = when (platform) {
                Platform.DOUYIN -> parseDouyin(realUrl)
                Platform.XIAOHONGSHU -> parseXiaohongshu(realUrl)
                Platform.UNKNOWN -> throw Exception("不支持的链接格式")
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve short URL redirects to get the real URL
     */
    private fun resolveShortUrl(url: String): String {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .head()
                .build()

            val response = client.newCall(request).execute()
            response.request.url.toString()
        } catch (e: Exception) {
            url
        }
    }

    /**
     * Parse Douyin video link
     * Extracts video information from the Douyin page
     */
    private fun parseDouyin(url: String): MediaResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://www.douyin.com/")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("无法获取页面内容")

        // Try to extract video URL from HTML
        // Look for video source in the page
        val videoPattern = Pattern.compile("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""")
        val videoMatcher = videoPattern.matcher(html)

        if (videoMatcher.find()) {
            val videoUrl = videoMatcher.group()
            return MediaResult(
                type = MediaType.VIDEO,
                title = "抖音视频",
                videoUrl = videoUrl,
                source = "douyin"
            )
        }

        // Try to extract from JSON data
        val jsonPattern = Pattern.compile("""playAddr.*?"url_list":\s*\["(https?://[^"]+)"""")
        val jsonMatcher = jsonPattern.matcher(html)

        if (jsonMatcher.find()) {
            val videoUrl = jsonMatcher.group(1)
                ?.replace("\\u002F", "/")
                ?.replace("\\/", "/")
            return MediaResult(
                type = MediaType.VIDEO,
                title = "抖音视频",
                videoUrl = videoUrl,
                source = "douyin"
            )
        }

        throw Exception("无法解析抖音视频，请检查链接是否正确")
    }

    /**
     * Parse Xiaohongshu note link
     * Extracts image information from the Xiaohongshu page
     */
    private fun parseXiaohongshu(url: String): MediaResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://www.xiaohongshu.com/")
            .build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("无法获取页面内容")

        // Try to extract image URLs from HTML
        // Look for high-quality images (avoid avatars, icons, etc.)
        val imagePattern = Pattern.compile("""https?://[^\s"'<>]+\.(?:jpg|jpeg|png|webp)[^\s"'<>]*""")
        val matcher = imagePattern.matcher(html)
        val images = mutableListOf<String>()

        while (matcher.find()) {
            val imgUrl = matcher.group()
            // Filter out small images, avatars, icons
            if (!imgUrl.contains("avatar") &&
                !imgUrl.contains("icon") &&
                !imgUrl.contains("logo") &&
                !imgUrl.contains("emoji") &&
                !imgUrl.contains("sticker") &&
                !imgUrl.contains("180x180") &&
                !imgUrl.contains("thumbnail")
            ) {
                images.add(imgUrl)
            }
        }

        if (images.isNotEmpty()) {
            // Remove duplicates and take up to 20 images
            val uniqueImages = images.distinct().take(20)
            return MediaResult(
                type = MediaType.IMAGE,
                title = "小红书笔记",
                images = uniqueImages,
                source = "xiaohongshu"
            )
        }

        throw Exception("无法解析小红书笔记，请检查链接是否正确")
    }
}
