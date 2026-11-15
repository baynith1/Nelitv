package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.load
import com.example.data.NeliSubscriptionManager
import com.example.ui.tv.NeliTvDetector
import com.inmobi.ads.AdMetaInfo
import com.inmobi.ads.InMobiAdRequestStatus
import com.inmobi.ads.InMobiBanner
import com.inmobi.ads.InMobiInterstitial
import com.inmobi.ads.InMobiNative
import com.inmobi.ads.listeners.BannerAdEventListener
import com.inmobi.ads.listeners.InterstitialAdEventListener
import com.inmobi.ads.listeners.NativeAdEventListener
import com.inmobi.media.ads.nativeAd.InMobiNativeViewData
import com.inmobi.sdk.InMobiSdk
import com.inmobi.sdk.SdkInitializationListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Snapshot wrapper for a loaded production [InMobiNative] creative.
 */
data class NeliInMobiNativeAdHolder(
    val nativeAd: InMobiNative,
    val title: String,
    val description: String,
    val iconUrl: String?,
    val ctaText: String,
    val advertiserName: String,
    val rating: Float,
    val isVideo: Boolean
)

/**
 * VOD VAST cue points supported in Movies & Series playback.
 */
enum class VodVastCue {
    PRE_ROLL,
    MID_ROLL_30,
    MID_ROLL_75,
    END_ROLL
}

/**
 * Deterministic active-playback accumulator for Live TV 15-minute VAST breaks.
 * Only counts time while Live TV is actively playing (excludes pauses, buffering, and ad display).
 */
class LiveTvAdTimerState(
    val intervalMs: Long = NeliAdManager.LIVE_VAST_INTERVAL_MS
) {
    var activeChannelId: String? = null
        private set
    var accumulatedActivePlaybackMs: Long = 0L
        private set

    fun onPlaybackStarted(channelId: String) {
        if (activeChannelId != channelId) {
            activeChannelId = channelId
            accumulatedActivePlaybackMs = 0L
        }
    }

    fun resetSession(channelId: String? = activeChannelId) {
        activeChannelId = channelId
        accumulatedActivePlaybackMs = 0L
    }

    fun recordTick(
        elapsedMs: Long,
        isPlaying: Boolean,
        isBuffering: Boolean,
        isAdShowing: Boolean
    ): Boolean {
        if (elapsedMs <= 0L || !isPlaying || isBuffering || isAdShowing) {
            return false
        }
        accumulatedActivePlaybackMs += elapsedMs
        return accumulatedActivePlaybackMs >= intervalMs
    }

    fun onBreakTriggeredAndCompleted() {
        accumulatedActivePlaybackMs = 0L
    }

    fun onBreakUnavailable(retryDelayMs: Long = 60_000L) {
        val clampedBackoff = retryDelayMs.coerceIn(1_000L, intervalMs)
        accumulatedActivePlaybackMs = (intervalMs - clampedBackoff).coerceAtLeast(0L)
    }
}

/**
 * Per-item cue tracker guaranteeing each of the 4 VOD VAST opportunities triggers at most once,
 * eliminating duplicate ad loops and preserving exact VOD playback positions across mid-rolls.
 */
class VodVastCueTracker {
    var activePlaybackKey: String? = null
        private set
    var preRollAttempted: Boolean = false
        private set
    var mid30Attempted: Boolean = false
        private set
    var mid75Attempted: Boolean = false
        private set
    var endRollAttempted: Boolean = false
        private set
    var savedResumePositionMs: Long = 0L
        private set

    fun ensureSession(playbackKey: String) {
        if (activePlaybackKey != playbackKey) {
            activePlaybackKey = playbackKey
            preRollAttempted = false
            mid30Attempted = false
            mid75Attempted = false
            endRollAttempted = false
            savedResumePositionMs = 0L
        }
    }

    fun markPreRollHandled() {
        preRollAttempted = true
    }

    fun evaluateMidRollDue(positionMs: Long, durationMs: Long): VodVastCue? {
        if (durationMs <= 10_000L || positionMs <= 0L) return null
        val pct30Ms = (durationMs * 30L) / 100L
        val pct75Ms = (durationMs * 75L) / 100L

        if (!mid75Attempted && positionMs >= pct75Ms) {
            // If viewer scrubbed directly past 30% into >= 75%, don't fire a delayed 30% loop later
            mid30Attempted = true
            mid75Attempted = true
            savedResumePositionMs = positionMs
            return VodVastCue.MID_ROLL_75
        }
        if (!mid30Attempted && positionMs >= pct30Ms) {
            mid30Attempted = true
            savedResumePositionMs = positionMs
            return VodVastCue.MID_ROLL_30
        }
        return null
    }

