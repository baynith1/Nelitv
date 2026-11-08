package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.NeliSubscriptionManager
import com.example.ui.tv.NeliTvDetector
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.nativead.NativeAdDetails
import com.startapp.sdk.ads.nativead.NativeAdPreferences
import com.startapp.sdk.ads.nativead.StartAppNativeAd
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Start.io Ad Manager for NeliTv 2.5:
 *
 * App ID: 209957114
 *
 * Features:
 * 1. Automatic suppression when valid Premium subscription is active (100% ad-free, no blank gaps).
 * 2. Mobile and Smart TV separation (Smart TV hardware or forced TV mode has ZERO ads).
 * 3. Preloads and manages Interstitial, Rewarded Video, Banner, and Native ads.
 * 4. Channel opening ad integration with timeout safety (never blocks live streams if ad fails).
 * 5. 15-Minute intelligent playback ad-break scheduler.
 * 6. Detailed diagnostic logging under tag [NeliTvAds].
 */
object NeliAdManager {
    private const val TAG = "NeliTvAds"
    const val START_IO_APP_ID = "209957114"

    // 15-Minute playback ad interval in milliseconds (15 * 60 * 1000)
    const val AD_BREAK_INTERVAL_MS = 15 * 60 * 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isInitialized = false

    private var interstitialAd: StartAppAd? = null
    private var rewardedAd: StartAppAd? = null
    private var isInterstitialLoaded = false
    private var isRewardedLoaded = false
    private var isPreloadingInterstitial = false
    private var isPreloadingRewarded = false

    // Playback ad-break timer tracking
    private var currentChannelId: String? = null
    private var lastAdBreakTimeMs: Long = 0L
    private var playbackSessionStartMs: Long = 0L

    private val _nativeCardAdState = MutableStateFlow<NativeAdDetails?>(null)
    val nativeCardAdState: StateFlow<NativeAdDetails?> = _nativeCardAdState.asStateFlow()

    private val _isAdCurrentlyDisplaying = MutableStateFlow(false)
    val isAdCurrentlyDisplaying: StateFlow<Boolean> = _isAdCurrentlyDisplaying.asStateFlow()

    /**
     * Initializes Start.io SDK on application launch.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        val appContext = context.applicationContext

        // Rule: Smart TV has zero ads
        if (NeliTvDetector.isTelevision(appContext)) {
            Log.i(TAG, "Smart TV device detected. Start.io ads permanently disabled.")
            return
        }

        try {
            Log.i(TAG, "Initializing Start.io SDK with App ID $START_IO_APP_ID...")
            // Production: Always serve real live ads from Start.io network
            StartAppSDK.setTestAdsEnabled(false)
            Log.i(TAG, "Start.io real production ads active with App ID $START_IO_APP_ID.")
            StartAppSDK.initParams(appContext, START_IO_APP_ID)
                .setReturnAdsEnabled(false) // Disable intrusive return/splash ads
                .setCallback {
                    isInitialized = true
                    Log.i(TAG, "Start.io SDK initialized successfully!")
                    preloadInterstitialAd(appContext)
                    preloadRewardedAd(appContext)
                    preloadNativeAd(appContext)
                }
                .init()
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Start.io initialization failed: ${e.message}", e)
        }
    }

    /**
     * Centralized ad gate: Returns true ONLY if user is on Mobile AND does not have an active Premium plan.
     */
    fun isAdsAllowed(context: Context): Boolean {
        // 1. Smart TV devices are 100% ad-free
        if (NeliTvDetector.isTelevision(context)) {
            return false
        }
        // 2. Active Premium subscription users are 100% ad-free
        if (NeliSubscriptionManager.subscriptionState.value.isActiveNow) {
            return false
        }
        return true
    }

