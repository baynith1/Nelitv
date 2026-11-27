package com.example.data

import com.example.model.LiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ChannelHealthReport(
    val totalChecked: Int = 0,
    val healthyCount: Int = 0,
    val autoHealedCount: Int = 0,
    val lastAuditEpochMs: Long = 0L
)

/**
 * Channel Keep-Alive & Auto-Repair Guardian:
 * - Builds a complete multi-tier zero-death failover chain for every channel
 *   (Primary -> Original Backup -> Explicit Proxy -> NeliTv Proxy for Primary -> NeliTv Proxy for Backup).
 * - Remembers the last verified working URL per channel so future opens start immediately on a live stream.
 * - Periodically probes channels in the background and hot-heals dead/403/404 streams onto healthy backups.
 */
object ChannelHealthGuardian {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    // Verified working URL per channel ID (preferred on next open)
    private val verifiedWorkingUrlByChannelId = ConcurrentHashMap<String, String>()

    // Recently failed URLs per channel ID with failure timestamp (avoid retrying dead URL for 5 min)
    private val failedUrlTimestamps = ConcurrentHashMap<String, Long>()

    private const val FAILED_URL_COOLDOWN_MS = 5 * 60 * 1000L

    private val _healthReport = MutableStateFlow(ChannelHealthReport())
    val healthReport: StateFlow<ChannelHealthReport> = _healthReport.asStateFlow()

    @Volatile
    private var isAuditRunning = false

    /**
     * Returns the best currently known working URL for [channel], preferring a recently verified
     * healed stream if the primary URL recently failed.
     */
    fun resolvePreferredStreamUrl(channel: LiveChannel): String {
        val verified = verifiedWorkingUrlByChannelId[channel.id]
        if (!verified.isNullOrBlank() && !isUrlRecentlyFailed(verified)) {
            return verified
        }
        val primary = ChannelRepository.normalizeDashStreamUrl(channel.streamUrl).trim()
        if (primary.isNotBlank() && !isUrlRecentlyFailed(primary)) {
            return primary
        }
        return buildFailoverChain(channel).firstOrNull { !isUrlRecentlyFailed(it) }
            ?: primary.ifBlank { channel.backupStreamUrl }
    }

    /**
     * Builds the ordered, deduplicated zero-death failover URL chain for a Live TV channel.
     * Critically preserves distinct [LiveChannel.backupStreamUrl] BEFORE wrapping dead primary URLs in proxy.
     */
    fun buildFailoverChain(
        channel: LiveChannel,
        originalPrimaryUrl: String = channel.streamUrl,
        originalBackupUrl: String = channel.backupStreamUrl
    ): List<String> {
        val candidates = LinkedHashSet<String>()

        fun addIfValid(url: String?) {
            val clean = url?.trim().orEmpty()
            if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
                candidates.add(clean)
            }
        }

        val normalizedPrimary = ChannelRepository.normalizeDashStreamUrl(originalPrimaryUrl)
        val normalizedBackup = if (originalBackupUrl.isNotBlank()) {
            ChannelRepository.normalizeDashStreamUrl(originalBackupUrl)
        } else {
            ""
        }

        // 1. Verified healed stream if previously confirmed healthy
        verifiedWorkingUrlByChannelId[channel.id]?.let { addIfValid(it) }

        // 2. Primary stream URL
        addIfValid(normalizedPrimary)

        // 3. Distinct Backup stream URL (fixes bug where backupStreamUrl was skipped/overwritten!)
        if (normalizedBackup.isNotBlank() && normalizedBackup != normalizedPrimary) {
            addIfValid(normalizedBackup)
        }

        // 4. Explicit server proxyStreamUrl
        if (channel.proxyStreamUrl.isNotBlank()) {
            addIfValid(channel.proxyStreamUrl)
        }

        // 5. Built NeliTv server proxy for primary URL (bypasses ISP/geo/header blocks)
        if (normalizedPrimary.isNotBlank() && !normalizedPrimary.contains("/api/proxy-stream", ignoreCase = true)) {
            addIfValid(NeliTvRepository.buildProxyStreamUrl(normalizedPrimary))
        }

