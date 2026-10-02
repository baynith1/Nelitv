package com.example.player

import android.content.Context
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

    const val TOKEN_PREFS_NAME = "neli_cdn_token_storage"
    const val KEY_STORED_TOKEN = "stored_cdn_token"
    const val KEY_STORED_CDN_HOST = "stored_cdn_host"
    const val KEY_STORED_SOURCE = "stored_cdn_source"
    const val KEY_STORED_EXPIRY = "stored_cdn_expiry"
    const val KEY_LAST_FETCH_TIME = "stored_last_fetch_time_ms"

    @Volatile
    private var appContext: Context? = null

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
     * Initializes TokenManager with application context and loads any locally stored token.
     */
    fun initialize(context: Context) {
        val app = context.applicationContext
        appContext = app
        loadStoredTokenLocally(app)
    }

    /**
     * Retrieves the epoch milliseconds of the last successful local or network token fetch.
     */
    fun getStoredLastFetchTimeMs(context: Context? = appContext): Long {
        return try {
            val targetContext = context ?: appContext ?: return 0L
            val prefs = targetContext.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getLong(KEY_LAST_FETCH_TIME, 0L)
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Loads the locally stored CDN token from SharedPreferences and applies it if non-expired.
     * If expired or missing, triggers a dynamic refresh in the background.
     */
    fun loadStoredTokenLocally(context: Context) {
        try {
            val prefs = context.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
            val savedToken = prefs.getString(KEY_STORED_TOKEN, "").orEmpty().trim()
            val savedHost = prefs.getString(KEY_STORED_CDN_HOST, "").orEmpty().trim()
            val savedSource = prefs.getString(KEY_STORED_SOURCE, "").orEmpty().trim()
            val savedExpiry = prefs.getLong(KEY_STORED_EXPIRY, 0L)

            if (savedToken.isNotBlank() && !isTokenExpired(savedToken, safetyMarginSec = 60L)) {
                ChannelRepository.applyDirectAuthorizationToken(
                    token = savedToken,
                    cdnHost = savedHost.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_HOST },
                    source = savedSource.ifBlank { "local_storage" },
                    expEpochSec = if (savedExpiry > 0L) savedExpiry else null
                )
                syncStateFromRepository()
            } else {
                prewarmScope.launch {
                    fetchLiveToken(forceRefresh = true)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Persists the active CDN token, CDN host, and expiration locally in SharedPreferences.
     */
    fun saveTokenLocally(
        context: Context? = appContext,
        token: String,
        cdnHost: String = currentCdnHost,
        source: String = currentSource,
        expEpochSec: Long? = null
    ) {
        val targetContext = context ?: appContext ?: return
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) return
        try {
            val resolvedExp = expEpochSec
                ?: ChannelRepository.extractJwtExpEpochSeconds(cleanToken)
                ?: ChannelRepository.AZAM_CDN_EXP
                ?: 0L
            val prefs = targetContext.getSharedPreferences(TOKEN_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_STORED_TOKEN, cleanToken)
                .putString(KEY_STORED_CDN_HOST, cdnHost.ifBlank { currentCdnHost })
                .putString(KEY_STORED_SOURCE, source.ifBlank { currentSource })
                .putLong(KEY_STORED_EXPIRY, resolvedExp)
                .putLong(KEY_LAST_FETCH_TIME, System.currentTimeMillis())
                .apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Checks if the active or specified token has expired or is about to expire within [safetyMarginSec]
     * (default 60 seconds). Dynamically checks nominal expiration and recent fetch window.
     */
    fun isTokenExpired(
        token: String = currentToken,
        safetyMarginSec: Long = 60L
    ): Boolean {
        if (token.isBlank()) return true
        val exp = ChannelRepository.extractJwtExpEpochSeconds(token)
            ?: ChannelRepository.AZAM_CDN_EXP
            ?: 0L
        val nowSec = System.currentTimeMillis() / 1000L
        if (exp > 0L) {
            if (exp > nowSec) {
                return (nowSec + safetyMarginSec) >= exp
            }
            if (token == currentToken) {
                val lastFetch = getStoredLastFetchTimeMs()
                val fifteenMinutesMs = 15 * 60 * 1000L
                if (lastFetch > 0L && (System.currentTimeMillis() - lastFetch) < fifteenMinutesMs) {
                    return false
                }
            }
            return true
        }
        val lastFetch = getStoredLastFetchTimeMs()
        val fifteenMinutesMs = 15 * 60 * 1000L
        if (lastFetch > 0L && (System.currentTimeMillis() - lastFetch) < fifteenMinutesMs) {
            return false
        }
        return true
    }

    /**
     * Dynamically fetches and refreshes the CDN token from `https://streamzone.fun/api/cdn-token`
     * before initializing the Media3 player or loading a channel stream.
     * Ensures the token is stored locally, checked for expiration, and replaced automatically when needed.
     */
    fun ensureValidToken(
        context: Context? = appContext,
        forceRefresh: Boolean = false
    ): String {
        val targetCtx = context ?: appContext
        if (targetCtx != null && appContext == null) {
            appContext = targetCtx.applicationContext
        }
        val isExpired = isTokenExpired()
        if (isExpired || forceRefresh) {
            val freshToken = fetchLiveTokenBlocking(
                forceRefresh = true,
                apiUrl = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL
            )
            targetCtx?.let {
                saveTokenLocally(it, freshToken)
            }
            return freshToken
        }
        return currentToken
    }

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
            .ifBlank { currentToken }

        // If the URL embeds its token inside the path (/tok_<JWT>/live/eds/...), update it with active token
        if (hasPathToken) {
            val tokInPath = basePart.substringAfter("/tok_").substringBefore("/").trim()
                .replace("%3D", "=", ignoreCase = true)
            val effectivePathToken = if (forceLatestToken || channelSpecificToken.isNotBlank()) {
                channelSpecificToken
            } else {
                tokInPath.ifBlank { channelSpecificToken }
            }
            val rewrittenBasePart = if (channelSpecificToken.isNotBlank()) {
                val beforeTok = basePart.substringBefore("/tok_")
                val afterTok = basePart.substringAfter("/tok_").substringAfter("/", "")
                if (afterTok.isNotEmpty()) "$beforeTok/tok_$channelSpecificToken/$afterTok" else basePart
            } else {
                basePart
            }
            val existingQuery = rawUriStr.substringAfter("?", "")
            val finalQuery = if (existingQuery.contains("cdntoken=", ignoreCase = true)) {
                existingQuery.split("&")
                    .map { if (it.startsWith("cdntoken=", ignoreCase = true)) "cdntoken=$channelSpecificToken" else it }
                    .joinToString("&")
            } else if (existingQuery.isNotEmpty()) {
                "$existingQuery&cdntoken=$channelSpecificToken"
            } else {
                "cdntoken=$channelSpecificToken"
            }
            val finalUrlStr = "$rewrittenBasePart?$finalQuery"
            val finalUri = Uri.parse(finalUrlStr)
            val extraHeaders = buildExoPlayerHeaders(
                streamUrl = finalUrlStr,
                tokenOverride = effectivePathToken
            )
            val specWithUri = if (finalUrlStr != rawUriStr) dataSpec.withUri(finalUri) else dataSpec
            return specWithUri.withAdditionalHeaders(extraHeaders)
        }

        // For channels using ?cdntoken=, ensure both manifest and relative .mp4/.m4s segments carry active token
        val effectiveToken = channelSpecificToken.ifBlank { currentToken }

        val isAzamOrTokenStream = basePart.contains("azamtvltd.co.tz", ignoreCase = true) ||
            basePart.contains("/live/eds/", ignoreCase = true) ||
            rawUriStr.contains("cdntoken=", ignoreCase = true)

        val finalUri = if (!isAzamOrTokenStream) {
            dataSpec.uri
        } else {
            val uriQuery = rawUriStr.substringAfter("?", "")
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
            appContext?.let { ctx ->
                saveTokenLocally(
                    context = ctx,
                    token = currentToken,
                    cdnHost = currentCdnHost,
                    source = currentSource,
                    expEpochSec = ChannelRepository.extractJwtExpEpochSeconds(currentToken)
                )
            }
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
        val minCooldownMs = if (forceRefresh) 2_000L else 15_000L
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
                    break
                }
            }
        }

        // Fetch backup channels API (`https://streamzone.fun/api/channels`) to update backup streams & clearkeys
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
