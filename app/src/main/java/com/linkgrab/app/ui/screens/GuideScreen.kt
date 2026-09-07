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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.linkgrab.app.ui.theme.NexioButton
import com.linkgrab.app.ui.theme.NexioCanvas
import com.linkgrab.app.ui.theme.NexioPurple
import com.linkgrab.app.ui.theme.nexioInk
import com.linkgrab.app.ui.theme.nexioSecondary
import kotlinx.coroutines.launch

private data class GuidePage(val symbol: String, val title: String, val detail: String, val accent: Color)
private val pages = listOf(
    GuidePage("↗", "从分享开始", "复制抖音或小红书的分享链接，回到 LinkGrab 粘贴。", NexioPurple),
    GuidePage("◌", "清晰的结果", "图片和视频会以清晰的预览呈现，下载操作始终在手边。", Color(0xFF7C4DFF)),
    GuidePage("↓", "保存到设备", "内容会保存到系统相册或音乐资料库，随时可以再次查看。", Color(0xFFFF7043)),
)

@Composable
fun GuideScreen(onFinished: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    NexioCanvas {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(0.55f))
            HorizontalPager(state = pager, modifier = Modifier.weight(2f).fillMaxWidth()) { index ->
                val page = pages[index]
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.size(132.dp).clip(CircleShape).background(page.accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                        Text(page.symbol, fontSize = 64.sp, color = page.accent, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.size(30.dp))
                    Text(page.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = nexioInk(), textAlign = TextAlign.Center)
                    Text(page.detail, fontSize = 16.sp, color = nexioSecondary(), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(22.dp)) {
                pages.indices.forEach { index -> Box(Modifier.size(if (index == pager.currentPage) 9.dp else 7.dp).clip(CircleShape).background(if (index == pager.currentPage) NexioPurple else nexioSecondary().copy(alpha = 0.35f))) }
            }
            NexioButton(if (last) "开始使用" else "继续", { if (last) onFinished() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, Modifier.fillMaxWidth())
            if (!last) Text("跳过", color = nexioSecondary(), modifier = Modifier.padding(18.dp))
            Spacer(Modifier.weight(0.35f))
        }
    }
}
