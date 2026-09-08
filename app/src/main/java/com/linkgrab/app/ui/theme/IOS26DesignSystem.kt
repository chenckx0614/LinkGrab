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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy

/* =========================================================
 * iOS 26 Liquid Glass Design System for LinkGrab
 *
 * Visual principles:
 * - System Blue as the single accent color
 * - Ultra-clean grouped backgrounds (#F2F2F7 / #000000)
 * - Translucent glass surfaces with subtle blur, vibrancy
 *   and hairline highlights
 * - Continuous, generous corner radii (squircle-like)
 * - Refined typography with tight tracking and clear hierarchy
 * - Physics-based spring animations for all interactions
 * ========================================================= */

// MARK: - Color Tokens

val IOSBlue = Color(0xFF007AFF)
val IOSBlueDark = Color(0xFF0A84FF)
val IOSGreen = Color(0xFF34C759)
val IOSRed = Color(0xFFFF3B30)
val IOSOrange = Color(0xFFFF9500)
val IOSPurple = Color(0xFFAF52DE)
val IOSTeal = Color(0xFF5AC8FA)

// Backgrounds
val IOSBackgroundLight = Color(0xFFF2F2F7)
val IOSBackgroundDark = Color(0xFF000000)

// Grouped backgrounds (for cards / lists)
val IOSGroupedBackgroundLight = Color(0xFFFFFFFF)
val IOSGroupedBackgroundDark = Color(0xFF1C1C1E)

// Secondary / tertiary backgrounds
val IOSSecondaryBackgroundLight = Color(0xFFF2F2F7)
val IOSSecondaryBackgroundDark = Color(0xFF2C2C2E)
val IOSTertiaryBackgroundLight = Color(0xFFE5E5EA)
val IOSTertiaryBackgroundDark = Color(0xFF3A3A3C)

// Labels
val IOSLabelLight = Color(0xFF000000)
val IOSLabelDark = Color(0xFFFFFFFF)
val IOSSecondaryLabelLight = Color(0xFF8E8E93)
val IOSSecondaryLabelDark = Color(0xFF98989F)
val IOSTertiaryLabelLight = Color(0xFFC7C7CC)
val IOSTertiaryLabelDark = Color(0xFF48484A)

// Separators
val IOSSeparatorLight = Color(0xFFC6C6C8)
val IOSSeparatorDark = Color(0xFF38383A)

// Glass effect colors
val IOSGlassLight = Color.White.copy(alpha = 0.72f)
val IOSGlassDark = Color(0xFF1C1C1E).copy(alpha = 0.78f)
val IOSGlassStrokeLight = Color.White.copy(alpha = 0.55f)
val IOSGlassStrokeDark = Color.White.copy(alpha = 0.10f)

// MARK: - Theme Helpers

@Composable
fun iosBlue(): Color = if (isInDarkTheme()) IOSBlueDark else IOSBlue

@Composable
fun iosBackground(): Color = if (isInDarkTheme()) IOSBackgroundDark else IOSBackgroundLight

@Composable
fun iosGroupedBackground(): Color = if (isInDarkTheme()) IOSGroupedBackgroundDark else IOSGroupedBackgroundLight

@Composable
fun iosSecondaryBackground(): Color = if (isInDarkTheme()) IOSSecondaryBackgroundDark else IOSSecondaryBackgroundLight

@Composable
fun iosTertiaryBackground(): Color = if (isInDarkTheme()) IOSTertiaryBackgroundDark else IOSTertiaryBackgroundLight

@Composable
fun iosLabel(): Color = if (isInDarkTheme()) IOSLabelDark else IOSLabelLight

@Composable
fun iosSecondaryLabel(): Color = if (isInDarkTheme()) IOSSecondaryLabelDark else IOSSecondaryLabelLight

@Composable
fun iosTertiaryLabel(): Color = if (isInDarkTheme()) IOSTertiaryLabelDark else IOSTertiaryLabelLight

@Composable
fun iosSeparator(): Color = if (isInDarkTheme()) IOSSeparatorDark else IOSSeparatorLight

@Composable
fun iosGlass(): Color = if (isInDarkTheme()) IOSGlassDark else IOSGlassLight

@Composable
fun iosGlassStroke(): Color = if (isInDarkTheme()) IOSGlassStrokeDark else IOSGlassStrokeLight

// MARK: - Shape Tokens

