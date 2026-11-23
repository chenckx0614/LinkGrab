package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linkgrab.app.ui.theme.IOS26BlurHeader
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.ui.theme.pageHeaderHeight
import com.linkgrab.app.ui.theme.pageHeaderScrollThresholdPx
import java.util.Locale

/** 两个转换工具共用同一个页面骨架：模糊顶栏 + 图标胶囊 + 标题副标题 + 可滚动内容。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConvertPage(
    title: String,
    subtitle: String,
    emoji: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val headerBackdrop = rememberLayerBackdrop()
    val scrollState = rememberScrollState()
    val headerScrolled = scrollState.value > pageHeaderScrollThresholdPx()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = iosBackground(),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset + pageHeaderHeight())
                    .padding(horizontal = 20.dp)
                    .layerBackdrop(headerBackdrop)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                IOS26IconCapsule(emoji, iosBlue())
                Spacer(Modifier.height(20.dp))
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = subtitle, color = iosSecondaryLabel(), fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
                content()
                Spacer(Modifier.height(40.dp))
            }
            IOS26BlurHeader(
                title = title,
                backdrop = headerBackdrop,
                scrolled = headerScrolled,
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = onBack,
            )
        }
    }
}

/** 进度条 + 一行说明。转换过程中每帧都在刷，直接画不补间，免得弹簧动画追不上真实进度。 */
@Composable
internal fun ConvertProgress(fraction: Float, label: String) {
    val value = fraction.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = iosSecondaryLabel(),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(value * 100).toInt()}%",
                color = iosSecondaryLabel(),
                fontSize = 14.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(iosTertiaryBackground()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(iosBlue()),
            )
        }
    }
}

/** 成品一行：文件名 + 详情 + 打开/分享。 */
@Composable
internal fun ConvertFileRow(
    name: String,
    detail: String?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = iosLabel(),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    color = iosSecondaryLabel(),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        RoundIcon(Icons.Default.OpenInNew, "打开", onOpen)
        RoundIcon(Icons.Default.Share, "分享", onShare)
    }
}

@Composable
private fun RoundIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iosBlue(),
            modifier = Modifier.size(20.dp),
        )
    }
}

internal fun fmtBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.CHINA, "%.2fGB", bytes / 1073741824.0)
    bytes >= 1L shl 20 -> String.format(Locale.CHINA, "%.1fMB", bytes / 1048576.0)
    bytes >= 1L shl 10 -> String.format(Locale.CHINA, "%.0fKB", bytes / 1024.0)
    else -> "${bytes}B"
}

internal fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.CHINA, "%d:%02d", minutes, seconds)
}
