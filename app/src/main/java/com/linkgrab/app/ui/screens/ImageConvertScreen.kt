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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import com.linkgrab.app.ui.theme.IOS26BackButton
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26IconCapsule
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.iosBackground
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.ui.theme.iosSeparator

data class ImageFormat(val name: String, val ext: String, val mimeType: String)

private val imageFormats = listOf(
    ImageFormat("JPG", "jpg", "image/jpeg"),
    ImageFormat("PNG", "png", "image/png"),
    ImageFormat("WebP", "webp", "image/webp"),
    ImageFormat("BMP", "bmp", "image/bmp"),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImageConvertScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("选择图片文件") }
    var selectedFormat by remember { mutableStateOf(imageFormats[0]) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedUri = uri
        statusText = "已选择图片"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = iosBackground(),
        topBar = {
            TopAppBar(
                title = { Text("图片转格式", fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp) },
                navigationIcon = {
                    IOS26BackButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = iosBackground().copy(alpha = 0.85f),
                    titleContentColor = iosLabel(),
                    navigationIconContentColor = iosLabel(),
                ),
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
            Spacer(modifier = Modifier.height(32.dp))

            IOS26IconCapsule("🖼", iosBlue())

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "图片转格式",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = iosLabel(),
                letterSpacing = (-0.4).sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "转换图片格式 (JPG/PNG/WebP/BMP)",
                color = iosSecondaryLabel(),
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            selectedUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = "预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(IOSCornerSmall),
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            IOS26Panel(Modifier.fillMaxWidth()) {
                Text(
                    text = "目标格式",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    color = iosLabel(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    imageFormats.forEach { format ->
                        FormatChipSmall(
                            format = format,
                            selected = selectedFormat == format,
                            onClick = { selectedFormat = format },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusText,
                    color = if (statusText.contains("失败")) MaterialTheme.colorScheme.error
                    else iosSecondaryLabel(),
                    fontSize = 14.sp,
                )

                if (isProcessing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = iosBlue())
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                IOS26OutlinedButton(
                    text = "选择图片",
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                )

                IOS26Button(
                    text = "转换",
                    onClick = {
                        selectedUri?.let { uri ->
                            isProcessing = true
                            statusText = "转换为 ${selectedFormat.name} 中..."
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    convertImage(context, uri, selectedFormat)
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        statusText = "转换完成！"
                                        Toast.makeText(context, "${selectedFormat.name} 已保存", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isProcessing = false
                                        statusText = "转换失败: ${e.message}"
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedUri != null && !isProcessing,
                )
            }
        }
    }
}

@Composable
private fun FormatChipSmall(
    format: ImageFormat,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by androidx.compose.animation.animateColorAsState(
        if (selected) iosBlue() else iosTertiaryBackground(),
        animationSpec = androidx.compose.animation.core.tween(180),
        label = "formatChipBg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (selected) 0.5.dp else 0.5.dp,
                color = if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = format.name,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (selected) Color.White else iosLabel(),
        )
    }
}

private suspend fun convertImage(
    context: android.content.Context,
    imageUri: Uri,
    format: ImageFormat,
) = withContext(Dispatchers.IO) {
    val inputStream = context.contentResolver.openInputStream(imageUri)
    val bitmap = BitmapFactory.decodeStream(inputStream)
    inputStream?.close()

    if (bitmap == null) throw Exception("无法解码图片")

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "LinkGrab_${System.currentTimeMillis()}.${format.ext}")
        put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LinkGrab")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        ?: throw Exception("无法创建文件")

    resolver.openOutputStream(outputUri)?.use { outputStream ->
        val compressFormat = when (format.ext) {
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
}