    /**
     * Preloads the next interstitial ad in background.
     */
    fun preloadInterstitialAd(context: Context) {
        if (!isAdsAllowed(context) || isPreloadingInterstitial) return
        isPreloadingInterstitial = true

        mainHandler.post {
            try {
                if (interstitialAd == null) {
                    interstitialAd = StartAppAd(context.applicationContext)
                }
                Log.d(TAG, "Requesting preloaded Interstitial ad from Start.io...")
                interstitialAd?.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        isInterstitialLoaded = true
                        isPreloadingInterstitial = false
                        Log.i(TAG, "Start.io Interstitial preloaded successfully!")
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        isInterstitialLoaded = false
                        isPreloadingInterstitial = false
                        Log.w(TAG, "Start.io Interstitial preload failed: ${ad?.errorMessage}")
                    }
                })
            } catch (e: Exception) {
                isPreloadingInterstitial = false
                Log.e(TAG, "Error preloading interstitial: ${e.message}")
            }
        }
    }

    /**
     * Preloads the next rewarded video ad in background.
     */
    fun preloadRewardedAd(context: Context) {
        if (!isAdsAllowed(context) || isPreloadingRewarded) return
        isPreloadingRewarded = true

        mainHandler.post {
            try {
                if (rewardedAd == null) {
                    rewardedAd = StartAppAd(context.applicationContext)
                }
                Log.d(TAG, "Requesting preloaded Rewarded Video ad from Start.io...")
                rewardedAd?.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        isRewardedLoaded = true
                        isPreloadingRewarded = false
                        Log.i(TAG, "Start.io Rewarded Video preloaded successfully!")
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        isRewardedLoaded = false
                        isPreloadingRewarded = false
                        Log.w(TAG, "Start.io Rewarded Video preload failed: ${ad?.errorMessage}")
                    }
                })
            } catch (e: Exception) {
                isPreloadingRewarded = false
                Log.e(TAG, "Error preloading rewarded video: ${e.message}")
            }
        }
    }

    /**
     * Preloads a native ad matching NeliTv's channel card layout.
     */
    fun preloadNativeAd(context: Context) {
        if (!isAdsAllowed(context)) {
            _nativeCardAdState.value = null
            return
        }
        mainHandler.post {
            try {
                val startAppNative = StartAppNativeAd(context.applicationContext)
                val pref = NativeAdPreferences()
                    .setAdsNumber(1)
                    .setAutoBitmapDownload(true)
                    .setPrimaryImageSize(2)

                startAppNative.loadAd(pref, object : AdEventListener {
                    override fun onReceiveAd(ad: Ad) {
                        val ads = startAppNative.nativeAds
                        if (!ads.isNullOrEmpty()) {
                            _nativeCardAdState.value = ads[0]
                            Log.i(TAG, "Start.io Native Ad loaded: ${ads[0].title}")
                        }
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        Log.d(TAG, "Start.io Native Ad no-fill / failed: ${ad?.errorMessage}")
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Error loading native ad: ${e.message}")
            }
        }
    }

    /**
     * Channel Opening Ad:
     * When user taps a channel on "Watch with Ads":
     * - Checks if an Interstitial ad is ready.
     * - If ready, displays it and invokes [onPlaybackResume] upon dismissal.
     * - If no ad is ready, or if ad fails/times out, IMMEDIATELY invokes [onPlaybackResume]
     *   without stalling or blocking player loading.
     */
    fun showChannelOpeningAd(
        activity: Activity,
        channelName: String,
        onPlaybackResume: () -> Unit
    ) {
        if (!isAdsAllowed(activity)) {
            Log.d(TAG, "Ad skipped for $channelName: User has Premium or is on TV.")
            onPlaybackResume()
            return
        }

        val ad = interstitialAd
        if (ad != null && isInterstitialLoaded) {
            Log.i(TAG, "Showing Start.io Interstitial for channel opening ($channelName)...")
            var hasResumed = false
            val safeResume = {
                if (!hasResumed) {
                    hasResumed = true
                    _isAdCurrentlyDisplaying.value = false
                    isInterstitialLoaded = false
                    preloadInterstitialAd(activity)
                    onPlaybackResume()
                }
            }

            // Safety watchdog timeout: If ad UI doesn't return within 8s, resume playback
            mainHandler.postDelayed({
                if (!hasResumed) {
                    Log.w(TAG, "Ad watchdog timeout triggered, resuming playback.")
                    safeResume()
                }
            }, 8000L)

            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    Log.d(TAG, "Interstitial closed by user, starting clean video playback.")
                    safeResume()
                }

                override fun adDisplayed(ad: Ad?) {
                    _isAdCurrentlyDisplaying.value = true
                    Log.i(TAG, "Interstitial impression logged successfully.")
                }

                override fun adClicked(ad: Ad?) {
                    Log.i(TAG, "User clicked Interstitial ad.")
                }

                override fun adNotDisplayed(ad: Ad?) {
                    Log.w(TAG, "Interstitial could not be displayed: ${ad?.errorMessage}")
                    safeResume()
                }
            })
        } else {
            Log.d(TAG, "No preloaded ad ready for $channelName. Opening channel immediately.")
            _isAdCurrentlyDisplaying.value = false
            preloadInterstitialAd(activity)
            onPlaybackResume()
        }
    }

    /**
     * Registers playback session start or channel change to track the 15-minute ad break.
     */
    fun onChannelPlaybackStarted(channelId: String) {
        currentChannelId = channelId
        playbackSessionStartMs = System.currentTimeMillis()
        lastAdBreakTimeMs = System.currentTimeMillis()
        Log.d(TAG, "Ad timer initialized for channel $channelId. Next break in 15 minutes.")
    }

    /**
     * Evaluates whether 15 minutes of uninterrupted playback have elapsed.
     * If ready, triggers ad break safely without corrupting stream playback.
     */
    fun checkAndTrigger15MinAdBreak(
        activity: Activity,
        onBeforeAd: () -> Unit,
        onAfterAd: () -> Unit
    ): Boolean {
        if (!isAdsAllowed(activity)) return false

        val now = System.currentTimeMillis()
        val elapsed = now - lastAdBreakTimeMs

        if (elapsed >= AD_BREAK_INTERVAL_MS) {
            val ad = interstitialAd
            if (ad != null && isInterstitialLoaded) {
                Log.i(TAG, "15-minute ad break interval reached ($elapsed ms). Showing scheduled ad break...")
                lastAdBreakTimeMs = now
                onBeforeAd()

                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad?) {
                        Log.d(TAG, "15-minute ad break dismissed, resuming stream.")
                        isInterstitialLoaded = false
                        preloadInterstitialAd(activity)
                        onAfterAd()
                    }

                    override fun adDisplayed(ad: Ad?) {
                        Log.i(TAG, "15-minute ad break displayed.")
                    }

                    override fun adClicked(ad: Ad?) {
                        Log.i(TAG, "15-minute ad break clicked.")
                    }

                    override fun adNotDisplayed(ad: Ad?) {
                        Log.w(TAG, "15-minute ad could not display, resuming stream directly.")
                        isInterstitialLoaded = false
                        preloadInterstitialAd(activity)
                        onAfterAd()
                    }
                })
                return true
            } else {
                Log.d(TAG, "15-minute interval reached but no ad preloaded. Postponing break cleanly.")
                lastAdBreakTimeMs = now - (AD_BREAK_INTERVAL_MS - 60_000L) // Retry in 1 minute
                preloadInterstitialAd(activity)
            }
        }
        return false
    }

    /**
     * Shows an ad when user taps "Download Movie".
     * Download starts in background and user is never blocked if ad fails or is dismissed.
     */
    fun showDownloadMovieAd(
        activity: Activity,
        onContinueDownload: () -> Unit
    ) {
        if (!isAdsAllowed(activity)) {
            onContinueDownload()
            return
        }

        val ad = interstitialAd
        if (ad != null && isInterstitialLoaded) {
            Log.i(TAG, "Showing Start.io ad for movie download...")
            var continued = false
            fun continueOnce() {
                if (!continued) {
                    continued = true
                    isInterstitialLoaded = false
                    preloadInterstitialAd(activity)
                    onContinueDownload()
                }
            }

            ad.showAd(object : AdDisplayListener {
                override fun adHidden(ad: Ad?) {
                    Log.i(TAG, "Download ad dismissed by user, starting download now.")
                    continueOnce()
                }

                override fun adDisplayed(ad: Ad?) {
                    Log.i(TAG, "Download ad displayed.")
                }

                override fun adClicked(ad: Ad?) {
                    Log.i(TAG, "Download ad clicked.")
                }

                override fun adNotDisplayed(ad: Ad?) {
                    Log.w(TAG, "Download ad not displayed, continuing download.")
                    continueOnce()
                }
            })
        } else {
            Log.d(TAG, "No preloaded ad for download, proceeding immediately.")
            preloadInterstitialAd(activity)
            onContinueDownload()
        }
    }

    /**
     * Shows Start.io App Open / Splash ad on app launch for mobile users.
     */
    fun showAppOpenSplashAd(activity: Activity) {
        if (!isAdsAllowed(activity)) return
        try {
            StartAppAd.showSplash(activity, null)
        } catch (e: Throwable) {
            Log.d(TAG, "App open splash ad skipped: ${e.message}")
        }
    }
}

