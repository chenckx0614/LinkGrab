package com.linkgrab.app.ui.liquid

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.IOSGreen
import com.linkgrab.app.ui.theme.IOSRed
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.isInDarkTheme
import kotlin.math.roundToInt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.SystemUpdateAlt

/**
 * iOS 26 液态玻璃弹窗外壳。删除确认、更新提示等自定义弹窗共用，避免每处都重复
 * 配一遍窗口模糊。
 *
 * 为什么不用 backdrop 库的玻璃：Compose [Dialog] 是独立窗口，库的 LayerBackdrop
 * 录制层与坐标系都跨不过窗口，采样不到主窗口内容。所以毛玻璃走系统级
 * FLAG_BLUR_BEHIND（minSdk 31 全设备可用）—— 系统把弹窗身后的 App 内容实时模糊，
 * 面板只压一层半透明玻璃底色。
 * 省电模式等场景系统会关闭跨窗口模糊（isCrossWindowBlurEnabled=false），此时
 * 回退为不透明底色 + 加深压暗，保证可读性。
 */
@Composable
fun IOS26GlassDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val view = LocalView.current
        val density = LocalDensity.current
        val dark = isInDarkTheme()
        var blurEnabled by remember { mutableStateOf(true) }
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) {
                blurEnabled = window.windowManager.isCrossWindowBlurEnabled
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = with(density) { 24.dp.toPx().roundToInt() }
                    // 有模糊时轻轻压暗即可；没有模糊时加深，靠暗度分隔前景
                    dimAmount = if (blurEnabled) 0.10f else 0.42f
                }
            }
            onDispose { }
        }

        val glassBase = iosGlass()
        val stroke = iosGlassStroke()
        val glassColor = glassBase.copy(alpha = if (blurEnabled) 0.72f else 0.96f)
        // 玻璃高光：顶部一道淡白渐变，模拟液态玻璃的受光面
        val highlightBrush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.08f else 0.16f),
            1f to Color.Transparent,
        )
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(glassColor)
                .drawBehind { drawRect(highlightBrush) }
                .border(1.dp, stroke, RoundedCornerShape(28.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }
    }
}

/**
 * 玻璃弹窗里的操作按钮。
 * [primary] 为主操作（实色蓝），否则为次级（半透明玻璃底 + 描边）。
 */
@Composable
fun GlassDialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(IOSCornerSmall)
            .then(
                if (primary) {
                    Modifier.background(if (enabled) iosBlue() else iosBlue().copy(alpha = 0.38f))
                } else {
                    Modifier
                        .background(iosSecondaryLabel().copy(alpha = 0.18f))
                        .border(1.dp, iosGlassStroke(), IOSCornerSmall)
                },
            )
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.White else iosLabel().copy(alpha = if (enabled) 1f else 0.4f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
    }
}

/** 弹窗顶部的主图标：圆形底 + 图标，与整套 iOS 26 弹窗语言一致 */
@Composable
private fun GlassDialogHeroIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

// MARK: - 更新弹窗

/**
 * 更新提示弹窗，信息架构移植自 Nexio 课程表（hyper_schedule）的 UpdateDialog：
 *
 * - **标题/摘要随状态变化**：发现新版本 → 正在下载 → 下载完成 → 已调起安装
 * - **更新说明正文**：仅在待下载时展示，下载/安装时让位给进度条
 * - **主/次按钮文案随状态变化**：稍后 / 后台下载、更新 / 正在下载 / 安装 / 重试
 * - 按钮带 Confirm 触觉反馈
 * - **任何时候都能关闭**：系统安装器可以被用户取消，弹窗不能因此卡死
 *
 * 视觉沿用本项目的 iOS 26 玻璃弹窗（[IOS26GlassDialog]），而非原生的 Material
 * AlertDialog —— 原来的原生弹窗标题/正文/按钮间距和圆角跟整套 iOS 风格不一致。
 */
