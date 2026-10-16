package com.linkgrab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.R
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosGroupedBackground
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.viewmodel.MainViewModel

/**
 * 底栏样式（「我的」→ 二级页）：四种底栏以实拍效果图展示，
 * 点卡片即切换，当前样式打蓝勾 + 蓝色描边。
 */
@Composable
fun BottomBarStyleScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val bottomBarStyle by viewModel.bottomBarStyle.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current

    IOS26Canvas {
        IOS26StickyPage(
            title = "底栏样式",
            onBack = onBack,
            spacedBy = 14.dp,
        ) {
            item {
                IOS26PageHeader("底栏样式", "选择主界面底部导航栏的外观。", topPadding = 0.dp)
            }

            item {
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                ) {
                    StyleCard(
                        title = "iOS 27",
                        description = "液态玻璃悬浮胶囊",
                        imageRes = R.drawable.bottom_style_ios27,
                        selected = bottomBarStyle == 0,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(0)
                        },
                    )
                    StyleCard(
                        title = "HyperOS 4",
                        description = "小米 HyperOS 小巧胶囊",
                        imageRes = R.drawable.bottom_style_hyperos4,
                        selected = bottomBarStyle == 1,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(1)
                        },
                    )
                    StyleCard(
                        title = "HarmonyOS 7",
                        description = "鸿蒙系统圆润胶囊",
                        imageRes = R.drawable.bottom_style_harmonyos7,
                        selected = bottomBarStyle == 2,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(2)
                        },
                    )
                    StyleCard(
                        title = "Material 3",
                        description = "安卓原生导航栏风格",
                        imageRes = R.drawable.bottom_style_material3,
                        selected = bottomBarStyle == 3,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(3)
                        },
                    )
                }
            }
        }
    }
}

/**
 * 样式卡片：上半是实拍效果图，下半是名称行。
 * 选中时图片区套蓝色描边、名称行打蓝勾。
 */
@Composable
private fun StyleCard(
    title: String,
    description: String,
    imageRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(iosGroupedBackground())
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(
                    if (selected) Modifier.border(
                        BorderStroke(2.dp, iosBlue()),
                        RoundedCornerShape(14.dp),
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = title,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = iosLabel(),
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = iosBlue(),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
