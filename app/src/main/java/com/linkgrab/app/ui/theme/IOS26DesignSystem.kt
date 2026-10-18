package com.linkgrab.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.linkgrab.app.ui.liquid.InteractiveHighlight
import com.linkgrab.app.ui.liquid.computeLiquidDragTransform
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedCornerStyle
import com.kyant.shapes.RoundedRectangle

/* =========================================================
 * iOS 26 Liquid Glass Design System for LinkGrab
 *
 * Visual principles:
 * - System Blue as the single accent color
 * - Ultra-clean grouped backgrounds (#F2F2F7 / #000000)
 * - Translucent glass surfaces with subtle blur, vibrancy
 *   and hairline highlights
 * - Continuous, generous corner radii (squircle-like)
 * - Refined typography with tight tracking and clear hierarchy
 * - Physics-based spring animations for all interactions
 * ========================================================= */

// MARK: - Color Tokens

val IOSBlue = Color(0xFF007AFF)
val IOSBlueDark = Color(0xFF0A84FF)
val IOSGreen = Color(0xFF34C759)
val IOSRed = Color(0xFFFF3B30)
val IOSRedDark = Color(0xFFFF453A)
val IOSOrange = Color(0xFFFF9500)
val IOSPurple = Color(0xFFAF52DE)
val IOSTeal = Color(0xFF5AC8FA)

// Backgrounds
val IOSBackgroundLight = Color(0xFFF2F2F7)
val IOSBackgroundDark = Color(0xFF000000)

// Grouped backgrounds (for cards / lists)
val IOSGroupedBackgroundLight = Color(0xFFFFFFFF)
val IOSGroupedBackgroundDark = Color(0xFF1C1C1E)

// Secondary / tertiary backgrounds
val IOSSecondaryBackgroundLight = Color(0xFFF2F2F7)
val IOSSecondaryBackgroundDark = Color(0xFF2C2C2E)
val IOSTertiaryBackgroundLight = Color(0xFFE5E5EA)
val IOSTertiaryBackgroundDark = Color(0xFF3A3A3C)

// Labels
val IOSLabelLight = Color(0xFF000000)
val IOSLabelDark = Color(0xFFFFFFFF)
val IOSSecondaryLabelLight = Color(0xFF8E8E93)
val IOSSecondaryLabelDark = Color(0xFF98989F)
val IOSTertiaryLabelLight = Color(0xFFC7C7CC)
val IOSTertiaryLabelDark = Color(0xFF48484A)

// Separators
val IOSSeparatorLight = Color(0xFFC6C6C8)
val IOSSeparatorDark = Color(0xFF38383A)

// Glass effect colors
val IOSGlassLight = Color.White.copy(alpha = 0.72f)
val IOSGlassDark = Color(0xFF1C1C1E).copy(alpha = 0.78f)
val IOSGlassStrokeLight = Color.White.copy(alpha = 0.55f)
val IOSGlassStrokeDark = Color.White.copy(alpha = 0.10f)

// MARK: - Theme Helpers

@Composable
fun iosBlue(): Color = if (isInDarkTheme()) IOSBlueDark else IOSBlue

@Composable
fun iosBackground(): Color = if (isInDarkTheme()) IOSBackgroundDark else IOSBackgroundLight

@Composable
fun iosGroupedBackground(): Color = if (isInDarkTheme()) IOSGroupedBackgroundDark else IOSGroupedBackgroundLight

@Composable
fun iosSecondaryBackground(): Color = if (isInDarkTheme()) IOSSecondaryBackgroundDark else IOSSecondaryBackgroundLight

@Composable
fun iosTertiaryBackground(): Color = if (isInDarkTheme()) IOSTertiaryBackgroundDark else IOSTertiaryBackgroundLight

@Composable
fun iosLabel(): Color = if (isInDarkTheme()) IOSLabelDark else IOSLabelLight

@Composable
fun iosSecondaryLabel(): Color = if (isInDarkTheme()) IOSSecondaryLabelDark else IOSSecondaryLabelLight

@Composable
fun iosTertiaryLabel(): Color = if (isInDarkTheme()) IOSTertiaryLabelDark else IOSTertiaryLabelLight

@Composable
fun iosSeparator(): Color = if (isInDarkTheme()) IOSSeparatorDark else IOSSeparatorLight

@Composable
fun iosGlass(): Color = if (isInDarkTheme()) IOSGlassDark else IOSGlassLight

