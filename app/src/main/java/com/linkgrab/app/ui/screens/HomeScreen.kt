package com.linkgrab.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.linkgrab.app.data.model.Platform
import com.linkgrab.app.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToResult: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    initialShareText: String? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var inputUrl by remember { mutableStateOf("") }

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(initialShareText) {
        if (!initialShareText.isNullOrBlank()) {
            inputUrl = initialShareText
            viewModel.parseUrl(initialShareText)
        }
    }

    LaunchedEffect(uiState.result) {
        if (uiState.result != null) {
            onNavigateToResult()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val backdrop = rememberBlurBackdrop()
    val blurActive = backdrop != null

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "LinkGrab",
                    largeTitle = "LinkGrab",
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                val platform = when {
                    inputUrl.contains("douyin.com") || inputUrl.contains("v.douyin.com") -> Platform.DOUYIN
                    inputUrl.contains("xiaohongshu.com") || inputUrl.contains("xhslink.com") -> Platform.XIAOHONGSHU
                    else -> Platform.UNKNOWN
                }
                if (platform != Platform.UNKNOWN) {
                    Text(
                        text = "检测到: ${platform.displayName}链接",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }

            item {
                TextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "粘贴分享链接",
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val text = clipData.getItemAt(0).text?.toString() ?: ""
                            if (text.isNotEmpty()) inputUrl = text
                            else Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("从剪贴板粘贴")
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Button(
                    onClick = { viewModel.parseUrl(inputUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && inputUrl.isNotBlank(),
                ) {
                    if (uiState.isLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                    else Text("解析")
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Button(
                    onClick = { onNavigateToHistory() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("📋 历史记录")
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "使用说明", style = MiuixTheme.textStyles.title1)
                    Text(text = "1. 打开抖音/小红书，找到想要保存的视频或图片", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(text = "2. 点击分享按钮，复制链接", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(text = "3. 返回本应用，粘贴链接并点击解析", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(text = "4. 预览无水印内容，点击下载保存", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
