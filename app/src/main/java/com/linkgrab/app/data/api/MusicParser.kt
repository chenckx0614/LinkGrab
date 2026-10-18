package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MusicResult
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
import java.util.concurrent.TimeUnit

/**
 * 音乐多源解析门面：酷我 / 网易云 / QQ 音乐。
 *
 * 三家都只接「单曲链接」，不做搜索。各自的取链方式不一样：
 *
 *  - **酷我**：`anti.s` 直链换发，不需要 token（原 [KuwoMusicParser] 的实现）
 *  - **网易云**：`api/song/enhance/player/url` + `api/song/detail`。免费曲直接
 *    给 mp3；VIP/数字专辑返回 `url:null`、`code:-110`，这时明确提示「需要会员」
 *  - **QQ 音乐**：`musicu.fcg` 的 `vkey.GetVkeyServer` 换 vkey，拼
 *    `sip + purl`。免费曲有 purl，付费曲 purl 为空
 *
 * 网易云/QQ 的直链都带时效签名（通常几小时），下载要尽快；链接过期表现为
 * 403，重新解析一次即可。
 */
class MusicParser {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val kuwo = KuwoMusicParser()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 这段文本属于哪个平台；null = 都不是。 */
    fun detect(text: String): MusicSource? = when {
        kuwo.canParse(text) -> MusicSource.KUWO
        NETEASE_PATTERN.containsMatchIn(text) -> MusicSource.NETEASE
        QQ_PATTERN.containsMatchIn(text) -> MusicSource.QQ
        else -> null
    }

    fun canParse(text: String): Boolean = detect(text) != null

    suspend fun parse(text: String): Result<MusicResult> = when (detect(text)) {
        MusicSource.KUWO -> kuwo.parse(text)
        MusicSource.NETEASE -> parseNetease(text)
        MusicSource.QQ -> parseQq(text)
        null -> Result.failure(IllegalArgumentException("不是酷我 / 网易云 / QQ 音乐的链接"))
    }

    // ==================== 网易云 ====================