@Composable
fun iosGlassStroke(): Color = if (isInDarkTheme()) IOSGlassStrokeDark else IOSGlassStrokeLight

@Composable
fun iosError(): Color = if (isInDarkTheme()) IOSRedDark else IOSRed

// MARK: - Shape Tokens

val IOSCornerLarge = RoundedCornerShape(32.dp)
val IOSCornerMedium = RoundedCornerShape(24.dp)
val IOSCornerSmall = RoundedCornerShape(16.dp)
val IOSCornerXSmall = RoundedCornerShape(12.dp)

// MARK: - Icon Tokens

/**
 * 图标尺寸档位。之前各页面随手写 14/18/20/22/24/26dp，同一语义的图标在不同
 * 页面大小不一（列表箭头 20、菜单项 22、小按钮 18…），视觉权重对不齐。
 * 现在全部收敛到下面这些档位，语义相同的图标尺寸必须一致。
 */
object IOS26IconSize {
    /** 行内小图标：圆形小按钮内（34dp 圆底） */
    val Inline = 18.dp

    /** 列表 disclosure 箭头 */
    val Disclosure = 20.dp

    /** 下拉菜单项图标 */
    val MenuItem = 22.dp

    /** 底栏 / 顶栏导航图标 */
    val Navigation = 24.dp

    /** 弹窗主图标（56dp 圆底内） */
    val Hero = 26.dp

    /** 大覆盖层图标（56dp 圆底内，如视频播放） */
    val Display = 30.dp
}

/**
 * 列表 disclosure 箭头统一色调。之前历史记录页用蓝色、工具箱页用灰色，
 * 同一个"可进入下一级"的箭头两种颜色，视觉不统一。iOS 规范里它是次级信息色。
 */
@Composable
fun iosDisclosureColor(): Color = iosSecondaryLabel()

// MARK: - Canvas

@Composable
fun IOS26Canvas(content: @Composable () -> Unit) {
    val bg = iosBackground()
    Box(
        modifier = Modifier.background(
            Brush.verticalGradient(
                listOf(bg, bg, bg.copy(alpha = 0.98f))
            )
        ),
        content = { content() },
    )
}

// MARK: - Glass Panel

@Composable
fun IOS26Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = iosGlass()
    val stroke = iosGlassStroke()
    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = IOSCornerLarge,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .clip(IOSCornerLarge)
            .background(glass)
            .border(0.5.dp, stroke, IOSCornerLarge)
            .padding(20.dp),
        content = content,
    )
}

// MARK: - List Row

@Composable
fun IOS26ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && onClick != null) 0.98f else 1f,
        spring(dampingRatio = 0.78f, stiffness = 680f),
        label = "ios26ListRowScale",
    )
    val rowModifier = if (onClick != null) {
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(IOSCornerXSmall)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
    } else {
        modifier
    }
    Row(
        modifier = rowModifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = iosLabel(),
                letterSpacing = (-0.2).sp,
            )
            subtitle?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 2.dp),
                    letterSpacing = (-0.1).sp,
                )
            }
        }
        trailing()
    }
}

// MARK: - Buttons

@Composable
/**
 * 主操作按钮。默认实色蓝（无需 backdrop 即可用，向后兼容所有旧调用点）；
 * 传入 [backdrop] 时切换为液态玻璃样式 —— 移植自 Nexio 的 LiquidGlassTextButton：
 * 用 backdrop 库采样身后内容做 vibrancy + blur + lens 折射，按压缩放形变 +
 * Confirm 触感不变。
 */
fun IOS26Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop? = null,
) {
    // 按压形变学习自 Nexio 课程表按钮：整体生长 + 沿拖动方向拉伸 + tanh 阻尼
    // 跟手 + 触摸点高光 + Confirm 触感（替代旧的 collectIsPressed 纯缩放）。
    val animationScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val interactionSource = remember { MutableInteractionSource() }
    val isLight = !isInDarkTheme()

    val liquidEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit = remember {
        {
            vibrancy()
            blur(8.dp.toPx())
            lens(8f.dp.toPx(), 24f.dp.toPx())
        }
    }
    // 液态玻璃底色：浅色环境略透一点，让采样出的内容露出来
    val liquidBase = if (enabled) iosBlue().copy(alpha = if (isLight) 0.82f else 0.74f)
    else iosBlue().copy(alpha = 0.38f)
    val onDrawSurface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(liquidBase, interactiveHighlight) {
            {
                drawRect(liquidBase)
                drawRect(Color.Black.copy(alpha = 0.03f * interactiveHighlight.pressProgress))
            }
        }

    Box(
        modifier = modifier
            .graphicsLayer {
                clip = false
                val drag = computeLiquidDragTransform(
                    width = size.width,
                    height = size.height,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    shapeAspectRatio = 1f,
                )
                scaleX = drag.scaleX
                scaleY = drag.scaleY
                translationX = drag.translationX
                translationY = drag.translationY
            }
            .clip(IOSCornerMedium)
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(24.dp, RoundedCornerStyle.Continuous) },
                        effects = liquidEffects,
                        highlight = null,
                        shadow = null,
                        onDrawSurface = onDrawSurface,
                    )
                } else {
                    Modifier.background(if (enabled) iosBlue() else iosBlue().copy(alpha = 0.38f))
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .then(interactiveHighlight.modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            letterSpacing = (-0.2).sp,
        )
    }
}

