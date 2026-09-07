package com.linkgrab.app.data.model

import kotlinx.serialization.Serializable

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
     * corresponding entry in [images]. An image whose index appears here
     * is a live photo: a still frame plus a short playable video.
     */
    val livePhotos: Map<Int, String> = emptyMap(),
) {
    fun isLivePhoto(index: Int): Boolean = livePhotos.containsKey(index)
    fun livePhotoUrl(index: Int): String? = livePhotos[index]
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
