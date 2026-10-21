package com.linkgrab.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linkgrab.app.data.api.QuarkDirectLink
import com.linkgrab.app.data.api.QuarkFile
import com.linkgrab.app.data.api.QuarkPan
import com.linkgrab.app.ui.theme.IOS26BlurHeader
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26HeaderHeight
import com.linkgrab.app.ui.theme.IOS26HeaderScrollThreshold
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS27CircularProgressIndicator
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网盘直链（夸克 MVP）：应用内 WebView 登录 → 浏览「我的网盘」→ 点文件拿直链 →
 * 复制 / 保存到系统下载目录。仅调公开接口，凭证只留在本机 WebView Cookie 里。
 */
@Composable
fun PanDirectScreen(
    onBack: () -> Unit,
    onOpenQuarkLogin: () -> Unit,
) {
    val context = LocalContext.current
    val pan = remember { QuarkPan() }

    var loggedIn by remember { mutableStateOf(QuarkPan.isLoggedIn()) }
    // 路径栈：空 = 根目录；每项 (fid, 名称)
    var pathStack by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var files by remember { mutableStateOf(listOf<QuarkFile>()) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf<QuarkDirectLink?>(null) }
    var fetchingLink by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }

    fun load(dir: String, targetPage: Int, replace: Boolean) {
        if (loading) return
        loading = true
        error = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val (list, more) = pan.listFiles(dir, targetPage)
                files = if (replace) list else files + list
                page = targetPage
                hasMore = more
            } catch (e: Exception) {
                error = e.message ?: "加载失败"
            }
            loading = false
        }
    }

    fun enterFolder(file: QuarkFile) {
        pathStack = pathStack + (file.fid to file.name)
        files = emptyList()
        load(file.fid, 1, replace = true)
    }

    fun goUp(): Boolean {
        if (pathStack.isEmpty()) return false
        pathStack = pathStack.dropLast(1)
        files = emptyList()
        val parent = pathStack.lastOrNull()?.first ?: ""
        load(parent, 1, replace = true)
        return true
    }

    // 登录态：进入页面 & 从 WebView 登录回来（ON_RESUME）时刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val nowLoggedIn = QuarkPan.isLoggedIn()
                if (nowLoggedIn && !loggedIn && files.isEmpty()) {
                    load("", 1, replace = true)
                }
                loggedIn = nowLoggedIn
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 首次进入已登录：直接拉根目录
    androidx.compose.runtime.LaunchedEffect(loggedIn) {
        if (loggedIn && files.isEmpty() && error == null) load("", 1, replace = true)
    }

    fun fetchLink(file: QuarkFile) {
        if (fetchingLink) return
        fetchingLink = true
        error = null
        CoroutineScope(Dispatchers.Main).launch {
            try {
                link = pan.getDirectLink(file.fid)
            } catch (e: Exception) {
                error = e.message ?: "获取直链失败"
            }
            fetchingLink = false
        }
    }

    fun copyLink() {
        val target = link ?: return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("url", target.url))
        Toast.makeText(context, "已复制直链", Toast.LENGTH_SHORT).show()
    }

    fun saveToDownloads() {
        val target = link ?: return
        if (downloading) return
        downloading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pan.download(context, target)
                withContext(Dispatchers.Main) {
                    downloading = false
                    Toast.makeText(context, "已保存到 Download/LinkGrab", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    downloading = false
                    Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val headerBackdrop = rememberLayerBackdrop()
    val listState = rememberLazyListState()
    val headerScrolled by remember {
        androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val currentDir = pathStack.lastOrNull()?.first ?: ""

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = iosBackground(),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset + IOS26HeaderHeight)
                    .layerBackdrop(headerBackdrop),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 20.dp, bottom = 60.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        IOS26IconCapsule("☁️", iosBlue())
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "网盘直链 · 夸克",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = iosLabel(),
                            letterSpacing = (-0.4).sp,
                        )
                        Text(
                            "登录后浏览自己的网盘，点文件取直链",
                            color = iosSecondaryLabel(),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                if (!loggedIn) {
                    item {
                        IOS26Panel(Modifier.fillMaxWidth()) {
                            Text(
                                "还没登录夸克网盘",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = iosLabel(),
                            )
                            Text(
                                "用应用内浏览器登录一次即可，凭证只留在本机。不破解限速，" +
                                    "只帮你拿到自己网盘文件的下载直链。",
                                color = iosSecondaryLabel(),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Spacer(Modifier.height(14.dp))
                            IOS26Button(
                                text = "打开夸克网盘登录",
                                onClick = onOpenQuarkLogin,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // 路径导航：根 / 各级文件夹名，点击回跳
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(iosTertiaryBackground().copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            if (pathStack.isNotEmpty()) {
                                Text(
                                    "‹ 返回",
                                    color = iosBlue(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { goUp() }
                                        .padding(end = 10.dp),
                                )
                            }
                            Text(
                                "全部文件" + pathStack.joinToString("") { " / ${it.second}" },
                                color = iosSecondaryLabel(),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    items(files, key = { it.fid }) { file ->
                        QuarkFileRow(
                            file = file,
                            busy = fetchingLink,
                            onClick = {
                                if (file.isFolder) enterFolder(file) else fetchLink(file)
                            },
                        )
                    }

                    if (loading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                                IOS27CircularProgressIndicator()
                            }
                        }
                    } else if (hasMore) {
                        item {
                            IOS26Button(
                                text = "加载更多",
                                onClick = { load(currentDir, page + 1, replace = false) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (files.isEmpty() && error == null) {
                        item {
                            Text(
                                "这个文件夹是空的",
                                color = iosSecondaryLabel(),
                                fontSize = 14.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }

                    error?.let { msg ->
                        item {
                            Text(
                                msg,
                                color = iosError(),
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }

                item {
                    Text(
                        "直链有时效（约数小时），过期重新点一次即可。下载/复制直链时请遵守" +
                            "网盘服务条款，请勿用于侵权内容的传播。",
                        color = iosSecondaryLabel(),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            // 直链结果浮层（底部面板）
            link?.let { target ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    IOS26Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    target.fileName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = iosLabel(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    formatSize(target.size),
                                    color = iosSecondaryLabel(),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Text(
                                "关闭",
                                color = iosSecondaryLabel(),
                                fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { link = null }
                                    .padding(start = 12.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IOS26Button(
                                text = "复制直链",
                                onClick = { copyLink() },
                                modifier = Modifier.weight(1f),
                            )
                            IOS26Button(
                                text = if (downloading) "下载中…" else "保存到下载",
                                onClick = { saveToDownloads() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            IOS26BlurHeader(
                title = "网盘直链",
                backdrop = headerBackdrop,
                scrolled = headerScrolled,
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun QuarkFileRow(file: QuarkFile, busy: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(iosTertiaryBackground().copy(alpha = 0.55f))
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            if (file.isFolder) "📁" else "📄",
            fontSize = 20.sp,
            modifier = Modifier.width(32.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                fontSize = 15.sp,
                color = iosLabel(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (file.isFolder) "文件夹" else formatSize(file.size),
                color = iosSecondaryLabel(),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!file.isFolder) {
            Text(
                if (busy) "…" else "取直链",
                color = iosBlue(),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${bytes} B" else String.format("%.1f %s", value, units[unit])
}