val IOSCornerLarge = RoundedCornerShape(32.dp)
val IOSCornerMedium = RoundedCornerShape(24.dp)
val IOSCornerSmall = RoundedCornerShape(16.dp)
val IOSCornerXSmall = RoundedCornerShape(12.dp)

// MARK: - Canvas

@Composable
fun IOS26Canvas(content: @Composable () -> Unit) {
    val bg = iosBackground()
    Box(
        modifier = Modifier.background(
            Brush.verticalGradient(
                listOf(bg, bg, bg.copy(alpha = 0.98f))
            )
        ),
        content = { content() },
    )
}

// MARK: - Glass Panel

@Composable
fun IOS26Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = iosGlass()
    val stroke = iosGlassStroke()
    Column(
        modifier = modifier
            .shadow(
                elevation = 8.dp,
                shape = IOSCornerLarge,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.10f),
            )
            .clip(IOSCornerLarge)
            .background(glass)
            .border(0.5.dp, stroke, IOSCornerLarge)
            .padding(20.dp),
        content = content,
    )
}

// MARK: - List Row

@Composable
fun IOS26ListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = iosLabel(),
                letterSpacing = (-0.2).sp,
            )
            subtitle?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    color = iosSecondaryLabel(),
                    modifier = Modifier.padding(top = 2.dp),
                    letterSpacing = (-0.1).sp,
                )
            }
        }
        trailing()
    }
}

// MARK: - Buttons

@Composable
fun IOS26Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.78f, stiffness = 680f),
        label = "ios26ButtonScale",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = source,
        shape = IOSCornerMedium,
        color = if (enabled) iosBlue() else iosBlue().copy(alpha = 0.38f),
        contentColor = Color.White,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Box(
            Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                letterSpacing = (-0.2).sp,
            )
        }
    }
}

@Composable
fun IOS26OutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.78f, stiffness = 680f),
        label = "ios26OutlinedScale",
    )
    val glass = iosGlass()
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(IOSCornerMedium)
            .background(glass)
            .border(0.5.dp, iosGlassStroke(), IOSCornerMedium)
            .clickable(interactionSource = source, indication = null, enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = iosLabel(),
            letterSpacing = (-0.2).sp,
        )
    }
}

// MARK: - Choice Chip

@Composable
fun IOS26Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        if (selected) iosBlue() else iosTertiaryBackground(),
        tween(180),
        label = "ios26ChoiceBg",
    )
    Box(
        modifier = modifier
            .clip(IOSCornerSmall)
            .background(background)
            .border(
                0.5.dp,
                if (selected) iosBlue() else iosSeparator().copy(alpha = 0.5f),
                IOSCornerSmall,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else iosLabel(),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = (-0.1).sp,
        )
    }
}

// MARK: - Switch

@Composable
fun IOS26Switch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val trackColor by animateColorAsState(
        if (checked) iosBlue() else if (isInDarkTheme()) Color(0xFF48484A) else Color(0xFFE5E5EA),
        tween(200),
        label = "ios26SwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) 20.dp else 2.dp,
        spring(dampingRatio = 0.72f, stiffness = 720f),
        label = "ios26SwitchThumb",
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
                .shadow(1.dp, CircleShape)
        )
    }
}

// MARK: - Typography

@Composable
fun IOS26LargeTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = iosLabel(),
            letterSpacing = (-0.8).sp,
        )
        subtitle?.let {
            Text(
                it,
                fontSize = 16.sp,
                color = iosSecondaryLabel(),
                letterSpacing = (-0.2).sp,
            )
        }
    }
}

@Composable
fun IOS26PageHeader(title: String, subtitle: String? = null, topPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    Column(Modifier.padding(top = topPadding, bottom = 8.dp)) {
        IOS26LargeTitle(title, subtitle)
    }
}

@Composable
fun IOS26SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = iosLabel(),
            letterSpacing = (-0.3).sp,
        )
        subtitle?.let {
            Text(
                it,
                fontSize = 14.sp,
                color = iosSecondaryLabel(),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// MARK: - Icon Capsule

@Composable
fun IOS26IconCapsule(
    symbol: String,
    tint: Color = iosBlue(),
    size: androidx.compose.ui.unit.Dp = 72.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(IOSCornerMedium)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            fontSize = (size.value * 0.42f).sp,
            color = tint,
            fontWeight = FontWeight.Medium,
        )
    }
}

// MARK: - Divider

@Composable
fun IOS26Divider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = 0.5.dp,
        color = iosSeparator().copy(alpha = 0.5f),
    )
}

