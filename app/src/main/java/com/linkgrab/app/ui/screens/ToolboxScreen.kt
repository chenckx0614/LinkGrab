package com.linkgrab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight

@Composable
fun ToolboxScreen(onNavigateToVideoToAudio: () -> Unit = {}, onNavigateToImageConvert: () -> Unit = {}) {
    IOS26Canvas {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                IOS26PageHeader("工具", "为已经保存的内容，准备下一步。")
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    ToolRow("视频转音频", "提取音频并保存到音乐资料库", onNavigateToVideoToAudio)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("图片转换", "转换 JPG、PNG、WebP 或 BMP", onNavigateToImageConvert)
                }
            }
            item {
                Text(
                    "更多工具会在这里出现",
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.padding(bottom = 100.dp))
            }
        }
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = iosLabel(),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
            )
            Text(
                subtitle,
                color = iosSecondaryLabel(),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = iosSecondaryLabel(),
            modifier = Modifier.size(20.dp),
        )
    }
}
