package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * A [DataSource] that transparently:
 * 1. Propagates exact encoded query parameters (e.g., `cdntoken=...`) from the manifest URI to segment URIs.
 * 2. Automatically refreshes the live Azam CDN token from `https://streamzone.fun/api/cdn-token` and
 *    `https://streamzone.fun/api/channels` on background I/O threads if not yet synced or on HTTP 401/403.
 * 3. Strips `<ContentProtection>` tags from DASH `.mpd` manifests so ExoPlayer never throws DRM license errors.
 * 4. Decrypts CENC AES-128-CTR `.mp4` / `.m4s` initialization and media segments in memory using [Mp4CencDecryptor],
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

    companion object {
        private val AZAM_XML_HOST_REGEX = Regex(
            "https?://[a-zA-Z0-9.-]*azamtvltd\\.co\\.tz(?::\\d+)?",
            RegexOption.IGNORE_CASE
        )
    }

    private var currentUri: Uri? = null
    private var transformedData: ByteArray? = null
    private var readPosition: Int = 0
    private var bytesRemaining: Int = 0
    private var passthroughMode: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        val rawUriCheck = dataSpec.uri.toString()
        val isProxyStream = rawUriCheck.contains("/api/proxy-stream", ignoreCase = true)
        val isLegacyAzamHost = rawUriCheck.contains("azamtvltd.co.tz", ignoreCase = true)
        if (isLegacyAzamHost && com.example.data.ChannelRepository.isActiveTokenExpired()) {
            try {
                com.example.data.NeliTvRepository.fetchAppBootstrapBlocking(forceRefresh = false)
            } catch (_: Exception) {
            }
        }

        var effectiveDataSpec = if (isProxyStream || !isLegacyAzamHost) {
            val extraHeaders = com.example.data.NeliTvRepository.buildStreamPlayerHeaders(rawUriCheck)
            if (extraHeaders.isNotEmpty()) dataSpec.withAdditionalHeaders(extraHeaders) else dataSpec
        } else {
            resolveDataSpecToken(dataSpec, forceLatestRepoToken = true)
        }
        val uriString = effectiveDataSpec.uri.toString()
        val proxiedInnerUrl = if (isProxyStream) {
            effectiveDataSpec.uri.getQueryParameter("url").orEmpty()
        } else {
            ""
        }
        val pathPart = (proxiedInnerUrl.ifBlank { uriString }).substringBefore("?").lowercase()

        val isMpd = pathPart.endsWith(".mpd")
        val isMp4Segment = pathPart.endsWith(".mp4") || pathPart.endsWith(".m4s") || pathPart.endsWith(".cmfv") || pathPart.endsWith(".cmfa")

        if (!isMpd && (decryptor == null || !isMp4Segment)) {
            passthroughMode = true
            val (openedSpec, length) = openUpstreamWithTokenFailover(dataSpec, effectiveDataSpec)
            effectiveDataSpec = openedSpec
            currentUri = upstream.uri ?: effectiveDataSpec.uri
            transferStarted(effectiveDataSpec)
            return length
        }

        passthroughMode = false

        // Normalize path key (strip /tok_<JWT>/ prefix) for local init-segment caching
        val cachePathKey = if (pathPart.contains("/tok_")) {
            pathPart.substringBefore("/tok_") + "/" + pathPart.substringAfter("/tok_").substringAfter("/", "")
        } else {
            pathPart
        }

        // Fast-path 1: Reuse cached RAW DASH initialization segment bytes (0ms network),
        // ALWAYS running decryptor.processMp4Segment() on a fresh copy so the active
        // Mp4CencDecryptor instance populates its trackConfigs (KID, IV size, sample sizes).
        val isInitSegment = !isMpd && isMp4Segment && NeliLocalStreamEngine.isDashInitSegmentPath(pathPart)
        if (isInitSegment) {
            val cachedRawInit = NeliLocalStreamEngine.getCachedInitSegment(cachePathKey)
            if (cachedRawInit != null) {
                currentUri = effectiveDataSpec.uri
                val processedInit = decryptor?.processMp4Segment(cachedRawInit.copyOf()) ?: cachedRawInit
                return serveTransformedBytes(processedInit, effectiveDataSpec)
            }
        }

        // Fetch fresh live MPD manifest or media segment directly from CDN over pooled keep-alive connection
        val fullFetchSpec = effectiveDataSpec.buildUpon()
            .setPosition(0)
            .setLength(C.LENGTH_UNSET.toLong())
            .build()

        val (openedSpec, _) = openUpstreamWithTokenFailover(dataSpec, fullFetchSpec)
        currentUri = upstream.uri ?: openedSpec.uri

        val rawBytes = readAllUpstreamBytes()
        upstream.close()

        val processedBytes = if (isMpd) {
            val rawXml = String(rawBytes, Charsets.UTF_8)
            val xml = AZAM_XML_HOST_REGEX.replace(rawXml, com.example.data.NeliTvRepository.getCurrentCdnHost())
            val stripped = decryptor?.stripMpdContentProtection(xml)
                ?: Mp4CencDecryptor(emptyMap()).stripMpdContentProtection(xml)
            stripped.toByteArray(Charsets.UTF_8)
        } else {
            if (isInitSegment && rawBytes.isNotEmpty()) {
                // Save an unmodified RAW copy BEFORE in-place CENC box stripping mutates rawBytes
                NeliLocalStreamEngine.putCachedInitSegment(cachePathKey, rawBytes.copyOf())
            }
            decryptor?.processMp4Segment(rawBytes) ?: rawBytes
        }

        return serveTransformedBytes(processedBytes, effectiveDataSpec)
    }

    private fun serveTransformedBytes(processedBytes: ByteArray, effectiveDataSpec: DataSpec): Long {
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

    private fun openUpstreamWithTokenFailover(
        originalSpec: DataSpec,
        attemptSpec: DataSpec
    ): Pair<DataSpec, Long> {
        return try {
            val len = upstream.open(attemptSpec)
            attemptSpec to len
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            val attemptUriStr = attemptSpec.uri.toString()
            val isAuthError = TokenManager.isAuthenticationFailure(e.responseCode, e.message)
            val isAzamStream = attemptUriStr.contains("azamtvltd.co.tz", ignoreCase = true) ||
                attemptUriStr.contains("/live/eds/", ignoreCase = true)

            val shouldAutoRenewCdnKeys = isAuthError || (isAzamStream && (e.responseCode == 404 || e.responseCode == 410 || e.responseCode == 429))

            if (shouldAutoRenewCdnKeys) {
                try {
                    upstream.close()
                } catch (_: Exception) {
                }
                // Fast single-pass provider switch (aztv <-> mpingo) without blocking on slow backup channels JSON
                TokenManager.renewCdnKeysAndChannelsBlocking(
                    failedChannel = null,
                    httpStatusCode = e.responseCode,
                    switchProvider = isAzamStream && (e.responseCode == 429 || e.responseCode == 401 || e.responseCode == 403),
                    includeBackupChannelsSync = false
                )
                val refreshedBaseSpec = resolveDataSpecToken(originalSpec, forceLatestRepoToken = true)
                val retrySpec = refreshedBaseSpec.buildUpon()
                    .setPosition(attemptSpec.position)
                    .setLength(attemptSpec.length)
                    .build()
                try {
                    val retryLen = upstream.open(retrySpec)
                    return retrySpec to retryLen
                } catch (retryEx: HttpDataSource.InvalidResponseCodeException) {
                    if (isAzamStream) {
                        val openedAlt = tryOpenAzamAlternates(retrySpec, TokenManager.currentToken)
                        if (openedAlt != null) return openedAlt
                    }
                    throw retryEx
                }
            }

            if (isAzamStream) {
                val base = attemptUriStr.substringBefore("?")
                val channelToken = com.example.data.ChannelRepository.resolveChannelSpecificToken(base)
                    .ifBlank { TokenManager.currentToken }
                val openedAlt = tryOpenAzamAlternates(attemptSpec, channelToken)
                if (openedAlt != null) return openedAlt
            }

            throw e
        }
    }

    private fun tryOpenAzamAlternates(
        referenceSpec: DataSpec,
        tokenToUse: String
    ): Pair<DataSpec, Long>? {
        val uriStr = referenceSpec.uri.toString()
        val base = uriStr.substringBefore("?")
        val query = uriStr.substringAfter("?", "")
        val alternateUris = mutableListOf<String>()

        if (base.contains("/tok_", ignoreCase = true)) {
            val beforeTok = base.substringBefore("/tok_")
            val tokInPath = base.substringAfter("/tok_").substringBefore("/")
            val afterTok = base.substringAfter("/tok_").substringAfter("/", "")
            if (afterTok.isNotEmpty()) {
                val effectiveTok = tokenToUse.ifBlank { tokInPath }
                // 1. Canonical StreamZone clean query-only format (no redundant /tok_ in path)
                alternateUris.add("$beforeTok/$afterTok?cdntoken=$effectiveTok")
                // 2. Clean path-token-only format (no duplicate ?cdntoken= query param)
                alternateUris.add("$beforeTok/tok_$effectiveTok/$afterTok")
            }
            if (query.isNotEmpty()) {
                alternateUris.add(base)
            }
        } else if (base.contains("/live/eds/", ignoreCase = true)) {
            val edsIdx = base.indexOf("/live/eds/", ignoreCase = true)
            val hostPrefix = base.substring(0, edsIdx)
            val pathAfterHost = base.substring(edsIdx)
            // 1. Clean path-token-only format
            alternateUris.add("$hostPrefix/tok_$tokenToUse$pathAfterHost")
            // 2. Clean query-only format with latest token
            alternateUris.add("$hostPrefix$pathAfterHost?cdntoken=$tokenToUse")
        }

        for (altUrl in alternateUris.distinct()) {
            if (altUrl == uriStr) continue
            try {
                upstream.close()
            } catch (_: Exception) {
            }
            try {
                val altSpec = referenceSpec.buildUpon()
                    .setUri(Uri.parse(altUrl))
                    .setPosition(referenceSpec.position)
                    .setLength(referenceSpec.length)
                    .build()
                val altLen = upstream.open(altSpec)
                return altSpec to altLen
            } catch (_: Exception) {
            }
        }
        return null
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

    private fun resolveDataSpecToken(
        dataSpec: DataSpec,
        forceLatestRepoToken: Boolean = false
    ): DataSpec {
        return TokenManager.injectTokenIntoDataSpec(
            dataSpec = dataSpec,
            forceLatestToken = forceLatestRepoToken,
            encodedManifestQuery = encodedManifestQuery
        )
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
