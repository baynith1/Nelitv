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
    private const val KEY_IS_PIP_ALLOWED = "is_pip_background_allowed"
    private const val KEY_FORCE_TV_MODE = "key_force_tv_mode_pref"

    var isLightMode by mutableStateOf(false)
        private set

    var isPipModeAllowed by mutableStateOf(true)
        private set

    // null = Auto-detect hardware; true = Always TV Layout; false = Always Mobile Layout
    var forceTvMode by mutableStateOf<Boolean?>(null)
        private set

    val currentThemeLabel: String
        get() = if (isLightMode) "White (Light Mode)" else "Black (Dark Mode)"

    val currentPipStatusLabel: String
        get() = if (isPipModeAllowed) "Allowed • Plays in Background PiP when leaving app" else "Disallowed • Background PiP Off"

    val tvModeStatusLabel: String
        get() = when (forceTvMode) {
            null -> "Automatic (Detect TV or Mobile)"
            true -> "Smart TV Mode (Forced)"
            false -> "Mobile Mode (Forced)"
        }

    fun initialize(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isLightMode = prefs.getBoolean(KEY_IS_LIGHT_MODE, false)
            isPipModeAllowed = prefs.getBoolean(KEY_IS_PIP_ALLOWED, true)
            if (prefs.contains(KEY_FORCE_TV_MODE)) {
                forceTvMode = prefs.getBoolean(KEY_FORCE_TV_MODE, false)
            }
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

    fun setPipModeAllowed(context: Context, allowed: Boolean) {
        isPipModeAllowed = allowed
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_IS_PIP_ALLOWED, allowed)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun setTvModeSetting(context: Context, mode: Boolean?) {
        forceTvMode = mode
        try {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (mode == null) {
                editor.remove(KEY_FORCE_TV_MODE)
            } else {
                editor.putBoolean(KEY_FORCE_TV_MODE, mode)
            }
            editor.apply()
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

// 3D Neon Logo Signature Colors (Neon Amber -> Magenta -> Electric Purple -> Royal Blue -> Neon Cyan)
val NeliNeonOrange = Color(0xFFFF8500)
val NeliNeonMagenta = Color(0xFFFF007A)
val NeliNeonPurple = Color(0xFF8B00FF)
val NeliNeonBlue = Color(0xFF0055FF)
val NeliNeonCyan = Color(0xFF00E5FF)

val NeliLogoGradientColors = listOf(
    NeliNeonOrange,
    NeliNeonMagenta,
    NeliNeonPurple,
    NeliNeonBlue,
    NeliNeonCyan
)

val NeliLogoGradientBrush: Brush
    get() = Brush.horizontalGradient(NeliLogoGradientColors)

val NeliLogoSweepBrush: Brush
    get() = Brush.sweepGradient(NeliLogoGradientColors + NeliNeonOrange)

// Cinema Crimson / Rose Accent updated to Vivid Neon Magenta
val NeliMagenta = Color(0xFFFF007A)
val NeliMagentaDark = Color(0xFFC7005F)
val NeliMagentaLight = Color(0xFFFF409E)
val NeliMagentaGlow = Color(0x66FF007A)

// Pill Badge Accents
val NeliGenreCyan: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF0077B6) else Color(0xFF00E5FF)

val NeliDurationViolet = Color(0xFF8B00FF)
val NeliRatingPink = Color(0xFFFF007A)

// Aliases used across components
val NeliCyan = Color(0xFF00E5FF)
val NeliCyanDark = Color(0xFF0055FF)
val NeliLiveRed = Color(0xFFFF0055)
val NeliLiveRedGlow = Color(0x66FF0055)

val NeliTextPrimary: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF0F172A) else Color(0xFFF8FAFC)

val NeliTextSecondary: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF475569) else Color(0xFFB4C2DC)

val NeliTextMuted: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF64748B) else Color(0xFF7E8B9F)

val NeliAccentGreen = Color(0xFF00E676)
val NeliAccentPurple = Color(0xFF8B00FF)

val NeliBorder: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFCBD5E1) else Color(0xFF281E40)

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
                Color(0xFF0E0919),
                Color(0xFF07040D),
                Color(0xFF040208)
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
                Color(0xFF161026),
                Color(0xFF1E1634)
            )
        )
    }
