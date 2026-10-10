package com.linkgrab.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
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
import com.linkgrab.app.ui.theme.IOS26BlurHeader
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26HeaderHeight
import com.linkgrab.app.ui.theme.IOS26HeaderScrollThreshold
import com.linkgrab.app.ui.theme.IOS26IconCapsule
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
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    // 保存清晰度选择（视频）：默认最高档，仅影响下载的文件，不影响预览播放
    var selectedQualityIndex by remember(result?.videoUrl) { mutableIntStateOf(0) }

    // 系统返回：先处理弹窗/多选，否则暂停播放器再退出
    BackHandler {
        when {
            showPreview -> showPreview = false
            selectionMode -> { selectionMode = false; selectedImages = emptySet() }
            else -> { player?.pause(); onBack() }
        }
    }

    // Toast 下载状态
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Success -> {
                vibrateDevice(context)
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                viewModel.resetDownloadState()
            }
            is DownloadState.Error -> {
                Toast.makeText(context, "保存失败: ${state.message}", Toast.LENGTH_SHORT).show()
                viewModel.resetDownloadState()
            }
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
    val backdropLayerActive = rememberBackdropLayerActive(headerScrolled)
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
        // 挂上内容采样层：记录网格/视频区域，供底栏毛玻璃取样。
        // 底栏在 bottomBar slot 里，不在这棵树内，所以不会自引用。
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop)) {
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
                        backdropLayerActive = backdropLayerActive,
                        headerBackdrop = headerBackdrop,
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
                        backdropLayerActive = backdropLayerActive,
                        headerBackdrop = headerBackdrop,
                        onPlayerCreated = { player = it },
                        bottomPadding = paddingValues.calculateBottomPadding(),
                        selectedQualityIndex = selectedQualityIndex,
                        onSelectQuality = { selectedQualityIndex = it },
                    )
                    MediaType.UNKNOWN -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法识别的内容类型", color = iosSecondaryLabel(), fontSize = 15.sp)
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
                actions = {
                    if (result?.type == MediaType.IMAGE && result.images.size > 1) {
                        Text(
                            if (selectionMode) "完成" else "选择",
                            color = iosBlue(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectionMode = !selectionMode; selectedImages = emptySet() }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                },
            )
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
    backdropLayerActive: Boolean,
    headerBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
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
                .weight(1f)
                .then(if (backdropLayerActive) Modifier.layerBackdrop(headerBackdrop) else Modifier),
        ) {
            itemsIndexed(result.images, key = { index, _ -> index }) { index, imageUrl ->
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
                                    content = { Text("▶", color = Color.White, fontSize = 13.sp) },
                                )
                            }
                            SmallRoundButton(
                                onClick = { onDownloadImage(index, imageUrl) },
                                bg = iosGroupedBackground().copy(alpha = 0.85f),
                                content = {
                                    Icon(Icons.Default.Download, contentDescription = "下载", modifier = Modifier.size(18.dp), tint = iosLabel())
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
    backdropLayerActive: Boolean,
    headerBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    onPlayerCreated: (ExoPlayer?) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    selectedQualityIndex: Int,
    onSelectQuality: (Int) -> Unit,
) {
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var playing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topInset + IOS26HeaderHeight)
            .padding(horizontal = 20.dp)
            .then(if (backdropLayerActive) Modifier.layerBackdrop(headerBackdrop) else Modifier)
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
                    .background(Color.Black)
                    .clickable { exoPlayer?.let { it.playWhenReady = !it.playWhenReady; playing = it.playWhenReady } },
                contentAlignment = Alignment.Center,
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            (android.view.LayoutInflater.from(ctx)
                                .inflate(R.layout.player_view_texture, null) as PlayerView).apply {
                                this.player = exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    result.coverUrl?.let { coverUrl ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(coverUrl).crossfade(false).build(),
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
                "下载视频 · $selectedQualityLabel"
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
            IOS26Button(
                text = buttonText,
                onClick = onPrimaryClick,
                modifier = Modifier.weight(1f),
                enabled = buttonEnabled,
            )
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

@Composable
private fun SmallRoundButton(
    onClick: () -> Unit,
    bg: Color,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(34.dp).background(bg, CircleShape),
    ) { content() }
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
        Text("▶", color = Color.White, fontSize = 22.sp)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
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
                    LiquidPreviewButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("‹", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
                    }
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

                // 底部浮动操作栏：比散落的 IconButton 更容易发现，也不会和页码重叠。
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 18.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.14f))
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
    LiquidPreviewButton(
        onClick = onClick,
        shape = RoundedCornerShape(19.dp),
        pressedScale = 0.92f,
        container = if (active) iosBlue() else Color.White.copy(alpha = 0.14f),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * iOS 27 液态按压按钮（预览页专用）：
 * - 按下整钮跟手压缩，指尖位置浮现径向高光（液态玻璃受压折射的手感来源）
 * - 松手用欠阻尼弹簧回弹，带一次轻微过冲的「果冻感」
 * - 压缩与高光都在 draw 阶段读 Animatable，动画全程零重组
 *
 * pointerInput 放在 graphicsLayer 之前：手势坐标用未缩放空间，
 * 不会因为按钮压缩而漂移。
 */
@Composable
private fun LiquidPreviewButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    pressedScale: Float = 0.87f,
    container: Color = Color.White.copy(alpha = 0.14f),
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    val touch = remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = { pos ->
                        touch.value = pos
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        scope.launch {
                            press.animateTo(1f, spring(dampingRatio = 1f, stiffness = 900f))
                        }
                        var released = true
                        try {
                            awaitRelease()
                        } catch (_: Exception) {
                            released = false
                        }
                        // 欠阻尼弹簧：回弹带过冲
                        scope.launch {
                            press.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 340f))
                        }
                        if (released) onClick()
                    },
                )
            }
            .graphicsLayer {
                val s = 1f - (1f - pressedScale) * press.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(container)
            .drawWithContent {
                drawContent()
                val p = press.value
                if (p > 0.001f) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // 指尖高光：中心向按压点偏移、半径随压力扩大，像玻璃被压出液坑
                    val t = touch.value
                    val hx = cx + (t.x - cx) * 0.45f * p
                    val hy = cy + (t.y - cy) * 0.45f * p
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.30f * p),
                                Color.White.copy(alpha = 0.08f * p),
                                Color.Transparent,
                            ),
                            center = androidx.compose.ui.geometry.Offset(hx, hy),
                            radius = size.minDimension * (0.55f + 0.45f * p),
                        ),
                    )
                    // 边缘受压亮环：越压越亮，模拟液体被挤向边缘
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.White.copy(alpha = 0.22f * p),
                            ),
                            center = androidx.compose.ui.geometry.Offset(cx, cy),
                            radius = size.minDimension / 2f,
                        ),
                    )
                }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
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
