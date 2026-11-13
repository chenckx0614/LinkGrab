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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.linkgrab.app.ui.designsystem.LocalUnifiedUi
import com.linkgrab.app.ui.designsystem.linkGrabUiColors

val LocalColorMode = compositionLocalOf { 0 }

@Composable
fun LinkGrabTheme(
    colorMode: Int = 0,
    visualStyle: Int = 0,
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

    val softGlass = visualStyle == 1 || visualStyle == 2
    val colorScheme = if (softGlass) {
        linkGrabUiColors(isDark)
    } else if (isDark) {
        darkColorScheme(
            primary = IOSBlueDark,
            onPrimary = Color.White,
            primaryContainer = IOSBlueDark.copy(alpha = 0.28f),
            onPrimaryContainer = IOSBlueDark,
            secondary = IOSTeal,
            onSecondary = Color.Black,
            background = IOSBackgroundDark,
            onBackground = IOSLabelDark,
            surface = IOSGroupedBackgroundDark,
            onSurface = IOSLabelDark,
            surfaceVariant = IOSSecondaryBackgroundDark,
            onSurfaceVariant = IOSSecondaryLabelDark,
            outline = IOSSeparatorDark,
        )
    } else {
        lightColorScheme(
            primary = IOSBlue,
            onPrimary = Color.White,
            primaryContainer = IOSBlue.copy(alpha = 0.14f),
            onPrimaryContainer = IOSBlue,
            secondary = IOSTeal,
            onSecondary = Color.White,
            background = IOSBackgroundLight,
            onBackground = IOSLabelLight,
            surface = IOSGroupedBackgroundLight,
            onSurface = IOSLabelLight,
            surfaceVariant = IOSSecondaryBackgroundLight,
            onSurfaceVariant = IOSSecondaryLabelLight,
            outline = IOSSeparatorLight,
        )
    }

    CompositionLocalProvider(
        LocalColorMode provides colorMode,
        LocalUnifiedUi provides softGlass,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
        ) {
            if (softGlass) {
                CompositionLocalProvider(LocalContentColor provides colorScheme.onBackground, content = content)
            } else content()
        }
    }
}

@Composable
fun isInDarkTheme(): Boolean = when (LocalColorMode.current) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}
