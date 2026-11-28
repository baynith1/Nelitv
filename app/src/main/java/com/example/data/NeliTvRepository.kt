package com.example.data

import android.content.Context
import android.net.Uri
import com.example.BuildConfig
import com.example.model.LiveChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-Flight + Stale-While-Revalidate Repository for the official NeliTv Server
 * hosted at `https://nelitv.online`.
 *
 * Guarantees:
 * - Persists the full `/baynithalex/api/app` JSON response on disk (`SharedPreferences`) + RAM.
 * - Coalesces concurrent callers onto a single in-flight HTTP request (`Mutex` + `CompletableDeferred`).
 * - Enforces a 15-second minimum cooldown unless the user explicitly pulls-to-refresh (`forceRefresh = true`).
 * - Retries transient network / 5xx / 429 failures up to 2 times with exponential backoff.
 * - Never wipes cached channels on network error — keeps cached channels visible so "No content shown" never happens.
 * - Uses permanent token semantics (`neverExpires == true` or `exp >= 2_500_000_000L`) and dynamic `cdnHost`.
 */
object NeliTvRepository {

    const val BASE_URL = "https://nelitv.online"
    const val ENDPOINT_APP_BOOTSTRAP = "$BASE_URL/baynithalex/api/app"
    const val ENDPOINT_CHANNELS = "$BASE_URL/baynithalex/api/channels"
    const val ENDPOINT_CDN_TOKEN = "$BASE_URL/baynithalex/api/cdn-token"
    const val ENDPOINT_PROXY_STREAM = "$BASE_URL/api/proxy-stream"

    const val HEADER_CLIENT_NAME = "X-Neli-Client"
    const val HEADER_CLIENT_VALUE = "nelitv-app"
    const val HEADER_API_KEY_NAME = "X-Neli-Api-Key"

    const val DEFAULT_CDN_HOST = "https://1445733302.rsc.cdn77.org"
    const val PERMANENT_EXP_THRESHOLD_SEC = 2_500_000_000L
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private const val PREFS_NAME = "nelitv_bootstrap_cache_v1"
    private const val KEY_BOOTSTRAP_JSON = "app_bootstrap_raw_json"
    private const val KEY_EXTRA_CHANNELS_JSON = "app_extra_channels_raw_json"
    private const val KEY_SAVED_AT_MS = "app_bootstrap_saved_at_ms"

    private const val MIN_REFRESH_COOLDOWN_MS = 15_000L
    private const val MIN_BACKGROUND_REFRESH_SEC = 900L   // 15 minutes
    private const val DEFAULT_BACKGROUND_REFRESH_SEC = 1_200L // 20 minutes
    private const val MAX_BACKGROUND_REFRESH_SEC = 1_800L // 30 minutes
    private const val MAX_TRANSIENT_RETRIES = 2

    data class AppBootstrapState(
        val token: String = "",
        val cdnHost: String = DEFAULT_CDN_HOST,
        val neverExpires: Boolean = true,
        val exp: Long = 2_524_608_000L,
        val clearKeys: Map<String, String> = emptyMap(),
        val channels: List<LiveChannel> = emptyList(),
        val headers: Map<String, String> = mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to "$BASE_URL/"
        ),
        val refreshIntervalSeconds: Long = DEFAULT_BACKGROUND_REFRESH_SEC,
        val lastSyncedAtMs: Long = 0L,
        val isFromCache: Boolean = true,
        val lastErrorMessage: String? = null,
        val serverAnnouncement: String = "",
        val serverAnnouncementId: String = "",
        val serverUpdateVersion: String = "",
        val serverUpdateApkUrl: String = "",
        val serverUpdateNotes: String = "",
        val serverUpdateMandatory: Boolean = false,
        val serverFeatures: Set<String> = emptySet()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val singleFlightMutex = Mutex()
    private val initialized = AtomicBoolean(false)
    private val backgroundLoopStarted = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var inFlightDeferred: CompletableDeferred<AppBootstrapState?>? = null

    @Volatile
    private var lastFetchAttemptMs: Long = 0L

    private val _bootstrapState = MutableStateFlow(AppBootstrapState())
    val bootstrapState: StateFlow<AppBootstrapState> = _bootstrapState.asStateFlow()

