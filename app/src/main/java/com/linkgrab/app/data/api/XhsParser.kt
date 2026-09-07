package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Native Xiaohongshu (RED) parser ported from wujunwei928/parse-video-py
 * (parser/redbook.py).
 *
 * Flow:
 * 1. GET the share link (short xhslink.com links redirect to the full note
 *    page) with a desktop UA and follow redirects.
 * 2. Extract the `window.__INITIAL_STATE__ = {...}</script>` JSON blob.
 *    The payload uses the JS literal `undefined`, which is normalised to
 *    `null` before parsing.
 * 3. Read `note.noteDetailMap[currentNoteId].note`:
 *    - video note → video.media.stream.h264[0].masterUrl
 *    - image note → imageList[]; for each item, build the watermark-free
 *      `https://ci.xiaohongshu.com/notes_pre_post/...?imageView2/format/jpg`
 *      URL when the default URL lives under `notes_pre_post`, otherwise
 *      keep the original URL. Live Photos additionally expose
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

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    suspend fun parse(shareUrl: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(shareUrl)
                .header("User-Agent", desktopUa)
                .header("Referer", "https://www.xiaohongshu.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            val html = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                resp.body?.string() ?: throw Exception("空响应")
            }

            val rawState = Regex(
                """window\.__INITIAL_STATE__\s*=\s*(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            ).find(html)?.groupValues?.get(1)?.trim()
                ?: return@withContext Result.failure(Exception("页面数据结构解析失败（__INITIAL_STATE__ 不存在）"))

            // The payload is JS, not strict JSON: `undefined` appears as a
            // value literal. Normalise it before handing to the JSON parser.
            val stateJson = rawState
                .replace(Regex(""":\s*undefined(\s*[,}])"""), ":null$1")
                .replace(Regex("""\[\s*undefined(\s*[,]])"""), "[null$1")

            val root = json.parseToJsonElement(stateJson).jsonObject
            val note = root["note"]?.jsonObject
                ?: return@withContext Result.failure(Exception("页面数据结构异常（note 缺失）"))

            val noteId = note["currentNoteId"]?.jsonPrimitive?.content
            if (noteId.isNullOrEmpty() || noteId == "undefined") {
                return@withContext Result.failure(Exception("分享链接已过期，请重新复制最新链接"))
            }

            val data = note["noteDetailMap"]?.jsonObject
                ?.get(noteId)?.jsonObject
                ?.get("note")?.jsonObject
                ?: return@withContext Result.failure(Exception("未找到笔记详情"))

            val title = data["title"]?.jsonPrimitive?.content ?: "小红书笔记"

            // ---- video note ----
            val videoUrl = data["video"]?.jsonObject
                ?.get("media")?.jsonObject
                ?.get("stream")?.jsonObject
                ?.get("h264")?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("masterUrl")?.jsonPrimitive?.content

            if (!videoUrl.isNullOrEmpty()) {
                val cover = data["imageList"]?.jsonArray
                    ?.firstOrNull()?.jsonObject
                    ?.get("urlDefault")?.jsonPrimitive?.content
                return@withContext Result.success(
                    MediaResult(
                        type = MediaType.VIDEO,
                        title = title,
                        videoUrl = videoUrl,
                        coverUrl = cover,
                        source = "xiaohongshu",
                    )
                )
            }

            // ---- image note (图集 + 实况) ----
            val images = mutableListOf<String>()
            val livePhotos = mutableMapOf<Int, String>()

            data["imageList"]?.jsonArray?.forEachIndexed { index, imgEl ->
                val img = imgEl.jsonObject
                val urlDefault = img["urlDefault"]?.jsonPrimitive?.content ?: return@forEachIndexed

                // Build the watermark-free URL on ci.xiaohongshu.com when the
                // asset lives under notes_pre_post; otherwise keep the original.
                val imageUrl = if ("notes_pre_post" in urlDefault) {
                    val imageId = urlDefault.substringAfterLast('/').substringBefore('!')
                    val spectrum = if ("spectrum" in urlDefault) "spectrum/" else ""
                    "https://ci.xiaohongshu.com/notes_pre_post/$spectrum$imageId?imageView2/format/jpg"
                } else {
                    urlDefault
                }
                images.add(imageUrl)

                // 实况照片 (Live Photo): still frame + h264 stream
                val isLive = img["livePhoto"]?.let { runCatching { it.jsonPrimitive.boolean }.getOrNull() } ?: false
                if (isLive) {
                    val liveUrl = img["stream"]?.jsonObject
                        ?.get("h264")?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("masterUrl")?.jsonPrimitive?.content
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
}
