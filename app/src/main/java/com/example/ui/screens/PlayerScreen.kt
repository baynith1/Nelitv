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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.key
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
import androidx.media3.common.Player
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

    val isOfflineSavedPlayback = remember(activeChannel.id, activeChannel.streamUrl) {
        activeChannel.id.startsWith("dl_") || activeChannel.streamUrl.startsWith("file:", ignoreCase = true)
    }
    LaunchedEffect(activeChannel.id, activeChannel.streamUrl, activeChannel.name) {
        com.example.data.NeliRealtimeAnalyticsManager.updateCurrentDeviceWatching(context, activeChannel)
        com.example.ads.NeliAdManager.updateAgeRestrictedForKidsContent(
            activeChannel.categories.any { it.contains("kid", ignoreCase = true) }
        )
    }

    DisposableEffect(playerController, ownsController) {
        onDispose {
            com.example.data.NeliRealtimeAnalyticsManager.updateCurrentDeviceWatching(context, null)
            if (ownsController) {
                playerController.release()
            }
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

    // Wire Auto-Next Episode on natural VOD completion (zero 15-min or end-roll ad interruptions)
    LaunchedEffect(isSeriesVod, nextEpisode, seriesTitlePrefix, activeChannel.id) {
        playerController.onEpisodeEndedAutoNext = {
            if (isSeriesVod && nextEpisode != null) {
                nextEpisode.toPlayableChannel(seriesTitlePrefix)
            } else null
        }
        playerController.onVodPlaybackCompletedNaturally = { proceedAfterCompletion ->
            proceedAfterCompletion()
        }
    }

    // Dedicated Embedded Player for Super Sport & EPL League Tv (Clean WebView with Back & PiP button)
    if (activeChannel.isEmbedded) {
        EmbeddedChannelWebViewPlayer(
            channel = activeChannel,
            onBack = onBack,
            onEnterPipMode = onEnterPipMode ?: {}
        )
        return
    }

    var areControlsVisible by remember { mutableStateOf(true) }
    var isEpisodeDrawerOpen by remember { mutableStateOf(false) }
    var isSettingsDrawerOpen by remember { mutableStateOf(false) }
    var isAzamLanguageMenuOpen by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    val connectedCastDevice by com.example.player.NeliCastManager.connectedDevice.collectAsState()
    val isCastConnected = connectedCastDevice != null
    val subState by com.example.data.NeliSubscriptionManager.subscriptionState.collectAsState()
    val lockAllForFree by com.example.data.NeliAdminManager.areAllChannelsLocked.collectAsState()
    val lockAllAzamForFree by com.example.data.NeliAdminManager.areAllAzamChannelsLocked.collectAsState()
    val lockedChannelIds by com.example.data.NeliAdminManager.lockedChannelIds.collectAsState()
    // Default to RESIZE_MODE_FILL so Live TV & Movies fill the whole landscape screen cleanly in 16:9 widescreen
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    // Player is ALWAYS Full-Screen Landscape Cinema Mode for Live TV and Movies
    var orientationMode by remember(channel.id) {
        mutableStateOf(PlayerOrientationMode.LOCKED_LANDSCAPE)
    }
    val isPortraitYoutubeMode = false
    var showOrientationStatusBadge by remember { mutableStateOf(false) }
    var orientationBadgeTriggerToken by remember { mutableLongStateOf(0L) }
    val activeExoPlayer = remember(playerController) { playerController.initializePlayer() }

    // Continue Watching: auto-resume saved position for Movies/Episodes & record playback progress every 5s
    val watchProgressMediaId = remember(activeChannel.id, activeChannel.episodeId, activeChannel.seriesId) {
        activeChannel.episodeId.ifBlank { activeChannel.id.removePrefix("dl_").removePrefix("ep_") }
    }
    var hasAppliedSavedResume by remember(watchProgressMediaId) { mutableStateOf(false) }
    LaunchedEffect(watchProgressMediaId, activeChannel.isLiveBroadcast, playbackInfo.duration) {
        if (!activeChannel.isLiveBroadcast && !hasAppliedSavedResume && playbackInfo.duration > 30_000L) {
            hasAppliedSavedResume = true
            val savedResumeMs = com.example.data.NeliWatchProgressManager.getResumePositionMs(context, watchProgressMediaId)
            if (savedResumeMs > 5_000L && savedResumeMs < playbackInfo.duration - 15_000L) {
                playerController.seekTo(savedResumeMs)
            }
        }
    }
    LaunchedEffect(watchProgressMediaId, activeChannel.isLiveBroadcast, activeChannel.name) {
        if (!activeChannel.isLiveBroadcast) {
            while (true) {
                delay(5_000L)
                val info = playerController.playbackInfo.value
                if (info.duration > 15_000L && info.currentPosition > 5_000L) {
                    com.example.data.NeliWatchProgressManager.recordPlaybackProgress(
                        context = context,
                        id = watchProgressMediaId,
                        title = activeChannel.name,
                        subtitle = activeChannel.description,
                        posterUrl = activeChannel.thumbnailUrl,
                        genre = activeChannel.categories.firstOrNull().orEmpty(),
                        isLiveBroadcast = false,
                        positionMs = info.currentPosition,
                        durationMs = info.duration,
                        streamUrl = activeChannel.streamUrl,
                        seriesId = activeChannel.seriesId,
                        isSeries = isSeriesVod || activeChannel.seriesId.isNotBlank()
                    )
                }
            }
        }
    }

    // Real-time subscription expiry and admin lock enforcement while watching a Live TV channel:
    // As soon as Admin locks this channel or Azam channels, or subscription expires, Free Users are immediately blocked.
    LaunchedEffect(
        activeChannel.id,
        activeChannel.isLiveBroadcast,
        subState.isVerified,
        subState.expiresAtMs,
        lockAllForFree,
        lockAllAzamForFree,
        lockedChannelIds
    ) {
        if (activeChannel.isLiveBroadcast) {
            while (true) {
                val now = System.currentTimeMillis()
                if (subState.isVerified && !subState.isFreeForeverAccount && subState.expiresAtMs in 1..now) {
                    com.example.data.NeliSubscriptionManager.expireSubscriptionIfNeeded(context, now)
                }
                delay(2000L)
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

    val updateOrientationMode: (PlayerOrientationMode) -> Unit = { _ ->
        // Always stay full-screen landscape cinema mode
        orientationMode = PlayerOrientationMode.LOCKED_LANDSCAPE
        PlayerGestureHelper.saveAndApplyOrientationMode(context, activity, PlayerOrientationMode.LOCKED_LANDSCAPE)
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

    // Auto-dismiss Azam TV language switch notification badge after 2 seconds
    LaunchedEffect(playbackInfo.languageSwitchNotice) {
        if (!playbackInfo.languageSwitchNotice.isNullOrBlank()) {
            delay(2000L)
            playerController.clearLanguageSwitchNotice()
        }
    }

    val isTvDevice = com.example.ui.tv.NeliTvDetector.isTelevision(context)
    LaunchedEffect(orientationMode, isTvDevice) {
        if (!isTvDevice) {
            val targetOrientation = orientationMode.activityOrientationConstant
            if (activity != null && activity.requestedOrientation != targetOrientation) {
                view.post {
                    try {
                        if (activity.requestedOrientation != targetOrientation) {
                            activity.requestedOrientation = targetOrientation
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    DisposableEffect(playerController, isTvDevice) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Span whole landscape screen edge-to-edge including camera cutout margins for true full-screen 16:9 cinema
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window?.let { win ->
                val attrs = win.attributes
                if (attrs.layoutInDisplayCutoutMode !=
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                ) {
                    attrs.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    win.attributes = attrs
                }
            }
        }
        if (!isTvDevice && activity != null &&
            activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE &&
            activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ) {
            try {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } catch (_: Exception) {
            }
        }

        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        view.post {
            try {
                insetsController?.hide(WindowInsetsCompat.Type.systemBars())
            } catch (_: Exception) {
            }
        }

        onDispose {
            // Save final Continue Watching checkpoint before leaving PlayerScreen
            if (!activeChannel.isLiveBroadcast) {
                val info = playerController.playbackInfo.value
                if (info.duration > 15_000L && info.currentPosition > 5_000L) {
                    com.example.data.NeliWatchProgressManager.recordPlaybackProgress(
                        context = context,
                        id = watchProgressMediaId,
                        title = activeChannel.name,
                        subtitle = activeChannel.description,
                        posterUrl = activeChannel.thumbnailUrl,
                        genre = activeChannel.categories.firstOrNull().orEmpty(),
                        isLiveBroadcast = false,
                        positionMs = info.currentPosition,
                        durationMs = info.duration,
                        streamUrl = activeChannel.streamUrl,
                        seriesId = activeChannel.seriesId,
                        isSeries = isSeriesVod || activeChannel.seriesId.isNotBlank()
                    )
                }
            }
            if (ownsController) {
                playerController.release()
            }
            window?.let { win ->
                val attrs = win.attributes
                if (attrs.screenBrightness != WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) {
                    attrs.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    win.attributes = attrs
                }
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.decorView?.post {
                try {
                    window.decorView.scrollTo(0, 0)
                    window.decorView.translationY = 0f
                    // Restore status bar & navigation bar when leaving video player back to browse pages
                    insetsController?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                    val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
                    val desiredExitOrientation = when {
                        isTvDevice -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        isTablet -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    if (activity != null && activity.requestedOrientation != desiredExitOrientation) {
                        activity.requestedOrientation = desiredExitOrientation
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    var attachedPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    val isAdCurrentlyDisplaying by com.example.ads.NeliAdManager.isAdCurrentlyDisplaying.collectAsState()

    // Release hardware MediaCodec while a full-screen AdMob video ad is displayed and cleanly re-attach after dismissal
    LaunchedEffect(isAdCurrentlyDisplaying, playerController) {
        if (isAdCurrentlyDisplaying) {
            playerController.pauseForAdBreak()
        } else if (playerController.isAdPausedForBreak()) {
            attachedPlayerView?.let { pv ->
                pv.player = null
                pv.player = activeExoPlayer
            }
            if (activeChannel.isLiveBroadcast) {
                playerController.reconnectLiveEdgeBehind()
            } else {
                playerController.resumeVodFromPreRoll()
            }
        }
    }

    DisposableEffect(lifecycleOwner, playerController, orientationMode) {
        var wasPausedByLifecycle = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Only suspend & release hardware MediaCodec on STOP if Activity is NOT in Picture-in-Picture mode
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
                        playerController.suspendPlaybackAndReleaseCodec()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    val inPip = activity?.isInPictureInPictureMode == true
                    playerController.setBackgroundLoadingState(
                        isBackgroundLoading = false,
                        isPictureInPicture = inPip
                    )
                    if (!inPip && !isTvDevice) {
                        val targetOrientation = orientationMode.activityOrientationConstant
                        if (activity != null && activity.requestedOrientation != targetOrientation) {
                            activity.requestedOrientation = targetOrientation
                        }
                    }
                    if (wasPausedByLifecycle) {
                        wasPausedByLifecycle = false
                        // Re-attach video surface cleanly before resuming after MediaCodec suspension
                        attachedPlayerView?.let { pv ->
                            pv.player = null
                            pv.player = activeExoPlayer
                        }
                        if (!com.example.ads.NeliAdManager.isAdCurrentlyDisplaying.value) {
                            playerController.resumePlaybackAfterCodecSuspend()
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
                    val playerView = try {
                        android.view.LayoutInflater.from(ctx)
                            .inflate(com.example.R.layout.neli_texture_player_view, null, false) as PlayerView
                    } catch (_: Throwable) {
                        PlayerView(ctx)
                    }
                    playerView.apply {
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
                    }.also { pv ->
                        attachedPlayerView = pv
                    }
                },
                update = { playerView ->
                    if (attachedPlayerView !== playerView) {
                        attachedPlayerView = playerView
                    }
                    if (playerView.player != activeExoPlayer) {
                        playerView.player = activeExoPlayer
                    }
                    if (playerView.resizeMode != effectiveResizeMode) {
                        playerView.resizeMode = effectiveResizeMode
                    }
                },
                onRelease = { playerView ->
                    if (attachedPlayerView === playerView) {
                        attachedPlayerView = null
                    }
                    playerView.player = null
                },
                modifier = Modifier.fillMaxSize()
            )

            // Always-On Live Stream Freeze / Stall Auto-Fix Watchdog & Auto-Play on Smart TV / Mobile
            LaunchedEffect(activeChannel.id) {
                if (!activeExoPlayer.isPlaying &&
                    !playerController.isAdPausedForBreak() &&
                    !com.example.ads.NeliAdManager.isAdCurrentlyDisplaying.value
                ) {
                    activeExoPlayer.playWhenReady = true
                    activeExoPlayer.play()
                }
                var watchdogIteration = 0
                while (true) {
                    delay(900L)
                    watchdogIteration++
                    val adBlockingPlayback = playerController.isAdPausedForBreak() ||
                        com.example.ads.NeliAdManager.isAdCurrentlyDisplaying.value
                    // Only recover if player dropped into IDLE or lost playWhenReady unexpectedly (never interrupt active buffering)
                    if (!adBlockingPlayback &&
                        watchdogIteration % 4 == 0 &&
                        !activeExoPlayer.isPlaying &&
                        (!activeExoPlayer.playWhenReady || activeExoPlayer.playbackState == Player.STATE_IDLE)
                    ) {
                        if (!activeExoPlayer.playWhenReady) {
                            activeExoPlayer.playWhenReady = true
                        }
                        if (activeExoPlayer.playbackState == Player.STATE_IDLE) {
                            activeExoPlayer.prepare()
                        }
                        activeExoPlayer.play()
                    }

                    if (!adBlockingPlayback && activeChannel.isLiveBroadcast) {
                        playerController.checkAndAutoFixLiveStreamStall()
                    }
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
                        if (connectedCastDevice != null) {
                            com.example.player.NeliCastManager.startCastingMedia(
                                channel = selectedCh,
                                currentUser = null,
                                context = context
                            )
                            activeExoPlayer.pause()
                        } else {
                            playerController.switchChannel(selectedCh)
                        }
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

                            Column(modifier = Modifier.widthIn(max = if (isPortraitYoutubeMode) 160.dp else 260.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = activeChannel.name,
                                        color = Color.White,
                                        fontSize = if (isPortraitYoutubeMode) 13.sp else 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isSeriesVod) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NeliMagenta)
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "VOD",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isSeriesVod) {
                                        "SERIES VOD • ${playbackInfo.connectionLabel.uppercase()}"
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


                            // Azam TV Language Switcher Button: Instant 1-Tap Toggle between Kiswahili and English
                            if (activeChannel.isAzamTvChannel) {
                                val isEng = playbackInfo.activeAudioLanguage == "en"
                                val activeLangLabel = if (isEng) "ENG" else "KISW"
                                Box(
                                    modifier = Modifier
                                        .testTag("player_azam_language_button")
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isEng) NeliMagenta else Color(0xCC122238)
                                        )
                                        .border(
                                            1.dp,
                                            if (isEng) NeliMagenta else NeliGenreCyan,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            // Instant 1-tap language toggle directly without popup modal
                                            val nextLang = if (isEng) "sw" else "en"
                                            playerController.selectAudioTrackLanguage(nextLang)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = "Badilisha Lugha",
                                            tint = if (isEng) Color.White else NeliGenreCyan,
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
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "16:9 Full"
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

            // Azam TV Language Switcher Floating Toast Badge (Auto-dismissing)
            if (activeChannel.isAzamTvChannel && !playbackInfo.languageSwitchNotice.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 54.dp, end = 14.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD101626))
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("player_language_switch_notice")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = playbackInfo.languageSwitchNotice!!,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
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
                    AspectRatioFrameLayout.RESIZE_MODE_FILL -> "16:9 Full Screen"
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

/**
 * Crash-safe Dedicated Embedded Player for Baynith Sport 1 & 2 and NeliPlay White-Label YouTube Embeds.
 *
 * - For YouTube-backed Studio movies (`isYoutubeEmbedded == true`):
 *   * Loads `https://baynith-tv.base44.app/embed-neli/<VIDEO_ID>` in a strict 16:9 container on pure `#000000`
 *     with zero YouTube branding/watermarks and zero NeliPlay logo overlays over the picture.
 *   * Keeps video title & channel attribution cleanly above the player (never overlapping the video frame).
 *   * Disables Picture-in-Picture completely and auto-pauses/stops playback whenever the user leaves or
 *     backgrounds the app (`ON_PAUSE` / `ON_STOP`).
 * - Handles [WebViewClient.onRenderProcessGone] returning true so Chromium renderer crashes (code -1)
 *   never terminate the host application, supports HTML5 fullscreen custom views, and cleans up safely.
 */
@Composable
fun EmbeddedChannelWebViewPlayer(
    channel: LiveChannel,
    onBack: () -> Unit,
    onEnterPipMode: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) {
        var currentContext: android.content.Context? = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is Activity) return@remember currentContext
            currentContext = currentContext.baseContext
        }
        null
    }

    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isRendererCrashed by remember(channel.id) { mutableStateOf(false) }
    var hasLoadError by remember(channel.id) { mutableStateOf(false) }
    var isPageLoading by remember(channel.id) { mutableStateOf(true) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var customFullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var customViewCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    val isYoutubeEmbed = !channel.isPipAllowedForChannel || channel.isYoutubeEmbedded

    val handleExitFullscreenOrBack: () -> Unit = {
        val activeCallback = customViewCallback
        if (customFullscreenView != null && activeCallback != null) {
            try {
                activeCallback.onCustomViewHidden()
            } catch (_: Throwable) {
            }
            customFullscreenView = null
            customViewCallback = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleExitFullscreenOrBack)

    // Auto-stop / pause embedded playback when backgrounded or leaving screen (especially critical when PiP is disabled for YT videos)
    DisposableEffect(lifecycleOwner, channel.id, isYoutubeEmbed) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            val wv = webViewRef ?: return@LifecycleEventObserver
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    if (isYoutubeEmbed) {
                        try {
                            wv.evaluateJavascript(
                                "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){m.pause();});var f=document.querySelector('iframe');if(f&&f.contentWindow){f.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"pauseVideo\",\"args\":[]}','*');}}catch(e){}})();",
                                null
                            )
                            wv.onPause()
                        } catch (_: Throwable) {
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    if (isYoutubeEmbed) {
                        try {
                            wv.evaluateJavascript(
                                "(function(){try{document.querySelectorAll('video,audio').forEach(function(m){m.pause();});}catch(e){}})();",
                                null
                            )
                            wv.onPause()
                            wv.pauseTimers()
                        } catch (_: Throwable) {
                        }
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try {
                        wv.resumeTimers()
                        wv.onResume()
                    } catch (_: Throwable) {
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

    // Timeout safety so loading spinner never blocks the picture while the remote embed player renders
    LaunchedEffect(channel.id, reloadToken, isPageLoading) {
        if (isPageLoading) {
            delay(3_000L)
            isPageLoading = false
        }
    }

    DisposableEffect(channel.id, reloadToken) {
        val originalOrientation = activity?.requestedOrientation
        activity?.window?.decorView?.post {
            try {
                if (activity.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                val win = activity.window
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
                insetsController.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } catch (_: Throwable) {
            }
        }
        onDispose {
            try {
                customViewCallback?.onCustomViewHidden()
            } catch (_: Throwable) {
            }
            customFullscreenView = null
            customViewCallback = null
            try {
                webViewRef?.let { wv ->
                    wv.stopLoading()
                    wv.onPause()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                }
                webViewRef = null
                activity?.window?.decorView?.post {
                    try {
                        if (originalOrientation != null && activity.requestedOrientation != originalOrientation) {
                            activity.requestedOrientation = originalOrientation
                        }
                        val win = activity.window
                        val insetsController = androidx.core.view.WindowCompat.getInsetsController(win, win.decorView)
                        insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    val resolvedTargetUrl = remember(channel.id, channel.streamUrl, channel.embeddedHtml) {
        val raw = channel.streamUrl.trim()
        if (isYoutubeEmbed) {
            com.example.player.NeliEmbedPlayerHelper.resolveNeliEmbedUrl(streamUrl = raw)
        } else {
            raw
        }
    }

    if (customFullscreenView != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("embedded_player_html5_fullscreen")
        ) {
            AndroidView(
                factory = { customFullscreenView!! },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    // Unified 100% Full-Screen Embedded Player covering the entire screen just like Super Sport & EPL League Tv
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("embedded_player_container")
    ) {
        if (!isRendererCrashed && !hasLoadError) {
            key(channel.id, reloadToken) {
                AndroidView(
                    factory = { ctx ->
                        createConfiguredEmbedWebView(
                            ctx = ctx,
                            channel = channel,
                            resolvedTargetUrl = resolvedTargetUrl,
                            isYoutubeEmbed = isYoutubeEmbed,
                            onWebViewReady = { webViewRef = it },
                            onPageStarted = { isPageLoading = true },
                            onPageFinished = { isPageLoading = false },
                            onLoadError = {
                                isPageLoading = false
                                hasLoadError = true
                            },
                            onRendererCrashed = {
                                webViewRef = null
                                isPageLoading = false
                                isRendererCrashed = true
                            },
                            onShowCustomFullscreen = { view, cb ->
                                customFullscreenView = view
                                customViewCallback = cb
                            },
                            onHideCustomFullscreen = {
                                customFullscreenView = null
                                customViewCallback = null
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isPageLoading) {
                CircularProgressIndicator(
                    color = NeliMagenta,
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.Center)
                )
            }
        } else {
            EmbeddedPlayerRecoveryCard(
                title = if (hasLoadError) "Imeshindwa kupakia video (Angalia intaneti)" else "Player imeanza upya (Renderer Reset)",
                onRetry = {
                    hasLoadError = false
                    isRendererCrashed = false
                    isPageLoading = true
                    reloadToken++
                }
            )
        }

        // Minimal corner Back button (PiP button removed for all embedded channels)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(14.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = handleExitFullscreenOrBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .testTag("embedded_player_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Rudi nyuma",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun createConfiguredEmbedWebView(
    ctx: android.content.Context,
    channel: LiveChannel,
    resolvedTargetUrl: String,
    isYoutubeEmbed: Boolean,
    onWebViewReady: (android.webkit.WebView) -> Unit,
    onPageStarted: () -> Unit,
    onPageFinished: () -> Unit,
    onLoadError: () -> Unit,
    onRendererCrashed: () -> Unit,
    onShowCustomFullscreen: (android.view.View, android.webkit.WebChromeClient.CustomViewCallback) -> Unit,
    onHideCustomFullscreen: () -> Unit
): android.webkit.WebView {
    return android.webkit.WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            allowContentAccess = true
            allowFileAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            // Strip "; wv)" from Android WebView UA so YouTube & embed players treat it as Chrome Mobile and allow autoplay
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
        }
        try {
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        } catch (_: Throwable) {
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(android.webkit.WebView.RENDERER_PRIORITY_IMPORTANT, false)
        }
        webViewClient = object : android.webkit.WebViewClient() {
            override fun onRenderProcessGone(
                view: android.webkit.WebView?,
                detail: android.webkit.RenderProcessGoneDetail?
            ): Boolean {
                android.util.Log.w("NeliTvPlayer", "WebView renderer process gone. Handled safely without crash.")
                try {
                    view?.let { wv ->
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.destroy()
                    }
                } catch (_: Throwable) {
                }
                onRendererCrashed()
                return true
            }

            override fun onPageStarted(
                view: android.webkit.WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                onPageStarted()
            }

            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished()
                if (isYoutubeEmbed && view != null) {
                    try {
                        view.resumeTimers()
                        view.onResume()
                        view.evaluateJavascript(
                            "(function(){try{document.querySelectorAll('video').forEach(function(v){v.muted=false;var p=v.play();if(p&&p.catch){p.catch(function(){v.muted=true;v.play();});}});var f=document.getElementById('neli-player-frame')||document.querySelector('iframe');if(f&&f.contentWindow){f.focus();f.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":[]}','*');}}catch(e){}})();",
                            null
                        )
                    } catch (_: Throwable) {
                    }
                }
            }

            override fun onReceivedError(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    onLoadError()
                }
            }

            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val targetUri = request?.url?.toString().orEmpty()
                if (isYoutubeEmbed && request?.isForMainFrame == true && (
                        targetUri.contains("youtube.com/watch", ignoreCase = true) ||
                        targetUri.contains("youtu.be/", ignoreCase = true) ||
                        targetUri.startsWith("intent://", ignoreCase = true) ||
                        targetUri.startsWith("vnd.youtube:", ignoreCase = true)
                    )
                ) {
                    // Never allow top-level escape to external YouTube app/site
                    return true
                }
                return false
            }
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                return try {
                    android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).apply {
                        eraseColor(android.graphics.Color.BLACK)
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                try {
                    request?.grant(request.resources)
                } catch (_: Throwable) {
                    super.onPermissionRequest(request)
                }
            }

            override fun onShowCustomView(view: android.view.View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    onShowCustomFullscreen(view, callback)
                } else {
                    super.onShowCustomView(view, callback)
                }
            }

            override fun onHideCustomView() {
                onHideCustomFullscreen()
                super.onHideCustomView()
            }
        }
        setBackgroundColor(android.graphics.Color.BLACK)
        onWebViewReady(this)

        if (isYoutubeEmbed) {
            // Frame inside a full-screen 100% viewport HTML wrapper with HTTPS baseUrl so origin/referer & allow="autoplay" work identically to live websites
            val fullScreenHtml = com.example.player.NeliEmbedPlayerHelper.buildFullScreenNeliEmbedHtml(
                resolvedTargetUrl.ifBlank { channel.streamUrl }
            )
            loadDataWithBaseURL(
                "${com.example.player.NeliEmbedPlayerHelper.NELI_EMBED_ORIGIN}/",
                fullScreenHtml,
                "text/html",
                "UTF-8",
                null
            )
        } else if (resolvedTargetUrl.startsWith("http://", ignoreCase = true) ||
            resolvedTargetUrl.startsWith("https://", ignoreCase = true)
        ) {
            loadUrl(resolvedTargetUrl)
        } else {
            val rawHtml = channel.embeddedHtml.trim()
            val htmlContent = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                    <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            background-color: #000000;
                            overflow: hidden;
                        }
                        iframe {
                            width: 100%;
                            height: 100%;
                            border: 0;
                            display: block;
                        }
                    </style>
                </head>
                <body>
                    $rawHtml
                </body>
                </html>
            """.trimIndent()
            loadDataWithBaseURL("${com.example.player.NeliEmbedPlayerHelper.NELI_EMBED_ORIGIN}/", htmlContent, "text/html", "UTF-8", null)
        }
    }
}

@Composable
private fun EmbeddedPlayerRecoveryCard(
    title: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = NeliMagenta,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Try again", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}