        // 6. Built NeliTv server proxy for backup URL
        if (normalizedBackup.isNotBlank() &&
            normalizedBackup != normalizedPrimary &&
            !normalizedBackup.contains("/api/proxy-stream", ignoreCase = true)
        ) {
            addIfValid(NeliTvRepository.buildProxyStreamUrl(normalizedBackup))
        }

        // 7. Server-cached updated channel stream URL if newer
        val cached = NeliTvRepository.findCachedChannel(channel)
        if (cached != null) {
            addIfValid(ChannelRepository.normalizeDashStreamUrl(cached.streamUrl))
            if (cached.proxyStreamUrl.isNotBlank()) addIfValid(cached.proxyStreamUrl)
            if (cached.backupStreamUrl.isNotBlank()) addIfValid(cached.backupStreamUrl)
        }

        return candidates.toList()
    }

    /**
     * Picks the next untried failover candidate URL after [failedUrl] errored out.
     */
    fun getNextFailoverCandidate(
        channel: LiveChannel,
        failedUrl: String,
        triedUrls: Set<String>,
        originalPrimaryUrl: String,
        originalBackupUrl: String
    ): String? {
        markStreamFailed(failedUrl)
        val chain = buildFailoverChain(channel, originalPrimaryUrl, originalBackupUrl)
        return chain.firstOrNull { candidate ->
            candidate != failedUrl && candidate !in triedUrls
        }
    }

    fun markStreamHealthy(channelId: String, workingUrl: String) {
        val clean = workingUrl.trim()
        if (channelId.isBlank() || clean.isBlank()) return
        verifiedWorkingUrlByChannelId[channelId] = clean
        failedUrlTimestamps.remove(clean)
    }

    fun markStreamFailed(url: String) {
        val clean = url.trim()
        if (clean.isNotBlank()) {
            failedUrlTimestamps[clean] = System.currentTimeMillis()
        }
    }

    fun isUrlRecentlyFailed(url: String): Boolean {
        val failedAt = failedUrlTimestamps[url.trim()] ?: return false
        val expired = (System.currentTimeMillis() - failedAt) > FAILED_URL_COOLDOWN_MS
        if (expired) {
            failedUrlTimestamps.remove(url.trim())
            return false
        }
        return true
    }

    /**
     * Runs a gentle, non-blocking background audit of Live TV channels,
     * pre-healing any dead primary streams onto their working backup/proxy streams automatically.
     */
    fun runBackgroundHealthAudit(channels: List<LiveChannel>) {
        if (isAuditRunning || channels.isEmpty()) return
        val lastRun = _healthReport.value.lastAuditEpochMs
        if (lastRun > 0L && (System.currentTimeMillis() - lastRun) < 10 * 60 * 1000L) return

        isAuditRunning = true
        scope.launch {
            var checked = 0
            var healthy = 0
            var healed = 0
            try {
                // Probe non-embedded HLS/DASH channels gently (up to 30 top channels per cycle)
                val candidates = channels.filter { it.isLiveBroadcast && !it.isEmbedded }.take(30)
                for (ch in candidates) {
                    checked++
                    val primary = ChannelRepository.normalizeDashStreamUrl(ch.streamUrl)
                    if (isUrlReachable(primary)) {
                        markStreamHealthy(ch.id, primary)
                        healthy++
                    } else {
                        markStreamFailed(primary)
                        val backups = buildFailoverChain(ch).drop(1)
                        val workingBackup = backups.firstOrNull { isUrlReachable(it) }
                        if (workingBackup != null) {
                            markStreamHealthy(ch.id, workingBackup)
                            healthy++
                            healed++
                        }
                    }
                }
                _healthReport.value = ChannelHealthReport(
                    totalChecked = checked,
                    healthyCount = healthy,
                    autoHealedCount = healed,
                    lastAuditEpochMs = System.currentTimeMillis()
                )
            } catch (_: Exception) {
            } finally {
                isAuditRunning = false
            }
        }
    }

    private fun isUrlReachable(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "NeliTv/2.5 (Android)")
                .header("X-Neli-Client", "nelitv-app")
                .build()
            probeClient.newCall(req).execute().use { res ->
                res.code in 200..399 || res.code == 405
            }
        } catch (_: Exception) {
            false
        }
    }

    fun resetForTesting() {
        verifiedWorkingUrlByChannelId.clear()
        failedUrlTimestamps.clear()
        _healthReport.value = ChannelHealthReport()
        isAuditRunning = false
    }
}
