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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.linkgrab.app.ui.theme.NexioButton
import com.linkgrab.app.ui.theme.NexioCanvas
import com.linkgrab.app.ui.theme.NexioOutlinedButton
import com.linkgrab.app.ui.theme.NexioPageHeader
import com.linkgrab.app.ui.theme.NexioPanel
import com.linkgrab.app.ui.theme.nexioInk
import com.linkgrab.app.ui.theme.nexioSecondary
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigateToResult: () -> Unit, onNavigateToHistory: () -> Unit = {}, initialShareText: String? = null) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    LaunchedEffect(initialShareText) { initialShareText?.takeIf { it.isNotBlank() }?.let { input = it; viewModel.parseUrl(it) } }
    LaunchedEffect(state.result) { if (state.result != null) onNavigateToResult() }

    NexioCanvas {
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                NexioPageHeader("LinkGrab", "保存你真正想留下的内容")
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    Text("粘贴一个分享链接", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = nexioInk())
                    Text("抖音和小红书链接会自动识别", color = nexioSecondary(), modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(
                        value = input, onValueChange = { input = it }, singleLine = false,
                        label = { Text("分享链接") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                    )
                    AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 10.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NexioOutlinedButton("从剪贴板粘贴", { paste(context) { input = it } }, Modifier.weight(1f))
                        NexioButton("解析", { viewModel.parseUrl(input) }, Modifier.weight(0.68f), input.isNotBlank() && !state.isLoading)
                    }
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.padding(top = 14.dp))
                }
            }
            item {
                val platform = when {
                    input.contains("douyin.com") -> Platform.DOUYIN
                    input.contains("xiaohongshu.com") || input.contains("xhslink.com") -> Platform.XIAOHONGSHU
                    else -> Platform.UNKNOWN
                }
                if (platform != Platform.UNKNOWN) Text("已识别：${platform.displayName}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp))
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    Text("最近解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = nexioInk())
                    Text("继续查看或再次下载已保存的内容", color = nexioSecondary(), modifier = Modifier.padding(top = 5.dp))
                    Spacer(Modifier.height(14.dp))
                    NexioButton("打开历史记录", onNavigateToHistory, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

private fun paste(context: Context, onPaste: (String) -> Unit) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val value = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
    if (value.isBlank()) Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show() else onPaste(value)
}
