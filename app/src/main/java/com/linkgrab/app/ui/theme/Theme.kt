package com.linkgrab.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

val LocalColorMode = compositionLocalOf { 0 }

@Composable
fun LinkGrabTheme(
    colorMode: Int = 0,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val isDark = when (colorMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = NexioPurpleLight,
            onPrimary = Color(0xFF381E72),
            primaryContainer = NexioPurple.copy(alpha = 0.35f),
            onPrimaryContainer = NexioPurpleLight,
            secondary = NexioPinkLight,
            onSecondary = Color(0xFF492532),
            background = NexioCanvasDark,
            onBackground = NexioInkDark,
            surface = NexioSurfaceDark,
            onSurface = NexioInkDark,
            surfaceVariant = Color(0xFF2A2A32),
            onSurfaceVariant = NexioSecondaryDark,
            outline = Color(0xFF49454F),
        )
    } else {
        lightColorScheme(
            primary = NexioPurple,
            onPrimary = Color.White,
            primaryContainer = NexioPurpleLight.copy(alpha = 0.55f),
            onPrimaryContainer = NexioPurpleDark,
            secondary = NexioPink,
            onSecondary = Color.White,
            background = NexioCanvasLight,
            onBackground = NexioInkLight,
            surface = NexioSurfaceLight,
            onSurface = NexioInkLight,
            surfaceVariant = Color(0xFFE7E0EC),
            onSurfaceVariant = NexioSecondaryLight,
            outline = Color(0xFFCAC4D0),
        )
    }

    CompositionLocalProvider(LocalColorMode provides colorMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

@Composable
fun isInDarkTheme(): Boolean = when (LocalColorMode.current) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}
