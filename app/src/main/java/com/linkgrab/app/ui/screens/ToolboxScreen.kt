package com.linkgrab.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOSCornerXSmall
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosDisclosureColor
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight

@Composable
fun ToolboxScreen(
    onNavigateToVideoToAudio: () -> Unit = {},
    onNavigateToImageConvert: () -> Unit = {},
    onNavigateToMusic: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToFileTransfer: () -> Unit = {},
    onNavigateToPanDirect: () -> Unit = {},
) {
    IOS26Canvas {
        IOS26StickyPage(title = "工具") {
            item {
                IOS26PageHeader("工具", "为已经保存的内容，准备下一步。", topPadding = 0.dp)
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    ToolRow("音乐解析下载", "从酷我分享链接提取歌曲与歌词", onNavigateToMusic)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("视频转音频", "提取音频并保存到音乐资料库", onNavigateToVideoToAudio)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("图片转换", "转换 JPG、PNG、WebP 或 BMP", onNavigateToImageConvert)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("奶蛙消消乐", "顺便玩一局，摸鱼必备", onNavigateToGame)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("文件传输", "文叔叔 / AirPortal / 轻松传等 6 个中转站", onNavigateToFileTransfer)
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    ToolRow("网盘直链", "夸克 / UC 网盘文件一键取下载直链", onNavigateToPanDirect)
                }
            }
            item {
                Text(
                    "更多工具会在这里出现",
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun ToolRow(title: String, subtitle: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        spring(dampingRatio = 0.78f, stiffness = 680f),
        label = "toolRowScale",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(IOSCornerXSmall)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
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
            tint = iosDisclosureColor(),
            modifier = Modifier.size(IOS26IconSize.Disclosure),
        )
    }
}
