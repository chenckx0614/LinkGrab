package com.linkgrab.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
