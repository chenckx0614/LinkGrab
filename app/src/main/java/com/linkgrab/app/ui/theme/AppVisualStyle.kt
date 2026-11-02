package com.linkgrab.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppVisualStyle { IOS, HYPER_OS }

val LocalAppVisualStyle = staticCompositionLocalOf { AppVisualStyle.IOS }

@Composable
fun isHyperOsStyle(): Boolean = LocalAppVisualStyle.current == AppVisualStyle.HYPER_OS

// 取值来自 miuix 默认色板（top.yukonga.miuix.kmp.theme.Colors）：
// primary 亮 #3482FF / 暗 #277AF7；surface（页面底）亮 #F4F4F4 / 暗 Black；
// surfaceContainer（卡片）白 / #242424；secondaryContainer 亮 #ECECEC / 暗 #363636。
val HyperBlueLight = Color(0xFF3482FF)
val HyperBlueDark = Color(0xFF277AF7)
val HyperBackgroundLight = Color(0xFFF4F4F4)
val HyperBackgroundDark = Color(0xFF000000)
val HyperSurfaceLight = Color(0xFFFFFFFF)
val HyperSurfaceDark = Color(0xFF242424)
val HyperSecondarySurfaceLight = Color(0xFFECECEC)
val HyperSecondarySurfaceDark = Color(0xFF363636)
