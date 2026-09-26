package com.example.player

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.BandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlayerUiState {
    data object Loading : PlayerUiState
    data object Buffering : PlayerUiState
    data object Ready : PlayerUiState
    data class Error(val userFriendlyMessage: String, val technicalDetail: String? = null) : PlayerUiState
}

enum class NetworkQualityMode(val label: String) {
    AUTO_ADAPTIVE("Auto Quality"),
    ULTRA_LOW_BANDO_SAVER("Low Bando Saver (240p)"),
    WEAK_NETWORK_SAVER("Data Saver (360p)"),
    STANDARD_480P("Standard (480p)"),
    STRONG_NETWORK_HD("Full HD (720p/1080p)")
}

/**
 * Adaptive stream quality ladder ordered from highest quality (index 0) to emergency low-bandwidth (index 4).
 * Prioritizes high-quality HD streams when bandwidth & buffer are healthy, and dynamically steps down
 * when bandwidth drops or rebuffering stalls occur.
 */
enum class AdaptiveQualityTier(
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateBps: Int,
    val minPreferredWidth: Int,
    val minPreferredHeight: Int,
    val minPreferredBitrateBps: Int,
    val forceLowestBitrate: Boolean,
    val badgeLabel: String,
    val description: String
) {
    FULL_HD_1080P(
        maxWidth = 1920,
        maxHeight = 1080,
        maxBitrateBps = 5_500_000,
        minPreferredWidth = 1280,
        minPreferredHeight = 720,
        minPreferredBitrateBps = 1_500_000,
        forceLowestBitrate = false,
        badgeLabel = "1080p Full HD",
        description = "Prioritizing High-Quality 1080p/720p HD Stream"
    ),
    HD_720P(
        maxWidth = 1280,
        maxHeight = 720,
        maxBitrateBps = 2_800_000,
        minPreferredWidth = 854,
        minPreferredHeight = 480,
        minPreferredBitrateBps = 850_000,
        forceLowestBitrate = false,
        badgeLabel = "720p HD",
        description = "High-Quality 720p Adaptive Stream"
    ),
    STANDARD_480P(
        maxWidth = 854,
        maxHeight = 480,
        maxBitrateBps = 1_250_000,
        minPreferredWidth = 640,
        minPreferredHeight = 360,
        minPreferredBitrateBps = 450_000,
        forceLowestBitrate = false,
        badgeLabel = "480p SD",
        description = "Balanced 480p Adaptive Stream"
    ),
    DATA_SAVER_360P(
        maxWidth = 640,
        maxHeight = 360,
        maxBitrateBps = 650_000,
        minPreferredWidth = 426,
        minPreferredHeight = 240,
        minPreferredBitrateBps = 220_000,
        forceLowestBitrate = false,
        badgeLabel = "360p Saver",
        description = "Dynamically Downscaled for Bandwidth Stability"
    ),
    LOW_BANDO_240P(
        maxWidth = 426,
        maxHeight = 240,
        maxBitrateBps = 260_000,
        minPreferredWidth = 0,
        minPreferredHeight = 0,
        minPreferredBitrateBps = 0,
        forceLowestBitrate = true,
        badgeLabel = "240p Seamless",
        description = "Emergency Rebuffer Protection (240p)"
    );

    fun stepDown(): AdaptiveQualityTier {
        val nextIndex = (ordinal + 1).coerceAtMost(entries.lastIndex)
        return entries[nextIndex]
    }

    fun stepUp(): AdaptiveQualityTier {
        val prevIndex = (ordinal - 1).coerceAtLeast(0)
        return entries[prevIndex]
    }
}

data class PlayerPlaybackInfo(
    val isPlaying: Boolean = false,
    val isLive: Boolean = true,
    val isSeekable: Boolean = false,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val duration: Long = C.TIME_UNSET,
    val isMuted: Boolean = false,
    val volume: Float = 1.0f,
    val networkMode: NetworkQualityMode = NetworkQualityMode.AUTO_ADAPTIVE,
    val adaptiveQualityTier: AdaptiveQualityTier = AdaptiveQualityTier.FULL_HD_1080P,
    val activeVideoResolutionLabel: String = AdaptiveQualityTier.FULL_HD_1080P.badgeLabel,
    val estimatedBandwidthKbps: Int = 3800,
    val bufferedDurationMs: Long = 0L,
    val isDynamicallyDownscaled: Boolean = false,
    val connectionLabel: String = "Mobile Data / Wi-Fi",
    val autoSkipNotice: String? = null
)

private enum class ForcedContainerMode {
    NONE,
    FORCE_PROGRESSIVE_MP4,
    FORCE_HLS_M3U8
}

/**
 * Manages the active ExoPlayer instance for Neli TV across Full-Screen and Floating PiP modes.
 *
 * - Movies, Adult & Series Episodes use ExoPlayer with fast keyframe seeking (`CLOSEST_SYNC`),
 *   constant-bitrate seeking enabled for MP4/TS, and automatic container detection for both `.mp4` and `.m3u8`.
 * - Uses an Adaptive Track Selection strategy (`AdaptiveTrackSelection.Factory` + `DefaultTrackSelector`)
 *   that prioritizes high-quality HD streams (`1080p`/`720p`) while dynamically downscaling based on
 *   real-time network bandwidth estimates and buffering/rebuffering state to ensure seamless playback.
 * - Auto-skips the first 5 minutes and 30 seconds (`330_000L` ms) of DJ intro ads ONLY for Movies
 *   narrated in Swahili (`channel.shouldAutoSkipSwahiliMovieIntro == true`), never for Live TV, Adult, Series, or Episodes.
 * - Automatically advances to the next episode (`onEpisodeEndedAutoNext`) when a Series episode finishes.
 * - Live TV channels always play continuously at the real-time live edge without pause or rewind.
 */
