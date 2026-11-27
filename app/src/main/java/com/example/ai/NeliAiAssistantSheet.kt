package com.example.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ui.theme.NeliAccentGreen
import com.example.ui.theme.NeliCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliNeonPurple
import com.example.ui.theme.NeliWarmOrange
import com.example.ui.tv.tvGeminiFocusable

/**
 * Full Neli AI Hands-Free Assistant Studio Overlay.
 * Supports Touch (Mobile/Tablet) and D-Pad Remote (Smart TV), live voice recognition,
 * TTS replies, interactive channel/movie action cards, and the Brain & API Inspector.
 */
@Composable
fun NeliAiAssistantOverlay(
    visible: Boolean,
    messages: List<NeliAiMessage>,
    voiceState: NeliAiVoiceState,
    brain: NeliAiBrainSnapshot,
    isSmartTv: Boolean,
    onDismiss: () -> Unit,
    onSendCommand: (String) -> Unit,
    onStartOrStopListening: () -> Unit,
    onToggleHandsFree: (Boolean) -> Unit,
    onToggleVoiceReply: (Boolean) -> Unit,
    onCardSelected: (NeliAiCardItem) -> Unit,
    onTeachAlias: (String, String) -> Unit,
    onRemoveAlias: (String) -> Unit,
    onSaveRemoteUrl: (String) -> Boolean,
    onSaveApiKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showBrainTab by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    var pendingHandsFreeEnable by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            if (pendingHandsFreeEnable) {
                pendingHandsFreeEnable = false
                onToggleHandsFree(true)
            }
            onStartOrStopListening()
        } else {
            pendingHandsFreeEnable = false
        }
    }

    val requestMicAndRun: (enableHandsFree: Boolean) -> Unit = { enableHf ->
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            if (enableHf) onToggleHandsFree(true)
            onStartOrStopListening()
        } else {
            pendingHandsFreeEnable = enableHf
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(messages.size, visible, showBrainTab) {
        if (visible && !showBrainTab && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 })
    ) {
        BackHandler(enabled = visible) {
            onDismiss()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC04060F))
                .clickable(onClick = onDismiss)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .testTag("neli_ai_overlay_backdrop"),
            contentAlignment = if (isSmartTv) Alignment.Center else Alignment.BottomCenter
        ) {
            Surface(
                color = Color(0xFF080C1C),
                shape = if (isSmartTv) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (isSmartTv) 760.dp else 640.dp)
                    .fillMaxHeight(if (isSmartTv) 0.90f else 0.88f)
                    .clickable(enabled = false) {}
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                NeliCyan.copy(alpha = 0.6f),
                                NeliMagenta.copy(alpha = 0.45f),
                                Color(0x22FFFFFF)
                            )
                        ),
                        shape = if (isSmartTv) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .testTag("neli_ai_studio_sheet")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Gradient Header Bar
                    NeliAiHeaderBar(
                        showBrainTab = showBrainTab,
                        isHandsFreeEnabled = brain.isHandsFreeEnabled,
                        isVoiceReplyEnabled = brain.isVoiceReplyEnabled,
                        onSelectBrainTab = { showBrainTab = it },
                        onToggleHandsFree = {
                            if (brain.isHandsFreeEnabled) {
                                onToggleHandsFree(false)
                            } else {
                                requestMicAndRun(true)
                            }
                        },
                        onToggleVoiceReply = { onToggleVoiceReply(!brain.isVoiceReplyEnabled) },
                        onDismiss = onDismiss
                    )

                    if (showBrainTab) {
                        NeliAiBrainPanel(
                            brain = brain,
                            onTeachAlias = onTeachAlias,
                            onRemoveAlias = onRemoveAlias,
                            onSaveRemoteUrl = onSaveRemoteUrl,
                            onSaveApiKey = onSaveApiKey,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Living Voice Aurora Stage
                        NeliAiVoiceStage(
                            voiceState = voiceState,
                            isHandsFreeEnabled = brain.isHandsFreeEnabled,
                            liveChannelsCount = brain.liveChannelsCount,
                            mediaCount = brain.moviesCount + brain.seriesCount,
                            onOrbClick = { requestMicAndRun(false) }
                        )

                        // Conversation Feed with Interactive Media Cards
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("neli_ai_messages_list"),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                NeliAiMessageBubble(
                                    message = msg,
                                    onCardClick = onCardSelected,
                                    onQuickReplyClick = { onSendCommand(it) }
                                )
                            }
                        }

                        // Instant Demo Voice-Command Shortcuts
                        val demoPrompts = remember {
                            listOf(
                                "Weka Azam Sports 1",
                                "Nipendekezee cha kuangalia",
                                "Sasa hivi kuna kipindi gani?",
                                "Movie za Kiswahili",
                                "Channel za katuni",
                                "Je nelitv.online ina movie?",
                                "Weka 480p",
                                "Fundisha mpira = Azam Sports 1"
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(demoPrompts) { prompt ->
                                Box(
                                    modifier = Modifier
                                        .tvGeminiFocusable(
                                            shape = CircleShape,
                                            focusScale = 1.07f,
                                            borderWidth = 1.5.dp,
                                            onClick = { onSendCommand(prompt) }
                                        )
                                        .clip(CircleShape)
                                        .background(Color(0xFF131B36))
                                        .border(1.dp, Color(0x4400E5FF), CircleShape)
                                        .clickable { onSendCommand(prompt) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = prompt,
                                        color = Color(0xFFDCE9FF),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Bottom Command Input + Voice Mic Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A0F24))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                placeholder = {
                                    Text(
                                        text = "Sema au andika amri kwa Neli AI...",
                                        color = Color(0xFF7D90B2),
                                        fontSize = 13.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("neli_ai_command_input"),
                                shape = RoundedCornerShape(22.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeliCyan,
                                    unfocusedBorderColor = Color(0xFF253256),
                                    focusedContainerColor = Color(0xFF111833),
                                    unfocusedContainerColor = Color(0xFF0E142B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = NeliCyan
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (textInput.isNotBlank()) {
                                            val q = textInput
                                            textInput = ""
                                            onSendCommand(q)
                                        }
                                    }
                                )
                            )

                            val sendActive = textInput.isNotBlank()
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            if (sendActive) listOf(NeliMagenta, NeliWarmOrange)
                                            else listOf(NeliCyan, NeliNeonPurple)
                                        )
                                    )
                                    .clickable {
                                        if (sendActive) {
                                            val q = textInput
                                            textInput = ""
                                            onSendCommand(q)
                                        } else {
                                            requestMicAndRun(false)
                                        }
                                    }
                                    .testTag("neli_ai_send_or_mic_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (sendActive) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                                    contentDescription = if (sendActive) "Tuma amri" else "Ongea na Neli AI",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NeliAiHeaderBar(
    showBrainTab: Boolean,
    isHandsFreeEnabled: Boolean,
    isVoiceReplyEnabled: Boolean,
    onSelectBrainTab: (Boolean) -> Unit,
    onToggleHandsFree: () -> Unit,
    onToggleVoiceReply: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF12122E), Color(0xFF1A1030), Color(0xFF0D1B30))
                )
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NeliCyan, NeliMagenta))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Neli AI",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x3300E5FF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DEMO • ZERO API COST",
                                color = NeliCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Text(
                        text = "Hands-Free • Live TV + Firebase Brain",
                        color = Color(0xFF9FB4D6),
                        fontSize = 11.sp
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleVoiceReply,
                    modifier = Modifier.testTag("neli_ai_tts_toggle")
                ) {
                    Icon(
                        imageVector = if (isVoiceReplyEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = if (isVoiceReplyEnabled) "Zima sauti ya Neli AI" else "Washa sauti ya Neli AI",
                        tint = if (isVoiceReplyEnabled) NeliCyan else Color(0xFF6B7C9C)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("neli_ai_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Funga Neli AI",
                        tint = Color.White
                    )
                }
            }
        }

        // Mode switcher: Chat/Voice vs Ubongo & API + Hands-Free Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderModePill(
                label = "Sauti & Amri",
                icon = Icons.Default.Mic,
                selected = !showBrainTab,
                accent = NeliCyan,
                onClick = { onSelectBrainTab(false) },
                modifier = Modifier.testTag("neli_ai_tab_voice")
            )
            HeaderModePill(
                label = "Ubongo & API",
                icon = Icons.Default.Psychology,
                selected = showBrainTab,
                accent = NeliMagenta,
                onClick = { onSelectBrainTab(true) },
                modifier = Modifier.testTag("neli_ai_tab_brain")
            )
            Spacer(modifier = Modifier.weight(1f))
            HeaderModePill(
                label = if (isHandsFreeEnabled) "Hands-Free ON" else "Hands-Free",
                icon = Icons.Default.Hearing,
                selected = isHandsFreeEnabled,
                accent = NeliAccentGreen,
                onClick = onToggleHandsFree,
                modifier = Modifier.testTag("neli_ai_hands_free_toggle")
            )
        }
    }
}

