package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.VideoQuality
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
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 *    albums additionally need `request_source=200`). 空结果时强制刷新 ttwid
 *    重试一轮。The legacy HTML SSR page is kept as a fallback.
 * 3. Read the first `aweme_details` entry:
 *    - images → images[] (album). `download_url_list` is preferred because it
 *      is the watermark-free original, otherwise `url_list` is used.
 *      Live Photos expose images[].video.play_addr.url_list[0].
 *    - video  → 高清档位来自 video.bit_rate[]（见 bestVideoUrl）。注意
 *               video.play_addr 恒定是 `aweme/v1/play/?ratio=720p`，
 *               直接用它下载必然是 720p 的糊图，只能当兜底。
 *    - cover  → 优先 origin_cover（原图），cover 是压缩小图。
 *
 * No signature (X-Bogus / a_bogus) and no Cookie are required — but a
 * `ttwid` cookie is strongly recommended: without it douyin's risk control
 * intermittently answers the web API with empty `aweme_details`, which is
 * the main cause of "偶尔解析失败". We bootstrap one ttwid by visiting the
 * share homepage once and reuse it for all API calls.
 */
class DouyinParser {

    companion object {
        // ttwid 跨实例共享：WebView 通道拿到真浏览器会话后回填到这里，
        // 之后所有 DouyinParser 实例的原生请求都带上这个 cookie。
        @Volatile
        private var cachedTtwid: String? = null

        /** WebView 真页面拿到 ttwid 后回填（原生解析从此风控概率大降）。 */
        fun injectTtwid(value: String) {
            if (value.isNotBlank()) cachedTtwid = value
        }
    }

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

    // region: ttwid session
    //
    // 抖音 web 接口的风控：不带 ttwid cookie 时，slidesinfo / iteminfo 会**间歇性**
    // 返回空 aweme_details（这正是「偶尔解析失败」的主因）。浏览器访问任意
    // iesdouyin 页面都会下发 ttwid，这里引导一次并缓存复用（缓存移至 companion，
    // 供 WebView 通道回填共享）。

    private fun ttwidCookie(): String {
        cachedTtwid?.let { return "ttwid=$it" }
        // 通道 1：访问分享主页，读响应 Set-Cookie。部分网络环境主页不下发
        // ttwid（2026-10 实测），此时走通道 2。
        bootstrapTtwidFromHomepage()?.let {
            cachedTtwid = it
            return "ttwid=$it"
        }
        // 通道 2：字节的 union register 接口主动注册一个 —— POST 拿
        // redirect_url(ticket)，再 GET 回调地址读 Set-Cookie。实测（2026-10）
        // 桌面与移动网络均可用，拿到 ttwid 后 SSR 分享页能带出完整图集数据。
        bootstrapTtwidFromRegister()?.let {
            cachedTtwid = it
            return "ttwid=$it"
        }
        // 两条通道都失败不阻断解析：不带 cookie 直连仍有一定概率成功
        return ""
    }

