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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26Switch
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.update.UpdateResult
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateToUpdateLog: () -> Unit = {}) {
    val context = LocalContext.current
    val colorMode by viewModel.colorMode.collectAsState()
    val liveUpdates by viewModel.liveUpdatesEnabled.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val checkingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.toggleLiveUpdates(true) }

    IOS26Canvas {
        IOS26StickyPage(title = "设置") {
            item { IOS26PageHeader("设置", "让 LinkGrab 更符合你的使用习惯。", topPadding = 0.dp) }

            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS26ListRow(
                        "跟随系统外观",
                        "自动适配浅色和深色模式",
                    ) {
                        IOS26Switch(checked = colorMode == 0) { viewModel.setColorMode(if (it) 0 else 1) }
                    }
                    IOS26Divider(Modifier.padding(vertical = 4.dp))
                    IOS26ListRow(
                        "实时状态通知",
                        "下载和解析时显示进度",
                    ) {
                        IOS26Switch(checked = liveUpdates) { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else
                                viewModel.toggleLiveUpdates(enabled)
                        }
                    }
                    if (liveUpdates) {
                        Spacer(Modifier.padding(top = 8.dp))
                        IOS26Button("发送测试通知", { viewModel.testLiveUpdates() }, Modifier.fillMaxWidth())
                    }
                }
            }

            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "外观",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "选择 LinkGrab 的颜色模式",
                        fontSize = 14.sp,
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IOS26Choice("跟随系统", colorMode == 0, { viewModel.setColorMode(0) }, Modifier.weight(1f))
                        IOS26Choice("浅色", colorMode == 1, { viewModel.setColorMode(1) }, Modifier.weight(1f))
                        IOS26Choice("深色", colorMode == 2, { viewModel.setColorMode(2) }, Modifier.weight(1f))
                    }

                    Text(
                        "底栏样式",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                    Text(
                        "选择 LinkGrab 的底部导航风格",
                        fontSize = 14.sp,
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IOS26Choice("iOS27", bottomBarStyle == 0, { viewModel.setBottomBarStyle(0) }, Modifier.weight(1f))
                        IOS26Choice("HyperOS4", bottomBarStyle == 1, { viewModel.setBottomBarStyle(1) }, Modifier.weight(1f))
                        IOS26Choice("HarmonyOS 7", bottomBarStyle == 2, { viewModel.setBottomBarStyle(2) }, Modifier.weight(1f))
                    }
                }
            }

            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS26ListRow(
                        "软件更新",
                        "保持 LinkGrab 处于最新状态",
                        onClick = { if (!checkingUpdate) viewModel.checkForUpdate() },
                    ) {
                        Text(
                            if (checkingUpdate) "检查中…" else "检查",
                            color = iosBlue(),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.padding(top = 10.dp))
                    IOS26Button("检查更新", { viewModel.checkForUpdate() }, Modifier.fillMaxWidth(), !checkingUpdate)
                    when (val result = updateResult) {
                        is UpdateResult.UpToDate -> Text(
                            "当前已是最新版本 v${result.currentVersion}",
                            color = com.linkgrab.app.ui.theme.IOSGreen,
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
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "关于",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "LinkGrab",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "无水印媒体解析与保存工具",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 14.sp,
                    )
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME}",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.padding(top = 12.dp))
                    IOS26Button("查看更新日志", onNavigateToUpdateLog, Modifier.fillMaxWidth())
                    Text(
                        "解析服务由第三方网络接口提供，具体可用性取决于平台页面和网络环境。",
                        color = iosSecondaryLabel(),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
            }
        }
    }
}
