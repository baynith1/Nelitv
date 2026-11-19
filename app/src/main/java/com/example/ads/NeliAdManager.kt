package com.example.ads

import android.app.Activity
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.NeliAdminManager
import com.example.data.NeliFreeForeverAccountsManager
import com.example.data.NeliSubscriptionManager
import com.example.ui.theme.NeliThemeManager
import com.example.ui.tv.NeliTvDetector
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Google AdMob Monetization Manager for NeliTV.
 *
 * Strictly Production Mode (`IS_TEST_MODE_ENABLED = false`):
 * - App ID: `ca-app-pub-5204110379616479~8471497203`
 * - Banner: `ca-app-pub-5204110379616479/4528382767`
 * - Rewarded Interstitial: `ca-app-pub-5204110379616479/8561454550`
 * - Native Advanced: `ca-app-pub-5204110379616479/4267373368`
 * - App Open: `ca-app-pub-5204110379616479/4953382032`
 * - app-ads.txt: `google.com, pub-5204110379616479, DIRECT, f08c47fec0942fa0`
 *
 * Placements:
 * - App Open Ad on app launch / foreground resume.
 * - Banner Ads below Home hero slider, after every category in Discovery & Movies, and in Movie Details.
 * - Native Advanced Ads in Live TV on Home and inside Movie rows.
 * - Rewarded Interstitial Ads when user plays a movie, plays any channel, or downloads a movie/episode.
 * - Zero 15-minute playback interruptions; zero ads for VIP/Ad-Free emails (e.g. Alexbaineth@gmail.com),
 *   Admin, active Premium members, or Smart TV mode.
 */
object NeliAdManager {

    private const val TAG = "NeliAdMob"

    const val IS_TEST_MODE_ENABLED: Boolean = false
    const val ADMOB_APP_ID: String = "ca-app-pub-5204110379616479~8471497203"
    const val BANNER_AD_UNIT_ID: String = "ca-app-pub-5204110379616479/4528382767"
    const val REWARDED_INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-5204110379616479/8561454550"
    const val NATIVE_ADVANCED_AD_UNIT_ID: String = "ca-app-pub-5204110379616479/4267373368"
    const val APP_OPEN_AD_UNIT_ID: String = "ca-app-pub-5204110379616479/4953382032"
    const val APP_ADS_TXT_LINE: String = "google.com, pub-5204110379616479, DIRECT, f08c47fec0942fa0"

    private const val APP_OPEN_CACHE_VALIDITY_MS: Long = 4L * 60L * 60L * 1000L // 4 hours
    private const val APP_OPEN_MIN_INTERVAL_MS: Long = 30_000L // avoid double-triggering on quick activity resume
    private const val REWARDED_INTERSTITIAL_COOLDOWN_MS: Long = 20_000L // smooth UX while monetizing every action
    private const val NATIVE_RELOAD_INTERVAL_MS: Long = 90_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sdkInitialized = AtomicBoolean(false)
    private val isAppOpenLoading = AtomicBoolean(false)
    private val isRewardedInterstitialLoading = AtomicBoolean(false)
    private val isNativeLoading = AtomicBoolean(false)

    private val _isSdkReady = MutableStateFlow(false)
    val isSdkReady: StateFlow<Boolean> = _isSdkReady.asStateFlow()

    private val _isAdCurrentlyDisplaying = MutableStateFlow(false)
    val isAdCurrentlyDisplaying: StateFlow<Boolean> = _isAdCurrentlyDisplaying.asStateFlow()

    private val _nativeAdState = MutableStateFlow<NativeAd?>(null)
    val nativeAdState: StateFlow<NativeAd?> = _nativeAdState.asStateFlow()

    @Volatile
    private var cachedAppOpenAd: AppOpenAd? = null

    @Volatile
    private var appOpenLoadedAtMs: Long = 0L

    @Volatile
    private var lastAppOpenShownAtMs: Long = 0L

    @Volatile
    private var cachedRewardedInterstitialAd: RewardedInterstitialAd? = null

    @Volatile
    private var lastRewardedInterstitialShownAtMs: Long = 0L

