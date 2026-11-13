package com.linkgrab.app.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.linkgrab.app.ui.theme.isInDarkTheme

/** Migration boundary: presentation only; no preference writes or business state. */
val LocalUnifiedUi = staticCompositionLocalOf { false }

object LinkGrabUi {
    val PageHorizontal = 16.dp
    val SectionGap = 12.dp
    val TopBarHeight = 56.dp
    val DockHeight = 56.dp
    val MaterialThreshold = 24.dp
    const val MaterialDuration = 250
}

@Composable
fun softGlassDockInset() = maxOf(
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 2.dp,
    22.dp,
)

fun linkGrabUiColors(dark: Boolean) = if (dark) darkColorScheme(
    background = Color(0xFF101010), onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF242424), onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF333333), onSurfaceVariant = Color(0xFFB5B5B5),
    primary = Color(0xFF77AAFF), onPrimary = Color(0xFF001B3F),
    primaryContainer = Color(0xFF22334F), onPrimaryContainer = Color(0xFFD6E5FF),
    outline = Color(0xFF999999), error = Color(0xFFFFABA3), onError = Color(0xFF541C19),
) else lightColorScheme(
    background = Color(0xFFF7F7F7), onBackground = Color(0xFF1A1A1A),
    surface = Color.White, onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFECECEC), onSurfaceVariant = Color(0xFF777777),
    primary = Color(0xFF3482FF), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAF2FF), onPrimaryContainer = Color(0xFF1A1A1A),
    outline = Color(0xFF999999),
)

/** Border-free content surface. Glass is reserved for floating chrome and overlays. */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(ContinuousRoundedRectangle(20.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        content = content,
    )
}

@Composable
fun PreferenceRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(ContinuousRoundedRectangle(12.dp))
            .then(if (onClick == null) Modifier else Modifier.clickable {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(
                subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp, modifier = Modifier.padding(top = 3.dp),
            )
        }
        trailing()
    }
}