// MARK: - Sticky Blur Header (iOS 26 navigation bar)

/** Height of the pinned header bar (below the status bar). */
val IOS26HeaderHeight = 52.dp

/**
 * Extra height below the bar over which the blur fades out. Without it the
 * blurred region ends on a hard edge, which reads as a divider line.
 */
private val IOS26HeaderFade = 28.dp

/**
 * iOS 26-style pinned navigation bar. It stays attached to the top of the
 * screen while content scrolls underneath, and applies a real backdrop blur
 * (sampled from [backdrop], which should record the scrolling content via
 * `Modifier.layerBackdrop(backdrop)`).
 *
 * Behaviour matches UIKit: the bar is transparent at the top of the page;
 * once [scrolled] is true the blur + background tint fade in and the compact
 * title appears. There is deliberately **no** divider — the bar dissolves
 * into the page through [IOS26HeaderFade] of gradient instead.
 */
@Composable
fun IOS26BlurHeader(
    title: String,
    backdrop: Backdrop,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val progress by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(260),
        label = "ios26HeaderProgress",
    )
    val bg = iosBackground()
    val titleColor = iosLabel()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + IOS26HeaderHeight + IOS26HeaderFade)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    vibrancy()
                    blur((0.1f + 26f * progress).dp.toPx())
                },
                // Gradient mask instead of a flat fill: opaque under the bar,
                // fully transparent at the bottom of the fade — no seam.
                onDrawSurface = {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to bg.copy(alpha = 0.88f * progress),
                            0.55f to bg.copy(alpha = 0.72f * progress),
                            0.82f to bg.copy(alpha = 0.28f * progress),
                            1f to bg.copy(alpha = 0f),
                        ),
                    )
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topInset)
                .height(IOS26HeaderHeight)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IOS26BackButton(onClick = onBack)
                Spacer(Modifier.size(14.dp))
            }
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = progress },
            )
            actions()
        }
    }
}

/**
 * A page scaffold with a pinned iOS 26 blur header over a [LazyColumn].
 * The scrolling content is recorded into a backdrop layer so the header can
 * blur it in real time. Large titles should remain a content item — the
 * compact header title fades in once the page is scrolled.
 */
@Composable
fun IOS26StickyPage(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    spacedBy: Dp = 20.dp,
    bottomSpacing: Dp = 100.dp,
    applyImePadding: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8
        }
    }
    val backdrop = rememberLayerBackdrop()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .then(if (applyImePadding) Modifier.imePadding() else Modifier)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(spacedBy),
            contentPadding = PaddingValues(
                top = topInset + IOS26HeaderHeight + 10.dp,
                bottom = bottomSpacing,
            ),
            content = content,
        )
        IOS26BlurHeader(
            title = title,
            backdrop = backdrop,
            scrolled = scrolled,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = onBack,
            actions = actions,
        )
    }
}

// MARK: - Back Button (iOS 26 circular glass style)

/**
 * iOS 26-style back button: circular frosted-glass capsule with a
 * chevron-left glyph. Press feedback is a quick scale-down plus a
 * subtle background darkening, matching UIKit's UIHover / touch-down
 * feel on iOS 26.
 */
@Composable
fun IOS26BackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 700f),
        label = "ios26BackScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.55f else 1f,
        animationSpec = tween(120),
        label = "ios26BackBg",
    )
    val chevronColor = iosLabel()
    // iOS 26 uses a subtle tertiary system fill instead of opaque white glass
    val fillBase = if (isInDarkTheme()) Color.White.copy(alpha = 0.14f)
    else Color(0xFF787880).copy(alpha = 0.10f)
    val strokeBase = if (isInDarkTheme()) Color.White.copy(alpha = 0.08f)
    else Color.Black.copy(alpha = 0.05f)
    Box(
        modifier = modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fillBase.copy(alpha = fillBase.alpha * bgAlpha))
            .border(0.5.dp, strokeBase, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // chevron.left — drawn manually for the exact iOS glyph proportions
        androidx.compose.foundation.Canvas(modifier = Modifier.size(11.dp, 17.dp)) {
            val strokeWidth = 2.2.dp.toPx()
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.85f, 0f)
                lineTo(size.width * 0.15f, size.height / 2f)
                lineTo(size.width * 0.85f, size.height)
            }
            drawPath(
                path = path,
                color = chevronColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
    }
}
