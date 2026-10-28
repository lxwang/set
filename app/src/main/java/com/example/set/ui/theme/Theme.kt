package com.example.set.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.example.set.engine.CardColor

// Surface and ink
val Paper = Color(0xFFFAF7F2)
val Ink = Color(0xFF1B1B1F)
val Muted = Color(0xFF6B6459)
val Accent = Color(0xFF5B4BD6)
val Danger = Color(0xFFB3261E)
val CardFaceBackground = Color(0xFFFFFFFF)
val CardBorder = Color(0xFFD9D2C7)
val Gold = Color(0xFFC9A227)

// Classic card colours
val ClassicRed = Color(0xFFD93A2B)
val ClassicGreen = Color(0xFF1F9D55)
val ClassicPurple = Color(0xFF7A4FD1)

// On a dark card face the classic hues lose contrast, so shapes and the menu
// title use lifted variants.
val DarkRed = Color(0xFFFF7A68)
val DarkGreen = Color(0xFF57D48C)
val DarkPurple = Color(0xFFB79CFF)

// Okabe–Ito-derived palette: stays separable under deuteranopia/protanopia
val AccessibleOrange = Color(0xFFD55E00)
val AccessibleGreen = Color(0xFF009E73)
val AccessiblePurple = Color(0xFFCC79A7)

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E1FB),
    onPrimaryContainer = Color(0xFF1D1647),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0EBE3),
    onSurfaceVariant = Muted,
    outline = CardBorder,
    outlineVariant = Color(0xFFD9D2C7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    error = Danger,
)

// Dark theme is tuned for contrast, not tint: text sits at ~12:1 on the
// background, outlines are clearly visible, and card faces are lighter than the
// board so the grid still reads as physical cards.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC4BBFF),
    onPrimary = Color(0xFF1E1547),
    primaryContainer = Color(0xFF3B3070),
    onPrimaryContainer = Color(0xFFE9E3FF),
    background = Color(0xFF0E0D12),
    onBackground = Color(0xFFF2EFF7),
    surface = Color(0xFF0E0D12),
    onSurface = Color(0xFFF2EFF7),
    surfaceVariant = Color(0xFF2A2734),
    onSurfaceVariant = Color(0xFFD6D1DF),
    surfaceContainerLowest = Color(0xFF211E2A),
    outline = Color(0xFFA9A2B8),
    outlineVariant = Color(0xFF6B6478),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun SetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme) {
        // Without this, Text/Icon outside a Surface fall back to Color.Black,
        // which is invisible on the dark background (header, summary figures).
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            content()
        }
    }
}

/** Card colour for an attribute value, honouring palette and theme. */
fun cardColor(
    color: CardColor,
    colorblindPalette: Boolean,
    darkTheme: Boolean = false,
): Color = if (colorblindPalette) {
    when (color) {
        CardColor.RED -> AccessibleOrange
        CardColor.GREEN -> AccessibleGreen
        CardColor.PURPLE -> AccessiblePurple
    }
} else if (darkTheme) {
    when (color) {
        CardColor.RED -> DarkRed
        CardColor.GREEN -> DarkGreen
        CardColor.PURPLE -> DarkPurple
    }
} else {
    when (color) {
        CardColor.RED -> ClassicRed
        CardColor.GREEN -> ClassicGreen
        CardColor.PURPLE -> ClassicPurple
    }
}
