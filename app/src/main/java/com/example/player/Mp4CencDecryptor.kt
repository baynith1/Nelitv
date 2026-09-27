package com.example.player

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Performs in-place ISO/IEC 23001-7 (CENC AES-128-CTR) decryption of DASH MPD manifests
 * and fragmented MP4 (fMP4) initialization & media segments.
 *
 * By decrypting CENC segments in memory before ExoPlayer's extractor reads them:
 * - ExoPlayer receives standard unencrypted avc1 / mp4a streams.
 * - Avoids Android emulator CryptoHalHidl / MediaDrm failures and PSSH UUID mismatches.
 * - Preserves 100% of MP4 box sizes and byte offsets by converting DRM boxes (pssh, sinf, senc, saiz, saio) to 'free' boxes.
 */
class Mp4CencDecryptor(
    clearKeysHex: Map<String, String>
) {
    data class TrackEncryptionConfig(
        val trackId: Int,
        val isProtected: Boolean,
        val defaultIvSize: Int,
        val defaultKidHex: String,
        val defaultConstantIv: ByteArray? = null,
        val defaultSampleSize: Int = 0
    )

    private val keyMap: Map<String, ByteArray> = clearKeysHex.entries.associate { (k, v) ->
        normalizeHex(k) to ClearKeyUtil.hexToByteArray(v)
    }

    private val fallbackKeyBytes: ByteArray? = keyMap.values.firstOrNull()

    // Track ID -> TrackEncryptionConfig parsed from initialization segments
    private val trackConfigs = ConcurrentHashMap<Int, TrackEncryptionConfig>()
    private val trackConfigsByStream = ConcurrentHashMap<String, TrackEncryptionConfig>()

    // Last seen IV size (defaults to 8 bytes which is standard for Nagra/Azam/Widevine CENC)
    @Volatile
    private var lastKnownIvSize: Int = 8

    companion object {
        private val CONTENT_PROTECTION_SELF_CLOSING_REGEX =
            Regex("""<(?:[a-zA-Z0-9_]+:)?ContentProtection\b[^>]*/>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        private val CONTENT_PROTECTION_BLOCK_REGEX =
            Regex("""<(?:[a-zA-Z0-9_]+:)?ContentProtection\b[^>]*(?<!/)>.*?</(?:[a-zA-Z0-9_]+:)?ContentProtection\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        private fun normalizeHex(hex: String): String =
            hex.trim().replace("-", "").replace(" ", "").lowercase()

        private fun bytesToHex(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): String {
            val sb = StringBuilder(length * 2)
            for (i in offset until (offset + length)) {
                val b = bytes[i].toInt() and 0xFF
                sb.append(Character.forDigit(b ushr 4, 16))
                sb.append(Character.forDigit(b and 0x0F, 16))
            }
            return sb.toString()
        }

        private fun readInt32BE(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 24) or
                    ((data[offset + 1].toInt() and 0xFF) shl 16) or
                    ((data[offset + 2].toInt() and 0xFF) shl 8) or
                    (data[offset + 3].toInt() and 0xFF)
        }

        private fun readUInt16BE(data: ByteArray, offset: Int): Int {
            return ((data[offset].toInt() and 0xFF) shl 8) or
                    (data[offset + 1].toInt() and 0xFF)
        }

        private fun readType(data: ByteArray, offset: Int): String {
            if (offset + 4 > data.size) return ""
            return String(data, offset, 4, Charsets.ISO_8859_1)
        }

        private fun writeType(data: ByteArray, offset: Int, type: String) {
            val bytes = type.toByteArray(Charsets.ISO_8859_1)
            System.arraycopy(bytes, 0, data, offset, 4)
        }
    }

    /**
     * Strips <ContentProtection> elements from a DASH MPD XML string so ExoPlayer
     * treats the manifest as standard clear DASH.
     * Self-closing tags MUST be stripped before block tags so a self-closing tag never
     * matches across AdaptationSets to a later closing </ContentProtection> tag.
     */
    fun stripMpdContentProtection(mpdXml: String): String {
        val withoutSelfClosing = CONTENT_PROTECTION_SELF_CLOSING_REGEX.replace(mpdXml, "")
        return CONTENT_PROTECTION_BLOCK_REGEX.replace(withoutSelfClosing, "")
    }

    /**
     * Processes either an initialization segment (`moov`) or a media segment (`moof` + `mdat`)
     * in-place and returns the modified ByteArray.
     */
    fun processMp4Segment(data: ByteArray, streamKey: String = ""): ByteArray {
        var offset = 0
        val limit = data.size
        var currentMoofOffset = -1
        var pendingTrafInfo: List<TrafEncryptionData> = emptyList()

        while (offset + 8 <= limit) {
            val boxSize = readInt32BE(data, offset)
            val boxType = readType(data, offset + 4)
            val actualSize = when {
                boxSize == 1 && offset + 16 <= limit -> {
                    ByteBuffer.wrap(data, offset + 8, 8).order(ByteOrder.BIG_ENDIAN).long.toInt()
                }
                boxSize == 0 -> limit - offset
                else -> boxSize
            }
            if (actualSize < 8 || offset + actualSize > limit) break

            when (boxType) {
                "moov" -> {
                    processMoov(data, offset + 8, offset + actualSize, streamKey)
                }
                "moof" -> {
                    currentMoofOffset = offset
                    pendingTrafInfo = processMoof(data, offset, offset + 8, offset + actualSize, streamKey)
                }
                "mdat" -> {
                    if (pendingTrafInfo.isNotEmpty()) {
                        val headerSize = if (boxSize == 1) 16 else 8
                        val mdatPayloadStart = offset + headerSize
                        for (traf in pendingTrafInfo) {
                            decryptTrafSamples(
                                data = data,
                                moofOffset = currentMoofOffset,
                                mdatPayloadStart = mdatPayloadStart,
                                mdatEnd = offset + actualSize,
                                traf = traf
                            )
                        }
                        pendingTrafInfo = emptyList()
                    }
                }
            }

            offset += actualSize
        }
        return data
    }

    private fun processMoov(data: ByteArray, start: Int, end: Int, streamKey: String = "") {
        var offset = start
        val defaultSampleSizes = mutableMapOf<Int, Int>()

        // First scan mvex/trex for default sample sizes if any
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break
            if (type == "mvex") {
                parseMvex(data, offset + 8, offset + size, defaultSampleSizes)
            }
            offset += size
        }

        offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            when (type) {
                "pssh" -> {
                    // Neutralize PSSH box so FragmentedMp4Extractor does not attach DrmInitData
                    writeType(data, offset + 4, "free")
                }
                "trak" -> {
                    processTrak(data, offset + 8, offset + size, defaultSampleSizes, streamKey)
                }
            }
            offset += size
        }
    }

    private fun parseMvex(data: ByteArray, start: Int, end: Int, outDefaultSampleSizes: MutableMap<Int, Int>) {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break
            if (type == "trex" && size >= 32) {
                val trackId = readInt32BE(data, offset + 12)
                val defaultSampleSize = readInt32BE(data, offset + 24)
                outDefaultSampleSizes[trackId] = defaultSampleSize
            }
            offset += size
        }
    }

    private fun processTrak(
        data: ByteArray,
        start: Int,
        end: Int,
        defaultSampleSizes: Map<Int, Int>,
        streamKey: String = ""
    ) {
        var trackId = 0
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            if (type == "tkhd" && size >= 24) {
                val version = data[offset + 8].toInt() and 0xFF
                trackId = if (version == 1) {
                    readInt32BE(data, offset + 28)
                } else {
                    readInt32BE(data, offset + 20)
                }
            } else if (type == "mdia") {
                processMdia(data, offset + 8, offset + size, trackId, defaultSampleSizes[trackId] ?: 0, streamKey)
            }
            offset += size
        }
    }

    private fun processMdia(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int,
        streamKey: String = ""
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break
            if (type == "minf") {
                processMinf(data, offset + 8, offset + size, trackId, defaultSampleSize, streamKey)
            }
            offset += size
        }
    }

    private fun processMinf(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int,
        streamKey: String = ""
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break
            if (type == "stbl") {
                processStbl(data, offset + 8, offset + size, trackId, defaultSampleSize, streamKey)
            }
            offset += size
        }
    }

    private fun processStbl(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int,
        streamKey: String = ""
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break
            when (type) {
                "stsd" -> {
                    if (size >= 16) {
                        processStsd(data, offset + 16, offset + size, trackId, defaultSampleSize, streamKey)
                    }
                }
                "sbgp", "sgpd" -> {
                    if (size >= 16 && readType(data, offset + 12) == "seig") {
                        writeType(data, offset + 4, "free")
                    }
                }
                "saiz", "saio", "senc" -> {
                    writeType(data, offset + 4, "free")
                }
            }
            offset += size
        }
    }

    private fun processStsd(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int,
        streamKey: String = ""
    ) {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            if (type == "encv" || type == "enca" || type == "enct" || type == "encs") {
                val childStartOffset = when (type) {
                    "encv" -> offset + 86 // 8 header + 78 VisualSampleEntry
                    "enca" -> {
                        // 8 header + 28 AudioSampleEntry (version 0)
                        val soundVersion = if (offset + 18 <= end) readUInt16BE(data, offset + 16) else 0
                        when (soundVersion) {
                            1 -> offset + 36 + 16
                            2 -> offset + 36 + 36
                            else -> offset + 36
                        }
                    }
                    else -> offset + 16
                }

                var originalFormat = when (type) {
                    "encv" -> "avc1"
                    "enca" -> "mp4a"
                    else -> type
                }

                var childOffset = childStartOffset
                val entryEnd = offset + size
                while (childOffset + 8 <= entryEnd) {
                    val childSize = readInt32BE(data, childOffset)
                    val childType = readType(data, childOffset + 4)
                    if (childSize < 8 || childOffset + childSize > entryEnd) break

                    if (childType == "sinf") {
                        val sinfResult = parseSinf(data, childOffset + 8, childOffset + childSize, trackId, defaultSampleSize)
                        if (sinfResult.originalFormat.length == 4) {
                            originalFormat = sinfResult.originalFormat
                        }
                        if (sinfResult.config != null) {
                            trackConfigs[trackId] = sinfResult.config
                            if (streamKey.isNotEmpty()) {
                                trackConfigsByStream["$streamKey#$trackId"] = sinfResult.config
                            }
                            if (sinfResult.config.defaultIvSize > 0) {
                                lastKnownIvSize = sinfResult.config.defaultIvSize
                            }
                        }
                        // Neutralize sinf to 'free' so extractor treats sample entry as clear
                        writeType(data, childOffset + 4, "free")
                    }
                    childOffset += childSize
                }

                // Restore original sample entry type (e.g. 'encv' -> 'avc1', 'enca' -> 'mp4a')
                writeType(data, offset + 4, originalFormat)
            }

            offset += size
        }
    }

    private data class SinfParseResult(
        val originalFormat: String,
        val config: TrackEncryptionConfig?
    )

    private fun parseSinf(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int
    ): SinfParseResult {
        var originalFormat = ""
        var config: TrackEncryptionConfig? = null
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            when (type) {
                "frma" -> {
                    if (size >= 12) {
                        originalFormat = readType(data, offset + 8)
                    }
                }
                "schi" -> {
                    config = parseSchi(data, offset + 8, offset + size, trackId, defaultSampleSize)
                }
            }
            offset += size
        }
        return SinfParseResult(originalFormat, config)
    }

    private fun parseSchi(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        defaultSampleSize: Int
    ): TrackEncryptionConfig? {
        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            if (type == "tenc" && size >= 32) {
                // FullBox: 8 (box) + 4 (version/flags) + 2 (reserved/pattern) + 1 (isProtected) + 1 (ivSize) + 16 (KID) = 32 bytes
                val isProtected = (data[offset + 14].toInt() and 0xFF) != 0
                val ivSize = data[offset + 15].toInt() and 0xFF
                val kidHex = bytesToHex(data, offset + 16, 16)
                var constantIv: ByteArray? = null
                if (isProtected && ivSize == 0 && size >= 33) {
                    val constIvSize = data[offset + 32].toInt() and 0xFF
                    if (offset + 33 + constIvSize <= offset + size) {
                        constantIv = data.copyOfRange(offset + 33, offset + 33 + constIvSize)
                    }
                }
                return TrackEncryptionConfig(
                    trackId = trackId,
                    isProtected = isProtected,
                    defaultIvSize = ivSize,
                    defaultKidHex = kidHex,
                    defaultConstantIv = constantIv,
                    defaultSampleSize = defaultSampleSize
                )
            }
            offset += size
        }
        return null
    }

    private data class SubsampleEntry(
        val clearBytes: Int,
        val protectedBytes: Int
    )

    private data class SampleCencData(
        val iv: ByteArray,
        val subsamples: List<SubsampleEntry>
    )

    private data class TrafEncryptionData(
        val trackId: Int,
        val kidHex: String?,
        val dataOffset: Int?,
        val sampleSizes: IntArray,
        val sampleCencList: List<SampleCencData>
    )

    private fun processMoof(
        data: ByteArray,
        moofStart: Int,
        contentStart: Int,
        moofEnd: Int,
        streamKey: String = ""
    ): List<TrafEncryptionData> {
        val result = mutableListOf<TrafEncryptionData>()
        var offset = contentStart
        while (offset + 8 <= moofEnd) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > moofEnd) break

            if (type == "traf") {
                val trafData = processTraf(data, offset + 8, offset + size, streamKey)
                if (trafData != null) {
                    result.add(trafData)
                }
            }
            offset += size
        }
        return result
    }

    private fun processTraf(
        data: ByteArray,
        start: Int,
        end: Int,
        streamKey: String = ""
    ): TrafEncryptionData? {
        var trackId = 0
        var defaultSampleSizeFromTfhd = 0
        var dataOffsetFromTrun: Int? = null
        var sampleSizes = IntArray(0)
        var sencBoxOffset = -1
        var sencBoxSize = 0

        var offset = start
        while (offset + 8 <= end) {
            val size = readInt32BE(data, offset)
            val type = readType(data, offset + 4)
            if (size < 8 || offset + size > end) break

            when (type) {
                "tfhd" -> {
                    if (size >= 16) {
                        val flags = readInt32BE(data, offset + 8) and 0x00FFFFFF
                        trackId = readInt32BE(data, offset + 12)
                        var pos = offset + 16
                        if ((flags and 0x000001) != 0) pos += 8 // base_data_offset
                        if ((flags and 0x000002) != 0) pos += 4 // sample_description_index
                        if ((flags and 0x000008) != 0) pos += 4 // default_sample_duration
                        if ((flags and 0x000010) != 0 && pos + 4 <= offset + size) {
                            defaultSampleSizeFromTfhd = readInt32BE(data, pos)
                        }
                    }
                }
                "trun" -> {
                    val streamTrackCfg = if (streamKey.isNotEmpty()) trackConfigsByStream["$streamKey#$trackId"] else null
                    val trackDefaultSize = if (defaultSampleSizeFromTfhd > 0) {
                        defaultSampleSizeFromTfhd
                    } else {
                        (streamTrackCfg ?: trackConfigs[trackId])?.defaultSampleSize ?: 0
                    }
                    val trunParsed = parseTrun(data, offset, offset + size, trackDefaultSize)
                    dataOffsetFromTrun = trunParsed.first
                    sampleSizes = trunParsed.second
                }
                "senc" -> {
                    sencBoxOffset = offset
                    sencBoxSize = size
                    writeType(data, offset + 4, "free")
                }
                "saiz", "saio" -> {
                    writeType(data, offset + 4, "free")
                }
                "sbgp", "sgpd" -> {
                    if (size >= 16 && readType(data, offset + 12) == "seig") {
                        writeType(data, offset + 4, "free")
                    }
                }
            }
            offset += size
        }

        if (sencBoxOffset < 0 || sampleSizes.isEmpty()) return null

        val trackConfig = (if (streamKey.isNotEmpty()) trackConfigsByStream["$streamKey#$trackId"] else null)
            ?: trackConfigs[trackId]
        val ivSize = trackConfig?.defaultIvSize?.takeIf { it > 0 } ?: lastKnownIvSize
        val sampleCencList = parseSenc(
            data = data,
            sencStart = sencBoxOffset,
            sencEnd = sencBoxOffset + sencBoxSize,
            defaultIvSize = ivSize,
            expectedSamples = sampleSizes.size
        )

        return TrafEncryptionData(
            trackId = trackId,
            kidHex = trackConfig?.defaultKidHex,
            dataOffset = dataOffsetFromTrun,
            sampleSizes = sampleSizes,
            sampleCencList = sampleCencList
        )
    }

    private fun parseTrun(
        data: ByteArray,
        trunStart: Int,
        trunEnd: Int,
        defaultSampleSize: Int
    ): Pair<Int?, IntArray> {
        if (trunStart + 16 > trunEnd) return null to IntArray(0)
        val flags = readInt32BE(data, trunStart + 8) and 0x00FFFFFF
        val sampleCount = readInt32BE(data, trunStart + 12)
        if (sampleCount <= 0 || sampleCount > 100_000) return null to IntArray(0)

        var pos = trunStart + 16
        var dataOffset: Int? = null
        if ((flags and 0x000001) != 0 && pos + 4 <= trunEnd) {
            dataOffset = readInt32BE(data, pos)
            pos += 4
        }
        if ((flags and 0x000004) != 0) {
            pos += 4 // first_sample_flags
        }

        val hasDuration = (flags and 0x000100) != 0
        val hasSize = (flags and 0x000200) != 0
        val hasFlags = (flags and 0x000400) != 0
        val hasCto = (flags and 0x000800) != 0

        val sizes = IntArray(sampleCount)
        for (i in 0 until sampleCount) {
            if (hasDuration) pos += 4
            if (hasSize && pos + 4 <= trunEnd) {
                sizes[i] = readInt32BE(data, pos)
                pos += 4
            } else {
                sizes[i] = defaultSampleSize
            }
            if (hasFlags) pos += 4
            if (hasCto) pos += 4
        }
        return dataOffset to sizes
    }

    private fun parseSenc(
        data: ByteArray,
        sencStart: Int,
        sencEnd: Int,
        defaultIvSize: Int,
        expectedSamples: Int
    ): List<SampleCencData> {
        if (sencStart + 16 > sencEnd) return emptyList()
        val flags = readInt32BE(data, sencStart + 8) and 0x00FFFFFF
        var pos = sencStart + 12

        var ivSize = defaultIvSize
        if ((flags and 0x000001) != 0) {
            // Override track encryption parameters present
            if (pos + 20 > sencEnd) return emptyList()
            ivSize = data[pos + 3].toInt() and 0xFF
            pos += 20
        }

        if (pos + 4 > sencEnd) return emptyList()
        val sampleCount = readInt32BE(data, pos)
        pos += 4

        val useSubsamples = (flags and 0x000002) != 0
        val count = sampleCount.coerceAtMost(expectedSamples)
        val result = ArrayList<SampleCencData>(count)

        for (i in 0 until count) {
            if (pos + ivSize > sencEnd) break
            val iv = data.copyOfRange(pos, pos + ivSize)
            pos += ivSize

            val subsamples = if (useSubsamples) {
                if (pos + 2 > sencEnd) break
                val subCount = readUInt16BE(data, pos)
                pos += 2
                val subs = ArrayList<SubsampleEntry>(subCount)
                for (j in 0 until subCount) {
                    if (pos + 6 > sencEnd) break
                    val clearBytes = readUInt16BE(data, pos)
                    val protectedBytes = readInt32BE(data, pos + 2)
                    pos += 6
                    subs.add(SubsampleEntry(clearBytes, protectedBytes))
                }
                subs
            } else {
                emptyList()
            }

            result.add(SampleCencData(iv, subsamples))
        }
        return result
    }

    private fun decryptTrafSamples(
        data: ByteArray,
        moofOffset: Int,
        mdatPayloadStart: Int,
        mdatEnd: Int,
        traf: TrafEncryptionData
    ) {
        val keyBytes = (traf.kidHex?.let { keyMap[it] } ?: fallbackKeyBytes) ?: return
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")

        var sampleCursor = if (traf.dataOffset != null && moofOffset >= 0) {
            val candidate = moofOffset + traf.dataOffset
            if (candidate in mdatPayloadStart..mdatEnd) candidate else mdatPayloadStart
        } else {
            mdatPayloadStart
        }

        val count = minOf(traf.sampleSizes.size, traf.sampleCencList.size)
        var scratchCapacity = 0
        var encryptedScratch = ByteArray(0)
        var decryptedScratch = ByteArray(0)
        val iv16 = ByteArray(16)

        for (i in 0 until count) {
            val cenc = traf.sampleCencList[i]
            val subsampleTotalSize = if (cenc.subsamples.isNotEmpty()) {
                var sum = 0
                for (sub in cenc.subsamples) {
                    sum += sub.clearBytes + sub.protectedBytes
                }
                sum
            } else {
                0
            }
            val sampleSize = when {
                traf.sampleSizes[i] > 0 -> traf.sampleSizes[i]
                subsampleTotalSize > 0 -> subsampleTotalSize
                count == 1 -> (mdatEnd - sampleCursor).coerceAtLeast(0)
                else -> 0
            }

            if (sampleSize <= 0 || sampleCursor + sampleSize > mdatEnd) {
                break
            }

            // Build 16-byte AES-CTR counter block from 8-byte or 16-byte IV
            java.util.Arrays.fill(iv16, 0.toByte())
            System.arraycopy(cenc.iv, 0, iv16, 0, minOf(cenc.iv.size, 16))

            if (cenc.subsamples.isEmpty()) {
                // Full-sample encryption in-place
                cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv16))
                cipher.doFinal(data, sampleCursor, sampleSize, data, sampleCursor)
            } else {
                // Subsample encryption: all protected ranges in a sample form one contiguous AES-CTR stream
                var totalProtected = 0
                for (sub in cenc.subsamples) {
                    if (sub.protectedBytes > 0) {
                        totalProtected += sub.protectedBytes
                    }
                }

                if (totalProtected > 0) {
                    if (totalProtected > scratchCapacity) {
                        scratchCapacity = totalProtected
                        encryptedScratch = ByteArray(scratchCapacity)
                        decryptedScratch = ByteArray(scratchCapacity)
                    }
                    var readCursor = sampleCursor
                    var concatOffset = 0

                    for (sub in cenc.subsamples) {
                        readCursor += sub.clearBytes
                        if (sub.protectedBytes > 0 && readCursor + sub.protectedBytes <= mdatEnd) {
                            System.arraycopy(data, readCursor, encryptedScratch, concatOffset, sub.protectedBytes)
                            concatOffset += sub.protectedBytes
                            readCursor += sub.protectedBytes
                        }
                    }

                    if (concatOffset > 0) {
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv16))
                        cipher.doFinal(encryptedScratch, 0, concatOffset, decryptedScratch, 0)

                        var writeCursor = sampleCursor
                        var decOffset = 0
                        for (sub in cenc.subsamples) {
                            writeCursor += sub.clearBytes
                            if (sub.protectedBytes > 0 && writeCursor + sub.protectedBytes <= mdatEnd) {
                                System.arraycopy(decryptedScratch, decOffset, data, writeCursor, sub.protectedBytes)
                                decOffset += sub.protectedBytes
                                writeCursor += sub.protectedBytes
                            }
                        }
                    }
                }
            }

            sampleCursor += sampleSize
        }
    }
}
