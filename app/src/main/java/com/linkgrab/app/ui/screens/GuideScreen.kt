package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GuidePage(
    val emoji: String,
    val title: String,
    val description: String,
    val color: Long,
)

private val guidePages = listOf(
    GuidePage("🔗", "粘贴链接", "复制抖音或小红书的分享链接\n粘贴到输入框即可解析", 0xFF4CAF50),
    GuidePage("🖼️", "无水印预览", "解析后可预览无水印图片\n支持双指缩放和批量选择", 0xFF2196F3),
    GuidePage("🎬", "视频播放", "支持在线播放无水印视频\n可直接下载保存到本地", 0xFFFF9800),
    GuidePage("📋", "历史记录", "自动保存解析历史\n支持收藏、删除和重新解析", 0xFF9C27B0),
)

@Composable
fun GuideScreen(
    onFinished: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == guidePages.size - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // App title
        Text(
            text = "LinkGrab",
            style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "无水印媒体下载器",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val item = guidePages[page]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Emoji circle with color background
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = item.emoji, fontSize = 56.sp)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = item.title,
                    style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = item.description,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                )
            }
        }

        // Page indicator dots
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(guidePages.size) { index ->
                Box(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceVariant
                        ),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Main button
        Button(
            onClick = {
                if (isLastPage) {
                    onFinished()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (isLastPage) "开始使用" else "下一步", style = MiuixTheme.textStyles.button)
        }

        // Skip button
        if (!isLastPage) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "跳过",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFinished() }
                    .padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}
