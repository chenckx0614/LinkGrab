package com.linkgrab.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.ExperimentalAnimationSpecApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.linkgrab.app.data.history.HistoryItem
import com.linkgrab.app.ui.theme.IOS26Canvas
import com.linkgrab.app.ui.theme.IOS26Choice
import com.linkgrab.app.ui.theme.IOS26PageHeader
import com.linkgrab.app.ui.theme.IOS26StickyPage
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.IOS27TextField
import com.linkgrab.app.ui.theme.IOSCornerMedium
import com.linkgrab.app.ui.theme.IOSCornerSmall
import com.linkgrab.app.ui.theme.IOSRed
import com.linkgrab.app.ui.theme.iosBlue
import com.linkgrab.app.ui.theme.iosDisclosureColor
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.iosLabel
import com.linkgrab.app.ui.theme.isInDarkTheme
import com.linkgrab.app.ui.liquid.GlassDialogDangerButton
import com.linkgrab.app.ui.liquid.GlassDialogSecondaryButton
import com.linkgrab.app.ui.liquid.IOS26GlassDialog
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenu
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownMenuItem
import com.linkgrab.app.ui.liquid.LiquidGlassDropdownShadowPadding
import com.linkgrab.app.ui.theme.IOS26HeaderHeight
import com.linkgrab.app.ui.theme.iosSecondaryLabel
import com.linkgrab.app.ui.theme.iosTertiaryBackground
import com.linkgrab.app.viewmodel.MainViewModel

