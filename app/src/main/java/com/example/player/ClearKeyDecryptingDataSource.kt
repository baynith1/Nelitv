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

    private var currentUri: Uri? = null
    private var transformedData: ByteArray? = null
    private var readPosition: Int = 0
    private var bytesRemaining: Int = 0
    private var passthroughMode: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)

        var effectiveDataSpec = resolveDataSpecToken(dataSpec, forceLatestRepoToken = false)
        val uriString = effectiveDataSpec.uri.toString()
        val pathPart = uriString.substringBefore("?").lowercase()

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

        // If this is a DASH .mpd manifest and we have a freshly pre-warmed manifest in memory, serve it immediately!
        val cachedMpdBytes = if (isMpd) TokenManager.getPrewarmedManifestBytes(uriString) else null
        val rawBytes = if (cachedMpdBytes != null && cachedMpdBytes.isNotEmpty()) {
            currentUri = effectiveDataSpec.uri
            cachedMpdBytes
        } else {
            // Fetch full resource from upstream so we can parse/decrypt complete MP4 boxes or MPD XML
            val fullFetchSpec = effectiveDataSpec.buildUpon()
                .setPosition(0)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()

            val (openedSpec, _) = openUpstreamWithTokenFailover(dataSpec, fullFetchSpec)
            currentUri = upstream.uri ?: openedSpec.uri

            val fetched = readAllUpstreamBytes()
            upstream.close()
            if (isMpd && fetched.isNotEmpty()) {
                TokenManager.cachePrewarmedManifestBytes(uriString, fetched)
            }
            fetched
        }

        val processedBytes = if (isMpd) {
            val xml = String(rawBytes, Charsets.UTF_8)
                .replace("http://cdnblncr.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST, ignoreCase = true)
                .replace("https://cdnblncr.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST, ignoreCase = true)
            val stripped = decryptor?.stripMpdContentProtection(xml)
                ?: Mp4CencDecryptor(emptyMap()).stripMpdContentProtection(xml)
            stripped.toByteArray(Charsets.UTF_8)
        } else {
            decryptor?.processMp4Segment(rawBytes) ?: rawBytes
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

    private fun openUpstreamWithTokenFailover(
        originalSpec: DataSpec,
        attemptSpec: DataSpec
    ): Pair<DataSpec, Long> {
        return try {
            val len = upstream.open(attemptSpec)
            attemptSpec to len
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            val attemptUriStr = attemptSpec.uri.toString()
            val isAzamStream = attemptUriStr.contains("azamtvltd.co.tz", ignoreCase = true) ||
                attemptUriStr.contains("/live/eds/", ignoreCase = true)

            if (isAzamStream) {
                val base = attemptUriStr.substringBefore("?")
                val query = attemptUriStr.substringAfter("?", "")
                val alternateUris = mutableListOf<String>()

                val channelToken = com.example.data.ChannelRepository.resolveChannelSpecificToken(base)
                    .ifBlank { TokenManager.currentToken }

                if (base.contains("/tok_", ignoreCase = true)) {
                    // 1. Pure /tok_<JWT>/live/eds/... without query string
                    if (query.isNotEmpty()) {
                        alternateUris.add(base)
                    }
                    // 2. Strip /tok_<JWT> from path and use ?cdntoken=<JWT>
                    val beforeTok = base.substringBefore("/tok_")
                    val tokInPath = base.substringAfter("/tok_").substringBefore("/")
                    val afterTok = base.substringAfter("/tok_").substringAfter("/", "")
                    if (afterTok.isNotEmpty()) {
                        val tokenToUse = tokInPath.ifBlank { channelToken }
                        alternateUris.add("$beforeTok/$afterTok?cdntoken=$tokenToUse")
                    }
                } else if (base.contains("/live/eds/", ignoreCase = true)) {
                    // Inject /tok_<JWT>/ into path before /live/eds/
                    val edsIdx = base.indexOf("/live/eds/", ignoreCase = true)
                    val hostPrefix = base.substring(0, edsIdx)
                    val pathAfterHost = base.substring(edsIdx)
                    alternateUris.add("$hostPrefix/tok_$channelToken$pathAfterHost")
                    alternateUris.add("$hostPrefix/tok_$channelToken$pathAfterHost?cdntoken=$channelToken")
                }

                for (altUrl in alternateUris) {
                    try {
                        upstream.close()
                    } catch (_: Exception) {
                    }
                    try {
                        val altSpec = attemptSpec.buildUpon()
                            .setUri(Uri.parse(altUrl))
                            .setPosition(attemptSpec.position)
                            .setLength(attemptSpec.length)
                            .build()
                        val altLen = upstream.open(altSpec)
                        return altSpec to altLen
                    } catch (_: Exception) {
                    }
                }
            }

            if (TokenManager.isAuthenticationFailure(e.responseCode, e.message)) {
                try {
                    upstream.close()
                } catch (_: Exception) {
                }
                TokenManager.handleAuthenticationFailureBlocking(
                    failedUrl = attemptSpec.uri.toString(),
                    httpStatusCode = e.responseCode
                )
                val refreshedBaseSpec = resolveDataSpecToken(originalSpec, forceLatestRepoToken = true)
                val retrySpec = refreshedBaseSpec.buildUpon()
                    .setPosition(attemptSpec.position)
                    .setLength(attemptSpec.length)
                    .build()
                val retryLen = upstream.open(retrySpec)
                retrySpec to retryLen
            } else {
                throw e
            }
        }
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