@Composable
fun IOS26UpdateDialog(
    currentVersion: String,
    latestVersion: String,
    releaseNotes: String,
    downloadStatus: com.linkgrab.app.update.UpdateDownloadStatus,
    /** 有直链时主按钮走「下载 → 安装」，没有则只打开 release 页面 */
    hasDirectDownload: Boolean = true,
    onPrimaryAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isDownloading = downloadStatus is com.linkgrab.app.update.UpdateDownloadStatus.Downloading
    val isTerminal = downloadStatus is com.linkgrab.app.update.UpdateDownloadStatus.Installed

    val title = when (downloadStatus) {
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloading -> "正在下载"
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloaded -> "下载完成"
        is com.linkgrab.app.update.UpdateDownloadStatus.Installed -> "已调起安装"
        is com.linkgrab.app.update.UpdateDownloadStatus.Error -> "下载失败"
        is com.linkgrab.app.update.UpdateDownloadStatus.Idle -> "发现新版本"
    }
    // 摘要照搬 Nexio 的「最新版本: x」，并补上当前版本便于对照
    val summary = "最新版本：v$latestVersion（当前 v$currentVersion）"

    val primaryLabel = when (downloadStatus) {
        is com.linkgrab.app.update.UpdateDownloadStatus.Idle ->
            if (hasDirectDownload) "更新" else "去浏览器下载"
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloading -> "正在下载"
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloaded -> "安装"
        is com.linkgrab.app.update.UpdateDownloadStatus.Installed -> "已调起安装"
        is com.linkgrab.app.update.UpdateDownloadStatus.Error -> "重试"
    }
    // 次按钮同样随状态变化：下载中叫「后台下载」（关弹窗但继续下载），
    // 安装中叫「关闭」，保证任何时候都有退出入口
    val secondaryLabel = when (downloadStatus) {
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloading -> "后台下载"
        is com.linkgrab.app.update.UpdateDownloadStatus.Installed -> "关闭"
        else -> "稍后"
    }

    val (heroIcon, heroTint, heroBackground) = when (downloadStatus) {
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloading ->
            Triple(Icons.Default.Downloading, iosBlue(), iosBlue().copy(alpha = 0.12f))
        is com.linkgrab.app.update.UpdateDownloadStatus.Downloaded ->
            Triple(Icons.Default.InstallMobile, IOSGreen, IOSGreen.copy(alpha = 0.12f))
        is com.linkgrab.app.update.UpdateDownloadStatus.Installed ->
            Triple(Icons.Default.CheckCircleOutline, IOSGreen, IOSGreen.copy(alpha = 0.12f))
        is com.linkgrab.app.update.UpdateDownloadStatus.Error ->
            Triple(Icons.Default.ErrorOutline, iosError(), iosError().copy(alpha = 0.12f))
        is com.linkgrab.app.update.UpdateDownloadStatus.Idle ->
            if (hasDirectDownload) {
                Triple(Icons.Default.SystemUpdateAlt, iosBlue(), iosBlue().copy(alpha = 0.12f))
            } else {
                Triple(Icons.AutoMirrored.Filled.OpenInNew, iosBlue(), iosBlue().copy(alpha = 0.12f))
            }
    }

    IOS26GlassDialog(onDismissRequest = onDismiss) {
        GlassDialogHeroIcon(
            icon = heroIcon,
            tint = heroTint,
            background = heroBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosLabel(),
            letterSpacing = (-0.3).sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            summary,
            fontSize = 14.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
        )

        // 更新说明：参照 Nexio，只在待下载时展示，下载/安装时让位给进度条
        if (releaseNotes.isNotBlank() && downloadStatus is com.linkgrab.app.update.UpdateDownloadStatus.Idle) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                releaseNotes.take(300),
                fontSize = 14.sp,
                color = iosSecondaryLabel(),
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        when (downloadStatus) {
            is com.linkgrab.app.update.UpdateDownloadStatus.Downloading -> {
                IOS26LinearProgress(progress = downloadStatus.progress / 100f)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${downloadStatus.progress}%",
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                )
            }
            is com.linkgrab.app.update.UpdateDownloadStatus.Error -> {
                Text(
                    downloadStatus.message,
                    fontSize = 13.sp,
                    color = iosError(),
                    textAlign = TextAlign.Center,
                )
            }
            is com.linkgrab.app.update.UpdateDownloadStatus.Downloaded -> {
                Text(
                    "下载完成，点击下方「安装」",
                    fontSize = 13.sp,
                    color = IOSGreen,
                    textAlign = TextAlign.Center,
                )
            }
            else -> Unit
        }

        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassDialogButton(
                text = secondaryLabel,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            GlassDialogButton(
                text = primaryLabel,
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f),
                enabled = !isDownloading && !isTerminal,
                primary = true,
            )
        }
    }
}

/** iOS 风格的细条进度：圆角、蓝条、圆头 */
@Composable
private fun IOS26LinearProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        color = iosBlue(),
        trackColor = iosSecondaryLabel().copy(alpha = 0.18f),
        strokeCap = StrokeCap.Round,
    )
}

/** 次级按钮（玻璃底 + 描边）：与 [GlassDialogButton] 同源，语义更明确 */
@Composable
fun GlassDialogSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    GlassDialogButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        primary = false,
    )
}

/** 删除确认弹窗里的红色主按钮，供复用玻璃外壳的弹窗保持一致的按压手感 */
@Composable
fun GlassDialogDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(IOSCornerSmall)
            .background(IOSRed)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
