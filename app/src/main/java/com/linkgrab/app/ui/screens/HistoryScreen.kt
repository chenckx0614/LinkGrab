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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.linkgrab.app.ui.liquid.LiquidGlassCircleButton
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

private fun historyTypeName(raw: String): String = when (raw) {
    "video" -> "视频"
    "image" -> "图片"
    "audio" -> "音频"
    "unknown" -> "其他"
    else -> raw.ifBlank { "其他" }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel, onBack: () -> Unit, onParseUrl: (String) -> Unit) {
    val history by viewModel.allHistory.collectAsState(initial = emptyList())
    var platformFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchMode by rememberSaveable { mutableStateOf(false) }
    val searchFocus = remember { FocusRequester() }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var pendingDelete by remember { mutableStateOf<List<HistoryItem>?>(null) }
    // 右上角菜单（液态玻璃容器变换：42dp 圆按钮 ↔ 200dp 面板）
    var showMenu by remember { mutableStateOf(false) }
    val menuFraction = remember { Animatable(0f) }
    // 菜单玻璃要采样页面内容才有模糊：页面停在顶部时图层默认不录，这里强制录
    val menuVisible = showMenu || menuFraction.value > 0.01f

    // Every dimension combines with AND; favorite-first sorting is stable.
    val query = searchQuery.trim()
    val filtered = history
        .filter { item ->
            val searchOk = query.isEmpty() ||
                item.title.contains(query, ignoreCase = true) ||
                item.author.contains(query, ignoreCase = true) ||
                item.url.contains(query, ignoreCase = true)
            (platformFilter == null || item.platform == platformFilter) &&
                (typeFilter == null || item.type == typeFilter) &&
                (!downloadedOnly || item.downloaded) &&
                (!favoritesOnly || item.favorite) && searchOk
        }
        .sortedByDescending { it.favorite }
    val visibleIds = filtered.map { it.id }.toSet()
    // Bulk actions are restricted to visible records, even while filters change.
    val visibleSelected = selected.intersect(visibleIds)
    LaunchedEffect(visibleIds) { selected = selected.intersect(visibleIds) }
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
    // 搜索模式下返回键先收搜索（并清掉关键词）
    BackHandler(enabled = searchMode) {
        searchMode = false
        searchQuery = ""
    }

    IOS26Canvas {
        // Opening search returns the lazy list to the top so its input is present
        // even when search was opened while viewing a distant history record.
        key(searchMode) {
        IOS26StickyPage(
            title = if (selectionMode) "已选 ${visibleSelected.size} 项" else "历史记录",
            onBack = { if (selectionMode) exitSelection() else onBack() },
            spacedBy = 10.dp,
            recordBackdrop = menuVisible,
            overlay = { backdrop ->
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 搜索玻璃圆钮：与菜单触发钮同尺寸同材质同按压手感
                        LiquidGlassCircleButton(
                            icon = if (searchMode) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchMode) "关闭搜索" else "搜索",
                            onClick = {
                                searchMode = !searchMode
                                if (!searchMode) searchQuery = ""
                            },
                            backdrop = backdrop,
                            // 菜单触发钮左缘内缩 24dp，右移 12dp 让两个圆的间距是 12dp
                            modifier = Modifier
                                .padding(LiquidGlassDropdownShadowPadding)
                                .offset(x = 12.dp),
                        )
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
                                text = if (visibleSelected.size == filtered.size) "取消全选" else "全选",
                                onClick = {
                                    showMenu = false
                                    selected = if (visibleSelected.size == filtered.size) emptySet()
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
                            if (visibleSelected.isNotEmpty()) {
                                val favTargets = filtered.filter { it.id in visibleSelected }
                                val makeFav = favTargets.any { !it.favorite }
                                LiquidGlassDropdownMenuItem(
                                    text = if (makeFav) "收藏(${visibleSelected.size})" else "取消收藏(${visibleSelected.size})",
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
                                    text = "删除(${visibleSelected.size})",
                                    onClick = {
                                        showMenu = false
                                        pendingDelete = filtered.filter { it.id in visibleSelected }
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
                }
            },
        ) {
            item { IOS26PageHeader("历史记录", "查看和继续处理已解析的内容", topPadding = 0.dp) }
            if (searchMode) {
                item {
                    IOS27TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "搜索标题、作者或链接",
                        singleLine = true,
                        modifier = Modifier.focusRequester(searchFocus),
                    )
                    LaunchedEffect(Unit) { searchFocus.requestFocus() }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IOS26Choice("全部平台", platformFilter == null, onClick = { platformFilter = null })
                    (listOf("douyin", "xiaohongshu", "bilibili", "weibo", "web") +
                        history.map { it.platform }).distinct().forEach { platform ->
                        IOS26Choice(platformName(platform), platformFilter == platform,
                            onClick = { platformFilter = if (platformFilter == platform) null else platform })
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IOS26Choice("全部类型", typeFilter == null, onClick = { typeFilter = null })
                    (listOf("video", "image") + history.map { it.type }).distinct().forEach { type ->
                        IOS26Choice(historyTypeName(type), typeFilter == type,
                            onClick = { typeFilter = if (typeFilter == type) null else type })
                    }
                    IOS26Choice("已下载", downloadedOnly, onClick = { downloadedOnly = !downloadedOnly })
                    IOS26Choice("★ 收藏", favoritesOnly, onClick = { favoritesOnly = !favoritesOnly })
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${filtered.size} 条记录", color = iosSecondaryLabel(), fontSize = 13.sp)
                    if (query.isNotEmpty() || platformFilter != null || typeFilter != null ||
                        downloadedOnly || favoritesOnly) {
                        IOS26Choice("清除筛选", false, onClick = {
                            searchQuery = ""
                            platformFilter = null
                            typeFilter = null
                            downloadedOnly = false
                            favoritesOnly = false
                        })
                    }
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
    val haptic = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(IOSCornerMedium)
            .background(cardBg)
            .border(0.5.dp, cardBorder, IOSCornerMedium)
            .combinedClickable(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    onLongClick()
                },
            )
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
                "${platformName(item.platform)} · ${historyTypeName(item.type)}" +
                    if (item.downloaded) " · 已下载" else "",
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 14.sp,
            )
            if (item.author.isNotBlank()) {
                Text(
                    item.author,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 12.sp,
                    maxLines = 2,
                )
            }
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
