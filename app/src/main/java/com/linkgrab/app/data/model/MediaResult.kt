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
)

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
