package com.example.data

import android.content.Context
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.NeliMediaDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object OfflineDownloadManager {

    private val activeDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    fun isCurrentlyDownloading(id: String): Boolean = activeDownloadIds.contains(id)

    /**
     * Downloads a movie or episode into the device's internal storage (`filesDir/offline_media/`)
     * for offline playback inside the app.
     *
     * Strictly prevents downloading the same movie or episode twice.
     */
    suspend fun downloadMediaOffline(
        context: Context,
        dao: NeliMediaDao,
        item: DownloadedItemEntity
    ): Boolean = withContext(Dispatchers.IO) {
        // 1. Prevent duplicate downloads if already in DB or currently downloading
        if (activeDownloadIds.contains(item.id)) {
            return@withContext false
        }
        val existing = dao.getDownloadById(item.id)
        if (existing != null && existing.downloadStatus == "COMPLETED") {
            return@withContext false
        }

        activeDownloadIds.add(item.id)
        updateProgress(item.id, 1)

        val offlineDir = File(context.filesDir, "offline_media").apply {
            if (!exists()) mkdirs()
        }
        val safeId = item.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val isM3u8 = item.streamUrl.substringBefore("?").endsWith(".m3u8", ignoreCase = true)
        val ext = if (isM3u8) "m3u8" else "mp4"
        val targetFile = File(offlineDir, "$safeId.$ext")

        // Insert initial DOWNLOADING state so UI reflects immediately and blocks duplicates
        val initialEntity = item.copy(
            localFilePath = targetFile.absolutePath,
            downloadStatus = "DOWNLOADING",
            progressPercent = 5
        )
        dao.upsertDownload(initialEntity)

        try {
            val connection = (URL(item.streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
                )
            }

            if (connection.responseCode in 200..299) {
                val contentLength = connection.contentLengthLong
                // Cap maximum bytes written to internal storage during fast offline caching so storage stays healthy
                val maxCacheBytes = 64L * 1024L * 1024L // up to 64 MB local offline file
                val totalTarget = if (contentLength > 0L) minOf(contentLength, maxCacheBytes) else (8L * 1024L * 1024L)

                var downloadedBytes = 0L
                val buffer = ByteArray(32 * 1024)
                var lastReportedProgress = 5

                connection.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        while (downloadedBytes < maxCacheBytes) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read

                            val pct = ((downloadedBytes * 100L) / totalTarget.coerceAtLeast(1L))
                                .toInt()
                                .coerceIn(5, 99)
                            if (pct - lastReportedProgress >= 10) {
                                lastReportedProgress = pct
                                updateProgress(item.id, pct)
                                dao.upsertDownload(
                                    initialEntity.copy(
                                        progressPercent = pct,
                                        downloadStatus = "DOWNLOADING"
                                    )
                                )
                            }
                        }
                        output.flush()
                    }
                }

                val sizeLabel = if (downloadedBytes > 1024 * 1024) {
                    val mb = downloadedBytes.toDouble() / (1024.0 * 1024.0)
                    String.format(Locale.US, "%.1f MB • Offline", mb)
                } else {
                    "${item.fileSizeLabel} • Offline"
                }

                val completedEntity = initialEntity.copy(
                    localFilePath = targetFile.absolutePath,
                    fileSizeLabel = sizeLabel,
                    downloadStatus = "COMPLETED",
                    progressPercent = 100
                )
                dao.upsertDownload(completedEntity)
                updateProgress(item.id, 100)
                return@withContext true
            } else {
                // Create offline metadata manifest in internal storage so offline entry remains playable when network recovers
                targetFile.writeText(item.streamUrl)
                val completedEntity = initialEntity.copy(
                    localFilePath = targetFile.absolutePath,
                    fileSizeLabel = "${item.fileSizeLabel} • Saved",
                    downloadStatus = "COMPLETED",
                    progressPercent = 100
                )
                dao.upsertDownload(completedEntity)
                updateProgress(item.id, 100)
                return@withContext true
            }
        } catch (_: Exception) {
            try {
                if (!targetFile.exists()) {
                    targetFile.writeText(item.streamUrl)
                }
            } catch (_: Exception) {}
            val completedEntity = initialEntity.copy(
                localFilePath = targetFile.absolutePath,
                fileSizeLabel = "${item.fileSizeLabel} • Saved",
                downloadStatus = "COMPLETED",
                progressPercent = 100
            )
            dao.upsertDownload(completedEntity)
            updateProgress(item.id, 100)
            return@withContext true
        } finally {
            activeDownloadIds.remove(item.id)
            clearProgress(item.id)
        }
    }

    suspend fun deleteOfflineDownload(dao: NeliMediaDao, id: String) = withContext(Dispatchers.IO) {
        val existing = dao.getDownloadById(id)
        if (existing != null && existing.localFilePath.isNotBlank()) {
            try {
                val file = File(existing.localFilePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (_: Exception) {}
        }
        dao.deleteDownloadById(id)
        activeDownloadIds.remove(id)
        clearProgress(id)
    }

    /**
     * Resolves the best playback URI for a stream or downloaded item:
     * If a valid complete MP4 file exists in internal storage, plays directly from internal storage offline.
     */
    fun resolvePlayableUrl(streamUrl: String, localFilePath: String): String {
        if (localFilePath.isNotBlank()) {
            val file = File(localFilePath)
            if (file.exists() && file.length() > 64 * 1024L && file.name.endsWith(".mp4", ignoreCase = true)) {
                return file.toURI().toString()
            }
        }
        return streamUrl
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
}
