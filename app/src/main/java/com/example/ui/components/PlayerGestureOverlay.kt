package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextSecondary
import kotlin.math.abs
import kotlin.math.roundToInt

enum class DoubleTapZone {
    LEFT_REWIND,
    CENTER_TOGGLE,
    RIGHT_FORWARD
}

enum class GestureControlType {
    BRIGHTNESS,
    VOLUME
}

data class DoubleTapSeekFeedback(
    val zone: DoubleTapZone,
    val cumulativeSeconds: Int,
    val tapCount: Int = 1,
    val tapNormalizedX: Float = 0.5f,
    val tapNormalizedY: Float = 0.5f,
    val targetPositionLabel: String = "",
    val isPlayingAfterToggle: Boolean = true,
    val isLiveSyncPulse: Boolean = false,
    val triggerToken: Long = System.nanoTime()
)

data class GestureControlFeedback(
    val type: GestureControlType,
    val level: Float,
    val isDragging: Boolean = false,
    val triggerToken: Long = System.nanoTime()
) {
    val percentage: Int
        get() = (level.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
}

object PlayerGestureHelper {
    const val DEFAULT_SEEK_STEP_SECONDS = 10
    const val DEFAULT_SEEK_STEP_MS = 10_000L

    /**
     * Resolves which horizontal zone of the video player was double-tapped:
     * - Left 38% -> [DoubleTapZone.LEFT_REWIND] (-10s backward seek)
     * - Right 38% -> [DoubleTapZone.RIGHT_FORWARD] (+10s forward seek)
     * - Center 24% -> [DoubleTapZone.CENTER_TOGGLE] (Play/Pause or Live sync)
     */
    fun resolveDoubleTapZone(tapX: Float, containerWidth: Float): DoubleTapZone {
        if (containerWidth <= 0f) return DoubleTapZone.CENTER_TOGGLE
        val fraction = (tapX / containerWidth).coerceIn(0f, 1f)
        return when {
            fraction < 0.38f -> DoubleTapZone.LEFT_REWIND
            fraction > 0.62f -> DoubleTapZone.RIGHT_FORWARD
            else -> DoubleTapZone.CENTER_TOGGLE
        }
    }

    /**
     * Resolves which vertical swipe control is triggered based on the horizontal start coordinate:
     * - Left half (< 50% width) -> [GestureControlType.BRIGHTNESS]
     * - Right half (>= 50% width) -> [GestureControlType.VOLUME]
     */
    fun resolveVerticalGestureType(startX: Float, containerWidth: Float): GestureControlType {
        if (containerWidth <= 0f) return GestureControlType.VOLUME
        return if (startX < containerWidth * 0.5f) {
            GestureControlType.BRIGHTNESS
        } else {
            GestureControlType.VOLUME
        }
    }

    /**
     * Computes the updated [0f..1f] brightness or volume level from a vertical drag delta in pixels.
     * Swiping UP (negative `verticalDragDeltaPx`) increases the level; swiping DOWN decreases it.
     */
    fun computeUpdatedGestureLevel(
        currentLevel: Float,
        verticalDragDeltaPx: Float,
        containerHeightPx: Float
    ): Float {
        val effectiveSweepHeight = (containerHeightPx * 0.65f).coerceAtLeast(180f)
        val deltaFraction = -verticalDragDeltaPx / effectiveSweepHeight
        return (currentLevel + deltaFraction).coerceIn(0f, 1f)
    }

    /**
     * Computes cumulative seek seconds when the user performs consecutive double-taps / multi-taps
     * in the same seek zone (e.g. -10s -> -20s -> -30s or +10s -> +20s -> +30s).
     */
    fun computeCumulativeSeekSeconds(
        existingZone: DoubleTapZone?,
        existingSeconds: Int,
        newZone: DoubleTapZone,
        stepSeconds: Int = DEFAULT_SEEK_STEP_SECONDS
    ): Int {
        val signedStep = when (newZone) {
            DoubleTapZone.LEFT_REWIND -> -abs(stepSeconds)
            DoubleTapZone.RIGHT_FORWARD -> abs(stepSeconds)
            DoubleTapZone.CENTER_TOGGLE -> 0
        }
        if (signedStep == 0) return 0
        return if (existingZone == newZone && existingSeconds != 0) {
            existingSeconds + signedStep
        } else {
            signedStep
        }
    }

    fun readInitialScreenBrightness(activity: Activity?, context: Context): Float {
        val windowBrightness = activity?.window?.attributes?.screenBrightness ?: -1f
        if (windowBrightness in 0.0f..1.0f) {
            return windowBrightness.coerceIn(0.05f, 1.0f)
        }
        return try {
            val sys = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                170
            )
            (sys / 255f).coerceIn(0.05f, 1.0f)
        } catch (_: Exception) {
            0.68f
        }
    }

    fun applyWindowBrightness(activity: Activity?, brightnessLevel: Float) {
        val clamped = brightnessLevel.coerceIn(0.02f, 1.0f)
        activity?.window?.let { win ->
            val attrs = win.attributes
            attrs.screenBrightness = clamped
            win.attributes = attrs
        }
    }

    fun readInitialAudioVolume(context: Context, fallbackVolume: Float = 1.0f): Float {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return fallbackVolume
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
            (cur.toFloat() / max.toFloat()).coerceIn(0f, 1f)
        } catch (_: Exception) {
            fallbackVolume.coerceIn(0f, 1f)
        }
    }

    fun applyDeviceMusicVolume(context: Context, volumeLevel: Float) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = (volumeLevel.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        } catch (_: Exception) {
            // Ignore security or fixed-volume device restrictions; ExoPlayer volume still updates
        }
    }
}

