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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.NexioCanvas
import com.linkgrab.app.ui.theme.NexioPageHeader
import com.linkgrab.app.ui.theme.NexioPanel
import com.linkgrab.app.ui.theme.nexioInk
import com.linkgrab.app.ui.theme.nexioSecondary
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight

@Composable
fun ToolboxScreen(onNavigateToVideoToAudio: () -> Unit = {}, onNavigateToImageConvert: () -> Unit = {}) {
    NexioCanvas {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                NexioPageHeader("工具", "为已经保存的内容，准备下一步。")
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    ToolRow("视频转音频", "提取音频并保存到音乐资料库", onNavigateToVideoToAudio)
                    androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                    ToolRow("图片转换", "转换 JPG、PNG、WebP 或 BMP", onNavigateToImageConvert)
                }
            }
            item { Text("更多工具会在这里出现", color = nexioSecondary(), modifier = Modifier.padding(horizontal = 6.dp)); Spacer(Modifier.padding(bottom = 100.dp)) }
        }
    }
}

@Composable
private fun ToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = nexioInk(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = nexioSecondary(), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = nexioSecondary(), modifier = Modifier.size(20.dp))
    }
}
