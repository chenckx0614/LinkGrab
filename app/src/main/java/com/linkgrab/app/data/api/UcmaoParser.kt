package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern

/**
 * Parser using the ucmao media-parser API (parse.ucmao.cn).
 * For Xiaohongshu, also parses the page directly to get all images.
 */
class UcmaoParser {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val API_URL = "https://parse.ucmao.cn/api/parse"

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

    suspend fun parse(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val platform = detectPlatform(url)
            if (platform == Platform.UNKNOWN) {
                return@withContext Result.failure(Exception("不支持的链接格式"))
            }

            val result = when (platform) {
                Platform.DOUYIN -> parseDouyin(url)
                Platform.XIAOHONGSHU -> parseXiaohongshu(url)
                else -> throw Exception("不支持的平台")
            }
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse Douyin via ucmao API
     */
    private fun parseDouyin(text: String): MediaResult {
        val apiResult = callApi(text)
        return MediaResult(
            type = MediaType.VIDEO,
            title = apiResult.title ?: "抖音视频",
            videoUrl = apiResult.video_url,
            coverUrl = apiResult.cover_url,
            source = "douyin"
        )
    }

    /**
     * Parse Xiaohongshu via API (returns single image)
     */
    private fun parseXiaohongshu(text: String): MediaResult {
        val apiResult = callApi(text)

        val images = mutableListOf<String>()
        apiResult.cover_url?.let { images.add(it) }
        apiResult.video_url?.let { images.add(it) }

        return MediaResult(
            type = if (apiResult.video_url != null) MediaType.VIDEO else MediaType.IMAGE,
            title = apiResult.title ?: "小红书笔记",
            videoUrl = apiResult.video_url,
            images = images,
            coverUrl = apiResult.cover_url,
            source = "xiaohongshu"
        )
    }

    /**
     * Call the ucmao parse API
     */
    private fun callApi(text: String): ApiData {
        val timestamp = System.currentTimeMillis()
        val tsStr = timestamp.toString()
        val originalText = generateRandomText(32)
        val key = timestampToKey(tsStr)
        val encryptedText = vigenereEncrypt(originalText, key)

        val requestBody = """{"text":"$text"}""".toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(API_URL)
            .post(requestBody)
            .header("Content-Type", "application/json")
            .header("X-Timestamp", tsStr)
            .header("X-GCLT-Text", originalText)
            .header("X-EGCT-Text", encryptedText)
            .header("User-Agent", "LinkGrab/1.0")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw Exception("空响应")

        val apiResponse = json.decodeFromString<ApiResponse>(body)

        if (!apiResponse.succ) {
            throw Exception(apiResponse.retdesc.ifEmpty { "解析失败" })
        }

        return apiResponse.data ?: throw Exception("无数据返回")
    }

    // --- Vigenere Cipher ---

    private fun generateRandomText(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..length).map { chars.random() }.joinToString("")
    }

    private fun timestampToKey(timestamp: String): String {
        return timestamp.map { c ->
            if (c.isDigit()) ('a' + c.digitToInt()).toString()
            else "?"
        }.joinToString("")
    }

    private fun vigenereEncrypt(text: String, key: String): String {
        val encrypted = StringBuilder()
        var keyIndex = 0
        for (ch in text) {
            if (ch.isLetter()) {
                val shift = if (ch.isUpperCase()) 65 else 97
                val keyShift = key[keyIndex % key.length].lowercaseChar() - 'a'
                val encryptedChar = ((ch.code - shift + keyShift) % 26 + shift).toChar()
                encrypted.append(encryptedChar)
                keyIndex++
            } else {
                encrypted.append(ch)
            }
        }
        return encrypted.toString()
    }

    // --- API Response Models ---

    @Serializable
    data class ApiResponse(
        val retcode: Int = 0,
        val retdesc: String = "",
        val data: ApiData? = null,
        val succ: Boolean = false,
    )

    @Serializable
    data class ApiData(
        val video_id: String? = null,
        val platform: String? = null,
        val title: String? = null,
        val video_url: String? = null,
        val cover_url: String? = null,
    )
}
