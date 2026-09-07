package com.linkgrab.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.linkgrab.app.ui.theme.NexioCanvasDark
import com.linkgrab.app.ui.theme.NexioCanvasLight
import com.linkgrab.app.ui.theme.NexioPurple
import com.linkgrab.app.ui.theme.NexioPurpleLight
import com.linkgrab.app.ui.theme.isInDarkTheme

private fun linkGrabGradient(isDark: Boolean): Brush = Brush.verticalGradient(
    if (isDark) {
        listOf(
            NexioCanvasDark,
            NexioCanvasDark,
            NexioPurple.copy(alpha = 0.12f),
        )
    } else {
        listOf(
            Color(0xFFF8F6FF),
            NexioCanvasLight,
            NexioPurpleLight.copy(alpha = 0.12f),
        )
    }
)

@Composable
fun LinkGrabBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(linkGrabGradient(isInDarkTheme())), content = { content() })
}

fun Modifier.liquidSurface(shape: RoundedCornerShape = RoundedCornerShape(28.dp)): Modifier = this
    .clip(shape)
    .background(Color.White.copy(alpha = 0.66f))
    .border(1.dp, Color.White.copy(alpha = 0.9f), shape)

@Composable
fun LiquidPanel(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.liquidSurface().then(Modifier),
        content = content,
    )
}
