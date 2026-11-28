package com.example.data

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
 * Backend-Proxied YouTube Video ID Client for NeliTv.
 *
 * Security & Scope:
 * - ZERO raw YouTube API keys in the APK (no `BuildConfig.YOUTUBE_API_KEY`, no direct `googleapis.com` calls).
 * - Routes single-video ID lookups strictly through the `nelitv.online` backend proxy:
 *     `GET https://nelitv.online/api/v1/youtube/video?id=<VIDEO_ID>`
 *   where the server holds `process.env.YOUTUBE_API_KEY` securely.
 * - Scoped exclusively to resolving/validating video IDs of existing in-app movies/episodes
 *   that play via YouTube URLs through Baynith TV `/embed-video/<VIDEO_ID>`.
 * - Caches responses for 10 minutes in memory and degrades gracefully when the proxy is
 *   offline/not yet deployed so `/embed-video/<VIDEO_ID>` continues playing seamlessly.
 */
object NeliYouTubeApiClient {

    const val BACKEND_PROXY_VIDEO_ENDPOINT = "${NeliTvRepository.BASE_URL}/api/v1/youtube/video"
    private const val CACHE_TTL_MS = 10L * 60L * 1000L // 10 minutes
    private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

    private data class CacheEntry<T>(
        val value: T,
        val cachedAtMs: Long
    ) {
        fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
            (nowMs - cachedAtMs) < CACHE_TTL_MS
    }

    private val videoCache = ConcurrentHashMap<String, CacheEntry<YouTubeVideoMetadata>>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Checks whether [videoId] belongs to an existing YouTube-backed item in the app's media or live catalog.
     */
    fun isExistingInAppYoutubeVideoId(videoId: String): Boolean {
        val cleanId = videoId.trim()
        if (!YOUTUBE_ID_REGEX.matches(cleanId)) return false

        val inMediaCatalog = MediaContentRepository.mediaCatalog.value.any { media ->
            media.youtubeVideoId.trim() == cleanId ||
                matchesYoutubeId(media.youtubeUrl, cleanId) ||
                matchesYoutubeId(media.streamUrl, cleanId) ||
                matchesYoutubeId(media.playbackUrl, cleanId)
        }
        if (inMediaCatalog) return true

        return ChannelRepository.liveChannelsFlow.value.any { ch ->
            matchesYoutubeId(ch.streamUrl, cleanId) ||
                matchesYoutubeId(ch.embedUrl, cleanId) ||
                matchesYoutubeId(ch.backupStreamUrl, cleanId)
        }
    }

    private fun matchesYoutubeId(url: String?, targetVideoId: String): Boolean {
        if (url.isNullOrBlank()) return false
        val extracted = NeliEmbedPlayerHelper.extractYoutubeVideoId(url)
        return extracted.equals(targetVideoId, ignoreCase = false)
    }

    /**
     * Resolves and validates a single YouTube video ID for an existing in-app YouTube movie/episode
     * through the `nelitv.online` backend proxy (`GET /api/v1/youtube/video?id=<VIDEO_ID>`).
     *
     * Returns `null` cleanly if the proxy is unreachable or not yet deployed so playback via
     * `https://baynith-tv.base44.app/embed-video/<VIDEO_ID>` proceeds with zero interruption.
     */
    suspend fun fetchVideoDetails(
        rawVideoIdOrUrl: String,
        requireCatalogMatch: Boolean = false
    ): YouTubeVideoMetadata? = withContext(Dispatchers.IO) {
        val videoId = NeliEmbedPlayerHelper.extractYoutubeVideoId(rawVideoIdOrUrl).trim()
        if (!YOUTUBE_ID_REGEX.matches(videoId)) return@withContext null

        if (requireCatalogMatch && !isExistingInAppYoutubeVideoId(videoId)) {
            return@withContext null
        }

        videoCache[videoId]?.takeIf { it.isFresh() }?.let { return@withContext it.value }

        val proxyUrl = BACKEND_PROXY_VIDEO_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("id", videoId)
            .build()

        val json = executeProxyGetJson(proxyUrl.toString()) ?: return@withContext null
        val metadata = parseProxyVideoResponse(videoId = videoId, root = json) ?: return@withContext null
        videoCache[videoId] = CacheEntry(metadata, System.currentTimeMillis())
        metadata
    }

