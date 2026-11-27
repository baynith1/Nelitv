package com.example.player

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import com.example.BuildConfig
import com.example.data.ChannelRepository
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-speed in-app local streaming & token engine for Neli TV.
 *
 * Eliminates long loading spinners on Azam TV DASH & live HLS streams by:
 * 1. Maintaining a shared HTTP/2 + TLS keep-alive [ConnectionPool] directly to `*.azamtvltd.co.tz`
 *    so media segments reuse warm TCP/TLS sockets instead of handshaking on every chunk.
 * 2. Persisting the latest valid CDN token, host, provider, and optional self-hosted NeliTV token
 *    server URL inside a fast local [SharedPreferences] vault for 0ms cold-start playback.
 * 3. Racing multiple token endpoints (custom NeliTV server, Firebase RTDB, primary & alternate
 *    providers) in parallel with tight timeouts (~4.5s budget) instead of sequential 18s stalls.
 * 4. Caching static DASH initialization segments (`init.mp4`/`init.m4s`) and short-lived MPD
 *    micro-snapshots in memory to make channel startup and switching near-instant.
 */
@OptIn(UnstableApi::class)
object NeliLocalStreamEngine {

    private const val VAULT_PREFS_NAME = "neli_local_token_vault"
    private const val KEY_VAULT_TOKEN = "vault_cdn_token"
    private const val KEY_VAULT_HOST = "vault_cdn_host"
    private const val KEY_VAULT_PROVIDER = "vault_cdn_provider"
    private const val KEY_VAULT_SOURCE = "vault_cdn_source"
    private const val KEY_VAULT_EXP = "vault_cdn_exp"
    private const val KEY_VAULT_SERVER_CHANNELS_JSON = "vault_nelitv_server_channels_json"
    private const val KEY_CUSTOM_TOKEN_SERVER_URL = "custom_neli_token_server_url"

    private const val MPD_MICRO_CACHE_TTL_MS = 1_500L
    private const val MAX_INIT_SEGMENT_CACHE_ENTRIES = 24
    private const val MAX_MPD_CACHE_ENTRIES = 16

    @Volatile
    private var lastServerChannelsSyncMs: Long = 0L

    @Volatile
    private var consecutiveOverloadCount: Int = 0

    @Volatile
    private var overloadBackoffUntilMs: Long = 0L

    private val proactiveKeepAliveStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var vaultPrefs: SharedPreferences? = null

    @Volatile
    var customTokenServerUrl: String = ""
        private set

    private val warmupInFlight = AtomicBoolean(false)

