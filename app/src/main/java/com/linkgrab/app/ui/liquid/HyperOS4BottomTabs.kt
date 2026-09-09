package com.linkgrab.app.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkgrab.app.ui.theme.isInDarkTheme

/**
 * HyperOS 4 style floating capsule bottom bar.
 *
 * Visual recipe (from the reference screenshots):
 * - A wide, pill-shaped glass container floating above the bottom safe-area.
 * - Each tab is a vertical icon+label column.
 * - Inactive tabs use a soft grey; active tab uses a near-black / high-contrast tint.
 * - Press feedback is a dark, circular shadow/spot behind the pressed tab
 *   (not a full capsule highlight).
 */
@Composable
fun HyperOS4BottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(selectIndex: (Int) -> Unit) -> Unit
) {
    val isLightTheme = !isInDarkTheme()

    // Container colours: very light, slightly warm grey in light mode;
    // a soft dark grey in dark mode.
    val containerColor = if (isLightTheme) {
        Color(0xFFF5F5F5).copy(alpha = 0.94f)
    } else {
        Color(0xFF2A2A2A).copy(alpha = 0.94f)
    }
    val containerStroke = if (isLightTheme) {
        Color(0xFFFFFFFF).copy(alpha = 0.55f)
    } else {
        Color(0xFF3A3A3A).copy(alpha = 0.6f)
    }
    val shadowColor = if (isLightTheme) {
        Color(0xFF000000).copy(alpha = 0.10f)
    } else {
        Color(0xFF000000).copy(alpha = 0.25f)
    }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val tabWidth = constraints.maxWidth.toFloat() / tabsCount

        val offsetAnimation = remember { Animatable(0f) }
        val targetIndex by rememberUpdatedState(selectedTabIndex())

        LaunchedEffect(targetIndex) {
            val target = if (isLtr) targetIndex * tabWidth
            else (tabsCount - 1 - targetIndex) * tabWidth
            offsetAnimation.animateTo(
                targetValue = target,
                animationSpec = tween(260, easing = EaseOutBack)
            )
        }

        // The floating capsule container.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .drawBehind {
                    // Soft radial sheen on the top edge.
                    drawRoundRect(
                        color = containerColor,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()),
                    )
                    drawRoundRect(
                        color = containerStroke,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(32.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content { index ->
                onTabSelected(index)
            }
        }

        // A small pill / dot indicator that slides under the active tab.
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = offsetAnimation.value.toInt() + ((tabWidth - 24.dp.toPx()) / 2).toInt(),
                        y = 0
                    )
                }
                .padding(top = 2.dp)
                .size(width = 24.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .alpha(0.75f)
                .drawBehind {
                    drawRoundRect(
                        color = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFFFFFFF),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
        )
    }
}

@Composable
fun HyperOS4TabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val isLightTheme = !isInDarkTheme()

    val activeColor = if (isLightTheme) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
    val inactiveColor = if (isLightTheme) Color(0xFF8E8E93) else Color(0xFF98989D)
    val iconColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "iconColor"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) activeColor else inactiveColor,
        animationSpec = tween(180),
        label = "labelColor"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressBgColor = if (isLightTheme) Color(0xFF000000).copy(alpha = 0.10f)
    else Color(0xFFFFFFFF).copy(alpha = 0.10f)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .drawBehind {
                if (isPressed) {
                    drawCircle(
                        color = pressBgColor,
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
            }
            .clickableWithRipple(onClick = onClick, interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                color = labelColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.15).sp,
                lineHeight = 12.sp
            )
        }
    }
}

// Minimal local helper so we don't drag in Material3 ripple which fights the
// HyperOS press spot.
private fun Modifier.clickableWithRipple(
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource
): Modifier {
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}