    @Volatile
    private var lastNativeLoadedAtMs: Long = 0L

    @Volatile
    private var isKidsContentActive: Boolean = false

    @Volatile
    private var appOpenRetryCount: Int = 0

    @Volatile
    private var rewardedRetryCount: Int = 0

    @Volatile
    private var nativeRetryCount: Int = 0

    private const val MAX_AD_RETRY_ATTEMPTS: Int = 3
    private const val AD_SHOW_WATCHDOG_MS: Long = 2_500L

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        if (NeliTvDetector.isHardwareTelevision(appContext) || NeliTvDetector.isTelevision(appContext)) {
            return
        }
        if (isRunningInRobolectricTest()) {
            sdkInitialized.set(true)
            _isSdkReady.value = true
            return
        }
        if (sdkInitialized.compareAndSet(false, true)) {
            Thread({
                try {
                    // Initialize Google Mobile Ads SDK off the main thread per AdMob best practices
                    // to avoid blocking MainActivity's initial SurfaceSyncGroup frame transaction.
                    val prodConfig = RequestConfiguration.Builder()
                        .setTestDeviceIds(emptyList())
                        .build()
                    MobileAds.setRequestConfiguration(prodConfig)
                    MobileAds.initialize(appContext) {
                        _isSdkReady.value = true
                        mainHandler.post {
                            if (isAdsAllowed(appContext) && isDeviceOnline(appContext)) {
                                preloadAppOpenAd(appContext)
                                preloadRewardedInterstitialAd(appContext)
                                preloadNativeAd(appContext)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "AdMob init error: ${e.message}")
                    sdkInitialized.set(false)
                }
            }, "NeliAdMobInit").start()
        } else if (_isSdkReady.value && isAdsAllowed(appContext) && isDeviceOnline(appContext)) {
            mainHandler.post {
                if (cachedAppOpenAd == null) preloadAppOpenAd(appContext)
                if (cachedRewardedInterstitialAd == null) preloadRewardedInterstitialAd(appContext)
                if (_nativeAdState.value == null) preloadNativeAd(appContext)
            }
        }
    }

    fun onNetworkRestored(context: Context) {
        val appContext = context.applicationContext
        if (isRunningInRobolectricTest()) return
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        appOpenRetryCount = 0
        rewardedRetryCount = 0
        nativeRetryCount = 0
        if (!sdkInitialized.get()) {
            initialize(appContext)
        } else if (_isSdkReady.value) {
            preloadAppOpenAd(appContext)
            preloadRewardedInterstitialAd(appContext)
            preloadNativeAd(appContext)
        }
    }

    fun updateAgeRestrictedForKidsContent(isKids: Boolean) {
        isKidsContentActive = isKids
    }

    fun isAdsAllowed(context: Context): Boolean {
        val subState = NeliSubscriptionManager.subscriptionState.value
        val activeEmail = subState.linkedUserEmail
        if (subState.isActiveNow) return false
        if (NeliFreeForeverAccountsManager.isAdFreeVipEmail(activeEmail)) return false
        if (NeliAdminManager.isAdminSessionActive.value) return false
        if (NeliTvDetector.isHardwareTelevision(context) || NeliTvDetector.isTelevision(context)) return false
        return true
    }

    fun isDeviceOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Throwable) {
            true
        }
    }

    fun buildProductionAdRequest(): AdRequest {
        return AdRequest.Builder().build()
    }

    // =========================================================================
    // 1. APP OPEN AD (ca-app-pub-5204110379616479/4953382032)
    // =========================================================================

    fun preloadAppOpenAd(context: Context) {
        val appContext = context.applicationContext
        if (isRunningInRobolectricTest()) return
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        if (isAppOpenAdAvailable()) return
        if (!isAppOpenLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                AppOpenAd.load(
                    appContext,
                    APP_OPEN_AD_UNIT_ID,
                    buildProductionAdRequest(),
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            cachedAppOpenAd = ad
                            appOpenLoadedAtMs = System.currentTimeMillis()
                            appOpenRetryCount = 0
                            isAppOpenLoading.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            cachedAppOpenAd = null
                            isAppOpenLoading.set(false)
                            Log.w(TAG, "AppOpen failed to load: ${error.code} ${error.message}")
                            if (appOpenRetryCount < MAX_AD_RETRY_ATTEMPTS && isAdsAllowed(appContext) && isDeviceOnline(appContext)) {
                                val delayMs = 30_000L * (1L shl appOpenRetryCount)
                                appOpenRetryCount++
                                mainHandler.postDelayed({ preloadAppOpenAd(appContext) }, delayMs)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                isAppOpenLoading.set(false)
                Log.w(TAG, "AppOpen load exception: ${e.message}")
            }
        }
    }

    private fun isAppOpenAdAvailable(): Boolean {
        val ad = cachedAppOpenAd ?: return false
        val age = System.currentTimeMillis() - appOpenLoadedAtMs
        return age in 0 until APP_OPEN_CACHE_VALIDITY_MS
    }

    fun showAppOpenAdIfAvailable(activity: Activity, onComplete: () -> Unit = {}) {
        if (isRunningInRobolectricTest() || activity.isFinishing || activity.isDestroyed) {
            onComplete()
            return
        }
        if (!isAdsAllowed(activity) || !isDeviceOnline(activity)) {
            onComplete()
            return
        }
        if (_isAdCurrentlyDisplaying.value) {
            onComplete()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastAppOpenShownAtMs < APP_OPEN_MIN_INTERVAL_MS) {
            onComplete()
            return
        }
        val ad = cachedAppOpenAd
        if (ad == null || !isAppOpenAdAvailable()) {
            preloadAppOpenAd(activity)
            onComplete()
            return
        }

        val completed = AtomicBoolean(false)
        val safeComplete = {
            if (completed.compareAndSet(false, true)) {
                _isAdCurrentlyDisplaying.value = false
                mainHandler.post { onComplete() }
            }
        }

        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                safeComplete()
                return@post
            }
            cachedAppOpenAd = null
            _isAdCurrentlyDisplaying.value = true
            lastAppOpenShownAtMs = System.currentTimeMillis()
            val didShow = AtomicBoolean(false)
            val watchdog = Runnable {
                if (!didShow.get()) {
                    Log.w(TAG, "AppOpen show watchdog triggered; continuing app flow seamlessly")
                    safeComplete()
                }
            }
            mainHandler.postDelayed(watchdog, AD_SHOW_WATCHDOG_MS)
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mainHandler.removeCallbacks(watchdog)
                    preloadAppOpenAd(activity.applicationContext)
                    safeComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mainHandler.removeCallbacks(watchdog)
                    preloadAppOpenAd(activity.applicationContext)
                    safeComplete()
                }

                override fun onAdShowedFullScreenContent() {
                    didShow.set(true)
                    mainHandler.removeCallbacks(watchdog)
                    _isAdCurrentlyDisplaying.value = true
                }
            }
            try {
                ad.show(activity)
            } catch (e: Throwable) {
                mainHandler.removeCallbacks(watchdog)
                Log.w(TAG, "AppOpen show error: ${e.message}")
                preloadAppOpenAd(activity.applicationContext)
                safeComplete()
            }
        }
    }

    // =========================================================================
    // 2. REWARDED INTERSTITIAL AD (ca-app-pub-5204110379616479/8561454550)
    //    Triggers when user plays a movie, plays any channel, or downloads.
    // =========================================================================

    fun preloadRewardedInterstitialAd(context: Context) {
        val appContext = context.applicationContext
        if (isRunningInRobolectricTest()) return
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        if (cachedRewardedInterstitialAd != null) return
        if (!isRewardedInterstitialLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                RewardedInterstitialAd.load(
                    appContext,
                    REWARDED_INTERSTITIAL_AD_UNIT_ID,
                    buildProductionAdRequest(),
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedInterstitialAd) {
                            cachedRewardedInterstitialAd = ad
                            rewardedRetryCount = 0
                            isRewardedInterstitialLoading.set(false)
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            cachedRewardedInterstitialAd = null
                            isRewardedInterstitialLoading.set(false)
                            Log.w(TAG, "RewardedInterstitial failed to load: ${error.code} ${error.message}")
                            if (rewardedRetryCount < MAX_AD_RETRY_ATTEMPTS && isAdsAllowed(appContext) && isDeviceOnline(appContext)) {
                                val delayMs = 30_000L * (1L shl rewardedRetryCount)
                                rewardedRetryCount++
                                mainHandler.postDelayed({ preloadRewardedInterstitialAd(appContext) }, delayMs)
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                isRewardedInterstitialLoading.set(false)
                Log.w(TAG, "RewardedInterstitial load exception: ${e.message}")
            }
        }
    }

    fun showRewardedInterstitialAd(
        activity: Activity?,
        placementTag: String,
        enforceCooldown: Boolean = true,
        onProceed: () -> Unit
    ) {
        if (activity == null || isRunningInRobolectricTest() || activity.isFinishing || activity.isDestroyed) {
            onProceed()
            return
        }
        if (!isAdsAllowed(activity) || !isDeviceOnline(activity)) {
            onProceed()
            return
        }
        if (_isAdCurrentlyDisplaying.value) {
            onProceed()
            return
        }
        val now = System.currentTimeMillis()
        if (enforceCooldown && (now - lastRewardedInterstitialShownAtMs < REWARDED_INTERSTITIAL_COOLDOWN_MS)) {
            if (cachedRewardedInterstitialAd == null) {
                preloadRewardedInterstitialAd(activity)
            }
            onProceed()
            return
        }

        val ad = cachedRewardedInterstitialAd
        if (ad == null) {
            preloadRewardedInterstitialAd(activity)
            onProceed()
            return
        }

        val proceeded = AtomicBoolean(false)
        val safeProceed = {
            if (proceeded.compareAndSet(false, true)) {
                _isAdCurrentlyDisplaying.value = false
                mainHandler.post { onProceed() }
            }
        }

        mainHandler.post {
            if (activity.isFinishing || activity.isDestroyed) {
                safeProceed()
                return@post
            }
            cachedRewardedInterstitialAd = null
            _isAdCurrentlyDisplaying.value = true
            lastRewardedInterstitialShownAtMs = System.currentTimeMillis()
            val didShow = AtomicBoolean(false)
            val watchdog = Runnable {
                if (!didShow.get()) {
                    Log.w(TAG, "RewardedInterstitial ($placementTag) show watchdog triggered; proceeding without blocking")
                    preloadRewardedInterstitialAd(activity.applicationContext)
                    safeProceed()
                }
            }
            mainHandler.postDelayed(watchdog, AD_SHOW_WATCHDOG_MS)

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mainHandler.removeCallbacks(watchdog)
                    preloadRewardedInterstitialAd(activity.applicationContext)
                    safeProceed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mainHandler.removeCallbacks(watchdog)
                    Log.w(TAG, "RewardedInterstitial ($placementTag) show failed: ${adError.message}")
                    preloadRewardedInterstitialAd(activity.applicationContext)
                    safeProceed()
                }

                override fun onAdShowedFullScreenContent() {
                    didShow.set(true)
                    mainHandler.removeCallbacks(watchdog)
                    _isAdCurrentlyDisplaying.value = true
                }
            }

            try {
                ad.show(activity) { _ ->
                    // User earned reward; proceed once ad closes in onAdDismissedFullScreenContent
                }
            } catch (e: Throwable) {
                mainHandler.removeCallbacks(watchdog)
                Log.w(TAG, "RewardedInterstitial ($placementTag) exception: ${e.message}")
                preloadRewardedInterstitialAd(activity.applicationContext)
                safeProceed()
            }
        }
    }

    /**
     * Shows a Rewarded Interstitial ad when opening any Live TV channel or Movie.
     */
    fun showChannelOpeningAd(
        activity: Activity?,
        channelName: String,
        channelId: String,
        onProceed: () -> Unit
    ) {
        showRewardedInterstitialAd(
            activity = activity,
            placementTag = "play_$channelId",
            enforceCooldown = true,
            onProceed = onProceed
        )
    }

    /**
     * Shows a Rewarded Interstitial ad when starting a movie or episode download.
     */
    fun showDownloadRewardedAd(
        activity: Activity?,
        onProceed: () -> Unit
    ) {
        showRewardedInterstitialAd(
            activity = activity,
            placementTag = "download_action",
            enforceCooldown = false,
            onProceed = onProceed
        )
    }

    // =========================================================================
    // 3. NATIVE ADVANCED AD (ca-app-pub-5204110379616479/4267373368)
    // =========================================================================

    fun preloadNativeAd(context: Context, forceReload: Boolean = false) {
        val appContext = context.applicationContext
        if (isRunningInRobolectricTest()) return
        if (!isAdsAllowed(appContext) || !isDeviceOnline(appContext)) return
        val now = System.currentTimeMillis()
        if (!forceReload && _nativeAdState.value != null && (now - lastNativeLoadedAtMs < NATIVE_RELOAD_INTERVAL_MS)) {
            return
        }
        if (!isNativeLoading.compareAndSet(false, true)) return

        mainHandler.post {
            try {
                val adLoader = AdLoader.Builder(appContext, NATIVE_ADVANCED_AD_UNIT_ID)
                    .forNativeAd { loadedAd ->
                        val previous = _nativeAdState.value
                        _nativeAdState.value = loadedAd
                        lastNativeLoadedAtMs = System.currentTimeMillis()
                        nativeRetryCount = 0
                        isNativeLoading.set(false)
                        if (previous != null && previous !== loadedAd) {
                            mainHandler.postDelayed({
                                try {
                                    previous.destroy()
                                } catch (_: Throwable) {
                                }
                            }, 2_000L)
                        }
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isNativeLoading.set(false)
                            Log.w(TAG, "NativeAd failed to load: ${error.code} ${error.message}")
                            if (nativeRetryCount < MAX_AD_RETRY_ATTEMPTS && isAdsAllowed(appContext) && isDeviceOnline(appContext)) {
                                val delayMs = 30_000L * (1L shl nativeRetryCount)
                                nativeRetryCount++
                                mainHandler.postDelayed({ preloadNativeAd(appContext) }, delayMs)
                            }
                        }
                    })
                    .withNativeAdOptions(
                        NativeAdOptions.Builder()
                            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                            .build()
                    )
                    .build()

                adLoader.loadAd(buildProductionAdRequest())
            } catch (e: Throwable) {
                isNativeLoading.set(false)
                Log.w(TAG, "NativeAd load exception: ${e.message}")
            }
        }
    }

    fun isRunningInRobolectricTest(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            Build.HARDWARE.equals("robolectric", ignoreCase = true) ||
            System.getProperty("robolectric.logging") != null
    }
}

