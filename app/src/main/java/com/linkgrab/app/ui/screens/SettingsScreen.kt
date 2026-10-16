package com.linkgrab.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToUpdateSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
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
                        "跟随系统外观",
                        "自动适配浅色和深色模式",
                    ) {
                        IOS26Switch(checked = colorMode == 0, onCheckedChange = { viewModel.setColorMode(if (it) 0 else 1) })
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
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
                    Text(
                        "颜色模式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IOS26Choice("跟随系统", colorMode == 0, { viewModel.setColorMode(0) }, Modifier.weight(1f))
                        IOS26Choice("浅色", colorMode == 1, { viewModel.setColorMode(1) }, Modifier.weight(1f))
                        IOS26Choice("深色", colorMode == 2, { viewModel.setColorMode(2) }, Modifier.weight(1f))
                    }

                    Text(
                        "底栏样式",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IOS26Choice("iOS27", bottomBarStyle == 0, { viewModel.setBottomBarStyle(0) }, Modifier.weight(1f))
                        IOS26Choice("HyperOS4", bottomBarStyle == 1, { viewModel.setBottomBarStyle(1) }, Modifier.weight(1f))
                        IOS26Choice("HarmonyOS 7", bottomBarStyle == 2, { viewModel.setBottomBarStyle(2) }, Modifier.weight(1f))
                        IOS26Choice("Material 3", bottomBarStyle == 3, { viewModel.setBottomBarStyle(3) }, Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionLabel("通用")
                IOS26Panel(Modifier.fillMaxWidth()) {
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
