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
        get() = if (isLightMode) "White Pink Cream (Light)" else "Black Cinema (Dark)"

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

// Logo Signature Colors (Pink mix Orange and Pale Blue)
val NeliPink = Color(0xFFFF2E7E)
val NeliWarmOrange = Color(0xFFFF7A00)
val NeliPaleBlue = Color(0xFF70C4FF)
val NeliNeonPurple = Color(0xFF8B00FF)
val NeliNeonCyan = Color(0xFF00E5FF)

// Reactive White with Pink Cream Style <-> Dark Cinema Palette
val NeliBackground: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFF7F6) else Color(0xFF070913)

val NeliBackgroundDeep: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFF0EE) else Color(0xFF04050A)

val NeliBackgroundMid: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFFDFC) else Color(0xFF0C1020)

val NeliBackgroundLight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFDEAE7) else Color(0xFF141A33)

val NeliSurface: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFFFFF) else Color(0xFF0E1326)

val NeliSurfaceVariant: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFF2F0) else Color(0xFF141B36)

val NeliSurfaceHighlight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFE8E4) else Color(0xFF1D264A)

val NeliCardPurple: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFF5F4) else Color(0xFF0F1428)

val NeliCardPurpleLight: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFF0EE) else Color(0xFF151C38)

// 3D Neon Logo Signature Colors (Pink -> Orange -> Violet -> Pale Blue -> Neon Cyan)
val NeliNeonOrange = Color(0xFFFF7A00)
val NeliNeonMagenta = Color(0xFFFF2E7E)
val NeliNeonBlue = Color(0xFF0055FF)

val NeliLogoGradientColors = listOf(
    NeliPink,
    NeliWarmOrange,
    NeliNeonPurple,
    NeliPaleBlue,
    NeliNeonCyan
)

val NeliLogoGradientBrush: Brush
    get() = Brush.horizontalGradient(NeliLogoGradientColors)

val NeliLogoSweepBrush: Brush
    get() = Brush.sweepGradient(NeliLogoGradientColors + NeliPink)

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
    get() = if (NeliThemeManager.isLightMode) Color(0xFF201526) else Color(0xFFF8FAFC)

val NeliTextSecondary: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF6B5875) else Color(0xFFB4C2DC)

val NeliTextMuted: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFF9885A3) else Color(0xFF7E8B9F)

val NeliAccentGreen = Color(0xFF00E676)
val NeliAccentPurple = Color(0xFF8B00FF)

val NeliBorder: Color
    get() = if (NeliThemeManager.isLightMode) Color(0xFFFFDED9) else Color(0xFF281E40)

val NeliMainBackgroundBrush: Brush
    get() = if (NeliThemeManager.isLightMode) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFDFD),
                Color(0xFFFFF4F2),
                Color(0xFFFFF0EE)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF150928),
                Color(0xFF090E26),
                Color(0xFF070B1D),
                Color(0xFF160A1F),
                Color(0xFF050712)
            ),
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(1000f, 2200f)
        )
    }

val NeliCardGradientBrush: Brush
    get() = if (NeliThemeManager.isLightMode) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),
                Color(0xFFFFF2F0)
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
