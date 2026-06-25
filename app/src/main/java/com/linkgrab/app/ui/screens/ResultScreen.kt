package com.linkgrab.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.viewmodel.DownloadState
import com.linkgrab.app.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
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
    // 批量选择模式
    var selectionMode by remember { mutableStateOf(false) }
    var selectedImages by remember { mutableStateOf(setOf<Int>()) }

    DisposableEffect(Unit) {
        onDispose { viewModel.clearResult() }
    }

    // Download feedback with vibration
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = if (selectionMode) "已选 ${selectedImages.size} 项" else "解析结果",
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedImages = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
                actions = {
                    if (result?.type == MediaType.IMAGE && result.images.size > 1) {
                        IconButton(onClick = {
                            selectionMode = !selectionMode
                            selectedImages = emptySet()
                        }) {
                            Text(if (selectionMode) "取消" else "选择", style = MiuixTheme.textStyles.button)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (result == null) {
            LaunchedEffect(Unit) { onBack() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            when (result.type) {
                MediaType.IMAGE -> {
                    // Image grid
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(result.images) { index, imageUrl ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (selectionMode) {
                                            selectedImages = if (selectedImages.contains(index))
                                                selectedImages - index else selectedImages + index
                                        } else {
                                            selectedImageIndex = index
                                            showPreview = true
                                        }
                                    },
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(imageUrl).crossfade(true).build(),
                                    contentDescription = "图片 ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                if (selectionMode) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(8.dp)
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (selectedImages.contains(index)) MiuixTheme.colorScheme.primary else Color.Black.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selectedImages.contains(index)) {
                                            Text("${index + 1}", color = MiuixTheme.colorScheme.onPrimary)
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            val filename = "LinkGrab_${System.currentTimeMillis()}_${index}.jpg"
                                            viewModel.downloadImage(context, imageUrl, filename)
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .size(36.dp)
                                            .background(MiuixTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(18.dp)),
                                    ) {
                                        Icon(MiuixIcons.Download, contentDescription = "下载", modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Bottom actions
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Copy link button
                        Button(
                            onClick = {
                                copyToClipboard(context, result.images.joinToString("\n"))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("复制链接")
                        }

                        // Download selected / all
                        Button(
                            onClick = {
                                val toDownload = if (selectionMode && selectedImages.isNotEmpty()) {
                                    selectedImages.map { result.images[it] }
                                } else {
                                    result.images
                                }
                                toDownload.forEachIndexed { i, url ->
                                    val filename = "LinkGrab_${System.currentTimeMillis()}_${i}.jpg"
                                    viewModel.downloadImage(context, url, filename)
                                }
                                if (selectionMode) {
                                    selectionMode = false
                                    selectedImages = emptySet()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = downloadState !is DownloadState.Downloading,
                        ) {
                            if (downloadState is DownloadState.Downloading) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp))
                            } else {
                                Text(if (selectionMode && selectedImages.isNotEmpty()) "下载选中(${selectedImages.size})" else "下载全部(${result.images.size})")
                            }
                        }
                    }
                }

                MediaType.VIDEO -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = result.title.ifEmpty { "视频" }, style = MiuixTheme.textStyles.title1)
                        Spacer(modifier = Modifier.height(12.dp))

                        result.videoUrl?.let { videoUrl ->
                            val exoPlayer = remember {
                                ExoPlayer.Builder(context).build().apply {
                                    setMediaItem(MediaItem.fromUri(videoUrl))
                                    repeatMode = Player.REPEAT_MODE_ONE
                                    prepare()
                                }
                            }
                            DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

                            Box(
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black)
                                    .clickable { exoPlayer.playWhenReady = !exoPlayer.playWhenReady },
                                contentAlignment = Alignment.Center,
                            ) {
                                AndroidView(
                                    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true; showController() } },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        } ?: run {
                            result.coverUrl?.let { coverUrl ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(coverUrl).crossfade(true).build(),
                                    contentDescription = "视频封面",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                        .clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Copy + Download
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                copyToClipboard(context, result.videoUrl ?: "")
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.weight(1f)) { Text("复制链接") }

                            Button(onClick = {
                                result.videoUrl?.let { url ->
                                    viewModel.downloadVideo(context, url, "LinkGrab_${System.currentTimeMillis()}.mp4")
                                }
                            }, modifier = Modifier.weight(1f),
                                enabled = downloadState !is DownloadState.Downloading && result.videoUrl != null) {
                                if (downloadState is DownloadState.Downloading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                                else Text("下载视频")
                            }
                        }
                    }
                }

                MediaType.UNKNOWN -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法识别的内容类型")
                    }
                }
            }
        }
    }

    // Image preview with zoom
    if (showPreview && result?.type == MediaType.IMAGE) {
        ImagePreviewDialog(
            images = result.images,
            initialIndex = selectedImageIndex,
            onDismiss = { showPreview = false },
            onDownload = { index ->
                viewModel.downloadImage(context, result.images[index], "LinkGrab_${System.currentTimeMillis()}_$index.jpg")
            },
        )
    }
}

@Composable
private fun ImagePreviewDialog(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDownload: (Int) -> Unit,
) {
    val context = LocalContext.current
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset zoom when changing image
    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(images[currentIndex]).crossfade(true).build(),
                contentDescription = "预览 ${currentIndex + 1}/${images.size}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )

            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "${currentIndex + 1} / ${images.size}", color = Color.White)
                Row {
                    IconButton(onClick = { onDownload(currentIndex) }) {
                        Icon(MiuixIcons.Download, contentDescription = "下载", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color.White)
                    }
                }
            }

            // Bottom navigation
            if (images.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (currentIndex > 0) { currentIndex--; scale = 1f; offsetX = 0f; offsetY = 0f } }) {
                        Text("‹", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Text("${currentIndex + 1}", color = Color.White)
                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(onClick = { if (currentIndex < images.size - 1) { currentIndex++; scale = 1f; offsetX = 0f; offsetY = 0f } }) {
                        Text("›", color = Color.White)
                    }
                }
            }
        }
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
