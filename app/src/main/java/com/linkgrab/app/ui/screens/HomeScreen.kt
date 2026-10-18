package com.linkgrab.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.linkgrab.app.data.model.Platform
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS27CircularProgressIndicator
import com.linkgrab.app.ui.theme.IOS27TextField
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosError
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.viewmodel.MainViewModel

@Composable
fun HomeScreen(viewModel: MainViewModel, onNavigateToHistory: () -> Unit = {}, initialShareText: String? = null) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }
    // 分享进来的文本只负责回填输入框：解析由 MainActivity 统一做一次
    //（以前这里也调 parseUrl，一次分享会解析两遍）。
    LaunchedEffect(initialShareText) {
        initialShareText?.takeIf { it.isNotBlank() }?.let { input = it }
    }

    // ---- 剪贴板自动识别（3.0.16）----
    val clipboardDetect by viewModel.clipboardDetect.collectAsState()
    var clipboardLink by remember { mutableStateOf<String?>(null) }
    var dismissedLink by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, clipboardDetect) {        if (!clipboardDetect) clipboardLink = null
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && clipboardDetect) {
                val link = readClipboard(context)?.let { viewModel.clipboardLinkIn(it) }
                clipboardLink = if (link != null && link != dismissedLink && link != input) link else null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 二维码：从相册选一张图，用 ZXing 解出里面的链接（不做相机预览，
    // 这样不用申请 CAMERA 权限；截图/别人发来的分享码都能用）
    val qrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val decoded = decodeQrFromImage(context, uri)
        if (decoded.isNullOrBlank()) {
            Toast.makeText(context, "这张图里没读出二维码", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val link = viewModel.supportedLinkIn(decoded) ?: decoded
        input = link
        viewModel.parseUrl(link)
    }
    // result 是持久状态（结果页返回后仍非空），据此跳转会造成「返回首页→
    // 立刻再跳回结果页」的死循环。跳转改由 MainViewModel.navigateToResult
    // 这个一次性事件驱动，统一在 AppNavigation 里处理。
    IOS26Canvas {
        IOS26StickyPage(
            title = "LinkGrab",
            applyImePadding = true,
        ) {
            item {
                IOS26PageHeader("LinkGrab", "保存你真正想留下的内容", topPadding = 0.dp)
            }
            clipboardLink?.let { link ->
                item {
                    IOS26Panel(Modifier.fillMaxWidth()) {
                        Text(
                            "检测到${viewModel.platformLabelOf(link)}链接",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = iosLabel(),
                            letterSpacing = (-0.2).sp,
                        )
                        Text(
                            "剪贴板里有一条可解析的分享链接",
                            color = iosSecondaryLabel(),
                            modifier = Modifier.padding(top = 3.dp),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IOS26Button(
                                "立即解析",
                                {
                                    clipboardLink = null
                                    input = link
                                    viewModel.parseUrl(link)
                                },
                                Modifier.weight(1f),
                            )
                            IOS26OutlinedButton(
                                "忽略",
                                {
                                    dismissedLink = link
                                    clipboardLink = null
                                },
                                Modifier.weight(0.55f),
                            )
                        }
                    }
                }
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "粘贴一个分享链接",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "抖音和小红书链接会自动识别",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    IOS27TextField(
                        value = input,
                        onValueChange = { input = it },
                        label = "分享链接",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = false,
                    )
                    AnimatedVisibility(state.error != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(
                            state.error.orEmpty(),
                            color = iosError(),
                            modifier = Modifier.padding(top = 10.dp),
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IOS26OutlinedButton("粘贴", { paste(context) { input = it } }, Modifier.weight(0.8f))
                        IOS26OutlinedButton("二维码", { qrLauncher.launch("image/*") }, Modifier.weight(0.8f))
                        IOS26Button("解析", { viewModel.parseUrl(input) }, Modifier.weight(1f), input.isNotBlank() && !state.isLoading)
                    }
                    if (state.isLoading) {
                        Spacer(Modifier.height(14.dp))
                        IOS27CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item {
                // 平台识别标签：任何 http 链接都会显示「网页」（通用直链抓取）
                val platformName = viewModel.platformNameOfInput(input)
                if (platformName != null) {
                    Text(
                        "已识别：$platformName",
                        color = iosBlue(),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 14.sp,
                    )
                }
            }
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Text(
                        "最近解析",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = iosLabel(),
                        letterSpacing = (-0.3).sp,
                    )
                    Text(
                        "继续查看或再次下载已保存的内容",
                        color = iosSecondaryLabel(),
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    IOS26Button("打开历史记录", onNavigateToHistory, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun paste(context: Context, onPaste: (String) -> Unit) {
    val value = readClipboard(context).orEmpty()
    if (value.isBlank()) Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show() else onPaste(value)
}

/** 读剪贴板首条文本；空/无权限时返回 null。Android 10+ 仅前台可读，正好符合这里的用法。 */
private fun readClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    return clipboard?.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.text
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}

/**
 * 从相册图片里识别二维码（ZXing core，纯 Java，不需要相机权限）。
 *
 * 大图先做一次采样缩小：4000×3000 的原图直接 binarize 又慢又吃内存，
 * 二维码识别只需要 1000px 左右就够。
 */
private fun decodeQrFromImage(context: Context, uri: android.net.Uri): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        android.graphics.BitmapFactory.decodeStream(it)
    } ?: return@runCatching null

    val scaled = if (bitmap.width > 1200) {
        val ratio = 1200f / bitmap.width
        android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            1200,
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        bitmap
    }

    val pixels = IntArray(scaled.width * scaled.height)
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    val source = com.google.zxing.RGBLuminanceSource(scaled.width, scaled.height, pixels)
    val binary = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    com.google.zxing.MultiFormatReader().decode(binary).text
}.getOrNull()
