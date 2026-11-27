package com.example.ai

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliAccentGreen
import com.example.ui.theme.NeliCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliNeonPurple
import com.example.ui.theme.NeliWarmOrange
import com.example.ui.tv.tvGeminiFocusable
import kotlin.math.PI
import kotlin.math.sin

/**
 * Signature living plasma orb for Neli AI.
 * Reacts smoothly to Listening RMS volume, Thinking spin, Speaking pulse, and Hands-Free standby.
 */
@Composable
fun NeliAiAuroraOrb(
    voiceState: NeliAiVoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp
) {
    val infinite = rememberInfiniteTransition(label = "neli_ai_orb")
    val spinAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    is NeliAiVoiceState.Listening -> 2200
                    is NeliAiVoiceState.Thinking -> 1200
                    is NeliAiVoiceState.Speaking -> 2800
                    else -> 6500
                },
                easing = LinearEasing
            )
        ),
        label = "orb_spin"
    )
    val breathe by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_breathe"
    )

    val rmsBoost by animateFloatAsState(
        targetValue = when (voiceState) {
            is NeliAiVoiceState.Listening -> 1f + (voiceState.rmsDb / 12f).coerceIn(0f, 0.35f)
            is NeliAiVoiceState.Speaking -> 1.12f
            is NeliAiVoiceState.Thinking -> 1.06f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "orb_rms"
    )

    val isActive = voiceState is NeliAiVoiceState.Listening ||
        voiceState is NeliAiVoiceState.Speaking ||
        voiceState is NeliAiVoiceState.Thinking

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .tvGeminiFocusable(
                shape = CircleShape,
                focusScale = 1.12f,
                borderWidth = 2.5.dp,
                onClick = onClick
            )
            .clickable { onClick() }
            .testTag("neli_ai_aurora_orb"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = this.size.minDimension / 2f
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val pulseRadius = radius * (if (isActive) breathe * rmsBoost else 0.94f).coerceAtMost(1f)

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        NeliCyan.copy(alpha = if (isActive) 0.42f else 0.22f),
                        NeliMagenta.copy(alpha = if (isActive) 0.28f else 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // Rotating aurora plasma ring
            rotate(degrees = spinAngle, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            NeliCyan,
                            NeliMagenta,
                            NeliWarmOrange,
                            NeliNeonPurple,
                            NeliCyan
                        ),
                        center = center
                    ),
                    radius = pulseRadius * 0.80f,
                    center = center,
                    style = Stroke(
                        width = if (isActive) radius * 0.14f else radius * 0.09f,
                        cap = StrokeCap.Round
                    )
                )
            }

            // Inner dark obsidian core with glossy highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        if (voiceState is NeliAiVoiceState.Listening) Color(0xFF1A2A52) else Color(0xFF151933),
                        Color(0xFF070A16)
                    ),
                    center = Offset(center.x - radius * 0.15f, center.y - radius * 0.18f),
                    radius = radius * 0.75f
                ),
                radius = radius * 0.66f,
                center = center
            )
        }

        val icon = when (voiceState) {
            is NeliAiVoiceState.Listening -> Icons.Default.Stop
            is NeliAiVoiceState.Speaking -> Icons.Default.GraphicEq
            is NeliAiVoiceState.HandsFreeStandby -> Icons.Default.Hearing
            is NeliAiVoiceState.Thinking -> Icons.Default.AutoAwesome
            else -> Icons.Default.Mic
        }
        val iconTint = when (voiceState) {
            is NeliAiVoiceState.Listening -> Color(0xFFFF5286)
            is NeliAiVoiceState.Speaking -> NeliCyan
            is NeliAiVoiceState.HandsFreeStandby -> NeliAccentGreen
            else -> Color.White
        }
        Icon(
            imageVector = icon,
            contentDescription = "Neli AI Voice Control",
            tint = iconTint,
            modifier = Modifier.size(size * 0.34f)
        )
    }
}

/**
 * Compact 7-bar reactive equalizer waveform shown while Neli AI listens or speaks.
 */
@Composable
fun NeliAiVoiceWaveform(
    voiceState: NeliAiVoiceState,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "neli_wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "wave_phase"
    )
    val energy = when (voiceState) {
        is NeliAiVoiceState.Listening -> (0.45f + (voiceState.rmsDb / 12f) * 0.55f).coerceIn(0.35f, 1f)
        is NeliAiVoiceState.Speaking -> 0.72f
        is NeliAiVoiceState.Thinking -> 0.4f
        is NeliAiVoiceState.HandsFreeStandby -> 0.22f
        else -> 0.12f
    }

    Row(
        modifier = modifier.height(26.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colors = listOf(NeliCyan, Color(0xFF4F9DFF), NeliNeonPurple, NeliMagenta, NeliWarmOrange, NeliMagenta, NeliCyan)
        for (i in 0 until 7) {
            val wave = ((sin(phase + i * 0.85f) + 1f) / 2f)
            val barHeight = (5f + (19f * energy * (0.35f + 0.65f * wave))).dp
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(CircleShape)
                    .background(colors[i])
            )
        }
    }
}

