package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * 按钮按下缩放效果
 */
@Composable
fun Modifier.scaleOnPress(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "scale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 卡片按下缩放效果
 */
@Composable
fun Modifier.cardPressEffect(): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "cardScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 从底部滑入 + 淡入
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
            animationSpec = tween(durationMillis = 300, delayMillis = delay),
            initialOffsetY = { it / 3 },
        ) + fadeIn(tween(durationMillis = 300, delayMillis = delay)),
        exit = fadeOut(tween(200)),
    ) {
        content()
    }
}

/**
 * 数字递增动画
 */
@Composable
fun animatedCounter(targetValue: Int): Int {
    val animatedValue by androidx.compose.animation.core.animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 300),
        label = "counter",
    )
    return animatedValue
}

