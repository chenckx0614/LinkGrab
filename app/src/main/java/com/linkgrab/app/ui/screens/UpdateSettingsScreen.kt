package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.BuildConfig
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26ListRow
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26Switch
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosDisclosureColor
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.update.UpdateResult
import com.linkgrab.app.viewmodel.MainViewModel

/**
 * 更新设置（「我的」→ 二级页）：
 * 第一屏是版本信息 + 手动检查更新；「更多设置」里是下载源与两个自动化开关。
 * 检查更新从设置页挪到这里，入口一行带走当前版本。
 */
@Composable
fun UpdateSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val checkingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()

    IOS26Canvas {
        IOS26StickyPage(
            title = "更新设置",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader("更新设置", "管理 LinkGrab 的版本更新。", topPadding = 0.dp)
            }

            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS26ListRow(
                        "当前版本",
                        "v${BuildConfig.VERSION_NAME}",
                    )
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    IOS26ListRow(
                        "检查更新",
                        "查看是否有可用的新版本",
                        onClick = { if (!checkingUpdate) viewModel.checkForUpdate() },
                    ) {
                        if (checkingUpdate) {
                            Text(
                                "检查中…",
                                color = iosSecondaryLabel(),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = iosDisclosureColor(),
                                modifier = Modifier.size(IOS26IconSize.Disclosure),
                            )
                        }
                    }
                    // 检查结果就地显示在面板底部
                    when (val result = updateResult) {
                        is UpdateResult.UpToDate -> Text(
                            "当前已是最新版本 v${result.currentVersion}",
                            color = IOSGreen,
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                        is UpdateResult.CheckFailed -> Text(
                            "检查失败：${result.error}",
                            color = iosError(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                        is UpdateResult.UpdateAvailable -> Text(
                            "发现新版本 v${result.latestVersion}",
                            color = iosBlue(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                        null -> Unit
                    }
                }
            }

            item {
                Text(
                    "更多设置",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }

            item {
                val autoCheck by viewModel.autoCheckUpdate.collectAsState()
                val reminder by viewModel.updateReminder.collectAsState()
                val downloadSource by viewModel.downloadSource.collectAsState()
                var sourceExpanded by remember { mutableStateOf(false) }
                val hapticFeedback = LocalHapticFeedback.current
                // chevron 平滑旋转（弹簧），而不是硬切 0°/90°
                val chevronRotation by animateFloatAsState(
                    targetValue = if (sourceExpanded) 90f else 0f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                    label = "downloadSourceChevron",
                )
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS26ListRow(
                        "自动检查更新",
                        "启动时自动检查是否有新版本",
                    ) {
                        IOS26Switch(checked = autoCheck, onCheckedChange = { viewModel.setAutoCheckUpdate(it) })
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    IOS26ListRow(
                        "更新提醒",
                        "自动检查发现新版本时弹出提醒",
                    ) {
                        IOS26Switch(checked = reminder, onCheckedChange = { viewModel.setUpdateReminder(it) })
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    IOS26ListRow(
                        "下载源",
                        "选择检查更新与下载安装包的仓库",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            sourceExpanded = !sourceExpanded
                        },
                    ) {
                        Text(
                            if (downloadSource == "github") "GitHub" else "Gitee",
                            color = iosSecondaryLabel(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier
                                .size(IOS26IconSize.Disclosure)
                                .rotate(chevronRotation),
                        )
                    }
                    // 就地展开选择（交互对齐 Nexio 课程表的 OverlayDropdownMenu）：
                    // 点击行弹簧展开、当前项打勾；**选中即收起**（对齐库的
                    // collapseOnSelection = true），触觉反馈标记每一次选择。
                    AnimatedVisibility(
                        visible = sourceExpanded,
                        enter = fadeIn(tween(180)) +
                            expandVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            ),
                        exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
                    ) {
                        Column {
                            SourceOptionRow(
                                name = "Gitee",
                                description = "国内直连，检查与下载速度更快",
                                selected = downloadSource == "gitee",
                                onSelect = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    viewModel.setDownloadSource("gitee")
                                    sourceExpanded = false
                                },
                            )
                            SourceOptionRow(
                                name = "GitHub",
                                description = "国际源，适合海外网络环境",
                                selected = downloadSource == "github",
                                onSelect = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                    viewModel.setDownloadSource("github")
                                    sourceExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 下载源选项行：与 [IOS26ListRow] 同样的内边距与字号，选中项打蓝勾。
 * 打勾走弹性缩放出现（固定占位，行高不因勾的出现跳动）。
 */
@Composable
private fun SourceOptionRow(
    name: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) iosBlue() else iosLabel(),
                letterSpacing = (-0.2).sp,
            )
            Text(
                description,
                fontSize = 14.sp,
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 2.dp),
                letterSpacing = (-0.1).sp,
            )
        }
        // 固定 20dp 占位：勾弹性出现/缩回，行高与右侧布局不跳动
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            // 全限定调用：Row 内隐式接收者会误匹配 RowScope.AnimatedVisibility 扩展
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 640f),
                    initialScale = 0.4f,
                ) + fadeIn(tween(120)),
                exit = scaleOut(targetScale = 0.6f, animationSpec = tween(140)) + fadeOut(tween(120)),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = iosBlue(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
