package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.URLEncoder

/**
 * B 站（bilibili）解析。
 *
 * 支持：
 *  - `bilibili.com/video/BV…` / `av123` / 分享文本里的 BV 号
 *  - `b23.tv/xxxx` 短链（先跟一次重定向拿到真地址）
 *  - `bilibili.com/bangumi/play/ep123` 番剧单集（尽力而为：多数需要登录）
 *
 * ## WBI 签名（2026 起必须）
 *
 * `x/web-interface/view` 等核心接口已强制 WBI 签名（`wts` + `w_rid`），
 * 裸调会返回 -403/412。签名算法与社区一致（参考 BiliFlow，MIT）：
 * nav 接口拿 `img_key`/`sub_key` → 打乱表映射出 32 位 `mixin_key` →
 * 参数清洗（去 `!'()*`）、按 key 排序、urlencode 后拼 `mixin_key` 取 MD5。
 * key 会轮换，缓存 24h，签名失败自动刷新重试一次。
 *
 * ## 为什么用 progressive（durl）而不是 DASH
 *
 * `fnval=4048` 拿到的 DASH 是**视频流 + 音频流分开的两条地址**，只下 video
 * 会得到一段没有声音的 mp4。把两条流合起来需要下载两个文件再 ffmpeg 合并，
 * 对现在的下载队列（一个任务一条 URL）改动太大。所以这里取 `fnval=1` 的
 * progressive 流：一条带音轨的完整 mp4。匿名状态下画质通常是 360P/480P，
 * 文件名里会带上画质档位，用户看得见。
 *
 * ## 防盗链
 *
 * `*.bilivideo.com` / `*.hdslb.com` 必须带 `Referer: https://www.bilibili.com/`，
 * 否则 403。下载侧在 `MainViewModel.buildMediaRequest` 里按域名补 Referer。
 */
class BilibiliParser {

    companion object {
        /** WBI 混淆表：把 img_key+sub_key 按这个顺序重排取前 32 位。 */
        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
            27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
            37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
            22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52,
        )

        private const val WBI_TTL_MS = 24 * 3600_000L

        @Volatile private var cachedMixinKey: String? = null
        @Volatile private var cachedKeyAt = 0L
        private val wbiMutex = Mutex()
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /**
     * 匿名接口也会看 cookie：没有 `buvid3` 时部分接口返回 412（风控）。
     * 随机生成一个即可满足校验，不需要真实浏览器指纹。
     */
    private val buvidCookie = "buvid3=${UUID.randomUUID()}-${System.currentTimeMillis() % 100000}infoc"

    // ==================== WBI 签名 ====================

    /** 从 nav 接口取 wbi key 并算出 mixin_key（带 24h 缓存）。 */
    private suspend fun mixinKey(): String {
        cachedMixinKey?.takeIf { System.currentTimeMillis() - cachedKeyAt < WBI_TTL_MS }?.let { return it }
        return wbiMutex.withLock {
            cachedMixinKey?.takeIf { System.currentTimeMillis() - cachedKeyAt < WBI_TTL_MS }?.let { return it }
            val root = api("https://api.bilibili.com/x/web-interface/nav")
                ?: error("拿不到 B 站签名密钥（nav 接口无响应）")
            val wbi = root["data"]?.jsonObject?.get("wbi_img")?.jsonObject
                ?: error("拿不到 B 站签名密钥（nav 数据异常）")
            val imgKey = wbi["img_url"]?.jsonPrimitive?.contentOrNull
                ?.substringAfterLast('/')?.substringBefore('.')
                ?: error("WBI img_key 为空")
            val subKey = wbi["sub_url"]?.jsonPrimitive?.contentOrNull
                ?.substringAfterLast('/')?.substringBefore('.')
                ?: error("WBI sub_key 为空")
            val raw = imgKey + subKey
            val mixin = buildString { MIXIN_KEY_ENC_TAB.forEach { if (it < raw.length) append(raw[it]) } }.take(32)
            cachedMixinKey = mixin
            cachedKeyAt = System.currentTimeMillis()
            mixin
        }
    }

    private fun invalidateWbi() {
        cachedMixinKey = null
        cachedKeyAt = 0L
    }

    /** 对参数做 WBI 签名并拼成完整 URL。失败（key 过期）自动刷新重签一次。 */
    private suspend fun signedUrl(endpoint: String, params: Map<String, String>): String {
        val signed = signParams(params, mixinKey())
        return "https://api.bilibili.com$endpoint?" + signed.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private fun signParams(params: Map<String, String>, mixin: String): Map<String, String> {
        val withTs = LinkedHashMap<String, String>(params)
        withTs["wts"] = (System.currentTimeMillis() / 1000).toString()
        // 清洗 B 站拒绝的字符，按 key 排序后参与签名
        val sanitized = withTs.mapValues { (_, v) ->
            buildString { v.forEach { c -> if (c !in "!'()*") append(c) } }
        }.toSortedMap()
        val query = sanitized.entries.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
        val wRid = md5Hex(query + mixin)
        return sanitized + mapOf("w_rid" to wRid)
    }

    private fun urlEncode(s: String): String =
        URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")

    private fun md5Hex(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    // ==================== 请求 ====================

    private fun api(url: String): JsonObject? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", desktopUa)
            .header("Referer", "https://www.bilibili.com/")
            .header("Origin", "https://www.bilibili.com")
            .header("Cookie", buvidCookie)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
        }
    }.getOrNull()