@OptIn(UnstableApi::class)
class LivePlayerController(
    private val context: Context,
    initialChannel: LiveChannel
) {
    companion object {
        /**
         * 5 minutes and 30 seconds (5 * 60 + 30 = 330 seconds = 330,000 ms)
         * Auto-skip start position strictly for Swahili-narrated Movies.
         */
        const val SWAHILI_MOVIE_INTRO_SKIP_MS = 330_000L

        /**
         * Media3 AdaptiveTrackSelection tuning parameters:
         * - Fast quality decrease (2,000 ms) so ExoPlayer downscales quickly when bandwidth drops or buffer drains.
         * - Smooth quality increase (3,500 ms) so ExoPlayer scales up to 720p/1080p as soon as a 3.5s buffer forms.
         * - High bandwidth utilization fraction (0.85f) to prioritize high-quality video tracks.
         */
        const val ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS = 3_500
        const val ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS = 2_000
        const val ADAPTIVE_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS = 6_000
        const val ADAPTIVE_BANDWIDTH_FRACTION = 0.85f
        const val ADAPTIVE_BUFFERED_FRACTION_TO_LIVE_EDGE = 0.65f

        /**
         * Computes the target [AdaptiveQualityTier] by prioritizing high-quality HD streams when network
         * bandwidth is sufficient and dynamically downscaling based on bandwidth constraints, active
         * buffering stalls, and consecutive rebuffer count.
         */
        fun computeAdaptiveQualityTier(
            estimatedBitrateBps: Long,
            bufferedDurationMs: Long,
            isBuffering: Boolean,
            consecutiveRebufferCount: Int,
            isLowBandoNetwork: Boolean = false
        ): AdaptiveQualityTier {
            // 1. Determine base quality tier from real-time network bandwidth estimate (prioritizing High Quality)
            val bandwidthTier = when {
                isLowBandoNetwork || (estimatedBitrateBps in 1..319_999L) -> AdaptiveQualityTier.LOW_BANDO_240P
                estimatedBitrateBps in 320_000L..649_999L -> AdaptiveQualityTier.DATA_SAVER_360P
                estimatedBitrateBps in 650_000L..1_199_999L -> AdaptiveQualityTier.STANDARD_480P
                estimatedBitrateBps in 1_200_000L..2_199_999L -> AdaptiveQualityTier.HD_720P
                else -> AdaptiveQualityTier.FULL_HD_1080P
            }

            // 2. Dynamically downscale based on buffering state and rebuffer stalls
            var downscaleSteps = consecutiveRebufferCount.coerceAtLeast(0)
            if (isBuffering && bufferedDurationMs < 1_500L && downscaleSteps == 0) {
                // Active buffering with depleted buffer immediately steps down 1 tier for faster recovery
                downscaleSteps = 1
            } else if (!isBuffering && bufferedDurationMs in 1..1_800L && downscaleSteps == 0 &&
                bandwidthTier == AdaptiveQualityTier.FULL_HD_1080P
            ) {
                // Preemptively step down from 1080p to 720p when buffer cushion is critically thin (< 1.8s)
                downscaleSteps = 1
            }

            var resolvedTier = bandwidthTier
            repeat(downscaleSteps.coerceAtMost(AdaptiveQualityTier.entries.lastIndex)) {
                resolvedTier = resolvedTier.stepDown()
            }
            return resolvedTier
        }

        init {
            NativeLogSuppressor.suppressNonFatalNativeLogs()
        }
    }

    var channel: LiveChannel = initialChannel
        private set

    private val _currentChannel = MutableStateFlow(initialChannel)
    val currentChannel: StateFlow<LiveChannel> = _currentChannel.asStateFlow()

    /**
     * Optional callback invoked when a VOD Series episode reaches [Player.STATE_ENDED].
     * If it returns a non-null [LiveChannel], the controller automatically plays that next episode.
     */
    var onEpisodeEndedAutoNext: (() -> LiveChannel?)? = null

    private var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoReconnectAttempts = 0
    private var forcedContainerMode = ForcedContainerMode.NONE
    private var hasAppliedSwahiliMovieIntroSkip = false
    private var lastKnownVodPositionMs = 0L
    private var pausedByCallOrExternalAudio: Boolean = false
    private var pausedSpecificallyByPhoneCall: Boolean = false
    private var lastSystemStatusPollTimeMs = 0L
    private var cachedPhoneCallActive = false
    private var cachedConnectionLabel: String = "Mobile Data / Wi-Fi"

    // Adaptive Track Selection & Buffering State Telemetry
    private var latestEstimatedBandwidthBps: Long = 3_800_000L
    private var hasReachedReadyForCurrentStream: Boolean = false
    private var consecutiveRebufferCount: Int = 0
    private var bufferingEnteredAtRealtimeMs: Long = 0L
    private var healthyPlaybackSinceRealtimeMs: Long = 0L
    private var currentAdaptiveTier: AdaptiveQualityTier = AdaptiveQualityTier.FULL_HD_1080P
    private var activeTrackResolutionLabel: String = AdaptiveQualityTier.FULL_HD_1080P.badgeLabel

    private val bandwidthEventListener = BandwidthMeter.EventListener { _, _, bitrateEstimate ->
        if (bitrateEstimate > 0L) {
            latestEstimatedBandwidthBps = bitrateEstimate
            if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                evaluateAndApplyAdaptiveTrackSelection(
                    forceBufferingState = _uiState.value is PlayerUiState.Buffering
                )
            } else {
                updatePlaybackInfo()
            }
        }
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    private fun isPhoneCallActiveOrRinging(forceRefresh: Boolean = false): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!forceRefresh && now - lastSystemStatusPollTimeMs < 2500L) {
            return cachedPhoneCallActive
        }
        val mode = try {
            audioManager?.mode ?: AudioManager.MODE_NORMAL
        } catch (_: Exception) {
            AudioManager.MODE_NORMAL
        }
        cachedPhoneCallActive = mode == AudioManager.MODE_RINGTONE ||
                mode == AudioManager.MODE_IN_CALL ||
                mode == AudioManager.MODE_IN_COMMUNICATION ||
                mode == AudioManager.MODE_CALL_SCREENING
        return cachedPhoneCallActive
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _playbackInfo = MutableStateFlow(
        PlayerPlaybackInfo(
            isLive = initialChannel.isLiveBroadcast,
            isSeekable = !initialChannel.isLiveBroadcast,
            connectionLabel = detectConnectionLabel(),
            autoSkipNotice = if (initialChannel.shouldAutoSkipSwahiliMovieIntro) {
                "Auto-skipped to 05:30 • Swahili Movie Intro Ads Skipped"
            } else null
        )
    )
    val playbackInfo: StateFlow<PlayerPlaybackInfo> = _playbackInfo.asStateFlow()

    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer ?: return
            val now = android.os.SystemClock.elapsedRealtime()
            val callActive = isPhoneCallActiveOrRinging()
            if (callActive) {
                if (player.isPlaying || player.playWhenReady) {
                    // Auto-pause and stop immediately when phone rings or user is in any cellular/WhatsApp/VoIP call
                    pausedByCallOrExternalAudio = true
                    pausedSpecificallyByPhoneCall = true
                    player.playWhenReady = false
                    player.pause()
                    updatePlaybackInfo()
                }
                mainHandler.postDelayed(this, 450L)
                return
            } else if (pausedSpecificallyByPhoneCall) {
                // Call ended: automatically clear call flag and resume cleanly
                pausedSpecificallyByPhoneCall = false
                pausedByCallOrExternalAudio = false
                if (channel.isLiveBroadcast && player.isCurrentMediaItemLive) {
                    player.seekToDefaultPosition()
                }
                player.playWhenReady = true
                player.play()
            }

            // Refresh bandwidth estimate from DefaultBandwidthMeter
            bandwidthMeter?.bitrateEstimate?.takeIf { it > 0L }?.let { measuredBps ->
                latestEstimatedBandwidthBps = measuredBps
            }

            // Dynamic buffering & bandwidth adaptation during active playback/buffering
            if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                if (player.playbackState == Player.STATE_BUFFERING) {
                    if (bufferingEnteredAtRealtimeMs > 0L && now - bufferingEnteredAtRealtimeMs >= 2_200L) {
                        // Prolonged buffering stall -> step down another quality tier dynamically
                        consecutiveRebufferCount = (consecutiveRebufferCount + 1).coerceAtMost(4)
                        bufferingEnteredAtRealtimeMs = now
                        evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = true)
                    }
                } else if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                    val bufferedAheadMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
                    if (healthyPlaybackSinceRealtimeMs == 0L) {
                        healthyPlaybackSinceRealtimeMs = now
                    }
                    // When buffer cushion is healthy (>= 6s) and playback has been steady, recover toward HD
                    if (consecutiveRebufferCount > 0 &&
                        bufferedAheadMs >= 6_000L &&
                        now - healthyPlaybackSinceRealtimeMs >= 5_000L
                    ) {
                        consecutiveRebufferCount = (consecutiveRebufferCount - 1).coerceAtLeast(0)
                        healthyPlaybackSinceRealtimeMs = now
                        evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = false)
                    } else {
                        evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = false)
                    }
                }
            }

            if (channel.isLiveBroadcast) {
                // Ensure Live TV stays playing unless paused by phone call or external music player
                if (!pausedByCallOrExternalAudio && !player.isPlaying && player.playbackState == Player.STATE_READY) {
                    player.playWhenReady = true
                    player.play()
                }
                updatePlaybackInfo()
                mainHandler.postDelayed(this, 600L)
            } else {
                updatePlaybackInfo()
                mainHandler.postDelayed(this, 300L)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val now = android.os.SystemClock.elapsedRealtime()
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.value = PlayerUiState.Buffering
                    bufferingEnteredAtRealtimeMs = now
                    healthyPlaybackSinceRealtimeMs = 0L
                    if (hasReachedReadyForCurrentStream) {
                        // Mid-stream rebuffer detected: dynamically step down quality tier immediately
                        consecutiveRebufferCount = (consecutiveRebufferCount + 1).coerceAtMost(4)
                    }
                    if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                        evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = true)
                    }
                    updatePlaybackInfo()
                    mainHandler.removeCallbacks(positionUpdateRunnable)
                    mainHandler.postDelayed(positionUpdateRunnable, 450L)
                }
                Player.STATE_READY -> {
                    autoReconnectAttempts = 0
                    hasReachedReadyForCurrentStream = true
                    bufferingEnteredAtRealtimeMs = 0L
                    if (healthyPlaybackSinceRealtimeMs == 0L) {
                        healthyPlaybackSinceRealtimeMs = now
                    }
                    val player = exoPlayer
                    if (player != null) {
                        if (isPhoneCallActiveOrRinging()) {
                            pausedByCallOrExternalAudio = true
                            pausedSpecificallyByPhoneCall = true
                            player.playWhenReady = false
                            player.pause()
                        }
                        val dur = player.duration
                        if (channel.shouldAutoSkipSwahiliMovieIntro && dur != C.TIME_UNSET && dur in 1..(SWAHILI_MOVIE_INTRO_SKIP_MS + 5_000L)) {
                            // Stream is shorter than the 5:30 intro skip window; start at 0:00 so it plays normally
                            if (player.currentPosition >= dur - 2_000L || player.currentPosition >= SWAHILI_MOVIE_INTRO_SKIP_MS) {
                                lastKnownVodPositionMs = 0L
                                player.seekTo(0L)
                            }
                        } else if (!hasAppliedSwahiliMovieIntroSkip && channel.shouldAutoSkipSwahiliMovieIntro) {
                            hasAppliedSwahiliMovieIntroSkip = true
                            val canSkipTo530 = dur == C.TIME_UNSET || dur > (SWAHILI_MOVIE_INTRO_SKIP_MS + 10_000L)
                            if (canSkipTo530 && player.currentPosition < SWAHILI_MOVIE_INTRO_SKIP_MS - 3_000L) {
                                player.seekTo(SWAHILI_MOVIE_INTRO_SKIP_MS)
                            }
                        }
                    }
                    if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                        evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = false)
                    }
                    _uiState.value = PlayerUiState.Ready
                    updatePlaybackInfo()
                    mainHandler.removeCallbacks(positionUpdateRunnable)
                    mainHandler.post(positionUpdateRunnable)
                }
                Player.STATE_ENDED -> {
                    val player = exoPlayer
                    val dur = player?.duration ?: C.TIME_UNSET
                    if (channel.isLiveBroadcast) {
                        // Live TV never ends; restart or re-sync seamlessly
                        player?.let { p ->
                            p.seekTo(0L)
                            if (!pausedByCallOrExternalAudio) {
                                p.playWhenReady = true
                                p.play()
                            }
                        }
                    } else if (player != null && channel.shouldAutoSkipSwahiliMovieIntro && dur != C.TIME_UNSET && dur in 1..(SWAHILI_MOVIE_INTRO_SKIP_MS + 5_000L) && lastKnownVodPositionMs >= SWAHILI_MOVIE_INTRO_SKIP_MS) {
                        // Recover if initial 5:30 seek jumped past the end of a shorter movie clip
                        lastKnownVodPositionMs = 0L
                        player.seekTo(0L)
                        player.playWhenReady = true
                        player.play()
                    } else {
                        // Check if there is a Next Episode in Series VOD to auto-advance to
                        val nextEpChannel = onEpisodeEndedAutoNext?.invoke()
                        if (nextEpChannel != null) {
                            switchChannel(nextEpChannel)
                        } else {
                            _uiState.value = PlayerUiState.Ready
                            updatePlaybackInfo()
                        }
                    }
                }
                Player.STATE_IDLE -> {}
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                    for (i in 0 until group.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                activeTrackResolutionLabel = formatResolutionLabel(format.height, format.bitrate)
                                updatePlaybackInfo()
                                return
                            }
                        }
                    }
                }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) {
                activeTrackResolutionLabel = formatResolutionLabel(videoSize.height, C.INDEX_UNSET)
                updatePlaybackInfo()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && isPhoneCallActiveOrRinging()) {
                pausedByCallOrExternalAudio = true
                pausedSpecificallyByPhoneCall = true
                exoPlayer?.playWhenReady = false
                exoPlayer?.pause()
                return
            }
            if (isPlaying) {
                pausedByCallOrExternalAudio = false
            }
            _playbackInfo.value = _playbackInfo.value.copy(
                isPlaying = isPlaying,
                connectionLabel = detectConnectionLabel()
            )
            mainHandler.removeCallbacks(positionUpdateRunnable)
            mainHandler.post(positionUpdateRunnable)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && (
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
                    )
            ) {
                // Auto-stop/pause when user plays music on another app or receives/makes a call
                pausedByCallOrExternalAudio = true
                exoPlayer?.pause()
                updatePlaybackInfo()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val cause = error.cause
            val isContainerFormatMismatch = cause is UnrecognizedInputFormatException ||
                    cause is ParserException ||
                    (error.message?.contains("EXTM3U", ignoreCase = true) == true) ||
                    (cause?.message?.contains("EXTM3U", ignoreCase = true) == true)

            // If metadata format (e.g. "hls" vs "mp4") didn't match actual stream container, switch container automatically
            if (isContainerFormatMismatch && forcedContainerMode == ForcedContainerMode.NONE) {
                forcedContainerMode = if (channel.isMp4) {
                    ForcedContainerMode.FORCE_HLS_M3U8
                } else {
                    ForcedContainerMode.FORCE_PROGRESSIVE_MP4
                }
                _uiState.value = PlayerUiState.Buffering
                mainHandler.post {
                    exoPlayer?.let { p ->
                        loadChannelStream(p, preserveVodPosition = true)
                    }
                }
                return
            }

            val httpCode = extractHttpErrorCode(error)
            if (autoReconnectAttempts < 2) {
                autoReconnectAttempts++
                _uiState.value = PlayerUiState.Buffering
                // Dynamically downscale on transient network errors to ensure seamless recovery
                consecutiveRebufferCount = (consecutiveRebufferCount + 1).coerceAtMost(4)
                if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                    evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = true)
                } else if (autoReconnectAttempts >= 2) {
                    applyNetworkQualityMode(NetworkQualityMode.ULTRA_LOW_BANDO_SAVER)
                }
                val resumePos = lastKnownVodPositionMs
                mainHandler.postDelayed({
                    exoPlayer?.let { p ->
                        if (channel.isLiveBroadcast) {
                            channel = channel.copy(
                                streamUrl = com.example.data.ChannelRepository.normalizeDashStreamUrl(channel.streamUrl)
                            )
                            _currentChannel.value = channel
                            loadChannelStream(p, preserveVodPosition = false)
                        } else {
                            if (resumePos > 0L) {
                                p.seekTo(resumePos)
                            }
                            p.prepare()
                            p.play()
                        }
                    }
                }, 450L)
                return
            }

            val friendlyMsg = mapPlaybackError(error)
            val techMsg = if (httpCode != null) {
                "HTTP $httpCode - Stream authorization or token required by provider"
            } else {
                "${error.errorCodeName}: ${error.message ?: "Unable to connect"}"
            }

            _uiState.value = PlayerUiState.Error(friendlyMsg, techMsg)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updatePlaybackInfo()
        }
    }

    private fun formatResolutionLabel(height: Int, bitrateBps: Int): String {
        val base = when {
            height >= 1080 -> "1080p Full HD"
            height >= 720 -> "720p HD"
            height >= 480 -> "480p SD"
            height >= 360 -> "360p Saver"
            height > 0 -> "${height}p Seamless"
            else -> currentAdaptiveTier.badgeLabel
        }
        return if (bitrateBps > 0) {
            val kbps = (bitrateBps / 1000).coerceAtLeast(100)
            "$base ($kbps kbps)"
        } else {
            base
        }
    }

    private fun isLowBandoNetworkDetected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            val downKbps = caps.linkDownstreamBandwidthKbps
            val isUnvalidatedCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            isUnvalidatedCellular || (downKbps in 1..450)
        } catch (_: Exception) {
            false
        }
    }

    private fun detectConnectionLabel(forceRefresh: Boolean = false): String {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!forceRefresh && now - lastSystemStatusPollTimeMs < 2500L) {
            return cachedConnectionLabel
        }
        lastSystemStatusPollTimeMs = now
        cachedConnectionLabel = try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> "Low Bando / Zero-Rated Mode"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
                        (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) || caps.linkDownstreamBandwidthKbps in 1..450) ->
                    "Low Bando Data Saver"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "Online"
            }
        } catch (_: Exception) {
            "Mobile Data / Wi-Fi"
        }
        return cachedConnectionLabel
    }

    /**
     * Seeds the initial bandwidth estimate for [DefaultBandwidthMeter] so that [AdaptiveTrackSelection]
     * prioritizes high-quality HD streams (1080p/720p) on normal Wi-Fi and Mobile Data connections,
     * while respecting constrained or low-bando links.
     */
    private fun detectInitialBitrateEstimate(): Long {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val downKbps = caps?.linkDownstreamBandwidthKbps ?: 4_500
            val isLowBando = isLowBandoNetworkDetected()
            when {
                isLowBando -> 250_000L
                downKbps in 1..600 -> 520_000L
                downKbps in 601..1_400 -> 1_100_000L
                downKbps in 1_401..2_800 -> 2_400_000L
                else -> 3_800_000L
            }
        } catch (_: Exception) {
            3_800_000L
        }
    }

    /**
     * Evaluates current network bandwidth and buffering state to dynamically update
     * ExoPlayer's [DefaultTrackSelector] parameters for seamless adaptive playback.
     */
    fun evaluateAndApplyAdaptiveTrackSelection(
        estimatedBitrateBps: Long = latestEstimatedBandwidthBps,
        bufferedDurationMs: Long = exoPlayer?.let { (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L) } ?: 0L,
        forceBufferingState: Boolean = _uiState.value is PlayerUiState.Buffering,
        rebufferCountOverride: Int = consecutiveRebufferCount
    ): AdaptiveQualityTier {
        latestEstimatedBandwidthBps = estimatedBitrateBps.coerceAtLeast(100_000L)
        consecutiveRebufferCount = rebufferCountOverride.coerceIn(0, 4)

        val resolvedTier = computeAdaptiveQualityTier(
            estimatedBitrateBps = latestEstimatedBandwidthBps,
            bufferedDurationMs = bufferedDurationMs,
            isBuffering = forceBufferingState,
            consecutiveRebufferCount = consecutiveRebufferCount,
            isLowBandoNetwork = isLowBandoNetworkDetected() && estimatedBitrateBps < 350_000L
        )
        currentAdaptiveTier = resolvedTier
        activeTrackResolutionLabel = resolvedTier.badgeLabel

        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            trackSelector?.let { selector ->
                selector.setParameters(
                    selector.buildUponParameters()
                        .setMaxVideoSize(resolvedTier.maxWidth, resolvedTier.maxHeight)
                        .setMaxVideoBitrate(resolvedTier.maxBitrateBps)
                        .setMinVideoSize(resolvedTier.minPreferredWidth, resolvedTier.minPreferredHeight)
                        .setMinVideoBitrate(resolvedTier.minPreferredBitrateBps)
                        .setForceLowestBitrate(resolvedTier.forceLowestBitrate)
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedRendererCapabilitiesIfNecessary(true)
                )
            }
            exoPlayer?.let { player ->
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(resolvedTier.maxWidth, resolvedTier.maxHeight)
                    .setMaxVideoBitrate(resolvedTier.maxBitrateBps)
                    .setMinVideoSize(resolvedTier.minPreferredWidth, resolvedTier.minPreferredHeight)
                    .setMinVideoBitrate(resolvedTier.minPreferredBitrateBps)
                    .setForceLowestBitrate(resolvedTier.forceLowestBitrate)
                    .build()
            }
        }

        updatePlaybackInfo(
            overrideBufferedDurationMs = bufferedDurationMs,
            overrideEstimatedBitrateBps = latestEstimatedBandwidthBps
        )
        return resolvedTier
    }

    fun initializePlayer(): ExoPlayer {
        NativeLogSuppressor.suppressNonFatalNativeLogs()
        exoPlayer?.let { return it }
        autoReconnectAttempts = 0
        hasReachedReadyForCurrentStream = false
        consecutiveRebufferCount = 0
        _uiState.value = PlayerUiState.Loading

        // Prioritize standard AOSP platform codecs (c2.android.* / OMX.google.*) and exclude
        // buggy goldfish/ranchu emulator codecs that fail CCodecResources system resource queries (error 6)
        // or stall on seek flush.
        val reliableCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaultInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder
            )
            val androidPlatformCodecs = defaultInfos.filter { info ->
                info.name.startsWith("c2.android.", ignoreCase = true) ||
                        info.name.startsWith("OMX.google.", ignoreCase = true) ||
                        (info.softwareOnly && !info.vendor)
            }
            if (androidPlatformCodecs.isNotEmpty() && !requiresSecureDecoder && !requiresTunnelingDecoder) {
                androidPlatformCodecs
            } else {
                defaultInfos.filterNot { info ->
                    info.name.contains("goldfish", ignoreCase = true) ||
                            info.name.contains("ranchu", ignoreCase = true) ||
                            info.name.contains("cuttlefish", ignoreCase = true)
                }.ifEmpty { defaultInfos }
            }
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(reliableCodecSelector)
            .setEnableDecoderFallback(true)

        val initialBitrate = detectInitialBitrateEstimate()
        latestEstimatedBandwidthBps = initialBitrate
        val lowBandoActive = isLowBandoNetworkDetected() && initialBitrate < 350_000L

        val meter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(initialBitrate)
            .setResetOnNetworkTypeChange(true)
            .build()
            .also { bwMeter ->
                bwMeter.addEventListener(mainHandler, bandwidthEventListener)
            }
        bandwidthMeter = meter

        // AdaptiveTrackSelection.Factory configured to prioritize high-quality streams (bandwidthFraction = 0.85f)
        // while reacting rapidly (2,000ms) to downscale when network bandwidth or buffer drops.
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS,
            /* maxDurationForQualityDecreaseMs = */ ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
            /* minDurationToRetainAfterDiscardMs = */ ADAPTIVE_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
            /* bandwidthFraction = */ ADAPTIVE_BANDWIDTH_FRACTION,
            /* bufferedFractionToLiveEdgeForQualityIncrease = */ ADAPTIVE_BUFFERED_FRACTION_TO_LIVE_EDGE,
            /* clock = */ Clock.DEFAULT
        )

        val initialTier = computeAdaptiveQualityTier(
            estimatedBitrateBps = initialBitrate,
            bufferedDurationMs = 5_000L,
            isBuffering = false,
            consecutiveRebufferCount = 0,
            isLowBandoNetwork = lowBandoActive
        )
        currentAdaptiveTier = initialTier
        activeTrackResolutionLabel = initialTier.badgeLabel

        val selector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(initialTier.maxWidth, initialTier.maxHeight)
                    .setMaxVideoBitrate(initialTier.maxBitrateBps)
                    .setMinVideoSize(initialTier.minPreferredWidth, initialTier.minPreferredHeight)
                    .setMinVideoBitrate(initialTier.minPreferredBitrateBps)
                    .setForceLowestBitrate(initialTier.forceLowestBitrate)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
            )
        }
        trackSelector = selector

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ if (lowBandoActive) 4_000 else 8_000,
                /* maxBufferMs = */ if (lowBandoActive) 28_000 else 50_000,
                /* bufferForPlaybackMs = */ if (lowBandoActive) 400 else 600,
                /* bufferForPlaybackAfterRebufferMs = */ if (lowBandoActive) 900 else 1_200
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 15_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val mediaAudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(selector)
            .setBandwidthMeter(meter)
            .setLoadControl(loadControl)
            .setAudioAttributes(mediaAudioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                playWhenReady = !isPhoneCallActiveOrRinging()
                addListener(playerListener)
            }

        exoPlayer = player
        loadChannelStream(player, preserveVodPosition = false)
        updatePlaybackInfo()
        return player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer
    fun getTrackSelector(): DefaultTrackSelector? = trackSelector

    /**
     * Seamlessly switches the active channel or episode on the existing ExoPlayer instance
     * (used by Next/Previous Episode buttons, In-Player VOD Episode Switcher, and Auto-Next Episode).
     */
    fun switchChannel(newChannel: LiveChannel) {
        if (newChannel.id == channel.id && newChannel.streamUrl == channel.streamUrl && exoPlayer != null) {
            return
        }
        channel = newChannel
        _currentChannel.value = newChannel
        autoReconnectAttempts = 0
        hasReachedReadyForCurrentStream = false
        consecutiveRebufferCount = 0
        bufferingEnteredAtRealtimeMs = 0L
        healthyPlaybackSinceRealtimeMs = 0L
        forcedContainerMode = ForcedContainerMode.NONE
        hasAppliedSwahiliMovieIntroSkip = false
        lastKnownVodPositionMs = if (newChannel.shouldAutoSkipSwahiliMovieIntro) {
            SWAHILI_MOVIE_INTRO_SKIP_MS
        } else {
            0L
        }
        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            evaluateAndApplyAdaptiveTrackSelection(
                estimatedBitrateBps = latestEstimatedBandwidthBps,
                bufferedDurationMs = 5_000L,
                forceBufferingState = false,
                rebufferCountOverride = 0
            )
        }
        _playbackInfo.value = PlayerPlaybackInfo(
            isPlaying = false,
            isLive = newChannel.isLiveBroadcast,
            isSeekable = !newChannel.isLiveBroadcast,
            currentPosition = lastKnownVodPositionMs,
            bufferedPosition = lastKnownVodPositionMs,
            duration = C.TIME_UNSET,
            isMuted = _playbackInfo.value.isMuted,
            volume = _playbackInfo.value.volume,
            networkMode = _playbackInfo.value.networkMode,
            adaptiveQualityTier = currentAdaptiveTier,
            activeVideoResolutionLabel = activeTrackResolutionLabel,
            estimatedBandwidthKbps = (latestEstimatedBandwidthBps / 1000L).toInt().coerceAtLeast(100),
            bufferedDurationMs = 0L,
            isDynamicallyDownscaled = currentAdaptiveTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal,
            connectionLabel = detectConnectionLabel(),
            autoSkipNotice = if (newChannel.shouldAutoSkipSwahiliMovieIntro) {
                "Auto-skipped to 05:30 • Swahili Movie Intro Ads Skipped"
            } else null
        )
        val player = exoPlayer ?: initializePlayer()
        _uiState.value = PlayerUiState.Loading
        loadChannelStream(player, preserveVodPosition = false)
    }

    fun applyNetworkQualityMode(mode: NetworkQualityMode) {
        val mappedTier = when (mode) {
            NetworkQualityMode.ULTRA_LOW_BANDO_SAVER -> AdaptiveQualityTier.LOW_BANDO_240P
            NetworkQualityMode.WEAK_NETWORK_SAVER -> AdaptiveQualityTier.DATA_SAVER_360P
            NetworkQualityMode.STANDARD_480P -> AdaptiveQualityTier.STANDARD_480P
            NetworkQualityMode.STRONG_NETWORK_HD -> AdaptiveQualityTier.FULL_HD_1080P
            NetworkQualityMode.AUTO_ADAPTIVE -> {
                consecutiveRebufferCount = 0
                computeAdaptiveQualityTier(
                    estimatedBitrateBps = latestEstimatedBandwidthBps.takeIf { it > 0L } ?: detectInitialBitrateEstimate(),
                    bufferedDurationMs = exoPlayer?.let { (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L) } ?: 5_000L,
                    isBuffering = false,
                    consecutiveRebufferCount = 0,
                    isLowBandoNetwork = isLowBandoNetworkDetected() && latestEstimatedBandwidthBps < 350_000L
                )
            }
        }
        currentAdaptiveTier = mappedTier
        activeTrackResolutionLabel = mappedTier.badgeLabel

        _playbackInfo.value = _playbackInfo.value.copy(
            networkMode = mode,
            adaptiveQualityTier = mappedTier,
            activeVideoResolutionLabel = mappedTier.badgeLabel,
            isDynamicallyDownscaled = mode == NetworkQualityMode.AUTO_ADAPTIVE &&
                    mappedTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal,
            connectionLabel = detectConnectionLabel()
        )
        val player = exoPlayer ?: return
        player.trackSelectionParameters = when (mode) {
            NetworkQualityMode.ULTRA_LOW_BANDO_SAVER -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(426, 240)
                    .setMaxVideoBitrate(220_000)
                    .setMinVideoSize(0, 0)
                    .setMinVideoBitrate(0)
                    .setForceLowestBitrate(true)
                    .build()
            }
            NetworkQualityMode.WEAK_NETWORK_SAVER -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(640, 360)
                    .setMaxVideoBitrate(550_000)
                    .setMinVideoSize(0, 0)
                    .setMinVideoBitrate(0)
                    .setForceLowestBitrate(true)
                    .build()
            }
            NetworkQualityMode.STANDARD_480P -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(854, 480)
                    .setMaxVideoBitrate(1_100_000)
                    .setMinVideoSize(640, 360)
                    .setMinVideoBitrate(400_000)
                    .setForceLowestBitrate(false)
                    .build()
            }
            NetworkQualityMode.AUTO_ADAPTIVE -> {
                trackSelector?.let { selector ->
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setMaxVideoSize(mappedTier.maxWidth, mappedTier.maxHeight)
                            .setMaxVideoBitrate(mappedTier.maxBitrateBps)
                            .setMinVideoSize(mappedTier.minPreferredWidth, mappedTier.minPreferredHeight)
                            .setMinVideoBitrate(mappedTier.minPreferredBitrateBps)
                            .setForceLowestBitrate(mappedTier.forceLowestBitrate)
                            .setExceedVideoConstraintsIfNecessary(true)
                            .setExceedRendererCapabilitiesIfNecessary(true)
                    )
                }
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(mappedTier.maxWidth, mappedTier.maxHeight)
                    .setMaxVideoBitrate(mappedTier.maxBitrateBps)
                    .setMinVideoSize(mappedTier.minPreferredWidth, mappedTier.minPreferredHeight)
                    .setMinVideoBitrate(mappedTier.minPreferredBitrateBps)
                    .setForceLowestBitrate(mappedTier.forceLowestBitrate)
                    .build()
            }
            NetworkQualityMode.STRONG_NETWORK_HD -> {
                trackSelector?.let { selector ->
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setMaxVideoSize(1920, 1080)
                            .setMaxVideoBitrate(5_500_000)
                            .setMinVideoSize(1280, 720)
                            .setMinVideoBitrate(1_500_000)
                            .setForceLowestBitrate(false)
                            .setExceedVideoConstraintsIfNecessary(true)
                    )
                }
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(5_500_000)
                    .setMinVideoSize(1280, 720)
                    .setMinVideoBitrate(1_500_000)
                    .setForceLowestBitrate(false)
                    .build()
            }
        }
    }

    fun cycleNetworkQualityMode() {
        val next = when (_playbackInfo.value.networkMode) {
            NetworkQualityMode.AUTO_ADAPTIVE -> NetworkQualityMode.ULTRA_LOW_BANDO_SAVER
            NetworkQualityMode.ULTRA_LOW_BANDO_SAVER -> NetworkQualityMode.WEAK_NETWORK_SAVER
            NetworkQualityMode.WEAK_NETWORK_SAVER -> NetworkQualityMode.STANDARD_480P
            NetworkQualityMode.STANDARD_480P -> NetworkQualityMode.STRONG_NETWORK_HD
            NetworkQualityMode.STRONG_NETWORK_HD -> NetworkQualityMode.AUTO_ADAPTIVE
        }
        applyNetworkQualityMode(next)
    }

    /**
     * Ensures Live TV playback jumps directly to the current live broadcast edge without rewinding.
     */
    fun syncToLiveEdge() {
        exoPlayer?.let { player ->
            if (player.isCurrentMediaItemLive) {
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
            player.play()
        }
    }

    private fun loadChannelStream(player: ExoPlayer, preserveVodPosition: Boolean = false) {
        try {
            val mediaSource = createMediaSource(channel)
            player.repeatMode = if (channel.isLiveBroadcast) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            val startPositionMs = when {
                channel.isLiveBroadcast -> C.TIME_UNSET
                preserveVodPosition && lastKnownVodPositionMs > 0L -> lastKnownVodPositionMs
                channel.shouldAutoSkipSwahiliMovieIntro -> {
                    hasAppliedSwahiliMovieIntroSkip = true
                    lastKnownVodPositionMs = SWAHILI_MOVIE_INTRO_SKIP_MS
                    SWAHILI_MOVIE_INTRO_SKIP_MS
                }
                else -> 0L
            }

            if (startPositionMs != C.TIME_UNSET && startPositionMs > 0L) {
                player.setMediaSource(mediaSource, startPositionMs)
                _playbackInfo.value = _playbackInfo.value.copy(
                    currentPosition = startPositionMs,
                    bufferedPosition = startPositionMs
                )
            } else {
                player.setMediaSource(mediaSource, /* resetPosition = */ true)
            }

            player.prepare()
            if (channel.isLiveBroadcast && !channel.isMp4) {
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
            player.play()
        } catch (e: Exception) {
            _uiState.value = PlayerUiState.Error(
                userFriendlyMessage = "Unable to load stream",
                technicalDetail = e.message
            )
        }
    }

    private fun createMediaSource(channel: LiveChannel): MediaSource {
        val lookupId = channel.episodeId.ifBlank { channel.id }
        val resolvedOfflineOrOnlineUrl = com.example.data.OfflineDownloadManager.resolveLocalOfflineUriIfPresent(
            context = context,
            rawId = lookupId,
            fallbackStreamUrl = channel.streamUrl
        )
        val effectiveStreamUrl = com.example.data.ChannelRepository.normalizeDashStreamUrl(resolvedOfflineOrOnlineUrl)
        val manifestUri = Uri.parse(effectiveStreamUrl)
        val encodedManifestQuery = manifestUri.encodedQuery

        val baseHttpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )

        // Use ClearKeyDecryptingDataSource when ClearKey DRM or Azam cdntoken propagation is needed
        // (for DASH .mpd manifests, fragmented .mp4 CDN segments, and any CDN .mp4 stream using cdntoken).
        val needsClearKeyOrTokenWrapper =
            (channel.isClearKey && channel.clearKeys.isNotEmpty()) ||
                    effectiveStreamUrl.contains("cdntoken=", ignoreCase = true) ||
                    effectiveStreamUrl.contains("azamtvltd.co.tz", ignoreCase = true)

        val upstreamDataSourceFactory = if (needsClearKeyOrTokenWrapper) {
            val decryptor = if (channel.isClearKey && channel.clearKeys.isNotEmpty()) {
                Mp4CencDecryptor(channel.clearKeys)
            } else {
                null
            }
            ClearKeyDecryptingDataSource.Factory(
                upstreamFactory = baseHttpDataSourceFactory,
                encodedManifestQuery = encodedManifestQuery,
                decryptor = decryptor
            )
        } else {
            baseHttpDataSourceFactory
        }

        val dataSourceFactory = DefaultDataSource.Factory(context, upstreamDataSourceFactory)
        val loadErrorHandlingPolicy = LiveStreamLoadErrorHandlingPolicy()

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )

        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(10_000L)
            .setMinOffsetMs(4_000L)
            .setMaxOffsetMs(25_000L)
            .setMinPlaybackSpeed(0.98f)
            .setMaxPlaybackSpeed(1.02f)
            .build()

        val isLocalOfflineFile = effectiveStreamUrl.startsWith("file:", ignoreCase = true) ||
                effectiveStreamUrl.startsWith("/")
        val isLocalHlsPlaylist = isLocalOfflineFile &&
                effectiveStreamUrl.substringBefore("?").endsWith(".m3u8", ignoreCase = true)
        val useProgressive = when (forcedContainerMode) {
            ForcedContainerMode.FORCE_PROGRESSIVE_MP4 -> true
            ForcedContainerMode.FORCE_HLS_M3U8 -> false
            ForcedContainerMode.NONE -> (isLocalOfflineFile && !isLocalHlsPlaylist) || (!isLocalHlsPlaylist && channel.isMp4)
        }
        val useDash = forcedContainerMode == ForcedContainerMode.NONE &&
                !isLocalOfflineFile &&
                (channel.isDash || effectiveStreamUrl.contains(".mpd", ignoreCase = true))

        return when {
            useProgressive -> {
                // Do not hardcode MimeTypes.VIDEO_MP4 on local `.ts` files so DefaultExtractorsFactory
                // automatically sniffs both MP4 (`Mp4Extractor`) and MPEG-TS (`TsExtractor`) offline files!
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(manifestUri)
                    .setMediaId(channel.id)

                if (!effectiveStreamUrl.substringBefore("?").endsWith(".ts", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                }

                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .setContinueLoadingCheckIntervalBytes(1024 * 1024)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    .createMediaSource(mediaItemBuilder.build())
            }

            useDash -> {
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(manifestUri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setMediaId(channel.id)

                if (channel.isLiveBroadcast) {
                    mediaItemBuilder.setLiveConfiguration(liveConfiguration)
                }

                DashMediaSource.Factory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    .createMediaSource(mediaItemBuilder.build())
            }

            else -> {
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(manifestUri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setMediaId(channel.id)

                if (channel.isLiveBroadcast) {
                    mediaItemBuilder.setLiveConfiguration(liveConfiguration)
                }

                HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    .createMediaSource(mediaItemBuilder.build())
            }
        }
    }

    fun retry() {
        autoReconnectAttempts = 0
        val player = exoPlayer ?: return initializePlayer().let { }
        _uiState.value = PlayerUiState.Loading
        player.stop()
        loadChannelStream(player, preserveVodPosition = !channel.isLiveBroadcast)
    }

    /**
     * Only Movies, Adult & Series (`!channel.isLiveBroadcast`) can be paused or resumed manually.
     * Live TV always continues playing at the live broadcast edge.
     */
    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (channel.isLiveBroadcast) {
                if (!player.isPlaying) {
                    pausedByCallOrExternalAudio = false
                    pausedSpecificallyByPhoneCall = false
                    syncToLiveEdge()
                } else {
                    syncToLiveEdge()
                }
                return
            }
            if (player.isPlaying) {
                player.pause()
            } else {
                pausedByCallOrExternalAudio = false
                pausedSpecificallyByPhoneCall = false
                if (player.playbackState == Player.STATE_ENDED) {
                    val restartPos = if (channel.shouldAutoSkipSwahiliMovieIntro) SWAHILI_MOVIE_INTRO_SKIP_MS else 0L
                    player.seekTo(restartPos)
                }
                player.playWhenReady = true
                player.play()
            }
            updatePlaybackInfo()
        }
    }

    fun toggleMute() {
        exoPlayer?.let { player ->
            val currentlyMuted = _playbackInfo.value.isMuted
            if (currentlyMuted) {
                player.volume = 1.0f
                _playbackInfo.value = _playbackInfo.value.copy(isMuted = false, volume = 1.0f)
            } else {
                player.volume = 0.0f
                _playbackInfo.value = _playbackInfo.value.copy(isMuted = true, volume = 0.0f)
            }
        }
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = clamped
        _playbackInfo.value = _playbackInfo.value.copy(
            volume = clamped,
            isMuted = clamped == 0f
        )
    }

    fun seekTo(positionMs: Long) {
        if (channel.isLiveBroadcast) return
        exoPlayer?.let { player ->
            val duration = player.duration
            val target = if (duration != C.TIME_UNSET && duration > 0L) {
                positionMs.coerceIn(0L, duration)
            } else {
                positionMs.coerceAtLeast(0L)
            }
            lastKnownVodPositionMs = target
            _playbackInfo.value = _playbackInfo.value.copy(
                currentPosition = target,
                bufferedPosition = maxOf(player.bufferedPosition, target)
            )
            player.seekTo(target)
            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.playWhenReady = true
            player.play()
        }
    }

    fun seekRelative(deltaMs: Long) {
        if (channel.isLiveBroadcast) return
        val basePos = exoPlayer?.currentPosition?.takeIf { it > 0L } ?: _playbackInfo.value.currentPosition
        seekTo(basePos + deltaMs)
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun play() {
        exoPlayer?.let { player ->
            if (channel.isLiveBroadcast && player.isCurrentMediaItemLive) {
                // Returning to Live HLS/DASH TV jumps to the live edge, while progressive CDN MP4 continues smoothly
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
            player.play()
        }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        bandwidthMeter?.removeEventListener(bandwidthEventListener)
        bandwidthMeter = null
        trackSelector = null
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.clearVideoSurface()
            player.stop()
            player.release()
        }
        exoPlayer = null
    }

    private fun updatePlaybackInfo(
        overrideBufferedDurationMs: Long? = null,
        overrideEstimatedBitrateBps: Long? = null
    ) {
        val player = exoPlayer
        val isLive = channel.isLiveBroadcast
        val duration = player?.duration ?: C.TIME_UNSET
        val currentPos = (player?.currentPosition ?: lastKnownVodPositionMs).coerceAtLeast(0L)
        if (!isLive && currentPos > 0L) {
            lastKnownVodPositionMs = currentPos
        }
        val bufferedPos = (player?.bufferedPosition ?: currentPos).coerceAtLeast(currentPos)
        val computedBufferedAheadMs = overrideBufferedDurationMs ?: (bufferedPos - currentPos).coerceAtLeast(0L)
        val effectiveBps = overrideEstimatedBitrateBps
            ?: bandwidthMeter?.bitrateEstimate?.takeIf { it > 0L }
            ?: latestEstimatedBandwidthBps
        val kbps = (effectiveBps / 1000L).toInt().coerceAtLeast(100)
        val isSeekable = !isLive && ((player?.isCurrentMediaItemSeekable == true) || duration > 0)
        val isDownscaled = _playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE &&
                (currentAdaptiveTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal || consecutiveRebufferCount > 0)

        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = player?.isPlaying ?: _playbackInfo.value.isPlaying,
            isLive = isLive,
            isSeekable = isSeekable,
            currentPosition = currentPos,
            bufferedPosition = bufferedPos,
            duration = if (duration > 0) duration else C.TIME_UNSET,
            adaptiveQualityTier = currentAdaptiveTier,
            activeVideoResolutionLabel = activeTrackResolutionLabel,
            estimatedBandwidthKbps = kbps,
            bufferedDurationMs = computedBufferedAheadMs,
            isDynamicallyDownscaled = isDownscaled,
            connectionLabel = detectConnectionLabel()
        )
    }

    private fun extractHttpErrorCode(error: PlaybackException): Int? {
        val cause = error.cause
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            return cause.responseCode
        }
        val innerCause = cause?.cause
        if (innerCause is HttpDataSource.InvalidResponseCodeException) {
            return innerCause.responseCode
        }
        val msg = (error.message ?: "") + " " + (cause?.message ?: "")
        return when {
            msg.contains("403") -> 403
            msg.contains("401") -> 401
            msg.contains("404") -> 404
            msg.contains("410") -> 410
            msg.contains("500") -> 500
            msg.contains("502") -> 502
            msg.contains("503") -> 503
            else -> null
        }
    }

    private fun mapPlaybackError(error: PlaybackException): String {
        val httpCode = extractHttpErrorCode(error)
        val message = error.message ?: ""
        val causeMsg = error.cause?.message ?: ""

        if (httpCode == 401 || httpCode == 403 ||
            error.errorCode in listOf(
                PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
                PlaybackException.ERROR_CODE_DRM_UNSPECIFIED
            ) || message.contains("drm", ignoreCase = true) || causeMsg.contains("drm", ignoreCase = true)
        ) {
            return "Stream requires authorization"
        }

        if (httpCode == 404 || httpCode == 410) {
            return "Stream unavailable"
        }

        if (error.errorCode in listOf(
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED
            ) || message.contains("timeout", ignoreCase = true) || causeMsg.contains("timeout", ignoreCase = true)
        ) {
            return "Stream temporarily offline"
        }

        return "Unable to load stream"
    }

    private class LiveStreamLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val ex = loadErrorInfo.exception
            val responseCode = (ex as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                ?: (ex.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

            if ((responseCode == 401 || responseCode == 403 || responseCode == 410) && loadErrorInfo.errorCount > 2) {
                return C.TIME_UNSET
            }
            return minOf(loadErrorInfo.errorCount * 500L, 2000L)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 4
    }
}