    private suspend fun parseNetease(text: String): Result<MusicResult> = withContext(Dispatchers.IO) {
        runCatching {
            val id = Regex("""(?:song|songDetail)[?/#]+(?:id=)?(\d{4,})""").find(text)?.groupValues?.get(1)
                ?: Regex("""[?&]id=(\d{4,})""").find(text)?.groupValues?.get(1)
                ?: error("链接里没找到网易云歌曲 id")

            val detail = getJson("https://music.163.com/api/song/detail?ids=[$id]")
                ?: error("网易云接口无响应")
            val song = detail["songs"]?.jsonArray?.firstOrNull()?.jsonObject
                ?: error("网易云没返回这首歌曲的信息")
            val title = song["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val artist = song["artists"]?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("/").orEmpty()
            val cover = song["album"]?.jsonObject?.get("picUrl")?.jsonPrimitive?.contentOrNull

            // 先试 320k，拿不到再退 128k
            var playUrl: String? = null
            var bitrate = 0
            for (br in listOf(320000, 192000, 128000)) {
                val payload = getJson("https://music.163.com/api/song/enhance/player/url?ids=[$id]&br=$br")
                val entry = payload?.get("data")?.jsonArray?.firstOrNull()?.jsonObject
                val url = entry?.get("url")?.jsonPrimitive?.contentOrNull
                if (!url.isNullOrBlank()) {
                    playUrl = url
                    bitrate = entry["br"]?.jsonPrimitive?.longOrNull?.toInt() ?: br
                    break
                }
            }
            if (playUrl == null) {
                error("这首在网易云需要会员 / 数字专辑才能下载（接口返回无可用地址）")
            }

            MusicResult(
                rid = id,
                title = title,
                artist = artist,
                coverUrl = cover,
                lyric = fetchNeteaseLyric(id),
                playUrl320k = playUrl,
                source = "netease",
                bitrateLabel = "${bitrate / 1000}k",
            )
        }
    }

    private fun fetchNeteaseLyric(id: String): String {
        val payload = getJson("https://music.163.com/api/song/lyric?id=$id&lv=-1&kv=-1&tv=-1") ?: return ""
        return payload["lrc"]?.jsonObject?.get("lyric")?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    // ==================== QQ 音乐 ====================

    private suspend fun parseQq(text: String): Result<MusicResult> = withContext(Dispatchers.IO) {
        runCatching {
            val mid = Regex("""/songDetail/([0-9A-Za-z]{10,})""").find(text)?.groupValues?.get(1)
                ?: Regex("""[?&]songmid=([0-9A-Za-z]{10,})""").find(text)?.groupValues?.get(1)
                ?: error("链接里没找到 QQ 音乐 songmid")

            val detailPayload = """
                {"req_0":{"module":"music.pf_song_detail_svr","method":"get_song_detail",
                "param":{"song_mid":"$mid"}}}
            """.trimIndent()
            val detail = getJson(
                "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" + java.net.URLEncoder.encode(detailPayload, "UTF-8")
            )
            val track = detail?.get("req_0")?.jsonObject?.get("data")?.jsonObject?.get("track_info")?.jsonObject
            val title = track?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            val artist = track?.get("singer")?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("/").orEmpty()
            val albumMid = track?.get("album")?.jsonObject?.get("mid")?.jsonPrimitive?.contentOrNull
            val cover = albumMid?.let { "https://y.gtimg.cn/music/photo_new/T002R800x800M000$it.jpg" }

            val vkeyPayload = """
                {"req_0":{"module":"vkey.GetVkeyServer","method":"CgiGetVkey",
                "param":{"guid":"10000","songmid":["$mid"],"songtype":[0],"uin":"0","platform":"20"}},
                "comm":{"uin":0,"format":"json","ct":24,"cv":0}}
            """.trimIndent()
            val vkey = getJson(
                "https://u.y.qq.com/cgi-bin/musicu.fcg?data=" + java.net.URLEncoder.encode(vkeyPayload, "UTF-8")
            )
            val data = vkey?.get("req_0")?.jsonObject?.get("data")
            val info = data?.jsonObject?.get("midurlinfo")?.jsonArray?.firstOrNull()?.jsonObject
            val purl = info?.get("purl")?.jsonPrimitive?.contentOrNull.orEmpty()
            if (purl.isBlank()) {
                error("这首在 QQ 音乐需要会员 / 版权限制（接口没有返回可下载地址）")
            }
            val sip = data?.jsonObject?.get("sip")?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull.orEmpty()
            val playUrl = sip + purl

            MusicResult(
                rid = mid,
                title = title.ifBlank { "QQ音乐单曲" },
                artist = artist,
                coverUrl = cover,
                lyric = fetchQqLyric(mid),
                playUrl320k = playUrl,
                source = "qq",
                bitrateLabel = "128k",
            )
        }
    }

    private fun fetchQqLyric(mid: String): String {
        val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
            "?songmid=$mid&format=json&nobase64=1&g_tk=5381"
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", desktopUa)
                .header("Referer", "https://y.qq.com/")
                .build()
            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                json.parseToJsonElement(raw).jsonObject["lyric"]?.jsonPrimitive?.contentOrNull.orEmpty()
            }
        }.getOrDefault("")
    }

    // ==================== 公共 ====================

    private fun getJson(url: String): JsonObject? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", desktopUa)
            .header("Referer", "https://music.163.com/")
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return@use null
            json.parseToJsonElement(text).jsonObject
        }
    }.getOrNull()

    private companion object {
        val NETEASE_PATTERN = Regex("""music\.163\.com/""", RegexOption.IGNORE_CASE)
        val QQ_PATTERN = Regex("""(y\.qq\.com|c\.y\.qq\.com|i\.y\.qq\.com)/""", RegexOption.IGNORE_CASE)
    }
}

/** 音乐来源。 */
enum class MusicSource(val label: String) {
    KUWO("酷我"),
    NETEASE("网易云"),
    QQ("QQ音乐"),
}