    fun markEndRollIfDue(): Boolean {
        if (endRollAttempted) return false
        endRollAttempted = true
        return true
    }
}

/**
 * Production-only InMobi Ad Manager for NeliTV.
 *
 * Credentials configured strictly for production:
 * - App ID: `com.nelitv.app`
 * - App Key / Account ID: `10000252102`
 * - Interstitial: `10000869536`
 * - Banner: `10000869537`
 * - Rewarded: `10000869538`
 * - Video / VAST: `10000869539`
 * - Native: `10000869540`
 */
object NeliAdManager {
    private const val TAG = "NeliTvInMobiAds"

    const val INMOBI_APP_ID = "com.nelitv.app"
    const val INMOBI_APP_KEY = "10000252102"

    const val PLACEMENT_INTERSTITIAL_ID = 10000869536L
    const val PLACEMENT_BANNER_ID = 10000869537L
    const val PLACEMENT_REWARDED_ID = 10000869538L
    const val PLACEMENT_VIDEO_VAST_ID = 10000869539L
    const val PLACEMENT_NATIVE_ID = 10000869540L

    // Live TV periodic VAST interval & post-ad live edge offset
    const val LIVE_VAST_INTERVAL_MS = 15 * 60 * 1000L
    const val LIVE_RECONNECT_BEHIND_EDGE_MS = 30_000L

    private const val CHANNEL_OPEN_DEBOUNCE_MS = 900L
    private const val DISPLAY_WATCHDOG_TIMEOUT_MS = 4_000L
    private const val VIDEO_VAST_PRELOAD_TIMEOUT_MS = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val initStarted = AtomicBoolean(false)
    private val initCompleted = AtomicBoolean(false)
    private val isInterstitialLoading = AtomicBoolean(false)
    private val isRewardedLoading = AtomicBoolean(false)
    private val isVideoVastLoading = AtomicBoolean(false)
    private val isNativeLoading = AtomicBoolean(false)

    private var interstitialAd: InMobiInterstitial? = null
    private var rewardedAd: InMobiInterstitial? = null
    private var videoVastAd: InMobiInterstitial? = null
    private var activeNativeAd: InMobiNative? = null

    private var lastChannelOpenClickMs: Long = 0L
    private var lastChannelOpenId: String = ""

    private var currentActiveInterstitialCallback: (() -> Unit)? = null
    private var currentActiveRewardedCallback: ((Boolean) -> Unit)? = null
    private var currentActiveVideoVastCallback: (() -> Unit)? = null

    private var pendingVideoVastPreloadSignal: CompletableDeferred<Boolean>? = null

    /**
     * Optional remote VAST tag URL (if supplied by backend config). Never hardcoded or mocked.
     */
    @Volatile
    var configuredRemoteVastTagUrl: String? = null
        private set

    fun updateConfiguredRemoteVastTagUrl(vastUrl: String?) {
        val clean = vastUrl?.trim().orEmpty()
        configuredRemoteVastTagUrl = if (
            clean.startsWith("https://", ignoreCase = true) ||
            clean.startsWith("http://", ignoreCase = true)
        ) {
            clean
        } else {
            null
        }
    }

    private val _nativeCardAdState = MutableStateFlow<NeliInMobiNativeAdHolder?>(null)
    val nativeCardAdState: StateFlow<NeliInMobiNativeAdHolder?> = _nativeCardAdState.asStateFlow()

    private val _isAdCurrentlyDisplaying = MutableStateFlow(false)
    val isAdCurrentlyDisplaying: StateFlow<Boolean> = _isAdCurrentlyDisplaying.asStateFlow()

    private val _sdkReadyFlow = MutableStateFlow(false)
    val sdkReadyFlow: StateFlow<Boolean> = _sdkReadyFlow.asStateFlow()

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (NeliTvDetector.isTelevision(appContext)) {
            Log.i(TAG, "Smart TV detected; InMobi advertising disabled.")
            return
        }
        if (initCompleted.get() || InMobiSdk.isSDKInitialized()) {
            initCompleted.set(true)
            _sdkReadyFlow.value = true
            preloadAllPlacements(appContext)
            return
        }
        if (!initStarted.compareAndSet(false, true)) {
            return
        }