    /** 带签名的 API 调用；-403（签名失效）自动刷新 key 重试一次。 */
    private suspend fun signedApi(endpoint: String, params: Map<String, String>): JsonObject? {
        val first = api(signedUrl(endpoint, params))
        val code = first?.get("code")?.jsonPrimitive?.longOrNull
        if (code == -403L || code == 412L) {
            invalidateWbi()
            return api(signedUrl(endpoint, params))
        }
        return first
    }

    /** 这段文本是不是 B 站链接（含 b23 短链）。 */
    fun canParse(text: String): Boolean =
        Regex("""(bilibili\.com|b23\.tv|bili2233\.cn)/""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /**
     * 解析入口。返回的 [MediaResult] 里视频地址是 progressive 流。
     */
    suspend fun parse(rawUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = resolveShortLink(rawUrl)
            when {
                Regex("""/bangumi/play/(ep\d+)""").find(url) != null ->
                    parseBangumi(url)

                else -> parseVideo(url)
            }
        }.recoverCatching { e ->
            throw IllegalStateException(friendly(e.message), e)
        }
    }

    // ==================== 短链 ====================

    /** b23.tv / bili2233.cn 短链：GET 一次拿最终地址（不读 body）。 */
    private fun resolveShortLink(url: String): String {
        if (!Regex("""(b23\.tv|bili2233\.cn)""", RegexOption.IGNORE_CASE).containsMatchIn(url)) return url
        return runCatching {
            val request = Request.Builder().url(url).header("User-Agent", desktopUa).build()
            client.newCall(request).execute().use { resp ->
                resp.request.url.toString()
            }
        }.getOrDefault(url)
    }

    // ==================== 普通视频 ====================

    private suspend fun parseVideo(url: String): MediaResult {
        val bvid = Regex("""(BV[0-9A-Za-z]{10})""").find(url)?.groupValues?.get(1)
        val aid = Regex("""/av(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: Regex("""[?&]aid=(\d+)""").find(url)?.groupValues?.get(1)

        require(bvid != null || aid != null) { "链接里没找到 BV 号，可能是番剧或动态，暂不支持" }

        val view = signedApi(
            "/x/web-interface/view",
            if (bvid != null) mapOf("bvid" to bvid) else mapOf("aid" to aid!!),
        ) ?: error("B 站接口无响应（可能被风控），稍后重试")
        val code = view["code"]?.jsonPrimitive?.longOrNull ?: -1
        if (code != 0L) {
            val msg = view["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            error("B 站返回错误：${msg.ifBlank { "code=$code" }}")
        }
        val data = view["data"]?.jsonObject ?: error("B 站返回数据为空")
        val title = data["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "B站视频" }
        val cover = data["pic"]?.jsonPrimitive?.contentOrNull
        val author = data["owner"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val pages = data["pages"].jsonArraySafe()
        val page = pages.firstOrNull()
        val cid = page?.get("cid")?.jsonPrimitive?.longOrNull
        val partName = page?.get("part")?.jsonPrimitive?.contentOrNull.orEmpty()
        require(cid != null) { "没拿到 cid，无法取播放地址" }

        val play = fetchProgressive(bvid, aid, cid)
        return MediaResult(
            type = MediaType.VIDEO,
            title = if (partName.isNotBlank() && pages.size > 1) "$title · $partName" else title,
            videoUrl = play.first,
            coverUrl = cover,
            author = author,
            source = "bilibili",
            videoQualities = play.second,
        )
    }

    /**
     * 取 progressive 播放地址。
     *
     * 先按 `qn=64`（720P）请求，接口在无登录时会自动降到能给的最高档
     * （返回体 `quality` 会告诉你实际给了多少）。`durl` 里通常只有一条，
     * 但多 P / 部分视频会给备份地址，一并收进候选。
     */
    private suspend fun fetchProgressive(
        bvid: String?,
        aid: String?,
        cid: Long,
    ): Pair<String, List<VideoQuality>> {
        val params = buildMap {
            if (bvid != null) put("bvid", bvid) else put("avid", aid.orEmpty())
            put("cid", cid.toString())
            put("qn", "64")
            put("fnval", "1")
            put("fnver", "0")
            put("fourk", "0")
            // html5 平台 + high_quality：匿名也能给到 720P progressive mp4
            put("platform", "html5")
            put("high_quality", "1")
        }
        val root = signedApi("/x/player/playurl", params) ?: error("取播放地址失败（B 站无响应）")
        val code = root["code"]?.jsonPrimitive?.longOrNull ?: -1
        if (code != 0L) {
            val msg = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            error("取播放地址失败：${msg.ifBlank { "code=$code" }}")
        }
        val data = root["data"]?.jsonObject ?: error("播放地址为空")
        val quality = data["quality"]?.jsonPrimitive?.longOrNull ?: 0L
        val label = qualityLabel(quality)

        val durl = data["durl"].jsonArraySafe()
        val urls = buildList {
            durl.forEach { obj ->
                obj.strField("url")?.let { add(it) }
                // backup_url 可能缺省、可能是数组、也可能是显式 null（JsonNull 不是 Kotlin null！）
                obj.strField("backup_url")?.let { add(it) }
                obj.strList("backup_url").forEach { add(it) }
            }
        }.distinct()

        require(urls.isNotEmpty()) { "没有可下载的直链（番剧/付费内容需要登录）" }

        val qualities = listOf(VideoQuality(label = label, url = urls.first(), bitrate = 0))
        return urls.first() to qualities
    }

    private fun qualityLabel(quality: Long): String = when (quality) {
        127L, 126L, 125L, 120L -> "4K"
        116L -> "1080P60"
        112L -> "1080P+"
        80L -> "1080P"
        74L -> "720P60"
        64L -> "720P"
        32L -> "480P"
        16L -> "360P"
        else -> "原画"
    }

    // ==================== 番剧 ====================

    private suspend fun parseBangumi(url: String): MediaResult {
        val epId = Regex("""/bangumi/play/ep(\d+)""").find(url)?.groupValues?.get(1)
            ?: error("番剧链接里没找到 ep 号")
        val season = api("https://api.bilibili.com/pgc/view/web/season?ep_id=$epId")
            ?: error("番剧接口无响应")
        val result = season["result"]?.jsonObject ?: error(season["message"]?.jsonPrimitive?.contentOrNull ?: "番剧信息为空")
        val title = result["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "番剧" }
        val cover = result["cover"]?.jsonPrimitive?.contentOrNull
        val episodes = result["episodes"].jsonArraySafe()
        val ep = episodes.firstOrNull { it["id"]?.jsonPrimitive?.longOrNull == epId.toLongOrNull() }
            ?: episodes.firstOrNull() ?: error("没找到这一集")
        val epTitle = ep["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val longTitle = ep["long_title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val cid = ep["cid"]?.jsonPrimitive?.longOrNull

        // v2 播放端点：旧 /pgc/player/web/playurl 已不稳定，v2 返回结构与
        // 旧的兼容层不同——durl 在 result.durl（旧格式兜底）或 result.video.durl
        val playParams = buildMap {
            put("ep_id", epId)
            if (cid != null) put("cid", cid.toString())
            put("qn", "64")
            put("fnval", "1")
            put("fourk", "1")
        }
        val play = signedApi("/pgc/player/web/v2/playurl", playParams)
            ?: error("番剧播放地址获取失败")
        val data = play["result"]?.jsonObject ?: play["data"]?.jsonObject
            ?: error("番剧需要登录才能取播放地址（B 站限制）")
        val durl = data["durl"].jsonArraySafe().ifEmpty {
            data["video"]?.jsonObject?.get("durl").jsonArraySafe()
        }
        if (durl.isEmpty()) error("该集没有可下载的 progressive 流（可能仅提供 DASH 或需要登录）")
        val url0 = durl.firstOrNull()?.strField("url")
            ?: error("该集没有可下载的直链")

        return MediaResult(
            type = MediaType.VIDEO,
            title = listOf(title, epTitle, longTitle).filter { it.isNotBlank() }.joinToString(" · "),
            videoUrl = url0,
            coverUrl = cover,
            source = "bilibili",
            videoQualities = listOf(VideoQuality("番剧", url0)),
        )
    }

    /** 把底层报错翻译成能看懂的句子。 */
    private fun friendly(raw: String?): String {
        val msg = raw.orEmpty().ifBlank { "B 站解析失败" }
        return when {
            "412" in msg || "风控" in msg -> "B 站风控拦截了这次请求，稍后重试或换个网络"
            "登录" in msg -> "该内容需要登录 B 站才能获取"
            "not a JsonArray" in msg || "reflection" in msg ->
                "B 站返回了未知的数据结构，请更新到最新版本"
            else -> msg
        }
    }

    // ==================== 宽容 JSON 取值 ====================
    // B 站字段形状不稳定：同名字段可能是数组、单值字符串或显式 null。
    // kotlinx 里 JSON null 是 JsonNull 对象而非 Kotlin null，直接 ?.jsonArray
    // 会炸出 "is not a JsonArray"，所以这里全部软取。

    /** 数组安全取值：数组→元素列表；单对象→包一层；null/JsonNull/其它→空列表。 */
    private fun JsonElement?.jsonArraySafe(): List<JsonObject> = when (this) {
        is JsonArray -> mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(this)
        else -> emptyList()
    }

    private fun JsonObject.strField(key: String): String? = when (val el = this[key]) {
        is JsonPrimitive -> if (el is JsonNull) null else el.contentOrNull
        else -> null
    }

    private fun JsonObject.strList(key: String): List<String> = when (val el = this[key]) {
        is JsonArray -> el.mapNotNull { item ->
            (item as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
        }
        else -> emptyList()
    }
}
