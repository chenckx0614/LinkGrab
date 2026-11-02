package com.linkgrab.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppVisualStyle { IOS, HYPER_OS }

val LocalAppVisualStyle = staticCompositionLocalOf { AppVisualStyle.IOS }

@Composable
fun isHyperOsStyle(): Boolean = LocalAppVisualStyle.current == AppVisualStyle.HYPER_OS

val HyperBlueLight = Color(0xFF387BF6)
val HyperBlueDark = Color(0xFF6A9CFF)
val HyperBackgroundLight = Color(0xFFF7F8FA)
val HyperBackgroundDark = Color(0xFF101114)
val HyperSurfaceLight = Color(0xFFFFFFFF)
val HyperSurfaceDark = Color(0xFF202126)
val HyperSecondarySurfaceLight = Color(0xFFF0F2F6)
val HyperSecondarySurfaceDark = Color(0xFF2A2C32)
