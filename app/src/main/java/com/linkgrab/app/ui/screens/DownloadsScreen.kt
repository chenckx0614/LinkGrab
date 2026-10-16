package com.linkgrab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.linkgrab.app.ui.theme.IOS26Canvas
import androidx.compose.foundation.background
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26ListRow
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26Switch
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.viewmodel.DownloadStatus
import com.linkgrab.app.viewmodel.DownloadTaskItem
import com.linkgrab.app.viewmodel.MainViewModel
import java.util.Locale

/**
 * 下载管理（3.0）：任务队列的实时视图与干预入口。
 * 顶部是队列设置（并发数 / 自动归档），下面是任务列表——
 * 下载中可暂停、暂停可续传（断点 Range 续写）、失败可重试。
 */
@Composable
fun DownloadsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val tasks by viewModel.downloadTasks.collectAsState()
    val concurrency by viewModel.downloadConcurrency.collectAsState()
    val autoOrganize by viewModel.autoOrganize.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    IOS26Canvas {
        IOS26StickyPage(
            title = "下载管理",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader("下载管理", "查看、暂停与恢复下载任务。", topPadding = 0.dp)
            }

            // ---- 队列设置 ----
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("同时下载", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = iosLabel())
                            Text("并发数，下一批下载生效", fontSize = 14.sp, color = iosSecondaryLabel(), modifier = Modifier.padding(top = 2.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..4).forEach { n ->
                                val active = concurrency == n
                                Text(
                                    "$n",
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    color = if (active) androidx.compose.ui.graphics.Color.White else iosLabel(),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (active) iosBlue() else iosTertiaryBackground())
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                            viewModel.setDownloadConcurrency(n)
                                        }
                                        .padding(horizontal = 13.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    IOS26ListRow(
                        "自动整理",
                        "按平台/日期归档到子文件夹",
                    ) {
                        IOS26Switch(checked = autoOrganize, onCheckedChange = { viewModel.setAutoOrganize(it) })
                    }
                }
            }

            // ---- 任务列表 ----
            if (tasks.isEmpty()) {
                item {
                    IOS26Panel(Modifier.fillMaxWidth()) {
                        Text(
                            "还没有下载任务\n在首页解析后即可下载",
                            fontSize = 15.sp,
                            color = iosSecondaryLabel(),
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(vertical = 18.dp),
                        )
                    }
                }
            } else {
                items(tasks.size, key = { tasks[it].id }) { index ->
                    val task = tasks[index]
                    TaskRow(
                        task = task,
                        onPause = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.pauseTask(task.id)
                        },
                        onResume = {
                            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.resumeTask(context, task.id)
                        },
                        onCancel = { viewModel.cancelTask(context, task.id) },
                    )
                    if (index != tasks.lastIndex) IOS26Divider(Modifier.padding(vertical = 4.dp))
                }
            }

            // ---- 清除记录 ----
            if (tasks.any { it.status.isFinished }) {
                item {
                    Text(
                        "清除已完成记录",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = iosBlue(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.clearFinishedTasks() }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: DownloadTaskItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 类型图标
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(iosTertiaryBackground()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (task.isVideo) Icons.Default.Movie else Icons.Default.Image,
                contentDescription = null,
                tint = iosSecondaryLabel(),
                modifier = Modifier.size(20.dp),
            )
        }

        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(
                task.filename,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = iosLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val statusText = when (task.status) {
                DownloadStatus.Queued -> "排队中"
                DownloadStatus.Downloading ->
                    "${task.progress}% · ${formatBytes(task.downloadedBytes)}/${if (task.totalBytes > 0) formatBytes(task.totalBytes) else "未知"}"
                DownloadStatus.Paused -> "已暂停 · ${formatBytes(task.downloadedBytes)}${if (task.totalBytes > 0) "/" + formatBytes(task.totalBytes) else ""}"
                DownloadStatus.Success -> "已完成" + if (task.platform != null) " · ${task.platform}" else ""
                DownloadStatus.Failed -> "失败：${task.error ?: "未知错误"}"
                DownloadStatus.Canceled -> "已取消"
            }
            Text(
                statusText,
                fontSize = 13.sp,
                color = when (task.status) {
                    DownloadStatus.Success -> IOSGreen
                    DownloadStatus.Failed -> iosError()
                    DownloadStatus.Downloading -> iosBlue()
                    else -> iosSecondaryLabel()
                },
                modifier = Modifier.padding(top = 2.dp),
            )
            // 细进度条
            if (task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Paused) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.82f)
                        .height(3.dp)
                        .background(iosTertiaryBackground(), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(task.progress / 100f)
                            .height(3.dp)
                            .background(iosBlue(), RoundedCornerShape(2.dp)),
                    )
                }
            }
        }

        // 操作按钮
        when (task.status) {
            DownloadStatus.Downloading, DownloadStatus.Queued -> RowButton(Icons.Default.Pause, "暂停", onPause)
            DownloadStatus.Paused -> RowButton(Icons.Default.PlayArrow, "继续", onResume)
            DownloadStatus.Failed -> RowButton(Icons.Default.Refresh, "重试", onResume)
            DownloadStatus.Canceled -> {}
            DownloadStatus.Success -> {}
        }
        if (task.status == DownloadStatus.Downloading || task.status == DownloadStatus.Queued || task.status == DownloadStatus.Paused) {
            RowButton(Icons.Default.Close, "取消", onCancel, tint = iosSecondaryLabel())
        }
    }
}

@Composable
private fun RowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = iosLabel(),
) {
    Icon(
        icon,
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(7.dp),
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 20 -> String.format(Locale.CHINA, "%.1fMB", bytes / 1048576.0)
    bytes >= 1 shl 10 -> String.format(Locale.CHINA, "%.0fKB", bytes / 1024.0)
    else -> "${bytes}B"
}
