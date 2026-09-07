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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.text.font.FontWeight
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
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.viewmodel.DownloadState
import com.linkgrab.app.viewmodel.MainViewModel

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

    DisposableEffect(Unit) {
        onDispose { viewModel.clearResult() }
    }

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
        containerColor = iosBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) "已选 ${selectedImages.size} 项" else "解析结果",
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedImages = emptySet()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (result?.type == MediaType.IMAGE && result.images.size > 1) {
                        IconButton(onClick = {
                            selectionMode = !selectionMode
                            selectedImages = emptySet()
                        }) {
                            Text(
                                if (selectionMode) "取消" else "选择",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = iosBackground().copy(alpha = 0.85f),
                    titleContentColor = iosLabel(),
                    navigationIconContentColor = iosLabel(),
                    actionIconContentColor = iosBlue(),
                ),
            )
        },
    ) { innerPadding ->
        if (result == null) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                ResultLoadingSkeleton()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            when (result.type) {
                MediaType.IMAGE -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        itemsIndexed(result.images) { index, imageUrl ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(IOSCornerSmall)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
                                            .clip(CircleShape)
                                            .background(
                                                if (selectedImages.contains(index)) iosBlue()
                                                else Color.Black.copy(alpha = 0.4f)
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (selectedImages.contains(index)) {
                                            Text(
                                                "${index + 1}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
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
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                CircleShape,
                                            ),
                                    ) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = "下载",
                                            modifier = Modifier.size(20.dp),
                                            tint = iosLabel(),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IOS26OutlinedButton(
                            text = "复制链接",
                            onClick = {
                                copyToClipboard(context, result.images.joinToString("\n"))
                                Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        )

                        IOS26Button(
                            text = if (selectionMode && selectedImages.isNotEmpty()) "下载选中(${selectedImages.size})" else "下载全部(${result.images.size})",
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
                        )
                    }
                }

                MediaType.VIDEO -> {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        IOS26Panel(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IOS26IconCapsule("▶", iosBlue())
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = result.title.ifEmpty { "视频" },
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = iosLabel(),
                                        letterSpacing = (-0.3).sp,
                                    )
                                    Text(
                                        text = "${result.source.ifEmpty { "视频" }} 解析结果",
                                        color = iosSecondaryLabel(),
                                        modifier = Modifier.padding(top = 4.dp),
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))

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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(IOSCornerSmall)
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
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .clip(IOSCornerSmall)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IOS26OutlinedButton(
                                text = "复制链接",
                                onClick = {
                                    copyToClipboard(context, result.videoUrl ?: "")
                                    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                            )

                            IOS26Button(
                                text = if (downloadState is DownloadState.Downloading) {
                                    val progress = (downloadState as DownloadState.Downloading).progress
                                    "下载中 $progress%"
                                } else "下载视频",
                                onClick = {
                                    result.videoUrl?.let { url ->
                                        viewModel.downloadVideo(context, url, "LinkGrab_${System.currentTimeMillis()}.mp4")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = downloadState !is DownloadState.Downloading && result.videoUrl != null,
                            )
                        }
                    }
                }

                MediaType.UNKNOWN -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("无法识别的内容类型", color = iosSecondaryLabel(), fontSize = 15.sp)
                    }
                }
            }
        }
    }

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

    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${currentIndex + 1} / ${images.size}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Row {
                    IconButton(onClick = { onDownload(currentIndex) }) {
                        Icon(Icons.Default.Download, contentDescription = "下载", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = Color.White, fontSize = 18.sp)
                    }
                }
            }

            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (currentIndex > 0) { currentIndex--; scale = 1f; offsetX = 0f; offsetY = 0f } }) {
                        Text("‹", color = Color.White, fontSize = 28.sp)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Text("${currentIndex + 1}", color = Color.White, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(onClick = { if (currentIndex < images.size - 1) { currentIndex++; scale = 1f; offsetX = 0f; offsetY = 0f } }) {
                        Text("›", color = Color.White, fontSize = 28.sp)
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
