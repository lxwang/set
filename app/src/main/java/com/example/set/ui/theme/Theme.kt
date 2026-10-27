package com.example.set.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFB9AFFF),
    background = Color(0xFF14131A),
    surface = Color(0xFF14131A),
)

@Composable
fun SetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}

/** Card colour for an attribute value, honouring the colorblind palette. */
fun cardColor(color: CardColor, colorblindPalette: Boolean): Color =
    if (colorblindPalette) {
        when (color) {
            CardColor.RED -> AccessibleOrange
            CardColor.GREEN -> AccessibleGreen
            CardColor.PURPLE -> AccessiblePurple
        }
    } else {
        when (color) {
            CardColor.RED -> ClassicRed
            CardColor.GREEN -> ClassicGreen
            CardColor.PURPLE -> ClassicPurple
        }
    }
