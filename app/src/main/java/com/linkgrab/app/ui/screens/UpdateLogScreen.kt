package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

data class UpdateLogItem(val version: String, val date: String, val changes: List<String>)

private val updateLogs = listOf(
    UpdateLogItem("2.0.3", "2026-09-08", listOf(
        "修复页面未滚动时顶部就出现一块带硬边的「遮罩」：原因是吸顶头的 vibrancy 色彩滤镜（饱和度 1.5x）未随滚动淡出",
        "吸顶模糊改为滚动超过约 56dp 后才渐入，模糊区底部与页面底色无缝衔接，无分割线",
    )),
    UpdateLogItem("2.0.2", "2026-09-08", listOf(
        "修复小红书解析：补全 xhslink.cn 短链识别、短链自动展开，并加固 __INITIAL_STATE__ 中 undefined 的归一化",
        "小红书链接过期或缺少安全令牌时，改为提示「请在小红书 App 中重新分享」而不是直接报解析失败",
        "小红书视频解析新增 h265/av1 编码兜底，图集图片补充 urlPre 回退与实况照片识别",
        "抖音图文（图集）解析增强：优先取无水印原图 download_url_list，支持更多返回结构",
        "抖音短链解析新增多级兜底（移动端/桌面端 UA、JS 跳转页面），图文与视频自动适配接口参数",
        "顶部导航栏去掉分割线：改为下滑时渐变模糊过渡，向下自然消散",
        "重做页面转场：返回动画改为 iOS 视差推拉，并补齐页面底色，彻底消除切换白屏",
    )),
    UpdateLogItem("2.0.1", "2026-09-08", listOf(
        "修复抖音解析：分享页 SSR 字段下线导致解析失败，切换至 slidesinfo 专用接口，视频与图文均恢复",
        "修复小红书新版 xhslink.cn 短链无法识别的问题",
        "全部页面升级为 iOS 26 吸顶导航栏：返回按钮与标题固定顶部，滚动时呈现实时模糊遮罩",
        "修复底栏点击无选中动画的问题（仅按住滑动才生效）",
        "修复底部标签反复点击导致页面栈堆积、返回时概率白屏卡住的问题",
        "底栏尺寸与排版微调，标签切换改用状态保存式导航",
    )),
    UpdateLogItem("2.0.0", "2026-09-07", listOf(
        "解析系统全面重构：接入原生抖音/小红书解析引擎（移植自 parse-video-py），不再依赖第三方解析网站",
        "新增小红书实况照片（Live Photo）解析、预览播放与下载",
        "新增抖音图文/Live Photo 解析支持",
        "实况照片在网格与预览中显示「实况」徽标，可单独下载实况视频",
        "批量下载时自动同时保存实况图片与实况视频",
        "解析速度与成功率显著提升，抖音视频直链自动 302 跟随",
    )),
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
        IOS26StickyPage(
            title = "更新日志",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item { IOS26PageHeader("更新日志", "LinkGrab 的每一次改进。", topPadding = 0.dp) }
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
        }
    }
}
