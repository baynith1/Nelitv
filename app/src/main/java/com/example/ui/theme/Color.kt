package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Manages Nelitv's dynamic Black (Dark Cinema) <-> White (Clean Light) theme switching.
 * Backed by Compose Snapshot state so all screens & components recompose instantaneously.
 */
object NeliThemeManager {
    private const val PREFS_NAME = "nelitv_theme_prefs"
    private const val KEY_IS_LIGHT_MODE = "is_light_white_mode"

    var isLightMode by mutableStateOf(false)
        private set

    val currentThemeLabel: String
        get() = if (isLightMode) "White (Light Mode)" else "Black (Dark Mode)"

    fun initialize(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isLightMode = prefs.getBoolean(KEY_IS_LIGHT_MODE, false)
        } catch (_: Exception) {
        }
    }

    fun setThemeMode(context: Context, lightWhiteMode: Boolean) {
        isLightMode = lightWhiteMode
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_LIGHT_MODE, lightWhiteMode)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun toggleTheme(context: Context) {
        setThemeMode(context, !isLightMode)
    }
}

// Reactive Black (Dark) <-> White (Light) Palette
val NeliBackground: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFF8FAFC) else Color(0xFF090A0F)

val NeliBackgroundDeep: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFF1F5F9) else Color(0xFF050609)

val NeliBackgroundMid: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFFFFF) else Color(0xFF0F121C)

val NeliBackgroundLight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFE2E8F0) else Color(0xFF171B29)

val NeliSurface: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFFFFF) else Color(0xFF121520)

val NeliSurfaceVariant: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFEFF4FA) else Color(0xFF1A1F2E)

val NeliSurfaceHighlight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFE2E8F0) else Color(0xFF242B3D)

val NeliCardPurple: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFF3E8FF) else Color(0xFF171C2B)

val NeliCardPurpleLight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFEDE9FE) else Color(0xFF21283C)

// Cinema Crimson / Rose Accent
val NeliMagenta = Color(0xFFF41B54)
val NeliMagentaDark = Color(0xFFC81040)
val NeliMagentaLight = Color(0xFFFF547E)
val NeliMagentaGlow = Color(0x55F41B54)

// Pill Badge Accents
val NeliGenreCyan: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF0284C7) else Color(0xFF00D2FF)

val NeliDurationViolet = Color(0xFF6366F1)
val NeliRatingPink = Color(0xFFF41B54)

// Aliases used across components
val NeliCyan = Color(0xFFF41B54)
val NeliCyanDark = Color(0xFF9F1239)
val NeliLiveRed = Color(0xFFE11D48)
val NeliLiveRedGlow = Color(0x66E11D48)

val NeliTextPrimary: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)

val NeliTextSecondary: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF475569) else Color(0xFF94A3B8)

val NeliTextMuted: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF64748B) else Color(0xFF64748B)

val NeliAccentGreen = Color(0xFF10B981)
val NeliAccentPurple = Color(0xFF6366F1)

val NeliBorder: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFCBD5E1) else Color(0xFF252D40)

val NeliMainBackgroundBrush: Brush
    get() = if (NeliThemeManager.isLightMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF8FAFC),
                Color(0xFFF1F5F9)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0D1018),
                Color(0xFF090A0F),
                Color(0xFF050609)
            )
        )
    }

val NeliCardGradientBrush: Brush
    get() = if (NeliThemeManager.isLightMode) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFF1F5F9)
            )
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF151926),
                Color(0xFF1E2436)
            )
        )
    }