    private fun bootstrapTtwidFromHomepage(): String? {
        return try {
            val request = Request.Builder().url("https://www.iesdouyin.com/")
                .header("User-Agent", mobileUa)
                .build()
            client.newCall(request).execute().use { resp ->
                Cookie.parseAll("https://www.iesdouyin.com/".toHttpUrl(), resp.headers)
                    .firstOrNull { it.name == "ttwid" }?.value
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun bootstrapTtwidFromRegister(): String? {
        return try {
            val body = """{"region":"cn","aid":6383,"needFid":false,"service":"www.douyin.com",""" +
                """"migrate_info":{"ticket":"","source":"node"},"cbUrlProtocol":"https","union":true}"""
            val request = Request.Builder().url("https://ttwid.bytedance.com/ttwid/union/register/")
                .header("User-Agent", defaultUa)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val callback = client.newCall(request).execute().use { resp ->
                json.parseToJsonElement(resp.body?.string() ?: return null)
                    .jsonObject["redirect_url"]?.let { it as? JsonPrimitive }?.contentOrNull
            } ?: return null
            noRedirectClient.newCall(
                Request.Builder().url(callback).header("User-Agent", defaultUa).build()
            ).execute().use { resp ->
                Cookie.parseAll(callback.toHttpUrl(), resp.headers)
                    .firstOrNull { it.name == "ttwid" }?.value
            }
        } catch (_: Exception) {
            null
        }
    }
    // endregion

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
            val livePhotos = mutableMapOf<Int, List<String>>()

            album?.forEachIndexed { index, imgEl ->
                val img = imgEl.jsonObject
                val imageUrl = pickImageUrl(img) ?: return@forEachIndexed
                images.add(imageUrl)

                // Live Photo: 实况照片的视频流。url_list 里的地址全部收下，
                // 首个作为主地址，其余作为下载失败时的回退。
                val liveUrls = img["video"]?.jsonObject
                    ?.get("play_addr")?.jsonObject
                    ?.get("url_list")?.jsonArray
                    ?.let { urlCandidates(it) }
                    .orEmpty()
                if (liveUrls.isNotEmpty()) {
                    livePhotos[index] = liveUrls
                }
            }

            if (images.isNotEmpty()) {
                val title = data.string("desc")?.takeIf { it.isNotBlank() } ?: "抖音图文"
                return@withContext Result.success(
                    MediaResult(
                        type = MediaType.IMAGE,
                        title = title,
                        images = images,
                        // 图集封面直接用第一张原图：video.cover 只是压缩过的小图，
                        // 在结果页放大展示时明显发虚。
                        coverUrl = images.first(),
                        livePhotos = livePhotos,
                        source = "douyin",
                    )
                )
            }

            // 图文（note）作品的 detail 即使被风控打薄，也会带 images 字段
            // （可能为 null / 空数组）。这种情况绝不能落到视频分支 —— note 自带的
            // video 只是图集预览流，返回 VIDEO 会让用户在结果页以视频方式
            // “预览”，且只能看到一张封面。
            if (images.isEmpty() && data.containsKey("images")) {
                return@withContext Result.failure(Exception("抖音图集未取到图片（接口被风控过滤）"))
            }

            // ---- video (视频) ----
            // 全量画质档位（高→低）：UI 让用户选，默认用最高档。
            val qualities = videoQualities(data)
            val playUrl = qualities.firstOrNull()?.url
                ?: fallbackVideoUrl(data)
                ?: return@withContext Result.failure(Exception("未找到视频或图片地址"))

            // Follow the 302 to the final mp4 (西瓜视频 may have no redirect).
            val finalUrl = resolveRedirect(playUrl)

            val cover = bestCoverUrl(data)

            Result.success(
                MediaResult(
                    type = MediaType.VIDEO,
                    title = data.string("desc")?.takeIf { it.isNotBlank() } ?: "抖音视频",
                    videoUrl = finalUrl,
                    coverUrl = cover,
                    source = "douyin",
                    videoQualities = qualities,
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("抖音解析失败: ${e.message}"))
        }
    }

    /**
     * Web API: /web/api/v2/aweme/slidesinfo/ — supports both videos and
     * image albums (albums need `request_source=200`).
     *
     * 2026-10 实测：`iteminfo` 已下线（恒定返回 encrypt_data_miss），不再浪费一趟；
     * slidesinfo 本体健康（干净请求连 ttwid 都不需要），但风控可能间歇性过滤
     * （aweme_details 为 null + filter_list）。此时强制刷新 ttwid 换新会话重试一次。
     */
    private fun fetchAwemeDetail(videoId: String): JsonObject? {
        // `request_source=200` is required for image albums but returns an
        // empty list for videos, so both variants are tried and the first
        // response that actually carries media wins.
        fun trySlidesinfo(): JsonObject? {
            var partial: JsonObject? = null
            for (rsParam in listOf("", "&request_source=200")) {
                val detail = try {
                    val body = get(
                        "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B$videoId%5D$rsParam",
                        followRedirects = true,
                    )
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

        trySlidesinfo()?.let { return it }

        // 空结果 → 缓存的 ttwid 可能已被风控标记，丢弃强制重新引导后再试一轮
        if (cachedTtwid != null) {
            cachedTtwid = null
            return trySlidesinfo()
        }
        return null
    }

    private fun hasMedia(detail: JsonObject): Boolean {
        val album = detail["images"]?.jsonArray
        if (album != null && album.any { pickImageUrl(it.jsonObject) != null }) return true
        return !bestVideoUrl(detail).isNullOrEmpty()
    }

    /**
     * 全部可用画质档位，按「高 → 低」排列，供 UI 让用户选择。
     *
     * 抖音 web 接口里 `video.play_addr` 基本恒定是
     * `.../aweme/v1/play/?video_id=…&ratio=720p` —— 也就是 720p，直接拿它下载必然糊。
     * 真正分档的画质在 `video.bit_rate[]` 里，每档带自己的 `play_addr` / `bit_rate`。
     *
     * H.265 档位码率往往更高，但兼容性差（部分机型和系统相册播不出来），
     * 所以优先输出 H.264 档位，只有清一色 H.265 时才退而用之。
     */
    private fun videoQualities(detail: JsonObject): List<VideoQuality> {
        val video = detail["video"]?.jsonObject ?: return emptyList()

        val gears = video["bit_rate"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { gear ->
                val addr = gear["play_addr"] as? JsonObject
                val url = addrUrl(gear["play_addr"]) ?: return@mapNotNull null
                val bitrate = (gear["bit_rate"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
                val h265 = (gear["is_h265"] as? JsonPrimitive)?.contentOrNull
                    ?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: false
                val width = (addr?.get("width") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                val height = (addr?.get("height") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
                val gearName = (gear["gear_name"] as? JsonPrimitive)?.contentOrNull ?: ""
                VideoGear(
                    url = url.replace("playwm", "play"),
                    bitrate = bitrate,
                    h265 = h265,
                    label = qualityLabel(width, height, gearName, bitrate),
                )
            }
            .orEmpty()

        val ordered = gears.filter { !it.h265 }
            .ifEmpty { gears }
            .sortedByDescending { it.bitrate }
            .distinctBy { it.url }

        // 同一分辨率可能有多档码率，标签重复时补码率区分，避免 UI 出现两个「1080P」
        val usedLabels = mutableSetOf<String>()
        return ordered.map { gear ->
            val label = if (usedLabels.add(gear.label)) gear.label
            else "${gear.label} (${gear.bitrate / 1000}K)"
            VideoQuality(label = label, url = gear.url, bitrate = gear.bitrate)
        }
    }

    /** 没有 bit_rate 档位信息时的兜底地址（至少能拿到 720p）。 */
    private fun fallbackVideoUrl(detail: JsonObject): String? {
        val video = detail["video"]?.jsonObject ?: return null
        return (addrUrl(video["play_addr_h264"]) ?: addrUrl(video["play_addr"]))
            ?.replace("playwm", "play")
    }

    private fun bestVideoUrl(detail: JsonObject): String? =
        videoQualities(detail).firstOrNull()?.url ?: fallbackVideoUrl(detail)

    /** 画质标签：优先用真实分辨率，其次 gear_name，最后按码率反推。 */
    private fun qualityLabel(width: Int, height: Int, gearName: String, bitrate: Long): String {
        val shortSide = minOf(width, height)
        if (shortSide > 0) return "${shortSide}P"
        Regex("""(\d{3,4})\s*[pP]""").find(gearName)?.let { return "${it.groupValues[1]}P" }
        return when {
            bitrate >= 2_000_000 -> "1080P"
            bitrate >= 1_000_000 -> "720P"
            bitrate >= 500_000 -> "540P"
            else -> "480P"
        }
    }

    /** url_list 的全部候选地址（首个为主，其余用于下载回退），并统一去水印。 */
    private fun urlCandidates(urlList: JsonArray): List<String> =
        urlList.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it.isNotEmpty() }
            .map { it.replace("playwm", "play") }
            .distinct()

    /**
     * 封面优先取 `origin_cover`（原图），`cover` / `dynamic_cover` 都是压缩小图，
     * 在结果页放大展示时会明显发虚。
     */
    private fun bestCoverUrl(detail: JsonObject): String? {
        val video = detail["video"]?.jsonObject ?: return null
        for (key in listOf("origin_cover", "cover", "dynamic_cover")) {
            val url = (video[key] as? JsonObject)
                ?.get("url_list")?.jsonArray
                ?.let { preferNonWebp(it) }
            if (url != null && url.isNotEmpty()) return url
        }
        return null
    }

    /** `{ "url_list": [...] }` 这种结构的第一个可用地址。 */
    private fun addrUrl(element: JsonElement?): String? =
        (element as? JsonObject)?.get("url_list")?.jsonArray?.let { firstUrl(it) }

    /**
     * 旧的 HTML SSR 解析（iesdouyin 分享页 _ROUTER_DATA），作为接口失败时的兜底。
     *
     * 2026-10 实测：SSR 页**只在带 ttwid cookie 时**才内嵌完整媒体数据
     * （无 cookie 的页面是纯客户端渲染壳，_ROUTER_DATA 里没有 item_list）；
     * 搜索引擎蜘蛛 UA 会被 302 封锁，不能走假 UA 的路。
     * 图文作品的分享路径是 /share/note/，视频是 /share/video/，两条都试。
     * 页面行为不稳定：多数情况 200 直出；风控时 302 —— 两种策略都试。
     */
    private fun fetchAwemeDetailFromHtml(videoId: String): JsonObject? {
        return try {
            // 图文作品的分享路径是 /share/note/，必须先试 —— /share/video/{noteId}
            // 会返回图集的预览视频变体，导致图集被当成视频解析。
            for (path in listOf("share/note", "share/video")) {
                val pageUrl = "https://www.iesdouyin.com/$path/$videoId/"
                for (followRedirects in listOf(false, true)) {
                    val html = fetchShareHtml(pageUrl, followRedirects) ?: continue
                    parseShareHtmlRouterData(html)?.let { return it }
                }
            }
            // 全部落空 → ttwid 可能已被风控标记，刷新后再试一轮
            if (cachedTtwid != null) {
                cachedTtwid = null
                for (path in listOf("share/note", "share/video")) {
                    val pageUrl = "https://www.iesdouyin.com/$path/$videoId/"
                    val html = fetchShareHtml(pageUrl, followRedirects = false) ?: continue
                    parseShareHtmlRouterData(html)?.let { return it }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** 从分享页 HTML 里抠 _ROUTER_DATA 并找出带媒体的 item_list。 */
    private fun parseShareHtmlRouterData(html: String): JsonObject? {
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
        return null
    }

    /** 分享页 HTML 抓取：可选跟随重定向，失败返回 null。 */
    private fun fetchShareHtml(pageUrl: String, followRedirects: Boolean): String? {
        return try {
            val builder = Request.Builder().url(pageUrl)
                .header("User-Agent", mobileUa)
                .header("Referer", "https://www.douyin.com/")
            ttwidCookie().takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
            val http = if (followRedirects) client else noRedirectClient
            http.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
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
     * 抖音的图片地址几乎都带查询串（`…~tplv-xxx.webp?biz_tag=…`），
     * 所以判断 webp 必须先把 query / fragment 去掉再比后缀，
     * 否则 `endsWith(".webp")` 永远为假，等于没筛。
     */
    private fun isWebp(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return path.endsWith(".webp", ignoreCase = true)
    }

    /**
     * Prefer the first url_list entry that is not a .webp asset.
     */
    private fun preferNonWebp(urlList: JsonArray): String? {
        val urls = urlList.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it.isNotEmpty() }
        return urls.firstOrNull { !isWebp(it) } ?: urls.firstOrNull()
    }

    /**
     * GET helper with explicit redirect policy control.
     */
    private fun get(url: String, followRedirects: Boolean): String {
        val builder = Request.Builder().url(url)
            .header("User-Agent", defaultUa)
            .header("Referer", "https://www.douyin.com/")
        // ttwid 显著降低风控返回空数据的概率（见 ttwidCookie 注释）
        ttwidCookie().takeIf { it.isNotEmpty() }?.let { builder.header("Cookie", it) }
        val http = if (followRedirects) client else noRedirectClient
        http.newCall(builder.build()).execute().use { resp ->
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

/** 一档画质的中间表示（仅本文件使用）。 */
private data class VideoGear(
    val url: String,
    val bitrate: Long,
    val h265: Boolean,
    val label: String,
)