/**
 * Full-screen gesture detector layer for the video player:
 * - Single tap: toggles player controls (or chains +10s/-10s if multi-tap seek overlay is currently active on that side)
 * - Double tap left (0..38% width): seeks 10s backward
 * - Double tap right (62..100% width): seeks 10s forward
 * - Double tap center (38..62% width): toggles Play/Pause (or syncs Live edge on Live TV)
 * - Vertical drag on left half: adjusts Screen Brightness (0% - 100%)
 * - Vertical drag on right half: adjusts Audio Volume (0% - 100%)
 */
@Composable
fun PlayerGestureTouchSurface(
    isLiveBroadcast: Boolean,
    activeSeekFeedback: DoubleTapSeekFeedback?,
    onSingleTapToggleControls: () -> Unit,
    onDoubleTapSeek: (zone: DoubleTapZone, normalizedX: Float, normalizedY: Float) -> Unit,
    onVerticalGestureStart: (GestureControlType) -> Unit,
    onVerticalGestureDelta: (GestureControlType, Float, Float) -> Unit,
    onVerticalGestureEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("player_gesture_touch_surface")
            .pointerInput(isLiveBroadcast, activeSeekFeedback) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val zone = PlayerGestureHelper.resolveDoubleTapZone(offset.x, width)
                        onDoubleTapSeek(
                            zone,
                            (offset.x / width).coerceIn(0f, 1f),
                            (offset.y / height).coerceIn(0f, 1f)
                        )
                    },
                    onTap = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val tappedZone = PlayerGestureHelper.resolveDoubleTapZone(offset.x, width)
                        val canChainSeekTap = !isLiveBroadcast &&
                                activeSeekFeedback != null &&
                                activeSeekFeedback.zone == tappedZone &&
                                (tappedZone == DoubleTapZone.LEFT_REWIND || tappedZone == DoubleTapZone.RIGHT_FORWARD)

                        if (canChainSeekTap) {
                            onDoubleTapSeek(
                                tappedZone,
                                (offset.x / width).coerceIn(0f, 1f),
                                (offset.y / height).coerceIn(0f, 1f)
                            )
                        } else {
                            onSingleTapToggleControls()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var activeGestureType: GestureControlType? = null
                var ignoreCurrentDrag = false

                detectVerticalDragGestures(
                    onDragStart = { startOffset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        // Avoid top status bar swipe-down and bottom seekbar scrub zone
                        val inSafeVerticalRegion = startOffset.y in (height * 0.08f)..(height * 0.85f)
                        val inSafeHorizontalRegion = startOffset.x in (width * 0.03f)..(width * 0.97f)
                        if (inSafeVerticalRegion && inSafeHorizontalRegion) {
                            ignoreCurrentDrag = false
                            val gestureType = PlayerGestureHelper.resolveVerticalGestureType(startOffset.x, width)
                            activeGestureType = gestureType
                            onVerticalGestureStart(gestureType)
                        } else {
                            ignoreCurrentDrag = true
                            activeGestureType = null
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        val gestureType = activeGestureType
                        if (!ignoreCurrentDrag && gestureType != null) {
                            change.consume()
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            onVerticalGestureDelta(gestureType, dragAmount, height)
                        }
                    },
                    onDragEnd = {
                        if (!ignoreCurrentDrag && activeGestureType != null) {
                            onVerticalGestureEnd()
                        }
                        activeGestureType = null
                        ignoreCurrentDrag = false
                    },
                    onDragCancel = {
                        if (!ignoreCurrentDrag && activeGestureType != null) {
                            onVerticalGestureEnd()
                        }
                        activeGestureType = null
                        ignoreCurrentDrag = false
                    }
                )
            }
    )
}

