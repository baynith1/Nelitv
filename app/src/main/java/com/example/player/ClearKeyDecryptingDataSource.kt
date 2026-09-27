package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * A [DataSource] that transparently:
 * 1. Propagates exact encoded query parameters (e.g., `cdntoken=...`) from the manifest URI to segment URIs.
 * 2. Strips `<ContentProtection>` tags from DASH `.mpd` manifests when software ClearKey decryption is active.
 * 3. Decrypts CENC AES-128-CTR `.mp4` / `.m4s` initialization and media segments in memory using [Mp4CencDecryptor],
 *    eliminating reliance on hardware/emulator CryptoHalHidl DRM factories.
 */
@OptIn(UnstableApi::class)
class ClearKeyDecryptingDataSource(
    private val upstream: DataSource,
    private val encodedManifestQuery: String?,
    private val decryptor: Mp4CencDecryptor?
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val encodedManifestQuery: String?,
        private val decryptor: Mp4CencDecryptor?
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return ClearKeyDecryptingDataSource(
                upstream = upstreamFactory.createDataSource(),
                encodedManifestQuery = encodedManifestQuery,
                decryptor = decryptor
            )
        }
    }

    private var currentUri: Uri? = null
    private var transformedData: ByteArray? = null
    private var readPosition: Int = 0
    private var bytesRemaining: Int = 0
    private var passthroughMode: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        val effectiveDataSpec = resolveDataSpecToken(dataSpec)
        val uriString = effectiveDataSpec.uri.toString()
        val pathPart = uriString.substringBefore("?").lowercase()

        val isMpd = pathPart.endsWith(".mpd")
        val isMp4Segment = pathPart.endsWith(".mp4") ||
            pathPart.endsWith(".m4s") ||
            pathPart.endsWith(".cmfv") ||
            pathPart.endsWith(".cmfa") ||
            pathPart.endsWith(".dash") ||
            pathPart.endsWith(".m4v") ||
            pathPart.endsWith(".m4a") ||
            pathPart.endsWith(".init")

        if (decryptor == null || (!isMpd && !isMp4Segment)) {
            passthroughMode = true
            val length = upstream.open(effectiveDataSpec)
            currentUri = upstream.uri ?: effectiveDataSpec.uri
            transferStarted(effectiveDataSpec)
            return length
        }

        passthroughMode = false

        // Fetch full resource from upstream so we can parse/decrypt complete MP4 boxes or MPD XML
        var fullFetchSpec = effectiveDataSpec.buildUpon()
            .setPosition(0)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        try {
            upstream.open(fullFetchSpec)
        } catch (e: androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
            if ((e.responseCode == 403 || e.responseCode == 401) &&
                fullFetchSpec.uri.toString().contains("azamtvltd.co.tz", ignoreCase = true)
            ) {
                try {
                    upstream.close()
                } catch (_: Exception) {
                }
                if (com.example.data.ChannelRepository.refreshCdnTokenFromEndpointSync()) {
                    val refreshedSpec = resolveDataSpecToken(dataSpec)
                    fullFetchSpec = refreshedSpec.buildUpon()
                        .setPosition(0)
                        .setLength(C.LENGTH_UNSET.toLong())
                        .build()
                    upstream.open(fullFetchSpec)
                } else {
                    throw e
                }
            } else {
                throw e
            }
        }
        currentUri = upstream.uri ?: fullFetchSpec.uri

        val rawBytes = readAllUpstreamBytes()
        upstream.close()

        val streamKey = pathPart.substringAfterLast("/").substringBeforeLast("-")
        val processedBytes = if (isMpd) {
            val xml = String(rawBytes, Charsets.UTF_8)
                .replace("http://cdnblncr.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST, ignoreCase = true)
                .replace("https://cdnblncr.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST, ignoreCase = true)
            decryptor.stripMpdContentProtection(xml).toByteArray(Charsets.UTF_8)
        } else {
            decryptor.processMp4Segment(rawBytes, streamKey)
        }

        transformedData = processedBytes
        val startOffset = effectiveDataSpec.position.toInt().coerceAtLeast(0)
        if (startOffset > processedBytes.size) {
            readPosition = processedBytes.size
            bytesRemaining = 0
        } else {
            readPosition = startOffset
            val available = processedBytes.size - startOffset
            bytesRemaining = if (effectiveDataSpec.length != C.LENGTH_UNSET.toLong()) {
                min(effectiveDataSpec.length.toInt(), available)
            } else {
                available
            }
        }

        transferStarted(effectiveDataSpec)
        return bytesRemaining.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        if (passthroughMode) {
            val read = upstream.read(buffer, offset, length)
            if (read > 0) {
                bytesTransferred(read)
            }
            return read
        }

        if (bytesRemaining <= 0) {
            return C.RESULT_END_OF_INPUT
        }

        val data = transformedData ?: return C.RESULT_END_OF_INPUT
        val toRead = min(length, bytesRemaining)
        System.arraycopy(data, readPosition, buffer, offset, toRead)
        readPosition += toRead
        bytesRemaining -= toRead
        bytesTransferred(toRead)
        return toRead
    }

    override fun getUri(): Uri? {
        return if (passthroughMode) upstream.uri else currentUri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return upstream.responseHeaders
    }

    override fun close() {
        if (passthroughMode) {
            upstream.close()
        } else {
            if (transformedData != null) {
                transformedData = null
                readPosition = 0
                bytesRemaining = 0
                transferEnded()
            }
        }
        currentUri = null
    }

    private fun resolveDataSpecToken(dataSpec: DataSpec): DataSpec {
        val rawUriStr = dataSpec.uri.toString()
        val normalizedStr = com.example.data.ChannelRepository.normalizeDashStreamUrl(rawUriStr)
        val normalizedUri = Uri.parse(normalizedStr)

        if (normalizedUri.toString().contains("cdntoken=", ignoreCase = true)) {
            return if (normalizedStr != rawUriStr) dataSpec.withUri(normalizedUri) else dataSpec
        }

        val fallbackQuery = encodedManifestQuery?.takeIf { it.isNotBlank() }
            ?: if (normalizedStr.contains("azamtvltd.co.tz", ignoreCase = true)) {
                "cdntoken=${com.example.data.ChannelRepository.AZAM_CDN_TOKEN}"
            } else {
                null
            }

        if (fallbackQuery.isNullOrBlank()) {
            return if (normalizedStr != rawUriStr) dataSpec.withUri(normalizedUri) else dataSpec
        }

        val currentEncodedQuery = normalizedUri.encodedQuery
        val mergedEncodedQuery = if (currentEncodedQuery.isNullOrBlank()) {
            fallbackQuery
        } else {
            "$currentEncodedQuery&$fallbackQuery"
        }

        val resolvedUri = normalizedUri.buildUpon()
            .encodedQuery(mergedEncodedQuery)
            .build()
        return dataSpec.withUri(resolvedUri)
    }

    private fun readAllUpstreamBytes(): ByteArray {
        val out = ByteArrayOutputStream(64 * 1024)
        val temp = ByteArray(16 * 1024)
        while (true) {
            val read = upstream.read(temp, 0, temp.size)
            if (read == C.RESULT_END_OF_INPUT || read < 0) break
            if (read > 0) {
                out.write(temp, 0, read)
            }
        }
        return out.toByteArray()
    }
}
