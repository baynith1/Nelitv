package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NeliDarkColorScheme = darkColorScheme(
    primary = NeliMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1A1F2E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF00D2FF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF171C2B),
    onSecondaryContainer = Color(0xFF94A3B8),
    tertiary = NeliAccentPurple,
    onTertiary = Color.White,
    background = Color(0xFF090A0F),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF121520),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1A1F2E),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF252D40),
    error = Color(0xFFFF4D6D),
    onError = Color.White
)

private val NeliLightColorScheme = lightColorScheme(
    primary = NeliMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF4FA),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF475569),
    tertiary = NeliAccentPurple,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEFF4FA),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFE11D48),
    onError = Color.White
)

@Composable
fun NeliTVTheme(
    content: @Composable () -> Unit
) {
    val activeColorScheme = if (NeliThemeManager.isLightMode) {
        NeliLightColorScheme
    } else {
        NeliDarkColorScheme
    }

    MaterialTheme(
        colorScheme = activeColorScheme,
        typography = Typography,
        content = content
    )
}
