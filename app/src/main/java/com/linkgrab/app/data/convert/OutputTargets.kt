package com.linkgrab.app.data.convert

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.linkgrab.app.data.download.SaveLocation

/**
 * 转换成品的落盘位置，跟下载走同一套规则：
 * 用户选过保存文件夹就用 SAF 写进去，没选就进 MediaStore 的 图片/音乐 目录。
 *
 * MediaStore 那份先以 `IS_PENDING=1` 建出来，写完再置 0 —— 中途失败/取消就把 uri 删掉，
 * 相册里不会留下半张图、半首歌。
 */
object OutputTargets {

    private const val IMAGE_RELATIVE = "Pictures/LinkGrab/转换"
    private const val AUDIO_RELATIVE = "Music/LinkGrab"
    private val IMAGE_SUBDIRS = listOf("LinkGrab", "转换")
    private val AUDIO_SUBDIRS = listOf("LinkGrab")

    fun createImage(context: Context, name: String, mime: String, tree: String?): Uri? =
        create(
            context = context,
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            relativePath = IMAGE_RELATIVE,
            subDirs = IMAGE_SUBDIRS,
            name = name,
            mime = mime,
            tree = tree,
        )

    fun createAudio(context: Context, name: String, mime: String, tree: String?): Uri? =
        create(
            context = context,
            collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            relativePath = AUDIO_RELATIVE,
            subDirs = AUDIO_SUBDIRS,
            name = name,
            mime = mime,
            tree = tree,
        )

    /** 写完收尾：MediaStore 的挂着 IS_PENDING，得显式放出来才在相册/音乐里可见。 */
    fun publish(context: Context, uri: Uri) {
        if (!SaveLocation.isMediaStoreUri(uri)) return
        val values = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        runCatching { context.contentResolver.update(uri, values, null, null) }
    }

    fun discard(context: Context, uri: Uri) {
        SaveLocation.delete(context, uri)
    }

    private fun create(
        context: Context,
        collection: Uri,
        relativePath: String,
        subDirs: List<String>,
        name: String,
        mime: String,
        tree: String?,
    ): Uri? {
        // 自定义目录优先；用户挑的目录被删/权限被回收时静默回退到 MediaStore，
        // 不让一次转换因为存储位置失败而整体失败。
        tree?.takeIf { it.isNotBlank() }?.let { saved ->
            runCatching { Uri.parse(saved) }.getOrNull()?.let { treeUri ->
                SaveLocation.createInTree(context, treeUri, subDirs, name, mime)?.let { return it }
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        return runCatching { context.contentResolver.insert(collection, values) }.getOrNull()
    }

    /** 原文件名去掉后缀，换成目标格式的扩展名；读不到就用时间戳。 */
    fun targetName(context: Context, source: Uri, ext: String): String {
        val original = runCatching {
            context.contentResolver.query(source, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        val base = original
            ?.substringBeforeLast('.', original)
            ?.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "LinkGrab_${System.currentTimeMillis()}"
        return "$base.$ext"
    }
}
