package com.linkgrab.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.data.feedback.FeedbackStore
import com.linkgrab.app.ui.theme.IOS26Button
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26ListRow
import com.linkgrab.app.ui.theme.IOS26OutlinedButton
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26Switch
import com.linkgrab.app.ui.theme.IOS27TextField
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 「反馈此问题」入口要带的上下文：跳到反馈页前先塞进来。 */
object FeedbackDraft {
    var prefillType: String? = null
    var prefillLink: String? = null

    fun clear() {
        prefillType = null
        prefillLink = null
    }
}

private val IssueTypes = listOf("解析失败", "保存失败", "功能建议", "其他")

private const val ISSUE_BASE_URL = "https://github.com/chenckx0614/LinkGrab/issues/new"

@Composable
fun FeedbackScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val lastFailure = remember { FeedbackStore.lastFailure(context) }
    // prefill 是「跳转前塞进来的一次性上下文」：remember 初值取走后立刻清，
    // 否则下次从设置页进来还带着上次的解析失败链接。
    var type by remember { mutableStateOf(FeedbackDraft.prefillType ?: IssueTypes.first()) }
    var description by remember { mutableStateOf("") }
    var link by remember { mutableStateOf(FeedbackDraft.prefillLink.orEmpty()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { FeedbackDraft.clear() }
    var includeDiagnostics by remember { mutableStateOf(true) }

    fun buildBody(): String = buildString {
        appendLine("**问题类型**：$type")
        if (description.isNotBlank()) {
            appendLine()
            appendLine("**描述**：")
            appendLine(description)
        }
        if (link.isNotBlank()) {
            appendLine()
            appendLine("**相关链接**：$link")
        }
        if (includeDiagnostics) {
            appendLine()
            appendLine("**诊断信息**：")
            appendLine("<!-- 自动生成，可在 App 中关闭。不含任何个人标识信息 -->")
            FeedbackStore.deviceInfo(context).forEach { appendLine("- $it") }
            lastFailure?.let {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    .format(Date(it.timestamp))
                appendLine("- 最近解析失败：${it.platform} / ${it.error} / $time")
            }
        }
    }

    fun submitToGithub() {
        val title = buildString {
            append(if (type == "功能建议") "[建议] " else "[问题] ")
            append(description.lineSequence().firstOrNull().orEmpty().take(40).ifBlank { type })
        }
        val url = ISSUE_BASE_URL +
            "?template=feedback.md" +
            "&title=" + URLEncoder.encode(title, "UTF-8") +
            "&body=" + URLEncoder.encode(buildBody(), "UTF-8")
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    IOS26StickyPage(
        title = "反馈问题",
        onBack = onBack,
        applyImePadding = true,
        bottomSpacing = 48.dp,
    ) {
        item {
            Text(
                "遇到什么问题？",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IssueTypes.take(2).forEach { t ->
                    IOS26Choice(t, type == t, { type = t }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IssueTypes.drop(2).forEach { t ->
                    IOS26Choice(t, type == t, { type = t }, Modifier.weight(1f))
                }
            }
        }

        item {
            IOS26Panel(Modifier.fillMaxWidth()) {
                IOS27TextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = "说说发生了什么（选填）",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
                Spacer(Modifier.height(14.dp))
                IOS27TextField(
                    value = link,
                    onValueChange = { link = it },
                    placeholder = "相关链接（选填）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item {
            IOS26Panel(Modifier.fillMaxWidth()) {
                IOS26ListRow(
                    "附带诊断信息",
                    lastFailure?.let { "含最近一次解析失败记录" } ?: "App 版本 / 系统版本 / 机型",
                ) {
                    IOS26Switch(checked = includeDiagnostics, onCheckedChange = { includeDiagnostics = it })
                }
            }
        }

        item {
            IOS26Button(
                "在 GitHub 提 Issue",
                { submitToGithub() },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            IOS26OutlinedButton(
                "复制反馈内容",
                { clipboard.setText(AnnotatedString(buildBody())) },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "提 Issue 需要登录 GitHub 账号，内容是公开的。" +
                    "没有账号可以复制内容，通过关于页的邮箱或群发给开发者。",
                fontSize = 13.sp,
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
