package com.example

import android.Manifest
import android.app.PictureInPictureParams
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
import com.example.model.LiveChannel
import com.example.player.LivePlayerController
import com.example.ui.components.FloatingPipPlayerOverlay
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.NeliTVTheme

class MainActivity : ComponentActivity() {

    private var isSystemInPipMode by mutableStateOf(false)
    private var hasActivePlaybackForPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeliTVTheme {
                NeliApp(
                    isSystemInPipMode = isSystemInPipMode,
                    onActivePlaybackChanged = { isActive ->
                        hasActivePlaybackForPip = isActive
                        updateSystemPipParams(isActive)
                    }
                )
            }
        }
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
        // Automatically enter Picture-in-Picture mode when user presses Home while watching a movie, series, or live TV
        if (hasActivePlaybackForPip && !isInPictureInPictureMode) {
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
    onActivePlaybackChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current

    // Request notification permission on Android 13+ so background downloads display live progress bar notifications outside the app
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
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

    // Active channel currently playing (either full-screen or in floating PiP)
    var activeChannel by remember { mutableStateOf<LiveChannel?>(null) }
    // Whether the player is minimized into the in-app draggable & corner-resizable floating PiP overlay
    var isFloatingPipMinimized by remember { mutableStateOf(false) }

    // Shared LivePlayerController persists across Full-Screen PlayerScreen, In-App Floating PiP, and System Home PiP
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
        isFloatingPipMinimized = false
    }

    val closeAndReleasePlayback: () -> Unit = {
        sharedPlayerController?.release()
        sharedPlayerController = null
        activeChannel = null
        isFloatingPipMinimized = false
    }

    // If Android System PiP is active (e.g. user pressed Home button to watch over other apps),
    // render the dedicated PiP surface with content-specific controls
    val controller = sharedPlayerController
    if (isSystemInPipMode && activeChannel != null && controller != null) {
        FloatingPipPlayerOverlay(
            playerController = controller,
            onExpandToFullScreen = {
                isFloatingPipMinimized = false
            },
            onClosePip = closeAndReleasePlayback,
            isSystemPipMode = true
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val showFullPlayer = activeChannel != null && !isFloatingPipMinimized && controller != null

        AnimatedContent(
            targetState = showFullPlayer,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "neli_screen_transition"
        ) { isFullPlayerVisible ->
            val currentChan = activeChannel
            val currentCtrl = sharedPlayerController
            if (isFullPlayerVisible && currentChan != null && currentCtrl != null) {
                PlayerScreen(
                    channel = currentChan,
                    onBack = {
                        // Live TV stops when user explicitly backs out of the watch page (unless they tapped PiP)
                        if (currentChan.isLiveBroadcast) {
                            closeAndReleasePlayback()
                        } else {
                            // Minimize Movies/Series/Adult into the floating draggable & corner-resizable PiP player
                            isFloatingPipMinimized = true
                        }
                    },
                    sharedController = currentCtrl,
                    onEnterPipMode = {
                        // Switch any stream (including Live TV, Movies, Series, Adult) into floating PiP mode
                        isFloatingPipMinimized = true
                    }
                )
            } else {
                HomeScreen(
                    onChannelSelected = { selected ->
                        startOrSwitchChannel(selected)
                    }
                )
            }
        }

        // Draggable & Corner-Resizable Floating PiP Player when minimized inside the app
        if (activeChannel != null && isFloatingPipMinimized && controller != null) {
            FloatingPipPlayerOverlay(
                playerController = controller,
                onExpandToFullScreen = {
                    isFloatingPipMinimized = false
                },
                onClosePip = closeAndReleasePlayback,
                isSystemPipMode = false
            )
        }
    }
}
