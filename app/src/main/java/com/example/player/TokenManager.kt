package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.model.LiveChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
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
        val provider: String = ChannelRepository.AZAM_CDN_PROVIDER,
        val effectiveExpiryEpochSec: Long = ChannelRepository.effectiveTokenExpiryEpochSec,
        val lastUpdatedEpochMs: Long = System.currentTimeMillis()
    )

    private val refreshMutex = Mutex()
    private val blockingLock = Any()

    private const val EXCLUDED_PROVIDER_TTL_MS = 120_000L

    @Volatile
    private var lastNetworkRefreshAttemptMs: Long = 0L

    @Volatile
    private var lastAuthFailureRecoveryMs: Long = 0L

    @Volatile
    private var excludedProviderName: String? = null

    @Volatile
    private var excludedProviderUntilMs: Long = 0L

    private val _tokenSessionFlow = MutableStateFlow(
        TokenSession(
            token = ChannelRepository.AZAM_CDN_TOKEN,
            cdnHost = ChannelRepository.AZAM_CDN_HOST,
            source = ChannelRepository.AZAM_CDN_SOURCE,
            provider = ChannelRepository.AZAM_CDN_PROVIDER,
            effectiveExpiryEpochSec = ChannelRepository.effectiveTokenExpiryEpochSec,
            lastUpdatedEpochMs = System.currentTimeMillis()
        )
    )
    val tokenSessionFlow: StateFlow<TokenSession> = _tokenSessionFlow.asStateFlow()

    private val registeredHttpFactories = CopyOnWriteArrayList<DefaultHttpDataSource.Factory>()

    val currentToken: String
        get() = ChannelRepository.AZAM_CDN_TOKEN.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_TOKEN }

    val currentCdnHost: String
        get() = ChannelRepository.AZAM_CDN_HOST.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_HOST }

    val currentSource: String
        get() = ChannelRepository.AZAM_CDN_SOURCE

    val currentProvider: String
        get() = ChannelRepository.AZAM_CDN_PROVIDER.ifBlank { ChannelRepository.DEFAULT_AZAM_CDN_PROVIDER }

    val effectiveExpiryEpochSec: Long
        get() = ChannelRepository.effectiveTokenExpiryEpochSec

    /**
     * Returns the currently excluded Azam token provider (e.g. `"aztv"` or `"mpingo"`)
     * if it recently failed with HTTP 429/401/403 within [EXCLUDED_PROVIDER_TTL_MS].
     */
    fun getActiveExcludedProvider(nowMs: Long = System.currentTimeMillis()): String? {
        val candidate = excludedProviderName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        if (nowMs >= excludedProviderUntilMs) {
            excludedProviderName = null
            excludedProviderUntilMs = 0L
            return null
        }
        return candidate
    }

    /**
     * Marks a token provider (`"aztv"` or `"mpingo"`) as temporarily unhealthy (e.g. HTTP 429 Too Many IPs)
     * so subsequent fetches pass `excludeProvider=<provider>&fresh=1` and use the alternate provider.
     */
    fun markProviderFailed(
        provider: String?,
        httpStatusCode: Int? = null,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val normalized = provider?.trim()?.lowercase().orEmpty()
        if (normalized == "aztv" || normalized == "mpingo") {
            excludedProviderName = normalized
            val ttl = if (httpStatusCode == 429) EXCLUDED_PROVIDER_TTL_MS else 60_000L
            excludedProviderUntilMs = nowMs + ttl
        }
    }

    fun clearExcludedProvider() {
        excludedProviderName = null
        excludedProviderUntilMs = 0L
    }

    /**
     * Builds the StreamZone CDN token API URL with cache-busting `?t=<ms>`, optional `fresh=1`,
     * and optional `excludeProvider=<aztv|mpingo>` matching the live web player's failover protocol.
     */
    fun buildTokenApiUrl(
        baseUrl: String = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL,
        forceRefresh: Boolean = false,
        excludeProvider: String? = getActiveExcludedProvider(),
        nowMs: Long = System.currentTimeMillis()
    ): String {
        val cleanBase = baseUrl.trim().ifBlank { DEFAULT_TOKEN_API_URL }
        if (!cleanBase.contains("/api/cdn-token", ignoreCase = true)) {
            return cleanBase
        }
        val baseWithoutQuery = cleanBase.substringBefore("?")
        val params = mutableListOf("t=$nowMs")
        val cleanExclude = excludeProvider?.trim()?.lowercase().orEmpty()
        if (cleanExclude == "aztv" || cleanExclude == "mpingo") {
            params.add("excludeProvider=$cleanExclude")
        }
        if (forceRefresh) {
            params.add("fresh=1")
        }
        return "$baseWithoutQuery?${params.joinToString("&")}"
    }

    /**
     * Returns true ONLY for Azam TV / StreamZone CDN stream URLs that require
     * Azam JWT authorization headers and `?cdntoken=` parameters.
     * Standard third-party HLS CDNs (CloudFront, Akamai, Brightcove, Wurl, Amagi, Wowza, Castr, AynaOTT)
     * MUST NOT receive unrelated Authorization/Origin/Referer headers or they reject requests with HTTP 400/403.
     */
    fun isAzamOrStreamzoneUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return false
        return clean.contains("azamtvltd.co.tz", ignoreCase = true) ||
            clean.contains("/live/eds/", ignoreCase = true) ||
            clean.contains("cdntoken=", ignoreCase = true) ||
            clean.contains("streamzone.fun", ignoreCase = true)
    }

    /**
     * Builds the HTTP headers to inject into Media3 ExoPlayer requests.
     * Strictly scopes Azam Authorization/Origin/Referer headers to Azam/StreamZone URLs only
     * so third-party M3U8 HLS CDNs never reject playback with HTTP 400/403.
     */
    fun buildExoPlayerHeaders(
        streamUrl: String? = null,
        tokenOverride: String? = null
    ): Map<String, String> {
        val activeToken = tokenOverride?.trim()?.takeIf { it.isNotEmpty() } ?: currentToken
        val headers = linkedMapOf(
            "Accept" to "*/*",
            "Connection" to "keep-alive",
            "User-Agent" to USER_AGENT
        )
        val url = streamUrl.orEmpty()
        when {
            url.contains("193.47.62.") -> {
                headers["Referer"] = "http://www.fawanews.sc/"
                headers["Origin"] = "http://www.fawanews.sc"
            }
            url.contains("obstreamx.click", ignoreCase = true) -> {
                headers["Referer"] = "https://getembed.live/"
                headers["Origin"] = "https://getembed.live"
            }
            url.contains("azplay", ignoreCase = true) -> {
                headers["Referer"] = "https://getembed.live/"
                headers["Origin"] = "https://getembed.live"
            }
            isAzamOrStreamzoneUrl(url) -> {
                if (activeToken.isNotEmpty()) {
                    headers["Authorization"] = "Bearer $activeToken"
                    headers["X-CDN-Token"] = activeToken
                    headers["cdntoken"] = activeToken
                    headers["Origin"] = "https://streamzone.fun"
                    headers["Referer"] = "https://streamzone.fun/"
                }
            }
        }
        return headers
    }

    /**
     * Injects stream-appropriate HTTP headers into a Media3 [DefaultHttpDataSource.Factory].
     * Only Azam/StreamZone factories are registered for automatic live token header rotation so
     * third-party HLS factories are never polluted with Azam Authorization headers.
     */
    fun injectHeadersIntoFactory(
        factory: DefaultHttpDataSource.Factory,
        streamUrl: String? = null
    ): DefaultHttpDataSource.Factory {
        if (isAzamOrStreamzoneUrl(streamUrl)) {
            if (!registeredHttpFactories.contains(factory)) {
                if (registeredHttpFactories.size >= 24) {
                    registeredHttpFactories.removeAt(0)
                }
                registeredHttpFactories.add(factory)
            }
        } else {
            registeredHttpFactories.remove(factory)
        }
        val headers = buildExoPlayerHeaders(streamUrl = streamUrl)
        factory.setDefaultRequestProperties(headers)
        return factory
    }

    /**
     * Propagates the latest Azam token headers strictly to registered Azam Media3 [DefaultHttpDataSource.Factory] instances.
     */
    private fun updateRegisteredFactories() {
        val headers = buildExoPlayerHeaders(streamUrl = currentCdnHost)
        for (factory in registeredHttpFactories) {
            try {
                factory.setDefaultRequestProperties(headers)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Injects the active authorization token into a Media3 [DataSpec] both via HTTP request headers
     * and via the `?cdntoken=` query parameter for Azam CDN DASH/MP4 requests.
     */
    fun injectTokenIntoDataSpec(
        dataSpec: DataSpec,
        forceLatestToken: Boolean = false,
        encodedManifestQuery: String? = null
    ): DataSpec {
        val rawUriStr = dataSpec.uri.toString()
        val basePart = rawUriStr.substringBefore("?")
        val existingPathTok = if (basePart.contains("/tok_", ignoreCase = true)) {
            basePart.substringAfter("/tok_").substringBefore("/").trim()
        } else {
            ""
        }
        val shouldRewritePathTok = basePart.contains("/tok_", ignoreCase = true) && (
            forceLatestToken || ChannelRepository.isTokenExpiredOrOlderThanActive(existingPathTok)
        )
        val rewrittenBasePart = if (shouldRewritePathTok) {
            val beforeTok = basePart.substringBefore("/tok_")
            val afterTok = basePart.substringAfter("/tok_").substringAfter("/", "")
            if (afterTok.isNotEmpty()) "$beforeTok/tok_$currentToken/$afterTok" else basePart
        } else {
            basePart
        }
        val inputUrl = if (forceLatestToken && rawUriStr.contains("?")) {
            val otherParams = rawUriStr.substringAfter("?")
                .split("&")
                .filter { it.isNotBlank() && !it.startsWith("cdntoken=", ignoreCase = true) }
            if (otherParams.isEmpty()) rewrittenBasePart else "$rewrittenBasePart?${otherParams.joinToString("&")}"
        } else if (rewrittenBasePart != basePart) {
            val q = rawUriStr.substringAfter("?", "")
            if (q.isEmpty()) rewrittenBasePart else "$rewrittenBasePart?$q"
        } else {
            rawUriStr
        }

        val normalizedStr = ChannelRepository.normalizeDashStreamUrl(
            rawUrl = inputUrl,
            forceLatestLiveToken = forceLatestToken
        )
        val normalizedUri = Uri.parse(normalizedStr)

        val finalUri = if (normalizedUri.toString().contains("cdntoken=", ignoreCase = true)) {
            normalizedUri
        } else {
            val fallbackQuery = if (
                normalizedStr.contains("azamtvltd.co.tz", ignoreCase = true) ||
                normalizedStr.contains("/live/eds/", ignoreCase = true) ||
                encodedManifestQuery?.contains("cdntoken=", ignoreCase = true) == true
            ) {
                "cdntoken=$currentToken"
            } else {
                encodedManifestQuery?.takeIf { it.isNotBlank() }
            }

            if (fallbackQuery.isNullOrBlank()) {
                normalizedUri
            } else {
                val currentEncodedQuery = normalizedUri.encodedQuery
                val mergedEncodedQuery = if (currentEncodedQuery.isNullOrBlank()) {
                    fallbackQuery
                } else {
                    "$currentEncodedQuery&$fallbackQuery"
                }
                normalizedUri.buildUpon()
                    .encodedQuery(mergedEncodedQuery)
                    .build()
            }
        }

        val finalUriStr = finalUri.toString()
        val extraHeaders = buildExoPlayerHeaders(streamUrl = finalUriStr)
        val specWithUri = if (finalUriStr != rawUriStr) dataSpec.withUri(finalUri) else dataSpec
        return specWithUri.withAdditionalHeaders(extraHeaders)
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

    private val prewarmedManifestCache = ConcurrentHashMap<String, ByteArray>()

    fun getPrewarmedManifestBytes(uriStr: String): ByteArray? {
        val cleanUri = uriStr.substringBefore("?")
        val cachedBytes: ByteArray? = prewarmedManifestCache[cleanUri] ?: prewarmedManifestCache[uriStr]
        return cachedBytes
    }

    fun cachePrewarmedManifestBytes(uriStr: String, bytes: ByteArray) {
        val cleanUri = uriStr.substringBefore("?")
        prewarmedManifestCache[cleanUri] = bytes
    }

    fun ensureValidToken(): String {
        return fetchLiveTokenBlocking(false)
    }

    fun prewarmAllChannelsInBackground(channels: List<LiveChannel>) {
        // Pre-warming warm-up helper
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
            provider = currentProvider,
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
        apiUrl: String = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL,
        excludeProvider: String? = getActiveExcludedProvider()
    ): String = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            fetchLiveTokenBlocking(
                forceRefresh = forceRefresh,
                apiUrl = apiUrl,
                excludeProvider = excludeProvider
            )
        }
    }

    /**
     * Blocking variant of [fetchLiveToken] safe to call from Media3 ExoPlayer I/O loader threads.
     * Supports StreamZone's multi-provider failover (`aztv` <-> `mpingo`) via `excludeProvider` and `fresh=1`.
     */
    fun fetchLiveTokenBlocking(
        forceRefresh: Boolean = false,
        apiUrl: String = ChannelRepository.AZAM_TOKEN_ENDPOINT_URL,
        bypassCooldown: Boolean = false,
        excludeProvider: String? = getActiveExcludedProvider()
    ): String = synchronized(blockingLock) {
        val now = System.currentTimeMillis()
        val minCooldownMs = if (forceRefresh) 1_500L else 15_000L
        if (!bypassCooldown && lastNetworkRefreshAttemptMs > 0L && (now - lastNetworkRefreshAttemptMs) < minCooldownMs) {
            return currentToken
        }
        lastNetworkRefreshAttemptMs = now

        val effectiveBaseUrl = apiUrl.ifBlank { DEFAULT_TOKEN_API_URL }
        val cleanExclude = excludeProvider?.trim()?.lowercase()?.takeIf { it == "aztv" || it == "mpingo" }

        val targetUrls = buildList {
            if (cleanExclude != null) {
                // First request the alternate provider with fresh=1 (e.g. aztv failed 429 -> fetch mpingo)
                add(buildTokenApiUrl(effectiveBaseUrl, forceRefresh = true, excludeProvider = cleanExclude, nowMs = now))
            }
            // Standard (or fresh) primary token request with cache-buster
            add(buildTokenApiUrl(effectiveBaseUrl, forceRefresh = forceRefresh, excludeProvider = null, nowMs = now + 1L))
            if (effectiveBaseUrl != DEFAULT_TOKEN_API_URL) {
                add(buildTokenApiUrl(DEFAULT_TOKEN_API_URL, forceRefresh = forceRefresh, excludeProvider = cleanExclude, nowMs = now + 2L))
            }
            add("https://neliplay-default-rtdb.firebaseio.com/config/azam_token.json")
            add("https://neliplay-default-rtdb.firebaseio.com/azam_token.json")
            add("https://neliplay-default-rtdb.firebaseio.com/cdn_token.json")
        }.distinct()

        for (endpoint in targetUrls) {
            val responseText = executeHttpGet(endpoint)
            if (!responseText.isNullOrBlank()) {
                // If this endpoint explicitly asked to exclude a provider, reject payload if backend still echoed that provider
                if (cleanExclude != null && endpoint.contains("excludeProvider=$cleanExclude", ignoreCase = true)) {
                    val returnedProvider = extractProviderFromPayload(responseText)
                    if (returnedProvider == cleanExclude) {
                        continue
                    }
                }
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

    private fun extractProviderFromPayload(payload: String): String? {
        return try {
            val trimmed = payload.trim()
            if (!trimmed.startsWith("{")) return null
            val root = org.json.JSONObject(trimmed)
            val data = root.optJSONObject("data") ?: root
            data.optString("provider", root.optString("provider", ""))
                .trim()
                .lowercase()
                .takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Handles stream authentication or CDN rate-limit failures (HTTP 401/403/410/429 or DRM/token rejection)
     * by switching Azam token providers (`aztv` <-> `mpingo`) and updating Media3 ExoPlayer headers and URLs.
     */
    suspend fun handleAuthenticationFailure(
        failedUrl: String? = null,
        httpStatusCode: Int? = null
    ): String = withContext(Dispatchers.IO) {
        handleAuthenticationFailureBlocking(failedUrl = failedUrl, httpStatusCode = httpStatusCode)
    }

    /**
     * Blocking handler for stream authentication/rate-limit failures invoked directly on ExoPlayer error/retry paths.
     */
    fun handleAuthenticationFailureBlocking(
        failedUrl: String? = null,
        httpStatusCode: Int? = null
    ): String = synchronized(blockingLock) {
        val shouldSwitch = httpStatusCode == 429 || httpStatusCode == 401 || httpStatusCode == 403
        val now = System.currentTimeMillis()
        if (!shouldSwitch && lastAuthFailureRecoveryMs > 0L && (now - lastAuthFailureRecoveryMs) < 1_000L) {
            return currentToken
        }
        lastAuthFailureRecoveryMs = now
        val providerToExclude = if (shouldSwitch) {
            val failedProv = currentProvider
            markProviderFailed(failedProv, httpStatusCode, now)
            failedProv
        } else {
            getActiveExcludedProvider(now)
        }
        // 1. Fetch fresh token immediately bypassing cooldown (switching provider when rate-limited/rejected)
        fetchLiveTokenBlocking(
            forceRefresh = true,
            bypassCooldown = true,
            excludeProvider = providerToExclude
        )

        // 2. Re-normalize all live channel URLs and update registered ExoPlayer HTTP factories
        ChannelRepository.refreshLiveChannels()
        syncStateFromRepository()
        return currentToken
    }

    /**
     * Full automatic CDN key & token renewal routine:
     * 1. Immediately fetches fresh CDN tokens from live token API (`streamzone.fun/api/cdn-token`) & Firebase RTDB.
     *    When [switchProvider] is true (or on HTTP 429/401/403), automatically fails over between `aztv` and `mpingo`.
     * 2. Fetches fresh channel definitions & ClearKeys from backup channels API (`streamzone.fun/api/channels`).
     * 3. Re-normalizes all live channel URLs, updates registered ExoPlayer HTTP factories, and returns the
     *    freshest updated [LiveChannel] matching [failedChannel] (if provided) so playback heals automatically.
     */
    suspend fun renewCdnKeysAndChannels(
        failedChannel: LiveChannel? = null,
        httpStatusCode: Int? = null,
        switchProvider: Boolean = false
    ): LiveChannel? = withContext(Dispatchers.IO) {
        renewCdnKeysAndChannelsBlocking(
            failedChannel = failedChannel,
            httpStatusCode = httpStatusCode,
            switchProvider = switchProvider
        )
    }

    fun renewCdnKeysAndChannelsBlocking(
        failedChannel: LiveChannel? = null,
        httpStatusCode: Int? = null,
        switchProvider: Boolean = false
    ): LiveChannel? = synchronized(blockingLock) {
        val now = System.currentTimeMillis()
        val isRateLimitOrAuthFail = httpStatusCode == 429 || httpStatusCode == 401 || httpStatusCode == 403
        val shouldSwitchProvider = switchProvider || isRateLimitOrAuthFail
        val canHitNetwork = shouldSwitchProvider || lastAuthFailureRecoveryMs == 0L || (now - lastAuthFailureRecoveryMs) >= 1_000L
        if (canHitNetwork) {
            lastAuthFailureRecoveryMs = now
            val providerToExclude = if (shouldSwitchProvider) {
                val activeProv = currentProvider
                markProviderFailed(activeProv, httpStatusCode, now)
                activeProv
            } else {
                getActiveExcludedProvider(now)
            }

            // 1. Force fresh CDN authorization token from primary/fallback provider + RTDB
            fetchLiveTokenBlocking(
                forceRefresh = true,
                bypassCooldown = true,
                excludeProvider = providerToExclude
            )

            // 2. Also refresh backup channels API so rotated ClearKeys & updated CDN hosts/manifests apply immediately
            try {
                val backupJson = executeHttpGet(ChannelRepository.CHANNELS_BACKUP_API_URL)
                if (!backupJson.isNullOrBlank()) {
                    ChannelRepository.parseBackupChannelsApiPayload(backupJson)
                }
            } catch (_: Exception) {
            }

            // 3. Re-normalize all live channels with the newly renewed CDN token and ClearKeys
            ChannelRepository.refreshLiveChannels()
            syncStateFromRepository()
        }

        if (failedChannel == null) return null

        val refreshedFromRepo = ChannelRepository.getChannelById(failedChannel.id)
        val backupMatch = ChannelRepository.findBackupChannelFor(failedChannel)
        val mergedClearKeys = ChannelRepository.resolveClearKeysForChannel(
            failedChannel.copy(
                clearKeys = (backupMatch?.clearKeys ?: emptyMap()) +
                    (refreshedFromRepo?.clearKeys ?: emptyMap()) +
                    failedChannel.clearKeys
            )
        )
        val freshPrimary = ChannelRepository.resolvePlayableAzamManifestUrl(
            refreshedFromRepo?.streamUrl?.ifBlank { failedChannel.streamUrl } ?: failedChannel.streamUrl
        )
        val freshBackup = (
            refreshedFromRepo?.backupStreamUrl?.ifBlank { null }
                ?: failedChannel.backupStreamUrl.ifBlank { null }
                ?: backupMatch?.streamUrl?.ifBlank { null }
                ?: ""
            ).let { raw ->
                if (raw.isNotBlank()) ChannelRepository.resolvePlayableAzamManifestUrl(raw) else ""
            }

        return failedChannel.copy(
            streamUrl = freshPrimary,
            backupStreamUrl = freshBackup,
            clearKeys = mergedClearKeys,
            encryptionType = if (mergedClearKeys.isNotEmpty()) "clearkey" else failedChannel.encryptionType
        )
    }

    /**
     * Returns true if the given HTTP status code or error message indicates a stream authentication,
     * expired CDN token, CDN client-ID IP limit (HTTP 429), or decryption key failure that should
     * trigger automatic CDN key/provider renewal.
     */
    fun isAuthenticationFailure(httpStatusCode: Int?, errorMessage: String? = null): Boolean {
        if (httpStatusCode == 401 || httpStatusCode == 403 || httpStatusCode == 410 || httpStatusCode == 429) return true
        val msg = errorMessage?.lowercase().orEmpty()
        return msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("410") ||
            msg.contains("429") ||
            msg.contains("too many") ||
            msg.contains("rate limit") ||
            msg.contains("client id") ||
            msg.contains("unauthorized") ||
            msg.contains("forbidden") ||
            msg.contains("authorization") ||
            msg.contains("token") ||
            msg.contains("expired") ||
            msg.contains("clearkey") ||
            msg.contains("decrypt") ||
            msg.contains("drm")
    }

    private fun executeHttpGet(urlStr: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 18_000
                instanceFollowRedirects = true
                useCaches = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                setRequestProperty("Cache-Control", "no-store, no-cache")
                if (urlStr.contains("streamzone.fun", ignoreCase = true)) {
                    setRequestProperty("Origin", "https://streamzone.fun")
                    setRequestProperty("Referer", "https://streamzone.fun/")
                }
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
