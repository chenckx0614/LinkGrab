package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.linkgrab.app.ui.theme.IOS26IconSize
import com.linkgrab.app.ui.theme.iosDisclosureColor
import kotlinx.coroutines.delay

/**
 * iOS 26-style button press: subtle, quick, with slight overshoot.
 */
@Composable
fun Modifier.scaleOnPress(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 750f,
        ),
        label = "ios26PressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * iOS 26-style card press: gentler, more refined.
 */
@Composable
fun Modifier.cardPressEffect(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 700f,
        ),
        label = "ios26CardScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * iOS 26-style list item entrance: fade + rise with spring physics.
 */
@Composable
fun SlideInItem(
    visible: Boolean,
    delay: Int = 0,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = 600f,
            ),
            initialOffsetY = { it / 4 },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 280,
                delayMillis = delay,
            )
        ),
        exit = slideOutVertically(
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 800f,
            ),
            targetOffsetY = { -it / 5 },
        ) + fadeOut(tween(180)),
    ) {
        content()
    }
}

/**
 * iOS 26-style counter animation.
 */
@Composable
fun animatedCounter(targetValue: Int): Int {
    val animatedValue by androidx.compose.animation.core.animateIntAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = 500f,
        ),
        label = "ios26Counter",
    )
    return animatedValue
}

/**
 * 统一的「卡片展开」：头部点击 → chevron 弹簧旋转 90° + 内容高度弹簧展开 + 淡入。
 *
 * 以前各处各写一份 `AnimatedVisibility(expandVertically())`，曲线一致但手感
 * 偏"平"（默认 tween）。这里统一走 spring，并补上触觉反馈。
 */
@Composable
fun ExpandableSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 520f),
        label = "expandChevron",
    )
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    onToggle()
                }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { header() }
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
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
                expandFrom = Alignment.Top,
            ) + fadeIn(tween(180)),
            exit = shrinkVertically(
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 700f),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(tween(120)),
        ) {
            Box(Modifier.padding(top = 6.dp)) { content() }
        }
    }
}

/**
 * 错误抖动：`trigger` 每 +1 播一次（水平 ±6dp 抖 3 下，末尾回正）。
 * 用 Animatable 而不是 infiniteRepeatable：抖完就停，不常驻。
 */
@Composable
fun Modifier.shakeOnTrigger(trigger: Int, amplitudeDp: Float = 6f): Modifier {
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val amp = with(density) { amplitudeDp.dp.toPx() }
        val spec = spring<Float>(dampingRatio = 0.42f, stiffness = 2200f)
        repeat(3) {
            offset.animateTo(amp, spec)
            offset.animateTo(-amp, spec)
        }
        offset.animateTo(0f, tween(120))
    }
    return this.graphicsLayer { translationX = offset.value }
}