/**
 * Animated Cinema Double-Tap Seek Overlay (10s Rewind on Left / 10s Fast-Forward on Right / Center Play-Pause Pulse).
 */
@Composable
fun DoubleTapSeekOverlay(
    feedback: DoubleTapSeekFeedback?,
    modifier: Modifier = Modifier
) {
    val rippleProgress = remember { Animatable(0f) }
    LaunchedEffect(feedback?.triggerToken) {
        if (feedback != null) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
    }

    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
        exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.95f, animationSpec = tween(220)),
        modifier = modifier.fillMaxSize()
    ) {
        val current = feedback ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("double_tap_seek_overlay")
        ) {
            when (current.zone) {
                DoubleTapZone.LEFT_REWIND, DoubleTapZone.RIGHT_FORWARD -> {
                    val isRewind = current.zone == DoubleTapZone.LEFT_REWIND
                    val accentColor = if (isRewind) NeliGenreCyan else NeliMagenta

                    // Curved Side Arc + Expanding Touch Ripple Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.42f)
                            .align(if (isRewind) Alignment.CenterStart else Alignment.CenterEnd)
                    ) {
                        val w = size.width
                        val h = size.height
                        val arcPath = Path().apply {
                            if (isRewind) {
                                moveTo(0f, 0f)
                                lineTo(w * 0.62f, 0f)
                                quadraticTo(w * 1.15f, h * 0.5f, w * 0.62f, h)
                                lineTo(0f, h)
                                close()
                            } else {
                                moveTo(w, 0f)
                                lineTo(w * 0.38f, 0f)
                                quadraticTo(-w * 0.15f, h * 0.5f, w * 0.38f, h)
                                lineTo(w, h)
                                close()
                            }
                        }

                        drawPath(
                            path = arcPath,
                            brush = Brush.horizontalGradient(
                                colors = if (isRewind) {
                                    listOf(
                                        Color(0xAA14052B),
                                        accentColor.copy(alpha = 0.28f),
                                        Color.Transparent
                                    )
                                } else {
                                    listOf(
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.28f),
                                        Color(0xAA14052B)
                                    )
                                }
                            )
                        )

                        val localTapX = if (isRewind) {
                            (current.tapNormalizedX / 0.42f).coerceIn(0.15f, 0.85f) * w
                        } else {
                            ((current.tapNormalizedX - 0.58f) / 0.42f).coerceIn(0.15f, 0.85f) * w
                        }
                        val localTapY = current.tapNormalizedY.coerceIn(0.15f, 0.85f) * h
                        val maxRadius = maxOf(w, h) * 0.72f
                        val currentRadius = 28f + (maxRadius * rippleProgress.value)
                        val rippleAlpha = (1f - rippleProgress.value).coerceIn(0f, 1f) * 0.42f

                        drawCircle(
                            color = accentColor.copy(alpha = rippleAlpha),
                            radius = currentRadius,
                            center = Offset(localTapX, localTapY)
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = rippleAlpha * 0.7f),
                            radius = currentRadius,
                            center = Offset(localTapX, localTapY),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Seek Badge Content inside the Left or Right Zone
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.38f)
                            .align(if (isRewind) Alignment.CenterStart else Alignment.CenterEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color(0xD9120426))
                                .border(1.5.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                                .padding(horizontal = 22.dp, vertical = 16.dp)
                                .testTag(
                                    if (isRewind) "double_tap_rewind_badge" else "double_tap_forward_badge"
                                )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isRewind) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.65f + 0.35f * rippleProgress.value),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(accentColor.copy(alpha = 0.22f))
                                        .border(1.dp, accentColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRewind) Icons.Default.Replay10 else Icons.Default.Forward10,
                                        contentDescription = if (isRewind) "Rewind 10 seconds" else "Forward 10 seconds",
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                                if (!isRewind) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.65f + 0.35f * rippleProgress.value),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            val signedLabel = if (current.cumulativeSeconds > 0) {
                                "+${current.cumulativeSeconds} seconds"
                            } else {
                                "${current.cumulativeSeconds} seconds"
                            }

                            Text(
                                text = signedLabel,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("double_tap_seek_amount_text")
                            )

                            if (current.targetPositionLabel.isNotBlank()) {
                                Text(
                                    text = if (isRewind) {
                                        "« ${current.targetPositionLabel}"
                                    } else {
                                        "${current.targetPositionLabel} »"
                                    },
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                DoubleTapZone.CENTER_TOGGLE -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xDD120426))
                            .border(1.5.dp, NeliMagenta.copy(alpha = 0.85f), RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .testTag("double_tap_center_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(NeliMagenta),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        current.isLiveSyncPulse -> Icons.Default.Sensors
                                        current.isPlayingAfterToggle -> Icons.Default.PlayArrow
                                        else -> Icons.Default.Pause
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = when {
                                    current.isLiveSyncPulse -> "LIVE • Real-Time Edge"
                                    current.isPlayingAfterToggle -> "Playing"
                                    else -> "Paused"
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Professional Mobile Brightness & Volume Gesture HUD Overlay:
 * - Top-Center Glassmorphic Pill with icon, label, smooth gradient bar, and percentage readout.
 * - Side Vertical Level Meter on the opposite side of the thumb so the user's hand never blocks the gauge.
 */
@Composable
fun BrightnessVolumeGestureOverlay(
    feedback: GestureControlFeedback?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = feedback != null,
        enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.94f, animationSpec = tween(150)),
        exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)),
        modifier = modifier.fillMaxSize()
    ) {
        val current = feedback ?: return@AnimatedVisibility
        val isBrightness = current.type == GestureControlType.BRIGHTNESS
        val accentColor = if (isBrightness) NeliGenreCyan else NeliMagenta
        val animatedLevel by animateFloatAsState(
            targetValue = current.level.coerceIn(0f, 1f),
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "gestureLevelAnimation"
        )
        val pct = current.percentage
        val icon = resolveGestureIcon(current.type, pct)
        val titleLabel = if (isBrightness) "Brightness" else "Volume"
        val valueText = if (!isBrightness && pct == 0) "Muted" else "$pct%"

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("brightness_volume_gesture_hud")
        ) {
            // 1. Top-Center Cinema HUD Pill
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 26.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color(0xEB120426))
                    .border(1.5.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp)
                    .testTag("gesture_hud_top_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = titleLabel,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.width(150.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = titleLabel,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = valueText,
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.testTag("gesture_hud_percentage_text")
                        )
                    }

                    // Smooth Gradient Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x44FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedLevel)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            accentColor.copy(alpha = 0.8f),
                                            accentColor
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // 2. Vertical Side Capsule Meter (placed on the opposite side of the active thumb)
            Column(
                modifier = Modifier
                    .align(if (isBrightness) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xDD120426))
                    .border(1.dp, accentColor.copy(alpha = 0.75f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 10.dp, vertical = 14.dp)
                    .testTag("gesture_vertical_meter"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )

                Canvas(
                    modifier = Modifier
                        .width(10.dp)
                        .height(124.dp)
                ) {
                    val trackWidth = size.width
                    val trackHeight = size.height
                    val corner = CornerRadius(trackWidth / 2f, trackWidth / 2f)

                    // Background track
                    drawRoundRect(
                        color = Color(0x3DFFFFFF),
                        topLeft = Offset.Zero,
                        size = Size(trackWidth, trackHeight),
                        cornerRadius = corner
                    )

                    // Active fill from bottom to top
                    val fillHeight = (trackHeight * animatedLevel).coerceIn(0f, trackHeight)
                    if (fillHeight > 0f) {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor, accentColor.copy(alpha = 0.65f)),
                                startY = trackHeight - fillHeight,
                                endY = trackHeight
                            ),
                            topLeft = Offset(0f, trackHeight - fillHeight),
                            size = Size(trackWidth, fillHeight),
                            cornerRadius = corner
                        )
                    }
                }

                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * Interactive quick-access gesture status & controls strip displayed inside the player overlay
 * when controls are visible so users can see Brightness & Volume percentages or tap to adjust/seek.
 */
