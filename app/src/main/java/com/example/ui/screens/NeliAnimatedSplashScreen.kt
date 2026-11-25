package com.example.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val NELI_SPLASH_TOTAL_DURATION_MS = 5_000L

private data class SplashMovieItem(
    val imageUrl: String,
    val title: String
)

/**
 * 5-Second Cinematic Movie-Orbit Splash Screen for NeliTv:
 * - Runs for exactly 5 seconds with NO skip button.
 * - Displays ONLY movies (no Live TV channels) gliding in a calm, slow, translucent
 *   orbital rotation around the centered NeliTv app logo.
 * - Reads high-frequency animation values strictly in draw/graphicsLayer phases to
 *   prevent UI thread jank on cold launch.
 */
@Composable
fun NeliAnimatedSplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var mountOrbitLayer by remember { mutableStateOf(false) }

    val defaultMovieItems = remember {
        listOf(
            SplashMovieItem("https://image.tmdb.org/t/p/w342/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg", "Cinema 1"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/kDp1vUBnMpe8ak4rjgl3cLELqjU.jpg", "Cinema 2"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/vpnVM9B6NMmQpWeZvzLvDESb2QY.jpg", "Cinema 3"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/gKkl37BQuKTanygYQG1pyYgLVgf.jpg", "Cinema 4"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/wWba3TaojhK7NdycRhoQpsG0FaH.jpg", "Cinema 5"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg", "Cinema 6"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/qhb1qOilapbapxWQn9jtRCMwXJF.jpg", "Cinema 7"),
            SplashMovieItem("https://image.tmdb.org/t/p/w342/tMefBSflR6PGQLv7WvFPpKLZkyk.jpg", "Cinema 8")
        )
    }

    var splashMovieItems by remember { mutableStateOf(defaultMovieItems) }

    LaunchedEffect(Unit) {
        // Wait for initial UI frames to commit before mounting orbiting movie artwork
        withFrameNanos { }
        withFrameNanos { }
        delay(100L)
        mountOrbitLayer = true

        // Load real cached movies off the main thread (strictly movies only — zero channels)
        launch(Dispatchers.IO) {
            try {
                MediaContentRepository.initializeAndPrewarmFromCache(context)
                val catalog = MediaContentRepository.mediaCatalog.value
                val realPosters = catalog
                    .filter { it.published && !it.isAdultContent }
                    .mapNotNull { media ->
                        val url = media.posterUrl.trim().ifBlank { media.backdropUrl.trim() }
                        if (url.startsWith("http", ignoreCase = true)) {
                            SplashMovieItem(imageUrl = url, title = media.title)
                        } else {
                            null
                        }
                    }
                val mergedMovies = (realPosters + defaultMovieItems)
                    .distinctBy { it.imageUrl }
                    .distinctBy { it.title.lowercase().trim().ifBlank { it.imageUrl } }
                    .take(8)

                withContext(Dispatchers.Main) {
                    if (mergedMovies.isNotEmpty()) {
                        splashMovieItems = mergedMovies
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    // 5-second choreographed animatables (read strictly in draw/graphicsLayer phases)
    val backdropAlpha = remember { Animatable(0f) }
    val logoContainerAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.86f) }
    val borderTraceProgress = remember { Animatable(0f) }
    val logoRefillProgress = remember { Animatable(0f) }
    val climaxBurst = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textSlideY = remember { Animatable(20f) }
    val overallProgress = remember { Animatable(0f) }
    val exitFadeAlpha = remember { Animatable(1f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_movie_orbit_motion")

    // Calm, luxurious slow rotation around the app logo (26 seconds per full 360° turn)
    val orbitAngleState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "movie_orbit_angle"
    )

    val liquidWavePhaseState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1_150, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_wave_phase"
    )

    val auraPulseState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aura_pulse"
    )

    val dotsCycleState: State<Float> = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dots_cycle"
    )

    LaunchedEffect(Unit) {
        // Master 5-second progress bar
        launch {
            overallProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = NELI_SPLASH_TOTAL_DURATION_MS.toInt(),
                    easing = LinearEasing
                )
            )
        }

        // Act 1 (0ms -> 600ms): Translucent orbiting movies + logo container emerge together
        launch {
            delay(80L)
            backdropAlpha.animateTo(1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        }
        launch {
            delay(100L)
            logoContainerAlpha.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        }
        launch {
            delay(100L)
            logoScale.animateTo(1.0f, animationSpec = tween(680, easing = FastOutSlowInEasing))
        }
        launch {
            delay(100L)
            borderTraceProgress.animateTo(1f, animationSpec = tween(1_900, easing = FastOutSlowInEasing))
        }

        delay(250L)

        // Studio intro chime
        launch {
            NeliIntroAudioSynthesizer.playIntroChime()
        }

        // Act 2 (250ms -> 2,850ms): Smooth bottom-to-top liquid refill of the NeliTv logo (2.6s)
        logoRefillProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2_600, easing = FastOutSlowInEasing)
        )

        // Act 3 (2,850ms -> 4,580ms): Climax neon bloom + brand wordmark reveal while movies keep orbiting slowly
        launch {
            logoScale.animateTo(1.055f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            logoScale.animateTo(1.0f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            climaxBurst.animateTo(1f, animationSpec = tween(850, easing = FastOutSlowInEasing))
        }
        launch {
            textAlpha.animateTo(1f, animationSpec = tween(480, easing = FastOutSlowInEasing))
        }
        launch {
            textSlideY.animateTo(0f, animationSpec = tween(480, easing = FastOutSlowInEasing))
        }

        delay(1_730L)

        // Act 4 (4,580ms -> 5,000ms): Smooth cinema fade-out right at 5.0 seconds
        exitFadeAlpha.animateTo(0f, animationSpec = tween(420, easing = FastOutSlowInEasing))
        onSplashFinished()
    }

    // Reusable Skia drawing paths (zero per-frame allocations)
    val reusableWavePath = remember { Path() }
    val reusableCrestPath = remember { Path() }
    val reusableFullSquircle = remember { Path() }
    val reusablePartialPath = remember { Path() }
    val reusablePathMeasure = remember { PathMeasure() }

    val rainbowColors = remember {
        listOf(
            Color(0xFFFF9500),
            Color(0xFFFF007A),
            Color(0xFF9D00FF),
            Color(0xFF00E5FF),
            Color(0xFFFF9500)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF100A24),
                        Color(0xFF070718),
                        Color(0xFF03040D)
                    )
                )
            )
            .graphicsLayer { alpha = exitFadeAlpha.value }
            .testTag("animated_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1: Translucent Movie Posters slowly orbiting in a sleek halo around the NeliTv logo
        if (mountOrbitLayer) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha.value }
            ) {
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val orbitCount = splashMovieItems.size.coerceIn(6, 8)
                val centerX = widthPx / 2f
                val centerY = heightPx * 0.44f
                val baseRadius = min(widthPx * 0.40f, heightPx * 0.30f)
                val radiusX = baseRadius
                val radiusY = (baseRadius * 1.12f).coerceAtMost(heightPx * 0.34f)
                val cardWidthDp = if (maxWidth >= 600.dp) 104f else 84f
                val cardWPx = with(density) { cardWidthDp.dp.toPx() }
                val cardHPx = with(density) { (cardWidthDp / 0.67f).dp.toPx() }

                // Subtle glowing circular orbit track framing the logo
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val ringRadius = (radiusX + radiusY) * 0.5f
                    drawCircle(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                Color(0x28FF007A),
                                Color(0x2200E5FF),
                                Color(0x189D00FF),
                                Color(0x28FF007A)
                            ),
                            center = Offset(centerX, centerY)
                        ),
                        radius = ringRadius,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = ringRadius * 0.72f,
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                for (slot in 0 until orbitCount) {
                    val movieItem = splashMovieItems[slot % splashMovieItems.size]
                    key("movie_orbit_$slot") {
                        val slotOffsetDeg = slot * (360f / orbitCount)
                        SplashTranslucentMovieCard(
                            item = movieItem,
                            widthDp = cardWidthDp,
                            accentMagenta = slot % 2 == 0,
                            modifier = Modifier.graphicsLayer {
                                val angleRad = Math.toRadians((orbitAngleState.value + slotOffsetDeg).toDouble())
                                val cosA = cos(angleRad).toFloat()
                                val sinA = sin(angleRad).toFloat()
                                // Depth goes 0f (top/back of orbit) -> 1f (bottom/front of orbit)
                                val depth = (sinA + 1f) / 2f
                                val posX = centerX + cosA * radiusX - (cardWPx / 2f)
                                val posY = centerY + sinA * radiusY - (cardHPx / 2f)
                                val perspectiveScale = 0.84f + (depth * 0.22f)
                                // Sleek, airy translucent opacity so posters look glass-like and never overpower the logo
                                val translucentAlpha = 0.25f + (depth * 0.30f)

                                translationX = posX
                                translationY = posY
                                scaleX = perspectiveScale
                                scaleY = perspectiveScale
                                rotationZ = cosA * 5.5f
                                alpha = translucentAlpha
                            }
                        )
                    }
                }
            }
        }

        // LAYER 2: Soft Radial Cinema Glow Vignette — keeps center logo razor-sharp while translucent movies glow around it
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to Color(0x9E08061A),
                        0.45f to Color(0x4A060718),
                        1.0f to Color(0x8803040E)
                    )
                )
        )

        // LAYER 3: Centerpiece — Signature NeliTv Logo REFILL Animation + Wordmark (No Skip Button)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(214.dp)
                    .graphicsLayer {
                        val s = logoScale.value
                        scaleX = s
                        scaleY = s
                        alpha = logoContainerAlpha.value
                    },
                contentAlignment = Alignment.Center
            ) {
                // Ambient Neon Halo behind logo
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .graphicsLayer {
                            val fillPct = logoRefillProgress.value.coerceIn(0f, 1f)
                            val auraScale = if (fillPct >= 0.99f) auraPulseState.value else 0.88f + 0.12f * fillPct
                            scaleX = auraScale
                            scaleY = auraScale
                            alpha = 0.24f + 0.56f * fillPct
                        }
                        .background(
                            Brush.radialGradient(
                                0.0f to Color(0xAAFF007A),
                                0.42f to Color(0x669D00FF),
                                0.72f to Color(0x3300E5FF),
                                1.0f to Color.Transparent
                            ),
                            shape = CircleShape
                        )
                )

                // Expanding Neon Shockwave Ring at 100% refill completion
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val burst = climaxBurst.value
                    if (burst in 0.01f..0.99f) {
                        val radius = 70.dp.toPx() + 44.dp.toPx() * burst
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color(0xFFFF007A), Color(0xFF00E5FF), Color(0xFFFF9500), Color(0xFFFF007A))
                            ),
                            radius = radius,
                            center = Offset(size.width / 2f, size.height / 2f),
                            style = Stroke(width = (4.dp.toPx() * (1f - burst)).coerceAtLeast(1f)),
                            alpha = (1f - burst).coerceIn(0f, 1f)
                        )
                    }
                }

                // Core Squircle Stage hosting the Official NeliTv Logo Liquid Refill
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .clip(RoundedCornerShape(38.dp))
                        .background(Color(0xE6050714)),
                    contentAlignment = Alignment.Center
                ) {
                    // 1) Faint silhouette shell
                    Image(
                        painter = painterResource(id = R.drawable.neli_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .alpha(0.16f)
                    )

                    // 2) Full-vibrancy NeliTv Logo refilling from bottom -> top with liquid wave
                    Image(
                        painter = painterResource(id = R.drawable.neli_logo),
                        contentDescription = "NeliTv Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .drawWithContent {
                                val fillPct = logoRefillProgress.value.coerceIn(0f, 1f)
                                if (fillPct <= 0.005f) return@drawWithContent
                                val w = size.width
                                val h = size.height
                                val waterTopY = h * (1f - fillPct)
                                val waveAmp = if (fillPct in 0.02f..0.98f) 5.5.dp.toPx() else 0f
                                val phase = liquidWavePhaseState.value

                                reusableWavePath.reset()
                                reusableWavePath.moveTo(0f, h)
                                reusableWavePath.lineTo(0f, waterTopY)
                                val steps = 20
                                for (s in 0..steps) {
                                    val x = (w * s) / steps
                                    val waveY = waterTopY + sin((x / w) * 2.2 * PI + phase).toFloat() * waveAmp
                                    reusableWavePath.lineTo(x, waveY)
                                }
                                reusableWavePath.lineTo(w, h)
                                reusableWavePath.close()

                                clipPath(reusableWavePath) {
                                    this@drawWithContent.drawContent()
                                }

                                if (fillPct in 0.02f..0.98f) {
                                    reusableCrestPath.reset()
                                    for (s in 0..steps) {
                                        val x = (w * s) / steps
                                        val waveY = waterTopY + sin((x / w) * 2.2 * PI + phase).toFloat() * waveAmp
                                        if (s == 0) reusableCrestPath.moveTo(x, waveY) else reusableCrestPath.lineTo(x, waveY)
                                    }
                                    drawPath(
                                        path = reusableCrestPath,
                                        brush = Brush.horizontalGradient(
                                            listOf(
                                                Color(0xFFFFA800),
                                                Color(0xFFFF0080),
                                                Color(0xFFFFFFFF),
                                                Color(0xFF00E5FF)
                                            )
                                        ),
                                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                                        alpha = 0.92f
                                    )
                                }
                            }
                    )

                    // 3) Progressive Neon Rainbow Border Trace
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val tracePct = borderTraceProgress.value.coerceIn(0f, 1f)
                        val strokeW = 3.8.dp.toPx()
                        val half = strokeW / 2f
                        reusableFullSquircle.reset()
                        reusableFullSquircle.addRoundRect(
                            RoundRect(
                                rect = Rect(half, half, size.width - half, size.height - half),
                                cornerRadius = CornerRadius(38.dp.toPx(), 38.dp.toPx())
                            )
                        )

                        drawPath(
                            path = reusableFullSquircle,
                            color = Color.White.copy(alpha = 0.12f),
                            style = Stroke(width = strokeW)
                        )

                        if (tracePct > 0.01f) {
                            reusablePathMeasure.setPath(reusableFullSquircle, forceClosed = true)
                            reusablePartialPath.reset()
                            val stopDist = reusablePathMeasure.length * tracePct
                            reusablePathMeasure.getSegment(0f, stopDist, reusablePartialPath, true)

                            drawPath(
                                path = reusablePartialPath,
                                brush = Brush.sweepGradient(rainbowColors),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )

                            if (tracePct < 0.99f) {
                                val tip = reusablePathMeasure.getPosition(stopDist)
                                drawCircle(
                                    color = Color.White,
                                    radius = 5.dp.toPx(),
                                    center = tip
                                )
                                drawCircle(
                                    color = Color(0xFF00E5FF).copy(alpha = 0.55f),
                                    radius = 10.dp.toPx(),
                                    center = tip
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SplashRefillPercentLabel(
                fillProgressProvider = { logoRefillProgress.value },
                containerAlphaProvider = { logoContainerAlpha.value }
            )

            // Brand Title & Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    val fillPct = logoRefillProgress.value.coerceIn(0f, 1f)
                    alpha = textAlpha.value.coerceAtLeast(if (fillPct > 0.25f) (fillPct * 0.45f) else 0f)
                    translationY = textSlideY.value * (1f - fillPct * 0.5f)
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Neli",
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.6).sp
                    )
                    Text(
                        text = "Tv",
                        color = Color(0xFFFF2A85),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.6).sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Free Entertainment For Everyone",
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 13.5.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Smooth 5-second neon progress strip
                Canvas(
                    modifier = Modifier
                        .width(168.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                ) {
                    drawRect(color = Color.White.copy(alpha = 0.14f))
                    val barW = size.width * overallProgress.value.coerceIn(0.02f, 1f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color(0xFFFF9500),
                                Color(0xFFFF007A),
                                Color(0xFF00E5FF)
                            )
                        ),
                        size = size.copy(width = barW)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                SplashCinemaDotsRow(dotsCycleProvider = { dotsCycleState.value })
            }
        }
    }
}

