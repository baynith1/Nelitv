package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.MediaContentRepository
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.player.LivePlayerController
import com.example.player.NetworkQualityMode
import com.example.player.PlayerUiState
import com.example.ui.components.BrightnessVolumeGestureOverlay
import com.example.ui.components.DoubleTapSeekFeedback
import com.example.ui.components.DoubleTapSeekOverlay
import com.example.ui.components.DoubleTapZone
import com.example.ui.components.GestureControlFeedback
import com.example.ui.components.GestureControlType
import com.example.ui.components.LiveIndicatorBadge
import com.example.ui.components.OrientationLockStatusBadge
import com.example.ui.components.PlayerGestureHelper
import com.example.ui.components.PlayerGestureQuickBar
import com.example.ui.components.PlayerGestureTouchSurface
import com.example.ui.components.PlayerOrientationMode
import com.example.ui.components.PlayerSettingsDrawer
import com.example.ui.components.resolveOrientationModeIcon
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-Screen Landscape ExoPlayer Screen:
 *
 * - Automatically locks screen orientation to FULL LANDSCAPE (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`)
 *   and hides system bars for cinema playback.
 * - Live TV (`channel.isLiveBroadcast == true`) has NO play/pause button and NO timeline/seekbar;
 *   it plays continuously in real time until the user exits the watch page.
 * - Movies, Adult & Series (`channel.isLiveBroadcast == false`) display Play/Pause, 10s Rewind/Forward
 *   seek buttons, and an interactive timeline Slider powered by ExoPlayer.
 * - Swahili-Narrated Movies (`channel.shouldAutoSkipSwahiliMovieIntro == true`) automatically skip
 *   the first 5m 30s (`05:30`) of DJ intro ads on initial start.
 * - Series includes on-screen Next & Previous Episode buttons, Auto-Next Episode when an episode finishes,
 *   and an In-Player VOD Episode Switcher Drawer so the user can change episodes without leaving the player.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: LiveChannel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    sharedController: LivePlayerController? = null,
    onEnterPipMode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val ownsController = sharedController == null
    val playerController = remember(sharedController, channel.id) {
        sharedController ?: LivePlayerController(context, channel)
    }

    val activeChannel by playerController.currentChannel.collectAsState()
    val uiState by playerController.uiState.collectAsState()
    val playbackInfo by playerController.playbackInfo.collectAsState()

    LaunchedEffect(activeChannel.id, activeChannel.name) {
        com.example.data.NeliRealtimeAnalyticsManager.updateCurrentDeviceWatching(context, activeChannel)
    }

    DisposableEffect(Unit) {
        onDispose {
            com.example.data.NeliRealtimeAnalyticsManager.updateCurrentDeviceWatching(context, null)
        }
    }

    // Resolve Series VOD episode list for In-Player Episode Switcher, Next/Prev buttons, and Auto-Next
    val episodesCatalog by MediaContentRepository.episodesCatalog.collectAsState()
    val seriesEpisodes = remember(activeChannel.id, activeChannel.seriesId, episodesCatalog) {
        val explicitSeriesId = activeChannel.seriesId.ifBlank {
            val cleanEpId = activeChannel.episodeId.ifBlank { activeChannel.id.removePrefix("ep_") }
            episodesCatalog.find { it.id == cleanEpId }?.seriesId.orEmpty()
        }
        if (explicitSeriesId.isNotBlank()) {
            MediaContentRepository.getEpisodesForSeries(explicitSeriesId)
        } else {
            emptyList()
        }
    }

    val currentEpisodeIndex = remember(activeChannel.id, activeChannel.episodeId, seriesEpisodes) {
        if (seriesEpisodes.isEmpty()) -1 else {
            val cleanEpId = activeChannel.episodeId.ifBlank { activeChannel.id.removePrefix("ep_") }
            val idx = seriesEpisodes.indexOfFirst { it.id == cleanEpId }
            if (idx >= 0) idx else 0
        }
    }

    val isSeriesVod = !activeChannel.isLiveBroadcast && seriesEpisodes.isNotEmpty()
    val currentEpisodeItem = if (isSeriesVod && currentEpisodeIndex in seriesEpisodes.indices) {
        seriesEpisodes[currentEpisodeIndex]
    } else null
    val previousEpisode = if (isSeriesVod && currentEpisodeIndex > 0) {
        seriesEpisodes[currentEpisodeIndex - 1]
    } else null
    val nextEpisode = if (isSeriesVod && currentEpisodeIndex >= 0 && currentEpisodeIndex < seriesEpisodes.lastIndex) {
        seriesEpisodes[currentEpisodeIndex + 1]
    } else null

    val seriesTitlePrefix = remember(activeChannel.name, activeChannel.seriesId) {
        MediaContentRepository.getMediaById(activeChannel.seriesId)?.title
            ?: activeChannel.name.substringBefore(" •").trim()
    }

    // Wire Auto-Next Episode when current Series episode finishes
    LaunchedEffect(isSeriesVod, nextEpisode, seriesTitlePrefix) {
        playerController.onEpisodeEndedAutoNext = {
            if (isSeriesVod && nextEpisode != null) {
                nextEpisode.toPlayableChannel(seriesTitlePrefix)
            } else null
        }
    }

    val isOfflineSavedPlayback = remember(activeChannel.id, activeChannel.streamUrl) {
        activeChannel.id.startsWith("dl_") || activeChannel.streamUrl.startsWith("file:", ignoreCase = true)
    }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsDrawerOpen by remember { mutableStateOf(false) }
    var isAzamLanguageMenuOpen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    var orientationMode by remember {
        mutableStateOf(PlayerGestureHelper.readSavedOrientationMode(context))
    }
    var showOrientationStatusBadge by remember { mutableStateOf(false) }
    var orientationBadgeTriggerToken by remember { mutableLongStateOf(0L) }
    val activeExoPlayer = remember(playerController) { playerController.initializePlayer() }

    val updateOrientationMode: (PlayerOrientationMode) -> Unit = { newMode ->
        orientationMode = newMode
        PlayerGestureHelper.saveAndApplyOrientationMode(context, activity, newMode)
        showOrientationStatusBadge = true
        orientationBadgeTriggerToken = System.nanoTime()
    }

    LaunchedEffect(orientationBadgeTriggerToken, showOrientationStatusBadge) {
        if (showOrientationStatusBadge) {
            delay(1800L)
            showOrientationStatusBadge = false
        }
    }

    // Smooth timeline scrubbing state for Movies, Adult & Series
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    // Custom Mobile Gesture States: Double-Tap 10s Seek + Vertical Swipe Brightness & Volume
    var brightnessLevel by remember {
        mutableFloatStateOf(PlayerGestureHelper.readInitialScreenBrightness(activity, context))
    }
    var volumeLevel by remember {
        mutableFloatStateOf(PlayerGestureHelper.readInitialAudioVolume(context, playbackInfo.volume))
    }
    var doubleTapSeekFeedback by remember { mutableStateOf<DoubleTapSeekFeedback?>(null) }
    var gestureControlFeedback by remember { mutableStateOf<GestureControlFeedback?>(null) }

    LaunchedEffect(playbackInfo.isMuted, playbackInfo.volume) {
        if (gestureControlFeedback?.isDragging != true) {
            volumeLevel = if (playbackInfo.isMuted) 0f else playbackInfo.volume.coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(doubleTapSeekFeedback?.triggerToken) {
        if (doubleTapSeekFeedback != null) {
            delay(880L)
            doubleTapSeekFeedback = null
        }
    }

    LaunchedEffect(gestureControlFeedback?.triggerToken, gestureControlFeedback?.isDragging) {
        val current = gestureControlFeedback
        if (current != null && !current.isDragging) {
            delay(1150L)
            gestureControlFeedback = null
        }
    }

    val triggerDoubleTapSeek: (DoubleTapZone, Float, Float) -> Unit = { zone, normX, normY ->
        if (activeChannel.isLiveBroadcast) {
            playerController.syncToLiveEdge()
            doubleTapSeekFeedback = DoubleTapSeekFeedback(
                zone = DoubleTapZone.CENTER_TOGGLE,
                cumulativeSeconds = 0,
                tapNormalizedX = normX,
                tapNormalizedY = normY,
                isLiveSyncPulse = true
            )
        } else {
            when (zone) {
                DoubleTapZone.LEFT_REWIND -> {
                    val existing = doubleTapSeekFeedback
                    val nextCumulative = PlayerGestureHelper.computeCumulativeSeekSeconds(
                        existingZone = existing?.zone,
                        existingSeconds = existing?.cumulativeSeconds ?: 0,
                        newZone = DoubleTapZone.LEFT_REWIND
                    )
                    val nextTapCount = if (existing?.zone == DoubleTapZone.LEFT_REWIND) {
                        existing.tapCount + 1
                    } else 1
                    val targetMs = (playbackInfo.currentPosition - PlayerGestureHelper.DEFAULT_SEEK_STEP_MS)
                        .coerceAtLeast(0L)
                    playerController.seekRelative(-PlayerGestureHelper.DEFAULT_SEEK_STEP_MS)
                    doubleTapSeekFeedback = DoubleTapSeekFeedback(
                        zone = DoubleTapZone.LEFT_REWIND,
                        cumulativeSeconds = nextCumulative,
                        tapCount = nextTapCount,
                        tapNormalizedX = normX,
                        tapNormalizedY = normY,
                        targetPositionLabel = formatDurationMs(targetMs)
                    )
                }

                DoubleTapZone.RIGHT_FORWARD -> {
                    val existing = doubleTapSeekFeedback
                    val nextCumulative = PlayerGestureHelper.computeCumulativeSeekSeconds(
                        existingZone = existing?.zone,
                        existingSeconds = existing?.cumulativeSeconds ?: 0,
                        newZone = DoubleTapZone.RIGHT_FORWARD
                    )
                    val nextTapCount = if (existing?.zone == DoubleTapZone.RIGHT_FORWARD) {
                        existing.tapCount + 1
                    } else 1
                    val knownDur = playbackInfo.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                    val rawTargetMs = playbackInfo.currentPosition + PlayerGestureHelper.DEFAULT_SEEK_STEP_MS
                    val targetMs = if (knownDur != null) rawTargetMs.coerceIn(0L, knownDur) else rawTargetMs
                    playerController.seekRelative(PlayerGestureHelper.DEFAULT_SEEK_STEP_MS)
                    doubleTapSeekFeedback = DoubleTapSeekFeedback(
                        zone = DoubleTapZone.RIGHT_FORWARD,
                        cumulativeSeconds = nextCumulative,
                        tapCount = nextTapCount,
                        tapNormalizedX = normX,
                        tapNormalizedY = normY,
                        targetPositionLabel = formatDurationMs(targetMs)
                    )
                }

                DoubleTapZone.CENTER_TOGGLE -> {
                    val willBePlaying = !playbackInfo.isPlaying
                    playerController.togglePlayPause()
                    doubleTapSeekFeedback = DoubleTapSeekFeedback(
                        zone = DoubleTapZone.CENTER_TOGGLE,
                        cumulativeSeconds = 0,
                        tapNormalizedX = normX,
                        tapNormalizedY = normY,
                        isPlayingAfterToggle = willBePlaying
                    )
                }
            }
        }
    }

    val applyBrightnessGestureLevel: (Float, Boolean) -> Unit = { newLevel, isDragging ->
        val clamped = newLevel.coerceIn(0f, 1f)
        brightnessLevel = clamped
        PlayerGestureHelper.applyWindowBrightness(activity, clamped)
        gestureControlFeedback = GestureControlFeedback(
            type = GestureControlType.BRIGHTNESS,
            level = clamped,
            isDragging = isDragging
        )
    }

    val applyVolumeGestureLevel: (Float, Boolean) -> Unit = { newLevel, isDragging ->
        val clamped = newLevel.coerceIn(0f, 1f)
        volumeLevel = clamped
        playerController.setVolume(clamped)
        PlayerGestureHelper.applyDeviceMusicVolume(context, clamped)
        gestureControlFeedback = GestureControlFeedback(
            type = GestureControlType.VOLUME,
            level = clamped,
            isDragging = isDragging
        )
    }

    // Show temporary badge when 5m 30s Swahili Movie intro skip is applied
    var showSwahiliSkipBadge by remember(activeChannel.id) {
        mutableStateOf(activeChannel.shouldAutoSkipSwahiliMovieIntro)
    }
    LaunchedEffect(activeChannel.id, showSwahiliSkipBadge) {
        if (showSwahiliSkipBadge) {
            delay(5500)
            showSwahiliSkipBadge = false
        }
    }

    DisposableEffect(playerController, orientationMode) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = orientationMode.activityOrientationConstant

        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        playerController.initializePlayer()

        onDispose {
            if (ownsController) {
                playerController.release()
            }
            window?.let { win ->
                val attrs = win.attributes
                attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = attrs
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.decorView?.let { decor ->
                decor.scrollTo(0, 0)
                decor.translationY = 0f
            }
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(lifecycleOwner, playerController, orientationMode) {
        var wasPausedByLifecycle = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Only pause on STOP if Activity is NOT in Picture-in-Picture mode
                    if (activity?.isInPictureInPictureMode == true) {
                        playerController.setBackgroundLoadingState(
                            isBackgroundLoading = false,
                            isPictureInPicture = true
                        )
                    } else {
                        wasPausedByLifecycle = true
                        playerController.setBackgroundLoadingState(
                            isBackgroundLoading = true,
                            isPictureInPicture = false
                        )
                        playerController.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    val inPip = activity?.isInPictureInPictureMode == true
                    playerController.setBackgroundLoadingState(
                        isBackgroundLoading = false,
                        isPictureInPicture = inPip
                    )
                    if (!inPip) {
                        activity?.requestedOrientation = orientationMode.activityOrientationConstant
                    }
                    if (wasPausedByLifecycle) {
                        wasPausedByLifecycle = false
                        if (activeChannel.isLiveBroadcast) {
                            playerController.syncToLiveEdge()
                        } else {
                            playerController.play()
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    if (ownsController) {
                        playerController.release()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val handleExit: () -> Unit = {
        when {
            isSettingsDrawerOpen -> isSettingsDrawerOpen = false
            isEpisodeDrawerOpen -> isEpisodeDrawerOpen = false
            else -> {
                if (ownsController) {
                    playerController.release()
                }
                activity?.window?.let { win ->
                    win.decorView.scrollTo(0, 0)
                    win.decorView.translationY = 0f
                    WindowCompat.getInsetsController(win, view).show(WindowInsetsCompat.Type.systemBars())
                }
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                onBack()
            }
        }
    }

    BackHandler(onBack = handleExit)

    LaunchedEffect(
        areControlsVisible,
        playbackInfo.isPlaying,
        uiState,
        isScrubbing,
        isEpisodeDrawerOpen,
        isSettingsDrawerOpen
    ) {
        if (areControlsVisible &&
            !isScrubbing &&
            !isEpisodeDrawerOpen &&
            !isSettingsDrawerOpen &&
            playbackInfo.isPlaying &&
            uiState is PlayerUiState.Ready
        ) {
            delay(4000)
            areControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen")
    ) {
        // Full-Screen Landscape Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(false)
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = activeExoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player != activeExoPlayer) {
                    playerView.player = activeExoPlayer
                }
                if (playerView.resizeMode != resizeMode) {
                    playerView.resizeMode = resizeMode
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Always-On Live Stream Freeze / Stall Auto-Fix Watchdog (runs even when player controls are hidden):
        // Automatically detects and fixes any live stream stall or freeze so the user never has to manually
        // press the bottom "LIVE STREAM • CONTINUOUS REAL-TIME PLAYBACK" button.
        LaunchedEffect(activeChannel.id, activeChannel.isLiveBroadcast) {
            if (activeChannel.isLiveBroadcast) {
                while (true) {
                    delay(900L)
                    playerController.checkAndAutoFixLiveStreamStall()
                }
            }
        }

        // Interactive Gesture Surface:
        // - Single Tap: Toggle overlay controls
        // - Double Tap Left / Right: Custom 10s backward / forward seek with cumulative multi-tap counter
        // - Double Tap Center: Toggle Play/Pause (or sync live edge)
        // - Vertical Drag Left / Right: Brightness (left) and Volume (right) HUD control
        PlayerGestureTouchSurface(
            isLiveBroadcast = activeChannel.isLiveBroadcast,
            activeSeekFeedback = doubleTapSeekFeedback,
            onSingleTapToggleControls = {
                when {
                    isSettingsDrawerOpen -> isSettingsDrawerOpen = false
                    isEpisodeDrawerOpen -> isEpisodeDrawerOpen = false
                    else -> areControlsVisible = !areControlsVisible
                }
            },
            onDoubleTapSeek = { zone, normX, normY ->
                when {
                    isSettingsDrawerOpen -> isSettingsDrawerOpen = false
                    isEpisodeDrawerOpen -> isEpisodeDrawerOpen = false
                    else -> triggerDoubleTapSeek(zone, normX, normY)
                }
            },
            onVerticalGestureStart = { gestureType ->
                val startLvl = if (gestureType == GestureControlType.BRIGHTNESS) {
                    brightnessLevel
                } else {
                    volumeLevel
                }
                gestureControlFeedback = GestureControlFeedback(
                    type = gestureType,
                    level = startLvl,
                    isDragging = true
                )
            },
            onVerticalGestureDelta = { gestureType, dragDeltaPx, containerHeightPx ->
                if (gestureType == GestureControlType.BRIGHTNESS) {
                    val updated = PlayerGestureHelper.computeUpdatedGestureLevel(
                        currentLevel = brightnessLevel,
                        verticalDragDeltaPx = dragDeltaPx,
                        containerHeightPx = containerHeightPx
                    )
                    applyBrightnessGestureLevel(updated, true)
                } else {
                    val updated = PlayerGestureHelper.computeUpdatedGestureLevel(
                        currentLevel = volumeLevel,
                        verticalDragDeltaPx = dragDeltaPx,
                        containerHeightPx = containerHeightPx
                    )
                    applyVolumeGestureLevel(updated, true)
                }
            },
            onVerticalGestureEnd = {
                gestureControlFeedback = gestureControlFeedback?.copy(
                    isDragging = false,
                    triggerToken = System.nanoTime()
                )
            }
        )

        // Swahili Narrated Movie 5:30 Auto-Skip Notification Pill
        AnimatedVisibility(
            visible = showSwahiliSkipBadge && activeChannel.shouldAutoSkipSwahiliMovieIntro,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 78.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE10B981))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
                    .testTag("swahili_movie_autoskip_badge"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Auto-Skipped to 05:30 • Swahili Movie Intro Ads Skipped",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = areControlsVisible || uiState !is PlayerUiState.Ready,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660B021A))
            ) {
                // Top Bar (with safe display cutout padding so camera notch/edge never overlaps controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xE614052B), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = handleExit,
                            modifier = Modifier
                                .testTag("player_back_button")
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x882B1055))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        Column {
                            Text(
                                text = activeChannel.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (isSeriesVod) "SERIES" else activeChannel.category,
                                    color = NeliMagenta,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "•",
                                    color = NeliTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (activeChannel.isLiveBroadcast) {
                                        "LIVE STREAM • ${playbackInfo.connectionLabel.uppercase()}"
                                    } else if (isOfflineSavedPlayback) {
                                        "OFFLINE INTERNAL STORAGE • ${activeChannel.description}"
                                    } else {
                                        "${activeChannel.description.ifBlank { "HD STREAM" }} • ${playbackInfo.connectionLabel.uppercase()}"
                                    },
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // In-Player VOD Episode Switcher Button (for Series)
                        if (isSeriesVod) {
                            Box(
                                modifier = Modifier
                                    .testTag("player_episodes_drawer_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(NeliMagenta)
                                    .clickable {
                                        isEpisodeDrawerOpen = !isEpisodeDrawerOpen
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = "Episodes VOD",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (currentEpisodeItem != null) {
                                            "Episodes (S${currentEpisodeItem.seasonNumber}:E${currentEpisodeItem.episodeNumber})"
                                        } else {
                                            "Episodes (${seriesEpisodes.size})"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        // Outside-App System PiP Mode Button
                        if (onEnterPipMode != null) {
                            Box(
                                modifier = Modifier
                                    .testTag("player_pip_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xCC121520))
                                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                                    .clickable {
                                        onEnterPipMode()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Outside App PiP Mode",
                                        tint = NeliGenreCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PiP",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        // Auto Quality Control according to user internet (Mobile Data & Wi-Fi)
                        Box(
                            modifier = Modifier
                                .testTag("quality_mode_button")
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xAA2B1055))
                                .border(
                                    1.dp,
                                    if (playbackInfo.isDynamicallyDownscaled) NeliGenreCyan else NeliMagenta.copy(alpha = 0.6f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { playerController.cycleNetworkQualityMode() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NetworkCell,
                                    contentDescription = "Auto Quality Control",
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                val qualitySummary = if (playbackInfo.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                                    if (playbackInfo.adaptiveQualityTier == com.example.player.AdaptiveQualityTier.LOW_BANDO_240P) {
                                        "Auto HD → Low Data"
                                    } else {
                                        "Auto Full HD (${playbackInfo.adaptiveQualityTier.badgeLabel})"
                                    }
                                } else {
                                    playbackInfo.networkMode.label
                                }
                                Text(
                                    text = "$qualitySummary • ${playbackInfo.connectionLabel}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (activeChannel.isLiveBroadcast) {
                            LiveIndicatorBadge()
                        }

                        // Azam TV Language Switcher Button (Kiswahili Primary / English)
                        if (activeChannel.isAzamTvChannel) {
                            val activeLangLabel = if (playbackInfo.activeAudioLanguage == "en") {
                                "ENG"
                            } else {
                                "KISW (Primary)"
                            }
                            Box(
                                modifier = Modifier
                                    .testTag("player_azam_language_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isAzamLanguageMenuOpen) NeliMagenta else Color(0xCC122238)
                                    )
                                    .border(
                                        1.dp,
                                        NeliGenreCyan,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        isEpisodeDrawerOpen = false
                                        isSettingsDrawerOpen = false
                                        isAzamLanguageMenuOpen = !isAzamLanguageMenuOpen
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = "Badilisha Lugha (Azam TV)",
                                        tint = NeliGenreCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Lugha: $activeLangLabel",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        // Video Player Settings Button (Screen Orientation Lock, Fit & Audio/Display)
                        Box(
                            modifier = Modifier
                                .testTag("player_settings_button")
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSettingsDrawerOpen) NeliMagenta else Color(0xAA2B1055)
                                )
                                .border(
                                    1.dp,
                                    if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color(0x66FFFFFF),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    isEpisodeDrawerOpen = false
                                    isSettingsDrawerOpen = !isSettingsDrawerOpen
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Video Player Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Settings",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Center Area:
                // - Live TV: NO play/pause or seek buttons! Always continues playing live until user exits.
                // - Movies, Adult & Series: Rewind 10s, Play/Pause, Forward 10s, plus Next/Previous Episode buttons for Series.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = uiState) {
                        is PlayerUiState.Loading -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = NeliMagenta,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    text = if (activeChannel.isLiveBroadcast) {
                                        "Connecting to ${activeChannel.name} Live Stream..."
                                    } else {
                                        "Loading ${activeChannel.name}..."
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        is PlayerUiState.Buffering -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = NeliMagenta,
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = if (playbackInfo.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                                        if (playbackInfo.adaptiveQualityTier == com.example.player.AdaptiveQualityTier.LOW_BANDO_240P) {
                                            "Buffering • Low Data Mode (${playbackInfo.connectionLabel})..."
                                        } else {
                                            "Buffering • Auto Full HD (${playbackInfo.connectionLabel})..."
                                        }
                                    } else {
                                        "Buffering stream (${playbackInfo.connectionLabel})..."
                                    },
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        is PlayerUiState.Error -> {
                            if (isOfflineSavedPlayback) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (activeChannel.thumbnailUrl.isNotBlank()) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(activeChannel.thumbnailUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = activeChannel.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xDD14052B))
                                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(20.dp))
                                            .padding(20.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text(
                                                text = "${activeChannel.name} • Offline Cinema Mode",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                            Text(
                                                text = "Playing from phone internal storage without internet",
                                                color = NeliGenreCyan,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(NeliCardPurple)
                                        .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Error",
                                            tint = NeliMagenta,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Text(
                                            text = state.userFriendlyMessage,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        Text(
                                            text = activeChannel.name,
                                            color = NeliGenreCyan,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        if (!state.technicalDetail.isNullOrBlank()) {
                                            Text(
                                                text = state.technicalDetail,
                                                color = NeliTextSecondary,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 2
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = { playerController.retry() },
                                                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.testTag("retry_stream_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Retry",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = handleExit,
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = "Back",
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        is PlayerUiState.Ready -> {
                            // Strictly ONLY Movies, Adult & Series have Play/Pause, Seek, and Next/Previous Episode buttons.
                            // Live TV has NO pause/play button and plays continuously until exit.
                            if (areControlsVisible && !activeChannel.isLiveBroadcast) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                                ) {
                                    // Previous Episode button for Series
                                    if (isSeriesVod) {
                                        IconButton(
                                            onClick = {
                                                previousEpisode?.let { prev ->
                                                    playerController.switchChannel(prev.toPlayableChannel(seriesTitlePrefix))
                                                }
                                            },
                                            enabled = previousEpisode != null,
                                            modifier = Modifier
                                                .testTag("previous_episode_button")
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (previousEpisode != null) Color(0xAA2B1055) else Color(0x442B1055)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (previousEpisode != null) NeliGenreCyan else Color(0x33FFFFFF),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipPrevious,
                                                contentDescription = "Previous Episode",
                                                tint = if (previousEpisode != null) Color.White else NeliTextSecondary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = {
                                            triggerDoubleTapSeek(DoubleTapZone.LEFT_REWIND, 0.22f, 0.5f)
                                        },
                                        modifier = Modifier
                                            .testTag("seek_rewind_button")
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xAA2B1055))
                                            .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = "Rewind 10 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = { playerController.togglePlayPause() },
                                        modifier = Modifier
                                            .testTag("play_pause_button")
                                            .size(76.dp)
                                            .clip(CircleShape)
                                            .background(NeliMagenta)
                                    ) {
                                        Icon(
                                            imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            triggerDoubleTapSeek(DoubleTapZone.RIGHT_FORWARD, 0.78f, 0.5f)
                                        },
                                        modifier = Modifier
                                            .testTag("seek_forward_button")
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xAA2B1055))
                                            .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forward10,
                                            contentDescription = "Forward 10 seconds",
                                            tint = Color.White,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }

                                    // Next Episode button for Series
                                    if (isSeriesVod) {
                                        IconButton(
                                            onClick = {
                                                nextEpisode?.let { next ->
                                                    playerController.switchChannel(next.toPlayableChannel(seriesTitlePrefix))
                                                }
                                            },
                                            enabled = nextEpisode != null,
                                            modifier = Modifier
                                                .testTag("next_episode_button")
                                                .size(54.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (nextEpisode != null) Color(0xAA2B1055) else Color(0x442B1055)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (nextEpisode != null) NeliGenreCyan else Color(0x33FFFFFF),
                                                    CircleShape
                                                )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SkipNext,
                                                contentDescription = "Next Episode",
                                                tint = if (nextEpisode != null) Color.White else NeliTextSecondary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Bar:
                // - Live TV: NO timeline slider and NO pause button; strictly real-time broadcast indicator
                // - Movie / Adult / Series Episode: Interactive seekbar + time labels + play/pause/seek + Next/Prev & Episodes drawer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xEE14052B))
                            )
                        )
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    // Quick Gesture Status & Interactive Controls Pill (Brightness • Double-Tap ±10s • Volume)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerGestureQuickBar(
                            brightnessLevel = brightnessLevel,
                            volumeLevel = volumeLevel,
                            isLiveBroadcast = activeChannel.isLiveBroadcast,
                            onStepBrightness = { nextBrightness ->
                                applyBrightnessGestureLevel(nextBrightness, false)
                            },
                            onStepVolume = { nextVolume ->
                                applyVolumeGestureLevel(nextVolume, false)
                            },
                            onQuickSeekRelative = { seekZone ->
                                val normX = if (seekZone == DoubleTapZone.LEFT_REWIND) 0.22f else 0.78f
                                triggerDoubleTapSeek(seekZone, normX, 0.5f)
                            }
                        )
                    }

                    if (!activeChannel.isLiveBroadcast) {
                        val hasKnownDuration = playbackInfo.duration != C.TIME_UNSET && playbackInfo.duration > 0L
                        val durationMs = if (hasKnownDuration) {
                            playbackInfo.duration
                        } else {
                            maxOf(playbackInfo.currentPosition + 600_000L, 3_600_000L)
                        }
                        val liveProgress = (playbackInfo.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        val displayFraction = if (isScrubbing) scrubFraction else liveProgress
                        val displayPositionMs = if (isScrubbing) {
                            (scrubFraction * durationMs).toLong()
                        } else {
                            playbackInfo.currentPosition
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("vod_timeline_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatDurationMs(displayPositionMs),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Slider(
                                value = displayFraction,
                                onValueChange = { fraction ->
                                    val clamped = fraction.coerceIn(0f, 1f)
                                    isScrubbing = true
                                    scrubFraction = clamped
                                    playerController.seekTo((clamped * durationMs).toLong())
                                },
                                onValueChangeFinished = {
                                    playerController.seekTo((scrubFraction * durationMs).toLong())
                                    isScrubbing = false
                                },
                                enabled = true,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeliMagenta,
                                    activeTrackColor = NeliMagenta,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("vod_timeline_slider")
                            )
                            Text(
                                text = if (hasKnownDuration) formatDurationMs(durationMs) else formatDurationMs(durationMs),
                                color = NeliTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (activeChannel.isLiveBroadcast) {
                            // Live Real-Time Broadcast Badge with Auto-Fix (No timeline, no pause!)
                            Row(
                                modifier = Modifier
                                    .testTag("live_stream_continuous_playback_button")
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xAA2B1055))
                                    .border(1.dp, NeliMagenta, RoundedCornerShape(20.dp))
                                    .clickable { playerController.syncToLiveEdge("manual_live_edge_button") }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(NeliMagenta)
                                )
                                Icon(
                                    imageVector = Icons.Default.Sensors,
                                    contentDescription = null,
                                    tint = NeliMagenta,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "LIVE STREAM • CONTINUOUS REAL-TIME PLAYBACK (AUTO-FIX)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Bottom transport bar for Movies, Adult & Series
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isSeriesVod) {
                                    IconButton(
                                        onClick = {
                                            previousEpisode?.let { prev ->
                                                playerController.switchChannel(prev.toPlayableChannel(seriesTitlePrefix))
                                            }
                                        },
                                        enabled = previousEpisode != null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x882B1055))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipPrevious,
                                            contentDescription = "Previous Episode",
                                            tint = if (previousEpisode != null) Color.White else NeliTextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { playerController.togglePlayPause() },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x882B1055))
                                ) {
                                    Icon(
                                        imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        triggerDoubleTapSeek(DoubleTapZone.LEFT_REWIND, 0.22f, 0.5f)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x882B1055))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay10,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        triggerDoubleTapSeek(DoubleTapZone.RIGHT_FORWARD, 0.78f, 0.5f)
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x882B1055))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forward10,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White
                                    )
                                }

                                if (isSeriesVod) {
                                    IconButton(
                                        onClick = {
                                            nextEpisode?.let { next ->
                                                playerController.switchChannel(next.toPlayableChannel(seriesTitlePrefix))
                                            }
                                        },
                                        enabled = nextEpisode != null,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x882B1055))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = "Next Episode",
                                            tint = if (nextEpisode != null) Color.White else NeliTextSecondary
                                        )
                                    }

                                    // Quick button in bottom bar to open In-Player Episode Switcher
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xAA2B1055))
                                            .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                            .clickable { isEpisodeDrawerOpen = true }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VideoLibrary,
                                                contentDescription = null,
                                                tint = NeliGenreCyan,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Text(
                                                text = "All Episodes",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = if (isSeriesVod && nextEpisode != null) {
                                        "Auto-Next: S${nextEpisode.seasonNumber}E${nextEpisode.episodeNumber} ${nextEpisode.name}"
                                    } else {
                                        activeChannel.description.ifBlank { "HD Cinema Mode" }
                                    },
                                    color = NeliGenreCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Volume, Screen Orientation Lock Toggle & Full Screen Fill/Fit Ratio Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { playerController.toggleMute() },
                                modifier = Modifier
                                    .testTag("volume_mute_button")
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x882B1055))
                            ) {
                                Icon(
                                    imageVector = if (playbackInfo.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (playbackInfo.isMuted) "Unmute" else "Mute",
                                    tint = if (playbackInfo.isMuted) NeliMagenta else Color.White
                                )
                            }

                            // Screen Orientation Lock / Toggle Button (Forces Landscape to prevent accidental rotation)
                            Box(
                                modifier = Modifier
                                    .testTag("orientation_lock_toggle_button")
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        if (orientationMode.isLandscapeLocked) Color(0xCC1B0A3A) else Color(0x882B1055)
                                    )
                                    .border(
                                        1.dp,
                                        if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color(0x55FFFFFF),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .clickable {
                                        val nextMode = PlayerGestureHelper.toggleLandscapeLock(orientationMode)
                                        updateOrientationMode(nextMode)
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = resolveOrientationModeIcon(orientationMode),
                                        contentDescription = "Screen Orientation Lock",
                                        tint = if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = orientationMode.shortBadgeLabel,
                                        color = if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Aspect Ratio Full Mode / Fit Toggle
                            Box(
                                modifier = Modifier
                                    .testTag("fullscreen_toggle_button")
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0x882B1055))
                                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(24.dp))
                                    .clickable {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Screen Fit Mode",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Full Screen"
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                            else -> "Fit"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Custom Double-Tap 10s Seek Ripple & Arc Overlay
        DoubleTapSeekOverlay(
            feedback = doubleTapSeekFeedback
        )

        // Custom Vertical Swipe Brightness & Volume HUD Overlay
        BrightnessVolumeGestureOverlay(
            feedback = gestureControlFeedback
        )

        // Floating Orientation Lock Status Pill
        OrientationLockStatusBadge(
            visible = showOrientationStatusBadge && gestureControlFeedback == null,
            orientationMode = orientationMode,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        )

        // Azam TV Language Switcher Floating Card & Notice
        if (activeChannel.isAzamTvChannel && (isAzamLanguageMenuOpen || !playbackInfo.languageSwitchNotice.isNullOrBlank())) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 86.dp, end = 24.dp)
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE101626))
                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .testTag("player_azam_language_panel"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Lugha ya Azam TV (Primary: Kiswahili)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = {
                            isAzamLanguageMenuOpen = false
                            playerController.clearLanguageSwitchNotice()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Funga",
                            tint = NeliTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isSwActive = playbackInfo.activeAudioLanguage != "en"
                    val isEnActive = playbackInfo.activeAudioLanguage == "en"

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSwActive) NeliMagenta else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isSwActive) Color(0xFF34D399) else Color(0x44FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                playerController.switchAzamAudioLanguage("sw")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("player_lang_option_sw"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isSwActive) "Kiswahili ✓ (Primary)" else "Kiswahili (Primary)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isEnActive) NeliMagenta else Color(0xFF1E293B))
                            .border(
                                1.dp,
                                if (isEnActive) Color(0xFF34D399) else Color(0x44FFFFFF),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                playerController.switchAzamAudioLanguage("en")
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp)
                            .testTag("player_lang_option_en"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isEnActive) "English ✓" else "English",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (!playbackInfo.languageSwitchNotice.isNullOrBlank()) {
                    Text(
                        text = playbackInfo.languageSwitchNotice!!,
                        color = if (activeChannel.isKiswahiliOnlyProgram && playbackInfo.preferredAudioLanguage == "en") {
                            Color(0xFFFBBF24)
                        } else {
                            Color(0xFF34D399)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("player_language_switch_notice")
                    )
                }
            }
        }

        // In-Player Video Settings Drawer (Screen Orientation Lock, Aspect Ratio, Quality & Audio/Display)
        AnimatedVisibility(
            visible = isSettingsDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val resizeModeLabel = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Full Screen"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                else -> "Fit"
            }
            PlayerSettingsDrawer(
                orientationMode = orientationMode,
                onSelectOrientationMode = { selectedMode ->
                    updateOrientationMode(selectedMode)
                },
                onToggleForceLandscapeLock = { forceLocked ->
                    val nextMode = PlayerGestureHelper.setForceLandscapeLocked(forceLocked)
                    updateOrientationMode(nextMode)
                },
                resizeModeLabel = resizeModeLabel,
                onCycleResizeMode = {
                    resizeMode = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    }
                },
                networkQualityMode = playbackInfo.networkMode,
                onSelectNetworkQualityMode = { mode ->
                    playerController.applyNetworkQualityMode(mode)
                },
                adaptiveQualityBadge = playbackInfo.activeVideoResolutionLabel,
                adaptiveQualityDescription = playbackInfo.adaptiveQualityTier.description,
                estimatedBandwidthKbps = playbackInfo.estimatedBandwidthKbps,
                bufferedDurationMs = playbackInfo.bufferedDurationMs,
                isDynamicallyDownscaled = playbackInfo.isDynamicallyDownscaled,
                batteryOptimizationMode = playbackInfo.batteryOptimizationMode,
                onSelectBatteryOptimizationMode = { mode ->
                    playerController.setBatteryOptimizationMode(mode)
                },
                batteryPowerProfile = playbackInfo.batteryPowerProfile,
                batteryLevelPct = playbackInfo.batteryLevelPct,
                isBatteryCharging = playbackInfo.isBatteryCharging,
                isOsPowerSaveMode = playbackInfo.isOsPowerSaveMode,
                isCpuSavingActive = playbackInfo.isCpuSavingActive,
                activeMaxFrameRate = playbackInfo.activeMaxFrameRate,
                brightnessLevel = brightnessLevel,
                onBrightnessChange = { newBrightness ->
                    applyBrightnessGestureLevel(newBrightness, false)
                },
                volumeLevel = volumeLevel,
                onVolumeChange = { newVolume ->
                    applyVolumeGestureLevel(newVolume, false)
                },
                onClose = { isSettingsDrawerOpen = false }
            )
        }

        // In-Player VOD Episode & Season Selector Overlay Drawer (for Series)
        AnimatedVisibility(
            visible = isSeriesVod && isEpisodeDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            InPlayerEpisodesDrawer(
                seriesTitle = seriesTitlePrefix,
                episodes = seriesEpisodes,
                currentEpisodeId = currentEpisodeItem?.id.orEmpty(),
                onSelectEpisode = { selectedEp ->
                    playerController.switchChannel(selectedEp.toPlayableChannel(seriesTitlePrefix))
                    isEpisodeDrawerOpen = false
                },
                onClose = { isEpisodeDrawerOpen = false }
            )
        }
    }
}

@Composable
private fun InPlayerEpisodesDrawer(
    seriesTitle: String,
    episodes: List<EpisodeItem>,
    currentEpisodeId: String,
    onSelectEpisode: (EpisodeItem) -> Unit,
    onClose: () -> Unit
) {
    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted()
    }
    val initialSeason = remember(episodes, currentEpisodeId) {
        episodes.find { it.id == currentEpisodeId }?.seasonNumber ?: seasons.firstOrNull() ?: 1
    }
    var selectedSeason by remember(initialSeason) { mutableIntStateOf(initialSeason) }

    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNumber == selectedSeason }.ifEmpty { episodes }
    }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(Color(0xF2120426))
            .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume clicks inside drawer */ }
            .padding(16.dp)
            .testTag("in_player_episodes_drawer")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = seriesTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Switch episode directly inside player • Auto-Next Active",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeliCardPurple)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Episodes Drawer",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (seasons.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(seasons) { sNum ->
                        val isSelected = sNum == selectedSeason
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) NeliMagenta else NeliSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) NeliMagenta else Color(0x44A855F7),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { selectedSeason = sNum }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Season $sNum",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(seasonEpisodes, key = { it.id }) { ep ->
                    val isPlayingNow = ep.id == currentEpisodeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlayingNow) NeliCardPurple else NeliSurface)
                            .border(
                                width = 1.dp,
                                color = if (isPlayingNow) NeliMagenta else Color(0x33A855F7),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectEpisode(ep) }
                            .padding(10.dp)
                            .testTag("in_player_episode_item_${ep.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(88.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeliSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ep.stillPath.isNotBlank()) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(ep.stillPath)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = ep.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlayingNow) NeliMagenta else Color(0xAA14052B)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "S${ep.seasonNumber}:E${ep.episodeNumber}",
                                    color = if (isPlayingNow) NeliMagenta else NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (isPlayingNow) {
                                    Text(
                                        text = "• PLAYING",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = ep.name,
                                color = NeliTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = ep.durationLabel,
                                color = NeliTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
