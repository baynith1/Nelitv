package com.example.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Full-Screen Neli Flow Dictation & Privacy Voice-to-Text Studio.
 * Combines:
 * - Wispr Flow-style fast AI voice dictation (removes fillers, fixes self-corrections, auto-punctuates, auto-copies across apps)
 * - Superwhisper-style privacy-focused offline on-device Speech-to-Text with local-only wipeable history.
 */
@Composable
fun NeliVoiceDictationScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val controller = remember(context) { NeliVoiceDictationController(context) }
    val state by controller.uiState.collectAsState()

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            controller.startDictation()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "dictation_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state.isRecording) 1.14f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dictation_scale"
    )

    Surface(
        color = Color(0xFF070912),
        modifier = Modifier
            .fillMaxSize()
            .testTag("neli_voice_dictation_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            controller.stopDictation()
                            onBack()
                        },
                        modifier = Modifier.testTag("neli_dictation_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "Neli Flow Dictation",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = if (state.privacyOfflineOnly) {
                                "Private On-Device STT • Auto-Clean AI"
                            } else {
                                "Fast AI Voice-to-Text • Cross-App Ready"
                            },
                            color = if (state.privacyOfflineOnly) Color(0xFF34D399) else Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Language Toggle Chip (English / Swahili)
                Surface(
                    color = Color(0xFF141B30),
                    shape = CircleShape,
                    modifier = Modifier
                        .border(1.dp, Color(0x4438BDF8), CircleShape)
                        .clip(CircleShape)
                        .clickable { controller.toggleLanguage() }
                        .testTag("neli_dictation_lang_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Switch Language",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (state.languageTag == "sw-TZ") "SW" else "EN",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Privacy Offline Mode + Auto-Copy Banner (Superwhisper + Wispr Flow Controls)
                item {
                    Surface(
                        color = Color(0xFF101526),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (state.privacyOfflineOnly) Color(0x5534D399) else Color(0x3338BDF8),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Privacy Mode",
                                        tint = if (state.privacyOfflineOnly) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "On-Device Privacy Mode",
                                            color = Color.White,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Superwhisper-style offline speech-to-text, zero cloud logs.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = state.privacyOfflineOnly,
                                    onCheckedChange = { controller.setPrivacyOfflineOnly(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF10B981)
                                    ),
                                    modifier = Modifier.testTag("neli_dictation_privacy_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Copy Across Apps",
                                        color = Color.White,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Automatically copies cleaned text to clipboard for WhatsApp, Gmail & Notes.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Switch(
                                    checked = state.autoCopyEnabled,
                                    onCheckedChange = { controller.setAutoCopyEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF00D4FF)
                                    ),
                                    modifier = Modifier.testTag("neli_dictation_autocopy_switch")
                                )
                            }
                        }
                    }
                }

                // Style Preset Chips (Wispr Flow Tone Formatting)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DictationStylePreset.entries.forEach { preset ->
                            val selected = state.stylePreset == preset
                            Surface(
                                color = if (selected) Color(0xFF00D4FF) else Color(0xFF141A2E),
                                shape = CircleShape,
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (selected) Color(0xFF00D4FF) else Color(0x33FFFFFF),
                                        CircleShape
                                    )
                                    .clip(CircleShape)
                                    .clickable { controller.setStylePreset(preset) }
                                    .testTag("neli_dictation_style_${preset.name.lowercase()}")
                            ) {
                                Text(
                                    text = preset.label,
                                    color = if (selected) Color.Black else Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Live Metrics Row (WPM + Words + Fillers Cleaned)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DictationMetricChip(
                            label = "SPEED",
                            value = if (state.estimatedWpm > 0) "${state.estimatedWpm} WPM" else "200+ Ready",
                            accent = Color(0xFF00D4FF),
                            modifier = Modifier.weight(1f)
                        )
                        DictationMetricChip(
                            label = "WORDS",
                            value = "${state.wordCount}",
                            accent = Color(0xFFFF2E93),
                            modifier = Modifier.weight(1f)
                        )
                        DictationMetricChip(
                            label = "CLEANED",
                            value = "${state.removedFillersCount} fillers",
                            accent = Color(0xFF34D399),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Live Cleaned Dictation Canvas
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.partialText.isNotBlank()) {
                            Surface(
                                color = Color(0x2200D4FF),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Hearing: ${state.partialText}",
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }

                        OutlinedTextField(
                            value = state.cleanedText,
                            onValueChange = { controller.updateManualText(it) },
                            placeholder = {
                                Text(
                                    text = "Tap the mic below and speak freely. Neli Flow automatically removes \"um / uh\", fixes self-corrections, adds punctuation, and copies clean text for any app.",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.5.sp
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0E1322),
                                unfocusedContainerColor = Color(0xFF0E1322),
                                focusedBorderColor = if (state.isRecording) Color(0xFFFF2E93) else Color(0xFF00D4FF),
                                unfocusedBorderColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 170.dp)
                                .testTag("neli_dictation_text_box")
                        )

                        Text(
                            text = state.statusMessage,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }

                // Action Buttons: AI Clean, Copy, Share Across Apps, Clear
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { controller.copyToClipboard() },
                            enabled = state.cleanedText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("neli_dictation_copy_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = { controller.shareAcrossApps() },
                            enabled = state.cleanedText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E93)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("neli_dictation_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send App", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { controller.applyCleanupNow() },
                            enabled = state.cleanedText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("neli_dictation_clean_btn")
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Clean Text", tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = { controller.clearCurrentText() },
                            enabled = state.cleanedText.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("neli_dictation_clear_btn")
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Text", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Local-Only Privacy History
                if (state.recentHistory.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "LOCAL TRANSCRIPTIONS (DEVICE ONLY)",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Wipe All",
                                color = Color(0xFFF87171),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { controller.wipeLocalHistory() }
                                    .padding(4.dp)
                                    .testTag("neli_dictation_wipe_history_btn")
                            )
                        }
                    }

                    itemsIndexed(state.recentHistory) { idx, itemText ->
                        Surface(
                            color = Color(0xFF101422),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x1EFFFFFF), RoundedCornerShape(14.dp))
                                .clickable { controller.copyToClipboard(itemText) }
                                .testTag("neli_dictation_history_$idx")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = itemText,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy history item",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Pulsing Mic Trigger Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0D18))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (state.isRecording) {
                                    listOf(Color(0xFFFF2E93), Color(0xFFEF4444))
                                } else {
                                    listOf(Color(0xFF00D4FF), Color(0xFF6366F1))
                                }
                            )
                        )
                        .clickable {
                            if (state.isRecording) {
                                controller.stopDictation()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    controller.startDictation()
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                        .testTag("neli_dictation_mic_toggle_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (state.isRecording) "Stop Dictation" else "Start Dictation",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DictationMetricChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF101422),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color(0xFF94A3B8),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = value,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
