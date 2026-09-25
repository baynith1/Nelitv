package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.ParserException
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
    WEAK_NETWORK_SAVER("Data Saver (360p)"),
    STANDARD_480P("Standard (480p)"),
    STRONG_NETWORK_HD("Full HD (720p/1080p)")
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

        init {
            try {
                androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
            } catch (_: Throwable) {}
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
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoReconnectAttempts = 0
    private var forcedContainerMode = ForcedContainerMode.NONE
    private var hasAppliedSwahiliMovieIntroSkip = false
    private var lastKnownVodPositionMs = 0L

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
            if (channel.isLiveBroadcast) {
                // Ensure Live TV always stays playing unless user exits
                if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                    player.seekToDefaultPosition()
                    player.play()
                }
            } else {
                updatePlaybackInfo()
                mainHandler.postDelayed(this, 300L)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.value = PlayerUiState.Buffering
                    if (!channel.isLiveBroadcast) {
                        updatePlaybackInfo()
                    }
                }
                Player.STATE_READY -> {
                    autoReconnectAttempts = 0
                    val player = exoPlayer
                    if (player != null && !hasAppliedSwahiliMovieIntroSkip && channel.shouldAutoSkipSwahiliMovieIntro) {
                        hasAppliedSwahiliMovieIntroSkip = true
                        val dur = player.duration
                        val canSkipTo530 = dur == C.TIME_UNSET || dur > (SWAHILI_MOVIE_INTRO_SKIP_MS + 10_000L)
                        if (canSkipTo530 && player.currentPosition < SWAHILI_MOVIE_INTRO_SKIP_MS - 3_000L) {
                            player.seekTo(SWAHILI_MOVIE_INTRO_SKIP_MS)
                        }
                    }
                    _uiState.value = PlayerUiState.Ready
                    updatePlaybackInfo()
                    mainHandler.removeCallbacks(positionUpdateRunnable)
                    mainHandler.post(positionUpdateRunnable)
                }
                Player.STATE_ENDED -> {
                    if (channel.isLiveBroadcast) {
                        // Live TV never ends; automatically re-sync to live broadcast edge
                        syncToLiveEdge()
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

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackInfo.value = _playbackInfo.value.copy(
                isPlaying = isPlaying,
                connectionLabel = detectConnectionLabel()
            )
            if (!channel.isLiveBroadcast) {
                mainHandler.removeCallbacks(positionUpdateRunnable)
                if (isPlaying) {
                    mainHandler.post(positionUpdateRunnable)
                }
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
            if (autoReconnectAttempts < 2 && httpCode != 401 && httpCode != 403 && httpCode != 404) {
                autoReconnectAttempts++
                _uiState.value = PlayerUiState.Buffering
                applyNetworkQualityMode(NetworkQualityMode.WEAK_NETWORK_SAVER)
                val resumePos = lastKnownVodPositionMs
                mainHandler.postDelayed({
                    exoPlayer?.let { p ->
                        if (channel.isLiveBroadcast) {
                            p.seekToDefaultPosition()
                        } else if (resumePos > 0L) {
                            p.seekTo(resumePos)
                        }
                        p.prepare()
                        p.play()
                    }
                }, 500L)
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

    private fun detectConnectionLabel(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val net = cm?.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            when {
                caps == null -> "Offline Mode"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                else -> "Online"
            }
        } catch (_: Exception) {
            "Mobile Data / Wi-Fi"
        }
    }

    private fun detectInitialBitrateEstimate(): Long {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val downKbps = caps?.linkDownstreamBandwidthKbps ?: 2000
            when {
                downKbps <= 800 -> 420_000L
                downKbps <= 2500 -> 950_000L
                else -> 1_800_000L
            }
        } catch (_: Exception) {
            1_000_000L
        }
    }

    fun initializePlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        autoReconnectAttempts = 0
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
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(initialBitrate)
            .build()

        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 4000,
            /* maxDurationForQualityDecreaseMs = */ 1500,
            /* minDurationToRetainAfterDiscardMs = */ 4000,
            /* bandwidthFraction = */ 0.75f
        )

        val maxInitialWidth = if (initialBitrate < 500_000L) 640 else 1280
        val maxInitialHeight = if (initialBitrate < 500_000L) 360 else 720
        val maxInitialVideoBitrate = if (initialBitrate < 500_000L) 650_000 else 2_400_000

        val trackSelector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(maxInitialWidth, maxInitialHeight)
                    .setMaxVideoBitrate(maxInitialVideoBitrate)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 6_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 15_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .apply {
                playWhenReady = true
                addListener(playerListener)
            }

        exoPlayer = player
        loadChannelStream(player, preserveVodPosition = false)
        return player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

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
        forcedContainerMode = ForcedContainerMode.NONE
        hasAppliedSwahiliMovieIntroSkip = false
        lastKnownVodPositionMs = if (newChannel.shouldAutoSkipSwahiliMovieIntro) {
            SWAHILI_MOVIE_INTRO_SKIP_MS
        } else {
            0L
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
        _playbackInfo.value = _playbackInfo.value.copy(
            networkMode = mode,
            connectionLabel = detectConnectionLabel()
        )
        val player = exoPlayer ?: return
        player.trackSelectionParameters = when (mode) {
            NetworkQualityMode.WEAK_NETWORK_SAVER -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(640, 360)
                    .setMaxVideoBitrate(550_000)
                    .setForceLowestBitrate(true)
                    .build()
            }
            NetworkQualityMode.STANDARD_480P -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(854, 480)
                    .setMaxVideoBitrate(1_100_000)
                    .setForceLowestBitrate(false)
                    .build()
            }
            NetworkQualityMode.AUTO_ADAPTIVE -> {
                val est = detectInitialBitrateEstimate()
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(if (est < 500_000L) 640 else 1280, if (est < 500_000L) 360 else 720)
                    .setMaxVideoBitrate(if (est < 500_000L) 650_000 else 2_400_000)
                    .setForceLowestBitrate(false)
                    .build()
            }
            NetworkQualityMode.STRONG_NETWORK_HD -> {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1920, 1080)
                    .setMaxVideoBitrate(4_500_000)
                    .setForceLowestBitrate(false)
                    .build()
            }
        }
    }

    fun cycleNetworkQualityMode() {
        val next = when (_playbackInfo.value.networkMode) {
            NetworkQualityMode.AUTO_ADAPTIVE -> NetworkQualityMode.WEAK_NETWORK_SAVER
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
            player.seekToDefaultPosition()
            player.playWhenReady = true
            player.play()
        }
    }

    private fun loadChannelStream(player: ExoPlayer, preserveVodPosition: Boolean = false) {
        try {
            val mediaSource = createMediaSource(channel)
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
            if (channel.isLiveBroadcast) {
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

        // Only use ClearKeyDecryptingDataSource when ClearKey DRM or Azam cdntoken propagation is needed.
        // For all Movies, Adult, Series, and standard HLS/MP4 streams, use native DefaultHttpDataSource
        // directly so HTTP 206 Range seeking is instantaneous and unthrottled.
        val needsClearKeyOrTokenWrapper =
            (channel.isClearKey && channel.clearKeys.isNotEmpty()) ||
                    effectiveStreamUrl.contains("cdntoken=", ignoreCase = true)

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
            .setTargetOffsetMs(2_500L)
            .setMinOffsetMs(1_200L)
            .setMaxOffsetMs(6_000L)
            .setMinPlaybackSpeed(0.97f)
            .setMaxPlaybackSpeed(1.04f)
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
        if (channel.isLiveBroadcast) {
            syncToLiveEdge()
            return
        }
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.playbackState == Player.STATE_ENDED) {
                    val restartPos = if (channel.shouldAutoSkipSwahiliMovieIntro) SWAHILI_MOVIE_INTRO_SKIP_MS else 0L
                    player.seekTo(restartPos)
                }
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
            if (channel.isLiveBroadcast) {
                // Returning to Live TV always jumps to the live edge, never rewinding back
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
            player.play()
        }
    }

    fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        exoPlayer?.let { player ->
            player.removeListener(playerListener)
            player.clearVideoSurface()
            player.stop()
            player.release()
        }
        exoPlayer = null
    }

    private fun updatePlaybackInfo() {
        val player = exoPlayer ?: return
        val isLive = channel.isLiveBroadcast
        val duration = player.duration
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        if (!isLive && currentPos > 0L) {
            lastKnownVodPositionMs = currentPos
        }
        val isSeekable = !isLive && (player.isCurrentMediaItemSeekable || duration > 0)
        _playbackInfo.value = _playbackInfo.value.copy(
            isPlaying = player.isPlaying,
            isLive = isLive,
            isSeekable = isSeekable,
            currentPosition = currentPos,
            bufferedPosition = player.bufferedPosition.coerceAtLeast(currentPos),
            duration = if (duration > 0) duration else C.TIME_UNSET,
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

            if (responseCode == 401 || responseCode == 403 || responseCode == 404 || responseCode == 410) {
                return C.TIME_UNSET
            }
            return minOf(loadErrorInfo.errorCount * 600L, 2400L)
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 3
    }
}
