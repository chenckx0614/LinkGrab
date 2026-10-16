package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.linkgrab.app.data.model.MediaType
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.viewmodel.BatchParseItem
import com.linkgrab.app.viewmodel.MainViewModel

/**
 * 批量解析结果页（3.0）：粘贴多条链接时逐条展示解析结果，
 * 勾选后一键全部下载——每条成功的笔记按平台自动归档。
 */
@Composable
fun BatchResultScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val items by viewModel.batchResults.collectAsState()
    val isParsing by viewModel.isBatchParsing.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // 选择状态按 url 记：默认勾上解析成功的条目
    val selected = remember { mutableStateMapOf<String, Boolean>() }
    items.forEach { item ->
        if (!selected.containsKey(item.url)) selected[item.url] = item.isSuccess
    }

    val selectedItems = items.filter { selected[it.url] == true && it.isSuccess }
    IOS26Canvas {
        IOS26StickyPage(
            title = "批量解析",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader(
                    "批量解析",
                    if (isParsing) "正在解析…" else "共 ${items.size} 条链接，勾选要下载的内容。",
                    topPadding = 0.dp,
                )
            }

            items(items.size, key = { items[it].url }) { index ->
                val item = items[index]
                BatchCard(
                    item = item,
                    selected = selected[item.url] == true,
                    onToggle = {
                        if (item.isSuccess) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            selected[item.url] = !(selected[item.url] ?: false)
                        }
                    },
                )
            }

            item {
                val enabled = selectedItems.isNotEmpty() && !isParsing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (enabled) iosBlue() else iosTertiaryBackground())
                        .clickable(enabled = enabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            val images = mutableListOf<Pair<String, String>>()
                            val videos = mutableListOf<Pair<List<String>, String>>()
                            val ts = System.currentTimeMillis()
                            selectedItems.forEach { entry ->
                                val result = entry.result ?: return@forEach
                                when (result.type) {
                                    MediaType.VIDEO -> {
                                        val url = result.videoQualities.firstOrNull()?.url ?: result.videoUrl
                                        if (url != null) videos.add(listOf(url) to "LinkGrab_${ts}.mp4")
                                    }
                                    else -> result.images.forEachIndexed { i, image ->
                                        images.add(image to "LinkGrab_${ts}_$i.jpg")
                                    }
                                }
                            }
                            viewModel.downloadBatch(
                                context,
                                images = images,
                                videos = videos,
                                platformLabel = selectedItems.firstOrNull()?.platform?.displayName,
                            )
                            onBack()
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (selectedItems.isEmpty()) "选择要下载的内容"
                        else "下载所选（${selectedItems.size}）",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (enabled) androidx.compose.ui.graphics.Color.White else iosSecondaryLabel(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatchCard(item: BatchParseItem, selected: Boolean, onToggle: () -> Unit) {
    IOS26Panel(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 封面
            val cover = item.result?.coverUrl ?: item.result?.images?.firstOrNull()
            Box(
                Modifier
                    .width(64.dp)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iosTertiaryBackground()),
                contentAlignment = Alignment.Center,
            ) {
                if (cover != null) {
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(cover).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(64.dp),
                    )
                } else {
                    Text(if (item.isSuccess) "图" else "✕", fontSize = 18.sp, color = iosSecondaryLabel())
                }
            }

            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                Text(
                    item.result?.title?.ifEmpty { "未命名" } ?: item.error ?: "解析失败",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (item.isSuccess) iosLabel() else iosError(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        item.result == null -> item.url
                        item.result.type == MediaType.VIDEO -> "${item.platform.displayName} · 视频"
                        else -> "${item.platform.displayName} · ${item.result.images.size} 张图片"
                    },
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // 选择圈
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            selected -> iosBlue()
                            item.isSuccess -> iosTertiaryBackground()
                            else -> iosTertiaryBackground()
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                }
            }
        }
    }
}
