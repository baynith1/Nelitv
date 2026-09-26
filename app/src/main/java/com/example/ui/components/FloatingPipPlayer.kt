package com.example.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.MediaContentRepository
import com.example.model.LiveChannel
import com.example.player.LivePlayerController
import com.example.player.PlayerUiState
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Floating Picture-in-Picture (PiP) Player Overlay:
 * - Can be dragged smoothly to all 4 corners of the phone screen (or any position).
 * - Holding any corner for a moment ("akishika kwenye conner kwa mda") or dragging a corner
 *   increases the PiP screen size (`230.dp` -> `280.dp` -> `325.dp` -> `365.dp`).
 * - Content-specific controls:
 *   - Live TV: `X` Cancel button + `Expand to Full Watching` button next to it (NO play/pause, NO timeline).
 *   - Movie & Adult: `X` Cancel button + `Expand` button + `Play/Pause` + `Seek (-10s / +10s)` + `Timeline`.
 *   - Series: `X` Cancel button + `Expand` button + `Play/Pause` + `Previous & Next Episode` + `Timeline`.
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingPipPlayerOverlay(
    playerController: LivePlayerController,
    onExpandToFullScreen: () -> Unit,
    onClosePip: () -> Unit,
    modifier: Modifier = Modifier,
    isSystemPipMode: Boolean = false
) {
    val activeChannel by playerController.currentChannel.collectAsState()
    val uiState by playerController.uiState.collectAsState()
    val playbackInfo by playerController.playbackInfo.collectAsState()
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

    // Wire Auto-Next Episode when watching a Series in PiP mode
    LaunchedEffect(isSeriesVod, nextEpisode, seriesTitlePrefix) {
        playerController.onEpisodeEndedAutoNext = {
            if (isSeriesVod && nextEpisode != null) {
                nextEpisode.toPlayableChannel(seriesTitlePrefix)
            } else null
        }
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("floating_pip_container")
    ) {
        val maxScreenWidthDp = maxWidth
        val maxScreenHeightDp = maxHeight

        val minPipWidthDp = 220f
        val maxPipWidthDp = (maxScreenWidthDp.value - 20f).coerceAtLeast(260f)

        var pipWidthDp by remember { mutableFloatStateOf(255f.coerceAtMost(maxPipWidthDp)) }
        val pipHeightDp = (pipWidthDp * 9f / 16f).coerceAtLeast(142f)

        val maxOffsetX = with(density) {
            (maxScreenWidthDp - pipWidthDp.dp - 12.dp).toPx().coerceAtLeast(0f)
        }
        val maxOffsetY = with(density) {
            (maxScreenHeightDp - pipHeightDp.dp - 96.dp).toPx().coerceAtLeast(0f)
        }

        // Default position: Bottom-Right corner
        var offsetX by remember { mutableFloatStateOf(maxOffsetX) }
        var offsetY by remember { mutableFloatStateOf(maxOffsetY) }
        var hasInitializedCorner by remember { mutableStateOf(false) }

        LaunchedEffect(maxOffsetX, maxOffsetY) {
            if (!hasInitializedCorner && maxOffsetX > 0f && maxOffsetY > 0f) {
                offsetX = maxOffsetX
                offsetY = maxOffsetY
                hasInitializedCorner = true
            } else {
                offsetX = offsetX.coerceIn(8f, maxOffsetX.coerceAtLeast(8f))
                offsetY = offsetY.coerceIn(48f, maxOffsetY.coerceAtLeast(48f))
            }
        }

        var showControls by remember { mutableStateOf(true) }
        var resizeFeedbackText by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(resizeFeedbackText) {
            if (resizeFeedbackText != null) {
                delay(1800)
                resizeFeedbackText = null
            }
        }

        // Holding any corner enlarges the PiP window step-by-step
        val enlargePipOnCornerHold: () -> Unit = {
            val stepSizes = listOf(
                225f,
                270f.coerceAtMost(maxPipWidthDp),
                315f.coerceAtMost(maxPipWidthDp),
                maxPipWidthDp
            ).distinct()
            val nextSize = stepSizes.firstOrNull { it > pipWidthDp + 10f } ?: stepSizes.first()
            pipWidthDp = nextSize
            val pct = ((nextSize / 225f) * 100).roundToInt()
            resizeFeedbackText = "PiP Size: $pct%"
            showControls = true
        }

        // Snap near corners helper when user finishes dragging
        val snapToNearestEdgeOrCorner: () -> Unit = {
            val midX = maxOffsetX / 2f
            val topSnapThreshold = with(density) { 140.dp.toPx() }
            val bottomSnapThreshold = (maxOffsetY - with(density) { 120.dp.toPx() }).coerceAtLeast(topSnapThreshold)

            offsetX = if (offsetX < midX) {
                with(density) { 12.dp.toPx() }
            } else {
                maxOffsetX
            }
            offsetY = when {
                offsetY < topSnapThreshold -> with(density) { 56.dp.toPx() }
                offsetY > bottomSnapThreshold -> maxOffsetY
                else -> offsetY.coerceIn(with(density) { 56.dp.toPx() }, maxOffsetY)
            }
        }

        val windowBoxModifier = if (isSystemPipMode) {
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
                .testTag("floating_pip_window")
        } else {
            Modifier
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = offsetY.roundToInt()
                    )
                }
                .width(pipWidthDp.dp)
                .height(pipHeightDp.dp)
                .shadow(16.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black)
                .border(1.5.dp, NeliMagenta.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                .pointerInput(maxOffsetX, maxOffsetY) {
                    detectDragGestures(
                        onDragEnd = { snapToNearestEdgeOrCorner() }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(
                            0f,
                            (maxOffsetX + 12f).coerceAtLeast(0f)
                        )
                        offsetY = (offsetY + dragAmount.y).coerceIn(
                            24f,
                            (maxOffsetY + 72f).coerceAtLeast(24f)
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showControls = !showControls
                }
                .testTag("floating_pip_window")
        }

        Box(
            modifier = windowBoxModifier
        ) {
            PipPlayerSurfaceAndControls(
                playerController = playerController,
                activeChannel = activeChannel,
                uiState = uiState,
                playbackInfo = playbackInfo,
                isSeriesVod = isSeriesVod,
                previousEpisode = previousEpisode,
                nextEpisode = nextEpisode,
                seriesTitlePrefix = seriesTitlePrefix,
                showControls = showControls,
                resizeFeedbackText = resizeFeedbackText,
                onExpandToFullScreen = onExpandToFullScreen,
                onClosePip = onClosePip
            )

            // 4 Interactive Corner Handles:
            // Holding any corner ("akishika kwenye conner kwa mda") or dragging a corner enlarges/resizes the PiP screen!
            val cornerHandleModifier = Modifier
                .size(26.dp)
                .pointerInput(maxPipWidthDp) {
                    detectTapGestures(
                        onLongPress = { enlargePipOnCornerHold() },
                        onDoubleTap = { enlargePipOnCornerHold() }
                    )
                }
                .pointerInput(maxPipWidthDp) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val deltaDp = with(density) { (dragAmount.x + dragAmount.y * 0.8f).toDp().value }
                        pipWidthDp = (pipWidthDp + deltaDp).coerceIn(minPipWidthDp, maxPipWidthDp)
                        val pct = ((pipWidthDp / minPipWidthDp) * 100).roundToInt()
                        resizeFeedbackText = "PiP Size: $pct%"
                    }
                }

            // Top-Left Corner Resize Zone
            Box(
                modifier = cornerHandleModifier
                    .align(Alignment.TopStart)
                    .testTag("pip_corner_top_start")
            )
            // Top-Right Corner Resize Zone
            Box(
                modifier = cornerHandleModifier
                    .align(Alignment.TopEnd)
                    .testTag("pip_corner_top_end")
            )
            // Bottom-Left Corner Resize Zone
            Box(
                modifier = cornerHandleModifier
                    .align(Alignment.BottomStart)
                    .testTag("pip_corner_bottom_start")
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(10.dp)
                        .border(1.5.dp, NeliGenreCyan.copy(alpha = 0.8f), RoundedCornerShape(bottomStart = 6.dp))
                )
            }
            // Bottom-Right Corner Resize Zone
            Box(
                modifier = cornerHandleModifier
                    .align(Alignment.BottomEnd)
                    .testTag("pip_corner_resize_handle")
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(10.dp)
                        .border(1.5.dp, NeliGenreCyan.copy(alpha = 0.8f), RoundedCornerShape(bottomEnd = 6.dp))
                )
            }
        }
    }
}

