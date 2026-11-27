package com.example.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveChannel
import com.example.model.MediaContent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 3D Particle & Wave Mesh AI Orb (matching the user's futuristic circular sphere aesthetic — NO typing box).
 *
 * State-Driven Colors (Exact Spec):
 * - Listening (User Speaking): Electric BLUE (#00D4FF / #2563EB)
 * - Thinking (Processing):     Luminous WHITE-YELLOW (#FFFBEB / #FACC15)
 * - Speaking (AI Talking):     Vibrant PINK pulsing in beat (#FF2E93 / #F472B6)
 * - Idle / Standby:            Soft Blue-Violet cosmic nebula
 */
@Composable
fun NeliAiParticleMeshOrb(
    voiceState: NeliAiVoiceState,
    size: Dp = 96.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is NeliAiVoiceState.Listening
    val isThinking = voiceState is NeliAiVoiceState.Thinking
    val isSpeaking = voiceState is NeliAiVoiceState.Speaking

    val primaryColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF00D4FF) // Blue when user speaks
            isThinking -> Color(0xFFFEF08A)  // White-Yellow when thinking
            isSpeaking -> Color(0xFFFF2E93)  // Pink when AI speaks
            else -> Color(0xFF38BDF8)
        },
        animationSpec = tween(260),
        label = "mesh_primary"
    )
    val secondaryColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF2563EB)
            isThinking -> Color(0xFFFFFFFF)
            isSpeaking -> Color(0xFFF472B6)
            else -> Color(0xFF9333EA)
        },
        animationSpec = tween(260),
        label = "mesh_secondary"
    )
    val accentColor by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF60A5FA)
            isThinking -> Color(0xFFFACC15)
            isSpeaking -> Color(0xFFFF7EB6)
            else -> Color(0xFFEC4899)
        },
        animationSpec = tween(260),
        label = "mesh_accent"
    )

    val dynamicEnergy = when (voiceState) {
        is NeliAiVoiceState.Listening -> (0.35f + (voiceState.rmsDb / 12f) * 0.65f).coerceIn(0.35f, 1f)
        is NeliAiVoiceState.Speaking -> voiceState.beatAmplitude.coerceIn(0.3f, 1f)
        is NeliAiVoiceState.Thinking -> 0.85f
        else -> 0.25f
    }
    val smoothEnergy by animateFloatAsState(
        targetValue = dynamicEnergy,
        animationSpec = tween(90),
        label = "mesh_energy"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orb_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThinking) 1300 else if (isSpeaking) 1700 else 2800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_phase"
    )
    val beatScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = if (isSpeaking) 1.09f else if (isListening) 1.05f else 1.01f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 320 else 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mesh_beat"
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("neli_ai_particle_mesh_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxR = (minOf(this.size.width, this.size.height) / 2f) * 0.92f
            val baseR = maxR * 0.72f * beatScale

            // 1. Ambient outer glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.55f * smoothEnergy + 0.15f),
                        secondaryColor.copy(alpha = 0.28f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxR * 1.08f
                ),
                radius = maxR * 1.08f,
                center = center
            )

            // 2. Deep dark core sphere backdrop
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryColor.copy(alpha = 0.42f),
                        Color(0xFF0A0C18),
                        Color(0xFF05070E)
                    ),
                    center = Offset(center.x - baseR * 0.18f, center.y - baseR * 0.18f),
                    radius = baseR
                ),
                radius = baseR,
                center = center
            )

            // 3. Flowing 3D Wireframe / Wave Mesh Rings (Image 5 aesthetic)
            val rings = 7
            val steps = 40
            for (rIdx in 0 until rings) {
                val frac = (rIdx + 1).toFloat() / rings.toFloat()
                val ringRadius = baseR * (0.36f + frac * 0.62f)
                val path = Path()
                for (s in 0..steps) {
                    val theta = (s.toFloat() / steps.toFloat()) * (2.0 * PI).toFloat()
                    val waveAmp = baseR * (0.06f + 0.11f * smoothEnergy)
                    val deform = sin(theta * 3f + phase + rIdx * 0.8f) * waveAmp +
                        cos(theta * 5f - phase * 1.4f + rIdx) * (waveAmp * 0.45f)
                    val rr = ringRadius + deform
                    val x = center.x + cos(theta) * rr
                    val y = center.y + sin(theta) * rr * (0.86f + 0.14f * cos(phase + rIdx))
                    if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                val ringColor = if (rIdx % 2 == 0) primaryColor else accentColor
                drawPath(
                    path = path,
                    color = ringColor.copy(alpha = (0.28f + 0.45f * frac).coerceIn(0.2f, 0.85f)),
                    style = Stroke(width = (1.1f + frac * 1.4f).dp.toPx())
                )
            }

            // 4. Shimmering 3D Particle Cloud Dots (Image 4 aesthetic)
            val dotCount = 54
            for (i in 0 until dotCount) {
                val goldenAngle = i * 2.39996f + phase * (if (isThinking) 1.5f else 0.7f)
                val radialFrac = 0.25f + ((i % 13) / 13f) * 0.75f
                val wobble = sin(phase * 2f + i * 0.6f) * (baseR * 0.08f * smoothEnergy)
                val dist = (baseR * radialFrac) + wobble
                val px = center.x + cos(goldenAngle) * dist
                val py = center.y + sin(goldenAngle) * dist
                val dotR = (1.2f + (i % 3) * 0.75f).dp.toPx()
                val dotColor = when (i % 3) {
                    0 -> primaryColor
                    1 -> secondaryColor
                    else -> accentColor
                }
                drawCircle(
                    color = dotColor.copy(alpha = (0.55f + 0.4f * smoothEnergy).coerceIn(0.4f, 0.98f)),
                    radius = dotR,
                    center = Offset(px, py)
                )
            }

            // 5. Crisp Inner Rim Highlight
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(primaryColor, secondaryColor, accentColor, primaryColor),
                    center = center
                ),
                radius = baseR,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