@Composable
fun IOS26OutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val animationScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope) }
    val interactionSource = remember { MutableInteractionSource() }
    val glass = iosGlass()
    Box(
        modifier = modifier
            .graphicsLayer {
                clip = false
                val drag = computeLiquidDragTransform(
                    width = size.width,
                    height = size.height,
                    pressProgress = interactiveHighlight.pressProgress,
                    offset = interactiveHighlight.offset,
                    density = this,
                    shapeAspectRatio = 1f,
                )
                scaleX = drag.scaleX
                scaleY = drag.scaleY
                translationX = drag.translationX
                translationY = drag.translationY
            }
            .clip(IOSCornerMedium)
            .background(glass)
            .border(0.5.dp, iosGlassStroke(), IOSCornerMedium)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .then(interactiveHighlight.modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = iosLabel(),
            letterSpacing = (-0.2).sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// MARK: - Text Field (iOS 27 style)

@Composable
fun IOS27TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(IOSCornerXSmall),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = iosTertiaryLabel()) } },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = iosTertiaryBackground(),
            unfocusedContainerColor = iosTertiaryBackground(),
            disabledContainerColor = iosTertiaryBackground().copy(alpha = 0.5f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedTextColor = iosLabel(),
            unfocusedTextColor = iosLabel(),
            focusedLabelColor = iosSecondaryLabel(),
            unfocusedLabelColor = iosSecondaryLabel(),
            cursorColor = iosBlue(),
            focusedTrailingIconColor = iosSecondaryLabel(),
            unfocusedTrailingIconColor = iosSecondaryLabel(),
        ),
        shape = IOSCornerXSmall,
    )
}

// MARK: - Progress Indicator

@Composable
fun IOS27CircularProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    strokeWidth: Dp = 2.5.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = iosBlue(),
        strokeWidth = strokeWidth,
    )
}

// MARK: - Choice Chip

@Composable
fun IOS26Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) iosBlue() else iosTertiaryBackground(),
        tween(180),
        label = "ios26ChoiceBg",
    )
    Box(
        modifier = modifier
            .clip(IOSCornerSmall)
            .background(background)
            .border(
                0.5.dp,
                if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                IOSCornerSmall,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else iosLabel(),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = (-0.1).sp,
        )
    }
}

// MARK: - Switch

/**
 * iOS 26 风格开关，移植自 Nexio 课程表所用的 miuix [Switch]（Apache-2.0）。
 * 特性动画：① 滑块弹簧位移（damping 0.7 / stiffness 987，k 值高、回弹干脆）；
 * ② 按压/拖拽/悬停时滑块放大到 1.127× 的「挤缩」手感；③ 轨道颜色用
 * damping 0.99 / stiffness 438.6 的弹簧，开启瞬间会略微过冲再回落；④ 支持
 * 横向拖拽，拖到阈值翻状态，并在两端触发 ToggleOn/Off 触觉；⑤ 点击翻状态也带
 * ToggleOn/Off 触觉。capsule 用 RoundedCornerShape(50) 等价（项目未引入 kyant capsule 库）。
 */
