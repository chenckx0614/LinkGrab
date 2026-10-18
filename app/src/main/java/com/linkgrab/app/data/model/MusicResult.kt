package com.linkgrab.app.data.model

import kotlinx.serialization.Serializable

/**
 * 音乐解析结果。
 *
 * 2026-10 起从「只有酷我」扩成多源：酷我 / 网易云 / QQ 音乐。
 * `source` 标明来源，`rid` 的语义变成「该平台的歌曲 id」：
 *  - 酷我：纯数字 rid
 *  - 网易云：纯数字歌曲 id
 *  - QQ 音乐：songmid（字母数字混合）
 */
@Serializable
data class MusicResult(
    /** 平台内的歌曲 id（酷我 rid / 网易云 id / QQ songmid）。 */
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
    /** 来源平台标识："kuwo" / "netease" / "qq"。 */
    val source: String = "kuwo",
    /** 实际拿到的码率标签（"320k"/"192k"/"128k"），仅用于展示。 */
    val bitrateLabel: String = "",
)
