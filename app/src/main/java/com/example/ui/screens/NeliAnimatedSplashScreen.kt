package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * NeliTv 6-Step Water-Filling Neon Splash Screen:
 *
 * Implements the exact sequence illustrated in NeliTv_Neon_Splash_Storyboard:
 * 1. Initial State: Empty squircle with radiant glowing neon gradient border.
 * 2. Water Starts Filling: Glowing blue/cyan liquid with ripples & rising bubbles (~20%).
 * 3. Half Filled: Dynamic undulating liquid wave rising to ~50% with bubbles.
 * 4. Almost Full: Liquid rising to ~85% transitioning from cyan to vibrant magenta, splashing against top rim.
 * 5. Full & Bright: Squircle fully filled with liquid, revealing illuminated 3D glossy N logo.
 * 6. Final Splash: Bursting liquid droplet splashes radiate outward, unveiling "NeliTv",
 *    "Free Entertainment For Everyone" and glowing multi-dot loading indicators.
 */
@Composable
fun NeliAnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    // 0f (empty) to 1f (full) liquid fill progression
    val waterFillProgress = remember { Animatable(0f) }
    // Scale and fade-in of the squircle emblem
    val emblemScale = remember { Animatable(0.85f) }
    val emblemAlpha = remember { Animatable(0f) }
    // Glow pulsation
    val glowPulse = remember { Animatable(0.4f) }
    // Burst splash effect on final step
    val splashBurstProgress = remember { Animatable(0f) }
    // Brand text & loading fade-in
    val textAlpha = remember { Animatable(0f) }
    val loadingDotsAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_wave_cycle")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val dotsCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_cycle"
    )

    LaunchedEffect(Unit) {
        // Signature cinematic intro audio ("Ta-Dum!" impact and neon chime)
        launch {
            NeliIntroAudioSynthesizer.playIntroChime()
        }

        // Step 1: Initial State - Fade & scale in empty neon squircle
        launch {
            emblemAlpha.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
        }
        launch {
            emblemScale.animateTo(1f, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
        delay(250)

        // Steps 2, 3, 4, 5: Water starts filling, half filled, almost full, full & bright
        launch {
            waterFillProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
            )
        }
        launch {
            glowPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1800, easing = FastOutSlowInEasing)
            )
        }
        delay(1650)

        // Step 6: Final Splash - Outward liquid splash burst + Brand reveal
        launch {
            splashBurstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
            )
        }
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            loadingDotsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }

        // Keep displayed long enough to appreciate the complete storyboard then enter smoothly
        delay(1300)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060919))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Instant skip on tap
                onSplashFinished()
            }
            .testTag("animated_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // Authentic custom design wallpaper
        Image(
            painter = painterResource(id = R.drawable.neli_bg_wallpaper),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(emblemScale.value)
                .alpha(emblemAlpha.value)
        ) {
            // Main Storyboard Container: Neon Squircle + Water Fill + 3D N Logo + Splash Burst
            Box(
                modifier = Modifier
                    .size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Ambient Glow Aura
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(RoundedCornerShape(48.dp))
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF7A00),
                                    Color(0xFFFF007A),
                                    Color(0xFF8B00FF),
                                    Color(0xFF00E5FF),
                                    Color(0xFFFF7A00)
                                )
                            )
                        )
                        .blur(26.dp)
                        .alpha(0.45f * glowPulse.value)
                )

                // Step 6 Burst Splashes Canvas (radiating outward water droplets)
                val splashBurst = splashBurstProgress.value
                if (splashBurst > 0f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val baseRadius = 88.dp.toPx()

                        // Array of splash droplet particles
                        val particles = listOf(
                            Triple(-0.85f, -0.6f, Color(0xFFFF2A75)),
                            Triple(-0.95f, -0.2f, Color(0xFFFF7A00)),
                            Triple(-0.75f, 0.4f, Color(0xFFFF5500)),
                            Triple(-0.6f, 0.85f, Color(0xFFFF0080)),
                            Triple(0.85f, -0.65f, Color(0xFF00E5FF)),
                            Triple(0.95f, -0.15f, Color(0xFF0099FF)),
                            Triple(0.75f, 0.45f, Color(0xFF00C8FF)),
                            Triple(0.6f, 0.85f, Color(0xFF2979FF)),
                            Triple(0f, -0.95f, Color(0xFFFF4081)),
                            Triple(0f, 0.95f, Color(0xFF00E5FF))
                        )

                        particles.forEach { (dirX, dirY, particleColor) ->
                            val dist = baseRadius + (dirX * dirX + dirY * dirY) * 28.dp.toPx() * splashBurst
                            val px = cx + dirX * dist
                            val py = cy + dirY * dist
                            val pRadius = (6.dp.toPx() * (1f - splashBurst * 0.4f)).coerceAtLeast(2.dp.toPx())

                            drawCircle(
                                color = particleColor.copy(alpha = (1f - splashBurst * 0.2f).coerceIn(0f, 1f)),
                                radius = pRadius,
                                center = Offset(px, py)
                            )
                        }
                    }
                }

                // Core 180dp Squircle Container
                Box(
                    modifier = Modifier
                        .size(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Canvas 1: Internal Dynamic Water Wave Fill
                    val currentFill = waterFillProgress.value
                    Canvas(
                        modifier = Modifier
                            .size(172.dp)
                            .clip(RoundedCornerShape(44.dp))
                    ) {
                        val w = size.width
                        val h = size.height
                        val squirclePath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                                    cornerRadius = CornerRadius(44.dp.toPx(), 44.dp.toPx())
                                )
                            )
                        }

                        clipPath(squirclePath) {
                            // Deep background of squircle interior
                            drawRect(
                                color = Color(0xFF0A0F26),
                                size = Size(w, h)
                            )

                            if (currentFill > 0.01f) {
                                val waterLevelY = h * (1f - currentFill)
                                val waveAmplitude = (10.dp.toPx() * (1f - currentFill * 0.65f)).coerceAtLeast(3.dp.toPx())

                                // Build dynamic wavy surface path
                                val waterPath = Path().apply {
                                    moveTo(0f, h)
                                    lineTo(0f, waterLevelY)

                                    val waveSteps = 24
                                    for (i in 0..waveSteps) {
                                        val x = w * (i.toFloat() / waveSteps)
                                        val waveSin = sin((x / w) * 2f * PI.toFloat() + wavePhase)
                                        val y = waterLevelY + waveSin * waveAmplitude
                                        lineTo(x, y)
                                    }

                                    lineTo(w, h)
                                    close()
                                }

                                // Glowing Water Gradient: Electric Cyan at bottom transitioning to Magenta/Pink
                                val waterBrush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFF007F).copy(alpha = (0.75f * currentFill).coerceIn(0f, 1f)),
                                        Color(0xFF8B00FF).copy(alpha = 0.85f),
                                        Color(0xFF0077FF),
                                        Color(0xFF00E5FF)
                                    ),
                                    startY = waterLevelY,
                                    endY = h
                                )

                                drawPath(path = waterPath, brush = waterBrush)

                                // Surface crest highlight line
                                val crestPath = Path().apply {
                                    moveTo(0f, waterLevelY)
                                    val waveSteps = 24
                                    for (i in 0..waveSteps) {
                                        val x = w * (i.toFloat() / waveSteps)
                                        val waveSin = sin((x / w) * 2f * PI.toFloat() + wavePhase)
                                        val y = waterLevelY + waveSin * waveAmplitude
                                        lineTo(x, y)
                                    }
                                }
                                drawPath(
                                    path = crestPath,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // Rising bubbles inside water
                                val bubbleTime = wavePhase * 18f
                                val bubbles = listOf(
                                    Offset(w * 0.25f, (h - (bubbleTime * 1.8f) % (h - waterLevelY)).coerceIn(waterLevelY, h)),
                                    Offset(w * 0.5f, (h - (bubbleTime * 2.3f + 40f) % (h - waterLevelY)).coerceIn(waterLevelY, h)),
                                    Offset(w * 0.72f, (h - (bubbleTime * 1.5f + 80f) % (h - waterLevelY)).coerceIn(waterLevelY, h)),
                                    Offset(w * 0.38f, (h - (bubbleTime * 2.7f + 120f) % (h - waterLevelY)).coerceIn(waterLevelY, h))
                                )
                                bubbles.forEach { bPos ->
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.6f),
                                        radius = 3.5.dp.toPx(),
                                        center = bPos
                                    )
                                }
                            }
                        }
                    }

                    // Layer 2: 3D Glossy N Logo
                    // While water is filling, the logo emerges gradually; when full, it shines brightly!
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .align(Alignment.Center)
                    ) {
                        // Subdued base silhouette
                        Image(
                            painter = painterResource(id = R.drawable.neli_logo),
                            contentDescription = "NeliTv Logo",
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.05f) }),
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(0.25f)
                        )

                        // Saturated full color 3D N revealed by water progression
                        if (currentFill > 0.05f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        val cutTopY = size.height * (1f - currentFill)
                                        clipRect(
                                            left = 0f,
                                            top = cutTopY,
                                            right = size.width,
                                            bottom = size.height
                                        ) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.neli_logo),
                                    contentDescription = "NeliTv Glossy N Logo",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Layer 3: Radiant Glowing Rainbow Neon Squircle Frame
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val strokeW = 4.5.dp.toPx()
                        val halfStroke = strokeW / 2f
                        val frameRect = androidx.compose.ui.geometry.Rect(
                            halfStroke,
                            halfStroke,
                            size.width - halfStroke,
                            size.height - halfStroke
                        )
                        val squircleBorder = Path().apply {
                            addRoundRect(RoundRect(frameRect, CornerRadius(46.dp.toPx(), 46.dp.toPx())))
                        }

                        val rainbowBrush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF9500), // Bright Orange/Gold
                                Color(0xFFFF007A), // Hot Magenta
                                Color(0xFF9D00FF), // Neon Purple
                                Color(0xFF00E5FF), // Electric Cyan
                                Color(0xFFFF9500)
                            )
                        )

                        drawPath(
                            path = squircleBorder,
                            brush = rainbowBrush,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Step 6 Brand Typography: "NeliTv" + "Free Entertainment For Everyone"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(textAlpha.value)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Neli",
                        color = Color.White,
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Tv",
                        color = Color(0xFFFF2A85),
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Free Entertainment For Everyone",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Step 6 Bottom: Pulsing Multi-Dot Loading Indicator + "Loading..."
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.alpha(loadingDotsAlpha.value)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeIndex = (dotsCycle.toInt()) % 8
                    for (i in 0 until 8) {
                        val isHighlighted = i == activeIndex
                        val dotColor = when {
                            i < 4 -> Color(0xFFFF2A85) // Magenta dots
                            else -> Color(0xFF00E5FF)  // Cyan dots
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isHighlighted) 7.5.dp else 5.5.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isHighlighted) dotColor else dotColor.copy(alpha = 0.35f)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Loading...",
                    color = Color(0xFF8E9BAE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
