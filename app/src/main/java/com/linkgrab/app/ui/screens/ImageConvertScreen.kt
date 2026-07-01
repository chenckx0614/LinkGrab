package com.linkgrab.app.ui.screens

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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

@Composable
fun ImageConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("选择图片文件") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
        statusText = "已选择图片"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = "图片转格式",
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

            Text(text = "🖼️ → 📄", style = MiuixTheme.textStyles.title1)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "转换图片格式 (JPG/PNG/WebP/BMP)", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

            Spacer(modifier = Modifier.height(24.dp))

            // Preview
            selectedUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(text = statusText, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)

            if (isProcessing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
            ) {
                Text("选择图片")
            }

            if (selectedUri != null && !isProcessing) {
                Spacer(modifier = Modifier.height(12.dp))

                // Format buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FormatButton("转 JPG", Modifier.weight(1f)) {
                        isProcessing = true
                        statusText = "转换中..."
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                convertImage(context, selectedUri!!, "jpg") { p, s ->
                                    // progress callback
                                }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换完成！"
                                    Toast.makeText(context, "JPG 已保存", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换失败: ${e.message}"
                                }
                            }
                        }
                    }
                    FormatButton("转 PNG", Modifier.weight(1f)) {
                        isProcessing = true
                        statusText = "转换中..."
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                convertImage(context, selectedUri!!, "png") { _, _ -> }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换完成！"
                                    Toast.makeText(context, "PNG 已保存", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换失败: ${e.message}"
                                }
                            }
                        }
                    }
                    FormatButton("转 WebP", Modifier.weight(1f)) {
                        isProcessing = true
                        statusText = "转换中..."
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                convertImage(context, selectedUri!!, "webp") { _, _ -> }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换完成！"
                                    Toast.makeText(context, "WebP 已保存", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换失败: ${e.message}"
                                }
                            }
                        }
                    }
                    FormatButton("转 BMP", Modifier.weight(1f)) {
                        isProcessing = true
                        statusText = "转换中..."
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                convertImage(context, selectedUri!!, "bmp") { _, _ -> }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换完成！"
                                    Toast.makeText(context, "BMP 已保存", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    statusText = "转换失败: ${e.message}"
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text)
    }
}

private suspend fun convertImage(
    context: android.content.Context,
    uri: Uri,
    format: String,
    onProgress: (Float, String) -> Unit,
) = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(uri) ?: throw Exception("无法读取图片")
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream.close()

    if (bitmap == null) throw Exception("无法解码图片")

    val mimeType = when (format) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        else -> "image/jpeg"
    }

    val ext = format.uppercase()
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "LinkGrab_${System.currentTimeMillis()}.$format")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinkGrab")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("无法创建文件")

    resolver.openOutputStream(outputUri)?.use { outputStream ->
        val compressFormat = when (format) {
            "jpg" -> Bitmap.CompressFormat.JPEG
            "png" -> Bitmap.CompressFormat.PNG
            "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
        bitmap.compress(compressFormat, 95, outputStream)
    }

    bitmap.recycle()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(outputUri, contentValues, null, null)
    }

    onProgress(1f, "转换完成！")
}
