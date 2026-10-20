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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

@Immutable
data class NeliTvConfiguration(
    val isTelevision: Boolean = false,
    val isHardwareTv: Boolean = false,
    val uiScaleMultiplier: Float = 1.0f,
    val buttonScaleMultiplier: Float = 1.18f,
    val buttonMinHeight: Dp = 48.dp,
    val cardPaddingMultiplier: Float = 1.0f,
    val defaultGrowScale: Float = 1.08f,
    val safeOverscanPadding: Dp = 0.dp
)

val LocalTvConfiguration = compositionLocalOf { NeliTvConfiguration() }

object NeliTvDetector {
    /**
     * Accurately detects whether the running environment is hardware Android Smart TV / TV Box / Leanback / Fire TV.
     * Strictly verifies android.hardware.type.television and related TV system features.
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
        if (pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.software.leanback") ||
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

    /**
     * Resolves the complete TV layout configuration for UI scaling and remote ergonomics.
     */
    fun resolveConfiguration(context: Context): NeliTvConfiguration {
        val isHardware = isHardwareTelevision(context)
        val isTv = isTelevision(context)
        return NeliTvConfiguration(
            isTelevision = isTv,
            isHardwareTv = isHardware,
            uiScaleMultiplier = if (isTv) 1.20f else 1.0f,
            buttonScaleMultiplier = if (isTv) 1.25f else 1.0f,
            buttonMinHeight = if (isTv) 56.dp else 48.dp,
            cardPaddingMultiplier = if (isTv) 1.25f else 1.0f,
            defaultGrowScale = if (isTv) 1.10f else 1.06f,
            safeOverscanPadding = if (isTv) 20.dp else 0.dp
        )
    }
}

/**
 * Animated Gemini Halo Gradient palette for Smart TV Remote focus boundaries:
 * Matches 3D Neon Logo: Orange -> Magenta -> Purple -> Blue -> Cyan -> Orange wrap.
 */
val GeminiFocusGradientColors = listOf(
    Color(0xFFFF8500), // Neon Amber/Orange
    Color(0xFFFF007A), // Vivid Neon Magenta
    Color(0xFF8B00FF), // Electric Violet/Purple
    Color(0xFF0055FF), // Royal Blue
    Color(0xFF00E5FF), // Neon Cyan
    Color(0xFFFF8500)  // Wrap
)

/**
 * Universal growing effect modifier for Smart TV D-Pad focus & touch interaction:
 * 1. Scales up dynamically when focused (e.g., 1.08x) or pressed (1.04x).
 * 2. Elevated zIndex so the item hovers above neighboring cards/elements.
 * 3. Animated breathing Neon Halo boundary on focus.
 * 4. Triggers [onClick] on TV remote D-Pad Center/Enter key.
 */
@Composable
fun Modifier.growingInteractiveEffect(
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.08f,
    pressScale: Float = 1.04f,
    borderWidth: Dp = 2.dp,
    showHaloGlow: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onClick: (() -> Unit)? = null
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetScale = when {
        isFocused -> focusScale
        isPressed -> pressScale
        else -> 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "neli_growing_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "halo_transition")
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )

    val geminiBrush = remember(haloAlpha) {
        Brush.sweepGradient(
            colors = GeminiFocusGradientColors.map { it.copy(alpha = haloAlpha) }
        )
    }

    return this
        .zIndex(if (isFocused || isPressed) 15f else 1f)
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .onFocusChanged { state ->
            isFocused = state.isFocused || state.hasFocus
        }
        .focusable(interactionSource = interactionSource)
        .then(
            if (isFocused && showHaloGlow) {
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
            } else if (isPressed) {
                Modifier.border(
                    width = 1.5.dp,
                    color = Color(0xFFFF007A).copy(alpha = 0.8f),
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

/**
 * Convenient card grow effect with smooth 1.07x expansion & neon halo on focus/hover.
 */
@Composable
fun Modifier.tvCardGrow(
    shape: Shape = RoundedCornerShape(16.dp),
    focusScale: Float = 1.07f,
    onClick: (() -> Unit)? = null
): Modifier = growingInteractiveEffect(
    shape = shape,
    focusScale = focusScale,
    pressScale = 1.03f,
    borderWidth = 2.dp,
    showHaloGlow = true,
    onClick = onClick
)

/**
 * Convenient button grow effect with 1.09x expansion for remote-friendly TV buttons.
 */
@Composable
fun Modifier.tvButtonGrow(
    shape: Shape = RoundedCornerShape(12.dp),
    focusScale: Float = 1.09f,
    onClick: (() -> Unit)? = null
): Modifier = growingInteractiveEffect(
    shape = shape,
    focusScale = focusScale,
    pressScale = 1.04f,
    borderWidth = 2.dp,
    showHaloGlow = true,
    onClick = onClick
)

/**
 * Convenient tile/menu item grow effect with 1.05x expansion for settings & list items.
 */
@Composable
fun Modifier.tvTileGrow(
    shape: Shape = RoundedCornerShape(14.dp),
    focusScale: Float = 1.05f,
    onClick: (() -> Unit)? = null
): Modifier = growingInteractiveEffect(
    shape = shape,
    focusScale = focusScale,
    pressScale = 1.02f,
    borderWidth = 1.5.dp,
    showHaloGlow = true,
    onClick = onClick
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
): Modifier = growingInteractiveEffect(
    shape = shape,
    focusScale = focusScale,
    pressScale = 1.04f,
    borderWidth = borderWidth,
    showHaloGlow = true,
    onClick = onClick
)
