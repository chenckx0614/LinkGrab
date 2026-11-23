package com.linkgrab.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.linkgrab.app.data.convert.ConvertedFile
import com.linkgrab.app.data.convert.ImageConverter
import com.linkgrab.app.data.convert.ImageTarget
import com.linkgrab.app.data.repository.SettingsRepository
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosSeparator
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.ui.util.MediaActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val MAX_BATCH = 30

@Composable
fun ImageConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val saveTree by settings.customSaveTree.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var sources by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var target by remember { mutableStateOf(ImageTarget.JPG) }
    var running by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(0f) }
    var status by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ConvertedFile>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { picked ->
        if (picked.isNotEmpty()) {
            sources = picked.take(MAX_BATCH)
            results = emptyList()
            error = null
            status = ""
            fraction = 0f
        }
    }

    val startConversion = {
        val list = sources
        if (list.isNotEmpty() && !running) {
            running = true
            error = null
            results = emptyList()
            fraction = 0f
            status = "准备转换"
            val format = target
            job = scope.launch {
                val done = mutableListOf<ConvertedFile>()
                try {
                    list.forEachIndexed { index, uri ->
                        status = "正在转换 ${index + 1}/${list.size}"
                        fraction = index.toFloat() / list.size
                        val dest = ImageConverter.convert(context, uri, format, saveTree)
                        val (name, size) = describe(context, dest, format)
                        done += ConvertedFile(name, dest.toString(), size)
                        results = done.toList()
                        fraction = (index + 1).toFloat() / list.size
                    }
                    status = "已保存 ${done.size} 个文件"
                } catch (cancel: CancellationException) {
                    status = "已取消"
                    fraction = 0f
                    throw cancel
                } catch (e: Exception) {
                    error = e.message ?: "转换失败"
                    status = if (done.isEmpty()) "没有转换成功" else "已保存 ${done.size} 个，后面的失败了"
                } catch (e: Throwable) {
                    // 解码超大图可能抛 OutOfMemoryError：它是 Error 不是 Exception，
                    // 不接住会直接把应用带走，这里降级成一条提示。
                    error = "这张图太大，手机内存不够，换张小一点的试试"
                    status = if (done.isEmpty()) "没有转换成功" else "已保存 ${done.size} 个，后面的失败了"
                } finally {
                    running = false
                    job = null
                }
            }
        }
    }

    ConvertPage(
        title = "图片转换",
        subtitle = "一次可以转多张，格式随你挑",
        emoji = "🖼",
        onBack = onBack,
    ) {
        IOS26Panel(Modifier.fillMaxWidth()) {
            Text(
                text = "目标格式",
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
                ImageTarget.entries.forEach { format ->
                    ImageTargetChip(
                        format = format,
                        selected = target == format,
                        enabled = !running,
                        onClick = { target = format },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (saveTree.isNullOrBlank()) "保存到 图片/LinkGrab/转换" else "保存到你选的文件夹",
                color = iosSecondaryLabel(),
                fontSize = 13.sp,
            )
        }

        Spacer(Modifier.height(14.dp))

        IOS26Panel(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "待转换",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                    modifier = Modifier.weight(1f),
                )
                if (sources.isNotEmpty() && !running) {
                    Text(
                        text = "清空",
                        fontSize = 14.sp,
                        color = iosBlue(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                sources = emptyList()
                                results = emptyList()
                                status = ""
                                error = null
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            if (sources.isEmpty()) {
                Text(
                    text = "还没选图片，一次最多 $MAX_BATCH 张",
                    color = iosSecondaryLabel(),
                    fontSize = 14.sp,
                )
            } else {
                Text(
                    text = "已选 ${sources.size} 张",
                    color = iosSecondaryLabel(),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sources) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(IOSCornerSmall),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (running) {
            IOS26Panel(Modifier.fillMaxWidth()) {
                ConvertProgress(fraction, status)
            }
            Spacer(Modifier.height(14.dp))
        }

        if (!error.isNullOrBlank()) {
            Text(
                text = error.orEmpty(),
                color = iosError(),
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (running) {
                IOS26OutlinedButton(
                    text = "取消",
                    onClick = { job?.cancel() },
                    modifier = Modifier.weight(1f),
                )
            } else {
                IOS26OutlinedButton(
                    text = "选择图片",
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                )
                IOS26Button(
                    text = if (sources.size > 1) "转成 ${target.label}" else "转换",
                    onClick = { startConversion() },
                    modifier = Modifier.weight(1f),
                    enabled = sources.isNotEmpty(),
                )
            }
        }

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            IOS26Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = "已保存 ${results.size} 个文件",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                )
                Spacer(Modifier.height(4.dp))
                results.forEach { file ->
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
}

@Composable
private fun ImageTargetChip(
    format: ImageTarget,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) iosBlue() else iosTertiaryBackground(),
        animationSpec = tween(180),
        label = "imageTargetChip",
    )
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = 0.5.dp,
                color = if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format.label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) Color.White else iosLabel(),
        )
    }
}

/** 从落盘后的 uri 读真实文件名和大小 —— MediaStore 遇到重名会自动加后缀，不能拿原名字猜。 */
private fun describe(context: Context, uri: Uri, format: ImageTarget): Pair<String, String> {
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
    return name to detail
}
