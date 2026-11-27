package com.example.ai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ChannelRepository
import com.example.data.NeliAdminManager
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.player.LivePlayerController
import com.example.ui.NeliViewModel
import com.example.ui.components.BottomNavTab
import com.example.ui.tv.tvGeminiFocusable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Global Production Neli AI Host mounted inside [com.example.NeliApp]:
 * - Inside NeliTv app & Player: NO floating AI ball/pill! Uses the full-perimeter Apple-Intelligence/Siri
 *   Glowing Edge Border (`NeliAiEdgeGlowOverlay`) + clean dark live caption bar (NO typing box!).
 * - In Player (Mobile & Smart TV): Saying "Hey Neli" smoothly grows the player container (`onAiActiveInPlayerChanged(true)`),
 *   auto-mutes player audio for 5 seconds while video keeps playing, and auto-unmutes if no command arrives in 5s!
 * - Tapping "NeliTv AI" at the bottom of the Account Page opens `NeliAiSetupScreen` (permissions, hands-free,
 *   outside-app overlay ball, lock-screen wake, mobile assistant, screen lock, voice cancellation & API settings).
 * - Outside NeliTv app: `NeliAiSystemOverlayService` takes over with the floating 3D Particle Mesh AI Ball.
 */
@Composable
fun NeliAiGlobalHost(
    isOpen: Boolean,
    onSetOpen: (Boolean) -> Unit,
    neliViewModel: NeliViewModel,
    activeChannel: LiveChannel?,
    playerController: LivePlayerController?,
    isSmartTv: Boolean,
    isPremiumActive: Boolean,
    onPlayChannel: (LiveChannel) -> Unit,
    onClosePlayer: () -> Unit,
    onOpenPremium: () -> Unit = {},
    onAiActiveInPlayerChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        NeliAiMemoryStore.initialize(context)
    }

    val liveChannels by neliViewModel.liveChannels.collectAsState()
    val mediaCatalog by neliViewModel.mediaCatalog.collectAsState()
    val downloads by neliViewModel.downloads.collectAsState()
    val currentUser by neliViewModel.currentUser.collectAsState()

    val learnedAliases by NeliAiMemoryStore.learnedAliases.collectAsState()
    val channelPlayCounts by NeliAiMemoryStore.channelPlayCounts.collectAsState()
    val totalInteractions by NeliAiMemoryStore.totalInteractions.collectAsState()
    val isHandsFreeEnabled by NeliAiMemoryStore.isHandsFreeEnabled.collectAsState()
    val isVoiceReplyEnabled by NeliAiMemoryStore.isVoiceReplyEnabled.collectAsState()
    val isOverlayBallEnabled by NeliAiMemoryStore.isOverlayBallEnabled.collectAsState()
    val isLockScreenWakeEnabled by NeliAiMemoryStore.isLockScreenWakeEnabled.collectAsState()
    val isVoiceCancellationEnabled by NeliAiMemoryStore.isVoiceCancellationEnabled.collectAsState()
    val isCaptionsEnabled by NeliAiMemoryStore.isCaptionsEnabled.collectAsState()
    val remoteEndpointUrl by NeliAiMemoryStore.remoteEndpointUrl.collectAsState()
    val customApiKey by NeliAiMemoryStore.customApiKey.collectAsState()

    // In-app / in-player glowing edge overlay state
    var isEdgeGlowVisible by remember { mutableStateOf(false) }
    var activeCaptionText by remember { mutableStateOf("") }
    var isPlayerMutedForAi by remember { mutableStateOf(false) }
    var previousPlayerVolume by remember { mutableStateOf(1f) }
    var suggestedMoviesForPicker by remember { mutableStateOf<List<MediaContent>>(emptyList()) }
    var movieInfoTarget by remember { mutableStateOf<MediaContent?>(null) }
    var lockedChannelPrompt by remember { mutableStateOf<LiveChannel?>(null) }

    fun restorePlayerAudioAfterAi() {
        if (isPlayerMutedForAi) {
            isPlayerMutedForAi = false
            playerController?.setVolume(previousPlayerVolume.coerceAtLeast(0.5f))
        }
        onAiActiveInPlayerChanged(false)
    }

    fun mutePlayerForAiCommandWindow() {
        if (activeChannel != null && playerController != null && !isPlayerMutedForAi) {
            previousPlayerVolume = playerController.playbackInfo.value.volume.takeIf { it > 0.05f } ?: 1f
            playerController.setVolume(0f)
            isPlayerMutedForAi = true
        }
        if (activeChannel != null) {
            onAiActiveInPlayerChanged(true)
        }
        isEdgeGlowVisible = true
    }

    val executeAction: (NeliAiAction) -> Unit = { action ->
        val prioritized = ChannelRepository.getPrioritizedAllChannels(liveChannels)
        when (action) {
            NeliAiAction.None,
            NeliAiAction.WakeGreeting -> Unit

            is NeliAiAction.PlayChannel -> {
                suggestedMoviesForPicker = emptyList()
                movieInfoTarget = null
                lockedChannelPrompt = null
                NeliAiMemoryStore.recordChannelPlayed(context, action.channel.name, action.channel.categories)
                restorePlayerAudioAfterAi()
                onPlayChannel(action.channel)
            }

            is NeliAiAction.LockedChannelNeedsPayment -> {
                suggestedMoviesForPicker = emptyList()
                movieInfoTarget = null
                lockedChannelPrompt = action.channel
                isEdgeGlowVisible = true
            }

            is NeliAiAction.OpenMedia -> {
                suggestedMoviesForPicker = emptyList()
                movieInfoTarget = null
                lockedChannelPrompt = null
                NeliAiMemoryStore.recordMovieWatched(context, action.media.id)
                restorePlayerAudioAfterAi()
                if (action.autoPlay && action.media.streamUrl.isNotBlank() && !action.media.isSeries) {
                    onPlayChannel(action.media.toPlayableChannel())
                } else {
                    if (activeChannel != null) onClosePlayer()
                    neliViewModel.openMediaDetails(action.media.id)
                }
            }

            is NeliAiAction.SuggestMovies -> {
                movieInfoTarget = null
                lockedChannelPrompt = null
                suggestedMoviesForPicker = action.movies
                isEdgeGlowVisible = true
            }

            is NeliAiAction.ShowMovieInfo -> {
                suggestedMoviesForPicker = emptyList()
                lockedChannelPrompt = null
                movieInfoTarget = action.media
                isEdgeGlowVisible = true
            }

            is NeliAiAction.DownloadRandomMovies -> {
                action.movies.forEach { movie ->
                    neliViewModel.addDownload(movie)
                }
            }

            is NeliAiAction.DownloadSpecificMovies -> {
                action.movies.forEach { movie ->
                    neliViewModel.addDownload(movie)
                }
            }

            is NeliAiAction.PlayMoviesInSequence -> {
                suggestedMoviesForPicker = emptyList()
                movieInfoTarget = null
                if (action.movies.isNotEmpty()) {
                    val first = action.movies.first()
                    NeliAiMemoryStore.setSequentialPlayQueue(context, action.movies.drop(1).map { it.id })
                    NeliAiMemoryStore.recordMovieWatched(context, first.id)
                    restorePlayerAudioAfterAi()
                    onPlayChannel(first.toPlayableChannel())
                }
            }

            NeliAiAction.SwitchNextChannel -> {
                if (prioritized.isNotEmpty()) {
                    val idx = prioritized.indexOfFirst { it.id == activeChannel?.id }
                    val next = prioritized[(idx + 1).mod(prioritized.size)]
                    NeliAiMemoryStore.recordChannelPlayed(context, next.name, next.categories)
                    restorePlayerAudioAfterAi()
                    onPlayChannel(next)
                }
            }

            NeliAiAction.SwitchPreviousChannel -> {
                if (prioritized.isNotEmpty()) {
                    val idx = prioritized.indexOfFirst { it.id == activeChannel?.id }
                    val prev = if (idx <= 0) prioritized.last() else prioritized[idx - 1]
                    NeliAiMemoryStore.recordChannelPlayed(context, prev.name, prev.categories)
                    restorePlayerAudioAfterAi()
                    onPlayChannel(prev)
                }
            }

            is NeliAiAction.SetQuality -> {
                playerController?.applyNetworkQualityMode(action.mode)
            }

            is NeliAiAction.AdjustVolume -> {
                if (action.direction == NeliAiVolumeDirection.MUTE) {
                    isPlayerMutedForAi = false
                }
                adjustDeviceOrPlayerVolume(context, playerController, action.direction)
            }

            is NeliAiAction.AdjustBrightness -> {
                adjustActivityBrightness(context, action.direction)
            }

            NeliAiAction.TogglePlayPause -> {
                playerController?.togglePlayPause()
            }

            NeliAiAction.ClosePlayer -> {
                restorePlayerAudioAfterAi()
                isEdgeGlowVisible = false
                onClosePlayer()
            }

            NeliAiAction.LockDeviceScreen -> {
                val locked = NeliDeviceAdminReceiver.lockDeviceNow(context)
                if (!locked) {
                    activeCaptionText = "Enable Screen Lock permission in NeliTv AI Setup."
                    onSetOpen(true)
                }
            }

            NeliAiAction.OpenNeliTvApp -> {
                if (activeChannel != null) onClosePlayer()
                neliViewModel.selectTab(BottomNavTab.HOME)
                onSetOpen(false)
            }

            is NeliAiAction.NavigateTab -> {
                restorePlayerAudioAfterAi()
                if (activeChannel != null) onClosePlayer()
                neliViewModel.selectTab(action.tab)
                isEdgeGlowVisible = false
            }

            is NeliAiAction.FilterLiveCategory -> {
                restorePlayerAudioAfterAi()
                if (activeChannel != null) onClosePlayer()
                neliViewModel.selectTab(BottomNavTab.HOME)
                neliViewModel.selectLiveCategory(action.category)
            }

            is NeliAiAction.OpenSearch -> {
                restorePlayerAudioAfterAi()
                if (activeChannel != null) onClosePlayer()
                neliViewModel.selectTab(BottomNavTab.SEARCH)
            }
        }
    }

    lateinit var submitQuery: (String, String) -> Unit

    val voiceController = remember(context) {
        NeliAiVoiceController(
            context = context,
            onRecognizedQuery = { transcript ->
                submitQuery(transcript, "voice")
            },
            onWakeWordDetected = { chained ->
                mutePlayerForAiCommandWindow()
                activeCaptionText = chained ?: "I'm listening..."
            },
            onCommandWindowExpired = {
                // 5 seconds elapsed with no command -> auto-unmute player & continue watching smoothly!
                restorePlayerAudioAfterAi()
                if (suggestedMoviesForPicker.isEmpty() && movieInfoTarget == null && lockedChannelPrompt == null) {
                    isEdgeGlowVisible = false
                }
            },
            onSpeechFinished = {
                restorePlayerAudioAfterAi()
                scope.launch {
                    delay(2_800L)
                    if (suggestedMoviesForPicker.isEmpty() && movieInfoTarget == null && lockedChannelPrompt == null) {
                        isEdgeGlowVisible = false
                    }
                }
            }
        )
    }

    submitQuery = { rawText, inputMode ->
        val clean = rawText.trim()
        if (clean.isNotBlank()) {
            activeCaptionText = clean
            isEdgeGlowVisible = true
            voiceController.setThinking()
            scope.launch {
                val downloadedIds = downloads.map { it.id }.toSet()
                val snapshot = NeliAiContextSnapshot(
                    liveChannels = ChannelRepository.getPrioritizedAllChannels(liveChannels),
                    mediaCatalog = mediaCatalog,
                    activePlayerChannel = activeChannel,
                    isSmartTv = isSmartTv,
                    isPremiumActive = isPremiumActive,
                    userName = currentUser?.realName.orEmpty(),
                    hourOfDay24 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    isOnline = isDeviceOnline(context),
                    isOutsideApp = false,
                    downloadedMediaIds = downloadedIds,
                    watchedWithin30DaysIds = NeliAiMemoryStore.getWatchedMovieIdsWithin30Days(),
                    isChannelLockedCheck = { ch ->
                        NeliAdminManager.isChannelLockedForUser(
                            channelId = ch.id,
                            channelObj = ch,
                            currentUser = currentUser,
                            isPremiumActive = isPremiumActive,
                            context = context
                        )
                    }
                )
                val response = NeliAiEngine.processCommand(
                    rawQuery = clean,
                    snapshot = snapshot,
                    context = context,
                    inputMode = inputMode
                )
                activeCaptionText = response.replyText
                val shouldListenAfter = response.action is NeliAiAction.WakeGreeting
                executeAction(response.action)
                voiceController.speak(response.replySpeech, listenForFollowUpAfter = shouldListenAfter)
            }
        }
    }

    // Keep passive hands-free wake listening active inside NeliTv (including inside PlayerScreen!)
    LaunchedEffect(isHandsFreeEnabled, isVoiceCancellationEnabled, isVoiceReplyEnabled) {
        voiceController.voiceReplyEnabled = isVoiceReplyEnabled
        voiceController.voiceCancellationEnabled = isVoiceCancellationEnabled
        voiceController.handsFreeActive = isHandsFreeEnabled
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (isHandsFreeEnabled && hasMic) {
            voiceController.startPassiveWakeListening()
        } else if (!isHandsFreeEnabled) {
            voiceController.stopListening()
        }
    }

    DisposableEffect(voiceController) {
        onDispose {
            restorePlayerAudioAfterAi()
            voiceController.release()
        }
    }

    val voiceState by voiceController.voiceState.collectAsState()

    val brainSnapshot = remember(
        liveChannels.size,
        mediaCatalog.size,
        learnedAliases,
        channelPlayCounts,
        totalInteractions,
        remoteEndpointUrl,
        customApiKey,
        isHandsFreeEnabled,
        isVoiceReplyEnabled,
        isOverlayBallEnabled,
        isLockScreenWakeEnabled,
        isVoiceCancellationEnabled,
        isCaptionsEnabled
    ) {
        NeliAiBrainSnapshot(
            liveChannelsCount = liveChannels.size,
            moviesCount = mediaCatalog.count { !it.isAdult && it.isMovie },
            seriesCount = mediaCatalog.count { !it.isAdult && it.isSeries },
            learnedAliases = learnedAliases,
            topWatchedChannels = channelPlayCounts.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value },
            totalInteractions = totalInteractions,
            remoteEndpointUrl = remoteEndpointUrl,
            hasApiKeyConfigured = NeliAiMemoryStore.resolveApiKey().isNotBlank(),
            isHandsFreeEnabled = isHandsFreeEnabled,
            isVoiceReplyEnabled = isVoiceReplyEnabled,
            isOverlayBallEnabled = isOverlayBallEnabled,
            isLockScreenWakeEnabled = isLockScreenWakeEnabled,
            isVoiceCancellationEnabled = isVoiceCancellationEnabled,
            isCaptionsEnabled = isCaptionsEnabled
        )
    }

    // 1. Inside NeliTv App & Player: Full-Perimeter Glowing Edge Border + Caption & No-Tick Movie Picker
    val showEdgeEffect = !isOpen && (
        isEdgeGlowVisible ||
            voiceState is NeliAiVoiceState.Listening ||
            voiceState is NeliAiVoiceState.Thinking ||
            voiceState is NeliAiVoiceState.Speaking ||
            suggestedMoviesForPicker.isNotEmpty() ||
            movieInfoTarget != null ||
            lockedChannelPrompt != null
        )

    NeliAiEdgeGlowOverlay(
        visible = showEdgeEffect,
        voiceState = voiceState,
        captionText = activeCaptionText,
        isPlayerMutedForAi = isPlayerMutedForAi,
        showCaptions = isCaptionsEnabled,
        suggestedMovies = suggestedMoviesForPicker,
        movieInfoTarget = movieInfoTarget,
        lockedChannelPrompt = lockedChannelPrompt,
        onTapMic = {
            if (voiceState is NeliAiVoiceState.Listening) {
                voiceController.stopListening()
                restorePlayerAudioAfterAi()
            } else {
                mutePlayerForAiCommandWindow()
                voiceController.startActiveCommandWindow(NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
            }
        },
        onDismiss = {
            voiceController.stopListening()
            voiceController.stopSpeaking()
            suggestedMoviesForPicker = emptyList()
            movieInfoTarget = null
            lockedChannelPrompt = null
            isEdgeGlowVisible = false
            restorePlayerAudioAfterAi()
        },
        onDownloadSelectedMovies = { moviesToDownload ->
            executeAction(NeliAiAction.DownloadSpecificMovies(moviesToDownload))
            suggestedMoviesForPicker = emptyList()
            movieInfoTarget = null
            activeCaptionText = "Downloading ${moviesToDownload.size} movies in the background!"
            voiceController.speak("Downloading ${moviesToDownload.size} movies for you now.")
        },
        onPlayMoviesInSequence = { moviesToPlay ->
            executeAction(NeliAiAction.PlayMoviesInSequence(moviesToPlay))
        },
        onOpenMovieNow = { movie ->
            executeAction(NeliAiAction.OpenMedia(movie, autoPlay = !movie.isSeries))
        },
        onUnlockVipClick = {
            lockedChannelPrompt = null
            isEdgeGlowVisible = false
            restorePlayerAudioAfterAi()
            onOpenPremium()
        }
    )

    // 1B. Smart TV In-App Floating Neli AI Orb (Shown ONLY on Smart TV inside NeliTv; Mobile stays edge-glow only!)
    if (isSmartTv && !isOpen) {
        val isTvOrbActive = showEdgeEffect ||
            voiceState is NeliAiVoiceState.Listening ||
            voiceState is NeliAiVoiceState.Thinking ||
            voiceState is NeliAiVoiceState.Speaking
        val tvOrbAlpha = if (activeChannel != null && !isTvOrbActive) 0.55f else 1f
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 24.dp)
                    .alpha(tvOrbAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .tvGeminiFocusable(
                            shape = CircleShape,
                            focusScale = 1.12f,
                            borderWidth = 2.5.dp,
                            onClick = {
                                if (voiceState is NeliAiVoiceState.Listening) {
                                    voiceController.stopListening()
                                    restorePlayerAudioAfterAi()
                                } else {
                                    mutePlayerForAiCommandWindow()
                                    activeCaptionText = "Neli AI is listening... Say any channel, movie, volume, or quality command."
                                    voiceController.startActiveCommandWindow(NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
                                }
                            }
                        )
                        .testTag("tv_in_app_neli_ai_orb")
                ) {
                    NeliAiParticleMeshOrb(
                        voiceState = voiceState,
                        size = 66.dp,
                        onClick = null
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xC80D111C))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    val label = when (voiceState) {
                        is NeliAiVoiceState.Listening -> "Listening..."
                        is NeliAiVoiceState.Thinking -> "Thinking..."
                        is NeliAiVoiceState.Speaking -> "Neli AI"
                        else -> "Hey Neli"
                    }
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // 2. Full-Screen NeliTv AI Setup & Permissions Page (opened from the very bottom of Account Page)
    if (isOpen) {
        NeliAiSetupScreen(
            voiceState = voiceState,
            brain = brainSnapshot,
            onBack = { onSetOpen(false) },
            onTestVoiceNow = {
                onSetOpen(false)
                mutePlayerForAiCommandWindow()
                activeCaptionText = "I'm listening... try \"Suggest me movies\" or \"Open Azam Sports 1\""
                voiceController.startActiveCommandWindow(NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
            },
            onToggleHandsFree = { enabled ->
                NeliAiMemoryStore.setHandsFreeEnabled(context, enabled)
                voiceController.handsFreeActive = enabled
                if (enabled) {
                    voiceController.startPassiveWakeListening()
                } else {
                    voiceController.stopListening()
                }
            },
            onToggleOverlayBall = { enabled ->
                NeliAiMemoryStore.setOverlayBallEnabled(context, enabled)
                if (enabled) {
                    NeliAiSystemOverlayService.start(context)
                } else {
                    NeliAiSystemOverlayService.stop(context)
                }
            },
            onToggleLockScreenWake = { enabled ->
                NeliAiMemoryStore.setLockScreenWakeEnabled(context, enabled)
                if (enabled) {
                    NeliAiSystemOverlayService.start(context)
                }
            },
            onToggleVoiceCancellation = { enabled ->
                NeliAiMemoryStore.setVoiceCancellationEnabled(context, enabled)
                voiceController.voiceCancellationEnabled = enabled
            },
            onToggleCaptions = { enabled ->
                NeliAiMemoryStore.setCaptionsEnabled(context, enabled)
            },
            onToggleVoiceReply = { enabled ->
                NeliAiMemoryStore.setVoiceReplyEnabled(context, enabled)
                if (!enabled) voiceController.stopSpeaking()
            },
            onSaveRemoteUrl = { url ->
                NeliAiMemoryStore.setRemoteEndpointUrl(context, url)
            },
            onSaveApiKey = { key ->
                NeliAiMemoryStore.setCustomApiKey(context, key)
            }
        )
    }
}

private fun isDeviceOnline(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}

private fun adjustActivityBrightness(
    context: Context,
    direction: NeliAiBrightnessDirection
) {
    try {
        val activity = context as? Activity ?: return
        val attrs = activity.window.attributes
        val current = if (attrs.screenBrightness < 0f) 0.55f else attrs.screenBrightness
        attrs.screenBrightness = when (direction) {
            NeliAiBrightnessDirection.UP -> (current + 0.22f).coerceIn(0.15f, 1.0f)
            NeliAiBrightnessDirection.DOWN -> (current - 0.22f).coerceIn(0.08f, 1.0f)
            NeliAiBrightnessDirection.MAX -> 1.0f
            NeliAiBrightnessDirection.LOW -> 0.12f
            NeliAiBrightnessDirection.AUTO -> android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        activity.window.attributes = attrs
    } catch (_: Exception) {
    }
}

private fun adjustDeviceOrPlayerVolume(
    context: Context,
    playerController: LivePlayerController?,
    direction: NeliAiVolumeDirection
) {
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        when (direction) {
            NeliAiVolumeDirection.UP -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    AudioManager.FLAG_SHOW_UI
                )
                playerController?.setVolume(1f)
            }
            NeliAiVolumeDirection.DOWN -> {
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            NeliAiVolumeDirection.MUTE -> {
                playerController?.setVolume(0f)
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            NeliAiVolumeDirection.UNMUTE -> {
                playerController?.setVolume(1f)
                audioManager?.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
        }
    } catch (_: Exception) {
    }
}
