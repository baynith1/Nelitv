package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Robust manager for fetching, caching, injecting, and auto-refreshing live CDN authorization tokens
 * from `https://streamzone.fun/api/cdn-token` (with fallback to `https://streamzone.fun/api/channels`
 * and cloud config) for Media3 ExoPlayer playback.
 */
@OptIn(UnstableApi::class)
object TokenManager {

    const val DEFAULT_TOKEN_API_URL = ChannelRepository.DEFAULT_TOKEN_ENDPOINT_URL
    const val DEFAULT_CHANNELS_BACKUP_API_URL = ChannelRepository.DEFAULT_CHANNELS_BACKUP_API_URL
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    data class TokenSession(
        val token: String = ChannelRepository.AZAM_CDN_TOKEN,
        val cdnHost: String = ChannelRepository.AZAM_CDN_HOST,
        val source: String = ChannelRepository.AZAM_CDN_SOURCE,
        val effectiveExpiryEpochSec: Long = ChannelRepository.effectiveTokenExpiryEpochSec,
        val lastUpdatedEpochMs: Long = System.currentTimeMillis()
    )

    private val refreshMutex = Mutex()
    private val blockingLock = Any()

    @Volatile
    private var lastNetworkRefreshAttemptMs: Long = 0L

    private val _tokenSessionFlow = MutableStateFlow(
        TokenSession(
            token = ChannelRepository.AZAM_CDN_TOKEN,
            cdnHost = ChannelRepository.AZAM_CDN_HOST,
            source = ChannelRepository.AZAM_CDN_SOURCE,
            effectiveExpiryEpochSec = ChannelRepository.effectiveTokenExpiryEpochSec,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    )
    val tokenSessionFlow: StateFlow<TokenSession> = _tokenSessionFlow.asStateFlow()

    private val registeredHttpFactories = CopyOnWriteArrayList<DefaultHttpDataSource.Factory>()
    private val prewarmedManifestBytes = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, ByteArray>>()
    private val prewarmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private const val PREWARM_MANIFEST_TTL_MS = 25_000L

    private val _isChannelsPrewarmed = MutableStateFlow(false)
    val isChannelsPrewarmed: StateFlow<Boolean> = _isChannelsPrewarmed.asStateFlow()

    val currentToken: String
        get() = ChannelRepository.AZAM_CDN_TOKEN.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_TOKEN }