/**
 * Production Google AdMob Adaptive Banner Composable (`ca-app-pub-5204110379616479/4528382767`).
 * Collapses completely to 0dp when user is VIP/Ad-Free, offline, TV, or if ad does not fill.
 */
@Composable
fun AdMobBannerAd(
    adTag: String = "banner_default",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isAdminSession by NeliAdminManager.isAdminSessionActive.collectAsState()
    val isSdkReady by NeliAdManager.isSdkReady.collectAsState()

    val isVipAdFree = subState.isActiveNow ||
        NeliFreeForeverAccountsManager.isAdFreeVipEmail(subState.linkedUserEmail) ||
        isAdminSession

    if (isVipAdFree ||
        !NeliAdManager.isAdsAllowed(context) ||
        !NeliAdManager.isDeviceOnline(context) ||
        NeliAdManager.isRunningInRobolectricTest()
    ) {
        return
    }

    var isLoaded by remember(adTag) { mutableStateOf(false) }
    var isFailed by remember(adTag) { mutableStateOf(false) }
    var hasRequestedLoad by remember(adTag) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NeliAdManager.initialize(context.applicationContext)
    }

    // If a banner fails transiently (e.g., temporary no-fill), keep height 0dp and retry silently once after 45s
    LaunchedEffect(isFailed, isSdkReady) {
        if (isFailed && isSdkReady) {
            kotlinx.coroutines.delay(45_000L)
            if (NeliAdManager.isAdsAllowed(context) && NeliAdManager.isDeviceOnline(context)) {
                isFailed = false
                hasRequestedLoad = false
            }
        }
    }

    if (!isSdkReady || isFailed) return

    val adViewHolder = remember(adTag) { arrayOfNulls<AdView>(1) }
    DisposableEffect(adTag) {
        onDispose {
            try {
                adViewHolder[0]?.destroy()
                adViewHolder[0] = null
            } catch (_: Throwable) {
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isLoaded) {
                    Modifier.padding(vertical = 4.dp)
                } else {
                    Modifier
                }
            )
            .testTag("admob_banner_$adTag"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .then(if (isLoaded) Modifier.heightIn(min = 50.dp) else Modifier.heightIn(max = 0.dp)),
            factory = { ctx ->
                val container = FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                try {
                    val density = ctx.resources.displayMetrics.density.coerceAtLeast(1f)
                    val widthDp = ((ctx.resources.displayMetrics.widthPixels / density).toInt() - 16)
                        .coerceIn(320, 720)
                    val adView = AdView(ctx).apply {
                        adUnitId = NeliAdManager.BANNER_AD_UNIT_ID
                        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                isLoaded = true
                                isFailed = false
                            }

                            override fun onAdFailedToLoad(error: LoadAdError) {
                                isLoaded = false
                                isFailed = true
                            }
                        }
                    }
                    adViewHolder[0] = adView
                    container.addView(
                        adView,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.CENTER
                        )
                    )
                    hasRequestedLoad = true
                    adView.loadAd(NeliAdManager.buildProductionAdRequest())
                } catch (_: Throwable) {
                    isFailed = true
                }
                container
            },
            update = { _ ->
                if (isSdkReady && !isLoaded && !isFailed && !hasRequestedLoad) {
                    adViewHolder[0]?.let { existing ->
                        hasRequestedLoad = true
                        try {
                            existing.loadAd(NeliAdManager.buildProductionAdRequest())
                        } catch (_: Throwable) {
                            isFailed = true
                        }
                    }
                }
            }
        )
    }
}

