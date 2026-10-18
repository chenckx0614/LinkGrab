package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * B 站（bilibili）解析。
 *
 * 支持：
 *  - `bilibili.com/video/BV…` / `av123` / 分享文本里的 BV 号
 *  - `b23.tv/xxxx` 短链（先跟一次重定向拿到真地址）
 *  - `bilibili.com/bangumi/play/ep123` 番剧单集（尽力而为：多数需要登录）
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

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 匿名接口也会看 cookie：没有 `buvid3` 时部分接口返回 412（风控）。
     * 随机生成一个即可满足校验，不需要真实浏览器指纹。
     */
    private val buvidCookie = "buvid3=${UUID.randomUUID()}-${System.currentTimeMillis() % 100000}infoc"

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

    private fun parseVideo(url: String): MediaResult {
        val bvid = Regex("""(BV[0-9A-Za-z]{10})""").find(url)?.groupValues?.get(1)
        val aid = Regex("""/av(\d+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.get(1)
            ?: Regex("""[?&]aid=(\d+)""").find(url)?.groupValues?.get(1)

        require(bvid != null || aid != null) { "链接里没找到 BV 号，可能是番剧或动态，暂不支持" }

        val viewUrl = if (bvid != null) {
            "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
        } else {
            "https://api.bilibili.com/x/web-interface/view?aid=$aid"
        }
        val view = api(viewUrl) ?: error("B 站接口无响应（可能被风控），稍后重试")
        val code = view["code"]?.jsonPrimitive?.longOrNull ?: -1
        if (code != 0L) {
            val msg = view["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            error("B 站返回错误：${msg.ifBlank { "code=$code" }}")
        }
        val data = view["data"]?.jsonObject ?: error("B 站返回数据为空")
        val title = data["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "B站视频" }
        val cover = data["pic"]?.jsonPrimitive?.contentOrNull
        val author = data["owner"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
        val pages = data["pages"]?.jsonArray
        val page = pages?.firstOrNull()?.jsonObject
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
    private fun fetchProgressive(
        bvid: String?,
        aid: String?,
        cid: Long,
    ): Pair<String, List<VideoQuality>> {
        val idParam = if (bvid != null) "bvid=$bvid" else "avid=$aid"
        val url = "https://api.bilibili.com/x/player/playurl?" +
            "$idParam&cid=$cid&qn=64&fnval=1&fnver=0&fourk=0&platform=html5&high_quality=1"
        val root = api(url) ?: error("取播放地址失败（B 站无响应）")
        val code = root["code"]?.jsonPrimitive?.longOrNull ?: -1
        if (code != 0L) {
            val msg = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
            error("取播放地址失败：${msg.ifBlank { "code=$code" }}")
        }
        val data = root["data"]?.jsonObject ?: error("播放地址为空")
        val quality = data["quality"]?.jsonPrimitive?.longOrNull ?: 0L
        val label = qualityLabel(quality)

        val durl = data["durl"]?.jsonArray
        val urls = buildList {
            durl?.forEach { entry ->
                val obj = entry.jsonObject
                obj["url"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
                obj["backup_url"]?.jsonArray?.forEach { backup ->
                    backup.jsonPrimitive.contentOrNull?.let { add(it) }
                }
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

    private fun parseBangumi(url: String): MediaResult {
        val epId = Regex("""/bangumi/play/ep(\d+)""").find(url)?.groupValues?.get(1)
            ?: error("番剧链接里没找到 ep 号")
        val season = api("https://api.bilibili.com/pgc/view/web/season?ep_id=$epId")
            ?: error("番剧接口无响应")
        val result = season["result"]?.jsonObject ?: error(season["message"]?.jsonPrimitive?.contentOrNull ?: "番剧信息为空")
        val title = result["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "番剧" }
        val cover = result["cover"]?.jsonPrimitive?.contentOrNull
        val episodes = result["episodes"]?.jsonArray
        val ep = episodes?.map { it.jsonObject }
            ?.firstOrNull { it["id"]?.jsonPrimitive?.longOrNull == epId.toLongOrNull() }
            ?: episodes?.firstOrNull()?.jsonObject ?: error("没找到这一集")
        val epTitle = ep["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val longTitle = ep["long_title"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val play = api(
            "https://api.bilibili.com/pgc/player/web/playurl?ep_id=$epId&qn=64&fnval=1&fourk=0"
        ) ?: error("番剧播放地址获取失败")
        val data = play["result"]?.jsonObject ?: play["data"]?.jsonObject
            ?: error("番剧需要登录才能取播放地址（B 站限制）")
        val durl = data["durl"]?.jsonArray ?: error("该集没有可下载的 progressive 流（可能仅提供 DASH）")
        val url0 = durl.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
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
            else -> msg
        }
    }
}
