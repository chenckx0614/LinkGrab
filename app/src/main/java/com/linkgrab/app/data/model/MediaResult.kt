package com.linkgrab.app.data.model

import kotlinx.serialization.Serializable

/**
 * 一个可选的视频画质档位（抖音 `video.bit_rate[]` 里的一档）。
 */
@Serializable
data class VideoQuality(
    /** 展示用标签，如 "1080P"、"720P"。 */
    val label: String,
    /** 该档位的直链。 */
    val url: String,
    /** 码率（bps），用于排序与展示。 */
    val bitrate: Long = 0,
)

@Serializable
data class MediaResult(
    val type: MediaType,
    val title: String = "",
    val images: List<String> = emptyList(),
    val videoUrl: String? = null,
    val coverUrl: String? = null,
    val author: String = "",
    val source: String = "",
    /**
     * Live Photo (实况照片) video streams, keyed by the index of the
     * corresponding entry in [images].
     *
     * 值是一个**候选列表**而不是单个地址：小红书/抖音 CDN 的首选地址可能
     * 403 或带时效签名过期，下载时按顺序回退，避免「实况保存报错」。
     * 出现该下标的图片即为实况照片（一帧静态图 + 一段可播放视频）。
     */
    val livePhotos: Map<Int, List<String>> = emptyMap(),
    /**
     * 可选画质档位，按画质从高到低排列。目前仅抖音提供；
     * 为空表示该来源只给了一个地址，不展示选择 UI。
     */
    val videoQualities: List<VideoQuality> = emptyList(),
) {
    fun isLivePhoto(index: Int): Boolean = !livePhotos[index].isNullOrEmpty()

    /** 实况照片的全部候选地址（下载时按顺序回退）。 */
    fun livePhotoCandidates(index: Int): List<String> = livePhotos[index].orEmpty()

    /** 实况照片的首选地址（预览播放用）。 */
    fun livePhotoUrl(index: Int): String? = livePhotos[index]?.firstOrNull()

    val hasLivePhotos: Boolean get() = livePhotos.isNotEmpty()
}

enum class MediaType {
    IMAGE,
    VIDEO,
    UNKNOWN
}

enum class Platform(val displayName: String) {
    DOUYIN("抖音"),
    XIAOHONGSHU("小红书"),
    UNKNOWN("未知")
}
