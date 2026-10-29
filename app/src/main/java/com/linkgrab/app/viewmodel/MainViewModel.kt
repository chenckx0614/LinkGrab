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
import com.linkgrab.app.data.feedback.FeedbackStore
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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

    /** 更新下载源："gitee"（默认）/ "github"。 */
    private val _downloadSource = MutableStateFlow("gitee")
    val downloadSource: StateFlow<String> = _downloadSource.asStateFlow()

    // ==================== 3.0 下载队列 ====================

    /** 下载任务列表（会话内持久，下载管理页展示）。 */
    private val _downloadTasks = MutableStateFlow<List<DownloadTaskItem>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTaskItem>> = _downloadTasks.asStateFlow()

    /** 队列并发数（1-4，默认 2），每批下载开始时生效。 */
    private val _downloadConcurrency = MutableStateFlow(2)
    val downloadConcurrency: StateFlow<Int> = _downloadConcurrency.asStateFlow()

    /** 下载完成按 平台/日期 自动归档子目录，默认开。 */
    private val _autoOrganize = MutableStateFlow(true)
    val autoOrganize: StateFlow<Boolean> = _autoOrganize.asStateFlow()
    private val _toolboxOpenInBrowser = MutableStateFlow(false)
    val toolboxOpenInBrowser: StateFlow<Boolean> = _toolboxOpenInBrowser.asStateFlow()

    /** 回前台自动识别剪贴板里的分享链接（默认开）。 */
    private val _clipboardDetect = MutableStateFlow(true)
    val clipboardDetect: StateFlow<Boolean> = _clipboardDetect.asStateFlow()

    /** 主界面左右滑动手势是否切换页面（默认开）。 */
    private val _swipeSwitchPages = MutableStateFlow(true)
    val swipeSwitchPages: StateFlow<Boolean> = _swipeSwitchPages.asStateFlow()

    /** 上次选的保存清晰度 label（"1080P" 之类；空 = 用最高档）。 */
    private val _preferredQuality = MutableStateFlow("")
    val preferredQuality: StateFlow<String> = _preferredQuality.asStateFlow()

    /** 批量解析结果（3.0）。 */
    private val _batchResults = MutableStateFlow<List<BatchParseItem>>(emptyList())
    val batchResults: StateFlow<List<BatchParseItem>> = _batchResults.asStateFlow()

    private val _isBatchParsing = MutableStateFlow(false)
    val isBatchParsing: StateFlow<Boolean> = _isBatchParsing.asStateFlow()

    private val _navigateToBatch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToBatch: SharedFlow<Unit> = _navigateToBatch.asSharedFlow()

    /** 运行时控制旗标：worker 在写入循环里轮询这两个集合实现暂停/取消。 */
    private val pauseFlags: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val cancelFlags: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** 任务列表改动锁 + id 发放器。 */
    private val taskMutex = kotlinx.coroutines.sync.Mutex()
    private var taskIdSeed = 0L

    /** 最近一次成功解析的平台显示名（抖音/小红书），下载归档用。 */
    var currentPlatformLabel: String? = null
        private set

    /** 最近一次成功解析的作品标题与平台 key，下载成功后给历史打「已保存」。 */
    private var currentTitle: String = ""
    private var currentPlatformKey: String = ""

    /** 缓存有效期：媒体直链带签名（小红书约 1 小时失效），45 分钟稳妥。 */
    private val cacheTtlMs = 45 * 60 * 1000L

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
            settingsRepository.downloadSource.collect { _downloadSource.value = it }
        }
        viewModelScope.launch {
            settingsRepository.downloadConcurrency.collect { _downloadConcurrency.value = it }
        }
        viewModelScope.launch {
            settingsRepository.autoOrganize.collect { _autoOrganize.value = it }
        }
        viewModelScope.launch {
            settingsRepository.toolboxOpenInBrowser.collect { _toolboxOpenInBrowser.value = it }
        }
        viewModelScope.launch {
            settingsRepository.clipboardDetect.collect { _clipboardDetect.value = it }
        }
        viewModelScope.launch {
            settingsRepository.swipeSwitchPages.collect { _swipeSwitchPages.value = it }
        }
        viewModelScope.launch {
            settingsRepository.preferredQuality.collect { _preferredQuality.value = it }
        }
        viewModelScope.launch {
            settingsRepository.guideNeeded.collect {
                // 只在第一次安装（本地无任何记录）时展示欢迎页
                _guideShown.value = !it
                _dataLoaded.value = true
            }
        }
        // 启动时检查更新（受「自动检查更新」开关控制；等开关加载完再决定）
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000) // 延迟2秒避免影响启动
            if (!_autoCheckUpdate.value) return@launch
            try {
                _manualUpdateCheck.value = false // 自动检查：弹窗与否看「更新提醒」
                _updateResult.value = updateChecker.checkForUpdate(_downloadSource.value)
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

    fun setDownloadConcurrency(n: Int) {
        viewModelScope.launch { settingsRepository.setDownloadConcurrency(n) }
    }

    fun setAutoOrganize(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoOrganize(enabled) }
    }

    fun setToolboxOpenInBrowser(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setToolboxOpenInBrowser(enabled) }
    }

    fun setClipboardDetect(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setClipboardDetect(enabled) }
    }

    fun setSwipeSwitchPages(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSwipeSwitchPages(enabled) }
    }

    fun setPreferredQuality(label: String) {
        viewModelScope.launch { settingsRepository.setPreferredQuality(label) }
    }

    /**
     * 从一段任意文本里挑出第一个**受支持**的分享链接（剪贴板/分享文案用）。
     * 只认抖音和小红书，返回 null 表示这段文字没什么可解析的。
     */
    fun supportedLinkIn(text: String): String? {
        if (text.isBlank()) return null
        return extractUrls(text).firstOrNull {
            parseRepository.detectPlatform(it) != Platform.UNKNOWN
        }
    }

    /**
     * 剪贴板提示专用：只认**专门适配过**的平台，不包含通用网页。
     * 否则用户复制任何一条普通链接（新闻、地图）都会被弹一张「检测到网页链接」，
     * 变成骚扰。通用抓取留给用户主动粘贴/分享。
     */
    fun clipboardLinkIn(text: String): String? =
        extractUrls(text).firstOrNull {
            val platform = parseRepository.detectPlatform(it)
            platform != Platform.UNKNOWN && platform != Platform.WEB
        }

    /** 平台显示名（给剪贴板提示卡片用）。 */
    fun platformLabelOf(url: String): String =
        parseRepository.detectPlatform(url).displayName

    /**
     * 输入框里这段文字的「已识别平台」标签；认不出链接就返回 null。
     * 任何 http(s) 链接都会识别成「网页」（通用抓取），所以这里不再有"不支持"。
     */
    fun platformNameOfInput(text: String): String? {
        val link = supportedLinkIn(text) ?: return null
        val platform = parseRepository.detectPlatform(link)
        return if (platform == Platform.UNKNOWN) null else platform.displayName
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

        // 批量解析：抽取出文本里的全部链接，只留支持的平台
        val urls = extractUrls(url).filter { parseRepository.detectPlatform(it) != Platform.UNKNOWN }
        if (urls.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "没有识别到链接，粘贴一条分享链接再试")
            return
        }

        when {
            urls.size == 1 -> parseSingleUrl(urls.first())
            else -> parseMultipleUrls(urls.take(5)) // 上限 5 条，避免刷屏式粘贴拖垮解析
        }
    }

    /** 批量解析（3.0）：多条链接逐条解析，进批量结果页挑选下载。 */
    private fun parseMultipleUrls(urls: List<String>) {
        viewModelScope.launch {
            _isBatchParsing.value = true
            _batchResults.value = urls.map { BatchParseItem(it, parseRepository.detectPlatform(it)) }
            _navigateToBatch.tryEmit(Unit)

            urls.forEachIndexed { index, url ->
                val platform = parseRepository.detectPlatform(url)
                val parsed = parseRepository.parse(url).getOrNull()
                val item = if (parsed != null) {
                    historyRepository.add(
                        HistoryItem(
                            platform = platform.name.lowercase(),
                            title = parsed.title.ifEmpty { "未命名" },
                            author = parsed.author,
                            cover = parsed.coverUrl ?: parsed.images.firstOrNull() ?: "",
                            url = url,
                            type = if (parsed.type == com.linkgrab.app.data.model.MediaType.VIDEO) "video" else "image",
                        )
                    )
                    BatchParseItem(url, platform, parsed)
                } else {
                    BatchParseItem(url, platform, error = "解析失败")
                }
                _batchResults.value = _batchResults.value.mapIndexed { i, old -> if (i == index) item else old }
            }
            // 记住首个成功项的平台，批量下载归档用
            currentPlatformLabel = _batchResults.value.firstOrNull { it.isSuccess }
                ?.platform?.displayName ?: currentPlatformLabel
            _isBatchParsing.value = false
        }
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
                url = url,
                fromCache = false,
                alreadyDownloaded = false,
            )

            // ① 缓存命中（同一链接 45 分钟内解析过）→ 直接秒开，一次网络请求都不发
            val cached = historyRepository.cachedResult(url)
            if (cached != null) {
                applyParsedResult(url, platform, cached, fromCache = true)
                return@launch
            }

            parseRepository.parse(url)
                .onSuccess { result ->
                    applyParsedResult(url, platform, result, fromCache = false)
                }
                .onFailure { e ->
                    // ② 网络失败但本地有旧缓存（哪怕过了有效期）：先给个能用的结果，
                    //    媒体直链可能已失效，但标题/封面/结构都还在，总比直接报错强
                    val stale = historyRepository.cachedResult(url, ignoreTtl = true)
                    if (stale != null) {
                        applyParsedResult(url, platform, stale, fromCache = true)
                        return@onFailure
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = friendlyParseError(e),
                    )
                    // 记下这次失败：反馈页可以一键附带，省去用户描述环境
                    FeedbackStore.recordFailure(
                        getApplication(),
                        platform.displayName,
                        url,
                        friendlyParseError(e),
                    )
                }
        }
    }

    /**
     * 解析成功的统一收尾：落历史、挂缓存、标记「已存过」，再发一次跳转事件。
     * 首次解析和缓存命中都走这里，保证两条路径行为一致。
     */
    private suspend fun applyParsedResult(
        url: String,
        platform: Platform,
        result: MediaResult,
        fromCache: Boolean,
    ) {
        val platformKey = platform.name.lowercase()
        val title = result.title.ifEmpty { "未命名" }
        currentPlatformLabel = platform.displayName // 下载归档用
        currentPlatformKey = platformKey
        currentTitle = title

        val alreadyDownloaded = historyRepository
            .findDownloadedByKey(platformKey, title) != null

        _uiState.value = _uiState.value.copy(
            isLoading = false,
            result = result,
            error = null,
            platform = platform,
            url = url,
            fromCache = fromCache,
            alreadyDownloaded = alreadyDownloaded,
        )
        // 用一次性事件驱动跳转：首页和历史页发起的解析都能到达
        _navigateToResult.tryEmit(Unit)

        historyRepository.add(
            HistoryItem(
                platform = platformKey,
                title = title,
                author = result.author,
                cover = result.coverUrl ?: result.images.firstOrNull() ?: "",
                url = url,
                type = if (result.type == com.linkgrab.app.data.model.MediaType.VIDEO) "video" else "image",
            )
        )
        if (!fromCache) {
            historyRepository.cacheResult(url, result, cacheTtlMs)
        }
    }

    /**
     * 把底层异常翻译成用户能看懂、能行动的话。
     * 以前一律弹「解析失败」，用户既不知道是网络、风控还是链接过期。
     */
    private fun friendlyParseError(e: Throwable): String {
        val raw = e.message.orEmpty().trim()
        if (raw.isBlank()) return "解析失败，请稍后重试"
        if ("不支持的链接" in raw) return raw
        val low = raw.lowercase()
        return when {
            listOf("timeout", "timed out", "超时", "connection reset", "connect", "unable to resolve", "network")
                .any { it in low } -> "网络超时或连不上。检查网络后重试，也可以换个网络"
            listOf("403", "401", "forbidden", "unauthorized", "无权", "登录")
                .any { it in low } -> "被站点拒绝了（可能需要登录或触发了风控）。可以试试用浏览器打开"
            listOf("404", "410", "not found", "不存在", "已删除", "已失效", "失效")
                .any { it in low } -> "内容不存在或已被删除"
            listOf("captcha", "verify", "验证", "风控", "shield").any { it in low } ->
                "碰上站点的人机校验了。换个网络或过一会儿再试"
            else -> raw
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
            _updateResult.value = updateChecker.checkForUpdate(_downloadSource.value)
            _isCheckingUpdate.value = false
        }
    }

    fun setDownloadSource(source: String) {
        viewModelScope.launch { settingsRepository.setDownloadSource(source) }
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
     * 统一下载入口（3.0 队列引擎）：任务入队，按 [downloadConcurrency] 并发
     * 调度，支持暂停/续传/取消；可在「下载管理」页实时查看与干预。
     *
     * [platformLabel] 用于自动归档子目录（如「抖音/20261016」），默认取最近
     * 一次成功解析的平台。
     */
    fun downloadBatch(
        context: Context,
        images: List<Pair<String, String>> = emptyList(),
        videos: List<Pair<List<String>, String>> = emptyList(),
        platformLabel: String? = currentPlatformLabel,
        motionPhotos: List<MotionPhotoDownload> = emptyList(),
        historyUrls: List<String> = listOf(_uiState.value.url),
    ) {
        val imageItems = images.filter { it.first.isNotBlank() }
        val videoItems = videos.map { (candidates, name) ->
            candidates.filter { it.isNotBlank() }.distinct() to name
        }.filter { it.first.isNotEmpty() }
        val motionItems = motionPhotos.filter { it.imageUrl.isNotBlank() && it.videoUrls.isNotEmpty() }
        if (imageItems.isEmpty() && videoItems.isEmpty() && motionItems.isEmpty()) return

        val total = imageItems.size + videoItems.size + motionItems.size
        val platform = platformLabel
        val ownerUrls = historyUrls.filter { it.isNotBlank() }.distinct()

        viewModelScope.launch {
            val batchIds = mutableListOf<Long>()
            taskMutex.withLock {
                // 列表上限 100：超出时先丢最老的已完结任务
                val finished = _downloadTasks.value.filter { it.status.isFinished }
                var kept = _downloadTasks.value
                val removeIds = finished.take((kept.size + total - 100).coerceAtLeast(0)).map { it.id }.toSet()
                kept = kept.filterNot { it.id in removeIds }
                _downloadTasks.value = kept
                imageItems.forEach { (url, filename) ->
                    val id = ++taskIdSeed
                    batchIds.add(id)
                    _downloadTasks.value = _downloadTasks.value + DownloadTaskItem(
                        id = id, isVideo = false, urls = listOf(url), filename = filename, platform = platform,
                    )
                }
                videoItems.forEach { (candidates, filename) ->
                    val id = ++taskIdSeed
                    batchIds.add(id)
                    _downloadTasks.value = _downloadTasks.value + DownloadTaskItem(
                        id = id, isVideo = true, urls = candidates, filename = filename, platform = platform,
                    )
                }
                motionItems.forEach { item ->
                    val id = ++taskIdSeed
                    batchIds.add(id)
                    _downloadTasks.value = _downloadTasks.value + DownloadTaskItem(
                        id = id, isVideo = false, urls = item.videoUrls, filename = item.filename,
                        platform = platform, motionImageUrl = item.imageUrl,
                    )
                }
            }

            _downloadState.value = DownloadState.Downloading(0)
            if (_liveUpdatesEnabled.value) {
                liveUpdatesHelper.startProgress(title = "LinkGrab", content = "准备下载...", indeterminate = true)
            }

            val semaphore = kotlinx.coroutines.sync.Semaphore(_downloadConcurrency.value)
            val ids = batchIds.toList()
            val jobs = ids.mapIndexed { index, id ->
                launch { semaphore.withPermit { runDownloadTask(context, id, fileIndex = index, fileTotal = total) } }
            }
            jobs.joinAll()

            // 聚合批次结果（保留原有 _downloadState 语义供结果页展示）
            val snapshot = taskMutex.withLock { _downloadTasks.value.filter { it.id in ids } }
            val succeeded = snapshot.count { it.status == DownloadStatus.Success }
            val pausedCount = snapshot.count { it.status == DownloadStatus.Paused }
            val firstError = snapshot.firstOrNull { it.status == DownloadStatus.Failed }?.error
            val isVideoBatch = videoItems.isNotEmpty()
            val noun = if (videoItems.isEmpty()) "图片" else "视频"
            when {
                succeeded == total -> {
                    _downloadState.value = DownloadState.Success
                    val msg = if (total > 1) "已保存 $total 个${noun}文件" else "${noun}保存成功"
                    showDownloadCompleteNotification(context, msg, isVideo = isVideoBatch)
                    // 记住「这篇存过了」：下次同一作品再解析时提示，避免重复保存
                    val firstFile = snapshot
                        .firstOrNull { it.status == DownloadStatus.Success }
                        ?.filename.orEmpty()
                    ownerUrls.forEach { ownerUrl ->
                        historyRepository.markDownloadedByUrl(ownerUrl, firstFile)
                    }
                    if (_uiState.value.url in ownerUrls) {
                        _uiState.value = _uiState.value.copy(alreadyDownloaded = true)
                    }
                }
                succeeded > 0 -> {
                    val pauseHint = if (pausedCount > 0) "；$pausedCount 个已暂停，可在下载管理继续" else ""
                    _downloadState.value = DownloadState.Error(
                        "已保存 $succeeded/$total 个文件${pauseHint}；${firstError ?: "其余下载失败"}"
                    )
                }
                pausedCount > 0 && succeeded == 0 -> {
                    _downloadState.value = DownloadState.Error("$pausedCount 个任务已暂停，可在下载管理继续")
                }
                else -> {
                    _downloadState.value = DownloadState.Error(firstError ?: "Download failed")
                    showDownloadCompleteNotification(context, "下载失败: $firstError", isVideo = isVideoBatch)
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

    /** A live image is one queue item, published only after both parts have been packaged. */
    fun downloadLivePhoto(context: Context, imageUrl: String, videoUrls: List<String>, filename: String) =
        downloadBatch(context, motionPhotos = listOf(MotionPhotoDownload(imageUrl, videoUrls, filename)))

    fun downloadImageSelection(context: Context, result: MediaResult, indices: List<Int>, asMotionPhoto: Boolean) {
        val stamp = System.currentTimeMillis()
        val images = mutableListOf<Pair<String, String>>()
        val videos = mutableListOf<Pair<List<String>, String>>()
        val motion = mutableListOf<MotionPhotoDownload>()
        indices.distinct().filter { it in result.images.indices }.forEach { i ->
            val live = result.livePhotoCandidates(i)
            if (asMotionPhoto && live.isNotEmpty()) {
                motion.add(MotionPhotoDownload(result.images[i], live, "MVIMG_LinkGrab_${stamp}_$i.jpg"))
            } else {
                images.add(result.images[i] to "LinkGrab_${stamp}_$i.jpg")
                if (live.isNotEmpty()) videos.add(live to "LinkGrab_Live_${stamp}_$i.mp4")
            }
        }
        downloadBatch(context, images = images, videos = videos, motionPhotos = motion)
    }

    // ---- 下载管理页操作 ----

    fun pauseTask(id: Long) {
        pauseFlags.add(id)
        // 尚未开跑的任务直接置为 Paused（worker 起跑时会再校验旗标）
        viewModelScope.launch {
            updateTask(id) { if (it.status == DownloadStatus.Queued) it.copy(status = DownloadStatus.Paused) else it }
        }
    }

    fun resumeTask(context: Context, id: Long) {
        pauseFlags.remove(id) // 清掉可能残留的旗标（暂停瞬间任务恰好完结等边界）
        cancelFlags.remove(id)
        viewModelScope.launch {
            updateTask(id) { if (it.status == DownloadStatus.Paused || it.status == DownloadStatus.Failed) it.copy(status = DownloadStatus.Queued, error = null) else it }
        }
        viewModelScope.launch { runDownloadTask(context, id, fileIndex = 0, fileTotal = 1) }
    }

    fun cancelTask(context: Context, id: Long) {
        viewModelScope.launch {
            val task = taskMutex.withLock { _downloadTasks.value.firstOrNull { it.id == id } } ?: return@launch
            when (task.status) {
                DownloadStatus.Queued, DownloadStatus.Downloading -> cancelFlags.add(id)
                else -> {
                    task.partialUri?.let { uri ->
                        runCatching { context.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
                    }
                    updateTask(id) { it.copy(status = DownloadStatus.Canceled, partialUri = null) }
                }
            }
        }
    }

    /** 清掉已完结（成功/失败/取消）的任务记录。 */
    fun clearFinishedTasks() {
        viewModelScope.launch {
            taskMutex.withLock {
                _downloadTasks.value = _downloadTasks.value.filter { !it.status.isFinished }
            }
        }
    }

    private suspend fun updateTask(id: Long, transform: (DownloadTaskItem) -> DownloadTaskItem) {
        taskMutex.withLock {
            _downloadTasks.value = _downloadTasks.value.map { if (it.id == id) transform(it) else it }
        }
    }

    /**
     * 队列 worker：执行单个任务，直到 Success/Failed/Paused/Canceled。
     * [fileIndex]/[fileTotal] 用于跨文件聚合进度；单独恢复的任务传 0/1。
     */
    private suspend fun runDownloadTask(context: Context, taskId: Long, fileIndex: Int, fileTotal: Int) {
        val task = taskMutex.withLock { _downloadTasks.value.firstOrNull { it.id == taskId } } ?: return
        if (task.status == DownloadStatus.Success || task.status == DownloadStatus.Canceled) return
        if (pauseFlags.remove(taskId)) {
            updateTask(taskId) { it.copy(status = DownloadStatus.Paused) }
            return
        }
        if (cancelFlags.remove(taskId)) {
            task.partialUri?.let { uri ->
                runCatching { context.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
            }
            updateTask(taskId) { it.copy(status = DownloadStatus.Canceled, partialUri = null) }
            return
        }
        updateTask(taskId) { it.copy(status = DownloadStatus.Downloading, error = null) }
        try {
            if (task.motionImageUrl != null) runMotionPhotoTask(context, task)
            else if (task.isVideo) runVideoTask(context, task, fileIndex, fileTotal)
            else runImageTask(context, task, fileIndex, fileTotal)
        } catch (e: TaskPausedSignal) {
            // 半成品保留（IS_PENDING=1 不会出现在相册），续传从这里开始
            updateTask(taskId) { it.copy(status = DownloadStatus.Paused) }
        } catch (e: TaskCanceledSignal) {
            updateTask(taskId) { it.copy(partialUri = null, status = DownloadStatus.Canceled) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 失败时丢弃半成品（与旧版行为一致），Failed 可手动重试
            task.partialUri?.let { uri ->
                runCatching { context.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
            }
            updateTask(taskId) {
                it.copy(status = DownloadStatus.Failed, partialUri = null, error = e.message ?: "下载失败")
            }
        }
    }

    private suspend fun runMotionPhotoTask(context: Context, task: DownloadTaskItem) {
        val saver = com.linkgrab.app.data.media.MotionPhotoSaver(httpClient) { buildMediaRequest(it, null) }
        saver.save(context, requireNotNull(task.motionImageUrl), task.urls, task.filename,
            organizeRelativePath(false, task.platform),
            checkpoint = { checkPauseCancel(task.id) },
            onProgress = { progress ->
                updateTask(task.id) { it.copy(progress = progress) }
                _downloadState.value = DownloadState.Downloading(progress)
            })
        updateTask(task.id) { it.copy(status = DownloadStatus.Success, progress = 100, partialUri = null) }
    }

    /**
     * 下载图片任务（3.0）：**直接落盘原始字节**，保留原始格式（jpg/png/webp），
     * 格式与扩展名按响应头自动匹配。支持暂停/续传：暂停时半成品保留在
     * MediaStore（IS_PENDING=1 不进相册），恢复时带 Range 头追加写入。
     */
    private suspend fun runImageTask(
        context: Context,
        task: DownloadTaskItem,
        fileIndex: Int,
        fileTotal: Int,
    ) = withContext(Dispatchers.IO) {
        val url = task.urls.first()
        val resolver = context.contentResolver
        var uriString: String? = task.partialUri
        var written = task.downloadedBytes

        val resumeFrom = if (uriString != null) written else 0L
        httpClient.newCall(buildMediaRequest(url, if (resumeFrom > 0) "bytes=$resumeFrom-" else null)).execute().use { resp ->
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
            val displayName = task.filename.substringBeforeLast('.') + ".$ext"

            var uri = uriString?.let { android.net.Uri.parse(it) }
            var appendMode = false
            if (uri != null && resp.code == 206) {
                appendMode = true // 服务器支持 Range，从半成品继续
            } else {
                if (uri != null) runCatching { resolver.delete(uri, null, null) } // 不支持 Range，推倒重来
                written = 0
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, organizeRelativePath(isVideo = false, platform = task.platform))
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                }
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create file")
                uriString = uri.toString()
                updateTask(task.id) { it.copy(partialUri = uriString, downloadedBytes = 0) }
            }
            val totalBytes = if (appendMode) written + body.contentLength() else body.contentLength()

            val out = resolver.openOutputStream(uri!!, if (appendMode) "wa" else "w")
                ?: throw Exception("Failed to open output stream")
            out.use { stream ->
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastEmit = 0L // 进度写节流：120ms 一次，别让每 8KB 的写入打爆重组
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    checkPauseCancel(task.id)
                    stream.write(buffer, 0, bytesRead)
                    written += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 120) {
                        lastEmit = now
                        updateTask(task.id) {
                            it.copy(downloadedBytes = written, totalBytes = totalBytes, progress = bytesPercent(written, totalBytes))
                        }
                        reportOverallProgress(fileIndex, fileTotal, written, totalBytes, "下载图片")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(uri!!, done, null, null)
            }
            updateTask(task.id) {
                it.copy(status = DownloadStatus.Success, partialUri = null, progress = 100, downloadedBytes = written, totalBytes = totalBytes)
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
     * 下载视频任务（3.0）：候选地址按序回退；暂停/续传只对当前候选生效
     * （[DownloadTaskItem.candidateIndex] 记录进度落在哪个候选上）。
     */
    private suspend fun runVideoTask(
        context: Context,
        task: DownloadTaskItem,
        fileIndex: Int,
        fileTotal: Int,
    ) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (index in task.candidateIndex until task.urls.size) {
            val url = task.urls[index]
            val isNewAttempt = (index != task.candidateIndex) || (task.partialUri == null)
            try {
                downloadVideoCandidate(
                    context, task, url, fileIndex, fileTotal,
                    resumeUri = if (isNewAttempt) null else task.partialUri,
                    resumeBytes = if (isNewAttempt) 0L else task.downloadedBytes,
                )
                return@withContext
            } catch (e: TaskPausedSignal) {
                updateTask(task.id) { it.copy(candidateIndex = index) }
                throw e
            } catch (e: TaskCanceledSignal) {
                throw e
            } catch (e: Exception) {
                // 该候选失败：丢弃半成品，从下一个候选重来
                task.partialUri?.let { uri ->
                    runCatching { context.contentResolver.delete(android.net.Uri.parse(uri), null, null) }
                }
                updateTask(task.id) { it.copy(partialUri = null, candidateIndex = 0, downloadedBytes = 0) }
                lastError = e
            }
        }
        throw lastError ?: Exception("下载失败")
    }

    private suspend fun downloadVideoCandidate(
        context: Context,
        task: DownloadTaskItem,
        url: String,
        fileIndex: Int,
        fileTotal: Int,
        resumeUri: String?,
        resumeBytes: Long,
    ) {
        val resolver = context.contentResolver
        var uriString: String? = resumeUri
        var written = resumeBytes

        httpClient.newCall(buildMediaRequest(url, if (resumeBytes > 0) "bytes=$resumeBytes-" else null)).execute().use { resp ->
            // 416 = 半成品其实已经写满（暂停时机踩在结尾），直接收尾成功
            if (resp.code == 416 && uriString != null) {
                val uri = android.net.Uri.parse(uriString!!)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    resolver.update(uri, done, null, null)
                }
                updateTask(task.id) {
                    it.copy(status = DownloadStatus.Success, partialUri = null, candidateIndex = 0, progress = 100)
                }
                return
            }
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty response")

            // 有些情况下服务端会回一个 HTML 错误页而不是视频，直接写出会得到坏文件
            val contentType = body.contentType()?.toString().orEmpty()
            if (contentType.contains("text/html")) throw Exception("服务端返回了网页而不是视频")

            var uri = uriString?.let { android.net.Uri.parse(it) }
            var appendMode = false
            if (uri != null && resp.code == 206) {
                appendMode = true
            } else {
                if (uri != null) runCatching { resolver.delete(uri, null, null) }
                written = 0
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, task.filename)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, organizeRelativePath(isVideo = true, platform = task.platform))
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }
                uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw Exception("Failed to create file")
                uriString = uri.toString()
                updateTask(task.id) { it.copy(partialUri = uriString, downloadedBytes = 0) }
            }
            val totalBytes = if (appendMode) written + body.contentLength() else body.contentLength()

            val out = resolver.openOutputStream(uri!!, if (appendMode) "wa" else "w")
                ?: throw Exception("Failed to open output stream")
            out.use { stream ->
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var lastEmit = 0L // 进度写节流：120ms 一次（与图片任务同策略）
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    checkPauseCancel(task.id)
                    stream.write(buffer, 0, bytesRead)
                    written += bytesRead
                    val now = System.currentTimeMillis()
                    if (now - lastEmit > 120) {
                        lastEmit = now
                        updateTask(task.id) {
                            it.copy(downloadedBytes = written, totalBytes = totalBytes, progress = bytesPercent(written, totalBytes))
                        }
                        reportOverallProgress(fileIndex, fileTotal, written, totalBytes, "下载视频")
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri!!, done, null, null)
            }
            updateTask(task.id) {
                it.copy(status = DownloadStatus.Success, partialUri = null, candidateIndex = 0, progress = 100, downloadedBytes = written, totalBytes = totalBytes)
            }
        }
    }

    /** 3.0 自动归档：开且知道平台 → `LinkGrab/平台/yyyyMMdd`，否则 `LinkGrab`。 */
    private fun organizeRelativePath(isVideo: Boolean, platform: String?): String {
        val base = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        return if (_autoOrganize.value && platform != null) {
            val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.CHINA).format(java.util.Date())
            "$base/LinkGrab/$platform/$day"
        } else {
            "$base/LinkGrab"
        }
    }

    private fun buildMediaRequest(url: String, range: String?): Request {
        val builder = Request.Builder().url(url).header("User-Agent", BROWSER_UA)
        // 各家 CDN 的防盗链规则不同：
        //  - 小红书：空 Referer 更容易 403
        //  - B 站 *.bilivideo.com / *.hdslb.com：必须带 bilibili.com 的 Referer
        //  - 微博 sinaimg / weibocdn：带 weibo.com
        //  - 网易云/QQ 音乐/酷我：带各自站点（部分直链会校验）
        val referer = when {
            url.contains("xhscdn.com") || url.contains("xiaohongshu.com") -> "https://www.xiaohongshu.com/"
            url.contains("bilivideo.com") || url.contains("hdslb.com") || url.contains("bilibili.com") ->
                "https://www.bilibili.com/"
            url.contains("sinaimg.cn") || url.contains("weibo.com") || url.contains("weibocdn.com") ->
                "https://weibo.com/"
            url.contains("music.163.com") || url.contains("126.net") -> "https://music.163.com/"
            url.contains("qq.com") || url.contains("qqmusic") -> "https://y.qq.com/"
            url.contains("kuwo.cn") -> "https://www.kuwo.cn/"
            else -> null
        }
        referer?.let { builder.header("Referer", it) }
        if (range != null) builder.header("Range", range)
        return builder.build()
    }

    /** 写入循环中的暂停/取消检查点：每 8KB 一次，开销可忽略。 */
    private fun checkPauseCancel(taskId: Long) {
        if (cancelFlags.remove(taskId)) throw TaskCanceledSignal()
        if (pauseFlags.remove(taskId)) throw TaskPausedSignal()
    }

    private fun bytesPercent(bytes: Long, total: Long): Int =
        if (total > 0) ((bytes * 100) / total).toInt().coerceIn(0, 99) else 0

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
    /** 本次解析的原始链接（缓存、下载标记都按它对齐）。 */
    val url: String = "",
    /** 结果来自本地缓存（同一链接 45 分钟内解析过 / 网络失败时的旧缓存兜底）。 */
    val fromCache: Boolean = false,
    /** 这篇作品之前已经保存过了（结果页据此提示「再存一份」）。 */
    val alreadyDownloaded: Boolean = false,
)

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int = 0) : DownloadState()
    data object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/** 下载队列的单个任务（3.0）。 */
enum class DownloadStatus {
    Queued, Downloading, Paused, Success, Failed, Canceled;

    /** 已到终态：可从任务列表清除。 */
    val isFinished: Boolean get() = this == Success || this == Failed || this == Canceled
}

data class DownloadTaskItem(
    val id: Long,
    val isVideo: Boolean,
    /** 视频 = 候选地址列表（按序回退）；图片 = 单条。 */
    val urls: List<String>,
    val filename: String,
    /** 平台显示名（抖音/小红书），自动归档子目录用；null = 不归档。 */
    val platform: String? = null,
    val status: DownloadStatus = DownloadStatus.Queued,
    /** 单文件进度 0-100（成功时 100）。 */
    val progress: Int = 0,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** 暂停时的半成品文件 uri（IS_PENDING=1，不进相册），续传起点。 */
    val partialUri: String? = null,
    /** 视频多候选时当前进行到的候选下标。 */
    val candidateIndex: Int = 0,
    val error: String? = null,
    /** Non-null for a JPEG + MP4 Motion Photo task. Pause/retry restarts packaging. */
    val motionImageUrl: String? = null,
)

data class MotionPhotoDownload(val imageUrl: String, val videoUrls: List<String>, val filename: String)

/** worker 内部控制流信号：不是错误，上层据此转成 Paused/Canceled 状态。 */
private class TaskPausedSignal : Exception()
private class TaskCanceledSignal : Exception()

/** 批量解析的单条结果（3.0）。 */
data class BatchParseItem(
    val url: String,
    val platform: Platform,
    val result: MediaResult? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = result != null
}
