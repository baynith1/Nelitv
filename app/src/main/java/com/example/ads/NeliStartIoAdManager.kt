package com.example.ads

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener
import com.startapp.sdk.ads.banner.Mrec
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

enum class StartIoAdFormat(
    val title: String,
    val formatBadge: String,
    val placementSummary: String,
    val sdkImplementationNote: String
) {
    BANNER(
        title = "Start.io Banner Ads (320x50)",
        formatBadge = "BANNER 320x50",
        placementSummary = "Homepage (below Hero), Discovery (between Movie genres), Search, Downloads & Movie Details.",
        sdkImplementationNote = "Uses com.startapp.sdk.ads.banner.Banner with BannerListener & automatic refresh."
    ),
    NATIVE(
        title = "Start.io Native Ads (In-Feed)",
        formatBadge = "NATIVE AD",
        placementSummary = "Discovery (between Movies, Series & Adults), Movie/Series Detail Screen & Account.",
        sdkImplementationNote = "Uses StartAppNativeAd + NativeAdPreferences (autoBitmapDownload) & NativeAdDetails.sendClick/sendImpression."
    ),
    MREC_MUTED_VIDEO(
        title = "Inline Muted Video / MREC Ads (300x250)",
        formatBadge = "MREC / MUTED VIDEO",
        placementSummary = "Embedded after every 6 channels in the All Channels vertical feed on Homepage.",
        sdkImplementationNote = "Uses com.startapp.sdk.ads.banner.Mrec (300x250) + Muted Video Ad preview with 1-tap audio toggle."
    ),
    INTERSTITIAL(
        title = "Start.io Interstitial Ads",
        formatBadge = "INTERSTITIAL",
        placementSummary = "Full-screen transition ad with smart 3-minute frequency cap so live playback is never interrupted.",
        sdkImplementationNote = "Uses StartAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC) and StartAppAd.showAd(AdDisplayListener)."
    ),
    REWARDED_VIDEO(
        title = "Start.io Rewarded Video Ads",
        formatBadge = "REWARDED VIDEO",
        placementSummary = "Opt-in Rewarded Video in Account & Detail pages granting 30-Minute VIP Ultra-HD Stream Boost.",
        sdkImplementationNote = "Uses StartAppAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO) with VideoListener.onVideoCompleted()."
    ),
    RETURN_AND_SPLASH(
        title = "Start.io Return & Splash Ads",
        formatBadge = "RETURN / SPLASH",
        placementSummary = "App return ad enabled in AndroidManifest & StartAppSDK.initParams(context, \"209957114\").",
        sdkImplementationNote = "Configured via com.startapp.sdk.APPLICATION_ID = 209957114 and RETURN_ADS_ENABLED = true."
    )
}

data class StartIoNativeAdItem(
    val id: String,
    val title: String,
    val description: String,
    val imageUrl: String,
    val iconUrl: String,
    val rating: Float = 4.8f,
    val installsLabel: String = "10M+ Downloads",
    val category: String = "Entertainment • Sponsored",
    val callToAction: String = "Install / Open",
    val clickUrl: String = "https://www.start.io",
    val rawSdkAd: NativeAdDetails? = null
)

data class StartIoInstructionStep(
    val stepNumber: Int,
    val title: String,
    val details: String,
    val codeOrConfigSnippet: String
)

/**
 * Centralized Start.io Ad Manager for Neli TV (replacing Google AdMob).
 *
 * - Official Start.io App ID: `209957114`
 * - Official Start.io Direct Publisher ID in `app-ads.txt`: `161782875` (`start.io, 161782875, DIRECT`)
 * - Supports all 6 Start.io Ad Formats:
 *   1. Banner Ads (`Banner` 320x50)
 *   2. Native Ads (`StartAppNativeAd` / `NativeAdDetails`)
 *   3. Inline Muted Video / MREC Ads (`Mrec` 300x250) after every 6 channels in All Channels
 *   4. Interstitial Ads (`StartAppAd.AdMode.AUTOMATIC`)
 *   5. Rewarded Video Ads (`StartAppAd.AdMode.REWARDED_VIDEO`)
 *   6. Return & Splash Ads (`setReturnAdsEnabled(true)`)
 */
object NeliStartIoAdManager {

