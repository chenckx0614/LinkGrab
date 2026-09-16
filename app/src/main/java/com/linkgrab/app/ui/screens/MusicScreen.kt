package com.linkgrab.app.ui.screens

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.linkgrab.app.data.api.KuwoMusicParser
import com.linkgrab.app.data.model.MusicResult
import com.linkgrab.app.ui.theme.IOS26BlurHeader
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26HeaderHeight
import com.linkgrab.app.ui.theme.IOS26HeaderScrollThreshold
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS27CircularProgressIndicator
import com.linkgrab.app.ui.theme.IOS27TextField
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosSeparator
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private val qualityOptions = listOf(
    "128k" to "标准",
    "320k" to "高品",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val parser = remember { KuwoMusicParser() }

    var input by remember { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MusicResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var quality by remember { mutableStateOf("128k") }
    var downloading by remember { mutableStateOf(false) }

    fun doParse() {
        if (input.isBlank() || parsing) return
        parsing = true
        error = null
        result = null
        CoroutineScope(Dispatchers.Main).launch {
            val r = parser.parse(input)
            r.onSuccess { result = it }
                .onFailure { error = it.message ?: "解析失败" }
            parsing = false
        }
    }

    val headerBackdrop = rememberLayerBackdrop()
    val scrollState = rememberScrollState()
    val headerScrolled = scrollState.value >
        with(LocalDensity.current) { IOS26HeaderScrollThreshold.roundToPx() }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = iosBackground(),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset + IOS26HeaderHeight)
                    .padding(horizontal = 20.dp)
                    .layerBackdrop(headerBackdrop)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))
                IOS26IconCapsule("🎵", iosBlue())
                Spacer(Modifier.height(20.dp))
                Text(
                    "音乐解析下载",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "粘贴酷我音乐的分享链接",
                    color = iosSecondaryLabel(),
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(28.dp))

                // ---- 输入面板 ----
                IOS26Panel(Modifier.fillMaxWidth()) {
                    IOS27TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "酷我分享链接",
                        placeholder = "https://kuwo.cn/play_detail/…",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = false,
                    )
                    if (error != null) {
                        Text(
                            error.orEmpty(),
                            color = iosError(),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    IOS26Button(
                        text = if (parsing) "解析中…" else "解析",
                        onClick = { doParse() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ---- 结果面板 ----
                result?.let { music ->
                    Spacer(Modifier.height(20.dp))
                    IOS26Panel(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (music.coverUrl != null) {
                                AsyncImage(
                                    model = music.coverUrl,
                                    contentDescription = music.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(iosTertiaryBackground()),
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(iosBlue().copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("♪", fontSize = 26.sp, color = iosBlue())
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    music.title.ifEmpty { "未知歌曲" },
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = iosLabel(),
                                )
                                Text(
                                    music.artist.ifEmpty { "酷我音乐" },
                                    fontSize = 14.sp,
                                    color = iosSecondaryLabel(),
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }

                        // 音质选择：320k 拿不到时只留 128k。
                        val available = qualityOptions.filter { (q, _) ->
                            q == "128k" || music.playUrl320k != null
                        }
                        if (available.size > 1) {
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                available.forEach { (q, label) ->
                                    QualityChip(
                                        label = "$label $q",
                                        selected = quality == q,
                                        onClick = { quality = q },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        // 歌词。
                        if (music.lyric.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "歌词",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = iosSecondaryLabel(),
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(iosTertiaryBackground().copy(alpha = 0.5f))
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp),
                            ) {
                                Text(
                                    music.lyric,
                                    fontSize = 13.sp,
                                    color = iosLabel().copy(alpha = 0.85f),
                                    lineHeight = 20.sp,
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        IOS26Button(
                            text = when {
                                downloading -> "下载中…"
                                else -> "下载 $quality"
                            },
                            onClick = {
                                val url = if (quality == "320k") music.playUrl320k
                                    ?: music.playUrl128k else music.playUrl128k
                                if (url != null && !downloading) {
                                    downloading = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            downloadMusic(context, url, music)
                                            withContext(Dispatchers.Main) {
                                                downloading = false
                                                Toast.makeText(
                                                    context,
                                                    "已保存到音乐库",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            withContext(Dispatchers.Main) {
                                                downloading = false
                                                Toast.makeText(
                                                    context,
                                                    "下载失败：${e.message}",
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "目前只支持酷我音乐链接。在酷我音乐 App 里找到歌曲，" +
                        "点「分享 → 复制链接」，再粘贴到上面。",
                    color = iosSecondaryLabel(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(40.dp))
            }

            IOS26BlurHeader(
                title = "音乐解析下载",
                backdrop = headerBackdrop,
                scrolled = headerScrolled,
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun QualityChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by androidx.compose.animation.animateColorAsState(
        if (selected) iosBlue() else iosTertiaryBackground(),
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "qualityChipBg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = 0.5.dp,
                color = if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) Color.White else iosLabel(),
        )
    }
}

/** 用 OkHttp 把直链下载下来，写入系统音乐库（Music/LinkGrab）。 */
private suspend fun downloadMusic(
    context: Context,
    url: String,
    music: MusicResult,
) = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Safari/537.36",
        )
        .header("Range", "bytes=0-")
        .build()

    val safeName = buildString {
        if (music.artist.isNotEmpty()) append(music.artist).append(" - ")
        append(music.title.ifEmpty { "music_${music.rid}" })
    }.replace(Regex("""[\\/:*?"<>|]"""), "_")

    val contentValues = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.mp3")
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/LinkGrab")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("无法创建文件")

    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
            resolver.delete(uri, null, null)
            throw Exception("下载失败（HTTP ${resp.code}）")
        }
        val body = resp.body ?: run {
            resolver.delete(uri, null, null)
            throw Exception("空响应")
        }
        resolver.openOutputStream(uri)?.use { out ->
            body.byteStream().use { input -> input.copyTo(out) }
        } ?: run {
            resolver.delete(uri, null, null)
            throw Exception("无法写入文件")
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
    }
}