@Composable
fun IOS26Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hapticFeedback = LocalHapticFeedback.current

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rawDragOffset by remember { mutableFloatStateOf(0f) }
    var hasVibrated by remember { mutableStateOf(false) }
    var hasVibratedOnce by remember { mutableStateOf(false) }
    var currentDragInteraction by remember { mutableStateOf<DragInteraction.Start?>(null) }

    val thumbOffsetSpring = remember { spring<Dp>(dampingRatio = 0.7f, stiffness = 987f) }
    val thumbScaleSpring = remember { spring<Float>(dampingRatio = 0.6f, stiffness = 987f) }

    val thumbOffset by animateDpAsState(
        targetValue = (if (checked) 25.dp else 4.dp) + dragOffset.dp,
        animationSpec = thumbOffsetSpring,
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (!enabled) 1f else if (isPressed || isDragged || isHovered) 1.127f else 1f,
        animationSpec = thumbScaleSpring,
    )
    val thumbColor by animateColorAsState(if (enabled) Color.White else Color.White.copy(alpha = 0.6f))
    val trackColor by animateColorAsState(
        if (checked) {
            if (enabled) iosBlue() else iosBlue().copy(alpha = 0.4f)
        } else {
            if (enabled) {
                if (isInDarkTheme()) Color(0xFF48484A) else Color(0xFFE5E5EA)
            } else {
                if (isInDarkTheme()) Color(0xFF2C2C2E) else Color(0xFFF0F0F2)
            }
        },
        animationSpec = spring(dampingRatio = 0.99f, stiffness = 438.6f),
    )

    val toggleableModifier = Modifier.toggleable(
        value = checked,
        onValueChange = { v ->
            onCheckedChange(v)
            hapticFeedback.performHapticFeedback(
                if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
        },
        enabled = enabled,
        role = Role.Switch,
        interactionSource = interactionSource,
    )

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.Center)
            .size(49.dp, 28.dp)
            .clip(RoundedCornerShape(50))
            .drawBehind { drawRect(trackColor) }
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .then(toggleableModifier),
    ) {
        Box(
            Modifier
                .size(20.dp)
                .align(Alignment.CenterStart)
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                .drawBehind { drawCircle(thumbColor) }
                .then(
                    if (enabled) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { dragAmount ->
                                rawDragOffset += dragAmount / 2f
                                dragOffset = if (checked) {
                                    rawDragOffset.coerceIn(-21f, 0f)
                                } else {
                                    rawDragOffset.coerceIn(0f, 21f)
                                }
                                if (dragOffset in -11f..-10f || dragOffset in 10f..11f) {
                                    hasVibratedOnce = false
                                } else if (dragOffset in -20f..-1f || dragOffset in 1f..20f) {
                                    hasVibrated = false
                                } else if (!hasVibrated) {
                                    if ((checked && dragOffset == -21f) || (!checked && dragOffset == 0f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                        hasVibrated = true
                                        hasVibratedOnce = true
                                    } else if ((checked && dragOffset == 0f) || (!checked && dragOffset == 21f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                        hasVibrated = true
                                        hasVibratedOnce = true
                                    }
                                }
                            },
                            onDragStarted = { _ ->
                                currentDragInteraction = DragInteraction.Start().also { interactionSource.tryEmit(it) }
                                hasVibrated = true
                                hasVibratedOnce = false
                                rawDragOffset = 0f
                            },
                            onDragStopped = {
                                if (dragOffset.absoluteValue > 21f / 2f) onCheckedChange(!checked)
                                if (!hasVibratedOnce && dragOffset.absoluteValue >= 1f) {
                                    if ((checked && dragOffset <= -11f) || (!checked && dragOffset <= 10f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOff)
                                    } else if ((checked && dragOffset >= -10f) || (!checked && dragOffset >= 11f)) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.ToggleOn)
                                    }
                                }
                                currentDragInteraction?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
                                dragOffset = 0f
                                rawDragOffset = 0f
                            },
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

// MARK: - Typography

@Composable
fun IOS26LargeTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            letterSpacing = (-0.8).sp,
        )
        subtitle?.let {
            Text(
                it,
                fontSize = 16.sp,
                color = iosSecondaryLabel(),
                letterSpacing = (-0.2).sp,
            )
        }
    }
}

@Composable
fun IOS26PageHeader(title: String, subtitle: String? = null, topPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    Column(Modifier.padding(top = topPadding, bottom = 8.dp)) {
        IOS26LargeTitle(title, subtitle)
    }
}

