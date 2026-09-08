package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
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
 * 2. GET the `slidesinfo` web API (works for both videos and image albums;
 *    albums additionally need `request_source=200`). The legacy HTML SSR
 *    page is kept as a fallback.
 * 3. Read the first `aweme_details` entry:
 *    - images → images[] (album). `download_url_list` is preferred because it
 *      is the watermark-free original, otherwise `url_list` is used.
 *      Live Photos expose images[].video.play_addr.url_list[0].
 *    - video  → video.play_addr.url_list[0], rewrite `playwm` → `play`
 *               (watermark-free), then follow the 302 to the final mp4.
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

    private val mobileUa =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    suspend fun parse(shareUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val videoId = resolveVideoId(shareUrl)
                ?: return@withContext Result.failure(Exception("无法从链接中提取抖音作品 ID"))

            // 优先走 slidesinfo 专用接口（SSR 页面的 videoInfoRes 字段已下线，
            // 图文 note 需要带 request_source=200 才返回数据）。接口失败时
            // 回退到旧的 HTML SSR 解析。
            val data = fetchAwemeDetail(videoId)
                ?: fetchAwemeDetailFromHtml(videoId)
                ?: return@withContext Result.failure(Exception("未找到作品信息"))

            // ---- images (图集) + LivePhoto ----
            val album = data["images"]?.jsonArray
            val images = mutableListOf<String>()
            val livePhotos = mutableMapOf<Int, String>()

            album?.forEachIndexed { index, imgEl ->
                val img = imgEl.jsonObject
                val imageUrl = pickImageUrl(img) ?: return@forEachIndexed
                images.add(imageUrl)

                // Live Photo: 实况照片的视频流
                val liveUrl = img["video"]?.jsonObject
                    ?.get("play_addr")?.jsonObject
                    ?.get("url_list")?.jsonArray
                    ?.let { firstUrl(it) }
                if (!liveUrl.isNullOrEmpty()) {
                    livePhotos[index] = liveUrl
                }
            }

            if (images.isNotEmpty()) {
                val title = data.string("desc")?.takeIf { it.isNotBlank() } ?: "抖音图文"
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
                ?.let { firstUrl(it) }
                ?.replace("playwm", "play")
                ?: return@withContext Result.failure(Exception("未找到视频或图片地址"))

            // Follow the 302 to the final mp4 (西瓜视频 may have no redirect).
            val finalUrl = resolveRedirect(playUrl)

            val cover = data["video"]?.jsonObject
                ?.get("cover")?.jsonObject
                ?.get("url_list")?.jsonArray
                ?.let { preferNonWebp(it) }

            Result.success(
                MediaResult(
                    type = MediaType.VIDEO,
                    title = data.string("desc")?.takeIf { it.isNotBlank() } ?: "抖音视频",
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
     * Web API: /web/api/v2/aweme/slidesinfo/ — supports both videos and
     * image albums (albums need `request_source=200`). `iteminfo` is kept as a
     * legacy fallback. A response is only accepted when it actually carries
     * playable media, otherwise the next endpoint is tried.
     */
    private fun fetchAwemeDetail(videoId: String): JsonObject? {
        // `request_source=200` is required for image albums but returns an
        // empty list for videos, so both variants are tried and the first
        // response that actually carries media wins.
        val apiUrls = listOf(
            "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B$videoId%5D",
            "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B$videoId%5D&request_source=200",
            "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?aweme_ids=%5B$videoId%5D",
        )
        var partial: JsonObject? = null
        for (apiUrl in apiUrls) {
            val detail = try {
                val body = get(apiUrl, followRedirects = true)
                json.parseToJsonElement(body).jsonObject["aweme_details"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
            } catch (e: Exception) {
                null
            } ?: continue

            if (hasMedia(detail)) return detail
            if (partial == null) partial = detail
        }
        return partial
    }

    private fun hasMedia(detail: JsonObject): Boolean {
        val album = detail["images"]?.jsonArray
        if (album != null && album.any { pickImageUrl(it.jsonObject) != null }) return true
        return !videoPlayUrl(detail).isNullOrEmpty()
    }

    private fun videoPlayUrl(detail: JsonObject): String? =
        detail["video"]?.jsonObject
            ?.get("play_addr")?.jsonObject
            ?.get("url_list")?.jsonArray
            ?.let { firstUrl(it) }

    /**
     * 旧的 HTML SSR 解析（iesdouyin 分享页 _ROUTER_DATA），作为接口失败时的兜底。
     * 注意页面会 302 到 www.douyin.com，必须禁止跟随重定向并使用移动端 UA。
     */
    private fun fetchAwemeDetailFromHtml(videoId: String): JsonObject? {
        return try {
            val pageUrl = "https://www.iesdouyin.com/share/video/$videoId/"
            val request = Request.Builder().url(pageUrl)
                .header("User-Agent", mobileUa)
                .header("Referer", "https://www.douyin.com/")
                .build()
            val html = noRedirectClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string() ?: return null
            }
            val routerJson = Regex(
                """window\._ROUTER_DATA\s*=\s*(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)?.groupValues?.get(1)?.trim() ?: return null

            val loaderData = json.parseToJsonElement(routerJson).jsonObject["loaderData"]?.jsonObject
                ?: return null

            // Newer pages drop `videoInfoRes`: scan every loader entry for an
            // `item_list` instead of relying on a fixed key.
            for ((_, value) in loaderData) {
                val itemList = value.jsonObject["videoInfoRes"]?.jsonObject
                    ?.get("item_list")?.jsonArray
                    ?: continue
                val item = itemList.firstOrNull()?.jsonObject ?: continue
                if (hasMedia(item)) return item
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve an aweme id from any supported share link form.
     */
    private fun resolveVideoId(url: String): String? {
        val lower = url.lowercase()
        return when {
            "v.douyin.com" in lower -> {
                // Short link: GET without redirects and read the `location`
                // header. Some responses carry a relative location, others
                // answer with a JS-rendered page and no header at all — in
                // that case fall back to the URL the client finally lands on.
                val location = shortLinkLocation(url)
                    ?: shortLinkFinalUrl(url)
                    ?: return null
                if ("ixigua.com" in location) return null
                extractIdFromPath(location)
            }
            else -> extractIdFromPath(url)
        }
    }

    /** Absolute `location` target of a short link, or null. */
    private fun shortLinkLocation(url: String): String? {
        for (ua in listOf(mobileUa, defaultUa)) {
            try {
                val request = Request.Builder().url(url).header("User-Agent", ua).build()
                noRedirectClient.newCall(request).execute().use { resp ->
                    val location = resp.header("location") ?: return@use null
                    return resp.request.url.resolve(location)?.toString() ?: location
                }
            } catch (e: Exception) {
                // try the next UA
            }
        }
        return null
    }

    /** Final URL after following the redirect chain (JS-rendered fallback). */
    private fun shortLinkFinalUrl(url: String): String? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", mobileUa)
                .build()
            client.newCall(request).execute().use { resp ->
                resp.request.url.toString().takeIf { it != url }
            }
        } catch (e: Exception) {
            null
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
     * Best still-image URL for an album entry. `download_url_list` holds the
     * watermark-free original, `url_list` is the rendered (sometimes webp)
     * variant. Some responses nest both under a `url` / `download_url` object.
     */
    private fun pickImageUrl(img: JsonObject): String? {
        preferNonWebpOrNull(img["download_url_list"])?.let { return it }
        preferNonWebpOrNull(img["url_list"])?.let { return it }
        val download = img["download_url"]?.jsonObject
        preferNonWebpOrNull(download?.get("url_list"))?.let { return it }
        val url = img["url"]?.jsonObject
        preferNonWebpOrNull(url?.get("url_list"))?.let { return it }
        return null
    }

    private fun preferNonWebpOrNull(element: JsonElement?): String? {
        val array = element as? JsonArray ?: return null
        return preferNonWebp(array)
    }

    /** First non-empty entry of a url_list. */
    private fun firstUrl(urlList: JsonArray): String? =
        urlList.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { it.isNotEmpty() }

    /**
     * Prefer the first url_list entry that is not a .webp asset.
     */
    private fun preferNonWebp(urlList: JsonArray): String? {
        val urls = urlList.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it.isNotEmpty() }
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
     * Resolve the final location of a (possibly relative, possibly 302) video url.
     */
    private fun resolveRedirect(url: String): String {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", defaultUa)
                .build()
            noRedirectClient.newCall(request).execute().use { resp ->
                val location = resp.header("location") ?: return url
                resp.request.url.resolve(location)?.toString() ?: location
            }
        } catch (e: Exception) {
            url
        }
    }

    /** Null-safe string read: JSON `null` / missing keys yield null. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull
}
