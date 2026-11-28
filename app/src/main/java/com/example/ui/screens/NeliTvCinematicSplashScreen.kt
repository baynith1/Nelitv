package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV-ONLY Cinematic Brand Splash Screen for NeliTV (Netflix / YouTube TV-inspired choreography).
 *
 * - Pure pitch-black obsidian canvas with the exact dual-tone magenta/violet + electric cyan/teal
 *   nebula aura from the official TV banner (`img_nelitv_tv_banner_1795633622151`).
 * - Zero movie poster background animation on Smart TV.
 * - Act 1: Official glowing App Logo (`img_nelitv_app_logo_1790873809763`) emerges centered with
 *          cinematic zoom, neon shockwave ring, and studio intro chime.
 * - Act 2: App Logo glides smoothly to the left while the growing "NeliTV" wordmark (crisp white
 *          "Neli" + glowing outlined neon magenta "TV") expands outward beside the logo with a
 *          luminous reveal beam.
 * - Act 3: Specular light glint sweeps across the completed hero lockup with a sleek gradient beam.
 * - Act 4: Signature subtle zoom-fade into the TV Home screen.
 */
@Composable
fun NeliTvCinematicSplashScreen(
    onSplashFinished: () -> Unit
) {
    val density = LocalDensity.current

    val cinemaEaseOut = remember { CubicBezierEasing(0.16f, 1f, 0.3f, 1f) }
    val slideEase = remember { CubicBezierEasing(0.22f, 1f, 0.36f, 1f) }

    // Act 1 — Center Logo Emergence
    val ambientGlowAlpha = remember { Animatable(0f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.60f) }
    val shockwaveProgress = remember { Animatable(0f) }

    // Act 2 — Logo Slide Left + Growing "NeliTV" Wordmark Reveal
    // 0f = logo centered on screen; 1f = logo settled in left lockup position
    val logoSlideProgress = remember { Animatable(0f) }
    val wordRevealProgress = remember { Animatable(0f) }
    val wordGrowScale = remember { Animatable(0.72f) }
    val wordAlpha = remember { Animatable(0f) }
    val tvNeonIgnition = remember { Animatable(0f) }

    // Act 3 — Hero Glint Sweep & Accent Horizon Line
    val heroSheenProgress = remember { Animatable(-0.35f) }
    val horizonLineProgress = remember { Animatable(0f) }

    // Act 4 — Cinema Zoom-Fade Exit
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Act 1 (0ms -> 920ms): Obsidian nebula wakes + App Logo enters dead-center
        launch {
            ambientGlowAlpha.animateTo(
                targetValue = 0.72f,
                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(80L)
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(80L)
            logoScale.animateTo(
                targetValue = 1.06f,
                animationSpec = tween(durationMillis = 560, easing = cinemaEaseOut)
            )
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(170L)
            NeliIntroAudioSynthesizer.playIntroChime()
        }
        launch {
            delay(260L)
            shockwaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 740, easing = cinemaEaseOut)
            )
        }

        delay(960L)

        // Act 2 (960ms -> 2,260ms): Logo slides smoothly left while growing "NeliTV" emerges beside it
        launch {
            ambientGlowAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
            )
        }
        launch {
            logoSlideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 760, easing = slideEase)
            )
        }
        launch {
            delay(160L)
            wordAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(160L)
            wordRevealProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 860, easing = slideEase)
            )
        }
        launch {
            delay(160L)
            wordGrowScale.animateTo(
                targetValue = 1.03f,
                animationSpec = tween(durationMillis = 700, easing = cinemaEaseOut)
            )
            wordGrowScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(540L)
            tvNeonIgnition.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(620L)
            horizonLineProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 740, easing = slideEase)
            )
        }

        delay(1_280L)

        // Act 3 (2,240ms -> 3,400ms): Hero specular sheen glides across the completed NeliTV lockup
        launch {
            heroSheenProgress.animateTo(
                targetValue = 1.35f,
                animationSpec = tween(durationMillis = 780, easing = LinearEasing)
            )
        }

        delay(1_160L)

        // Act 4 (3,400ms -> 3,860ms): Signature subtle zoom-fade into TV Home
        launch {
            exitScale.animateTo(
                targetValue = 1.045f,
                animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing)
            )
        }
        exitAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 440, easing = FastOutSlowInEasing)
        )
        onSplashFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer {
                alpha = exitAlpha.value
                scaleX = exitScale.value
                scaleY = exitScale.value
            }
            .testTag("tv_cinematic_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        val shortestSide = min(maxWidth, maxHeight)
        val logoSize = (shortestSide * 0.34f).coerceIn(136.dp, 224.dp)
        val logoCornerRadius = logoSize * 0.235f
        val logoWordGap = (logoSize * 0.16f).coerceIn(20.dp, 36.dp)
        val wordFontSizeSp = (logoSize.value * 0.56f).coerceIn(66f, 118f)
        val fallbackWordWidthPx = with(density) { (wordFontSizeSp * 3.15f).dp.toPx() }
        var measuredWordWidthPx by remember { mutableFloatStateOf(0f) }
        val effectiveWordWidthPx = if (measuredWordWidthPx > 0f) measuredWordWidthPx else fallbackWordWidthPx
        val logoWordGapPx = with(density) { logoWordGap.toPx() }
        // Exact geometric offset so the App Logo sits mathematically dead-center when slide == 0f
        // and settles cleanly into the balanced left-of-wordmark lockup when slide == 1f.
        val halfRevealShiftPx = (effectiveWordWidthPx + logoWordGapPx) * 0.5f

        // LAYER 1: Ambient Obsidian Nebula matching the exact palette of img_nelitv_tv_banner_1795633622151
        // Pure pitch-black edges + warm magenta-crimson aura on center-left + electric cyan-teal aura on center-right
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val glowAlpha = ambientGlowAlpha.value.coerceIn(0f, 1f)
            val slide = logoSlideProgress.value.coerceIn(0f, 1f)
            val neonIgnite = tvNeonIgnition.value.coerceIn(0f, 1f)

            // Base pitch-black background
            drawRect(color = Color.Black)

            // Center-left warm magenta-violet nebula mist (matches Image 2 left-center aura)
            val magentaCenter = Offset(
                x = w * (0.48f - 0.10f * slide),
                y = h * 0.50f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFFD61284).copy(alpha = 0.26f * glowAlpha),
                        0.38f to Color(0xFF8C0D66).copy(alpha = 0.14f * glowAlpha),
                        0.72f to Color(0xFF31062C).copy(alpha = 0.05f * glowAlpha),
                        1.0f to Color.Transparent
                    ),
                    center = magentaCenter,
                    radius = w * 0.44f
                ),
                radius = w * 0.44f,
                center = magentaCenter
            )

            // Center-right electric cyan-teal nebula mist (matches Image 2 right-center aura behind "NeliTV")
            val cyanCenter = Offset(
                x = w * (0.53f + 0.09f * slide),
                y = h * 0.49f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to Color(0xFF009CD1).copy(alpha = (0.14f + 0.14f * slide) * glowAlpha),
                        0.40f to Color(0xFF025A7F).copy(alpha = (0.09f + 0.08f * slide) * glowAlpha),
                        0.75f to Color(0xFF012030).copy(alpha = 0.04f * glowAlpha),
                        1.0f to Color.Transparent
                    ),
                    center = cyanCenter,
                    radius = w * 0.42f
                ),
                radius = w * 0.42f,
                center = cyanCenter
            )

            // Soft magenta halo blooming specifically behind the glowing outlined "TV" letters
            if (neonIgnite > 0.01f) {
                val tvHaloCenter = Offset(
                    x = w * 0.68f,
                    y = h * 0.50f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xFFFF1894).copy(alpha = 0.22f * neonIgnite),
                            0.45f to Color(0xFFB40F73).copy(alpha = 0.08f * neonIgnite),
                            1.0f to Color.Transparent
                        ),
                        center = tvHaloCenter,
                        radius = w * 0.24f
                    ),
                    radius = w * 0.24f,
                    center = tvHaloCenter
                )
            }

            // Act 1 expanding neon shockwave ring around centered logo
            val burst = shockwaveProgress.value
            if (burst in 0.01f..0.99f) {
                val baseR = with(density) { (logoSize * 0.46f).toPx() }
                val ringR = baseR + with(density) { 86.dp.toPx() } * burst
                val ringAlpha = (1f - burst).coerceIn(0f, 1f) * 0.62f
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color(0xFFFFA800),
                            Color(0xFFFF0080),
                            Color(0xFF8F00FF),
                            Color(0xFF00D8FF),
                            Color(0xFFFFA800)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f)
                    ),
                    radius = ringR,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = (3.5.dp.toPx() * (1f - burst)).coerceAtLeast(1f)),
                    alpha = ringAlpha
                )
            }
        }

        // LAYER 2: Animated Hero Lockup — Centered App Logo -> Slides Left -> Growing "NeliTV" Reveal
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    // Act 3 diagonal cinema specular glint sweeping smoothly across the completed lockup
                    val sheen = heroSheenProgress.value
                    if (sheen in -0.25f..1.25f) {
                        val bandW = size.width * 0.16f
                        val cx = size.width * sheen
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.18f),
                                    Color(0x33FF5CC0),
                                    Color.Transparent
                                ),
                                start = Offset(cx - bandW, 0f),
                                end = Offset(cx + bandW, size.height)
                            ),
                            blendMode = BlendMode.Screen
                        )
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // 1) Official NeliTv Glowing App Logo (Starts Centered -> Glides Left)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(logoSize)
                            .graphicsLayer {
                                val slide = logoSlideProgress.value
                                // When slide == 0f, translate right so the logo sits dead-center on TV.
                                // As slide -> 1f, smoothly glides left into its banner lockup position.
                                translationX = halfRevealShiftPx * (1f - slide)
                                val s = logoScale.value
                                scaleX = s
                                scaleY = s
                                alpha = logoAlpha.value
                            }
                    ) {
                        // Subtle gradient bloom behind the neon squircle frame
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0x55FF0080),
                                        Color(0x3300D0FF),
                                        Color.Transparent
                                    ),
                                    radius = size.minDimension * 0.72f
                                ),
                                radius = size.minDimension * 0.72f
                            )
                        }

                        Image(
                            painter = painterResource(id = R.drawable.img_nelitv_app_logo_1790873809763),
                            contentDescription = "NeliTV Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(logoCornerRadius))
                                .graphicsLayer {
                                    // Slightly trim outer black margin so the glowing neon border sits crisp & flush
                                    scaleX = 1.07f
                                    scaleY = 1.07f
                                }
                        )
                    }

                    Spacer(modifier = Modifier.width(logoWordGap))

                    // 2) Growing "NeliTV" Wordmark beside the logo (exact visual match to TV banner)
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .onSizeChanged { size ->
                                if (size.width > 0) {
                                    measuredWordWidthPx = size.width.toFloat()
                                }
                            }
                            .graphicsLayer {
                                val grow = wordGrowScale.value
                                scaleX = grow
                                scaleY = grow
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                translationX = with(density) { (-20.dp.toPx()) * (1f - wordRevealProgress.value) }
                                alpha = wordAlpha.value
                            }
                            .drawWithContent {
                                val reveal = wordRevealProgress.value.coerceIn(0f, 1f)
                                if (reveal <= 0.005f) return@drawWithContent
                                val clipRight = size.width * reveal
                                clipRect(
                                    left = -24f,
                                    top = -32f,
                                    right = clipRight + 8f,
                                    bottom = size.height + 32f
                                ) {
                                    this@drawWithContent.drawContent()
                                }
                                // Luminous light-edge sweep riding along the growing wordmark reveal
                                if (reveal in 0.03f..0.96f) {
                                    val beamWidth = 28.dp.toPx()
                                    val beamAlpha = ((1f - reveal) * 1.4f).coerceIn(0f, 0.85f)
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color(0xFFFF2AA8).copy(alpha = 0.55f * beamAlpha),
                                                Color.White.copy(alpha = 0.90f * beamAlpha),
                                                Color(0xFF00E5FF).copy(alpha = 0.45f * beamAlpha),
                                                Color.Transparent
                                            ),
                                            startX = (clipRight - beamWidth).coerceAtLeast(0f),
                                            endX = clipRight + beamWidth * 0.45f
                                        ),
                                        topLeft = Offset((clipRight - beamWidth).coerceAtLeast(0f), size.height * 0.12f),
                                        size = Size(beamWidth * 1.45f, size.height * 0.76f),
                                        blendMode = BlendMode.Screen
                                    )
                                }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Crisp heavy geometric white "Neli" (matching Image 2)
                            Text(
                                text = "Neli",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = wordFontSizeSp.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1.8).sp,
                                    shadow = Shadow(
                                        color = Color.White.copy(alpha = 0.24f),
                                        offset = Offset.Zero,
                                        blurRadius = 16f
                                    )
                                )
                            )

                            Spacer(modifier = Modifier.width(3.dp))

                            // Glowing hollow outlined neon magenta-pink "TV" (matching Image 2)
                            val neonGlow = (0.35f + 0.65f * tvNeonIgnition.value).coerceIn(0f, 1f)
                            val outerStrokePx = with(density) { 4.8.dp.toPx() }
                            val midStrokePx = with(density) { 2.8.dp.toPx() }
                            val coreStrokePx = with(density) { 1.35.dp.toPx() }

                            Box(contentAlignment = Alignment.Center) {
                                // Pass 1: Wide outer neon magenta aura
                                Text(
                                    text = "TV",
                                    style = TextStyle(
                                        color = Color(0xFFFF008C).copy(alpha = 0.46f * neonGlow),
                                        fontSize = wordFontSizeSp.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp,
                                        drawStyle = Stroke(
                                            width = outerStrokePx,
                                            join = StrokeJoin.Round,
                                            cap = StrokeCap.Round
                                        ),
                                        shadow = Shadow(
                                            color = Color(0xFFFF008C).copy(alpha = 0.90f * neonGlow),
                                            offset = Offset.Zero,
                                            blurRadius = 32f
                                        )
                                    )
                                )
                                // Pass 2: Vivid hot-pink neon outline body
                                Text(
                                    text = "TV",
                                    style = TextStyle(
                                        color = Color(0xFFFF24A4).copy(alpha = 0.95f * neonGlow),
                                        fontSize = wordFontSizeSp.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp,
                                        drawStyle = Stroke(
                                            width = midStrokePx,
                                            join = StrokeJoin.Round,
                                            cap = StrokeCap.Round
                                        ),
                                        shadow = Shadow(
                                            color = Color(0xFFFF2AA8).copy(alpha = 0.85f * neonGlow),
                                            offset = Offset.Zero,
                                            blurRadius = 16f
                                        )
                                    )
                                )
                                // Pass 3: Razor-sharp bright pink-white neon core highlight
                                Text(
                                    text = "TV",
                                    style = TextStyle(
                                        color = Color(0xFFFFA6DF).copy(alpha = 0.92f * neonGlow),
                                        fontSize = wordFontSizeSp.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.5.sp,
                                        drawStyle = Stroke(
                                            width = coreStrokePx,
                                            join = StrokeJoin.Round,
                                            cap = StrokeCap.Round
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Minimalist signature gradient horizon accent beam beneath lockup
            Canvas(
                modifier = Modifier
                    .width((logoSize * 2.2f).coerceIn(240.dp, 420.dp))
                    .height(3.dp)
            ) {
                val p = horizonLineProgress.value.coerceIn(0f, 1f)
                if (p > 0.01f) {
                    val drawnWidth = size.width * p
                    val startX = (size.width - drawnWidth) * 0.5f
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFFF9E00),
                                Color(0xFFFF0080),
                                Color(0xFF9D00FF),
                                Color(0xFF00D8FF),
                                Color.Transparent
                            ),
                            startX = startX,
                            endX = startX + drawnWidth
                        ),
                        start = Offset(startX, size.height * 0.5f),
                        end = Offset(startX + drawnWidth, size.height * 0.5f),
                        strokeWidth = size.height,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
