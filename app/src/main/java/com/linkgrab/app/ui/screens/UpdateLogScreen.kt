package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

data class UpdateLogItem(val version: String, val date: String, val changes: List<String>)

private val updateLogs = listOf(
    UpdateLogItem("1.8.3", "2026-09-07", listOf("基于 iOS 26 Liquid Glass 设计规范重构全局 UI", "统一系统蓝主题色、更通透的玻璃面板、更精致的圆角与阴影", "优化页面转场动画与按钮按压反馈，提升整体流畅度")),
    UpdateLogItem("1.8.2", "2026-09-07", listOf("基于 NexioSchedule / HyperOS 4 风格重构全局 UI", "引入紫色主题、液态玻璃面板、Squircle 圆角与深色模式配色", "统一首页、结果页、设置页、历史记录、工具箱和引导页视觉")),
    UpdateLogItem("1.8.1", "2026-09-01", listOf("按 Backdrop 官方 Glass Bottom Bar 教程补齐共享 LayerBackdrop 采样结构，增强底栏背景内容的实时折射效果")),
    UpdateLogItem("1.8.0", "2026-08-20", listOf("彻底移除 Miuix，主题、图标、基础控件和模糊兼容层全部迁移到 AndroidX Compose 与 Liquid Glass")),
    UpdateLogItem("1.7.9", "2026-08-20", listOf("将图片转换、视频转音频和解析结果页面的基础控件迁移为 Material 3 Apple 化组件，移除旧 Miuix Scaffold 与 TopAppBar")),
    UpdateLogItem("1.7.8", "2026-08-20", listOf("恢复设置页的跟随系统、浅色和深色手动选择")),
    UpdateLogItem("1.7.7", "2026-08-20", listOf("提升液态玻璃底栏的可视对比度，优化白色基底、蓝色选中态、高光与阴影层次")),
    UpdateLogItem("1.7.6", "2026-08-20", listOf("标题与状态栏增加安全留白，底栏迁移液态玻璃选中胶囊的弹性滑动和折射动画")),
    UpdateLogItem("1.7.5", "2026-08-20", listOf("统一巡检各页面间距、标题留白、内容边距和结果页网格布局，优化小屏设备的可读性与底部避让")),
    UpdateLogItem("1.7.4", "2026-08-20", listOf("接入 AndroidLiquidGlass Backdrop 2.0.0，重构悬浮底栏的液态玻璃背景、选中胶囊和按压反馈")),
    UpdateLogItem("1.7.3", "2026-08-13", listOf("重做悬浮底部导航选中态、按压反馈和胶囊展开动画")),
    UpdateLogItem("1.7.2", "2026-08-13", listOf("恢复并重做关于页面", "保留版本信息、更新日志入口和服务说明")),
    UpdateLogItem("1.7.1", "2026-08-13", listOf("恢复设置页检查更新入口与状态反馈", "保留所有原有设置和更新检查功能")),
    UpdateLogItem("1.7.0", "2026-08-13", listOf("历史记录和更新日志切换为纯 Compose Apple 布局", "统一内容层卡片、空状态和删除确认交互")),
    UpdateLogItem("1.6.9", "2026-08-13", listOf("Apple 内容层与控制层布局重构", "玻璃效果收敛到导航与控制层")),
    UpdateLogItem("1.6.8", "2026-08-13", listOf("首页与导航切换为纯 Compose Apple 风格")),
    UpdateLogItem("1.6.7", "2026-08-13", listOf("优化小红书图片解析等待策略")),
    UpdateLogItem("1.6.5", "2026-08-13", listOf("修复 Android 16 Live Updates 通知")),
)

@Composable
fun UpdateLogScreen(onBack: () -> Unit) {
    IOS26Canvas {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { IOS26PageHeader("更新日志", "LinkGrab 的每一次改进。") }
            items(updateLogs.size) { index ->
                val item = updateLogs[index]
                IOS26Panel {
                    Text(
                        "v${item.version}",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        item.date,
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 3.dp),
                        fontSize = 14.sp,
                    )
                    item.changes.forEach {
                        Text(
                            "•  $it",
                            color = iosSecondaryLabel(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            item { Spacer(Modifier.padding(bottom = 100.dp)) }
        }
    }
}
