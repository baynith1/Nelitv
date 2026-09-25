package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeliColorScheme = darkColorScheme(
    primary = NeliMagenta,
    onPrimary = Color.White,
    primaryContainer = NeliSurfaceVariant,
    onPrimaryContainer = Color.White,
    secondary = NeliGenreCyan,
    onSecondary = Color.White,
    secondaryContainer = NeliCardPurple,
    onSecondaryContainer = NeliTextSecondary,
    tertiary = NeliAccentPurple,
    onTertiary = Color.White,
    background = NeliBackground,
    onBackground = NeliTextPrimary,
    surface = NeliSurface,
    onSurface = NeliTextPrimary,
    surfaceVariant = NeliSurfaceVariant,
    onSurfaceVariant = NeliTextSecondary,
    outline = NeliBorder,
    error = Color(0xFFFF4D6D),
    onError = Color.White
)

@Composable
fun NeliTVTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeliColorScheme,
        typography = Typography,
        content = content
    )
}
