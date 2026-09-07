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
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.linkgrab.app.BuildConfig
import com.linkgrab.app.ui.theme.NexioButton
import com.linkgrab.app.ui.theme.NexioCanvas
import com.linkgrab.app.ui.theme.NexioChoice
import com.linkgrab.app.ui.theme.NexioListRow
import com.linkgrab.app.ui.theme.NexioPageHeader
import com.linkgrab.app.ui.theme.NexioPanel
import com.linkgrab.app.ui.theme.NexioSwitch
import com.linkgrab.app.ui.theme.nexioInk
import com.linkgrab.app.ui.theme.nexioSecondary
import com.linkgrab.app.update.UpdateResult
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, onNavigateToUpdateLog: () -> Unit = {}) {
    val context = LocalContext.current
    val colorMode by viewModel.colorMode.collectAsState()
    val liveUpdates by viewModel.liveUpdatesEnabled.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val checkingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.toggleLiveUpdates(true) }

    NexioCanvas {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { NexioPageHeader("设置", "让 LinkGrab 更符合你的使用习惯。") }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    NexioListRow("跟随系统外观", "自动适配浅色和深色模式") { NexioSwitch(checked = colorMode == 0) { viewModel.setColorMode(if (it) 0 else 1) } }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    NexioListRow("实时状态通知", "下载和解析时显示进度") { NexioSwitch(checked = liveUpdates) { enabled -> if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else viewModel.toggleLiveUpdates(enabled) } }
                    if (liveUpdates) NexioButton("发送测试通知", { viewModel.testLiveUpdates() }, Modifier.fillMaxWidth().padding(top = 12.dp))
                }
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    Text("外观", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = nexioInk())
                    Text("选择 LinkGrab 的颜色模式", fontSize = 14.sp, color = nexioSecondary(), modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NexioChoice("跟随系统", colorMode == 0, { viewModel.setColorMode(0) }, Modifier.weight(1f))
                        NexioChoice("浅色", colorMode == 1, { viewModel.setColorMode(1) }, Modifier.weight(1f))
                        NexioChoice("深色", colorMode == 2, { viewModel.setColorMode(2) }, Modifier.weight(1f))
                    }
                }
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    NexioListRow("软件更新", "保持 LinkGrab 处于最新状态") { Text(if (checkingUpdate) "检查中…" else "检查", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                    NexioButton("检查更新", { viewModel.checkForUpdate() }, Modifier.fillMaxWidth().padding(top = 12.dp), !checkingUpdate)
                    when (val result = updateResult) {
                        is UpdateResult.UpToDate -> Text("当前已是最新版本 v${result.currentVersion}", color = Color(0xFF248A3D), modifier = Modifier.padding(top = 10.dp))
                        is UpdateResult.CheckFailed -> Text("检查失败：${result.error}", color = Color(0xFFD70015), modifier = Modifier.padding(top = 10.dp))
                        is UpdateResult.UpdateAvailable -> Text("发现新版本 v${result.latestVersion}", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                        null -> Unit
                    }
                }
            }
            item {
                NexioPanel(Modifier.fillMaxWidth()) {
                    Text("关于", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = nexioInk())
                    Text("LinkGrab", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = nexioInk(), modifier = Modifier.padding(top = 14.dp))
                    Text("无水印媒体解析与保存工具", color = nexioSecondary(), modifier = Modifier.padding(top = 4.dp))
                    Text("版本 ${BuildConfig.VERSION_NAME}", color = nexioSecondary(), modifier = Modifier.padding(top = 4.dp))
                    NexioButton("查看更新日志", onNavigateToUpdateLog, Modifier.fillMaxWidth().padding(top = 14.dp))
                    Text("解析服务由第三方网络接口提供，具体可用性取决于平台页面和网络环境。", color = nexioSecondary(), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                }
                Spacer(Modifier.padding(bottom = 100.dp))
            }
        }
    }
}
