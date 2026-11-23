package com.linkgrab.app.data.download

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile

/**
 * 成品文件的落地位置。
 *
 * 两种目标共用同一套下载循环（都是 `contentResolver.openOutputStream(uri, mode)`）：
 * - 默认：MediaStore 相册/视频库，`IS_PENDING=1` 让半成品不露脸，完成后置 0；
 * - 自定义：用户用系统文件选择器挑的目录（SAF），直接以正常文件名写进去。
 *
 * 自定义目录任何一步失败都返回 null，调用方据此回退到 MediaStore —— 用户自己挑的
 * 目录可能被删、SD 卡被拔、权限被系统回收，不能让下载因此失败。
 */
object SaveLocation {

    /** 在 SAF 目录下创建文件；必要时逐级建子目录。失败返回 null。 */
    fun createInTree(
        context: Context,
        treeUri: Uri,
        subDirs: List<String>,
        displayName: String,
        mime: String,
    ): Uri? = runCatching {
        var dir = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!dir.canWrite()) return null
        for (name in subDirs) {
            val existing = dir.findFile(name)
            dir = when {
                existing != null && existing.isDirectory -> existing
                existing != null -> return null // 同名文件占着位置，别硬来
                else -> dir.createDirectory(name) ?: return null
            }
        }
        dir.createFile(mime, displayName)?.uri
    }.getOrNull()

    /** MediaStore 的 uri 才需要 IS_PENDING 收尾；SAF 文件创建即完整可见。 */
    fun isMediaStoreUri(uri: Uri): Boolean = uri.authority == MediaStore.AUTHORITY

    /** 删除半成品/取消的文件；两种 uri 都能走 ContentResolver。 */
    fun delete(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }
}