/**
 * Production Google AdMob Native Advanced Ad Card (`ca-app-pub-5204110379616479/4267373368`).
 * Renders inside a genuine `NativeAdView` with icon, headline, body, "Ad" badge, and CTA button.
 * Collapses completely to 0dp when no ad is available or for VIP/Ad-Free users.
 */
@Composable
fun AdMobNativeCardAd(
    adTag: String = "native_default",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isAdminSession by NeliAdminManager.isAdminSessionActive.collectAsState()
    val nativeAd by NeliAdManager.nativeAdState.collectAsState()
    val isLightMode = NeliThemeManager.isLightMode

    val isVipAdFree = subState.isActiveNow ||
        NeliFreeForeverAccountsManager.isAdFreeVipEmail(subState.linkedUserEmail) ||
        isAdminSession

    if (isVipAdFree ||
        !NeliAdManager.isAdsAllowed(context) ||
        !NeliAdManager.isDeviceOnline(context) ||
        NeliAdManager.isRunningInRobolectricTest()
    ) {
        return
    }

    LaunchedEffect(adTag) {
        NeliAdManager.initialize(context.applicationContext)
        NeliAdManager.preloadNativeAd(context.applicationContext)
    }

    val activeAd = nativeAd ?: return

    val cardBg = if (isLightMode) Color(0xFFFDF2F6) else Color(0xFF10152B)
    val cardBorder = if (isLightMode) Color(0x44FF2E7E) else Color(0x44FF2E7E)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .padding(2.dp)
            .testTag("admob_native_$adTag")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                buildStyledNativeAdView(ctx, isLightMode)
            },
            update = { nativeAdView ->
                bindNativeAdToView(nativeAdView, activeAd, isLightMode)
            }
        )
    }
}

