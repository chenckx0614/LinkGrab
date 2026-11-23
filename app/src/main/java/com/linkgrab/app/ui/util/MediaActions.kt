package com.linkgrab.app.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast

/** 已保存文件的通用动作：打开、分享、复制位置。下载管理与工具箱转换共用。 */
object MediaActions {

    fun open(context: Context, uri: Uri) {
        val mime = mimeOf(context, uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val ok = runCatching { context.startActivity(intent) }.isSuccess
        if (!ok) toast(context, "没有能打开这个文件的应用")
    }

    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(context, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val ok = runCatching {
            context.startActivity(Intent.createChooser(intent, "分享到"))
        }.isSuccess
        if (!ok) toast(context, "没有可用的分享方式")
    }

    fun copyLocation(context: Context, uri: Uri) {
        val location = locationOf(context, uri)
        if (location == null) {
            toast(context, "读不到保存位置")
            return
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("path", location))
        toast(context, "已复制：$location")
    }

    /** 形如 “Pictures/LinkGrab/抖音/20261123/xxx.jpg”，方便去文件管理器里找。 */
    fun locationOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val dir = cursor.getString(0).orEmpty().trimEnd('/')
            val name = cursor.getString(1).orEmpty()
            listOf(dir, name).filter { it.isNotBlank() }.joinToString("/").ifBlank { null }
        }
    }.getOrNull()

    private fun mimeOf(context: Context, uri: Uri): String =
        runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "*/*"

    private fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
