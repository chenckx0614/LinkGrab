package com.linkgrab.app.ui.screens

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class UpdateLogItem(
    val version: String,
    val date: String,
    val changes: List<String>,
)

private val updateLogs = listOf(
    UpdateLogItem("1.2.2", "2026-06-19", listOf("新增检查更新功能", "启动自动检测版本", "跳转Gitee下载更新")),
    UpdateLogItem("1.2.1", "2026-06-19", listOf("新增历史记录模块", "修复返回栈导航", "修复高斯模糊遮挡文字")),
    UpdateLogItem("1.2.0", "2026-06-19", listOf("v1.2 大版本：历史记录 + Room数据库")),
    UpdateLogItem("1.1.9", "2026-06-18", listOf("全面屏沉浸式适配", "版本号动态读取")),
    UpdateLogItem("1.1.8", "2026-06-18", listOf("顶栏/底栏高斯模糊", "预测返回手势开关")),
    UpdateLogItem("1.1.7", "2026-06-18", listOf("Live Updates 重构", "targetSdk 升至 36")),
    UpdateLogItem("1.1.6", "2026-06-18", listOf("Live Updates 进度通知")),
    UpdateLogItem("1.1.5", "2026-06-18", listOf("修复跟随系统开关", "图标统一大小")),
    UpdateLogItem("1.1.4", "2026-06-18", listOf("外观选项联动")),
    UpdateLogItem("1.1.3", "2026-06-18", listOf("替换社交媒体真实图标")),
    UpdateLogItem("1.1.2", "2026-06-18", listOf("导航动画优化")),
    UpdateLogItem("1.1.1", "2026-06-18", listOf("修复关于闪退", "恢复顶栏模糊")),
    UpdateLogItem("1.1.0", "2026-06-18", listOf("标题折叠动画", "Live Updates 前台服务")),
    UpdateLogItem("1.0.8", "2026-06-18", listOf("Live Updates 通知", "调试开关")),
    UpdateLogItem("1.0.7", "2026-06-18", listOf("分享接收功能", "应用图标")),
    UpdateLogItem("1.0.6", "2026-06-18", listOf("小红书改回 tools.emmmm.dev")),
    UpdateLogItem("1.0.5", "2026-06-18", listOf("修复返回暂无数据")),
    UpdateLogItem("1.0.4", "2026-06-18", listOf("修复返回白屏")),
    UpdateLogItem("1.0.3", "2026-06-18", listOf("口令自动提取链接")),
    UpdateLogItem("1.0.2", "2026-06-18", listOf("集成优创猫解析 API")),
    UpdateLogItem("1.0.1", "2026-06-18", listOf("新增 WebView 解析引擎")),
    UpdateLogItem("1.0.0", "2026-06-18", listOf("首次发布")),
)

@Composable
fun UpdateLogScreen(onBack: () -> Unit) {
    val listState = rememberLazyListState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "更新日志",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    updateLogs.forEachIndexed { index, item ->
                        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
                            Text(text = "v${item.version}  (${item.date})", style = MiuixTheme.textStyles.title2)
                            Spacer(modifier = Modifier.height(4.dp))
                            item.changes.forEach { change ->
                                Text(text = "  • $change", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                            if (index < updateLogs.size - 1) Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}
