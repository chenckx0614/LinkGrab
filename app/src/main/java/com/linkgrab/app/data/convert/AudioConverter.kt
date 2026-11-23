package com.linkgrab.app.data.convert

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 选中视频的基本信息，给界面上那张信息卡用。 */
data class SourceInfo(
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val audioMime: String?,
)

/**
 * 视频转音频。
 *
 * 全程用系统平台能力，不依赖 ffmpeg：
 * - M4A：MediaExtractor 直接读 content:// + MediaMuxer 原样搬 AAC 音轨 —— 不解码不重编码，
 *   一小时的视频也是秒级完成，音质和原片完全一致；
 * - WAV：MediaCodec 解码成 PCM，自己写 RIFF 头 —— 任何系统能解的音频都能转。
 *
 * 相比旧实现（先把整个视频拷进 cache 再交给 ffmpeg）少了整片拷贝，进度、取消、
 * 清理都能做对。
 */
object AudioConverter {

    private const val CODEC_TIMEOUT_US = 10_000L
    private const val COPY_BUFFER = 128 * 1024

    suspend fun inspect(context: Context, source: Uri): SourceInfo = withContext(Dispatchers.IO) {
        var name: String? = null
        var size = 0L
        runCatching {
            context.contentResolver.query(
                source,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0)
                    size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
                }
            }
        }

        var durationMs = 0L
        var audioMime: String? = null
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, source, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioMime = mime
                    durationMs = format.longOr(MediaFormat.KEY_DURATION, 0L) / 1000
                    break
                }
            }
        } catch (_: Exception) {
            // 读不出来就让界面显示"未知"，真正转换时再报具体原因
        } finally {
            runCatching { extractor.release() }
        }
        SourceInfo(name ?: "视频文件", size, durationMs, audioMime)
    }

    suspend fun convert(
        context: Context,
        source: Uri,
        target: AudioTarget,
        tree: String?,
        onProgress: (fraction: Float, label: String) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "convert").apply { mkdirs() }
        val temp = File(tempDir, "audio_${System.currentTimeMillis()}.${target.ext}")
        try {
            when (target) {
                AudioTarget.M4A -> remuxAac(context, source, temp, onProgress)
                AudioTarget.WAV -> decodeToWav(context, source, temp, onProgress)
            }

            onProgress(0.97f, "正在保存")
            val name = OutputTargets.targetName(context, source, target.ext)
            val dest = OutputTargets.createAudio(context, name, target.mime, tree)
                ?: throw ConvertException("无法在目标位置创建文件")
            val failure = runCatching {
                context.contentResolver.openOutputStream(dest)?.use { out -> copy(temp, out) }
                    ?: throw ConvertException("无法写入文件")
            }.exceptionOrNull()
            if (failure != null) {
                OutputTargets.discard(context, dest)
                if (failure is CancellationException) throw failure
                throw failure as? ConvertException
                    ?: ConvertException(failure.message ?: "写入失败")
            }
            OutputTargets.publish(context, dest)
            onProgress(1f, "已保存")
            dest
        } finally {
            temp.delete()
        }
    }

    private suspend fun copy(from: File, out: OutputStream) {
        from.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read <= 0) break
                out.write(buffer, 0, read)
            }
        }
    }

    /** AAC 轨原样搬进 MP4 容器——不解码，所以又快又无损。 */
    private suspend fun remuxAac(
        context: Context,
        source: Uri,
        out: File,
        onProgress: (Float, String) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, source, null)
            var trackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME) == AAC_MIME) {
                    trackIndex = i
                    audioFormat = format
                    break
                }
            }
            val format = audioFormat
                ?: throw ConvertException("这个视频的音轨不是 AAC，转不了 M4A，换成 WAV 试试")
            val durationUs = format.longOr(MediaFormat.KEY_DURATION, 0L)
            extractor.selectTrack(trackIndex)

            val target = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = target
            val track = target.addTrack(format)
            target.start()

            val buffer = ByteBuffer.allocateDirect(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                currentCoroutineContext().ensureActive()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                buffer.position(0)
                buffer.limit(size)
                val timeUs = extractor.sampleTime
                info.set(0, size, timeUs, extractor.sampleFlags)
                target.writeSampleData(track, buffer, info)
                extractor.advance()
                if (durationUs > 0 && timeUs > 0) {
                    onProgress((timeUs.toFloat() / durationUs).coerceIn(0f, 0.95f), "正在取出音轨")
                }
            }
            target.stop()
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /** 解码成 16 位 PCM 写成 WAV。头部的长度字段要等写完才知道，所以先占位再回填。 */
    private suspend fun decodeToWav(
        context: Context,
        source: Uri,
        out: File,
        onProgress: (Float, String) -> Unit,
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source, null)
            var trackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    audioFormat = format
                    // AAC 之外（opus / vorbis / mp3）也能解，但优先挑第一个音轨即可
                    if (mime == AAC_MIME) break
                }
            }
            val format = audioFormat ?: throw ConvertException("这个视频没有音轨")
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val durationUs = format.longOr(MediaFormat.KEY_DURATION, 0L)
            extractor.selectTrack(trackIndex)

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.intOr(MediaFormat.KEY_SAMPLE_RATE, 44_100)
            var channels = format.intOr(MediaFormat.KEY_CHANNEL_COUNT, 2)
            var dataBytes = 0L

            val info = MediaCodec.BufferInfo()
            FileOutputStream(out).use { pcm ->
                pcm.write(ByteArray(44))
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    currentCoroutineContext().ensureActive()

                    if (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inIndex >= 0) {
                            val input = decoder.getInputBuffer(inIndex)
                            val size = if (input != null) extractor.readSampleData(input, 0) else -1
                            if (size < 0) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outIndex = decoder.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val output = decoder.outputFormat
                            sampleRate = output.intOr(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                            channels = output.intOr(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        }
                        else -> if (outIndex >= 0) {
                            if (info.size > 0) {
                                decoder.getOutputBuffer(outIndex)?.let { buffer ->
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    val chunk = ByteArray(info.size)
                                    buffer.get(chunk)
                                    pcm.write(chunk)
                                    dataBytes += chunk.size
                                }
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            if (durationUs > 0) {
                                onProgress(
                                    (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.95f),
                                    "正在解码音轨",
                                )
                            }
                        }
                    }
                }
            }

            RandomAccessFile(out, "rw").use { raf ->
                raf.seek(0)
                raf.write(wavHeader(dataBytes, sampleRate, channels))
            }
        } finally {
            codec?.let { c ->
                runCatching { c.stop() }
                runCatching { c.release() }
            }
            runCatching { extractor.release() }
        }
    }

    private fun wavHeader(dataBytes: Long, sampleRate: Int, channels: Int): ByteArray {
        val bytesPerFrame = channels * 2
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt((36 + dataBytes).toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)                                   // fmt 块长度
            putShort(1)                                  // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * bytesPerFrame)           // 每秒字节数
            putShort(bytesPerFrame.toShort())            // 每帧字节数
            putShort(16)                                 // 位深
            put("data".toByteArray())
            putInt(dataBytes.toInt())
        }.array()
    }

    private fun MediaFormat.longOr(key: String, default: Long): Long =
        if (containsKey(key)) runCatching { getLong(key) }.getOrDefault(default) else default

    private fun MediaFormat.intOr(key: String, default: Int): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(default) else default
}
