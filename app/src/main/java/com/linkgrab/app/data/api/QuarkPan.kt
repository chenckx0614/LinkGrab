package com.linkgrab.app.data.api

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * 夸克网盘直链（MVP：仅「我的网盘」）。
 *
 * 端点参考自公开行为（LinkSwift 等开源脚本同款公开接口）：
 * - 列表 GET /1/clouddrive/file/sort（drive-pc.quark.cn）
 * - 直链 POST /1/clouddrive/file/download（drive-pc.quark.cn）
 * 鉴权只依赖用户自己在 WebView 登录后的 Cookie，不保存、不上传任何凭证。
 * 下载必须带夸克客户端 UA + Referer，否则 CDN 会拒绝。
 */

data class QuarkFile(
    val fid: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
)

data class QuarkDirectLink(
    val fileName: String,
    val size: Long,
    val url: String,
)

class QuarkPanException(message: String) : Exception(message)

/**
 * 网盘站点配置。夸克 / UC 同属阿里 UC 系，API 路径、Cookie 体系一致，
 * 只差域名、pr 参数和客户端 UA。
 */
data class PanConfig(
    val name: String,
    val emoji: String,
    /** 网页端首页（WebView 登录页 + Cookie 域）。 */
    val homeUrl: String,
    /** API 根，如 https://drive-pc.quark.cn/1/clouddrive */
    val apiBase: String,
    /** download 接口的 pr 参数（ucpro / UCBrowser）。 */
    val pr: String,
    /** PC 客户端 UA：直链下载与列表接口都认这个。 */
    val clientUa: String,
)

class QuarkPan(private val config: PanConfig) {

    companion object {
        private const val PAGE_SIZE = 50L

        val QUARK = PanConfig(
            name = "夸克",
            emoji = "🪐",
            homeUrl = "https://pan.quark.cn/",
            apiBase = "https://drive-pc.quark.cn/1/clouddrive",
            pr = "ucpro",
            clientUa =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) quark-cloud-drive/3.20.0 Chrome/112.0.5615.165 " +
                    "Electron/24.1.3.8 Safari/537.36 Channel/pckk_other_ch",
        )