        mainHandler.post {
            try {
                val consentObject = JSONObject().apply {
                    put(InMobiSdk.IM_GDPR_CONSENT_AVAILABLE, true)
                    put(InMobiSdk.IM_GDPR_CONSENT_GDPR_APPLIES, "0")
                }
                InMobiSdk.setLogLevel(InMobiSdk.LogLevel.ERROR)
                InMobiSdk.setIsAgeRestricted(false)
                InMobiSdk.init(
                    appContext,
                    INMOBI_APP_KEY,
                    consentObject,
                    object : SdkInitializationListener {
                        override fun onInitializationComplete(error: Error?) {
                            if (error == null) {
                                initCompleted.set(true)
                                _sdkReadyFlow.value = true
                                Log.i(TAG, "InMobi SDK initialized for $INMOBI_APP_ID.")
                                preloadAllPlacements(appContext)
                            } else {
                                initStarted.set(false)
                                initCompleted.set(false)
                                _sdkReadyFlow.value = false
                                Log.w(TAG, "InMobi SDK initialization reported error: ${error.message}")
                            }
                        }
                    }
                )
            } catch (t: Throwable) {
                initStarted.set(false)
                initCompleted.set(false)
                Log.w(TAG, "InMobi SDK initialization unavailable: ${t.message}")
            }
        }
    }

    private fun preloadAllPlacements(context: Context) {
        if (!isAdsAllowed(context) || !isDeviceOnline(context)) return
        preloadInterstitialAd(context)
        preloadRewardedAd(context)
        preloadVideoVastAd(context)
        preloadNativeAd(context)
    }

    /**
     * Enables/disables COPPA child-directed flag when entering or leaving Kids channels/content.
     */
    fun updateAgeRestrictedForKidsContent(isKidsContent: Boolean) {
        try {
            if (InMobiSdk.isSDKInitialized()) {
                InMobiSdk.setIsAgeRestricted(isKidsContent)
            }
        } catch (_: Throwable) {
        }
    }

    fun isAdsAllowed(context: Context): Boolean {
        if (NeliTvDetector.isTelevision(context)) return false
        if (NeliSubscriptionManager.subscriptionState.value.isActiveNow) return false
        return true
    }

    fun isDeviceOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            false
        }
    }

    private fun isSdkReady(): Boolean = initCompleted.get() || try {
        InMobiSdk.isSDKInitialized()
    } catch (_: Throwable) {
        false
    }

    fun preloadInterstitialAd(context: Context) {
        val appContext = context.applicationContext
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        if (!isSdkReady()) {
            initialize(appContext)
            return
        }
        if (interstitialAd?.isReady() == true) return
        if (!isInterstitialLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                val listener = object : InterstitialAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                        isInterstitialLoading.set(false)
                        Log.i(TAG, "Interstitial placement $PLACEMENT_INTERSTITIAL_ID loaded.")
                    }

                    override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isInterstitialLoading.set(false)
                        Log.d(TAG, "Interstitial placement $PLACEMENT_INTERSTITIAL_ID load failed: ${status.statusCode} ${status.message}")
                    }

                    override fun onAdFetchFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isInterstitialLoading.set(false)
                        Log.d(TAG, "Interstitial fetch failed: ${status.message}")
                    }

                    override fun onAdWillDisplay(ad: InMobiInterstitial) {
                        interstitialDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                        interstitialDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveInterstitialCallback
                        currentActiveInterstitialCallback = null
                        cb?.invoke()
                        preloadInterstitialAd(appContext)
                    }

                    override fun onAdDismissed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveInterstitialCallback
                        currentActiveInterstitialCallback = null
                        cb?.invoke()
                        preloadInterstitialAd(appContext)
                    }
                }
                val ad = interstitialAd ?: InMobiInterstitial(appContext, PLACEMENT_INTERSTITIAL_ID, listener).also {
                    interstitialAd = it
                }
                ad.setListener(listener)
                ad.load()
            } catch (t: Throwable) {
                isInterstitialLoading.set(false)
                Log.w(TAG, "Interstitial preload exception: ${t.message}")
            }
        }
    }

    fun preloadRewardedAd(context: Context) {
        val appContext = context.applicationContext
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        if (!isSdkReady()) {
            initialize(appContext)
            return
        }
        if (rewardedAd?.isReady() == true) return
        if (!isRewardedLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                var rewardGrantedForCurrentAd = false
                val listener = object : InterstitialAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                        isRewardedLoading.set(false)
                        Log.i(TAG, "Rewarded placement $PLACEMENT_REWARDED_ID loaded.")
                    }

                    override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isRewardedLoading.set(false)
                        Log.d(TAG, "Rewarded placement $PLACEMENT_REWARDED_ID load failed: ${status.statusCode} ${status.message}")
                    }

                    override fun onAdFetchFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isRewardedLoading.set(false)
                    }

                    override fun onAdWillDisplay(ad: InMobiInterstitial) {
                        rewardGrantedForCurrentAd = false
                        rewardedDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                        rewardedDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onRewardsUnlocked(ad: InMobiInterstitial, rewards: Map<Any, Any>) {
                        rewardGrantedForCurrentAd = true
                    }

                    override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveRewardedCallback
                        currentActiveRewardedCallback = null
                        cb?.invoke(false)
                        preloadRewardedAd(appContext)
                    }

                    override fun onAdDismissed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveRewardedCallback
                        currentActiveRewardedCallback = null
                        cb?.invoke(rewardGrantedForCurrentAd)
                        rewardGrantedForCurrentAd = false
                        preloadRewardedAd(appContext)
                    }
                }
                val ad = rewardedAd ?: InMobiInterstitial(appContext, PLACEMENT_REWARDED_ID, listener).also {
                    rewardedAd = it
                }
                ad.setListener(listener)
                ad.load()
            } catch (t: Throwable) {
                isRewardedLoading.set(false)
                Log.w(TAG, "Rewarded preload exception: ${t.message}")
            }
        }
    }

    fun isVideoVastAdReady(): Boolean {
        return try {
            isSdkReady() && videoVastAd?.isReady() == true
        } catch (_: Throwable) {
            false
        }
    }

    fun preloadVideoVastAd(context: Context) {
        val appContext = context.applicationContext
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) {
            pendingVideoVastPreloadSignal?.complete(false)
            return
        }
        if (!isSdkReady()) {
            initialize(appContext)
            pendingVideoVastPreloadSignal?.complete(false)
            return
        }
        if (videoVastAd?.isReady() == true) {
            pendingVideoVastPreloadSignal?.complete(true)
            return
        }
        if (!isVideoVastLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                val listener = object : InterstitialAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiInterstitial, info: AdMetaInfo) {
                        isVideoVastLoading.set(false)
                        pendingVideoVastPreloadSignal?.complete(true)
                        Log.i(TAG, "Video/VAST placement $PLACEMENT_VIDEO_VAST_ID loaded.")
                    }

                    override fun onAdLoadFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isVideoVastLoading.set(false)
                        pendingVideoVastPreloadSignal?.complete(false)
                        Log.d(TAG, "Video/VAST placement $PLACEMENT_VIDEO_VAST_ID load failed: ${status.statusCode} ${status.message}")
                    }

                    override fun onAdFetchFailed(ad: InMobiInterstitial, status: InMobiAdRequestStatus) {
                        isVideoVastLoading.set(false)
                        pendingVideoVastPreloadSignal?.complete(false)
                    }

                    override fun onAdWillDisplay(ad: InMobiInterstitial) {
                        videoVastDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onAdDisplayed(ad: InMobiInterstitial, info: AdMetaInfo) {
                        videoVastDisplayConfirmed.set(true)
                        _isAdCurrentlyDisplaying.value = true
                    }

                    override fun onAdDisplayFailed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveVideoVastCallback
                        currentActiveVideoVastCallback = null
                        cb?.invoke()
                        preloadVideoVastAd(appContext)
                    }

                    override fun onAdDismissed(ad: InMobiInterstitial) {
                        _isAdCurrentlyDisplaying.value = false
                        val cb = currentActiveVideoVastCallback
                        currentActiveVideoVastCallback = null
                        cb?.invoke()
                        preloadVideoVastAd(appContext)
                    }
                }
                val ad = videoVastAd ?: InMobiInterstitial(appContext, PLACEMENT_VIDEO_VAST_ID, listener).also {
                    videoVastAd = it
                }
                ad.setListener(listener)
                ad.load()
            } catch (t: Throwable) {
                isVideoVastLoading.set(false)
                pendingVideoVastPreloadSignal?.complete(false)
                Log.w(TAG, "Video/VAST preload exception: ${t.message}")
            }
        }
    }

    /**
     * Bounded preload wait (max [timeoutMs]) used before interrupting or delaying VOD playback.
     * Returns `true` only if a playable VAST creative is verified ready; `false` otherwise.
     */
    suspend fun awaitPlayableVideoVastAd(
        context: Context,
        timeoutMs: Long = VIDEO_VAST_PRELOAD_TIMEOUT_MS
    ): Boolean {
        if (!isAdsAllowed(context) || !isDeviceOnline(context)) return false
        if (isVideoVastAdReady()) return true
        if (!isSdkReady()) {
            initialize(context)
            return false
        }
        val signal = CompletableDeferred<Boolean>()
        pendingVideoVastPreloadSignal = signal
        preloadVideoVastAd(context)
        val ready = withTimeoutOrNull(timeoutMs) {
            signal.await()
        } ?: isVideoVastAdReady()
        if (pendingVideoVastPreloadSignal === signal) {
            pendingVideoVastPreloadSignal = null
        }
        return ready && isVideoVastAdReady()
    }

    /**
     * Plays an already verified InMobi Video/VAST placement (`10000869539`).
     * Never invokes [onBeforeAd] unless a playable creative is verified ready right before display.
     */
    fun showVerifiedVideoVastAd(
        activity: Activity,
        cueLabel: String,
        onBeforeAd: () -> Unit,
        onAfterAd: () -> Unit
    ): Boolean {
        if (!isAdsAllowed(activity) || !isDeviceOnline(activity) || !isSdkReady()) {
            preloadVideoVastAd(activity)
            return false
        }
        val ad = videoVastAd
        if (ad == null || !ad.isReady()) {
            preloadVideoVastAd(activity)
            return false
        }
        if (_isAdCurrentlyDisplaying.value) {
            return false
        }

        val completed = AtomicBoolean(false)
        val finishOnce = {
            if (completed.compareAndSet(false, true)) {
                videoVastDisplayConfirmed.set(false)
                _isAdCurrentlyDisplaying.value = false
                currentActiveVideoVastCallback = null
                preloadVideoVastAd(activity)
                onAfterAd()
            }
        }

        return try {
            onBeforeAd()
            videoVastDisplayConfirmed.set(false)
            _isAdCurrentlyDisplaying.value = true
            currentActiveVideoVastCallback = finishOnce
            mainHandler.postDelayed({
                if (!completed.get() && !videoVastDisplayConfirmed.get()) {
                    Log.w(TAG, "Video/VAST ($cueLabel) did not confirm display; resuming playback.")
                    finishOnce()
                }
            }, DISPLAY_WATCHDOG_TIMEOUT_MS)
            mainHandler.postDelayed({
                if (!completed.get() && _isAdCurrentlyDisplaying.value) {
                    Log.w(TAG, "Video/VAST ($cueLabel) safety cap reached; releasing player lock.")
                    finishOnce()
                }
            }, 60_000L)
            Log.i(TAG, "Displaying Video/VAST placement $PLACEMENT_VIDEO_VAST_ID for $cueLabel")
            ad.show(activity)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed displaying Video/VAST ($cueLabel): ${t.message}")
            finishOnce()
            false
        }
    }

    fun preloadNativeAd(context: Context) {
        val appContext = context.applicationContext
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) {
            _nativeCardAdState.value = null
            return
        }
        if (!isSdkReady()) {
            initialize(appContext)
            return
        }
        if (_nativeCardAdState.value?.nativeAd?.isReady() == true) return
        if (!isNativeLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                activeNativeAd?.destroy()
                val nativeListener = object : NativeAdEventListener() {
                    override fun onAdLoadSucceeded(ad: InMobiNative, info: AdMetaInfo) {
                        isNativeLoading.set(false)
                        _nativeCardAdState.value = NeliInMobiNativeAdHolder(
                            nativeAd = ad,
                            title = ad.getAdTitle().orEmpty(),
                            description = ad.getAdDescription().orEmpty(),
                            iconUrl = ad.getAdIcon()?.url,
                            ctaText = ad.getCtaText().orEmpty().ifBlank { "Open" },
                            advertiserName = ad.getAdvertiserName().orEmpty(),
                            rating = ad.getAdRating(),
                            isVideo = ad.isVideo()
                        )
                        Log.i(TAG, "Native placement $PLACEMENT_NATIVE_ID loaded.")
                    }

                    override fun onAdLoadFailed(ad: InMobiNative, status: InMobiAdRequestStatus) {
                        isNativeLoading.set(false)
                        _nativeCardAdState.value = null
                        Log.d(TAG, "Native placement $PLACEMENT_NATIVE_ID load failed: ${status.statusCode} ${status.message}")
                    }
                }
                val nativeAd = InMobiNative(appContext, PLACEMENT_NATIVE_ID, nativeListener)
                activeNativeAd = nativeAd
                nativeAd.load()
            } catch (t: Throwable) {
                isNativeLoading.set(false)
                _nativeCardAdState.value = null
                Log.w(TAG, "Native preload exception: ${t.message}")
            }
        }
    }

    /**
     * Live TV Channel Opening Interstitial (`10000869536`).
     * Debounces rapid duplicate taps so fast multi-taps on channels (e.g., Nick Jr) never stack screens.
     * Never blocks playback if offline, not ready, or failed.
     */
    fun showChannelOpeningAd(
        activity: Activity,
        channelName: String,
        channelId: String = channelName,
        onPlaybackResume: () -> Unit
    ) {
        val nowMs = System.currentTimeMillis()
        if (channelId == lastChannelOpenId && nowMs - lastChannelOpenClickMs < CHANNEL_OPEN_DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring rapid duplicate channel open for $channelName")
            return
        }
        lastChannelOpenClickMs = nowMs
        lastChannelOpenId = channelId

        if (_isAdCurrentlyDisplaying.value) {
            return
        }

        if (!isAdsAllowed(activity) || !isDeviceOnline(activity) || !isSdkReady()) {
            preloadInterstitialAd(activity)
            onPlaybackResume()
            return
        }

        val ad = interstitialAd
        if (ad == null || !ad.isReady()) {
            preloadInterstitialAd(activity)
            onPlaybackResume()
            return
        }

        val resumed = AtomicBoolean(false)
        val resumeOnce = {
            if (resumed.compareAndSet(false, true)) {
                interstitialDisplayConfirmed.set(false)
                _isAdCurrentlyDisplaying.value = false
                currentActiveInterstitialCallback = null
                preloadInterstitialAd(activity)
                onPlaybackResume()
            }
        }

        interstitialDisplayConfirmed.set(false)
        currentActiveInterstitialCallback = resumeOnce
        _isAdCurrentlyDisplaying.value = true

        // Watchdog ensures Live TV still opens if InMobi SDK fails silently to show
        mainHandler.postDelayed({
            if (!resumed.get() && !interstitialDisplayConfirmed.get()) {
                Log.w(TAG, "Channel opening interstitial ($channelName) did not display; opening player.")
                resumeOnce()
            }
        }, DISPLAY_WATCHDOG_TIMEOUT_MS)
        mainHandler.postDelayed({
            if (!resumed.get() && _isAdCurrentlyDisplaying.value) {
                Log.w(TAG, "Channel opening interstitial ($channelName) safety cap reached; resuming playback.")
                resumeOnce()
            }
        }, 45_000L)

        try {
            Log.i(TAG, "Showing InMobi Interstitial $PLACEMENT_INTERSTITIAL_ID for Live TV ($channelName)")
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "Channel opening interstitial failed: ${t.message}")
            resumeOnce()
        }
    }

    /**
     * Rewarded Ad (`10000869538`) shown before online downloads.
     * If offline, unavailable, or failed, starts the download immediately without blocking.
     */
    fun showDownloadRewardedAd(
        activity: Activity,
        onContinueDownload: () -> Unit
    ) {
        if (!isAdsAllowed(activity) || !isDeviceOnline(activity) || !isSdkReady()) {
            if (isDeviceOnline(activity)) {
                preloadRewardedAd(activity)
            }
            onContinueDownload()
            return
        }

        val ad = rewardedAd
        if (ad == null || !ad.isReady() || _isAdCurrentlyDisplaying.value) {
            preloadRewardedAd(activity)
            onContinueDownload()
            return
        }

        val continued = AtomicBoolean(false)
        val continueOnce = { _: Boolean ->
            if (continued.compareAndSet(false, true)) {
                rewardedDisplayConfirmed.set(false)
                _isAdCurrentlyDisplaying.value = false
                currentActiveRewardedCallback = null
                preloadRewardedAd(activity)
                onContinueDownload()
            }
        }

        rewardedDisplayConfirmed.set(false)
        currentActiveRewardedCallback = continueOnce
        _isAdCurrentlyDisplaying.value = true

        mainHandler.postDelayed({
            if (!continued.get() && !rewardedDisplayConfirmed.get()) {
                Log.w(TAG, "Download rewarded ad did not confirm display; continuing download.")
                continueOnce(false)
            }
        }, DISPLAY_WATCHDOG_TIMEOUT_MS)
        mainHandler.postDelayed({
            if (!continued.get() && _isAdCurrentlyDisplaying.value) {
                Log.w(TAG, "Download rewarded ad safety cap reached; releasing lock.")
                continueOnce(false)
            }
        }, 60_000L)

        try {
            Log.i(TAG, "Showing InMobi Rewarded $PLACEMENT_REWARDED_ID before online download")
            ad.show(activity)
        } catch (t: Throwable) {
            Log.w(TAG, "Download rewarded ad failed: ${t.message}")
            continueOnce(false)
        }
    }

    fun clearPlaybackTimer() {
        interstitialDisplayConfirmed.set(false)
        videoVastDisplayConfirmed.set(false)
        rewardedDisplayConfirmed.set(false)
        currentActiveInterstitialCallback = null
        currentActiveVideoVastCallback = null
        currentActiveRewardedCallback = null
        _isAdCurrentlyDisplaying.value = false
    }

    private val interstitialDisplayConfirmed = AtomicBoolean(false)
    private val videoVastDisplayConfirmed = AtomicBoolean(false)
    private val rewardedDisplayConfirmed = AtomicBoolean(false)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Production InMobi Banner Composable (`10000869537`).
 * Collapses to 0dp on Smart TVs, active Premium subscribers, offline devices, or no-fill.
 */
