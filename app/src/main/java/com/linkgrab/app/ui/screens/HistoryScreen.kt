package com.linkgrab.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.linkgrab.app.data.history.HistoryItem
import com.linkgrab.app.ui.theme.IOS26BackButton
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOSCornerMedium
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.viewmodel.MainViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit, onParseUrl: (String) -> Unit) {
    val history by viewModel.allHistory.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("all") }
    var deleteItem by remember { mutableStateOf<HistoryItem?>(null) }
    val filtered = history.filter { filter == "all" || it.platform == filter }
    IOS26Canvas {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            IOS26BackButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 16.dp),
            )
            IOS26PageHeader("历史记录", "查看和继续处理已解析的内容", topPadding = 20.dp)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IOS26Choice("全部", filter == "all", onClick = { filter = "all" })
                IOS26Choice("抖音", filter == "douyin", onClick = { filter = "douyin" })
                IOS26Choice("小红书", filter == "xiaohongshu", onClick = { filter = "xiaohongshu" })
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "还没有历史记录",
                            color = iosLabel(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp,
                        )
                        Text(
                            "解析一个链接后，它会出现在这里",
                            color = iosSecondaryLabel(),
                            modifier = Modifier.padding(top = 8.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 100.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        HistoryCard(item, onClick = { onParseUrl(item.url) }, onLongClick = { deleteItem = item })
                    }
                }
            }
        }
    }
    deleteItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteItem = null },
            title = { Text("删除这条记录？", fontWeight = FontWeight.SemiBold) },
            text = { Text(item.title) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteHistory(item); deleteItem = null }) {
                    Text("删除", color = com.linkgrab.app.ui.theme.IOSRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteItem = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IOSCornerMedium)
            .background(iosGlass())
            .border(0.5.dp, iosGlassStroke(), IOSCornerMedium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.cover).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(IOSCornerSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                maxLines = 1,
                color = iosLabel(),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = (-0.2).sp,
            )
            Text(
                "${item.platform} · ${if (item.type == "video") "视频" else "图片"}",
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 14.sp,
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = iosBlue().copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
    }
}
