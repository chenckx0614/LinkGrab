package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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
 * Native Xiaohongshu (RED) parser ported from wujunwei928/parse-video-py
 * (parser/redbook.py), hardened for the link forms the app actually receives.
 *
 * Flow:
 * 1. Expand short links (`xhslink.com` / `xhslink.cn` / `xslink.com`) to the
 *    canonical note page — the redirect target carries the `xsec_token`
 *    required by the web page.
 * 2. GET the page with a desktop UA (following redirects).
 * 3. Extract the `window.__INITIAL_STATE__ = {...}</script>` JSON blob. The
 *    payload is JavaScript, not strict JSON, and uses the `undefined` literal
 *    as a value — it must be normalised before parsing.
 * 4. Read `note.noteDetailMap[currentNoteId].note`:
 *    - video note → video.media.stream.{h264,h265,av1,h266}[0].masterUrl
 *    - image note → imageList[]; for each item, build the watermark-free
 *      `https://ci.xiaohongshu.com/notes_pre_post/...?imageView2/format/jpg`
 *      URL when the default URL lives under `notes_pre_post`, otherwise keep
 *      the original URL. Live Photos additionally expose
 *      imageList[].stream.h264[0].masterUrl.
 *
 * No X-S/X-T signature and no Cookie are required.
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

    /**
     * `undefined` used as a **value** token: preceded by `:`/`[`/`,` and
     * followed by `,`/`}`/`]`. Anything else (e.g. the word inside a quoted
     * string) is left untouched.
     */
    private val undefinedValue = Regex("""(?<=[:\[,])\s*undefined(?=\s*[,}\]])""")

    private val statePattern = Regex(
        """window\.__INITIAL_STATE__\s*=\s*(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val shortLinkHost = Regex("""https?://(?:www\.)?(xhslink\.com|xhslink\.cn|xslink\.com)/""")

    suspend fun parse(shareUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            // 1) Short links must be expanded first: the note id alone is not
            //    enough, the page needs the `xsec_token` from the redirect.
            val target = expandShortLink(shareUrl)

            val (finalUrl, html) = fetch(target)

            // 2) An expired / token-less link silently 302s to the 404 shell,
            //    whose __INITIAL_STATE__ carries `currentNoteId: undefined`.
            if (isNotFoundPage(finalUrl, html)) {
                return@withContext Result.failure(
                    Exception("链接已失效或缺少安全令牌(xsec_token)，请在小红书 App 中重新分享该笔记")
                )
            }

            val root = parseInitialState(html)
                ?: return@withContext Result.failure(
                    Exception(
                        if ("__INITIAL_STATE__" in html) "页面数据结构解析失败"
                        else "小红书返回了验证页，请在小红书 App 中打开该笔记后重新复制链接"
                    )
                )

            val noteBlock = root["note"]?.jsonObject
                ?: return@withContext Result.failure(Exception("页面数据结构异常（note 缺失）"))

            val noteId = noteBlock.string("currentNoteId")
            if (noteId.isNullOrEmpty() || noteId == "undefined") {
                return@withContext Result.failure(
                    Exception("链接已失效或缺少安全令牌(xsec_token)，请在小红书 App 中重新分享该笔记")
                )
            }

            val detailMap = noteBlock["noteDetailMap"]?.jsonObject
                ?: return@withContext Result.failure(Exception("未找到笔记详情"))

            // The map key may be suffixed (e.g. "<id>_feed"), so fall back to a
            // prefix scan before giving up.
            val data = detailMap[noteId]?.jsonObject?.get("note")?.jsonObject
                ?: detailMap.entries.firstOrNull { (k, v) ->
                    k.startsWith(noteId) && (v as? JsonObject)?.get("note") != null
                }?.value?.jsonObject?.get("note")?.jsonObject
                ?: detailMap.entries.firstOrNull { (_, v) ->
                    (v as? JsonObject)?.get("note") != null
                }?.value?.jsonObject?.get("note")?.jsonObject
                ?: return@withContext Result.failure(Exception("未找到笔记详情"))

            val title = data.string("title")?.takeIf { it.isNotBlank() }
                ?: data.string("desc")?.takeIf { it.isNotBlank() }
                ?: "小红书笔记"

            // ---- video note ----
            val stream = data["video"]?.jsonObject
                ?.get("media")?.jsonObject
                ?.get("stream")?.jsonObject
            val videoUrl = pickVideoUrl(stream)

            val firstCover = data["imageList"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.let { pickImageUrl(it) }

            if (!videoUrl.isNullOrEmpty()) {
                return@withContext Result.success(
                    MediaResult(
                        type = MediaType.VIDEO,
                        title = title,
                        videoUrl = videoUrl,
                        coverUrl = firstCover,
                        source = "xiaohongshu",
                    )
                )
            }

            // ---- image note (图集 + 实况) ----
            val images = mutableListOf<String>()
            val livePhotos = mutableMapOf<Int, String>()

            data["imageList"]?.jsonArray?.forEachIndexed { index, imgEl ->
                val img = imgEl.jsonObject
                val imageUrl = pickImageUrl(img) ?: return@forEachIndexed
                images.add(imageUrl)

                // 实况照片 (Live Photo): still frame + h264 stream
                if (isLivePhoto(img["livePhoto"])) {
                    val liveUrl = img["stream"]?.jsonObject
                        ?.get("h264")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.string("masterUrl")
                    if (!liveUrl.isNullOrEmpty()) {
                        livePhotos[index] = liveUrl
                    }
                }
            }

            if (images.isEmpty()) {
                return@withContext Result.failure(Exception("未找到图片或视频内容"))
            }

            Result.success(
                MediaResult(
                    type = MediaType.IMAGE,
                    title = title,
                    images = images,
                    coverUrl = images.first(),
                    livePhotos = livePhotos,
                    source = "xiaohongshu",
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("小红书解析失败: ${e.message}"))
        }
    }

    // region: network

    /**
     * Resolve `xhslink.com/a/xxx` style short links to the canonical
     * `www.xiaohongshu.com/...` URL. Returns the input unchanged when it is
     * already canonical or when the redirect cannot be read.
     */
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

    /** GET [url] (following redirects) and return the final URL + body. */
    private fun fetch(url: String): Pair<String, String> {
        val request = Request.Builder().url(url)
            .header("User-Agent", desktopUa)
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

    /**
     * An expired or token-less share link does not 404 at the HTTP level —
     * it redirects to the `/404?source=/404/...` shell.
     */
    private fun isNotFoundPage(finalUrl: String, html: String): Boolean {
        if (Regex("""xiaohongshu\.com/404""").containsMatchIn(finalUrl)) return true
        // The 404 shell renders a dedicated error page instead of note markup.
        if ("""error_code=\d+""".toRegex().containsMatchIn(finalUrl)) return true
        return "noteDetailMap" !in html && "__INITIAL_STATE__" in html
    }

    // endregion

    // region: payload

    /**
     * Pull `window.__INITIAL_STATE__` out of the page and parse it.
     *
     * The blob is JS, so `undefined` literals are rewritten to `null` first.
     * If the strict rewrite still fails, a last-resort global rewrite is
     * attempted — better a slightly lossy parse than a hard failure.
     */
    private fun parseInitialState(html: String): JsonObject? {
        val raw = statePattern.find(html)?.groupValues?.get(1)?.trim()
            ?.trimStart('=')
            ?.trim()
            ?: return null

        val candidates = buildList {
            add(undefinedValue.replace(raw, "null"))
            // last resort: strip every remaining `undefined` token
            val aggressive = raw.replace(Regex("""\bundefined\b"""), "null")
            if (aggressive != raw) add(aggressive)
        }

        for (candidate in candidates) {
            val parsed = runCatching {
                json.parseToJsonElement(candidate).jsonObject
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    /** First non-empty masterUrl across the codecs, best quality first. */
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

    /**
     * Pick the best still-image URL for an `imageList` entry.
     *
     * Assets served from `notes_pre_post` can be re-hosted on
     * `ci.xiaohongshu.com` (no watermark); anything else keeps its URL.
     */
    private fun pickImageUrl(img: JsonObject): String? {
        val urlDefault = img.string("urlDefault") ?: img.string("urlPre") ?: img.string("url")
        if (urlDefault.isNullOrEmpty()) return null
        if ("notes_pre_post" !in urlDefault) return urlDefault
        val imageId = urlDefault.substringAfterLast('/').substringBefore('!')
        if (imageId.isEmpty()) return urlDefault
        val spectrum = if ("spectrum" in urlDefault) "spectrum/" else ""
        return "https://ci.xiaohongshu.com/notes_pre_post/$spectrum$imageId?imageView2/format/jpg"
    }

    /** `livePhoto` is a boolean on most notes but can arrive as a string. */
    private fun isLivePhoto(element: JsonElement?): Boolean {
        val primitive = element as? JsonPrimitive ?: return false
        return primitive.contentOrNull?.toBooleanStrictOrNull() ?: false
    }

    /** Null-safe string read: JSON `null` / missing keys yield null. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    // endregion
}
