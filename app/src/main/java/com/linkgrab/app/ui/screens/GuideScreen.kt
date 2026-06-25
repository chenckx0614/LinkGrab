package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.linkgrab.app.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class GuidePage(val emoji: String, val title: String, val description: String)

private val guidePages = listOf(
    GuidePage("🔗", "粘贴链接", "复制抖音或小红书的分享链接\n粘贴到输入框即可解析"),
    GuidePage("🖼️", "无水印预览", "解析后可预览无水印图片\n支持双指缩放和批量选择"),
    GuidePage("🎬", "视频播放", "支持在线播放无水印视频\n可直接下载保存到本地"),
    GuidePage("📋", "历史记录", "自动保存解析历史\n支持收藏、删除和重新解析"),
)

@Composable
fun GuideScreen(
    onFinished: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { guidePages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = guidePages[page].emoji, style = MiuixTheme.textStyles.title1)
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = guidePages[page].title, style = MiuixTheme.textStyles.title1)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = guidePages[page].description,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Page indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(guidePages.size) { index ->
                Spacer(
                    modifier = Modifier
                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (pagerState.currentPage == index) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceVariant)
                )
                if (index < guidePages.size - 1) Spacer(modifier = Modifier.width(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (pagerState.currentPage < guidePages.size - 1) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (pagerState.currentPage < guidePages.size - 1) "下一步" else "开始使用")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pagerState.currentPage < guidePages.size - 1) {
            Button(
                onClick = { onFinished() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("跳过")
            }
        }
    }
}
