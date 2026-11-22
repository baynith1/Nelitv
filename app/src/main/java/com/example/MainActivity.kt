package com.example

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.model.LiveChannel
import com.example.notifications.NeliNotificationScheduler
import com.example.player.LivePlayerController
import com.example.player.NativeLogSuppressor
import com.example.ads.NeliAdManager
import com.example.ui.NeliViewModel
import com.example.ui.components.BottomNavTab
import com.example.ui.components.FloatingPipPlayerOverlay
import com.example.ui.components.MandatoryGoogleSignInDialog
import com.example.ui.components.NeliHomepageAutoUpdatePopupDialog
import com.example.ui.components.requiresMandatoryGoogleSignIn
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.NeliTVTheme
import com.example.ui.theme.NeliThemeManager
import com.example.widget.NeliHomeWidgetProvider

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_LAUNCH_CHANNEL_ID = "extra_launch_channel_id"
        const val EXTRA_LAUNCH_MEDIA_ID = "extra_launch_media_id"
        const val EXTRA_LAUNCH_TAB = "extra_launch_tab"
    }

    init {
        NativeLogSuppressor.suppressNonFatalNativeLogs()
    }

    private var isSystemInPipMode by mutableStateOf(false)
    private var hasActivePlaybackForPip by mutableStateOf(false)
    private var wasInBackground by mutableStateOf(false)
    private var pendingLaunchChannelId by mutableStateOf<String?>(null)
    private var pendingLaunchMovieId by mutableStateOf<String?>(null)
    private var pendingLaunchTab by mutableStateOf<String?>(null)
    private var isHardwareTv by mutableStateOf(false)
    private var isTvEnvironment by mutableStateOf(false)
    // Increments on cold launch (value=1) and whenever returning to app after >= 4 hours even without closing
    private var recommendedOverlayTriggerTick by mutableStateOf(1)

    private var immersiveAutoHideJob: kotlinx.coroutines.Job? = null
    private var isTabletDevice: Boolean = false

    private fun hideSystemBarsImmersive() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            }
        } catch (_: Exception) {
        }
    }

    private fun scheduleImmersiveBarsAutoHide(delayMs: Long = 5_000L) {
        immersiveAutoHideJob?.cancel()
        immersiveAutoHideJob = lifecycleScope.launch {
            kotlinx.coroutines.delay(delayMs)
            hideSystemBarsImmersive()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBarsImmersive()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBarsImmersive()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        NativeLogSuppressor.suppressNonFatalNativeLogs()
        super.onCreate(savedInstanceState)
        NeliThemeManager.initialize(this)
        com.example.data.NeliWatchProgressManager.initialize(this)
        enableEdgeToEdge()
        hideSystemBarsImmersive()

        // Swipe from status bar or navigation bar edge reveals system bars, then auto-hides after 5 seconds
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            val statusVisible = insets.isVisible(WindowInsetsCompat.Type.statusBars())
            val navVisible = insets.isVisible(WindowInsetsCompat.Type.navigationBars())
            if (statusVisible || navVisible) {
                scheduleImmersiveBarsAutoHide(5_000L)
            }
            androidx.core.view.ViewCompat.onApplyWindowInsets(view, insets)
        }

        // Check for android.hardware.type.television and Tablet form factor to dynamically adjust layout & orientation
        val pm = packageManager
        isHardwareTv = pm.hasSystemFeature("android.hardware.type.television") ||
            pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            pm.hasSystemFeature("android.software.leanback") ||
            (getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        isTvEnvironment = isHardwareTv || NeliThemeManager.forceTvMode == true
        isTabletDevice = resources.configuration.smallestScreenWidthDp >= 600

        if (isTvEnvironment) {
            try {
                if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (_: Exception) {
            }
        } else if (isTabletDevice) {
            // Never lock tablets to portrait so browsing adapts naturally and video opens smoothly in full landscape
            try {
                if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            } catch (_: Exception) {
            }
        } else {
            try {
                if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
            } catch (_: Exception) {
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        } catch (_: Exception) {
        }

        extractDeepLinkFromIntent(intent)

        // Schedule AdMob SDK init (mobile/tablet only — never on Smart TV), notifications, Firebase sync, and update check
        // off the main thread AFTER initial window draw so MainActivity's SurfaceSyncGroup completes immediately.
        if (!isTvEnvironment) {
            lifecycleScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.delay(1_200L)
                NeliAdManager.initialize(this@MainActivity)
            }
        }
        lifecycleScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(600L)
            com.example.data.NeliFirebaseSyncManager.initialize(this@MainActivity)
            NeliNotificationScheduler.scheduleAllDailyNotifications(this@MainActivity)
            NeliHomeWidgetProvider.ensureWidgetAutomaticallyPinnedAndUpdated(this@MainActivity)
            com.example.data.NeliAppUpdateManager.checkForUpdates(this@MainActivity)
        }

        setContent {
            val isLightMode = NeliThemeManager.isLightMode
            val forceTv = NeliThemeManager.forceTvMode
            val tvConfig = remember(isHardwareTv, forceTv) {
                com.example.ui.tv.NeliTvDetector.resolveConfiguration(this@MainActivity)
            }

            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(tvConfig.isTelevision) {
                if (tvConfig.isTelevision) {
                    try {
                        if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } catch (_: Exception) {
                    }
                }
            }

            LaunchedEffect(isLightMode) {
                try {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = isLightMode
                        isAppearanceLightNavigationBars = isLightMode
                    }
                } catch (_: Exception) {
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.example.ui.tv.LocalTvConfiguration provides tvConfig
            ) {
                NeliTVTheme {
                    if (showSplash) {
                        com.example.ui.screens.NeliAnimatedSplashScreen(
                            onSplashFinished = { showSplash = false }
                        )
                    } else {
                        NeliApp(
                            isSystemInPipMode = isSystemInPipMode,
                            pendingChannelId = pendingLaunchChannelId,
                            pendingMovieId = pendingLaunchMovieId,
                            pendingTab = pendingLaunchTab,
                            recommendedOverlayTriggerTick = recommendedOverlayTriggerTick,
                            onConsumeDeepLink = {
                                pendingLaunchChannelId = null
                                pendingLaunchMovieId = null
                                pendingLaunchTab = null
                            },
                            onActivePlaybackChanged = { isActive ->
                                hasActivePlaybackForPip = isActive
                                updateSystemPipParams(isActive)
                            },
                            onRequestSystemPipOutsideApp = {
                                enterSystemPipIfSupported()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch(Dispatchers.IO) {
            com.example.data.NeliAppUpdateManager.reconcileInstalledPackageState(this@MainActivity)
        }
        if (wasInBackground) {
            wasInBackground = false
            if (com.example.ui.components.NeliRecommendedEntryManager.shouldTriggerAfterResume(this)) {
                recommendedOverlayTriggerTick += 1
            }
            // Show Google AdMob App Open Ad when user returns to the app (strictly skipped on TV / VIP / Ad-Free)
            if (!isTvEnvironment && !com.example.ui.tv.NeliTvDetector.isTelevision(this)) {
                NeliAdManager.showAppOpenAdIfAvailable(this)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isInPictureInPictureMode) {
            wasInBackground = true
        }
        if (isFinishing) {
            wasInPipMode = false
            hasActivePlaybackForPip = false
            NeliPlaybackReleaseHook.onReleaseActivePlayback?.invoke()
        }
    }

    override fun onDestroy() {
        wasInPipMode = false
        hasActivePlaybackForPip = false
        NeliPlaybackReleaseHook.onReleaseActivePlayback?.invoke()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLinkFromIntent(intent)
    }

    private fun extractDeepLinkFromIntent(intent: Intent?) {
        if (intent == null) return
        val channelId = intent.getStringExtra(EXTRA_LAUNCH_CHANNEL_ID)
            ?.takeIf { it.isNotBlank() }
        val movieId = intent.getStringExtra(EXTRA_LAUNCH_MEDIA_ID)
            ?.takeIf { it.isNotBlank() }
        val tab = intent.getStringExtra(EXTRA_LAUNCH_TAB)
            ?.takeIf { it.isNotBlank() }

        if (channelId != null) pendingLaunchChannelId = channelId
        if (movieId != null) pendingLaunchMovieId = movieId
        if (tab != null) pendingLaunchTab = tab
    }

    private fun updateSystemPipParams(isActive: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val allowAutoPip = isActive && NeliThemeManager.isPipModeAllowed
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(allowAutoPip)
                    builder.setSeamlessResizeEnabled(true)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {
            }
        }
    }

    private fun enterSystemPipIfSupported(): Boolean {
        if (!NeliThemeManager.isPipModeAllowed) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return try {
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true)
                    builder.setSeamlessResizeEnabled(true)
                }
                enterPictureInPictureMode(builder.build())
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Automatically enter OS Picture-in-Picture mode in the background when user leaves the app via Home
        // or switches to another app, provided PiP Mode is allowed on the user's Account page.
        if (hasActivePlaybackForPip && NeliThemeManager.isPipModeAllowed && !isInPictureInPictureMode && !isFinishing) {
            enterSystemPipIfSupported()
        }
    }

    private var wasInPipMode = false

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isSystemInPipMode = isInPictureInPictureMode
        wasInPipMode = isInPictureInPictureMode
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (!isInMultiWindowMode && !isInPictureInPictureMode) {
            val supported = com.example.notifications.NeliSplitScreenNotifier.isSplitScreenSupported(this)
            if (!supported) {
                com.example.notifications.NeliSplitScreenNotifier.showUnsupportedSplitScreenToast(this)
            }
        }
    }

}

object NeliPlaybackReleaseHook {
    var onReleaseActivePlayback: (() -> Unit)? = null
}

@Composable
fun NeliApp(
    isSystemInPipMode: Boolean = false,
    pendingChannelId: String? = null,
    pendingMovieId: String? = null,
    pendingTab: String? = null,
    recommendedOverlayTriggerTick: Int = 1,
    onConsumeDeepLink: () -> Unit = {},
    onActivePlaybackChanged: (Boolean) -> Unit = {},
    onRequestSystemPipOutsideApp: () -> Unit = {},
    neliViewModel: NeliViewModel = viewModel()
) {
    val context = LocalContext.current

    // Active channel currently playing in full-screen PlayerScreen or outside-app OS PiP
    var activeChannel by remember { mutableStateOf<LiveChannel?>(null) }

    // Shared LivePlayerController persists across Full-Screen PlayerScreen and Outside-App System PiP
    var sharedPlayerController by remember { mutableStateOf<LivePlayerController?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            sharedPlayerController?.release()
            sharedPlayerController = null
        }
    }

    val isPipAllowed = NeliThemeManager.isPipModeAllowed
    LaunchedEffect(activeChannel, sharedPlayerController, isPipAllowed) {
        val channelAllowsPip = activeChannel?.isPipAllowedForChannel ?: false
        val hasActiveStream = channelAllowsPip && ((activeChannel != null && sharedPlayerController != null) || (activeChannel?.isEmbedded == true))
        onActivePlaybackChanged(hasActiveStream && isPipAllowed)
    }

    LaunchedEffect(isSystemInPipMode, sharedPlayerController) {
        sharedPlayerController?.setBackgroundLoadingState(
            isBackgroundLoading = false,
            isPictureInPicture = isSystemInPipMode
        )
    }

    val startOrSwitchChannel: (LiveChannel) -> Unit = { selected ->
        val existing = sharedPlayerController
        activeChannel = selected
        if (existing == null) {
            sharedPlayerController = LivePlayerController(context, selected)
        } else {
            existing.switchChannel(selected)
        }
    }

    val closeAndReleasePlayback: () -> Unit = {
        // Immediately disable system PiP auto-enter before closing so Back navigation never triggers PiP
        onActivePlaybackChanged(false)
        sharedPlayerController?.release()
        sharedPlayerController = null
        activeChannel = null
    }

    DisposableEffect(closeAndReleasePlayback) {
        NeliPlaybackReleaseHook.onReleaseActivePlayback = closeAndReleasePlayback
        onDispose {
            NeliPlaybackReleaseHook.onReleaseActivePlayback = null
        }
    }

    // Handle deep links from Home Screen Widget or Daily Notification taps
    LaunchedEffect(pendingChannelId, pendingMovieId, pendingTab) {
        if (!pendingTab.isNullOrBlank()) {
            val matchedTab = BottomNavTab.entries.find {
                it.name.equals(pendingTab, ignoreCase = true) ||
                    it.label.equals(pendingTab, ignoreCase = true)
            }
            if (matchedTab != null) {
                neliViewModel.selectTab(matchedTab)
            }
            if (pendingChannelId.isNullOrBlank() && pendingMovieId.isNullOrBlank()) {
                onConsumeDeepLink()
            }
        }
        if (!pendingChannelId.isNullOrBlank()) {
            val matchedChannel = ChannelRepository.getChannelById(pendingChannelId)
                ?: ChannelRepository.liveChannelsFlow.value.firstOrNull { ch ->
                    ch.id.equals(pendingChannelId, ignoreCase = true) ||
                        ch.name.contains(pendingChannelId.replace("-", " "), ignoreCase = true)
                }
            if (matchedChannel != null) {
                startOrSwitchChannel(matchedChannel)
                onConsumeDeepLink()
            }
        } else if (!pendingMovieId.isNullOrBlank()) {
            val matchedMovie = MediaContentRepository.getMediaById(pendingMovieId)
                ?: MediaContentRepository.getLatest2026NonAdultMovies(3).firstOrNull()
            if (matchedMovie != null && matchedMovie.streamUrl.isNotBlank()) {
                startOrSwitchChannel(matchedMovie.toPlayableChannel())
                onConsumeDeepLink()
            }
        }
    }

    // PiP is strictly ONLY supported OUTSIDE the app via Android System PiP (when user presses Home or switches to SMS/WhatsApp).
    // Never render a floating PiP overlay inside the NeliPlay app UI!
    val controller = sharedPlayerController
    if (isSystemInPipMode && activeChannel != null && activeChannel!!.isPipAllowedForChannel) {
        if (activeChannel!!.isEmbedded) {
            // Keep embedded channel playing smoothly inside system PiP window without black screen
            com.example.ui.screens.EmbeddedChannelWebViewPlayer(
                channel = activeChannel!!,
                onBack = closeAndReleasePlayback
            )
            return
        } else if (controller != null) {
            com.example.ui.components.SystemPipPlayerView(
                playerController = controller,
                onExpandToFullScreen = {},
                onClosePip = closeAndReleasePlayback
            )
            return
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentChan = activeChannel
        val currentCtrl = sharedPlayerController
        val showFullPlayer = currentChan != null && currentCtrl != null

        var wasInFullPlayer by remember { mutableStateOf(false) }
        LaunchedEffect(showFullPlayer, isSystemInPipMode) {
            if (showFullPlayer) {
                wasInFullPlayer = true
            } else if (wasInFullPlayer && !isSystemInPipMode) {
                wasInFullPlayer = false
                val act = context as? Activity
                act?.window?.decorView?.post {
                    try {
                        val isTv = com.example.ui.tv.NeliTvDetector.isTelevision(context)
                        val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
                        val desiredOrientation = when {
                            isTv -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            isTablet -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                        if (act.requestedOrientation != desiredOrientation) {
                            act.requestedOrientation = desiredOrientation
                        }
                        act.window?.decorView?.let { decor ->
                            decor.scrollTo(0, 0)
                            decor.translationY = 0f
                            WindowCompat.getInsetsController(act.window, decor).apply {
                                systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                                val isLight = NeliThemeManager.isLightMode
                                isAppearanceLightStatusBars = isLight
                                isAppearanceLightNavigationBars = isLight
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        if (currentChan != null && currentCtrl != null) {
            PlayerScreen(
                channel = currentChan,
                onBack = {
                    val lastWatchedChannel = currentCtrl.currentChannel.value
                    // Always route Movie/Series watchpage back navigation to its Movie Details page (and then Discovery)
                    neliViewModel.onReturnFromWatchPage(lastWatchedChannel)
                    // Back button NEVER triggers PiP; it cleanly stops playback and returns to the previous screen.
                    closeAndReleasePlayback()
                },
                sharedController = currentCtrl,
                onEnterPipMode = {
                    // Enters Android OS Picture-in-Picture mode OUTSIDE the app (never inside the app)
                    onRequestSystemPipOutsideApp()
                }
            )
        } else {
            HomeScreen(
                onChannelSelected = { selected ->
                    startOrSwitchChannel(selected)
                },
                neliViewModel = neliViewModel
            )
        }

        val currentUser by neliViewModel.currentUser.collectAsState()
        val mediaCatalog by neliViewModel.mediaCatalog.collectAsState()
        val hasResolvedInitialAuth by neliViewModel.hasResolvedInitialAuth.collectAsState()
        val isAuthLoading by neliViewModel.isAuthLoading.collectAsState()
        val authError by neliViewModel.authError.collectAsState()
        val tvConfig = com.example.ui.tv.LocalTvConfiguration.current
        val isTvMode = tvConfig.isTvDevice || com.example.ui.tv.NeliTvDetector.isTelevision(context)
        val needsMandatorySignIn = !isTvMode &&
            hasResolvedInitialAuth &&
            requiresMandatoryGoogleSignIn(currentUser, isTelevision = isTvMode)

        // YouTube-style silent Google auto-login attempt when unauthenticated on launch
        LaunchedEffect(hasResolvedInitialAuth, needsMandatorySignIn, isTvMode) {
            if (!isTvMode && hasResolvedInitialAuth && needsMandatorySignIn) {
                neliViewModel.trySilentGoogleAutoSignIn(context)
            }
        }

        // First-Time Welcome Screen vs Returning User "Continue enjoying our app" Greeting
        val onboardingPrefs = remember(context) {
            context.getSharedPreferences("neli_onboarding_welcome_prefs", android.content.Context.MODE_PRIVATE)
        }
        var hadSeenAuthPageThisSession by remember { mutableStateOf(false) }
        var showFirstTimeWelcome by remember { mutableStateOf(false) }
        var showReturningContinueBanner by remember { mutableStateOf(false) }
        var hasEvaluatedSessionGreeting by remember { mutableStateOf(false) }

        LaunchedEffect(hasResolvedInitialAuth, needsMandatorySignIn, currentUser?.uid, isTvMode) {
            if (!isTvMode && hasResolvedInitialAuth) {
                if (needsMandatorySignIn) {
                    hadSeenAuthPageThisSession = true
                } else if (currentUser != null && !hasEvaluatedSessionGreeting) {
                    hasEvaluatedSessionGreeting = true
                    val hasCompletedWelcomeBefore = onboardingPrefs.getBoolean("has_completed_first_welcome", false)
                    if (hadSeenAuthPageThisSession && !hasCompletedWelcomeBefore) {
                        showFirstTimeWelcome = true
                    } else {
                        // Existing / updated user who already had an account -> clean "Continue enjoying our app"
                        onboardingPrefs.edit().putBoolean("has_completed_first_welcome", true).apply()
                        showReturningContinueBanner = true
                    }
                }
            }
        }

        // Transparent Recommended Movie Overlay on every cold launch (even after closing app from Recents)
        // AND automatically every 4 hours when returning even without closing the app!
        var lastHandledOverlayTick by remember { mutableStateOf(0) }
        var activeRecommendedOverlayMovie by remember { mutableStateOf<com.example.model.MediaContent?>(null) }

        LaunchedEffect(
            recommendedOverlayTriggerTick,
            hasResolvedInitialAuth,
            needsMandatorySignIn,
            showFirstTimeWelcome,
            showFullPlayer,
            mediaCatalog.size,
            currentUser?.uid
        ) {
            if (!isTvMode &&
                hasResolvedInitialAuth &&
                !needsMandatorySignIn &&
                !showFirstTimeWelcome &&
                !showFullPlayer &&
                mediaCatalog.isNotEmpty() &&
                recommendedOverlayTriggerTick > lastHandledOverlayTick
            ) {
                val userKey = currentUser?.uid?.takeIf { it.isNotBlank() }
                    ?: com.example.ui.components.NeliRecommendedEntryManager.getOrCreateDeviceSalt(context).toString()
                val picked = com.example.ui.components.NeliRecommendedEntryManager.pickRecommendedMovieForUser(
                    context = context,
                    catalog = mediaCatalog,
                    userIdentifier = userKey
                )
                if (picked != null) {
                    lastHandledOverlayTick = recommendedOverlayTriggerTick
                    activeRecommendedOverlayMovie = picked
                    com.example.ui.components.NeliRecommendedEntryManager.markOverlayShown(
                        context = context,
                        movieId = picked.id
                    )
                }
            }
        }

        // Trigger Google AdMob App Open Ad once on cold app entry when user is signed in, not on TV, and not in player
        var hasShownLaunchAppOpenAd by remember { mutableStateOf(false) }
        LaunchedEffect(hasResolvedInitialAuth, needsMandatorySignIn, showFullPlayer, isTvMode) {
            if (!isTvMode && !hasShownLaunchAppOpenAd && hasResolvedInitialAuth && !needsMandatorySignIn && !showFullPlayer) {
                hasShownLaunchAppOpenAd = true
                (context as? Activity)?.let { act ->
                    NeliAdManager.showAppOpenAdIfAvailable(act)
                }
            }
        }

        // Full-Page Onboarding Sign-Up / Login (NOT a popup) on Mobile/Tablet (disabled on Smart TV)
        if (needsMandatorySignIn && !showFullPlayer) {
            MandatoryGoogleSignInDialog(
                isLoading = isAuthLoading,
                errorMessage = authError,
                onContinueWithGoogle = { neliViewModel.signInWithGoogle(context) },
                onSignInWithEmail = { email, password -> neliViewModel.signInUser(email, password) },
                onSignUpWithEmail = { realName, email, password ->
                    neliViewModel.signUpUser(realName, email, password)
                },
                onSignInWithPhone = { phoneOrUsername, password ->
                    neliViewModel.signInWithPhoneCredentials(phoneOrUsername, password)
                },
                onSignUpWithPhone = { username, phone, password ->
                    neliViewModel.signUpWithPhone(username, phone, password)
                },
                onClearError = { neliViewModel.clearAuthError() }
            )
        } else if (showFirstTimeWelcome && !showFullPlayer) {
            com.example.ui.components.NeliFirstTimeWelcomeScreen(
                userName = currentUser?.realName.orEmpty(),
                onStartWatching = {
                    onboardingPrefs.edit().putBoolean("has_completed_first_welcome", true).apply()
                    showFirstTimeWelcome = false
                }
            )
        }

        // Returning / Updated user subtle greeting: "Continue enjoying our app"
        if (!needsMandatorySignIn && !showFirstTimeWelcome && !showFullPlayer) {
            com.example.ui.components.NeliReturningUserContinueBanner(
                visible = showReturningContinueBanner,
                userName = currentUser?.realName.orEmpty(),
                onDismiss = { showReturningContinueBanner = false },
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter)
            )
        }

        // Transparent Recommended Movie Overlay with prominent X close button
        val overlayMovie = activeRecommendedOverlayMovie
        if (overlayMovie != null && !needsMandatorySignIn && !showFirstTimeWelcome && !showFullPlayer) {
            com.example.ui.components.NeliRecommendedEntryOverlay(
                movie = overlayMovie,
                visible = true,
                onWatchNow = { chosen ->
                    activeRecommendedOverlayMovie = null
                    startOrSwitchChannel(chosen.toPlayableChannel())
                },
                onOpenDetails = { chosen ->
                    activeRecommendedOverlayMovie = null
                    neliViewModel.openMediaDetails(chosen.id)
                },
                onDismiss = {
                    activeRecommendedOverlayMovie = null
                }
            )
        }

        // Mandatory In-App Update popup renders over EVERY screen and blocks until updated
        NeliHomepageAutoUpdatePopupDialog()

        // 3-second floating notification when user device does not support split screen
        com.example.notifications.FloatingSplitScreenUnsupportedNotice()
    }
}
