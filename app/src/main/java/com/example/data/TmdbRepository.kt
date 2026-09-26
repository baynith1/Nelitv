package com.example.data

import com.example.BuildConfig
import com.example.model.CastMember
import com.example.model.MediaContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TmdbEnrichmentResult(
    val tmdbId: Int,
    val posterUrl: String,
    val backdropUrl: String,
    val cast: List<CastMember>
)

/**
 * Integrates The Movie Database (TMDB) v3 API for fetching real cast members (actors, characters,
 * and profile photos) as well as official movie/series poster and backdrop URLs.
 * Uses high-concurrency connection pooling and in-memory edge caching to support millions of users.
 */
object TmdbRepository {

    const val TMDB_IMAGE_W500 = "https://image.tmdb.org/t/p/w500"
    const val TMDB_IMAGE_W780 = "https://image.tmdb.org/t/p/w780"
    const val TMDB_IMAGE_W185 = "https://image.tmdb.org/t/p/w185"

    private val enrichmentCache = ConcurrentHashMap<String, TmdbEnrichmentResult>()

    private val httpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 16
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    fun resolveTmdbApiKey(): String {
        val fromBuildConfig = try {
            BuildConfig.TMDB_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
        return if (fromBuildConfig.isNotBlank() &&
            !fromBuildConfig.equals("YOUR_TMDB_API_KEY", ignoreCase = true) &&
            !fromBuildConfig.equals("null", ignoreCase = true)
        ) {
            fromBuildConfig
        } else {
            ""
        }
    }

    /**
     * Parses a TMDB `/credits` JSON response into a list of [CastMember] with real TMDB profile URLs.
     */
    fun parseTmdbCreditsJson(creditsJson: String, maxCast: Int = 14): List<CastMember> {
        if (creditsJson.isBlank()) return emptyList()
        return try {
            val root = JSONObject(creditsJson)
            val castArr = root.optJSONArray("cast") ?: return emptyList()
            val result = mutableListOf<CastMember>()
            val count = minOf(castArr.length(), maxCast)
            for (i in 0 until count) {
                val item = castArr.optJSONObject(i) ?: continue
                val name = item.optString("name", "").trim()
                    .ifBlank { item.optString("original_name", "").trim() }
                if (name.isBlank()) continue
                val character = item.optString("character", "").trim()
                    .ifBlank { item.optString("known_for_department", "Actor").trim() }
                val profilePath = item.optString("profile_path", "").trim()
                val avatarUrl = if (profilePath.startsWith("/")) {
                    "$TMDB_IMAGE_W185$profilePath"
                } else if (profilePath.startsWith("http", ignoreCase = true)) {
                    profilePath
                } else {
                    ""
                }
                val id = item.optInt("id", i + 1).toString()
                result.add(
                    CastMember(
                        id = "tmdb_cast_$id",
                        name = name,
                        role = character.ifBlank { "Cast" },
                        avatarUrl = avatarUrl
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches real TMDB poster URL, backdrop URL, and Cast & Crew for the given [media].
     * Returns cached data immediately if already resolved.
     */
    suspend fun enrichMediaWithTmdb(
        media: MediaContent,
        explicitApiKey: String = resolveTmdbApiKey()
    ): TmdbEnrichmentResult? = withContext(Dispatchers.IO) {
        enrichmentCache[media.id]?.let { return@withContext it }
        if (explicitApiKey.isBlank()) return@withContext null

        try {
            val mediaType = if (media.isSeries) "tv" else "movie"
            var tmdbId = media.tmdbId.toInt()
            var posterUrl = media.posterUrl
            var backdropUrl = media.backdropUrl
            var castList = emptyList<CastMember>()

            // Fast path: if media already has a tmdbId, fetch details + credits in one single HTTP call
            if (tmdbId > 0) {
                val directUrl =
                    "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=$explicitApiKey&append_to_response=credits"
                val directReq = Request.Builder().url(directUrl).get().build()
                httpClient.newCall(directReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val json = JSONObject(body)
                        val pPath = json.optString("poster_path", "").trim()
                        val bPath = json.optString("backdrop_path", "").trim()
                        if (pPath.startsWith("/")) {
                            posterUrl = "$TMDB_IMAGE_W500$pPath"
                        }
                        if (bPath.startsWith("/")) {
                            backdropUrl = "$TMDB_IMAGE_W780$bPath"
                        }
                        json.optJSONObject("credits")?.let { creditsObj ->
                            castList = parseTmdbCreditsJson(creditsObj.toString())
                        }
                    }
                }
            }

            // Fallback search by title if tmdbId was not set or credits were empty
            if (tmdbId <= 0 || castList.isEmpty()) {
                val searchTitle = media.originalTitle.ifBlank { media.title }
                    .replace(Regex("\\(.*?\\)"), "")
                    .substringBefore(" - ")
                    .trim()
                if (searchTitle.isNotBlank()) {
                    val encodedQuery = URLEncoder.encode(searchTitle, "UTF-8")
                    val searchUrl =
                        "https://api.themoviedb.org/3/search/$mediaType?api_key=$explicitApiKey&query=$encodedQuery&include_adult=false"
                    val searchReq = Request.Builder().url(searchUrl).get().build()
                    httpClient.newCall(searchReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            val results = JSONObject(body).optJSONArray("results")
                            if (results != null && results.length() > 0) {
                                val first = results.getJSONObject(0)
                                tmdbId = first.optInt("id", -1)
                                val pPath = first.optString("poster_path", "").trim()
                                val bPath = first.optString("backdrop_path", "").trim()
                                if (pPath.startsWith("/")) {
                                    posterUrl = "$TMDB_IMAGE_W500$pPath"
                                }
                                if (bPath.startsWith("/")) {
                                    backdropUrl = "$TMDB_IMAGE_W780$bPath"
                                }
                            }
                        }
                    }
                }

                if (tmdbId > 0 && castList.isEmpty()) {
                    val creditsUrl =
                        "https://api.themoviedb.org/3/$mediaType/$tmdbId/credits?api_key=$explicitApiKey"
                    val creditsReq = Request.Builder().url(creditsUrl).get().build()
                    httpClient.newCall(creditsReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            castList = parseTmdbCreditsJson(body)
                        }
                    }
                }
            }

            if (tmdbId <= 0 && posterUrl == media.posterUrl && castList.isEmpty()) {
                return@withContext null
            }

            val enriched = TmdbEnrichmentResult(
                tmdbId = tmdbId.coerceAtLeast(0),
                posterUrl = posterUrl.ifBlank { media.posterUrl },
                backdropUrl = backdropUrl.ifBlank { media.backdropUrl },
                cast = castList.ifEmpty { media.cast }
            )
            enrichmentCache[media.id] = enriched
            enriched
        } catch (_: Exception) {
            null
        }
    }

    suspend fun enrichMediaContent(media: MediaContent): MediaContent {
        val res = enrichMediaWithTmdb(media) ?: return media
        return media.copy(
            tmdbId = if (res.tmdbId > 0) res.tmdbId.toLong() else media.tmdbId,
            posterUrl = res.posterUrl.ifBlank { media.posterUrl },
            backdropUrl = res.backdropUrl.ifBlank { media.backdropUrl },
            cast = if (res.cast.isNotEmpty()) res.cast else media.cast
        )
    }
}
