package com.linkgrab.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import android.view.WindowManager
import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.linkgrab.app.R
import com.linkgrab.app.data.model.MediaResult
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.ui.liquid.LiquidGlassBackButton
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenu
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenuItem
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownShadowPadding
import com.linkgrab.app.ui.theme.IOS26BlurHeader
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26HeaderHeight
import com.linkgrab.app.ui.theme.IOS26HeaderScrollThreshold
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS27CircularProgressIndicator
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosGroupedBackground
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.ui.theme.rememberBackdropLayerActive
import com.linkgrab.app.viewmodel.DownloadState
import com.linkgrab.app.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ResultScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val result = uiState.result

    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var showPreview by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf(setOf<Int>()) }
    // 右上角菜单（液态玻璃容器变换：42dp 圆按钮 ↔ 200dp 面板）
    var showMenu by remember { mutableStateOf(false) }
    val menuFraction = remember { Animatable(0f) }
    // 展开态 or 正在收起的动画中：两者都要保持 backdrop 录制层挂载
    val menuVisible = showMenu || menuFraction.value > 0.01f
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    // 保存清晰度选择（视频）：默认记住上次选的档位（按 label 匹配，比下标
    // 可靠——不同视频的档位数量不一样），没记住过就用最高档。
    // 仅影响下载的文件，不影响预览播放。
    val preferredQuality by viewModel.preferredQuality.collectAsState()
    var selectedQualityIndex by remember(result?.videoUrl, preferredQuality) {
        mutableIntStateOf(
            result?.videoQualities
                ?.indexOfFirst { it.label == preferredQuality }
                ?.takeIf { it >= 0 } ?: 0
        )
    }

    // 系统返回：先处理弹窗/多选，否则暂停播放器再退出
    BackHandler {
        when {
            showPreview -> showPreview = false
            showMenu -> showMenu = false
            selectionMode -> { selectionMode = false; selectedImages = emptySet() }
            else -> { player?.pause(); onBack() }
        }
    }

    // 保存成功：按钮 morph 成「✓ 已保存」，不再弹 Toast（反馈就在按钮上）
    var justSaved by remember { mutableStateOf(false) }
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Success -> {
                vibrateDevice(context)
                justSaved = true
                viewModel.resetDownloadState()
                kotlinx.coroutines.delay(1400)
                justSaved = false
            }
            is DownloadState.Error -> {
                Toast.makeText(context, "保存失败: ${state.message}", Toast.LENGTH_SHORT).show()
                viewModel.resetDownloadState()
            }
            is DownloadState.Downloading -> justSaved = false
            else -> {}
        }
    }

    // 转场落地后再挂载重内容，避免进场动画掉帧
    var heavyContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(240)
        heavyContentReady = true
    }

    val headerBackdrop = rememberLayerBackdrop()
    // 底栏毛玻璃专用的内容采样层。
    // 千万不能用 AppNavigation 那个 liquidBackdrop：它采样的是整个 NavHost，
    // 而结果页（含这条底栏）就在 NavHost 里 —— 底栏去采样它等于采样自己，
    // 形成循环引用，解析完成进入页面时直接崩溃。这里单独建一层，只记录
    // 内容区（Scaffold 的 content slot 不含 bottomBar），因此不会自引用。
    val contentBackdrop = rememberLayerBackdrop()
    val gridState = rememberLazyGridState()
    val videoScrollState = rememberScrollState()
    val scrollThreshold = with(LocalDensity.current) { IOS26HeaderScrollThreshold.roundToPx() }
    val headerScrolled by remember(scrollThreshold) {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 ||
                gridState.firstVisibleItemScrollOffset > scrollThreshold ||
                videoScrollState.value > scrollThreshold
        }
    }
    // 菜单展开时页面通常停在顶部（没滚动过），录制层默认是卸载的 → 玻璃采
    // 样不到任何内容。把菜单可见性并进条件，展开即强制挂载。
    val backdropLayerActive = rememberBackdropLayerActive(headerScrolled || menuVisible)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = iosBackground(),
        bottomBar = {
            result?.let { safeResult ->
                ResultBottomBar(
                    result = safeResult,
                    backdrop = contentBackdrop,
                    selectionMode = selectionMode,
                    selectedImages = selectedImages,
                    downloadState = downloadState,
                    // 这篇之前存过：主按钮变成「再存一份」，避免重复保存没提示
                    alreadyDownloaded = uiState.alreadyDownloaded,
                    justSaved = justSaved,
                    onCopy = { text ->
                        copyToClipboard(context, text)
                        Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                    },
                    onDownloadAll = { indices ->
                        val timestamp = System.currentTimeMillis()
                        when (safeResult.type) {
                            MediaType.IMAGE -> {
                                // 批量走统一队列：串行下载 + 跨文件聚合进度，
                                // 不再 N 个协程并发互相覆盖进度条
                                val imageJobs = indices.map { i ->
                                    safeResult.images[i] to "LinkGrab_${timestamp}_$i.jpg"
                                }
                                val liveJobs = indices.mapNotNull { i ->
                                    val liveCandidates = safeResult.livePhotoCandidates(i)
                                    if (liveCandidates.isNotEmpty()) liveCandidates to "LinkGrab_Live_${timestamp}_$i.mp4" else null
                                }
                                viewModel.downloadBatch(context, images = imageJobs, videos = liveJobs)
                            }
                            MediaType.VIDEO -> {
                                val quality = safeResult.videoQualities.getOrNull(selectedQualityIndex)
                                val url = quality?.url ?: safeResult.videoUrl
                                val suffix = quality?.label?.let { "_$it" }.orEmpty()
                                url?.let { viewModel.downloadVideo(context, it, "LinkGrab_${timestamp}$suffix.mp4") }
                            }
                            else -> {}
                        }
                        if (selectionMode) { selectionMode = false; selectedImages = emptySet() }
                    },
                    selectedQualityLabel = safeResult.videoQualities.getOrNull(selectedQualityIndex)?.label,
                )
            }
        },
    ) { paddingValues ->
        // 底栏专用采样层：整页常驻录制。底栏在 bottomBar slot 里，不在这棵
        // 树内，所以不会自引用。
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
            // 吸顶栏 / 右上角菜单专用的一层：只录「页面底色 + 内容」。
            // 吸顶栏与菜单都在这层之外，不会自引用；底色必须在层**内部**，
            // 否则菜单盖住的头部空白区在层里是全透明的，糊过去只能糊到空气。
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (backdropLayerActive) Modifier.layerBackdrop(headerBackdrop) else Modifier)
                    .background(iosBackground()),
            ) {
                if (result == null) {
                    if (!uiState.isLoading) {
                        LaunchedEffect(Unit) { onBack() }
                    }
                    Box(modifier = Modifier.fillMaxSize().padding(top = topInset + IOS26HeaderHeight)) {
                        ResultLoadingSkeleton()
                    }
                } else {
                    when (result.type) {
                        MediaType.IMAGE -> ImageResultBody(
                            result = result,
                            gridState = gridState,
                            heavyContentReady = heavyContentReady,
                            selectionMode = selectionMode,
                            selectedImages = selectedImages,
                            onToggleSelection = { selectionMode = !selectionMode; selectedImages = emptySet() },
                            onImageClick = { index ->
                                if (selectionMode) {
                                    selectedImages = if (selectedImages.contains(index)) selectedImages - index else selectedImages + index
                                } else {
                                    selectedImageIndex = index
                                    showPreview = true
                                }
                            },
                            onDownloadLive = { index ->
                                val liveCandidates = result.livePhotoCandidates(index)
                                if (liveCandidates.isNotEmpty()) {
                                    viewModel.downloadVideo(context, liveCandidates, "LinkGrab_Live_${System.currentTimeMillis()}_$index.mp4")
                                }
                            },
                            onDownloadImage = { index, imageUrl ->
                                viewModel.downloadImage(context, imageUrl, "LinkGrab_${System.currentTimeMillis()}_$index.jpg")
                            },
                            bottomPadding = paddingValues.calculateBottomPadding(),
                        )
                        MediaType.VIDEO -> VideoResultBody(
                            result = result,
                            scrollState = videoScrollState,
                            heavyContentReady = heavyContentReady,
                            onPlayerCreated = { player = it },
                            bottomPadding = paddingValues.calculateBottomPadding(),
                            selectedQualityIndex = selectedQualityIndex,
                            onSelectQuality = { index ->
                                selectedQualityIndex = index
                                // 记住这次选的档位，下次解析视频默认用它
                                result.videoQualities.getOrNull(index)?.label
                                    ?.let { viewModel.setPreferredQuality(it) }
                            },
                        )
                        MediaType.UNKNOWN -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("无法识别的内容类型", color = iosSecondaryLabel(), fontSize = 15.sp)
                        }
                    }
                }
            }

            IOS26BlurHeader(
                title = when {
                    result == null -> "解析结果"
                    selectionMode -> "已选 ${selectedImages.size} 项"
                    else -> "解析结果"
                },
                backdrop = headerBackdrop,
                scrolled = headerScrolled,
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = {
                    if (selectionMode) { selectionMode = false; selectedImages = emptySet() }
                    else { player?.pause(); onBack() }
                },
            )
            // 右上角菜单：与历史记录同一套液态玻璃容器变换
            if (result?.type == MediaType.IMAGE && result.images.size > 1) {
                if (menuVisible) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { showMenu = false },
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = (topInset + 5.dp - LiquidGlassDropdownShadowPadding).coerceAtLeast(0.dp))
                        // 吸顶栏水平内边距 20dp，菜单自带 24dp，补 4dp 对齐右缘
                        .offset(x = 4.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    LiquidGlassDropdownMenu(
                        show = showMenu,
                        backdrop = headerBackdrop,
                        fraction = menuFraction,
                        onDismiss = { showMenu = false },
                        triggerIcon = Icons.Default.MoreVert,
                        triggerContentDescription = "更多",
                        onExpand = { showMenu = true },
                    ) {
                        if (selectionMode) {
                            LiquidGlassDropdownMenuItem(
                                text = if (selectedImages.size == result.images.size) "取消全选" else "全选",
                                onClick = {
                                    showMenu = false
                                    selectedImages = if (selectedImages.size == result.images.size) {
                                        emptySet()
                                    } else {
                                        result.images.indices.toSet()
                                    }
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.SelectAll,
                                        contentDescription = null,
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.MenuItem),
                                    )
                                },
                            )
                            LiquidGlassDropdownMenuItem(
                                text = "完成",
                                onClick = {
                                    showMenu = false
                                    selectionMode = false
                                    selectedImages = emptySet()
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.MenuItem),
                                    )
                                },
                            )
                        } else {
                            LiquidGlassDropdownMenuItem(
                                text = "选择",
                                onClick = {
                                    showMenu = false
                                    selectionMode = true
                                    selectedImages = emptySet()
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.MenuItem),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPreview && result?.type == MediaType.IMAGE) {
        ImagePreviewDialog(
            images = result.images,
            livePhotos = result.livePhotos,
            initialIndex = selectedImageIndex,
            onDismiss = { showPreview = false },
            onDownload = { index ->
                viewModel.downloadImage(context, result.images[index], "LinkGrab_${System.currentTimeMillis()}_$index.jpg")
            },
            onDownloadLive = { index ->
                val liveCandidates = result.livePhotoCandidates(index)
                if (liveCandidates.isNotEmpty()) {
                    viewModel.downloadVideo(context, liveCandidates, "LinkGrab_Live_${System.currentTimeMillis()}_$index.mp4")
                }
            },
        )
    }
}

