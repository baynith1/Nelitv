package com.example.ui.tv

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

object NeliTvDetector {
    /**
     * Accurately detects whether the running environment is hardware Android Smart TV / TV Box / Leanback / Fire TV.
     */
    fun isHardwareTelevision(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val config = context.resources.configuration
        if ((config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }
        val pm = context.packageManager
        if (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.software.leanback") ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature("amazon.hardware.fire_tv")
        ) {
            return true
        }
        // TV boxes (e.g., Amlogic, Allwinner, Rockchip) that lack touchscreens and are wide screens
        val hasNoTouch = !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        if (hasNoTouch && config.screenWidthDp >= 600) {
            return true
        }
        return false
    }

    /**
     * Returns true if running on a Smart TV, or if the user enabled Smart TV Mode in Account settings.
     */
    fun isTelevision(context: Context): Boolean {
        return com.example.ui.theme.NeliThemeManager.forceTvMode ?: isHardwareTelevision(context)
    }
}

/**
 * Animated Gemini Halo Gradient palette for Smart TV Remote focus boundaries:
 * Cyan -> Purple -> Magenta -> Electric Blue -> Cyan.
 */
val GeminiFocusGradientColors = listOf(
    Color(0xFF00E5FF), // Gemini Electric Cyan
    Color(0xFF7C4DFF), // Deep Violet
    Color(0xFFE040FB), // Magenta Glow
    Color(0xFF2979FF), // Electric Blue
    Color(0xFF00E5FF)  // Wrap Cyan
)

/**
 * High-performance Compose modifier that adds:
 * 1. TV Remote Focusability & D-Pad Enter/Center click trigger.
 * 2. Animated scale expansion (Growing effect to 1.08x).
 * 3. Animated breathing "Gemini effect" glowing boundary border.
 * 4. Elevated z-index so focused cards pop above their row/grid neighbors.
 */
@Composable
fun Modifier.tvGeminiFocusable(
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.08f,
    borderWidth: Dp = 2.5.dp,
    onClick: (() -> Unit)? = null
): Modifier {
    var isFocused by remember { mutableStateOf(false) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "tv_scale_anim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "gemini_halo_pulse")
    val haloPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gemini_alpha_pulse"
    )

    val geminiBrush = remember(haloPulseAlpha) {
        Brush.sweepGradient(
            colors = GeminiFocusGradientColors.map { it.copy(alpha = haloPulseAlpha) }
        )
    }

    return this
        .zIndex(if (isFocused) 10f else 1f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .onFocusChanged { state ->
            isFocused = state.isFocused || state.hasFocus
        }
        .focusable()
        .then(
            if (isFocused) {
                Modifier
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        spotColor = Color(0xFF00E5FF),
                        ambientColor = Color(0xFFE040FB)
                    )
                    .border(
                        width = borderWidth,
                        brush = geminiBrush,
                        shape = shape
                    )
            } else {
                Modifier
            }
        )
        .onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyUp &&
                (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
            ) {
                onClick?.invoke()
                true
            } else {
                false
            }
        }
}
