package com.linkgrab.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class UpdateDownloadStatus {
    data object Idle : UpdateDownloadStatus()
    data class Downloading(val progress: Int) : UpdateDownloadStatus()
    data class Downloaded(val uri: Uri) : UpdateDownloadStatus()
    data object Installed : UpdateDownloadStatus()
    data class Error(val message: String) : UpdateDownloadStatus()
}

/**
 * 使用系统 DownloadManager 在应用内下载 APK 并调起安装。
 */
class UpdateDownloader(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val _status = MutableStateFlow<UpdateDownloadStatus>(UpdateDownloadStatus.Idle)
    val status: StateFlow<UpdateDownloadStatus> = _status.asStateFlow()

    private var currentDownloadId: Long = -1
    private var progressJob: Job? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != currentDownloadId) return
            progressJob?.cancel()
            handleDownloadComplete(id)
            unregisterReceiver()
        }
    }

    /**
     * 开始下载 APK。
     *
     * @param apkUrl release asset 的真实下载地址
     * @param version 新版本号，用于文件名与通知标题
     */
    fun startDownload(apkUrl: String, version: String) {
        if (apkUrl.isBlank()) {
            _status.value = UpdateDownloadStatus.Error("下载地址为空")
            return
        }

        // 取消上一次下载
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (_: Exception) {}
        }
        progressJob?.cancel()
        _status.value = UpdateDownloadStatus.Downloading(0)

        val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
            setMimeType("application/vnd.android.package-archive")
            setTitle("LinkGrab v$version")
            setDescription("正在下载更新…")
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "LinkGrab-v$version.apk",
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }

        registerReceiver()
        currentDownloadId = downloadManager.enqueue(request)
        pollProgress(currentDownloadId)
    }

    /**
     * 调起系统安装器。Android 8+ 会先检查「安装未知应用」权限。
     */
    fun installApk(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                kotlin.runCatching { context.startActivity(intent) }
                _status.value = UpdateDownloadStatus.Error("请授予安装未知应用权限后重试")
                return
            }
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        kotlin.runCatching {
            context.startActivity(intent)
            _status.value = UpdateDownloadStatus.Installed
        }.onFailure {
            _status.value = UpdateDownloadStatus.Error("无法打开安装器：${it.message}")
        }
    }

    /**
     * 用浏览器打开 release 页面作为兜底。
     */
    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        kotlin.runCatching { context.startActivity(intent) }
    }

    fun reset() {
        _status.value = UpdateDownloadStatus.Idle
    }

    fun cancel() {
        progressJob?.cancel()
        if (currentDownloadId != -1L) {
            try {
                downloadManager.remove(currentDownloadId)
            } catch (_: Exception) {}
        }
        unregisterReceiver()
        _status.value = UpdateDownloadStatus.Idle
    }

    private fun pollProgress(id: Long) {
        progressJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = downloadManager.query(query)
                try {
                    if (cursor.moveToFirst()) {
                        val statusIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        when (cursor.getInt(statusIdx)) {
                            DownloadManager.STATUS_RUNNING -> {
                                val totalIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val downloadedIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val total = cursor.getLong(totalIdx)
                                val downloaded = cursor.getLong(downloadedIdx)
                                if (total > 0) {
                                    val progress = ((downloaded * 100) / total).toInt()
                                    _status.value = UpdateDownloadStatus.Downloading(progress)
                                }
                            }
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                handleDownloadComplete(id)
                                progressJob?.cancel()
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                                _status.value = UpdateDownloadStatus.Error("下载失败 (${cursor.getInt(reasonIdx)})")
                                progressJob?.cancel()
                                unregisterReceiver()
                            }
                            else -> Unit
                        }
                    }
                } finally {
                    cursor.close()
                }
                delay(500)
            }
        }
    }

    private fun handleDownloadComplete(id: Long) {
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)
        try {
            if (cursor.moveToFirst()) {
                val statusIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                when (cursor.getInt(statusIdx)) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = downloadManager.getUriForDownloadedFile(id)
                        if (uri != null) {
                            _status.value = UpdateDownloadStatus.Downloaded(uri)
                        } else {
                            _status.value = UpdateDownloadStatus.Error("无法获取下载文件")
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reasonIdx = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                        _status.value = UpdateDownloadStatus.Error("下载失败 (${cursor.getInt(reasonIdx)})")
                    }
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        receiverRegistered = false
    }
}
