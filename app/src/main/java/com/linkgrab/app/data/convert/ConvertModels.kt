package com.linkgrab.app.data.convert

/**
 * 工具箱里两个转换工具的目标格式。
 *
 * 只放「一定能转成功」的格式：ffmpeg-kit 已经从 Maven 下架、本地只剩去掉外部编码器的 min 包
 * （没有 libmp3lame / libvorbis），所以 MP3、OGG 这类必须编解码器支持的格式全部去掉，
 * 只用系统平台自带的能力 + 手写容器。
 */
enum class ImageTarget(val label: String, val ext: String, val mime: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WebP", "webp", "image/webp"),
    BMP("BMP", "bmp", "image/bmp");

    /** JPEG / BMP 没有透明通道，落盘前要铺一层白底，否则透明区域会变黑。 */
    val needsOpaque: Boolean get() = this == JPG || this == BMP
}

enum class AudioTarget(val label: String, val ext: String, val mime: String, val hint: String) {
    M4A("M4A", "m4a", "audio/mp4", "直接取出原始音轨，不重新编码，最快也最保真"),
    WAV("WAV", "wav", "audio/wav", "未压缩的 PCM，体积大，剪辑软件都认"),
}

/** M4A 直搬音轨只认 AAC；别的编码（opus / vorbis）得走 WAV 解码。 */
const val AAC_MIME = "audio/mp4a-latm"

/** 转换过程中能说清原因的错误；UI 直接把 message 显示给用户。 */
class ConvertException(message: String) : Exception(message)

/** 转换完成的一件成品，UI 拿它拼「打开 / 分享」那一行。 */
data class ConvertedFile(
    val name: String,
    val uri: String,
    val detail: String? = null,
)
