package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeliThemeManager
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ultra-smooth, zero-stutter live liquid water-wave background for NeliTV.
 *
 * Key smoothness guarantees:
 * - Pre-allocated [Path] objects reused across frames via `reset()` (zero per-frame GC churn).
 * - Strictly integer phase multipliers (`1x`, `2x`) over `0..2π` so `RepeatMode.Restart` loops
 *   with mathematical C∞ continuity (zero visible snap or jump at loop boundaries).
 * - Deep velvet-cinema base with soft translucent ocean swells and subtle luminous crests that
 *   enhance readability behind cards and text in both Dark and Light themes.
 */
@Composable
fun NeliLiveWaterWaveBackground(
    modifier: Modifier = Modifier
) {
    val isLight = NeliThemeManager.isLightMode
    val infiniteTransition = rememberInfiniteTransition(label = "neli_live_water_waves")

    val twoPi = (2.0 * PI).toFloat()

    val slowTidePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "slow_tide_phase"
    )

    val gentleRipplePhase by infiniteTransition.animateFloat(
        initialValue = twoPi,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gentle_ripple_phase"
    )

    val auroraDriftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = twoPi,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora_drift_phase"
    )

    // Pre-allocate Path instances once to avoid per-frame allocations at 60/120fps
    val fillPath = remember { Path() }
    val crestPath = remember { Path() }

    val baseBrush = remember(isLight) {
        if (isLight) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFF8FA),
                    Color(0xFFFCEFF5),
                    Color(0xFFF3F6FF)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF050816),
                    Color(0xFF090E24),
                    Color(0xFF100B26),
                    Color(0xFF050712)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseBrush)
            .testTag("neli_live_water_wave_background")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            // 1. Soft drifting ambient glow pools (exact 1x integer harmonics -> seamless loop)
            val orb1Center = Offset(
                x = w * (0.24f + 0.14f * cos(auroraDriftPhase)),
                y = h * (0.18f + 0.08f * sin(auroraDriftPhase))
            )
            val orb2Center = Offset(
                x = w * (0.76f + 0.14f * sin(auroraDriftPhase)),
                y = h * (0.58f + 0.10f * cos(auroraDriftPhase))
            )
            val orb3Center = Offset(
                x = w * (0.48f - 0.16f * cos(auroraDriftPhase)),
                y = h * (0.86f + 0.06f * sin(auroraDriftPhase))
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isLight) {
                        listOf(Color(0x1EFF2E7E), Color(0x0AFF70A6), Color.Transparent)
                    } else {
                        listOf(Color(0x30FF2E7E), Color(0x147C3AED), Color.Transparent)
                    },
                    center = orb1Center,
                    radius = w * 0.78f
                ),
                radius = w * 0.78f,
                center = orb1Center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isLight) {
                        listOf(Color(0x2200B4D8), Color(0x0A70C4FF), Color.Transparent)
                    } else {
                        listOf(Color(0x2800E5FF), Color(0x120055FF), Color.Transparent)
                    },
                    center = orb2Center,
                    radius = w * 0.82f
                ),
                radius = w * 0.82f,
                center = orb2Center
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (isLight) {
                        listOf(Color(0x18FF4DA6), Color.Transparent)
                    } else {
                        listOf(Color(0x229333EA), Color.Transparent)
                    },
                    center = orb3Center,
                    radius = w * 0.70f
                ),
                radius = w * 0.70f,
                center = orb3Center
            )

            // 2. Smooth layered ocean-silk ribbons (integer phase multipliers = zero stutter)
            fun drawSmoothWaveRibbon(
                baseYFraction: Float,
                amplitudeDp: Float,
                spatialCycles: Float,
                phaseA: Float,
                phaseB: Float,
                fillColors: List<Color>,
                crestColor: Color,
                crestAlpha: Float,
                crestWidthDp: Float
            ) {
                val ampPx = amplitudeDp.dp.toPx()
                val baseY = h * baseYFraction
                val segments = 36
                val step = w / segments

                fillPath.reset()
                crestPath.reset()

                fillPath.moveTo(0f, h)

                for (i in 0..segments) {
                    val x = if (i == segments) w else i * step
                    val t = x / w
                    val angleA = (t * spatialCycles * twoPi) + phaseA
                    val angleB = (t * spatialCycles * 2f * twoPi) + phaseB
                    val y = baseY +
                        ampPx * sin(angleA) +
                        (ampPx * 0.32f) * cos(angleB)

                    fillPath.lineTo(x, y)
                    if (i == 0) {
                        crestPath.moveTo(x, y)
                    } else {
                        crestPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(w, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = fillColors,
                        startY = baseY - ampPx * 1.4f,
                        endY = (baseY + h * 0.38f).coerceAtMost(h)
                    )
                )

                drawPath(
                    path = crestPath,
                    color = crestColor.copy(alpha = crestAlpha),
                    style = Stroke(width = crestWidthDp.dp.toPx())
                )
            }

            if (isLight) {
                drawSmoothWaveRibbon(
                    baseYFraction = 0.34f,
                    amplitudeDp = 16f,
                    spatialCycles = 1.25f,
                    phaseA = slowTidePhase,
                    phaseB = gentleRipplePhase,
                    fillColors = listOf(Color(0x1470C4FF), Color(0x04FFFFFF), Color.Transparent),
                    crestColor = Color(0xFF38BDF8),
                    crestAlpha = 0.22f,
                    crestWidthDp = 1.1f
                )
                drawSmoothWaveRibbon(
                    baseYFraction = 0.60f,
                    amplitudeDp = 20f,
                    spatialCycles = 1.45f,
                    phaseA = gentleRipplePhase,
                    phaseB = slowTidePhase,
                    fillColors = listOf(Color(0x14FF2E7E), Color(0x06FF70A6), Color.Transparent),
                    crestColor = Color(0xFFFF2E7E),
                    crestAlpha = 0.18f,
                    crestWidthDp = 1.1f
                )
                drawSmoothWaveRibbon(
                    baseYFraction = 0.82f,
                    amplitudeDp = 18f,
                    spatialCycles = 1.15f,
                    phaseA = slowTidePhase + 1.57f,
                    phaseB = gentleRipplePhase + 1.57f,
                    fillColors = listOf(Color(0x1800B4D8), Color(0x0AFF4DA6)),
                    crestColor = Color(0xFF00A8E8),
                    crestAlpha = 0.20f,
                    crestWidthDp = 1.0f
                )
            } else {
                // Deep indigo-violet ocean swell
                drawSmoothWaveRibbon(
                    baseYFraction = 0.30f,
                    amplitudeDp = 18f,
                    spatialCycles = 1.2f,
                    phaseA = slowTidePhase,
                    phaseB = gentleRipplePhase,
                    fillColors = listOf(Color(0x207C3AED), Color(0x0B0F1733), Color.Transparent),
                    crestColor = Color(0xFFA855F7),
                    crestAlpha = 0.22f,
                    crestWidthDp = 1.1f
                )
                // Soft cyan current
                drawSmoothWaveRibbon(
                    baseYFraction = 0.54f,
                    amplitudeDp = 22f,
                    spatialCycles = 1.4f,
                    phaseA = gentleRipplePhase,
                    phaseB = slowTidePhase,
                    fillColors = listOf(Color(0x1E00E5FF), Color(0x0D0055FF), Color.Transparent),
                    crestColor = Color(0xFF00E5FF),
                    crestAlpha = 0.24f,
                    crestWidthDp = 1.2f
                )
                // Signature NeliTV magenta silk wave near lower third
                drawSmoothWaveRibbon(
                    baseYFraction = 0.74f,
                    amplitudeDp = 20f,
                    spatialCycles = 1.3f,
                    phaseA = slowTidePhase + 2.09f,
                    phaseB = gentleRipplePhase + 1.05f,
                    fillColors = listOf(Color(0x22FF2E7E), Color(0x127C3AED), Color(0x06050712)),
                    crestColor = Color(0xFFFF2E7E),
                    crestAlpha = 0.26f,
                    crestWidthDp = 1.2f
                )
            }
        }
    }
}
