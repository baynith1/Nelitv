package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
 * User-configurable policy for Battery-Aware Adaptive Playback CPU optimization.
 */
enum class BatteryOptimizationMode(val label: String, val subtitle: String) {
    AUTO_BATTERY_AWARE(
        label = "Auto Battery-Aware",
        subtitle = "Adapts CPU, FPS & resolution on low battery or background load"
    ),
    ECO_BATTERY_SAVER(
        label = "Always Eco Saver",
        subtitle = "Caps at 480p @ 30fps & throttles background CPU wakeups"
    ),
    PERFORMANCE_UNRESTRICTED(
        label = "Max Performance",
        subtitle = "Full 1080p @ 60fps in foreground unless backgrounded"
    )
}

/**
 * Battery & background power profiles that govern ExoPlayer decoder resolution cap, frame rate limit,
 * buffer window sizing, progressive extractor check intervals, and main-thread telemetry polling frequency
 * to reduce CPU and thermal usage during low battery or background loading states.
 */
enum class BatteryPowerProfile(
    val maxQualityTierCap: AdaptiveQualityTier,
    val maxFrameRate: Int,
    val telemetryPollIntervalLiveMs: Long,
    val telemetryPollIntervalVodMs: Long,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val backBufferMs: Int,
    val continueLoadingCheckIntervalBytes: Int,
    val badgeLabel: String,
    val statusDescription: String,
    val isCpuSavingActive: Boolean
) {
    OPTIMAL_POWER(
        maxQualityTierCap = AdaptiveQualityTier.FULL_HD_1080P,
        maxFrameRate = 60,
        telemetryPollIntervalLiveMs = 600L,
        telemetryPollIntervalVodMs = 300L,
        minBufferMs = 8_000,
        maxBufferMs = 50_000,
        backBufferMs = 15_000,
        continueLoadingCheckIntervalBytes = 1024 * 1024,
        badgeLabel = "Optimal Power (60fps)",
        statusDescription = "Full hardware acceleration • Normal CPU & buffer profile",
        isCpuSavingActive = false
    ),
    LOW_BATTERY_SAVER(
        maxQualityTierCap = AdaptiveQualityTier.STANDARD_480P,
        maxFrameRate = 30,
        telemetryPollIntervalLiveMs = 1_200L,
        telemetryPollIntervalVodMs = 1_000L,
        minBufferMs = 5_000,
        maxBufferMs = 22_000,
        backBufferMs = 6_000,
        continueLoadingCheckIntervalBytes = 2 * 1024 * 1024,
        badgeLabel = "Low Battery Eco (480p•30fps)",
        statusDescription = "CPU Saver Active: 30fps cap, 480p decode & reduced polling",
        isCpuSavingActive = true
    ),
    CRITICAL_BATTERY_SAVER(
        maxQualityTierCap = AdaptiveQualityTier.DATA_SAVER_360P,
        maxFrameRate = 24,
        telemetryPollIntervalLiveMs = 1_800L,
        telemetryPollIntervalVodMs = 1_500L,
        minBufferMs = 4_000,
        maxBufferMs = 14_000,
        backBufferMs = 3_000,
        continueLoadingCheckIntervalBytes = 3 * 1024 * 1024,
        badgeLabel = "Critical Battery Saver (360p•24fps)",
        statusDescription = "Max CPU Saver: 24fps cap, 360p decode & minimal wakeups",
        isCpuSavingActive = true
    ),
    BACKGROUND_LOADING_SAVER(
        maxQualityTierCap = AdaptiveQualityTier.LOW_BANDO_240P,
        maxFrameRate = 24,
        telemetryPollIntervalLiveMs = 2_200L,
        telemetryPollIntervalVodMs = 2_000L,
        minBufferMs = 3_500,
        maxBufferMs = 12_000,
        backBufferMs = 2_000,
        continueLoadingCheckIntervalBytes = 4 * 1024 * 1024,
        badgeLabel = "Background CPU Saver",
        statusDescription = "Background/PiP Load: Throttled decoder & chunk wakeups",
        isCpuSavingActive = true
    )
}

/**
 * Snapshot of real-time Android device battery & power state used to drive battery-aware playback adaptation.
 */
data class DeviceBatterySnapshot(
    val batteryLevelPct: Int = 85,
    val isCharging: Boolean = false,
    val isOsPowerSaveMode: Boolean = false,
    val isBackgroundLoading: Boolean = false,
    val isPictureInPicture: Boolean = false
)