@Composable
fun PlayerGestureQuickBar(
    brightnessLevel: Float,
    volumeLevel: Float,
    isLiveBroadcast: Boolean,
    onStepBrightness: (Float) -> Unit,
    onStepVolume: (Float) -> Unit,
    onQuickSeekRelative: (DoubleTapZone) -> Unit,
    modifier: Modifier = Modifier
) {
    val brightnessPct = (brightnessLevel.coerceIn(0f, 1f) * 100f).roundToInt()
    val volumePct = (volumeLevel.coerceIn(0f, 1f) * 100f).roundToInt()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xAA14052B))
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("player_gesture_quick_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Brightness Quick Cycle / Indicator
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val next = if (brightnessLevel >= 0.95f) 0.35f else (brightnessLevel + 0.2f).coerceAtMost(1.0f)
                    onStepBrightness(next)
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("quick_brightness_control"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = resolveGestureIcon(GestureControlType.BRIGHTNESS, brightnessPct),
                contentDescription = "Brightness $brightnessPct%",
                tint = NeliGenreCyan,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "$brightnessPct%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isLiveBroadcast) {
            Text(
                text = "•",
                color = NeliTextSecondary,
                fontSize = 11.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Double-Tap ±10s",
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x662B1055))
                        .clickable { onQuickSeekRelative(DoubleTapZone.LEFT_REWIND) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("quick_double_tap_rewind_chip")
                ) {
                    Text(
                        text = "-10s",
                        color = NeliGenreCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x662B1055))
                        .clickable { onQuickSeekRelative(DoubleTapZone.RIGHT_FORWARD) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("quick_double_tap_forward_chip")
                ) {
                    Text(
                        text = "+10s",
                        color = NeliMagenta,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        } else {
            Text(
                text = "• Swipe Left/Right: Brightness & Volume",
                color = NeliTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "•",
            color = NeliTextSecondary,
            fontSize = 11.sp
        )

        // Volume Quick Cycle / Indicator
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    val next = if (volumeLevel >= 0.95f) 0.25f else (volumeLevel + 0.25f).coerceAtMost(1.0f)
                    onStepVolume(next)
                }
                .padding(horizontal = 6.dp, vertical = 3.dp)
                .testTag("quick_volume_control"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = resolveGestureIcon(GestureControlType.VOLUME, volumePct),
                contentDescription = "Volume $volumePct%",
                tint = NeliMagenta,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (volumePct == 0) "Muted" else "$volumePct%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun resolveGestureIcon(type: GestureControlType, percentage: Int): ImageVector {
    return when (type) {
        GestureControlType.BRIGHTNESS -> when {
            percentage < 34 -> Icons.Default.BrightnessLow
            percentage < 70 -> Icons.Default.BrightnessMedium
            else -> Icons.Default.BrightnessHigh
        }
        GestureControlType.VOLUME -> when {
            percentage <= 0 -> Icons.AutoMirrored.Filled.VolumeOff
            percentage < 30 -> Icons.AutoMirrored.Filled.VolumeMute
            percentage < 70 -> Icons.AutoMirrored.Filled.VolumeDown
            else -> Icons.AutoMirrored.Filled.VolumeUp
        }
    }
}
