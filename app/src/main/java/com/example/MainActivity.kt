package com.example

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.model.LiveChannel
import com.example.notifications.NeliNotificationScheduler
import com.example.player.LivePlayerController
import com.example.player.NativeLogSuppressor
import com.example.ui.NeliViewModel
import com.example.ui.components.FloatingPipPlayerOverlay
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
    private var pendingLaunchChannelId by mutableStateOf<String?>(null)
    private var pendingLaunchMovieId by mutableStateOf<String?>(null)
    private var pendingLaunchTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        NativeLogSuppressor.suppressNonFatalNativeLogs()
        super.onCreate(savedInstanceState)
        NeliThemeManager.initialize(this)
        enableEdgeToEdge()
        try {
            val isLight = NeliThemeManager.isLightMode
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        } catch (_: Exception) {
        }
        NeliNotificationScheduler.scheduleAllDailyNotifications(this)
        NeliHomeWidgetProvider.ensureWidgetAutomaticallyPinnedAndUpdated(this)
        extractDeepLinkFromIntent(intent)

        setContent {
            val isLightMode = NeliThemeManager.isLightMode
            LaunchedEffect(isLightMode) {
                try {
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = isLightMode
                        isAppearanceLightNavigationBars = isLightMode
                    }
                } catch (_: Exception) {
                }
            }
            NeliTVTheme {
                NeliApp(
                    isSystemInPipMode = isSystemInPipMode,
                    pendingChannelId = pendingLaunchChannelId,
                    pendingMovieId = pendingLaunchMovieId,
                    pendingTab = pendingLaunchTab,
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
                val builder = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(isActive)
                    builder.setSeamlessResizeEnabled(true)
                }
                setPictureInPictureParams(builder.build())
            } catch (_: Exception) {
            }
        }
    }

    private fun enterSystemPipIfSupported(): Boolean {
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
        // Automatically enter OS Picture-in-Picture mode ONLY when user leaves the app via Home button
        // or switches to another app (such as opening an incoming SMS or WhatsApp message notification).
        // Never triggered by the Back button and never shown as an in-app overlay inside NeliPlay!
        if (hasActivePlaybackForPip && !isInPictureInPictureMode && !isFinishing) {
            enterSystemPipIfSupported()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isSystemInPipMode = isInPictureInPictureMode
    }
}

@Composable
fun NeliApp(
    isSystemInPipMode: Boolean = false,
    pendingChannelId: String? = null,
    pendingMovieId: String? = null,
    pendingTab: String? = null,
    onConsumeDeepLink: () -> Unit = {},
    onActivePlaybackChanged: (Boolean) -> Unit = {},
    onRequestSystemPipOutsideApp: () -> Unit = {},
    neliViewModel: NeliViewModel = viewModel()
) {
    val context = LocalContext.current

    // Request notification permission on Android 13+ and immediately dispatch automatic startup notification
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                NeliNotificationScheduler.scheduleAllDailyNotifications(context)
            }
        }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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

    LaunchedEffect(activeChannel, sharedPlayerController) {
        onActivePlaybackChanged(activeChannel != null && sharedPlayerController != null)
    }

    val startOrSwitchChannel: (LiveChannel) -> Unit = { selected ->
        val existing = sharedPlayerController
        if (existing == null) {
            val controller = LivePlayerController(context, selected)
            controller.initializePlayer()
            sharedPlayerController = controller
        } else {
            existing.switchChannel(selected)
        }
        activeChannel = selected
    }

    val closeAndReleasePlayback: () -> Unit = {
        // Immediately disable system PiP auto-enter before closing so Back navigation never triggers PiP
        onActivePlaybackChanged(false)
        sharedPlayerController?.release()
        sharedPlayerController = null
        activeChannel = null
    }

    // Handle deep links from Home Screen Widget or Daily Notification taps
    LaunchedEffect(pendingChannelId, pendingMovieId) {
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
    if (isSystemInPipMode && activeChannel != null && controller != null) {
        FloatingPipPlayerOverlay(
            playerController = controller,
            onExpandToFullScreen = {},
            onClosePip = closeAndReleasePlayback,
            isSystemPipMode = true
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val currentChan = activeChannel
        val currentCtrl = sharedPlayerController
        val showFullPlayer = currentChan != null && currentCtrl != null

        if (showFullPlayer && currentChan != null && currentCtrl != null) {
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
    }
}
