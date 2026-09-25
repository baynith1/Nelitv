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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Sensors
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.LiveChannel
import com.example.player.LivePlayerController
import com.example.player.PlayerUiState
import com.example.ui.components.LiveIndicatorBadge
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Full-Screen Landscape Player Screen.
 *
 * - Automatically locks screen orientation to FULL LANDSCAPE (`SCREEN_ORIENTATION_SENSOR_LANDSCAPE`)
 *   and hides system bars for true full-mode playback.
 * - Live TV (`channel.isLiveBroadcast == true`) has NO play/pause button and NO timeline/seekbar;
 *   it plays continuously in real time until the user exits the watch page, and returning always
 *   continues at the live stream edge without rewinding.
 * - Movies & Series Episodes (`channel.isLiveBroadcast == false`) display Play/Pause, 10s Rewind/Forward
 *   seek buttons, and a responsive interactive timeline Slider.
 * - Supports Auto Quality Control according to user internet over both Mobile Data and Wi-Fi.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: LiveChannel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val playerController = remember(channel.id) {
        LivePlayerController(context, channel)
    }

    val uiState by playerController.uiState.collectAsState()
    val playbackInfo by playerController.playbackInfo.collectAsState()

    var areControlsVisible by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
    var activeExoPlayer by remember(channel.id) { mutableStateOf<ExoPlayer?>(null) }

    // Smooth timeline scrubbing state for Movies & Series
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    DisposableEffect(channel.id) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val insetsController = window?.let { WindowCompat.getInsetsController(it, view) }
        insetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController?.hide(WindowInsetsCompat.Type.systemBars())

        activeExoPlayer = playerController.initializePlayer()

        onDispose {
            activeExoPlayer = null
            playerController.release()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(lifecycleOwner, channel.id) {
        var wasPausedByLifecycle = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    wasPausedByLifecycle = true
                    playerController.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    if (wasPausedByLifecycle) {
                        wasPausedByLifecycle = false
                        if (channel.isLiveBroadcast) {
                            // Returning to Live TV always resumes at the live stream edge, never rewinding back
                            playerController.syncToLiveEdge()
                        } else {
                            playerController.play()
                        }
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    playerController.release()
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
        activeExoPlayer = null
        playerController.release()
        activity?.window?.let { win ->
            WindowCompat.getInsetsController(win, view).show(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onBack()
    }

    BackHandler(onBack = handleExit)

    LaunchedEffect(areControlsVisible, playbackInfo.isPlaying, uiState, isScrubbing) {
        if (areControlsVisible && !isScrubbing && playbackInfo.isPlaying && uiState is PlayerUiState.Ready) {
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

        // Tap surface to toggle overlay controls
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    areControlsVisible = !areControlsVisible
                }
        )

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
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xE614052B), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
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
                                text = channel.name,
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
                                    text = channel.category,
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
                                    text = if (channel.isLiveBroadcast) {
                                        "LIVE STREAM • ${playbackInfo.connectionLabel.uppercase()}"
                                    } else {
                                        "${channel.description.ifBlank { "HD STREAM" }} • ${playbackInfo.connectionLabel.uppercase()}"
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
                        // Auto Quality Control according to user internet (Mobile Data & Wi-Fi)
                        Box(
                            modifier = Modifier
                                .testTag("quality_mode_button")
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xAA2B1055))
                                .border(1.dp, NeliMagenta.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
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
                                Text(
                                    text = "${playbackInfo.networkMode.label} • ${playbackInfo.connectionLabel}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (channel.isLiveBroadcast) {
                            LiveIndicatorBadge()
                        }
                    }
                }

                // Center Area:
                // - Live TV: NO play/pause or seek buttons! Always continues playing live until user exits.
                // - Movies & Series: Rewind 10s, Play/Pause, and Forward 10s buttons.
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
                                    text = if (channel.isLiveBroadcast) {
                                        "Connecting to ${channel.name} Live Stream..."
                                    } else {
                                        "Loading ${channel.name}..."
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
                                    text = "Auto-adjusting quality for your ${playbackInfo.connectionLabel} connection...",
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        is PlayerUiState.Error -> {
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
                                        text = channel.name,
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

                        is PlayerUiState.Ready -> {
                            // Strictly ONLY Movies & Series have Play/Pause and Seek buttons.
                            // Live TV has NO pause/play button and plays continuously until exit.
                            if (areControlsVisible && !channel.isLiveBroadcast) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                                ) {
                                    IconButton(
                                        onClick = { playerController.seekRelative(-10_000L) },
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
                                        onClick = { playerController.seekRelative(10_000L) },
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
                                }
                            }
                        }
                    }
                }

                // Bottom Bar:
                // - Live TV: NO timeline slider and NO pause button; strictly real-time broadcast indicator
                // - VOD Movie / Series Episode: Interactive seekbar + time labels + quick play/pause/seek controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xEE14052B))
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (!channel.isLiveBroadcast) {
                        val hasKnownDuration = playbackInfo.duration != C.TIME_UNSET && playbackInfo.duration > 0L
                        val durationMs = if (hasKnownDuration) playbackInfo.duration else 1L
                        val liveProgress = if (hasKnownDuration) {
                            (playbackInfo.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val displayFraction = if (isScrubbing) scrubFraction else liveProgress
                        val displayPositionMs = if (isScrubbing && hasKnownDuration) {
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
                                    isScrubbing = true
                                    scrubFraction = fraction.coerceIn(0f, 1f)
                                },
                                onValueChangeFinished = {
                                    if (hasKnownDuration) {
                                        playerController.seekTo((scrubFraction * durationMs).toLong())
                                    }
                                    isScrubbing = false
                                },
                                enabled = hasKnownDuration,
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
                                text = if (hasKnownDuration) formatDurationMs(durationMs) else "--:--",
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
                        if (channel.isLiveBroadcast) {
                            // Live Real-Time Broadcast Badge (No timeline, no pause!)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xAA2B1055))
                                    .border(1.dp, NeliMagenta, RoundedCornerShape(20.dp))
                                    .clickable { playerController.syncToLiveEdge() }
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
                                    text = "LIVE STREAM • CONTINUOUS REAL-TIME PLAYBACK",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            // Bottom transport bar for Movies & Series
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
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
                                    onClick = { playerController.seekRelative(-10_000L) },
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
                                    onClick = { playerController.seekRelative(10_000L) },
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
                                Text(
                                    text = channel.description.ifBlank { "HD Cinema Mode" },
                                    color = NeliGenreCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Right: Volume & Full Screen Fill/Fit Ratio Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
