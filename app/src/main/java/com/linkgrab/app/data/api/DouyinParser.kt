package com.linkgrab.app.data.api

import android.os.SystemClock
import android.util.Log
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.data.model.VideoQuality
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
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
import okhttp3.ConnectionPool
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
 *    - images → images[] (album). 候选 URL 按「不含水印模板优先」挑选
 *      （`download_url_list` 与 `url_list` 谁无水印随接口版本而变）。
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
        private const val TAG = "DouyinParser"

        /** 图文作品的 aweme_type：2 普通图集 / 68 单图+配乐 / 150 图文。 */
        private val NOTE_TYPES = setOf("2", "68", "150")

        /**
         * 引导失败后的冷却时间：通道全灭时不必每次解析都再去白跑两三个请求，
         * 冷却过后再试。这是「解析忽快忽慢」里最贵的一段浪费。
         */
        private const val TTWID_COOLDOWN_MS = 90_000L

        /**
         * 各层通道的时间预算（从解析开始算起）。到点就跳过后面的层，把时间让给
         * 降级链 —— 用户等的是结果，不是「一定要在原生通道里成功」。
         */
        private const val SESSION_RETRY_BUDGET_MS = 9_000L
        private const val HTML_BUDGET_MS = 16_000L
        private const val HTML_HARD_DEADLINE_MS = 22_000L

        /**
         * 全应用共用的 OkHttp 客户端。
         *
         * 抖音解析每次要打好几个 HTTPS 端点，客户端按实例 new 出来会把连接池、
         * HTTP/2 多路复用、TLS session 票据全部丢掉，每次解析都要重新握手
         * （跨境网络下这笔开销能到几百毫秒 × 请求数）。这里全局单例，并且
         * 连接保活 5 分钟，连续解析/续传都复用同一条连接。
         */
        private val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(9, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build()
        }

        private val sharedNoRedirectClient: OkHttpClient by lazy {
            sharedClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }

        // 访客会话跨实例共享：WebView 通道拿到真浏览器会话后回填到这里，
        // 之后所有 DouyinParser 实例的原生请求都带上这些 cookie。
        @Volatile
        private var cachedTtwid: String? = null

        /**
         * ttwid 会话池（最多 3 个）。风控是按会话标记的，命中后换一个会话重打
         * 比拿同一个 ttwid 死磕有效；WebView 通道回填的会话也进池。
         */
        private val ttwidPool = java.util.Collections.synchronizedList(mutableListOf<String>())

        /** 上一次主动引导失败的时间点，用于冷却。 */
        @Volatile
        private var ttwidFailedAt = 0L

        /** uifid（页面 secsdk JS 铸造的访客 id）—— x-secsdk-web-signature 的绑定对象。 */
        @Volatile
        private var cachedUifid: String? = null

        /** s_v_web_id（即 verifyFp / fp 参数与 cookie）。 */
        @Volatile
        private var cachedSvWebId: String? = null

        /** WebView 会话里的 msToken（没有也能用，伪造值已实测可接受）。 */
        @Volatile
        private var cachedMsToken: String? = null

        /** WebView 真页面拿到 ttwid 后回填（原生解析从此风控概率大降）。 */
        fun injectTtwid(value: String) {
            if (value.isBlank()) return
            cachedTtwid = value
            synchronized(ttwidPool) {
                if (value !in ttwidPool) {
                    if (ttwidPool.size >= 3) ttwidPool.removeAt(0)
                    ttwidPool.add(value)
                }
            }
        }

        /** WebView 访问抖音页面后回填其余访客凭据（uifid 是签名通道的钥匙）。 */
        fun injectVisitorCookies(uifid: String?, svWebId: String?, msToken: String?) {
            uifid?.takeIf { it.isNotBlank() }?.let { cachedUifid = it }
            svWebId?.takeIf { it.isNotBlank() }?.let { cachedSvWebId = it }
            msToken?.takeIf { it.isNotBlank() }?.let { cachedMsToken = it }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 本次解析途中是否见过「被风控打薄的图文数据」。
     *
     * 只影响失败文案：带「图集」二字时 [ParseRepository] 会拒绝降级链给出的
     * 视频结果（那只是图集预览流），避免用户拿到一个点不动的「下载视频」。
     */
    private var sawNote = false

    private val client: OkHttpClient get() = sharedClient

    private val noRedirectClient: OkHttpClient get() = sharedNoRedirectClient

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
    //
    // 2026-11 提速调整：**第一发接口请求不再等会话**。实测 slidesinfo 干净请求
    // （无 cookie）大多数时候就能拿到数据，而引导一个 ttwid 要额外 1~3 个往返
    // （主页 Set-Cookie 拿不到时还得走 union register 两次请求）。所以现在的顺序是：
    //   ① 无会话直打 → ② 命中风控才引导/轮换会话 + 换 UA 重打 → ③ HTML 兜底。
    // 引导失败还会进 90s 冷却，避免每次解析都白跑一遍引导。

    /** 当前会话的 Cookie 头；没有会话返回 null（不发 Cookie 头，也不触发引导）。 */
    private fun cachedTtwidCookie(): String? = cachedTtwid?.let { "ttwid=$it" }

    /**
     * 取一个可用会话：先用缓存，再翻会话池，都没有才现场引导（受冷却限制）。
     * 返回 null 表示这一轮拿不到会话 —— 调用方应当继续裸打，不要卡住。
     */
    private fun acquireTtwid(): String? {
        cachedTtwid?.let { return it }
        val pooled = synchronized(ttwidPool) { ttwidPool.lastOrNull() }
        if (pooled != null) {
            cachedTtwid = pooled
            return pooled
        }
        if (SystemClock.elapsedRealtime() - ttwidFailedAt < TTWID_COOLDOWN_MS) return null
        val fresh = bootstrapTtwidFromHomepage() ?: bootstrapTtwidFromRegister()
        if (fresh == null) {
            ttwidFailedAt = SystemClock.elapsedRealtime()
            Log.d(TAG, "ttwid 引导失败，${TTWID_COOLDOWN_MS / 1000}s 内不再尝试")
            return null
        }
        injectTtwid(fresh)
        return fresh
    }

    /**
     * 当前会话疑似被风控标记：丢掉它并换池里的下一个。
     * 池里没有别的会话时留空，下次 [acquireTtwid] 会重新引导（冷却期内则裸打）。
     */
    private fun rotateTtwid(bad: String?) {
        if (bad == null) return
        if (cachedTtwid == bad) cachedTtwid = null
        synchronized(ttwidPool) { ttwidPool.removeAll { it == bad } }
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
        sawNote = false
        val startedAt = SystemClock.elapsedRealtime()
        var lapAt = startedAt
        fun lap(stage: String) {
            val now = SystemClock.elapsedRealtime()
            Log.d(TAG, "$stage +${now - lapAt}ms / 累计 ${now - startedAt}ms")
            lapAt = now
        }
        try {
            val videoId = resolveVideoId(shareUrl)
                ?: return@withContext Result.failure(Exception("无法从链接中提取抖音作品 ID"))
            lap("解析作品 ID")

            // 优先走 slidesinfo 专用接口（SSR 页面的 videoInfoRes 字段已下线，
            // 图文 note 需要带 request_source=200 才返回数据）。接口被风控时
            // 换会话重打、并发带签名的官方 detail 接口（需要 WebView 铸造的
            // uifid），最后才是旧的 HTML SSR 解析。
            val data = resolveDetail(videoId, startedAt)
                ?: return@withContext Result.failure(
                    Exception(
                        if (sawNote) "抖音图集未取到图片（接口被风控过滤）"
                        else "未找到作品信息"
                    )
                )
            lap("接口取到作品数据")

            // ---- images (图集) + LivePhoto ----
            // 注意用 as? JsonArray：风控打薄时常给 `"images": null`，
            // JsonNull 直接 .jsonArray 会抛异常，把整条解析拖进失败态。
            val album = data["images"] as? JsonArray
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
            // （可能为 null / 空数组），或者干脆丢了 images 键、只剩 aweme_type=68。
            // 这种情况绝不能落到视频分支 —— note 自带的 video 只是图集预览流，
            // 返回 VIDEO 会让用户在结果页以视频方式“预览”，下载也拿不到图。
            if (isNote(data)) {
                return@withContext Result.failure(Exception("抖音图集未取到图片（接口被风控过滤）"))
            }

            // ---- video (视频) ----
            // 全量画质档位（高→低）：UI 让用户选，默认用最高档。
            val qualities = videoQualities(data)
            val playUrl = qualities.firstOrNull()?.url
                ?: fallbackVideoUrl(data)
                ?: return@withContext Result.failure(Exception("未找到视频或图片地址"))
            // 双保险：万一打薄数据漏过 hasMedia，也别把 BGM mp3 当视频给用户
            if (looksLikeAudio(playUrl)) {
                return@withContext Result.failure(Exception("未找到视频或图片地址"))
            }

            // Follow the 302 to the final mp4. 已经是 CDN 直链（bit_rate 档位大多
            // 是 v3-web.douyinvod.com 的 .mp4）时跳过这一趟 —— 每个解析省一次
            // 往返，而下载器本来就会自己跟 302。
            val finalUrl = if (isDirectMediaUrl(playUrl)) playUrl else resolveRedirect(playUrl)
            lap("解析播放地址")

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

    /** 单发 slidesinfo；`rsParam` 决定是否带 `request_source=200`（图集必需）。 */
    private fun slidesinfoOnce(
        videoId: String,
        rsParam: String,
        cookie: String?,
        ua: String,
    ): JsonObject? {
        // 打薄（风控过滤）的 detail 一律不返回：它 images 为空却常保留
        // video.play_addr（图文作品的 BGM mp3），会被任何「有字段就算数」的宽容
        // 判定误收，从而挡住后面的签名 detail 通道
        // （实测 7700942408430718115：slidesinfo 打薄 → 假成功 → 解析失败）。
        val detail = try {
            val body = get(
                "https://www.iesdouyin.com/web/api/v2/aweme/slidesinfo/?aweme_ids=%5B$videoId%5D$rsParam",
                followRedirects = true,
                cookie = cookie,
                ua = ua,
            )
            json.parseToJsonElement(body).jsonObject["aweme_details"]?.jsonArray
                ?.firstOrNull()?.jsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        return detail.takeIf { hasMedia(it) }
    }

    /**
     * 一发完整 slidesinfo：先打无 `request_source`（视频），不中再打图集变体。
     *
     * `request_source=200` 对视频恒定返回空列表，所以只有第一发没拿到媒体才补打，
     * 不会给每次视频解析都多塞一个注定失败的请求。
     */
    private fun slidesinfo(
        videoId: String,
        cookie: String?,
        ua: String = defaultUa,
    ): JsonObject? {
        slidesinfoOnce(videoId, "", cookie, ua)?.let { return it }
        return slidesinfoOnce(videoId, "&request_source=200", cookie, ua)
    }

    /**
     * 取作品 detail 的分层通道，按「便宜 → 贵」排列，任一层出结果即返回。
     *
     * 层 1：无会话直打（0 额外往返，实测大多数命中）
     * 层 2：换会话 + 换 UA 重打，**同时并发**带签名的官方 detail —— 风控命中时
     *       这两条路互相兜底，串行跑等于把最慢的失败路径翻倍
     * 层 3：HTML SSR 兜底
     *
     * `startedAt` 是整次解析的起点，用于给后两层设预算：宁可快点失败交给降级链，
     * 也别让用户对着 loading 干等十几秒。
     */
    private suspend fun resolveDetail(videoId: String, startedAt: Long): JsonObject? {
        // 层 1：不带会话（有缓存会话就顺手带上，反正零成本）
        slidesinfo(videoId, cookie = cachedTtwidCookie())?.let { return it }

        // 层 2：风控命中 —— 会话轮换与签名通道并发竞赛
        if (SystemClock.elapsedRealtime() - startedAt < SESSION_RETRY_BUDGET_MS) {
            val tasks = mutableListOf<suspend () -> JsonObject?>()
            tasks += { slidesinfoWithRotatedSession(videoId) }
            if (cachedUifid != null) tasks += { fetchAwemeDetailSigned(videoId) }
            firstNonNull(tasks)?.let { return it }
        }

        // 层 3：HTML SSR
        if (SystemClock.elapsedRealtime() - startedAt < HTML_BUDGET_MS) {
            return fetchAwemeDetailFromHtml(videoId, startedAt + HTML_HARD_DEADLINE_MS)
        }
        return null
    }

    /**
     * 用会话重打 slidesinfo：池里的会话先试移动 UA，仍被挡就丢掉换下一个会话
     * 再来一轮。最多两轮，避免风控死循环式重试。
     */
    private fun slidesinfoWithRotatedSession(videoId: String): JsonObject? {
        repeat(2) { round ->
            val ttwid = acquireTtwid()
            val cookie = ttwid?.let { "ttwid=$it" }
            // 第一轮用移动 UA（与 ttwid 下发时的 UA 自洽），第二轮换桌面 UA
            val ua = if (round == 0) mobileUa else defaultUa
            slidesinfo(videoId, cookie, ua)?.let { return it }
            rotateTtwid(ttwid)
        }
        return null
    }

    /**
     * 并发跑多个候选通道，返回第一个非空结果（其余取消）；全部落空返回 null。
     * 通道内部抛异常按「没拿到」处理，不影响其它通道。
     */
    private suspend fun <T : Any> firstNonNull(tasks: List<suspend () -> T?>): T? = coroutineScope {
        val winner = CompletableDeferred<T>()
        val jobs = tasks.map { task ->
            launch(Dispatchers.IO) {
                runCatching { task() }.getOrNull()?.let { winner.complete(it) }
            }
        }
        launch {
            jobs.joinAll()
            // 全部跑完仍无结果 → 唤醒等待方（用普通异常，避免把作用域取消掉）
            winner.completeExceptionally(NoSuchElementException("no channel returned data"))
        }
        try {
            winner.await()
        } catch (_: Exception) {
            null
        } finally {
            jobs.forEach { it.cancel() }
        }
    }

    private fun hasMedia(detail: JsonObject): Boolean {
        val album = detail["images"] as? JsonArray
        if (album != null && album.any { pickImageUrl(it.jsonObject) != null }) return true
        // 图文作品（带 images 键 / image_post_info，或打薄后只剩 aweme_type）：
        // 没有可用图片就没有媒体。它常还留着 video.play_addr —— 那是 BGM 音频
        // 或图集预览流，按视频收下只会产出「下载视频」必失败的假结果。
        if (isNote(detail)) {
            sawNote = true
            return false
        }
        // 有画质档位就是真视频。
        val gears = videoQualities(detail)
        if (gears.any { !looksLikeAudio(it.url) }) return true
        // 没有档位：可能是接口把 bit_rate 挪走/删了（slidesinfo 和网页 detail 的
        // 字段随版本变），也可能是被打薄的图集预览流。图集特征上面已经拦过，
        // 这里只要求它有一条不像音频的播放地址。
        // 注意别再收紧回「没有档位一律拒」——那样接口一改字段，整类视频都会
        // 被判成「未找到作品信息」，表现是抖音解析整个失效。
        val fallback = fallbackVideoUrl(detail) ?: return false
        return !looksLikeAudio(fallback)
    }

    /**
     * 图文（note）作品判定。
     *
     * 打薄后的 detail 常常只剩作品类型，所以类型必须认全：
     * `2` 普通图集、`68` 单图/图集+配乐、`150` 图文——实测这条链接
     * （7700942408430718115）的分享页数据就是 `aweme_type=2`，只认 68 会把
     * 它当视频解析，拿图集预览流产出「下载视频」必失败的假结果。
     */
    private fun isNote(detail: JsonObject): Boolean {
        // 数组存在就算图集（空数组也说明接口承认这是图文，只是被风控滤掉了
        // 图片）；但 `"images": null` 不算 —— 打薄的普通视频也会带这个空键，
        // 认了它就会把所有视频都判成图集。
        val images = detail["images"] as? JsonArray
        if (images != null) return true
        // image_post_info 同理：只有真的带内容才算
        val postInfo = detail["image_post_info"] as? JsonObject
        if (postInfo != null && postInfo.isNotEmpty()) return true
        return (detail["aweme_type"] as? JsonPrimitive)?.contentOrNull in NOTE_TYPES
    }

    /**
     * BGM 音频地址识别：图文（note）作品的 `video.play_addr` 指向配乐而非
     * 视频流，特征是 ies-music 目录或 .mp3 / .m4a 后缀（可能出现在 path，
     * 也可能出现在 query 的 video_id 参数里）。
     */
    private fun looksLikeAudio(url: String): Boolean {
        val lowered = url.lowercase()
        if ("ies-music" in lowered) return true
        val path = lowered.substringBefore('?').substringBefore('#')
        return path.endsWith(".mp3") || path.endsWith(".m4a")
    }

    // region: 带签名的官方 detail 接口
    //
    // /aweme/v1/web/aweme/detail/ 是抖音的签名保护端点，需要三件套：
    //   1. a_bogus —— bdms SDK 逆向出的纯算签名（DouyinWebSigner.ABogus）
    //   2. uifid —— 真浏览器页面 JS 铸造的访客 id（WebView 通道收割），
    //      用于 x-secsdk-web-signature = md5(uifid_时间戳_盐_query)
    //   3. ttwid cookie
    // 参考实现 2026-09-08/09 实测 24/24 通过；缺 uifid 时该端点一律 403
    // "Uifid Not Found"，所以没有 uifid 时直接跳过本通道不浪费请求。

    private fun fetchAwemeDetailSigned(videoId: String): JsonObject? {
        val uifid = cachedUifid ?: return null
        return try {
            val msToken = cachedMsToken ?: fakeMsToken()
            val params = baseDetailParams() + listOf("aweme_id" to videoId, "msToken" to msToken)

            // a_bogus 的前像 = form 编码的业务参数（不含 a_bogus 本身），
            // 附加时整个值要 percent-encode（s4 字母表含 / - =）。
            val query = DouyinWebSigner.pythonUrlEncode(params)
            val bogus = DouyinWebSigner.ABogus(defaultUa).getValue(query)
            val withBogus = params + listOf("a_bogus" to bogus)

            // x-secsdk 对「重新序列化后的完整 query」签名（verifyFp/fp 来自
            // s_v_web_id cookie，浏览器页面也带这两个空参与非空参两种形态）。
            val svWebId = cachedSvWebId
            val visitorPairs = withBogus +
                (svWebId?.let { listOf("verifyFp" to it, "fp" to it) } ?: emptyList())
            val (signedQuery, signHeaders) = DouyinWebSigner.webSign(visitorPairs, uifid)

            val cookie = buildList {
                cachedTtwid?.let { add("ttwid=$it") }
                add("uifid=$uifid")
                svWebId?.let { add("s_v_web_id=$it") }
                add("msToken=$msToken")
            }.joinToString("; ")

            val request = Request.Builder()
                .url("https://www.douyin.com/aweme/v1/web/aweme/detail/?$signedQuery")
                .header("User-Agent", defaultUa)
                .header("Referer", "https://www.douyin.com/")
                .header("Cookie", cookie)
                .header("uifid", signHeaders.getValue("uifid"))
                .header("x-secsdk-web-signature", signHeaders.getValue("x-secsdk-web-signature"))
                .header("x-secsdk-web-expire", signHeaders.getValue("x-secsdk-web-expire"))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val detail = json.parseToJsonElement(body).jsonObject["aweme_detail"]?.jsonObject
                    ?: return null
                detail.takeIf { hasMedia(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 与 defaultUa（Chrome/128 桌面）自洽的浏览器画像参数。 */
    private fun baseDetailParams(): List<Pair<String, String>> = listOf(
        "device_platform" to "webapp",
        "aid" to "6383",
        "channel" to "channel_pc_web",
        "pc_client_type" to "1",
        "version_code" to "290100",
        "version_name" to "29.1.0",
        "cookie_enabled" to "true",
        "screen_width" to "1920",
        "screen_height" to "1080",
        "browser_language" to "zh-CN",
        "browser_platform" to "Win32",
        "browser_name" to "Chrome",
        "browser_version" to "128.0.0.0",
        "browser_online" to "true",
        "engine_name" to "Blink",
        "engine_version" to "128.0.0.0",
        "os_name" to "Windows",
        "os_version" to "10",
        "cpu_core_num" to "8",
        "device_memory" to "8",
        "platform" to "PC",
        "downlink" to "10",
        "effective_type" to "4g",
        "round_trip_time" to "0",
        "update_version_code" to "170400",
    )

    /** 本地伪造 msToken（抖音对 detail 端点已实测不校验其真实性）。 */
    private fun fakeMsToken(): String =
        buildString {
            val alphabet = ('a'..'z') + ('A'..'Z') + ('0'..'9')
            repeat(116) { append(alphabet.random()) }
            append("==")
        }
    // endregion

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
     *
     * 2026-11 提速：原来每条路径都要跑「不跟随 / 跟随重定向」两遍，但不跟随那次
     * 拿到 302 时 body 是空的、判 !isSuccessful 直接丢——纯属白发请求。现在只跑
     * 跟随重定向的一遍，落空时换会话再试一轮（受 deadline 约束）。
     */
    private fun fetchAwemeDetailFromHtml(videoId: String, deadlineAt: Long): JsonObject? {
        return try {
            var round = 0
            while (round < 2 && SystemClock.elapsedRealtime() < deadlineAt) {
                val ttwid = acquireTtwid()
                val cookie = ttwid?.let { "ttwid=$it" }
                // 图文作品的分享路径是 /share/note/，必须先试 —— /share/video/{noteId}
                // 会返回图集的预览视频变体，导致图集被当成视频解析。
                for (path in listOf("share/note", "share/video")) {
                    val pageUrl = "https://www.iesdouyin.com/$path/$videoId/"
                    val html = fetchShareHtml(pageUrl, cookie) ?: continue
                    parseShareHtmlRouterData(html)?.let { return it }
                }
                // 全部落空 → 会话可能已被风控标记，换一个再试一轮
                if (ttwid == null) break
                rotateTtwid(ttwid)
                round++
            }
            null
        } catch (_: Exception) {
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

    /** 分享页 HTML 抓取（客户端自己跟 302），失败返回 null。 */
    private fun fetchShareHtml(pageUrl: String, cookie: String?): String? {
        return try {
            val builder = Request.Builder().url(pageUrl)
                .header("User-Agent", mobileUa)
                .header("Referer", "https://www.douyin.com/")
            cookie?.let { builder.header("Cookie", it) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        } catch (_: Exception) {
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
     * Best still-image URL for an album entry.
     *
     * 两个字段的语义随接口版本而变：web detail 的 `download_url_list` 全部是
     * 带水印模板（`~tplv-dy-water-*`）的下载版，`url_list` 才是无水印渲染图；
     * 老移动端接口恰好相反。所以不按字段名挑，而是把所有候选摊开，按
     * 「URL 不含水印模板」优先排序后再挑非 webp。
     */
    private fun pickImageUrl(img: JsonObject): String? {
        val urls = listOfNotNull(
            img["url_list"],
            img["download_url_list"],
            img["download_url"]?.jsonObject?.get("url_list"),
            img["url"]?.jsonObject?.get("url_list"),
        ).asSequence()
            .filterIsInstance<JsonArray>()
            .flatMap { arr -> arr.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
        val clean = urls.filter { !looksWatermarked(it) }
        return preferNonWebpList(clean.ifEmpty { urls })
    }

    /**
     * 水印模板识别：只匹配 tplv 模板名里的 water（如 `~tplv-dy-water-v2:`），
     * 不裸查 "water" —— 避免误伤文件名本身含水（waterfall 等）的图。
     */
    private fun looksWatermarked(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return Regex("""tplv[a-zA-Z0-9:_-]*water""", RegexOption.IGNORE_CASE).containsMatchIn(path)
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
    private fun preferNonWebp(urlList: JsonArray): String? =
        preferNonWebpList(urlList.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it.isNotEmpty() })

    private fun preferNonWebpList(urls: List<String>): String? =
        urls.firstOrNull { !isWebp(it) } ?: urls.firstOrNull()

    /**
     * GET helper.
     *
     * `cookie` 由调用方显式给出（null = 不发 Cookie 头），这样「无会话直打」
     * 这条快路子不会被 ttwid 引导拖住。
     */
    private fun get(
        url: String,
        followRedirects: Boolean,
        cookie: String? = cachedTtwidCookie(),
        ua: String = defaultUa,
    ): String {
        val builder = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Referer", "https://www.douyin.com/")
        cookie?.let { builder.header("Cookie", it) }
        val http = if (followRedirects) client else noRedirectClient
        http.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("空响应")
        }
    }

    /**
     * 已经是 CDN 直链时不必再探一次 302。
     *
     * bit_rate 档位给的地址基本都是 CDN 上的真实媒体文件（实测
     * `v11-default.365yg.com/.../video/tos/...?mime_type=video_mp4`，直接 200，
     * 没有跳转；`v3-web.douyinvod.com` 同理）。只有 `/aweme/v1/play` 这类
     * 播放网关才需要跟一次重定向。下载器本来就跟随 302，所以这里省下的是
     * 每个视频解析一次完整往返（还要多建一条到 CDN 的连接）。
     */
    private fun isDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        if ("mime_type=video_mp4" in lower) return true
        val host = try {
            url.toHttpUrl().host.lowercase()
        } catch (_: Exception) {
            return false
        }
        if (host.contains("douyinvod.com") || host.contains("bytevcdn") ||
            host.contains("volccdn") || host.contains("bytecdn") ||
            host.contains("365yg.com")
        ) {
            return true
        }
        return url.substringBefore('?').substringBefore('#').endsWith(".mp4", ignoreCase = true)
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
