package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.NeliMediaDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

object OfflineDownloadManager {

    const val SPECIAL_DEVICE_FOLDER_NAME = "NeliPlay"
    const val SPECIAL_DEVICE_FOLDER_DISPLAY_PATH = "In-App Private Storage (Nelitv Only)"

    private const val NOTIFICATION_CHANNEL_ID = "neli_offline_downloads_channel"
    private const val NOTIFICATION_CHANNEL_NAME = "Nelitv Background Downloads"
    private const val MIN_VALID_VIDEO_BYTES = 64 * 1024L // At least 64 KB of real media data
    private const val HLS_PARALLEL_SEGMENT_WORKERS = 12
    private const val MAX_CONCURRENT_MULTI_DOWNLOADS = 4

    /**
     * Application-scoped background coroutine scope so downloads continue automatically
     * with live progress even when the user switches screens or exits the app via Home button.
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val multiDownloadSemaphore = Semaphore(MAX_CONCURRENT_MULTI_DOWNLOADS)

    private val activeDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val pausedByUserIds = ConcurrentHashMap.newKeySet<String>()
    private val recentlyCompletedTimestamps = ConcurrentHashMap<String, Long>()
    private val lastDbPersistTimeById = ConcurrentHashMap<String, Long>()
    private val downloadStartTimestamps = ConcurrentHashMap<String, Long>()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _activeDownloadTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDownloadTitles: StateFlow<Map<String, String>> = _activeDownloadTitles.asStateFlow()

    private val _activeDownloadEntities = MutableStateFlow<Map<String, DownloadedItemEntity>>(emptyMap())
    val activeDownloadEntities: StateFlow<Map<String, DownloadedItemEntity>> = _activeDownloadEntities.asStateFlow()

    private val _downloadBannerMessage = MutableStateFlow<String?>(null)
    val downloadBannerMessage: StateFlow<String?> = _downloadBannerMessage.asStateFlow()

    /**
     * Returns true ONLY while [id] is actively downloading in the background.
     * Never returns true for completed downloads so completed items are immediately clickable and playable.
     */
    fun isCurrentlyDownloading(id: String): Boolean {
        return activeDownloadIds.contains(id) ||
            _downloadProgress.value.containsKey(id) ||
            _activeDownloadEntities.value.containsKey(id)
    }

    private fun isRecentlyCompletedGracePeriod(id: String): Boolean {
        val completedAt = recentlyCompletedTimestamps[id] ?: return false
        return (System.currentTimeMillis() - completedAt) < 20_000L
    }

    fun dismissBannerMessage() {
        _downloadBannerMessage.value = null
    }

    /**
     * Automatically creates and returns the private in-app `NeliPlay` offline storage directory.
     *
     * All downloaded movies, series, and videos are stored strictly inside the app's private internal
     * storage (`filesDir/offline_media/NeliPlay`) and are never exported or duplicated to external
     * phone folders, minimizing device storage usage.
     */
    fun ensureSpecialDeviceDownloadFolder(context: Context): File {
        val appContext = context.applicationContext
        val internalBase = File(appContext.filesDir, "offline_media").apply {
            if (!exists()) mkdirs()
        }
        return File(internalBase, SPECIAL_DEVICE_FOLDER_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Returns all candidate in-app directories where offline files or partial resumes may be stored.
     */
    private fun getOfflineStorageDirectories(context: Context): List<File> {
        val appContext = context.applicationContext
        val dirs = mutableListOf<File>()
        try {
            val specialDir = ensureSpecialDeviceDownloadFolder(appContext)
            dirs.add(specialDir)
        } catch (_: Exception) {
        }
        val legacyInternal = File(appContext.filesDir, "offline_media").apply {
            if (!exists()) mkdirs()
        }
        if (dirs.none { it.absolutePath == legacyInternal.absolutePath }) {
            dirs.add(legacyInternal)
        }
        return dirs.distinctBy { it.absolutePath }
    }

    /**
     * Builds a clean, human-readable filename for saving movies & series in the device's `NeliPlay` folder
     * so users can easily identify and play them from their phone's Files app even outside the app.
     */
    fun buildCleanDeviceFileName(title: String, id: String, extension: String = "mp4"): String {
        val cleanExt = extension.trim().removePrefix(".").ifBlank { "mp4" }
        val cleanTitle = title
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-zA-Z0-9_.-]"), "")
            .trim('_')
            .take(56)
            .ifBlank { "NeliPlay_Video" }
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24)
        return "${cleanTitle}_${safeId}.$cleanExt"
    }

    /**
     * Keeps the completed video file strictly inside the app's private internal storage folder
     * without copying or exporting to external/public phone folders, saving user device storage.
     */
    private fun exportCompletedVideoToDeviceFilesFolder(
        context: Context,
        sourceFile: File,
        item: DownloadedItemEntity
    ): File {
        if (!sourceFile.exists() || sourceFile.length() <= MIN_VALID_VIDEO_BYTES) {
            return sourceFile
        }
        val specialFolder = ensureSpecialDeviceDownloadFolder(context)
        if (sourceFile.parentFile?.absolutePath == specialFolder.absolutePath) {
            return sourceFile
        }
        val ext = sourceFile.extension.lowercase(Locale.US).ifBlank { "mp4" }
        val cleanFileName = buildCleanDeviceFileName(item.title, item.id, ext)
        return try {
            val targetInAppFile = File(specialFolder, cleanFileName)
            if (sourceFile.absolutePath != targetInAppFile.absolutePath) {
                val moved = sourceFile.renameTo(targetInAppFile)
                if (!moved) {
                    sourceFile.copyTo(targetInAppFile, overwrite = true)
                    sourceFile.delete()
                }
            }
            if (targetInAppFile.exists() && targetInAppFile.length() > MIN_VALID_VIDEO_BYTES) {
                targetInAppFile
            } else {
                sourceFile
            }
        } catch (_: Exception) {
            sourceFile
        }
    }

    /**
     * Verifies whether a [DownloadedItemEntity] has a real, non-empty video file or HLS bundle on storage.
     */
    fun isDownloadFileValidOnDisk(entity: DownloadedItemEntity): Boolean {
        if (entity.downloadStatus != "COMPLETED" || entity.localFilePath.isBlank()) {
            return false
        }
        val file = File(entity.localFilePath)
        if (!file.exists()) return false

        // If it's a local HLS playlist (.m3u8), verify that the folder has downloaded segment files
        if (file.name.endsWith(".m3u8", ignoreCase = true)) {
            val parent = file.parentFile ?: return false
            val totalDirBytes = parent.listFiles()?.sumOf { it.length() } ?: 0L
            return file.length() > 32L && totalDirBytes > MIN_VALID_VIDEO_BYTES
        }

        // Otherwise it's a direct .mp4 / .ts video file
        return file.length() > MIN_VALID_VIDEO_BYTES
    }

    /**
     * Cleans up any broken/0-byte download records from Room so the UI never falsely shows "Downloaded"
     * for an item that isn't actually on internal storage, while strictly protecting active, paused/resumable,
     * and newly completed downloads from race-condition deletion.
     */
    suspend fun purgeInvalidDownloads(dao: NeliMediaDao, items: List<DownloadedItemEntity>) = withContext(Dispatchers.IO) {
        for (item in items) {
            if (isCurrentlyDownloading(item.id) || isRecentlyCompletedGracePeriod(item.id)) continue
            val fresh = dao.getDownloadById(item.id) ?: continue
            if (isCurrentlyDownloading(fresh.id) || isRecentlyCompletedGracePeriod(fresh.id)) continue

            if (isDownloadFileValidOnDisk(fresh)) {
                continue
            }

            if (fresh.downloadStatus == "COMPLETED" && !isDownloadFileValidOnDisk(fresh)) {
                deleteOfflineFilesOnDisk(fresh.localFilePath, fresh.id, null)
                dao.deleteDownloadById(fresh.id)
            }
        }
    }

