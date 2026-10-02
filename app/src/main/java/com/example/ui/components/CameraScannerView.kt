package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.player.CastReceiverConfig
import com.example.player.ScanToCastManager
import com.example.player.ScanToCastSessionState
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import java.util.EnumMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dedicated `CameraScannerView` component built with CameraX (`camera-core`, `camera-camera2`,
 * `camera-lifecycle`, `camera-view`) and ZXing (`MultiFormatReader`) for real-time QR code scanning.
 *
 * Launches when the Scan-to-Cast camera icon in the header is pressed on an AZAM TV view
 * to capture the QR code displayed on the TV/PC Web Receiver (`https://cast-nelitv.web.app`),
 * pair the cast session, and immediately stream the live AZAM TV channel.
 */
@Composable
fun CameraScannerView(
    currentUser: UserAccountEntity? = null,
    initialAzamChannel: LiveChannel? = null,
    preferredAudioLanguage: String? = null,
    onDismiss: () -> Unit,
    onQrCodePaired: (ScanToCastSessionState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var statusBannerText by remember {
        mutableStateOf(
            if (initialAzamChannel != null) {
                "Point camera at TV QR code on https://cast-nelitv.web.app to stream ${initialAzamChannel.name}"
            } else {
                "Point camera at the QR code on https://cast-nelitv.web.app"
            }
        )
    }
    var isPairingSession by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var manualSessionInput by remember { mutableStateOf("") }
    val hasHandledPairing = remember { AtomicBoolean(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            statusBannerText = "Camera permission is needed to scan the TV QR code."
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(onBack = onDismiss)

    val handleCapturedQrPayload: (String) -> Unit = { rawQrPayload ->
        val trimmed = rawQrPayload.trim()
        val extractedSessionId = CastReceiverConfig.extractSessionIdFromScannedQr(trimmed)
        if (extractedSessionId.isNotBlank() && hasHandledPairing.compareAndSet(false, true)) {
            isPairingSession = true
            statusBannerText = "QR Code Scanned ($extractedSessionId)! Connecting & streaming to TV..."
            val connectResult = ScanToCastManager.connectToScannedQrSession(
                context = context,
                rawScannedQr = trimmed,
                currentUser = currentUser,
                initialAzamChannel = initialAzamChannel,
                preferredAudioLanguage = preferredAudioLanguage
            )
            val connectedState = connectResult.getOrNull() ?: ScanToCastManager.sessionState.value
            onQrCodePaired(connectedState)
        } else if (extractedSessionId.isBlank()) {
            showManualInput = true
            statusBannerText = "Scan the TV QR code on https://cast-nelitv.web.app or enter the TV Session Code below."
        }
    }

    val safeTopPadding = WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .asPaddingValues()
        .calculateTopPadding()
        .coerceAtLeast(32.dp)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("camera_scanner_view"),
        color = Color(0xFF070B14)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = safeTopPadding, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .testTag("scan_to_cast_camera_scanner_dialog"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar: Back / Title / Zoom / Torch / Flip Camera / Close
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
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("camera_scanner_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Column {
                        Text(
                            text = "Scan to Cast QR Scanner",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (initialAzamChannel != null) {
                                "${initialAzamChannel.name} • https://cast-nelitv.web.app"
                            } else {
                                "https://cast-nelitv.web.app"
                            },
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasCameraPermission) {
                        // 1x / 2x Zoom button for scanning TV screens from across the room
                        IconButton(
                            onClick = {
                                val nextZoom = if (zoomRatio < 1.8f) 2.0f else 1.0f
                                zoomRatio = nextZoom
                                try {
                                    activeCamera?.cameraControl?.setZoomRatio(nextZoom)
                                } catch (_: Throwable) {
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (zoomRatio > 1.1f) Color(0x3300E5FF) else NeliSurfaceVariant
                                )
                                .testTag("camera_scanner_zoom_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = if (zoomRatio > 1.1f) "Reset 1x Zoom" else "2x TV Zoom",
                                tint = if (zoomRatio > 1.1f) NeliGenreCyan else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextTorch = !isTorchEnabled
                                isTorchEnabled = nextTorch
                                try {
                                    activeCamera?.cameraControl?.enableTorch(nextTorch)
                                } catch (_: Throwable) {
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isTorchEnabled) Color(0x33FBBF24) else NeliSurfaceVariant
                                )
                                .testTag("camera_scanner_torch_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = if (isTorchEnabled) "Turn Flash Off" else "Turn Flash On",
                                tint = if (isTorchEnabled) Color(0xFFFBBF24) else Color.White
                            )
                        }

                        IconButton(
                            onClick = {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(NeliSurfaceVariant)
                                .testTag("camera_scanner_switch_lens_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Switch Camera",
                                tint = NeliGenreCyan
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("scan_to_cast_scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NeliTextSecondary
                        )
                    }
                }
            }

            // CameraX Live Preview & Real-Time ZXing QR Code Frame Analyzer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .border(2.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .testTag("scan_to_cast_camera_preview_box"),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    CameraXQrAnalyzerSurface(
                        lensFacing = lensFacing,
                        zoomRatio = zoomRatio,
                        lifecycleOwner = lifecycleOwner,
                        onCameraBound = { cam ->
                            activeCamera = cam
                        },
                        onQrCodeDetected = { qrText ->
                            ContextCompat.getMainExecutor(context).execute {
                                handleCapturedQrPayload(qrText)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(52.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Permission Required",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Allow camera access so NeliTV can scan the QR code displayed on your TV or PC browser.",
                            color = NeliTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeliGenreCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("scan_to_cast_grant_camera_button")
                        ) {
                            Text(
                                text = "Grant Camera Permission",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Animated QR Scanner Reticle & Laser Line Overlay
                QrScannerReticleOverlay(
                    modifier = Modifier.fillMaxSize()
                )

                // Bottom Status Pill inside Camera Viewfinder
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xD9090E1A))
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusBannerText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Connect via Manual TV Session Code or Toggle Manual Input
            Button(
                onClick = {
                    val entered = manualSessionInput.trim()
                    if (entered.isNotBlank()) {
                        handleCapturedQrPayload(entered)
                    } else {
                        showManualInput = true
                        statusBannerText = "Point the camera directly at the QR code on https://cast-nelitv.web.app or enter the TV Session Code below."
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .height(52.dp)
                    .testTag("scan_to_cast_scan_and_connect_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isPairingSession) {
                        "Connecting & Streaming to TV..."
                    } else if (manualSessionInput.isNotBlank()) {
                        "Connect & Stream to TV (${manualSessionInput.trim()})"
                    } else {
                        "Scanning QR Live... (Or Tap to Enter TV Code)"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Optional manual session code input toggle
            Text(
                text = if (showManualInput) {
                    "Hide Session Code Input"
                } else {
                    "Have a TV Session Code? Enter manually"
                },
                color = NeliTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { showManualInput = !showManualInput }
                    .padding(vertical = 4.dp)
                    .testTag("scan_to_cast_toggle_manual_code")
            )

            if (showManualInput) {
                OutlinedTextField(
                    value = manualSessionInput,
                    onValueChange = { manualSessionInput = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Paste https://cast-nelitv.web.app/cast/... or TV session ID",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeliGenreCyan,
                        unfocusedBorderColor = NeliBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .testTag("scan_to_cast_manual_code_input")
                )
            }
        }
    }
}

@Composable
private fun QrScannerReticleOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser")
    val scanLineProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_laser_line"
    )

    Canvas(modifier = modifier) {
        val boxSide = min(size.width, size.height) * 0.66f
        val left = (size.width - boxSide) / 2f
        val top = (size.height - boxSide) / 2f
        val right = left + boxSide
        val bottom = top + boxSide
        val cornerLen = boxSide * 0.16f
        val strokePx = 4.dp.toPx()

        // Subtle frame border
        drawRoundRect(
            color = Color(0x4400E5FF),
            topLeft = Offset(left, top),
            size = Size(boxSide, boxSide),
            cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx())
        )

        // Corner brackets (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        val bracketColor = Color(0xFF34D399)
        // Top-Left
        drawLine(bracketColor, Offset(left, top + cornerLen), Offset(left, top), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, top), Offset(left + cornerLen, top), strokePx, StrokeCap.Round)
        // Top-Right
        drawLine(bracketColor, Offset(right - cornerLen, top), Offset(right, top), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, top), Offset(right, top + cornerLen), strokePx, StrokeCap.Round)
        // Bottom-Left
        drawLine(bracketColor, Offset(left, bottom - cornerLen), Offset(left, bottom), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokePx, StrokeCap.Round)
        // Bottom-Right
        drawLine(bracketColor, Offset(right - cornerLen, bottom), Offset(right, bottom), strokePx, StrokeCap.Round)
        drawLine(bracketColor, Offset(right, bottom - cornerLen), Offset(right, bottom), strokePx, StrokeCap.Round)

        // Animated laser line inside reticle
        val laserY = top + boxSide * scanLineProgress
        drawLine(
            color = Color(0xFFFF2E7E),
            start = Offset(left + 14.dp.toPx(), laserY),
            end = Offset(right - 14.dp.toPx(), laserY),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CameraXQrAnalyzerSurface(
    lensFacing: Int,
    zoomRatio: Float,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    onCameraBound: (Camera) -> Unit,
    onQrCodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                analysisExecutor.shutdown()
            } catch (_: Throwable) {
            }
        }
    }

    // Continuous center-reticle autofocus every 2.5s so TV screen QR codes stay crisp
    LaunchedEffect(boundCamera) {
        val cam = boundCamera ?: return@LaunchedEffect
        while (true) {
            try {
                val w = previewView.width.toFloat()
                val h = previewView.height.toFloat()
                if (w > 0f && h > 0f) {
                    val point = previewView.meteringPointFactory.createPoint(w * 0.5f, h * 0.5f)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    )
                        .setAutoCancelDuration(2, TimeUnit.SECONDS)
                        .build()
                    cam.cameraControl.startFocusAndMetering(action)
                }
            } catch (_: Throwable) {
            }
            delay(2500L)
        }
    }

    LaunchedEffect(lensFacing) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val decoded = CameraQrFrameDecoder.decodeQrFromImageProxy(imageProxy)
                                if (!decoded.isNullOrBlank()) {
                                    val sessionId = CastReceiverConfig.extractSessionIdFromScannedQr(decoded)
                                    if (sessionId.isNotBlank()) {
                                        onQrCodeDetected(decoded)
                                    }
                                }
                            } catch (_: Throwable) {
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
                try {
                    camera.cameraControl.setZoomRatio(zoomRatio)
                } catch (_: Throwable) {
                }
                boundCamera = camera
                onCameraBound(camera)
            } catch (_: Throwable) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    AndroidView(
        factory = { previewView },
        update = { view ->
            if (view.scaleType != PreviewView.ScaleType.FILL_CENTER) {
                view.scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier
    )
}

/**
 * Real-time ZXing + ISO/IEC 18004 QR Code Decoder for CameraX `ImageProxy` YUV_420_888 frames
 * and boolean module matrices.
 */
internal object CameraQrFrameDecoder {

    private val zxingHints: Map<DecodeHintType, Any> = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE))
        put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
        put(DecodeHintType.CHARACTER_SET, "UTF-8")
    }

    /**
     * Decodes a QR code string from a CameraX [ImageProxy] Y-plane luminance buffer using ZXing
     * (`PlanarYUVLuminanceSource` + `HybridBinarizer` / `GlobalHistogramBinarizer` across 0°/90°
     * orientations and normal/inverted luminance), with fallback to the ISO module matrix decoder.
     */
    fun decodeQrFromImageProxy(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        if (width < 21 || height < 21) return null

        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val yBytes = extractContiguousYPlane(buffer, width, height, rowStride, pixelStride) ?: return null

        // 1. Try ZXing on full frame (0° normal & inverted)
        decodeWithZxingYuv(yBytes, width, height)?.let { return it }

        // 2. Try ZXing on 90°-rotated frame (for portrait camera sensor orientation)
        val rotatedBytes = rotateYPlane90Clockwise(yBytes, width, height)
        decodeWithZxingYuv(rotatedBytes, height, width)?.let { return it }

        // 3. Try ZXing on center 65% cropped ROI (helps when scanning a TV screen from across the room)
        val cropW = (width * 65) / 100
        val cropH = (height * 65) / 100
        if (cropW >= 32 && cropH >= 32) {
            decodeWithZxingYuvCrop(
                yBytes = yBytes,
                dataWidth = width,
                dataHeight = height,
                left = (width - cropW) / 2,
                top = (height - cropH) / 2,
                cropWidth = cropW,
                cropHeight = cropH
            )?.let { return it }
        }

        // 4. Fallback to direct boolean bitmap decoder
        val cropSide = min(width, height) * 3 / 4
        val startX = (width - cropSide) / 2
        val startY = (height - cropSide) / 2
        val gridDim = min(180, cropSide)
        val step = cropSide.toFloat() / gridDim.toFloat()
        var sumLum = 0L
        val lumGrid = Array(gridDim) { IntArray(gridDim) }

        for (r in 0 until gridDim) {
            val srcY = (startY + (r * step).toInt()).coerceIn(0, height - 1)
            val rowOffset = srcY * width
            for (c in 0 until gridDim) {
                val srcX = (startX + (c * step).toInt()).coerceIn(0, width - 1)
                val lum = yBytes[rowOffset + srcX].toInt() and 0xFF
                lumGrid[r][c] = lum
                sumLum += lum
            }
        }

        val threshold = (sumLum / (gridDim * gridDim)).toInt().coerceIn(60, 195)
        val darkBitmap = Array(gridDim) { r ->
            BooleanArray(gridDim) { c -> lumGrid[r][c] < threshold }
        }

        return decodeQrFromBooleanBitmap(darkBitmap)
    }

    private fun extractContiguousYPlane(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray? {
        return try {
            val duplicate = buffer.duplicate()
            duplicate.rewind()
            val out = ByteArray(width * height)
            if (rowStride == width && pixelStride == 1 && duplicate.remaining() >= width * height) {
                duplicate.get(out, 0, width * height)
                return out
            }
            val limit = duplicate.limit()
            var outIdx = 0
            for (y in 0 until height) {
                val rowStart = y * rowStride
                if (pixelStride == 1 && rowStart + width <= limit) {
                    duplicate.position(rowStart)
                    duplicate.get(out, outIdx, width)
                    outIdx += width
                } else {
                    for (x in 0 until width) {
                        val pos = rowStart + x * pixelStride
                        out[outIdx++] = if (pos in 0 until limit) duplicate.get(pos) else 0
                    }
                }
            }
            out
        } catch (_: Throwable) {
            null
        }
    }

    private fun rotateYPlane90Clockwise(yBytes: ByteArray, width: Int, height: Int): ByteArray {
        val rotated = ByteArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            val dstCol = height - 1 - y
            for (x in 0 until width) {
                rotated[x * height + dstCol] = yBytes[rowOffset + x]
            }
        }
        return rotated
    }

    private fun decodeWithZxingYuv(yBytes: ByteArray, width: Int, height: Int): String? {
        return try {
            val source = PlanarYUVLuminanceSource(yBytes, width, height, 0, 0, width, height, false)
            decodeFromLuminanceSource(source)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeWithZxingYuvCrop(
        yBytes: ByteArray,
        dataWidth: Int,
        dataHeight: Int,
        left: Int,
        top: Int,
        cropWidth: Int,
        cropHeight: Int
    ): String? {
        return try {
            val source = PlanarYUVLuminanceSource(
                yBytes,
                dataWidth,
                dataHeight,
                left,
                top,
                cropWidth,
                cropHeight,
                false
            )
            decodeFromLuminanceSource(source)
        } catch (_: Throwable) {
            null
        }
    }

    private fun decodeFromLuminanceSource(source: LuminanceSource): String? {
        val reader = MultiFormatReader().apply { setHints(zxingHints) }

        // 1. Normal HybridBinarizer
        try {
            val res = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
            val text = res.text?.trim()
            if (!text.isNullOrEmpty()) return text
        } catch (_: Throwable) {
            reader.reset()
        }

        // 2. Normal GlobalHistogramBinarizer (better for low-contrast / screen glare)
        try {
            val res = reader.decodeWithState(BinaryBitmap(GlobalHistogramBinarizer(source)))
            val text = res.text?.trim()
            if (!text.isNullOrEmpty()) return text
        } catch (_: Throwable) {
            reader.reset()
        }

        // 3. Inverted luminance (for light-on-dark QR codes on dark TV themes)
        try {
            val inverted = source.invert()
            val res = reader.decodeWithState(BinaryBitmap(HybridBinarizer(inverted)))
            val text = res.text?.trim()
            if (!text.isNullOrEmpty()) return text
        } catch (_: Throwable) {
            reader.reset()
        }

        return null
    }

    /**
     * Decodes a QR code string from a 2D boolean bitmap (either a raw module matrix from `IsoQrCodeEncoder`
     * or a thresholded camera image).
     */
    fun decodeQrFromBooleanBitmap(bitmap: Array<BooleanArray>): String? {
        val h = bitmap.size
        if (h < 21) return null
        val w = bitmap[0].size
        if (w < 21) return null

        // Try ZXing RGBLuminanceSource with quiet zone padding first
        try {
            val scale = 4
            val quietZone = 16
            val imgW = w * scale + quietZone * 2
            val imgH = h * scale + quietZone * 2
            val pixels = IntArray(imgW * imgH) { 0xFFFFFFFF.toInt() }
            for (r in 0 until h) {
                for (c in 0 until w) {
                    if (bitmap[r][c]) {
                        val baseY = quietZone + r * scale
                        val baseX = quietZone + c * scale
                        for (dy in 0 until scale) {
                            val rowIdx = (baseY + dy) * imgW + baseX
                            for (dx in 0 until scale) {
                                pixels[rowIdx + dx] = 0xFF000000.toInt()
                            }
                        }
                    }
                }
            }
            val rgbSource = RGBLuminanceSource(imgW, imgH, pixels)
            decodeFromLuminanceSource(rgbSource)?.let { return it }
        } catch (_: Throwable) {
        }

        // Find bounding box of dark modules
        var minR = h
        var maxR = -1
        var minC = w
        var maxC = -1
        for (r in 0 until h) {
            for (c in 0 until w) {
                if (bitmap[r][c]) {
                    if (r < minR) minR = r
                    if (r > maxR) maxR = r
                    if (c < minC) minC = c
                    if (c > maxC) maxC = c
                }
            }
        }
        if (maxR <= minR || maxC <= minC) return null

        val boxH = maxR - minR + 1
        val boxW = maxC - minC + 1

        // Estimate module size from top-left 7x7 finder pattern
        var topBorderRun = 0
        for (c in minC..maxC) {
            if (bitmap[minR][c]) topBorderRun++ else break
        }
        if (topBorderRun <= 0) return null

        val moduleSize = (topBorderRun / 7.0f).coerceAtLeast(1.0f)
        val rawDim = (((boxW + boxH) / 2.0f) / moduleSize).roundToInt()
        val version = ((rawDim - 17) / 4.0f).roundToInt().coerceIn(1, 6)
        val dim = 17 + version * 4

        val modules = Array(dim) { BooleanArray(dim) }
        val stepR = boxH.toFloat() / dim.toFloat()
        val stepC = boxW.toFloat() / dim.toFloat()

        for (r in 0 until dim) {
            val sampleR = (minR + (r + 0.5f) * stepR).toInt().coerceIn(0, h - 1)
            for (c in 0 until dim) {
                val sampleC = (minC + (c + 0.5f) * stepC).toInt().coerceIn(0, w - 1)
                modules[r][c] = bitmap[sampleR][sampleC]
            }
        }

        return decodeNormalizedModuleMatrix(version, dim, modules)
    }

    internal fun decodeNormalizedModuleMatrix(
        version: Int,
        dim: Int,
        modules: Array<BooleanArray>
    ): String? {
        if (!modules[3][3] || !modules[3][dim - 4] || !modules[dim - 4][3]) return null

        var formatBits = 0
        for (i in 0..5) {
            formatBits = (formatBits shl 1) or (if (modules[8][i]) 1 else 0)
        }
        formatBits = (formatBits shl 1) or (if (modules[8][7]) 1 else 0)
        formatBits = (formatBits shl 1) or (if (modules[8][8]) 1 else 0)
        formatBits = (formatBits shl 1) or (if (modules[7][8]) 1 else 0)
        for (i in 9..14) {
            formatBits = (formatBits shl 1) or (if (modules[14 - i][8]) 1 else 0)
        }
        val unmaskedFormat = formatBits xor 0x5412
        val maskPattern = (unmaskedFormat ushr 10) and 0b111

        val isFunction = buildFunctionMask(version, dim)
        val unmasked = Array(dim) { r ->
            BooleanArray(dim) { c ->
                val bit = modules[r][c]
                if (!isFunction[r][c] && applyMaskPredicate(maskPattern, r, c)) !bit else bit
            }
        }

        val codewords = extractCodewords(dim, unmasked, isFunction)
        val deinterleaved = if (version == 6 && codewords.size >= 136) {
            val data = IntArray(136)
            for (i in 0 until 68) {
                data[i] = codewords[i * 2]
                data[68 + i] = codewords[i * 2 + 1]
            }
            data
        } else {
            codewords
        }

        return decodeByteModePayload(deinterleaved)
    }

    private fun applyMaskPredicate(mask: Int, r: Int, c: Int): Boolean {
        return when (mask) {
            0 -> (r + c) % 2 == 0
            1 -> r % 2 == 0
            2 -> c % 3 == 0
            3 -> (r + c) % 3 == 0
            4 -> ((r / 2) + (c / 3)) % 2 == 0
            5 -> ((r * c) % 2) + ((r * c) % 3) == 0
            6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
            7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
            else -> (r + c) % 2 == 0
        }
    }

    private fun buildFunctionMask(version: Int, size: Int): Array<BooleanArray> {
        val isFunction = Array(size) { BooleanArray(size) }
        fun markFinder(centerR: Int, centerC: Int) {
            for (dr in -4..4) {
                for (dc in -4..4) {
                    val rr = centerR + dr
                    val cc = centerC + dc
                    if (rr in 0 until size && cc in 0 until size) {
                        isFunction[rr][cc] = true
                    }
                }
            }
        }
        markFinder(3, 3)
        markFinder(3, size - 4)
        markFinder(size - 4, 3)

        for (i in 0 until size) {
            isFunction[6][i] = true
            isFunction[i][6] = true
        }

        val alignPositions = when (version) {
            2 -> intArrayOf(6, 18)
            3 -> intArrayOf(6, 22)
            4 -> intArrayOf(6, 26)
            5 -> intArrayOf(6, 30)
            6 -> intArrayOf(6, 34)
            else -> intArrayOf()
        }
        for (ar in alignPositions) {
            for (ac in alignPositions) {
                if ((ar == 6 && ac == 6) || (ar == 6 && ac == size - 7) || (ar == size - 7 && ac == 6)) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        isFunction[ar + dr][ac + dc] = true
                    }
                }
            }
        }

        for (i in 0..8) {
            isFunction[8][i] = true
            isFunction[i][8] = true
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            isFunction[size - 1 - i][8] = true
        }
        isFunction[size - 8][8] = true
        return isFunction
    }

    private fun extractCodewords(
        size: Int,
        modules: Array<BooleanArray>,
        isFunction: Array<BooleanArray>
    ): IntArray {
        val bits = ArrayList<Int>(size * size)
        var right = size - 1
        var vert = 0
        while (right >= 1) {
            if (right == 6) right = 5
            for (cnt in 0 until size) {
                val r = if ((vert and 1) == 0) size - 1 - cnt else cnt
                for (j in 0..1) {
                    val c = right - j
                    if (!isFunction[r][c]) {
                        bits.add(if (modules[r][c]) 1 else 0)
                    }
                }
            }
            right -= 2
            vert++
        }
        val numBytes = bits.size / 8
        val result = IntArray(numBytes)
        for (i in 0 until numBytes) {
            var b = 0
            for (j in 0 until 8) {
                b = (b shl 1) or bits[i * 8 + j]
            }
            result[i] = b
        }
        return result
    }

    private fun decodeByteModePayload(codewords: IntArray): String? {
        if (codewords.size < 3) return null
        val totalBits = codewords.size * 8
        fun readBits(startBit: Int, count: Int): Int {
            var v = 0
            for (i in 0 until count) {
                val bitIdx = startBit + i
                if (bitIdx >= totalBits) break
                val bit = (codewords[bitIdx ushr 3] ushr (7 - (bitIdx and 7))) and 1
                v = (v shl 1) or bit
            }
            return v
        }

        val mode = readBits(0, 4)
        if (mode != 0b0100) return null
        val byteLen = readBits(4, 8)
        if (byteLen <= 0 || 12 + byteLen * 8 > totalBits) return null

        val out = ByteArray(byteLen)
        for (i in 0 until byteLen) {
            out[i] = readBits(12 + i * 8, 8).toByte()
        }
        val decoded = String(out, Charsets.UTF_8).trim()
        return decoded.takeIf { it.isNotEmpty() }
    }
}