@Composable
fun InMobiBannerAd(
    modifier: Modifier = Modifier,
    adTag: String = "inline_banner"
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow

    if (!NeliAdManager.isAdsAllowed(context) || isPremiumActive || !NeliAdManager.isDeviceOnline(context)) {
        return
    }

    val isSdkReadyState by NeliAdManager.sdkReadyFlow.collectAsState()
    var isAdLoaded by remember(adTag) { mutableStateOf(false) }
    var isAdFailed by remember(adTag) { mutableStateOf(false) }
    var bannerViewRef by remember(adTag) { mutableStateOf<InMobiBanner?>(null) }
    var hasRequestedLoad by remember(adTag) { mutableStateOf(false) }

    LaunchedEffect(isSdkReadyState, bannerViewRef) {
        val banner = bannerViewRef ?: return@LaunchedEffect
        if (isSdkReadyState && !hasRequestedLoad) {
            hasRequestedLoad = true
            try {
                banner.load()
            } catch (_: Throwable) {
                isAdFailed = true
            }
        }
    }

    DisposableEffect(adTag) {
        onDispose {
            try {
                bannerViewRef?.destroy()
            } catch (_: Throwable) {
            }
            bannerViewRef = null
        }
    }

    if (isAdFailed) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isAdLoaded) {
                    Modifier
                        .height(54.dp)
                        .padding(vertical = 2.dp)
                } else {
                    Modifier
                        .height(50.dp)
                }
            )
            .testTag("inmobi_banner_ad_$adTag"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                try {
                    NeliAdManager.initialize(ctx)
                    val bannerContext = ctx.findActivity() ?: ctx
                    val density = ctx.resources.displayMetrics.density
                    val widthPx = (320f * density).toInt().coerceAtLeast(1)
                    val heightPx = (50f * density).toInt().coerceAtLeast(1)
                    val banner = InMobiBanner(bannerContext, NeliAdManager.PLACEMENT_BANNER_ID).apply {
                        setEnableAutoRefresh(true)
                        setRefreshInterval(60)
                        setAnimationType(InMobiBanner.AnimationType.ANIMATION_ALPHA)
                        layoutParams = FrameLayout.LayoutParams(
                            widthPx,
                            heightPx,
                            Gravity.CENTER
                        )
                        setListener(object : BannerAdEventListener() {
                            override fun onAdLoadSucceeded(ad: InMobiBanner, info: AdMetaInfo) {
                                isAdFailed = false
                                isAdLoaded = true
                            }

                            override fun onAdLoadFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                                isAdLoaded = false
                                isAdFailed = true
                            }

                            override fun onAdFetchFailed(ad: InMobiBanner, status: InMobiAdRequestStatus) {
                                isAdLoaded = false
                                isAdFailed = true
                            }
                        })
                    }
                    bannerViewRef = banner
                    container.addView(banner)
                    if (NeliAdManager.sdkReadyFlow.value && !hasRequestedLoad) {
                        hasRequestedLoad = true
                        banner.load()
                    }
                } catch (_: Throwable) {
                    isAdFailed = true
                }
                container
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Production InMobi Native Ad Card Composable (`10000869540`) using [InMobiNativeViewData.Builder]
 * so native impressions and clicks are tracked directly by the InMobi 11.x SDK.
 * Collapses cleanly when no native ad is available, offline, on Smart TV, or for Premium subscribers.
 */
