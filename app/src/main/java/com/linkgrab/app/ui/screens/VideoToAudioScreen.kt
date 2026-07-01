package com.linkgrab.app.ui.screens

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.media.MediaExtractor
import android.media.MediaFormat
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
import java.nio.ByteBuffer

@Composable
fun VideoToAudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var statusText by remember { mutableStateOf("选择视频文件") }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isProcessing = true
            progress = 0f
            statusText = "正在提取音频..."

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    extractAudio(context, it) { p, status ->
                        progress = p
                        statusText = status
                    }
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        isProcessing = false
                        statusText = "提取完成！"
                        Toast.makeText(context, "音频已保存到音乐目录", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "🎬 → 🎵", style = MiuixTheme.textStyles.title1)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "从视频中提取音频", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = statusText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

            if (isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { videoPicker.launch("video/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
            ) {
                Text("选择视频文件")
            }
        }
    }
}

private suspend fun extractAudio(
    context: android.content.Context,
    videoUri: Uri,
    onProgress: (Float, String) -> Unit,
) = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, videoUri, null)

    // Find audio track
    var audioTrackIndex = -1
    for (i in 0 until extractor.trackCount) {
        val format = extractor.getTrackFormat(i)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/")) {
            audioTrackIndex = i
            break
        }
    }

    if (audioTrackIndex == -1) {
        throw Exception("视频中没有音频轨道")
    }

    extractor.selectTrack(audioTrackIndex)
    val format = extractor.getTrackFormat(audioTrackIndex)
    val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/mp4a-latm"
    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    // Create output file in Music/LinkGrab
    val contentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "LinkGrab_${System.currentTimeMillis()}.m4a")
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4a-latm")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LinkGrab")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("无法创建文件")

    resolver.openOutputStream(outputUri)?.use { outputStream ->
        val buffer = ByteArray(1024 * 1024) // 1MB buffer
        var isEOS = false
        while (!isEOS) {
            val sampleSize = extractor.readSampleData(ByteBuffer.wrap(buffer), 0)
            if (sampleSize < 0) {
                isEOS = true
            } else {
                outputStream.write(buffer, 0, sampleSize)
                extractor.advance()
            }
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(outputUri, contentValues, null, null)
    }

    extractor.release()
    onProgress(1f, "提取完成！")
}