private fun dpToPx(context: Context, dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        context.resources.displayMetrics
    ).toInt()
}

private fun buildStyledNativeAdView(context: Context, isLightMode: Boolean): NativeAdView {
    val pad = dpToPx(context, 12f)
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val rootRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(pad, pad, pad, pad)
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val iconSize = dpToPx(context, 48f)
    val iconView = ImageView(context).apply {
        id = android.R.id.icon
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
            marginEnd = dpToPx(context, 10f)
        }
    }

    val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dpToPx(context, 10f)
        }
    }

    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val badgeView = TextView(context).apply {
        text = "Ad"
        textSize = 9f
        setTypeface(null, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        val badgeBg = GradientDrawable().apply {
            cornerRadius = dpToPx(context, 4f).toFloat()
            setColor(AndroidColor.parseColor("#FF2E7E"))
        }
        background = badgeBg
        setPadding(dpToPx(context, 5f), dpToPx(context, 1f), dpToPx(context, 5f), dpToPx(context, 1f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dpToPx(context, 6f)
        }
    }

    val headlineView = TextView(context).apply {
        id = android.R.id.text1
        textSize = 13.5f
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(if (isLightMode) AndroidColor.parseColor("#111827") else AndroidColor.WHITE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    headerRow.addView(badgeView)
    headerRow.addView(headlineView)

    val bodyView = TextView(context).apply {
        id = android.R.id.text2
        textSize = 11.5f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setTextColor(if (isLightMode) AndroidColor.parseColor("#4B5563") else AndroidColor.parseColor("#B8C3D9"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(context, 3f)
        }
    }

    textCol.addView(headerRow)
    textCol.addView(bodyView)

    val ctaButton = Button(context).apply {
        id = android.R.id.button1
        textSize = 11.5f
        isAllCaps = false
        setTypeface(null, Typeface.BOLD)
        setTextColor(AndroidColor.WHITE)
        minHeight = dpToPx(context, 38f)
        minimumHeight = dpToPx(context, 38f)
        minWidth = dpToPx(context, 78f)
        val btnBg = GradientDrawable().apply {
            cornerRadius = dpToPx(context, 10f).toFloat()
            setColor(AndroidColor.parseColor("#FF2E7E"))
        }
        background = btnBg
        setPadding(dpToPx(context, 12f), dpToPx(context, 6f), dpToPx(context, 12f), dpToPx(context, 6f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    rootRow.addView(iconView)
    rootRow.addView(textCol)
    rootRow.addView(ctaButton)
    nativeAdView.addView(rootRow)

    nativeAdView.iconView = iconView
    nativeAdView.headlineView = headlineView
    nativeAdView.bodyView = bodyView
    nativeAdView.callToActionView = ctaButton

    return nativeAdView
}

private fun bindNativeAdToView(nativeAdView: NativeAdView, ad: NativeAd, isLightMode: Boolean) {
    (nativeAdView.headlineView as? TextView)?.apply {
        text = ad.headline ?: "Sponsored"
        setTextColor(if (isLightMode) AndroidColor.parseColor("#111827") else AndroidColor.WHITE)
    }
    (nativeAdView.bodyView as? TextView)?.apply {
        val bodyText = ad.body?.takeIf { it.isNotBlank() } ?: ad.advertiser.orEmpty()
        text = bodyText
        visibility = if (bodyText.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
        setTextColor(if (isLightMode) AndroidColor.parseColor("#4B5563") else AndroidColor.parseColor("#B8C3D9"))
    }
    (nativeAdView.iconView as? ImageView)?.apply {
        val iconDrawable = ad.icon?.drawable
        if (iconDrawable != null) {
            setImageDrawable(iconDrawable)
            visibility = android.view.View.VISIBLE
        } else {
            visibility = android.view.View.GONE
        }
    }
    (nativeAdView.callToActionView as? Button)?.apply {
        val cta = ad.callToAction?.takeIf { it.isNotBlank() } ?: "Open"
        text = cta
        visibility = android.view.View.VISIBLE
    }
    nativeAdView.setNativeAd(ad)
}
