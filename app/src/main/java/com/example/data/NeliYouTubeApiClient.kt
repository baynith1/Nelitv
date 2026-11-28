package com.example.data

import com.example.BuildConfig
import com.example.player.NeliEmbedPlayerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class YouTubeVideoMetadata(
    val videoId: String,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val durationLabel: String = "",
    val viewCount: Long = 0L,
    val isEmbeddable: Boolean = true,
    val isAvailable: Boolean = true,
    val embedUrl: String = "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$videoId"
)

/**
 * Official YouTube Data API v3 client supporting Baynith TV `/embed-video/<VIDEO_ID>` playback.
 *
 * - Reads `BuildConfig.YOUTUBE_API_KEY` injected via Secrets Gradle Plugin (`.env` / AI Studio Secrets).
 * - Never scrapes YouTube; calls only official `videos.list`, `playlistItems.list`, and `search.list`.
 * - Caches responses for 10 minutes to minimize API quota usage.
 * - Gracefully degrades when API key is not configured so `/embed-video/<VIDEO_ID>` still plays directly.
 */
object NeliYouTubeApiClient {

    private const val BASE_URL = "https://www.googleapis.com/youtube/v3"
    private const val CACHE_TTL_MS = 10L * 60L * 1000L // 10 minutes

    private data class CacheEntry<T>(
        val value: T,
        val cachedAtMs: Long
    ) {
        fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
            (nowMs - cachedAtMs) < CACHE_TTL_MS
    }

    private val videoCache = ConcurrentHashMap<String, CacheEntry<YouTubeVideoMetadata>>()
    private val playlistCache = ConcurrentHashMap<String, CacheEntry<List<YouTubeVideoMetadata>>>()
    private val searchCache = ConcurrentHashMap<String, CacheEntry<List<YouTubeVideoMetadata>>>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveApiKey(): String {
        val key = BuildConfig.YOUTUBE_API_KEY.trim()
        if (key.isBlank() || key.equals("YOUR_YOUTUBE_API_KEY", ignoreCase = true)) {
            return ""
        }
        return key
    }

    fun isApiKeyConfigured(): Boolean = resolveApiKey().isNotBlank()

    /**
     * Fetches official metadata, channel attribution, duration, and embeddability for a YouTube video ID.
     * Costs 1 quota unit per uncached call. Returns null if the API key is unconfigured or network fails.
     */
    suspend fun fetchVideoDetails(rawVideoIdOrUrl: String): YouTubeVideoMetadata? = withContext(Dispatchers.IO) {
        val videoId = NeliEmbedPlayerHelper.extractYoutubeVideoId(rawVideoIdOrUrl)
        if (videoId.isBlank() || videoId.startsWith("BTV-", ignoreCase = true)) return@withContext null

        videoCache[videoId]?.takeIf { it.isFresh() }?.let { return@withContext it.value }

        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return@withContext null

        val url = "$BASE_URL/videos".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet,contentDetails,status,statistics")
            .addQueryParameter("id", videoId)
            .addQueryParameter("key", apiKey)
            .build()

        val json = executeGetJson(url.toString()) ?: return@withContext null
        val items = json.optJSONArray("items")
        if (items == null || items.length() == 0) {
            val unavailable = YouTubeVideoMetadata(
                videoId = videoId,
                title = "",
                channelTitle = "",
                thumbnailUrl = NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(videoId),
                isEmbeddable = false,
                isAvailable = false
            )
            videoCache[videoId] = CacheEntry(unavailable, System.currentTimeMillis())
            return@withContext unavailable
        }

        val item = items.optJSONObject(0) ?: return@withContext null
        val snippet = item.optJSONObject("snippet")
        val contentDetails = item.optJSONObject("contentDetails")
        val status = item.optJSONObject("status")
        val stats = item.optJSONObject("statistics")

        val privacyStatus = status?.optString("privacyStatus", "public").orEmpty().lowercase()
        val uploadStatus = status?.optString("uploadStatus", "processed").orEmpty().lowercase()
        val embeddable = status?.optBoolean("embeddable", true) ?: true
        val available = privacyStatus != "private" &&
            uploadStatus != "rejected" &&
            uploadStatus != "deleted" &&
            uploadStatus != "failed"

        val metadata = YouTubeVideoMetadata(
            videoId = videoId,
            title = snippet?.optString("title", "").orEmpty().trim(),
            channelTitle = snippet?.optString("channelTitle", "").orEmpty().trim(),
            thumbnailUrl = extractBestThumbnail(snippet)
                .ifBlank { NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(videoId) },
            durationLabel = formatIso8601Duration(contentDetails?.optString("duration", "").orEmpty()),
            viewCount = stats?.optString("viewCount", "0")?.toLongOrNull() ?: 0L,
            isEmbeddable = embeddable,
            isAvailable = available,
            embedUrl = "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$videoId"
        )
        videoCache[videoId] = CacheEntry(metadata, System.currentTimeMillis())
        metadata
    }

