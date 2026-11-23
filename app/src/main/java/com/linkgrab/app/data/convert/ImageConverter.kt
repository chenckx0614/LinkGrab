package com.linkgrab.app.data.convert

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * 图片转格式。
 *
 * 三个必须做对的地方（旧的实现三样都缺）：
 * 1. 解码前先读尺寸算采样率 —— 一张 48MP 原图直接解是 192MB 位图，必崩；
 * 2. 读 EXIF 方向 —— 手机竖拍的图在像素里躺着，不转过来导出的就是横的；
 * 3. JPG / BMP 先铺白底 —— 否则透明 PNG 转出来是一片黑。
 */
object ImageConverter {

    /** 长边上限。再大对手机没有意义，却会让解码+旋转吃到两三百兆内存。 */
    private const val MAX_LONG_SIDE = 4096

    suspend fun convert(
        context: Context,
        source: Uri,
        target: ImageTarget,
        tree: String?,
    ): Uri = withContext(Dispatchers.IO) {
        val decoded = decode(context, source)
        val bitmap = try {
            orient(decoded, readOrientation(context, source), target.needsOpaque)
        } catch (e: Throwable) {
            decoded.recycle()
            throw e
        }
        try {
            val name = OutputTargets.targetName(context, source, target.ext)
            val dest = OutputTargets.createImage(context, name, target.mime, tree)
                ?: throw ConvertException("无法在目标位置创建文件")
            val failure = runCatching {
                context.contentResolver.openOutputStream(dest)?.use { out -> encode(bitmap, target, out) }
                    ?: throw ConvertException("无法写入文件")
            }.exceptionOrNull()
            if (failure != null) {
                OutputTargets.discard(context, dest)
                // 取消不是错误：先删半成品，再把取消原样抛出去，别包成 ConvertException
                if (failure is CancellationException) throw failure
                throw failure as? ConvertException
                    ?: ConvertException(failure.message ?: "写入失败")
            }
            OutputTargets.publish(context, dest)
            dest
        } finally {
            bitmap.recycle()
        }
    }

    private fun decode(context: Context, source: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context, source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ConvertException("这张图读不出来")

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return open(context, source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw ConvertException("这张图读不出来")
    }

    private fun open(context: Context, source: Uri) =
        runCatching { context.contentResolver.openInputStream(source) }.getOrNull()

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > MAX_LONG_SIDE) sample *= 2
        return sample
    }

    private fun readOrientation(context: Context, source: Uri): Int =
        runCatching {
            open(context, source)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0) } ?: 0
        }.getOrDefault(0)

    /**
     * 按 EXIF 方向转正；需要不透明时顺手把白底铺上。什么都不用做就原样返回，
     * 少一次整图拷贝就是少一次 OOM 机会。
     */
    private fun orient(source: Bitmap, orientation: Int, flatten: Boolean): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        val identity = matrix.isIdentity
        if (identity && !(flatten && source.hasAlpha())) return source

        val bounds = RectF(0f, 0f, source.width.toFloat(), source.height.toFloat())
        matrix.mapRect(bounds)
        val width = bounds.width().roundToInt().coerceAtLeast(1)
        val height = bounds.height().roundToInt().coerceAtLeast(1)

        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (flatten) canvas.drawColor(Color.WHITE)
        matrix.postTranslate(-bounds.left, -bounds.top)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        if (out !== source) source.recycle()
        return out
    }

    private fun encode(bitmap: Bitmap, target: ImageTarget, out: OutputStream) {
        val ok = when (target) {
            ImageTarget.JPG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            ImageTarget.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            ImageTarget.WEBP -> bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, out)
            ImageTarget.BMP -> {
                writeBmp(bitmap, out)
                true
            }
        }
        if (!ok) throw ConvertException("编码失败")
    }

    /**
     * 手写 24 位 BMP（Bitmap.CompressFormat 里没有 BMP，旧代码拿 JPEG 冒充过）。
     * 54 字节文件头 + 自下而上的 BGR 行，每行补齐到 4 字节。
     */
    private fun writeBmp(bitmap: Bitmap, out: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val rowSize = (24 * width + 31) / 32 * 4
        val pixelBytes = rowSize * height

        val header = ByteBuffer.allocate(54).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte())
            put('M'.code.toByte())
            putInt(54 + pixelBytes)   // 文件总大小
            putShort(0)               // 保留
            putShort(0)               // 保留
            putInt(54)                // 像素数据偏移
            putInt(40)                // BITMAPINFOHEADER
            putInt(width)
            putInt(height)
            putShort(1)               // planes
            putShort(24)              // 位深
            putInt(0)                 // 不压缩
            putInt(pixelBytes)
            putInt(2835)              // 72 DPI
            putInt(2835)
            putInt(0)
            putInt(0)
        }
        out.write(header.array())

        val row = ByteArray(rowSize)
        val pixels = IntArray(width)
        for (y in height - 1 downTo 0) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            var i = 0
            for (x in 0 until width) {
                val color = pixels[x]
                row[i++] = (color and 0xFF).toByte()          // B
                row[i++] = (color shr 8 and 0xFF).toByte()    // G
                row[i++] = (color shr 16 and 0xFF).toByte()   // R
            }
            out.write(row)
        }
        out.flush()
    }
}
