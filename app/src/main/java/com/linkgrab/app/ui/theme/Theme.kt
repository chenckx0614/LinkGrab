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

    val style = if (visualStyle == 1) AppVisualStyle.HYPER_OS else AppVisualStyle.IOS
    val colorScheme = if (style == AppVisualStyle.HYPER_OS && isDark) {
        darkColorScheme(
            primary = HyperBlueDark,
            onPrimary = Color.White,
            primaryContainer = HyperBlueDark.copy(alpha = 0.24f),
            onPrimaryContainer = HyperBlueDark,
            secondary = Color(0xFF9CB6EC),
            onSecondary = Color(0xFF111C33),
            secondaryContainer = Color(0xFF273650),
            onSecondaryContainer = Color(0xFFD9E4FF),
            background = HyperBackgroundDark,
            onBackground = Color.White,
            surface = HyperSurfaceDark,
            onSurface = Color.White,
            surfaceVariant = HyperSecondarySurfaceDark,
            onSurfaceVariant = Color(0xFFADB0B9),
            outline = Color(0xFF464851),
        )
    } else if (style == AppVisualStyle.HYPER_OS) {
        lightColorScheme(
            primary = HyperBlueLight,
            onPrimary = Color.White,
            primaryContainer = HyperBlueLight.copy(alpha = 0.13f),
            onPrimaryContainer = HyperBlueLight,
            secondary = Color(0xFF5977B2),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5EDFF),
            onSecondaryContainer = Color(0xFF354E82),
            background = HyperBackgroundLight,
            onBackground = Color(0xFF17191E),
            surface = HyperSurfaceLight,
            onSurface = Color(0xFF17191E),
            surfaceVariant = HyperSecondarySurfaceLight,
            onSurfaceVariant = Color(0xFF777B85),
            outline = Color(0xFFE3E5EA),
        )
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
        LocalAppVisualStyle provides style,
    ) {
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
