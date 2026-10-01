package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Widgets
import com.example.widget.NeliHomeWidgetProvider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.NeliAppUpdateManager
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * ISO/IEC 18004 compliant QR Code Generator (Byte Mode, Error Correction Level L)
 * for generating real, scannable QR codes offline and online.
 */
object NeliQrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocks: Int,
        val alignmentPositions: IntArray
    ) {
        val size: Int get() = version * 4 + 17
        val dataCodewords: Int get() = totalCodewords - ecCodewordsPerBlock * numBlocks
        val maxBytePayload: Int get() = dataCodewords - 2 // 4-bit mode + 8-bit length + 4-bit terminator = 2 bytes
    }

    private val versionSpecs = listOf(
        VersionSpec(1, 26, 7, 1, intArrayOf()),
        VersionSpec(2, 44, 10, 1, intArrayOf(6, 18)),
        VersionSpec(3, 70, 15, 1, intArrayOf(6, 22)),
        VersionSpec(4, 100, 20, 1, intArrayOf(6, 26)),
        VersionSpec(5, 134, 26, 1, intArrayOf(6, 30)),
        VersionSpec(6, 172, 18, 2, intArrayOf(6, 34))
    )

    fun encodeUrlToMatrix(content: String): Array<BooleanArray> {
        val payloadBytes = content.toByteArray(Charsets.UTF_8)
        val spec = versionSpecs.firstOrNull { payloadBytes.size <= it.maxBytePayload }
            ?: versionSpecs.last()
        val usableBytes = if (payloadBytes.size > spec.maxBytePayload) {
            payloadBytes.copyOf(spec.maxBytePayload)
        } else {
            payloadBytes
        }

        // 1. Build bit stream in Byte mode (0100) + 8-bit length + bytes + terminator + pad bytes
        val dataCodewords = ByteArray(spec.dataCodewords)
        var bitLen = 0
        fun appendBits(value: Int, numBits: Int) {
            for (i in numBits - 1 downTo 0) {
                val bit = (value ushr i) and 1
                val byteIdx = bitLen ushr 3
                val bitIdx = 7 - (bitLen and 7)
                if (byteIdx < dataCodewords.size && bit != 0) {
                    dataCodewords[byteIdx] = (dataCodewords[byteIdx].toInt() or (1 shl bitIdx)).toByte()
                }
                bitLen++
            }
        }

        appendBits(0b0100, 4) // Byte mode indicator
        appendBits(usableBytes.size, 8)
        for (b in usableBytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }
        val capacityBits = spec.dataCodewords * 8
        appendBits(0, minOf(4, capacityBits - bitLen))
        while (bitLen % 8 != 0) {
            appendBits(0, 1)
        }
        var padIndex = 0
        while (bitLen < capacityBits) {
            appendBits(if (padIndex % 2 == 0) 0xEC else 0x11, 8)
            padIndex++
        }

        // 2. Compute Reed-Solomon error correction and interleave blocks if numBlocks > 1
        val divisor = reedSolomonComputeDivisor(spec.ecCodewordsPerBlock)
        val allCodewords = ByteArray(spec.totalCodewords)
        if (spec.numBlocks == 1) {
            System.arraycopy(dataCodewords, 0, allCodewords, 0, dataCodewords.size)
            val ec = reedSolomonComputeRemainder(dataCodewords, divisor)
            System.arraycopy(ec, 0, allCodewords, dataCodewords.size, ec.size)
        } else {
            val blockDataSize = spec.dataCodewords / spec.numBlocks
            val dataBlocks = Array(spec.numBlocks) { b ->
                dataCodewords.copyOfRange(b * blockDataSize, (b + 1) * blockDataSize)
            }
            val ecBlocks = Array(spec.numBlocks) { b ->
                reedSolomonComputeRemainder(dataBlocks[b], divisor)
            }
            var outIdx = 0
            for (i in 0 until blockDataSize) {
                for (b in 0 until spec.numBlocks) {
                    allCodewords[outIdx++] = dataBlocks[b][i]
                }
            }
            for (i in 0 until spec.ecCodewordsPerBlock) {
                for (b in 0 until spec.numBlocks) {
                    allCodewords[outIdx++] = ecBlocks[b][i]
                }
            }
        }

        // 3. Initialize matrix & function module mask
        val size = spec.size
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y][x] = isDark
                isFunction[y][x] = true
            }
        }

        // Timing patterns
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }

        // Finder patterns (3 corners)
        fun drawFinderPattern(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val dist = maxOf(abs(dx), abs(dy))
                    setFunctionModule(cx + dx, cy + dy, dist != 2 && dist != 4)
                }
            }
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        // Alignment patterns
        val align = spec.alignmentPositions
        val numAlign = align.size
        for (i in 0 until numAlign) {
            for (j in 0 until numAlign) {
                if ((i == 0 && j == 0) || (i == 0 && j == numAlign - 1) || (i == numAlign - 1 && j == 0)) {
                    continue
                }
                val cx = align[i]
                val cy = align[j]
                for (dy in -2..2) {
                    for (dx in -2..2) {
                        setFunctionModule(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
                    }
                }
            }
        }

        // Reserve format info bits & Dark Module (with Mask 0)
        fun getBit(value: Int, bitIndex: Int): Boolean = ((value ushr bitIndex) and 1) != 0
        fun drawFormatBits(mask: Int) {
            val data = (0b01 shl 3) or mask // Level L = 01
            var rem = data
            for (i in 0 until 10) {
                rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            }
            val bits = ((data shl 10) or rem) xor 0x5412
            for (i in 0..5) setFunctionModule(8, i, getBit(bits, i))
            setFunctionModule(8, 7, getBit(bits, 6))
            setFunctionModule(8, 8, getBit(bits, 7))
            setFunctionModule(7, 8, getBit(bits, 8))
            for (i in 9..14) setFunctionModule(14 - i, 8, getBit(bits, i))
            for (i in 0..7) setFunctionModule(size - 1 - i, 8, getBit(bits, i))
            for (i in 8..14) setFunctionModule(8, size - 15 + i, getBit(bits, i))
            setFunctionModule(8, size - 8, true)
        }
        drawFormatBits(0)

        // 4. Zig-zag codeword placement
        var bitIdx = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && bitIdx < allCodewords.size * 8) {
                        val byteVal = allCodewords[bitIdx ushr 3].toInt() and 0xFF
                        modules[y][x] = getBit(byteVal, 7 - (bitIdx and 7))
                        bitIdx++
                    }
                }
            }
            right -= 2
        }

        // 5. Apply Mask 0 ((x + y) % 2 == 0)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!isFunction[y][x] && (x + y) % 2 == 0) {
                    modules[y][x] = !modules[y][x]
                }
            }
        }
        drawFormatBits(0)

        return modules
    }

    private fun reedSolomonComputeDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = reedSolomonMultiply(result[j].toInt() and 0xFF, root).toByte()
                if (j + 1 < degree) {
                    result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
                }
            }
            root = reedSolomonMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonComputeRemainder(data: ByteArray, divisor: ByteArray): ByteArray {
        val result = ByteArray(divisor.size)
        for (b in data) {
            val factor = (b.toInt() xor result[0].toInt()) and 0xFF
            System.arraycopy(result, 1, result, 0, result.size - 1)
            result[result.size - 1] = 0
            for (i in divisor.indices) {
                result[i] = (
                    result[i].toInt() xor
                        reedSolomonMultiply(divisor[i].toInt() and 0xFF, factor)
                    ).toByte()
            }
        }
        return result
    }

    private fun reedSolomonMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }
}

