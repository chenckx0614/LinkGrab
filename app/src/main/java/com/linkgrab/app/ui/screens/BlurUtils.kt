package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Blurred bar background when scrolled.
 * Uses semi-transparent gradient instead of blur to avoid hiding content.
 */
@Composable
fun blurredBarModifier(scrollProgress: Float): Modifier {
    if (scrollProgress <= 0f) return Modifier
    val surface = MiuixTheme.colorScheme.surface
    return Modifier.background(
        Brush.verticalGradient(
            colors = listOf(
                surface.copy(alpha = 0.95f),
                surface.copy(alpha = 0.85f),
            )
        )
    )
}
