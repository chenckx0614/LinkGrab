package com.linkgrab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ToolboxScreen(
    onNavigateToVideoToAudio: () -> Unit = {},
    onNavigateToImageConvert: () -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "工具箱",
                largeTitle = "工具箱",
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text(
                    text = "媒体工具",
                    style = MiuixTheme.textStyles.title1,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    ToolboxItem(
                        title = "视频转音频",
                        description = "从视频中提取音频，保存为 M4A 格式",
                        onClick = onNavigateToVideoToAudio,
                    )
                    ToolboxItem(
                        title = "图片转格式",
                        description = "转换图片格式：JPG / PNG / WebP / BMP",
                        onClick = onNavigateToImageConvert,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            item {
                Text(
                    text = "更多功能即将推出...",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun ToolboxItem(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MiuixTheme.textStyles.title2)
            Text(
                text = description,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Icon(
            MiuixIcons.ChevronForward,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
