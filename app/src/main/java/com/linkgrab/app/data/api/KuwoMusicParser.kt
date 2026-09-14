package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MusicResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 酷我音乐解析。
 *
 * 调研结论（2026-09，实测）：公共音乐解析 API（kit9 / TuneHub / 洛雪公共后端）
 * 当前已全部下线或半废；网易云、QQ 的直链都带一次性时效签名 + 防盗链，
 * 纯客户端基本下载不到。酷我的 `anti.s` 直链转换接口不需要 token、防盗链
 * 最松，是唯一实测能稳定拿到真实 MP3 数据的链路。
 *
 * 所以输入限定为**酷我分享链接**（用户从酷我 App 复制）。酷我版权覆盖面广
 * （移动旗下买了大量版权），大部分歌都能下到。
 *
 * 链路：
 *  1. 从链接 / 分享文本里提取 rid（纯数字歌曲 id）
 *  2. `anti.s` 换 128k / 320k 直链（无需 token）
 *  3. `songinfoandlrc` 拿歌词，并从首行「歌名 - 歌手」解析出标题与歌手
 *  4. 封面：抓酷我移动播放页的 og:image（尽力而为，拿不到就留空）
 */
class KuwoMusicParser {

    // 酷我的搜索/详情接口已全面接入 csrf，主页也不再下发 token，所以
    // 这两个「不需要 token」的接口是当前唯一可靠入口。UA / Referer 用桌面
    // 端，实测移动 UA 对歌词接口反而会被拒。
    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** 判断一段文本是不是酷我链接 / 含酷我链接。 */
    fun canParse(text: String): Boolean = extractRid(text) != null

    /**
     * 从分享链接或分享文本里提取酷我 rid。
     * 支持：
     *  - https://kuwo.cn/play_detail/{rid}
     *  - https://www.kuwo.cn/play_detail/{rid}
     *  - https://m.kuwo.cn/newh5app/play_detail/{rid}
     *  - https://kuwo.cn/yinyue/{rid}
     *  - 以及「分享xx的单曲《xx》 https://kuwo.cn/... 」这种整段文本
     */
    fun extractRid(text: String): String? {
        val t = text.trim()
        // play_detail / yinyue 后面跟的数字就是 rid
        val patterns = listOf(
            Regex("""(?:play_detail|yinyue)/(\d+)"""),
            // 兜底：kuwo.cn 域名下任何 6 位以上的数字段
            Regex("""kuwo\.cn[^\d]*(\d{5,})"""),
        )
        for (p in patterns) {
            val m = p.find(t)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    /** 解析酷我链接，返回带直链与歌词的 [MusicResult]。 */
    suspend fun parse(input: String): Result<MusicResult> = withContext(Dispatchers.IO) {
        try {
            val rid = extractRid(input)
                ?: return@withContext Result.failure(
                    Exception("没认出酷我链接。请从酷我音乐 App 里「分享 → 复制链接」再粘贴。")
                )

            // 直链：128k 一定有；320k 视歌曲而定，拿不到就留空。
            val url128 = getPlayUrl(rid, "128k")
            val url320 = getPlayUrl(rid, "320k")
            if (url128 == null) {
                return@withContext Result.failure(
                    Exception("这首歌暂时拿不到直链（可能暂无版权或受地区限制）。")
                )
            }

            // 歌词 + 歌名歌手（从首行解析）。
            val (titleFromLrc, artistFromLrc, lyric) = getLyric(rid)

            // 分享文本里有时直接带「《歌名》」，可作兜底。
            val titleFromText = Regex("""《([^》]+)》""").find(input)?.groupValues?.get(1)

            // 封面：尽力而为。
            val cover = getCover(rid)

            Result.success(
                MusicResult(
                    rid = rid,
                    title = titleFromLrc ?: titleFromText ?: "",
                    artist = artistFromLrc ?: "",
                    coverUrl = cover,
                    lyric = lyric,
                    playUrl128k = url128,
                    playUrl320k = url320,
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("解析失败：${e.message}", e))
        }
    }

    /**
     * `anti.s` 直链转换。成功返回 CDN 直链文本；失败返回 "refuse request!"
     * 或空，这里统一归一成 null。
     */
    private fun getPlayUrl(rid: String, br: String): String? {
        val url = "https://antiserver.kuwo.cn/anti.s" +
            "?type=convert_url&rid=MUSIC_$rid&format=mp3&br=$br"
        return try {
            val body = httpGet(url, referer = null)
            body.trim().takeIf { it.startsWith("http") }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 歌词接口（无需 token，但对 UA 敏感，必须桌面 UA + m.kuwo.cn Referer）。
     * 返回三元组：(歌名?, 歌手?, 完整歌词文本)。接口失败时三者都尽量兜底。
     */
    private fun getLyric(rid: String): Triple<String?, String?, String> {
        return try {
            val body = httpGet(
                "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$rid",
                referer = "https://m.kuwo.cn/",
            )
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: return Triple(null, null, "")
            val list = data["lrclist"]?.jsonArray ?: return Triple(null, null, "")

            // 首行通常是「歌名 - 歌手 (xxx)」，拆出标题和歌手。
            var title: String? = null
            var artist: String? = null
            val first = list.firstOrNull()?.jsonObject?.get("lineLyric")?.jsonPrimitive?.content
            if (first != null && " - " in first) {
                val parts = first.split(" - ", limit = 2)
                title = parts[0].trim().ifEmpty { null }
                artist = parts.getOrNull(1)
                    ?.replace(Regex("""\s*\(.*?\)\s*$"""), "")
                    ?.trim()
                    ?.ifEmpty { null }
            }

            // 拼成带时间轴的完整歌词。
            val lyric = buildString {
                for (item in list) {
                    val line = item.jsonObject["lineLyric"]?.jsonPrimitive?.content ?: continue
                    val time = item.jsonObject["time"]?.jsonPrimitive?.content
                    if (!time.isNullOrEmpty()) append("[$time] ")
                    append(line).append('\n')
                }
            }.trim()

            Triple(title, artist, lyric)
        } catch (e: Exception) {
            Triple(null, null, "")
        }
    }

    /** 从酷我移动播放页 HTML 里抓 og:image 作封面；抓不到返回 null。 */
    private fun getCover(rid: String): String? {
        return try {
            val html = httpGet(
                "https://m.kuwo.cn/newh5app/play_detail/$rid",
                referer = "https://m.kuwo.cn/",
            )
            Regex("""<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)""")
                .find(html)?.groupValues?.get(1)
                ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']""")
                    .find(html)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    private fun httpGet(url: String, referer: String?): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", desktopUa)
            .header("Accept", "*/*")
        if (referer != null) builder.header("Referer", referer)
        client.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("空响应")
        }
    }
}
