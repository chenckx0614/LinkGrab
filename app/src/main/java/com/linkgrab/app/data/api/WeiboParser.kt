package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 微博解析（尽力而为）。
 *
 * 微博从 2023 起对匿名访问加了「访客系统」：直接请求 `m.weibo.cn` 的接口会被
 * 302 到 `passport.weibo.com/visitor`，必须先走一遍
 *
 *   POST /visitor/genvisitor   → 拿 tid
 *   POST /visitor/visitor?a=incarnate&t=<tid> → 换 SUB/SUBP cookie
 *
 * 才能读接口。这里用 OkHttp 的内存 CookieJar 完整走一遍；不少网络环境
 * （尤其是机房 IP）这一环会被直接拦掉，所以再兜一层：解析详情页 HTML 里
 * 内嵌的 `var $render_data = [...]`。
 *
 * 两条路都失败时给的是「微博拦截了匿名访问」这类能看懂的提示，而不是裸错误。
 */
class WeiboParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** 极简内存 CookieJar：只在这一个解析器实例里共享，不落盘。 */
    private val cookieJar = object : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { newCookie ->
                list.removeAll { it.name == newCookie.name }
                list.add(newCookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            return store[url.host].orEmpty().filter { it.expiresAt > now }
        }

        @Synchronized
        fun header(url: HttpUrl): String =
            loadForRequest(url).joinToString("; ") { "${it.name}=${it.value}" }
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val mobileUa =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

    fun canParse(text: String): Boolean =
        Regex("""(weibo\.com|weibo\.cn|t\.cn)/""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    suspend fun parse(rawUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = resolveShortLink(rawUrl)
            val id = extractStatusId(url)
                ?: error("链接里没找到微博正文 id（仅支持单条微博链接）")

            bootstrapVisitor()

            val viaApi = fetchStatusJson(id)?.let { parseStatus(it) }
            if (viaApi != null) return@runCatching viaApi

            val viaHtml = fetchDetailHtml(id)?.let { parseRenderData(it) }
            if (viaHtml != null) return@runCatching viaHtml

            error("微博拦截了匿名访问（访客校验未通过）。可以先用浏览器打开这条微博，再长按图片/视频保存")
        }
    }

    // ==================== 链接处理 ====================

    /** t.cn 短链：跟一次重定向。 */
    private fun resolveShortLink(url: String): String {
        if (!url.contains("t.cn/")) return url
        return runCatching {
            client.newCall(
                Request.Builder().url(url).header("User-Agent", mobileUa).build()
            ).execute().use { it.request.url.toString() }
        }.getOrDefault(url)
    }

    private fun extractStatusId(url: String): String? = listOf(
        Regex("""/detail/(\d{10,})"""),
        Regex("""/status/(\d{10,})"""),
        Regex("""[?&]id=(\d{10,})"""),
        Regex("""weibo\.com/\d+/([0-9A-Za-z]{9,})"""),
    ).firstNotNullOfOrNull { it.find(url)?.groupValues?.get(1) }

    // ==================== 访客系统 ====================

    private fun postForm(url: String, form: Map<String, String>, referer: String): String? =
        runCatching {
            val body = form.entries
                .joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
                .toRequestBody("application/x-www-form-urlencoded; charset=UTF-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", mobileUa)
                .header("Referer", referer)
                .post(body)
                .build()
            client.newCall(request).execute().use { resp -> resp.body?.string() }
        }.getOrNull()

    private fun bootstrapVisitor() {
        // 已经有 SUB cookie 就不用再走一遍（同一次 App 会话内复用）
        val probe = "https://m.weibo.cn/".toHttpUrlOrNull() ?: return
        if (cookieJar.header(probe).contains("SUB=")) return

        val gen = postForm(
            "https://passport.weibo.com/visitor/genvisitor",
            mapOf("cb" to "visitor_gray_callback", "tid" to "", "from" to "weibo", "webdriver" to "false"),
            "https://passport.weibo.com/visitor/visitor",
        ) ?: return
        val tid = runCatching {
            val jsonStart = gen.indexOf('{')
            val jsonEnd = gen.lastIndexOf('}')
            json.parseToJsonElement(gen.substring(jsonStart, jsonEnd + 1))
                .jsonObject["data"]?.jsonObject?.get("tid")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return

        postForm(
            "https://passport.weibo.com/visitor/visitor?a=incarnate",
            mapOf(
                "a" to "incarnate", "t" to tid, "w" to "2", "c" to "095", "gc" to "",
                "cb" to "visitor_gray_callback", "from" to "weibo",
            ),
            "https://passport.weibo.com/visitor/visitor",
        )
    }

    // ==================== 两条取数通道 ====================

    private fun fetchStatusJson(id: String): JsonObject? = runCatching {
        val request = Request.Builder()
            .url("https://m.weibo.cn/statuses/show?id=$id")
            .header("User-Agent", mobileUa)
            .header("Referer", "https://m.weibo.cn/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!text.startsWith("{")) return@use null
            json.parseToJsonElement(text).jsonObject.takeIf {
                it["id"] != null || it["text"] != null
            }
        }
    }.getOrNull()

    private fun fetchDetailHtml(id: String): String? = runCatching {
        val request = Request.Builder()
            .url("https://m.weibo.cn/detail/$id")
            .header("User-Agent", mobileUa)
            .build()
        client.newCall(request).execute().use { resp -> resp.body?.string() }
    }.getOrNull()

    /** `var $render_data = [{ status: {...} }, ...]` */
    private fun parseRenderData(html: String): MediaResult? = runCatching {
        val marker = html.indexOf("\$render_data")
        if (marker < 0) return@runCatching null
        val start = html.indexOf('[', marker)
        val end = html.indexOf("];", start)
        if (start < 0 || end < 0) return@runCatching null
        val array = json.parseToJsonElement(html.substring(start, end + 1)).jsonArray
        val status = array.firstOrNull()
            ?.jsonObject?.get("status")?.jsonObject
            ?: return@runCatching null
        parseStatus(status)
    }.getOrNull()

    // ==================== JSON → MediaResult ====================

    private fun parseStatus(status: JsonObject): MediaResult? {
        val text = stripHtml(status["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        val author = status["user"]?.jsonObject?.get("screen_name")?.jsonPrimitive?.contentOrNull.orEmpty()

        // 视频：page_info.media_info 里通常有 mp4 直链
        val pageInfo = status["page_info"]?.jsonObject
        val media = pageInfo?.get("media_info")?.jsonObject
        val video = media?.get("stream_url_hd")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: media?.get("stream_url")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        if (video != null) {
            return MediaResult(
                type = MediaType.VIDEO,
                title = text.ifBlank { "微博视频" }.take(80),
                videoUrl = video,
                coverUrl = pageInfo?.get("page_pic")?.let { picUrl(it) }
                    ?: media?.get("cover_url")?.jsonPrimitive?.contentOrNull,
                author = author,
                source = "weibo",
            )
        }

        // 图片：pics[].large.url 是原图
        val images = buildList {
            status["pics"]?.jsonArray?.forEach { entry ->
                val pic = entry.jsonObject
                (pic["large"] ?: pic["bmiddle"] ?: pic["thumbnail"])
                    ?.let { picUrl(it) }
                    ?.let { add(it) }
            }
        }
        if (images.isEmpty()) return null

        return MediaResult(
            type = MediaType.IMAGE,
            title = text.ifBlank { "微博图片" }.take(80),
            images = images.distinct(),
            coverUrl = images.first(),
            author = author,
            source = "weibo",
        )
    }

    /** pic_url 可能是对象 `{url: ...}`，也可能直接是字符串。 */
    private fun picUrl(element: kotlinx.serialization.json.JsonElement): String? =
        runCatching {
            if (element is JsonObject) {
                element["url"]?.jsonPrimitive?.contentOrNull
            } else {
                element.jsonPrimitive.contentOrNull
            }
        }.getOrNull()

    private fun stripHtml(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}