@Composable
private fun SplashRefillPercentLabel(
    fillProgressProvider: () -> Float,
    containerAlphaProvider: () -> Float
) {
    val percentInt by remember {
        derivedStateOf { (fillProgressProvider().coerceIn(0f, 1f) * 100f).toInt() }
    }
    val isVisible by remember {
        derivedStateOf { fillProgressProvider() < 0.995f && containerAlphaProvider() > 0.1f }
    }
    if (isVisible) {
        Text(
            text = "Loading NeliTv • $percentInt%",
            color = Color(0xFF00E5FF).copy(alpha = 0.92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SplashCinemaDotsRow(
    dotsCycleProvider: () -> Float
) {
    val activeIndex by remember {
        derivedStateOf { dotsCycleProvider().toInt() % 6 }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 6) {
            val isHighlighted = i == activeIndex
            val dotColor = if (i < 3) Color(0xFFFF2A85) else Color(0xFF00E5FF)
            Box(
                modifier = Modifier
                    .size(if (isHighlighted) 7.5.dp else 5.dp)
                    .clip(CircleShape)
                    .background(if (isHighlighted) dotColor else dotColor.copy(alpha = 0.35f))
            )
        }
    }
}

@Composable
private fun SplashTranslucentMovieCard(
    item: SplashMovieItem,
    widthDp: Float,
    accentMagenta: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(item.imageUrl) {
        ImageRequest.Builder(context)
            .data(item.imageUrl)
            .size(160, 240)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .crossfade(true)
            .build()
    }
    Box(
        modifier = modifier
            .width(widthDp.dp)
            .aspectRatio(0.67f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x33161C38))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f),
                        if (accentMagenta) Color(0x66FF007A) else Color(0x6600E5FF),
                        Color.White.copy(alpha = 0.12f)
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.82f)
        )
        // Soft translucent glass sheen across poster for an ethereal floating cinema look
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.14f),
                            Color.Transparent,
                            Color(0x55040614)
                        )
                    )
                )
        )
    }
}
