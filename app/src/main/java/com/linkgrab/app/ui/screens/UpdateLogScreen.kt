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
        "3.0.29", "2026-10-21", listOf(
            "网盘直链支持 UC 网盘：顶部可切换夸克 / UC",
        )
    ),
    UpdateLogItem(
        "3.0.28", "2026-10-21", listOf(
            "修复夸克登录被引导下载 App：改用桌面版网页登录",
        )
    ),
    UpdateLogItem(
        "3.0.27", "2026-10-21", listOf(
            "修复 B 站部分视频解析报错（兼容字段为空的响应）",
        )
    ),
    UpdateLogItem(
        "3.0.26", "2026-10-21", listOf(
            "修复 B 站解析失效：接口升级为 WBI 签名请求，番剧端点换 v2",
        )
    ),
    UpdateLogItem(
        "3.0.25", "2026-10-21", listOf(
            "工具箱新增「网盘直链」：登录夸克网盘即可浏览并获取文件直链",
        )
    ),
    UpdateLogItem(
        "3.0.24", "2026-10-19", listOf(
            "iOS 开关手感调整：滑块弹簧不再回弹，到位更干脆",
        )
    ),
    UpdateLogItem(
        "3.0.23", "2026-10-18", listOf(
            "引导页底栏衔接动画重做：选中卡片原地淡出，位置直接交给真底栏",
        )
    ),
    UpdateLogItem(
        "3.0.22", "2026-10-18", listOf(
            "整理更新日志：去重、精简、去掉琐碎小改动",
        )
    ),
    UpdateLogItem(
        "3.0.21", "2026-10-18", listOf(
            "修复「二维码」按钮文字换行",
            "按钮加宽后末尾字符不再被裁",
        )
    ),
    UpdateLogItem(
        "3.0.19", "2026-10-18", listOf(
            "新增 B 站 / 微博 / 通用网页抓取 / 二维码解析",
            "音乐扩展到网易云、QQ 音乐",
            "下载按平台自动补 Referer",
        )
    ),
    UpdateLogItem(
        "3.0.16", "2026-10-18", listOf(
            "剪贴板自动识别分享链接",
            "解析结果与保存记录更聪明",
            "失败提示更明确",
        )
    ),
    UpdateLogItem(
        "3.0.15", "2026-10-18", listOf(
            "工具箱新增「文件传输」二级页",
            "网页菜单毛玻璃与页面诊断",
            "文叔叔视口自愈",
        )
    ),
    UpdateLogItem(
        "3.0.14", "2026-10-18", listOf(
            "网页页不再被采样层录制，修复显示不全",
            "工具页「选择文件」可用",
        )
    ),
    UpdateLogItem(
        "3.0.12", "2026-10-18", listOf(
            "菜单支持点外部关闭",
        )
    ),
    UpdateLogItem(
        "3.0.10", "2026-10-17", listOf(
            "WebView 健壮性与调试",
        )
    ),
    UpdateLogItem(
        "3.0.8", "2026-10-17", listOf(
            "网页工具页顶栏与菜单优化",
            "文叔叔桌面版加载",
        )
    ),
    UpdateLogItem(
        "3.0.4", "2026-10-17", listOf(
            "工具箱新增「文叔叔」",
            "修复奶蛙消消乐打不开",
        )
    ),
    UpdateLogItem(
        "3.0.2", "2026-10-16", listOf(
            "引导页四页推进式重做",
        )
    ),
    UpdateLogItem(
        "3.0", "2026-10-16", listOf(
            "安装包瘦身、下载队列、批量解析、自动整理",
            "欢迎页与底栏样式页",
        )
    ),
)

/** 更早的版本：按主题归并成几个阶段，默认折叠。 */
private val archiveLogs = listOf(
    UpdateLogItem("2.5.12 – 2.5.40", "2026-10-12 ~ 10-16", listOf(
        "iOS27 液态玻璃底栏与全局按钮样式统一",
        "页面切换改为覆盖式转场，层级感更强",
        "新增捐赠支持、更新设置与关于应用页",
        "视频播放与图片预览体验补齐",
        "多处修复：游戏转场、通知、小红书解析等",
    )),
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