@Composable
fun InMobiNativeCardAd(
    modifier: Modifier = Modifier,
    adTag: String = "native_card",
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow

    if (!NeliAdManager.isAdsAllowed(context) || isPremiumActive || !NeliAdManager.isDeviceOnline(context)) {
        return
    }

    val isSdkReadyState by NeliAdManager.sdkReadyFlow.collectAsState()
    val nativeHolder by NeliAdManager.nativeCardAdState.collectAsState()

    LaunchedEffect(isSdkReadyState) {
        if (nativeHolder == null) {
            NeliAdManager.preloadNativeAd(context)
        }
    }

    val holder = nativeHolder ?: return
    val nativeAd = holder.nativeAd
    val isReady = remember(holder) {
        try {
            nativeAd.isReady()
        } catch (_: Throwable) {
            false
        }
    }
    if (!isReady) return

    DisposableEffect(nativeAd) {
        onDispose {
            try {
                nativeAd.unTrackViews()
            } catch (_: Throwable) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .testTag("inmobi_native_card_$adTag")
    ) {
        AndroidView(
            factory = { ctx ->
                val dp = ctx.resources.displayMetrics.density
                val rootCard = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#0F1738"))
                        cornerRadius = 18 * dp
                        setStroke((1 * dp).toInt(), AndroidColor.parseColor("#55F59E0B"))
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Header Row: "SPONSORED" badge + optional Advertiser + AdChoice Icon
                val topRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val badgeView = TextView(ctx).apply {
                    text = "SPONSORED"
                    setTextColor(AndroidColor.BLACK)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                    setTypeface(null, Typeface.BOLD)
                    setPadding((6 * dp).toInt(), (2 * dp).toInt(), (6 * dp).toInt(), (2 * dp).toInt())
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#F59E0B"))
                        cornerRadius = 4 * dp
                    }
                }
                topRow.addView(badgeView)

                val advertiserView = TextView(ctx).apply {
                    text = holder.advertiserName.ifBlank { "Featured Partner" }
                    setTextColor(AndroidColor.parseColor("#8E9BAE"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                topRow.addView(advertiserView)

                try {
                    val choiceIcon = nativeAd.getAdChoiceIcon()
                    if (choiceIcon != null) {
                        (choiceIcon.parent as? ViewGroup)?.removeView(choiceIcon)
                        choiceIcon.layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt())
                        topRow.addView(choiceIcon)
                    }
                } catch (_: Throwable) {
                }

                rootCard.addView(topRow)

                // Media view when SDK provides native media
                try {
                    val mediaView = nativeAd.getMediaView()
                    if (mediaView != null) {
                        (mediaView.parent as? ViewGroup)?.removeView(mediaView)
                        mediaView.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            (150 * dp).toInt()
                        ).apply {
                            topMargin = (8 * dp).toInt()
                        }
                        rootCard.addView(mediaView)
                    }
                } catch (_: Throwable) {
                }

                // Bottom Content Row: Icon + Title/Description + CTA Button
                val bodyRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (10 * dp).toInt()
                    }
                }

                val iconView = ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                        marginEnd = (10 * dp).toInt()
                    }
                    holder.iconUrl?.let { url ->
                        if (url.isNotBlank()) load(url)
                    }
                }
                bodyRow.addView(iconView)

                val textCol = LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val titleView = TextView(ctx).apply {
                    text = holder.title.ifBlank { "Recommended" }
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                val descView = TextView(ctx).apply {
                    text = holder.description
                    setTextColor(AndroidColor.parseColor("#8E9BAE"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                textCol.addView(titleView)
                textCol.addView(descView)
                bodyRow.addView(textCol)

                val ctaView = TextView(ctx).apply {
                    text = holder.ctaText.ifBlank { "Open" }
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    minHeight = (36 * dp).toInt()
                    setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
                    background = GradientDrawable().apply {
                        setColor(AndroidColor.parseColor("#0055FF"))
                        cornerRadius = 8 * dp
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = (10 * dp).toInt()
                    }
                }
                bodyRow.addView(ctaView)
                rootCard.addView(bodyRow)

                try {
                    val viewData = InMobiNativeViewData.Builder(rootCard)
                        .setTitleView(titleView)
                        .setDescriptionView(descView)
                        .setIconView(iconView)
                        .setCTAView(ctaView)
                        .setAdvertiserView(advertiserView)
                        .build()
                    nativeAd.registerViewForTracking(viewData)
                } catch (t: Throwable) {
                    Log.w("NeliTvInMobiAds", "Error registering InMobi native views: ${t.message}")
                }

                ctaView.setOnClickListener {
                    try {
                        nativeAd.takeAction()
                        onClick()
                    } catch (_: Throwable) {
                    }
                }
                rootCard
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