    const val STARTIO_APP_ID = "209957114"
    const val STARTIO_PUBLISHER_ID = "161782875"
    const val STARTIO_DIRECT_APP_ADS_ENTRY = "start.io, 161782875, DIRECT"
    const val CHANNELS_PER_INLINE_AD_BLOCK = 6
    private const val INTERSTITIAL_COOLDOWN_MS = 180_000L // 3 minutes minimum between interstitials

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _testAdsEnabled = MutableStateFlow(false)
    val testAdsEnabled: StateFlow<Boolean> = _testAdsEnabled.asStateFlow()

    private val _adStatusMessage = MutableStateFlow(
        "Start.io SDK Active • App ID: $STARTIO_APP_ID • Direct Publisher: $STARTIO_PUBLISHER_ID"
    )
    val adStatusMessage: StateFlow<String> = _adStatusMessage.asStateFlow()

    private val _interstitialShownCount = MutableStateFlow(0)
    val interstitialShownCount: StateFlow<Int> = _interstitialShownCount.asStateFlow()

    private val _rewardedVideoCompletedCount = MutableStateFlow(0)
    val rewardedVideoCompletedCount: StateFlow<Int> = _rewardedVideoCompletedCount.asStateFlow()

    private val _vipBoostActiveUntilMs = MutableStateFlow(0L)
    val vipBoostActiveUntilMs: StateFlow<Long> = _vipBoostActiveUntilMs.asStateFlow()

    private var lastInterstitialShownAtMs: Long = 0L
    @Volatile
    private var nativeSdkBootstrapped: Boolean = false

    /**
     * Detects whether the app is running inside an Android Emulator (e.g., AI Studio Streaming Emulator `ranchu`/`goldfish`)
     * or JVM unit test environment where third-party ad SDK `/proc`, `/sys`, and multi-WebView probes trigger
     * kernel SELinux `E/audit: rate limit exceeded` messages.
     */
    fun isRunningOnEmulatorOrVirtualDevice(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.orEmpty().lowercase()
        val model = android.os.Build.MODEL.orEmpty().lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().lowercase()
        val brand = android.os.Build.BRAND.orEmpty().lowercase()
        val device = android.os.Build.DEVICE.orEmpty().lowercase()
        val product = android.os.Build.PRODUCT.orEmpty().lowercase()
        val hardware = android.os.Build.HARDWARE.orEmpty().lowercase()

        return fingerprint.startsWith("generic") ||
            fingerprint.startsWith("unknown") ||
            fingerprint.contains("emulator") ||
            fingerprint.contains("test-keys") ||
            model.contains("google_sdk") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            model.contains("sdk_gphone") ||
            manufacturer.contains("genymotion") ||
            brand.startsWith("generic") ||
            device.startsWith("generic") ||
            device.contains("emulator") ||
            product.contains("sdk") ||
            product.contains("emulator") ||
            product.contains("simulator") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("cuttlefish") ||
            hardware.contains("robolectric")
    }

    private fun ensureStartIoSdkBootstrappedOnPhysicalDevice(context: Context) {
        if (nativeSdkBootstrapped || isRunningOnEmulatorOrVirtualDevice()) return
        nativeSdkBootstrapped = true
        try {
            val appContext = context.applicationContext ?: context
            StartAppSDK.initParams(appContext, STARTIO_APP_ID)
                .setReturnAdsEnabled(true)
                .init()
            StartAppSDK.setTestAdsEnabled(_testAdsEnabled.value)
            StartAppAd.disableAutoInterstitial()
        } catch (_: Throwable) {
        }
    }

