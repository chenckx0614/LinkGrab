package com.linkgrab.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.linkgrab.app.data.history.HistoryItem
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOSCornerMedium
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.IOSRed
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.iosGroupedBackground
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.viewmodel.MainViewModel

private fun platformName(raw: String): String = when (raw) {
    "douyin" -> "抖音"
    "xiaohongshu" -> "小红书"
    else -> raw
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit, onParseUrl: (String) -> Unit) {
    val history by viewModel.allHistory.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("all") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<List<HistoryItem>?>(null) }

    val filtered = history.filter { filter == "all" || it.platform == filter }

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    // 多选状态下系统返回键先退出多选，而不是直接离开页面
    BackHandler(enabled = selectionMode) { exitSelection() }

    IOS26Canvas {
        IOS26StickyPage(
            title = if (selectionMode) "已选 ${selected.size} 项" else "历史记录",
            onBack = { if (selectionMode) exitSelection() else onBack() },
            spacedBy = 10.dp,
            actions = {
                if (filtered.isEmpty()) return@IOS26StickyPage
                if (selectionMode) {
                    Text(
                        if (selected.size == filtered.size) "取消全选" else "全选",
                        color = iosBlue(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selected = if (selected.size == filtered.size) emptySet()
                                else filtered.map { it.id }.toSet()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (selected.isNotEmpty()) {
                        Text(
                            "删除(${selected.size})",
                            color = IOSRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { pendingDelete = filtered.filter { it.id in selected } }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                } else {
                    Text(
                        "选择",
                        color = iosBlue(),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                selectionMode = true
                                selected = emptySet()
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            },
        ) {
            item { IOS26PageHeader("历史记录", "查看和继续处理已解析的内容", topPadding = 0.dp) }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IOS26Choice("全部", filter == "all", onClick = { filter = "all" })
                    IOS26Choice("抖音", filter == "douyin", onClick = { filter = "douyin" })
                    IOS26Choice("小红书", filter == "xiaohongshu", onClick = { filter = "xiaohongshu" })
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
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
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    HistoryCard(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in selected,
                        onClick = {
                            if (selectionMode) {
                                selected = if (item.id in selected) selected - item.id
                                else selected + item.id
                            } else {
                                onParseUrl(item.url)
                            }
                        },
                        onLongClick = {
                            if (selectionMode) {
                                selected = if (item.id in selected) selected - item.id
                                else selected + item.id
                            } else {
                                selectionMode = true
                                selected = setOf(item.id)
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { items ->
        IOSDeleteConfirmDialog(
            count = items.size,
            sampleTitle = items.firstOrNull()?.title.orEmpty(),
            onConfirm = {
                viewModel.deleteHistoryItems(items)
                pendingDelete = null
                exitSelection()
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IOSCornerMedium)
            .background(iosGlass())
            .border(
                0.5.dp,
                if (selectionMode && selected) iosBlue().copy(alpha = 0.6f) else iosGlassStroke(),
                IOSCornerMedium,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) iosBlue() else iosTertiaryBackground()),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(item.cover).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(IOSCornerSmall)
                .background(iosTertiaryBackground()),
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
                "${platformName(item.platform)} · ${if (item.type == "video") "视频" else "图片"}",
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 14.sp,
            )
        }
        if (!selectionMode) {
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = iosBlue().copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 自定义删除确认弹窗：原来用 Material3 默认 AlertDialog，标题/正文/按钮的
 * 间距和圆角跟整套 iOS 26 风格不一致，看起来很「原生安卓」。
 */
@Composable
private fun IOSDeleteConfirmDialog(
    count: Int,
    sampleTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(iosGroupedBackground())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(IOSRed.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = IOSRed,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                if (count == 1) "删除这条记录？" else "删除这 $count 条记录？",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabel(),
                letterSpacing = (-0.3).sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (count == 1) sampleTitle else "包含「$sampleTitle」等",
                fontSize = 14.sp,
                color = iosSecondaryLabel(),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(IOSCornerSmall)
                        .background(iosTertiaryBackground())
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", color = iosLabel(), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(IOSCornerSmall)
                        .background(IOSRed)
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("删除", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }
        }
    }
}
