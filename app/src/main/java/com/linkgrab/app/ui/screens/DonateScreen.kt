package com.linkgrab.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.R
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26SectionHeader
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

/**
 * 捐赠支持（关于应用 → 二级页）：赞赏码居中展示，下面挂赞赏名单。
 * 结构参考 Nexio AppreciateAuthorScreen 的 DonateQrCard（正方形卡片 + 圆角图）；
 * 名单是本地常量，不联网。
 */
@Composable
fun DonateScreen(onBack: () -> Unit) {
    IOS26Canvas {
        IOS26StickyPage(
            title = "捐赠支持",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader("捐赠支持", "请作者喝杯咖啡，继续更新。", topPadding = 0.dp)
            }

            item {
                // 正方形卡片：赞赏码完整展示（图自带白底与标题）
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(R.drawable.donate_qrcode),
                        contentDescription = "赞赏码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            item {
                Text(
                    "扫码请作者喝杯咖啡，你的支持是 LinkGrab 持续更新的动力。",
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            if (supporters.isNotEmpty()) {
                item {
                    IOS26SectionHeader("赞赏名单", "谢谢每一位支持过 LinkGrab 的人")
                }
                item {
                    IOS26Panel(Modifier.fillMaxWidth()) {
                        supporters.forEachIndexed { index, name ->
                            if (index > 0) IOS26Divider()
                            SupporterRow(name)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 赞赏名单：手动维护，按时间先后追加（新的放最后）。
 * 不加日期和金额，只留名字——名单是心意，不是账本。
 */
private val supporters = listOf(
    "田柒",
)

/** 首字圆形头像 + 名字。颜色走设计系统，两种主题下都自适应。 */
@Composable
private fun SupporterRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iosBlue().copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.take(1),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = iosBlue(),
            )
        }
        Text(
            name,
            fontSize = 16.sp,
            color = iosLabel(),
            letterSpacing = (-0.2).sp,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
