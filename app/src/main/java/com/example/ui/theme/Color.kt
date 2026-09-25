package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep Cosmic Purple & Neon Magenta Palette (matching reference UI)
val NeliBackground = Color(0xFF16072E)
val NeliBackgroundDeep = Color(0xFF110424)
val NeliBackgroundMid = Color(0xFF260D4D)
val NeliBackgroundLight = Color(0xFF391570)

val NeliSurface = Color(0xFF2B1055)
val NeliSurfaceVariant = Color(0xFF3D1875)
val NeliSurfaceHighlight = Color(0xFF522299)
val NeliCardPurple = Color(0xFF38156B)
val NeliCardPurpleLight = Color(0xFF4C1E8E)

// Vibrant Hot Pink / Magenta Accent
val NeliMagenta = Color(0xFFFF1E8E)
val NeliMagentaDark = Color(0xFFD60A70)
val NeliMagentaLight = Color(0xFFFF5CAD)
val NeliMagentaGlow = Color(0x66FF1E8E)

// Pill Badge Accents from reference mockup
val NeliGenreCyan = Color(0xFF00B4D8)
val NeliDurationViolet = Color(0xFF7B2CBF)
val NeliRatingPink = Color(0xFFFF1E8E)

// Legacy aliases used across components (mapped to new theme)
val NeliCyan = Color(0xFFFF1E8E)
val NeliCyanDark = Color(0xFF9D174D)
val NeliLiveRed = Color(0xFFFF1E8E)
val NeliLiveRedGlow = Color(0x66FF1E8E)

val NeliTextPrimary = Color(0xFFFFFFFF)
val NeliTextSecondary = Color(0xFFD0B8F8)
val NeliTextMuted = Color(0xFF9C7FD1)

val NeliAccentGreen = Color(0xFF10B981)
val NeliAccentPurple = Color(0xFFA855F7)
val NeliBorder = Color(0xFF5A279E)

val NeliMainBackgroundBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF280D52),
        Color(0xFF1D083D),
        Color(0xFF14052B)
    )
)

val NeliCardGradientBrush = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF3E1878),
        Color(0xFF5621A3)
    )
)
