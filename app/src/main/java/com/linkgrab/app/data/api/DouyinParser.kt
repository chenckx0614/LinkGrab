package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Native Douyin parser ported from wujunwei928/parse-video-py (parser/douyin.py).
 *
 * Flow:
 * 1. Resolve the share link to an aweme id:
 *    - v.douyin.com/xxx  → GET without redirects, read the `location` header
 *    - www.douyin.com / iesdouyin.com links → take the last path segment
 *      (or the `modal_id` query param)
 * 2. GET https://www.iesdouyin.com/share/video/{id}/ and extract the
 *    `window._ROUTER_DATA = {...}</script>` JSON blob.
 * 3. Read `loaderData["video_(id)/page" | "note_(id)/page"].videoInfoRes
 *    .item_list[0]`:
 *    - video  → video.play_addr.url_list[0], rewrite `playwm` → `play`
 *               (watermark-free), then follow the 302 to the final mp4.
 *    - images → images[].url_list (prefer non-webp), and for Live Photos
 *               images[].video.play_addr.url_list[0].
 *
 * No signature (X-Bogus / a_bogus) and no Cookie are required.
 */
class DouyinParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val defaultUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    suspend fun parse(shareUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val videoId = resolveVideoId(shareUrl)
                ?: return@withContext Result.failure(Exception("无法从链接中提取抖音作品 ID"))

            val pageUrl = "https://www.iesdouyin.com/share/video/$videoId/"
            val html = get(pageUrl, followRedirects = true)

            // window._ROUTER_DATA = {...}</script>
            val routerJson = Regex(
                """window\._ROUTER_DATA\s*=\s*(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)?.groupValues?.get(1)?.trim()
                ?: return@withContext Result.failure(Exception("页面数据结构解析失败（_ROUTER_DATA 不存在）"))

            val root = json.parseToJsonElement(routerJson).jsonObject
            val loaderData = root["loaderData"]?.jsonObject
                ?: return@withContext Result.failure(Exception("页面数据结构异常（loaderData 缺失）"))

            val videoInfoRes = (
                loaderData["video_(id)/page"]?.jsonObject?.get("videoInfoRes")
                    ?: loaderData["note_(id)/page"]?.jsonObject?.get("videoInfoRes")
                )?.jsonObject
                ?: return@withContext Result.failure(Exception("未找到作品信息"))

            val itemList = videoInfoRes["item_list"]?.jsonArray
            if (itemList == null || itemList.isEmpty()) {
                val filterMsg = videoInfoRes["filter_list"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("detail_msg")?.jsonPrimitive?.content
                return@withContext Result.failure(Exception(filterMsg ?: "作品不存在或已被删除"))
            }
            val data = itemList[0].jsonObject

            // ---- images (图集) + LivePhoto ----
            val images = mutableListOf<String>()
            val livePhotos = mutableMapOf<Int, String>()
            data["images"]?.jsonArray?.forEachIndexed { index, imgEl ->
                val img = imgEl.jsonObject
                val urlList = img["url_list"]?.jsonArray ?: return@forEachIndexed
                val imageUrl = preferNonWebp(urlList) ?: return@forEachIndexed
                images.add(imageUrl)
                // Live Photo: 实况照片的视频流
                val liveUrl = img["video"]?.jsonObject
                    ?.get("play_addr")?.jsonObject
                    ?.get("url_list")?.jsonArray
                    ?.firstOrNull()?.jsonPrimitive?.content
                if (!liveUrl.isNullOrEmpty()) {
                    livePhotos[index] = liveUrl
                }
            }

            if (images.isNotEmpty()) {
                val title = data["desc"]?.jsonPrimitive?.content ?: "抖音图文"
                val cover = data["video"]?.jsonObject
                    ?.get("cover")?.jsonObject
                    ?.get("url_list")?.jsonArray
                    ?.let { preferNonWebp(it) }
                return@withContext Result.success(
                    MediaResult(
                        type = MediaType.IMAGE,
                        title = title,
                        images = images,
                        coverUrl = cover ?: images.first(),
                        livePhotos = livePhotos,
                        source = "douyin",
                    )
                )
            }

            // ---- video (视频) ----
            val playUrl = data["video"]?.jsonObject
                ?.get("play_addr")?.jsonObject
                ?.get("url_list")?.jsonArray
                ?.firstOrNull()?.jsonPrimitive?.content
                ?.replace("playwm", "play")
                ?: return@withContext Result.failure(Exception("未找到视频播放地址"))

            // Follow the 302 to the final mp4 (西瓜视频 may have no redirect).
            val finalUrl = resolveRedirect(playUrl)

            val cover = data["video"]?.jsonObject
                ?.get("cover")?.jsonObject
                ?.get("url_list")?.jsonArray
                ?.let { preferNonWebp(it) }

            Result.success(
                MediaResult(
                    type = MediaType.VIDEO,
                    title = data["desc"]?.jsonPrimitive?.content ?: "抖音视频",
                    videoUrl = finalUrl,
                    coverUrl = cover,
                    source = "douyin",
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("抖音解析失败: ${e.message}"))
        }
    }

    /**
     * Resolve an aweme id from any supported share link form.
     */
    private fun resolveVideoId(url: String): String? {
        val lower = url.lowercase()
        return when {
            "v.douyin.com" in lower -> {
                // Short link: GET without redirects, parse the location header.
                val location = try {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", defaultUa)
                        .build()
                    noRedirectClient.newCall(request).execute().use { resp ->
                        resp.header("location")
                    }
                } catch (e: Exception) { null } ?: return null
                if ("ixigua.com" in location) return null
                extractIdFromPath(location)
            }
            else -> extractIdFromPath(url)
        }
    }

    /**
     * Last non-empty path segment, or the `modal_id` query param
     * (https://www.douyin.com/jingxuan?modal_id=xxx).
     */
    private fun extractIdFromPath(url: String): String? {
        Regex("""[?&]modal_id=(\d+)""").find(url)?.let { return it.groupValues[1] }
        val path = url.substringBefore('?').trimEnd('/')
        val segment = path.substringAfterLast('/')
        return segment.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    }

    /**
     * Prefer the first url_list entry that is not a .webp asset.
     */
    private fun preferNonWebp(urlList: JsonArray): String? {
        val urls = urlList.map { it.jsonPrimitive.content }.filter { it.isNotEmpty() }
        return urls.firstOrNull { !it.endsWith(".webp") } ?: urls.firstOrNull()
    }

    /**
     * GET helper with explicit redirect policy control.
     */
    private fun get(url: String, followRedirects: Boolean): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", defaultUa)
            .header("Referer", "https://www.douyin.com/")
            .build()
        val http = if (followRedirects) client else noRedirectClient
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("空响应")
        }
    }

    /**
     * Resolve the final location of a (possibly 302) video url.
     */
    private fun resolveRedirect(url: String): String {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", defaultUa)
                .build()
            noRedirectClient.newCall(request).execute().use { resp ->
                resp.header("location") ?: url
            }
        } catch (e: Exception) {
            url
        }
    }
}