    /**
     * Fetches ordered episodes from a YouTube playlist (`playlistItems.list`, 1 quota unit).
     * Automatically drops deleted/private items so no dead cards appear.
     */
    suspend fun fetchPlaylistVideos(
        playlistIdOrUrl: String,
        maxResults: Int = 50
    ): List<YouTubeVideoMetadata> = withContext(Dispatchers.IO) {
        val playlistId = NeliEmbedPlayerHelper.extractYoutubePlaylistId(playlistIdOrUrl)
            .ifBlank { playlistIdOrUrl.trim() }
        if (playlistId.isBlank()) return@withContext emptyList()

        playlistCache[playlistId]?.takeIf { it.isFresh() }?.let { return@withContext it.value }

        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/playlistItems".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet,contentDetails,status")
            .addQueryParameter("playlistId", playlistId)
            .addQueryParameter("maxResults", maxResults.coerceIn(1, 50).toString())
            .addQueryParameter("key", apiKey)
            .build()

        val json = executeGetJson(url.toString()) ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        val result = ArrayList<YouTubeVideoMetadata>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val snippet = item.optJSONObject("snippet") ?: continue
            val contentDetails = item.optJSONObject("contentDetails")
            val status = item.optJSONObject("status")
            val privacy = status?.optString("privacyStatus", "public").orEmpty().lowercase()
            val title = snippet.optString("title", "").trim()
            if (privacy == "private" ||
                title.equals("Private video", ignoreCase = true) ||
                title.equals("Deleted video", ignoreCase = true)
            ) {
                continue
            }
            val vid = contentDetails?.optString("videoId", "").orEmpty()
                .ifBlank { snippet.optJSONObject("resourceId")?.optString("videoId", "").orEmpty() }
                .trim()
            if (vid.isBlank()) continue

            result.add(
                YouTubeVideoMetadata(
                    videoId = vid,
                    title = title,
                    channelTitle = snippet.optString("videoOwnerChannelTitle", "")
                        .ifBlank { snippet.optString("channelTitle", "") }
                        .trim(),
                    thumbnailUrl = extractBestThumbnail(snippet)
                        .ifBlank { NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(vid) },
                    isEmbeddable = true,
                    isAvailable = true,
                    embedUrl = "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$vid"
                )
            )
        }
        playlistCache[playlistId] = CacheEntry(result, System.currentTimeMillis())
        result
    }

    /**
     * Searches embeddable YouTube videos (`search.list`) with 10-minute caching.
     */
    suspend fun searchVideos(
        query: String,
        maxResults: Int = 15
    ): List<YouTubeVideoMetadata> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val cacheKey = "${cleanQuery.lowercase()}|$maxResults"
        searchCache[cacheKey]?.takeIf { it.isFresh() }?.let { return@withContext it.value }

        val apiKey = resolveApiKey()
        if (apiKey.isBlank()) return@withContext emptyList()

        val url = "$BASE_URL/search".toHttpUrl().newBuilder()
            .addQueryParameter("part", "snippet")
            .addQueryParameter("type", "video")
            .addQueryParameter("videoEmbeddable", "true")
            .addQueryParameter("maxResults", maxResults.coerceIn(1, 25).toString())
            .addQueryParameter("q", cleanQuery)
            .addQueryParameter("key", apiKey)
            .build()

        val json = executeGetJson(url.toString()) ?: return@withContext emptyList()
        val items = json.optJSONArray("items") ?: return@withContext emptyList()
        val result = ArrayList<YouTubeVideoMetadata>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val vid = item.optJSONObject("id")?.optString("videoId", "").orEmpty().trim()
            val snippet = item.optJSONObject("snippet") ?: continue
            if (vid.isBlank()) continue
            result.add(
                YouTubeVideoMetadata(
                    videoId = vid,
                    title = snippet.optString("title", "").trim(),
                    channelTitle = snippet.optString("channelTitle", "").trim(),
                    thumbnailUrl = extractBestThumbnail(snippet)
                        .ifBlank { NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(vid) },
                    isEmbeddable = true,
                    isAvailable = true,
                    embedUrl = "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$vid"
                )
            )
        }
        searchCache[cacheKey] = CacheEntry(result, System.currentTimeMillis())
        result
    }

    private fun executeGetJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                JSONObject(body)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractBestThumbnail(snippet: JSONObject?): String {
        val thumbs = snippet?.optJSONObject("thumbnails") ?: return ""
        val keys = listOf("maxres", "standard", "high", "medium", "default")
        for (key in keys) {
            val url = thumbs.optJSONObject(key)?.optString("url", "").orEmpty().trim()
            if (url.isNotBlank()) return url
        }
        return ""
    }

    internal fun formatIso8601Duration(iso: String): String {
        if (iso.isBlank()) return ""
        val match = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(iso.trim()) ?: return ""
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            seconds > 0 -> "${seconds}s"
            else -> ""
        }
    }
}
