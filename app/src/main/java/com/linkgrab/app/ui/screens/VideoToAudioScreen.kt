package com.linkgrab.app.ui.screens

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class AudioFormat(val name: String, val ext: String, val desc: String)

private val audioFormats = listOf(
    AudioFormat("M4A", "m4a", "AAC 编码，体积小，兼容性好"),
    AudioFormat("MP3", "mp3", "最通用的音频格式"),
    AudioFormat("WAV", "wav", "无损，体积大"),
    AudioFormat("OGG", "ogg", "开源格式，体积小"),
    AudioFormat("FLAC", "flac", "无损压缩，音质最佳"),
)

@Composable
fun VideoToAudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("选择视频文件") }
    var selectedFormat by remember { mutableStateOf(audioFormats[0]) }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            statusText = "正在提取音频..."
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    extractAudioFFmpeg(context, it, selectedFormat) { text ->
                        statusText = text
                    }
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "提取完成！"
                        Toast.makeText(context, "音频已保存 (${selectedFormat.name})", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "提取失败: ${e.message}"
                    }
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "视频转音频",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎵", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "视频转音频",
                style = MiuixTheme.textStyles.title1,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "从视频中提取音频文件",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Format selection
            Text(
                text = "输出格式",
                style = MiuixTheme.textStyles.title2,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                audioFormats.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { format ->
                            FormatChip(
                                format = format,
                                selected = selectedFormat == format,
                                onClick = { selectedFormat = format },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Fill remaining space
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Status
            Text(
                text = statusText,
                color = if (statusText.contains("失败")) MiuixTheme.colorScheme.error
                else MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )

            if (isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action button
            Button(
                onClick = { videoPicker.launch("video/*") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = !isProcessing,
            ) {
                Text("选择视频文件", style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun FormatChip(
    format: AudioFormat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primaryContainer
                else MiuixTheme.colorScheme.surfaceVariant
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = format.name,
            fontWeight = FontWeight.Bold,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = format.desc,
            fontSize = 10.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private suspend fun extractAudioFFmpeg(
    context: android.content.Context,
    videoUri: Uri,
    format: AudioFormat,
    onProgress: (String) -> Unit,
) = withContext(Dispatchers.IO) {
    // Copy video to temp file
    val inputStream = context.contentResolver.openInputStream(videoUri)
    val tempFile = java.io.File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.mp4")
    inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }

    // Output file
    val outputFile = java.io.File(context.cacheDir, "output_audio.${format.ext}")

    // FFmpeg command
    val codec = when (format.ext) {
        "mp3" -> "-codec:a libmp3lame -b:a 192k"
        "flac" -> "-codec:a flac"
        "wav" -> "-codec:a pcm_s16le"
        "ogg" -> "-codec:a libvorbis -q:a 4"
        else -> "-codec:a copy"
    }
    val command = "-i ${tempFile.absolutePath} -vn $codec ${outputFile.absolutePath}"

    val session = FFmpegKit.execute(command)
    if (!ReturnCode.isSuccess(session.returnCode)) {
        tempFile.delete()
        throw Exception("转换失败")
    }

    // Save to MediaStore
    val mimeType = "audio/${format.ext}"
    val contentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "LinkGrab_${System.currentTimeMillis()}.${format.ext}")
        put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LinkGrab")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("无法创建文件")

    resolver.openOutputStream(outputUri)?.use { output ->
        outputFile.inputStream().use { input -> input.copyTo(output) }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(outputUri, contentValues, null, null)
    }

    tempFile.delete()
    outputFile.delete()
    onProgress("提取完成！")
}
