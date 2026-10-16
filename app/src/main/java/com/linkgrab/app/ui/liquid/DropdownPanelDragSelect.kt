package com.linkgrab.app.ui.liquid

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.util.fastFirstOrNull
import com.linkgrab.app.ui.theme.isInDarkTheme

/**
 * 跟手滑选。移植自 Nexio 课程表（gitee.com/com_haooz_account/hyper_schedule，
 * AGPL-3.0）的 DropdownPanelDragSelect.kt，供 [LiquidGlassDropdownMenu] 使用。
 *
 * 坐标系统一为**面板局部坐标**：面板整体缩放走 graphicsLayer，不进 layout 坐标，
 * 挂在面板上的手势与菜单项天然同一套。
 */

/** 跟手滑选生效的最小展开进度：面板接近满尺寸后菜单项位置不再变动 */
private const val DragSelectReadyFraction = 0.98f

/** 菜单项登记信息：面板局部坐标下的纵向区间 + 点击动作 */
class DropdownPanelEntry internal constructor() {
    internal var top = 0f
    internal var bottom = 0f
    internal var action: (() -> Unit)? = null
    internal var enabled = true
}

/**
 * 跟手滑选状态。由面板内容层创建，通过 [LocalDropdownPanelDragSelect] 下发给菜单项。
 */
class DropdownPanelDragSelectState internal constructor() {
    private val entries = mutableStateListOf<DropdownPanelEntry>()

    /** 当前命中的项；null = 松手不执行，菜单保持展开 */
    var selected by mutableStateOf<DropdownPanelEntry?>(null)
        internal set

    /**
     * 列表是否一屏装得下。
     *
     * 装不下时纵向手势归滚动，跟手选择整体禁用 —— 两种手势同时生效会互相抢，
     * 表现为「滑动时菜单乱滚一截才停」。
     */
    var fitsOnScreen by mutableStateOf(true)
        internal set

    /** 面板顶端在 root 里的 y，供菜单项把 boundsInRoot 换算成面板局部坐标 */
    var panelTopInRoot by mutableFloatStateOf(0f)

    internal fun register(entry: DropdownPanelEntry) {
        if (!entries.contains(entry)) entries.add(entry)
    }

    internal fun unregister(entry: DropdownPanelEntry) {
        entries.remove(entry)
        if (selected === entry) selected = null
    }

    internal fun updateBounds(entry: DropdownPanelEntry, top: Float, bottom: Float) {
        entry.top = top
        entry.bottom = bottom
    }

    /** @param y 面板局部 y；@return 命中的项，null = 空白处 */
    internal fun hitTest(y: Float): DropdownPanelEntry? {
        val hit = entries.firstOrNull { it.enabled && y >= it.top && y < it.bottom }
        selected = hit
        return hit
    }

    internal fun clear() {
        selected = null
    }
}

internal val LocalDropdownPanelDragSelect =
    compositionLocalOf<DropdownPanelDragSelectState?> { null }

/**
 * 面板上**唯一**的手势状态：按下 → 命中 → 逐帧改命中 → 松手执行。
 * 菜单项上不能再挂 clickable —— 两个手势状态互相抢事件正是「先按住再滑动会跳状态」的根因。
 *
 * 收起态不接管（面板太小、没有项可选），也不消费 down 事件，所以收起态的点按展开
 * 仍走触发区原有的 clickable。
 *
 * @param fraction 现读展开进度。**不能捕获** —— `pointerInput(Unit)` 的 lambda
 *   只在首次组合跑一次，捕获会得到陈旧值（Nexio 曾因此「直接滑动毫无反应」）。
 */
fun Modifier.dropdownPanelDragSelect(
    state: DropdownPanelDragSelectState,
    fraction: () -> Float,
    hapticFeedback: HapticFeedback,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 面板没长开 / 一屏装不下时完全不参与：前者让收起态照常点按，后者归滚动
        if (!state.fitsOnScreen || fraction() < DragSelectReadyFraction) return@awaitEachGesture
        down.consume()
        var lastHit = state.hitTest(down.position.y)
        while (true) {
            val change = awaitPointerEvent()
                .changes.fastFirstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val hit = state.hitTest(change.position.y)
            if (hit != null && hit !== lastHit) {
                // 仅「换了一项」才震，按下即命中的那一下不震
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHit = hit
            change.consume()
        }
        val hit = state.selected
        state.clear()
        hit?.action?.invoke()
    }
}

/** 命中项的高亮底色 */
private fun dropdownPanelEntryHighlightColor(isDark: Boolean): Color =
    if (isDark) Color.White.copy(0.11f) else Color.Black.copy(0.075f)

/**
 * 菜单项侧：登记纵向区间 + 绘制命中高亮。
 *
 * 登记与绘制合在一个修饰符里：两者共用同一个 [DropdownPanelEntry]，拆开会各自
 * remember 出不同实例，选中态永远匹配不上。
 *
 * @param enabled false 的项不参与命中测试（滑过去不高亮、松手也不执行）
 * @param action 命中并松手时执行。每次重组都会更新，可直接传 lambda
 */
@Composable
fun Modifier.dropdownPanelEntry(
    enabled: Boolean,
    action: () -> Unit,
): Modifier = composed {
    val state = LocalDropdownPanelDragSelect.current
    val entry = remember(state) { DropdownPanelEntry() }
    DisposableEffect(state, entry) {
        state?.register(entry)
        onDispose { state?.unregister(entry) }
    }
    SideEffect {
        entry.action = action
        entry.enabled = enabled
    }
    val selected = state != null && state.selected === entry
    // drawBehind 的 lambda 不是 @Composable，主题色必须在这里取好再传进去
    val highlightColor = dropdownPanelEntryHighlightColor(isInDarkTheme())
    this
        .onGloballyPositioned {
            // boundsInRoot 不含 graphicsLayer 变换，面板与项都取 root 坐标再作差
            val top = it.boundsInRoot().top - (state?.panelTopInRoot ?: 0f)
            state?.updateBounds(entry, top, top + it.size.height)
        }
        .drawBehind {
            if (selected) drawRect(highlightColor)
        }
}
