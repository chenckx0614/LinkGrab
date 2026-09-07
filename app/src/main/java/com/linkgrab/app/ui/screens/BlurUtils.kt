package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.isInDarkTheme

@Composable
fun BlurredBar(
    backdrop: Any? = null,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = iosGlass()
    val alpha = if (blurEnabled) 0.72f else 0.94f
    Box(modifier.background(base.copy(alpha = alpha))) { content() }
}
