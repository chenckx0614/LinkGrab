package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOSBlue
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.ui.theme.IOSOrange
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import kotlinx.coroutines.launch

private data class GuidePage(val symbol: String, val title: String, val detail: String, val accent: Color)
private val pages = listOf(
    GuidePage("↗", "从分享开始", "复制抖音或小红书的分享链接，回到 LinkGrab 粘贴。", IOSBlue),
    GuidePage("◌", "清晰的结果", "图片和视频会以清晰的预览呈现，下载操作始终在手边。", IOSGreen),
    GuidePage("↓", "保存到设备", "内容会保存到系统相册或音乐资料库，随时可以再次查看。", IOSOrange),
)

@Composable
fun GuideScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    IOS26Canvas {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            HorizontalPager(state = pager, modifier = Modifier.weight(2f).fillMaxWidth()) { index ->
                val page = pages[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(page.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            page.symbol,
                            fontSize = 56.sp,
                            color = page.accent,
                            fontWeight = FontWeight.Light,
                        )
                    }
                    Spacer(Modifier.size(28.dp))
                    Text(
                        page.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = iosLabel(),
                        textAlign = TextAlign.Center,
                        letterSpacing = (-0.6).sp,
                    )
                    Text(
                        page.detail,
                        fontSize = 16.sp,
                        color = iosSecondaryLabel(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(20.dp),
            ) {
                pages.indices.forEach { index ->
                    Box(
                        Modifier
                            .size(if (index == pager.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pager.currentPage) iosBlue()
                                else iosSecondaryLabel().copy(alpha = 0.3f)
                            )
                    )
                }
            }
            IOS26Button(
                if (last) "开始使用" else "继续",
                { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                Modifier.fillMaxWidth(),
            )
            if (!last) {
                Text(
                    "跳过",
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.weight(0.4f))
        }
    }
}
