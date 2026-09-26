package com.example.ui.components

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button as AndroidButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.NeliAdMobManager
import com.example.data.OfflineDownloadManager
import com.example.ui.theme.NeliSurface
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay

/**
 * High-performance Responsive / Adaptive AdMob Banner Composable.
 *
 * Performance & UX Rules enforced:
 * - Avoids `BoxWithConstraints` subcomposition inside `LazyColumn` so scrolling stays 60/120fps.
 * - Wraps `AdView` in a non-focusable `FrameLayout` with `FOCUS_BLOCK_DESCENDANTS` so internal
 *   AdMob Chromium WebViews NEVER steal focus or force the `LazyColumn` / Window to scroll/climb up!
 * - Defers WebView / `AdView` instantiation via `LaunchedEffect` so initial screen
 *   rendering and fast flinging never stall the main UI thread.
 * - If an ad fails to load (or while loading / when offline), hides the ad container completely
 *   so it never leaves a large empty space or causes layout thrashing.
 */
@Composable
fun NeliAdaptiveBannerAd(
    placementKey: String,
    modifier: Modifier = Modifier,
    adUnitId: String = NeliAdMobManager.resolveBannerAdUnitId()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    val configuration = LocalConfiguration.current

    var isAdLoaded by rememberSaveable(placementKey) { mutableStateOf(false) }
    var isAdFailed by rememberSaveable(placementKey) {
        mutableStateOf(NeliAdMobManager.bannerLoadStatusByPlacement[placementKey] == false)
    }
    var adViewInstance by remember(placementKey) { mutableStateOf<AdView?>(null) }

    val isOnline = remember(context) { OfflineDownloadManager.isDeviceOnline(context) }
    if (isInspection || !isOnline || isAdFailed) {
        return
    }

    val adWidthDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp - 32).coerceAtLeast(300)
    }

    // Defer AdView creation & loading so UI frames and scrolling render smoothly first
    LaunchedEffect(placementKey, adWidthDp) {
        if (adViewInstance == null && !isAdFailed) {
            delay(450L)
            try {
                val createdView = AdView(context).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setAdSize(
                        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                            context,
                            adWidthDp
                        )
                    )
                    this.adUnitId = adUnitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                            isAdFailed = false
                            NeliAdMobManager.bannerLoadStatusByPlacement[placementKey] = true
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isAdLoaded = false
                            isAdFailed = true
                            NeliAdMobManager.bannerLoadStatusByPlacement[placementKey] = false
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
                adViewInstance = createdView
            } catch (_: Throwable) {
                isAdFailed = true
                NeliAdMobManager.bannerLoadStatusByPlacement[placementKey] = false
            }
        }
    }

    DisposableEffect(placementKey) {
        onDispose {
            try {
                adViewInstance?.destroy()
            } catch (_: Throwable) {
            }
            adViewInstance = null
        }
    }

    val activeAdView = adViewInstance
    if (activeAdView != null && !isAdFailed && isAdLoaded) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NeliSurface)
                .border(0.5.dp, Color(0x33A855F7), RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp)
                .heightIn(min = 50.dp, max = 72.dp)
                .testTag("admob_banner_$placementKey"),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        (activeAdView.parent as? ViewGroup)?.removeView(activeAdView)
                        addView(
                            activeAdView,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                Gravity.CENTER
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Native Advanced AdMob Card for Search (placed occasionally between result groups).
 *
 * Rules enforced:
 * - Defers loading slightly so Search filtering and typing remain instantaneous.
 * - Uses AdMob Native Advanced ID (ca-app-pub-4408731854837351/7038845705 in release, test ID in debug).
 * - If the native ad fails or is not yet loaded, hides the container completely (leaves zero empty space).
 */
@Composable
fun NeliNativeSearchAd(
    placementKey: String,
    modifier: Modifier = Modifier,
    adUnitId: String = NeliAdMobManager.resolveNativeAdUnitId()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    var nativeAd by remember(placementKey) { mutableStateOf<NativeAd?>(null) }
    var hasFailed by rememberSaveable(placementKey) { mutableStateOf(false) }
    var hasRequested by remember(placementKey) { mutableStateOf(false) }

    val isOnline = remember(context) { OfflineDownloadManager.isDeviceOnline(context) }

    LaunchedEffect(placementKey, isOnline) {
        if (!isInspection && isOnline && !hasFailed && !hasRequested) {
            delay(300L)
            hasRequested = true
            try {
                val adLoader = AdLoader.Builder(context, adUnitId)
                    .forNativeAd { loadedAd ->
                        nativeAd?.destroy()
                        nativeAd = loadedAd
                        hasFailed = false
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            nativeAd?.destroy()
                            nativeAd = null
                            hasFailed = true
                        }
                    })
                    .withNativeAdOptions(NativeAdOptions.Builder().build())
                    .build()
                adLoader.loadAd(AdRequest.Builder().build())
            } catch (_: Throwable) {
                hasFailed = true
            }
        }
    }

    DisposableEffect(placementKey) {
        onDispose {
            try {
                nativeAd?.destroy()
            } catch (_: Throwable) {
            }
            nativeAd = null
        }
    }

    val currentAd = nativeAd
    if (isInspection || !isOnline || hasFailed || currentAd == null) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x33A855F7), RoundedCornerShape(14.dp))
            .padding(12.dp)
            .testTag("admob_native_$placementKey")
    ) {
        AndroidView(
            factory = { ctx ->
                NativeAdView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    val rootRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val textCol = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }

                    val badgeAndHeadlineRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val adBadgeView = TextView(ctx).apply {
                        text = "Ad"
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        typeface = Typeface.DEFAULT_BOLD
                        setBackgroundColor(AndroidColor.parseColor("#A855F7"))
                        setPadding(12, 4, 12, 4)
                    }

                    val headlineTextView = TextView(ctx).apply {
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(16, 0, 12, 0)
                    }

                    badgeAndHeadlineRow.addView(adBadgeView)
                    badgeAndHeadlineRow.addView(headlineTextView)

                    val bodyTextView = TextView(ctx).apply {
                        setTextColor(AndroidColor.parseColor("#B8C1D9"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, 6, 12, 0)
                    }

                    textCol.addView(badgeAndHeadlineRow)
                    textCol.addView(bodyTextView)

                    val ctaButton = AndroidButton(ctx).apply {
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.DEFAULT_BOLD
                        isAllCaps = false
                        setBackgroundColor(AndroidColor.parseColor("#D946EF"))
                        setPadding(24, 10, 24, 10)
                    }

                    rootRow.addView(textCol)
                    rootRow.addView(ctaButton)
                    addView(rootRow)

                    headlineView = headlineTextView
                    bodyView = bodyTextView
                    callToActionView = ctaButton
                }
            },
            update = { adView ->
                (adView.headlineView as? TextView)?.text = currentAd.headline ?: "Sponsored"
                val bodyView = adView.bodyView as? TextView
                if (!currentAd.body.isNullOrBlank()) {
                    bodyView?.text = currentAd.body
                    bodyView?.visibility = android.view.View.VISIBLE
                } else {
                    bodyView?.visibility = android.view.View.GONE
                }
                val ctaView = adView.callToActionView as? AndroidButton
                if (!currentAd.callToAction.isNullOrBlank()) {
                    ctaView?.text = currentAd.callToAction
                    ctaView?.visibility = android.view.View.VISIBLE
                } else {
                    ctaView?.visibility = android.view.View.GONE
                }
                adView.setNativeAd(currentAd)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Inline Muted Video Ad / Native Media Ad for the "All Channels" vertical feed (placed after every 6 channels).
 *
 * - Configures AdMob [VideoOptions] with `setStartMuted(true)` so video ads play muted while the user
 *   scrolls and browses Live TV channels without interrupting audio.
 * - Embeds a [MediaView] inside a [NativeAdView] so muted video ads continue playing inline.
 * - Automatically falls back to [NeliAdaptiveBannerAd] if no native video ad fill is returned, and hides
 *   completely when offline or unavailable.
 */
@Composable
fun NeliMutedInlineVideoAdCard(
    placementKey: String,
    modifier: Modifier = Modifier,
    adUnitId: String = NeliAdMobManager.resolveNativeVideoAdUnitId()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    var nativeVideoAd by remember(placementKey) { mutableStateOf<NativeAd?>(null) }
    var hasFailed by rememberSaveable(placementKey) { mutableStateOf(false) }
    var hasRequested by remember(placementKey) { mutableStateOf(false) }

    val isOnline = remember(context) { OfflineDownloadManager.isDeviceOnline(context) }

    LaunchedEffect(placementKey, isOnline) {
        if (!isInspection && isOnline && !hasFailed && !hasRequested) {
            delay(260L)
            hasRequested = true
            try {
                val videoOptions = VideoOptions.Builder()
                    .setStartMuted(true)
                    .setCustomControlsRequested(false)
                    .build()

                val nativeAdOptions = NativeAdOptions.Builder()
                    .setVideoOptions(videoOptions)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build()

                val adLoader = AdLoader.Builder(context, adUnitId)
                    .forNativeAd { loadedAd ->
                        nativeVideoAd?.destroy()
                        try {
                            loadedAd.mediaContent?.videoController?.apply {
                                mute(true)
                                videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks() {
                                    override fun onVideoStart() {
                                        mute(true)
                                    }
                                }
                            }
                        } catch (_: Throwable) {
                        }
                        nativeVideoAd = loadedAd
                        hasFailed = false
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            nativeVideoAd?.destroy()
                            nativeVideoAd = null
                            hasFailed = true
                        }
                    })
                    .withNativeAdOptions(nativeAdOptions)
                    .build()
                adLoader.loadAd(AdRequest.Builder().build())
            } catch (_: Throwable) {
                hasFailed = true
            }
        }
    }

    DisposableEffect(placementKey) {
        onDispose {
            try {
                nativeVideoAd?.destroy()
            } catch (_: Throwable) {
            }
            nativeVideoAd = null
        }
    }

    if (isInspection || !isOnline) {
        return
    }

    val currentAd = nativeVideoAd
    if (hasFailed) {
        // Fallback to responsive banner if native video ad had no fill
        NeliAdaptiveBannerAd(
            placementKey = "${placementKey}_banner",
            modifier = modifier
        )
        return
    }

    if (currentAd == null) {
        return
    }

    val density = context.resources.displayMetrics.density
    val mediaHeightPx = (168f * density).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(14.dp))
            .padding(10.dp)
            .testTag("admob_muted_video_$placementKey")
    ) {
        AndroidView(
            factory = { ctx ->
                NativeAdView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    val containerCol = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val mediaViewWidget = MediaView(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            mediaHeightPx
                        ).apply {
                            bottomMargin = (8 * density).toInt()
                        }
                    }

                    val bottomRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    val textCol = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    }

                    val headerRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }

                    val adBadgeView = TextView(ctx).apply {
                        text = "MUTED VIDEO AD"
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        typeface = Typeface.DEFAULT_BOLD
                        setBackgroundColor(AndroidColor.parseColor("#A855F7"))
                        setPadding(12, 4, 12, 4)
                    }

                    val headlineTextView = TextView(ctx).apply {
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        typeface = Typeface.DEFAULT_BOLD
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(14, 0, 10, 0)
                    }

                    headerRow.addView(adBadgeView)
                    headerRow.addView(headlineTextView)

                    val bodyTextView = TextView(ctx).apply {
                        setTextColor(AndroidColor.parseColor("#B8C1D9"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, 4, 10, 0)
                    }

                    textCol.addView(headerRow)
                    textCol.addView(bodyTextView)

                    val ctaButton = AndroidButton(ctx).apply {
                        setTextColor(AndroidColor.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        typeface = Typeface.DEFAULT_BOLD
                        isAllCaps = false
                        setBackgroundColor(AndroidColor.parseColor("#D946EF"))
                        setPadding(22, 8, 22, 8)
                    }

                    bottomRow.addView(textCol)
                    bottomRow.addView(ctaButton)

                    containerCol.addView(mediaViewWidget)
                    containerCol.addView(bottomRow)
                    addView(containerCol)

                    mediaView = mediaViewWidget
                    headlineView = headlineTextView
                    bodyView = bodyTextView
                    callToActionView = ctaButton
                }
            },
            update = { adView ->
                try {
                    currentAd.mediaContent?.let { mc ->
                        mc.videoController.mute(true)
                        adView.mediaView?.mediaContent = mc
                    }
                } catch (_: Throwable) {
                }
                (adView.headlineView as? TextView)?.text = currentAd.headline ?: "Sponsored Video"
                val bodyView = adView.bodyView as? TextView
                if (!currentAd.body.isNullOrBlank()) {
                    bodyView?.text = currentAd.body
                    bodyView?.visibility = android.view.View.VISIBLE
                } else {
                    bodyView?.visibility = android.view.View.GONE
                }
                val ctaView = adView.callToActionView as? AndroidButton
                if (!currentAd.callToAction.isNullOrBlank()) {
                    ctaView?.text = currentAd.callToAction
                    ctaView?.visibility = android.view.View.VISIBLE
                } else {
                    ctaView?.visibility = android.view.View.GONE
                }
                adView.setNativeAd(currentAd)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