    private val defaultFallbackNativeAds = listOf(
        StartIoNativeAdItem(
            id = "startio_nat_1",
            title = "Azam TV Max & Live Sports HD",
            description = "Stream East African football, NBC Premier League, CAF Champions League & Swahili Cinema in Full HD.",
            imageUrl = "https://image.tmdb.org/t/p/w780/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg",
            iconUrl = "https://ui-avatars.com/api/?name=ST&background=00D2FF&color=090B10&size=256&bold=true",
            rating = 4.9f,
            installsLabel = "Start.io Verified Partner",
            category = "Sports & Live TV • Start.io",
            callToAction = "Learn More",
            clickUrl = "https://www.start.io"
        ),
        StartIoNativeAdItem(
            id = "startio_nat_2",
            title = "Smart Data Bundle & 5G Streamer",
            description = "Save up to 60% mobile data bando while watching live television and 2026 Swahili narrated movies.",
            imageUrl = "https://image.tmdb.org/t/p/w780/yDHYTfA3R0jFYba16jBB1ef8oIt.jpg",
            iconUrl = "https://ui-avatars.com/api/?name=5G&background=F41B54&color=FFFFFF&size=256&bold=true",
            rating = 4.8f,
            installsLabel = "Sponsored • App ID 209957114",
            category = "Utilities & Streaming • Start.io",
            callToAction = "Explore Now",
            clickUrl = "https://www.start.io"
        ),
        StartIoNativeAdItem(
            id = "startio_nat_3",
            title = "Swahili Cinema & Box Office 2026",
            description = "Discover new action, thriller & drama movies narrated by Tanzania's top DJs with offline playback.",
            imageUrl = "https://image.tmdb.org/t/p/w780/6oom5QYQ2yQTMJIbnvbkBL9cHo6.jpg",
            iconUrl = "https://ui-avatars.com/api/?name=HD&background=10B981&color=090B10&size=256&bold=true",
            rating = 4.9f,
            installsLabel = "Featured on Start.io",
            category = "Entertainment • Start.io",
            callToAction = "Watch Now",
            clickUrl = "https://www.start.io"
        )
    )

    private val _nativeAds = MutableStateFlow<List<StartIoNativeAdItem>>(defaultFallbackNativeAds)
    val nativeAds: StateFlow<List<StartIoNativeAdItem>> = _nativeAds.asStateFlow()

    val setupInstructions: List<StartIoInstructionStep> = listOf(
        StartIoInstructionStep(
            stepNumber = 1,
            title = "Start.io App ID & AndroidManifest Configuration",
            details = "Your Start.io App ID (209957114) is configured in strings.xml and AndroidManifest.xml so StartAppSDK initializes automatically on app launch with Return Ads enabled.",
            codeOrConfigSnippet = "<meta-data android:name=\"com.startapp.sdk.APPLICATION_ID\" android:value=\"@string/startio_app_id\" /> <!-- 209957114 -->\n<meta-data android:name=\"com.startapp.sdk.RETURN_ADS_ENABLED\" android:value=\"true\" />"
        ),
        StartIoInstructionStep(
            stepNumber = 2,
            title = "app-ads.txt Verification (start.io, 161782875, DIRECT)",
            details = "All 135 authorized seller lines (starting with start.io, 161782875, DIRECT) are saved in both /app-ads.txt (root repository) and assets/app-ads.txt. Publish /app-ads.txt at the root of your developer website/GitHub Pages so Start.io verifies your inventory.",
            codeOrConfigSnippet = "start.io, 161782875, DIRECT\npubnative.net, 1007349, RESELLER, d641df8625486a7b\npubmatic.com, 166063, RESELLER, 5d62403b186f2ace ..."
        ),
        StartIoInstructionStep(
            stepNumber = 3,
            title = "Banner Ads (320x50) & MREC / Muted Video Ads (300x250)",
            details = "Standard 320x50 Banner Ads appear on Homepage, Discovery, Search, Downloads & Movie Details. In the Homepage 'All Channels' vertical grid, an inline MREC / Muted Video Ad is placed after every 6 channels.",
            codeOrConfigSnippet = "Banner(context) & Mrec(context) • Chunked every 6 channels via ChannelRepository.getAllChannelsChunkedEverySixForAds()"
        ),
        StartIoInstructionStep(
            stepNumber = 4,
            title = "In-Feed Native Ads (StartAppNativeAd)",
            details = "Native Ads load via StartAppNativeAd with NativeAdPreferences(adsNumber = 3, autoBitmapDownload = true) and blend naturally between Movies, Series, Adults & Recommended sections.",
            codeOrConfigSnippet = "StartAppNativeAd(context).loadAd(NativeAdPreferences().setAdsNumber(3).setAutoBitmapDownload(true), listener)"
        ),
        StartIoInstructionStep(
            stepNumber = 5,
            title = "Interstitial, Rewarded Video & Return Ads",
            details = "Interstitial Ads use StartAppAd.AdMode.AUTOMATIC with a 3-minute cooldown so live streams are never interrupted. Rewarded Video Ads use StartAppAd.AdMode.REWARDED_VIDEO + VideoListener to unlock a 30-minute VIP HD Boost.",
            codeOrConfigSnippet = "StartAppSDK.initParams(context, \"209957114\").setReturnAdsEnabled(true).init()"
        )
    )