    /**
     * Inspects any existing partial download on disk (`.mp4.part` or `hls_<safeId>`) to restore
     * the exact progress percentage and bytes downloaded so resuming NEVER resets UI to 1%.
     */
    fun inspectPartialDownloadState(context: Context, id: String, fallbackPct: Int = 1): Pair<Int, Long> {
        val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dirs = getOfflineStorageDirectories(context)

        for (dir in dirs) {
            val partFile = File(dir, "$safeId.mp4.part")
            val metaFile = File(dir, "$safeId.mp4.meta")
            if (partFile.exists() && partFile.length() > 0L) {
                val bytes = partFile.length()
                val expectedTotal = readExpectedTotalFromMeta(metaFile)
                val pct = if (expectedTotal > 0L) {
                    ((bytes * 99L) / expectedTotal).toInt().coerceIn(1, 99)
                } else {
                    fallbackPct.coerceIn(1, 95)
                }
                return pct to bytes
            }

            val hlsDir = File(dir, "hls_$safeId")
            if (hlsDir.exists() && hlsDir.isDirectory) {
                val segFiles = hlsDir.listFiles { f ->
                    (f.name.startsWith("seg_") || f.name.startsWith("init_map_")) &&
                        !f.name.endsWith(".tmp") &&
                        f.length() > 0L
                }.orEmpty()
                if (segFiles.isNotEmpty()) {
                    val bytes = segFiles.sumOf { it.length() }
                    val totalSegCount = readExpectedSegmentsFromMeta(File(hlsDir, "hls_meta.json"))
                    val pct = if (totalSegCount > 0) {
                        ((segFiles.size * 99) / totalSegCount).coerceIn(1, 99)
                    } else {
                        fallbackPct.coerceIn(1, 95)
                    }
                    return pct to bytes
                }
            }
        }
        return fallbackPct.coerceIn(1, 99) to 0L
    }

    /**
     * Enqueues a background download using the streaming link's native quality directly.
     * Automatically creates the special `NeliPlay` folder on device storage and preserves any existing
     * partial bytes on disk so resuming after an internet interruption continues seamlessly where it stopped.
     */
    fun enqueueBackgroundDownload(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity
    ) {
        val appContext = context.applicationContext
        // Immediately ensure the special NeliPlay folder exists on the phone's storage
        ensureSpecialDeviceDownloadFolder(appContext)

        pausedByUserIds.remove(item.id)

        if (activeDownloadIds.contains(item.id) && activeDownloadJobs[item.id]?.isActive == true) {
            _downloadBannerMessage.value = "\"${item.title}\" is already downloading."
            return
        }

        val (resumedPct, resumedBytes) = inspectPartialDownloadState(
            context = appContext,
            id = item.id,
            fallbackPct = item.progressPercent.takeIf { it in 1..99 } ?: 1
        )
        val initialPct = if (resumedBytes > 0L) resumedPct.coerceIn(1, 99) else (item.progressPercent.takeIf { it in 2..99 } ?: 1)
        val initialLabel = if (resumedBytes > 0L) {
            val mb = resumedBytes.toDouble() / (1024.0 * 1024.0)
            String.format(Locale.US, "Resuming download • %d%% (%.1f MB saved)", initialPct, mb)
        } else {
            "Starting download • $initialPct%"
        }

        val queuedEntity = item.copy(
            streamUrl = ChannelRepository.normalizeDashStreamUrl(item.streamUrl.trim()),
            fileSizeLabel = initialLabel,
            downloadStatus = "DOWNLOADING",
            progressPercent = initialPct,
            timestamp = System.currentTimeMillis()
        )

        activeDownloadIds.add(item.id)
        downloadStartTimestamps[item.id] = System.currentTimeMillis()
        setActiveEntity(item.id, queuedEntity)
        setActiveTitle(item.id, item.title)
        updateProgress(item.id, initialPct)

        val totalActive = _downloadProgress.value.size
        _downloadBannerMessage.value = if (resumedBytes > 0L) {
            "Resuming \"${item.title}\" from $initialPct%..."
        } else if (totalActive > 1) {
            "Multi-Download Active ($totalActive videos) • Added \"${item.title}\"..."
        } else {
            "Downloading \"${item.title}\" in background ($initialPct%)..."
        }

        syncForegroundServiceState(appContext)

        val job = backgroundScope.launch {
            try {
                dao.upsertDownload(queuedEntity)
            } catch (_: Exception) {
            }
            multiDownloadSemaphore.withPermit {
                downloadMediaOffline(
                    context = appContext,
                    dao = dao,
                    item = queuedEntity,
                    alreadyRegisteredActive = true
                )
            }
        }
        activeDownloadJobs[item.id] = job
    }

    /**
     * Enqueues multiple movies or episodes for simultaneous background downloading.
     */
    fun enqueueMultipleBackgroundDownloads(
        context: Context,
        dao: NeliMediaDao,
        items: List<DownloadedItemEntity>
    ) {
        items.forEach { item ->
            enqueueBackgroundDownload(context, dao, item)
        }
    }

    /**
     * Pauses an active download WITHOUT deleting its partial `.part` or HLS segment files on disk,
     * so the user or automatic network recovery can resume from the exact percentage where it paused.
     */
    fun pauseDownload(context: Context, dao: NeliMediaDao, id: String) {
        val appContext = context.applicationContext
        pausedByUserIds.add(id)
        activeDownloadJobs.remove(id)?.cancel()
        activeDownloadIds.remove(id)
        val currentPct = _downloadProgress.value[id]
        removeActiveEntity(id)
        removeActiveTitle(id)
        clearProgress(id)
        cancelDownloadNotification(appContext, id)
        syncForegroundServiceState(appContext)

        backgroundScope.launch {
            val existing = dao.getDownloadById(id) ?: return@launch
            val (diskPct, diskBytes) = inspectPartialDownloadState(
                context = appContext,
                id = id,
                fallbackPct = currentPct ?: existing.progressPercent
            )
            val savedPct = maxOf(diskPct, currentPct ?: 1, existing.progressPercent).coerceIn(1, 99)
            val mbLabel = if (diskBytes > 0L) {
                String.format(Locale.US, " (%.1f MB saved)", diskBytes.toDouble() / (1024.0 * 1024.0))
            } else ""
            val pausedEntity = existing.copy(
                downloadStatus = "PAUSED_ERROR",
                progressPercent = savedPct,
                fileSizeLabel = "Paused at $savedPct%$mbLabel • Tap Resume"
            )
            dao.upsertDownload(pausedEntity)
            _downloadBannerMessage.value = "Paused \"${existing.title}\" at $savedPct%. Tap Resume anytime."
        }
    }

