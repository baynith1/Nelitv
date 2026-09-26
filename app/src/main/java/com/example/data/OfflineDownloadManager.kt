package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

object OfflineDownloadManager {

    private const val NOTIFICATION_CHANNEL_ID = "neli_offline_downloads_channel"
    private const val NOTIFICATION_CHANNEL_NAME = "Neli TV Background Downloads"
    private const val MIN_VALID_VIDEO_BYTES = 64 * 1024L // At least 64 KB of real media data
    private const val HLS_PARALLEL_SEGMENT_WORKERS = 8
    private const val MAX_CONCURRENT_MULTI_DOWNLOADS = 4

    /**
     * Application-scoped background coroutine scope so downloads continue automatically
     * with live progress even when the user switches screens or exits the app via Home button.
     */
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val multiDownloadSemaphore = Semaphore(MAX_CONCURRENT_MULTI_DOWNLOADS)

    private val activeDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val recentlyCompletedTimestamps = ConcurrentHashMap<String, Long>()

    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _activeDownloadTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDownloadTitles: StateFlow<Map<String, String>> = _activeDownloadTitles.asStateFlow()

    private val _downloadBannerMessage = MutableStateFlow<String?>(null)
    val downloadBannerMessage: StateFlow<String?> = _downloadBannerMessage.asStateFlow()

    fun isCurrentlyDownloading(id: String): Boolean {
        if (activeDownloadIds.contains(id) || _downloadProgress.value.containsKey(id)) return true
        val completedAt = recentlyCompletedTimestamps[id] ?: return false
        return (System.currentTimeMillis() - completedAt) < 15_000L
    }

    fun dismissBannerMessage() {
        _downloadBannerMessage.value = null
    }

    /**
     * Verifies whether a [DownloadedItemEntity] has a real, non-empty video file or HLS bundle on internal storage.
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
     * for an item that isn't actually on internal storage, while strictly protecting active and newly
     * completed downloads from race-condition deletion.
     */
    suspend fun purgeInvalidDownloads(dao: NeliMediaDao, items: List<DownloadedItemEntity>) = withContext(Dispatchers.IO) {
        for (item in items) {
            if (isCurrentlyDownloading(item.id)) continue
            val fresh = dao.getDownloadById(item.id) ?: continue
            if (isCurrentlyDownloading(fresh.id)) continue

            if (isDownloadFileValidOnDisk(fresh)) {
                continue
            }

            if (fresh.downloadStatus == "COMPLETED" && !isDownloadFileValidOnDisk(fresh)) {
                deleteOfflineFilesOnDisk(fresh.localFilePath, fresh.id, null)
                dao.deleteDownloadById(fresh.id)
            } else if (fresh.downloadStatus == "DOWNLOADING" && !isCurrentlyDownloading(fresh.id)) {
                deleteOfflineFilesOnDisk(fresh.localFilePath, fresh.id, null)
                dao.deleteDownloadById(fresh.id)
            }
        }
    }