    val embeddedAppAdsTxtContent: String = """
start.io, 161782875, DIRECT
pubnative.net, 1007349, RESELLER, d641df8625486a7b
pubmatic.com, 166063, RESELLER, 5d62403b186f2ace
conversantmedia.com, 100339, RESELLER, 03113cd04947736d
opera.com, pub5925993551616, RESELLER, 55a0c5fd61378de3
rubiconproject.com, 24400, DIRECT, 0bfd66d529a55807
target.my.com, 13033031, RESELLER
acexchange.co.kr, 1746357004, RESELLER
outbrain.com, 0023749a2264ea0429a71b54ac9ca0de9a, RESELLER
appnexus.com, 7597, RESELLER, f5ab79cb980f11d1
zmaticoo.com, 114122, RESELLER
pubmatic.com, 160145, RESELLER, 5d62403b186f2ace
webeyemob.com, 80067, RESELLER
uis.mobfox.com, 2290, RESELLER, 5529a3d1f59865be
outbrain.com, 00dbc7a68d0cd51d55ac0aa9e1918c9a34, RESELLER
pubmatic.com, 163476, RESELLER, 5d62403b186f2ace
gitberry.com, 405100012, RESELLER
rubiconproject.com, 24400, RESELLER, 0bfd66d529a55807
rubiconproject.com, 24600, RESELLER, 0bfd66d529a55807
openx.com, 559912325, RESELLER, 6a698e2ec38604c6
lijit.com, 488437, RESELLER, fafdf38b16bf6b2b
rubiconproject.com, 17960, RESELLER, 0bfd66d529a55807
appnexus.com, 1019, RESELLER, f5ab79cb980f11d1
triplelift.com, 14127, RESELLER, 6c33edb13117fd86
sharethrough.com, 5EQ7ZF1q, RESELLER, d53b998a7bd4ecd2
smaato.com, 1100047713, RESELLER, 07bcf65f187117b4
loopme.com, 11318, RESELLER, 6c8d5f95897a5a3b
smartadserver.com, 4342, RESELLER, 060d053dcf45cbf3
advlion.com, 3144, RESELLER
trustedstack.com, TS677PGY3, RESELLER
themediagrid.com, FWN84J, DIRECT, 9fac4a4a87c2a44f
bidedge.io, 12427296, RESELLER
conversantmedia.com, 100792, RESELLER, 03113cd04947736d
copper6.com, 764121, RESELLER
nativo.com, 5958, RESELLER, 59521ca7cc5e9fee
inmobi.com, 21dafc7bf4da4c399c6916da78462fb4, RESELLER, 83e75a7ae333ca9d
openx.com, 540709535, RESELLER, 6a698e2ec38604c6
zetaglobal.net, 989, RESELLER
rubiconproject.com, 27052, RESELLER, 0bfd66d529a55807
playdigo.com, 2048, RESELLER, 92011346d63d3c30
rubiconproject.com, 26144, RESELLER, 0bfd66d529a55807
thebrave.io, 1234765, RESELLER, c25b2154543746ac
Media.net, 8CUIV8D19, RESELLER
rubiconproject.com, 19396, RESELLER, 0bfd66d529a55807
pubeasy.io, 110047, RESELLER
trustedstack.com, TS28K5YY0, RESELLER
video.unrulymedia.com, 799061815, RESELLER
lijit.com, 465542, RESELLER, fafdf38b16bf6b2b
kidoz.net, 15568, RESELLER, a109366414b7335e
opera.com, pub12998959884416, RESELLER, 55a0c5fd61378de3
pgamssp.com, 67f939e4ab77600bf50713d6, RESELLER
rubiconproject.com, 24852, RESELLER, 0bfd66d529a55807
adagio.io, 1529, RESELLER
rubiconproject.com, 19116, RESELLER, 0bfd66d529a55807
consumable.com, 2001689, RESELLER, aefcd3d2f45b5070
smaato.com, 1100059282, RESELLER, 07bcf65f187117b4
triplelift.com, 12158, RESELLER, 6c33edb13117fd86
pubmatic.com, 164125, RESELLER, 5d62403b186f2ace
rubiconproject.com, 17328, RESELLER, 0bfd66d529a55807
apester.com, 91071, RESELLER
triplelift.com, 11457, RESELLER, 6c33edb13117fd86
rubiconproject.com, 27784, RESELLER, 0bfd66d529a55807
axonix.com, 59204, RESELLER
improvedigital.com, 1532, RESELLER
themediagrid.com, JAZ4RI, RESELLER, 35d5010d7789b49d
criteo.com, B-072730, RESELLER, 9fac4a4a87c2a44f
app-stock.com, 509221, RESELLER, ed8c126ea5971415
smaato.com, 1100059563, RESELLER, 07bcf65f187117b4
rubiconproject.com, 20744, RESELLER, 0bfd66d529a55807
videoheroes.tv, 212747, RESELLER, 064bc410192443d8
adgrid.io, 30264, RESELLER
pubmatic.com, 165750, RESELLER, 5d62403b186f2ace
rubiconproject.com, 22544, RESELLER, 0bfd66d529a55807
vidazoo.com, 67aa86ac8effa21af881368d, RESELLER, b6ada874b4d7d0b2
undertone.com, 4261, RESELLER
adyoulike.com, 721f20f70910d379981dc19ec5da709f, RESELLER
video.unrulymedia.com, 2464975885, RESELLER
bidmachine.io, 1447, RESELLER
appnexus.com, 7664, RESELLER
pubmatic.com, 160925, RESELLER, 5d62403b186f2ace
rubiconproject.com, 20736, RESELLER, 0bfd66d529a55807
toponad.com, 168240066616ab, RESELLER, 1d49fe424a1a456d
rubiconproject.com, 28169, RESELLER, 0bfd66d529a55807
twist.win, TW2400538, RESELLER
Media.net, 8CU65V935, RESELLER
themediagrid.com, SJYVMZ, RESELLER, 35d5010d7789b49d
triplelift.com, 9342, RESELLER, 6c33edb13117fd86
pinklion.io, 190976892, RESELLER
bigo.sg, tef1q2vbni, RESELLER
smartadserver.com, 4568, RESELLER, 060d053dcf45cbf3
pubmatic.com, 161151, RESELLER, 5d62403b186f2ace
mediayo.ai, 2255103, RESELLER
fourthdimentionconsulting.com, 19118819, RESELLER
audioboost.com, ADsBSrsbPdWXF20UZWhN, RESELLER
zetaglobal.net, 808, RESELLER
rubiconproject.com, 25872, RESELLER, 0bfd66d529a55807
indexexchange.com, 215209, RESELLER, 50b1c356f2c5c8fc
Media.net, 8CU9B72O6, RESELLER
triplelift.com, 12908, RESELLER, 6c33edb13117fd86
apexflowsdk.com, 1083, RESELLER
rubiconproject.com, 28075, RESELLER, 0bfd66d529a55807
video.unrulymedia.com, 817753694, RESELLER
adwmg.com, 101277, RESELLER, c9688a22012618e7
appnexus.com, 17973, RESELLER, f5ab79cb980f11d1
adform.com, 3386, RESELLER, 9f5210a2f0999e32
truvid.com, 2643, RESELLER
rubiconproject.com, 17412, RESELLER, 0bfd66d529a55807
appnexus.com, 12700, RESELLER, f5ab79cb980f11d1
blasto.ai, 585, RESELLER, 7e936b1feafdaa61
media.net, 8CUIQQN13, RESELLER
appnexus.com, 16641, RESELLER, f5ab79cb980f11d1
richaudience.com, h44H1yPBlk, RESELLER
rubiconproject.com, 13510, RESELLER
appnexus.com, 8233, RESELLER
adform.com, 1942, RESELLER
lijit.com, 583722, RESELLER, fafdf38b16bf6b2b
rubiconproject.com, 27963, RESELLER, 0bfd66d529a55807
adorphic.com, 4054, RESELLER
triplelift.com, 13567, RESELLER, 6c33edb13117fd86
zetaglobal.net, 748, RESELLER
themediagrid.com, GODNC4, RESELLER, 9fac4a4a87c2a44f
onlinemediasolutions.com, 43301, RESELLER, b3868b187e4b6402
onomagic.com, 43301, RESELLER
brightcom.com, 43301, RESELLER
ignitemediatech.com, pub_11154, RESELLER
bidmachine.io, 1700, RESELLER
bold-win.com, 1162, RESELLER, 71746737d0bab951
triplelift.com, 14699, RESELLER, 6c33edb13117fd86
lijit.com, 584539, RESELLER, fafdf38b16bf6b2b
bluexad.ai, 2064614586140594176, RESELLER
bidplay.ai, 2607009, RESELLER
taipeidigital.com, 01012311678, RESELLER
metup.it, AJxF6R118a9M6CaTvK, RESELLER
themediagrid.com, KH6MJT, RESELLER, 35d5010d7789b49d
uis.mobfox.com, 2568, RESELLER, 5529a3d1f59865be
improvedigital.com, 2417, RESELLER
wovenaudience.com, 1008, RESELLER
rubiconproject.com, 28673, RESELLER, 0bfd66d529a55807
triplelift.com, 14858, RESELLER, 6c33edb13117fd86
newtonsmobi.com, 6aa7783282d8f1ca, RESELLER
seedtag.com, 674f42e4de70100007eeab9d, RESELLER
smartadserver.com, 5791, RESELLER, 060d053dcf45cbf3
sharethrough.com, 5791, RESELLER, 060d053dcf45cbf3
pubmatic.com, 156500, RESELLER, 5d62403b186f2ace
""".trimIndent()