        val UC = PanConfig(
            name = "UC",
            emoji = "🐿️",
            homeUrl = "https://drive.uc.cn/",
            apiBase = "https://pc-api.uc.cn/1/clouddrive",
            pr = "UCBrowser",
            clientUa =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) uc-cloud-drive/2.5.20 Chrome/100.0.4896.160 " +
                    "Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch",
        )

        /** 支持的网盘（新盘在这里加）。 */
        val SUPPORTED = listOf(QUARK, UC)
    }

    /** 判断用户是否已在应用内 WebView 登录该盘（看关键会话 Cookie）。 */
    fun isLoggedIn(): Boolean {
        val cookie = CookieManager.getInstance().getCookie(config.homeUrl) ?: return false
        return cookie.contains("__puus=") || cookie.contains("__pus=")
    }

    /** 取该盘域下的全部 Cookie。 */
    private fun currentCookie(): String? =
        CookieManager.getInstance().getCookie(config.homeUrl)

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 列目录。fid 传 "" 表示根目录。 */
    suspend fun listFiles(fid: String, page: Int): Pair<List<QuarkFile>, Boolean> =
        withContext(Dispatchers.IO) {
            val cookie = currentCookie() ?: throw QuarkPanException("未登录${config.name}网盘")
            val url = buildString {
                append("${config.apiBase}/file/sort?pr=${config.pr}&fr=pc")
                append("&pdir_fid=").append(if (fid.isEmpty()) "" else fid)
                append("&_page=").append(page)
                append("&_size=").append(PAGE_SIZE)
                append("&_fetch_total=1")
                append("&_sort=file_type:asc,updated_at:desc")
                append("&__t=").append(System.currentTimeMillis())
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", config.clientUa)
                .header("Referer", config.homeUrl)
                .header("Cookie", cookie)
                .build()

            client.newCall(request).execute().use { resp ->
                val bodyText = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw QuarkPanException("网络错误（HTTP ${resp.code}）")
                val root = parseObject(bodyText)
                    ?: throw QuarkPanException("响应解析失败")
                val code = root.longField("code") ?: root.longField("status_code")
                if (code != null && code != 0L) throw codeToException(code)
                val data = root["data"]?.jsonObject
                    ?: throw QuarkPanException("响应缺少 data")
                val list = data["list"]?.jsonArray
                    ?: return@use Pair(emptyList<QuarkFile>(), false)
                val files = list.mapNotNull { item ->
                    val obj = item as? JsonObject ?: return@mapNotNull null
                    val fid = obj.textField("fid") ?: obj.textField("_id") ?: return@mapNotNull null
                    val name = obj.textField("file_name") ?: return@mapNotNull null
                    // 夸克：dir=true 是文件夹；file=true 是文件（两个都可能缺，兜底判断 size 字段结构）
                    val isFolder = obj.booleanField("dir")
                        ?: (obj.booleanField("file")?.not() ?: false)
                    val size = obj.longField("size") ?: 0L
                    QuarkFile(fid, name, isFolder, size)
                }
                val total = data["_total"]?.jsonPrimitive?.content?.toLongOrNull() ?: -1L
                val hasMore = if (total >= 0) (page.toLong() * PAGE_SIZE) < total else files.size >= PAGE_SIZE
                Pair(files, hasMore)
            }
        }

    /** 取一批文件（fid）的直链。夸克单次最多 15 个，MVP 只支持单文件调用。 */
    suspend fun getDirectLink(fid: String): QuarkDirectLink = withContext(Dispatchers.IO) {
        val cookie = currentCookie() ?: throw QuarkPanException("未登录${config.name}网盘")
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            JsonObject(mapOf("fids" to kotlinx.serialization.json.JsonPrimitive(fid))),
        ).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${config.apiBase}/file/download?entry=ft&fr=pc&pr=${config.pr}")
            .post(body)
            .header("User-Agent", config.clientUa)
            .header("Referer", config.homeUrl)
            .header("Cookie", cookie)
            .build()

        client.newCall(request).execute().use { resp ->
            val bodyText = resp.body?.string().orEmpty()
            val root = parseObject(bodyText) ?: throw QuarkPanException("响应解析失败")
            val code = root.longField("code") ?: root.longField("status_code")
            if (code != null && code != 0L) throw codeToException(code)
            val data = root["data"]?.jsonArray?.firstOrNull() as? JsonObject
                ?: throw QuarkPanException("没有拿到直链")
            val url = data.textField("download_url")
                ?: throw QuarkPanException("直链为空，稍后再试")
            QuarkDirectLink(
                fileName = data.textField("file_name") ?: "file",
                size = data.longField("size") ?: 0L,
                url = url,
            )
        }
    }

    /** 把直链下载到系统下载目录（Download/LinkGrab），必须带夸克 UA + Referer + Cookie。 */
    suspend fun download(context: Context, link: QuarkDirectLink): Unit =
        withContext(Dispatchers.IO) {
            val cookie = currentCookie().orEmpty()
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
            val request = Request.Builder()
                .url(link.url)
                .header("User-Agent", config.clientUa)
                .header("Referer", config.homeUrl)
                .header("Cookie", cookie)
                .build()

            val safeName = link.fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/LinkGrab")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw QuarkPanException("无法创建下载文件")

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    resolver.delete(uri, null, null)
                    throw QuarkPanException("下载失败（HTTP ${resp.code}），直链可能已过期")
                }
                val body = resp.body ?: run {
                    resolver.delete(uri, null, null)
                    throw QuarkPanException("空响应")
                }
                resolver.openOutputStream(uri)?.use { out ->
                    body.byteStream().use { input -> input.copyTo(out) }
                } ?: run {
                    resolver.delete(uri, null, null)
                    throw QuarkPanException("无法写入文件")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }

    private fun codeToException(code: Long): QuarkPanException = when (code) {
        31001L -> QuarkPanException("登录已过期，请重新登录${config.name}网盘")
        23018L -> QuarkPanException("超出游客可获取的大小限制，请先登录网盘")
        else -> QuarkPanException("${config.name}返回错误（代码 $code）")
    }

    // ---- 宽容解析：夸克返回字段时变，全部软取 ----

    private fun parseObject(text: String): JsonObject? = try {
        json.parseToJsonElement(text).jsonObject
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.longField(key: String): Long? =
        (this[key]?.jsonPrimitive?.content?.toLongOrNull()) ?: (this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong())

    private fun JsonObject.textField(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private fun JsonObject.booleanField(key: String): Boolean? = when (val v = this[key]?.jsonPrimitive?.content) {
        null -> null
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}
