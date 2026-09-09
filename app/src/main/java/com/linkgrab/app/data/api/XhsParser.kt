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
 * Native Xiaohongshu (RED) parser with multiple fallback strategies.
 *
 * The app has no X-S/X-T signature and no user Cookie, so it relies on the
 * public web page. XHS serves different HTML shells depending on UA, link age,
 * and whether the note requires login. This parser tries several extraction
 * methods in order and returns the first one that yields usable media.
 *
 * Strategies:
 * 1. Desktop UA + window.__INITIAL_STATE__ (richest: title, author, live photos)
 * 2. Desktop UA + JSON-LD / og:image / generic xhscdn regex
 * 3. Mobile UA + __INITIAL_STATE__ (some notes render the state only on mobile)
 * 4. Mobile UA + meta/regex fallback
 */
class XhsParser {

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

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val mobileUa =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

    private val undefinedValue = Regex("""(?<=[:\[,])\s*undefined(?=\s*[,}\]])""")
    private val statePattern = Regex(
        """window\.__INITIAL_STATE__\s*=\s*(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    private val shortLinkHost = Regex("""https?://(?:www\.)?(xhslink\.com|xhslink\.cn|xslink\.com|xhs\.link)/""")

    suspend fun parse(shareUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1) Short links must be expanded first: the canonical page needs xsec_token.
            val target = expandShortLink(shareUrl)

            // 2) Try desktop first (fastest, most complete)
            val (finalUrl, desktopHtml) = fetch(target, desktopUa)
            if (isNotFoundPage(finalUrl, desktopHtml)) {
                return@withContext Result.failure(
                    Exception("链接已失效或缺少安全令牌(xsec_token)，请在小红书 App 中重新分享该笔记")
                )
            }

            parseFromHtml(desktopHtml)?.let { return@withContext Result.success(it) }

            // 3) Desktop didn't yield content. Try mobile rendering.
            val (_, mobileHtml) = fetch(target, mobileUa)
            parseFromHtml(mobileHtml)?.let { return@withContext Result.success(it) }

            // 4) Last resort: any xhscdn image we can scrape from the page
            scrapeAnyImages(desktopHtml + mobileHtml)
                ?.let { return@withContext Result.success(it) }

            return@withContext Result.failure(
                Exception("无法从页面提取到图片或视频，可能需要登录或该笔记暂不可见")
            )
        }.getOrElse { e ->
            Result.failure(Exception("小红书解析失败: ${e.message}"))
        }
    }

    // region: strategies

    /**
     * Try all structured extraction methods against a single HTML body.
     */
    private fun parseFromHtml(html: String): MediaResult? {
        if (html.isBlank()) return null

        // Strategy A: __INITIAL_STATE__ JSON
        parseInitialState(html)?.let { root ->
            val result = parseInitialStateRoot(root)
            if (result != null && result.hasContent) return result
        }

        // Strategy B: JSON-LD structured data
        parseJsonLd(html)?.let { return it }

        // Strategy C: OpenGraph + meta tags
        parseMetaTags(html)?.let { return it }

        return null
    }

    private fun parseInitialStateRoot(root: JsonObject): MediaResult? {
        val noteBlock = root["note"]?.jsonObject
            ?: root["noteDetailMap"]?.jsonObject?.let { return parseNoteDetailMap(it) }
            ?: return null

        val noteId = noteBlock.string("currentNoteId")
        if (noteId.isNullOrEmpty() || noteId == "undefined") return null

        val detailMap = noteBlock["noteDetailMap"]?.jsonObject ?: return null

        val data = detailMap[noteId]?.jsonObject?.get("note")?.jsonObject
            ?: detailMap.entries.firstOrNull { (k, v) ->
                k.startsWith(noteId) && (v as? JsonObject)?.get("note") != null
            }?.value?.jsonObject?.get("note")?.jsonObject
            ?: detailMap.entries.firstOrNull { (_, v) ->
                (v as? JsonObject)?.get("note") != null
            }?.value?.jsonObject?.get("note")?.jsonObject
            ?: return null

        return buildMediaResult(data)
    }

    private fun parseNoteDetailMap(detailMap: JsonObject): MediaResult? {
        val data = detailMap.entries.firstOrNull { (_, v) ->
            (v as? JsonObject)?.get("note") != null
        }?.value?.jsonObject?.get("note")?.jsonObject ?: return null
        return buildMediaResult(data)
    }

    private fun buildMediaResult(data: JsonObject): MediaResult? {
        val title = data.string("title")?.takeIf { it.isNotBlank() }
            ?: data.string("desc")?.takeIf { it.isNotBlank() }
            ?: "小红书笔记"
        val author = data["user"]?.jsonObject?.string("nickname") ?: ""
        val source = data.string("source") ?: "xiaohongshu"

        // ---- video note ----
        val stream = data["video"]?.jsonObject
            ?.get("media")?.jsonObject
            ?.get("stream")?.jsonObject
        val videoUrl = pickVideoUrl(stream)
        val firstCover = data["imageList"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.let { pickImageUrl(it) }

        if (!videoUrl.isNullOrEmpty()) {
            return MediaResult(
                type = MediaType.VIDEO,
                title = title,
                videoUrl = videoUrl,
                coverUrl = firstCover,
                author = author,
                source = source,
            )
        }

        // ---- image note (图集 + 实况) ----
        val images = mutableListOf<String>()
        val livePhotos = mutableMapOf<Int, String>()

        data["imageList"]?.jsonArray?.forEachIndexed { index, imgEl ->
            val img = imgEl.jsonObject
            val imageUrl = pickImageUrl(img) ?: return@forEachIndexed
            images.add(imageUrl)

            // 实况照片：多路探测
            val liveUrl = pickLivePhotoUrl(img)
            if (!liveUrl.isNullOrEmpty()) {
                livePhotos[index] = liveUrl
            }
        }

        if (images.isEmpty()) return null

        return MediaResult(
            type = MediaType.IMAGE,
            title = title,
            images = images,
            coverUrl = images.first(),
            livePhotos = livePhotos,
            author = author,
            source = source,
        )
    }

    private fun parseJsonLd(html: String): MediaResult? {
        val ldPattern = Regex(
            """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val matches = ldPattern.findAll(html).toList()
        if (matches.isEmpty()) return null

        for (match in matches) {
            val raw = match.groupValues[1].trim()
            val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: continue

            val type = obj.string("@type") ?: continue
            if (type !in listOf("VideoObject", "ImageObject", "Article", "SocialMediaPosting")) continue

            val title = obj.string("headline")
                ?: obj.string("name")
                ?: obj.string("description")?.takeIf { it.length <= 60 }
                ?: "小红书笔记"

            // VideoObject
            obj.string("contentUrl")?.takeIf { it.isNotBlank() }?.let { videoUrl ->
                return MediaResult(
                    type = MediaType.VIDEO,
                    title = title,
                    videoUrl = videoUrl,
                    coverUrl = obj.string("thumbnailUrl"),
                    source = "xiaohongshu",
                )
            }

            // ImageObject / Article with image
            obj["image"]?.let { imageEl ->
                val urls = when (imageEl) {
                    is JsonPrimitive -> listOfNotNull(imageEl.contentOrNull)
                    is JsonArray -> imageEl.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    else -> emptyList()
                }
                val contentUrls = urls.filter { isContentImage(it) }
                if (contentUrls.isNotEmpty()) {
                    return MediaResult(
                        type = MediaType.IMAGE,
                        title = title,
                        images = contentUrls.take(20),
                        coverUrl = contentUrls.first(),
                        source = "xiaohongshu",
                    )
                }
            }
        }
        return null
    }

    private fun parseMetaTags(html: String): MediaResult? {
        val title = Regex("""<meta[^>]+(?:property|name)=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.replace(" - 小红书", "")
            ?.trim()
            ?: extractTitle(html)

        val ogImages = Regex(
            """<meta[^>]+(?:property|name)=["']og:image["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ).findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { isContentImage(it) }
            .toList()

        if (ogImages.isNotEmpty()) {
            return MediaResult(
                type = MediaType.IMAGE,
                title = title,
                images = ogImages.distinct().take(20),
                coverUrl = ogImages.first(),
                source = "xiaohongshu",
            )
        }
        return null
    }

    private fun scrapeAnyImages(html: String): MediaResult? {
        if (html.isBlank()) return null
        val title = extractTitle(html)

        // 1) urlDefault from __INITIAL_STATE__ / inline scripts
        val urlDefaultPattern = Regex(""""urlDefault"\s*:\s*"([^"]+)""")
        val fromScripts = urlDefaultPattern.findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { isContentImage(it) }
            .toList()

        // 2) Any xhscdn image URL
        val cdnPattern = Regex("""https?://[a-z0-9.-]*xhscdn\.com/[^\s"'<>\\]+\.(?:jpg|jpeg|png|webp)(?:\?[^\s"'<>\\]*)?""")
        val fromCdn = cdnPattern.findAll(html)
            .mapNotNull { it.value }
            .filter { isContentImage(it) }
            .toList()

        val all = (fromScripts + fromCdn).distinct().take(20)
        if (all.isEmpty()) return null

        return MediaResult(
            type = MediaType.IMAGE,
            title = title,
            images = all,
            coverUrl = all.first(),
            source = "xiaohongshu",
        )
    }

    // endregion

    // region: network

    private fun expandShortLink(url: String): String {
        if (!shortLinkHost.containsMatchIn(url)) return url
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", mobileUa)
                .build()
            noRedirectClient.newCall(request).execute().use { resp ->
                resp.header("location")?.takeIf { it.isNotBlank() } ?: url
            }
        } catch (e: Exception) {
            url
        }
    }

    private fun fetch(url: String, userAgent: String): Pair<String, String> {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.xiaohongshu.com/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("空响应")
            return resp.request.url.toString() to body
        }
    }

    private fun isNotFoundPage(finalUrl: String, html: String): Boolean {
        if (Regex("""xiaohongshu\.com/404""").containsMatchIn(finalUrl)) return true
        if ("""error_code=\d+""".toRegex().containsMatchIn(finalUrl)) return true
        return "noteDetailMap" !in html && "__INITIAL_STATE__" in html
    }

    // endregion

    // region: payload helpers

    private fun parseInitialState(html: String): JsonObject? {
        val raw = statePattern.find(html)?.groupValues?.get(1)?.trim()
            ?.trimStart('=')
            ?.trim()
            ?: return null

        val candidates = buildList {
            add(undefinedValue.replace(raw, "null"))
            val aggressive = raw.replace(Regex("""\bundefined\b"""), "null")
            if (aggressive != raw) add(aggressive)
        }

        for (candidate in candidates) {
            runCatching {
                json.parseToJsonElement(candidate).jsonObject
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun pickVideoUrl(stream: JsonObject?): String? {
        if (stream == null) return null
        for (codec in listOf("h264", "h265", "av1", "h266")) {
            val url = stream[codec]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.string("masterUrl")
            if (!url.isNullOrEmpty()) return url
        }
        return null
    }

    private fun pickImageUrl(img: JsonObject): String? {
        val urlDefault = img.string("urlDefault")
            ?: img.string("urlPre")
            ?: img.string("url")
            ?: img.string("originUrl")
        if (urlDefault.isNullOrEmpty()) return null
        if ("notes_pre_post" !in urlDefault) return urlDefault
        val imageId = urlDefault.substringAfterLast('/').substringBefore('!')
        if (imageId.isEmpty()) return urlDefault
        val spectrum = if ("spectrum" in urlDefault) "spectrum/" else ""
        return "https://ci.xiaohongshu.com/notes_pre_post/$spectrum$imageId?imageView2/format/jpg"
    }

    /**
     * 实况照片链路很多，逐个试：stream.h264/h265、video.url、livePhotoUrl。
     */
    private fun pickLivePhotoUrl(img: JsonObject): String? {
        if (!isLivePhotoFlag(img)) return null

        // 1) imageList[].stream.{h264,h265}
        val streamObj = img["stream"]?.jsonObject
        if (streamObj != null) {
            for (codec in listOf("h264", "h265", "av1")) {
                streamObj[codec]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.string("masterUrl")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
        }

        // 2) imageList[].video.url
        img["video"]?.jsonObject?.string("url")?.takeIf { it.isNotBlank() }?.let { return it }

        // 3) imageList[].livePhotoUrl / livePhotoMedia
        img.string("livePhotoUrl")?.takeIf { it.isNotBlank() }?.let { return it }
        img["livePhotoMedia"]?.jsonObject?.string("url")?.takeIf { it.isNotBlank() }?.let { return it }

        return null
    }

    private fun isLivePhotoFlag(img: JsonObject): Boolean {
        val livePhoto = img["livePhoto"]
        if (livePhoto is JsonPrimitive) {
            return livePhoto.contentOrNull?.toBooleanStrictOrNull() ?: false
        }
        // 有些节点 livePhoto 是对象，只要有 stream/video/livePhotoUrl 就算实况
        return img["stream"] != null || img["video"] != null ||
            img.string("livePhotoUrl") != null || img["livePhotoMedia"] != null
    }

    private fun extractTitle(html: String): String {
        val titlePattern = Regex("""<title>([^<]+)</title>""")
        return titlePattern.find(html)?.groupValues?.get(1)
            ?.replace(" - 小红书", "")
            ?.trim()
            ?: "小红书笔记"
    }

    private fun isContentImage(url: String): Boolean {
        val lower = url.lowercase()
        return !lower.contains("avatar") &&
            !lower.contains("icon") &&
            !lower.contains("logo") &&
            !lower.contains("emoji") &&
            !lower.contains("sticker") &&
            !lower.contains("180x180") &&
            !lower.contains("thumbnail") &&
            !lower.contains("fe-platform") &&
            !lower.contains("fe-static") &&
            !lower.contains("fe-video")
    }

    private val MediaResult.hasContent: Boolean
        get() = when (type) {
            MediaType.IMAGE -> images.isNotEmpty()
            MediaType.VIDEO -> !videoUrl.isNullOrEmpty()
            else -> false
        }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    // endregion
}
