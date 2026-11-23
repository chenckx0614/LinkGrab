package com.linkgrab.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.data.convert.AAC_MIME
import com.linkgrab.app.data.convert.AudioConverter
import com.linkgrab.app.data.convert.AudioTarget
import com.linkgrab.app.data.convert.ConvertedFile
import com.linkgrab.app.data.convert.SourceInfo
import com.linkgrab.app.data.repository.SettingsRepository
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosSeparator
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.ui.util.MediaActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VideoToAudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val saveTree by settings.customSaveTree.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var selected by remember { mutableStateOf<Uri?>(null) }
    var info by remember { mutableStateOf<SourceInfo?>(null) }
    var target by remember { mutableStateOf(AudioTarget.M4A) }
    var running by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ConvertedFile?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    // 非 AAC 音轨（opus / vorbis）没法原样塞进 m4a，只能走 WAV
    val m4aSupported = info?.audioMime.let { it == null || it == AAC_MIME }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selected = uri
            info = null
            error = null
            result = null
            fraction = 0f
            status = ""
            scope.launch {
                val loaded = AudioConverter.inspect(context, uri)
                info = loaded
                if (loaded.audioMime != null && loaded.audioMime != AAC_MIME) target = AudioTarget.WAV
            }
        }
    }

    val startConversion = {
        val source = selected
        if (source != null && !running) {
            running = true
            error = null
            result = null
            fraction = 0f
            status = "准备中"
            val format = target
            job = scope.launch {
                try {
                    val dest = AudioConverter.convert(context, source, format, saveTree) { value, label ->
                        fraction = value
                        status = label
                    }
                    val (name, size) = describeOutput(context, dest, format)
                    result = ConvertedFile(name, dest.toString(), size)
                    status = "已保存"
                    fraction = 1f
                } catch (cancel: CancellationException) {
                    status = "已取消"
                    fraction = 0f
                    throw cancel
                } catch (e: Exception) {
                    error = e.message ?: "提取失败"
                    status = ""
                } finally {
                    running = false
                    job = null
                }
            }
        }
    }

    ConvertPage(
        title = "视频转音频",
        subtitle = "把视频里的声音单独存出来",
        emoji = "🎵",
        onBack = onBack,
    ) {
        IOS26Panel(Modifier.fillMaxWidth()) {
            Text(
                text = "输出格式",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabel(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AudioTarget.entries.forEach { format ->
                    AudioTargetChip(
                        format = format,
                        selected = target == format,
                        enabled = !running && (format != AudioTarget.M4A || m4aSupported),
                        onClick = { target = format },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(text = target.hint, color = iosSecondaryLabel(), fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (saveTree.isNullOrBlank()) "保存到 音乐/LinkGrab" else "保存到你选的文件夹",
                color = iosSecondaryLabel(),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        IOS26Panel(Modifier.fillMaxWidth()) {
            Text(
                text = "视频",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabel(),
            )
            Spacer(Modifier.height(10.dp))
            val picked = selected
            if (picked == null) {
                Text(text = "还没选视频", color = iosSecondaryLabel(), fontSize = 14.sp)
            } else {
                Text(
                    text = info?.name ?: "读取中…",
                    color = iosLabel(),
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val loaded = info
                if (loaded != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${fmtDuration(loaded.durationMs)} · ${fmtBytes(loaded.sizeBytes)}",
                        color = iosSecondaryLabel(),
                        fontSize = 13.sp,
                    )
                    if (!m4aSupported) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "这个视频的音轨不是 AAC，只能转 WAV",
                            color = iosSecondaryLabel(),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        if (running) {
            Spacer(Modifier.height(14.dp))
            IOS26Panel(Modifier.fillMaxWidth()) {
                ConvertProgress(fraction, status)
            }
        }

        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = error.orEmpty(),
                color = iosError(),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        when {
            running -> IOS26OutlinedButton(
                text = "取消",
                onClick = { job?.cancel() },
                modifier = Modifier.fillMaxWidth(),
            )
            selected == null -> IOS26Button(
                text = "选择视频",
                onClick = { picker.launch("video/*") },
                modifier = Modifier.fillMaxWidth(),
            )
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IOS26OutlinedButton(
                    text = "换个视频",
                    onClick = { picker.launch("video/*") },
                    modifier = Modifier.weight(1f),
                )
                IOS26Button(
                    text = "开始提取",
                    onClick = { startConversion() },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        result?.let { file ->
            Spacer(Modifier.height(20.dp))
            IOS26Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = "已保存",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                )
                Spacer(Modifier.height(4.dp))
                ConvertFileRow(
                    name = file.name,
                    detail = file.detail,
                    onOpen = { runCatching { MediaActions.open(context, Uri.parse(file.uri)) } },
                    onShare = { runCatching { MediaActions.share(context, Uri.parse(file.uri)) } },
                )
            }
        }
    }
}

@Composable
private fun AudioTargetChip(
    format: AudioTarget,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) iosBlue() else iosTertiaryBackground(),
        animationSpec = tween(180),
        label = "audioTargetChip",
    )
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) background else background.copy(alpha = 0.4f))
            .border(
                width = 0.5.dp,
                color = if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format.label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = when {
                selected && enabled -> Color.White
                enabled -> iosLabel()
                else -> iosSecondaryLabel()
            },
        )
    }
}

private suspend fun describeOutput(
    context: android.content.Context,
    uri: Uri,
    format: AudioTarget,
): Pair<String, String> = withContext(Dispatchers.IO) {
    var name = "LinkGrab.${format.ext}"
    var size = 0L
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)?.let { name = it }
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
    }
    val detail = if (size > 0) "${format.label} · ${fmtBytes(size)}" else format.label
    name to detail
}