/**
 * Used when the Activity itself is inside Android's system-level Picture-in-Picture window
 * (when the user presses the Home button to watch over the home screen or inside other apps).
 */
@OptIn(UnstableApi::class)
@Composable
fun SystemPipPlayerView(
    playerController: LivePlayerController,
    onExpandToFullScreen: () -> Unit,
    onClosePip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeChannel by playerController.currentChannel.collectAsState()
    val uiState by playerController.uiState.collectAsState()
    val playbackInfo by playerController.playbackInfo.collectAsState()
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

    LaunchedEffect(isSeriesVod, nextEpisode, seriesTitlePrefix) {
        playerController.onEpisodeEndedAutoNext = {
            if (isSeriesVod && nextEpisode != null) {
                nextEpisode.toPlayableChannel(seriesTitlePrefix)
            } else null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("system_pip_player_view")
    ) {
        PipPlayerSurfaceAndControls(
            playerController = playerController,
            activeChannel = activeChannel,
            uiState = uiState,
            playbackInfo = playbackInfo,
            isSeriesVod = isSeriesVod,
            previousEpisode = previousEpisode,
            nextEpisode = nextEpisode,
            seriesTitlePrefix = seriesTitlePrefix,
            showControls = true,
            resizeFeedbackText = null,
            onExpandToFullScreen = onExpandToFullScreen,
            onClosePip = onClosePip
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PipPlayerSurfaceAndControls(
    playerController: LivePlayerController,
    activeChannel: LiveChannel,
    uiState: PlayerUiState,
    playbackInfo: com.example.player.PlayerPlaybackInfo,
    isSeriesVod: Boolean,
    previousEpisode: com.example.model.EpisodeItem?,
    nextEpisode: com.example.model.EpisodeItem?,
    seriesTitlePrefix: String,
    showControls: Boolean,
    resizeFeedbackText: String?,
    onExpandToFullScreen: () -> Unit,
    onClosePip: () -> Unit
) {
    val activeExoPlayer = remember(playerController) { playerController.initializePlayer() }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = activeExoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player != activeExoPlayer) {
                    playerView.player = activeExoPlayer
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls || uiState !is PlayerUiState.Ready,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x660A0216))
            ) {
                // Top Bar: Channel/Title + Expand to Fullscreen Button + X Cancel Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xDD14052B), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (activeChannel.isLiveBroadcast) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeliLiveRed)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Text(
                            text = resizeFeedbackText ?: activeChannel.name,
                            color = if (resizeFeedbackText != null) NeliGenreCyan else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Return to Full Watching Inside App Button (right next to X button)
                        IconButton(
                            onClick = onExpandToFullScreen,
                            modifier = Modifier
                                .testTag("pip_expand_button")
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC2B1055))
                                .border(1.dp, NeliGenreCyan.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Return to Full Watching",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // X Cancel / Close PiP Button
                        IconButton(
                            onClick = onClosePip,
                            modifier = Modifier
                                .testTag("pip_close_button")
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0xCCEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel PiP",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Center Transport Controls:
                // - Live TV: NO play/pause, NO seek buttons!
                // - Movies & Adult: Seek Rewind (-10s), Play/Pause, Seek Forward (+10s)
                // - Series: Previous Episode, Seek (-10s), Play/Pause, Seek (+10s), Next Episode
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState is PlayerUiState.Loading || uiState is PlayerUiState.Buffering) {
                        CircularProgressIndicator(
                            color = NeliMagenta,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (!activeChannel.isLiveBroadcast) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSeriesVod) {
                                // Previous Episode Button for Series
                                IconButton(
                                    onClick = {
                                        previousEpisode?.let { prev ->
                                            playerController.switchChannel(prev.toPlayableChannel(seriesTitlePrefix))
                                        }
                                    },
                                    enabled = previousEpisode != null,
                                    modifier = Modifier
                                        .testTag("pip_prev_episode_button")
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xAA2B1055))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipPrevious,
                                        contentDescription = "Previous Episode",
                                        tint = if (previousEpisode != null) Color.White else NeliTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Seek Rewind 10s Button (Movies, Adult & Series)
                            IconButton(
                                onClick = { playerController.seekRelative(-10_000L) },
                                modifier = Modifier
                                    .testTag("pip_seek_rewind_button")
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA2B1055))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            // Play / Pause Button (Movies, Adult & Series)
                            IconButton(
                                onClick = { playerController.togglePlayPause() },
                                modifier = Modifier
                                    .testTag("pip_play_pause_button")
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(NeliMagenta)
                            ) {
                                Icon(
                                    imageVector = if (playbackInfo.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Seek Forward 10s Button (Movies, Adult & Series)
                            IconButton(
                                onClick = { playerController.seekRelative(10_000L) },
                                modifier = Modifier
                                    .testTag("pip_seek_forward_button")
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xAA2B1055))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10s",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            if (isSeriesVod) {
                                // Next Episode Button for Series
                                IconButton(
                                    onClick = {
                                        nextEpisode?.let { next ->
                                            playerController.switchChannel(next.toPlayableChannel(seriesTitlePrefix))
                                        }
                                    },
                                    enabled = nextEpisode != null,
                                    modifier = Modifier
                                        .testTag("pip_next_episode_button")
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xAA2B1055))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Next Episode",
                                        tint = if (nextEpisode != null) Color.White else NeliTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Timeline Bar (ONLY for Movies, Adult & Series — NEVER for Live TV)
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
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xEE14052B))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formatPipTimeMs(displayPositionMs),
                            color = Color.White,
                            fontSize = 9.sp,
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
                                .height(22.dp)
                                .testTag("pip_timeline_slider")
                        )
                        Text(
                            text = formatPipTimeMs(durationMs),
                            color = NeliGenreCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

private fun formatPipTimeMs(ms: Long): String {
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