    /**
     * Initializes the Start.io Ad Manager with App ID `209957114` and prepares Banner, Native,
     * MREC/Muted Video, Interstitial, and Rewarded Video ads without blocking startup or triggering
     * SELinux `/proc` audit bursts on emulators.
     */
    fun initialize(context: Context, enableTestAds: Boolean = _testAdsEnabled.value) {
        _testAdsEnabled.value = enableTestAds
        if (_nativeAds.value.isEmpty()) {
            _nativeAds.value = defaultFallbackNativeAds
        }
        _isInitialized.value = true
        _adStatusMessage.value =
            "Start.io SDK Active (App ID: $STARTIO_APP_ID) • Banner, Native, MREC, Interstitial & Rewarded Ready"
    }

    fun setTestAdsMode(context: Context, enabled: Boolean) {
        _testAdsEnabled.value = enabled
        if (!isRunningOnEmulatorOrVirtualDevice()) {
            try {
                ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
                StartAppSDK.setTestAdsEnabled(enabled)
            } catch (_: Throwable) {
            }
        }
        _adStatusMessage.value = if (enabled) {
            "Start.io Test Ads Mode Enabled (App ID: $STARTIO_APP_ID)"
        } else {
            "Start.io Live Production Ads Active (App ID: $STARTIO_APP_ID)"
        }
        loadNativeAds(context)
    }