    val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves the configured `NELITV_API_KEY` injected via Secrets Gradle Plugin (`BuildConfig.NELITV_API_KEY`).
     * Returns empty string if unset or still holding the `.env.example` placeholder.
     */
    fun getConfiguredApiKey(): String {
        val raw = try {
            BuildConfig.NELITV_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        return if (
            raw.isNotEmpty() &&
            !raw.equals("YOUR_NELITV_API_KEY", ignoreCase = true) &&
            !raw.equals("NELITV_API_KEY_DEFAULT_VALUE", ignoreCase = true)
        ) {
            raw
        } else {
            ""
        }
    }

    /**
     * Returns the standard NeliTv Server API headers sent on every `/baynithalex/api/` request.
     */
    fun buildApiHeaders(): Map<String, String> {
        val map = linkedMapOf(
            HEADER_CLIENT_NAME to HEADER_CLIENT_VALUE,
            "Accept" to "application/json",
            "User-Agent" to getPlayerUserAgent()
        )
        val apiKey = getConfiguredApiKey()
        if (apiKey.isNotEmpty()) {
            map[HEADER_API_KEY_NAME] = apiKey
        }
        return map
    }

    /**
     * Returns HTTP headers for ExoPlayer playback requests (direct CDN or NeliTv proxy).
     */
    fun buildStreamPlayerHeaders(streamUrl: String): Map<String, String> {
        val state = _bootstrapState.value
        val headers = linkedMapOf(
            "User-Agent" to getPlayerUserAgent(),
            "Accept" to "*/*",
            "Connection" to "keep-alive"
        )
        val serverReferer = state.headers.entries
            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            ?.value
            ?.trim()
            .orEmpty()

        val isNeliProxy = streamUrl.contains("nelitv.online", ignoreCase = true)
        val isAzamOrCdn = isAzamOrDynamicCdnUrl(streamUrl)

        if (isNeliProxy) {
            headers[HEADER_CLIENT_NAME] = HEADER_CLIENT_VALUE
            val apiKey = getConfiguredApiKey()
            if (apiKey.isNotEmpty()) {
                headers[HEADER_API_KEY_NAME] = apiKey
            }
            headers["Referer"] = serverReferer.ifBlank { "$BASE_URL/" }
            headers["Origin"] = BASE_URL
        } else if (isAzamOrCdn) {
            if (serverReferer.isNotEmpty()) {
                headers["Referer"] = serverReferer
            } else {
                headers["Referer"] = "$BASE_URL/"
            }
            headers["Origin"] = BASE_URL
        }
        return headers
    }

    fun getPlayerUserAgent(): String {
        val serverUa = _bootstrapState.value.headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value
            ?.trim()
        return if (!serverUa.isNullOrBlank()) serverUa else DEFAULT_USER_AGENT
    }

    fun getPlayerHeaders(): Map<String, String> = _bootstrapState.value.headers

    fun getGlobalClearKeys(): Map<String, String> = _bootstrapState.value.clearKeys

    fun getCurrentCdnHost(): String =
        _bootstrapState.value.cdnHost.trim().removeSuffix("/").ifBlank { DEFAULT_CDN_HOST }

    fun getCurrentToken(): String = _bootstrapState.value.token.trim()

    fun isCurrentTokenPermanent(): Boolean {
        val st = _bootstrapState.value
        return isTokenPermanent(st.neverExpires, st.exp)
    }

    fun isTokenPermanent(neverExpires: Boolean, expEpochSec: Long?): Boolean {
        return neverExpires || (expEpochSec != null && expEpochSec >= PERMANENT_EXP_THRESHOLD_SEC)
    }

    fun isAzamOrDynamicCdnUrl(url: String?): Boolean {
        val clean = url?.trim().orEmpty()
        if (clean.isEmpty()) return false
        val dynamicHost = getCurrentCdnHost()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
        return clean.contains("/live/eds/", ignoreCase = true) ||
            clean.contains("cdn77.org", ignoreCase = true) ||
            clean.contains("azamtvltd.co.tz", ignoreCase = true) ||
            (dynamicHost.isNotEmpty() && clean.contains(dynamicHost, ignoreCase = true))
    }

    /**
     * Builds an Azam/EDS DASH URL dynamically using the server-provided `cdnHost` and `token`.
     * Never hardcodes legacy Azam edge hosts.
     */
    fun buildDynamicAzamUrl(
        slug: String,
        cdnHost: String = getCurrentCdnHost(),
        token: String = getCurrentToken()
    ): String {
        val cleanHost = cdnHost.trim().removeSuffix("/").ifBlank { DEFAULT_CDN_HOST }
        val cleanSlug = slug.trim().trim('/')
        if (cleanSlug.isEmpty()) return ""
        return if (cleanHost.contains("azamtvltd", ignoreCase = true) && token.isNotBlank()) {
            "$cleanHost/tok_$token/live/eds/$cleanSlug/DASH/$cleanSlug.mpd"
        } else {
            "$cleanHost/live/eds/$cleanSlug/DASH/$cleanSlug.mpd"
        }
    }

    /**
     * Builds a backup proxied stream URL through `https://nelitv.online/api/proxy-stream`.
     */
    fun buildProxyStreamUrl(rawStreamUrl: String): String {
        val clean = rawStreamUrl.trim()
        if (clean.isEmpty()) return ""
        if (clean.contains("/api/proxy-stream", ignoreCase = true)) return clean
        return "$ENDPOINT_PROXY_STREAM?url=${Uri.encode(clean)}"
    }

    /**
     * Merges channel-specific ClearKeys with global `/baynithalex/api/app` ClearKeys and
     * repository ClearKeys so multi-KID streams (such as ZBC) always decrypt reliably.
     * Channel-specific keys are placed first in insertion order.
     */
    fun getMergedClearKeysForChannel(channel: LiveChannel): Map<String, String> {
        val merged = LinkedHashMap<String, String>()
        fun putClean(source: Map<String, String>) {
            source.forEach { (rawKid, rawKey) ->
                val kid = rawKid.trim().replace("-", "").replace(" ", "").lowercase()
                val key = rawKey.trim().replace("-", "").replace(" ", "").lowercase()
                if (kid.length == 32 && key.length == 32 && !merged.containsKey(kid)) {
                    merged[kid] = key
                }
            }
        }
        putClean(channel.clearKeys)
        // Check cached bootstrap channel by id/slug/name for any refreshed keys
        val cachedMatch = findCachedChannel(channel)
        if (cachedMatch != null) {
            putClean(cachedMatch.clearKeys)
        }
        putClean(_bootstrapState.value.clearKeys)
        putClean(ChannelRepository.resolveClearKeysForChannel(channel))
        return merged
    }

    /**
     * Looks up a channel instantly in RAM cache (0ms, zero network) by id, slug, or name.
     */
    fun findCachedChannel(channel: LiveChannel): LiveChannel? {
        val cached = _bootstrapState.value.channels
        if (cached.isEmpty()) return null
        val targetSlug = channel.slug.trim()
        val targetSlugKey = ChannelRepository.normalizeChannelMatchKey(targetSlug)
        val targetKey = ChannelRepository.normalizeChannelMatchKey(channel.name)
        return cached.firstOrNull { it.id.equals(channel.id, ignoreCase = true) }
            ?: if (targetSlug.isNotEmpty()) {
                cached.firstOrNull {
                    it.slug.equals(targetSlug, ignoreCase = true) ||
                        (targetSlugKey.isNotEmpty() && ChannelRepository.normalizeChannelMatchKey(it.slug) == targetSlugKey)
                }
            } else {
                null
            }
            ?: if (targetKey.isNotEmpty()) {
                cached.firstOrNull {
                    ChannelRepository.normalizeChannelMatchKey(it.name) == targetKey ||
                        ChannelRepository.normalizeChannelMatchKey(it.slug) == targetKey ||
                        ChannelRepository.normalizeChannelMatchKey(it.id) == targetKey
                }
            } else {
                null
            }
    }

    /**
     * Initializes disk cache immediately into RAM (non-blocking for UI) and kicks off
     * a silent background bootstrap + periodic 15–30 minute refresh loop.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized.compareAndSet(false, true)) {
            loadDiskCacheIntoRam(context.applicationContext)
        }
        startSilentBackgroundRefresh(context.applicationContext)
    }

    /**
     * Preloads `/baynithalex/api/app` silently on app splash/startup without blocking the UI thread.
     */
    fun preloadOnStartup(context: Context) {
        initialize(context)
        scope.launch {
            fetchAppBootstrap(forceRefresh = false)
        }
    }

    private fun startSilentBackgroundRefresh(context: Context) {
        if (!backgroundLoopStarted.compareAndSet(false, true)) return
        appContext = context.applicationContext
        scope.launch {
            // Initial silent refresh immediately on launch; if cold-start network was still waking up,
            // retry quickly (3s, then 7s) so all 63+ nelitv.online channels appear automatically without pull-to-refresh.
            var initial = fetchAppBootstrap(forceRefresh = false)
            if (initial == null || initial.isFromCache || initial.channels.size < 50) {
                delay(3_000L)
                initial = fetchAppBootstrap(forceRefresh = true)
            }
            if (initial == null || initial.isFromCache) {
                delay(7_000L)
                fetchAppBootstrap(forceRefresh = true)
            }
            while (isActive) {
                val intervalSec = _bootstrapState.value.refreshIntervalSeconds
                    .coerceIn(MIN_BACKGROUND_REFRESH_SEC, MAX_BACKGROUND_REFRESH_SEC)
                delay(intervalSec * 1_000L)
                fetchAppBootstrap(forceRefresh = false)
            }
        }
    }

    private fun loadDiskCacheIntoRam(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedJson = prefs.getString(KEY_BOOTSTRAP_JSON, null)?.trim().orEmpty()
            val savedExtraChannelsJson = prefs.getString(KEY_EXTRA_CHANNELS_JSON, null)?.trim().orEmpty()
            val savedAt = prefs.getLong(KEY_SAVED_AT_MS, 0L)
            val parsedPrimary = if (savedJson.isNotEmpty()) {
                parseAppBootstrapJson(
                    rawJson = savedJson,
                    isFromCache = true,
                    syncedAtMs = savedAt
                )
            } else {
                null
            }
            val parsedExtra = if (savedExtraChannelsJson.isNotEmpty()) {
                parseAppBootstrapJson(
                    rawJson = savedExtraChannelsJson,
                    isFromCache = true,
                    syncedAtMs = savedAt,
                    baseState = parsedPrimary ?: _bootstrapState.value
                )
            } else {
                null
            }
            val combinedState = when {
                parsedPrimary != null && parsedExtra != null -> parsedPrimary.copy(
                    channels = mergeServerChannelLists(parsedPrimary.channels, parsedExtra.channels),
                    clearKeys = parsedExtra.clearKeys + parsedPrimary.clearKeys
                )
                parsedPrimary != null -> parsedPrimary
                else -> parsedExtra
            }
            if (combinedState != null) {
                _bootstrapState.value = combinedState
                ChannelRepository.applyNeliTvBootstrapState(combinedState)
            }
        } catch (_: Exception) {
        }
    }