@Composable
private fun ImageResultBody(
    result: MediaResult,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    heavyContentReady: Boolean,
    selectionMode: Boolean,
    selectedImages: Set<Int>,
    onToggleSelection: () -> Unit,
    onImageClick: (Int) -> Unit,
    onDownloadLive: (Int) -> Unit,
    onDownloadImage: (Int, String) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset + IOS26HeaderHeight)
            .padding(horizontal = 20.dp),
    ) {
        ResultMetaCard(result = result)
        Spacer(modifier = Modifier.height(14.dp))
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = bottomPadding + 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f),
        ) {
            itemsIndexed(result.images, key = { _, imageUrl -> imageUrl }) { index, imageUrl ->
                val isLive = result.isLivePhoto(index)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(IOSCornerSmall)
                        .background(iosTertiaryBackground())
                        .clickable { onImageClick(index) },
                ) {
                    if (heavyContentReady) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                            contentDescription = "图片 ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (isLive && !selectionMode) {
                        LiveBadge(modifier = Modifier.align(Alignment.TopStart))
                    }
                    if (selectionMode) {
                        SelectionIndicator(
                            selected = selectedImages.contains(index),
                            order = selectedImages.indexOf(index).takeIf { it >= 0 }?.plus(1),
                            modifier = Modifier.align(Alignment.TopStart),
                        )
                    } else {
                        Row(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isLive) {
                                SmallRoundButton(
                                    onClick = { onDownloadLive(index) },
                                    bg = iosBlue().copy(alpha = 0.9f),
                                    content = {
                                        // 原来是 Text("▶") 文字当图标；改用"实况照片"语义的图标
                                        Icon(
                                            Icons.Default.MotionPhotosOn,
                                            contentDescription = "下载实况照片",
                                            tint = Color.White,
                                            modifier = Modifier.size(IOS26IconSize.Inline),
                                        )
                                    },
                                )
                            }
                            SmallRoundButton(
                                onClick = { onDownloadImage(index, imageUrl) },
                                bg = iosGroupedBackground().copy(alpha = 0.85f),
                                content = {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "下载",
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.Inline),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoResultBody(
    result: MediaResult,
    scrollState: androidx.compose.foundation.ScrollState,
    heavyContentReady: Boolean,
    onPlayerCreated: (ExoPlayer?) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    selectedQualityIndex: Int,
    onSelectQuality: (Int) -> Unit,
) {
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset + IOS26HeaderHeight)
            .padding(horizontal = 20.dp)
            .verticalScroll(scrollState),
    ) {
        ResultMetaCard(result = result)
        Spacer(modifier = Modifier.height(16.dp))

        result.videoUrl?.let { videoUrl ->
            val exoPlayer = remember(heavyContentReady) {
                if (!heavyContentReady) null
                else ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(videoUrl))
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
            }
            DisposableEffect(exoPlayer) {
                onPlayerCreated(exoPlayer)
                onDispose {
                    exoPlayer?.pause()
                    exoPlayer?.release()
                    onPlayerCreated(null)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(IOSCornerSmall)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx)
                                .inflate(R.layout.player_view_texture, null) as PlayerView).apply {
                                this.player = exoPlayer
                                // 关掉 Media3 自带控制器：外层要接管触摸（点一下切换控件
                                // 显隐），而且它的样式跟整套 iOS 26 语言不一致。
                                this.useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // 自定义控制层：暂停/播放、快退快进 10 秒、可拖动进度条
                    VideoControlsOverlay(player = exoPlayer, modifier = Modifier.matchParentSize())
                } else {
                    result.coverUrl?.let { coverUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                            contentDescription = "视频封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (!heavyContentReady) {
                        IOS27CircularProgressIndicator(modifier = Modifier, size = 28.dp)
                    } else {
                        PlayOverlay(modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        } ?: run {
            result.coverUrl?.let { coverUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                    contentDescription = "视频封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .clip(IOSCornerSmall)
                        .background(iosTertiaryBackground()),
                )
            }
        }

        // 保存清晰度选择（仅抖音这类提供多档画质的来源才展示）
        if (result.videoQualities.size > 1) {
            Spacer(modifier = Modifier.height(16.dp))
            IOS26Panel(Modifier.fillMaxWidth()) {
                Text(
                    "保存清晰度",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                )
                Text(
                    "预览始终播放最高画质，这里只影响下载保存的文件",
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 3.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.videoQualities.forEachIndexed { index, quality ->
                        IOS26Choice(
                            label = quality.label,
                            selected = index == selectedQualityIndex,
                            onClick = { onSelectQuality(index) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomPadding + 16.dp))
    }
}

/**
 * 给当前 Dialog 窗口开启系统级背景模糊（FLAG_BLUR_BEHIND，minSdk 31 全设备可用）。
 * Dialog 是独立窗口，backdrop 库的录制层采样不到主窗口内容，跨窗口模糊只能走系统。
 * 抽出来是为了让「需要模糊背景的弹窗」统一处理，不用各写一遍窗口配置。
 */
@Composable
private fun EnableDialogBlurBehind() {
    val view = LocalView.current
    val density = LocalDensity.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            val blurEnabled = window.windowManager.isCrossWindowBlurEnabled
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = with(density) { 24.dp.toPx().roundToInt() }
                // 系统关掉跨窗口模糊（省电模式）时加深压暗兜底
                dimAmount = if (blurEnabled) 0f else 0.55f
            }
        }
        onDispose { }
    }
}

/** 毫秒 → m:ss */
private fun formatMediaTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * 视频播放控制层：暂停/播放、快退 10 秒、快进 10 秒、可拖动进度条（带时间）。
 *
 * 为什么自己做一套：原来指望 Media3 PlayerView 的自带控制器，但外层的 Box 挂了
 * `.clickable` 用来「点一下播放/暂停」，把全部触摸都吃掉了 —— 控制器收不到点击
 * 事件，永远不显示，于是看起来就是「没有任何控件」。现在自带控制器关掉，改画
 * 这套与 iOS 26 一致的控件：点画面切换显隐，播放中 3 秒后自动隐藏，暂停时常驻。
 */
@Composable
private fun VideoControlsOverlay(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) controlsVisible = true
            }

            override fun onPlaybackStateChanged(state: Int) {
                durationMs = player.duration.takeIf { it > 0 } ?: 0L
                if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    controlsVisible = true
                }
            }
        }
        player.addListener(listener)
        isPlaying = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        durationMs = player.duration.takeIf { it > 0 } ?: 0L
        positionMs = player.currentPosition.coerceAtLeast(0L)
        onDispose { player.removeListener(listener) }
    }

    // 播放中轮询进度：200ms 一次足够顺滑，且不在组合期高频写状态
    LaunchedEffect(isPlaying, scrubbing) {
        while (isPlaying && !scrubbing) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(200L)
        }
    }
    // 播放后自动隐藏控件，暂停时保持常驻
    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000L)
            controlsVisible = false
        }
    }

    fun seekToRatio(ratio: Float) {
        if (durationMs <= 0L) return
        val target = (ratio.coerceIn(0f, 1f) * durationMs).toLong()
        player.seekTo(target)
        positionMs = target
    }

    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // 预先算好再进手势 lambda：detectDragGestures 的 lambda 作用域里直接写
    // `durationMs > 0L` 会让编译器报 compareTo 缺 operator（作用域解析问题），
    // 提到外面就正常了。
    val canSeek = durationMs > 0L
    // 暂停时始终显示控件，否则点击画面会「什么都点不出来」
    val showControls = controlsVisible || !isPlaying

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controlsVisible = !controlsVisible },
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (showControls) {
            // 底部渐变：只负责承托控件，不遮画面主体
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                        ),
                    ),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // 进度条 + 时间
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatMediaTime(positionMs),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 11.sp,
                        modifier = Modifier.width(38.dp),
                    )
                    // 进度条：点击跳转 + 拖动擦洗
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .pointerInput(durationMs) {
                                detectTapGestures { offset ->
                                    if (durationMs > 0L) {
                                        seekToRatio(offset.x / size.width)
                                        scrubbing = false
                                    }
                                }
                            }
                            .pointerInput(durationMs) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        scrubbing = true
                                        if (canSeek) seekToRatio(offset.x / size.width)
                                    },
                                    onDrag = { change, _ ->
                                        if (canSeek) seekToRatio(change.position.x / size.width)
                                    },
                                    onDragEnd = { scrubbing = false },
                                    onDragCancel = { scrubbing = false },
                                )
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.26f)),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                        Box(
                            Modifier
                                .offset(x = (progress * 100f).coerceIn(0f, 100f).dp - 6.dp)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                    Text(
                        formatMediaTime(durationMs),
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 11.sp,
                        modifier = Modifier.width(38.dp),
                        textAlign = TextAlign.End,
                    )
                }
                // 控制按钮：快退 10s / 播放暂停 / 快进 10s
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerControlButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = "快退 10 秒",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                            positionMs = player.currentPosition.coerceAtLeast(0L)
                        },
                    )
                    PlayerControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        size = 34.dp,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            player.playWhenReady = !player.playWhenReady
                            isPlaying = player.playWhenReady
                        },
                    )
                    PlayerControlButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = "快进 10 秒",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            val d = player.duration.takeIf { it > 0 } ?: 0L
                            player.seekTo((player.currentPosition + 10_000L).coerceAtMost(d))
                            positionMs = player.currentPosition.coerceAtLeast(0L)
                        },
                    )
                }
            }
        }
    }
}

