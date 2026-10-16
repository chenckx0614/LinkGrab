package com.linkgrab.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.iosGroupedBackground
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryBackground
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.viewmodel.MainViewModel

/**
 * 底栏样式（「我的」→ 二级页）：四种底栏以迷你可视化预览展示，
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
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StyleCard(
                        title = "iOS 27",
                        description = "液态玻璃悬浮胶囊",
                        selected = bottomBarStyle == 0,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(0)
                        },
                    ) { MiniBarStyleIOS27(bottomBarStyle == 0) }
                    StyleCard(
                        title = "HyperOS 4",
                        description = "小米 HyperOS 小巧胶囊",
                        selected = bottomBarStyle == 1,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(1)
                        },
                    ) { MiniBarStyleHyperOS4(bottomBarStyle == 1) }
                    StyleCard(
                        title = "HarmonyOS 7",
                        description = "鸿蒙系统圆润胶囊",
                        selected = bottomBarStyle == 2,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(2)
                        },
                    ) { MiniBarStyleHarmonyOS7(bottomBarStyle == 2) }
                    StyleCard(
                        title = "Material 3",
                        description = "安卓原生导航栏风格",
                        selected = bottomBarStyle == 3,
                        onClick = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                            viewModel.setBottomBarStyle(3)
                        },
                    ) { MiniBarStyleMd3(bottomBarStyle == 3) }
                }
            }
        }
    }
}

/**
 * 样式卡片：上半是预览舞台（迷你假底栏沉在底部），下半是名称行。
 * 选中时预览区套蓝色描边、名称行打蓝勾。
 */
@Composable
private fun StyleCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(iosGroupedBackground())
            .clickable(onClick = onClick),
    ) {
        // 预览舞台：模拟一小块屏幕底部，迷你底栏沉在下面
        Box(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .height(104.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(iosSecondaryBackground())
                .then(
                    if (selected) Modifier.border(
                        BorderStroke(2.dp, iosBlue()),
                        RoundedCornerShape(14.dp),
                    ) else Modifier
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.padding(bottom = 12.dp)) { preview() }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 14.dp),
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

// MARK: - 迷你底栏预览（按真实底栏的形态等比缩小绘制）

/** iOS 27 / Nexio：窄悬浮液态玻璃胶囊，选中项玻璃发亮。 */
@Composable
private fun MiniBarStyleIOS27(selected: Boolean) {
    Box(
        Modifier
            .width(150.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(iosGlass())
            .border(1.dp, iosGlassStroke(), RoundedCornerShape(17.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Build).forEachIndexed { i, icon ->
                if (i == 0 && selected) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(iosBlue().copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = iosBlue(), modifier = Modifier.size(12.dp))
                    }
                } else {
                    Icon(icon, null, tint = iosSecondaryLabel(), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** HyperOS 4：通栏窄一截的小巧胶囊，选中项淡色遮罩。 */
@Composable
private fun MiniBarStyleHyperOS4(selected: Boolean) {
    Box(
        Modifier
            .width(190.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(iosGroupedBackground())
            .border(1.dp, iosSeparatorFallback(), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Build).forEachIndexed { i, icon ->
                if (i == 0 && selected) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(iosTertiaryBackground()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = iosBlue(), modifier = Modifier.size(12.dp))
                    }
                } else {
                    Icon(icon, null, tint = iosSecondaryLabel(), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** HarmonyOS 7：全宽圆角胶囊，图标更大更圆润。 */
@Composable
private fun MiniBarStyleHarmonyOS7(selected: Boolean) {
    Box(
        Modifier
            .width(168.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(iosTertiaryBackground()),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Build).forEachIndexed { i, icon ->
                Icon(
                    icon, null,
                    tint = if (i == 0 && selected) iosBlue() else iosSecondaryLabel(),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Material 3：更方的导航栏，选中项背后一颗药丸指示器。 */
@Composable
private fun MiniBarStyleMd3(selected: Boolean) {
    Box(
        Modifier
            .width(200.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(iosGroupedBackground())
            .border(1.dp, iosSeparatorFallback(), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Default.Home, Icons.Default.Person, Icons.Default.Build).forEachIndexed { i, icon ->
                if (i == 0 && selected) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(width = 26.dp, height = 20.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(iosBlue().copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = iosBlue(), modifier = Modifier.size(12.dp))
                        }
                    }
                } else {
                    Icon(icon, null, tint = iosSecondaryLabel(), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

/** 描边用中性色（细描边比 separator 稍淡）。 */
@Composable
private fun iosSeparatorFallback() = iosSecondaryLabel().copy(alpha = 0.25f)
