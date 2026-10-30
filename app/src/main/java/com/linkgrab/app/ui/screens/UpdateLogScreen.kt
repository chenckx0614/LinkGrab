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
    UpdateLogItem("3.2.9", "2026-10-29", listOf(
        "交流与反馈支持直接前往 GitHub Issues",
    )),
    UpdateLogItem("3.2.8", "2026-10-29", listOf(
        "新增反馈问题页面，可带诊断信息一键提 Issue",
    )),
    UpdateLogItem("3.2.7", "2026-10-29", listOf(
        "修复部分图文作品解析失败的问题",
    )),
    UpdateLogItem("3.2.6", "2026-10-29", listOf(
        "修复抖音图集保存的图片带水印的问题",
    )),
    UpdateLogItem("3.2.5", "2026-10-29", listOf(
        "修复返回按钮没有震动反馈的问题",
        "抖音解析接入官方数据接口，成功率更高",
    )),
    UpdateLogItem("3.2.1", "2026-10-28", listOf(
        "修复保存的实况照片在部分相册中不动的问题",
    )),
    UpdateLogItem("3.2.0", "2026-10-28", listOf(
        "实况照片可保存为单个文件，相册中直接播放",
        "保存实况时也可选择图片、视频分开存",
        "历史记录支持按作者搜索，可组合筛选类型和保存状态",
        "再次解析同一链接时，收藏和缓存会保留",
        "修复抖音图集解析后只显示一张图的问题",
        "支持在设置中关闭左右滑动切换页面",
    )),
    UpdateLogItem(
        "3.1.0", "2026-10-26", listOf(
            "图片预览支持双击缩放，单击任意位置退出",
            "预览背景改为毛玻璃，打开时从缩略图位置展开",
            "修复抖音图文只显示封面的问题",
            "修复图片预览无法左右滑动翻页的问题",
        )
    ),
    UpdateLogItem(
        "3.0.30 – 3.0.33", "2026-10-23 ~ 10-25", listOf(
            "历史记录支持搜索、平台筛选与收藏置顶",
            "全应用统一按压震动反馈",
        )
    ),
    UpdateLogItem(
        "3.0.25 – 3.0.29", "2026-10-21", listOf(
            "工具箱新增「网盘直链」，支持夸克 / UC 切换",
            "修复 B 站解析与夸克登录问题",
        )
    ),
    UpdateLogItem(
        "3.0.14 – 3.0.24", "2026-10-18 ~ 10-19", listOf(
            "新增 B 站 / 微博 / 通用网页 / 二维码解析，音乐扩展到网易云、QQ 音乐",
            "工具箱新增「文叔叔」与「文件传输」",
            "剪贴板自动识别分享链接，下载按平台自动补 Referer",
        )
    ),
    UpdateLogItem(
        "3.0 – 3.0.12", "2026-10-16 ~ 10-17", listOf(
            "新增下载队列、批量解析与下载内容自动整理",
            "安装包体积大幅精简",
            "引导页重做，可选底栏样式",
            "增强网页解析稳定性",
        )
    ),
)

/** 更早的版本：按主题归并成几个阶段，默认折叠。 */
private val archiveLogs = listOf(
    UpdateLogItem("2.5.12 – 2.5.40", "2026-10-12 ~ 10-16", listOf(
        "底栏与按钮统一为液态玻璃样式",
        "页面切换改为覆盖式转场",
        "新增捐赠支持、更新设置与关于页面",
        "补齐视频播放与图片预览功能",
        "修复游戏转场、通知与小红书解析问题",
    )),
    UpdateLogItem("2.5.0 – 2.5.11", "2026-10-09 ~ 10-12", listOf(
        "底栏新增 Material 3 风格",
        "图片预览支持左右翻页、双指缩放与实况播放",
        "下载改为直接保存原始文件，更快且不损画质",
        "修复已知问题",
    )),
    UpdateLogItem("2.3.6 – 2.4.0", "2026-09-27", listOf(
        "修复小红书解析不稳定的问题",
        "抖音支持选择保存清晰度",
        "修复已知问题",
    )),
    UpdateLogItem("2.3.0 – 2.3.5", "2026-09-14 ~ 09-27", listOf(
        "新增音乐解析下载（酷我分享链接）",
        "修复已知问题",
    )),
    UpdateLogItem("2.2.0 – 2.2.11", "2026-09-09 ~ 09-12", listOf(
        "重做首次启动引导页，可预览底栏样式",
        "修复已知问题",
    )),
    UpdateLogItem("2.1.0 – 2.1.11", "2026-09-09", listOf(
        "底栏新增 HarmonyOS 7 风格",
        "重构解析结果页，信息展示更清晰",
        "修复已知问题",
    )),
    UpdateLogItem("2.0.0 – 2.0.9", "2026-09-07 ~ 09-09", listOf(
        "解析不再依赖第三方网站，更快更稳",
        "新增小红书实况照片与抖音图文的解析下载",
        "全部页面升级为吸顶导航栏",
        "修复已知问题",
    )),
    UpdateLogItem("1.6.5 – 1.8.3", "2026-08-13 ~ 09-07", listOf(
        "全局界面改为液态玻璃风格",
        "历史记录与更新日志换用新布局",
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