/** 播放器上的圆形图标按钮（44dp 触控区，符合无障碍下限） */
@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun ResultMetaCard(result: MediaResult) {
    IOS26Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IOS26IconCapsule(
                symbol = when (result.type) {
                    MediaType.IMAGE -> "◈"
                    MediaType.VIDEO -> "▶"
                    MediaType.UNKNOWN -> "?"
                },
                tint = iosBlue(),
                size = 56.dp,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title.ifEmpty { "未命名内容" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                    letterSpacing = (-0.3).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildMetaSubtitle(result),
                    color = iosSecondaryLabel(),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun buildMetaSubtitle(result: MediaResult): String {
    val parts = mutableListOf<String>()
    if (result.source.isNotBlank()) parts.add(result.source)
    if (result.author.isNotBlank()) parts.add(result.author)
    when (result.type) {
        MediaType.IMAGE -> parts.add("${result.images.size} 张图片" + if (result.hasLivePhotos) " · ${result.livePhotos.size} 实况" else "")
        MediaType.VIDEO -> parts.add("视频")
        else -> {}
    }
    return if (parts.isEmpty()) "解析结果" else parts.joinToString(" · ")
}

@Composable
private fun ResultBottomBar(
    result: MediaResult,
    selectionMode: Boolean,
    selectedImages: Set<Int>,
    downloadState: DownloadState,
    onCopy: (String) -> Unit,
    onDownloadAll: (List<Int>) -> Unit,
    backdrop: Backdrop? = null,
    selectedQualityLabel: String? = null,
    alreadyDownloaded: Boolean = false,
    justSaved: Boolean = false,
) {
    val buttonText: String
    val buttonEnabled: Boolean
    val onPrimaryClick: () -> Unit
    val copyText: String

    when (result.type) {
        MediaType.IMAGE -> {
            val indices = if (selectionMode && selectedImages.isNotEmpty()) selectedImages.toList() else result.images.indices.toList()
            buttonText = when {
                downloadState is DownloadState.Downloading -> {
                    val progress = (downloadState as DownloadState.Downloading).progress
                    "下载中 $progress%"
                }
                selectionMode && selectedImages.isNotEmpty() -> "下载选中(${selectedImages.size})"
                alreadyDownloaded -> "再存一份(${result.images.size})"
                else -> "下载全部(${result.images.size})"
            }
            buttonEnabled = downloadState !is DownloadState.Downloading
            onPrimaryClick = { onDownloadAll(indices) }
            copyText = result.images.joinToString("\n")
        }
        MediaType.VIDEO -> {
            buttonText = if (downloadState is DownloadState.Downloading) {
                "下载中 ${(downloadState as DownloadState.Downloading).progress}%"
            } else if (selectedQualityLabel != null) {
                if (alreadyDownloaded) "再存一份 · $selectedQualityLabel" else "下载视频 · $selectedQualityLabel"
            } else if (alreadyDownloaded) {
                "再存一份"
            } else "下载视频"
            buttonEnabled = downloadState !is DownloadState.Downloading && result.videoUrl != null
            onPrimaryClick = { onDownloadAll(emptyList()) }
            copyText = result.videoUrl ?: ""
        }
        else -> {
            buttonText = "无法下载"
            buttonEnabled = false
            onPrimaryClick = {}
            copyText = ""
        }
    }

    // 毛玻璃底栏：有页面 backdrop 时取样真实模糊（内容透过来），否则退回
    // 半透明实色。注意 v2.0.7 的教训：drawBackdrop 的默认 highlight/shadow
    // 必须显式关掉，否则全宽矩形会在底边画出一条描边「线」。
    val barTint = iosBackground().copy(alpha = 0.70f)
    val barSolid = iosBackground().copy(alpha = 0.92f)
    val baseModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { androidx.compose.ui.graphics.RectangleShape },
            effects = { blur(18.dp.toPx()) },
            highlight = { null },
            shadow = { null },
            onDrawSurface = { drawRect(barTint) },
        )
    } else {
        Modifier.background(barSolid)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(baseModifier)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IOS26OutlinedButton(
                text = "复制链接",
                onClick = { onCopy(copyText) },
                modifier = Modifier.weight(1f),
                enabled = copyText.isNotBlank(),
            )
            // 保存成功后按钮"变身"成「✓ 已保存」，1.4s 后弹回（见 justSaved）
            androidx.compose.animation.AnimatedContent(
                targetState = justSaved,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                        androidx.compose.animation.scaleIn(
                            initialScale = 0.86f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = 0.55f,
                                stiffness = 700f,
                            ),
                        )) togetherWith androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(140),
                    )
                },
                modifier = Modifier.weight(1f),
                label = "saveMorph",
            ) { saved ->
                if (saved) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(iosBlue()),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "已保存",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                } else {
                    IOS26Button(
                        text = buttonText,
                        onClick = onPrimaryClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = buttonEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text("实况", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SelectionIndicator(selected: Boolean, order: Int?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) iosBlue() else Color.Black.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && order != null) {
            Text("$order", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 图片网格上的小圆形操作按钮。
 * 视觉圆仍是 34dp，但**触控目标扩到 44dp**（无障碍最小建议值）—— 原来整个
 * IconButton 只有 34dp，在网格里手指很容易点空或点到相邻卡片。
 */
@Composable
private fun SmallRoundButton(
    onClick: () -> Unit,
    bg: Color,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(bg, CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
private fun PlayOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        // 原来是 Text("▶")：文字当图标，字体渲染因设备而异，读屏还会念出"▶"。
        // 这张覆盖层是装饰性的（卡片本身可点并有自己的描述），所以描述设为 null。
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(IOS26IconSize.Display),
        )
    }
}

@Composable
private fun ImagePreviewDialog(
    images: List<String>,
    livePhotos: Map<Int, List<String>>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDownload: (Int) -> Unit,
    onDownloadLive: (Int) -> Unit,
) {
    val context = LocalContext.current
    // 玻璃按钮的采样源：把翻页图片录成 backdrop 层，按钮才能做出
    // 真实的背景模糊 + 折射，而不是画在纯色上的假玻璃
    val previewBackdrop = rememberLayerBackdrop()
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialIndex) { images.size }
    var playingLive by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val liveUrl = livePhotos[pagerState.currentPage]?.firstOrNull()

    // 翻页/关闭预览时停掉实况播放
    LaunchedEffect(pagerState.currentPage) { playingLive = false }

    val exoPlayer = remember(pagerState.currentPage, playingLive) {
        if (playingLive && liveUrl != null) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(liveUrl))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
        } else null
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer?.pause(); exoPlayer?.release() }
    }

    Dialog(onDismissRequest = onDismiss) {
        // 背景不再是一整块死黑。
        // 参考 Nexio 的 BlurBottomSheet：弹窗遮罩用「系统级模糊 + 半透明压暗」，
        // 而不是 opaque 纯色 —— 纯黑全屏就是那块「大黑片」。这里给窗口开
        // FLAG_BLUR_BEHIND（minSdk 31 全设备可用），再压一层半透明的暗色，
        // 于是背景是"被虚化且压暗的上一页"，而不是一块黑板。
        EnableDialogBlurBehind()
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 遮罩透明度在**绘制期**读取（drawBehind），不在组合期用
                // Modifier.background —— 后者会在打开动画期间引发重组（Nexio 的做法）
                .drawBehind { drawRect(Color.Black.copy(alpha = 0.82f)) },
        ) {
            // 被录进 backdrop 的内容层：只有图片/视频本体。
            // 玻璃控件本身不放进录制层，避免「玻璃采样玻璃」。
            // 控件隐藏时不录，省一次全屏离屏合成。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (controlsVisible) Modifier.layerBackdrop(previewBackdrop) else Modifier),
            ) {
                // 翻页：HorizontalPager，左右滑动切换（之前只能点箭头）
                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    key = { images[it] },
                    // 放大状态下禁用翻页，把手势让给图片拖动（观察 snapshot map，状态可响应）
                    userScrollEnabled = (previewScaleRegistry[pagerState.currentPage] ?: 1f) <= 1f,
                ) { page ->
                    ZoomablePreviewImage(
                        url = images[page],
                        contentDescription = "预览 ${page + 1}/${images.size}",
                        scaleRegistry = previewScaleRegistry,
                        pageKey = page,
                        onTap = { controlsVisible = !controlsVisible },
                    )
                }

                // 实况播放覆盖层：播放时盖住当前页
                if (playingLive && exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx)
                                .inflate(R.layout.player_view_texture, null) as PlayerView).apply {
                                this.player = exoPlayer
                                this.useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }

            if (controlsVisible) {
                // 顶部渐变只负责承托控件，不遮住图片主体。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                            start = 14.dp,
                            end = 14.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidGlassBackButton(
                        onClick = onDismiss,
                        backdrop = previewBackdrop,
                        buttonSize = 42.dp,
                        darkBackground = true,
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("图片预览", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${pagerState.currentPage + 1} / ${images.size}",
                            color = Color.White.copy(alpha = 0.66f),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.size(42.dp))
                }

                // 底部页码指示：少图用圆点，多图用数字
                if (images.size > 1) {
                    if (images.size <= 6) {
                        Row(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            images.indices.forEach { i ->
                                Box(
                                    modifier = Modifier
                                        .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(if (i == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.35f)),
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "${pagerState.currentPage + 1}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
                        )
                    }
                }

                // 底部浮动操作栏：一整块 Liquid Glass 胶囊（真背景模糊），
                // 操作项在其上做按压「生长」+ 触摸点照亮 —— 对齐 Apple
                // GlassEffectContainer 内多个 glass 按钮合并成一块玻璃的规范。
                // shape / effects 两个 lambda 必须 remember 住：drawBackdrop 的
                // ModifierNodeElement 按**引用**比较它们，每次组合新建 lambda 就会
                // 判不等 → 节点 update → 重录采样层 + 重跑一次 GPU 模糊。
                // 在预览页这种高频重组的场景（翻页、缩放）会明显掉帧（Nexio 的做法）。
                val barShape: RoundedCornerShape = remember { RoundedCornerShape(26.dp) }
                val barShapeBlock: () -> RoundedCornerShape = remember { { barShape } }
                val barEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
                    remember { { blur(10f.dp.toPx()) } }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp)
                        .drawBackdrop(
                            backdrop = previewBackdrop,
                            shape = barShapeBlock,
                            effects = barEffects,
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 0.10f)) },
                        )
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (liveUrl != null) {
                            PreviewAction(
                                label = if (playingLive) "停止" else "实况",
                                active = playingLive,
                                onClick = { playingLive = !playingLive },
                            )
                            PreviewAction(label = "保存实况", onClick = { onDownloadLive(pagerState.currentPage) })
                        }
                        PreviewAction(label = "保存图片", onClick = { onDownload(pagerState.currentPage) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewAction(
    label: String,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val touch = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = Modifier
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pos ->
                        touch.value = pos
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch { press.animateTo(1f, spring(1f, 1200f, 0.001f)) }
                        val released = try {
                            awaitRelease()
                            true
                        } catch (_: Exception) {
                            false
                        }
                        scope.launch { press.animateTo(0f, spring(0.5f, 320f, 0.001f)) }
                        if (released) onClick()
                    },
                )
            }
            .graphicsLayer {
                // Apple 规范：按压时轻微生长，松手果冻回弹
                val s = 1f + 0.08f * press.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(19.dp))
            .background(if (active) iosBlue() else Color.Transparent)
            .drawWithContent {
                drawContent()
                val p = press.value
                if (p > 0.01f) {
                    // 触摸点照亮（illumination from touch point），clip 限制在胶囊内
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.32f * p),
                                Color.Transparent,
                            ),
                            center = touch.value,
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                    )
                }
            }
            .padding(horizontal = 13.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 每页缩放状态登记表（snapshot 状态 map）：翻页容器据此决定是否允许滑动翻页。
 * 只在「放大 ⇄ 复位」切换时写入，避免捏合过程中的每帧都触发重组。
 */
private val previewScaleRegistry = androidx.compose.runtime.mutableStateMapOf<Int, Float>()

/**
 * 单张可缩放预览图：
 * - 双指捏合缩放（1x–5x），放大后可拖动查看细节，拖动范围有边界
 * - 双击在 1x / 2.5x 之间切换，双击放大时以点击位置为中心
 * - 单击切换顶部/底部控制栏显隐
 *
 * 注意：pointerInput 必须放在 graphicsLayer **之前** —— 手势坐标用未变换的
 * 屏幕空间，否则放大后拖动速度会被缩放系数放大、双击定位也会漂移。
 */
@Composable
private fun ZoomablePreviewImage(
    url: String,
    contentDescription: String,
    scaleRegistry: androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>,
    pageKey: Int,
    onTap: () -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offsetX by remember(url) { mutableFloatStateOf(0f) }
    var offsetY by remember(url) { mutableFloatStateOf(0f) }
    var containerSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    // 翻页容器需要知道本页是否处于放大状态；只在状态翻转时写注册表
    LaunchedEffect(scale) {
        val zoomed = scale > 1f
        val registered = scaleRegistry[pageKey] ?: 1f
        if ((registered > 1f) != zoomed) {
            scaleRegistry[pageKey] = if (zoomed) scale else 1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(url) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tap ->
                            if (scale > 1.5f) {
                                scale = 1f; offsetX = 0f; offsetY = 0f
                            } else {
                                val newScale = 2.5f
                                // 以点击点为中心放大：保持点击点下的图像内容原位不动
                                offsetX = tap.x - (tap.x - offsetX) * (newScale / scale)
                                offsetY = tap.y - (tap.y - offsetY) * (newScale / scale)
                                scale = newScale
                            }
                        },
                    )
                }
                .pointerInput(url) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        scale = newScale
                        if (newScale <= 1f) {
                            offsetX = 0f; offsetY = 0f
                        } else {
                            // 拖动边界：放大后最大可拖出 (scale-1) * 半屏（近似值，
                            // ContentScale.Fit 的实际图幅更小，留边无妨）
                            val maxX = (newScale - 1f) * containerSize.width / 2f
                            val maxY = (newScale - 1f) * containerSize.height / 2f
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                )
                .clipToBounds(),
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("LinkGrab", text))
}

private fun vibrateDevice(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (e: Exception) { /* ignore */ }
}