/**
 * Compact glowing "Neli AI" pill placed in TopNavBar (Mobile) and NeliTvTopBar (Smart TV).
 */
@Composable
fun NeliAiTopBarTriggerChip(
    isHandsFreeActive: Boolean,
    isListening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "neli_chip_glow")
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "chip_glow"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .tvGeminiFocusable(
                shape = CircleShape,
                focusScale = 1.12f,
                borderWidth = 2.dp,
                onClick = onClick
            )
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF171338),
                        Color(0xFF28103D),
                        Color(0xFF0E2538)
                    )
                )
            )
            .border(
                width = 1.3.dp,
                brush = Brush.horizontalGradient(
                    listOf(
                        NeliCyan.copy(alpha = glowAlpha),
                        NeliMagenta.copy(alpha = glowAlpha),
                        NeliWarmOrange.copy(alpha = glowAlpha)
                    )
                ),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
            .testTag("header_neli_ai_button")
    ) {
        Icon(
            imageVector = if (isListening || isHandsFreeActive) Icons.Default.GraphicEq else Icons.Default.AutoAwesome,
            contentDescription = "Neli AI Assistant",
            tint = if (isHandsFreeActive) NeliAccentGreen else NeliCyan,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (isHandsFreeActive) "Neli AI • LIVE" else "Neli AI",
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp,
            maxLines = 1
        )
    }
}

/**
 * Compact docked Hands-Free / Player overlay pill so users can command Neli AI while watching video.
 */
