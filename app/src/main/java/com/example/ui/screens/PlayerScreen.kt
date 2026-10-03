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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliBorder
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
    var showCastDialog by remember { mutableStateOf(false) }
    val connectedCastDevice by com.example.player.NeliCastManager.connectedDevice.collectAsState()
    val isCastConnected = connectedCastDevice != null
    val subState by com.example.data.NeliSubscriptionManager.subscriptionState.collectAsState()
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    // Always open every channel or movie in Full Screen Landscape until the user explicitly switches to Portrait mode
    var orientationMode by remember(channel.id) {
        mutableStateOf(PlayerOrientationMode.LOCKED_LANDSCAPE)
    }
    val isPortraitYoutubeMode = !orientationMode.isLandscapeLocked
    var showOrientationStatusBadge by remember { mutableStateOf(false) }
    var orientationBadgeTriggerToken by remember { mutableLongStateOf(0L) }
    val activeExoPlayer = remember(playerController) { playerController.initializePlayer() }

    // Real-time subscription expiry enforcement while watching a locked Live TV channel:
    // As soon as the countdown expires (1 day = 24 hours), user reverts to Free User and locked channel closes.
    LaunchedEffect(activeChannel.id, activeChannel.isLiveBroadcast, subState.isVerified, subState.expiresAtMs) {
        if (activeChannel.isLiveBroadcast) {
            while (true) {
                val now = System.currentTimeMillis()
                if (subState.isVerified && !subState.isFreeForeverAccount && subState.expiresAtMs in 1..now) {
                    com.example.data.NeliSubscriptionManager.expireSubscriptionIfNeeded(context, now)
                }
                val isLockedNow = com.example.data.NeliAdminManager.isChannelLockedForUser(
                    channelId = activeChannel.id,
                    currentUser = null,
                    isPremiumActive = com.example.data.NeliSubscriptionManager.isPremiumMemberActive(now, context),
                    nowMs = now,
                    context = context
                )
                if (isLockedNow) {
                    onBack()
                    break
                }
                delay(1000L)
            }
        }
    }

    LaunchedEffect(channel.id) {
        orientationMode = PlayerOrientationMode.LOCKED_LANDSCAPE
        PlayerGestureHelper.saveAndApplyOrientationMode(
            context,
            activity,
            PlayerOrientationMode.LOCKED_LANDSCAPE
        )
    }

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

    val allLiveChannels by com.example.data.ChannelRepository.liveChannelsFlow.collectAsState()
    val allMediaCatalog by MediaContentRepository.mediaCatalog.collectAsState()
    var selectedPortraitCategory by remember(activeChannel.category) {
        mutableStateOf("All")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isPortraitYoutubeMode) NeliBackground else Color.Black)
            .then(if (isPortraitYoutubeMode) Modifier.statusBarsPadding() else Modifier)
            .testTag("player_screen")
    ) {
        // Video Player Container:
        // - Full-Screen Landscape by default when opening any channel or movie
        // - Responsive 16:9 YouTube-style top player when the user clicks Portrait mode
        Box(
            modifier = if (isPortraitYoutubeMode) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .testTag("player_youtube_video_box")
            } else {
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            }
        ) {
            val effectiveResizeMode = if (isPortraitYoutubeMode && resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            } else {
                resizeMode
            }
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        setKeepContentOnPlayerReset(false)
                        this.resizeMode = effectiveResizeMode
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
                    if (playerView.resizeMode != effectiveResizeMode) {
                        playerView.resizeMode = effectiveResizeMode
                    }
                },
                onRelease = { playerView ->
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            // Always-On Live Stream Freeze / Stall Auto-Fix Watchdog & Real-Time Premium Package Expiry Check
            LaunchedEffect(activeChannel.id, activeChannel.isLiveBroadcast) {
                if (activeChannel.isLiveBroadcast) {
                    if (!activeExoPlayer.isPlaying) {
                        activeExoPlayer.playWhenReady = true
                        activeExoPlayer.play()
                    }
                    while (true) {
                        delay(900L)
                        playerController.checkAndAutoFixLiveStreamStall()
                        com.example.data.NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
                        val isNowLocked = com.example.data.NeliAdminManager.isChannelLockedForUser(
                            channelId = activeChannel.id,
                            currentUser = null,
                            context = context
                        )
                        if (isNowLocked) {
                            playerController.pause()
                            handleExit()
                            break
                        }
                    }
                }
            }

            // Sync active channel to connected Google Cast / Smart TV device
            LaunchedEffect(
                activeChannel.id,
                playbackInfo.activeAudioLanguage,
                connectedCastDevice?.id
            ) {
                if (connectedCastDevice != null) {
                    com.example.player.NeliCastManager.updateCastingChannel(
                        channel = activeChannel,
                        context = context
                    )
                }
            }

            if (showCastDialog) {
                NeliCastModalSheet(
                    availableChannels = com.example.data.ChannelRepository.getPrioritizedAllChannels(),
                    currentChannel = activeChannel,
                    currentUser = null,
                    onDismiss = { showCastDialog = false },
                    onSelectChannelToWatchAndCast = { selectedCh ->
                        playerController.switchChannel(selectedCh)
                    }
                )
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
            androidx.compose.animation.AnimatedVisibility(
                visible = showSwahiliSkipBadge && activeChannel.shouldAutoSkipSwahiliMovieIntro,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xEE10B981))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("swahili_movie_autoskip_badge"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Auto-Skipped to 05:30 • Swahili Intro Skipped",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Overlay Controls (Small & Responsive across all devices, no PiP button, no Live button)
            androidx.compose.animation.AnimatedVisibility(
                visible = areControlsVisible || uiState !is PlayerUiState.Ready,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x660B021A))
                ) {
                    // Top Bar: Back + Title on Left, Small Responsive Scrollable Action Pills on Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xE614052B), Color.Transparent)
                                )
                            )
                            .then(
                                if (isPortraitYoutubeMode) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                            )
                            .padding(
                                horizontal = if (isPortraitYoutubeMode) 10.dp else 14.dp,
                                vertical = if (isPortraitYoutubeMode) 8.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            IconButton(
                                onClick = handleExit,
                                modifier = Modifier
                                    .testTag("player_back_button")
                                    .size(if (isPortraitYoutubeMode) 34.dp else 38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x882B1055))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.widthIn(max = if (isPortraitYoutubeMode) 150.dp else 240.dp)) {
                                Text(
                                    text = activeChannel.name,
                                    color = Color.White,
                                    fontSize = if (isPortraitYoutubeMode) 13.sp else 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isSeriesVod) {
                                        "SERIES • ${playbackInfo.connectionLabel.uppercase()}"
                                    } else if (isOfflineSavedPlayback) {
                                        "OFFLINE • ${activeChannel.category}"
                                    } else {
                                        "${activeChannel.category} • ${playbackInfo.connectionLabel.uppercase()}"
                                    },
                                    color = NeliGenreCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            // In-Player VOD Episode Switcher Button (for Series)
                            if (isSeriesVod) {
                                Box(
                                    modifier = Modifier
                                        .testTag("player_episodes_drawer_button")
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NeliMagenta)
                                        .clickable {
                                            isEpisodeDrawerOpen = !isEpisodeDrawerOpen
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideoLibrary,
                                            contentDescription = "Episodes VOD",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = if (currentEpisodeItem != null) {
                                                "S${currentEpisodeItem.seasonNumber}:E${currentEpisodeItem.episodeNumber}"
                                            } else {
                                                "Episodes (${seriesEpisodes.size})"
                                            },
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            // Small Responsive Auto Quality Control Pill
                            Box(
                                modifier = Modifier
                                    .testTag("quality_mode_button")
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xAA2B1055))
                                    .border(
                                        1.dp,
                                        if (playbackInfo.isDynamicallyDownscaled) NeliGenreCyan else NeliMagenta.copy(alpha = 0.6f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable { playerController.cycleNetworkQualityMode() }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCell,
                                        contentDescription = "Auto Quality Control",
                                        tint = NeliGenreCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    val qualitySummary = if (playbackInfo.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                                        if (playbackInfo.adaptiveQualityTier == com.example.player.AdaptiveQualityTier.LOW_BANDO_240P) {
                                            "Low Data"
                                        } else {
                                            "Auto ${playbackInfo.adaptiveQualityTier.badgeLabel}"
                                        }
                                    } else {
                                        playbackInfo.networkMode.label
                                    }
                                    Text(
                                        text = qualitySummary,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Normal Google Cast Button (Small & Responsive)
                            Box(
                                modifier = Modifier
                                    .testTag("player_cast_button")
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isCastConnected) Color(0xFF065F46) else Color(0xCC122238)
                                    )
                                    .border(
                                        1.dp,
                                        if (isCastConnected) Color(0xFF34D399) else NeliGenreCyan.copy(alpha = 0.7f),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        isEpisodeDrawerOpen = false
                                        isSettingsDrawerOpen = false
                                        isAzamLanguageMenuOpen = false
                                        showCastDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                        contentDescription = if (isCastConnected) "Connected to TV" else "Cast to TV",
                                        tint = if (isCastConnected) Color(0xFF34D399) else NeliGenreCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isCastConnected) "Casting" else "Cast",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Azam TV Language Switcher Button (Small & Responsive)
                            if (activeChannel.isAzamTvChannel) {
                                val activeLangLabel = if (playbackInfo.activeAudioLanguage == "en") {
                                    "ENG"
                                } else {
                                    "KISW"
                                }
                                Box(
                                    modifier = Modifier
                                        .testTag("player_azam_language_button")
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isAzamLanguageMenuOpen) NeliMagenta else Color(0xCC122238)
                                        )
                                        .border(
                                            1.dp,
                                            NeliGenreCyan,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            isEpisodeDrawerOpen = false
                                            isSettingsDrawerOpen = false
                                            isAzamLanguageMenuOpen = !isAzamLanguageMenuOpen
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Badilisha Lugha (Azam TV)",
                                            tint = NeliGenreCyan,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = activeLangLabel,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }

                            // Small Responsive Video Player Settings Button
                            Box(
                                modifier = Modifier
                                    .testTag("player_settings_button")
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSettingsDrawerOpen) NeliMagenta else Color(0xAA2B1055)
                                    )
                                    .border(
                                        1.dp,
                                        if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color(0x66FFFFFF),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        isEpisodeDrawerOpen = false
                                        isSettingsDrawerOpen = !isSettingsDrawerOpen
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Video Player Settings",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Settings",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // Center Area
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val state = uiState) {
                            is PlayerUiState.Loading -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NeliMagenta,
                                        modifier = Modifier.size(if (isPortraitYoutubeMode) 36.dp else 44.dp)
                                    )
                                    Text(
                                        text = if (activeChannel.isLiveBroadcast) {
                                            "Connecting to ${activeChannel.name}..."
                                        } else {
                                            "Loading ${activeChannel.name}..."
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            is PlayerUiState.Buffering -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = NeliMagenta,
                                        modifier = Modifier.size(if (isPortraitYoutubeMode) 34.dp else 40.dp)
                                    )
                                    Text(
                                        text = "Buffering (${playbackInfo.connectionLabel})...",
                                        color = Color.White,
                                        fontSize = 11.sp
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
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0xDD14052B))
                                                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(16.dp))
                                                .padding(14.dp)
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                                Text(
                                                    text = "${activeChannel.name} • Offline Mode",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Text(
                                                    text = "Playing from phone internal storage",
                                                    color = NeliGenreCyan,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(NeliCardPurple)
                                            .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = "Error",
                                                tint = NeliMagenta,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Text(
                                                text = if (state.userFriendlyMessage.isNotBlank()) {
                                                    state.userFriendlyMessage
                                                } else {
                                                    "Kuna shida ya mtandao kwa sasa jaribu tena baadae"
                                                },
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Button(
                                                    onClick = { playerController.retry() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.testTag("retry_stream_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Refresh,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Retry",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = handleExit,
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = "Back",
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            is PlayerUiState.Ready -> {
                                if (areControlsVisible && !activeChannel.isLiveBroadcast) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(if (isPortraitYoutubeMode) 14.dp else 18.dp)
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
                                                    .testTag("previous_episode_button")
                                                    .size(if (isPortraitYoutubeMode) 38.dp else 44.dp)
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
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                triggerDoubleTapSeek(DoubleTapZone.LEFT_REWIND, 0.22f, 0.5f)
                                            },
                                            modifier = Modifier
                                                .testTag("seek_rewind_button")
                                                .size(if (isPortraitYoutubeMode) 40.dp else 46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xAA2B1055))
                                                .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Replay10,
                                                contentDescription = "Rewind 10 seconds",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { playerController.togglePlayPause() },
                                            modifier = Modifier
                                                .testTag("play_pause_button")
                                                .size(if (isPortraitYoutubeMode) 50.dp else 58.dp)
                                                .clip(CircleShape)
                                                .background(NeliMagenta)
                                        ) {
                                            Icon(
                                                imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(if (isPortraitYoutubeMode) 28.dp else 32.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                triggerDoubleTapSeek(DoubleTapZone.RIGHT_FORWARD, 0.78f, 0.5f)
                                            },
                                            modifier = Modifier
                                                .testTag("seek_forward_button")
                                                .size(if (isPortraitYoutubeMode) 40.dp else 46.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xAA2B1055))
                                                .border(1.dp, Color(0x55FFFFFF), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Forward10,
                                                contentDescription = "Forward 10 seconds",
                                                tint = Color.White,
                                                modifier = Modifier.size(22.dp)
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
                                                    .testTag("next_episode_button")
                                                    .size(if (isPortraitYoutubeMode) 38.dp else 44.dp)
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
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Bar (Compact & Responsive across all devices, Live button removed)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xEE14052B))
                                )
                            )
                            .then(
                                if (isPortraitYoutubeMode) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                            )
                            .padding(
                                horizontal = if (isPortraitYoutubeMode) 10.dp else 14.dp,
                                vertical = if (isPortraitYoutubeMode) 6.dp else 10.dp
                            )
                    ) {
                        if (!isPortraitYoutubeMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatDurationMs(displayPositionMs),
                                    color = Color.White,
                                    fontSize = 10.sp,
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
                                        .height(26.dp)
                                        .testTag("vod_timeline_slider")
                                )
                                Text(
                                    text = formatDurationMs(durationMs),
                                    color = NeliTextSecondary,
                                    fontSize = 10.sp,
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
                                // Compact channel info subtitle on left (Live button removed as requested)
                                Text(
                                    text = "${activeChannel.name} • ${playbackInfo.activeVideoResolutionLabel}",
                                    color = NeliGenreCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            } else {
                                // Compact bottom transport bar for Movies, Adult & Series
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    IconButton(
                                        onClick = { playerController.togglePlayPause() },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0x882B1055))
                                    ) {
                                        Icon(
                                            imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (isSeriesVod && !isPortraitYoutubeMode) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xAA2B1055))
                                                .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                .clickable { isEpisodeDrawerOpen = true }
                                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = "All Episodes",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Right: Small & Responsive Mute, Orientation (Landscape <-> Portrait YT), and Fit/Full Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { playerController.toggleMute() },
                                    modifier = Modifier
                                        .testTag("volume_mute_button")
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x882B1055))
                                ) {
                                    Icon(
                                        imageVector = if (playbackInfo.isMuted) Icons.AutoMirrored.Filled.VolumeMute else Icons.AutoMirrored.Filled.VolumeUp,
                                        contentDescription = if (playbackInfo.isMuted) "Unmute" else "Mute",
                                        tint = if (playbackInfo.isMuted) NeliMagenta else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Screen Orientation Lock / Portrait YouTube Toggle Button
                                Box(
                                    modifier = Modifier
                                        .testTag("orientation_lock_toggle_button")
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(
                                            if (orientationMode.isLandscapeLocked) Color(0xCC1B0A3A) else Color(0x882B1055)
                                        )
                                        .border(
                                            1.dp,
                                            if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color(0x55FFFFFF),
                                            RoundedCornerShape(17.dp)
                                        )
                                        .clickable {
                                            val nextMode = if (orientationMode == PlayerOrientationMode.PORTRAIT) {
                                                PlayerOrientationMode.LOCKED_LANDSCAPE
                                            } else {
                                                PlayerOrientationMode.PORTRAIT
                                            }
                                            updateOrientationMode(nextMode)
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = resolveOrientationModeIcon(orientationMode),
                                            contentDescription = "Screen Orientation Lock",
                                            tint = if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isPortraitYoutubeMode) "Full Screen" else "Portrait",
                                            color = if (orientationMode.isLandscapeLocked) NeliGenreCyan else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Small Responsive Aspect Ratio Fit / Full Toggle
                                Box(
                                    modifier = Modifier
                                        .testTag("fullscreen_toggle_button")
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(Color(0x882B1055))
                                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(17.dp))
                                        .clickable {
                                            resizeMode = when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            }
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AspectRatio,
                                            contentDescription = "Screen Fit Mode",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Full"
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
                                                else -> "Fit"
                                            },
                                            color = Color.White,
                                            fontSize = 10.sp,
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
                    .padding(top = 18.dp)
            )

            // Azam TV Language Switcher Floating Card & Notice
            if (activeChannel.isAzamTvChannel && (isAzamLanguageMenuOpen || !playbackInfo.languageSwitchNotice.isNullOrBlank())) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 54.dp, end = 14.dp)
                        .widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xEE101626))
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .testTag("player_azam_language_panel"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Lugha ya Azam TV (Kiswahili / ENG)",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(
                            onClick = {
                                isAzamLanguageMenuOpen = false
                                playerController.clearLanguageSwitchNotice()
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Funga",
                                tint = NeliTextSecondary,
                                modifier = Modifier.size(14.dp)
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
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .testTag("player_lang_option_sw"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSwActive) "Kiswahili ✓" else "Kiswahili",
                                color = Color.White,
                                fontSize = 11.sp,
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
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                                .testTag("player_lang_option_en"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isEnActive) "English ✓" else "English",
                                color = Color.White,
                                fontSize = 11.sp,
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
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("player_language_switch_notice")
                        )
                    }
                }
            }

            // In-Player Video Settings Drawer
            androidx.compose.animation.AnimatedVisibility(
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
            androidx.compose.animation.AnimatedVisibility(
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

        // YouTube-Style Portrait System Below the 16:9 Player (Only visible when user switches to Portrait mode)
        if (isPortraitYoutubeMode) {
            val filteredLiveChannels = remember(allLiveChannels, selectedPortraitCategory) {
                val baseList = allLiveChannels.ifEmpty { com.example.data.ChannelRepository.getPrioritizedAllChannels() }
                if (selectedPortraitCategory == "All") {
                    baseList
                } else {
                    baseList.filter { it.category.equals(selectedPortraitCategory, ignoreCase = true) }
                        .ifEmpty { baseList }
                }
            }
            val relatedMovies = remember(allMediaCatalog, activeChannel.id) {
                allMediaCatalog.filter { !it.isSeries && !it.isAdultContent && it.id != activeChannel.id }.take(18)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(NeliBackground)
                    .testTag("player_youtube_portrait_layout"),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // YouTube-style Title, Metadata & Quick Action Chips Bar
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurface)
                            .border(1.dp, NeliBorder, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (activeChannel.thumbnailUrl.isNotBlank()) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(activeChannel.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = activeChannel.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(NeliSurfaceVariant)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeChannel.name,
                                    color = NeliTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeChannel.category} • ${playbackInfo.activeVideoResolutionLabel} • ${playbackInfo.connectionLabel}",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // YouTube-style Horizontal Action Pills Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Return to Full Landscape Chip
                            Box(
                                modifier = Modifier
                                    .testTag("portrait_return_landscape_button")
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NeliMagenta)
                                    .clickable {
                                        updateOrientationMode(PlayerOrientationMode.LOCKED_LANDSCAPE)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Full Landscape",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            // Normal Cast Chip
                            Box(
                                modifier = Modifier
                                    .testTag("portrait_cast_chip")
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isCastConnected) Color(0xFF065F46) else NeliSurfaceVariant)
                                    .border(1.dp, NeliBorder, RoundedCornerShape(16.dp))
                                    .clickable { showCastDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                                        contentDescription = null,
                                        tint = if (isCastConnected) Color(0xFF34D399) else NeliGenreCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isCastConnected) "Casting to TV" else "Cast to TV",
                                        color = NeliTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Auto Quality Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(NeliSurfaceVariant)
                                    .border(1.dp, NeliBorder, RoundedCornerShape(16.dp))
                                    .clickable { playerController.cycleNetworkQualityMode() }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NetworkCell,
                                        contentDescription = null,
                                        tint = NeliGenreCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = playbackInfo.networkMode.label,
                                        color = NeliTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Azam TV Language Chip
                            if (activeChannel.isAzamTvChannel) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(NeliSurfaceVariant)
                                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                        .clickable {
                                            val nextLang = if (playbackInfo.activeAudioLanguage == "en") "sw" else "en"
                                            playerController.switchAzamAudioLanguage(nextLang)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = NeliGenreCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (playbackInfo.activeAudioLanguage == "en") "Lugha: ENG" else "Lugha: KISW",
                                            color = NeliTextPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Series Episodes Section (if watching a Series)
                if (isSeriesVod && seriesEpisodes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Vipindi vya Series (${seriesEpisodes.size})",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    items(seriesEpisodes, key = { "yt_ep_${it.id}" }) { ep ->
                        val isCurrentEp = ep.id == currentEpisodeItem?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrentEp) NeliCardPurple else NeliSurface)
                                .border(
                                    1.dp,
                                    if (isCurrentEp) NeliMagenta else NeliBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    playerController.switchChannel(ep.toPlayableChannel(seriesTitlePrefix))
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeliSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (ep.stillPath.isNotBlank()) {
                                    SubcomposeAsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(ep.stillPath)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = ep.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "S${ep.seasonNumber}:E${ep.episodeNumber} • ${ep.name}",
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

                // Related Movies Section (if watching a Movie)
                if (!activeChannel.isLiveBroadcast && !isSeriesVod && relatedMovies.isNotEmpty()) {
                    item {
                        Text(
                            text = "Movies Nyingine (More Movies)",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    items(relatedMovies, key = { "yt_mov_${it.id}" }) { movie ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeliSurface)
                                .border(1.dp, NeliBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    playerController.switchChannel(movie.toPlayableChannel())
                                }
                                .padding(10.dp)
                                .testTag("portrait_yt_movie_item_${movie.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(movie.backdropUrl.ifBlank { movie.posterUrl })
                                    .crossfade(true)
                                    .build(),
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(108.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeliSurfaceVariant)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = movie.title,
                                    color = NeliTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${movie.genre} • ${movie.releaseYear} • ${movie.duration}",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Live TV Channels Section (Always available below player like YouTube Up Next feed)
                item {
                    val categories = listOf("All", "Sports", "Entertainment", "News", "Movies", "Music", "Kids", "Religious")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Live TV Channels (${filteredLiveChannels.size})",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedPortraitCategory.equals(cat, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) NeliMagenta else NeliSurfaceVariant)
                                        .border(1.dp, if (isSelected) NeliMagenta else NeliBorder, RoundedCornerShape(14.dp))
                                        .clickable { selectedPortraitCategory = cat }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else NeliTextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                items(filteredLiveChannels, key = { "yt_ch_${it.id}" }) { ch ->
                    val isPlayingThisChannel = ch.id == activeChannel.id
                    val isLockedForUser = ch.isLiveBroadcast && com.example.data.NeliAdminManager.isChannelLockedForUser(
                        channelId = ch.id,
                        currentUser = null,
                        context = context
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlayingThisChannel) NeliCardPurple else NeliSurface)
                            .border(
                                1.dp,
                                if (isPlayingThisChannel) NeliMagenta else NeliBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (!isLockedForUser) {
                                    playerController.switchChannel(ch)
                                }
                            }
                            .padding(10.dp)
                            .testTag("portrait_yt_channel_item_${ch.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(84.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeliSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (ch.thumbnailUrl.isNotBlank()) {
                                SubcomposeAsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(ch.thumbnailUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = ch.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ch.name,
                                color = NeliTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${ch.category} • ${ch.description.ifBlank { "Live HD Broadcast" }}",
                                color = NeliTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = when {
                                isLockedForUser -> "VIP Lock"
                                isPlayingThisChannel -> "Playing ✓"
                                else -> "Watch"
                            },
                            color = when {
                                isLockedForUser -> Color(0xFFFBBF24)
                                isPlayingThisChannel -> Color(0xFF34D399)
                                else -> NeliMagenta
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
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
