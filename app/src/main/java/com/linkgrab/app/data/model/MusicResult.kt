package com.linkgrab.app.data.model

import kotlinx.serialization.Serializable

/**
 * 音乐解析结果。
 *
 * 当前只接入酷我（kuwo）：它的 `anti.s` 直链转换接口不需要 token、
 * 防盗链也最松，是实测当下唯一稳定的来源。其它平台（网易云/QQ）
 * 的直链都带一次性时效签名，纯客户端基本下载不到。
 */
@Serializable
data class MusicResult(
    /** 酷我歌曲 id（rid）。 */
    val rid: String,
    /** 歌名。可能为空（歌词接口拿不到时）。 */
    val title: String = "",
    /** 歌手。可能为空。 */
    val artist: String = "",
    /** 封面图地址。可能为空。 */
    val coverUrl: String? = null,
    /** 完整歌词（含时间轴的 LRC 或纯文本）。 */
    val lyric: String = "",
    /** 128k MP3 直链。 */
    val playUrl128k: String? = null,
    /** 320k MP3 直链（部分歌曲可能没有，解析失败时为 null）。 */
    val playUrl320k: String? = null,
    /** 来源平台标识。 */
    val source: String = "kuwo",
)