@Composable
fun NeliAiFloatingHandsFreePill(
    voiceState: NeliAiVoiceState,
    isHandsFreeEnabled: Boolean,
    onTapOrb: () -> Unit,
    onOpenFullSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = when (voiceState) {
        is NeliAiVoiceState.Listening -> voiceState.partialText.ifBlank { "Neli AI anakusikiliza..." }
        is NeliAiVoiceState.Thinking -> "Neli AI anafikiria..."
        is NeliAiVoiceState.Speaking -> "Neli AI anajibu..."
        is NeliAiVoiceState.HandsFreeStandby -> "Hands-Free • Sema amri"
        else -> if (isHandsFreeEnabled) "Hands-Free Tayari" else "Uliza Neli AI"
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xDD0B1024))
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(listOf(NeliCyan, NeliMagenta)),
                shape = CircleShape
            )
            .clickable { onOpenFullSheet() }
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp)
            .testTag("neli_ai_floating_pill"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NeliAiAuroraOrb(
            voiceState = voiceState,
            onClick = onTapOrb,
            size = 34.dp
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Brain Inspector & Custom API Panel inside Neli AI Studio:
 * Displays live data connections (nelitv.online + Firebase), learned memory, alias trainer,
 * and future standalone Neli AI server URL plug-in.
 */
@Composable
fun NeliAiBrainPanel(
    brain: NeliAiBrainSnapshot,
    onTeachAlias: (trigger: String, target: String) -> Unit,
    onRemoveAlias: (trigger: String) -> Unit,
    onSaveRemoteUrl: (url: String) -> Boolean,
    onSaveApiKey: (key: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var triggerInput by remember { mutableStateOf("") }
    var targetInput by remember { mutableStateOf("") }
    var teachStatus by remember { mutableStateOf("") }
    var endpointInput by remember(brain.remoteEndpointUrl) { mutableStateOf(brain.remoteEndpointUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var endpointStatus by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Live Knowledge Sources Overview
        Text(
            text = "Vyanzo vya Data vya Neli AI (Real-Time)",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BrainStatTile(
                icon = Icons.Default.LiveTv,
                accent = NeliCyan,
                value = "${brain.liveChannelsCount}",
                label = "Live TV\n(nelitv.online)",
                modifier = Modifier.weight(1f)
            )
            BrainStatTile(
                icon = Icons.Default.Movie,
                accent = NeliMagenta,
                value = "${brain.moviesCount + brain.seriesCount}",
                label = "Movie & Series\n(Firebase)",
                modifier = Modifier.weight(1f)
            )
            BrainStatTile(
                icon = Icons.Default.Psychology,
                accent = NeliAccentGreen,
                value = "${brain.learnedAliases.size + brain.topWatchedChannels.size}",
                label = "Kumbukumbu\n(Local Brain)",
                modifier = Modifier.weight(1f)
            )
        }

        // 2. Teach Neli AI Custom Alias
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101730))
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.School, contentDescription = null, tint = NeliCyan, modifier = Modifier.size(18.dp))
                Text(
                    text = "Fundisha Neli AI (Custom Shortcut)",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Mfano: Nikisema \"mpira\" -> fungua \"Azam Sports 1\"",
                color = Color(0xFF9FB2D3),
                fontSize = 11.5.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = triggerInput,
                    onValueChange = { triggerInput = it },
                    placeholder = { Text("Neno (mf. mpira)", fontSize = 12.sp, color = Color(0xFF7B8CA8)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("neli_ai_teach_trigger_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeliCyan,
                        unfocusedBorderColor = Color(0xFF283454),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { targetInput = it },
                    placeholder = { Text("Chaneli (mf. Azam Sports 1)", fontSize = 12.sp, color = Color(0xFF7B8CA8)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("neli_ai_teach_target_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeliMagenta,
                        unfocusedBorderColor = Color(0xFF283454),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Button(
                onClick = {
                    if (triggerInput.trim().length >= 2 && targetInput.trim().length >= 2) {
                        onTeachAlias(triggerInput.trim(), targetInput.trim())
                        teachStatus = "Imehifadhiwa: \"${triggerInput.trim()}\" -> ${targetInput.trim()}"
                        triggerInput = ""
                        targetInput = ""
                    } else {
                        teachStatus = "Andika neno na jina la chaneli kwanza."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("neli_ai_save_alias_button")
            ) {
                Text("Hifadhi Kwenye Ubongo wa Neli AI", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            if (teachStatus.isNotBlank()) {
                Text(text = teachStatus, color = NeliAccentGreen, fontSize = 11.5.sp)
            }
            if (brain.learnedAliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                brain.learnedAliases.forEach { (trigger, target) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF182244))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "\"$trigger\"  →  $target",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveAlias(trigger) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete alias",
                                tint = Color(0xFFFF7A9A),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Learned Watch Habits
        if (brain.topWatchedChannels.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF101730))
                    .border(1.dp, Color(0x33FF007A), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Tabia za Utazamaji Alizojifunza",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                brain.topWatchedChannels.forEach { (name, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "• $name", color = Color(0xFFD6E2F5), fontSize = 12.sp)
                        Text(text = "mara $count", color = NeliCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Neli AI Center Backend Connector (POST https://nelitv.online/v1/neli-ai/command)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF101730))
                .border(1.dp, Color(0x3300E676), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = NeliAccentGreen, modifier = Modifier.size(18.dp))
                Text(
                    text = "Neli AI Center API & Voice Profile",
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Voice: Free 10–14 yr Female EN (Locale.US • Pitch 1.28 • Rate 1.04) • Key: ${if (brain.hasApiKeyConfigured) "ACTIVE" else "Set in AI Studio Secrets or below"}",
                color = if (brain.hasApiKeyConfigured) NeliAccentGreen else Color(0xFFFFD54F),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = endpointInput,
                onValueChange = { endpointInput = it },
                placeholder = { Text("https://nelitv.online/v1/neli-ai/command", fontSize = 11.5.sp, color = Color(0xFF7B8CA8)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("neli_ai_remote_url_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeliAccentGreen,
                    unfocusedBorderColor = Color(0xFF283454),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                placeholder = { Text("X-API-Key (hiari kama tayari ipo Secrets)", fontSize = 11.5.sp, color = Color(0xFF7B8CA8)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("neli_ai_api_key_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeliCyan,
                    unfocusedBorderColor = Color(0xFF283454),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Button(
                onClick = {
                    val saved = onSaveRemoteUrl(endpointInput)
                    if (apiKeyInput.isNotBlank()) {
                        onSaveApiKey(apiKeyInput)
                        apiKeyInput = ""
                    }
                    endpointStatus = if (saved) {
                        if (endpointInput.isBlank()) "Imerudi kwenye On-Device Demo Engine."
                        else "Muunganisho wa Neli AI Center umehifadhiwa!"
                    } else {
                        "URL lazima ianze na https:// kwa usalama."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF116446)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("neli_ai_save_url_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hifadhi Neli AI Center API", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            if (endpointStatus.isNotBlank()) {
                Text(
                    text = endpointStatus,
                    color = if (endpointStatus.contains("https")) Color(0xFFFF8A80) else NeliAccentGreen,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun BrainStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111833))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = label,
            color = Color(0xFF9DB0D1),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2
        )
    }
}