private fun platformName(raw: String): String = when (raw) {
    "douyin" -> "抖音"
    "xiaohongshu" -> "小红书"
    "bilibili" -> "B站"
    "weibo" -> "微博"
    "web" -> "网页"
    else -> raw
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit, onParseUrl: (String) -> Unit) {
    val history by viewModel.allHistory.collectAsState(initial = emptyList())
    var filter by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<List<HistoryItem>?>(null) }
    // 右上角菜单（液态玻璃容器变换：42dp 圆按钮 ↔ 200dp 面板）
    var showMenu by remember { mutableStateOf(false) }
    val menuFraction = remember { Animatable(0f) }
    // 菜单玻璃要采样页面内容才有模糊：页面停在顶部时图层默认不录，这里强制录
    val menuVisible = showMenu || menuFraction.value > 0.01f

    // 筛选（平台 / 收藏）+ 搜索（标题、链接），收藏置顶（稳定排序保住时间序）
    val query = searchQuery.trim()
    val filtered = history
        .filter { item ->
            val platformOk = when {
                filter == "all" -> true
                filter == "fav" -> item.favorite
                else -> item.platform == filter
            }
            val searchOk = query.isEmpty() ||
                item.title.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true)
            platformOk && searchOk
        }
        .sortedByDescending { it.favorite }
    // 菜单要对齐到吸顶栏里那颗按钮的基线（状态栏下方）
    val headerTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    fun exitSelection() {
        selectionMode = false
        selected = emptySet()
    }

    // 多选状态下系统返回键先退出多选，而不是直接离开页面
    BackHandler(enabled = selectionMode) { exitSelection() }
    // 菜单展开时返回键先收菜单
    BackHandler(enabled = showMenu) { showMenu = false }

    IOS26Canvas {
        IOS26StickyPage(
            title = if (selectionMode) "已选 ${selected.size} 项" else "历史记录",
            onBack = { if (selectionMode) exitSelection() else onBack() },
            spacedBy = 10.dp,
            recordBackdrop = menuVisible,
            overlay = { backdrop ->
                if (filtered.isEmpty()) return@IOS26StickyPage
                // 点空白处收起菜单
                if (menuVisible) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { showMenu = false },
                    )
                }
                // 与吸顶栏里的按钮同一条基线：栏高 52dp、按钮 42dp → 顶边内缩 5dp。
                // 菜单根 Box 自带 24dp 阴影内缩，这里先减掉再对齐到栏内。
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = (headerTopInset + 5.dp - LiquidGlassDropdownShadowPadding).coerceAtLeast(0.dp))
                        // 栏的水平内边距是 20dp，菜单自带 24dp，补 4dp 让按钮右缘落在 20dp
                        .offset(x = 4.dp),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    LiquidGlassDropdownMenu(
                        show = showMenu,
                        backdrop = backdrop,
                        fraction = menuFraction,
                        onDismiss = { showMenu = false },
                        triggerIcon = Icons.Default.MoreVert,
                        triggerContentDescription = "更多",
                        onExpand = { showMenu = true },
                    ) {
                        if (selectionMode) {
                            LiquidGlassDropdownMenuItem(
                                text = if (selected.size == filtered.size) "取消全选" else "全选",
                                onClick = {
                                    showMenu = false
                                    selected = if (selected.size == filtered.size) emptySet()
                                    else filtered.map { it.id }.toSet()
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.SelectAll,
                                        contentDescription = null,
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.MenuItem),
                                    )
                                },
                            )
                            if (selected.isNotEmpty()) {
                                val favTargets = filtered.filter { it.id in selected }
                                val makeFav = favTargets.any { !it.favorite }
                                LiquidGlassDropdownMenuItem(
                                    text = if (makeFav) "收藏(${selected.size})" else "取消收藏(${selected.size})",
                                    onClick = {
                                        showMenu = false
                                        favTargets.forEach {
                                            viewModel.toggleFavorite(it.id, makeFav)
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (makeFav) Icons.Default.Star else Icons.Outlined.StarOutline,
                                            contentDescription = null,
                                            tint = iosLabel(),
                                            modifier = Modifier.size(IOS26IconSize.MenuItem),
                                        )
                                    },
                                )
                                LiquidGlassDropdownMenuItem(
                                    text = "删除(${selected.size})",
                                    onClick = {
                                        showMenu = false
                                        pendingDelete = filtered.filter { it.id in selected }
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = IOSRed,
                                            modifier = Modifier.size(IOS26IconSize.MenuItem),
                                        )
                                    },
                                )
                            }
                        } else {
                            LiquidGlassDropdownMenuItem(
                                text = "选择",
                                onClick = {
                                    showMenu = false
                                    selectionMode = true
                                    selected = emptySet()
                                },
                                icon = {
                                    Icon(
                                        Icons.Default.Checklist,
                                        contentDescription = null,
                                        tint = iosLabel(),
                                        modifier = Modifier.size(IOS26IconSize.MenuItem),
                                    )
                                },
                            )
                        }
                    }
                }
            },
        ) {
            item { IOS26PageHeader("历史记录", "查看和继续处理已解析的内容", topPadding = 0.dp) }
            item {
                IOS27TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = "搜索",
                    placeholder = "按标题或链接搜索",
                    singleLine = true,
                )
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IOS26Choice("全部", filter == "all", onClick = { filter = "all" })
                    IOS26Choice("★ 收藏", filter == "fav", onClick = { filter = "fav" })
                    IOS26Choice("抖音", filter == "douyin", onClick = { filter = "douyin" })
                    IOS26Choice("小红书", filter == "xiaohongshu", onClick = { filter = "xiaohongshu" })
                    IOS26Choice("B站", filter == "bilibili", onClick = { filter = "bilibili" })
                    IOS26Choice("微博", filter == "weibo", onClick = { filter = "weibo" })
                    IOS26Choice("网页", filter == "web", onClick = { filter = "web" })
                }
            }
            if (history.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "还没有历史记录",
                                color = iosLabel(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp,
                            )
                            Text(
                                "解析一个链接后，它会出现在这里",
                                color = iosSecondaryLabel(),
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "没有匹配的记录",
                                color = iosLabel(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.3).sp,
                            )
                            Text(
                                "换个关键词或筛选条件试试",
                                color = iosSecondaryLabel(),
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(filtered, key = { _, item -> item.id }) { _, item ->
                    HistoryCard(
                        item = item,
                        selectionMode = selectionMode,
                        selected = item.id in selected,
                        onClick = {
                            if (selectionMode) {
                                selected = if (item.id in selected) selected - item.id
                                else selected + item.id
                            } else {
                                onParseUrl(item.url)
                            }
                        },
                        onLongClick = {
                            if (selectionMode) {
                                selected = if (item.id in selected) selected - item.id
                                else selected + item.id
                            } else {
                                selectionMode = true
                                selected = setOf(item.id)
                            }
                        },
                    )
                }
            }
        }
    }

    pendingDelete?.let { items ->
        IOSDeleteConfirmDialog(
            count = items.size,
            sampleTitle = items.firstOrNull()?.title.orEmpty(),
            onConfirm = {
                viewModel.deleteHistoryItems(items)
                pendingDelete = null
                exitSelection()
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 选中态的颜色过渡 + 勾选弹跳：硬切会很"廉价"，这里统一走 160ms / 欠阻尼 spring
    val highlight = selectionMode && selected
    val cardBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlight) iosBlue().copy(alpha = 0.10f) else iosGlass(),
        animationSpec = tween(160),
        label = "historyCardBg",
    )
    val cardBorder by androidx.compose.animation.animateColorAsState(
        targetValue = if (highlight) iosBlue().copy(alpha = 0.6f) else iosGlassStroke(),
        animationSpec = tween(160),
        label = "historyCardBorder",
    )
    val checkBg by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) iosBlue() else iosTertiaryBackground(),
        animationSpec = tween(140),
        label = "historyCheckBg",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IOSCornerMedium)
            .background(cardBg)
            .border(0.5.dp, cardBorder, IOSCornerMedium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(checkBg),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 900f),
                        initialScale = 0.4f,
                    ) + fadeIn(tween(110)),
                    exit = scaleOut(tween(90)) + fadeOut(tween(90)),
                ) {
                    // 原来是 Text("✓") 文字当图标：不同设备字体渲染不一致、深色模式
                    // 也不会跟着主题走。改用真图标。
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Box(Modifier.size(60.dp)) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(item.cover).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(IOSCornerSmall)
                    .background(iosTertiaryBackground()),
            )
            if (item.favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "已收藏",
                        tint = Color(0xFFFFD60A),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                maxLines = 1,
                color = iosLabel(),
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                letterSpacing = (-0.2).sp,
            )
            Text(
                "${platformName(item.platform)} · ${if (item.type == "video") "视频" else "图片"}",
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 14.sp,
            )
        }
        if (!selectionMode) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = iosDisclosureColor(),
                modifier = Modifier.size(IOS26IconSize.Disclosure),
            )
        }
    }
}

/**
 * 删除确认弹窗：液态玻璃版，复用 [IOS26GlassDialog] 外壳（窗口模糊与玻璃面板
 * 由外壳统一处理，这里只负责内容）。
 */
@Composable
private fun IOSDeleteConfirmDialog(
    count: Int,
    sampleTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    IOS26GlassDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(IOSRed.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = IOSRed,
                modifier = Modifier.size(IOS26IconSize.Hero),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (count == 1) "删除这条记录？" else "删除这 $count 条记录？",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosLabel(),
            letterSpacing = (-0.3).sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (count == 1) sampleTitle else "包含「$sampleTitle」等",
            fontSize = 14.sp,
            color = iosSecondaryLabel(),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassDialogSecondaryButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            GlassDialogDangerButton(
                text = "删除",
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
