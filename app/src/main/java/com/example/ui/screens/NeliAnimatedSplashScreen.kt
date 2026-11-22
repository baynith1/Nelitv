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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.MediaContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

const val NELI_SPLASH_TOTAL_DURATION_MS = 10_000L

private data class SoaringPosterSpec(
    val laneFractionX: Float,
    val phaseOffset: Float,
    val speedMultiplier: Float,
    val widthDp: Float,
    val tiltZ: Float,
    val swayAmplitudeDp: Float,
    val depthAlpha: Float
)

/**
 * 10-Second Cinematic Splash Screen for NeliTv:
 * 1. Background displays real movie posters from the app (`MediaContentRepository.mediaCatalog`),
 *    simultaneously ORBITING ("zinazunguka") in a graceful 3D tilted cinema carousel ring AND
 *    SOARING UPWARD ("zinapaa") in layered parallax flight lanes with gentle 3D sway.
 * 2. Centerpiece performs the signature NeliTv Logo REFILL animation:
 *    - The glowing neon rainbow squircle border progressively traces around the frame (`PathMeasure`)
 *      with a luminous comet head.
 *    - The official NeliTv logo (`R.drawable.neli_logo`) starts as a ghostly silhouette and
 *      smoothly refills from bottom to top (0% -> 100%) with an animated liquid wave crest & glow.
 * 3. Runs for a full 10 seconds (`NELI_SPLASH_TOTAL_DURATION_MS`) with brand wordmark, tagline,
 *    smooth progress bar, and a subtle top-right Skip pill for users who wish to enter early.
 */