    /**
     * Automatically resumes any interrupted or network-paused downloads when internet connectivity returns,
     * continuing from the exact byte/segment offset already saved on disk.
     */
    fun resumeInterruptedDownloads(context: Context, dao: NeliMediaDao) {
        val appContext = context.applicationContext
        if (!isDeviceOnline(appContext)) return
        backgroundScope.launch {
            try {
                val all = dao.getAllDownloads().first()
                for (entry in all) {
                    if (pausedByUserIds.contains(entry.id)) continue
                    if (isCurrentlyDownloading(entry.id)) continue
                    val needsResume = entry.downloadStatus == "PAUSED_ERROR" ||
                        (entry.downloadStatus == "DOWNLOADING" && !isDownloadFileValidOnDisk(entry))
                    if (needsResume && entry.streamUrl.isNotBlank()) {
                        enqueueBackgroundDownload(appContext, dao, entry)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun syncForegroundServiceState(appContext: Context) {
        val currentMap = _downloadProgress.value
        val activeCount = currentMap.size
        if (activeCount <= 0 && activeDownloadIds.isEmpty()) {
            NeliDownloadService.stopIfIdle(appContext)
            return
        }
        val avgPct = if (currentMap.isNotEmpty()) {
            currentMap.values.average().toInt().coerceIn(1, 99)
        } else 1
        val titles = _activeDownloadTitles.value.values.toList()
        val summaryTitle = when {
            activeCount > 1 -> "Multi-Download ($activeCount videos) • $avgPct%"
            titles.isNotEmpty() -> "Downloading ${titles.first()}"
            else -> "Background Download Active"
        }
        NeliDownloadService.startOrUpdate(
            context = appContext,
            activeCount = activeCount.coerceAtLeast(1),
            summaryTitle = summaryTitle,
            avgProgress = avgPct
        )
    }

    /**
     * Checks whether the device currently has an active network interface (Wi-Fi or Mobile Data),
     * including low-credit/zero-rated mobile data bundles ("low internet credit bando") so Live TV is never blocked.
     */
    fun isDeviceOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private sealed interface StreamDownloadAttemptResult {
        data class Completed(val playableFile: File, val totalBytes: Long) : StreamDownloadAttemptResult
        data class InterruptedResumable(val partialBytes: Long, val lastPct: Int) : StreamDownloadAttemptResult
        data object UnsupportedCandidate : StreamDownloadAttemptResult
    }

    /**
     * Downloads a movie or series episode into the device's special `NeliPlay` folder AND internal storage:
     *
     * - Automatically creates the `NeliPlay` special folder on device storage (`Movies/NeliPlay`).
     * - Preserves partial progress across internet interruptions (HTTP Range `.mp4.part` & segment-level HLS caching)
     *   so an interrupted download NEVER restarts from 0%.
     * - Exports completed downloads as clean, standalone video files in the device's `NeliPlay` folder AND
     *   marks them `COMPLETED` with `isCurrentlyDownloading = false` so they are immediately playable offline inside the app.
     */
    suspend fun downloadMediaOffline(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity,
        alreadyRegisteredActive: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val specialDeviceFolder = ensureSpecialDeviceDownloadFolder(appContext)
        val rawStreamUrl = ChannelRepository.normalizeDashStreamUrl(item.streamUrl.trim())

        if (rawStreamUrl.isBlank()) {
            activeDownloadIds.remove(item.id)
            removeActiveEntity(item.id)
            clearProgress(item.id)
            removeActiveTitle(item.id)
            _downloadBannerMessage.value = "Cannot download \"${item.title}\": streaming link is empty."
            return@withContext false
        }

        if (!alreadyRegisteredActive && !activeDownloadIds.add(item.id)) {
            _downloadBannerMessage.value = "\"${item.title}\" is already downloading."
            return@withContext false
        }

        val existing = dao.getDownloadById(item.id)
        if (existing != null && isDownloadFileValidOnDisk(existing)) {
            activeDownloadIds.remove(item.id)
            removeActiveEntity(item.id)
            clearProgress(item.id)
            removeActiveTitle(item.id)
            _downloadBannerMessage.value = "\"${item.title}\" is already downloaded for offline viewing."
            return@withContext true
        }

        val (resumedPct, resumedBytes) = inspectPartialDownloadState(
            context = appContext,
            id = item.id,
            fallbackPct = maxOf(item.progressPercent, existing?.progressPercent ?: 1).coerceIn(1, 99)
        )
        val startPct = if (resumedBytes > 0L) resumedPct else maxOf(1, item.progressPercent.coerceIn(1, 99))

        downloadStartTimestamps.putIfAbsent(item.id, System.currentTimeMillis())
        setActiveTitle(item.id, item.title)
        updateProgress(item.id, startPct)
        _downloadBannerMessage.value = if (resumedBytes > 0L) {
            "Resuming \"${item.title}\" ($startPct%)..."
        } else {
            "Downloading \"${item.title}\" ($startPct%)..."
        }
        showDownloadNotification(appContext, item.id, item.title, startPct, isCompleted = false)
        syncForegroundServiceState(appContext)

        val wakeLock = acquirePartialWakeLock(appContext, item.id)
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val initialEntity = item.copy(
            streamUrl = rawStreamUrl,
            fileSizeLabel = if (resumedBytes > 0L) {
                val mb = resumedBytes.toDouble() / (1024.0 * 1024.0)
                String.format(Locale.US, "Resuming • %d%% (%.1f MB)", startPct, mb)
            } else {
                "Downloading • $startPct%"
            },
            localFilePath = existing?.localFilePath.orEmpty(),
            downloadStatus = "DOWNLOADING",
            progressPercent = startPct,
            timestamp = System.currentTimeMillis()
        )
        setActiveEntity(item.id, initialEntity)
        dao.upsertDownload(initialEntity)

        try {
            // Preserve query parameters (such as CDN auth tokens) when building candidate URLs
            val querySuffix = rawStreamUrl.substringAfter("?", "").let { if (it.isNotEmpty()) "?$it" else "" }
            val hasExistingMp4Part = File(specialDeviceFolder, "$safeId.mp4.part").let { it.exists() && it.length() > 0L }
            val hasExistingHlsBundle = File(specialDeviceFolder, "hls_$safeId").let {
                it.exists() && (it.listFiles()?.isNotEmpty() == true)
            }

            val candidateUrls = buildList {
                if (hasExistingHlsBundle && !hasExistingMp4Part) {
                    // Already have partial HLS segments on disk: resume the HLS stream directly!
                    add(rawStreamUrl)
                } else if (rawStreamUrl.contains("b-cdn.net", ignoreCase = true)) {
                    val baseDir = rawStreamUrl.substringBefore("?").substringBeforeLast("/")
                    if (rawStreamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true)) {
                        add(rawStreamUrl)
                        add("$baseDir/play_480p.mp4$querySuffix")
                        add("$baseDir/play_360p.mp4$querySuffix")
                        add("$baseDir/play_720p.mp4$querySuffix")
                        add("$baseDir/playlist.m3u8$querySuffix")
                    } else {
                        add("$baseDir/play_480p.mp4$querySuffix")
                        add("$baseDir/play_360p.mp4$querySuffix")
                        add("$baseDir/play_720p.mp4$querySuffix")
                        add(rawStreamUrl)
                    }
                } else {
                    add(rawStreamUrl)
                }
            }.distinct()

            var finalPlayableFile: File? = null
            var totalDownloadedBytes = 0L
            var interruptedPartialBytes = resumedBytes
            var interruptedPct = startPct

            for (candidateUrl in candidateUrls) {
                if (!coroutineContext.isActive) throw CancellationException()

                val isHlsCandidate = isUrlOrResponseHls(candidateUrl)
                if (isHlsCandidate) {
                    val hlsBundleDir = File(specialDeviceFolder, "hls_$safeId")
                    val localPlaylistFile = File(hlsBundleDir, "local_playlist.m3u8")
                    when (
                        val outcome = downloadHlsStreamToOfflinePlaylist(
                            appContext = appContext,
                            masterOrVariantUrl = candidateUrl,
                            bundleDir = hlsBundleDir,
                            localPlaylistFile = localPlaylistFile,
                            specialDeviceFolder = specialDeviceFolder,
                            safeId = safeId,
                            initialEntity = initialEntity,
                            dao = dao
                        )
                    ) {
                        is StreamDownloadAttemptResult.Completed -> {
                            finalPlayableFile = outcome.playableFile
                            totalDownloadedBytes = outcome.totalBytes
                            break
                        }
                        is StreamDownloadAttemptResult.InterruptedResumable -> {
                            // Keep all downloaded segments on disk so retry/reconnect resumes where it left off!
                            interruptedPartialBytes = maxOf(interruptedPartialBytes, outcome.partialBytes)
                            interruptedPct = maxOf(interruptedPct, outcome.lastPct)
                            break
                        }
                        StreamDownloadAttemptResult.UnsupportedCandidate -> {
                            // Try next candidate URL
                        }
                    }
                } else {
                    val mp4File = File(specialDeviceFolder, "$safeId.mp4")
                    val partFile = File(specialDeviceFolder, "$safeId.mp4.part")
                    val metaFile = File(specialDeviceFolder, "$safeId.mp4.meta")
                    when (
                        val outcome = downloadDirectMp4ToFile(
                            appContext = appContext,
                            urlStr = candidateUrl,
                            targetFile = mp4File,
                            partFile = partFile,
                            metaFile = metaFile,
                            initialEntity = initialEntity,
                            dao = dao
                        )
                    ) {
                        is StreamDownloadAttemptResult.Completed -> {
                            finalPlayableFile = outcome.playableFile
                            totalDownloadedBytes = outcome.totalBytes
                            break
                        }
                        is StreamDownloadAttemptResult.InterruptedResumable -> {
                            // Network dropped after downloading partial bytes: NEVER delete partFile!
                            // Stop trying fallback URLs so we preserve this partial MP4 for seamless HTTP Range resume.
                            interruptedPartialBytes = maxOf(interruptedPartialBytes, outcome.partialBytes)
                            interruptedPct = maxOf(interruptedPct, outcome.lastPct)
                            break
                        }
                        StreamDownloadAttemptResult.UnsupportedCandidate -> {
                            // Candidate URL was 404/403 before any bytes were saved; try next candidate URL
                        }
                    }
                }
            }

            // Strictly verify that the complete video file exists on storage before marking COMPLETED
            val savedFile = finalPlayableFile
            if (savedFile == null || !savedFile.exists() || totalDownloadedBytes <= MIN_VALID_VIDEO_BYTES) {
                val (diskPct, diskBytes) = inspectPartialDownloadState(appContext, item.id, interruptedPct)
                val finalPausedPct = maxOf(interruptedPct, diskPct, _downloadProgress.value[item.id] ?: 1).coerceIn(1, 99)
                val finalPartialBytes = maxOf(interruptedPartialBytes, diskBytes)
                val mbSavedStr = if (finalPartialBytes > 0L) {
                    String.format(Locale.US, " (%.1f MB saved)", finalPartialBytes.toDouble() / (1024.0 * 1024.0))
                } else ""
                val pausedEntity = initialEntity.copy(
                    fileSizeLabel = "Paused at $finalPausedPct%$mbSavedStr • Auto-resumes / Tap Resume",
                    downloadStatus = "PAUSED_ERROR",
                    progressPercent = finalPausedPct
                )
                activeDownloadIds.remove(item.id)
                removeActiveEntity(item.id)
                clearProgress(item.id)
                removeActiveTitle(item.id)
                dao.upsertDownload(pausedEntity)
                _downloadBannerMessage.value = "Connection interrupted at $finalPausedPct%$mbSavedStr. Will resume from $finalPausedPct%."
                cancelDownloadNotification(appContext, item.id)
                return@withContext false
            }

            // Export/copy standalone video file into the phone's visible Movies/NeliPlay folder
            val exportedDeviceFile = if (!savedFile.name.endsWith(".m3u8", ignoreCase = true)) {
                exportCompletedVideoToDeviceFilesFolder(appContext, savedFile, initialEntity)
            } else {
                savedFile
            }
            val finalPlayablePath = if (exportedDeviceFile.exists() && exportedDeviceFile.length() > MIN_VALID_VIDEO_BYTES) {
                exportedDeviceFile.absolutePath
            } else {
                savedFile.absolutePath
            }

            val mb = totalDownloadedBytes.toDouble() / (1024.0 * 1024.0)
            val finalSizeLabel = String.format(Locale.US, "%.1f MB • Offline Ready", mb)

            recentlyCompletedTimestamps[item.id] = System.currentTimeMillis()

            val completedEntity = initialEntity.copy(
                localFilePath = finalPlayablePath,
                fileSizeLabel = finalSizeLabel,
                downloadStatus = "COMPLETED",
                progressPercent = 100,
                timestamp = System.currentTimeMillis()
            )

            // Crucial: clear active downloading flags BEFORE emitting completedEntity to Room
            // so the UI immediately transitions from "Downloading" to playable "COMPLETED" state!
            activeDownloadIds.remove(item.id)
            removeActiveEntity(item.id)
            removeActiveTitle(item.id)
            clearProgress(item.id)

            dao.upsertDownload(completedEntity)
            _downloadBannerMessage.value = "Download complete: \"${item.title}\" ($finalSizeLabel) • Tap to play offline!"
            showDownloadNotification(appContext, item.id, item.title, 100, isCompleted = true)
            return@withContext true
        } catch (e: CancellationException) {
            if (pausedByUserIds.contains(item.id)) {
                // User paused the download: keep partial files on disk so they can resume from the same spot!
                return@withContext false
            }
            deleteOfflineFilesOnDisk(initialEntity.localFilePath, item.id, appContext)
            dao.deleteDownloadById(item.id)
            _downloadBannerMessage.value = "Download removed for \"${item.title}\"."
            cancelDownloadNotification(appContext, item.id)
            return@withContext false
        } catch (e: Exception) {
            val (diskPct, diskBytes) = inspectPartialDownloadState(
                context = appContext,
                id = item.id,
                fallbackPct = _downloadProgress.value[item.id] ?: startPct
            )
            val pausedPct = maxOf(diskPct, _downloadProgress.value[item.id] ?: startPct).coerceIn(1, 99)
            val mbSavedStr = if (diskBytes > 0L) {
                String.format(Locale.US, " (%.1f MB saved)", diskBytes.toDouble() / (1024.0 * 1024.0))
            } else ""
            val pausedEntity = initialEntity.copy(
                fileSizeLabel = "Paused at $pausedPct%$mbSavedStr • Tap Resume",
                downloadStatus = "PAUSED_ERROR",
                progressPercent = pausedPct
            )
            activeDownloadIds.remove(item.id)
            removeActiveEntity(item.id)
            clearProgress(item.id)
            removeActiveTitle(item.id)
            try {
                dao.upsertDownload(pausedEntity)
            } catch (_: Exception) {
            }
            _downloadBannerMessage.value = "Network interrupted at $pausedPct%$mbSavedStr. Tap Resume to continue."
            cancelDownloadNotification(appContext, item.id)
            return@withContext false
        } finally {
            releaseWakeLockSafely(wakeLock)
            activeDownloadIds.remove(item.id)
            activeDownloadJobs.remove(item.id)
            removeActiveEntity(item.id)
            removeActiveTitle(item.id)
            clearProgress(item.id)
            lastDbPersistTimeById.remove(item.id)
            downloadStartTimestamps.remove(item.id)
            syncForegroundServiceState(appContext)
        }
    }

    /**
     * Detects whether [urlStr] is an HLS `.m3u8` playlist (by URL extension or by Content-Type / `#EXTM3U` header).
     */
    private fun isUrlOrResponseHls(urlStr: String): Boolean {
        val cleanPath = urlStr.substringBefore("?").lowercase(Locale.US)
        if (cleanPath.endsWith(".m3u8")) return true
        if (cleanPath.endsWith(".mp4")) return false

        return try {
            val conn = openHttpConnectionWithRedirects(urlStr)
            try {
                val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
                if (contentType.contains("mpegurl")) return true
                if (contentType.contains("video/mp4")) return false
                val prefix = ByteArray(16)
                val read = conn.inputStream.read(prefix)
                if (read >= 7) {
                    val text = String(prefix, 0, read, Charsets.UTF_8)
                    if (text.startsWith("#EXTM3U")) return true
                }
                false
            } finally {
                try {
                    conn.inputStream?.close()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun saveExpectedTotalToMeta(metaFile: File, expectedTotalBytes: Long, sourceUrl: String) {
        if (expectedTotalBytes <= 0L) return
        try {
            val json = JSONObject().apply {
                put("expectedTotalBytes", expectedTotalBytes)
                put("sourceUrl", sourceUrl)
            }
            metaFile.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun readExpectedTotalFromMeta(metaFile: File): Long {
        if (!metaFile.exists()) return -1L
        return try {
            val json = JSONObject(metaFile.readText(Charsets.UTF_8))
            json.optLong("expectedTotalBytes", -1L)
        } catch (_: Exception) {
            -1L
        }
    }

    private fun saveExpectedSegmentsToMeta(metaFile: File, totalSegments: Int, mediaPlaylistUrl: String) {
        if (totalSegments <= 0) return
        try {
            val json = JSONObject().apply {
                put("totalSegments", totalSegments)
                put("mediaPlaylistUrl", mediaPlaylistUrl)
            }
            metaFile.writeText(json.toString(), Charsets.UTF_8)
        } catch (_: Exception) {
        }
    }

    private fun readExpectedSegmentsFromMeta(metaFile: File): Int {
        if (!metaFile.exists()) return -1
        return try {
            val json = JSONObject(metaFile.readText(Charsets.UTF_8))
            json.optInt("totalSegments", -1)
        } catch (_: Exception) {
            -1
        }
    }

    /**
     * Downloads a full `.mp4` stream into [partFile] (`<safeId>.mp4.part`) with persistent HTTP Range
     * resume across both transient network drops AND manual/automatic retries, then atomically moves
     * the finished file to [targetFile] (`<safeId>.mp4`) once 100% complete.
     */
    private suspend fun downloadDirectMp4ToFile(
        appContext: Context,
        urlStr: String,
        targetFile: File,
        partFile: File,
        metaFile: File,
        initialEntity: DownloadedItemEntity,
        dao: NeliMediaDao
    ): StreamDownloadAttemptResult {
        val normalizedUrl = ChannelRepository.normalizeDashStreamUrl(urlStr)
        var downloadedBytes = if (partFile.exists()) partFile.length().coerceAtLeast(0L) else 0L
        var expectedTotalBytes = readExpectedTotalFromMeta(metaFile)
        val buffer = ByteArray(256 * 1024) // 256 KB high-throughput socket buffer
        var lastReportedPct = if (expectedTotalBytes > 0L && downloadedBytes > 0L) {
            ((downloadedBytes * 99L) / expectedTotalBytes).toInt().coerceIn(1, 99)
        } else {
            initialEntity.progressPercent.coerceIn(1, 99)
        }
        var lastReportTimeMs = System.currentTimeMillis()

        for (attempt in 0..5) {
            if (!coroutineContext.isActive) throw CancellationException()
            var conn: HttpURLConnection? = null
            try {
                // Always request HTTP Range from downloadedBytes (even on attempt == 0!) so we resume where we stopped!
                val rangeStart = if (downloadedBytes > 0L && (expectedTotalBytes <= 0L || downloadedBytes < expectedTotalBytes)) {
                    downloadedBytes
                } else {
                    0L
                }
                conn = openHttpConnectionWithRedirects(normalizedUrl, rangeStartBytes = rangeStart)
                val code = conn.responseCode

                // HTTP 416 Range Not Satisfiable means the partial file on disk is already >= full remote content length
                if (code == 416 && downloadedBytes > MIN_VALID_VIDEO_BYTES && partFile.exists()) {
                    if (targetFile.exists()) targetFile.delete()
                    partFile.renameTo(targetFile)
                    metaFile.delete()
                    return StreamDownloadAttemptResult.Completed(targetFile, downloadedBytes)
                }

                if (code !in 200..299) {
                    if (code in listOf(403, 404, 410) && downloadedBytes == 0L) {
                        return StreamDownloadAttemptResult.UnsupportedCandidate
                    }
                    if (attempt == 5) break
                    delay(600L * (attempt + 1))
                    continue
                }

                val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
                if (contentType.contains("text/html") || contentType.contains("application/json") || contentType.contains("mpegurl")) {
                    if (downloadedBytes == 0L) {
                        return StreamDownloadAttemptResult.UnsupportedCandidate
                    }
                    break
                }

                val isResuming = (code == HttpURLConnection.HTTP_PARTIAL && rangeStart > 0L)
                if (!isResuming) {
                    // Server returned 200 OK from byte 0
                    downloadedBytes = 0L
                    val len = conn.contentLengthLong
                    if (len > 0L) {
                        expectedTotalBytes = len
                        saveExpectedTotalToMeta(metaFile, expectedTotalBytes, normalizedUrl)
                    }
                } else {
                    val contentRange = conn.getHeaderField("Content-Range").orEmpty()
                    val slashTotal = contentRange.substringAfter("/", "").trim().toLongOrNull()
                    if (slashTotal != null && slashTotal > 0L) {
                        expectedTotalBytes = slashTotal
                        saveExpectedTotalToMeta(metaFile, expectedTotalBytes, normalizedUrl)
                    } else if (expectedTotalBytes <= 0L && conn.contentLengthLong > 0L) {
                        expectedTotalBytes = rangeStart + conn.contentLengthLong
                        saveExpectedTotalToMeta(metaFile, expectedTotalBytes, normalizedUrl)
                    }
                }

                conn.inputStream.use { input ->
                    FileOutputStream(partFile, isResuming).use { output ->
                        while (coroutineContext.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val now = System.currentTimeMillis()
                            val pct = if (expectedTotalBytes > 0L) {
                                ((downloadedBytes * 99L) / expectedTotalBytes).toInt().coerceIn(1, 99)
                            } else {
                                val mbDownloaded = downloadedBytes / (1024L * 1024L)
                                (1 + (mbDownloaded * 2).toInt()).coerceIn(1, 95)
                            }

                            if (pct > lastReportedPct || (now - lastReportTimeMs >= 700L && downloadedBytes > 0L)) {
                                lastReportedPct = pct
                                lastReportTimeMs = now
                                reportProgressUpdate(
                                    appContext = appContext,
                                    dao = dao,
                                    initialEntity = initialEntity,
                                    localPath = "",
                                    pct = pct,
                                    downloadedBytes = downloadedBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }

                val isFullyDownloaded = if (expectedTotalBytes > 0L) {
                    downloadedBytes >= expectedTotalBytes
                } else {
                    downloadedBytes > MIN_VALID_VIDEO_BYTES
                }

                if (isFullyDownloaded && downloadedBytes > MIN_VALID_VIDEO_BYTES) {
                    if (targetFile.exists()) targetFile.delete()
                    val renamed = partFile.renameTo(targetFile)
                    if (!renamed) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                    metaFile.delete()
                    return StreamDownloadAttemptResult.Completed(targetFile, targetFile.length())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt == 5) break
                delay(750L * (attempt + 1))
            } finally {
                try {
                    conn?.inputStream?.close()
                } catch (_: Exception) {}
            }
        }

        val savedPartBytes = if (partFile.exists()) partFile.length() else downloadedBytes
        return if (savedPartBytes > 0L) {
            StreamDownloadAttemptResult.InterruptedResumable(savedPartBytes, lastReportedPct.coerceIn(1, 99))
        } else {
            StreamDownloadAttemptResult.UnsupportedCandidate
        }
    }

    private sealed interface HlsPlaylistEntry {
        data class RawDirective(val line: String) : HlsPlaylistEntry
        data class MediaSegmentTask(
            val segmentIndex: Int,
            val absSegUrl: String,
            val localSegName: String,
            val extInfLine: String
        ) : HlsPlaylistEntry
    }

    /**
     * Downloads an HLS `.m3u8` stream into [bundleDir] with full segment-level resume support:
     * 1. Never deletes [bundleDir] when starting or resuming — any segments already on disk from before an
     *    internet interruption are reused immediately without re-downloading!
     * 2. Downloads each segment to a `.tmp` file first and renames to `seg_XXXXX.ts`/`.m4s` only when complete.
     * 3. Requires ALL segments to complete before marking the download `COMPLETED` (never marks a partially
     *    interrupted HLS stream as 100% completed).
     * 4. Writes `local_playlist.m3u8` with explicit `file://` URIs AND also merges unencrypted segments into
     *    a standalone video file in the phone's `NeliPlay` folder so it plays both in-app and in the phone's Files app!
     */
    private suspend fun downloadHlsStreamToOfflinePlaylist(
        appContext: Context,
        masterOrVariantUrl: String,
        bundleDir: File,
        localPlaylistFile: File,
        specialDeviceFolder: File,
        safeId: String,
        initialEntity: DownloadedItemEntity,
        dao: NeliMediaDao
    ): StreamDownloadAttemptResult {
        return try {
            if (!bundleDir.exists()) {
                bundleDir.mkdirs()
            }

            val cachedMediaPlaylistFile = File(bundleDir, "cached_media_playlist.m3u8")
            val cachedMediaUrlFile = File(bundleDir, "cached_media_url.txt")
            val hlsMetaFile = File(bundleDir, "hls_meta.json")

            val normalizedMasterUrl = ChannelRepository.normalizeDashStreamUrl(masterOrVariantUrl)

            val mediaPlaylistUrl: String
            val mediaPlaylistText: String

            if (cachedMediaPlaylistFile.exists() &&
                cachedMediaPlaylistFile.length() > 32L &&
                cachedMediaUrlFile.exists()
            ) {
                mediaPlaylistUrl = ChannelRepository.normalizeDashStreamUrl(
                    cachedMediaUrlFile.readText(Charsets.UTF_8).trim().ifBlank { normalizedMasterUrl }
                )
                mediaPlaylistText = cachedMediaPlaylistFile.readText(Charsets.UTF_8)
            } else {
                val initialPlaylistText = fetchTextUrl(normalizedMasterUrl)
                    ?: return if (bundleDir.listFiles()?.isNotEmpty() == true) {
                        val (pct, bytes) = inspectPartialDownloadState(appContext, initialEntity.id, initialEntity.progressPercent)
                        StreamDownloadAttemptResult.InterruptedResumable(bytes, pct)
                    } else {
                        StreamDownloadAttemptResult.UnsupportedCandidate
                    }

                if (!initialPlaylistText.contains("#EXTM3U", ignoreCase = true)) {
                    return StreamDownloadAttemptResult.UnsupportedCandidate
                }

                val variantUrl = selectBestVariantPlaylistFromMaster(
                    masterUrl = normalizedMasterUrl,
                    masterContent = initialPlaylistText
                )?.let { ChannelRepository.normalizeDashStreamUrl(it) }

                mediaPlaylistUrl = variantUrl ?: normalizedMasterUrl
                mediaPlaylistText = if (variantUrl != null && variantUrl != normalizedMasterUrl) {
                    fetchTextUrl(variantUrl) ?: return StreamDownloadAttemptResult.InterruptedResumable(0L, initialEntity.progressPercent)
                } else {
                    initialPlaylistText
                }

                try {
                    cachedMediaUrlFile.writeText(mediaPlaylistUrl, Charsets.UTF_8)
                    cachedMediaPlaylistFile.writeText(mediaPlaylistText, Charsets.UTF_8)
                } catch (_: Exception) {
                }
            }

            val lines = mediaPlaylistText.lines()
            var headerBytesWritten = 0L
            var segmentIndex = 0
            var keyIndex = 0
            var mapIndex = 0
            var hasEncryptionKey = false
            val initMapFiles = mutableListOf<File>()

            val parsedEntries = mutableListOf<HlsPlaylistEntry>()
            var pendingExtInfLine: String? = null

            for (rawLine in lines) {
                if (!coroutineContext.isActive) throw CancellationException()
                val line = rawLine.trim()
                if (line.isEmpty()) continue

                when {
                    line.startsWith("#EXT-X-MAP:", ignoreCase = true) -> {
                        val uriVal = line.substringAfter("URI=\"", "").substringBefore("\"", "")
                        if (uriVal.isNotEmpty()) {
                            val absMapUrl = ChannelRepository.normalizeDashStreamUrl(resolveRelativeUrl(mediaPlaylistUrl, uriVal))
                            val localMapName = "init_map_${mapIndex++}.mp4"
                            val localMapFile = File(bundleDir, localMapName)
                            val bytes = if (localMapFile.exists() && localMapFile.length() > 0L) {
                                localMapFile.length()
                            } else {
                                downloadBinarySegmentWithRetry(absMapUrl, localMapFile)
                            }
                            if (bytes > 0L && localMapFile.exists()) {
                                headerBytesWritten += bytes
                                initMapFiles.add(localMapFile)
                                val fileUriStr = Uri.fromFile(localMapFile).toString()
                                val rewrittenMap = line.replace("URI=\"$uriVal\"", "URI=\"$fileUriStr\"")
                                parsedEntries.add(HlsPlaylistEntry.RawDirective(rewrittenMap))
                            }
                        }
                    }

                    line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                        val uriVal = line.substringAfter("URI=\"", "").substringBefore("\"", "")
                        if (uriVal.isNotEmpty()) {
                            hasEncryptionKey = true
                            val absKeyUrl = ChannelRepository.normalizeDashStreamUrl(resolveRelativeUrl(mediaPlaylistUrl, uriVal))
                            val localKeyName = "enc_key_${keyIndex++}.bin"
                            val localKeyFile = File(bundleDir, localKeyName)
                            val bytes = if (localKeyFile.exists() && localKeyFile.length() > 0L) {
                                localKeyFile.length()
                            } else {
                                downloadBinarySegmentWithRetry(absKeyUrl, localKeyFile)
                            }
                            if (bytes > 0L && localKeyFile.exists()) {
                                headerBytesWritten += bytes
                                val fileUriStr = Uri.fromFile(localKeyFile).toString()
                                val rewrittenKey = line.replace("URI=\"$uriVal\"", "URI=\"$fileUriStr\"")
                                parsedEntries.add(HlsPlaylistEntry.RawDirective(rewrittenKey))
                            }
                        } else {
                            parsedEntries.add(HlsPlaylistEntry.RawDirective(line))
                        }
                    }

                    line.startsWith("#EXTINF:", ignoreCase = true) -> {
                        pendingExtInfLine = line
                    }

                    line.startsWith("#") -> {
                        parsedEntries.add(HlsPlaylistEntry.RawDirective(line))
                    }

                    else -> {
                        val cleanSeg = line.substringBefore("?")
                        if (!cleanSeg.endsWith(".m3u8", ignoreCase = true)) {
                            val absSegUrl = ChannelRepository.normalizeDashStreamUrl(resolveRelativeUrl(mediaPlaylistUrl, line))
                            val ext = when {
                                cleanSeg.endsWith(".m4s", ignoreCase = true) -> "m4s"
                                cleanSeg.endsWith(".mp4", ignoreCase = true) -> "mp4"
                                cleanSeg.endsWith(".aac", ignoreCase = true) -> "aac"
                                else -> "ts"
                            }
                            val idx = segmentIndex++
                            val localSegName = String.format(Locale.US, "seg_%05d.%s", idx, ext)
                            val extInf = pendingExtInfLine ?: "#EXTINF:4.0,"
                            pendingExtInfLine = null
                            parsedEntries.add(
                                HlsPlaylistEntry.MediaSegmentTask(
                                    segmentIndex = idx,
                                    absSegUrl = absSegUrl,
                                    localSegName = localSegName,
                                    extInfLine = extInf
                                )
                            )
                        }
                    }
                }
            }

            val segmentTasks = parsedEntries.filterIsInstance<HlsPlaylistEntry.MediaSegmentTask>()
            val totalSegmentCount = segmentTasks.size
            if (totalSegmentCount == 0) {
                return StreamDownloadAttemptResult.UnsupportedCandidate
            }
            saveExpectedSegmentsToMeta(hlsMetaFile, totalSegmentCount, mediaPlaylistUrl)

            val totalBytesAtomic = AtomicLong(headerBytesWritten)
            val completedSegmentsAtomic = AtomicInteger(0)
            val succeededSegmentIndices = ConcurrentHashMap.newKeySet<Int>()
            val progressMutex = Mutex()
            var lastReportedPct = initialEntity.progressPercent.coerceIn(1, 99)
            var lastReportTimeMs = 0L
            val semaphore = Semaphore(HLS_PARALLEL_SEGMENT_WORKERS)

            coroutineScope {
                segmentTasks.map { task ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (!coroutineContext.isActive) throw CancellationException()
                            val localSegFile = File(bundleDir, task.localSegName)
                            // Reuse segment if already downloaded before an internet interruption!
                            val bytes = if (localSegFile.exists() && localSegFile.length() > 0L) {
                                localSegFile.length()
                            } else {
                                downloadBinarySegmentWithRetry(task.absSegUrl, localSegFile)
                            }
                            if (bytes > 0L && localSegFile.exists() && localSegFile.length() > 0L) {
                                val doneCount = completedSegmentsAtomic.incrementAndGet()
                                val currentTotalBytes = totalBytesAtomic.addAndGet(bytes)
                                succeededSegmentIndices.add(task.segmentIndex)

                                val pct = ((doneCount * 99) / totalSegmentCount.coerceAtLeast(1)).coerceIn(1, 99)
                                val now = System.currentTimeMillis()
                                if (pct > lastReportedPct || (now - lastReportTimeMs >= 600L && currentTotalBytes > 0L)) {
                                    progressMutex.withLock {
                                        val recheckNow = System.currentTimeMillis()
                                        if (pct > lastReportedPct || (recheckNow - lastReportTimeMs >= 600L && currentTotalBytes > 0L)) {
                                            lastReportedPct = maxOf(lastReportedPct, pct)
                                            lastReportTimeMs = recheckNow
                                            reportProgressUpdate(
                                                appContext = appContext,
                                                dao = dao,
                                                initialEntity = initialEntity,
                                                localPath = "",
                                                pct = lastReportedPct,
                                                downloadedBytes = currentTotalBytes
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            val finalTotalBytes = totalBytesAtomic.get()
            val succeededCount = succeededSegmentIndices.size
            // Never mark an interrupted HLS download as COMPLETED unless all segments succeeded!
            val allSegmentsCompleted = succeededCount >= totalSegmentCount

            if (!allSegmentsCompleted || finalTotalBytes <= MIN_VALID_VIDEO_BYTES) {
                val partialPct = ((succeededCount * 99) / totalSegmentCount.coerceAtLeast(1)).coerceIn(1, 99)
                return StreamDownloadAttemptResult.InterruptedResumable(
                    partialBytes = finalTotalBytes,
                    lastPct = maxOf(lastReportedPct, partialPct)
                )
            }

            // Write local_playlist.m3u8 with explicit file:// URIs for every segment
            val rewrittenPlaylistLines = mutableListOf<String>()
            for (entry in parsedEntries) {
                when (entry) {
                    is HlsPlaylistEntry.RawDirective -> {
                        rewrittenPlaylistLines.add(entry.line)
                    }
                    is HlsPlaylistEntry.MediaSegmentTask -> {
                        if (succeededSegmentIndices.contains(entry.segmentIndex)) {
                            rewrittenPlaylistLines.add(entry.extInfLine)
                            val segFile = File(bundleDir, entry.localSegName)
                            rewrittenPlaylistLines.add(Uri.fromFile(segFile).toString())
                        }
                    }
                }
            }

            if (rewrittenPlaylistLines.none { it.startsWith("#EXT-X-ENDLIST", ignoreCase = true) }) {
                rewrittenPlaylistLines.add("#EXT-X-ENDLIST")
            }

            localPlaylistFile.writeText(rewrittenPlaylistLines.joinToString("\n"), Charsets.UTF_8)

            StreamDownloadAttemptResult.Completed(localPlaylistFile, finalTotalBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            val (pct, bytes) = inspectPartialDownloadState(appContext, initialEntity.id, initialEntity.progressPercent)
            StreamDownloadAttemptResult.InterruptedResumable(bytes, pct)
        }
    }

    private suspend fun downloadBinarySegmentWithRetry(urlStr: String, targetFile: File, maxRetries: Int = 4): Long {
        if (targetFile.exists() && targetFile.length() > 0L) {
            return targetFile.length()
        }
        for (attempt in 0 until maxRetries) {
            if (!coroutineContext.isActive) throw CancellationException()
            val bytes = downloadBinarySegmentToFile(urlStr, targetFile)
            if (bytes > 0L && targetFile.exists() && targetFile.length() > 0L) {
                return bytes
            }
            if (attempt < maxRetries - 1) {
                delay(300L * (attempt + 1))
            }
        }
        return 0L
    }

    /**
     * Downloads a segment atomically via a `.tmp` file so a mid-segment network drop never leaves
     * a corrupt/truncated segment file on disk.
     */
    private fun downloadBinarySegmentToFile(urlStr: String, targetFile: File): Long {
        var conn: HttpURLConnection? = null
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        return try {
            conn = openHttpConnectionWithRedirects(urlStr)
            if (conn.responseCode !in 200..299) {
                return 0L
            }
            val expectedLen = conn.contentLengthLong
            var bytesWritten = 0L
            val buffer = ByteArray(32 * 1024)
            conn.inputStream.use { input ->
                FileOutputStream(tempFile, false).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesWritten += read
                    }
                    output.flush()
                }
            }
            if (bytesWritten <= 0L || (expectedLen > 0L && bytesWritten < expectedLen)) {
                tempFile.delete()
                return 0L
            }
            if (targetFile.exists()) targetFile.delete()
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            bytesWritten
        } catch (_: Exception) {
            try {
                tempFile.delete()
            } catch (_: Exception) {}
            0L
        } finally {
            try {
                conn?.inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun reportProgressUpdate(
        appContext: Context,
        dao: NeliMediaDao,
        initialEntity: DownloadedItemEntity,
        localPath: String,
        pct: Int,
        downloadedBytes: Long
    ) {
        val now = System.currentTimeMillis()
        val startMs = downloadStartTimestamps[initialEntity.id] ?: (now - 1000L)
        val elapsedSec = ((now - startMs) / 1000.0).coerceAtLeast(0.5)
        val mbSoFar = downloadedBytes.toDouble() / (1024.0 * 1024.0)
        val speedMbPerSec = (mbSoFar / elapsedSec).coerceAtLeast(0.2)
        val safePct = pct.coerceIn(1, 99)
        val sizeProgressLabel = String.format(
            Locale.US,
            "Downloading • %d%% (%.1f MB • %.1f MB/s)",
            safePct,
            mbSoFar,
            speedMbPerSec
        )
        updateProgress(initialEntity.id, safePct)
        val updatedEntity = initialEntity.copy(
            localFilePath = localPath,
            fileSizeLabel = sizeProgressLabel,
            progressPercent = safePct,
            downloadStatus = "DOWNLOADING"
        )
        setActiveEntity(initialEntity.id, updatedEntity)

        val totalActive = _downloadProgress.value.size
        _downloadBannerMessage.value = if (totalActive > 1) {
            String.format(
                Locale.US,
                "Multi-Download (%d videos) • \"%s\" %d%% (%.1f MB)",
                totalActive,
                initialEntity.title,
                safePct,
                mbSoFar
            )
        } else {
            String.format(
                Locale.US,
                "Downloading \"%s\" • %d%% (%.1f MB • %.1f MB/s)",
                initialEntity.title,
                safePct,
                mbSoFar,
                speedMbPerSec
            )
        }

        // Throttle disk & notification IPC to once per 1200ms in a non-blocking launch so network download never stalls
        val lastPersist = lastDbPersistTimeById[initialEntity.id] ?: 0L
        if (now - lastPersist >= 1200L || safePct >= 99) {
            lastDbPersistTimeById[initialEntity.id] = now
            backgroundScope.launch {
                try {
                    val currentInDb = dao.getDownloadById(initialEntity.id)
                    if (currentInDb?.downloadStatus != "COMPLETED") {
                        dao.upsertDownload(updatedEntity)
                    }
                } catch (_: Exception) {
                }
                showDownloadNotification(
                    context = appContext,
                    itemId = initialEntity.id,
                    title = initialEntity.title,
                    progressPct = safePct,
                    isCompleted = false
                )
                syncForegroundServiceState(appContext)
            }
        }
    }

    /**
     * Picks the best variant `.m3u8` from an HLS master playlist (preferring 480p or 720p for fast, crisp mobile playback).
     */
    private fun selectBestVariantPlaylistFromMaster(
        masterUrl: String,
        masterContent: String
    ): String? {
        val lines = masterContent.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val variantCandidates = mutableListOf<String>()
        var previousWasStreamInf = false

        for (line in lines) {
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                previousWasStreamInf = true
            } else if (!line.startsWith("#")) {
                val clean = line.substringBefore("?")
                if (previousWasStreamInf || clean.endsWith(".m3u8", ignoreCase = true)) {
                    variantCandidates.add(resolveRelativeUrl(masterUrl, line))
                }
                previousWasStreamInf = false
            } else {
                previousWasStreamInf = false
            }
        }

        if (variantCandidates.isEmpty()) return null

        // Prefer 480p or 720p variant if labeled in the path, otherwise use the middle variant
        return variantCandidates.firstOrNull { it.contains("480", ignoreCase = true) }
            ?: variantCandidates.firstOrNull { it.contains("720", ignoreCase = true) }
            ?: variantCandidates.firstOrNull { it.contains("360", ignoreCase = true) }
            ?: variantCandidates[variantCandidates.size / 2]
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbsolute: String): String {
        if (relativeOrAbsolute.startsWith("http://", ignoreCase = true) ||
            relativeOrAbsolute.startsWith("https://", ignoreCase = true)
        ) {
            return relativeOrAbsolute
        }
        val baseWithoutQuery = baseUrl.substringBefore("?")
        val queryPart = baseUrl.substringAfter("?", "")
        val resolvedPath = if (relativeOrAbsolute.startsWith("/")) {
            val uri = Uri.parse(baseUrl)
            "${uri.scheme}://${uri.authority}$relativeOrAbsolute"
        } else {
            val baseDir = baseWithoutQuery.substringBeforeLast("/")
            "$baseDir/$relativeOrAbsolute"
        }
        return if (queryPart.isNotEmpty() && !resolvedPath.contains("?")) {
            "$resolvedPath?$queryPart"
        } else {
            resolvedPath
        }
    }

    /**
     * Opens an [HttpURLConnection] using the exact same headers as [com.example.player.LivePlayerController]
     * (no unauthorized Referer header that causes BunnyCDN 403) and follows HTTP/HTTPS redirects.
     */
    private fun openHttpConnectionWithRedirects(
        urlStr: String,
        maxRedirects: Int = 5,
        rangeStartBytes: Long = 0L
    ): HttpURLConnection {
        var currentUrl = ChannelRepository.normalizeDashStreamUrl(urlStr)
        var redirects = 0

        while (redirects <= maxRedirects) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 25_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "keep-alive")
                if (rangeStartBytes > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStartBytes-")
                }
            }

            val code = conn.responseCode
            if (code in listOf(
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    307,
                    308
                )
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrBlank()) {
                    currentUrl = ChannelRepository.normalizeDashStreamUrl(resolveRelativeUrl(currentUrl, location))
                    redirects++
                    continue
                }
            }
            return conn
        }
        return (URL(currentUrl).openConnection() as HttpURLConnection)
    }

    private fun fetchTextUrl(urlStr: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openHttpConnectionWithRedirects(urlStr)
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.inputStream?.close()
            } catch (_: Exception) {}
        }
    }

    private fun deleteOfflineFilesOnDisk(localFilePath: String, id: String, context: Context?) {
        try {
            if (localFilePath.isNotBlank()) {
                val file = File(localFilePath)
                if (file.name.endsWith(".m3u8", ignoreCase = true) && file.parentFile?.name?.startsWith("hls_") == true) {
                    file.parentFile?.deleteRecursively()
                } else if (file.exists()) {
                    file.delete()
                }
            }
            val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val dirs = if (context != null) {
                getOfflineStorageDirectories(context)
            } else {
                listOfNotNull(
                    localFilePath.takeIf { it.isNotBlank() }?.let { File(it).parentFile }
                )
            }
            for (dir in dirs) {
                File(dir, "$safeId.mp4").takeIf { it.exists() }?.delete()
                File(dir, "$safeId.ts").takeIf { it.exists() }?.delete()
                File(dir, "$safeId.mp4.part").takeIf { it.exists() }?.delete()
                File(dir, "$safeId.mp4.meta").takeIf { it.exists() }?.delete()
                File(dir, "hls_$safeId").takeIf { it.exists() }?.deleteRecursively()
                dir.listFiles { f -> f.name.endsWith("_$safeId.mp4") || f.name.endsWith("_$safeId.ts") }
                    ?.forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteOfflineDownload(dao: NeliMediaDao, id: String, context: Context? = null) = withContext(Dispatchers.IO) {
        pausedByUserIds.remove(id)
        activeDownloadJobs.remove(id)?.cancel()
        recentlyCompletedTimestamps.remove(id)
        val existing = dao.getDownloadById(id)
        if (existing != null) {
            deleteOfflineFilesOnDisk(existing.localFilePath, id, context)
        } else if (context != null) {
            deleteOfflineFilesOnDisk("", id, context)
        }
        dao.deleteDownloadById(id)
        activeDownloadIds.remove(id)
        removeActiveEntity(id)
        removeActiveTitle(id)
        clearProgress(id)
    }

    /**
     * Automatically checks all `NeliPlay` offline directories for a valid downloaded `.mp4`, `.ts`, or
     * `hls_<safeId>/local_playlist.m3u8` matching [rawId] so offline playback works everywhere
     * (Downloads tab, Movie/Series details, Next/Prev episode, In-Player VOD).
     */
    fun resolveLocalOfflineUriIfPresent(
        context: Context,
        rawId: String,
        fallbackStreamUrl: String
    ): String {
        if (fallbackStreamUrl.startsWith("file:", ignoreCase = true)) {
            return fallbackStreamUrl
        }
        if (fallbackStreamUrl.startsWith("/")) {
            val f = File(fallbackStreamUrl)
            if (f.exists()) return Uri.fromFile(f).toString()
        }
        val cleanId = rawId
            .removePrefix("vod_")
            .removePrefix("ep_")
            .removePrefix("dl_")
            .trim()
        if (cleanId.isBlank()) return fallbackStreamUrl

        val safeCandidates = listOf(
            cleanId.replace(Regex("[^a-zA-Z0-9_-]"), "_"),
            "ep_$cleanId".replace(Regex("[^a-zA-Z0-9_-]"), "_"),
            rawId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        ).distinct()

        val offlineDirs = getOfflineStorageDirectories(context)

        for (dir in offlineDirs) {
            if (!dir.exists()) continue
            for (safeId in safeCandidates) {
                // 1. Check direct MP4 or TS file by safeId
                val mp4File = File(dir, "$safeId.mp4")
                if (mp4File.exists() && mp4File.length() > MIN_VALID_VIDEO_BYTES) {
                    return Uri.fromFile(mp4File).toString()
                }
                val tsFile = File(dir, "$safeId.ts")
                if (tsFile.exists() && tsFile.length() > MIN_VALID_VIDEO_BYTES) {
                    return Uri.fromFile(tsFile).toString()
                }
                // 2. Check titled file in NeliPlay folder (`<Title>_<safeId>.mp4`)
                val titledMatch = dir.listFiles { f ->
                    (f.name.endsWith("_$safeId.mp4", ignoreCase = true) ||
                        f.name.endsWith("_$safeId.ts", ignoreCase = true)) &&
                        f.length() > MIN_VALID_VIDEO_BYTES
                }?.firstOrNull()
                if (titledMatch != null) {
                    return Uri.fromFile(titledMatch).toString()
                }
                // 3. Check local HLS bundle playlist
                val hlsPlaylist = File(File(dir, "hls_$safeId"), "local_playlist.m3u8")
                if (hlsPlaylist.exists() && hlsPlaylist.length() > 32L) {
                    val bundleBytes = hlsPlaylist.parentFile?.listFiles()?.sumOf { it.length() } ?: 0L
                    if (bundleBytes > MIN_VALID_VIDEO_BYTES) {
                        return Uri.fromFile(hlsPlaylist).toString()
                    }
                }
            }
        }
        return fallbackStreamUrl
    }

    /**
     * Resolves the playback URI for a stream or downloaded item:
     * Whenever a valid downloaded video file or local HLS `.m3u8` bundle exists on phone storage,
     * ALWAYS returns the local `file://` URI so offline playback works 100% offline without needing internet.
     */
    fun resolvePlayableUrl(
        streamUrl: String,
        localFilePath: String,
        context: Context? = null,
        itemId: String = ""
    ): String {
        if (localFilePath.isNotBlank()) {
            val file = File(localFilePath)
            if (file.exists()) {
                if (file.name.endsWith(".m3u8", ignoreCase = true)) {
                    val parentBytes = file.parentFile?.listFiles()?.sumOf { it.length() } ?: 0L
                    if (file.length() > 32L && parentBytes > MIN_VALID_VIDEO_BYTES) {
                        return Uri.fromFile(file).toString()
                    }
                } else if (file.length() > MIN_VALID_VIDEO_BYTES) {
                    return Uri.fromFile(file).toString()
                }
            }
        }
        if (context != null && itemId.isNotBlank()) {
            val resolvedById = resolveLocalOfflineUriIfPresent(
                context = context,
                rawId = itemId,
                fallbackStreamUrl = streamUrl
            )
            if (resolvedById.startsWith("file:", ignoreCase = true)) {
                return resolvedById
            }
        }
        return streamUrl
    }

    fun resolvePlayableUriForDownloadedEntity(
        context: Context,
        entity: DownloadedItemEntity
    ): String {
        return resolvePlayableUrl(
            streamUrl = entity.streamUrl,
            localFilePath = entity.localFilePath,
            context = context,
            itemId = entity.id
        )
    }

    fun resolveOfflineAwareChannel(
        context: Context,
        channel: com.example.model.LiveChannel,
        downloads: List<DownloadedItemEntity>
    ): com.example.model.LiveChannel {
        val rawId = channel.id.removePrefix("vod_").removePrefix("ep_").removePrefix("dl_")
        val matchedDownload = downloads.firstOrNull {
            it.id == rawId || it.id == channel.id
        }
        val resolvedUrl = resolvePlayableUrl(
            streamUrl = channel.streamUrl,
            localFilePath = matchedDownload?.localFilePath.orEmpty(),
            context = context,
            itemId = matchedDownload?.id ?: rawId
        )
        return if (resolvedUrl != channel.streamUrl) {
            channel.copy(
                streamUrl = resolvedUrl,
                streamFormat = if (resolvedUrl.endsWith(".m3u8", ignoreCase = true)) "hls" else "mp4"
            )
        } else {
            channel
        }
    }

    private fun acquirePartialWakeLock(context: Context, itemId: String): PowerManager.WakeLock? {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NeliTV:Download_$itemId")?.apply {
                acquire(30 * 60 * 1000L)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseWakeLockSafely(wakeLock: PowerManager.WakeLock?) {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        } catch (_: Exception) {}
    }

    private fun cancelDownloadNotification(context: Context, itemId: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            nm.cancel(itemId.hashCode())
        } catch (_: Exception) {}
    }

    private fun showDownloadNotification(
        context: Context,
        itemId: String,
        title: String,
        progressPct: Int,
        isCompleted: Boolean
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress for movies and series downloading in the background"
                }
                nm.createNotificationChannel(channel)
            }

            val notificationId = itemId.hashCode()
            val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(
                    if (isCompleted) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_sys_download
                )
                .setContentTitle(
                    if (isCompleted) "Download Complete • Ready Offline"
                    else "Downloading $title"
                )
                .setContentText(
                    if (isCompleted) "$title is saved inside Nelitv app (offline ready)"
                    else "$progressPct% • Saving inside Nelitv app only"
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)

            if (isCompleted) {
                builder.setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
            } else {
                builder.setOngoing(true)
                    .setProgress(100, progressPct.coerceIn(0, 100), false)
            }

            nm.notify(notificationId, builder.build())
        } catch (_: Exception) {
        }
    }

    fun retryDownload(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity
    ) {
        enqueueBackgroundDownload(context, dao, item)
    }

    private fun setActiveEntity(id: String, entity: DownloadedItemEntity) {
        val current = _activeDownloadEntities.value.toMutableMap()
        current[id] = entity
        _activeDownloadEntities.value = current
    }

    private fun removeActiveEntity(id: String) {
        val current = _activeDownloadEntities.value.toMutableMap()
        current.remove(id)
        _activeDownloadEntities.value = current
    }

    private fun updateProgress(id: String, progress: Int) {
        val current = _downloadProgress.value.toMutableMap()
        current[id] = progress
        _downloadProgress.value = current
    }

    private fun clearProgress(id: String) {
        val current = _downloadProgress.value.toMutableMap()
        current.remove(id)
        _downloadProgress.value = current
    }

    private fun setActiveTitle(id: String, title: String) {
        val current = _activeDownloadTitles.value.toMutableMap()
        current[id] = title
        _activeDownloadTitles.value = current
    }

    private fun removeActiveTitle(id: String) {
        val current = _activeDownloadTitles.value.toMutableMap()
        current.remove(id)
        _activeDownloadTitles.value = current
    }
}
