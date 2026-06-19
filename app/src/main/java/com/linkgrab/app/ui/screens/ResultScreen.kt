package com.linkgrab.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
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

    // Clear result when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearResult()
        }
    }

    // Handle download state
    LaunchedEffect(downloadState) {
        when (val state = downloadState) {
            is DownloadState.Success -> {
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
                title = "解析结果",
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        // If result is null (e.g. after clearResult), just go back
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
                                        selectedImageIndex = index
                                        showPreview = true
                                    },
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "图片 ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )

                                // Download button overlay
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
                                            MiuixTheme.colorScheme.surface.copy(alpha = 0.8f),
                                            RoundedCornerShape(18.dp),
                                        ),
                                ) {
                                    Icon(
                                        MiuixIcons.Download,
                                        contentDescription = "下载",
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Download all button
                    if (result.images.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                result.images.forEachIndexed { index, imageUrl ->
                                    val filename = "LinkGrab_${System.currentTimeMillis()}_${index}.jpg"
                                    viewModel.downloadImage(context, imageUrl, filename)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = downloadState !is DownloadState.Downloading,
                        ) {
                            if (downloadState is DownloadState.Downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                )
                            } else {
                                Text("下载全部 (${result.images.size}张)")
                            }
                        }
                    }
                }

                MediaType.VIDEO -> {
                    // Scrollable column for video
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = result.title.ifEmpty { "视频" },
                            style = MiuixTheme.textStyles.title1,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cover image - constrained height
                        result.coverUrl?.let { coverUrl ->
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(coverUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "视频封面",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant),
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Download video button
                        Button(
                            onClick = {
                                result.videoUrl?.let { url ->
                                    val filename = "LinkGrab_${System.currentTimeMillis()}.mp4"
                                    viewModel.downloadVideo(context, url, filename)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = downloadState !is DownloadState.Downloading && result.videoUrl != null,
                        ) {
                            if (downloadState is DownloadState.Downloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                )
                            } else {
                                Text("下载视频")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                MediaType.UNKNOWN -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("无法识别的内容类型")
                    }
                }
            }
        }
    }

    // Full screen image preview
    if (showPreview && result?.type == MediaType.IMAGE) {
        ImagePreviewDialog(
            images = result.images,
            initialIndex = selectedImageIndex,
            onDismiss = { showPreview = false },
            onDownload = { index ->
                val imageUrl = result.images[index]
                val filename = "LinkGrab_${System.currentTimeMillis()}_$index.jpg"
                viewModel.downloadImage(context, imageUrl, filename)
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

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(images[currentIndex])
                    .crossfade(true)
                    .build(),
                contentDescription = "预览 ${currentIndex + 1}/${images.size}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            )

            // Top bar
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
                    color = MiuixTheme.colorScheme.onBackground,
                )
                Row {
                    IconButton(onClick = { onDownload(currentIndex) }) {
                        Icon(
                            MiuixIcons.Download,
                            contentDescription = "下载",
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Text("✕", color = MiuixTheme.colorScheme.onBackground)
                    }
                }
            }

            // Bottom navigation
            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { if (currentIndex > 0) currentIndex-- }) {
                        Text("‹", color = MiuixTheme.colorScheme.onBackground)
                    }
                    Spacer(modifier = Modifier.width(24.dp))
                    Text("${currentIndex + 1}", color = MiuixTheme.colorScheme.onBackground)
                    Spacer(modifier = Modifier.width(24.dp))
                    IconButton(onClick = { if (currentIndex < images.size - 1) currentIndex++ }) {
                        Text("›", color = MiuixTheme.colorScheme.onBackground)
                    }
                }
            }
        }
    }
}