@Composable
fun NeliAnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val liveCatalog by MediaContentRepository.mediaCatalog.collectAsState()

    // Prewarm real app movie posters from cache immediately
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            try {
                MediaContentRepository.initializeAndPrewarmFromCache(context)
            } catch (_: Throwable) {
            }
        }
    }

    val splashPosters = remember(liveCatalog) {
        val realCatalogPosters = liveCatalog
            .filter { it.published && !it.isAdultContent }
            .mapNotNull { media ->
                val url = media.posterUrl.trim().ifBlank { media.backdropUrl.trim() }
                if (url.startsWith("http", ignoreCase = true)) url else null
            }
            .distinct()
            .take(18)

        val fallbackCinemaPosters = listOf(
            "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            "https://image.tmdb.org/t/p/w500/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg",
            "https://image.tmdb.org/t/p/w500/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg",
            "https://image.tmdb.org/t/p/w500/gKkl37BQuKTanygYQG1pyYgLVgf.jpg",
            "https://image.tmdb.org/t/p/w500/wWba3TaojhK7NdycRhoQpsG0FaH.jpg",
            "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            "https://image.tmdb.org/t/p/w500/z1p34vh7dEOnLDmyCrlUVLuoDzd.jpg",
            "https://image.tmdb.org/t/p/w500/qhb1qOilapbapxWQn9jtRCMwXJF.jpg",
            "https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg",
            "https://image.tmdb.org/t/p/w500/sh7Rg8Er3tFcN9BpKIPOMvALgZd.jpg",
            "https://image.tmdb.org/t/p/w500/d5NXSklXo0qyIYkgV94XAgMIckC.jpg",
            "https://image.tmdb.org/t/p/w500/rCzpDGLbOoPwLjy3OAm5NUPOTrC.jpg"
        )

        (realCatalogPosters + fallbackCinemaPosters).distinct().take(18)
    }

    val soaringSpecs = remember {
        listOf(
            SoaringPosterSpec(0.10f, 0.05f, 1.00f, 84f, -9f, 12f, 0.72f),
            SoaringPosterSpec(0.32f, 0.42f, 0.85f, 74f, 6f, 16f, 0.58f),
            SoaringPosterSpec(0.56f, 0.74f, 1.12f, 88f, -6f, 14f, 0.68f),
            SoaringPosterSpec(0.82f, 0.22f, 0.94f, 82f, 10f, 15f, 0.74f),
            SoaringPosterSpec(0.20f, 0.63f, 1.06f, 78f, 8f, 10f, 0.62f),
            SoaringPosterSpec(0.72f, 0.88f, 0.88f, 76f, -8f, 13f, 0.60f),
            SoaringPosterSpec(0.45f, 0.16f, 0.78f, 68f, 5f, 11f, 0.50f),
            SoaringPosterSpec(0.90f, 0.55f, 1.04f, 80f, -11f, 12f, 0.66f)
        )
    }

    // 10-second choreographed animatables
    val backdropAlpha = remember { Animatable(0f) }
    val logoContainerAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.84f) }
    val borderTraceProgress = remember { Animatable(0f) } // 0 -> 1 traces neon squircle border
    val logoRefillProgress = remember { Animatable(0f) }  // 0 -> 1 liquid refill from bottom to top
    val climaxBurst = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textSlideY = remember { Animatable(26f) }
    val overallProgress = remember { Animatable(0f) }
    val exitFadeAlpha = remember { Animatable(1f) }

    // Continuous smooth orbital + soaring + liquid wave motion for the entire 10 seconds
    val infiniteTransition = rememberInfiniteTransition(label = "splash_cinema_motion")

    // Posters orbiting around the center (full 360° revolution every 7.8s)
    val orbitAngleDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "poster_orbit_angle"
    )

    // Posters soaring continuously upward from bottom to top
    val soarCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "poster_soar_cycle"
    )

    // Liquid refill crest wave phase
    val liquidWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_wave_phase"
    )

    // Subtle shimmer & breathing aura after refill completes
    val auraPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    val dotsCycle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1050, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_cycle"
    )

    LaunchedEffect(Unit) {
        // Total 10-second linear master progress bar
        launch {
            overallProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = NELI_SPLASH_TOTAL_DURATION_MS.toInt(), easing = LinearEasing)
            )
        }

        // Act 1 (0.0s -> 1.4s): Orbiting & soaring movie posters fade in across the cinema canvas
        launch {
            backdropAlpha.animateTo(1f, animationSpec = tween(900, easing = FastOutSlowInEasing))
        }

        delay(800L)

        // Act 2 (0.8s -> 6.4s): Signature NeliTv Logo Container emerges + Border Trace + Liquid Refill (0% -> 100%)
        launch {
            logoContainerAlpha.animateTo(1f, animationSpec = tween(700, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1.0f, animationSpec = tween(1400, easing = FastOutSlowInEasing))
        }
        launch {
            borderTraceProgress.animateTo(1f, animationSpec = tween(3200, easing = FastOutSlowInEasing))
        }

        delay(450L)

        // Play studio cinematic intro chime as the refill builds momentum
        launch {
            NeliIntroAudioSynthesizer.playIntroChime()
        }

        // Smooth bottom-to-top liquid refill of the NeliTv logo (1.25s -> 6.25s = 5.0 seconds lush refill)
        logoRefillProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 5000, easing = FastOutSlowInEasing)
        )

        // Act 3 (6.25s -> 7.6s): 100% Refill Climax! Shockwave + neon bloom + brand wordmark reveal
        launch {
            logoScale.animateTo(1.06f, animationSpec = tween(260, easing = FastOutSlowInEasing))
            logoScale.animateTo(1.0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            climaxBurst.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            textSlideY.animateTo(0f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        }

        // Act 4 (6.25s -> 9.6s): Majestic hold while orbiting & soaring posters continue showcasing movies
        delay(3300L)

        // Smooth cinema fade-out right into the 10-second finish mark
        exitFadeAlpha.animateTo(0f, animationSpec = tween(450, easing = FastOutSlowInEasing))
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040612))
            .alpha(exitFadeAlpha.value)
            .testTag("animated_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1: Real Movie Posters SOARING UPWARD ("zinapaa") & ORBITING ("zinazunguka")
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha.value)
        ) {
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }

            // Sub-layer 1A: Floating Movie Posters soaring smoothly from bottom -> top with gentle sway
            soaringSpecs.forEachIndexed { idx, spec ->
                val posterUrl = splashPosters[idx % splashPosters.size]
                val progress = (soarCycle * spec.speedMultiplier + spec.phaseOffset) % 1f
                val cardHeightPx = with(density) { (spec.widthDp / 0.67f).dp.toPx() }
                val travelSpan = heightPx + cardHeightPx * 2.1f
                val yPx = (heightPx + cardHeightPx * 0.6f) - (progress * travelSpan)
                val swayPx = with(density) {
                    (sin((progress * 2.0 * PI + idx).toDouble()).toFloat() * spec.swayAmplitudeDp).dp.toPx()
                }
                val xPx = (widthPx * spec.laneFractionX) - with(density) { (spec.widthDp / 2f).dp.toPx() } + swayPx
                // Softly fade in at bottom and fade out at top edge
                val edgeFade = when {
                    progress < 0.12f -> progress / 0.12f
                    progress > 0.86f -> ((1f - progress) / 0.14f).coerceIn(0f, 1f)
                    else -> 1f
                }

                SplashFloatingPosterCard(
                    posterUrl = posterUrl,
                    widthDp = spec.widthDp,
                    accentMagenta = idx % 2 == 0,
                    modifier = Modifier.graphicsLayer {
                        translationX = xPx
                        translationY = yPx
                        rotationZ = spec.tiltZ + sin((progress * 2.0 * PI).toDouble()).toFloat() * 5f
                        rotationY = cos((progress * 2.0 * PI + idx).toDouble()).toFloat() * 14f
                        alpha = spec.depthAlpha * edgeFade
                        cameraDistance = 14f * density.density
                    }
                )
            }

            // Sub-layer 1B: 3D Tilted Orbital Ring of Real Movie Posters Revolving Around the Center ("zinazunguka")
            val orbitCount = 8
            val radiusX = widthPx * 0.43f
            val radiusY = heightPx * 0.25f
            val centerX = widthPx / 2f
            val centerY = heightPx * 0.46f

            // Compute orbital positions sorted back-to-front so nearer posters layer cleanly above farther ones
            val orbitItems = (0 until orbitCount).map { slot ->
                val angleRad = Math.toRadians((orbitAngleDeg + slot * (360f / orbitCount)).toDouble())
                val sinA = sin(angleRad).toFloat()
                val cosA = cos(angleRad).toFloat()
                // Depth factor in [0f..1f] where 1f is frontmost and 0f is furthest back
                val depth = (sinA + 1f) / 2f
                Triple(slot, angleRad, depth)
            }.sortedBy { it.third }

            orbitItems.forEach { (slot, angleRad, depth) ->
                val posterUrl = splashPosters[(slot + soaringSpecs.size) % splashPosters.size]
                val cardW = 86f
                val cardWPx = with(density) { cardW.dp.toPx() }
                val cardHPx = with(density) { (cardW / 0.67f).dp.toPx() }

                val cosA = cos(angleRad).toFloat()
                val sinA = sin(angleRad).toFloat()
                // Subtle diagonal tilt on the orbit + gentle vertical breathing float
                val posX = centerX + cosA * radiusX - (cardWPx / 2f)
                val posY = centerY + sinA * radiusY + (cosA * heightPx * 0.055f) - (cardHPx / 2f)
                val perspectiveScale = 0.66f + (depth * 0.44f)
                val perspectiveAlpha = 0.34f + (depth * 0.56f)

                SplashFloatingPosterCard(
                    posterUrl = posterUrl,
                    widthDp = cardW,
                    accentMagenta = slot % 2 == 1,
                    modifier = Modifier.graphicsLayer {
                        translationX = posX
                        translationY = posY
                        scaleX = perspectiveScale
                        scaleY = perspectiveScale
                        rotationY = -cosA * 28f
                        rotationZ = cosA * 10f
                        alpha = perspectiveAlpha
                        cameraDistance = 16f * density.density
                    }
                )
            }
        }

        // LAYER 2: Deep Cinema Vignette so the Refilling NeliTv Logo Pops Dramatically
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xD90A071E),
                            Color(0xBA050817),
                            Color(0xEA03050F)
                        )
                    )
                )
        )

        // Subtle Top-Right "Skip" pill (accessible after first second without accidental tap exits)
        if (overallProgress.value > 0.12f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 14.dp, end = 16.dp)
                    .clip(CircleShape)
                    .background(Color(0x66121730))
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
                    .clickable { onSplashFinished() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("splash_skip_button")
            ) {
                Text(
                    text = "Skip",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // LAYER 3: Centerpiece — Signature NeliTv Logo REFILL Animation + Wordmark
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            val fillPct = logoRefillProgress.value.coerceIn(0f, 1f)
            val tracePct = borderTraceProgress.value.coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .size(230.dp)
                    .scale(logoScale.value)
                    .alpha(logoContainerAlpha.value),
                contentAlignment = Alignment.Center
            ) {
                // Radiant Ambient Neon Aura that intensifies as the logo refills to 100%
                Box(
                    modifier = Modifier
                        .size(182.dp)
                        .scale(if (fillPct >= 0.99f) auraPulse else 0.88f + 0.12f * fillPct)
                        .clip(RoundedCornerShape(46.dp))
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color(0xFFFF9500),
                                    Color(0xFFFF007A),
                                    Color(0xFF9D00FF),
                                    Color(0xFF00E5FF),
                                    Color(0xFFFF9500)
                                )
                            )
                        )
                        .blur(30.dp)
                        .alpha(0.18f + 0.48f * fillPct)
                )

                // Expanding Neon Shockwave Ring at 100% refill completion
                val burst = climaxBurst.value
                if (burst in 0.01f..0.99f) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val radius = 74.dp.toPx() + 48.dp.toPx() * burst
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFFFF007A), Color(0xFF00E5FF), Color(0xFFFF9500), Color(0xFFFF007A))
                            ),
                            radius = radius,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = (4.5.dp.toPx() * (1f - burst)).coerceAtLeast(1f)),
                            alpha = (1f - burst).coerceIn(0f, 1f)
                        )
                    }
                }

                // Core Squircle Stage hosting the Unfilled Silhouette + Liquid Refilling Official Neli Logo
                Box(
                    modifier = Modifier
                        .size(166.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color(0xFF040610)),
                    contentAlignment = Alignment.Center
                ) {
                    // 1) Faint ghost silhouette of the official logo (the empty shell waiting to refill)
                    Image(
                        painter = painterResource(id = R.drawable.neli_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .alpha(0.16f)
                    )

                    // 2) Full-vibrancy official NeliTv Logo REFILLING smoothly from bottom -> top with liquid wave!
                    Image(
                        painter = painterResource(id = R.drawable.neli_logo),
                        contentDescription = "NeliTv Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .drawWithContent {
                                if (fillPct <= 0.005f) return@drawWithContent
                                val w = size.width
                                val h = size.height
                                val waterTopY = h * (1f - fillPct)
                                val waveAmp = if (fillPct in 0.02f..0.98f) 6.dp.toPx() else 0f

                                val wavePath = Path().apply {
                                    moveTo(0f, h)
                                    lineTo(0f, waterTopY)
                                    val steps = 28
                                    for (s in 0..steps) {
                                        val x = (w * s) / steps
                                        val waveY = waterTopY + sin((x / w) * 2.2 * PI + liquidWavePhase).toFloat() * waveAmp
                                        lineTo(x, waveY)
                                    }
                                    lineTo(w, h)
                                    close()
                                }

                                clipPath(wavePath) {
                                    this@drawWithContent.drawContent()
                                }

                                // Glowing liquid surface crest riding smoothly upward during refill
                                if (fillPct in 0.02f..0.98f) {
                                    val crestPath = Path().apply {
                                        val steps = 28
                                        for (s in 0..steps) {
                                            val x = (w * s) / steps
                                            val waveY = waterTopY + sin((x / w) * 2.2 * PI + liquidWavePhase).toFloat() * waveAmp
                                            if (s == 0) moveTo(x, waveY) else lineTo(x, waveY)
                                        }
                                    }
                                    drawPath(
                                        path = crestPath,
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFFA800),
                                                Color(0xFFFF0080),
                                                Color(0xFFFFFFFF),
                                                Color(0xFF00E5FF)
                                            )
                                        ),
                                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
                                        alpha = 0.92f
                                    )
                                }
                            }
                    )

                    // 3) Progressive Neon Rainbow Border Trace (`PathMeasure` refill around the squircle)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeW = 4.2.dp.toPx()
                        val half = strokeW / 2f
                        val fullSquircle = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = Rect(half, half, size.width - half, size.height - half),
                                    cornerRadius = CornerRadius(40.dp.toPx(), 40.dp.toPx())
                                )
                            )
                        }

                        // Subtle unlit track
                        drawPath(
                            path = fullSquircle,
                            color = Color.White.copy(alpha = 0.12f),
                            style = Stroke(width = strokeW)
                        )

                        // Animated traced rainbow border
                        if (tracePct > 0.01f) {
                            val measure = PathMeasure()
                            measure.setPath(fullSquircle, forceClosed = true)
                            val partialPath = Path()
                            val stopDist = measure.length * tracePct
                            measure.getSegment(0f, stopDist, partialPath, true)

                            drawPath(
                                path = partialPath,
                                brush = Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF9500),
                                        Color(0xFFFF007A),
                                        Color(0xFF9D00FF),
                                        Color(0xFF00E5FF),
                                        Color(0xFFFF9500)
                                    )
                                ),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )

                            // Glowing comet head leading the border trace
                            if (tracePct < 0.99f) {
                                val tip = measure.getPosition(stopDist)
                                drawCircle(
                                    color = Color.White,
                                    radius = 5.5.dp.toPx(),
                                    center = tip
                                )
                                drawCircle(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.55f),
                                    radius = 11.dp.toPx(),
                                    center = tip
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Refill percentage pill during the fill stage -> transforms smoothly into the brand reveal
            if (fillPct < 0.995f && logoContainerAlpha.value > 0.1f) {
                Text(
                    text = "Loading NeliTv • ${(fillPct * 100).toInt()}%",
                    color = Color(0xFF00E5FF).copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Brand Title & Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlpha.value.coerceAtLeast(if (fillPct > 0.25f) (fillPct * 0.45f) else 0f))
                    .graphicsLayer { translationY = textSlideY.value * (1f - fillPct * 0.5f) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Neli",
                        color = Color.White,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.6).sp
                    )
                    Text(
                        text = "Tv",
                        color = Color(0xFFFF2A85),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.6).sp
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

                Spacer(modifier = Modifier.height(22.dp))

                // Smooth 10-second neon progress strip + orbiting cinema dots
                Box(
                    modifier = Modifier
                        .width(176.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(overallProgress.value.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFF9500),
                                        Color(0xFFFF007A),
                                        Color(0xFF00E5FF)
                                    )
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeIndex = dotsCycle.toInt() % 8
                    for (i in 0 until 8) {
                        val isHighlighted = i == activeIndex
                        val dotColor = if (i < 4) Color(0xFFFF2A85) else Color(0xFF00E5FF)
                        Box(
                            modifier = Modifier
                                .size(if (isHighlighted) 7.5.dp else 5.dp)
                                .clip(CircleShape)
                                .background(if (isHighlighted) dotColor else dotColor.copy(alpha = 0.35f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashFloatingPosterCard(
    posterUrl: String,
    widthDp: Float,
    accentMagenta: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(posterUrl) {
        ImageRequest.Builder(context)
            .data(posterUrl)
            .size(260, 390)
            .crossfade(true)
            .build()
    }
    Box(
        modifier = modifier
            .width(widthDp.dp)
            .aspectRatio(0.67f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF131932))
            .border(
                width = 1.dp,
                color = if (accentMagenta) Color(0x66FF007A) else Color(0x6600E5FF),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
