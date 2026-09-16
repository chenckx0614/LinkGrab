package com.linkgrab.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.data.model.Platform
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS27CircularProgressIndicator
import com.linkgrab.app.ui.theme.IOS27TextField
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigateToHistory: () -> Unit = {}, initialShareText: String? = null) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(initialShareText) { initialShareText?.takeIf { it.isNotBlank() }?.let { input = it; viewModel.parseUrl(it) } }
    // 注意：不要在这里用 `if (state.result != null) onNavigateToResult()`。
    // result 是持久状态（结果页返回后仍非空），据此跳转会造成「返回首页→
    // 立刻再跳回结果页」的死循环。跳转改由 MainViewModel.navigateToResult
    // 这个一次性事件驱动，统一在 AppNavigation 里处理。

    IOS26Canvas {
        IOS26StickyPage(
            title = "LinkGrab",
            applyImePadding = true,
        ) {
            item {
                IOS26PageHeader("LinkGrab", "保存你真正想留下的内容", topPadding = 0.dp)
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "粘贴一个分享链接",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "抖音和小红书链接会自动识别",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    IOS27TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "分享链接",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = false,
                    )
                    AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            state.error.orEmpty(),
                            color = iosError(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IOS26OutlinedButton("从剪贴板粘贴", { paste(context) { input = it } }, Modifier.weight(1f))
                        IOS26Button("解析", { viewModel.parseUrl(input) }, Modifier.weight(0.72f), input.isNotBlank() && !state.isLoading)
                    }
                    if (state.isLoading) {
                        Spacer(Modifier.height(14.dp))
                        IOS27CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                val platform = when {
                    input.contains("douyin.com") -> Platform.DOUYIN
                    input.contains("xiaohongshu.com") || input.contains("xhslink.com") || input.contains("xhslink.cn") -> Platform.XIAOHONGSHU
                    else -> Platform.UNKNOWN
                }
                if (platform != Platform.UNKNOWN) {
                    Text(
                        "已识别：${platform.displayName}",
                        color = iosBlue(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 14.sp,
                    )
                }
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "最近解析",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "继续查看或再次下载已保存的内容",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    IOS26Button("打开历史记录", onNavigateToHistory, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun paste(context: Context, onPaste: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
    if (value.isBlank()) Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show() else onPaste(value)
}
