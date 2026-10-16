package com.linkgrab.app.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
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

    private val _autoCheckUpdate = MutableStateFlow(true)
    val autoCheckUpdate: StateFlow<Boolean> = _autoCheckUpdate.asStateFlow()

    private val _updateReminder = MutableStateFlow(true)
    val updateReminder: StateFlow<Boolean> = _updateReminder.asStateFlow()

    // 最近一次检查是否为手动触发：手动检查发现新版本始终弹窗；
    // 自动检查弹不弹由「更新提醒」开关决定。
    private val _manualUpdateCheck = MutableStateFlow(false)
    val manualUpdateCheck: StateFlow<Boolean> = _manualUpdateCheck.asStateFlow()

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
            settingsRepository.autoCheckUpdate.collect { _autoCheckUpdate.value = it }
        }
        viewModelScope.launch {
            settingsRepository.updateReminder.collect { _updateReminder.value = it }
        }
        viewModelScope.launch {
            settingsRepository.guideShown.collect {
                _guideShown.value = it
                _dataLoaded.value = true
            }
        }
        // 启动时检查更新（受「自动检查更新」开关控制；等开关加载完再决定）
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 延迟2秒避免影响启动
            if (!_autoCheckUpdate.value) return@launch
            try {
                _manualUpdateCheck.value = false // 自动检查：弹窗与否看「更新提醒」
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

    fun setAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoCheckUpdate(enabled) }
    }

    fun setUpdateReminder(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUpdateReminder(enabled) }
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
            _manualUpdateCheck.value = true // 手动检查：发现新版本始终弹窗
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

    /**
     * 统一下载入口：图片 + 视频按队列**串行**下载，进度跨文件聚合。
     *
     * 之前「下载全部」是 N 个协程并发各自往 _downloadState 写进度，互相覆盖
     * （进度条回跳、并发下载抢占带宽），现在收敛为一个队列顺序执行。
     */
    fun downloadBatch(
        context: Context,
        images: List<Pair<String, String>> = emptyList(),
        videos: List<Pair<List<String>, String>> = emptyList(),
    ) {
        val imageItems = images.filter { it.first.isNotBlank() }
        val videoItems = videos.map { (candidates, name) ->
            candidates.filter { it.isNotBlank() }.distinct() to name
        }.filter { it.first.isNotEmpty() }
        if (imageItems.isEmpty() && videoItems.isEmpty()) return

        val total = imageItems.size + videoItems.size
        val jobId = ++downloadJobCount
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading(0)
            if (_liveUpdatesEnabled.value) {
                liveUpdatesHelper.startProgress(title = "LinkGrab", content = "准备下载...", indeterminate = true)
            }
            var succeeded = 0
            var firstError: Exception? = null
            withContext(Dispatchers.IO) {
                imageItems.forEachIndexed { i, (url, filename) ->
                    if (downloadJobCount != jobId) return@withContext
                    try {
                        saveImageFromNetwork(context, url, filename, fileIndex = i, fileTotal = total)
                        succeeded++
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                    }
                }
                videoItems.forEachIndexed { j, (candidates, filename) ->
                    if (downloadJobCount != jobId) return@withContext
                    val fileIndex = imageItems.size + j
                    var lastError: Exception? = null
                    for (candidate in candidates) {
                        try {
                            fetchAndSaveVideo(context, candidate, filename, jobId, fileIndex, total)
                            lastError = null
                            break
                        } catch (e: Exception) {
                            lastError = e
                        }
                    }
                    if (lastError == null) succeeded++
                    else if (firstError == null) firstError = lastError
                }
            }
            if (downloadJobCount != jobId) return@launch
            when {
                succeeded == total -> {
                    _downloadState.value = DownloadState.Success
                    val noun = if (videoItems.isEmpty()) "图片" else "视频"
                    val msg = if (total > 1) "已保存 $total 个${noun}文件" else "${noun}保存成功"
                    showDownloadCompleteNotification(context, msg, isVideo = videoItems.isNotEmpty())
                }
                succeeded > 0 -> {
                    _downloadState.value = DownloadState.Error(
                        "已保存 $succeeded/$total 个文件；${firstError?.message ?: "其余下载失败"}"
                    )
                }
                else -> {
                    _downloadState.value = DownloadState.Error(firstError?.message ?: "Download failed")
                    showDownloadCompleteNotification(
                        context, "下载失败: ${firstError?.message}", isVideo = videoItems.isNotEmpty(),
                    )
                }
            }
        }
    }

    fun downloadImage(context: Context, url: String, filename: String) =
        downloadBatch(context, images = listOf(url to filename))

    fun downloadVideo(context: Context, url: String, filename: String) =
        downloadBatch(context, videos = listOf(listOf(url) to filename))

    fun downloadVideo(context: Context, urls: List<String>, filename: String) =
        downloadBatch(context, videos = listOf(urls to filename))

    /**
     * 下载图片并写入相册：**直接落盘原始字节**，不再「读进内存 → 解码 Bitmap
     * → 重压缩 JPEG」。老流程大图要吃几十 MB 内存、CPU 重压缩一次还会损画质；
     * 现在保留原始格式（jpg/png/webp），格式与扩展名按响应头自动匹配。
     */
    private fun saveImageFromNetwork(
        context: Context,
        url: String,
        filename: String,
        fileIndex: Int,
        fileTotal: Int,
    ) {
        val requestBuilder = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
        // 小红书 CDN 对空 Referer 的请求更容易 403
        if (url.contains("xhscdn.com") || url.contains("xiaohongshu.com")) {
            requestBuilder.header("Referer", "https://www.xiaohongshu.com/")
        }
        httpClient.newCall(requestBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty response")

            val contentType = body.contentType()?.toString()?.substringBefore(';')?.trim().orEmpty()
            if (contentType.startsWith("text/html")) throw Exception("服务端返回了网页而不是图片")
            val mime = contentType.ifBlank { "image/jpeg" }
            val ext = when (mime) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                "image/heic", "image/heif" -> "heic"
                else -> "jpg"
            }
            val displayName = filename.substringBeforeLast('.') + ".$ext"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinkGrab")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create file")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    val input = body.byteStream()
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    val contentLength = body.contentLength()
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        reportOverallProgress(fileIndex, fileTotal, totalRead, contentLength, "下载图片")
                    }
                } ?: throw Exception("Failed to open output stream")
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        }
    }

    /** 跨文件聚合进度：已完成的整文件 + 当前文件的字节占比。 */
    private fun reportOverallProgress(fileIndex: Int, fileTotal: Int, bytes: Long, contentLength: Long, label: String) {
        if (fileTotal <= 0) return
        val fileFraction = if (contentLength > 0) (bytes.toDouble() / contentLength).coerceIn(0.0, 1.0) else 0.0
        val progress = ((fileIndex + fileFraction) * 100 / fileTotal).toInt().coerceIn(0, 99)
        _downloadState.value = DownloadState.Downloading(progress)
        if (_liveUpdatesEnabled.value) {
            liveUpdatesHelper.updateProgress(
                title = "LinkGrab",
                content = "$label: $progress%",
                progress = progress,
                maxProgress = 100,
            )
        }
    }

    /**
     * 下载视频并写入相册。失败时抛异常（HTTP 状态码会带进 message），
     * 交给上层决定是否换下一个候选地址。
     */
    private fun fetchAndSaveVideo(
        context: Context,
        url: String,
        filename: String,
        jobId: Int,
        fileIndex: Int = 0,
        fileTotal: Int = 1,
    ) {
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
                        if (downloadJobCount == jobId) {
                            reportOverallProgress(fileIndex, fileTotal, totalRead, contentLength, "下载视频")
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
