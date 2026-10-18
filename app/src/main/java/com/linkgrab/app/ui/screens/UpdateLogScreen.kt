package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

data class UpdateLogItem(val version: String, val date: String, val changes: List<String>)

/** 最近几个版本，进页面就能看到。每条尽量一句话说清，改动少的版本统一写「修复已知问题」。 */
val recentLogs = listOf(
    UpdateLogItem(
        "3.0.12", "2026-10-18", listOf(
            "菜单外部点击关闭：展开后点任意空白处即可收起",
            "返回键优先关闭菜单，再处理网页历史 / 退出",
            "菜单毛玻璃只采顶栏，不再把 WebView 录进采样层，解决闪屏/显示不全",
        )
    ),
    UpdateLogItem(
        "3.0.11", "2026-10-18", listOf(
            "文叔叔改回手机页面：不再强制桌面版 UA",
            "恢复工具页右上角菜单的毛玻璃背景模糊",
            "继续修复网页工具打开后的闪屏与白屏问题",
        )
    ),
    UpdateLogItem(
        "3.0.10", "2026-10-17", listOf(
            "网页内核健壮性：渲染进程崩溃自动重建 WebView（白屏自救）",
            "重定向打断加载不再误报为加载失败；外部协议链接交给系统打开",
            "Debug 包支持 Chrome DevTools 远程调试，网页控制台日志接入 Logcat",
        )
    ),
    UpdateLogItem(
        "3.0.9", "2026-10-17", listOf(
            "修复打开右上角菜单时页面闪一下：玻璃采样层改为常挂的静态层",
            "文叔叔桌面版加载绕过缓存，避免命中旧移动页缓存",
        )
    ),
    UpdateLogItem(
        "3.0.8", "2026-10-17", listOf(
            "网页工具页右上角菜单与返回键对齐到同一条直线",
            "文叔叔改用桌面版网页：移动网页面本身是精简落地页，PC 页内容完整且可缩放",
            "消消乐恢复原 WebView 配方，修复上一版引入的闪屏",
        )
    ),
    UpdateLogItem(
        "3.0.7", "2026-10-17", listOf(
            "修复网页工具页菜单展开时顶栏被撑大：顶栏高度现在固定",
            "继续修文叔叔白屏：WebView 实色底 + WebChromeClient + Cookie 支持 + 字体缩放固定",
        )
    ),
    UpdateLogItem(
        "3.0.6", "2026-10-17", listOf(
            "修复文叔叔白屏：WebView 换用标准 Chrome UA，不再被站点识别为空壳",
            "网页工具页新增顶栏与右上角菜单：支持刷新、使用浏览器打开",
            "「工具用浏览器打开」开关现在对消消乐同样生效",
        )
    ),
    UpdateLogItem(
        "3.0.5", "2026-10-17", listOf(
            "「文叔叔」默认改为应用内打开，不再跳浏览器",
            "「我的」新增开关：工具箱网页可用系统浏览器打开",
        )
    ),
    UpdateLogItem(
        "3.0.4", "2026-10-17", listOf(
            "修复奶蛙消消乐打不开：加载失败或超时后提示重试，不再无限转圈",
            "工具箱新增「文叔叔」：一键打开免登录大文件传输",
        )
    ),
    UpdateLogItem(
        "3.0.3", "2026-10-16", listOf(
            "吸顶标题栏换成与底栏一致的真毛玻璃：滚动内容透出模糊，视觉统一",
            "底栏样式设置页选项间距优化，引导页底栏样式改为竖排大图选择",
        )
    ),
    UpdateLogItem(
        "3.0.2", "2026-10-16", listOf(
            "引导页全面重做：欢迎 → 解析 → 历史记录 → 选底栏样式，四页推进式",
            "「下一步」改为磨砂玻璃效果，页间向左推开转场并带进度圆点",
            "完成引导时底栏样式卡片衔接落位到真底栏，背景模糊白纱收尾",
            "底栏样式设置页改用实拍图展示四种样式",
        )
    ),
    UpdateLogItem(
        "3.0", "2026-10-16", listOf(
            "安装包从 93MB 瘦身到约 27MB",
            "下载队列管理：暂停 / 断点续传 / 取消 / 重试，并发数可调",
            "新增「下载管理」页：实时查看任务进度与队列设置",
            "批量解析：一次粘贴多条链接（最多 5 条），勾选一键下载",
            "下载完成自动整理：按 平台/日期 归档到子文件夹（可关闭）",
            "全新 3.0 欢迎页：弥散渐变流动背景 + 大「3」",
            "底栏样式独立成页：四种底栏可视化预览选择",
            "深色模式改为下拉选择：跟随系统 / 浅色 / 深色",
            "性能优化：长列表与页面转场更流畅",
        )
    ),
    UpdateLogItem("2.5.40", "2026-10-16", listOf(
        "下载源选择优化：选中即收起，打勾弹性出现",
        "展开收起弹簧动画，全程触觉反馈",
    )),
    UpdateLogItem("2.5.39", "2026-10-16", listOf(
        "更新设置新增「下载源」：可选 Gitee（默认）或 GitHub",
        "检查更新与下载安装包按所选仓库进行",
    )),
    UpdateLogItem("2.5.38", "2026-10-16", listOf(
        "进入二级页时底栏不再突然消失，改为跟随页面一起推走渐隐",
        "返回主界面时底栏从让位处滑回原位，与转场同步",
    )),
    UpdateLogItem("2.5.37", "2026-10-16", listOf(
        "转场加上底层页推进：新页盖入时旧页被推着左移，层次感更强",
        "返回时下层页从让位处滑回原位，模糊同步恢复清晰",
    )),
    UpdateLogItem("2.5.36", "2026-10-16", listOf(
        "页面切换重构为覆盖式转场：新页从右滑入，旧页原地渐糊渐暗",
        "返回时上层页滑出、下层页从模糊平滑恢复清晰",
    )),
    UpdateLogItem("2.5.35", "2026-10-16", listOf(
        "HyperOS4 底栏更小巧，修复按压动画卡住的问题",
        "修复 Material 3 底栏指示器缩小后不恢复",
        "「我的」页面分组样式与二级页统一",
    )),
    UpdateLogItem("2.5.34", "2026-10-16", listOf(
        "退出游戏转场彻底优化：WebView 退出布局，截图替身参与转场",
    )),
    UpdateLogItem("2.5.33", "2026-10-16", listOf(
        "修复退出游戏时的卡顿与画面闪烁",
    )),
    UpdateLogItem("2.5.32", "2026-10-16", listOf(
        "修复打开游戏时的白屏闪烁",
        "转场提速，页面切换更利落",
    )),
    UpdateLogItem("2.5.31", "2026-10-14", listOf(
        "工具箱新增「奶蛙消消乐」，应用内直接玩",
    )),
    UpdateLogItem("2.5.30", "2026-10-14", listOf(
        "恢复页面滑动切换转场，替换渐变效果",
    )),
    UpdateLogItem("2.5.29", "2026-10-14", listOf(
        "页面切换改用系统转场动画",
        "捐赠支持页上线：赞赏码扫码请作者喝杯咖啡",
    )),
    UpdateLogItem("2.5.28", "2026-10-14", listOf(
        "修复进入关于应用页闪退的问题",
        "交流与反馈改为在关于应用页内展开",
    )),
    UpdateLogItem("2.5.27", "2026-10-14", listOf(
        "关于应用页改为新样式：大图标、版本号与内嵌更新日志",
        "新增捐赠支持入口",
    )),
    UpdateLogItem("2.5.26", "2026-10-14", listOf(
        "新增「关于应用」页：应用图标、版本信息与更新日志入口",
        "新增「交流与反馈」页（内容筹备中）",
    )),
    UpdateLogItem("2.5.25", "2026-10-14", listOf(
        "修复实时状态通知偶发不消失的问题",
        "新增更新设置页：当前版本、检查更新、自动检查与更新提醒开关",
    )),
    UpdateLogItem("2.5.24", "2026-10-13", listOf(
        "图片预览背景改为虚化压暗，不再是纯黑",
        "视频播放补齐暂停、快进快退与可拖动进度条",
    )),
    UpdateLogItem("2.5.23", "2026-10-13", listOf(
        "优化进入页面的过渡动画，减少掉帧",
    )),
    UpdateLogItem("2.5.22", "2026-10-13", listOf(
        "更新弹窗换为新样式，标题与按钮随下载状态变化",
        "下载或安装途中也能随时关闭弹窗",
    )),
    UpdateLogItem("2.5.21", "2026-10-13", listOf(
        "统一各处图标的尺寸与配色",
        "放大图片按钮的点击区域，减少误触",
    )),
    UpdateLogItem("2.5.20", "2026-10-12", listOf(
        "设置里的开关换为新样式，支持左右滑动切换",
    )),
    UpdateLogItem("2.5.19", "2026-10-12", listOf(
        "删除确认弹窗改为液态玻璃效果",
    )),
    UpdateLogItem("2.5.18", "2026-10-12", listOf(
        "修复已知问题",
    )),
    UpdateLogItem("2.5.17", "2026-10-12", listOf(
        "修复小红书解析不出来",
        "修复图片过一段时间就保存失败，并提升保存画质",
    )),
    UpdateLogItem("2.5.16", "2026-10-12", listOf(
        "顶栏按钮统一换为液态玻璃样式",
        "历史记录与解析结果的「选择」改为右上角菜单",
    )),
    UpdateLogItem("2.5.15", "2026-10-12", listOf(
        "底栏顺序调整为「首页 / 我的 / 工具箱」",
        "修复已知问题",
    )),
    UpdateLogItem("2.5.14", "2026-10-12", listOf(
        "顶部导航栏改为渐进式模糊，过渡更自然",
        "返回按钮升级为液态玻璃版",
        "主页标签页支持左右滑动切换",
        "修复已知问题",
    )),
    UpdateLogItem("2.5.13", "2026-10-12", listOf(
        "修复已知问题",
    )),
    UpdateLogItem("2.5.12", "2026-10-12", listOf(
        "底栏新增 iOS27 液态玻璃风格，并设为默认",
    )),
)

