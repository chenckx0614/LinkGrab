package com.linkgrab.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.linkgrab.app.BuildConfig
import com.linkgrab.app.R
import com.linkgrab.app.update.UpdateResult
import com.linkgrab.app.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateToUpdateLog: () -> Unit = {},
) {
    val colorMode by viewModel.colorMode.collectAsState()
    val liveUpdatesEnabled by viewModel.liveUpdatesEnabled.collectAsState()
    val predictiveBack by viewModel.predictiveBack.collectAsState()
    val updateResult by viewModel.updateResult.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    val scrollProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else 0f
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.toggleLiveUpdates(true)
    }

    // Show toast for up-to-date
    LaunchedEffect(updateResult) {
        when (val result = updateResult) {
            is UpdateResult.UpToDate -> {
                Toast.makeText(context, "已是最新版本 v${result.currentVersion}", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdate()
            }
            is UpdateResult.CheckFailed -> {
                Toast.makeText(context, "检查失败: ${result.error}", Toast.LENGTH_SHORT).show()
                viewModel.dismissUpdate()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = blurredBarModifier(scrollProgress)) {
                TopAppBar(
                    title = "设置",
                    largeTitle = "设置",
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 16.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // ========== 外观 ==========
            item {
                SectionHeader("外观")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "跟随系统",
                        summary = "自动切换深色和浅色模式",
                        checked = colorMode == 0,
                        onCheckedChange = { enabled ->
                            if (enabled) viewModel.setColorMode(0)
                            else viewModel.setColorMode(1)
                        },
                    )
                    if (colorMode != 0) {
                        SwitchPreference(
                            title = "浅色模式",
                            summary = "始终使用浅色主题",
                            checked = colorMode == 1,
                            onCheckedChange = { viewModel.setColorMode(1) },
                        )
                        SwitchPreference(
                            title = "深色模式",
                            summary = "始终使用深色主题",
                            checked = colorMode == 2,
                            onCheckedChange = { viewModel.setColorMode(2) },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 手势 ==========
            item {
                SectionHeader("手势")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "预测返回手势",
                        summary = "滑动返回时预览上一页（Android 13+）",
                        checked = predictiveBack == 1,
                        onCheckedChange = { enabled ->
                            viewModel.setPredictiveBack(if (enabled) 1 else 2)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 更新 ==========
            item {
                SectionHeader("更新")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.checkForUpdate() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "检查更新", style = MiuixTheme.textStyles.title2)
                            Text(
                                text = "当前版本 v${BuildConfig.VERSION_NAME}",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Icon(MiuixIcons.ChevronForward, contentDescription = "检查")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 调试 ==========
            item {
                SectionHeader("调试")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "Live Updates 通知",
                        summary = "开启后下载/解析时显示进度通知",
                        checked = liveUpdatesEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPermission) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else viewModel.toggleLiveUpdates(true)
                            } else {
                                viewModel.toggleLiveUpdates(enabled)
                            }
                        },
                    )
                    if (liveUpdatesEnabled) {
                        Button(
                            onClick = { viewModel.testLiveUpdates() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        ) {
                            Text("发送测试通知")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 关于 ==========
            item {
                SectionHeader("关于")

                // Version card
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "LinkGrab",
                            style = MiuixTheme.textStyles.title1,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "作者：chenckx",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Description
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "一款媒体下载工具，支持解析抖音、小红书的分享链接，获取无水印图片和视频。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 更新日志 ==========
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToUpdateLog() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "更新日志", style = MiuixTheme.textStyles.title2, modifier = Modifier.weight(1f))
                        Icon(MiuixIcons.ChevronForward, contentDescription = "查看")
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(20.dp)) }

            // ========== 关注我 ==========
            item {
                SectionHeader("关注我")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SocialLinkItem(
                            iconRes = R.drawable.ic_weibo,
                            name = "微博",
                            onClick = { uriHandler.openUri("https://weibo.com/u/6548741952") },
                        )
                        SocialLinkItem(
                            iconRes = R.drawable.ic_douyin,
                            name = "抖音",
                            onClick = { uriHandler.openUri("https://v.douyin.com/wl0wb4k3cNQ/") },
                        )
                        SocialLinkItem(
                            iconRes = R.drawable.ic_bilibili,
                            name = "B站",
                            onClick = { uriHandler.openUri("https://space.bilibili.com/431227749") },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }

    // Update dialog
    when (val result = updateResult) {
        is UpdateResult.UpdateAvailable -> {
            androidx.compose.ui.window.Dialog(onDismissRequest = { viewModel.dismissUpdate() }) {
                Card(modifier = Modifier.padding(24.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(text = "发现新版本", style = MiuixTheme.textStyles.title2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "v${result.currentVersion} → v${result.latestVersion}",
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "更新内容：", style = MiuixTheme.textStyles.title2)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.releaseNotes.ifEmpty { "暂无更新说明" },
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .padding(bottom = 4.dp),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(onClick = { viewModel.dismissUpdate() }) {
                                Text("稍后")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(onClick = {
                                uriHandler.openUri(result.downloadUrl)
                                viewModel.dismissUpdate()
                            }) {
                                Text("去更新")
                            }
                        }
                    }
                }
            }
        }
        else -> {}
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MiuixTheme.textStyles.title1,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SocialLinkItem(
    iconRes: Int,
    name: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = name,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = name, style = MiuixTheme.textStyles.title2)
    }
}