/**
 * Full-Screen / In-Player Glowing Edge Border Effect (Image 3 style) + Sleek Dark Caption Bar (Image 1 style)
 * Used INSIDE NeliTv App & INSIDE PlayerScreen — NO floating AI ball inside the app!
 *
 * Also renders:
 * - Interactive Movie Suggestion Checklist Popup (Tick movies -> Download Selected / Play in Sequence)
 * - Movie Info Preview Card
 * - VIP Payment Unlock Card when user asks for a locked channel without VIP
 */
@Composable
fun NeliAiEdgeGlowOverlay(
    visible: Boolean,
    voiceState: NeliAiVoiceState,
    captionText: String,
    isPlayerMutedForAi: Boolean,
    showCaptions: Boolean,
    suggestedMovies: List<MediaContent>,
    movieInfoTarget: MediaContent?,
    lockedChannelPrompt: LiveChannel?,
    onTapMic: () -> Unit,
    onDismiss: () -> Unit,
    onDownloadSelectedMovies: (List<MediaContent>) -> Unit,
    onPlayMoviesInSequence: (List<MediaContent>) -> Unit,
    onOpenMovieNow: (MediaContent) -> Unit,
    onUnlockVipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = voiceState is NeliAiVoiceState.Listening
    val isThinking = voiceState is NeliAiVoiceState.Thinking
    val isSpeaking = voiceState is NeliAiVoiceState.Speaking

    val edgeColorA by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF00D4FF) // Blue listening
            isThinking -> Color(0xFFFFFFFF)  // White-Yellow thinking
            isSpeaking -> Color(0xFFFF2E93)  // Pink speaking
            else -> Color(0xFF38BDF8)
        },
        animationSpec = tween(250),
        label = "edge_a"
    )
    val edgeColorB by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF2563EB)
            isThinking -> Color(0xFFFACC15)
            isSpeaking -> Color(0xFFF472B6)
            else -> Color(0xFFA855F7)
        },
        animationSpec = tween(250),
        label = "edge_b"
    )
    val edgeColorC by animateColorAsState(
        targetValue = when {
            isListening -> Color(0xFF38BDF8)
            isThinking -> Color(0xFFFEF08A)
            isSpeaking -> Color(0xFFFF7EB6)
            else -> Color(0xFFFF2E93)
        },
        animationSpec = tween(250),
        label = "edge_c"
    )

    val infinite = rememberInfiniteTransition(label = "edge_glow_anim")
    val glowPulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = if (isSpeaking) 1.0f else 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 300 else 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_pulse"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(240)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("neli_ai_edge_glow_overlay")
        ) {
            // 1. Apple-Intelligence / Siri Full-Perimeter Glowing Edge Border (Image 3)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeOuter = (14.dp.toPx() * glowPulse)
                val strokeInner = (5.dp.toPx() * glowPulse)
                val corner = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                val brush = Brush.sweepGradient(
                    colors = listOf(edgeColorA, edgeColorB, edgeColorC, edgeColorA),
                    center = Offset(size.width / 2f, size.height / 2f)
                )
                // Soft outer diffused neon aura
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(strokeOuter / 2f, strokeOuter / 2f),
                    size = Size(size.width - strokeOuter, size.height - strokeOuter),
                    cornerRadius = corner,
                    alpha = 0.38f * glowPulse,
                    style = Stroke(width = strokeOuter)
                )
                // Sharp crisp luminous inner edge
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(strokeInner / 2f, strokeInner / 2f),
                    size = Size(size.width - strokeInner, size.height - strokeInner),
                    cornerRadius = corner,
                    alpha = 0.92f,
                    style = Stroke(width = strokeInner)
                )
            }

            // 2. Bottom Sleek Caption & Interactive Cards Container (Image 1 aesthetic — NO typing box!)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // VIP Locked Channel Payment Card
                if (lockedChannelPrompt != null) {
                    Surface(
                        color = Color(0xF011131D),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, Color(0xFFF59E0B), RoundedCornerShape(22.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "VIP Required",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lockedChannelPrompt.name} requires VIP",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pay to unlock VIP and watch instantly.",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp
                                )
                            }
                            Button(
                                onClick = onUnlockVipClick,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("neli_ai_unlock_vip_btn")
                            ) {
                                Text("Pay VIP", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Movie Info Card
                if (movieInfoTarget != null) {
                    Surface(
                        color = Color(0xF2121521),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, edgeColorA.copy(alpha = 0.65f), RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = movieInfoTarget.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${movieInfoTarget.releaseYear.ifBlank { "New" }} • ${movieInfoTarget.primaryGenre.ifBlank { movieInfoTarget.genre }} • ★ ${movieInfoTarget.rating.ifBlank { "HD" }}",
                                color = edgeColorA,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = movieInfoTarget.synopsis.ifBlank { "Available to stream or download on NeliTv." },
                                color = Color(0xFFD1D5DB),
                                fontSize = 12.5.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onOpenMovieNow(movieInfoTarget) },
                                    colors = ButtonDefaults.buttonColors(containerColor = edgeColorA),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onDownloadSelectedMovies(listOf(movieInfoTarget)) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Zero-Tick Hands-Free Recommended Movies Popup (Say "Play number 1", "Download all", "Play them" — or 1-tap!)
                if (suggestedMovies.isNotEmpty()) {
                    Surface(
                        color = Color(0xF2111420),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, edgeColorA.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                            .testTag("neli_ai_movie_picker_popup")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Neli AI Picks • Say \"Play #1\", \"Download all\" or tap any",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(suggestedMovies, key = { _, m -> m.id }) { index, movie ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF1A1F30))
                                            .clickable { onOpenMovieNow(movie) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(edgeColorA.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = edgeColorA,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = movie.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${movie.primaryGenre.ifBlank { movie.genre }} • ${movie.releaseYear.ifBlank { "HD" }}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDownloadSelectedMovies(listOf(movie)) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Download,
                                                contentDescription = "Download ${movie.title}",
                                                tint = edgeColorA
                                            )
                                        }
                                        IconButton(
                                            onClick = { onOpenMovieNow(movie) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play ${movie.title}",
                                                tint = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onDownloadSelectedMovies(suggestedMovies) },
                                    colors = ButtonDefaults.buttonColors(containerColor = edgeColorA),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("neli_ai_picker_download_btn")
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Download All (${suggestedMovies.size})", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onPlayMoviesInSequence(suggestedMovies) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("neli_ai_picker_sequence_btn")
                                ) {
                                    Icon(Icons.Default.PlaylistPlay, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Play All", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Image 1 Style Dark Rounded Live Caption Card (NO typing box!)
                AnimatedVisibility(
                    visible = showCaptions,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    Surface(
                        color = Color(0xEB151822),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.2.dp, edgeColorA.copy(alpha = 0.7f), RoundedCornerShape(26.dp))
                            .testTag("neli_ai_caption_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(edgeColorA)
                                    )
                                    val statusLabel = when {
                                        isListening && isPlayerMutedForAi -> "Listening (Player Muted 5s)..."
                                        isListening -> "Neli AI is Listening..."
                                        isThinking -> "Neli AI is Thinking..."
                                        isSpeaking -> "Neli AI is Speaking..."
                                        else -> "Neli AI Ready"
                                    }
                                    Text(
                                        text = statusLabel,
                                        color = edgeColorA,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("neli_ai_edge_dismiss_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss Neli AI",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            val liveHeadline = when (voiceState) {
                                is NeliAiVoiceState.Listening -> voiceState.partialText.ifBlank {
                                    captionText.ifBlank { "Speak your command..." }
                                }
                                is NeliAiVoiceState.Speaking -> voiceState.text
                                is NeliAiVoiceState.Thinking -> captionText.ifBlank { "Thinking..." }
                                is NeliAiVoiceState.Notice -> voiceState.message
                                else -> captionText.ifBlank { "Say \"Hey Neli\" followed by any command" }
                            }

                            Text(
                                text = liveHeadline,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Clean centered Voice Pill Button (Image 1 style — zero typing box!)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(edgeColorA.copy(alpha = 0.22f))
                                        .border(1.5.dp, edgeColorA, CircleShape)
                                        .clickable { onTapMic() }
                                        .padding(horizontal = 24.dp, vertical = 10.dp)
                                        .testTag("neli_ai_caption_mic_btn"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isListening || isSpeaking) Icons.Default.GraphicEq else Icons.Default.Mic,
                                            contentDescription = "Speak to Neli AI",
                                            tint = edgeColorA,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = if (isListening) "Listening" else if (isSpeaking) "Speaking" else "Tap to Speak",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
