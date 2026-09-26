package com.example.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import com.example.BuildConfig
import com.example.data.OfflineDownloadManager
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized Google AdMob Monetization Manager for Neli TV.
 *
 * Strictly enforces user experience, startup speed, and placement rules:
 * - Initialized in [com.example.NeliApplication] on a background thread with test device IDs
 *   configured for development so app startup is fast and never blocks the main UI thread.
 * - NO ADS inside the video player (PlayerScreen / Live TV / Movie / Series playback).
 * - Uses test ads and test device configuration during development (BuildConfig.DEBUG),
 *   and switches automatically to the publisher's production AdMob IDs in release builds.
 * - Interstitial Ads: Cooldown-limited, never during playback, never after every movie click,
 *   never immediately after an App Open ad. Triggered naturally when user presses Download:
 *   if eligible and loaded -> show interstitial -> ad closes -> start download automatically;
 *   if unavailable/ineligible -> start download immediately without requiring a second tap.
 * - App Open Ads: Shown only occasionally when returning to foreground from background,
 *   never during Movie/Live TV playback, never immediately after another full-screen ad.
 */
object NeliAdMobManager {

    // IAB Tech Lab app-ads.txt specification snippet
    const val APP_ADS_TXT_SNIPPET = "google.com, pub-4408731854837351, DIRECT, f08c47fec0942fa0"

    // Publisher Production AdMob IDs
    const val APP_ID = "ca-app-pub-4408731854837351~1794082871"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-4408731854837351/6443774325"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-4408731854837351/4300078286"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4408731854837351/7721371986"
    const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4408731854837351/6635346014"
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-4408731854837351/5246242721"
    const val NATIVE_ADVANCED_AD_UNIT_ID = "ca-app-pub-4408731854837351/7038845705"

    // Official Google AdMob Test Ad Unit IDs for safe development/test device configuration
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    const val TEST_NATIVE_ADVANCED_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_NATIVE_VIDEO_AD_UNIT_ID = "ca-app-pub-3940256099942544/1044960115"

    // Standard development test device IDs (Emulator + development test device ID)
    const val DEVELOPMENT_TEST_DEVICE_ID = "33BE2250B43518CCDA7DE426D04EE231"
    val DEFAULT_TEST_DEVICE_IDS: List<String> = listOf(
        AdRequest.DEVICE_ID_EMULATOR,
        DEVELOPMENT_TEST_DEVICE_ID
    )

    // Frequency / Cooldown limits (in milliseconds)
    const val INTERSTITIAL_COOLDOWN_MS = 180_000L // 3 minutes between interstitials
    const val APP_OPEN_COOLDOWN_MS = 240_000L // 4 minutes between App Open ads
    const val POST_APP_OPEN_GRACE_MS = 60_000L // 1 minute grace after App Open ad before any Interstitial
    const val POST_FULLSCREEN_AD_GRACE_MS = 90_000L // 1.5 minutes grace after Interstitial before App Open
    private const val APP_OPEN_MAX_CACHE_AGE_MS = 4 * 3600_000L // 4 hours max cache validity

    private val isInitialized = AtomicBoolean(false)
    private val adInitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isApplicationInitialized: Boolean = false
        private set

    @Volatile
    var configuredTestDeviceIds: List<String> = emptyList()
        private set

    @Volatile
    var useTestAdsInDevelopment: Boolean = BuildConfig.DEBUG

    @Volatile
    var isPlaybackActive: Boolean = false
        private set

    @Volatile
    var isShowingFullScreenAd: Boolean = false
        private set

    @Volatile
    var lastInterstitialShownAtMs: Long = 0L
        internal set

    @Volatile
    var lastAppOpenShownAtMs: Long = 0L
        internal set

    @Volatile
    var lastAnyFullScreenAdDismissedAtMs: Long = 0L
        internal set

    private var appOpenAd: AppOpenAd? = null
    private var appOpenAdLoadedAtMs: Long = 0L
    private var isLoadingAppOpenAd: Boolean = false
    private val hasAttemptedInitialAppOpenShow = AtomicBoolean(false)
    private val lifecycleCallbacksRegistered = AtomicBoolean(false)

    @Volatile
    private var currentForegroundActivityRef: WeakReference<Activity>? = null

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitialAd: Boolean = false

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoadingRewardedInterstitialAd: Boolean = false

    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewardedAd: Boolean = false

