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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.linkgrab.app.ui.theme.IOSCornerLarge
import com.linkgrab.app.ui.theme.IOSCornerMedium
import com.linkgrab.app.ui.theme.IOSBackgroundDark
import com.linkgrab.app.ui.theme.IOSBackgroundLight
import com.linkgrab.app.ui.theme.IOSBlue
import com.linkgrab.app.ui.theme.IOSBlueDark
import com.linkgrab.app.ui.theme.iosGlass
import com.linkgrab.app.ui.theme.iosGlassStroke
import com.linkgrab.app.ui.theme.isInDarkTheme

private fun linkGrabGradient(isDark: Boolean): Brush = Brush.verticalGradient(
    if (isDark) {
        listOf(
            IOSBackgroundDark,
            IOSBackgroundDark,
            IOSBlueDark.copy(alpha = 0.05f),
        )
    } else {
        listOf(
            IOSBackgroundLight,
            IOSBackgroundLight,
            IOSBlue.copy(alpha = 0.03f),
        )
    }
)

@Composable
fun LinkGrabBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(linkGrabGradient(isInDarkTheme())), content = { content() })
}

@Composable
fun Modifier.ios26GlassSurface(shape: RoundedCornerShape = IOSCornerLarge): Modifier = this
    .shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.05f),
        spotColor = Color.Black.copy(alpha = 0.08f),
    )
    .clip(shape)
    .background(iosGlass())
    .border(0.5.dp, iosGlassStroke(), shape)

@Composable
fun LiquidPanel(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.ios26GlassSurface().then(Modifier),
        content = content,
    )
}
