package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.BuildConfig
import com.linkgrab.app.R
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Divider
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26Panel
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.iosDisclosureColor
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.iosSecondaryLabel

/**
 * 关于应用（「我的」→ 二级页）：信息架构移植自 Nexio 课程表的 AboutActivity——
 * 大图标 + 应用名 + 版本号居中，下面是「交流与反馈 / 捐赠支持」与
 * 内嵌更新日志预览（最近 3 条，第一条默认展开，点击收起，chevron 旋转）。
 * 交流与反馈也在本页内展开，不再跳独立页。
 *
 * 项目仓库一行按需求去掉。
 *
 * 踩坑：`painterResource(R.mipmap.ic_launcher)` 会崩——mipmap-anydpi-v26 里
 * ic_launcher 是 adaptive-icon XML，painterResource 不支持该格式，运行时直接
 * 抛异常（进关于页即闪退）。必须走 Drawable + rememberDrawablePainter。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onNavigateToUpdateLog: () -> Unit,
    onNavigateToDonate: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // adaptive-icon XML 不能走 painterResource（运行时崩），也不能交给 coil3
    // （它没有 rememberDrawablePainter）——手动渲染成 Bitmap 最稳。
    val launcherBitmap = remember {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!
        // AdaptiveIconDrawable 无固有尺寸（intrinsicWidth 可能为 -1），固定 512 渲染
        val size = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight).takeIf { it > 0 } ?: 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap.asImageBitmap()
    }

    IOS26Canvas {
        IOS26StickyPage(
            title = "关于应用",
            onBack = onBack,
            spacedBy = 12.dp,
        ) {
            item {
                IOS26PageHeader("关于应用", "了解 LinkGrab 的版本与来历。", topPadding = 0.dp)
            }

            // ===== 应用身份：大图标 + 应用名 + 版本（Nexio 同构） =====
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        bitmap = launcherBitmap,
                        contentDescription = "LinkGrab 图标",
                        modifier = Modifier
                            .size(88.dp)
                            .clip(RoundedCornerShape(22.dp)),
                    )
                    Text(
                        "LinkGrab",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = iosLabel(),
                        modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                    )
                    Text(
                        "v${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}",
                        fontSize = 14.sp,
                        color = iosSecondaryLabel(),
                    )
                }
            }

            // ===== 卡片1：交流与反馈（页内展开）/ 捐赠支持 =====
            item {
                IOS26Panel(Modifier.fillMaxWidth()) {
                    var feedbackExpanded by remember { mutableStateOf(false) }
                    val feedbackRotation by animateFloatAsState(
                        targetValue = if (feedbackExpanded) 90f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "aboutFeedbackRotation",
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                feedbackExpanded = !feedbackExpanded
                            },
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 13.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "交流与反馈",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                                color = iosLabel(),
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = iosDisclosureColor(),
                                modifier = Modifier
                                    .size(IOS26IconSize.Disclosure)
                                    .graphicsLayer { rotationZ = feedbackRotation },
                            )
                        }
                        AnimatedVisibility(
                            visible = feedbackExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 13.dp)) {
                                Text(
                                    "敬请期待",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = iosLabel(),
                                )
                                Text(
                                    "交流群与反馈通道正在筹备中，后续版本会在这里上线。",
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = iosSecondaryLabel(),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                    IOS26Divider(Modifier.padding(vertical = 2.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onNavigateToDonate()
                            }
                            .padding(horizontal = 4.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "捐赠支持",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = iosLabel(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "请作者喝杯咖啡",
                                fontSize = 14.sp,
                                color = iosSecondaryLabel(),
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = iosDisclosureColor(),
                                modifier = Modifier.size(IOS26IconSize.Disclosure),
                            )
                        }
                    }
                }
            }

            // ===== 卡片2：更新日志入口 + 内嵌最近 3 条（第一条默认展开） =====
            item {
                val recent = recentLogs.take(3)
                IOS26Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onNavigateToUpdateLog()
                            }
                            .padding(horizontal = 4.dp, vertical = 13.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "更新日志",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            color = iosLabel(),
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = iosDisclosureColor(),
                            modifier = Modifier.size(IOS26IconSize.Disclosure),
                        )
                    }
                    recent.forEachIndexed { index, entry ->
                        var expanded by remember(entry.version) { mutableStateOf(index == 0) }
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 90f else -90f,
                            animationSpec = tween(durationMillis = 200),
                            label = "aboutChangelogRotation$index",
                        )
                        if (index > 0) {
                            IOS26Divider(Modifier.padding(vertical = 2.dp))
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded },
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 13.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "v${entry.version}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = iosLabel(),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    entry.date,
                                    fontSize = 14.sp,
                                    color = iosSecondaryLabel(),
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = iosDisclosureColor(),
                                    modifier = Modifier
                                        .size(IOS26IconSize.Disclosure)
                                        .graphicsLayer { rotationZ = rotation },
                                )
                            }
                            AnimatedVisibility(
                                visible = expanded,
                                enter = expandVertically(),
                                exit = shrinkVertically(),
                            ) {
                                Column(
                                    Modifier.padding(start = 6.dp, end = 4.dp, bottom = 12.dp),
                                ) {
                                    entry.changes.forEach { change ->
                                        Row(Modifier.padding(bottom = 2.dp)) {
                                            Text(
                                                "• ",
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp,
                                                color = iosSecondaryLabel(),
                                            )
                                            Text(
                                                change,
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp,
                                                color = iosSecondaryLabel(),
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "解析服务由第三方网络接口提供，具体可用性取决于平台页面和网络环境。",
                    fontSize = 12.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