    private fun persistBootstrapJsonToDisk(
        rawJson: String?,
        syncedAtMs: Long,
        extraChannelsJson: String? = null
    ) {
        val ctx = appContext ?: return
        try {
            val editor = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_SAVED_AT_MS, syncedAtMs)
            if (!rawJson.isNullOrBlank()) {
                editor.putString(KEY_BOOTSTRAP_JSON, rawJson)
            }
            if (!extraChannelsJson.isNullOrBlank()) {
                editor.putString(KEY_EXTRA_CHANNELS_JSON, extraChannelsJson)
            }
            editor.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * Merges primary `/baynithalex/api/app` channels with supplementary `/baynithalex/api/channels`
     * items so all 63+ channels hosted on `nelitv.online` appear immediately on launch without
     * losing primary token/ClearKey metadata or creating duplicates.
     */
    fun mergeServerChannelLists(
        primary: List<LiveChannel>,
        supplementary: List<LiveChannel>
    ): List<LiveChannel> {
        if (supplementary.isEmpty()) return primary
        if (primary.isEmpty()) return supplementary
        val result = ArrayList<LiveChannel>(primary.size + supplementary.size)
        val seenIds = HashSet<String>(primary.size * 2)
        val seenSlugs = HashSet<String>(primary.size * 2)
        val seenNameKeys = HashSet<String>(primary.size * 2)
        primary.forEach { ch ->
            result.add(ch)
            if (ch.id.isNotBlank()) seenIds.add(ch.id.trim().lowercase())
            if (ch.slug.isNotBlank()) seenSlugs.add(ch.slug.trim().lowercase())
            val key = ChannelRepository.normalizeChannelMatchKey(ch.name)
            if (key.isNotEmpty()) seenNameKeys.add(key)
        }
        supplementary.forEach { ch ->
            val idKey = ch.id.trim().lowercase()
            val slugKey = ch.slug.trim().lowercase()
            val nameKey = ChannelRepository.normalizeChannelMatchKey(ch.name)
            val alreadyPresent = (idKey.isNotEmpty() && idKey in seenIds) ||
                (slugKey.isNotEmpty() && slugKey in seenSlugs) ||
                (nameKey.isNotEmpty() && nameKey in seenNameKeys)
            if (!alreadyPresent) {
                result.add(ch)
                if (idKey.isNotEmpty()) seenIds.add(idKey)
                if (slugKey.isNotEmpty()) seenSlugs.add(slugKey)
                if (nameKey.isNotEmpty()) seenNameKeys.add(nameKey)
            }
        }
        return result
    }

    /**
     * Blocking convenience bridge for legacy background threads (e.g., player failover Step 2).
     * Still participates in Single-Flight coalescing and cooldown protection.
     */
    fun fetchAppBootstrapBlocking(forceRefresh: Boolean = false): AppBootstrapState? {
        return try {
            runBlocking(Dispatchers.IO) {
                fetchAppBootstrap(forceRefresh = forceRefresh)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Single-Flight Stale-While-Revalidate fetch of `GET https://nelitv.online/baynithalex/api/app`.
     * - Concurrent calls share the exact same in-flight `CompletableDeferred`.
     * - Respects 15-second cooldown unless `forceRefresh = true`.
     * - On failure, preserves existing cached channels and never wipes UI state.
     */
    suspend fun fetchAppBootstrap(forceRefresh: Boolean = false): AppBootstrapState? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var existingDeferred: CompletableDeferred<AppBootstrapState?>? = null
        var myDeferred: CompletableDeferred<AppBootstrapState?>? = null

        singleFlightMutex.withLock {
            val currentInFlight = inFlightDeferred
            if (currentInFlight != null && !currentInFlight.isCompleted) {
                existingDeferred = currentInFlight
            } else {
                val withinCooldown = !forceRefresh &&
                    lastFetchAttemptMs > 0L &&
                    (now - lastFetchAttemptMs) < MIN_REFRESH_COOLDOWN_MS &&
                    _bootstrapState.value.channels.isNotEmpty()
                if (withinCooldown) {
                    return@withContext _bootstrapState.value
                }
                lastFetchAttemptMs = now
                val created = CompletableDeferred<AppBootstrapState?>()
                inFlightDeferred = created
                myDeferred = created
            }
        }

        if (existingDeferred != null) {
            return@withContext existingDeferred!!.await()
        }

        val deferred = myDeferred ?: return@withContext _bootstrapState.value
        var resultState: AppBootstrapState? = null
        try {
            val syncedNow = System.currentTimeMillis()
            // Fetch both /baynithalex/api/app and /baynithalex/api/channels concurrently so every
            // channel hosted on nelitv.online (63+) appears immediately on launch without pull-to-refresh.
            val (body, channelsBody) = coroutineScope {
                val appJob = async { executeGetWithBackoff(ENDPOINT_APP_BOOTSTRAP) }
                val chJob = async { executeGetWithBackoff(ENDPOINT_CHANNELS) }
                Pair(appJob.await(), chJob.await())
            }

            val parsedPrimary = body?.let {
                parseAppBootstrapJson(
                    rawJson = it,
                    isFromCache = false,
                    syncedAtMs = syncedNow
                )
            }
            val parsedExtraChannels = channelsBody?.let {
                parseAppBootstrapJson(
                    rawJson = it,
                    isFromCache = false,
                    syncedAtMs = syncedNow,
                    baseState = parsedPrimary ?: _bootstrapState.value
                )
            }

            val resolvedState = when {
                parsedPrimary != null && parsedExtraChannels != null -> {
                    val mergedChannels = mergeServerChannelLists(
                        primary = parsedPrimary.channels,
                        supplementary = parsedExtraChannels.channels
                    )
                    val mergedKeys = parsedExtraChannels.clearKeys + parsedPrimary.clearKeys
                    persistBootstrapJsonToDisk(
                        rawJson = body,
                        syncedAtMs = syncedNow,
                        extraChannelsJson = channelsBody
                    )
                    parsedPrimary.copy(
                        channels = mergedChannels,
                        clearKeys = mergedKeys
                    )
                }
                parsedPrimary != null && parsedPrimary.channels.isNotEmpty() -> {
                    persistBootstrapJsonToDisk(rawJson = body, syncedAtMs = syncedNow)
                    parsedPrimary
                }
                parsedExtraChannels != null && parsedExtraChannels.channels.isNotEmpty() -> {
                    persistBootstrapJsonToDisk(
                        rawJson = if (parsedPrimary == null) channelsBody else body,
                        syncedAtMs = syncedNow,
                        extraChannelsJson = channelsBody
                    )
                    (parsedPrimary ?: parsedExtraChannels).copy(
                        channels = parsedExtraChannels.channels,
                        clearKeys = (parsedPrimary?.clearKeys.orEmpty()) + parsedExtraChannels.clearKeys
                    )
                }
                parsedPrimary != null -> parsedPrimary.copy(
                    channels = _bootstrapState.value.channels // Preserve cached channels!
                )
                else -> null
            }

            if (resolvedState != null) {
                // Never overwrite non-empty cached channels with an empty list; merge any prior unique server channels
                val finalChannels = if (resolvedState.channels.isNotEmpty()) {
                    mergeServerChannelLists(resolvedState.channels, _bootstrapState.value.channels)
                } else {
                    _bootstrapState.value.channels
                }
                val finalClearKeys = if (resolvedState.clearKeys.isNotEmpty()) {
                    _bootstrapState.value.clearKeys + resolvedState.clearKeys
                } else {
                    _bootstrapState.value.clearKeys
                }
                val finalState = resolvedState.copy(
                    channels = finalChannels,
                    clearKeys = finalClearKeys,
                    lastErrorMessage = null
                )
                _bootstrapState.value = finalState
                ChannelRepository.applyNeliTvBootstrapState(finalState)
                applyServerDrivenExtras(finalState)
                resultState = finalState
            } else {
                // Keep cached state completely intact on failure
                _bootstrapState.value = _bootstrapState.value.copy(
                    lastErrorMessage = "Imeshindwa kuwasiliana na seva ya NeliTv. Tafadhali jaribu tena."
                )
                resultState = _bootstrapState.value.takeIf { it.channels.isNotEmpty() }
            }
        } catch (e: Exception) {
            _bootstrapState.value = _bootstrapState.value.copy(
                lastErrorMessage = e.message ?: "Kuna shida ya mtandao kwa sasa."
            )
            resultState = _bootstrapState.value.takeIf { it.channels.isNotEmpty() }
        } finally {
            singleFlightMutex.withLock {
                if (inFlightDeferred === deferred) {
                    inFlightDeferred = null
                }
            }
            deferred.complete(resultState)
        }
        resultState
    }

    /**
     * Checks `GET https://nelitv.online/baynithalex/api/cdn-token` for CDN host/token metadata.
     */
    suspend fun fetchCdnTokenCheck(): AppBootstrapState? = withContext(Dispatchers.IO) {
        val body = executeGetWithBackoff(ENDPOINT_CDN_TOKEN) ?: return@withContext null
        try {
            val root = JSONObject(body)
            val data = root.optJSONObject("data") ?: root
            val token = data.optString("token").ifBlank { root.optString("token") }.trim()
            val cdnHost = data.optString("cdnHost")
                .ifBlank { root.optString("cdnHost") }
                .trim()
                .removeSuffix("/")
            val exp = data.optLong("exp", root.optLong("exp", 0L))
            val neverExpires = data.optBoolean(
                "neverExpires",
                root.optBoolean("neverExpires", exp >= PERMANENT_EXP_THRESHOLD_SEC)
            )
            val current = _bootstrapState.value
            val updated = current.copy(
                token = token.ifBlank { current.token },
                cdnHost = cdnHost.takeIf { it.startsWith("http", ignoreCase = true) } ?: current.cdnHost,
                exp = if (exp > 0L) exp else current.exp,
                neverExpires = neverExpires || isTokenPermanent(current.neverExpires, exp.takeIf { it > 0L })
            )
            _bootstrapState.value = updated
            ChannelRepository.applyNeliTvBootstrapState(updated)
            updated
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Executes a GET request with official NeliTv headers and retries up to [MAX_TRANSIENT_RETRIES]
     * times with exponential backoff (500ms -> 1500ms) ONLY on transient errors.
     */
    private suspend fun executeGetWithBackoff(url: String): String? {
        val headers = buildApiHeaders()
        var attempt = 0
        var backoffMs = 500L
        while (attempt <= MAX_TRANSIENT_RETRIES) {
            try {
                val reqBuilder = Request.Builder().url(url).get()
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                apiClient.newCall(reqBuilder.build()).execute().use { resp ->
                    val code = resp.code
                    if (resp.isSuccessful) {
                        val text = resp.body?.string()?.trim()
                        if (!text.isNullOrEmpty()) return text
                    }
                    val isTransientHttp = code == 429 || code == 500 || code == 502 || code == 503 || code == 504
                    if (!isTransientHttp || attempt >= MAX_TRANSIENT_RETRIES) {
                        return null
                    }
                }
            } catch (_: IOException) {
                if (attempt >= MAX_TRANSIENT_RETRIES) return null
            } catch (_: Exception) {
                return null
            }
            delay(backoffMs)
            backoffMs *= 3L
            attempt++
        }
        return null
    }

    /**
     * Defensively parses `/baynithalex/api/app` (or `/baynithalex/api/channels`) JSON.
     * - Reads `channels ?: data ?: emptyList()`.
     * - Never discards valid channels when optional fields (`currentProgram`, `audioUrl`, `embedUrl`, etc.) are null.
     */
    fun parseAppBootstrapJson(
        rawJson: String,
        isFromCache: Boolean,
        syncedAtMs: Long,
        baseState: AppBootstrapState = _bootstrapState.value
    ): AppBootstrapState? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val root = if (trimmed.startsWith("[")) {
                JSONObject().put("channels", JSONArray(trimmed))
            } else {
                JSONObject(trimmed)
            }
            val nestedObj = root.optJSONObject("data")

            val parsedToken = optCleanString(root, "token")
                .ifBlank { optCleanString(nestedObj, "token") }
                .ifBlank { baseState.token }

            val parsedCdnHost = optCleanString(root, "cdnHost")
                .ifBlank { optCleanString(nestedObj, "cdnHost") }
                .ifBlank { baseState.cdnHost }
                .removeSuffix("/")
                .ifBlank { DEFAULT_CDN_HOST }

            val parsedExp = root.optLong("exp", nestedObj?.optLong("exp", 0L) ?: 0L)
                .takeIf { it > 0L }
                ?: baseState.exp

            val parsedNeverExpires = when {
                root.has("neverExpires") -> root.optBoolean("neverExpires", false)
                nestedObj?.has("neverExpires") == true -> nestedObj.optBoolean("neverExpires", false)
                else -> parsedExp >= PERMANENT_EXP_THRESHOLD_SEC || baseState.neverExpires
            }

            // Parse global clearKeys map
            val globalClearKeys = LinkedHashMap<String, String>()
            baseState.clearKeys.forEach { (k, v) -> globalClearKeys[k] = v }
            parseHexKeyMapInto(root.optJSONObject("clearKeys"), globalClearKeys)
            parseHexKeyMapInto(nestedObj?.optJSONObject("clearKeys"), globalClearKeys)

            // Parse server-recommended player HTTP headers
            val parsedHeaders = LinkedHashMap<String, String>(baseState.headers)
            listOfNotNull(root.optJSONObject("headers"), nestedObj?.optJSONObject("headers")).forEach { hdrObj ->
                val keys = hdrObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = optCleanString(hdrObj, key)
                    if (key.isNotBlank() && value.isNotBlank()) {
                        parsedHeaders[key] = value
                    }
                }
            }

            val rawRefreshSec = root.optLong(
                "refreshIntervalSeconds",
                nestedObj?.optLong("refreshIntervalSeconds", DEFAULT_BACKGROUND_REFRESH_SEC)
                    ?: DEFAULT_BACKGROUND_REFRESH_SEC
            )
            val refreshIntervalSec = rawRefreshSec.coerceIn(MIN_BACKGROUND_REFRESH_SEC, MAX_BACKGROUND_REFRESH_SEC)

            // Defensive channels array resolution: channels ?: data ?: emptyList()
            val channelsArray: JSONArray? = root.optJSONArray("channels")
                ?: root.optJSONArray("data")
                ?: nestedObj?.optJSONArray("channels")
                ?: nestedObj?.optJSONArray("data")

            val parsedChannels = mutableListOf<LiveChannel>()
            if (channelsArray != null) {
                for (i in 0 until channelsArray.length()) {
                    val obj = channelsArray.optJSONObject(i) ?: continue
                    val channel = parseSingleChannelDefensively(
                        obj = obj,
                        index = i,
                        cdnHost = parsedCdnHost,
                        token = parsedToken,
                        globalClearKeys = globalClearKeys
                    )
                    if (channel != null) {
                        parsedChannels.add(channel)
                    }
                }
            }

            // Parse optional server announcement / notice banner from nelitv.online
            var parsedAnnouncement = ""
            var parsedAnnouncementId = ""
            val announcementObj = root.optJSONObject("announcement")
                ?: root.optJSONObject("notice")
                ?: nestedObj?.optJSONObject("announcement")
                ?: nestedObj?.optJSONObject("notice")
            if (announcementObj != null) {
                val enabled = announcementObj.optBoolean("enabled", true) &&
                    announcementObj.optBoolean("active", true)
                if (enabled) {
                    parsedAnnouncement = optCleanString(announcementObj, "message")
                        .ifBlank { optCleanString(announcementObj, "text") }
                        .ifBlank { optCleanString(announcementObj, "title") }
                    parsedAnnouncementId = optCleanString(announcementObj, "id")
                        .ifBlank { "neli_srv_${parsedAnnouncement.hashCode()}" }
                }
            } else {
                parsedAnnouncement = optCleanString(root, "announcement")
                    .ifBlank { optCleanString(root, "notice") }
                    .ifBlank { optCleanString(nestedObj, "announcement") }
                    .ifBlank { optCleanString(nestedObj, "notice") }
                if (parsedAnnouncement.isNotBlank()) {
                    parsedAnnouncementId = "neli_srv_${parsedAnnouncement.hashCode()}"
                }
            }

            // Parse optional server-driven app update metadata (strictly HTTPS APK URLs)
            val updateObj = root.optJSONObject("appUpdate")
                ?: root.optJSONObject("update")
                ?: root.optJSONObject("app_update")
                ?: nestedObj?.optJSONObject("appUpdate")
                ?: nestedObj?.optJSONObject("update")
            val parsedUpdateVersion = optCleanString(updateObj, "latestVersion")
                .ifBlank { optCleanString(updateObj, "version") }
                .ifBlank { optCleanString(updateObj, "versionName") }
                .ifBlank { optCleanString(root, "latestVersion") }
                .ifBlank { optCleanString(nestedObj, "latestVersion") }
            val rawUpdateApkUrl = normalizeRelativeUrl(
                optCleanString(updateObj, "apkUrl")
                    .ifBlank { optCleanString(updateObj, "downloadUrl") }
                    .ifBlank { optCleanString(updateObj, "url") }
                    .ifBlank { optCleanString(root, "apkUrl") }
            )
            val parsedUpdateApkUrl = rawUpdateApkUrl.takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty()
            val parsedUpdateNotes = optCleanString(updateObj, "notes")
                .ifBlank { optCleanString(updateObj, "whatsNew") }
                .ifBlank { optCleanString(updateObj, "changelog") }
                .ifBlank { optCleanString(updateObj, "message") }
            val parsedUpdateMandatory = updateObj?.optBoolean("mandatory", false)
                ?: updateObj?.optBoolean("forceUpdate", false)
                ?: false

            // Parse optional server feature flags (array of strings or boolean map)
            val parsedFeatures = linkedSetOf<String>()
            val featuresArr = root.optJSONArray("features") ?: nestedObj?.optJSONArray("features")
            if (featuresArr != null) {
                for (i in 0 until featuresArr.length()) {
                    val flag = featuresArr.optString(i, "").trim()
                    if (flag.isNotEmpty() && !flag.equals("null", ignoreCase = true)) {
                        parsedFeatures.add(flag)
                    }
                }
            }
            val featuresObj = root.optJSONObject("features") ?: nestedObj?.optJSONObject("features")
            if (featuresObj != null) {
                val keys = featuresObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().trim()
                    if (key.isNotEmpty() && featuresObj.optBoolean(key, false)) {
                        parsedFeatures.add(key)
                    }
                }
            }

            AppBootstrapState(
                token = parsedToken,
                cdnHost = parsedCdnHost,
                neverExpires = parsedNeverExpires || parsedExp >= PERMANENT_EXP_THRESHOLD_SEC,
                exp = parsedExp,
                clearKeys = globalClearKeys,
                channels = parsedChannels,
                headers = parsedHeaders,
                refreshIntervalSeconds = refreshIntervalSec,
                lastSyncedAtMs = syncedAtMs,
                isFromCache = isFromCache,
                lastErrorMessage = null,
                serverAnnouncement = parsedAnnouncement,
                serverAnnouncementId = parsedAnnouncementId,
                serverUpdateVersion = parsedUpdateVersion,
                serverUpdateApkUrl = parsedUpdateApkUrl,
                serverUpdateNotes = parsedUpdateNotes,
                serverUpdateMandatory = parsedUpdateMandatory,
                serverFeatures = parsedFeatures
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun applyServerDrivenExtras(state: AppBootstrapState) {
        val ctx = appContext ?: return
        try {
            if (state.serverAnnouncement.isNotBlank()) {
                NeliAdminManager.applyCloudAdminNotification(
                    context = ctx,
                    id = state.serverAnnouncementId.ifBlank { "neli_srv_${state.serverAnnouncement.hashCode()}" },
                    message = state.serverAnnouncement,
                    placement = AdminBannerPlacement.BELOW_SLIDER,
                    sendPushNotification = false,
                    createdAtMs = state.lastSyncedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }
            if (state.serverUpdateVersion.isNotBlank() && state.serverUpdateApkUrl.startsWith("https://", ignoreCase = true)) {
                NeliAppUpdateManager.applyCloudUpdateInfo(
                    latestVersion = state.serverUpdateVersion,
                    apkUrl = state.serverUpdateApkUrl,
                    whatsNewNotes = state.serverUpdateNotes,
                    isMandatory = state.serverUpdateMandatory
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun parseSingleChannelDefensively(
        obj: JSONObject,
        index: Int,
        cdnHost: String,
        token: String,
        globalClearKeys: Map<String, String>
    ): LiveChannel? {
        return try {
            val name = optCleanString(obj, "name")
                .ifBlank { optCleanString(obj, "title") }
            if (name.isBlank()) return null

            val slug = optCleanString(obj, "slug")
            val id = optCleanString(obj, "id")
                .ifBlank { optCleanString(obj, "_id") }
                .ifBlank { slug }
                .ifBlank { "neli_ch_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}_$index" }

            val rawFormat = optCleanString(obj, "streamFormat")
                .ifBlank { optCleanString(obj, "stream_format") }
                .ifBlank { optCleanString(obj, "format") }
                .lowercase()

            val embedUrl = normalizeRelativeUrl(
                optCleanString(obj, "embedUrl").ifBlank { optCleanString(obj, "embed_url") }
            )
            val isAzamFlag = obj.optBoolean("isAzam", false) ||
                obj.optBoolean("is_azam", false)

            var streamUrl = normalizeRelativeUrl(
                optCleanString(obj, "streamUrl")
                    .ifBlank { optCleanString(obj, "stream_url") }
                    .ifBlank { optCleanString(obj, "url") }
            )
            if (streamUrl.isBlank() && isAzamFlag && slug.isNotBlank()) {
                streamUrl = buildDynamicAzamUrl(slug = slug, cdnHost = cdnHost, token = token)
            }
            if (streamUrl.isBlank() && embedUrl.isNotBlank()) {
                streamUrl = embedUrl
            }
            if (streamUrl.isBlank()) return null

            val proxyStreamUrl = normalizeRelativeUrl(
                optCleanString(obj, "proxyStreamUrl")
                    .ifBlank { optCleanString(obj, "proxy_stream_url") }
                    .ifBlank { optCleanString(obj, "proxyUrl") }
            )
            val backupStreamUrl = normalizeRelativeUrl(
                optCleanString(obj, "backupStreamUrl")
                    .ifBlank { optCleanString(obj, "backup_url") }
            ).ifBlank { proxyStreamUrl }

            val thumbnailUrl = normalizeRelativeUrl(
                optCleanString(obj, "thumbnailUrl")
                    .ifBlank { optCleanString(obj, "logoUrl") }
                    .ifBlank { optCleanString(obj, "logo") }
            )

            val categories = parseCategoriesDefensively(obj, name)
            val drmScheme = optCleanString(obj, "drmScheme")
                .ifBlank { optCleanString(obj, "drm_scheme") }
                .ifBlank { optCleanString(obj, "encryptionType") }
                .lowercase()

            val channelClearKeys = LinkedHashMap<String, String>()
            parseHexKeyMapInto(obj.optJSONObject("clearKeys"), channelClearKeys)
            parseHexKeyMapInto(obj.optJSONObject("clear_keys"), channelClearKeys)

            val isEmbed = rawFormat == "embed" || rawFormat == "embedded" ||
                (embedUrl.isNotBlank() && streamUrl == embedUrl)
            val isDash = !isEmbed && (
                rawFormat == "dash" || rawFormat == "mpd" ||
                    streamUrl.substringBefore("?").endsWith(".mpd", ignoreCase = true) ||
                    streamUrl.contains("/live/eds/", ignoreCase = true)
                )
            val isMp4 = !isEmbed && !isDash && (
                rawFormat == "mp4" || streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true)
                )
            val resolvedFormat = when {
                isEmbed -> "embed"
                isDash -> "dash"
                isMp4 -> "mp4"
                else -> "hls"
            }

            val usesClearKey = drmScheme == "clearkey" || channelClearKeys.isNotEmpty() || (isDash && isAzamFlag && globalClearKeys.isNotEmpty())
            val effectiveEncryption = if (usesClearKey) "clearkey" else "none"

            val currentProgram = optCleanString(obj, "currentProgram")
            val nextProgram = optCleanString(obj, "nextProgram")
            val description = optCleanString(obj, "description")
                .ifBlank { currentProgram }
                .ifBlank { "${categories.firstOrNull() ?: "Live TV"} • NeliTv" }
            val audioUrl = normalizeRelativeUrl(
                optCleanString(obj, "audioUrl").ifBlank { optCleanString(obj, "audioStreamUrl") }
            )

            LiveChannel(
                id = id,
                slug = slug,
                name = name,
                description = description,
                streamUrl = streamUrl,
                backupStreamUrl = backupStreamUrl,
                proxyStreamUrl = proxyStreamUrl,
                embedUrl = embedUrl,
                streamFormat = resolvedFormat,
                thumbnailUrl = thumbnailUrl,
                categories = categories,
                language = optCleanString(obj, "language").ifBlank { if (isAzamFlag) "sw" else "en" },
                encryptionType = effectiveEncryption,
                clearKeys = channelClearKeys,
                country = optCleanString(obj, "country").ifBlank { if (isAzamFlag) "Tanzania" else "" },
                featured = obj.optBoolean("featured", isAzamFlag),
                enabled = obj.optBoolean("enabled", true),
                published = obj.optBoolean("published", true),
                audioStreamUrl = audioUrl,
                isAzam = isAzamFlag || streamUrl.contains("/live/eds/", ignoreCase = true),
                currentProgram = currentProgram,
                nextProgram = nextProgram,
                isFromNeliTvServer = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseCategoriesDefensively(obj: JSONObject, channelName: String): List<String> {
        val result = mutableListOf<String>()
        val catArr = obj.optJSONArray("category") ?: obj.optJSONArray("categories")
        if (catArr != null) {
            for (i in 0 until catArr.length()) {
                val c = catArr.optString(i, "").trim()
                if (c.isNotEmpty() && !c.equals("null", ignoreCase = true) && result.none { it.equals(c, ignoreCase = true) }) {
                    result.add(c)
                }
            }
        }
        val singleCat = optCleanString(obj, "category")
        if (singleCat.isNotEmpty() && !singleCat.startsWith("[")) {
            singleCat.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { c ->
                if (result.none { it.equals(c, ignoreCase = true) }) result.add(c)
            }
        }
        if (result.isNotEmpty()) return result
        val lower = channelName.lowercase()
        return when {
            lower.contains("sport") || lower.contains("espn") || lower.contains("bein") -> listOf("Sports")
            lower.contains("sinema") || lower.contains("movie") || lower.contains("cinema") || lower.contains("kix") -> listOf("Movies")
            lower.contains("kid") || lower.contains("cartoon") || lower.contains("pop") || lower.contains("tom and jerry") -> listOf("Kids")
            lower.contains("news") || lower.contains("habari") -> listOf("News")
            lower.contains("music") || lower.contains("trace") || lower.contains("wasafi") || lower.contains("crown") -> listOf("Music")
            else -> listOf("Entertainment")
        }
    }

    private fun parseHexKeyMapInto(obj: JSONObject?, out: MutableMap<String, String>) {
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val rawKid = keys.next()
            val rawKey = optCleanString(obj, rawKid)
            val cleanKid = rawKid.trim().replace("-", "").replace(" ", "").lowercase()
            val cleanKey = rawKey.trim().replace("-", "").replace(" ", "").lowercase()
            if (cleanKid.length == 32 && cleanKey.length == 32) {
                out[cleanKid] = cleanKey
            }
        }
    }

    private fun optCleanString(obj: JSONObject?, key: String): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return ""
        val value = obj.opt(key) ?: return ""
        if (value is JSONArray || value is JSONObject) return ""
        val str = value.toString().trim()
        return if (str.equals("null", ignoreCase = true)) "" else str
    }

    private fun normalizeRelativeUrl(raw: String): String {
        val clean = raw.trim()
        return when {
            clean.isEmpty() -> ""
            clean.startsWith("/") -> "$BASE_URL$clean"
            else -> clean
        }
    }
}