    private fun executeProxyGetJson(url: String): JSONObject? {
        return try {
            val builder = Request.Builder()
                .url(url)
                .get()
            NeliTvRepository.buildApiHeaders().forEach { (k, v) ->
                builder.header(k, v)
            }
            httpClient.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty().trim()
                if (body.isBlank() || !body.startsWith("{")) return null
                JSONObject(body)
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Parses both the clean flat NeliTv proxy JSON contract (`{ videoId, title, ... }` or `{ data: { ... } }`)
     * and raw YouTube Data API v3 passthrough (`{ items: [ ... ] }`).
     */
    internal fun parseProxyVideoResponse(videoId: String, root: JSONObject): YouTubeVideoMetadata? {
        // 1) Raw YouTube Data API v3 passthrough shape: { "items": [ ... ] }
        val items = root.optJSONArray("items")
        if (items != null) {
            if (items.length() == 0) {
                return YouTubeVideoMetadata(
                    videoId = videoId,
                    title = "",
                    channelTitle = "",
                    thumbnailUrl = NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(videoId),
                    isEmbeddable = false,
                    isAvailable = false
                )
            }
            val item = items.optJSONObject(0) ?: return null
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

            val resolvedId = item.optString("id", videoId).trim().ifBlank { videoId }
            return YouTubeVideoMetadata(
                videoId = resolvedId,
                title = snippet?.optString("title", "").orEmpty().trim(),
                channelTitle = snippet?.optString("channelTitle", "").orEmpty().trim(),
                thumbnailUrl = extractBestThumbnail(snippet)
                    .ifBlank { NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(resolvedId) },
                durationLabel = formatIso8601Duration(contentDetails?.optString("duration", "").orEmpty()),
                viewCount = stats?.optString("viewCount", "0")?.toLongOrNull() ?: 0L,
                isEmbeddable = embeddable,
                isAvailable = available,
                embedUrl = "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$resolvedId"
            )
        }

        // 2) Clean NeliTv backend proxy shape: { "videoId": "...", ... } or { "data": { ... } }
        val payload = root.optJSONObject("data") ?: root.optJSONObject("video") ?: root
        val resolvedId = payload.optString("videoId", payload.optString("id", videoId))
            .trim()
            .ifBlank { videoId }
        if (!YOUTUBE_ID_REGEX.matches(resolvedId)) return null

        val isAvailable = payload.optBoolean(
            "isAvailable",
            payload.optBoolean("available", payload.optBoolean("ok", true))
        )
        val isEmbeddable = payload.optBoolean("isEmbeddable", payload.optBoolean("embeddable", true))
        val rawDuration = payload.optString("durationLabel", "").trim()
            .ifBlank { formatIso8601Duration(payload.optString("duration", "").trim()) }
        val rawEmbed = payload.optString("embedUrl", "").trim()
            .ifBlank { "${NeliEmbedPlayerHelper.BAYNITH_EMBED_VIDEO_BASE_URL}$resolvedId" }

        return YouTubeVideoMetadata(
            videoId = resolvedId,
            title = payload.optString("title", "").trim(),
            channelTitle = payload.optString("channelTitle", payload.optString("channel", "")).trim(),
            thumbnailUrl = payload.optString("thumbnailUrl", payload.optString("thumbnail", "")).trim()
                .ifBlank { NeliEmbedPlayerHelper.buildYoutubeThumbnailUrl(resolvedId) },
            durationLabel = rawDuration,
            viewCount = payload.optLong("viewCount", 0L),
            isEmbeddable = isEmbeddable,
            isAvailable = isAvailable,
            embedUrl = rawEmbed
        )
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