    // Tracks banner placement status so banners do not constantly reload when scrolling
    internal val bannerLoadStatusByPlacement = ConcurrentHashMap<String, Boolean>()

    fun resolveAppOpenAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_APP_OPEN_AD_UNIT_ID else APP_OPEN_AD_UNIT_ID

    fun resolveBannerAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID

    fun resolveInterstitialAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_INTERSTITIAL_AD_UNIT_ID else INTERSTITIAL_AD_UNIT_ID

    fun resolveRewardedInterstitialAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID else REWARDED_INTERSTITIAL_AD_UNIT_ID

    fun resolveRewardedAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_REWARDED_AD_UNIT_ID else REWARDED_AD_UNIT_ID

    fun resolveNativeAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_NATIVE_ADVANCED_AD_UNIT_ID else NATIVE_ADVANCED_AD_UNIT_ID

    fun resolveNativeVideoAdUnitId(useTestAds: Boolean = useTestAdsInDevelopment): String =
        if (useTestAds) TEST_NATIVE_VIDEO_AD_UNIT_ID else NATIVE_ADVANCED_AD_UNIT_ID

    /**
     * Configures AdMob test device IDs for development so test ads are safely served on
     * emulators and registered test devices.
     */
    fun configureTestDeviceIdsForDevelopment(
        extraTestDeviceIds: List<String> = emptyList()
    ): RequestConfiguration {
        val deviceIds = (DEFAULT_TEST_DEVICE_IDS + extraTestDeviceIds).distinct()
        configuredTestDeviceIds = deviceIds
        val requestConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(deviceIds)
            .build()
        try {
            MobileAds.setRequestConfiguration(requestConfig)
        } catch (_: Throwable) {
        }
        return requestConfig
    }

