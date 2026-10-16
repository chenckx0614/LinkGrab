package com.linkgrab.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.R
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosSecondaryLabel

/**
 * 捐赠支持（关于应用 → 二级页）：赞赏码居中展示。
 * 结构参考 Nexio AppreciateAuthorScreen 的 DonateQrCard（正方形卡片 + 圆角图），
 * 领奖台/捐赠明细需要云端数据，LinkGrab 暂无，只放赞赏码与说明。
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
        }
    }
}