/**
 * Adaptive stream quality ladder ordered from highest quality (index 0) to emergency low-bandwidth (index 4).
 * Prioritizes high-quality HD streams when bandwidth & buffer are healthy, and dynamically steps down
 * when bandwidth drops, rebuffering stalls occur, or battery/background CPU saver caps are active.
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
        description = "Dynamically Downscaled for Bandwidth / Power Stability"
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
        description = "Emergency Rebuffer / Background CPU Protection (240p)"
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
    val batteryOptimizationMode: BatteryOptimizationMode = BatteryOptimizationMode.AUTO_BATTERY_AWARE,
    val batteryPowerProfile: BatteryPowerProfile = BatteryPowerProfile.OPTIMAL_POWER,
    val batteryLevelPct: Int = 85,
    val isBatteryCharging: Boolean = false,
    val isOsPowerSaveMode: Boolean = false,
    val isBackgroundLoadingActive: Boolean = false,
    val isCpuSavingActive: Boolean = false,
    val activeMaxFrameRate: Int = 60,
    val connectionLabel: String = "Mobile Data / Wi-Fi",
    val autoSkipNotice: String? = null,
    val activeAudioLanguage: String = "sw",
    val preferredAudioLanguage: String = "sw",
    val languageSwitchNotice: String? = null,
    val liveAutoFixCount: Int = 0,
    val isLiveAutoFixEnabled: Boolean = true,
    val lastAutoFixReason: String? = null
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
 * - Implements a Battery-Aware Adaptive Playback Strategy (`BatteryPowerProfile`) that monitors device
 *   battery level, charging state, OS Power Save Mode, and background/PiP loading states to dynamically
 *   cap video decoding frame rate (`30fps`/`24fps`), cap resolution (`480p`/`360p`/`240p`), disable
 *   off-screen background video track decoding, and throttle main-thread telemetry wakeups to reduce CPU usage.
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
         * Battery thresholds for Battery-Aware Adaptive Playback CPU optimization.
         */
        const val LOW_BATTERY_THRESHOLD_PCT = 25
        const val CRITICAL_BATTERY_THRESHOLD_PCT = 12

        /**
         * Live Stream Freeze / Stall Auto-Fix thresholds:
         * Automatically invokes [syncToLiveEdge] and stream recovery whenever a Live TV stream freezes
         * or stalls for >= 2,000 ms so the user never needs to manually click the bottom
         * "LIVE STREAM • CONTINUOUS REAL-TIME PLAYBACK" button.
         */
        const val LIVE_STREAM_STALL_AUTO_FIX_THRESHOLD_MS = 2_000L
        const val LIVE_STREAM_AUTO_FIX_COOLDOWN_MS = 1_500L

        /**
         * Computes the target [BatteryPowerProfile] based on real-time device battery level,
         * charging state, OS Power Save mode, background/PiP loading state, and user optimization policy.
         */
        fun computeBatteryPowerProfile(
            batteryLevelPct: Int,
            isCharging: Boolean,
            isOsPowerSaveMode: Boolean,
            isBackgroundLoading: Boolean,
            isPictureInPicture: Boolean = false,
            optimizationMode: BatteryOptimizationMode = BatteryOptimizationMode.AUTO_BATTERY_AWARE
        ): BatteryPowerProfile {
            if (isBackgroundLoading) {
                return BatteryPowerProfile.BACKGROUND_LOADING_SAVER
            }
            if (optimizationMode == BatteryOptimizationMode.ECO_BATTERY_SAVER) {
                return if (!isCharging && batteryLevelPct in 1..CRITICAL_BATTERY_THRESHOLD_PCT) {
                    BatteryPowerProfile.CRITICAL_BATTERY_SAVER
                } else {
                    BatteryPowerProfile.LOW_BATTERY_SAVER
                }
            }
            if (optimizationMode == BatteryOptimizationMode.PERFORMANCE_UNRESTRICTED) {
                return if (isPictureInPicture) {
                    BatteryPowerProfile.LOW_BATTERY_SAVER
                } else {
                    BatteryPowerProfile.OPTIMAL_POWER
                }
            }
            // AUTO_BATTERY_AWARE:
            return when {
                !isCharging && batteryLevelPct in 1..CRITICAL_BATTERY_THRESHOLD_PCT ->
                    BatteryPowerProfile.CRITICAL_BATTERY_SAVER
                (!isCharging && batteryLevelPct in 1..LOW_BATTERY_THRESHOLD_PCT) || isOsPowerSaveMode || isPictureInPicture ->
                    BatteryPowerProfile.LOW_BATTERY_SAVER
                else ->
                    BatteryPowerProfile.OPTIMAL_POWER
            }
        }

        /**
         * Computes the target [AdaptiveQualityTier] by prioritizing high-quality HD streams when network
         * bandwidth and battery are sufficient, and dynamically downscaling based on bandwidth constraints,
         * active buffering stalls, consecutive rebuffer count, and [BatteryPowerProfile] CPU saver caps.
         */
        fun computeAdaptiveQualityTier(
            estimatedBitrateBps: Long,
            bufferedDurationMs: Long,
            isBuffering: Boolean,
            consecutiveRebufferCount: Int,
            isLowBandoNetwork: Boolean = false,
            batteryPowerProfile: BatteryPowerProfile = BatteryPowerProfile.OPTIMAL_POWER
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

            // 3. Enforce battery-aware CPU & background loading quality cap so low-battery or background
            //    states do not waste CPU cycles decoding high-bitrate 1080p/720p video frames.
            val cappedOrdinal = maxOf(resolvedTier.ordinal, batteryPowerProfile.maxQualityTierCap.ordinal)
                .coerceIn(0, AdaptiveQualityTier.entries.lastIndex)
            return AdaptiveQualityTier.entries[cappedOrdinal]
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
    private var lastAppliedTrackSelectorKey: String = ""
    private var cachedOfflineStreamChannelId: String = ""
    private var cachedIsLocalOfflineStream: Boolean = false

    // Live Stream Freeze / Stall Auto-Fix Telemetry
    private var lastLiveObservedPositionMs: Long = C.TIME_UNSET
    private var lastLivePositionProgressRealtimeMs: Long = 0L
    private var lastLiveAutoFixRealtimeMs: Long = 0L
    private var consecutiveLiveStallAutoFixes: Int = 0
    private var totalLiveAutoFixCount: Int = 0
    private var lastAutoFixReasonLabel: String? = null

    private val sharedBaseHttpDataSourceFactory: DefaultHttpDataSource.Factory by lazy {
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(7_000)
            .setUserAgent(TokenManager.USER_AGENT)
        TokenManager.injectHeadersIntoFactory(factory, channel.streamUrl)
    }

    private val sharedExtractorsFactory: DefaultExtractorsFactory by lazy {
        DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setConstantBitrateSeekingAlwaysEnabled(true)
            .setMp4ExtractorFlags(Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
    }

    // Battery-Aware Adaptive Playback & Background CPU Saver Telemetry
    private var batteryOptimizationMode: BatteryOptimizationMode = BatteryOptimizationMode.AUTO_BATTERY_AWARE
    private var currentBatteryProfile: BatteryPowerProfile = BatteryPowerProfile.OPTIMAL_POWER
    private var cachedBatterySnapshot: DeviceBatterySnapshot = DeviceBatterySnapshot()
    private var lastBatteryPollTimeMs: Long = 0L
    private var isBackgroundLoadingActive: Boolean = false
    private var isPictureInPictureActive: Boolean = false
    private var isPowerBroadcastReceiverRegistered: Boolean = false
    private var manualBatterySnapshotOverride: DeviceBatterySnapshot? = null

    private val powerStateBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Immediately refresh battery snapshot & apply battery-aware ExoPlayer track/CPU limits
            readDeviceBatterySnapshot(forceRefresh = true)
            evaluateAndApplyBatteryAwareConfig()
        }
    }

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

    private val powerManager: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val batteryManager: BatteryManager? by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
    }

    /**
     * Reads real-time device battery level, charging status, and OS Power Save Mode using
     * Android's [BatteryManager], sticky [Intent.ACTION_BATTERY_CHANGED], and [PowerManager].
     */
    fun readDeviceBatterySnapshot(forceRefresh: Boolean = false): DeviceBatterySnapshot {
        manualBatterySnapshotOverride?.let { override ->
            cachedBatterySnapshot = override.copy(
                isBackgroundLoading = isBackgroundLoadingActive,
                isPictureInPicture = isPictureInPictureActive
            )
            return cachedBatterySnapshot
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (!forceRefresh && lastBatteryPollTimeMs > 0L && now - lastBatteryPollTimeMs < 4_000L) {
            cachedBatterySnapshot = cachedBatterySnapshot.copy(
                isBackgroundLoading = isBackgroundLoadingActive,
                isPictureInPicture = isPictureInPictureActive
            )
            return cachedBatterySnapshot
        }
        lastBatteryPollTimeMs = now

        var levelPct = 85
        var charging = false
        try {
            val stickyIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (stickyIntent != null) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    levelPct = ((level * 100f) / scale.toFloat()).toInt().coerceIn(1, 100)
                } else {
                    val propCap = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                    if (propCap in 1..100) {
                        levelPct = propCap
                    }
                }
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = stickyIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged != 0
            } else {
                val propCap = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (propCap in 1..100) {
                    levelPct = propCap
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    charging = batteryManager?.isCharging == true
                }
            }
        } catch (_: Exception) {
        }

        val osPowerSave = try {
            powerManager?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }

        cachedBatterySnapshot = DeviceBatterySnapshot(
            batteryLevelPct = levelPct,
            isCharging = charging,
            isOsPowerSaveMode = osPowerSave,
            isBackgroundLoading = isBackgroundLoadingActive,
            isPictureInPicture = isPictureInPictureActive
        )
        return cachedBatterySnapshot
    }

    private fun resolveActiveBatteryPowerProfile(forceRefreshBattery: Boolean = false): BatteryPowerProfile {
        val snap = readDeviceBatterySnapshot(forceRefresh = forceRefreshBattery)
        val profile = computeBatteryPowerProfile(
            batteryLevelPct = snap.batteryLevelPct,
            isCharging = snap.isCharging,
            isOsPowerSaveMode = snap.isOsPowerSaveMode,
            isBackgroundLoading = snap.isBackgroundLoading,
            isPictureInPicture = snap.isPictureInPicture,
            optimizationMode = batteryOptimizationMode
        )
        currentBatteryProfile = profile
        return profile
    }

    private fun registerPowerBroadcastReceiverIfNeeded() {
        if (isPowerBroadcastReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_LOW)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(powerStateBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(powerStateBroadcastReceiver, filter)
            }
            isPowerBroadcastReceiverRegistered = true
        } catch (_: Exception) {
        }
    }

    private fun unregisterPowerBroadcastReceiverIfNeeded() {
        if (!isPowerBroadcastReceiverRegistered) return
        try {
            context.unregisterReceiver(powerStateBroadcastReceiver)
        } catch (_: Exception) {
        }
        isPowerBroadcastReceiverRegistered = false
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

            // Refresh device battery & background power profile
            val activePowerProfile = resolveActiveBatteryPowerProfile(forceRefreshBattery = false)

            // Dynamic buffering, bandwidth & battery-aware adaptation during active playback/buffering
            if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
                if (player.playbackState == Player.STATE_BUFFERING) {
                    if (bufferingEnteredAtRealtimeMs > 0L && now - bufferingEnteredAtRealtimeMs >= 4_500L) {
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

            // If backgrounded (not in PiP) and paused/idle, stop scheduling UI polling wakeups to save CPU
            if (isBackgroundLoadingActive && !isPictureInPictureActive &&
                !player.isPlaying && player.playbackState != Player.STATE_BUFFERING
            ) {
                updatePlaybackInfo()
                return
            }

            if (channel.isLiveBroadcast) {
                // Ensure Live TV never freezes or gets stuck: run continuous Auto-Fix stall detection
                if (!pausedByCallOrExternalAudio && !isBackgroundLoadingActive) {
                    checkAndAutoFixLiveStreamStall(elapsedRealtimeMs = now)
                }
                updatePlaybackInfo()
                mainHandler.postDelayed(this, activePowerProfile.telemetryPollIntervalLiveMs)
            } else {
                updatePlaybackInfo()
                mainHandler.postDelayed(this, activePowerProfile.telemetryPollIntervalVodMs)
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
            val maxReconnects = if (channel.isLiveBroadcast) 1 else 2
            if (autoReconnectAttempts < maxReconnects) {
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
                if (channel.isLiveBroadcast) {
                    Thread {
                        try {
                            TokenManager.handleAuthenticationFailureBlocking(
                                failedUrl = channel.streamUrl,
                                httpStatusCode = httpCode
                            )
                        } catch (_: Exception) {
                        }
                        mainHandler.post {
                            exoPlayer?.let { p ->
                                val backupMatch = com.example.data.ChannelRepository.findBackupChannelFor(channel)
                                val candidateUrl = channel.backupStreamUrl
                                    .ifBlank { backupMatch?.streamUrl.orEmpty() }
                                    .ifBlank { channel.streamUrl }
                                val mergedClearKeys = com.example.data.ChannelRepository.resolveClearKeysForChannel(
                                    channel.copy(clearKeys = (backupMatch?.clearKeys ?: emptyMap()) + channel.clearKeys)
                                )
                                val normalizedStreamUrl = com.example.data.ChannelRepository.normalizeDashStreamUrl(candidateUrl)
                                TokenManager.injectHeadersIntoFactory(sharedBaseHttpDataSourceFactory, normalizedStreamUrl)
                                channel = channel.copy(
                                    streamUrl = normalizedStreamUrl,
                                    backupStreamUrl = channel.backupStreamUrl.ifBlank {
                                        backupMatch?.streamUrl?.let { com.example.data.ChannelRepository.normalizeDashStreamUrl(it) }.orEmpty()
                                    },
                                    clearKeys = mergedClearKeys,
                                    encryptionType = if (mergedClearKeys.isNotEmpty()) "clearkey" else channel.encryptionType
                                )
                                _currentChannel.value = channel
                                loadChannelStream(p, preserveVodPosition = false)
                            }
                        }
                    }.start()
                } else {
                    mainHandler.postDelayed({
                        exoPlayer?.let { p ->
                            if (resumePos > 0L) {
                                p.seekTo(resumePos)
                            }
                            p.prepare()
                            p.play()
                        }
                    }, 300L)
                }
                return
            }

            val friendlyMsg = mapPlaybackError(error)
            val techMsg = if (channel.isLiveBroadcast || httpCode == 401 || httpCode == 403) {
                null
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
     * Updates the background loading or Picture-in-Picture state so ExoPlayer immediately reduces
     * CPU decoder workload, frame rate, wake locks, and UI telemetry polling during background or PiP states.
     */
    fun setBackgroundLoadingState(
        isBackgroundLoading: Boolean,
        isPictureInPicture: Boolean = false
    ): BatteryPowerProfile {
        isBackgroundLoadingActive = isBackgroundLoading
        isPictureInPictureActive = isPictureInPicture
        val profile = evaluateAndApplyBatteryAwareConfig()
        if (isBackgroundLoading && !isPictureInPicture) {
            exoPlayer?.setWakeMode(C.WAKE_MODE_NONE)
        } else {
            exoPlayer?.setWakeMode(
                if (profile.isCpuSavingActive) C.WAKE_MODE_NONE else C.WAKE_MODE_NETWORK
            )
            mainHandler.removeCallbacks(positionUpdateRunnable)
            mainHandler.post(positionUpdateRunnable)
        }
        return profile
    }

    /**
     * Sets the user's [BatteryOptimizationMode] policy and immediately re-evaluates ExoPlayer's
     * battery-aware track selection, frame rate cap, and CPU polling intervals.
     */
    fun setBatteryOptimizationMode(mode: BatteryOptimizationMode): BatteryPowerProfile {
        batteryOptimizationMode = mode
        return evaluateAndApplyBatteryAwareConfig()
    }

    fun cycleBatteryOptimizationMode(): BatteryOptimizationMode {
        val entries = BatteryOptimizationMode.entries
        val nextIndex = (entries.indexOf(batteryOptimizationMode) + 1) % entries.size
        val nextMode = entries[nextIndex]
        setBatteryOptimizationMode(nextMode)
        return nextMode
    }

    /**
     * Allows deterministic testing or runtime simulation of a specific [DeviceBatterySnapshot]
     * (pass `null` to return to live Android [BatteryManager]/[PowerManager] hardware queries).
     */
    fun setDeviceBatterySnapshotOverrideForTesting(snapshot: DeviceBatterySnapshot?): BatteryPowerProfile {
        manualBatterySnapshotOverride = snapshot
        if (snapshot != null) {
            isBackgroundLoadingActive = snapshot.isBackgroundLoading
            isPictureInPictureActive = snapshot.isPictureInPicture
        }
        return evaluateAndApplyBatteryAwareConfig(forceRefreshBattery = true)
    }

    /**
     * Evaluates current device battery state, charging status, OS Power Save Mode, and background/PiP
     * loading state to apply CPU-saving parameters to ExoPlayer.
     */
    fun evaluateAndApplyBatteryAwareConfig(forceRefreshBattery: Boolean = false): BatteryPowerProfile {
        val profile = resolveActiveBatteryPowerProfile(forceRefreshBattery = forceRefreshBattery)
        val disableOffscreenVideoTrack = isBackgroundLoadingActive && !isPictureInPictureActive

        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            evaluateAndApplyAdaptiveTrackSelection(
                estimatedBitrateBps = latestEstimatedBandwidthBps,
                forceBufferingState = _uiState.value is PlayerUiState.Buffering,
                batteryProfileOverride = profile
            )
        } else {
            // Even in manual quality modes, enforce frame rate cap and off-screen background video track disabling
            trackSelector?.let { selector ->
                selector.setParameters(
                    selector.buildUponParameters()
                        .setMaxVideoFrameRate(profile.maxFrameRate)
                        .setRendererDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                )
            }
            exoPlayer?.let { player ->
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoFrameRate(profile.maxFrameRate)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .build()
                player.setWakeMode(
                    if (profile.isCpuSavingActive || disableOffscreenVideoTrack) C.WAKE_MODE_NONE else C.WAKE_MODE_NETWORK
                )
            }
            updatePlaybackInfo()
        }
        return profile
    }

    /**
     * Evaluates current network bandwidth, buffering state, and battery power profile to dynamically update
     * ExoPlayer's [DefaultTrackSelector] parameters for seamless, battery-aware adaptive playback.
     */
    fun evaluateAndApplyAdaptiveTrackSelection(
        estimatedBitrateBps: Long = latestEstimatedBandwidthBps,
        bufferedDurationMs: Long = exoPlayer?.let { (it.bufferedPosition - it.currentPosition).coerceAtLeast(0L) } ?: 0L,
        forceBufferingState: Boolean = _uiState.value is PlayerUiState.Buffering,
        rebufferCountOverride: Int = consecutiveRebufferCount,
        batteryProfileOverride: BatteryPowerProfile? = null
    ): AdaptiveQualityTier {
        latestEstimatedBandwidthBps = estimatedBitrateBps.coerceAtLeast(100_000L)
        consecutiveRebufferCount = rebufferCountOverride.coerceIn(0, 4)

        val activePowerProfile = batteryProfileOverride ?: resolveActiveBatteryPowerProfile(forceRefreshBattery = false)
        currentBatteryProfile = activePowerProfile
        val disableOffscreenVideoTrack = isBackgroundLoadingActive && !isPictureInPictureActive

        val resolvedTier = computeAdaptiveQualityTier(
            estimatedBitrateBps = latestEstimatedBandwidthBps,
            bufferedDurationMs = bufferedDurationMs,
            isBuffering = forceBufferingState,
            consecutiveRebufferCount = consecutiveRebufferCount,
            isLowBandoNetwork = isLowBandoNetworkDetected() && estimatedBitrateBps < 350_000L,
            batteryPowerProfile = activePowerProfile
        )
        currentAdaptiveTier = resolvedTier
        activeTrackResolutionLabel = if (activePowerProfile.isCpuSavingActive) {
            "${resolvedTier.badgeLabel} • ${activePowerProfile.maxFrameRate}fps Eco"
        } else {
            resolvedTier.badgeLabel
        }

        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            val isOfflineLocalFile = isPlayingLocalOfflineStream()
            val maxW = if (isOfflineLocalFile) Int.MAX_VALUE else resolvedTier.maxWidth
            val maxH = if (isOfflineLocalFile) Int.MAX_VALUE else resolvedTier.maxHeight
            val maxBr = if (isOfflineLocalFile) Int.MAX_VALUE else resolvedTier.maxBitrateBps
            // Use 0 for minW/minH/minBr in AUTO_ADAPTIVE so ExoPlayer starts video immediately on the fastest
            // keyframe without stalling for a heavy 1080p segment, then smoothly scales up to maxW/maxH
            val minW = 0
            val minH = 0
            val minBr = 0
            val forceLowest = if (isOfflineLocalFile) {
                false
            } else {
                resolvedTier.forceLowestBitrate ||
                    activePowerProfile == BatteryPowerProfile.CRITICAL_BATTERY_SAVER ||
                    activePowerProfile == BatteryPowerProfile.BACKGROUND_LOADING_SAVER
            }
            val selectorKey = "AUTO_${maxW}x${maxH}_${maxBr}_${activePowerProfile.maxFrameRate}_${forceLowest}_${disableOffscreenVideoTrack}_${activePowerProfile.isCpuSavingActive}"
            if (selectorKey != lastAppliedTrackSelectorKey) {
                lastAppliedTrackSelectorKey = selectorKey
                trackSelector?.let { selector ->
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setMaxVideoSize(maxW, maxH)
                            .setMaxVideoBitrate(maxBr)
                            .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                            .setMinVideoSize(minW, minH)
                            .setMinVideoBitrate(minBr)
                            .setForceLowestBitrate(forceLowest)
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                            .setExceedVideoConstraintsIfNecessary(true)
                            .setExceedRendererCapabilitiesIfNecessary(true)
                    )
                }
                exoPlayer?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setMaxVideoSize(maxW, maxH)
                        .setMaxVideoBitrate(maxBr)
                        .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                        .setMinVideoSize(minW, minH)
                        .setMinVideoBitrate(minBr)
                        .setForceLowestBitrate(forceLowest)
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                        .build()
                    player.setWakeMode(
                        if (activePowerProfile.isCpuSavingActive || disableOffscreenVideoTrack) C.WAKE_MODE_NONE else C.WAKE_MODE_NETWORK
                    )
                }
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
        registerPowerBroadcastReceiverIfNeeded()

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
        val initialBatteryProfile = resolveActiveBatteryPowerProfile(forceRefreshBattery = true)
        val disableOffscreenVideoTrack = isBackgroundLoadingActive && !isPictureInPictureActive

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
            isLowBandoNetwork = lowBandoActive,
            batteryPowerProfile = initialBatteryProfile
        )
        currentAdaptiveTier = initialTier
        activeTrackResolutionLabel = if (initialBatteryProfile.isCpuSavingActive) {
            "${initialTier.badgeLabel} • ${initialBatteryProfile.maxFrameRate}fps Eco"
        } else {
            initialTier.badgeLabel
        }

        val selector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(initialTier.maxWidth, initialTier.maxHeight)
                    .setMaxVideoBitrate(initialTier.maxBitrateBps)
                    .setMaxVideoFrameRate(initialBatteryProfile.maxFrameRate)
                    .setMinVideoSize(0, 0)
                    .setMinVideoBitrate(0)
                    .setForceLowestBitrate(
                        initialTier.forceLowestBitrate ||
                                initialBatteryProfile == BatteryPowerProfile.CRITICAL_BATTERY_SAVER ||
                                initialBatteryProfile == BatteryPowerProfile.BACKGROUND_LOADING_SAVER
                    )
                    .setRendererDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setPreferredAudioLanguages("sw", "swa", "kis", "en", "eng")
            )
        }
        trackSelector = selector

        val minBufMs = if (lowBandoActive) 2_000 else minOf(3_500, initialBatteryProfile.minBufferMs)
        val maxBufMs = if (lowBandoActive) minOf(16_000, initialBatteryProfile.maxBufferMs) else minOf(24_000, initialBatteryProfile.maxBufferMs)
        val backBufMs = minOf(6_000, initialBatteryProfile.backBufferMs)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minBufMs,
                /* maxBufferMs = */ maxBufMs,
                /* bufferForPlaybackMs = */ 150,
                /* bufferForPlaybackAfterRebufferMs = */ 300
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ backBufMs,
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
            .setWakeMode(
                if (initialBatteryProfile.isCpuSavingActive || disableOffscreenVideoTrack) {
                    C.WAKE_MODE_NONE
                } else {
                    C.WAKE_MODE_NETWORK
                }
            )
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
        lastLiveObservedPositionMs = C.TIME_UNSET
        lastLivePositionProgressRealtimeMs = 0L
        consecutiveLiveStallAutoFixes = 0
        forcedContainerMode = ForcedContainerMode.NONE
        hasAppliedSwahiliMovieIntroSkip = false
        lastKnownVodPositionMs = if (newChannel.shouldAutoSkipSwahiliMovieIntro) {
            SWAHILI_MOVIE_INTRO_SKIP_MS
        } else {
            0L
        }
        val activePowerProfile = resolveActiveBatteryPowerProfile(forceRefreshBattery = false)
        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            evaluateAndApplyAdaptiveTrackSelection(
                estimatedBitrateBps = latestEstimatedBandwidthBps,
                bufferedDurationMs = 5_000L,
                forceBufferingState = false,
                rebufferCountOverride = 0,
                batteryProfileOverride = activePowerProfile
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
            isDynamicallyDownscaled = currentAdaptiveTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal ||
                    activePowerProfile.isCpuSavingActive,
            batteryOptimizationMode = batteryOptimizationMode,
            batteryPowerProfile = activePowerProfile,
            batteryLevelPct = cachedBatterySnapshot.batteryLevelPct,
            isBatteryCharging = cachedBatterySnapshot.isCharging,
            isOsPowerSaveMode = cachedBatterySnapshot.isOsPowerSaveMode,
            isBackgroundLoadingActive = isBackgroundLoadingActive,
            isCpuSavingActive = activePowerProfile.isCpuSavingActive,
            activeMaxFrameRate = activePowerProfile.maxFrameRate,
            connectionLabel = detectConnectionLabel(),
            autoSkipNotice = if (newChannel.shouldAutoSkipSwahiliMovieIntro) {
                "Auto-skipped to 05:30 • Swahili Movie Intro Ads Skipped"
            } else null,
            activeAudioLanguage = "sw",
            preferredAudioLanguage = "sw",
            languageSwitchNotice = null,
            liveAutoFixCount = totalLiveAutoFixCount,
            isLiveAutoFixEnabled = true,
            lastAutoFixReason = lastAutoFixReasonLabel
        )
        val player = exoPlayer ?: initializePlayer()
        _uiState.value = PlayerUiState.Loading
        loadChannelStream(player, preserveVodPosition = false)
    }

    fun applyNetworkQualityMode(mode: NetworkQualityMode) {
        val activePowerProfile = resolveActiveBatteryPowerProfile(forceRefreshBattery = false)
        val disableOffscreenVideoTrack = isBackgroundLoadingActive && !isPictureInPictureActive
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
                    isLowBandoNetwork = isLowBandoNetworkDetected() && latestEstimatedBandwidthBps < 350_000L,
                    batteryPowerProfile = activePowerProfile
                )
            }
        }
        currentAdaptiveTier = mappedTier
        activeTrackResolutionLabel = if (activePowerProfile.isCpuSavingActive) {
            "${mappedTier.badgeLabel} • ${activePowerProfile.maxFrameRate}fps Eco"
        } else {
            mappedTier.badgeLabel
        }

        _playbackInfo.value = _playbackInfo.value.copy(
            networkMode = mode,
            adaptiveQualityTier = mappedTier,
            activeVideoResolutionLabel = activeTrackResolutionLabel,
            isDynamicallyDownscaled = (mode == NetworkQualityMode.AUTO_ADAPTIVE &&
                    mappedTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal) || activePowerProfile.isCpuSavingActive,
            batteryOptimizationMode = batteryOptimizationMode,
            batteryPowerProfile = activePowerProfile,
            batteryLevelPct = cachedBatterySnapshot.batteryLevelPct,
            isBatteryCharging = cachedBatterySnapshot.isCharging,
            isOsPowerSaveMode = cachedBatterySnapshot.isOsPowerSaveMode,
            isBackgroundLoadingActive = isBackgroundLoadingActive,
            isCpuSavingActive = activePowerProfile.isCpuSavingActive,
            activeMaxFrameRate = activePowerProfile.maxFrameRate,
            connectionLabel = detectConnectionLabel()
        )
        val player = exoPlayer ?: return
        player.trackSelectionParameters = when (mode) {
            NetworkQualityMode.ULTRA_LOW_BANDO_SAVER -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(426, 240)
                    .setMaxVideoBitrate(220_000)
                    .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                    .setMinVideoSize(0, 0)
                    .setMinVideoBitrate(0)
                    .setForceLowestBitrate(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .build()
            }
            NetworkQualityMode.WEAK_NETWORK_SAVER -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(640, 360)
                    .setMaxVideoBitrate(550_000)
                    .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                    .setMinVideoSize(0, 0)
                    .setMinVideoBitrate(0)
                    .setForceLowestBitrate(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .build()
            }
            NetworkQualityMode.STANDARD_480P -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(854, 480)
                    .setMaxVideoBitrate(1_100_000)
                    .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                    .setMinVideoSize(640, 360)
                    .setMinVideoBitrate(400_000)
                    .setForceLowestBitrate(false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .build()
            }
            NetworkQualityMode.AUTO_ADAPTIVE -> {
                trackSelector?.let { selector ->
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setMaxVideoSize(mappedTier.maxWidth, mappedTier.maxHeight)
                            .setMaxVideoBitrate(mappedTier.maxBitrateBps)
                            .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                            .setMinVideoSize(mappedTier.minPreferredWidth, mappedTier.minPreferredHeight)
                            .setMinVideoBitrate(mappedTier.minPreferredBitrateBps)
                            .setForceLowestBitrate(mappedTier.forceLowestBitrate)
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                            .setExceedVideoConstraintsIfNecessary(true)
                            .setExceedRendererCapabilitiesIfNecessary(true)
                    )
                }
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(mappedTier.maxWidth, mappedTier.maxHeight)
                    .setMaxVideoBitrate(mappedTier.maxBitrateBps)
                    .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                    .setMinVideoSize(mappedTier.minPreferredWidth, mappedTier.minPreferredHeight)
                    .setMinVideoBitrate(mappedTier.minPreferredBitrateBps)
                    .setForceLowestBitrate(mappedTier.forceLowestBitrate)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                    .build()
            }
            NetworkQualityMode.STRONG_NETWORK_HD -> {
                trackSelector?.let { selector ->
                    selector.setParameters(
                        selector.buildUponParameters()
                            .setMaxVideoSize(1920, 1080)
                            .setMaxVideoBitrate(5_500_000)
                            .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                            .setMinVideoSize(1280, 720)
                            .setMinVideoBitrate(1_500_000)
                            .setForceLowestBitrate(false)
                            .setRendererDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
                            .setExceedVideoConstraintsIfNecessary(true)
                    )
                }
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(5_500_000)
                    .setMaxVideoFrameRate(activePowerProfile.maxFrameRate)
                    .setMinVideoSize(1280, 720)
                    .setMinVideoBitrate(1_500_000)
                    .setForceLowestBitrate(false)
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disableOffscreenVideoTrack)
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
     * Ensures Live TV playback jumps directly to the current live broadcast edge without rewinding,
     * and recovers immediately if ExoPlayer was in IDLE, ENDED, or a frozen segment state.
     */
    fun syncToLiveEdge(reason: String = "manual_or_auto_live_edge_sync") {
        exoPlayer?.let { player ->
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            if (player.isCurrentMediaItemLive || (channel.isLiveBroadcast && !channel.isMp4)) {
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
            player.play()
            lastLivePositionProgressRealtimeMs = android.os.SystemClock.elapsedRealtime()
            lastLiveObservedPositionMs = player.currentPosition
        }
    }

    /**
     * Automatic Live Stream Freeze / Stall Watchdog & Auto-Fix Engine:
     * Detects when a Live TV stream freezes or gets stuck (e.g. playback position stops advancing,
     * prolonged buffering stall, paused while ready, or idle/ended state) and automatically executes
     * [triggerLiveStreamAutoFix] (which invokes [syncToLiveEdge] + adaptive track step-down + media
     * source reload if needed) so the user NEVER has to manually press the bottom
     * "LIVE STREAM • CONTINUOUS REAL-TIME PLAYBACK" button.
     */
    fun checkAndAutoFixLiveStreamStall(
        currentPositionMs: Long? = null,
        playbackState: Int? = null,
        isPlaying: Boolean? = null,
        elapsedRealtimeMs: Long = android.os.SystemClock.elapsedRealtime()
    ): Boolean {
        if (!channel.isLiveBroadcast || pausedByCallOrExternalAudio || isBackgroundLoadingActive) {
            return false
        }
        val player = exoPlayer
        val pos = currentPositionMs ?: player?.currentPosition ?: 0L
        val state = playbackState ?: player?.playbackState ?: Player.STATE_READY
        val playing = isPlaying ?: (player?.isPlaying == true)

        if (lastLivePositionProgressRealtimeMs == 0L) {
            lastLivePositionProgressRealtimeMs = elapsedRealtimeMs
            lastLiveObservedPositionMs = pos
        }

        val positionDeltaMs = if (lastLiveObservedPositionMs == C.TIME_UNSET) {
            0L
        } else {
            kotlin.math.abs(pos - lastLiveObservedPositionMs)
        }

        // If playback position is advancing normally while playing in STATE_READY, stream is healthy
        if (playing && state == Player.STATE_READY && positionDeltaMs >= 60L) {
            lastLiveObservedPositionMs = pos
            lastLivePositionProgressRealtimeMs = elapsedRealtimeMs
            consecutiveLiveStallAutoFixes = 0
            return false
        }

        val stalledDurationMs = (elapsedRealtimeMs - lastLivePositionProgressRealtimeMs).coerceAtLeast(0L)
        val stallReason = when {
            state == Player.STATE_IDLE || state == Player.STATE_ENDED ->
                "live_state_idle_or_ended"
            state == Player.STATE_READY && !playing ->
                "live_ready_not_playing"
            state == Player.STATE_BUFFERING && stalledDurationMs >= LIVE_STREAM_STALL_AUTO_FIX_THRESHOLD_MS ->
                "live_buffering_stall_${stalledDurationMs}ms"
            state == Player.STATE_READY && playing && positionDeltaMs < 60L && stalledDurationMs >= LIVE_STREAM_STALL_AUTO_FIX_THRESHOLD_MS ->
                "live_position_frozen_${stalledDurationMs}ms"
            else -> null
        } ?: return false

        val canRunAutoFixNow = lastLiveAutoFixRealtimeMs == 0L ||
            (elapsedRealtimeMs - lastLiveAutoFixRealtimeMs >= LIVE_STREAM_AUTO_FIX_COOLDOWN_MS)
        if (!canRunAutoFixNow) {
            return false
        }

        return triggerLiveStreamAutoFix(reason = stallReason, elapsedRealtimeMs = elapsedRealtimeMs)
    }

    /**
     * Immediately unfreezes a stuck Live TV stream by automatically running the live-edge recovery
     * ([syncToLiveEdge]), stepping down adaptive bitrate if needed, and reloading the live manifest
     * if repeated stalls occur.
     */
    fun triggerLiveStreamAutoFix(
        reason: String = "auto_fix_live_stall",
        elapsedRealtimeMs: Long = android.os.SystemClock.elapsedRealtime()
    ): Boolean {
        if (!channel.isLiveBroadcast) return false
        lastLiveAutoFixRealtimeMs = elapsedRealtimeMs
        lastLivePositionProgressRealtimeMs = elapsedRealtimeMs
        consecutiveLiveStallAutoFixes = (consecutiveLiveStallAutoFixes + 1).coerceAtMost(6)
        totalLiveAutoFixCount++
        lastAutoFixReasonLabel = reason

        if (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE) {
            consecutiveRebufferCount = (consecutiveRebufferCount + 1).coerceAtMost(4)
            evaluateAndApplyAdaptiveTrackSelection(forceBufferingState = true)
        }

        val player = exoPlayer
        if (player != null) {
            if (consecutiveLiveStallAutoFixes >= 3) {
                // Escalated self-healing: reload live stream manifest at live edge
                consecutiveLiveStallAutoFixes = 0
                loadChannelStream(player, preserveVodPosition = false)
            } else {
                syncToLiveEdge(reason = reason)
            }
            lastLiveObservedPositionMs = player.currentPosition
        }

        _playbackInfo.value = _playbackInfo.value.copy(
            liveAutoFixCount = totalLiveAutoFixCount,
            isLiveAutoFixEnabled = true,
            lastAutoFixReason = reason
        )
        return true
    }

    private fun isPlayingLocalOfflineStream(targetChannel: LiveChannel = channel): Boolean {
        val rawUrl = targetChannel.streamUrl.trim()
        if (rawUrl.startsWith("file:", ignoreCase = true) || rawUrl.startsWith("/")) {
            return true
        }
        // Live TV broadcasts are never local offline files; avoid any disk I/O on the main thread
        if (targetChannel.isLiveBroadcast && !targetChannel.id.startsWith("dl_")) {
            return false
        }
        val cacheKey = "${targetChannel.id}|${targetChannel.episodeId}|$rawUrl"
        if (cacheKey == cachedOfflineStreamChannelId) {
            return cachedIsLocalOfflineStream
        }
        val lookupId = targetChannel.episodeId.ifBlank { targetChannel.id }
        val resolved = com.example.data.OfflineDownloadManager.resolveLocalOfflineUriIfPresent(
            context = context,
            rawId = lookupId,
            fallbackStreamUrl = rawUrl
        )
        val isLocal = resolved.startsWith("file:", ignoreCase = true) || resolved.startsWith("/")
        cachedOfflineStreamChannelId = cacheKey
        cachedIsLocalOfflineStream = isLocal
        return isLocal
    }

    private fun loadChannelStream(player: ExoPlayer, preserveVodPosition: Boolean = false) {
        try {
            val isLocalOffline = isPlayingLocalOfflineStream(channel)
            val mediaSource = createMediaSource(channel)
            player.repeatMode = if (channel.isLiveBroadcast) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            val startPositionMs = when {
                channel.isLiveBroadcast -> C.TIME_UNSET
                preserveVodPosition && lastKnownVodPositionMs > 0L -> lastKnownVodPositionMs
                !isLocalOffline && channel.shouldAutoSkipSwahiliMovieIntro -> {
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
        val resolvedOfflineOrOnlineUrl = if (channel.isLiveBroadcast && !channel.id.startsWith("dl_")) {
            channel.streamUrl
        } else {
            val lookupId = channel.episodeId.ifBlank { channel.id }
            com.example.data.OfflineDownloadManager.resolveLocalOfflineUriIfPresent(
                context = context,
                rawId = lookupId,
                fallbackStreamUrl = channel.streamUrl
            )
        }
        val isLocalOfflineFile = resolvedOfflineOrOnlineUrl.startsWith("file:", ignoreCase = true) ||
                resolvedOfflineOrOnlineUrl.startsWith("/")
        val effectiveStreamUrl = if (isLocalOfflineFile) {
            resolvedOfflineOrOnlineUrl
        } else {
            com.example.data.ChannelRepository.resolvePlayableAzamManifestUrl(resolvedOfflineOrOnlineUrl)
        }
        val manifestUri = if (effectiveStreamUrl.startsWith("/")) {
            Uri.fromFile(java.io.File(effectiveStreamUrl))
        } else {
            Uri.parse(effectiveStreamUrl)
        }
        val encodedManifestQuery = manifestUri.encodedQuery

        val baseHttpDataSourceFactory = TokenManager.injectHeadersIntoFactory(
            sharedBaseHttpDataSourceFactory,
            effectiveStreamUrl
        )

        // Use ClearKeyDecryptingDataSource for all online DASH/MPD, ClearKey, or Azam cdntoken streams
        val resolvedChannelKeys = com.example.data.ChannelRepository.resolveClearKeysForChannel(channel)
        val mergedClearKeys = com.example.data.ChannelRepository.getAllKnownClearKeys() +
            resolvedChannelKeys +
            channel.clearKeys
        val needsClearKeyOrTokenWrapper = !isLocalOfflineFile && (
            channel.isDash ||
                effectiveStreamUrl.contains(".mpd", ignoreCase = true) ||
                (channel.isClearKey && channel.clearKeys.isNotEmpty()) ||
                resolvedChannelKeys.isNotEmpty() ||
                effectiveStreamUrl.contains("cdntoken=", ignoreCase = true) ||
                effectiveStreamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
                effectiveStreamUrl.contains("/live/eds/", ignoreCase = true)
            )

        val upstreamDataSourceFactory = if (needsClearKeyOrTokenWrapper) {
            val decryptor = if (mergedClearKeys.isNotEmpty()) {
                Mp4CencDecryptor(mergedClearKeys)
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

        val extractorsFactory = sharedExtractorsFactory

        val liveConfiguration = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(4_500L)
            .setMinOffsetMs(2_000L)
            .setMaxOffsetMs(16_000L)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.03f)
            .build()

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
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(manifestUri)
                    .setMediaId(channel.id)

                // For local offline files, let DefaultExtractorsFactory auto-sniff MP4, fMP4, MPEG-TS, or MKV headers
                if (!isLocalOfflineFile && !effectiveStreamUrl.substringBefore("?").endsWith(".ts", ignoreCase = true)) {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                }

                ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                    .setContinueLoadingCheckIntervalBytes(currentBatteryProfile.continueLoadingCheckIntervalBytes)
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
        if (channel.isLiveBroadcast) {
            Thread {
                try {
                    TokenManager.handleAuthenticationFailureBlocking(
                        failedUrl = channel.streamUrl,
                        httpStatusCode = null
                    )
                } catch (_: Exception) {
                }
                mainHandler.post {
                    val activePlayer = exoPlayer ?: return@post
                    val backupMatch = com.example.data.ChannelRepository.findBackupChannelFor(channel)
                    val failoverUrl = channel.backupStreamUrl
                        .ifBlank { backupMatch?.streamUrl.orEmpty() }
                        .ifBlank { channel.streamUrl }
                    val mergedClearKeys = com.example.data.ChannelRepository.resolveClearKeysForChannel(
                        channel.copy(clearKeys = (backupMatch?.clearKeys ?: emptyMap()) + channel.clearKeys)
                    )
                    val normalizedStreamUrl = com.example.data.ChannelRepository.resolvePlayableAzamManifestUrl(failoverUrl)
                    TokenManager.injectHeadersIntoFactory(sharedBaseHttpDataSourceFactory, normalizedStreamUrl)
                    channel = channel.copy(
                        streamUrl = normalizedStreamUrl,
                        backupStreamUrl = channel.backupStreamUrl.ifBlank {
                            backupMatch?.streamUrl?.let { com.example.data.ChannelRepository.resolvePlayableAzamManifestUrl(it) }.orEmpty()
                        },
                        clearKeys = mergedClearKeys,
                        encryptionType = if (mergedClearKeys.isNotEmpty()) "clearkey" else channel.encryptionType
                    )
                    _currentChannel.value = channel
                    loadChannelStream(activePlayer, preserveVodPosition = false)
                }
            }.start()
        } else {
            loadChannelStream(player, preserveVodPosition = true)
        }
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

    /**
     * Switches the audio language for Azam TV channels between Kiswahili (`"sw"`, Primary) and English (`"en"`).
     *
     * Rules:
     * 1. Primary language is always Kiswahili (`"sw"`).
     * 2. If the user selects English (`"en"`) while watching a program that is strictly Kiswahili
     *    (`channel.isKiswahiliOnlyProgram == true` or the active stream tracks only contain Swahili audio),
     *    the active language MUST NOT change — it stays `"sw"` (Kiswahili) and notifies the user.
     */
    fun switchAzamAudioLanguage(targetLanguageCode: String): String {
        val normalizedTarget = if (targetLanguageCode.lowercase().startsWith("en")) "en" else "sw"

        // Check if current ExoPlayer tracks haveloaded and only have Kiswahili audio
        val hasLoadedTracksButNoEnglish = exoPlayer?.currentTracks?.let { tracks ->
            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
            if (audioGroups.isEmpty()) {
                false
            } else {
                val allLangs = mutableListOf<String>()
                for (group in audioGroups) {
                    for (i in 0 until group.length) {
                        val lang = group.getTrackFormat(i).language?.lowercase().orEmpty()
                        if (lang.isNotBlank()) allLangs.add(lang)
                    }
                }
                allLangs.isNotEmpty() && allLangs.none { it.startsWith("en") }
            }
        } ?: false

        if (normalizedTarget == "en" && (channel.isKiswahiliOnlyProgram || hasLoadedTracksButNoEnglish)) {
            // Do NOT change the language when the program is in Kiswahili!
            applyPreferredAudioLanguagesToPlayer("sw")
            _playbackInfo.value = _playbackInfo.value.copy(
                activeAudioLanguage = "sw",
                preferredAudioLanguage = "en",
                languageSwitchNotice = "Kipindi hiki ni cha Kiswahili pekee — lugha haijabadilika (imebaki Kiswahili)."
            )
            return "sw"
        }

        applyPreferredAudioLanguagesToPlayer(normalizedTarget)
        val notice = if (normalizedTarget == "sw") {
            "Lugha ya Sauti: Kiswahili (Primary Language)"
        } else {
            "Lugha ya Sauti: English (Azam TV Dual Audio)"
        }
        _playbackInfo.value = _playbackInfo.value.copy(
            activeAudioLanguage = normalizedTarget,
            preferredAudioLanguage = normalizedTarget,
            languageSwitchNotice = notice
        )
        return normalizedTarget
    }

    fun clearLanguageSwitchNotice() {
        _playbackInfo.value = _playbackInfo.value.copy(languageSwitchNotice = null)
    }

    private fun applyPreferredAudioLanguagesToPlayer(langCode: String) {
        val preferredOrder = if (langCode == "en") {
            arrayOf("en", "eng", "sw", "swa", "kis")
        } else {
            arrayOf("sw", "swa", "kis", "en", "eng")
        }
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setPreferredAudioLanguages(*preferredOrder)
            )
        }
        exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setPreferredAudioLanguages(*preferredOrder)
                .build()
        }
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
        unregisterPowerBroadcastReceiverIfNeeded()
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
        val isDownscaled = (_playbackInfo.value.networkMode == NetworkQualityMode.AUTO_ADAPTIVE &&
                (currentAdaptiveTier.ordinal > AdaptiveQualityTier.HD_720P.ordinal || consecutiveRebufferCount > 0)) ||
                currentBatteryProfile.isCpuSavingActive

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
            batteryOptimizationMode = batteryOptimizationMode,
            batteryPowerProfile = currentBatteryProfile,
            batteryLevelPct = cachedBatterySnapshot.batteryLevelPct,
            isBatteryCharging = cachedBatterySnapshot.isCharging,
            isOsPowerSaveMode = cachedBatterySnapshot.isOsPowerSaveMode,
            isBackgroundLoadingActive = isBackgroundLoadingActive,
            isCpuSavingActive = currentBatteryProfile.isCpuSavingActive,
            activeMaxFrameRate = currentBatteryProfile.maxFrameRate,
            connectionLabel = detectConnectionLabel(),
            liveAutoFixCount = totalLiveAutoFixCount,
            isLiveAutoFixEnabled = true,
            lastAutoFixReason = lastAutoFixReasonLabel
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
        return "Kuna shida ya mtandao kwa sasa jaribu tena baadae"
    }

    private class LiveStreamLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val ex = loadErrorInfo.exception
            val responseCode = (ex as? HttpDataSource.InvalidResponseCodeException)?.responseCode
                ?: (ex.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode

            if ((responseCode == 401 || responseCode == 403 || responseCode == 404 || responseCode == 410) && loadErrorInfo.errorCount >= 1) {
                return C.TIME_UNSET
            }
            return minOf(loadErrorInfo.errorCount * 300L, 900L)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 1
    }
}