/**
 * Responsive Start.io Banner Ad Composable.
 *
 * If Premium is active or Smart TV is detected:
 * - Completely hides and collapses container (0dp space, no blank gap).
 *
 * If Ads are enabled:
 * - Embeds native Start.io Banner inside AndroidView.
 * - Auto-handles ad load errors and collapse without jitter.
 */
@Composable
fun StartIoBannerAd(
    modifier: Modifier = Modifier,
    adTag: String = "inline_banner"
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow

    if (!NeliAdManager.isAdsAllowed(context) || isPremiumActive) {
        // Zero height, zero blank space when Premium is active
        return
    }

    var isFailed by remember { mutableStateOf(false) }

    if (!isFailed) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("startio_banner_ad_$adTag"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    val frameLayout = FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        clipChildren = false
                        clipToPadding = false
                    }
                    try {
                        var act: Activity? = null
                        var c = ctx
                        while (c is android.content.ContextWrapper) {
                            if (c is Activity) {
                                act = c
                                break
                            }
                            c = c.baseContext
                        }
                        val bannerContext = act ?: ctx

                        val banner = Banner(bannerContext, object : BannerListener {
                            override fun onReceiveAd(bannerView: View?) {
                                isFailed = false
                                Log.i("NeliTvAds", "Start.io Banner ($adTag) received successfully.")
                            }

                            override fun onFailedToReceiveAd(bannerView: View?) {
                                isFailed = true
                                Log.d("NeliTvAds", "Start.io Banner ($adTag) failed to receive ad. Collapsing.")
                            }

                            override fun onClick(bannerView: View?) {
                                Log.d("NeliTvAds", "Start.io Banner ($adTag) clicked.")
                            }

                            override fun onImpression(bannerView: View?) {
                                Log.d("NeliTvAds", "Start.io Banner ($adTag) impression recorded.")
                            }
                        }).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        }
                        frameLayout.addView(banner)
                    } catch (e: Exception) {
                        isFailed = true
                        Log.e("NeliTvAds", "Error inflating Start.io Banner ($adTag): ${e.message}")
                    }
                    frameLayout
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            )
        }
    }
}