@Composable
fun IOS26SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosLabel(),
            letterSpacing = (-0.3).sp,
        )
        subtitle?.let {
            Text(
                it,
                fontSize = 14.sp,
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// MARK: - Icon Capsule

@Composable
fun IOS26IconCapsule(
    symbol: String,
    tint: Color = iosBlue(),
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(IOSCornerMedium)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontSize = (size.value * 0.42f).sp,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Divider

@Composable
fun IOS26Divider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = iosSeparator().copy(alpha = 0.5f),
    )
}

// MARK: - Sticky Blur Header (iOS 26 navigation bar)

/** Height of the pinned header bar (below the status bar). */
val IOS26HeaderHeight = 52.dp

/**
 * 玻璃层（含 blur）只到 bar 底边，下面这一段是**不含模糊**的纯色渐隐，
 * 负责把 bar 底边溶进页面。不能太长，否则整块遮罩会显得比 bar 低很多。
 */
private val IOS26HeaderFade = 16.dp

/** 滚动超过这段距离后，吸顶模糊才开始出现。 */
val IOS26HeaderScrollThreshold = 56.dp

/**
 * iOS 26-style pinned navigation bar. It stays attached to the top of the
 * screen while content scrolls underneath, and applies a real backdrop blur
 * (sampled from [backdrop], which should record the scrolling content via
 * `Modifier.layerBackdrop(backdrop)`).
 *
 * 沉浸效果对齐 Nexio 课程表的 ProgressiveBlurTopBar（性能档）：
 * 1. 糊层不再是「统一模糊 + 硬底边 + 独立渐隐条」，而是整块 blur(12dp) 后用
 *    DstIn 垂直渐变把糊层底边**溶解**进页面，过渡连续无硬边；
 * 2. 表面着色 55% × progress（与底栏毛玻璃同档），滚动内容隐约透出；
 * 3. 叠加一层随滚动淡入的轻量 surface 渐变遮罩，只软化上缘。
 * 行为仍匹配 UIKit：顶部时全透明，滚动后出现 compact 标题。
 */
@Composable
fun IOS26BlurHeader(
    title: String,
    backdrop: Backdrop,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(260),
        label = "ios26HeaderProgress",
    )
    val bg = iosBackground()
    val titleColor = iosLabel()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val blurHeight = topInset + IOS26HeaderHeight + IOS26HeaderFade
    // 糊层底边溶解曲线（参照 Nexio edgeFadeStart=0.88 的多档缓收）
    val fadeBrush = remember(bg) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to Color.White,
                0.48f to Color.White,
                0.88f to Color.White.copy(alpha = 0.45f),
                1.0f to Color.Transparent,
            ),
        )
    }
    // 顶部渐变遮罩：多档缓收，不再是一条硬边
    val gradientMaskHeight = IOS26HeaderHeight + 40.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blurHeight + (gradientMaskHeight - IOS26HeaderHeight - IOS26HeaderFade).coerceAtLeast(0.dp)),
    ) {
        // 1) 渐进糊层：blur 采样 + DstIn 垂直淡出，底边溶进页面。
        //    Offscreen 合成是 DstIn 生效的前提。
        Box(
            Modifier
                .fillMaxWidth()
                .height(blurHeight)
                .graphicsLayer {
                    alpha = progress
                    compositingStrategy = CompositingStrategy.Offscreen
                    clip = true
                }
                .drawWithContent {
                    drawContent()
                    drawRect(fadeBrush, blendMode = BlendMode.DstIn)
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    highlight = { null },
                    shadow = { null },
                    // 未滚动时 backdrop 图层根本不录（见
                    // rememberBackdropLayerActive），这里什么都不画。
                    effects = {
                        val p = progress
                        if (p > 0.01f) {
                            // 与结果页底栏毛玻璃同款：blur 18dp，视觉统一
                            blur(18f.dp.toPx())
                        }
                    },
                    onDrawSurface = {
                        val p = progress
                        if (p > 0.01f) {
                            // 着色提到 0.55：模糊内容透得出来，又不糊住标题
                            drawRect(bg.copy(alpha = 0.55f * p))
                        }
                    },
                )
        )
        // 2) surface 渐变遮罩：随滚动淡入，软化顶部边缘
        Box(
            Modifier
                .fillMaxWidth()
                .height(gradientMaskHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = progress }
                .drawBehind {
                    // 之前最高 0.85 的实色遮罩把模糊全盖死了（看起来像没糊）；
                    // 降到轻罩一层，只负责软化上缘，让玻璃透出来
                    drawRect(
                        Brush.verticalGradient(
                            0f to bg.copy(alpha = 0.32f),
                            0.45f to bg.copy(alpha = 0.20f),
                            0.7f to bg.copy(alpha = 0.10f),
                            0.85f to bg.copy(alpha = 0.04f),
                            0.93f to bg.copy(alpha = 0.01f),
                            1f to Color.Transparent,
                        )
                    )
                }
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(IOS26HeaderHeight)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IOS26BackButton(onClick = onBack, backdrop = backdrop)
                Spacer(Modifier.size(14.dp))
            }
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = progress },
            )
            actions()
        }
    }
}

