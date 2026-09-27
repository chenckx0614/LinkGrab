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
import com.linkgrab.app.update.UpdateDownloader
import com.linkgrab.app.update.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    // 「跳转到结果页」必须是一次性事件，不能由持久状态推导：结果页返回后
    // result 仍是非空（故意不在页面销毁时清，否则会把还在屏幕上的页面清空
    // 造成白屏），若首页写 `if (result != null) navigate()` 就会立刻再跳
    // 回去，形成死循环卡在结果页。
    private val _navigateToResult = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<Unit> = _navigateToResult.asSharedFlow()

    private val _colorMode = MutableStateFlow(0)
    val colorMode: StateFlow<Int> = _colorMode.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _liveUpdatesEnabled = MutableStateFlow(false)
    val liveUpdatesEnabled: StateFlow<Boolean> = _liveUpdatesEnabled.asStateFlow()

    private val _predictiveBack = MutableStateFlow(0)
    val predictiveBack: StateFlow<Int> = _predictiveBack.asStateFlow()

    private val _bottomBarStyle = MutableStateFlow(0)
    val bottomBarStyle: StateFlow<Int> = _bottomBarStyle.asStateFlow()

    private val _updateResult = MutableStateFlow<UpdateResult?>(null)
    val updateResult: StateFlow<UpdateResult?> = _updateResult.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _guideShown = MutableStateFlow(true) // 默认 true，等 DataStore 加载后再决定
    val guideShown: StateFlow<Boolean> = _guideShown.asStateFlow()

    private val _dataLoaded = MutableStateFlow(false)
    val dataLoaded: StateFlow<Boolean> = _dataLoaded.asStateFlow()

    private val updateChecker = UpdateChecker()
    private val updateDownloader = UpdateDownloader(application, viewModelScope)
    val updateDownloadStatus = updateDownloader.status

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private companion object {
        /** 下载 CDN 媒体时使用的浏览器 UA（原来那个缺少 Chrome 尾巴，容易被 CDN 拒）。 */
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
    }

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
            settingsRepository.bottomBarStyle.collect { _bottomBarStyle.value = it }
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

    fun setBottomBarStyle(style: Int) {
        viewModelScope.launch { settingsRepository.setBottomBarStyle(style) }
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
        // 分享文案有时会把短链从中间折行（如 https://xhslink.cn/o ⏎ /ANysiW4TgYR），
        // 先把「前一行以字母数字结尾 + 换行 + 下一行以 / 开头」的断点拼回去再抽链接。
        val glued = text.replace(Regex("""(?<=[A-Za-z0-9])\r?\n(?=/)"""), "")
        val urlPattern = """https?://[^\s<>"{}|\\^`\[\]]+""".toRegex()
        return urlPattern.findAll(glued).map { it.value }.distinct().toList()
    }

    private fun parseSingleUrl(url: String) {
        val platform = parseRepository.detectPlatform(url)
        if (platform == Platform.UNKNOWN) {
            _uiState.value = _uiState.value.copy(error = "不支持的链接，请输入抖音或小红书链接")
            return
        }

        viewModelScope.launch {
            // 新解析开始时立刻清掉上一次的结果——这是唯一允许出现空结果的
            // 时机。绝不要在结果页 onDispose 时清状态：pop 转场期间结果页仍在
            // 组合里，退出瞬间清 result 会让还在屏幕上的页面塌成骨架屏
            // （用户看到的「返回白屏」），还会让首页在动画收尾帧多一次重组。
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                result = null,
                platform = platform,
            )

            parseRepository.parse(url)
                .onSuccess { result ->
                    _uiState.value = _uiState.value.copy(isLoading = false, result = result)
                    // 用一次性事件驱动跳转：首页和历史页发起的解析都能到达
                    _navigateToResult.tryEmit(Unit)
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

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    // ==================== History ====================

    fun toggleFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { historyRepository.toggleFavorite(id, favorite) }
    }

    fun deleteHistory(item: HistoryItem) {
        viewModelScope.launch { historyRepository.delete(item) }
    }

    fun deleteHistoryItems(items: List<HistoryItem>) {
        viewModelScope.launch { historyRepository.deleteMany(items) }
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

    fun downloadUpdate(apkUrl: String, version: String) {
        updateDownloader.startDownload(apkUrl, version)
    }

    fun installUpdate(uri: Uri) {
        updateDownloader.installApk(uri)
    }

    fun openUpdateInBrowser(url: String) {
        updateDownloader.openInBrowser(url)
    }

    fun resetUpdateDownload() {
        updateDownloader.reset()
    }

    override fun onCleared() {
        super.onCleared()
        updateDownloader.cancel()
    }

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

    fun downloadVideo(context: Context, url: String, filename: String) =
        downloadVideo(context, listOf(url), filename)

    /**
     * 下载视频，支持**多个候选地址按顺序回退**。
     *
     * 实况照片的视频 CDN 地址常带时效签名，首选地址可能 403 / 已过期；
     * 解析阶段已把 backupUrls 一并收下，这里逐个试。
     */
    fun downloadVideo(context: Context, urls: List<String>, filename: String) {
        val candidates = urls.filter { it.isNotBlank() }.distinct()
        if (candidates.isEmpty()) {
            _downloadState.value = DownloadState.Error("没有可用的视频地址")
            return
        }
        val jobId = ++downloadJobCount
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            if (_liveUpdatesEnabled.value) {
                liveUpdatesHelper.startProgress(title = "LinkGrab", content = "下载视频中...", indeterminate = true)
            }
            try {
                withContext(Dispatchers.IO) {
                    var lastError: Exception? = null
                    for (candidate in candidates) {
                        try {
                            fetchAndSaveVideo(context, candidate, filename, jobId)
                            lastError = null
                            break
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }
                    lastError?.let { throw it }
                }
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Success
                    showDownloadCompleteNotification(context, "视频保存成功", isVideo = true)
                }
            } catch (e: Exception) {
                if (downloadJobCount == jobId) {
                    _downloadState.value = DownloadState.Error(e.message ?: "Download failed")
                    showDownloadCompleteNotification(context, "下载失败: ${e.message}", isVideo = true)
                }
            }
        }
    }

    /**
     * 真正下载并写入相册。失败时抛异常（HTTP 状态码会带进 message），
     * 交给上层决定是否换下一个候选地址。
     */
    private fun fetchAndSaveVideo(context: Context, url: String, filename: String, jobId: Int) {
        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
        // 小红书 CDN 对空 Referer 的请求更容易 403
        if (url.contains("xhscdn.com") || url.contains("xiaohongshu.com")) {
            requestBuilder.header("Referer", "https://www.xiaohongshu.com/")
        }
        val response = httpClient.newCall(requestBuilder.build()).execute()
        response.use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty response")

            // 有些情况下服务端会回一个 HTML 错误页而不是视频，直接写出会得到坏文件
            val contentType = body.contentType()?.toString().orEmpty()
            if (contentType.contains("text/html")) throw Exception("服务端返回了网页而不是视频")

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

            try {
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
                                    liveUpdatesHelper.updateProgress(
                                        title = "LinkGrab",
                                        content = "下载视频: $progress%",
                                        progress = progress,
                                        maxProgress = 100,
                                    )
                                }
                            }
                        }
                    }
                } ?: throw Exception("Failed to open output stream")
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            } finally {
                runCatching { inputStream.close() }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
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
