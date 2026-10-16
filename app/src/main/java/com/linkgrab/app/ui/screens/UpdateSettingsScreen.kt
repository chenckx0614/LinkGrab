package com.linkgrab.app.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
 * 第一屏是版本信息 + 手动检查更新；「更多设置」里是两个自动化开关。
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
                }
            }
        }
    }
}