    val currentCdnHost: String
        get() = ChannelRepository.AZAM_CDN_HOST.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_HOST }

    val currentSource: String
        get() = ChannelRepository.AZAM_CDN_SOURCE

    val effectiveExpiryEpochSec: Long
        get() = ChannelRepository.effectiveTokenExpiryEpochSec

    /**
     * Builds the HTTP headers to inject into Media3 ExoPlayer requests, including the live
     * authorization token headers and standard streaming headers.
     */
    fun buildExoPlayerHeaders(
        streamUrl: String? = null,
        tokenOverride: String? = null
    ): Map<String, String> {
        val embeddedUrlToken = if (!streamUrl.isNullOrBlank()) {
            val base = streamUrl.substringBefore("?")
            val query = streamUrl.substringAfter("?", "")
            when {
                base.contains("/tok_", ignoreCase = true) ->
                    base.substringAfter("/tok_").substringBefore("/").trim().replace("%3D", "=", ignoreCase = true)
                query.contains("cdntoken=", ignoreCase = true) ->
                    query.split("&").firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
                        ?.substringAfter("=")?.trim()?.replace("%3D", "=", ignoreCase = true).orEmpty()
                else -> ""
            }
        } else {
            ""
        }
        val activeToken = tokenOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: embeddedUrlToken.takeIf { it.isNotEmpty() }
            ?: currentToken
        val headers = linkedMapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        )
        val isAzamOrTokenStream = streamUrl == null ||
            streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
            streamUrl.contains("/live/eds/", ignoreCase = true) ||
            streamUrl.contains("cdntoken=", ignoreCase = true) ||
            streamUrl.contains("streamzone.fun", ignoreCase = true)

        if (activeToken.isNotEmpty() && isAzamOrTokenStream) {
            headers["Authorization"] = "Bearer $activeToken"
            headers["X-CDN-Token"] = activeToken
            headers["cdntoken"] = activeToken
            headers["Origin"] = "https://streamzone.fun"
            headers["Referer"] = "https://streamzone.fun/"
        }
        return headers
    }

    /**
     * Registers and injects the live authorization token headers directly into a Media3
     * [DefaultHttpDataSource.Factory] so all outgoing ExoPlayer requests carry the latest token.
     */
    fun injectHeadersIntoFactory(
        factory: DefaultHttpDataSource.Factory,
        streamUrl: String? = null
    ): DefaultHttpDataSource.Factory {
        if (!registeredHttpFactories.contains(factory)) {
            registeredHttpFactories.add(factory)
        }
        val headers = buildExoPlayerHeaders(streamUrl = streamUrl)
        factory.setDefaultRequestProperties(headers)
        return factory
    }

    /**
     * Propagates the latest token headers to all registered Media3 [DefaultHttpDataSource.Factory] instances.
     */
    private fun updateRegisteredFactories() {
        val headers = buildExoPlayerHeaders()
        for (factory in registeredHttpFactories) {
            try {
                factory.setDefaultRequestProperties(headers)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Injects the active authorization token into a Media3 [DataSpec] while strictly preserving
     * each channel's own embedded `/tok_<JWT>/` path token or `?cdntoken=<JWT>` query token.
     */
    fun injectTokenIntoDataSpec(
        dataSpec: DataSpec,
        forceLatestToken: Boolean = false,
        encodedManifestQuery: String? = null
    ): DataSpec {
        val rawUriStr = dataSpec.uri.toString()
        val basePart = rawUriStr.substringBefore("?")
        val hasPathToken = basePart.contains("/tok_", ignoreCase = true)
        val channelSpecificToken = ChannelRepository.resolveChannelSpecificToken(basePart)

        // If the URL already embeds its token inside the path (/tok_<JWT>/live/eds/...), preserve it cleanly!
        if (hasPathToken) {
            val tokInPath = basePart.substringAfter("/tok_").substringBefore("/").trim()
                .replace("%3D", "=", ignoreCase = true)
            val rewrittenBasePart = if (forceLatestToken && channelSpecificToken.isNotBlank()) {
                val beforeTok = basePart.substringBefore("/tok_")
                val afterTok = basePart.substringAfter("/tok_").substringAfter("/", "")
                if (afterTok.isNotEmpty()) "$beforeTok/tok_$channelSpecificToken/$afterTok" else basePart
            } else {
                basePart
            }
            val existingQuery = rawUriStr.substringAfter("?", "")
            val finalUrlStr = if (existingQuery.isNotEmpty()) "$rewrittenBasePart?$existingQuery" else rewrittenBasePart
            val finalUri = Uri.parse(finalUrlStr)
            val extraHeaders = buildExoPlayerHeaders(
                streamUrl = finalUrlStr,
                tokenOverride = tokInPath.ifBlank { channelSpecificToken }
            )
            val specWithUri = if (finalUrlStr != rawUriStr) dataSpec.withUri(finalUri) else dataSpec
            return specWithUri.withAdditionalHeaders(extraHeaders)
        }

        // Otherwise, for channels using ?cdntoken= (such as ZBC2, ZBC, KIX, Crown Tv, Wasafi Tv),
        // ensure both the manifest and every relative .mp4/.m4s segment carry that channel's exact cdntoken!
        val manifestTokenParam = encodedManifestQuery
            ?.split("&")
            ?.firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()

        val uriQuery = rawUriStr.substringAfter("?", "")
        val uriTokenParam = uriQuery
            .split("&")
            .firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()

        val effectiveToken = when {
            uriTokenParam.isNotEmpty() && !forceLatestToken -> uriTokenParam
            manifestTokenParam.isNotEmpty() && !forceLatestToken -> manifestTokenParam
            channelSpecificToken.isNotEmpty() -> channelSpecificToken
            else -> currentToken
        }

        val isAzamOrTokenStream = basePart.contains("azamtvltd.co.tz", ignoreCase = true) ||
            basePart.contains("/live/eds/", ignoreCase = true) ||
            uriTokenParam.isNotEmpty() ||
            manifestTokenParam.isNotEmpty()

        val finalUri = if (!isAzamOrTokenStream) {
            dataSpec.uri
        } else {
            val otherParams = uriQuery
                .split("&")
                .filter { it.isNotBlank() && !it.startsWith("cdntoken=", ignoreCase = true) }
            val mergedQuery = (otherParams + "cdntoken=$effectiveToken").joinToString("&")
            Uri.parse("$basePart?$mergedQuery")
        }

        val finalUriStr = finalUri.toString()
        val extraHeaders = buildExoPlayerHeaders(
            streamUrl = finalUriStr,
            tokenOverride = effectiveToken.replace("%3D", "=", ignoreCase = true)
        )
        val specWithUri = if (finalUriStr != rawUriStr) dataSpec.withUri(finalUri) else dataSpec
        return specWithUri.withAdditionalHeaders(extraHeaders)
    }

    /**
     * Returns cached `.mpd` manifest bytes if recently pre-warmed within [PREWARM_MANIFEST_TTL_MS].
     */
    fun getPrewarmedManifestBytes(url: String): ByteArray? {
        val key = url.substringBefore("?").lowercase()
        val entry = prewarmedManifestBytes[key] ?: return null
        if (System.currentTimeMillis() - entry.first <= PREWARM_MANIFEST_TTL_MS) {
            return entry.second
        }
        prewarmedManifestBytes.remove(key)
        return null
    }

    fun cachePrewarmedManifestBytes(url: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val key = url.substringBefore("?").lowercase()
        prewarmedManifestBytes[key] = System.currentTimeMillis() to bytes
    }

    /**
     * Automatically pre-warms all live channels in the background when the user enters the app
     * so that DNS, TLS keep-alive sockets, and DASH manifests are ready for instant playback.
     */
    fun prewarmAllChannelsInBackground(channels: List<com.example.model.LiveChannel> = ChannelRepository.liveChannelsFlow.value) {
        _isChannelsPrewarmed.value = true
        updateRegisteredFactories()
        prewarmScope.launch {
            val priorityChannels = channels.take(18)
            for (ch in priorityChannels) {
                try {
                    val playableUrl = ChannelRepository.resolvePlayableAzamManifestUrl(ch.streamUrl)
                    if (playableUrl.contains(".mpd", ignoreCase = true)) {
                        val rawManifest = executeHttpGetBytes(playableUrl, timeoutMs = 1_800)
                        if (rawManifest != null && rawManifest.isNotEmpty()) {
                            cachePrewarmedManifestBytes(playableUrl, rawManifest)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun executeHttpGetBytes(urlStr: String, timeoutMs: Int = 2_000): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            val headers = buildExoPlayerHeaders(streamUrl = urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                useCaches = true
                setRequestProperty("User-Agent", USER_AGENT)
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Parses a JSON or raw JWT token payload, updates [ChannelRepository], refreshes [tokenSessionFlow],
     * and re-injects the updated token into all registered Media3 ExoPlayer HTTP factories.
     */
    fun parseAndApplyTokenPayload(
        payload: String,
        isAuthoritative: Boolean = true
    ): Boolean {
        val updated = ChannelRepository.updateCdnAuthorizationToken(
            jsonStr = payload,
            isAuthoritativeTokenApi = isAuthoritative
        )
        if (updated) {
            syncStateFromRepository()
            updateRegisteredFactories()
        }
        return updated
    }

    /**
     * Synchronizes [tokenSessionFlow] with the current state in [ChannelRepository] and updates
     * all registered Media3 HTTP data source factories.
     */
    fun syncStateFromRepository() {
        _tokenSessionFlow.value = TokenSession(
            token = currentToken,
            cdnHost = currentCdnHost,
            source = currentSource,
            effectiveExpiryEpochSec = effectiveExpiryEpochSec,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
        updateRegisteredFactories()
    }

    /**
     * Fetches the live authorization token from `https://streamzone.fun/api/cdn-token`
     * asynchronously, updates [ChannelRepository], and injects the fresh token into Media3 ExoPlayer headers.
     */
    suspend fun fetchLiveToken(
        forceRefresh: Boolean = false,
        apiUrl: String = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL
    ): String = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            fetchLiveTokenBlocking(forceRefresh = forceRefresh, apiUrl = apiUrl)
        }
    }

    /**
     * Blocking variant of [fetchLiveToken] safe to call from Media3 ExoPlayer I/O loader threads.
     */
    fun fetchLiveTokenBlocking(
        forceRefresh: Boolean = false,
        apiUrl: String = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL
    ): String = synchronized(blockingLock) {
        val now = System.currentTimeMillis()
        val minCooldownMs = if (forceRefresh) 10_000L else 30_000L
        if (lastNetworkRefreshAttemptMs > 0L && (now - lastNetworkRefreshAttemptMs) < minCooldownMs) {
            return currentToken
        }
        lastNetworkRefreshAttemptMs = now

        val targetUrls = listOf(
            apiUrl.ifBlank { DEFAULT_TOKEN_API_URL },
            DEFAULT_TOKEN_API_URL
        ).distinct()

        for (endpoint in targetUrls) {
            val responseText = executeHttpGet(endpoint)
            if (!responseText.isNullOrBlank()) {
                if (parseAndApplyTokenPayload(responseText, isAuthoritative = true)) {
                    return currentToken
                }
            }
        }

        // Fallback: check backup channels API (`https://streamzone.fun/api/channels`) which may also embed fresh cdntokens
        if (forceRefresh || ChannelRepository.getCachedBackupApiChannels().isEmpty()) {
            val backupJson = executeHttpGet(ChannelRepository.CHANNELS_BACKUP_API_URL)
            if (!backupJson.isNullOrBlank()) {
                val parsed = ChannelRepository.parseBackupChannelsApiPayload(backupJson)
                if (parsed.isNotEmpty()) {
                    syncStateFromRepository()
                }
            }
        }

        syncStateFromRepository()
        return currentToken
    }

    /**
     * Handles stream authentication failures (e.g. HTTP 401/403 or DRM/token rejection) by automatically
     * refreshing the live token from the API and updating Media3 ExoPlayer headers and channel URLs.
     */
    suspend fun handleAuthenticationFailure(
        failedUrl: String? = null,
        httpStatusCode: Int? = null
    ): String = withContext(Dispatchers.IO) {
        handleAuthenticationFailureBlocking(failedUrl = failedUrl, httpStatusCode = httpStatusCode)
    }

    /**
     * Blocking handler for stream authentication failures invoked directly on ExoPlayer error/retry paths.
     */
    fun handleAuthenticationFailureBlocking(
        failedUrl: String? = null,
        httpStatusCode: Int? = null
    ): String = synchronized(blockingLock) {
        val now = System.currentTimeMillis()
        if (lastNetworkRefreshAttemptMs > 0L && (now - lastNetworkRefreshAttemptMs) < 10_000L) {
            return currentToken
        }
        // 1. Fetch fresh token from the primary token API endpoint
        fetchLiveTokenBlocking(forceRefresh = true)

        // 2. Re-normalize all live channel URLs and update registered ExoPlayer HTTP factories
        ChannelRepository.refreshLiveChannels()
        syncStateFromRepository()
        return currentToken
    }

    /**
     * Returns true if the given HTTP status code or error message indicates a stream authentication failure
     * that should trigger an automatic token refresh.
     */
    fun isAuthenticationFailure(httpStatusCode: Int?, errorMessage: String? = null): Boolean {
        if (httpStatusCode == 401 || httpStatusCode == 403) return true
        val msg = errorMessage?.lowercase().orEmpty()
        return msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("unauthorized") ||
            msg.contains("forbidden") ||
            msg.contains("authorization") ||
            msg.contains("token")
    }

    private fun executeHttpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 2_500
                readTimeout = 2_500
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Cache-Control", "no-cache")
            }
            val code = connection.responseCode
            if (code in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sb.append(line).append('\n')
                    }
                    sb.toString().trim()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
