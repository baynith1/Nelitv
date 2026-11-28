package com.example.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.ChannelHealthGuardian

/**
 * Full-Screen NeliTv AI Setup, Permissions & Settings Page
 * Opened from the "NeliTv AI" card at the very bottom of the Account page.
 *
 * Lets the user easily allow & configure:
 * - Notifications
 * - Microphone + Human-Voice Cancellation
 * - Act as Mobile Assistant
 * - Hands-Free Wake Mode ("Hey Neli" / "Neli") + Lock-Screen Wake
 * - Overlay Over Other Apps & Home Screen (Siri-style 3D Particle Mesh Ball)
 * - Device Screen Lock ("Hey Neli lock my screen")
 * - Live Captions, Voice Reply, App Settings, Channel Keep-Alive Status & Neli AI Center API
 */
@Composable
fun NeliAiSetupScreen(
    voiceState: NeliAiVoiceState,
    brain: NeliAiBrainSnapshot,
    onBack: () -> Unit,
    onTestVoiceNow: () -> Unit,
    onRunQuickTestCommand: (String) -> Unit = {},
    onToggleHandsFree: (Boolean) -> Unit,
    onToggleOverlayBall: (Boolean) -> Unit,
    onToggleLockScreenWake: (Boolean) -> Unit,
    onToggleVoiceCancellation: (Boolean) -> Unit,
    onToggleCaptions: (Boolean) -> Unit,
    onToggleVoiceReply: (Boolean) -> Unit,
    onTogglePowerButtonWake: (Boolean) -> Unit = {},
    onOpenVoiceDictation: () -> Unit = {},
    onSaveRemoteUrl: (String) -> Boolean,
    onSaveApiKey: (String) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionRefreshTick by remember { mutableIntStateOf(0) }

    // Refresh permission badges whenever user returns from Android System Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionRefreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micGranted = remember(permissionRefreshTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    val notifGranted = remember(permissionRefreshTick) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    val overlayGranted = remember(permissionRefreshTick) {
        Settings.canDrawOverlays(context)
    }
    val lockAdminGranted = remember(permissionRefreshTick) {
        NeliDeviceAdminReceiver.isLockPermissionGranted(context)
    }
    val isDefaultAssistant = remember(permissionRefreshTick) {
        NeliDigitalAssistantHelper.isNeliDefaultDigitalAssistant(context)
    }

    var pendingTestVoiceAfterMicGrant by remember { mutableStateOf(false) }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefreshTick++
        if (granted) {
            onToggleHandsFree(true)
            if (pendingTestVoiceAfterMicGrant) {
                pendingTestVoiceAfterMicGrant = false
                onTestVoiceNow()
            }
        } else {
            pendingTestVoiceAfterMicGrant = false
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionRefreshTick++
    }

    val healthReport by ChannelHealthGuardian.healthReport.collectAsState()
    var showAdvancedApi by remember { mutableStateOf(false) }
    var endpointInput by remember(brain.remoteEndpointUrl) { mutableStateOf(brain.remoteEndpointUrl) }
    var apiKeyInput by remember { mutableStateOf("") }
    var apiStatusMsg by remember { mutableStateOf("") }

    Surface(
        color = Color(0xFF070912),
        modifier = Modifier
            .fillMaxSize()
            .testTag("neli_ai_setup_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("neli_ai_setup_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "NeliTv AI",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Hands-Free Assistant • ${brain.totalCommandsSupported}+ Commands",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.testTag("neli_ai_app_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Android App Settings",
                        tint = Color(0xFFCBD5E1)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hero 3D Particle/Wave Mesh Orb Preview & Instant Test
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF131A33), Color(0xFF0B0F1E))
                                )
                            )
                            .border(1.dp, Color(0x4438BDF8), RoundedCornerShape(26.dp))
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeliAiParticleMeshOrb(
                                voiceState = voiceState,
                                size = 118.dp,
                                onClick = {
                                    if (!micGranted) {
                                        pendingTestVoiceAfterMicGrant = true
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onTestVoiceNow()
                                    }
                                }
                            )
                            Text(
                                text = "Say \"Hey Neli\" or \"Neli\"",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Blue = Listening  •  White-Yellow = Thinking  •  Pink = Speaking",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                            if (voiceState is NeliAiVoiceState.Notice) {
                                Text(
                                    text = voiceState.message,
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Button(
                                onClick = {
                                    if (!micGranted) {
                                        pendingTestVoiceAfterMicGrant = true
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onTestVoiceNow()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                                shape = CircleShape,
                                modifier = Modifier.testTag("neli_ai_test_voice_btn")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Talk to Neli AI Now", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                            }
                            // Instant 1-tap voice demo triggers (great for testing in emulator where mic isn't bridged)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                            ) {
                                listOf(
                                    "Hey Neli" to "Hey Neli",
                                    "Suggest movies" to "Hey Neli suggest me movies",
                                    "Random movie" to "Hey Neli play a random movie"
                                ).forEach { (label, cmd) ->
                                    Surface(
                                        color = Color(0x2238BDF8),
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .border(1.dp, Color(0x4438BDF8), CircleShape)
                                            .clip(CircleShape)
                                            .clickable { onRunQuickTestCommand(cmd) }
                                            .testTag("neli_ai_quick_demo_${label.lowercase().replace(" ", "_")}")
                                    ) {
                                        Text(
                                            text = label,
                                            color = Color(0xFFE0F2FE),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Fast AI Voice Dictation & Privacy Voice-to-Text Card (Wispr Flow + Superwhisper Style)
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.RecordVoiceOver,
                        title = "Neli Flow Dictation & Privacy STT",
                        subtitle = "Fast 200+ WPM AI voice dictation that strips fillers, auto-punctuates, stays private on-device & copies cleanly across apps.",
                        granted = micGranted,
                        actionLabel = "Open Studio",
                        testTag = "neli_ai_open_dictation_card",
                        onClick = { onOpenVoiceDictation() }
                    )
                }

                // Section Title: Required Permissions
                item {
                    Text(
                        text = "PERMISSIONS & SYSTEM ACCESS",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                // 1. Notifications Permission
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.Notifications,
                        title = "Allow Notifications",
                        subtitle = "Keep Hey Neli running smoothly in background & show download progress.",
                        granted = notifGranted,
                        actionLabel = if (notifGranted) "Allowed" else "Allow",
                        testTag = "neli_ai_perm_notifications",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifGranted) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                runCatching {
                                    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(i)
                                }
                            }
                        }
                    )
                }

                // 2. Microphone Permission
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.Mic,
                        title = "Allow Microphone",
                        subtitle = "Required to hear \"Hey Neli\" hands-free inside player, outside apps, and lock screen.",
                        granted = micGranted,
                        actionLabel = if (micGranted) "Allowed" else "Allow",
                        testTag = "neli_ai_perm_microphone",
                        onClick = {
                            if (!micGranted) {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    )
                }

                // 3. Overlay Over Other Apps & Home Screen
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.Layers,
                        title = "Overlay Over Other Apps & Home Screen",
                        subtitle = "Display the circular Neli AI ball over WhatsApp, Home Screen & other apps with live captions & movie popups.",
                        granted = overlayGranted,
                        actionLabel = if (overlayGranted) "Allowed" else "Allow Overlay",
                        testTag = "neli_ai_perm_overlay",
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )
                }

                // 4. Act as Default Digital Assistant App (Enables Hold Power Button & Corner Swipe)
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.Assistant,
                        title = "Default Digital Assistant App",
                        subtitle = "Set NeliTv as your device's Digital Assistant so holding the Power / Shut Down button or swiping up wakes Neli AI anywhere.",
                        granted = isDefaultAssistant,
                        actionLabel = if (isDefaultAssistant) "Default ✓" else "Set Default",
                        testTag = "neli_ai_perm_assistant",
                        onClick = {
                            NeliDigitalAssistantHelper.openDigitalAssistantSettings(context)
                        }
                    )
                }

                // 4B. Hold or Double-Tap Power / Shut Down Button Shortcut
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.PowerSettingsNew,
                        title = "Power Button Gesture (Hold / Double-Tap)",
                        subtitle = "Configure Hold Power button -> Digital Assistant (NeliTv) in system gestures, or double-tap Power button to wake Neli AI.",
                        granted = isDefaultAssistant && brain.isPowerButtonWakeEnabled,
                        actionLabel = "Configure",
                        testTag = "neli_ai_perm_power_gesture",
                        onClick = {
                            NeliDigitalAssistantHelper.openPowerButtonGestureSettings(context)
                        }
                    )
                }

                // 5. Device Screen Lock ("Hey Neli lock my screen")
                item {
                    NeliAiPermissionCard(
                        icon = Icons.Default.Lock,
                        title = "Screen Lock Control",
                        subtitle = "Allow \"Hey Neli lock my screen\" to lock your phone instantly with voice.",
                        granted = lockAdminGranted,
                        actionLabel = if (lockAdminGranted) "Active" else "Enable Lock",
                        testTag = "neli_ai_perm_screen_lock",
                        onClick = {
                            runCatching {
                                context.startActivity(NeliDeviceAdminReceiver.buildEnableAdminIntent(context))
                            }
                        }
                    )
                }

                // Section Title: Hands-Free & Voice Behavior
                item {
                    Text(
                        text = "HANDS-FREE & VOICE CONTROLS",
                        color = Color(0xFFFF2E93),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                    )
                }

                item {
                    Surface(
                        color = Color(0xFF101422),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeliAiToggleRow(
                                icon = Icons.Default.RecordVoiceOver,
                                title = "Hands-Free Mode (\"Hey Neli\")",
                                subtitle = "Auto-mutes player for 30s when called & resumes if no command.",
                                checked = brain.isHandsFreeEnabled,
                                testTag = "neli_ai_toggle_handsfree",
                                onCheckedChange = { enabled ->
                                    if (enabled && !micGranted) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onToggleHandsFree(enabled)
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.Layers,
                                title = "Floating AI Ball Outside NeliTv",
                                subtitle = "Shows Siri-style 3D mesh ball outside app (edge glow inside NeliTv).",
                                checked = brain.isOverlayBallEnabled,
                                testTag = "neli_ai_toggle_overlay_ball",
                                onCheckedChange = { enabled ->
                                    if (enabled && !overlayGranted) {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                        }
                                    } else {
                                        onToggleOverlayBall(enabled)
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.PhonelinkLock,
                                title = "Lock-Screen Voice Commands",
                                subtitle = "Say \"Hey Neli download 5 random movies\" or \"Play Azam Two\" even while locked.",
                                checked = brain.isLockScreenWakeEnabled,
                                testTag = "neli_ai_toggle_lockscreen",
                                onCheckedChange = { enabled ->
                                    if (enabled && !micGranted) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onToggleLockScreenWake(enabled)
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.PowerSettingsNew,
                                title = "Double-Tap Power Button to Wake Neli AI",
                                subtitle = "Quickly press the Power / Shut Down button twice to wake Neli AI even on Lock Screen.",
                                checked = brain.isPowerButtonWakeEnabled,
                                testTag = "neli_ai_toggle_power_wake",
                                onCheckedChange = { enabled ->
                                    if (enabled && !micGranted) {
                                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    } else {
                                        onTogglePowerButtonWake(enabled)
                                    }
                                }
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.GraphicEq,
                                title = "Human-Voice Isolation & Cancellation",
                                subtitle = "Filters out movie/TV speaker audio so only your voice wakes Neli.",
                                checked = brain.isVoiceCancellationEnabled,
                                testTag = "neli_ai_toggle_voice_cancel",
                                onCheckedChange = onToggleVoiceCancellation
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.ClosedCaption,
                                title = "Live Voice Captions",
                                subtitle = "Show clean captions while speaking and when Neli replies.",
                                checked = brain.isCaptionsEnabled,
                                testTag = "neli_ai_toggle_captions",
                                onCheckedChange = onToggleCaptions
                            )
                            HorizontalDivider(color = Color(0x18FFFFFF))
                            NeliAiToggleRow(
                                icon = Icons.Default.RecordVoiceOver,
                                title = "Neli Female English Voice Reply",
                                subtitle = "Sweet natural English voice (100% free on-device TTS).",
                                checked = brain.isVoiceReplyEnabled,
                                testTag = "neli_ai_toggle_voice_reply",
                                onCheckedChange = onToggleVoiceReply
                            )
                        }
                    }
                }

                // Channel Keep-Alive Guardian & Catalog Telemetry Card
                item {
                    Surface(
                        color = Color(0xFF0E1722),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0x4410B981), RoundedCornerShape(20.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HealthAndSafety,
                                contentDescription = "Channel Keep-Alive Guardian",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Channel Keep-Alive Guardian Active",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${brain.liveChannelsCount} Live TV • ${brain.moviesCount} Movies • ${brain.seriesCount} Series • Auto-Healed: ${healthReport.autoHealedCount}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                // App Settings & Advanced Neli AI Center Connection
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(i)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("App Settings", color = Color.White, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { showAdvancedApi = !showAdvancedApi },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Neli AI Server", color = Color(0xFF38BDF8), fontSize = 12.sp)
                        }
                    }
                }

                if (showAdvancedApi) {
                    item {
                        Surface(
                            color = Color(0xFF101422),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x3338BDF8), RoundedCornerShape(20.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Neli AI Center Endpoint & Key",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (brain.hasApiKeyConfigured) "API Key Configured ✓" else "Running On-Device Brain (1,500+ commands ready offline & online)",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.5.sp
                                )
                                OutlinedTextField(
                                    value = endpointInput,
                                    onValueChange = { endpointInput = it },
                                    label = { Text("Endpoint URL (HTTPS)", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00D4FF),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("Optional X-API-Key Override", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFF00D4FF),
                                        unfocusedBorderColor = Color(0xFF334155)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = {
                                        val ok = onSaveRemoteUrl(endpointInput)
                                        if (apiKeyInput.isNotBlank()) {
                                            onSaveApiKey(apiKeyInput)
                                        }
                                        apiStatusMsg = if (ok) "Saved Neli AI Center settings!" else "URL must start with https://"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Server Settings", color = Color.Black, fontWeight = FontWeight.ExtraBold)
                                }
                                if (apiStatusMsg.isNotBlank()) {
                                    Text(apiStatusMsg, color = Color(0xFF38BDF8), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun NeliAiPermissionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = Color(0xFF101422),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (granted) Color(0x5534D399) else Color(0x28FFFFFF),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (granted) Color(0x2234D399) else Color(0x2200D4FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else icon,
                    contentDescription = title,
                    tint = if (granted) Color(0xFF34D399) else Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
            Surface(
                color = if (granted) Color(0x2834D399) else Color(0xFF00D4FF),
                shape = CircleShape
            ) {
                Text(
                    text = actionLabel,
                    color = if (granted) Color(0xFF6EE7B7) else Color.Black,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NeliAiToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (checked) Color(0xFF00D4FF) else Color(0xFF64748B),
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 14.5.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF00D4FF)
            )
        )
    }
}
