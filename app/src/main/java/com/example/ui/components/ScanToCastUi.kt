package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.ChannelRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.player.CastQualityPreset
import com.example.player.CastReceiverConfig
import com.example.player.ScanToCastManager
import com.example.player.ScanToCastSessionState
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/**
 * 1. CAMERA QR CODE SCANNER DIALOG (Opened when user clicks the Camera icon in the top header):
 * - Launches the dedicated [CameraScannerView] component built with Android CameraX dependencies
 *   to capture the QR code displayed on the TV/PC (`https://cast-nelitv.web.app`).
 * - As soon as a QR code is captured and paired, invokes [onConnectedOpenAzamGrid] to open the
 *   AZAM TV Channels Vertical Grid Tab.
 */
@Composable
fun ScanToCastCameraScannerDialog(
    currentUser: UserAccountEntity? = null,
    onDismiss: () -> Unit,
    onConnectedOpenAzamGrid: (ScanToCastSessionState) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CameraScannerView(
            currentUser = currentUser,
            onDismiss = onDismiss,
            onQrCodePaired = onConnectedOpenAzamGrid,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * 2. AZAM TV CHANNELS VERTICAL GRID TAB (Opened immediately after scanning QR code and connecting):
 * - Displays all hardcoded AZAM TV channels in a clean 2-column Grid with vertical scroll ONLY (`LazyVerticalGrid`).
 * - Shows "Pay to Watch" lock badge on channels locked by Admin if user has not paid.
 * - When the user taps a channel, opens the Casted Device Player (`onChannelClickedForCastedPlayer`)
 *   and NOT the local app ExoPlayer!
 */
@Composable
fun ScanToCastAzamGridTabContent(
    sessionState: ScanToCastSessionState,
    currentUser: UserAccountEntity? = null,
    isPremiumActive: Boolean = false,
    onSelectAzamChannelForCastedPlayer: (LiveChannel) -> Unit,
    onRescanQrCamera: () -> Unit,
    onDisconnectCast: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hardcodedAzamChannels = remember { ChannelRepository.hardcodedAzamChannels }
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D18))
            .testTag("scan_to_cast_azam_grid_tab")
    ) {
        // Connected Cast Banner Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121B2E))
                .border(1.dp, Color(0xFF34D399).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackToHome,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .testTag("scan_to_cast_grid_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Connected to Cast Receiver",
                            color = Color(0xFF34D399),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = if (sessionState.channelName.isNotBlank()) {
                            "Playing on TV: ${sessionState.channelName} • Tap any AZAM channel below"
                        } else {
                            "Chagua AZAM TV Channel hapa chini ianze kucheza kwenye TV"
                        },
                        color = NeliTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = onRescanQrCamera,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .testTag("scan_to_cast_rescan_cam_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Rescan QR",
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDisconnectCast,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7F1D1D))
                        .testTag("scan_to_cast_grid_disconnect_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Disconnect",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Section Title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "AZAM TV Channels (${hardcodedAzamChannels.size})",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Casted Device Player",
                color = NeliGenreCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Vertical Scroll ONLY Grid of Hardcoded AZAM TV Channels
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .testTag("scan_to_cast_azam_vertical_grid"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(hardcodedAzamChannels, key = { it.id }) { azamChannel ->
                val isLockedForUser = remember(
                    azamChannel.id,
                    currentUser,
                    isPremiumActive,
                    lockedChannelIds,
                    lockAllForFree
                ) {
                    NeliAdminManager.isChannelLockedForUser(
                        channelId = azamChannel.id,
                        currentUser = currentUser,
                        isPremiumActive = isPremiumActive,
                        context = context
                    )
                }
                val isPlayingThisOnTv = sessionState.channelId.equals(azamChannel.id, ignoreCase = true) &&
                    !sessionState.isPayToWatchLocked
                val logoUrl = remember(azamChannel.id, azamChannel.thumbnailUrl) {
                    ChannelRepository.resolveGuaranteedChannelLogoUrl(azamChannel)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isPlayingThisOnTv) Color(0xFF132A26) else Color(0xFF141B2D)
                        )
                        .border(
                            width = if (isPlayingThisOnTv) 1.5.dp else 1.dp,
                            color = when {
                                isLockedForUser -> Color(0xFFF59E0B).copy(alpha = 0.75f)
                                isPlayingThisOnTv -> Color(0xFF34D399)
                                else -> NeliBorder
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            onSelectAzamChannelForCastedPlayer(azamChannel)
                        }
                        .padding(12.dp)
                        .testTag("scan_to_cast_grid_item_${azamChannel.id}"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0B101D)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(logoUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = azamChannel.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )

                        // Top-right badge: "Pay to Watch" if locked by Admin, otherwise "LIVE"
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isLockedForUser -> Color(0xFFF59E0B)
                                        isPlayingThisOnTv -> Color(0xFF10B981)
                                        else -> NeliLiveRed
                                    }
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isLockedForUser) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Pay to Watch",
                                        tint = Color.Black,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = when {
                                        isLockedForUser -> "Pay to Watch"
                                        isPlayingThisOnTv -> "ON TV"
                                        else -> "LIVE"
                                    },
                                    color = if (isLockedForUser) Color.Black else Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    Text(
                        text = azamChannel.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isLockedForUser) {
                                "Pay to Watch"
                            } else if (isPlayingThisOnTv) {
                                "Playing on Casted TV ✓"
                            } else {
                                "Play on Casted TV"
                            },
                            color = when {
                                isLockedForUser -> Color(0xFFFBBF24)
                                isPlayingThisOnTv -> Color(0xFF34D399)
                                else -> NeliGenreCyan
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = if (isLockedForUser) Icons.Default.Lock else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isLockedForUser) Color(0xFFFBBF24) else NeliMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3. CASTED DEVICE PLAYER SCREEN (Opened when user selects an AZAM channel in Scan to Cast):
 * - This is the Player of the Casted Device (`https://cast-nelitv.web.app`), NOT the local app ExoPlayer!
 * - No local video or audio plays on the phone.
 * - If Admin locked the selected channel and the user hasn't paid:
 *   - Sends `"PAY_TO_WATCH"` to `castSessions/{sessionId}` so the casted device writes `"Pay to Watch"` and blocks playback.
 *   - Shows a prominent `"Pay to Watch"` state on this player screen with a button to pay via Premium.
 * - If the channel is free (or user has active Premium / Free Forever VIP):
 *   - Immediately starts playing the AZAM channel on the casted device and provides full remote control.
 */
@Composable
fun ScanToCastCastedDevicePlayerScreen(
    channel: LiveChannel,
    currentUser: UserAccountEntity? = null,
    isPremiumActive: Boolean = false,
    onBackToAzamGrid: () -> Unit,
    onSwitchChannelOnCastedPlayer: (LiveChannel) -> Unit,
    onGoToPremiumPayToWatch: (LiveChannel) -> Unit,
    onDisconnectCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by ScanToCastManager.sessionState.collectAsState()
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()
    val hardcodedAzamChannels = remember { ChannelRepository.hardcodedAzamChannels }

    var preferredLang by remember { mutableStateOf("sw") }
    var sliderVolume by remember(sessionState.volume) { mutableFloatStateOf(sessionState.volume) }

    val isLockedForUser = remember(
        channel.id,
        currentUser,
        isPremiumActive,
        lockedChannelIds,
        lockAllForFree
    ) {
        NeliAdminManager.isChannelLockedForUser(
            channelId = channel.id,
            currentUser = currentUser,
            isPremiumActive = isPremiumActive,
            context = context
        )
    }

    // Trigger playback on the Casted Device (or enforce Pay to Watch if locked by Admin)
    LaunchedEffect(channel.id, isLockedForUser, preferredLang) {
        if (isLockedForUser) {
            ScanToCastManager.notifyLockedChannelPayToWatch(context, channel)
        } else {
            ScanToCastManager.castAzamChannelToConnectedDevice(
                context = context,
                channel = channel,
                currentUser = currentUser,
                preferredAudioLanguage = preferredLang
            )
        }
    }

    BackHandler(onBack = onBackToAzamGrid)

    val safeTopPadding = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtLeast(32.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(top = safeTopPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("scan_to_cast_casted_device_player_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Bar of Casted Device Player
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBackToAzamGrid,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .testTag("casted_player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to AZAM Channels",
                        tint = Color.White
                    )
                }

                Column {
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isLockedForUser || sessionState.isPayToWatchLocked) {
                            "Pay to Watch • Casting Blocked Until Subscribed"
                        } else {
                            "Casted Device Player • Playing on TV (cast-nelitv.web.app)"
                        },
                        color = if (isLockedForUser || sessionState.isPayToWatchLocked) {
                            Color(0xFFFBBF24)
                        } else {
                            Color(0xFF34D399)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Language Switcher (Kiswahili / English) on Casted Device
            if (!isLockedForUser) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliSurfaceVariant)
                        .border(1.dp, NeliGenreCyan, RoundedCornerShape(14.dp))
                        .clickable {
                            preferredLang = if (preferredLang == "sw") "en" else "sw"
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("casted_player_language_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Audio Language",
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (preferredLang == "en") "ENG" else "KISW",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Main Casted Device Monitor Screen
        val logoUrl = remember(channel.id, channel.thumbnailUrl) {
            ChannelRepository.resolveGuaranteedChannelLogoUrl(channel)
        }

        if (isLockedForUser || sessionState.isPayToWatchLocked) {
            // PAY TO WATCH LOCKED SCREEN (Also written to Casted Device via Firebase)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF1F1118))
                    .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(22.dp))
                    .padding(22.dp)
                    .testTag("casted_player_pay_to_watch_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0x33F59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Pay to Watch",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Pay to Watch",
                    color = Color(0xFFFBBF24),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.testTag("casted_player_pay_to_watch_title")
                )

                Text(
                    text = "${channel.name} imefungwa na Admin. Kwenye TV/PC iliyounganishwa imeandikwa 'Pay to Watch' na haitaruhusu casting hadi ulipie kifurushi cha Premium.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = { onGoToPremiumPayToWatch(channel) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("casted_player_pay_to_watch_action_button")
                ) {
                    Text(
                        text = "Pay to Watch Now",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        } else {
            // ACTIVE CASTED DEVICE VIDEO/AUDIO PLAYER CONTROLLER
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF131D33), Color(0xFF0D1424))
                        )
                    )
                    .border(1.dp, Color(0xFF34D399).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .padding(18.dp)
                    .testTag("casted_device_active_player_card"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // TV Screen Status Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (sessionState.isPlaying) "PLAYING ON CASTED DEVICE" else "PAUSED ON CASTED DEVICE",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Text(
                        text = "Quality: ${sessionState.currentQuality.label}",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Channel Logo & Now Playing on Casted TV
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0A0F1D))
                        .border(1.dp, NeliBorder, RoundedCornerShape(18.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Text(
                    text = "Now Playing: ${sessionState.channelName.ifBlank { channel.name }}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Video na Sauti vinacheza moja kwa moja kwenye Casted Device (https://cast-nelitv.web.app)",
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                // Transport Controls for Casted Device Player: Seek Back, Play/Pause, Seek Forward, Mute
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { ScanToCastManager.sendSeekBackCommand() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("casted_player_seek_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Seek Back 10s",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { ScanToCastManager.togglePlayPause() },
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(NeliMagenta)
                            .testTag("casted_player_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (sessionState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (sessionState.isPlaying) "Pause on TV" else "Play on TV",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = { ScanToCastManager.sendSeekForwardCommand() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("casted_player_seek_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Seek Forward 10s",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { ScanToCastManager.toggleMute() },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(if (sessionState.muted) Color(0xFF7F1D1D) else NeliSurfaceVariant)
                            .testTag("casted_player_mute_button")
                    ) {
                        Icon(
                            imageVector = if (sessionState.muted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (sessionState.muted) "Unmute TV" else "Mute TV",
                            tint = Color.White
                        )
                    }
                }

                // Casted Device Volume Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Slider(
                        value = if (sessionState.muted) 0f else sliderVolume,
                        onValueChange = { sliderVolume = it },
                        onValueChangeFinished = {
                            ScanToCastManager.sendSetVolumeCommand(sliderVolume)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = NeliGenreCyan,
                            activeTrackColor = NeliGenreCyan,
                            inactiveTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("casted_player_volume_slider")
                    )
                    Text(
                        text = "${((if (sessionState.muted) 0f else sliderVolume) * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quality Presets (AUTO / LOW / MEDIUM / HIGH)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CastQualityPreset.entries.forEach { preset ->
                        val isSelected = sessionState.currentQuality == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF065F46) else NeliSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF34D399) else NeliBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { ScanToCastManager.sendSetQualityCommand(preset) }
                                .padding(vertical = 8.dp)
                                .testTag("casted_player_quality_${preset.code}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = preset.label,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Disconnect & Return to AZAM Channels Grid Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToAzamGrid,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AZAM Channels",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDisconnectCast,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("casted_player_disconnect_button")
                    ) {
                        Text(
                            text = "Disconnect TV",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Vertical 2-Column Grid of AZAM Channels below the Casted Device Player for instant switching
        Text(
            text = "Switch AZAM TV Channel on Casted Device:",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            hardcodedAzamChannels.chunked(2).forEach { rowChannels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowChannels.forEach { azamCh ->
                        val isCurrent = channel.id.equals(azamCh.id, ignoreCase = true)
                        val isChLocked = NeliAdminManager.isChannelLockedForUser(
                            channelId = azamCh.id,
                            currentUser = currentUser,
                            isPremiumActive = isPremiumActive,
                            context = context
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) NeliMagenta else Color(0xFF141B2D))
                                .border(
                                    1.dp,
                                    when {
                                        isChLocked -> Color(0xFFF59E0B)
                                        isCurrent -> Color.White
                                        else -> NeliBorder
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSwitchChannelOnCastedPlayer(azamCh)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isChLocked) Icons.Default.Lock else Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = if (isChLocked) Color(0xFFFBBF24) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = azamCh.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isChLocked) {
                                Text(
                                    text = "Pay",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                    if (rowChannels.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog & controller for the AZAM-only "SCAN TO CAST" experience when triggered from PlayerScreen.
 */
@Composable
fun ScanToCastModalSheet(
    channel: LiveChannel,
    preferredAudioLanguage: String = "sw",
    currentUser: UserAccountEntity? = null,
    onDismiss: () -> Unit,
    onSelectAzamChannel: (LiveChannel) -> Unit = {}
) {
    val context = LocalContext.current
    val sessionState by ScanToCastManager.sessionState.collectAsState()
    val hardcodedAzamChannels = remember { ChannelRepository.hardcodedAzamChannels }
    var sliderVolume by remember(sessionState.volume) { mutableFloatStateOf(sessionState.volume) }

    val isLockedForUser = remember(channel.id, currentUser) {
        ScanToCastManager.isChannelLockedForCast(channel, currentUser, context)
    }

    // If not yet connected to TV via QR camera, open the Camera QR Scanner first
    if (!sessionState.receiverConnected && !isLockedForUser) {
        ScanToCastCameraScannerDialog(
            currentUser = currentUser,
            onDismiss = onDismiss,
            onConnectedOpenAzamGrid = {
                ScanToCastManager.castAzamChannelToConnectedDevice(
                    context = context,
                    channel = channel,
                    currentUser = currentUser,
                    preferredAudioLanguage = preferredAudioLanguage
                )
            }
        )
        return
    }

    LaunchedEffect(channel.id, isLockedForUser) {
        if (ChannelRepository.isHardcodedAzamChannel(channel)) {
            if (isLockedForUser) {
                ScanToCastManager.notifyLockedChannelPayToWatch(context, channel)
            } else {
                val current = ScanToCastManager.sessionState.value
                if (!current.isSessionActive) {
                    ScanToCastManager.startScanToCastSession(
                        context = context,
                        channel = channel,
                        preferredAudioLanguage = preferredAudioLanguage,
                        currentUser = currentUser
                    )
                } else if (!current.channelId.equals(channel.id, ignoreCase = true)) {
                    ScanToCastManager.switchCastAzamChannel(
                        channel = channel,
                        preferredAudioLanguage = preferredAudioLanguage,
                        currentUser = currentUser,
                        context = context
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .widthIn(max = 540.dp)
                .testTag("scan_to_cast_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF0E1424)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x2234D399)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CastConnected,
                                contentDescription = "SCAN TO CAST",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "SCAN TO CAST",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.testTag("scan_to_cast_title")
                            )
                            Text(
                                text = if (isLockedForUser || sessionState.isPayToWatchLocked) {
                                    "Pay to Watch"
                                } else {
                                    "Connected to TV"
                                },
                                color = if (isLockedForUser || sessionState.isPayToWatchLocked) {
                                    Color(0xFFFBBF24)
                                } else {
                                    Color(0xFF34D399)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("scan_to_cast_connection_status")
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("scan_to_cast_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NeliTextSecondary
                        )
                    }
                }

                // Now Playing Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF151E32))
                        .border(1.dp, NeliMagenta.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .testTag("scan_to_cast_now_playing_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLockedForUser || sessionState.isPayToWatchLocked) "Pay to Watch:" else "Now Playing:",
                            color = NeliTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = sessionState.channelName.ifBlank { channel.name },
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("scan_to_cast_channel_name")
                        )
                    }
                }

                // Remote Controls Section
                if (!isLockedForUser && !sessionState.isPayToWatchLocked) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF131B2E))
                            .border(1.dp, Color(0x4410B981), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Casted Device Controls",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Quality: ${sessionState.currentQuality.label}",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("scan_to_cast_quality_label")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { ScanToCastManager.sendSeekBackCommand() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NeliSurfaceVariant)
                                    .testTag("scan_to_cast_seek_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Seek Back",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = { ScanToCastManager.togglePlayPause() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(NeliMagenta)
                                    .testTag("scan_to_cast_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (sessionState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (sessionState.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(
                                onClick = { ScanToCastManager.sendSeekForwardCommand() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NeliSurfaceVariant)
                                    .testTag("scan_to_cast_seek_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Seek Forward",
                                    tint = Color.White
                                )
                            }

                            IconButton(
                                onClick = { ScanToCastManager.toggleMute() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (sessionState.muted) Color(0xFF7F1D1D) else NeliSurfaceVariant)
                                    .testTag("scan_to_cast_mute_button")
                            ) {
                                Icon(
                                    imageVector = if (sessionState.muted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (sessionState.muted) "Unmute" else "Mute",
                                    tint = Color.White
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = NeliGenreCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Slider(
                                value = if (sessionState.muted) 0f else sliderVolume,
                                onValueChange = { sliderVolume = it },
                                onValueChangeFinished = {
                                    ScanToCastManager.sendSetVolumeCommand(sliderVolume)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = NeliGenreCyan,
                                    activeTrackColor = NeliGenreCyan,
                                    inactiveTrackColor = Color(0xFF1E293B)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_to_cast_volume_slider")
                            )
                            Text(
                                text = "${((if (sessionState.muted) 0f else sliderVolume) * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CastQualityPreset.entries.forEach { preset ->
                                val isSelected = sessionState.currentQuality == preset
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) Color(0xFF065F46) else NeliSurfaceVariant)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF34D399) else NeliBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            ScanToCastManager.sendSetQualityCommand(preset)
                                        }
                                        .padding(vertical = 9.dp)
                                        .testTag("scan_to_cast_quality_${preset.code}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.label,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Vertical 2-column grid of AZAM channels inside the modal
                Text(
                    text = "AZAM TV Channels:",
                    color = NeliTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hardcodedAzamChannels.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { azamCh ->
                                val isCurrent = sessionState.channelId.equals(azamCh.id, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) NeliMagenta else NeliSurfaceVariant)
                                        .clickable {
                                            ScanToCastManager.switchCastAzamChannel(
                                                channel = azamCh,
                                                preferredAudioLanguage = preferredAudioLanguage,
                                                currentUser = currentUser,
                                                context = context
                                            )
                                            onSelectAzamChannel(azamCh)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("scan_to_cast_channel_${azamCh.id}")
                                ) {
                                    Text(
                                        text = azamCh.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        ScanToCastManager.disconnectCastSession()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("scan_to_cast_disconnect_button")
                ) {
                    Text(
                        text = "Disconnect",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Full-screen remote controller overlay shown inside `PlayerScreen` when an AZAM channel is
 * actively connected via Scan to Cast (`receiverConnected == true`).
 */
@Composable
fun ScanToCastConnectedPlayerOverlay(
    sessionState: ScanToCastSessionState,
    onOpenFullCastSheet: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF2080D1A))
            .padding(24.dp)
            .testTag("scan_to_cast_connected_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF11192C))
                .border(1.dp, Color(0xFF34D399).copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CastConnected,
                    contentDescription = "Connected to TV",
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (sessionState.isPayToWatchLocked) "Pay to Watch" else "Connected to TV",
                    color = if (sessionState.isPayToWatchLocked) Color(0xFFFBBF24) else Color(0xFF34D399),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = if (sessionState.isPayToWatchLocked) {
                    "Pay to Watch: ${sessionState.channelName}"
                } else {
                    "Now Playing: ${sessionState.channelName}"
                },
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Quality: ${sessionState.currentQuality.label} • Video & Audio playing on TV/PC Browser",
                color = NeliGenreCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { ScanToCastManager.sendSeekBackCommand() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "Seek Back",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { ScanToCastManager.togglePlayPause() },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NeliMagenta)
                ) {
                    Icon(
                        imageVector = if (sessionState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (sessionState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = { ScanToCastManager.sendSeekForwardCommand() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "Seek Forward",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { ScanToCastManager.toggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (sessionState.muted) Color(0xFF7F1D1D) else NeliSurfaceVariant)
                ) {
                    Icon(
                        imageVector = if (sessionState.muted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (sessionState.muted) "Unmute" else "Mute",
                        tint = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CastQualityPreset.entries.forEach { preset ->
                    val isSelected = sessionState.currentQuality == preset
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF065F46) else NeliSurfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF34D399) else NeliBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { ScanToCastManager.sendSetQualityCommand(preset) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preset.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenFullCastSheet,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Remote & Channels",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Disconnect",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

/**
 * Pure-Kotlin ISO/IEC 18004 QR Code (Byte mode, ECC Level L) Canvas renderer.
 */
@Composable
fun IsoQrCodeCanvas(
    content: String,
    size: Dp = 176.dp,
    modifier: Modifier = Modifier
) {
    val matrix = remember(content) {
        IsoQrCodeEncoder.encodeByteModeEccL(content)
    }
    Canvas(
        modifier = modifier
            .size(size)
            .testTag("scan_to_cast_qr_canvas")
    ) {
        val dim = matrix.size
        if (dim <= 0) return@Canvas
        val quietZone = 2
        val totalModules = dim + quietZone * 2
        val cellSize = this.size.width / totalModules.toFloat()

        drawRect(color = Color.White, size = this.size)
        for (r in 0 until dim) {
            for (c in 0 until dim) {
                if (matrix[r][c]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset((c + quietZone) * cellSize, (r + quietZone) * cellSize),
                        size = Size(cellSize + 0.4f, cellSize + 0.4f)
                    )
                }
            }
        }
    }
}

internal object IsoQrCodeEncoder {
    private val VERSION_DATA_CODEWORDS_L = intArrayOf(0, 19, 34, 55, 80, 108, 136)
    private val VERSION_EC_CODEWORDS_L = intArrayOf(0, 7, 10, 15, 20, 26, 18)
    private val ALIGNMENT_POSITIONS = arrayOf(
        intArrayOf(),
        intArrayOf(),
        intArrayOf(6, 18),
        intArrayOf(6, 22),
        intArrayOf(6, 26),
        intArrayOf(6, 30),
        intArrayOf(6, 34)
    )

    fun encodeByteModeEccL(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val version = selectVersion(bytes.size)
        val size = 17 + version * 4
        val dataCapacity = VERSION_DATA_CODEWORDS_L[version]
        val dataCodewords = buildDataCodewords(bytes, dataCapacity)
        val finalCodewords = buildFinalMessageWithEcc(version, dataCodewords)

        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        drawFunctionPatterns(version, size, modules, isFunction)
        drawCodewords(size, modules, isFunction, finalCodewords)

        for (r in 0 until size) {
            for (c in 0 until size) {
                if (!isFunction[r][c] && (r + c) % 2 == 0) {
                    modules[r][c] = !modules[r][c]
                }
            }
        }
        drawFormatBits(size, modules, isFunction, mask = 0)
        return modules
    }

    private fun selectVersion(byteLen: Int): Int {
        for (v in 1..6) {
            if (byteLen + 2 <= VERSION_DATA_CODEWORDS_L[v]) return v
        }
        return 6
    }

    private fun buildDataCodewords(bytes: ByteArray, capacity: Int): IntArray {
        val maxBytes = bytes.size.coerceAtMost(capacity - 2)
        val bits = ArrayList<Int>(capacity * 8)
        fun appendBits(value: Int, len: Int) {
            for (i in len - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        appendBits(0b0100, 4)
        appendBits(maxBytes, 8)
        for (i in 0 until maxBytes) {
            appendBits(bytes[i].toInt() and 0xFF, 8)
        }
        val remainingBits = capacity * 8 - bits.size
        repeat(minOf(4, remainingBits)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val result = IntArray(capacity)
        val dataLen = bits.size / 8
        for (i in 0 until dataLen) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + j]
            }
            result[i] = b
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        for (i in dataLen until capacity) {
            result[i] = padBytes[(i - dataLen) % 2]
        }
        return result
    }

    private fun buildFinalMessageWithEcc(version: Int, data: IntArray): IntArray {
        if (version < 6) {
            val ecLen = VERSION_EC_CODEWORDS_L[version]
            val ec = reedSolomonComputeRemainder(data, ecLen)
            return data + ec
        }
        val block1 = data.copyOfRange(0, 68)
        val block2 = data.copyOfRange(68, 136)
        val ec1 = reedSolomonComputeRemainder(block1, 18)
        val ec2 = reedSolomonComputeRemainder(block2, 18)
        val interleaved = IntArray(136 + 36)
        var idx = 0
        for (i in 0 until 68) {
            interleaved[idx++] = block1[i]
            interleaved[idx++] = block2[i]
        }
        for (i in 0 until 18) {
            interleaved[idx++] = ec1[i]
            interleaved[idx++] = ec2[i]
        }
        return interleaved
    }

    private fun drawFunctionPatterns(
        version: Int,
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ) {
        fun setFunctionModule(r: Int, c: Int, dark: Boolean) {
            if (r in 0 until size && c in 0 until size) {
                modules[r][c] = dark
                isFunction[r][c] = true
            }
        }

        fun drawFinderPattern(centerR: Int, centerC: Int) {
            for (dr in -4..4) {
                for (dc in -4..4) {
                    val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                    val dark = dist != 2 && dist != 4
                    setFunctionModule(centerR + dr, centerC + dc, dark)
                }
            }
        }

        drawFinderPattern(3, 3)
        drawFinderPattern(3, size - 4)
        drawFinderPattern(size - 4, 3)

        for (i in 0 until size) {
            if (!isFunction[6][i]) setFunctionModule(6, i, i % 2 == 0)
            if (!isFunction[i][6]) setFunctionModule(i, 6, i % 2 == 0)
        }

        val alignCoords = ALIGNMENT_POSITIONS[version]
        for (ar in alignCoords) {
            for (ac in alignCoords) {
                if ((ar == 6 && ac == 6) || (ar == 6 && ac == size - 7) || (ar == size - 7 && ac == 6)) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dist = maxOf(kotlin.math.abs(dr), kotlin.math.abs(dc))
                        setFunctionModule(ar + dr, ac + dc, dist != 1)
                    }
                }
            }
        }

        for (i in 0..8) {
            if (i != 6) {
                setFunctionModule(8, i, false)
                setFunctionModule(i, 8, false)
            }
        }
        for (i in 0..7) {
            setFunctionModule(8, size - 1 - i, false)
            setFunctionModule(size - 1 - i, 8, false)
        }
        setFunctionModule(size - 8, 8, true)
    }

    private fun drawCodewords(
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        codewords: IntArray
    ) {
        var bitIdx = 0
        val totalBits = codewords.size * 8
        var right = size - 1
        var vert = 0
        while (right >= 1) {
            if (right == 6) right = 5
            for (cnt in 0 until size) {
                val r = if ((vert and 1) == 0) size - 1 - cnt else cnt
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        val dark = if (bitIdx < totalBits) {
                            ((codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1) != 0
                        } else {
                            false
                        }
                        modules[r][c] = dark
                        bitIdx++
                    }
                }
            }
            right -= 2
            vert++
        }
    }

    private fun drawFormatBits(
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        mask: Int
    ) {
        val data = (0b01 shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
        }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun getBit(i: Int): Boolean = ((bits ushr i) and 1) != 0

        for (i in 0..5) modules[8][i] = getBit(14 - i)
        modules[8][7] = getBit(8)
        modules[8][8] = getBit(7)
        modules[7][8] = getBit(6)
        for (i in 9..14) modules[14 - i][8] = getBit(14 - i)

        for (i in 0..7) modules[8][size - 1 - i] = getBit(i)
        for (i in 8..14) modules[size - 15 + i][8] = getBit(i)
        isFunction[size - 8][8] = true
        modules[size - 8][8] = true
    }

    private fun reedSolomonComputeRemainder(data: IntArray, degree: Int): IntArray {
        val gen = IntArray(degree)
        gen[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                gen[j] = gfMultiply(gen[j], root)
                if (j + 1 < degree) gen[j] = gen[j] xor gen[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        val result = IntArray(degree)
        for (b in data) {
            val factor = b xor result[0]
            System.arraycopy(result, 1, result, 0, degree - 1)
            result[degree - 1] = 0
            for (j in 0 until degree) {
                result[j] = result[j] xor gfMultiply(gen[j], factor)
            }
        }
        return result
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }
}