@Composable
private fun HeaderModePill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .tvGeminiFocusable(
                shape = CircleShape,
                focusScale = 1.06f,
                borderWidth = 1.5.dp,
                onClick = onClick
            )
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.22f) else Color(0xFF131932))
            .border(
                width = 1.dp,
                color = if (selected) accent else Color(0xFF283354),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else Color(0xFF94A7C7), modifier = Modifier.size(14.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFAABBD6),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun NeliAiVoiceStage(
    voiceState: NeliAiVoiceState,
    isHandsFreeEnabled: Boolean,
    liveChannelsCount: Int,
    mediaCount: Int,
    onOrbClick: () -> Unit
) {
    val statusHeadline = when (voiceState) {
        is NeliAiVoiceState.Listening -> voiceState.partialText.ifBlank { "Nakusikiliza... Sema amri yako!" }
        is NeliAiVoiceState.Thinking -> "Neli AI anachambua data zako..."
        is NeliAiVoiceState.Speaking -> "Neli AI anazungumza..."
        is NeliAiVoiceState.HandsFreeStandby -> "Hands-Free ipo tayari — ongea tu!"
        is NeliAiVoiceState.Notice -> voiceState.message
        NeliAiVoiceState.Idle -> if (isHandsFreeEnabled) {
            "Hands-Free imewashwa • Gusa Orb kuanza"
        } else {
            "Gusa Neli Orb useme \"Weka Azam Sports 1\""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0C122B), Color(0xFF080C1C))
                )
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        NeliAiAuroraOrb(
            voiceState = voiceState,
            onClick = onOrbClick,
            size = 74.dp
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = statusHeadline,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            NeliAiVoiceWaveform(voiceState = voiceState)
            Text(
                text = "Imeunganishwa: $liveChannelsCount Live TV • $mediaCount Movie & Series",
                color = Color(0xFF7D93B8),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun NeliAiMessageBubble(
    message: NeliAiMessage,
    onCardClick: (NeliAiCardItem) -> Unit,
    onQuickReplyClick: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.isUser) 18.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 18.dp
                    )
                )
                .background(
                    if (message.isUser) {
                        Brush.horizontalGradient(listOf(Color(0xFF9C0A56), Color(0xFFD62564)))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFF131B38), Color(0xFF181C3E)))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (message.isUser) Color(0x55FF6EA8) else Color(0x3300E5FF),
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.isUser) 18.dp else 4.dp,
                        bottomEnd = if (message.isUser) 4.dp else 18.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!message.isUser && message.sourceTag.isNotBlank()) {
                Text(
                    text = "${message.sourceTag} • ${(message.confidence * 100).toInt()}%",
                    color = NeliCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.3.sp
                )
            }
            Text(
                text = message.text,
                color = Color.White,
                fontSize = 13.5.sp,
                lineHeight = 19.sp
            )
        }

        // Interactive Channel & Movie Cards returned by Neli AI
        if (message.cards.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 6.dp)
            ) {
                items(message.cards, key = { it.id }) { card ->
                    NeliAiActionMediaCard(
                        card = card,
                        onClick = { onCardClick(card) }
                    )
                }
            }
        }

        // Contextual follow-up quick replies
        if (message.quickReplies.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(message.quickReplies) { reply ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF172042))
                            .border(1.dp, Color(0x44FF007A), CircleShape)
                            .clickable { onQuickReplyClick(reply) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = reply,
                            color = Color(0xFFFFB8D6),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeliAiActionMediaCard(
    card: NeliAiCardItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .tvGeminiFocusable(
                shape = RoundedCornerShape(14.dp),
                focusScale = 1.06f,
                borderWidth = 2.dp,
                onClick = onClick
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF111834))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("neli_ai_card_${card.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(Color(0xFF0A0F24))
        ) {
            if (card.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (card is NeliAiCardItem.ChannelCard) Color(0xDDFF0055) else Color(0xDD8B00FF))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = card.badge,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC00E5FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Cheza ${card.title}",
                    tint = Color(0xFF050814),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = card.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = card.subtitle,
                color = Color(0xFF9AB0D4),
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