    private val sharedConnectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)

    /**
     * Thread-safe DNS cache (5-minute TTL + stale-on-error fallback) so Android Smart TVs on
     * unstable Wi-Fi routers do not stall for 2-6 seconds re-resolving CDN hostnames on every reconnect.
     */
    internal object NeliCachingDns : Dns {
        private const val DNS_TTL_MS = 5 * 60 * 1_000L

        private data class CachedEntry(
            val addresses: List<InetAddress>,
            val resolvedAtMs: Long
        )

        private val cache = ConcurrentHashMap<String, CachedEntry>()

        override fun lookup(hostname: String): List<InetAddress> {
            val key = hostname.trim().lowercase()
            val now = System.currentTimeMillis()
            val cached = cache[key]
            if (cached != null && (now - cached.resolvedAtMs) < DNS_TTL_MS && cached.addresses.isNotEmpty()) {
                return cached.addresses
            }
            return try {
                val fresh = Dns.SYSTEM.lookup(hostname)
                if (fresh.isNotEmpty()) {
                    cache[key] = CachedEntry(fresh, now)
                }
                fresh
            } catch (e: UnknownHostException) {
                if (cached != null && cached.addresses.isNotEmpty()) {
                    cached.addresses
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Pooled HTTP/1.1 keep-alive client for Live TV manifests and segments.
     * Uses a 16-socket ConnectionPool + [NeliCachingDns] + resilient timeouts for unstable Smart TV Wi-Fi
     * so concurrent audio+video DASH/HLS chunks reuse warm TLS sockets without stalling on DNS or micro-drops.
     */
    val liveStreamHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dns(NeliCachingDns)
            .protocols(listOf(Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Pooled HTTP/2 + HTTP/1.1 keep-alive client for VOD movies/series with cached DNS so Android TV
     * movies start fast without 2–6s Wi-Fi DNS stalls or HTTP/1.1 head-of-line blocking.
     */
    val vodStreamHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dns(NeliCachingDns)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Dedicated ultra-fast client for parallel token server racing (never blocks for 18+ seconds).
     */
    val fastTokenHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .connectTimeout(3_000, TimeUnit.MILLISECONDS)
            .readTimeout(4_000, TimeUnit.MILLISECONDS)
            .callTimeout(4_800, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val raceExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "NeliTokenRaceWorker").apply { isDaemon = true }
    }

    private data class CachedMpdEntry(
        val bytes: ByteArray,
        val cachedAtMs: Long,
        val tokenUsed: String
    )

    private val initSegmentCache = ConcurrentHashMap<String, ByteArray>()
    private val mpdMicroCache = ConcurrentHashMap<String, CachedMpdEntry>()

    /**
     * Initializes the local token vault from [SharedPreferences] and restores any still-valid
     * persisted CDN token immediately (< 1ms) so cold launches do not block on external APIs.
     */
    fun initializeVault(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            vaultPrefs = prefs
            val savedCustomUrl = prefs.getString(KEY_CUSTOM_TOKEN_SERVER_URL, "").orEmpty().trim()
            if (savedCustomUrl.startsWith("http", ignoreCase = true)) {
                customTokenServerUrl = savedCustomUrl
            }
            val savedToken = prefs.getString(KEY_VAULT_TOKEN, "").orEmpty().trim()
            val savedHost = prefs.getString(KEY_VAULT_HOST, "").orEmpty().trim()
            val savedProvider = prefs.getString(KEY_VAULT_PROVIDER, "").orEmpty().trim()
            val savedSource = prefs.getString(KEY_VAULT_SOURCE, "vault").orEmpty().ifBlank { "vault" }
            val savedExp = prefs.getLong(KEY_VAULT_EXP, 0L)
            if (savedToken.isNotEmpty() && !ChannelRepository.isJwtTokenExpired(savedToken)) {
                val savedTokenExp = ChannelRepository.extractJwtExpEpochSeconds(savedToken) ?: savedExp
                val currentExp = ChannelRepository.extractJwtExpEpochSeconds(ChannelRepository.AZAM_CDN_TOKEN) ?: 0L
                val isCurrentExpired = ChannelRepository.isActiveTokenExpired()
                // Only restore vaulted token if current token is expired/placeholder OR vault token is equally fresh / newer
                if (isCurrentExpired || currentExp == 0L || savedTokenExp >= currentExp) {
                    val payload = org.json.JSONObject().apply {
                        put("token", savedToken)
                        if (savedHost.startsWith("http", ignoreCase = true)) put("cdnHost", savedHost)
                        if (savedProvider.isNotEmpty()) put("provider", savedProvider)
                        put("source", savedSource)
                        if (savedTokenExp > 0L) put("exp", savedTokenExp)
                    }
                    ChannelRepository.updateCdnAuthorizationToken(
                        jsonStr = payload.toString(),
                        isAuthoritativeTokenApi = isCurrentExpired || currentExp == 0L || savedTokenExp > currentExp
                    )
                }
            }
            val savedChannelsJson = prefs.getString(KEY_VAULT_SERVER_CHANNELS_JSON, "").orEmpty().trim()
            if (savedChannelsJson.isNotEmpty() && (savedChannelsJson.startsWith("{") || savedChannelsJson.startsWith("["))) {
                ChannelRepository.parseBackupChannelsApiPayload(savedChannelsJson, persistToVault = false)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Persists the latest server channels JSON from `nelitv.online` so cold launches immediately
     * use the newest server-controlled stream URLs, ClearKeys, and formats without an APK update.
     */
    fun saveServerChannelsJsonToVault(json: String) {
        val clean = json.trim()
        if (clean.isEmpty() || clean.length > 1_500_000) return
        try {
            vaultPrefs?.edit()?.putString(KEY_VAULT_SERVER_CHANNELS_JSON, clean)?.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Persists a freshly validated CDN token into the local vault for instant reuse on future launches.
     */
    fun saveTokenToVault(
        token: String,
        cdnHost: String,
        provider: String,
        source: String,
        expEpochSec: Long
    ) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return
        try {
            vaultPrefs?.edit()
                ?.putString(KEY_VAULT_TOKEN, cleanToken)
                ?.putString(KEY_VAULT_HOST, cdnHost.trim())
                ?.putString(KEY_VAULT_PROVIDER, provider.trim())
                ?.putString(KEY_VAULT_SOURCE, source.trim())
                ?.putLong(KEY_VAULT_EXP, expEpochSec)
                ?.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Returns the configured NeliTv App Server API Key injected securely via `BuildConfig.NELITV_API_KEY`
     * (set in the AI Studio Secrets panel), or empty string if still unset/placeholder.
     */
    fun getConfiguredNeliTvApiKey(): String {
        val key = try {
            BuildConfig.NELITV_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        return if (key.isBlank() || key.equals("YOUR_NELITV_API_KEY", ignoreCase = true)) "" else key
    }

    /**
     * Configures an optional self-hosted NeliTV Token Server URL (e.g. `https://nelitv.online/baynithalex/api/cdn-token`)
     * that takes top priority over all fallback providers.
     */
    fun setCustomTokenServerUrl(context: Context?, url: String) {
        val cleaned = url.trim()
        customTokenServerUrl = if (cleaned.startsWith("http", ignoreCase = true)) cleaned else ""
        try {
            val prefs = vaultPrefs ?: context?.applicationContext?.getSharedPreferences(VAULT_PREFS_NAME, Context.MODE_PRIVATE)
            prefs?.edit()?.putString(KEY_CUSTOM_TOKEN_SERVER_URL, customTokenServerUrl)?.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Races candidate token endpoints in parallel and returns the FIRST valid payload whose provider
     * is not [excludedProvider]. Cancels remaining slower HTTP calls immediately upon first success.
     */
    fun raceFetchFirstValidToken(
        endpoints: List<String>,
        excludedProvider: String? = null,
        validator: (endpoint: String, body: String) -> Boolean
    ): String? {
        val uniqueEndpoints = endpoints.map { it.trim() }.filter { it.startsWith("http", ignoreCase = true) }.distinct()
        if (uniqueEndpoints.isEmpty()) return null

        val activeCalls = ConcurrentHashMap.newKeySet<Call>()
        val completionService = ExecutorCompletionService<Pair<String, String?>>(raceExecutor)
        val futures = uniqueEndpoints.map { endpoint ->
            completionService.submit(Callable {
                val body = executePooledGet(endpoint, activeCalls)
                endpoint to body
            })
        }

        var winningBody: String? = null
        val deadlineMs = System.currentTimeMillis() + 6_000L
        try {
            for (i in uniqueEndpoints.indices) {
                val remainingMs = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(150L)
                val finished = completionService.poll(remainingMs, TimeUnit.MILLISECONDS) ?: break
                val (endpoint, body) = try {
                    finished.get()
                } catch (_: Exception) {
                    continue
                }
                if (!body.isNullOrBlank() && validator(endpoint, body)) {
                    winningBody = body
                    break
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            for (call in activeCalls) {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            for (f in futures) {
                if (!f.isDone) {
                    f.cancel(true)
                }
            }
        }
        return winningBody
    }

    private fun executePooledGet(urlStr: String, activeCalls: MutableSet<Call>): String? {
        val effectiveUrl = urlStr.trim()
        val reqBuilder = Request.Builder()
            .url(effectiveUrl)
            .get()
            .header("User-Agent", com.example.data.NeliTvRepository.getPlayerUserAgent())
            .header("Accept", "application/json")
            .header("Cache-Control", "no-store, no-cache")

        if (effectiveUrl.contains("nelitv.online", ignoreCase = true)) {
            reqBuilder
                .header(
                    com.example.data.NeliTvRepository.HEADER_CLIENT_NAME,
                    com.example.data.NeliTvRepository.HEADER_CLIENT_VALUE
                )
                .header("Origin", ChannelRepository.NELITV_SERVER_BASE_URL)
                .header("Referer", "${ChannelRepository.NELITV_SERVER_BASE_URL}/")
            val apiKey = com.example.data.NeliTvRepository.getConfiguredApiKey()
            if (apiKey.isNotEmpty()) {
                reqBuilder.header(com.example.data.NeliTvRepository.HEADER_API_KEY_NAME, apiKey)
            }
        } else if (effectiveUrl.contains("streamzone.fun", ignoreCase = true)) {
            reqBuilder
                .header("Origin", "https://streamzone.fun")
                .header("Referer", "https://streamzone.fun/")
        }

        val call = fastTokenHttpClient.newCall(reqBuilder.build())
        activeCalls.add(call)
        return try {
            call.execute().use { response ->
                val code = response.code
                if (response.isSuccessful) {
                    resetServerOverloadState()
                    response.body?.string()?.trim()
                } else {
                    if (code == 429 || code == 502 || code == 503 || code == 504) {
                        recordServerOverloadStatus(code)
                    }
                    null
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            activeCalls.remove(call)
        }
    }

    /**
     * Records an upstream rate-limit or overload response (HTTP 429/502/503/504) and computes a
     * jittered exponential backoff window (2s -> 5s -> 12s -> 30s) so bursts of concurrent users
     * never stampede `nelitv.online` while existing vault tokens & streams stay uninterrupted.
     */
    fun recordServerOverloadStatus(statusCode: Int, nowMs: Long = System.currentTimeMillis()): Long {
        if (statusCode != 429 && statusCode != 502 && statusCode != 503 && statusCode != 504) {
            return 0L
        }
        val count = (consecutiveOverloadCount + 1).coerceAtMost(6)
        consecutiveOverloadCount = count
        val baseMs = when (count) {
            1 -> 2_000L
            2 -> 5_000L
            3 -> 12_000L
            else -> 30_000L
        }
        val jitterMs = kotlin.random.Random.nextLong(250L, 1_500L)
        val cooldownMs = baseMs + jitterMs
        overloadBackoffUntilMs = maxOf(overloadBackoffUntilMs, nowMs + cooldownMs)
        return cooldownMs
    }

    fun resetServerOverloadState() {
        consecutiveOverloadCount = 0
        overloadBackoffUntilMs = 0L
    }

    fun isOverloadBackoffActive(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs < overloadBackoffUntilMs
    }

    /**
     * Starts a single background anti-expiry keep-alive watchdog that checks token health every
     * ~3 minutes (with randomized jitter so thousands of devices never hit `nelitv.online` at once)
     * and silently renews tokens + channels 20 minutes before expiry so streams never expire mid-watch.
     */
    fun startProactiveKeepAliveLoop() {
        if (!proactiveKeepAliveStarted.compareAndSet(false, true)) return
        Thread({
            while (true) {
                try {
                    val intervalSec = com.example.data.NeliTvRepository.bootstrapState.value
                        .refreshIntervalSeconds
                        .coerceIn(900L, 1_800L)
                    val jitterMs = kotlin.random.Random.nextLong(5_000L, 30_000L)
                    Thread.sleep(intervalSec * 1_000L + jitterMs)
                    if (isOverloadBackoffActive()) continue
                    com.example.data.NeliTvRepository.fetchAppBootstrapBlocking(forceRefresh = false)
                } catch (_: InterruptedException) {
                    break
                } catch (_: Exception) {
                }
            }
        }, "NeliAntiExpiryWatchdog").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Appends `apiKey=<BuildConfig.NELITV_API_KEY>` to `nelitv.online` API URLs when configured
     * and not already present in the query string.
     */
    fun appendNeliTvApiKeyIfNeeded(urlStr: String, apiKeyOverride: String? = null): String {
        val trimmed = urlStr.trim()
        if (!trimmed.contains("nelitv.online", ignoreCase = true)) return trimmed
        val key = (apiKeyOverride ?: getConfiguredNeliTvApiKey()).trim()
        if (key.isEmpty() || trimmed.contains("apiKey=", ignoreCase = true)) return trimmed
        val separator = if (trimmed.contains("?")) "&" else "?"
        return "$trimmed${separator}apiKey=$key"
    }

    /**
     * Builds a `nelitv.online/api/proxy-stream` relay URL for automatic failover when a user's
     * mobile ISP blocks or rate-limits direct CDN playback.
     */
    fun buildNeliTvProxyStreamUrl(targetStreamUrl: String, apiKeyOverride: String? = null): String {
        return com.example.data.NeliTvRepository.buildProxyStreamUrl(targetStreamUrl)
    }

    /**
     * Synchronizes live channels, stream URLs, ClearKeys, and the latest CDN token directly from
     * the NeliTv App Server (`nelitv.online/baynithalex/api/app`) via Single-Flight [NeliTvRepository].
     */
    fun syncLiveChannelsFromNeliTvServerBlocking(forceRefresh: Boolean = false): Boolean {
        val state = com.example.data.NeliTvRepository.fetchAppBootstrapBlocking(forceRefresh = forceRefresh)
        if (state != null && state.channels.isNotEmpty()) {
            lastServerChannelsSyncMs = System.currentTimeMillis()
            TokenManager.syncStateFromRepository()
            return true
        }
        return ChannelRepository.getCachedBackupApiChannels().isNotEmpty()
    }

    /**
     * Proactively preloads `/baynithalex/api/app` and pre-warms a keep-alive TLS socket to the dynamic CDN host.
     */
    fun warmUpAzamCdnAndTokenAsync() {
        startProactiveKeepAliveLoop()
        if (!warmupInFlight.compareAndSet(false, true)) return
        raceExecutor.execute {
            try {
                com.example.data.NeliTvRepository.fetchAppBootstrapBlocking(forceRefresh = false)
                TokenManager.syncStateFromRepository()
                val cdnHost = com.example.data.NeliTvRepository.getCurrentCdnHost()
                if (cdnHost.startsWith("http", ignoreCase = true)) {
                    val warmReq = Request.Builder()
                        .url(cdnHost)
                        .head()
                        .header("User-Agent", com.example.data.NeliTvRepository.getPlayerUserAgent())
                        .build()
                    try {
                        liveStreamHttpClient.newCall(warmReq).execute().close()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            } finally {
                warmupInFlight.set(false)
            }
        }
    }

    /**
     * Returns true if a DASH path represents a static initialization segment (`init.mp4`, `init.m4s`, etc.).
     */
    fun isDashInitSegmentPath(pathLower: String): Boolean {
        val fileName = pathLower.substringAfterLast("/")
        return fileName.contains("init") &&
            (fileName.endsWith(".mp4") || fileName.endsWith(".m4s") || fileName.endsWith(".cmfv") || fileName.endsWith(".cmfa"))
    }

    fun getCachedInitSegment(pathKey: String): ByteArray? = initSegmentCache[pathKey]

    fun putCachedInitSegment(pathKey: String, bytes: ByteArray) {
        if (bytes.isEmpty() || bytes.size > 256 * 1024) return
        if (initSegmentCache.size >= MAX_INIT_SEGMENT_CACHE_ENTRIES) {
            val firstKey = initSegmentCache.keys.firstOrNull()
            if (firstKey != null) initSegmentCache.remove(firstKey)
        }
        initSegmentCache[pathKey] = bytes
    }

    fun getFreshCachedMpd(pathKey: String, activeToken: String, nowMs: Long = System.currentTimeMillis()): ByteArray? {
        val entry = mpdMicroCache[pathKey] ?: return null
        if (entry.tokenUsed != activeToken || (nowMs - entry.cachedAtMs) > MPD_MICRO_CACHE_TTL_MS) {
            mpdMicroCache.remove(pathKey)
            return null
        }
        return entry.bytes
    }

    fun putCachedMpd(pathKey: String, bytes: ByteArray, activeToken: String, nowMs: Long = System.currentTimeMillis()) {
        if (bytes.isEmpty() || bytes.size > 512 * 1024) return
        if (mpdMicroCache.size >= MAX_MPD_CACHE_ENTRIES) {
            val firstKey = mpdMicroCache.keys.firstOrNull()
            if (firstKey != null) mpdMicroCache.remove(firstKey)
        }
        mpdMicroCache[pathKey] = CachedMpdEntry(bytes = bytes, cachedAtMs = nowMs, tokenUsed = activeToken)
    }

    fun clearTransientManifestCache() {
        mpdMicroCache.clear()
    }

    /**
     * Creates a pooled HTTP/2 Media3 [DataSource.Factory] backed by [liveStreamHttpClient] or [vodStreamHttpClient].
     */
    fun createPooledHttpDataSourceFactory(
        streamUrl: String?,
        isLive: Boolean
    ): DataSource.Factory {
        val client = if (isLive) liveStreamHttpClient else vodStreamHttpClient
        return NeliPooledOkHttpDataSource.Factory(
            callFactory = client,
            streamUrl = streamUrl
        )
    }
}

/**
 * Lightweight, high-performance Media3 [HttpDataSource] backed by OkHttp's HTTP/2 [ConnectionPool].
 * Eliminates per-segment TCP/TLS handshake delays and surfaces exact HTTP status codes (401/403/429/404)
 * via [HttpDataSource.InvalidResponseCodeException] for instant Azam provider failover.
 */
@OptIn(UnstableApi::class)
class NeliPooledOkHttpDataSource(
    private val callFactory: Call.Factory,
    private val defaultStreamUrl: String?
) : BaseDataSource(/* isNetwork = */ true), HttpDataSource {

    class Factory(
        private val callFactory: Call.Factory,
        private val streamUrl: String?
    ) : HttpDataSource.Factory {
        private val customProperties = HttpDataSource.RequestProperties()

        override fun createDataSource(): HttpDataSource {
            return NeliPooledOkHttpDataSource(callFactory, streamUrl).apply {
                for ((k, v) in customProperties.snapshot) {
                    setRequestProperty(k, v)
                }
            }
        }

        override fun setDefaultRequestProperties(defaultRequestProperties: MutableMap<String, String>): HttpDataSource.Factory {
            customProperties.clearAndSet(defaultRequestProperties)
            return this
        }
    }

    private val requestProperties = HttpDataSource.RequestProperties()
    private var currentDataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseBody: ResponseBody? = null
    private var responseInputStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesRead: Long = 0L

    override fun setRequestProperty(name: String, value: String) {
        requestProperties.set(name, value)
    }

    override fun clearRequestProperty(name: String) {
        requestProperties.remove(name)
    }

    override fun clearAllRequestProperties() {
        requestProperties.clear()
    }

    override fun getResponseCode(): Int = response?.code ?: -1

    override fun getResponseHeaders(): Map<String, List<String>> =
        response?.headers?.toMultimap() ?: emptyMap()

    override fun getUri(): Uri? =
        response?.request?.url?.toString()?.let { Uri.parse(it) } ?: currentDataSpec?.uri

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        bytesRead = 0L
        bytesToRead = C.LENGTH_UNSET.toLong()
        transferInitializing(dataSpec)

        val urlString = dataSpec.uri.toString()
        val requestBuilder = Request.Builder().url(urlString)

        // Scope NeliTv/Azam/Streamzone headers strictly to Neli/Azam/CDN hosts so third-party movie CDNs
        // (Firebase Storage, Cloudflare R2, Bunny, Archive.org, Drive, etc.) never reject requests with 403/400
        // or stall TLS handshakes on Android TV.
        val effectiveTarget = urlString.ifBlank { defaultStreamUrl.orEmpty() }
        val mergedHeaders = LinkedHashMap<String, String>()
        val targetLower = effectiveTarget.lowercase()
        val isNeliOrAzamHost = targetLower.contains("nelitv.online") ||
            targetLower.contains("azamtvltd.co.tz") ||
            targetLower.contains("streamzone.fun") ||
            targetLower.contains("cdn77.org") ||
            targetLower.contains("/live/eds/") ||
            targetLower.contains("/api/proxy-stream")
        if (isNeliOrAzamHost) {
            mergedHeaders.putAll(com.example.data.NeliTvRepository.buildStreamPlayerHeaders(effectiveTarget))
            mergedHeaders.putAll(TokenManager.buildExoPlayerHeaders(streamUrl = effectiveTarget))
        } else {
            mergedHeaders["User-Agent"] = TokenManager.USER_AGENT
            mergedHeaders["Accept"] = "*/*"
        }
        mergedHeaders.putAll(requestProperties.snapshot)
        mergedHeaders.putAll(dataSpec.httpRequestHeaders)

        for ((key, value) in mergedHeaders) {
            if (key.isBlank() || value.isBlank()) continue
            // Skip hop-by-hop connection headers; OkHttp manages keep-alive automatically
            if (key.equals("Connection", ignoreCase = true) ||
                key.equals("Keep-Alive", ignoreCase = true) ||
                key.equals("Transfer-Encoding", ignoreCase = true) ||
                key.equals("Upgrade", ignoreCase = true)
            ) {
                continue
            }
            requestBuilder.header(key, value)
        }

        if (dataSpec.position > 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) {
            val rangeHeader = buildString {
                append("bytes=")
                append(dataSpec.position)
                append("-")
                if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    append(dataSpec.position + dataSpec.length - 1L)
                }
            }
            requestBuilder.header("Range", rangeHeader)
        }

        when (dataSpec.httpMethod) {
            DataSpec.HTTP_METHOD_HEAD -> requestBuilder.head()
            else -> requestBuilder.get()
        }

        val executedResponse = try {
            callFactory.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                dataSpec,
                HttpDataSource.HttpDataSourceException.TYPE_OPEN
            )
        }

        response = executedResponse
        val body = executedResponse.body
        responseBody = body
        val responseCode = executedResponse.code

        if (!executedResponse.isSuccessful) {
            val headers = executedResponse.headers.toMultimap()
            val errorBytes = try {
                body?.bytes() ?: ByteArray(0)
            } catch (_: Exception) {
                ByteArray(0)
            }
            closeQuietly()
            throw HttpDataSource.InvalidResponseCodeException(
                responseCode,
                executedResponse.message,
                /* cause = */ null,
                headers,
                dataSpec,
                errorBytes
            )
        }

        val rawStream = body?.byteStream()
        val bytesToSkip = if (responseCode == 200 && dataSpec.position > 0L) dataSpec.position else 0L
        if (bytesToSkip > 2L * 1024L * 1024L) {
            // Server ignored the Range header and returned 200 OK for a deep seek (>2 MB).
            // Never synchronously read/discard tens or hundreds of megabytes on Android TV (which causes 25–60s freezes).
            closeQuietly()
            throw androidx.media3.datasource.DataSourceException(
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
            )
        }
        if (bytesToSkip > 0L && rawStream != null) {
            try {
                var remainingSkip = bytesToSkip
                val skipBuf = ByteArray(8192)
                while (remainingSkip > 0L) {
                    val skipped = rawStream.read(skipBuf, 0, minOf(remainingSkip, skipBuf.size.toLong()).toInt())
                    if (skipped == -1) break
                    remainingSkip -= skipped
                }
            } catch (e: IOException) {
                closeQuietly()
                throw HttpDataSource.HttpDataSourceException.createForIOException(
                    e,
                    dataSpec,
                    HttpDataSource.HttpDataSourceException.TYPE_OPEN
                )
            }
        }

        val contentLength = body?.contentLength() ?: C.LENGTH_UNSET.toLong()
        val adjustedContentLength = if (contentLength >= 0L && bytesToSkip > 0L) {
            (contentLength - bytesToSkip).coerceAtLeast(0L)
        } else {
            contentLength
        }
        bytesToRead = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            adjustedContentLength >= 0L -> adjustedContentLength
            else -> C.LENGTH_UNSET.toLong()
        }
        responseInputStream = rawStream
        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
        }
        val toRead = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(length.toLong(), bytesToRead - bytesRead).toInt()
        }
        val stream = responseInputStream ?: return C.RESULT_END_OF_INPUT
        val readCount = try {
            stream.read(buffer, offset, toRead)
        } catch (e: InterruptedIOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                currentDataSpec ?: DataSpec(Uri.EMPTY),
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
        } catch (e: IOException) {
            throw HttpDataSource.HttpDataSourceException.createForIOException(
                e,
                currentDataSpec ?: DataSpec(Uri.EMPTY),
                HttpDataSource.HttpDataSourceException.TYPE_READ
            )
        }
        if (readCount == -1) return C.RESULT_END_OF_INPUT
        bytesRead += readCount
        bytesTransferred(readCount)
        return readCount
    }

    override fun close() {
        if (opened) {
            opened = false
            transferEnded()
        }
        closeQuietly()
        currentDataSpec = null
    }

    private fun closeQuietly() {
        try {
            responseInputStream?.close()
        } catch (_: Exception) {
        }
        responseInputStream = null
        try {
            responseBody?.close()
        } catch (_: Exception) {
        }
        responseBody = null
        try {
            response?.close()
        } catch (_: Exception) {
        }
        response = null
    }
}