    /**
     * Called from [com.example.NeliApplication.onCreate] during app startup.
     * Configures the test device IDs immediately and initializes the Google Mobile Ads SDK
     * asynchronously on a background thread so startup is fast and never stalls the main UI thread.
     */
    fun initializeInApplication(application: Application) {
        val appContext = application.applicationContext ?: application
        isApplicationInitialized = true
        configureTestDeviceIdsForDevelopment()
        if (lifecycleCallbacksRegistered.compareAndSet(false, true)) {
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: Activity) {
                    if (!isShowingFullScreenAd) {
                        currentForegroundActivityRef = WeakReference(activity)
                    }
                }
                override fun onActivityResumed(activity: Activity) {
                    if (!isShowingFullScreenAd) {
                        currentForegroundActivityRef = WeakReference(activity)
                    }
                }
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    if (currentForegroundActivityRef?.get() === activity) {
                        currentForegroundActivityRef = null
                    }
                }
            })
        }
        if (isInitialized.compareAndSet(false, true)) {
            adInitScope.launch {
                try {
                    MobileAds.initialize(appContext) {
                        mainHandler.post {
                            preloadAppOpenAd(appContext)
                        }
                        mainHandler.postDelayed({
                            preloadInterstitialAd(appContext)
                        }, 1200L)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Ensures AdMob is initialized if not already started by [NeliApplication].
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext ?: context
        (context.findActivity())?.let { activity ->
            currentForegroundActivityRef = WeakReference(activity)
        }
        if (configuredTestDeviceIds.isEmpty()) {
            configureTestDeviceIdsForDevelopment()
        }
        if (isInitialized.compareAndSet(false, true)) {
            adInitScope.launch {
                try {
                    MobileAds.initialize(appContext) {
                        mainHandler.post {
                            preloadAppOpenAd(appContext)
                        }
                        mainHandler.postDelayed({
                            preloadInterstitialAd(appContext)
                        }, 1200L)
                    }
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Updates whether video playback (Movie, Series, or Live TV) is currently active.
     * While playback is active, NO full-screen ads (Interstitial / App Open) are ever allowed.
     */
    fun updatePlaybackActiveState(active: Boolean) {
        isPlaybackActive = active
    }

    /**
     * Evaluates whether an Interstitial ad is allowed to be shown right now.
     * Never allowed:
     * - inside the video player or while playback is active
     * - while another full-screen ad is already showing
     * - within INTERSTITIAL_COOLDOWN_MS of a previous interstitial
     * - immediately after an App Open ad (within POST_APP_OPEN_GRACE_MS)
     */
    fun isInterstitialEligible(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isPlaybackActive) return false
        if (isShowingFullScreenAd) return false
        if (lastInterstitialShownAtMs > 0L && (nowMs - lastInterstitialShownAtMs) < INTERSTITIAL_COOLDOWN_MS) {
            return false
        }
        if (lastAppOpenShownAtMs > 0L && (nowMs - lastAppOpenShownAtMs) < POST_APP_OPEN_GRACE_MS) {
            return false
        }
        if (lastAnyFullScreenAdDismissedAtMs > 0L && (nowMs - lastAnyFullScreenAdDismissedAtMs) < POST_APP_OPEN_GRACE_MS) {
            return false
        }
        return true
    }

    /**
     * Evaluates whether an App Open ad is allowed to be shown when the app returns to foreground.
     * Never allowed:
     * - while watching a movie or series
     * - while playing Live TV
     * - immediately after another full-screen ad
     * - repeatedly (enforces APP_OPEN_COOLDOWN_MS)
     */
    fun isAppOpenEligible(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (isPlaybackActive) return false
        if (isShowingFullScreenAd) return false
        if (lastAppOpenShownAtMs > 0L && (nowMs - lastAppOpenShownAtMs) < APP_OPEN_COOLDOWN_MS) {
            return false
        }
        if (lastInterstitialShownAtMs > 0L && (nowMs - lastInterstitialShownAtMs) < POST_FULLSCREEN_AD_GRACE_MS) {
            return false
        }
        if (lastAnyFullScreenAdDismissedAtMs > 0L && (nowMs - lastAnyFullScreenAdDismissedAtMs) < POST_FULLSCREEN_AD_GRACE_MS) {
            return false
        }
        return true
    }

    fun preloadInterstitialAd(context: Context) {
        val appContext = context.applicationContext ?: context
        if (interstitialAd != null || isLoadingInterstitialAd) return
        if (!OfflineDownloadManager.isDeviceOnline(appContext)) return

        isLoadingInterstitialAd = true
        val unitId = resolveInterstitialAdUnitId()
        mainHandler.post {
            try {
                InterstitialAd.load(
                    appContext,
                    unitId,
                    AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: InterstitialAd) {
                            interstitialAd = ad
                            isLoadingInterstitialAd = false
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            interstitialAd = null
                            isLoadingInterstitialAd = false
                        }
                    }
                )
            } catch (_: Throwable) {
                isLoadingInterstitialAd = false
            }
        }
    }

    /**
     * Natural Download Interstitial flow:
     * User presses Download:
     * -> if eligible and ad is loaded -> show interstitial -> ad closes -> start download automatically
     * -> if no ad is loaded (or not eligible) -> start download immediately.
     * Never makes the user press Download again.
     */
    fun runDownloadWithInterstitialIfEligible(
        context: Context,
        onStartDownload: () -> Unit
    ) {
        val downloadStarted = AtomicBoolean(false)
        val startDownloadOnce = {
            if (downloadStarted.compareAndSet(false, true)) {
                onStartDownload()
            }
        }

        val activity = context.findActivity()
        val loadedAd = interstitialAd
        val now = System.currentTimeMillis()

        if (activity == null || activity.isFinishing || loadedAd == null || !isInterstitialEligible(now)) {
            startDownloadOnce()
            preloadInterstitialAd(context)
            return
        }

        interstitialAd = null
        isShowingFullScreenAd = true
        lastInterstitialShownAtMs = now

        loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                lastAnyFullScreenAdDismissedAtMs = System.currentTimeMillis()
                preloadInterstitialAd(activity)
                startDownloadOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd = false
                preloadInterstitialAd(activity)
                startDownloadOnce()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
            }
        }

        try {
            loadedAd.show(activity)
        } catch (_: Throwable) {
            isShowingFullScreenAd = false
            startDownloadOnce()
        }
    }

    fun preloadAppOpenAd(context: Context) {
        val appContext = context.applicationContext ?: context
        if (isAppOpenAdAvailable() || isLoadingAppOpenAd) return
        if (!OfflineDownloadManager.isDeviceOnline(appContext)) return

        isLoadingAppOpenAd = true
        val unitId = resolveAppOpenAdUnitId()
        mainHandler.post {
            try {
                AppOpenAd.load(
                    appContext,
                    unitId,
                    AdRequest.Builder().build(),
                    object : AppOpenAd.AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            appOpenAd = ad
                            appOpenAdLoadedAtMs = System.currentTimeMillis()
                            isLoadingAppOpenAd = false
                            // On initial app launch (cold start), show the App Open Ad once if the user is on the
                            // foreground browse screen and has not started playing any Live TV, Movie, or Series.
                            if (hasAttemptedInitialAppOpenShow.compareAndSet(false, true)) {
                                val fgActivity = currentForegroundActivityRef?.get()
                                if (fgActivity != null && !fgActivity.isFinishing && !isPlaybackActive) {
                                    showAppOpenAdOnForegroundIfEligible(fgActivity)
                                }
                            }
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            appOpenAd = null
                            isLoadingAppOpenAd = false
                        }
                    }
                )
            } catch (_: Throwable) {
                isLoadingAppOpenAd = false
            }
        }
    }

    private fun isAppOpenAdAvailable(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (appOpenAd == null) return false
        val age = nowMs - appOpenAdLoadedAtMs
        if (age >= APP_OPEN_MAX_CACHE_AGE_MS) {
            appOpenAd = null
            return false
        }
        return true
    }

    /**
     * Shows an App Open ad only occasionally when the app returns to the foreground from the background.
     * Never shows during Movie/Series/Live TV playback or immediately after another full-screen ad.
     * If unavailable, continues normally.
     */
    fun showAppOpenAdOnForegroundIfEligible(activity: Activity, onContinue: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (activity.isFinishing || !isAppOpenEligible(now)) {
            onContinue()
            return
        }

        if (!isAppOpenAdAvailable(now)) {
            preloadAppOpenAd(activity)
            onContinue()
            return
        }

        val adToShow = appOpenAd ?: run {
            onContinue()
            return
        }

        appOpenAd = null
        isShowingFullScreenAd = true
        lastAppOpenShownAtMs = now

        adToShow.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isShowingFullScreenAd = false
                lastAnyFullScreenAdDismissedAtMs = System.currentTimeMillis()
                preloadAppOpenAd(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingFullScreenAd = false
                preloadAppOpenAd(activity)
                onContinue()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingFullScreenAd = true
            }
        }

        try {
            adToShow.show(activity)
        } catch (_: Throwable) {
            isShowingFullScreenAd = false
            onContinue()
        }
    }

    fun preloadRewardedInterstitialAd(context: Context) {
        val appContext = context.applicationContext ?: context
        if (rewardedInterstitialAd != null || isLoadingRewardedInterstitialAd) return
        if (!OfflineDownloadManager.isDeviceOnline(appContext)) return

        isLoadingRewardedInterstitialAd = true
        mainHandler.post {
            try {
                RewardedInterstitialAd.load(
                    appContext,
                    resolveRewardedInterstitialAdUnitId(),
                    AdRequest.Builder().build(),
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedInterstitialAd) {
                            rewardedInterstitialAd = ad
                            isLoadingRewardedInterstitialAd = false
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedInterstitialAd = null
                            isLoadingRewardedInterstitialAd = false
                        }
                    }
                )
            } catch (_: Throwable) {
                isLoadingRewardedInterstitialAd = false
            }
        }
    }

    fun preloadRewardedAd(context: Context) {
        val appContext = context.applicationContext ?: context
        if (rewardedAd != null || isLoadingRewardedAd) return
        if (!OfflineDownloadManager.isDeviceOnline(appContext)) return

        isLoadingRewardedAd = true
        mainHandler.post {
            try {
                RewardedAd.load(
                    appContext,
                    resolveRewardedAdUnitId(),
                    AdRequest.Builder().build(),
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            rewardedAd = ad
                            isLoadingRewardedAd = false
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            rewardedAd = null
                            isLoadingRewardedAd = false
                        }
                    }
                )
            } catch (_: Throwable) {
                isLoadingRewardedAd = false
            }
        }
    }

    internal fun resetForTesting() {
        isPlaybackActive = false
        isShowingFullScreenAd = false
        lastInterstitialShownAtMs = 0L
        lastAppOpenShownAtMs = 0L
        lastAnyFullScreenAdDismissedAtMs = 0L
        hasAttemptedInitialAppOpenShow.set(false)
        appOpenAd = null
        interstitialAd = null
    }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