/** 更早的版本：按主题归并成几个阶段，默认折叠。 */
private val archiveLogs = listOf(
    UpdateLogItem("2.5.0 – 2.5.11", "2026-10-09 ~ 10-12", listOf(
        "底栏新增 Material 3 风格",
        "图片预览支持左右翻页、双指缩放与实况播放",
        "下载改为直接保存原始文件，更快且不损画质",
        "修复已知问题",
    )),
    UpdateLogItem("2.3.6 – 2.4.0", "2026-09-27", listOf(
        "小红书改用更稳定的解析方案",
        "抖音支持选择保存清晰度",
        "修复已知问题",
    )),
    UpdateLogItem("2.3.0 – 2.3.5", "2026-09-14 ~ 09-27", listOf(
        "新增音乐解析下载（酷我分享链接）",
        "修复已知问题",
    )),
    UpdateLogItem("2.2.0 – 2.2.11", "2026-09-09 ~ 09-12", listOf(
        "重做首次启动引导页，可预览并选择底栏样式",
        "修复已知问题",
    )),
    UpdateLogItem("2.1.0 – 2.1.11", "2026-09-09", listOf(
        "底栏新增 HarmonyOS 7 风格",
        "解析结果页重构，信息展示更清晰",
        "修复已知问题",
    )),
    UpdateLogItem("2.0.0 – 2.0.9", "2026-09-07 ~ 09-09", listOf(
        "解析引擎全面重构，不再依赖第三方解析网站",
        "新增小红书实况照片与抖音图文的解析下载",
        "全部页面升级为吸顶导航栏",
        "修复已知问题",
    )),
    UpdateLogItem("1.6.5 – 1.8.3", "2026-08-13 ~ 09-07", listOf(
        "基于 Liquid Glass 规范重构全局界面",
        "历史记录与更新日志改为新布局",
        "修复已知问题",
    )),
)

@Composable
fun UpdateLogScreen(onBack: () -> Unit) {
    // 归档区默认收起：几十条细碎记录会把最近的版本淹没
    var showArchived by remember { mutableStateOf(false) }
    val visible = if (showArchived) recentLogs + archiveLogs else recentLogs

    IOS26Canvas {
        IOS26StickyPage(
            title = "更新日志",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader(
                    "更新日志",
                    if (showArchived) "最近的改动，以及按主题归并的历史版本。"
                    else "LinkGrab 的每一次改进。",
                    topPadding = 0.dp,
                )
            }
            items(visible.size, key = { visible[it].version }) { index ->
                UpdateLogCard(visible[index])
            }
            if (!showArchived) {
                item {
                    IOS26OutlinedButton(
                        text = "查看更早的版本（${archiveLogs.size} 个）",
                        onClick = { showArchived = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateLogCard(item: UpdateLogItem) {
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
