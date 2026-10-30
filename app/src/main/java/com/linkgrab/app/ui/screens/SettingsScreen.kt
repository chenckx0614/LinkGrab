package com.linkgrab.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.linkgrab.app.BuildConfig
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26ListRow
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26Switch
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosDisclosureColor
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToUpdateSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onNavigateToBottomBarStyle: () -> Unit = {},
) {
    val context = LocalContext.current
    val colorMode by viewModel.colorMode.collectAsState()
    val liveUpdates by viewModel.liveUpdatesEnabled.collectAsState()
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.toggleLiveUpdates(true) }

    IOS26Canvas {
        // 底栏第 2 栏是「我的」，页面主标题跟着走；里面各段仍是设置项。
        IOS26StickyPage(title = "我的") {
            item { IOS26PageHeader("我的", "让 LinkGrab 更符合你的使用习惯。", topPadding = 0.dp) }

            // 分组标题语言与二级页（更新设置/关于应用）统一：
            // panel 外 14sp 中性色小标题，panel 内不再放 20sp 大标题。
            item {
                SectionLabel("通知")
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS26ListRow(
                        "实时状态通知",
                        "下载和解析时显示进度",
                    ) {
                        IOS26Switch(
                            checked = liveUpdates,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else
                                    viewModel.toggleLiveUpdates(enabled)
                            },
                        )
                    }
                    if (liveUpdates) {
                        Spacer(Modifier.padding(top = 8.dp))
                        IOS26Button("发送测试通知", { viewModel.testLiveUpdates() }, Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                SectionLabel("外观")
                IOS26Panel(Modifier.fillMaxWidth()) {
                    // 深色模式：下载源同款下拉选择（点击展开、当前项打勾、选中即收起）
                    val modeName = when (colorMode) { 0 -> "跟随系统"; 2 -> "深色"; else -> "浅色" }
                    var modeExpanded by remember { mutableStateOf(false) }
                    val hapticFeedback = LocalHapticFeedback.current
                    val modeChevron by animateFloatAsState(
                        targetValue = if (modeExpanded) 90f else 0f,
                        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                        label = "colorModeChevron",
                    )
                    IOS26ListRow(
                        "深色模式",
                        "控制应用的明暗外观",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            modeExpanded = !modeExpanded
                        },
                    ) {
                        Text(
                            modeName,
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
                                .rotate(modeChevron),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = modeExpanded,
                        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) +
                            androidx.compose.animation.expandVertically(
                                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
                            ),
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140)) +
                            androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)),
                    ) {
                        Column {
                            ModeOptionRow("跟随系统", "自动适配系统的明暗设置", colorMode == 0) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setColorMode(0); modeExpanded = false
                            }
                            ModeOptionRow("浅色", "始终使用浅色外观", colorMode == 1) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setColorMode(1); modeExpanded = false
                            }
                            ModeOptionRow("深色", "始终使用深色外观", colorMode == 2) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setColorMode(2); modeExpanded = false
                            }
                        }
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 底栏样式：二级页可视化选择
                    val styleName = when (bottomBarStyle) {
                        1 -> "HyperOS 4"; 2 -> "HarmonyOS 7"; 3 -> "Material 3"; else -> "iOS 27"
                    }
                    IOS26ListRow(
                        "底栏样式",
                        "四种底栏可视化预览选择",
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            onNavigateToBottomBarStyle()
                        },
                    ) {
                        Text(
                            styleName,
                            color = iosSecondaryLabel(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier.size(IOS26IconSize.Disclosure),
                        )
                    }
                }
            }

            item {
                SectionLabel("通用")
                IOS26Panel(Modifier.fillMaxWidth()) {
                    // 工具箱网页类工具的打开方式：应用内 or 系统浏览器
                    val toolboxBrowser by viewModel.toolboxOpenInBrowser.collectAsState()
                    val switchHaptic = LocalHapticFeedback.current
                    IOS26ListRow(
                        "工具用浏览器打开",
                        if (toolboxBrowser) "工具箱网页（含消消乐）用系统浏览器打开" else "工具箱网页在应用内打开",
                    ) {
                        IOS26Switch(
                            checked = toolboxBrowser,
                            onCheckedChange = { enabled ->
                                switchHaptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setToolboxOpenInBrowser(enabled)
                            },
                        )
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 剪贴板自动识别：回前台时把复制的分享链接变成一张「立即解析」卡片
                    val clipboardDetect by viewModel.clipboardDetect.collectAsState()
                    IOS26ListRow(
                        "剪贴板自动识别",
                        if (clipboardDetect) "回到应用时自动识别复制的分享链接" else "只在你点「从剪贴板粘贴」时读取",
                    ) {
                        IOS26Switch(
                            checked = clipboardDetect,
                            onCheckedChange = { enabled ->
                                switchHaptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setClipboardDetect(enabled)
                            },
                        )
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 手势切页：关掉后主界面左右滑动不再切换页面
                    val swipeSwitchPages by viewModel.swipeSwitchPages.collectAsState()
                    IOS26ListRow(
                        "左右滑动切换页面",
                        if (swipeSwitchPages) "在主界面左右滑动即可切换页面" else "只能通过点底栏切换页面",
                    ) {
                        IOS26Switch(
                            checked = swipeSwitchPages,
                            onCheckedChange = { enabled ->
                                switchHaptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                                viewModel.setSwipeSwitchPages(enabled)
                            },
                        )
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 检查更新挪进「更新设置」二级页，这里只留一行入口带版本号
                    IOS26ListRow(
                        "更新设置",
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        onClick = onNavigateToUpdateSettings,
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier.size(IOS26IconSize.Disclosure),
                        )
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 「下载管理」：任务队列实时视图（暂停/续传/取消）+ 队列设置
                    val activeDownloads by viewModel.downloadTasks.collectAsState()
                    val activeCount = activeDownloads.count {
                        it.status == com.linkgrab.app.viewmodel.DownloadStatus.Queued ||
                            it.status == com.linkgrab.app.viewmodel.DownloadStatus.Downloading
                    }
                    IOS26ListRow(
                        "下载管理",
                        if (activeCount > 0) "$activeCount 个任务进行中" else "暂停、恢复与管理下载任务",
                        onClick = onNavigateToDownloads,
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier.size(IOS26IconSize.Disclosure),
                        )
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    // 「关于应用」二级页：软件名 / 图标 / 版本 / 更新日志 / 交流与反馈都在里面
                    IOS26ListRow(
                        "关于应用",
                        "版本信息、更新日志与捐赠支持",
                        onClick = onNavigateToAbout,
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier.size(IOS26IconSize.Disclosure),
                        )
                    }
                }
            }
        }
    }
}

/** 分组小标题：与更新设置/关于应用二级页同一语言（panel 外、14sp、中性色）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = iosSecondaryLabel(),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

/** 深色模式选项行：与更新设置的下载源选项同款（当前项蓝字 + 打蓝勾）。 */
@Composable
private fun ModeOptionRow(
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
        // 固定 20dp 占位：勾弹性出现/缩回，行高不跳动
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = selected,
                enter = androidx.compose.animation.scaleIn(
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 640f),
                    initialScale = 0.4f,
                ) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(120)),
                exit = androidx.compose.animation.scaleOut(
                    targetScale = 0.6f,
                    animationSpec = androidx.compose.animation.core.tween(140),
                ) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
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
