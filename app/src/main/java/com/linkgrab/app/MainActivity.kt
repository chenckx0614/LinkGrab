package com.linkgrab.app

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import com.linkgrab.app.ui.navigation.AppNavigation
import com.linkgrab.app.ui.navigation.Screen
import com.linkgrab.app.ui.theme.LinkGrabTheme
import com.linkgrab.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    /**
     * 待处理的外部分享文本。用 State 而不是普通字段：应用已在运行时再分享
     * 进来（onNewIntent）也要能触发解析。解析完立刻置空，保证**只解析一次**
     *（以前 MainActivity 和 HomeScreen 各解析一次，一次分享发两遍请求）。
     */
    private val pendingShareText = mutableStateOf<String?>(null)

    /** 待跳转的路由（桌面快捷方式 / 小组件用 `linkgrab://history` 之类）。 */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val colorMode by viewModel.colorMode.collectAsState()
            val shareText by pendingShareText
            val route by pendingRoute

            LaunchedEffect(shareText) {
                shareText?.takeIf { it.isNotBlank() }?.let { text ->
                    viewModel.parseUrl(text)
                    pendingShareText.value = null
                }
            }

            LinkGrabTheme(colorMode = colorMode) {
                AppNavigation(
                    viewModel = viewModel,
                    initialShareText = shareText,
                    initialRoute = route,
                    onRouteConsumed = { pendingRoute.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 三类入口统一在这里分发：
     * 1. 桌面快捷方式 / 小组件：`linkgrab://parse`（读剪贴板解析）、`linkgrab://history`
     * 2. 其它应用分享文本：ACTION_SEND / ACTION_PROCESS_TEXT
     * 3. 直接用链接打开：ACTION_VIEW
     */
    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri != null && uri.scheme == LINKGRAB_SCHEME) {
            when (uri.host) {
                "parse" -> {
                    val text = readClipboard()
                    if (text.isNullOrBlank()) {
                        // 剪贴板里没有东西：回首页，别停在空白页
                        pendingRoute.value = Screen.MainTabs.route
                    } else {
                        pendingShareText.value = text
                    }
                }
                "history" -> pendingRoute.value = Screen.History.route
            }
            return
        }
        getShareText(intent)?.takeIf { it.isNotBlank() }?.let { pendingShareText.value = it }
    }

    private fun readClipboard(): String? {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.text
            ?.toString()
    }

    private fun getShareText(intent: Intent?): String? {
        return when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> {
                val c = intent.getCharExtra(Intent.EXTRA_PROCESS_TEXT, ' ')
                if (c != ' ') c.toString() else null
            }
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }
    }

    private companion object {
        const val LINKGRAB_SCHEME = "linkgrab"
    }
}