/**
 * 吸顶模糊只在页面滚动后才可能出现，未滚动时把整页内容录进 backdrop 图层
 * 是一次纯浪费的全屏离屏合成（会明显拖慢页面转场）。这里让图层的挂载跟随
 * 模糊的可见性；关闭时延后到模糊淡出结束，避免图层提前卸载导致模糊内容
 * 突然消失。
 *
 * 用法：`Modifier.then(if (active) Modifier.layerBackdrop(backdrop) else Modifier)`
 */
@Composable
fun rememberBackdropLayerActive(scrolled: Boolean): Boolean {
    var active by remember { mutableStateOf(scrolled) }
    LaunchedEffect(scrolled) {
        if (scrolled) {
            active = true
        } else {
            // 与 IOS26BlurHeader 的淡出时长（260ms）对齐并留出余量
            delay(320)
            active = false
        }
    }
    return active
}

/**
 * A page scaffold with a pinned iOS 26 blur header over a [LazyColumn].
 * The scrolling content is recorded into a backdrop layer so the header can
 * blur it in real time. Large titles should remain a content item — the
 * compact header title fades in once the page is scrolled.
 */
@Composable
fun IOS26StickyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    spacedBy: Dp = 20.dp,
    bottomSpacing: Dp = 100.dp,
    applyImePadding: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * 盖在整页之上的浮层（下拉菜单、遮罩等）。拿得到本页的 [Backdrop]，
     * 浮层里的液态玻璃才能采样到页面内容。放在最后绘制，位于吸顶栏之上。
     */
    overlay: @Composable BoxScope.(Backdrop) -> Unit = {},
    /**
     * 强制把内容录进 backdrop 图层（即使没滚动）。浮层里的玻璃（如右上角菜单）
     * 要采样页面内容才有模糊；页面停在顶部时图层默认不录，玻璃会变一片死白。
     */
    recordBackdrop: Boolean = false,
    /**
     * 内容采样层，传给页面内的液态玻璃按钮（[IOS26Button] 的 backdrop 参数）。
     * 非 null 时内容会被额外录进这一层，按钮才能采样到身后内容做出液态玻璃质感。
     */
    contentBackdrop: LayerBackdrop? = null,
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scrollThreshold = with(LocalDensity.current) { IOS26HeaderScrollThreshold.roundToPx() }
    val scrolled by remember(scrollThreshold) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > scrollThreshold
        }
    }
    val backdrop = rememberLayerBackdrop()
    val layerActive = rememberBackdropLayerActive(scrolled || recordBackdrop)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize()) {
        // 内容层：页面底色 + 列表一起录进 backdrop。
        // 底色必须在录制层**内部**——列表顶部那段 contentPadding 在层里是
        // 全透明的，浮层里的玻璃（右上角菜单）正好盖在那一段上，糊过去只
        // 能糊到空气，表现就是"没有模糊"。铺上底色后任意位置都有东西可糊。
        // 吸顶栏与浮层都在这一层之外，不会自引用。
        Box(
            Modifier
                .fillMaxSize()
                .then(if (layerActive) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier)
                .background(iosBackground()),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (applyImePadding) Modifier.imePadding() else Modifier)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(spacedBy),
                contentPadding = PaddingValues(
                    top = topInset + IOS26HeaderHeight + 10.dp,
                    bottom = bottomSpacing,
                ),
                content = content,
            )
        }
        IOS26BlurHeader(
            title = title,
            backdrop = backdrop,
            scrolled = scrolled,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = onBack,
            actions = actions,
        )
        // 浮层最后画：盖在吸顶栏之上
        overlay(backdrop)
    }
}

// MARK: - Back Button (iOS 26 circular glass style)

/**
 * iOS 27 液态玻璃返回按钮：圆形真玻璃（采样页面 backdrop），按压生长 +
 * 沿拖动方向拉伸 + tanh 阻尼跟手 + 触摸点高光。实现在 ui/liquid 的
 * [com.linkgrab.app.ui.liquid.LiquidGlassBackButton]，此处保持原签名
 * 以兼容所有调用页；[backdrop] 为 null 时退回半透明实色。
 */
@Composable
fun IOS26BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
) {
    com.linkgrab.app.ui.liquid.LiquidGlassBackButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
    )
}
