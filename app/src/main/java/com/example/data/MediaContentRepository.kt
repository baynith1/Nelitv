package com.example.data

import com.example.data.local.DownloadedItemEntity
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.model.SeriesSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object MediaContentRepository {

    const val DEFAULT_PROJECT_ID = "neliplay"
    const val DEFAULT_DATABASE_URL = "https://neliplay-default-rtdb.firebaseio.com"

    val homeGenreTabs = listOf(
        "Popular",
        "Swahili",
        "Action",
        "Crime",
        "Drama",
        "Thriller",
        "Mystery",
        "Comedy",
        "Sci-Fi",
        "Series",
        "Movies",
        "Kids"
    )

    // Real production episodes from neliplay database
    private val initialProductionEpisodes: List<EpisodeItem> = listOf(
        EpisodeItem(
            id = "ep_1788988797380_5vlc5",
            seriesId = "ser_1788988691994_5tt72",
            seasonNumber = 3,
            episodeNumber = 1,
            name = "Keys and Knives",
            overview = "No-eul infiltrates a dangerous operation with a risky plan. A new game is announced: hide-and-seek, where one group holds a key and the other a knife.",
            stillPath = "https://image.tmdb.org/t/p/w500/3sUdP791SMnEQuJNIpRCT49pkxe.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8",
            playbackType = "m3u8",
            runtime = 58,
            airDate = "2025-06-27",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            published = true,
            viewsCount = 0
        ),
        EpisodeItem(
            id = "ep_1788988994281_e0gsc",
            seriesId = "ser_1788988691994_5tt72",
            seasonNumber = 3,
            episodeNumber = 2,
            name = "The Starry Night",
            overview = "Alliances fracture inside the dormitory as players prepare for the next round under the watch of the Front Man.",
            stillPath = "https://image.tmdb.org/t/p/w500/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8",
            playbackType = "m3u8",
            runtime = 61,
            airDate = "2025-06-27",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            published = true
        ),
        EpisodeItem(
            id = "ep_1788989040738_l2swm",
            seriesId = "ser_1788988691994_5tt72",
            seasonNumber = 2,
            episodeNumber = 1,
            name = "Bread and Lottery",
            overview = "Driven by revenge, Gi-hun tracks down the recruiter in Seoul while Detective Jun-ho follows a new lead.",
            stillPath = "https://image.tmdb.org/t/p/w500/yEB6bMYgNu6qEQWoBvlkg6Ea5P.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8",
            playbackType = "m3u8",
            runtime = 65,
            airDate = "2024-12-26",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            published = true
        ),
        EpisodeItem(
            id = "ep_1788989088681_ndzhk",
            seriesId = "ser_1788988691994_5tt72",
            seasonNumber = 1,
            episodeNumber = 1,
            name = "Red Light, Green Light",
            overview = "Hoping to win easy money, a broke and desperate Gi-hun agrees to take part in an enigmatic game.",
            stillPath = "https://image.tmdb.org/t/p/w500/oSk0j4yeDOqXNHIjYehgSJ2WujM.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8",
            playbackType = "m3u8",
            runtime = 60,
            airDate = "2021-09-17",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            published = true
        )
    )

    // Real production items from neliplay database (used before/during live cloud sync; no fake demo entries)
    private val initialProductionCatalog: List<MediaContent> = listOf(
        MediaContent(
            id = "mov_1788911132603_wdgav",
            title = "Never a Thief",
            originalTitle = "缉盗",
            originalLanguage = "zh",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Crime",
            subGenres = listOf("Popular", "Crime", "Action", "Movies", "Swahili"),
            duration = "2h 00m",
            rating = "5.3",
            director = "Action Cinema",
            screenplay = "Swahili Narrated",
            production = "China • Hong Kong",
            synopsis = "An insurance investigator infiltrates a Pacific island relic-smuggling gang with his lock-picking skills, navigates perilous heists to help police bust the syndicate, and unravels the mystery behind his father's disappearance.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2025",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 120,
            tmdbId = 1500522L,
            voteCount = 5L,
            productionCountries = listOf("China", "Hong Kong")
        ),
        MediaContent(
            id = "ser_1788988691994_5tt72",
            title = "Squid Game",
            originalTitle = "오징어 게임",
            originalLanguage = "ko",
            type = "series",
            posterUrl = "https://image.tmdb.org/t/p/w500/1QdXdRYfktUSONkl1oD5gc6Be0s.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8",
            streamFormat = "hls",
            genre = "Action & Adventure",
            subGenres = listOf("Popular", "Action & Adventure", "Action", "Mystery", "Drama", "Series", "Swahili"),
            duration = "3 Seasons • 22 Eps",
            rating = "7.9",
            director = "Hwang Dong-hyuk",
            screenplay = "Swahili Narrated",
            production = "Siren Pictures",
            synopsis = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games. Inside, a tempting prize awaits — with deadly high stakes.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2021",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            tmdbId = 93405L,
            voteCount = 17732L,
            numberOfSeasons = 3,
            numberOfEpisodes = 22,
            firstAirDate = "2021-09-17",
            lastAirDate = "2025-06-27",
            seasons = listOf(
                SeriesSeason(
                    seasonNumber = 1,
                    name = "Season 1",
                    episodeCount = 9,
                    airDate = "2021-09-17",
                    overview = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games. Inside, a tempting prize awaits — with deadly high stakes.",
                    posterPath = "https://image.tmdb.org/t/p/w500/oSk0j4yeDOqXNHIjYehgSJ2WujM.jpg"
                ),
                SeriesSeason(
                    seasonNumber = 2,
                    name = "Season 2",
                    episodeCount = 7,
                    airDate = "2024-12-26",
                    overview = "Ready to run for your lives? Player 456 returns for more heart-pounding children's games, facing deadly new challenges — but armed with a hidden agenda.",
                    posterPath = "https://image.tmdb.org/t/p/w500/yEB6bMYgNu6qEQWoBvlkg6Ea5P.jpg"
                ),
                SeriesSeason(
                    seasonNumber = 3,
                    name = "Season 3",
                    episodeCount = 6,
                    airDate = "2025-06-27",
                    overview = "Gi-hun is devastated by the loss of his best friend and sinks into deep despair after discovering that the Game Master had hidden his true identity to secretly infiltrate the game.",
                    posterPath = "https://image.tmdb.org/t/p/w500/okJESjE3wqN4qDNFOM8TecUVfHX.jpg"
                )
            )
        )
    )

    private val _mediaCatalog = MutableStateFlow<List<MediaContent>>(initialProductionCatalog)
    val mediaCatalog: StateFlow<List<MediaContent>> = _mediaCatalog.asStateFlow()

    private val _episodesCatalog = MutableStateFlow<List<EpisodeItem>>(initialProductionEpisodes)
    val episodesCatalog: StateFlow<List<EpisodeItem>> = _episodesCatalog.asStateFlow()

    private val _firebaseSyncStatus = MutableStateFlow("Online • Live & On-Demand Catalog Ready")
    val firebaseSyncStatus: StateFlow<String> = _firebaseSyncStatus.asStateFlow()

    fun getMediaById(id: String): MediaContent? {
        return _mediaCatalog.value.find { it.id == id }
    }

    fun getEpisodesForSeries(seriesId: String, seasonNumber: Int? = null): List<EpisodeItem> {
        val seriesEpisodes = _episodesCatalog.value
            .filter { it.seriesId == seriesId && it.published }
            .sortedWith(compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber })
        return if (seasonNumber != null) {
            seriesEpisodes.filter { it.seasonNumber == seasonNumber }
        } else {
            seriesEpisodes
        }
    }

    fun filterByGenreTab(tab: String): List<MediaContent> {
        val all = _mediaCatalog.value.filter { it.published }
        return when (tab.lowercase()) {
            "all", "popular" -> all.filter { it.featured || it.isTrending }.ifEmpty { all }
            "swahili" -> all.filter { it.narrated || it.narrationLanguage.equals("Swahili", ignoreCase = true) }.ifEmpty { all }
            "series" -> all.filter { it.isSeries }
            "movies" -> all.filter { it.isMovie }
            "kids" -> all.filter {
                it.isKids ||
                        it.genre.contains("kids", ignoreCase = true) ||
                        it.genre.contains("animation", ignoreCase = true) ||
                        it.genre.contains("family", ignoreCase = true) ||
                        it.subGenres.any { g ->
                            g.contains("kids", ignoreCase = true) ||
                                    g.contains("animation", ignoreCase = true) ||
                                    g.contains("family", ignoreCase = true)
                        }
            }.ifEmpty { all }
            else -> all.filter { item ->
                item.genre.contains(tab, ignoreCase = true) ||
                        item.subGenres.any { it.contains(tab, ignoreCase = true) }
            }.ifEmpty { all }
        }
    }

    /**
     * Groups all available Movies & Series by their real genres so the Homepage
     * displays rich genre rows dynamically from the live catalog.
     */
    fun getMediaGroupedByGenre(catalog: List<MediaContent> = _mediaCatalog.value): List<Pair<String, List<MediaContent>>> {
        val published = catalog.filter { it.published }
        if (published.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<String, List<MediaContent>>>()

        // 1. Swahili Narrated Movies & Series
        val swahiliItems = published.filter {
            it.narrated || it.narrationLanguage.equals("Swahili", ignoreCase = true)
        }
        if (swahiliItems.isNotEmpty()) {
            result.add("Swahili Narrated (zilizotafsiriwa)" to swahiliItems)
        }

        // 2. Collect all distinct genres across the catalog
        val excludedTags = setOf("popular", "movies", "series", "swahili")
        val genreMap = linkedMapOf<String, MutableList<MediaContent>>()

        for (media in published) {
            val itemGenres = (listOf(media.genre) + media.subGenres)
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.lowercase() !in excludedTags }
                .distinctBy { it.lowercase() }

            for (g in itemGenres) {
                val canonicalKey = genreMap.keys.firstOrNull { it.equals(g, ignoreCase = true) } ?: g
                val bucket = genreMap.getOrPut(canonicalKey) { mutableListOf() }
                if (bucket.none { it.id == media.id }) {
                    bucket.add(media)
                }
            }
        }

        // Sort genre sections so larger/priority genres appear first
        val priorityOrder = listOf(
            "Action",
            "Action & Adventure",
            "Crime",
            "Drama",
            "Thriller",
            "Mystery",
            "Sci-Fi",
            "Science Fiction",
            "Adventure",
            "Comedy",
            "Horror",
            "Animation",
            "Family"
        )

        val sortedGenres = genreMap.entries.sortedWith(
            compareBy<Map.Entry<String, MutableList<MediaContent>>> { entry ->
                val idx = priorityOrder.indexOfFirst { it.equals(entry.key, ignoreCase = true) }
                if (idx >= 0) idx else 100
            }.thenByDescending { it.value.size }
        )

        for ((genreName, items) in sortedGenres) {
            if (items.isNotEmpty()) {
                result.add(genreName to items)
            }
        }

        return result
    }

    fun getComingSoon(): List<MediaContent> =
        _mediaCatalog.value.filter { it.isComingSoon }.ifEmpty { _mediaCatalog.value.take(4) }

    fun getBestForKids(): List<MediaContent> =
        _mediaCatalog.value.filter { it.isKids }.ifEmpty { _mediaCatalog.value.takeLast(3) }

    fun getRelatedMedia(currentId: String): List<MediaContent> =
        _mediaCatalog.value.filter { it.id != currentId }.take(6)

    // Production mode: no fake downloads pre-seeded
    fun defaultDownloadsSeed(): List<DownloadedItemEntity> = emptyList()

    /**
     * Syncs both Cloud (`movies`, `series`, `episodes`, `tvChannels`) and
     * Realtime Database (`live_streams`, `channels`) in parallel for `neliplay`.
     */
    suspend fun syncFromFirebaseEndpoint(
        databaseUrl: String = DEFAULT_DATABASE_URL,
        apiKey: String = "",
        projectId: String = DEFAULT_PROJECT_ID
    ): Result<Int> = withContext(Dispatchers.IO) {
        val cleanUrl = databaseUrl.trim().removeSuffix("/").ifEmpty { DEFAULT_DATABASE_URL }
        val cleanProjectId = projectId.trim().ifEmpty {
            extractProjectIdFromUrl(cleanUrl).ifEmpty { DEFAULT_PROJECT_ID }
        }
        val cleanKey = if (apiKey.trim() == "YOUR_FIREBASE_API_KEY") "" else apiKey.trim()

        try {
            _firebaseSyncStatus.value = "Updating live catalog..."
            var totalSynced = 0

            coroutineScope {
                val firestoreDeferred = async {
                    if (cleanProjectId.isNotEmpty()) {
                        syncFromCloudFirestore(cleanProjectId, cleanKey).getOrDefault(0)
                    } else 0
                }

                val rtdbDeferred = async {
                    if (cleanUrl.contains(".firebaseio.com") || cleanUrl.endsWith(".json")) {
                        syncFromRealtimeDatabase(cleanUrl, cleanKey).getOrDefault(0)
                    } else 0
                }

                totalSynced = firestoreDeferred.await() + rtdbDeferred.await()
            }

            _firebaseSyncStatus.value =
                "Online • ${_mediaCatalog.value.size} Titles & ${ChannelRepository.liveChannelsFlow.value.size} Live Channels"
            Result.success(totalSynced.coerceAtLeast(_mediaCatalog.value.size))
        } catch (e: Exception) {
            _firebaseSyncStatus.value = "Online • Catalog Ready"
            Result.failure(e)
        }
    }

    private fun extractProjectIdFromUrl(url: String): String {
        if (url.contains("/projects/")) {
            return url.substringAfter("/projects/").substringBefore("/")
        }
        if (url.contains(".firebaseio.com")) {
            return url.substringAfter("://")
                .substringBefore(".firebaseio.com")
                .removeSuffix("-default-rtdb")
        }
        return ""
    }

    private suspend fun syncFromCloudFirestore(projectId: String, apiKey: String): Result<Int> = coroutineScope {
        try {
            val baseFirestoreUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
            val keyParam = if (apiKey.isNotEmpty()) "&key=$apiKey" else ""

            val moviesDeferred = async { fetchUrlText("$baseFirestoreUrl/movies?pageSize=300$keyParam") }
            val seriesDeferred = async { fetchUrlText("$baseFirestoreUrl/series?pageSize=200$keyParam") }
            val episodesDeferred = async { fetchUrlText("$baseFirestoreUrl/episodes?pageSize=300$keyParam") }
            val tvChannelsDeferred = async { fetchUrlText("$baseFirestoreUrl/tvChannels?pageSize=200$keyParam") }

            val totalSynced = parseFirestoreCollections(
                moviesJson = moviesDeferred.await(),
                seriesJson = seriesDeferred.await(),
                episodesJson = episodesDeferred.await(),
                tvChannelsJson = tvChannelsDeferred.await()
            )
            Result.success(totalSynced)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun syncFromRealtimeDatabase(cleanUrl: String, apiKey: String): Result<Int> = coroutineScope {
        try {
            val authQuery = if (apiKey.isNotBlank()) "?auth=$apiKey" else ""
            val rootEndpoint = if (cleanUrl.endsWith(".json")) {
                "$cleanUrl$authQuery"
            } else {
                "$cleanUrl/.json$authQuery"
            }

            val rootJson = fetchUrlText(rootEndpoint)
            if (!rootJson.isNullOrBlank() && rootJson.trim().startsWith("{")) {
                val count = parseFirebaseJsonPayload(rootJson)
                return@coroutineScope Result.success(count)
            }

            // Fallback to individual public RTDB nodes (`channels`, `live_streams`) per rules
            val channelsDeferred = async { fetchUrlText("$cleanUrl/channels.json$authQuery") }
            val liveStreamsDeferred = async { fetchUrlText("$cleanUrl/live_streams.json$authQuery") }

            val combinedObj = JSONObject()
            channelsDeferred.await()?.trim()?.let { chText ->
                if (chText.startsWith("{") || chText.startsWith("[")) {
                    combinedObj.put("channels", if (chText.startsWith("[")) JSONArray(chText) else JSONObject(chText))
                }
            }
            liveStreamsDeferred.await()?.trim()?.let { lsText ->
                if (lsText.startsWith("{") || lsText.startsWith("[")) {
                    combinedObj.put("live_streams", if (lsText.startsWith("[")) JSONArray(lsText) else JSONObject(lsText))
                }
            }

            if (combinedObj.length() > 0) {
                val count = parseFirebaseJsonPayload(combinedObj.toString())
                Result.success(count)
            } else {
                Result.success(0)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchUrlText(urlStr: String): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "keep-alive")
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses REST v1 JSON responses (`{"documents": [...]}`) for
     * `movies`, `series`, `episodes`, and `tvChannels`.
     */
    fun parseFirestoreCollections(
        moviesJson: String? = null,
        seriesJson: String? = null,
        episodesJson: String? = null,
        tvChannelsJson: String? = null
    ): Int {
        val parsedMedia = mutableListOf<MediaContent>()
        val parsedEpisodes = mutableListOf<EpisodeItem>()
        val parsedChannels = mutableListOf<LiveChannel>()

        if (!episodesJson.isNullOrBlank()) {
            val docs = JSONObject(episodesJson).optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    parseFirestoreEpisodeDoc(doc)?.let { parsedEpisodes.add(it) }
                }
            }
        }

        if (parsedEpisodes.isNotEmpty()) {
            _episodesCatalog.value = parsedEpisodes + initialProductionEpisodes.filter { def ->
                parsedEpisodes.none { it.id == def.id }
            }
        }

        if (!moviesJson.isNullOrBlank()) {
            val docs = JSONObject(moviesJson).optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    parseFirestoreMovieDoc(doc)?.let { parsedMedia.add(it) }
                }
            }
        }

        if (!seriesJson.isNullOrBlank()) {
            val docs = JSONObject(seriesJson).optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    parseFirestoreSeriesDoc(doc)?.let { parsedMedia.add(it) }
                }
            }
        }

        if (!tvChannelsJson.isNullOrBlank()) {
            val docs = JSONObject(tvChannelsJson).optJSONArray("documents")
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    parseFirestoreTvChannelDoc(doc)?.let { parsedChannels.add(it) }
                }
            }
        }

        if (parsedMedia.isNotEmpty()) {
            val enrichedMedia = parsedMedia.map { media ->
                if (media.isSeries && media.streamUrl.isBlank()) {
                    val firstEp = _episodesCatalog.value.firstOrNull { it.seriesId == media.id }
                    if (firstEp != null) {
                        media.copy(
                            streamUrl = firstEp.streamUrl,
                            streamFormat = if (firstEp.playbackType.equals("mp4", true)) "mp4" else "hls"
                        )
                    } else media
                } else media
            }
            _mediaCatalog.value = enrichedMedia + initialProductionCatalog.filter { def ->
                enrichedMedia.none { it.id == def.id }
            }
        }

        if (parsedChannels.isNotEmpty()) {
            ChannelRepository.mergeFirebaseChannelsWithAzamPriority(parsedChannels)
        }

        return parsedMedia.size + parsedEpisodes.size + parsedChannels.size
    }

    private fun parseFirestoreMovieDoc(doc: JSONObject): MediaContent? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast("/")
        val id = fields.fsString("id").ifEmpty { docId }
        val title = fields.fsString("title")
        val streamUrl = fields.fsString("streamUrl")
        val published = fields.fsBoolean("published", true)
        if (!published || title.isEmpty() || streamUrl.isEmpty()) return null

        val genres = fields.fsStringList("genres").ifEmpty { listOf("Movies") }
        val primaryGenre = genres.firstOrNull() ?: "Movies"
        val narrated = fields.fsBoolean("narrated", false)
        val narrationLanguage = fields.fsString("narrationLanguage")
        val runtime = fields.fsInt("runtime", 120)
        val ratingVal = fields.fsDouble("rating", 8.0)
        val featured = fields.fsBoolean("featured", false)
        val year = fields.fsInt("year", 2025)
        val posterPath = fields.fsString("posterPath")
        val backdropPath = fields.fsString("backdropPath").ifEmpty { posterPath }

        val format = when {
            streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            streamUrl.contains(".mpd", ignoreCase = true) -> "dash"
            else -> "hls"
        }

        val subGenres = buildList {
            if (featured) add("Popular")
            addAll(genres)
            add("Movies")
            if (narrated && narrationLanguage.isNotBlank()) add(narrationLanguage)
        }

        return MediaContent(
            id = id,
            title = title,
            originalTitle = fields.fsString("originalTitle"),
            originalLanguage = fields.fsString("originalLanguage", "en"),
            type = "movie",
            posterUrl = posterPath,
            backdropUrl = backdropPath,
            streamUrl = streamUrl,
            streamFormat = format,
            genre = primaryGenre,
            subGenres = subGenres,
            duration = formatRuntimeMinutes(runtime),
            rating = String.format(Locale.US, "%.1f", ratingVal),
            production = fields.fsStringList("productionCountries").joinToString(" • ").ifEmpty { "Movies" },
            synopsis = fields.fsString("overview", "Watch $title streaming in HD on Neli TV."),
            isTrending = featured,
            isKids = genres.any { it.contains("kid", true) || it.contains("animation", true) || it.contains("family", true) },
            releaseYear = year.toString(),
            narrated = narrated,
            narrationLanguage = narrationLanguage,
            downloadEnabled = fields.fsBoolean("downloadEnabled", true),
            featured = featured,
            published = published,
            runtimeMinutes = runtime,
            tmdbId = fields.fsLong("tmdbId", 0L),
            viewsCount = fields.fsLong("viewsCount", 0L),
            voteCount = fields.fsLong("voteCount", 0L),
            watchlistsCount = fields.fsLong("watchlistsCount", 0L),
            productionCountries = fields.fsStringList("productionCountries")
        )
    }

    private fun parseFirestoreSeriesDoc(doc: JSONObject): MediaContent? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast("/")
        val id = fields.fsString("id").ifEmpty { docId }
        val name = fields.fsString("name").ifEmpty { fields.fsString("title") }
        val published = fields.fsBoolean("published", true)
        if (!published || name.isEmpty()) return null

        val genres = fields.fsStringList("genres").ifEmpty { listOf("Series") }
        val primaryGenre = genres.firstOrNull() ?: "Series"
        val featured = fields.fsBoolean("featured", false)
        val narrated = fields.fsBoolean("narrated", true)
        val narrationLanguage = fields.fsString("narrationLanguage", "Swahili")
        val ratingVal = fields.fsDouble("rating", 8.5)
        val numSeasons = fields.fsInt("numberOfSeasons", 1)
        val numEpisodes = fields.fsInt("numberOfEpisodes", 1)
        val year = fields.fsInt("year", 2025)
        val posterPath = fields.fsString("posterPath")
        val backdropPath = fields.fsString("backdropPath").ifEmpty { posterPath }

        val seasonsList = mutableListOf<SeriesSeason>()
        val seasonsArray = fields.optJSONObject("seasons")
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values")
        if (seasonsArray != null) {
            for (i in 0 until seasonsArray.length()) {
                val mapFields = seasonsArray.optJSONObject(i)
                    ?.optJSONObject("mapValue")
                    ?.optJSONObject("fields") ?: continue
                seasonsList.add(
                    SeriesSeason(
                        seasonNumber = mapFields.fsInt("seasonNumber", i + 1),
                        name = mapFields.fsString("name", "Season ${i + 1}"),
                        episodeCount = mapFields.fsInt("episodeCount", 1),
                        airDate = mapFields.fsString("airDate"),
                        overview = mapFields.fsString("overview"),
                        posterPath = mapFields.fsString("posterPath", posterPath)
                    )
                )
            }
        }

        val matchingEp = _episodesCatalog.value.firstOrNull { it.seriesId == id }
        val fallbackStream = matchingEp?.streamUrl
            ?: "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8"

        return MediaContent(
            id = id,
            title = name,
            originalTitle = fields.fsString("originalName"),
            originalLanguage = fields.fsString("originalLanguage", "en"),
            type = "series",
            posterUrl = posterPath,
            backdropUrl = backdropPath,
            streamUrl = fields.fsString("streamUrl").ifEmpty { fallbackStream },
            streamFormat = "hls",
            genre = primaryGenre,
            subGenres = buildList {
                if (featured) add("Popular")
                addAll(genres)
                add("Series")
                if (narrated && narrationLanguage.isNotBlank()) add(narrationLanguage)
            },
            duration = "$numSeasons Seasons • $numEpisodes Eps",
            rating = String.format(Locale.US, "%.1f", ratingVal),
            synopsis = fields.fsString("overview", "Watch $name all seasons on Neli TV."),
            isTrending = featured,
            releaseYear = year.toString(),
            narrated = narrated,
            narrationLanguage = narrationLanguage,
            featured = featured,
            published = published,
            tmdbId = fields.fsLong("tmdbId", 0L),
            viewsCount = fields.fsLong("viewsCount", 0L),
            voteCount = fields.fsLong("voteCount", 0L),
            watchlistsCount = fields.fsLong("watchlistsCount", 0L),
            numberOfSeasons = numSeasons,
            numberOfEpisodes = numEpisodes,
            firstAirDate = fields.fsString("firstAirDate"),
            lastAirDate = fields.fsString("lastAirDate"),
            seasons = seasonsList
        )
    }

    private fun parseFirestoreEpisodeDoc(doc: JSONObject): EpisodeItem? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast("/")
        val id = fields.fsString("id").ifEmpty { docId }
        val seriesId = fields.fsString("seriesId")
        val name = fields.fsString("name")
        val streamUrl = fields.fsString("streamUrl")
        val published = fields.fsBoolean("published", true)
        if (!published || seriesId.isEmpty() || streamUrl.isEmpty()) return null

        return EpisodeItem(
            id = id,
            seriesId = seriesId,
            seasonNumber = fields.fsInt("seasonNumber", 1),
            episodeNumber = fields.fsInt("episodeNumber", 1),
            name = name.ifEmpty { "Episode ${fields.fsInt("episodeNumber", 1)}" },
            overview = fields.fsString("overview"),
            stillPath = fields.fsString("stillPath"),
            streamUrl = streamUrl,
            playbackType = fields.fsString("playbackType", "m3u8"),
            runtime = fields.fsInt("runtime", 45),
            airDate = fields.fsString("airDate"),
            narrated = fields.fsBoolean("narrated", false),
            narrationLanguage = fields.fsString("narrationLanguage"),
            downloadEnabled = fields.fsBoolean("downloadEnabled", true),
            published = published,
            viewsCount = fields.fsLong("viewsCount", 0L)
        )
    }

    private fun parseFirestoreTvChannelDoc(doc: JSONObject): LiveChannel? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast("/")
        val id = fields.fsString("id").ifEmpty { docId }
        val name = fields.fsString("name")
        val rawUrl = fields.fsString("streamUrl")
        val enabled = fields.fsBoolean("enabled", true)
        val published = fields.fsBoolean("published", true)
        if (!enabled || !published || name.isEmpty() || rawUrl.isEmpty()) return null

        val normalizedUrl = ChannelRepository.normalizeDashStreamUrl(rawUrl)
        val category = fields.fsString("category", "Entertainment")
        val country = fields.fsString("country", "")
        val featured = fields.fsBoolean("featured", false)
        val logo = fields.fsString("logo")

        val categoriesList = buildList {
            add(category.lowercase())
            if (country.equals("Tanzania", ignoreCase = true)) {
                add("tanzania")
            }
        }

        val isDash = normalizedUrl.contains(".mpd", ignoreCase = true)
        return LiveChannel(
            id = id,
            name = name,
            description = if (country.isNotBlank()) "$category • $country" else category,
            streamUrl = normalizedUrl,
            streamFormat = if (isDash) "dash" else "hls",
            thumbnailUrl = logo,
            categories = categoriesList,
            language = if (country.equals("Tanzania", ignoreCase = true)) "sw" else "en",
            encryptionType = "none",
            isLiveBroadcast = fields.fsBoolean("isLive", true),
            country = country,
            featured = featured,
            enabled = enabled,
            published = published
        )
    }

    private fun JSONObject.fsString(key: String, default: String = ""): String {
        val field = optJSONObject(key) ?: return default
        return field.optString("stringValue", default)
    }

    private fun JSONObject.fsBoolean(key: String, default: Boolean = false): Boolean {
        val field = optJSONObject(key) ?: return default
        return if (field.has("booleanValue")) field.optBoolean("booleanValue", default) else default
    }

    private fun JSONObject.fsInt(key: String, default: Int = 0): Int {
        val field = optJSONObject(key) ?: return default
        if (field.has("integerValue")) {
            return field.optString("integerValue", default.toString()).toIntOrNull() ?: default
        }
        if (field.has("doubleValue")) {
            return field.optDouble("doubleValue", default.toDouble()).toInt()
        }
        return default
    }

    private fun JSONObject.fsLong(key: String, default: Long = 0L): Long {
        val field = optJSONObject(key) ?: return default
        if (field.has("integerValue")) {
            return field.optString("integerValue", default.toString()).toLongOrNull() ?: default
        }
        return default
    }

    private fun JSONObject.fsDouble(key: String, default: Double = 0.0): Double {
        val field = optJSONObject(key) ?: return default
        if (field.has("doubleValue")) {
            return field.optDouble("doubleValue", default)
        }
        if (field.has("integerValue")) {
            return field.optString("integerValue", default.toString()).toDoubleOrNull() ?: default
        }
        return default
    }

    private fun JSONObject.fsStringList(key: String): List<String> {
        val values = optJSONObject(key)
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until values.length()) {
            val str = values.optJSONObject(i)?.optString("stringValue", "")?.trim().orEmpty()
            if (str.isNotEmpty()) result.add(str)
        }
        return result
    }

    private fun formatRuntimeMinutes(minutes: Int): String {
        if (minutes <= 0) return "2h 00m"
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (hrs > 0) "${hrs}h ${mins.toString().padStart(2, '0')}m" else "${mins}m"
    }

    fun parseFirebaseJsonPayload(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val parsedMedia = mutableListOf<MediaContent>()
        val parsedEpisodes = mutableListOf<EpisodeItem>()
        val parsedChannels = mutableListOf<LiveChannel>()

        listOf("movies", "series", "content", "items").forEach { key ->
            if (root.has(key)) {
                val node = root.get(key)
                extractMediaNodes(node, defaultType = if (key == "series") "series" else "movie", out = parsedMedia)
            }
        }

        if (root.has("episodes")) {
            extractEpisodeNodes(root.get("episodes"), parsedEpisodes)
        }

        listOf("tvChannels", "channels", "live_streams", "live_tv", "livetv", "tv").forEach { key ->
            if (root.has(key)) {
                val node = root.get(key)
                extractLiveChannelNodes(node, out = parsedChannels)
            }
        }

        if (parsedEpisodes.isNotEmpty()) {
            _episodesCatalog.value = parsedEpisodes + _episodesCatalog.value.filter { def ->
                parsedEpisodes.none { it.id == def.id }
            }
        }

        if (parsedMedia.isNotEmpty()) {
            _mediaCatalog.value = parsedMedia + _mediaCatalog.value.filter { def ->
                parsedMedia.none { it.id == def.id }
            }
        }

        if (parsedChannels.isNotEmpty()) {
            ChannelRepository.mergeFirebaseChannelsWithAzamPriority(parsedChannels)
        }

        return parsedMedia.size + parsedEpisodes.size + parsedChannels.size
    }

    private fun extractMediaNodes(node: Any, defaultType: String, out: MutableList<MediaContent>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val obj = node.optJSONObject(i) ?: continue
                    parseSingleMediaObject(obj, "media_${defaultType}_$i", defaultType)?.let { out.add(it) }
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = node.optJSONObject(k) ?: continue
                    parseSingleMediaObject(obj, k, defaultType)?.let { out.add(it) }
                }
            }
        }
    }

    private fun extractEpisodeNodes(node: Any, out: MutableList<EpisodeItem>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val obj = node.optJSONObject(i) ?: continue
                    parseSingleEpisodeObject(obj, "ep_$i")?.let { out.add(it) }
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = node.optJSONObject(k) ?: continue
                    parseSingleEpisodeObject(obj, k)?.let { out.add(it) }
                }
            }
        }
    }

    private fun parseSingleEpisodeObject(obj: JSONObject, fallbackId: String): EpisodeItem? {
        val seriesId = obj.optString("seriesId", "").trim()
        val streamUrl = obj.optString("streamUrl", "").trim()
        if (seriesId.isEmpty() || streamUrl.isEmpty()) return null
        return EpisodeItem(
            id = obj.optString("id", fallbackId),
            seriesId = seriesId,
            seasonNumber = obj.optInt("seasonNumber", 1),
            episodeNumber = obj.optInt("episodeNumber", 1),
            name = obj.optString("name", "Episode"),
            overview = obj.optString("overview", ""),
            stillPath = obj.optString("stillPath", ""),
            streamUrl = streamUrl,
            playbackType = obj.optString("playbackType", "m3u8"),
            runtime = obj.optInt("runtime", 45),
            airDate = obj.optString("airDate", ""),
            narrated = obj.optBoolean("narrated", false),
            narrationLanguage = obj.optString("narrationLanguage", ""),
            downloadEnabled = obj.optBoolean("downloadEnabled", true),
            published = obj.optBoolean("published", true)
        )
    }

    private fun parseSingleMediaObject(obj: JSONObject, fallbackId: String, defaultType: String): MediaContent? {
        val title = obj.optString("title", obj.optString("name", "")).trim()
        val streamUrl = obj.optString(
            "streamUrl",
            obj.optString("url", obj.optString("videoUrl", ""))
        ).trim()
        val isSeries = obj.optString("type", defaultType).equals("series", ignoreCase = true) ||
                obj.has("numberOfSeasons") || obj.has("seasons")
        if (title.isEmpty() || (!isSeries && streamUrl.isEmpty())) return null

        val poster = obj.optString("posterPath", obj.optString("posterUrl", obj.optString("thumbnailUrl", obj.optString("image", ""))))
        val backdrop = obj.optString("backdropPath", obj.optString("backdropUrl", poster))

        val genresList = mutableListOf<String>()
        val genresArr = obj.optJSONArray("genres")
        if (genresArr != null) {
            for (i in 0 until genresArr.length()) {
                val g = genresArr.optString(i, "").trim()
                if (g.isNotEmpty()) genresList.add(g)
            }
        }
        val genre = genresList.firstOrNull() ?: obj.optString("genre", obj.optString("category", "Action"))
        val runtime = obj.optInt("runtime", 120)
        val duration = if (isSeries) {
            val sCount = obj.optInt("numberOfSeasons", 1)
            val eCount = obj.optInt("numberOfEpisodes", 8)
            "$sCount Seasons • $eCount Eps"
        } else {
            obj.optString("duration", formatRuntimeMinutes(runtime))
        }
        val rating = obj.optString("rating", "8.5")
        val synopsis = obj.optString("overview", obj.optString("synopsis", obj.optString("description", "Watch $title streaming on Neli TV.")))
        val narrated = obj.optBoolean("narrated", false)
        val narrationLanguage = obj.optString("narrationLanguage", "")

        val format = when {
            streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            streamUrl.contains(".mpd", ignoreCase = true) -> "dash"
            else -> "hls"
        }

        return MediaContent(
            id = obj.optString("id", fallbackId),
            title = title,
            type = if (isSeries) "series" else "movie",
            posterUrl = poster,
            backdropUrl = backdrop,
            streamUrl = streamUrl.ifEmpty { "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8" },
            streamFormat = format,
            genre = genre,
            subGenres = buildList {
                add("Popular")
                addAll(genresList)
                add(genre)
                add(if (isSeries) "Series" else "Movies")
                if (narrated && narrationLanguage.isNotBlank()) add(narrationLanguage)
            },
            duration = duration,
            rating = rating,
            synopsis = synopsis,
            isTrending = obj.optBoolean("featured", obj.optBoolean("isTrending", true)),
            narrated = narrated,
            narrationLanguage = narrationLanguage,
            downloadEnabled = obj.optBoolean("downloadEnabled", true),
            featured = obj.optBoolean("featured", false),
            published = obj.optBoolean("published", true),
            releaseYear = obj.optString("year", "2025")
        )
    }

    private fun extractLiveChannelNodes(node: Any, out: MutableList<LiveChannel>) {
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val obj = node.optJSONObject(i) ?: continue
                    parseSingleChannelObject(obj, "ch_$i")?.let { out.add(it) }
                }
            }
            is JSONObject -> {
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = node.optJSONObject(k) ?: continue
                    parseSingleChannelObject(obj, k)?.let { out.add(it) }
                }
            }
        }
    }

    private fun parseSingleChannelObject(obj: JSONObject, fallbackId: String): LiveChannel? {
        val name = obj.optString("name", obj.optString("title", "")).trim()
        val rawUrl = obj.optString("streamUrl", obj.optString("url", "")).trim()
        val enabled = obj.optBoolean("enabled", true)
        val published = obj.optBoolean("published", true)
        if (!enabled || !published || name.isEmpty() || rawUrl.isEmpty()) return null

        val normalizedUrl = ChannelRepository.normalizeDashStreamUrl(rawUrl)
        val isDash = normalizedUrl.contains(".mpd", ignoreCase = true) ||
                obj.optString("streamFormat").equals("dash", ignoreCase = true)

        val category = obj.optString("category", "entertainment")
        val country = obj.optString("country", "")
        val featured = obj.optBoolean("featured", false)

        val clearKeysMap = mutableMapOf<String, String>()
        val clearKeysObj = obj.optJSONObject("clearKeys")
        if (clearKeysObj != null) {
            val kIter = clearKeysObj.keys()
            while (kIter.hasNext()) {
                val kid = kIter.next()
                clearKeysMap[kid] = clearKeysObj.optString(kid, "")
            }
        } else {
            val kid = obj.optString("clearKeyId", obj.optString("kid", ""))
            val key = obj.optString("clearKey", obj.optString("key", ""))
            if (kid.isNotEmpty() && key.isNotEmpty()) {
                clearKeysMap[kid] = key
            }
        }

        return LiveChannel(
            id = obj.optString("id", fallbackId),
            name = name,
            description = if (country.isNotBlank()) "$category • $country" else obj.optString("description", category),
            streamUrl = normalizedUrl,
            streamFormat = if (isDash) "dash" else "hls",
            thumbnailUrl = obj.optString("logo", obj.optString("thumbnailUrl", "")),
            categories = buildList {
                add(category.lowercase())
                if (country.equals("Tanzania", ignoreCase = true)) add("tanzania")
            },
            language = if (country.equals("Tanzania", ignoreCase = true)) "sw" else obj.optString("language", "en"),
            encryptionType = if (clearKeysMap.isNotEmpty()) "clearkey" else "none",
            clearKeys = clearKeysMap,
            isLiveBroadcast = obj.optBoolean("isLive", true),
            country = country,
            featured = featured,
            enabled = enabled,
            published = published
        )
    }
}
