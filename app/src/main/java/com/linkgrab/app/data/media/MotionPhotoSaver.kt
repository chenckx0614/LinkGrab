package com.linkgrab.app.data.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/** Encodes off the main thread; publishes only a complete file, cleans all intermediate files. */
internal class MotionPhotoSaver(
    private val client: OkHttpClient,
    private val requestFor: (String) -> Request,
) {
    suspend fun save(
        context: Context,
        imageUrl: String,
        videoUrls: List<String>,
        filename: String,
        relativePath: String,
        checkpoint: () -> Unit,
        onProgress: suspend (Int) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()
        val check = { job.ensureActive(); checkpoint() }
        val dir = File(context.cacheDir, "motion-photo").apply { mkdirs() }
        val source = File.createTempFile("image-", ".source", dir)
        val jpeg = File.createTempFile("cover-", ".jpg", dir)
        val video = File.createTempFile("video-", ".mp4", dir)
        var pending: Uri? = null
        try {
            download(imageUrl, source, 40L * 1024 * 1024, check) { p -> onProgress(p / 5) }
            check()
            // Decode orientation and normalize PNG/WebP/HEIC into a clean JPEG.
            // Bound peak memory to 16 MP, and avoid inheriting incompatible XMP/gain maps.
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                val pixels = info.size.width.toLong() * info.size.height
                if (pixels > 16_000_000) {
                    val ratio = sqrt(16_000_000.0 / pixels)
                    decoder.setTargetSize((info.size.width * ratio).toInt().coerceAtLeast(1),
                        (info.size.height * ratio).toInt().coerceAtLeast(1))
                }
            }
            try {
                jpeg.outputStream().buffered().use {
                    require(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) { "封面转换失败" }
                }
            } finally { bitmap.recycle() }
            check()
            onProgress(25)
            var lastError: Exception? = null
            var found = false
            for (url in videoUrls.distinct()) {
                check()
                try {
                    download(url, video, 128L * 1024 * 1024, check) { p -> onProgress(25 + p * 60 / 100) }
                    validateVideo(video)
                    found = true
                    break
                } catch (e: IOException) {
                    lastError = e
                }
            }
            if (!found) throw IOException("实况视频不可用：${lastError?.message ?: "没有视频地址"}")
            check()
            onProgress(90)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建实况照片")
            pending = uri
            resolver.openOutputStream(uri)?.buffered()?.use { output ->
                jpeg.inputStream().buffered().use { cover ->
                    video.inputStream().buffered().use { clip ->
                        MotionPhotoWriter.write(cover, clip, video.length(), output, check)
                    }
                }
            } ?: throw IOException("无法写入实况照片")
            check()
            val updated = resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            if (updated != 1) throw IOException("相册写入未完成")
            pending = null
            uri
        } finally {
            pending?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            source.delete()
            jpeg.delete()
            video.delete()
        }
    }

    private suspend fun download(url: String, file: File, limit: Long, checkpoint: () -> Unit,
                                 progress: suspend (Int) -> Unit) {
        val call = client.newCall(requestFor(url))
        call.timeout().timeout(60, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("空响应")
            val mime = body.contentType()?.toString().orEmpty()
            if (mime.startsWith("text/") || mime.contains("json")) throw IOException("返回的不是媒体文件")
            val total = body.contentLength()
            if (total > limit) throw IOException("实况素材过大，请改用分开保存")
            var written = 0L
            var lastUpdate = 0L
            body.byteStream().use { input ->
                file.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkpoint()
                        val count = input.read(buffer)
                        if (count < 0) break
                        written += count
                        if (written > limit) throw IOException("实况素材过大，请改用分开保存")
                        out.write(buffer, 0, count)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate > 150) {
                            lastUpdate = now
                            progress(if (total > 0) (written * 100 / total).toInt().coerceIn(0, 99) else 0)
                        }
                    }
                }
            }
            if (written == 0L || (total > 0 && written != total)) throw IOException("媒体下载不完整")
        }
    }

    private fun validateVideo(file: File) {
        val extractor = MediaExtractor()
        try {
            file.inputStream().use { input ->
                val header = ByteArray(12)
                if (input.read(header) != 12 || String(header, 4, 4, Charsets.US_ASCII) != "ftyp" ||
                    String(header, 8, 4, Charsets.US_ASCII) == "qt  ") {
                    throw IOException("视频不是 MP4，请改用分开保存")
                }
            }
            extractor.setDataSource(file.absolutePath)
            val formats = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
            if (formats.none { it.getString(MediaFormat.KEY_MIME) in setOf("video/avc", "video/hevc", "video/av01") }) {
                throw IOException("视频编码不支持实况封装，请改用分开保存")
            }
        } catch (e: IOException) { throw e }
        catch (e: Exception) { throw IOException("无法读取实况视频", e) }
        finally { extractor.release() }
    }
}
