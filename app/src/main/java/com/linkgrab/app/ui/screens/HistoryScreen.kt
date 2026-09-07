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
import com.linkgrab.app.ui.theme.NexioCanvas
import com.linkgrab.app.ui.theme.NexioChoice
import com.linkgrab.app.ui.theme.NexioPageHeader
import com.linkgrab.app.ui.theme.NexioPurple
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.linkgrab.app.ui.theme.nexioInk
import com.linkgrab.app.ui.theme.nexioSecondary
import com.linkgrab.app.viewmodel.MainViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit, onParseUrl: (String) -> Unit) {
    val history by viewModel.allHistory.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("all") }
    var deleteItem by remember { mutableStateOf<HistoryItem?>(null) }
    val filtered = history.filter { filter == "all" || it.platform == filter }
    NexioCanvas {
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                NexioPageHeader("历史记录", "查看和继续处理已解析的内容")
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回", tint = nexioInk()) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NexioChoice("全部", filter == "all", onClick = { filter = "all" })
                NexioChoice("抖音", filter == "douyin", onClick = { filter = "douyin" })
                NexioChoice("小红书", filter == "xiaohongshu", onClick = { filter = "xiaohongshu" })
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("还没有历史记录", color = nexioInk(), fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("解析一个链接后，它会出现在这里", color = nexioSecondary(), modifier = Modifier.padding(top = 8.dp))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
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
            title = { Text("删除这条记录？") },
            text = { Text(item.title) },
            confirmButton = { TextButton(onClick = { viewModel.deleteHistory(item); deleteItem = null }) { Text("删除", color = Color.Red) } },
            dismissButton = { TextButton(onClick = { deleteItem = null }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val surface = if (isInDarkTheme()) Color(0xFF23232A).copy(alpha = 0.78f) else Color.White.copy(alpha = 0.78f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.cover).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(item.title, maxLines = 1, color = nexioInk(), fontWeight = FontWeight.SemiBold)
            Text("${item.platform} · ${if (item.type == "video") "视频" else "图片"}", color = nexioSecondary(), modifier = Modifier.padding(top = 5.dp))
        }
        Icon(Icons.Default.ChevronRight, null, tint = NexioPurple.copy(alpha = 0.7f))
    }
}
