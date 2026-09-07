package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.linkgrab.app.ui.theme.NexioSurfaceDark
import com.linkgrab.app.ui.theme.NexioSurfaceLight
import com.linkgrab.app.ui.theme.isInDarkTheme

@Composable
fun BlurredBar(
    backdrop: Any? = null,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = if (isInDarkTheme()) NexioSurfaceDark else NexioSurfaceLight
    val alpha = if (blurEnabled) 0.70f else 0.92f
    Box(modifier.background(base.copy(alpha = alpha))) { content() }
}
