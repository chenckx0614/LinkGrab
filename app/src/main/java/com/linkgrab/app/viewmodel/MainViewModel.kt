package com.linkgrab.app.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import java.io.FileOutputStream

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val parseRepository = ParseRepository(application)
    val settingsRepository = SettingsRepository(application)
    val liveUpdatesHelper = LiveUpdatesHelper(application)
    val historyRepository = HistoryRepository(application)

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

    private val updateChecker = UpdateChecker()

    private val httpClient = OkHttpClient()

    init {
        viewModelScope.launch {
            settingsRepository.colorMode.collect { mode ->
                _colorMode.value = mode
            }
        }
        viewModelScope.launch {
            settingsRepository.predictiveBack.collect { mode ->
                _predictiveBack.value = mode
            }
        }
    }

    fun setColorMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setColorMode(mode)
        }
    }

    fun setPredictiveBack(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setPredictiveBack(mode)
        }
    }

    fun parseUrl(url: String) {
        if (url.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "请输入链接")
            return
        }

        val platform = parseRepository.detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            _uiState.value = _uiState.value.copy(error = "不支持的链接，请输入抖音或小红书链接")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                platform = platform
            )

            parseRepository.parse(url)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        result = result
                    )
                    // Save to history
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "解析失败"
                    )
                }
        }
    }

    fun clearResult() {
        _uiState.value = ParseUiState()
    }

    // History
    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { historyRepository.toggleFavorite(id, favorite) }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch { historyRepository.delete(item) }
    }

    fun deleteHistoryById(id: Long) {
        viewModelScope.launch { historyRepository.deleteById(id) }
    }

    // Update check
    fun checkForUpdate() {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            _updateResult.value = updateChecker.checkForUpdate()
            _isCheckingUpdate.value = false
        }
    }

    fun dismissUpdate() {
        _updateResult.value = null
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun toggleLiveUpdates(enabled: Boolean) {
        _liveUpdatesEnabled.value = enabled
    }

    fun testLiveUpdates() {
        if (!_liveUpdatesEnabled.value) return

        android.util.Log.d("LiveUpdates", "testLiveUpdates() started")

        // Start with indeterminate progress
        liveUpdatesHelper.startProgress(
            title = "LinkGrab",
            content = "正在解析链接...",
            indeterminate = true,
        )

        // Simulate progress: 20 seconds, 1 second per step
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000) // 1s initial delay

            for (i in 1..20) {
                kotlinx.coroutines.delay(1000) // 1 second per step
                val percent = (i * 5).coerceAtMost(100)
                liveUpdatesHelper.updateProgress(
                    title = "LinkGrab",
                    content = "解析进度: $percent%  (${i}/20秒)",
                    progress = percent,
                    maxProgress = 100,
                )
                android.util.Log.d("LiveUpdates", "Progress: $percent% (${i}/20)")
            }

            // Finish
            liveUpdatesHelper.finish(
                title = "LinkGrab",
                content = "解析完成！",
            )
            android.util.Log.d("LiveUpdates", "Finished")
        }
    }

    fun downloadImage(context: Context, url: String, filename: String) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val bytes = response.body?.bytes() ?: throw Exception("Empty response")

                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw Exception("Failed to decode image")

                    saveBitmapToGallery(context, bitmap, filename)
                }
                _downloadState.value = DownloadState.Success
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun downloadVideo(context: Context, url: String, filename: String) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7)")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val body = response.body ?: throw Exception("Empty response")

                    saveVideoToGallery(context, body.byteStream(), filename)
                }
                _downloadState.value = DownloadState.Success
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

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

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        }

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

        resolver.openOutputStream(uri)?.use { outputStream ->
            inputStream.copyTo(outputStream)
        }

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
    data object Downloading : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
