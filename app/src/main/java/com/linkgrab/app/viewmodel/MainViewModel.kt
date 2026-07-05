package com.linkgrab.app.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linkgrab.app.data.history.HistoryItem
import com.linkgrab.app.data.history.HistoryRepository
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.Platform
import com.linkgrab.app.data.repository.ParseRepository
import com.linkgrab.app.data.repository.SettingsRepository
import androidx.core.app.NotificationCompat
import com.linkgrab.app.notification.LiveUpdatesHelper
import com.linkgrab.app.update.UpdateChecker
import com.linkgrab.app.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val parseRepository = ParseRepository(application)
    private val settingsRepository = SettingsRepository(application)
    val liveUpdatesHelper = LiveUpdatesHelper(application)
    private val historyRepository = HistoryRepository(application)

    val allHistory = historyRepository.allHistory

    private val _uiState = MutableStateFlow(ParseUiState())
    val uiState: StateFlow<ParseUiState> = _uiState.asStateFlow()

    private val _colorMode = MutableStateFlow(0)
    val colorMode: StateFlow<Int> = _colorMode.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _liveUpdatesEnabled = MutableStateFlow(false)
    val liveUpdatesEnabled: StateFlow<Boolean> = _liveUpdatesEnabled.asStateFlow()

    private val _predictiveBack = MutableStateFlow(0)
    val predictiveBack: StateFlow<Int> = _predictiveBack.asStateFlow()

    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _guideShown = MutableStateFlow(true) // 默认 true，等 DataStore 加载后再决定
    val guideShown: StateFlow<Boolean> = _guideShown.asStateFlow()

    private val _dataLoaded = MutableStateFlow(false)
    val dataLoaded: StateFlow<Boolean> = _dataLoaded.asStateFlow()

    private val updateChecker = UpdateChecker()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var downloadJobCount = 0

    init {
        viewModelScope.launch {
            settingsRepository.colorMode.collect { _colorMode.value = it }
        }
        viewModelScope.launch {
            settingsRepository.predictiveBack.collect { _predictiveBack.value = it }
        }
        viewModelScope.launch {
            settingsRepository.liveUpdatesEnabled.collect { _liveUpdatesEnabled.value = it }
        }
        viewModelScope.launch {
            settingsRepository.guideShown.collect {
                _guideShown.value = it
                _dataLoaded.value = true
            }
        }
        // 启动时检查更新
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 延迟2秒避免影响启动
            try {
                _updateResult.value = updateChecker.checkForUpdate()
            } catch (e: Exception) {
                // 静默失败，不崩溃
            }
        }
    }

    // ==================== Settings ====================

    fun setColorMode(mode: Int) {
        viewModelScope.launch { settingsRepository.setColorMode(mode) }
    }

    fun setPredictiveBack(mode: Int) {
        viewModelScope.launch { settingsRepository.setPredictiveBack(mode) }
    }

    fun toggleLiveUpdates(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLiveUpdatesEnabled(enabled) }
    }

    fun markGuideShown() {
        viewModelScope.launch { settingsRepository.setGuideShown() }
    }

    // ==================== Parse ====================

    fun parseUrl(url: String) {
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入链接")
            return
        }

        // Batch parsing: extract all URLs from text
        val urls = extractUrls(url)
        if (urls.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "未找到有效链接")
            return
        }

        // Parse first URL (primary)
        parseSingleUrl(urls.first())
    }

    private fun extractUrls(text: String): List<String> {
        val urlPattern = """https?://[^\s<>"{}|\\^`\[\]]+""".toRegex()
        return urlPattern.findAll(text).map { it.value }.distinct().toList()
    }

    private fun parseSingleUrl(url: String) {
        val platform = parseRepository.detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            _uiState.value = _uiState.value.copy(error = "不支持的链接，请输入抖音或小红书链接")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, platform = platform)

            parseRepository.parse(url)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(isLoading = false, result = result)
                    historyRepository.add(
                        HistoryItem(
                            platform = platform.name.lowercase(),
                            title = result.title.ifEmpty { "未命名" },
                            cover = result.coverUrl ?: result.images.firstOrNull() ?: "",
                            url = url,
                            type = if (result.type == com.linkgrab.app.data.model.MediaType.VIDEO) "video" else "image",
                        )
                    )
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "解析失败")
                }
        }
    }

    fun clearResult() { _uiState.value = ParseUiState() }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // ==================== History ====================

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { historyRepository.toggleFavorite(id, favorite) }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch { historyRepository.delete(item) }
    }

    // ==================== Update ====================

    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateResult.value = updateChecker.checkForUpdate()
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdate() { _updateResult.value = null }

    // ==================== Download with Live Updates ====================

    fun downloadImage(context: Context, url: String, filename: String) {
        val jobId = ++downloadJobCount
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            if (_liveUpdatesEnabled.value) {
                liveUpdatesHelper.startProgress(title = "LinkGrab", content = "下载图片中...", indeterminate = true)
            }
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7)")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body ?: throw Exception("Empty response")
                    val bytes = body.byteStream().readBytes()
                    body.close()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw Exception("Failed to decode image")
                    saveBitmapToGallery(context, bitmap, filename)
                }
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Success
                    showDownloadCompleteNotification(context, "图片保存成功", isVideo = false)
                }
            } catch (e: Exception) {
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                    showDownloadCompleteNotification(context, "下载失败: ${e.message}", isVideo = false)
                }
            }
        }
    }

    fun downloadVideo(context: Context, url: String, filename: String) {
        val jobId = ++downloadJobCount
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            if (_liveUpdatesEnabled.value) {
                liveUpdatesHelper.startProgress(title = "LinkGrab", content = "下载视频中...", indeterminate = true)
            }
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7)")
                        .build()
                    val response = httpClient.newCall(request).execute()
                    val body = response.body ?: throw Exception("Empty response")
                    val contentLength = body.contentLength()
                    val inputStream = body.byteStream()

                    val contentValues = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LinkGrab")
                            put(MediaStore.Video.Media.IS_PENDING, 1)
                        }
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                        ?: throw Exception("Failed to create file")

                    resolver.openOutputStream(uri)?.use { out ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            if (contentLength > 0) {
                                val progress = ((totalRead * 100) / contentLength).toInt()
                                if (downloadJobCount == jobId) {
                                    _downloadState.value = DownloadState.Downloading(progress)
                                    if (_liveUpdatesEnabled.value) {
                                        liveUpdatesHelper.updateProgress(title = "LinkGrab", content = "下载视频: $progress%", progress = progress, maxProgress = 100)
                                    }
                                }
                            }
                        }
                    }
                    inputStream.close()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                }
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Success
                    showDownloadCompleteNotification(context, "视频保存成功", isVideo = true)
                }
            } catch (e: Exception) {
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                    showDownloadCompleteNotification(context, "下载失败: ${e.message}", isVideo = false)
                }
            }
        }
    }

    fun resetDownloadState() { _downloadState.value = DownloadState.Idle }

    private fun showDownloadCompleteNotification(context: Context, message: String, isVideo: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "download_complete", "下载完成",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
            type = if (isVideo) "video/*" else "image/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, System.currentTimeMillis().toInt(), galleryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, "download_complete")
            .setSmallIcon(com.linkgrab.app.R.drawable.ic_notification)
            .setContentTitle("LinkGrab")
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ==================== Live Updates Test ====================

    fun testLiveUpdates() {
        if (!_liveUpdatesEnabled.value) return

        // 诊断日志
        val sdk = android.os.Build.VERSION.SDK_INT
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val release = android.os.Build.VERSION.RELEASE
        android.util.Log.i("LiveUpdates", "=== 设备信息 ===")
        android.util.Log.i("LiveUpdates", "  SDK: $sdk (需要 >= 36)")
        android.util.Log.i("LiveUpdates", "  设备: $device")
        android.util.Log.i("LiveUpdates", "  型号: $model")
        android.util.Log.i("LiveUpdates", "  系统: Android $release")
        android.util.Log.i("LiveUpdates", "  isAndroid16: ${sdk >= 36}")
        android.util.Log.i("LiveUpdates", "================")

        liveUpdatesHelper.startProgress(title = "LinkGrab", content = "正在解析链接...", indeterminate = true)
        android.util.Log.i("LiveUpdates", "Foreground service started")

        viewModelScope.launch {
            for (i in 1..20) {
                kotlinx.coroutines.delay(1000)
                liveUpdatesHelper.updateProgress(title = "LinkGrab", content = "解析进度: ${i * 5}%", progress = i * 5, maxProgress = 100)
            }
            liveUpdatesHelper.finish(title = "LinkGrab", content = "解析完成！")
            android.util.Log.i("LiveUpdates", "Finished")
        }
    }

    // ==================== MediaStore ====================

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinkGrab")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create file")
        resolver.openOutputStream(uri)?.use { it ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        bitmap.recycle()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    private fun saveVideoToGallery(context: Context, inputStream: java.io.InputStream, filename: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/LinkGrab")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create file")
        resolver.openOutputStream(uri)?.use { out ->
            inputStream.copyTo(out)
        }
        inputStream.close()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }
}

data class ParseUiState(
    val isLoading: Boolean = false,
    val result: MediaResult? = null,
    val error: String? = null,
    val platform: Platform = Platform.UNKNOWN,
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int = 0) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