/**
 * Visually integrated Native Ad Card that resembles a channel card on the Home Page,
 * with clear and transparent "AD" badge per Google Play policy.
 */
@Composable
fun StartIoNativeCardAd(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow

    if (!NeliAdManager.isAdsAllowed(context) || isPremiumActive) {
        return
    }

    val nativeDetails by NeliAdManager.nativeCardAdState.collectAsState()

    LaunchedEffect(Unit) {
        if (nativeDetails == null) {
            NeliAdManager.preloadNativeAd(context)
        }
    }

    val ad = nativeDetails ?: return

    var interactionView by remember { mutableStateOf<View?>(null) }
    DisposableEffect(ad) {
        onDispose {
            try {
                ad.unregisterView()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
    ) {
        // Real-sized view layer measured across the full card so StartApp impression validation succeeds
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    interactionView = this
                    try {
                        ad.registerViewForInteraction(this)
                    } catch (e: Exception) {
                        Log.e("NeliTvAds", "Error registering native ad: ${e.message}")
                    }
                }
            },
            modifier = Modifier.matchParentSize()
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable {
                    try {
                        interactionView?.performClick()
                        onClick()
                    } catch (e: Exception) {
                        Log.e("NeliTvAds", "Error clicking native ad: ${e.message}")
                    }
                }
                .border(1.dp, Color(0x55F59E0B), RoundedCornerShape(18.dp))
                .testTag("startio_native_channel_card"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1738)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1D264A), Color(0xFF101738))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ad.imageUrl,
                    contentDescription = ad.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )

                // Prominent and compliant AD badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFF59E0B))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "SPONSORED",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0055FF))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "INSTALL",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            // Info row matching channel card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF241B0D))
                        .border(1.dp, Color(0xFFF59E0B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AD",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ad.title.ifBlank { "Sponsored Recommendation" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ad.description.ifBlank { "Featured app on NeliTv" },
                        color = Color(0xFF8E9BAE),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
}
