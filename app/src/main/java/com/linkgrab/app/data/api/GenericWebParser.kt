package com.linkgrab.app.data.api

import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 通用网页直链抓取（没有专门适配的站点兜底）。
 *
 * 不跑 JS，只做静态 HTML 里的结构化信息提取，按可信度排序：
 *
 *  1. `og:video` / `og:video:url` / `twitter:player:stream` —— 绝大多数
 *     带视频的页面（新闻站、政府站、B 站之外的自建站）都会给
 *  2. `<video src>` / `<source src>` —— 原生 <video> 标签
 *  3. JSON-LD 里的 `"contentUrl"` —— 结构化数据
 *  4. `og:image` / `twitter:image` —— 单图
 *  5. 正文里体积最大的 `<img>`（按 width 属性、或文件名里的尺寸线索挑）
 *
 * 拿到什么就返回什么：有视频按视频返回（视频优先，因为视频页面通常也带封面），
 * 否则返回图片列表（最多 12 张）。什么都没有就明确报「这个页面里没找到可保存的媒体」。
 */
class GenericWebParser {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val desktopUa =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /** 任何 http(s) 页面都能试一把。 */
    fun canParse(text: String): Boolean =
        Regex("""^https?://""", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())

    suspend fun parse(url: String): Result<MediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val html = fetch(url)
            val base = url.substringBefore('#')
            val title = extractMeta(html, "og:title")
                ?: extractTagText(html, "title")
                ?: url.substringAfterLast('/').ifBlank { "网页内容" }

            val video = firstNonBlank(
                extractMeta(html, "og:video:secure_url"),
                extractMeta(html, "og:video:url"),
                extractMeta(html, "og:video"),
                extractMeta(html, "twitter:player:stream"),
                extractVideoTag(html),
                extractJsonLd(html, "contentUrl"),
            )?.let { absolutize(it, base) }

            val cover = firstNonBlank(
                extractMeta(html, "og:image:secure_url"),
                extractMeta(html, "og:image"),
                extractMeta(html, "twitter:image"),
            )?.let { absolutize(it, base) }

            if (video != null) {
                return@runCatching MediaResult(
                    type = MediaType.VIDEO,
                    title = title.take(90),
                    videoUrl = video,
                    coverUrl = cover,
                    source = "web",
                )
            }

            val images = collectImages(html, base)
            if (images.isEmpty()) {
                error("这个页面里没找到可保存的图片或视频（可能需要登录 / 由 JS 动态加载）")
            }
            MediaResult(
                type = MediaType.IMAGE,
                title = title.take(90),
                images = images,
                coverUrl = cover ?: images.first(),
                source = "web",
            )
        }
    }

    // ==================== 抓取 ====================

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", desktopUa)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("页面打不开：HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("页面没有内容")
            val type = body.contentType()?.toString().orEmpty()
            if (!type.contains("html")) throw IllegalStateException("这个地址不是网页（${type.ifBlank { "未知类型" }}）")
            return body.string()
        }
    }

    // ==================== 提取 ====================

    /** `<meta property="og:video" content="...">`，property/name 都试。 */
    private fun extractMeta(html: String, key: String): String? {
        val pattern = Regex(
            """<meta\s+[^>]*(?:property|name)\s*=\s*["']${Regex.escape(key)}["'][^>]*>""",
            RegexOption.IGNORE_CASE,
        )
        val tag = pattern.find(html)?.value ?: return null
        return Regex("""content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(tag)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotBlank() && !it.startsWith("data:") }
    }

    private fun extractTagText(html: String, tag: String): String? =
        Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.let { stripTags(it) }
            ?.trim()?.takeIf { it.isNotBlank() }

    private fun extractVideoTag(html: String): String? {
        Regex("""<video[^>]*\ssrc\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let { return it }
        return Regex("""<source[^>]*\ssrc\s*=\s*["']([^"']+\.(?:mp4|m3u8|webm)[^"']*)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
    }

    private fun extractJsonLd(html: String, field: String): String? =
        Regex("""["']$field["']\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)
            ?.takeIf { it.startsWith("http") }

    /**
     * 收集候选图片：先拿 og/large 的，再按 `<img>` 的 src/data-src 补齐，
     * 过滤掉头像/图标/透明占位（文件名里带 avatar/icon/logo/sprite 等），
     * 最后按 URL 去重并保留前 12 张。
     */
    private fun collectImages(html: String, base: String): List<String> {
        val candidates = mutableListOf<String>()

        Regex("""<img[^>]+>""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val tag = match.value
            val src = Regex("""(?:data-src|data-original|src)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)
            if (src.isNullOrBlank()) return@forEach
            if (src.startsWith("data:")) return@forEach
            val lower = src.lowercase()
            if (BAD_IMAGE_HINTS.any { it in lower }) return@forEach
            // 优先取带尺寸信息的原图属性
            val big = Regex("""data-src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)
            candidates.add(absolutize(big ?: src, base))
        }

        return candidates
            .filter { it.startsWith("http") }
            .distinct()
            .take(12)
    }

    private fun absolutize(url: String, base: String): String {
        val clean = url.trim()
        return when {
            clean.startsWith("//") -> "https:$clean"
            clean.startsWith("http") -> clean
            clean.startsWith("/") -> {
                val origin = Regex("""^https?://[^/]+""").find(base)?.value.orEmpty()
                origin + clean
            }
            else -> base.substringBeforeLast('/') + "/" + clean
        }
    }

    private fun stripTags(text: String): String =
        text.replace(Regex("<[^>]+>"), "").replace("&amp;", "&").replace("&nbsp;", " ").trim()

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }

    private companion object {
        /** 文件名里带这些词的基本都是装饰图，不是正文媒体。 */
        val BAD_IMAGE_HINTS = listOf(
            "avatar", "icon", "logo", "sprite", "blank", "placeholder", "loading",
            "favicon", "spacer", "1x1", "pixel", "qrcode", "qr_code",
        )
    }
}