    /**
     * Enqueues a background download using the streaming link's native quality directly.
     * Supports concurrent Multi-Download and runs inside a Foreground Service so downloads
     * continue even when the user leaves the app.
     */
    fun enqueueBackgroundDownload(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity
    ) {
        val appContext = context.applicationContext
        if (activeDownloadIds.contains(item.id)) {
            _downloadBannerMessage.value = "\"${item.title}\" is already downloading."
            return
        }
        setActiveTitle(item.id, item.title)
        updateProgress(item.id, 1)
        val totalActive = _downloadProgress.value.size
        _downloadBannerMessage.value = if (totalActive > 1) {
            "Multi-Download Active ($totalActive videos) • Added \"${item.title}\"..."
        } else {
            "Downloading \"${item.title}\" (1%)..."
        }

        syncForegroundServiceState(appContext)

        val job = backgroundScope.launch {
            multiDownloadSemaphore.withPermit {
                downloadMediaOffline(
                    context = appContext,
                    dao = dao,
                    item = item
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

    /**
     * Downloads a movie or series episode into the device's internal storage (`filesDir/offline_media/`)
     * using the streaming link's own quality (`item.streamUrl`), normalizing CDN authorization tokens if applicable.
     *
     * - For `.mp4` streams: downloads the full MP4 video file to `filesDir/offline_media/<safeId>.mp4`
     *   with automatic HTTP Range resume on network hiccups.
     * - For `.m3u8` HLS streams: downloads all video segments concurrently (8 workers with retry)
     *   into `filesDir/offline_media/hls_<safeId>/` and writes `local_playlist.m3u8` so ExoPlayer's
     *   `HlsMediaSource` plays the entire video 100% offline with full duration and timeline seeking.
     */
    suspend fun downloadMediaOffline(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity
    ): Boolean = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val rawStreamUrl = ChannelRepository.normalizeDashStreamUrl(item.streamUrl.trim())

        if (rawStreamUrl.isBlank()) {
            clearProgress(item.id)
            removeActiveTitle(item.id)
            _downloadBannerMessage.value = "Cannot download \"${item.title}\": streaming link is empty."
            return@withContext false
        }

        if (!activeDownloadIds.add(item.id)) {
            _downloadBannerMessage.value = "\"${item.title}\" is already downloading."
            return@withContext false
        }

        val existing = dao.getDownloadById(item.id)
        if (existing != null && isDownloadFileValidOnDisk(existing)) {
            activeDownloadIds.remove(item.id)
            clearProgress(item.id)
            removeActiveTitle(item.id)
            _downloadBannerMessage.value = "\"${item.title}\" is already downloaded for offline viewing."
            return@withContext false
        }

        setActiveTitle(item.id, item.title)
        updateProgress(item.id, 1)
        _downloadBannerMessage.value = "Downloading \"${item.title}\" (1%)..."
        showDownloadNotification(appContext, item.id, item.title, 1, isCompleted = false)

        val wakeLock = acquirePartialWakeLock(appContext, item.id)

        val offlineDir = File(appContext.filesDir, "offline_media").apply {
            if (!exists()) mkdirs()
        }
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

        val initialEntity = item.copy(
            streamUrl = rawStreamUrl,
            fileSizeLabel = "Starting download • 1%",
            localFilePath = "",
            downloadStatus = "DOWNLOADING",
            progressPercent = 1,
            timestamp = System.currentTimeMillis()
        )
        dao.upsertDownload(initialEntity)

        try {
            // Build candidate URLs starting with the normalized streaming link, plus BunnyCDN fallbacks
            val candidateUrls = buildList {
                add(rawStreamUrl)
                if (rawStreamUrl.contains("b-cdn.net", ignoreCase = true)) {
                    val baseDir = rawStreamUrl.substringBefore("?").substringBeforeLast("/")
                    if (rawStreamUrl.substringBefore("?").endsWith(".m3u8", ignoreCase = true)) {
                        add("$baseDir/play_720p.mp4")
                        add("$baseDir/play_480p.mp4")
                        add("$baseDir/play_360p.mp4")
                    } else {
                        add("$baseDir/play_480p.mp4")
                        add("$baseDir/play_720p.mp4")
                        add("$baseDir/play_360p.mp4")
                        add("$baseDir/playlist.m3u8")
                    }
                }
            }.distinct()

            var finalPlayableFile: File? = null
            var totalDownloadedBytes = 0L

            for (candidateUrl in candidateUrls) {
                if (!coroutineContext.isActive) throw CancellationException()

                val isHlsCandidate = isUrlOrResponseHls(candidateUrl)
                if (isHlsCandidate) {
                    val hlsBundleDir = File(offlineDir, "hls_$safeId")
                    val localPlaylistFile = File(hlsBundleDir, "local_playlist.m3u8")
                    val bytes = downloadHlsStreamToOfflinePlaylist(
                        appContext = appContext,
                        masterOrVariantUrl = candidateUrl,
                        bundleDir = hlsBundleDir,
                        localPlaylistFile = localPlaylistFile,
                        initialEntity = initialEntity,
                        dao = dao
                    )
                    if (bytes > MIN_VALID_VIDEO_BYTES && localPlaylistFile.exists() && localPlaylistFile.length() > 32L) {
                        finalPlayableFile = localPlaylistFile
                        totalDownloadedBytes = bytes
                        break
                    } else {
                        hlsBundleDir.deleteRecursively()
                    }
                } else {
                    val mp4File = File(offlineDir, "$safeId.mp4")
                    val bytes = downloadDirectMp4ToFile(
                        appContext = appContext,
                        urlStr = candidateUrl,
                        targetFile = mp4File,
                        initialEntity = initialEntity,
                        dao = dao
                    )
                    if (bytes > MIN_VALID_VIDEO_BYTES && mp4File.exists() && mp4File.length() > MIN_VALID_VIDEO_BYTES) {
                        finalPlayableFile = mp4File
                        totalDownloadedBytes = bytes
                        break
                    } else {
                        if (mp4File.exists()) mp4File.delete()
                    }
                }
            }

            // Strictly verify that real video bytes were saved on internal storage before marking COMPLETED
            val savedFile = finalPlayableFile
            if (savedFile == null || !savedFile.exists() || totalDownloadedBytes <= MIN_VALID_VIDEO_BYTES) {
                dao.deleteDownloadById(item.id)
                _downloadBannerMessage.value = "Could not download \"${item.title}\". Stream server did not return video data."
                cancelDownloadNotification(appContext, item.id)
                return@withContext false
            }

            val mb = totalDownloadedBytes.toDouble() / (1024.0 * 1024.0)
            val finalSizeLabel = String.format(Locale.US, "%.1f MB • Offline Ready", mb)

            recentlyCompletedTimestamps[item.id] = System.currentTimeMillis()

            val completedEntity = initialEntity.copy(
                localFilePath = savedFile.absolutePath,
                fileSizeLabel = finalSizeLabel,
                downloadStatus = "COMPLETED",
                progressPercent = 100
            )
            dao.upsertDownload(completedEntity)
            updateProgress(item.id, 100)
            _downloadBannerMessage.value = "Download complete: \"${item.title}\" ($finalSizeLabel) is ready to watch offline!"
            showDownloadNotification(appContext, item.id, item.title, 100, isCompleted = true)
            return@withContext true
        } catch (e: CancellationException) {
            deleteOfflineFilesOnDisk(initialEntity.localFilePath, item.id, appContext)
            dao.deleteDownloadById(item.id)
            _downloadBannerMessage.value = "Download cancelled for \"${item.title}\"."
            cancelDownloadNotification(appContext, item.id)
            return@withContext false
        } catch (e: Exception) {
            deleteOfflineFilesOnDisk(initialEntity.localFilePath, item.id, appContext)
            dao.deleteDownloadById(item.id)
            _downloadBannerMessage.value = "Download failed for \"${item.title}\". Please check your internet connection."
            cancelDownloadNotification(appContext, item.id)
            return@withContext false
        } finally {
            releaseWakeLockSafely(wakeLock)
            activeDownloadIds.remove(item.id)
            activeDownloadJobs.remove(item.id)
            removeActiveTitle(item.id)
            clearProgress(item.id)
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

    /**
     * Downloads a full `.mp4` stream without artificial size truncation so the entire video and its `moov` atom
     * are saved intact to [targetFile], supporting HTTP Range resume across transient network drops.
     */
    private suspend fun downloadDirectMp4ToFile(
        appContext: Context,
        urlStr: String,
        targetFile: File,
        initialEntity: DownloadedItemEntity,
        dao: NeliMediaDao
    ): Long {
        val normalizedUrl = ChannelRepository.normalizeDashStreamUrl(urlStr)
        var downloadedBytes = 0L
        var expectedTotalBytes = -1L
        val buffer = ByteArray(64 * 1024)
        var lastReportedPct = 1
        var lastReportTimeMs = System.currentTimeMillis()

        for (attempt in 0..3) {
            if (!coroutineContext.isActive) throw CancellationException()
            var conn: HttpURLConnection? = null
            try {
                val rangeStart = if (attempt > 0 && downloadedBytes > 0L && (expectedTotalBytes <= 0L || downloadedBytes < expectedTotalBytes)) {
                    downloadedBytes
                } else {
                    0L
                }
                conn = openHttpConnectionWithRedirects(normalizedUrl, rangeStartBytes = rangeStart)
                val code = conn.responseCode
                if (code !in 200..299) {
                    if (attempt == 0) return 0L
                    delay(400L * (attempt + 1))
                    continue
                }

                val contentType = conn.contentType.orEmpty().lowercase(Locale.US)
                if (contentType.contains("text/html") || contentType.contains("application/json")) {
                    return 0L
                }

                val isResuming = (code == HttpURLConnection.HTTP_PARTIAL && rangeStart > 0L)
                if (!isResuming) {
                    downloadedBytes = 0L
                    val len = conn.contentLengthLong
                    if (len > 0L) {
                        expectedTotalBytes = len
                    }
                } else if (expectedTotalBytes <= 0L && conn.contentLengthLong > 0L) {
                    expectedTotalBytes = rangeStart + conn.contentLengthLong
                }

                conn.inputStream.use { input ->
                    FileOutputStream(targetFile, isResuming).use { output ->
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
                                    localPath = targetFile.absolutePath,
                                    pct = pct,
                                    downloadedBytes = downloadedBytes
                                )
                            }
                        }
                        output.flush()
                    }
                }

                if (expectedTotalBytes <= 0L || downloadedBytes >= expectedTotalBytes) {
                    return downloadedBytes
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt == 3) break
                delay(400L * (attempt + 1))
            } finally {
                try {
                    conn?.inputStream?.close()
                } catch (_: Exception) {}
            }
        }

        return if (expectedTotalBytes <= 0L || downloadedBytes >= (expectedTotalBytes * 95L) / 100L) {
            downloadedBytes
        } else {
            0L
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
     * Downloads an HLS `.m3u8` stream into [bundleDir] by:
     * 1. Resolving the best variant `.m3u8` playlist from the master playlist (if master).
     * 2. Downloading any `#EXT-X-MAP` init segment (`init.mp4`) and `#EXT-X-KEY` encryption key (`key.bin`).
     * 3. Downloading all video segments (`seg_00000.ts` / `.m4s`) concurrently into [bundleDir] with automatic retries.
     * 4. Writing a self-contained local HLS playlist [localPlaylistFile] (`local_playlist.m3u8`) that points
     *    to the local segment files on internal storage so ExoPlayer's `HlsMediaSource` plays it 100% offline
     *    with full timeline & seeking support!
     */
    private suspend fun downloadHlsStreamToOfflinePlaylist(
        appContext: Context,
        masterOrVariantUrl: String,
        bundleDir: File,
        localPlaylistFile: File,
        initialEntity: DownloadedItemEntity,
        dao: NeliMediaDao
    ): Long {
        return try {
            if (bundleDir.exists()) {
                bundleDir.deleteRecursively()
            }
            bundleDir.mkdirs()

            val normalizedMasterUrl = ChannelRepository.normalizeDashStreamUrl(masterOrVariantUrl)
            val initialPlaylistText = fetchTextUrl(normalizedMasterUrl) ?: return 0L
            if (!initialPlaylistText.contains("#EXTM3U", ignoreCase = true)) {
                return 0L
            }

            // If this is a master playlist, pick the best streaming variant playlist
            val variantUrl = selectBestVariantPlaylistFromMaster(
                masterUrl = normalizedMasterUrl,
                masterContent = initialPlaylistText
            )?.let { ChannelRepository.normalizeDashStreamUrl(it) }

            val mediaPlaylistUrl = variantUrl ?: normalizedMasterUrl
            val mediaPlaylistText = if (variantUrl != null && variantUrl != normalizedMasterUrl) {
                fetchTextUrl(variantUrl) ?: return 0L
            } else {
                initialPlaylistText
            }

            val lines = mediaPlaylistText.lines()
            var headerBytesWritten = 0L
            var segmentIndex = 0
            var keyIndex = 0
            var mapIndex = 0

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
                            val bytes = downloadBinarySegmentWithRetry(absMapUrl, localMapFile)
                            if (bytes > 0L) {
                                headerBytesWritten += bytes
                                val rewrittenMap = line.replace("URI=\"$uriVal\"", "URI=\"$localMapName\"")
                                parsedEntries.add(HlsPlaylistEntry.RawDirective(rewrittenMap))
                            }
                        }
                    }

                    line.startsWith("#EXT-X-KEY:", ignoreCase = true) -> {
                        val uriVal = line.substringAfter("URI=\"", "").substringBefore("\"", "")
                        if (uriVal.isNotEmpty()) {
                            val absKeyUrl = ChannelRepository.normalizeDashStreamUrl(resolveRelativeUrl(mediaPlaylistUrl, uriVal))
                            val localKeyName = "enc_key_${keyIndex++}.bin"
                            val localKeyFile = File(bundleDir, localKeyName)
                            val bytes = downloadBinarySegmentWithRetry(absKeyUrl, localKeyFile)
                            if (bytes > 0L) {
                                headerBytesWritten += bytes
                                val rewrittenKey = line.replace("URI=\"$uriVal\"", "URI=\"$localKeyName\"")
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
                return 0L
            }

            val totalBytesAtomic = AtomicLong(headerBytesWritten)
            val completedSegmentsAtomic = AtomicInteger(0)
            val succeededSegmentIndices = ConcurrentHashMap.newKeySet<Int>()
            val progressMutex = Mutex()
            var lastReportedPct = 1
            var lastReportTimeMs = 0L
            val semaphore = Semaphore(HLS_PARALLEL_SEGMENT_WORKERS)

            coroutineScope {
                segmentTasks.map { task ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            if (!coroutineContext.isActive) throw CancellationException()
                            val localSegFile = File(bundleDir, task.localSegName)
                            val bytes = downloadBinarySegmentWithRetry(task.absSegUrl, localSegFile)
                            val doneCount = completedSegmentsAtomic.incrementAndGet()
                            if (bytes > 0L && localSegFile.exists() && localSegFile.length() > 0L) {
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
                                                localPath = localPlaylistFile.absolutePath,
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
            if (succeededSegmentIndices.isEmpty() || finalTotalBytes <= MIN_VALID_VIDEO_BYTES) {
                return 0L
            }

            val rewrittenPlaylistLines = mutableListOf<String>()
            for (entry in parsedEntries) {
                when (entry) {
                    is HlsPlaylistEntry.RawDirective -> {
                        rewrittenPlaylistLines.add(entry.line)
                    }
                    is HlsPlaylistEntry.MediaSegmentTask -> {
                        if (succeededSegmentIndices.contains(entry.segmentIndex)) {
                            rewrittenPlaylistLines.add(entry.extInfLine)
                            rewrittenPlaylistLines.add(entry.localSegName)
                        }
                    }
                }
            }

            if (rewrittenPlaylistLines.none { it.startsWith("#EXT-X-ENDLIST", ignoreCase = true) }) {
                rewrittenPlaylistLines.add("#EXT-X-ENDLIST")
            }

            localPlaylistFile.writeText(rewrittenPlaylistLines.joinToString("\n"), Charsets.UTF_8)
            finalTotalBytes
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun downloadBinarySegmentWithRetry(urlStr: String, targetFile: File, maxRetries: Int = 3): Long {
        for (attempt in 0 until maxRetries) {
            if (!coroutineContext.isActive) throw CancellationException()
            val bytes = downloadBinarySegmentToFile(urlStr, targetFile)
            if (bytes > 0L && targetFile.exists() && targetFile.length() > 0L) {
                return bytes
            }
            if (attempt < maxRetries - 1) {
                delay(200L * (attempt + 1))
            }
        }
        return 0L
    }

    private fun downloadBinarySegmentToFile(urlStr: String, targetFile: File): Long {
        var conn: HttpURLConnection? = null
        return try {
            conn = openHttpConnectionWithRedirects(urlStr)
            if (conn.responseCode !in 200..299) {
                return 0L
            }
            var bytesWritten = 0L
            val buffer = ByteArray(32 * 1024)
            conn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesWritten += read
                    }
                    output.flush()
                }
            }
            bytesWritten
        } catch (_: Exception) {
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
        val mbSoFar = downloadedBytes.toDouble() / (1024.0 * 1024.0)
        val sizeProgressLabel = String.format(
            Locale.US,
            "Downloading • %d%% (%.1f MB)",
            pct,
            mbSoFar
        )
        updateProgress(initialEntity.id, pct)
        val totalActive = _downloadProgress.value.size
        _downloadBannerMessage.value = if (totalActive > 1) {
            String.format(
                Locale.US,
                "Multi-Download (%d videos) • \"%s\" %d%% (%.1f MB)",
                totalActive,
                initialEntity.title,
                pct,
                mbSoFar
            )
        } else {
            String.format(
                Locale.US,
                "Downloading \"%s\" • %d%% (%.1f MB)",
                initialEntity.title,
                pct,
                mbSoFar
            )
        }
        dao.upsertDownload(
            initialEntity.copy(
                localFilePath = localPath,
                fileSizeLabel = sizeProgressLabel,
                progressPercent = pct,
                downloadStatus = "DOWNLOADING"
            )
        )
        showDownloadNotification(
            context = appContext,
            itemId = initialEntity.id,
            title = initialEntity.title,
            progressPct = pct,
            isCompleted = false
        )
        syncForegroundServiceState(appContext)
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
            if (context != null) {
                val safeId = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                val offlineDir = File(context.filesDir, "offline_media")
                File(offlineDir, "$safeId.mp4").takeIf { it.exists() }?.delete()
                File(offlineDir, "hls_$safeId").takeIf { it.exists() }?.deleteRecursively()
            }
        } catch (_: Exception) {}
    }

    suspend fun deleteOfflineDownload(dao: NeliMediaDao, id: String) = withContext(Dispatchers.IO) {
        activeDownloadJobs.remove(id)?.cancel()
        recentlyCompletedTimestamps.remove(id)
        val existing = dao.getDownloadById(id)
        if (existing != null) {
            deleteOfflineFilesOnDisk(existing.localFilePath, id, null)
        }
        dao.deleteDownloadById(id)
        activeDownloadIds.remove(id)
        removeActiveTitle(id)
        clearProgress(id)
    }

    /**
     * Automatically checks `filesDir/offline_media/` for a valid downloaded `.mp4` or `hls_<safeId>/local_playlist.m3u8`
     * matching [rawId] so offline playback works everywhere (Downloads tab, Movie/Series details, Next/Prev episode, In-Player VOD).
     */
    fun resolveLocalOfflineUriIfPresent(
        context: Context,
        rawId: String,
        fallbackStreamUrl: String
    ): String {
        if (fallbackStreamUrl.startsWith("file:", ignoreCase = true) || fallbackStreamUrl.startsWith("/")) {
            return fallbackStreamUrl
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

        val offlineDir = File(context.filesDir, "offline_media")
        if (!offlineDir.exists()) return fallbackStreamUrl

        for (safeId in safeCandidates) {
            // 1. Check direct MP4 file
            val mp4File = File(offlineDir, "$safeId.mp4")
            if (mp4File.exists() && mp4File.length() > MIN_VALID_VIDEO_BYTES) {
                return Uri.fromFile(mp4File).toString()
            }
            // 2. Check local HLS bundle playlist
            val hlsPlaylist = File(File(offlineDir, "hls_$safeId"), "local_playlist.m3u8")
            if (hlsPlaylist.exists() && hlsPlaylist.length() > 32L) {
                val bundleBytes = hlsPlaylist.parentFile?.listFiles()?.sumOf { it.length() } ?: 0L
                if (bundleBytes > MIN_VALID_VIDEO_BYTES) {
                    return Uri.fromFile(hlsPlaylist).toString()
                }
            }
        }
        return fallbackStreamUrl
    }

    /**
     * Resolves the playback URI for a stream or downloaded item:
     * Whenever a valid downloaded video file or local HLS `.m3u8` bundle exists on phone internal storage,
     * ALWAYS returns the local `file://` URI so offline playback works 100% offline without needing internet.
     */
    fun resolvePlayableUrl(
        streamUrl: String,
        localFilePath: String,
        context: Context? = null
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
        return streamUrl
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
                    if (isCompleted) "Offline Download Complete"
                    else "Downloading $title"
                )
                .setContentText(
                    if (isCompleted) "$title is saved to internal storage for offline viewing"
                    else "$progressPct% • Background download active"
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