@Composable
fun NeliQrCodeCanvas(
    url: String,
    qrSize: Dp = 180.dp,
    modifier: Modifier = Modifier
) {
    val matrix = remember(url) { NeliQrCodeGenerator.encodeUrlToMatrix(url) }

    Box(
        modifier = modifier
            .size(qrSize)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(2.dp, NeliGenreCyan, RoundedCornerShape(16.dp))
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val moduleCount = matrix.size
            val quietZone = 2
            val totalUnits = moduleCount + quietZone * 2
            val cellSize = minOf(size.width, size.height) / totalUnits.toFloat()
            val offsetX = (size.width - cellSize * totalUnits) / 2f
            val offsetY = (size.height - cellSize * totalUnits) / 2f

            for (row in 0 until moduleCount) {
                for (col in 0 until moduleCount) {
                    if (matrix[row][col]) {
                        drawRect(
                            color = Color(0xFF090B10),
                            topLeft = Offset(
                                x = offsetX + (col + quietZone) * cellSize,
                                y = offsetY + (row + quietZone) * cellSize
                            ),
                            size = Size(cellSize + 0.4f, cellSize + 0.4f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Account page section containing:
 * 1. Share Neli TV APK (Scan QR Code to Download APK + Share by Link)
 *    using `https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk`
 * 2. Check for New Update & Automatic Update Every Time New Update Appears
 *    with What's New from `https://github.com/baynith1/Nelitv/releases/tag/v1.0.0`
 */
@Composable
fun NeliShareApkAndAutoUpdateSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val releaseInfo by NeliAppUpdateManager.releaseInfo.collectAsState()
    val isCheckingUpdate by NeliAppUpdateManager.isCheckingUpdate.collectAsState()
    val autoUpdateEnabled by NeliAppUpdateManager.autoUpdateEnabled.collectAsState()
    val statusBannerMessage by NeliAppUpdateManager.apkDownloadStatusMessage.collectAsState()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        NeliAppUpdateManager.reconcileInstalledPackageState(context)
    }

    var showFullscreenQrDialog by rememberSaveable { mutableStateOf(false) }

    if (showFullscreenQrDialog) {
        Dialog(onDismissRequest = { showFullscreenQrDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF10131E))
                    .border(1.5.dp, NeliGenreCyan, RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .testTag("fullscreen_apk_qr_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = NeliGenreCyan
                        )
                        Text(
                            text = "Scan to Download Neli TV APK",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = { showFullscreenQrDialog = false },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close QR Scanner",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                NeliQrCodeCanvas(
                    url = releaseInfo.apkDownloadUrl,
                    qrSize = 250.dp,
                    modifier = Modifier.testTag("fullscreen_qr_code_canvas")
                )

                Text(
                    text = "Point any phone camera or QR scanner at this code to immediately download Nelitv.apk (${releaseInfo.versionTag})",
                    color = NeliTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = releaseInfo.apkDownloadUrl,
                    color = NeliGenreCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { NeliAppUpdateManager.shareApkByLink(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share Link",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = { showFullscreenQrDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Done",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("share_apk_and_update_section"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Home Screen Widget Setup & Instant Home Screen Placement Card
        NeliHomeWidgetAccountSetupCard()

        // 1. Share APK by Scan to Download QR & Share by Link Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF1A1033), Color(0xFF101524))
                    )
                )
                .border(1.dp, NeliMagenta.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .padding(16.dp)
                .testTag("share_apk_qr_card"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeliMagenta),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share APK • Scan to Download & Link",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Scan QR Code with any phone camera or share direct Nelitv.apk download link",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // QR Code + Direct Link Preview Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NeliSurfaceVariant.copy(alpha = 0.7f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                NeliQrCodeCanvas(
                    url = releaseInfo.apkDownloadUrl,
                    qrSize = 128.dp,
                    modifier = Modifier
                        .clickable { showFullscreenQrDialog = true }
                        .testTag("apk_qr_code_preview")
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SCAN TO DOWNLOAD APK",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Official Neli TV (${releaseInfo.versionTag}) Android Package",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = releaseInfo.apkDownloadUrl,
                        color = NeliTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    OutlinedButton(
                        onClick = { showFullscreenQrDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expand_qr_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Enlarge QR to Scan",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Share by Link, Copy APK Link & Direct APK Download Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { NeliAppUpdateManager.shareApkByLink(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_apk_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share by Link",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = { NeliAppUpdateManager.copyApkDownloadLink(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_apk_link_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy APK Link",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2. Check for New Update & Automatic Update Card (GitHub Release + What's New)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NeliSurface)
                .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(16.dp)
                .testTag("app_update_and_whats_new_card"),
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeliCardPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "App Update & What's New",
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = releaseInfo.statusMessage,
                            color = if (releaseInfo.isNewUpdateAvailable) Color(0xFFF59E0B) else Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (releaseInfo.isNewUpdateAvailable) Color(0x33F59E0B) else Color(0x2610B981)
                        )
                        .border(
                            1.dp,
                            if (releaseInfo.isNewUpdateAvailable) Color(0xFFF59E0B) else Color(0xFF10B981),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = releaseInfo.versionTag,
                        color = if (releaseInfo.isNewUpdateAvailable) Color(0xFFFDE68A) else Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Automatic Update Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeliSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = NeliMagenta,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Automatic App Updates",
                            color = NeliTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Update automatically every time a new update appears on GitHub",
                            color = NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        NeliAppUpdateManager.setAutoUpdateEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeliMagenta
                    ),
                    modifier = Modifier.testTag("auto_update_switch")
                )
            }

            // What's New in App Box (from https://github.com/baynith1/Nelitv/releases/tag/v1.0.0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeliSurfaceVariant)
                    .border(1.dp, NeliBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "What's New in ${releaseInfo.releaseTitle}",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Text(
                    text = releaseInfo.whatsNewNotes,
                    color = NeliTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Text(
                    text = "GitHub Release: ${releaseInfo.releasePageUrl}",
                    color = NeliTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (!statusBannerMessage.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x2210B981))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = statusBannerMessage!!,
                        color = Color(0xFFA7F3D0),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Action Buttons: Check for New Update & Download/Update APK Now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            NeliAppUpdateManager.checkForUpdates(context, triggeredByUser = true)
                        }
                    },
                    enabled = !isCheckingUpdate,
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("check_for_update_button")
                ) {
                    if (isCheckingUpdate) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCheckingUpdate) "Checking..." else "Check for Update",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = {
                        NeliAppUpdateManager.downloadAndInstallApk(
                            context = context,
                            apkUrl = releaseInfo.apkDownloadUrl,
                            versionTag = releaseInfo.versionTag,
                            isAutoUpdate = false
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_latest_apk_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (releaseInfo.isNewUpdateAvailable) "Update Now" else "Download APK",
                        color = NeliTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            OutlinedButton(
                onClick = { NeliAppUpdateManager.openGitHubWhatsNewPage(context) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_github_release_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "View What's New & Release Page on GitHub (${releaseInfo.versionTag})",
                    color = NeliTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Account page Widget Setup Card:
 * Allows the user to tap once to set/pin the Nelitv Home Screen Widget and immediately
 * see it on their phone's Home Screen.
 */
@Composable
fun NeliHomeWidgetAccountSetupCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPinnedFlow by NeliHomeWidgetProvider.isWidgetPinnedFlow.collectAsState()
    val widgetStatusMessage by NeliHomeWidgetProvider.widgetSetupStatusMessage.collectAsState()
    var isPinnedLocal by remember { mutableStateOf(NeliHomeWidgetProvider.isWidgetPinned(context)) }
    val isWidgetActive = isPinnedFlow || isPinnedLocal

    androidx.compose.runtime.LaunchedEffect(Unit) {
        isPinnedLocal = NeliHomeWidgetProvider.isWidgetPinned(context)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF141E38), Color(0xFF1A0F2E))
                )
            )
            .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("account_widget_setup_card"),
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeliMagenta),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Widgets,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Home Screen Widget (Weka Widget)",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Binya kuweka Widget ya Azam TV Live & Sinema 2026 ionekane kwenye Home Screen",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isWidgetActive) Color(0x2610B981) else Color(0x33F41B54))
                    .border(
                        1.dp,
                        if (isWidgetActive) Color(0xFF10B981) else NeliMagenta,
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isWidgetActive) "ACTIVE ✓" else "SET WIDGET",
                    color = if (isWidgetActive) Color(0xFF10B981) else Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Interactive Widget Preview (Tapping the preview also sets & opens the widget on Home Screen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0C101C))
                .border(1.dp, Color(0x4400D2FF), RoundedCornerShape(16.dp))
                .clickable {
                    NeliHomeWidgetProvider.setAndShowWidgetOnHomeScreen(
                        context = context,
                        navigateToHomeScreen = true
                    )
                    isPinnedLocal = true
                }
                .padding(12.dp)
                .testTag("account_widget_preview_box"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NELITV HOME WIDGET PREVIEW",
                    color = NeliGenreCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Tap to Add to Home Screen →",
                    color = NeliMagenta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Azam Sports 1 HD", "Azam Two", "WWE Live 24/7").forEach { chTitle ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF181236))
                            .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "LIVE TV",
                                color = NeliMagenta,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = chTitle,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("2026 Swahili HD", "2026 Action HD", "2026 Series HD").forEach { movLabel ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF101B2E))
                            .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .padding(vertical = 6.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = movLabel,
                            color = NeliGenreCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (!widgetStatusMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2210B981))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                    .padding(10.dp)
                    .testTag("account_widget_status_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = widgetStatusMessage!!,
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    NeliHomeWidgetProvider.setAndShowWidgetOnHomeScreen(
                        context = context,
                        navigateToHomeScreen = true
                    )
                    isPinnedLocal = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("account_set_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Widgets,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Set & Show Widget",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = {
                    NeliHomeWidgetProvider.updateAllWidgets(context)
                    NeliHomeWidgetProvider.openDeviceHomeScreen(context)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("account_open_home_widget_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "View on Home Screen",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Automatic Homepage Popup Dialog whenever a new update arrives on GitHub (`baynith1/Nelitv`).
 * Allows the user to immediately update the app or toggle Auto-Updates for all future GitHub releases.
 */
@Composable
fun NeliHomepageAutoUpdatePopupDialog() {
    val context = LocalContext.current
    val releaseInfo by NeliAppUpdateManager.releaseInfo.collectAsState()
    val showPopup by NeliAppUpdateManager.showHomepageUpdatePopup.collectAsState()
    val autoUpdateEnabled by NeliAppUpdateManager.autoUpdateEnabled.collectAsState()
    val statusBannerMessage by NeliAppUpdateManager.apkDownloadStatusMessage.collectAsState()

    if (!showPopup || !releaseInfo.isNewUpdateAvailable) return

    Dialog(
        onDismissRequest = {
            NeliAppUpdateManager.dismissHomepageUpdatePopup(releaseInfo.versionTag)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF101422))
                .border(1.5.dp, NeliGenreCyan, RoundedCornerShape(24.dp))
                .padding(20.dp)
                .testTag("homepage_auto_update_popup_dialog"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeliMagenta),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "New GitHub Update (${releaseInfo.versionTag})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = releaseInfo.releaseTitle,
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = {
                        NeliAppUpdateManager.dismissHomepageUpdatePopup(releaseInfo.versionTag)
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .testTag("homepage_popup_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Update Popup",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // What's New box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeliSurfaceVariant)
                    .border(1.dp, NeliBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "What's New on GitHub (${releaseInfo.publishedAt})",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = releaseInfo.whatsNewNotes,
                    color = NeliTextPrimary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Auto-Update Toggle inside Homepage Popup
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(NeliCardPurple)
                    .border(1.dp, Color(0x44A855F7), RoundedCornerShape(14.dp))
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
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Auto-Update from GitHub",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Sasisha app moja kwa moja kila toleo jipya linapowekwa GitHub",
                            color = NeliTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }

                Switch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        NeliAppUpdateManager.setAutoUpdateEnabled(context, enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NeliMagenta
                    ),
                    modifier = Modifier.testTag("homepage_popup_auto_update_switch")
                )
            }

            if (!statusBannerMessage.isNullOrBlank()) {
                Text(
                    text = statusBannerMessage!!,
                    color = Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        NeliAppUpdateManager.dismissHomepageUpdatePopup(releaseInfo.versionTag)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("homepage_popup_later_button")
                ) {
                    Text(
                        text = "Baadaye",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        NeliAppUpdateManager.downloadAndInstallApk(
                            context = context,
                            apkUrl = releaseInfo.apkDownloadUrl,
                            versionTag = releaseInfo.versionTag,
                            isAutoUpdate = false
                        )
                        NeliAppUpdateManager.dismissHomepageUpdatePopup(releaseInfo.versionTag)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("homepage_popup_update_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Update Now",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