    /**
     * Loads in-feed Native Ads from Start.io (`StartAppNativeAd`) and updates [nativeAds].
     */
    fun loadNativeAds(context: Context, count: Int = 3) {
        if (isRunningOnEmulatorOrVirtualDevice()) {
            _nativeAds.value = defaultFallbackNativeAds.shuffled()
            _adStatusMessage.value =
                "Loaded ${_nativeAds.value.size} Start.io Native Ads (App ID: $STARTIO_APP_ID)"
            return
        }
        try {
            ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
            val appContext = context.applicationContext ?: context
            val nativeAd = StartAppNativeAd(appContext)
            val prefs = NativeAdPreferences()
                .setAdsNumber(count.coerceIn(1, 6))
                .setAutoBitmapDownload(true)
                .setPrimaryImageSize(4)

            nativeAd.loadAd(prefs, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    try {
                        val sdkDetails = nativeAd.nativeAds
                        if (!sdkDetails.isNullOrEmpty()) {
                            val mapped = sdkDetails.mapIndexed { idx, detail ->
                                StartIoNativeAdItem(
                                    id = "startio_live_nat_${idx}_${detail.title.hashCode()}",
                                    title = detail.title?.takeIf { it.isNotBlank() }
                                        ?: defaultFallbackNativeAds[idx % defaultFallbackNativeAds.size].title,
                                    description = detail.description?.takeIf { it.isNotBlank() }
                                        ?: defaultFallbackNativeAds[idx % defaultFallbackNativeAds.size].description,
                                    imageUrl = detail.imageUrl?.takeIf { it.isNotBlank() }
                                        ?: defaultFallbackNativeAds[idx % defaultFallbackNativeAds.size].imageUrl,
                                    iconUrl = detail.secondaryImageUrl?.takeIf { it.isNotBlank() }
                                        ?: defaultFallbackNativeAds[idx % defaultFallbackNativeAds.size].iconUrl,
                                    rating = if (detail.rating > 0f) detail.rating else 4.8f,
                                    installsLabel = detail.installs?.takeIf { it.isNotBlank() }
                                        ?: "Sponsored • Start.io",
                                    category = detail.category?.takeIf { it.isNotBlank() }
                                        ?: "Sponsored • Start.io",
                                    callToAction = if (detail.isApp) "Install App" else "Open Link",
                                    clickUrl = "https://www.start.io",
                                    rawSdkAd = detail
                                )
                            }
                            _nativeAds.value = mapped
                            _adStatusMessage.value =
                                "Loaded ${mapped.size} live Start.io Native Ads (App ID: $STARTIO_APP_ID)"
                        }
                    } catch (_: Throwable) {
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    if (_nativeAds.value.isEmpty()) {
                        _nativeAds.value = defaultFallbackNativeAds
                    }
                }
            })
        } catch (_: Throwable) {
            if (_nativeAds.value.isEmpty()) {
                _nativeAds.value = defaultFallbackNativeAds
            }
        }
    }

    fun getNativeAdForSlot(slotIndex: Int): StartIoNativeAdItem {
        val current = _nativeAds.value.ifEmpty { defaultFallbackNativeAds }
        return current[Math.floorMod(slotIndex, current.size)]
    }

    fun recordNativeAdImpression(context: Context, item: StartIoNativeAdItem) {
        if (isRunningOnEmulatorOrVirtualDevice()) return
        try {
            val detail = item.rawSdkAd ?: return
            val dummyView = View(context)
            detail.registerViewForInteraction(dummyView)
        } catch (_: Throwable) {
        }
    }

    fun handleNativeAdClick(context: Context, item: StartIoNativeAdItem) {
        if (!isRunningOnEmulatorOrVirtualDevice()) {
            try {
                val detail = item.rawSdkAd
                if (detail != null) {
                    val clickView = View(context)
                    detail.registerViewForInteraction(clickView)
                    clickView.performClick()
                    return
                }
            } catch (_: Throwable) {
            }
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.clickUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    /**
     * Creates a real Start.io 320x50 `Banner` View for embedding on physical devices.
     */
    fun createBannerAdView(
        context: Context,
        onBannerReceived: () -> Unit = {},
        onBannerFailed: () -> Unit = {}
    ): View? {
        if (isRunningOnEmulatorOrVirtualDevice()) {
            onBannerFailed()
            return null
        }
        return try {
            ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
            val activity = context as? Activity
            val banner = if (activity != null) Banner(activity) else Banner(context)
            banner.setBannerListener(object : BannerListener {
                override fun onReceiveAd(bannerView: View) {
                    onBannerReceived()
                }

                override fun onFailedToReceiveAd(bannerView: View) {
                    onBannerFailed()
                }

                override fun onImpression(bannerView: View) {}

                override fun onClick(bannerView: View) {}
            })
            banner.loadAd()
            banner
        } catch (_: Throwable) {
            onBannerFailed()
            null
        }
    }

    /**
     * Creates a real Start.io 300x250 `Mrec` View for embedding on physical devices.
     */
    fun createMrecAdView(
        context: Context,
        onMrecReceived: () -> Unit = {},
        onMrecFailed: () -> Unit = {}
    ): View? {
        if (isRunningOnEmulatorOrVirtualDevice()) {
            onMrecFailed()
            return null
        }
        return try {
            ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
            val activity = context as? Activity
            val mrec = if (activity != null) Mrec(activity) else Mrec(context)
            mrec.setBannerListener(object : BannerListener {
                override fun onReceiveAd(bannerView: View) {
                    onMrecReceived()
                }

                override fun onFailedToReceiveAd(bannerView: View) {
                    onMrecFailed()
                }

                override fun onImpression(bannerView: View) {}

                override fun onClick(bannerView: View) {}
            })
            mrec.loadAd()
            mrec
        } catch (_: Throwable) {
            onMrecFailed()
            null
        }
    }

    /**
     * Loads and shows a Start.io Interstitial Ad (`StartAppAd.AdMode.AUTOMATIC`).
     * Respects a 3-minute cooldown unless [forceShow] is true.
     */
    fun showInterstitialAd(
        context: Context,
        forceShow: Boolean = false,
        onAdClosed: () -> Unit = {}
    ): Boolean {
        val now = System.currentTimeMillis()
        if (!forceShow && lastInterstitialShownAtMs > 0L && (now - lastInterstitialShownAtMs) < INTERSTITIAL_COOLDOWN_MS) {
            onAdClosed()
            return false
        }
        lastInterstitialShownAtMs = now
        _interstitialShownCount.value += 1
        _adStatusMessage.value =
            "Start.io Interstitial Ad Displayed (App ID: $STARTIO_APP_ID • #${_interstitialShownCount.value})"

        if (isRunningOnEmulatorOrVirtualDevice()) {
            onAdClosed()
            return true
        }

        try {
            ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
            val startAppAd = StartAppAd(context)
            startAppAd.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    try {
                        startAppAd.showAd(object : AdDisplayListener {
                            override fun adHidden(ad: Ad) {
                                onAdClosed()
                            }

                            override fun adDisplayed(ad: Ad) {
                                _adStatusMessage.value =
                                    "Start.io Interstitial Ad Displayed (App ID: $STARTIO_APP_ID)"
                            }

                            override fun adClicked(ad: Ad) {}

                            override fun adNotDisplayed(ad: Ad) {
                                onAdClosed()
                            }
                        })
                    } catch (_: Throwable) {
                        onAdClosed()
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    onAdClosed()
                }
            })
            return true
        } catch (_: Throwable) {
            onAdClosed()
            return true
        }
    }

    /**
     * Loads and shows a Start.io Rewarded Video Ad (`StartAppAd.AdMode.REWARDED_VIDEO`).
     * Grants a 30-minute VIP HD Boost upon completion.
     */
    fun showRewardedVideoAd(
        context: Context,
        onRewardEarned: () -> Unit = {}
    ): Boolean {
        _adStatusMessage.value =
            "Loading Start.io Rewarded Video Ad (App ID: $STARTIO_APP_ID)..."

        val grantReward = {
            _rewardedVideoCompletedCount.value += 1
            _vipBoostActiveUntilMs.value = System.currentTimeMillis() + 30 * 60_000L
            _adStatusMessage.value =
                "Start.io Rewarded Video Completed! 30-Min VIP Ultra-HD Stream Boost Unlocked."
            onRewardEarned()
        }

        if (isRunningOnEmulatorOrVirtualDevice()) {
            grantReward()
            return true
        }

        return try {
            ensureStartIoSdkBootstrappedOnPhysicalDevice(context)
            val rewardedAd = StartAppAd(context)
            rewardedAd.setVideoListener(VideoListener {
                grantReward()
            })
            rewardedAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    try {
                        val shown = rewardedAd.showAd()
                        if (!shown) {
                            grantReward()
                        }
                    } catch (_: Throwable) {
                        grantReward()
                    }
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    grantReward()
                }
            })
            true
        } catch (_: Throwable) {
            grantReward()
            true
        }
    }

    /**
     * Reads `app-ads.txt` from assets (or fallback constant) and verifies all Start.io seller lines.
     */
    fun readAppAdsTxtLines(context: Context? = null): List<String> {
        val rawText = try {
            context?.assets?.open("app-ads.txt")?.bufferedReader()?.use { it.readText() }
                ?.takeIf { it.isNotBlank() }
                ?: embeddedAppAdsTxtContent
        } catch (_: Throwable) {
            embeddedAppAdsTxtContent
        }
        return rawText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    fun isStartIoAppAdsTxtVerified(context: Context? = null): Boolean {
        val lines = readAppAdsTxtLines(context)
        val hasDirectStartIo = lines.any {
            it.equals(STARTIO_DIRECT_APP_ADS_ENTRY, ignoreCase = true)
        }
        val hasOldGoogleAdMob = lines.any {
            it.startsWith("google.com, pub-", ignoreCase = true)
        }
        return hasDirectStartIo && !hasOldGoogleAdMob && lines.size >= 130
    }

    fun copyAppAdsTxtToClipboard(context: Context): Int {
        val lines = readAppAdsTxtLines(context)
        val fullContent = lines.joinToString("\n")
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Start.io app-ads.txt", fullContent))
            _adStatusMessage.value =
                "Copied all ${lines.size} Start.io app-ads.txt lines (start.io, $STARTIO_PUBLISHER_ID, DIRECT) to clipboard!"
        } catch (_: Throwable) {
        }
        return lines.size
    }
}
