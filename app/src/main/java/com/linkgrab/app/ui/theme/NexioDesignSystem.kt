package com.linkgrab.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* =========================================================
 * Nexio / HyperOS 4 inspired design system for LinkGrab.
 * Replaces the previous Apple-style glass with a deeper,
 * more saturated HyperOS look: purple accent, squircle
 * corners, layered glass panels and subtle edge glow.
 * ========================================================= */

// Primary palette — pulled from NexioSchedule's Material3 tokens
val NexioPurple = Color(0xFF6650a4)
val NexioPurpleLight = Color(0xFFD0BCFF)
val NexioPurpleDark = Color(0xFF4A3A7A)
val NexioPink = Color(0xFF7D5260)
val NexioPinkLight = Color(0xFFEFB8C8)

// Semantic text / canvas colors
val NexioInkLight = Color(0xFF1C1B1F)
val NexioInkDark = Color(0xFFE6E1E5)
val NexioSecondaryLight = Color(0xFF605D64)
val NexioSecondaryDark = Color(0xFF938F99)

// Canvas backgrounds with a soft violet tint (light) / deep ink (dark)
val NexioCanvasLight = Color(0xFFF7F5FA)
val NexioCanvasDark = Color(0xFF0F0F12)

// Surface / glass tints
val NexioSurfaceLight = Color.White.copy(alpha = 0.78f)
val NexioSurfaceDark = Color(0xFF23232A).copy(alpha = 0.78f)
val NexioSurfaceStrokeLight = Color.White.copy(alpha = 0.85f)
val NexioSurfaceStrokeDark = Color.White.copy(alpha = 0.10f)

// Edge glow tint used behind panels
val NexioGlowLight = NexioPurpleLight.copy(alpha = 0.22f)
val NexioGlowDark = NexioPurple.copy(alpha = 0.28f)

@Composable
fun nexioInk(): Color = if (isInDarkTheme()) NexioInkDark else NexioInkLight

@Composable
fun nexioSecondary(): Color = if (isInDarkTheme()) NexioSecondaryDark else NexioSecondaryLight

@Composable
fun nexioCanvas(): Color = if (isInDarkTheme()) NexioCanvasDark else NexioCanvasLight

@Composable
fun nexioSurface(): Color = if (isInDarkTheme()) NexioSurfaceDark else NexioSurfaceLight

@Composable
fun nexioSurfaceStroke(): Color = if (isInDarkTheme()) NexioSurfaceStrokeDark else NexioSurfaceStrokeLight

@Composable
fun nexioGlow(): Color = if (isInDarkTheme()) NexioGlowDark else NexioGlowLight

/**
 * Squircle-ish rounded corner radius used across the app.
 * True continuous-curvature squircles would need Kyant Shapes;
 * here we use generously large corner radii to approach that feel.
 */
val NexioSquircleLarge = RoundedCornerShape(28.dp)
val NexioSquircleMedium = RoundedCornerShape(22.dp)
val NexioSquircleSmall = RoundedCornerShape(16.dp)

@Composable
fun NexioCanvas(content: @Composable () -> Unit) {
    val canvasColor = nexioCanvas()
    val glow = nexioGlow()
    Box(
        modifier = Modifier.background(
            Brush.verticalGradient(
                listOf(
                    canvasColor,
                    canvasColor,
                    canvasColor.copy(red = canvasColor.red + 0.01f, blue = canvasColor.blue + 0.02f),
                )
            )
        ),
        content = { content() },
    )
}

/**
 * Main glass panel with a subtle edge glow, translucent surface
 * and thin highlight border. Used for input cards, settings
 * groups and list containers.
 */
@Composable
fun NexioPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val surface = nexioSurface()
    val stroke = nexioSurfaceStroke()
    val glow = nexioGlow()
    Column(
        modifier = modifier
            .shadow(12.dp, NexioSquircleLarge, ambientColor = glow.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(NexioSquircleLarge)
            .background(surface)
            .border(1.dp, stroke, NexioSquircleLarge)
            .padding(20.dp),
        content = content,
    )
}

/**
 * Compact list row with hover press feedback.
 */
@Composable
fun NexioListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = nexioInk())
            subtitle?.let {
                Text(it, fontSize = 14.sp, color = nexioSecondary(), modifier = Modifier.padding(top = 4.dp))
            }
        }
        trailing()
    }
}

/**
 * Primary action button: solid purple, squircle, spring press scale.
 */
@Composable
fun NexioButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "nexioButtonScale",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = NexioSquircleMedium,
        color = if (enabled) NexioPurple.copy(alpha = 0.92f) else NexioPurple.copy(alpha = 0.38f),
        contentColor = Color.White,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 15.dp), contentAlignment = Alignment.Center) {
            Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

/**
 * Secondary / outlined button with translucent surface.
 */
@Composable
fun NexioOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "nexioOutlinedButtonScale",
    )
    val surface = nexioSurface()
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(NexioSquircleMedium)
            .background(surface)
            .border(1.dp, nexioSurfaceStroke(), NexioSquircleMedium)
            .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = nexioInk())
    }
}

/**
 * Segmented / single-select chip.
 */
@Composable
fun NexioChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) NexioPurple else nexioSurface(),
        tween(180),
        label = "nexioChoiceBg",
    )
    Box(
        modifier = modifier
            .clip(NexioSquircleSmall)
            .background(background)
            .border(1.dp, if (selected) NexioPurple else nexioSurfaceStroke(), NexioSquircleSmall)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else nexioInk(),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
    }
}

/**
 * Switch with HyperOS-like purple track and spring thumb.
 */
@Composable
fun NexioSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val trackColor by animateColorAsState(
        if (checked) NexioPurple else if (isInDarkTheme()) Color(0xFF48454F) else Color(0xFFD1D5DB),
        tween(180),
        label = "nexioSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "nexioSwitchThumb",
    )
    Box(
        Modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(interactionSource = interaction, indication = null) { onCheckedChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(27.dp)
                .graphicsLayer { scaleX = if (pressed) 0.9f else 1f; scaleY = if (pressed) 0.9f else 1f }
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * Section header used on scrolling pages.
 */
@Composable
fun NexioSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = nexioInk(), letterSpacing = (-0.8).sp)
        subtitle?.let { Text(it, fontSize = 16.sp, color = nexioSecondary()) }
    }
}

/**
 * Screen-level page title with optional back action placeholder spacing.
 */
@Composable
fun NexioPageHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 40.dp)) {
        NexioSectionTitle(title, subtitle)
    }
}

/**
 * Small floating icon capsule for feature icons.
 */
@Composable
fun NexioIconCapsule(symbol: String, tint: Color = NexioPurple) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(NexioSquircleMedium)
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 38.sp, color = tint, fontWeight = FontWeight.Medium)
    }
}
