package com.linkgrab.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AppleBlue = Color(0xFF0A84FF)
val AppleInk = Color(0xFF111318)
val AppleSecondary = Color(0xFF687180)
val AppleCanvas = Color(0xFFF7F7F5)

@Composable
fun AppleCanvas(content: @Composable () -> Unit) {
    Box(
        Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF7F7F5), Color(0xFFF3F1EE)))),
        content = { content() },
    )
}

@Composable
fun AppleGlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp))
            .shadow(10.dp, RoundedCornerShape(26.dp), ambientColor = Color(0x18000000), spotColor = Color(0x18000000))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun AppleGlassControl(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.68f))
            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp)),
        content = { content() },
    )
}

@Composable
fun AppleSection(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = AppleInk)
        subtitle?.let { Text(it, fontSize = 14.sp, color = AppleSecondary) }
        AppleGlassPanel(Modifier.fillMaxWidth(), content)
    }
}

@Composable
fun AppleActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "appleButtonScale")
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = RoundedCornerShape(18.dp),
        color = AppleBlue.copy(alpha = 0.86f),
        contentColor = Color.White,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AppleChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(if (selected) AppleBlue else Color.White.copy(alpha = 0.7f), label = "choiceColor")
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(color).border(1.dp, if (selected) AppleBlue else Color.White, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (selected) Color.White else AppleInk, fontWeight = FontWeight.Medium) }
}
