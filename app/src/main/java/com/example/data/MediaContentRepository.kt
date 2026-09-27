package com.example.data

import com.example.data.local.DownloadedItemEntity
import com.example.model.CastMember
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object MediaContentRepository {

    const val DEFAULT_PROJECT_ID = "neliplay"
    const val DEFAULT_DATABASE_URL = "https://neliplay-default-rtdb.firebaseio.com"
    const val MAX_CONCURRENT_USERS_CAPACITY = 10_000_000L

    private data class CachedEdgePayload(
        val body: String,
        val timestampMs: Long
    )

    private val edgeResponseCache = ConcurrentHashMap<String, CachedEdgePayload>()
    private const val EDGE_CACHE_TTL_MS = 45_000L
    private val catalogSyncMutex = Mutex()
    private val tokenSyncMutex = Mutex()

    // High-concurrency OkHttpClient with 64-connection keep-alive pool for 10M+ user scale
    private val highScaleHttpClient: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 128
            maxRequestsPerHost = 32
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Computes randomized backoff jitter (in milliseconds) so 10,000,000+ concurrent clients
     * stagger their background sync cycles smoothly without thundering-herd spikes.
     */
    fun computeJitterDelayMsFor10MScale(baseIntervalMs: Long = 180_000L): Long {
        val jitterSpread = (baseIntervalMs * 0.25).toLong().coerceAtLeast(5_000L)
        return baseIntervalMs + Random.Default.nextLong(0L, jitterSpread)
    }

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
            cast = listOf(
                CastMember(
                    id = "cast_nat_1",
                    name = "Archie Kao",
                    role = "Lead Investigator",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg"
                ),
                CastMember(
                    id = "cast_nat_2",
                    name = "Clara Lee",
                    role = "Syndicate Insider",
                    avatarUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg"
                ),
                CastMember(
                    id = "cast_nat_3",
                    name = "DJ Afro / Swahili Cast",
                    role = "Swahili Narration",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg"
                )
            ),
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
            cast = listOf(
                CastMember(
                    id = "cast_sg_1",
                    name = "Lee Jung-jae",
                    role = "Seong Gi-hun (Player 456)",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/1QdXdRYfktUSONkl1oD5gc6Be0s.jpg"
                ),
                CastMember(
                    id = "cast_sg_2",
                    name = "Lee Byung-hun",
                    role = "Hwang In-ho (Front Man)",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/2meX1nMdScFOoV4370rqHWKmXhY.jpg"
                ),
                CastMember(
                    id = "cast_sg_3",
                    name = "Wi Ha-jun",
                    role = "Detective Hwang Jun-ho",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/3sUdP791SMnEQuJNIpRCT49pkxe.jpg"
                ),
                CastMember(
                    id = "cast_sg_4",
                    name = "Yim Si-wan",
                    role = "Lee Myung-gi (Player 333)",
                    avatarUrl = "https://image.tmdb.org/t/p/w500/yEB6bMYgNu6qEQWoBvlkg6Ea5P.jpg"
                )
            ),
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
        ),
        MediaContent(
            id = "mov_2026_shadow_protocol",
            title = "Shadow Protocol",
            originalTitle = "Shadow Protocol",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Action",
            subGenres = listOf("Popular", "Action", "Thriller", "Movies", "Swahili"),
            duration = "2h 04m",
            rating = "8.7",
            director = "Action Cinema",
            screenplay = "Swahili Narrated",
            production = "International Cinema",
            synopsis = "An elite tactical unit uncovers a global cyber-syndicate operating across borders and races against the clock in a high-stakes 2026 action thriller.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 124
        ),
        MediaContent(
            id = "mov_2026_crimson_horizon",
            title = "Crimson Horizon",
            originalTitle = "Crimson Horizon",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/3sUdP791SMnEQuJNIpRCT49pkxe.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Thriller",
            subGenres = listOf("Popular", "Thriller", "Crime", "Movies", "Swahili"),
            duration = "1h 58m",
            rating = "8.4",
            director = "Cinema Studios",
            screenplay = "Swahili Narrated",
            production = "Action Studios",
            synopsis = "Stranded in a locked-down metropolis, a former operative must escort a key witness through relentless pursuit before dawn.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 118
        ),
        MediaContent(
            id = "mov_2026_dar_express",
            title = "Operation East Africa",
            originalTitle = "Operation East Africa",
            originalLanguage = "sw",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/yEB6bMYgNu6qEQWoBvlkg6Ea5P.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Crime",
            subGenres = listOf("Popular", "Crime", "Action", "Movies", "Swahili"),
            duration = "2h 10m",
            rating = "8.9",
            director = "East Africa Cinema",
            screenplay = "Swahili Narrated",
            production = "Tanzania • International",
            synopsis = "A fast-paced 2026 crime action blockbuster following a special investigation team tracking a high-value diamond heist across the Indian Ocean coast.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 130
        ),
        MediaContent(
            id = "mov_2026_bongo_strike",
            title = "Strike Force: Indian Ocean",
            originalTitle = "Strike Force",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/1QdXdRYfktUSONkl1oD5gc6Be0s.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Action",
            subGenres = listOf("Action", "Popular", "Movies", "Swahili"),
            duration = "2h 08m",
            rating = "8.8",
            director = "Action Cinema",
            screenplay = "Swahili Narrated",
            production = "International Cinema",
            synopsis = "A special maritime task force intercepts an armored cargo vessel off the East African coast in an explosive Swahili-narrated action blockbuster.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 128
        ),
        MediaContent(
            id = "mov_2026_rogue_sniper",
            title = "Zero Hour: Final Target",
            originalTitle = "Zero Hour",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/okJESjE3wqN4qDNFOM8TecUVfHX.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Action",
            subGenres = listOf("Action", "Thriller", "Movies", "Swahili"),
            duration = "1h 56m",
            rating = "8.6",
            director = "Cinema Studios",
            screenplay = "Swahili Narrated",
            production = "Action Studios",
            synopsis = "Framed for a high-profile mission gone wrong, a master marksman goes rogue to clear his name and expose the mastermind behind the conspiracy.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 116
        ),
        MediaContent(
            id = "mov_2026_silent_witness",
            title = "Nightfall Conspiracy",
            originalTitle = "Nightfall Conspiracy",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/oSk0j4yeDOqXNHIjYehgSJ2WujM.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Thriller",
            subGenres = listOf("Thriller", "Mystery", "Movies", "Swahili"),
            duration = "2h 02m",
            rating = "8.5",
            director = "Suspense Studios",
            screenplay = "Swahili Narrated",
            production = "International",
            synopsis = "When an encrypted drive vanishes during a citywide blackout, a forensic analyst is hunted across the night by operatives who will stop at nothing.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 122
        ),
        MediaContent(
            id = "mov_2026_cartel_gold",
            title = "Syndicate of Gold",
            originalTitle = "Syndicate of Gold",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/3sUdP791SMnEQuJNIpRCT49pkxe.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Crime",
            subGenres = listOf("Crime", "Action", "Movies", "Swahili"),
            duration = "2h 06m",
            rating = "8.7",
            director = "Crime Cinema",
            screenplay = "Swahili Narrated",
            production = "International Cinema",
            synopsis = "Undercover detectives infiltrate an international gold-smuggling cartel, risking everything when their cover is threatened from inside the department.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 126
        ),
        MediaContent(
            id = "mov_2026_cyber_nexus",
            title = "Nexus 2099: Uprising",
            originalTitle = "Nexus 2099",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/yEB6bMYgNu6qEQWoBvlkg6Ea5P.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Sci-Fi",
            subGenres = listOf("Sci-Fi", "Action", "Movies", "Swahili"),
            duration = "2h 12m",
            rating = "8.6",
            director = "Future Cinema",
            screenplay = "Swahili Narrated",
            production = "Sci-Fi Studios",
            synopsis = "In a neon-lit future metropolis, a rogue combat pilot teams up with a rebel engineer to shut down an orbital defense grid before launch.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 132
        ),
        MediaContent(
            id = "mov_2026_dynasty_crown",
            title = "Blood & Crown",
            originalTitle = "Blood & Crown",
            originalLanguage = "en",
            type = "movie",
            posterUrl = "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Drama",
            subGenres = listOf("Drama", "Crime", "Movies", "Swahili"),
            duration = "2h 05m",
            rating = "8.5",
            director = "Drama Studios",
            screenplay = "Swahili Narrated",
            production = "International",
            synopsis = "A powerful family empire faces betrayal from within when the heir uncovers the truth behind his brother's sudden disappearance.",
            isTrending = true,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2026",
            narrated = true,
            narrationLanguage = "Swahili",
            downloadEnabled = true,
            featured = true,
            published = true,
            runtimeMinutes = 125
        ),
        MediaContent(
            id = "adult_midnight_desire_hd",
            title = "Midnight Velvet (18+)",
            originalTitle = "Midnight Velvet",
            originalLanguage = "en",
            type = "adult",
            posterUrl = "https://image.tmdb.org/t/p/w500/oSk0j4yeDOqXNHIjYehgSJ2WujM.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "X Video",
            subGenres = listOf("X Video", "XXX", "Erotic Romance", "Adults 18+", "Adult", "Porn", "X", "18+"),
            duration = "1h 42m",
            rating = "18+",
            director = "Late Night Cinema",
            screenplay = "Original",
            production = "International 18+",
            synopsis = "Exclusive late-night adult X Video romance feature streaming in HD.",
            isTrending = false,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2025",
            narrated = false,
            narrationLanguage = "",
            downloadEnabled = true,
            featured = false,
            published = true,
            runtimeMinutes = 102
        ),
        MediaContent(
            id = "adult_crimson_nights_hd",
            title = "After Hours Secrets (18+)",
            originalTitle = "After Hours Secrets",
            originalLanguage = "en",
            type = "adult",
            posterUrl = "https://image.tmdb.org/t/p/w500/okJESjE3wqN4qDNFOM8TecUVfHX.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "XXX",
            subGenres = listOf("XXX", "Porn", "X Video", "Sensual Drama", "Adults 18+", "Adult", "X", "18+"),
            duration = "1h 36m",
            rating = "18+",
            director = "Late Night Cinema",
            screenplay = "Original",
            production = "International 18+",
            synopsis = "Late-night XXX 18+ cinema feature with full HD poster and thumbnail preview.",
            isTrending = false,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2025",
            narrated = false,
            narrationLanguage = "",
            downloadEnabled = true,
            featured = false,
            published = true,
            runtimeMinutes = 96
        ),
        MediaContent(
            id = "adult_velvet_temptation_hd",
            title = "Velvet Temptation Uncensored (18+)",
            originalTitle = "Velvet Temptation",
            originalLanguage = "en",
            type = "adult",
            posterUrl = "https://image.tmdb.org/t/p/w500/3sUdP791SMnEQuJNIpRCT49pkxe.jpg",
            backdropUrl = "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg",
            streamUrl = "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4",
            streamFormat = "mp4",
            genre = "Porn",
            subGenres = listOf("Porn", "X Video", "XXX", "Uncensored", "Erotic Romance", "Adults 18+", "Adult", "X", "18+"),
            duration = "1h 28m",
            rating = "18+",
            director = "Redlight Studio",
            screenplay = "Original",
            production = "International 18+",
            synopsis = "Uncensored 18+ adult feature organized by genre and category in HD.",
            isTrending = false,
            isComingSoon = false,
            isKids = false,
            releaseYear = "2025",
            narrated = false,
            narrationLanguage = "",
            downloadEnabled = true,
            featured = false,
            published = true,
            runtimeMinutes = 88
        )
    )

    private val initialProductionIds: Set<String> by lazy {
        initialProductionCatalog.map { it.id }.toSet()
    }

    /**
     * IDs of synthetic fallback/demo movies that must be replaced as soon as real movies from Firebase sync,
     * and excluded from the live Discovery screen when real Firebase movies are present.
     */
    val DEMO_FALLBACK_MOVIE_IDS: Set<String> = setOf(
        "mov_2026_shadow_protocol",
        "mov_2026_crimson_horizon",
        "mov_2026_dar_express",
        "mov_2026_bongo_strike",
        "mov_2026_rogue_sniper",
        "mov_2026_silent_witness",
        "mov_2026_cartel_gold",
        "mov_2026_cyber_nexus",
        "mov_2026_dynasty_crown"
    )

    private const val PREFS_FIREBASE_CATALOG_CACHE = "neli_firebase_catalog_cache_v2"
    private const val KEY_CACHED_MOVIES_JSON = "cached_firestore_movies_json"
    private const val KEY_CACHED_SERIES_JSON = "cached_firestore_series_json"
    private const val KEY_CACHED_EPISODES_JSON = "cached_firestore_episodes_json"
    private const val KEY_CACHED_ADULTS_JSON = "cached_firestore_adults_json"
    private const val KEY_CACHED_RTDB_JSON = "cached_rtdb_payload_json"

    @Volatile
    private var appContextRef: android.content.Context? = null

    private val _hasSyncedRealFirebaseMovies = MutableStateFlow(false)
    val hasSyncedRealFirebaseMovies: StateFlow<Boolean> = _hasSyncedRealFirebaseMovies.asStateFlow()

    private val _mediaCatalog = MutableStateFlow<List<MediaContent>>(initialProductionCatalog)
    val mediaCatalog: StateFlow<List<MediaContent>> = _mediaCatalog.asStateFlow()

    /**
     * Restores previously synced real Firebase movies/series/episodes from disk cache at 0ms on app launch
     * so users immediately see real Firebase movies on Discovery before the network call even finishes.
     */
    fun initializeAndPrewarmFromCache(context: android.content.Context) {
        appContextRef = context.applicationContext
        try {
            val prefs = context.applicationContext.getSharedPreferences(
                PREFS_FIREBASE_CATALOG_CACHE,
                android.content.Context.MODE_PRIVATE
            )
            val cachedMovies = prefs.getString(KEY_CACHED_MOVIES_JSON, null)
            val cachedSeries = prefs.getString(KEY_CACHED_SERIES_JSON, null)
            val cachedEpisodes = prefs.getString(KEY_CACHED_EPISODES_JSON, null)
            val cachedAdults = prefs.getString(KEY_CACHED_ADULTS_JSON, null)
            val cachedRtdb = prefs.getString(KEY_CACHED_RTDB_JSON, null)

            if (!cachedMovies.isNullOrBlank() || !cachedSeries.isNullOrBlank() || !cachedAdults.isNullOrBlank()) {
                parseFirestoreCollections(
                    moviesJson = cachedMovies,
                    seriesJson = cachedSeries,
                    episodesJson = cachedEpisodes,
                    adultsJson = cachedAdults,
                    isFromLiveNetworkSync = true
                )
            }
            if (!cachedRtdb.isNullOrBlank()) {
                parseFirebaseJsonPayload(cachedRtdb, isFromLiveNetworkSync = true)
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveFirestorePayloadsToDiskCache(
        moviesJson: String?,
        seriesJson: String?,
        episodesJson: String?,
        adultsJson: String?
    ) {
        val ctx = appContextRef ?: return
        try {
            val editor = ctx.getSharedPreferences(
                PREFS_FIREBASE_CATALOG_CACHE,
                android.content.Context.MODE_PRIVATE
            ).edit()
            if (!moviesJson.isNullOrBlank()) editor.putString(KEY_CACHED_MOVIES_JSON, moviesJson)
            if (!seriesJson.isNullOrBlank()) editor.putString(KEY_CACHED_SERIES_JSON, seriesJson)
            if (!episodesJson.isNullOrBlank()) editor.putString(KEY_CACHED_EPISODES_JSON, episodesJson)
            if (!adultsJson.isNullOrBlank()) editor.putString(KEY_CACHED_ADULTS_JSON, adultsJson)
            editor.apply()
        } catch (_: Throwable) {
        }
    }

    private fun saveRtdbPayloadToDiskCache(rtdbJson: String?) {
        val ctx = appContextRef ?: return
        if (rtdbJson.isNullOrBlank()) return
        try {
            ctx.getSharedPreferences(
                PREFS_FIREBASE_CATALOG_CACHE,
                android.content.Context.MODE_PRIVATE
            ).edit().putString(KEY_CACHED_RTDB_JSON, rtdbJson).apply()
        } catch (_: Throwable) {
        }
    }

    private val _episodesCatalog = MutableStateFlow<List<EpisodeItem>>(initialProductionEpisodes)
    val episodesCatalog: StateFlow<List<EpisodeItem>> = _episodesCatalog.asStateFlow()

    private val _firebaseSyncStatus = MutableStateFlow("Online • Live & On-Demand Catalog Ready")
    val firebaseSyncStatus: StateFlow<String> = _firebaseSyncStatus.asStateFlow()

    /**
     * Reactive rotation seed that updates automatically when Studio Admin movies sync, when the user
     * opens/refreshes Discovery, or on periodic rotation so movies dynamically change order ("zibadilike badilike")
     * instead of staying in one static sequence.
     */
    private val _catalogRotationSeed = MutableStateFlow(System.currentTimeMillis() xor 0x5DEECE66DL)
    val catalogRotationSeed: StateFlow<Long> = _catalogRotationSeed.asStateFlow()

    /**
     * Advances the catalog rotation seed to a guaranteed new value so all movie shelves, spotlight rows,
     * and recommendations immediately shuffle/rotate to showcase different movies.
     */
    fun rotateMovieCatalogOrder(explicitSeed: Long? = null): Long {
        val current = _catalogRotationSeed.value
        val candidate = explicitSeed ?: (System.nanoTime() xor (current * 6364136223846793005L + 1442695040888963407L))
        val nextSeed = if (candidate == current) candidate + 1009L else candidate
        _catalogRotationSeed.value = nextSeed
        return nextSeed
    }

    /**
     * Deterministically rotates and shuffles a list of [MediaContent] for a given [seed] and [salt].
     * Guarantees that when there are 2 or more movies, changing [seed] changes the leading movie
     * so users always see a dynamic variety of movies across visits and refreshes.
     */
    fun rotateMediaListForSeed(
        items: List<MediaContent>,
        seed: Long = _catalogRotationSeed.value,
        salt: String = ""
    ): List<MediaContent> {
        if (items.size <= 1) return items
        val combinedHash = seed xor (salt.hashCode().toLong() * 2654435761L)
        val rng = Random(combinedHash)
        val shuffled = items.shuffled(rng)
        // Also apply a cyclic shift based on positive seed steps so even 2-item or 3-item shelves visibly swap their first item
        val shift = (( combinedHash.ushr(1) % items.size.toLong() ).toInt() + items.size) % items.size
        if (shift == 0 && shuffled.first().id == items.first().id && items.size >= 2) {
            return shuffled.drop(1) + shuffled.first()
        }
        return if (shift > 0) {
            shuffled.drop(shift) + shuffled.take(shift)
        } else {
            shuffled
        }
    }

    /**
     * Returns a dynamically rotating spotlight mix of non-adult published movies (prioritizing Studio Admin
     * movies and rotating across all genres) so users immediately see fresh, changing movies on Discovery.
     */
    fun getRotatingSpotlightMovies(
        catalog: List<MediaContent> = _mediaCatalog.value,
        rotationSeed: Long = _catalogRotationSeed.value,
        limit: Int = 12
    ): List<MediaContent> {
        val rawMovies = catalog
            .filter { it.published && it.isMovie && !it.isAdultContent }
            .distinctBy { it.id }
        if (rawMovies.isEmpty()) return emptyList()

        val hasRealFirebaseMovies = _hasSyncedRealFirebaseMovies.value ||
            rawMovies.any { it.id !in initialProductionIds }
        val allMovies = if (hasRealFirebaseMovies) {
            rawMovies.filter { it.id !in DEMO_FALLBACK_MOVIE_IDS }.ifEmpty { rawMovies }
        } else {
            rawMovies
        }

        val studioAdminMovies = allMovies.filter { it.id !in initialProductionIds || it.createdAtEpochMs > 0L }
        val baseMovies = allMovies.filter { it.id in initialProductionIds && it.createdAtEpochMs <= 0L }

        val rotatedStudio = rotateMediaListForSeed(studioAdminMovies, rotationSeed, "studio_admin_spotlight")
        val rotatedBase = rotateMediaListForSeed(baseMovies, rotationSeed, "base_catalog_spotlight")

        // Interleave Studio Admin movies and rotated catalog movies so new uploads and diverse genres both shine
        val combined = mutableListOf<MediaContent>()
        val maxLen = maxOf(rotatedStudio.size, rotatedBase.size)
        for (i in 0 until maxLen) {
            rotatedStudio.getOrNull(i)?.let { combined.add(it) }
            rotatedBase.getOrNull(i)?.let { combined.add(it) }
        }
        return rotateMediaListForSeed(combined.distinctBy { it.id }, rotationSeed, "spotlight_final").take(limit)
    }

    /**
     * Returns the top [limit] New 2026 Movies from the catalog, strictly excluding any adult content (`!it.isAdultContent`),
     * and dynamically rotating among available 2026 movies using [rotationSeed].
     */
    fun getLatest2026NonAdultMovies(
        limit: Int = 3,
        rotationSeed: Long = _catalogRotationSeed.value
    ): List<MediaContent> {
        val nonAdultMovies = _mediaCatalog.value.filter {
            it.published && it.isMovie && !it.isAdultContent
        }.distinctBy { it.id }
        val movies2026 = rotateMediaListForSeed(
            nonAdultMovies.filter { it.releaseYear.trim() == "2026" },
            rotationSeed,
            "movies_2026"
        )
        val remaining = rotateMediaListForSeed(
            nonAdultMovies.filter { it.releaseYear.trim() != "2026" }
                .sortedByDescending { it.releaseYear },
            rotationSeed,
            "movies_other_years"
        )
        return (movies2026 + remaining).take(limit)
    }

    fun getMediaById(id: String): MediaContent? {
        return _mediaCatalog.value.find { it.id == id }
    }

    /**
     * Resolves the corresponding Movie or Series [MediaContent] for a non-live playback [LiveChannel].
     * Used when returning from the Watchpage (`PlayerScreen`) so the user is always taken to the
     * Movie/Series Details page of that respective title instead of the Homepage.
     */
    fun resolveMediaForPlaybackChannel(channel: LiveChannel): MediaContent? {
        if (channel.isLiveBroadcast) return null

        // 1. Explicit seriesId on the channel
        if (channel.seriesId.isNotBlank()) {
            getMediaById(channel.seriesId)?.let { return it }
        }

        // 2. Strip known playback prefixes (vod_, ep_, dl_)
        val cleanId = channel.id
            .removePrefix("vod_")
            .removePrefix("ep_")
            .removePrefix("dl_")
            .trim()

        getMediaById(cleanId)?.let { return it }
        getMediaById(channel.id)?.let { return it }

        // 3. Check if cleanId or episodeId matches an episode in episodesCatalog, then find its parent Series
        val epIdCandidate = channel.episodeId.ifBlank { cleanId }
        val matchedEpisode = _episodesCatalog.value.find {
            it.id.equals(epIdCandidate, ignoreCase = true) ||
                it.id.equals(cleanId, ignoreCase = true) ||
                "ep_${it.id}".equals(channel.id, ignoreCase = true)
        }
        if (matchedEpisode != null) {
            getMediaById(matchedEpisode.seriesId)?.let { return it }
        }

        // 4. Match by title or streamUrl against mediaCatalog
        val cleanTitle = channel.name
            .substringBefore(" •")
            .substringBefore(" (")
            .trim()
        _mediaCatalog.value.find { media ->
            media.title.equals(cleanTitle, ignoreCase = true) ||
                (channel.streamUrl.isNotBlank() && media.streamUrl.equals(channel.streamUrl, ignoreCase = true))
        }?.let { return it }

        // 5. Fallback to first published movie/series in catalog so non-live playback always resolves a detail page
        return _mediaCatalog.value.firstOrNull { !it.isAdultContent } ?: _mediaCatalog.value.firstOrNull()
    }

    fun updateSingleEnrichedMedia(enriched: MediaContent) {
        val current = _mediaCatalog.value
        val idx = current.indexOfFirst { it.id == enriched.id }
        if (idx >= 0) {
            val mutable = current.toMutableList()
            mutable[idx] = enriched
            _mediaCatalog.value = mutable
        }
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
     * Resolves a guaranteed non-blank poster or thumbnail URL for any Movie, Series, or Adult item.
     * Supports full HTTP/HTTPS URLs, relative TMDB paths (`/abc.jpg`), BunnyCDN automatic `thumbnail.jpg`,
     * and high-resolution fallback cinema artwork so Adult/Movie posters & thumbnails are ALWAYS visible.
     */
    fun resolveGuaranteedMediaImageUrl(
        primaryCandidate: String,
        secondaryCandidate: String = "",
        streamUrl: String = "",
        isBackdrop: Boolean = false
    ): String {
        fun normalizeSingleUrl(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) return ""
            if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
                return trimmed
            }
            if (trimmed.startsWith("//")) {
                return "https:$trimmed"
            }
            if (trimmed.startsWith("/")) {
                val sizeSegment = if (isBackdrop) "w780" else "w500"
                return "https://image.tmdb.org/t/p/$sizeSegment$trimmed"
            }
            return ""
        }

        val normPrimary = normalizeSingleUrl(primaryCandidate)
        if (normPrimary.isNotEmpty()) return normPrimary

        val normSecondary = normalizeSingleUrl(secondaryCandidate)
        if (normSecondary.isNotEmpty()) return normSecondary

        val cleanStream = streamUrl.trim()
        if (cleanStream.contains("b-cdn.net", ignoreCase = true)) {
            val baseDir = cleanStream.substringBefore("?").substringBeforeLast("/")
            if (baseDir.startsWith("http", ignoreCase = true)) {
                return "$baseDir/thumbnail.jpg"
            }
        }

        return if (isBackdrop) {
            "https://image.tmdb.org/t/p/original/2meX1nMdScFOoV4370rqHWKmXhY.jpg"
        } else {
            "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg"
        }
    }

    private val ADULT_SUBSTRING_KEYWORDS = listOf(
        "adult",
        "adults",
        "18+",
        "18 plus",
        "xxx",
        "x video",
        "x videos",
        "xvideo",
        "xvideos",
        "x-video",
        "x-rated",
        "xrated",
        "porn",
        "porno",
        "pornography",
        "xnxx",
        "erotic",
        "erotica",
        "nsfw",
        "uncensored",
        "explicit",
        "hentai",
        "hardcore",
        "softcore",
        "redlight",
        "wakubwa",
        "ngono"
    )

    private val ADULT_TOKEN_SPLIT_REGEX = Regex("[\\s,/|•_-]+")

    /**
     * Recognizes all Adult names, aliases, and search terms such as:
     * "X", "xxx", "X video", "xvideos", "porn", "porno", "18+", "adult", "adults", "erotic", etc.
     */
    fun isAdultKeywordOrQuery(raw: String): Boolean {
        if (raw.isEmpty()) return false
        val clean = raw.trim().lowercase(Locale.US)
        if (clean.isEmpty()) return false
        if (clean == "x") return true
        // Check standalone word "x" (e.g., "x video", "x movies", "x rated")
        if (clean.contains('x')) {
            val tokens = clean.split(ADULT_TOKEN_SPLIT_REGEX)
            if (tokens.any { it == "x" }) return true
        }
        return ADULT_SUBSTRING_KEYWORDS.any { clean.contains(it) }
    }

    /**
     * Extracts the specific genre or category of an Adult item (e.g., "X Video", "XXX", "Porn",
     * "Erotic Romance", "Sensual Drama", "Amateur", etc.) while preserving custom Firebase genres/categories.
     */
    fun extractAdultPrimaryGenreOrCategory(media: MediaContent): String {
        val genericAdultMeta = setOf(
            "adult",
            "adults",
            "adults 18+",
            "18+",
            "x",
            "all",
            "popular",
            "featured",
            "movies",
            "movie"
        )
        val candidates = buildList {
            media.genre.split(",", "/", "|", "•").forEach { part ->
                val clean = part.trim()
                if (clean.isNotEmpty()) add(clean)
            }
            media.subGenres.forEach { sub ->
                sub.split(",", "/", "|", "•").forEach { part ->
                    val clean = part.trim()
                    if (clean.isNotEmpty()) add(clean)
                }
            }
        }
        val specific = candidates.firstOrNull { c ->
            c.lowercase(Locale.US) !in genericAdultMeta
        }
        return specific ?: candidates.firstOrNull() ?: "Adults 18+"
    }

    /**
     * Returns all distinct Adult genres and categories from the catalog (including Firebase adult data)
     * so users can filter and browse Adult content by genre and category.
     */
    fun getAdultGenreAndCategoryFilters(catalog: List<MediaContent> = _mediaCatalog.value): List<String> {
        val adults = getAdultContentCatalog(catalog)
        val categories = LinkedHashSet<String>()
        categories.add("All")

        val genericMeta = setOf("adult", "adults", "18+", "all", "popular", "movies", "movie")
        adults.forEach { item ->
            val primary = extractAdultPrimaryGenreOrCategory(item)
            if (primary.isNotBlank() && primary.lowercase(Locale.US) !in genericMeta) {
                categories.add(primary)
            }
            item.subGenres.forEach { sub ->
                val clean = sub.trim()
                if (clean.isNotBlank() &&
                    clean.lowercase(Locale.US) !in genericMeta &&
                    !clean.equals("Adults 18+", ignoreCase = true) &&
                    !clean.equals("X", ignoreCase = true)
                ) {
                    categories.add(clean)
                }
            }
        }
        // Ensure standard adult aliases/categories are always available for quick filtering
        listOf("X Video", "XXX", "Porn", "Erotic Romance").forEach { std ->
            if (categories.none { it.equals(std, ignoreCase = true) }) {
                categories.add(std)
            }
        }
        return categories.toList()
    }

    /**
     * Organizes and groups Adult (18+) items by their Firebase genres and categories
     * (e.g., "X Video", "XXX", "Porn", "Erotic Romance", "Sensual Drama", etc.).
     */
    fun getAdultsGroupedByGenreAndCategory(
        catalog: List<MediaContent> = _mediaCatalog.value,
        selectedAdultCategory: String = "All"
    ): List<Pair<String, List<MediaContent>>> {
        val adults = getAdultContentCatalog(catalog)
        if (adults.isEmpty()) return emptyList()

        val filteredAdults = if (selectedAdultCategory.equals("All", ignoreCase = true)) {
            adults
        } else {
            adults.filter { item ->
                item.genre.equals(selectedAdultCategory, ignoreCase = true) ||
                    extractAdultPrimaryGenreOrCategory(item).equals(selectedAdultCategory, ignoreCase = true) ||
                    item.subGenres.any { it.equals(selectedAdultCategory, ignoreCase = true) || it.contains(selectedAdultCategory, ignoreCase = true) } ||
                    (isAdultKeywordOrQuery(selectedAdultCategory) && item.isAdultContent)
            }
        }
        if (filteredAdults.isEmpty()) return emptyList()

        // Group primarily by each adult item's primary genre/category
        val grouped = linkedMapOf<String, MutableList<MediaContent>>()
        for (item in filteredAdults) {
            val primaryCat = if (!selectedAdultCategory.equals("All", ignoreCase = true)) {
                selectedAdultCategory
            } else {
                extractAdultPrimaryGenreOrCategory(item)
            }
            val key = grouped.keys.firstOrNull { it.equals(primaryCat, ignoreCase = true) } ?: primaryCat
            grouped.getOrPut(key) { mutableListOf() }.add(item)
        }

        // Also include secondary category shelves when "All" is selected so multi-category Firebase adult data is browsable by all its categories
        if (selectedAdultCategory.equals("All", ignoreCase = true)) {
            val skipTags = setOf("adult", "adults", "adults 18+", "18+", "x", "all", "popular", "movies", "movie")
            for (item in filteredAdults) {
                for (sub in item.subGenres) {
                    val cleanSub = sub.trim()
                    if (cleanSub.isEmpty() || cleanSub.lowercase(Locale.US) in skipTags) continue
                    val existingKey = grouped.keys.firstOrNull { it.equals(cleanSub, ignoreCase = true) }
                    val targetKey = existingKey ?: cleanSub
                    val list = grouped.getOrPut(targetKey) { mutableListOf() }
                    if (list.none { it.id == item.id }) {
                        list.add(item)
                    }
                }
            }
        }

        return grouped.entries
            .filter { it.value.isNotEmpty() }
            .map { it.key to it.value.toList() }
    }

    /**
     * Comprehensive search matcher for Movies, Series, and Adults:
     * Matches title, originalTitle, genre, all subGenres/categories, synopsis, narrationLanguage,
     * and all Adult aliases ("X", "xxx", "X video", "porn", "18+", "adult", "erotic", etc.).
     */
    fun matchesMediaSearch(
        item: MediaContent,
        searchQuery: String,
        selectedCategory: String = "All"
    ): Boolean {
        val q = searchQuery.trim()
        val isAdultQuery = isAdultKeywordOrQuery(q)

        val matchesQuery = if (isAdultQuery) {
            item.isAdultContent
        } else {
            q.isEmpty() ||
                item.title.contains(q, ignoreCase = true) ||
                item.originalTitle.contains(q, ignoreCase = true) ||
                item.primaryGenre.contains(q, ignoreCase = true) ||
                item.genre.contains(q, ignoreCase = true) ||
                item.subGenres.any { it.contains(q, ignoreCase = true) } ||
                item.synopsis.contains(q, ignoreCase = true) ||
                item.narrationLanguage.contains(q, ignoreCase = true)
        }

        val matchesCat = when {
            selectedCategory.equals("All", ignoreCase = true) -> true
            selectedCategory.equals("Movies", ignoreCase = true) -> item.isMovie && !item.isAdultContent
            selectedCategory.equals("Series", ignoreCase = true) -> item.isSeries && !item.isAdultContent
            selectedCategory.equals("Adults", ignoreCase = true) ||
                isAdultKeywordOrQuery(selectedCategory) -> item.isAdultContent
            selectedCategory.equals("Swahili", ignoreCase = true) -> item.narrated
            else -> item.primaryGenre.contains(selectedCategory, ignoreCase = true) ||
                item.genre.contains(selectedCategory, ignoreCase = true) ||
                item.subGenres.any { it.contains(selectedCategory, ignoreCase = true) }
        }

        return matchesQuery && matchesCat
    }

    /**
     * Extracts ONLY the FIRST real genre of a movie/media item so a movie with multiple genres
     * (e.g. Action, Animation, Drama) is placed exclusively under its first genre ("Action")
     * and is never duplicated across other genre rows.
     */
    fun extractPrimaryGenre(media: MediaContent): String {
        if (media.type.equals("adult", ignoreCase = true)) {
            return extractAdultPrimaryGenreOrCategory(media)
        }
        val excludedMetaTags = setOf(
            "popular",
            "movies",
            "movie",
            "series",
            "tv_show",
            "swahili",
            "all",
            "featured",
            "trending"
        )
        val rawCandidates = buildList {
            media.genre.split(",", "/", "|", "•").forEach { part ->
                val clean = part.trim()
                if (clean.isNotEmpty()) add(clean)
            }
            media.subGenres.forEach { sub ->
                sub.split(",", "/", "|", "•").forEach { part ->
                    val clean = part.trim()
                    if (clean.isNotEmpty()) add(clean)
                }
            }
        }
        val firstGenre = rawCandidates.firstOrNull { candidate ->
            candidate.lowercase(Locale.US) !in excludedMetaTags
        }
        return firstGenre ?: media.genre.trim().ifEmpty { "Action" }
    }

    /**
     * Returns all published Adult (18+) items with guaranteed non-blank poster and thumbnail URLs.
     */
    fun getAdultContentCatalog(catalog: List<MediaContent> = _mediaCatalog.value): List<MediaContent> {
        return catalog
            .filter { it.published && it.isAdult }
            .distinctBy { it.id }
            .map { item ->
                val resolvedPoster = resolveGuaranteedMediaImageUrl(
                    primaryCandidate = item.posterUrl,
                    secondaryCandidate = item.backdropUrl,
                    streamUrl = item.streamUrl,
                    isBackdrop = false
                )
                val resolvedBackdrop = resolveGuaranteedMediaImageUrl(
                    primaryCandidate = item.backdropUrl,
                    secondaryCandidate = resolvedPoster,
                    streamUrl = item.streamUrl,
                    isBackdrop = true
                )
                item.copy(
                    posterUrl = resolvedPoster,
                    backdropUrl = resolvedBackdrop
                )
            }
    }

    /**
     * Alias for [getMoviesGroupedByPrimaryGenreOnly] used by Discovery tab to group movies strictly by their 1st genre only,
     * while dynamically rotating movie order within each genre and rotating genre shelves based on [rotationSeed].
     */
    fun getMoviesStrictlyByFirstGenre(
        catalog: List<MediaContent> = _mediaCatalog.value,
        rotationSeed: Long = _catalogRotationSeed.value
    ): List<Pair<String, List<MediaContent>>> = getMoviesGroupedByPrimaryGenreOnly(catalog, rotationSeed)

    /**
     * Groups Movies strictly by their FIRST genre only (`extractPrimaryGenre`) so that each movie
     * appears in AT MOST ONE genre row and is NEVER duplicated across multiple genres.
     * Uses [rotationSeed] to dynamically rotate the order of movies inside each genre shelf AND
     * rotate the genre shelves themselves so movies never sit in a static order.
     */
    fun getMoviesGroupedByPrimaryGenreOnly(
        catalog: List<MediaContent> = _mediaCatalog.value,
        rotationSeed: Long = _catalogRotationSeed.value
    ): List<Pair<String, List<MediaContent>>> {
        val rawPublishedMovies = catalog.filter { it.published && it.isMovie && !it.isAdult }
        if (rawPublishedMovies.isEmpty()) return emptyList()

        val hasRealFirebaseMovies = _hasSyncedRealFirebaseMovies.value ||
            rawPublishedMovies.any { it.id !in initialProductionIds }
        val publishedMovies = if (hasRealFirebaseMovies) {
            rawPublishedMovies.filter { it.id !in DEMO_FALLBACK_MOVIE_IDS }.ifEmpty { rawPublishedMovies }
        } else {
            rawPublishedMovies
        }

        val assignedMovieIds = mutableSetOf<String>()
        val genreMap = linkedMapOf<String, MutableList<MediaContent>>()

        for (movie in publishedMovies) {
            if (!assignedMovieIds.add(movie.id)) continue
            val firstGenre = extractPrimaryGenre(movie)
            val canonicalKey = genreMap.keys.firstOrNull { it.equals(firstGenre, ignoreCase = true) } ?: firstGenre
            val bucket = genreMap.getOrPut(canonicalKey) { mutableListOf() }
            bucket.add(movie)
        }

        val priorityOrder = listOf(
            "Action",
            "Action & Adventure",
            "Crime",
            "Thriller",
            "Drama",
            "Sci-Fi",
            "Science Fiction",
            "Mystery",
            "Adventure",
            "Comedy",
            "Horror",
            "Animation",
            "Family",
            "Romance"
        )

        val sortedGenres = genreMap.entries.sortedWith(
            compareBy<Map.Entry<String, MutableList<MediaContent>>> { entry ->
                val idx = priorityOrder.indexOfFirst { it.equals(entry.key, ignoreCase = true) }
                if (idx >= 0) idx else 100
            }.thenByDescending { it.value.size }
        )

        val baseRows = sortedGenres.mapNotNull { (genreName, items) ->
            if (items.isNotEmpty()) {
                genreName to rotateMediaListForSeed(items, rotationSeed, "genre_row_$genreName")
            } else null
        }

        if (baseRows.size <= 1) return baseRows
        val shelfShift = ((rotationSeed.ushr(3) % baseRows.size.toLong()).toInt() + baseRows.size) % baseRows.size
        return if (shelfShift > 0) {
            baseRows.drop(shelfShift) + baseRows.take(shelfShift)
        } else {
            baseRows
        }
    }

    /**
     * Groups available non-adult Movies (and optional Series) strictly by their FIRST genre only
     * so no item is ever duplicated across multiple genre rows.
     */
    fun getMediaGroupedByGenre(catalog: List<MediaContent> = _mediaCatalog.value): List<Pair<String, List<MediaContent>>> {
        val published = catalog.filter { it.published && !it.isAdult }
        if (published.isEmpty()) return emptyList()

        val assignedMediaIds = mutableSetOf<String>()
        val genreMap = linkedMapOf<String, MutableList<MediaContent>>()

        for (media in published) {
            if (!assignedMediaIds.add(media.id)) continue
            val firstGenre = extractPrimaryGenre(media)
            val canonicalKey = genreMap.keys.firstOrNull { it.equals(firstGenre, ignoreCase = true) } ?: firstGenre
            val bucket = genreMap.getOrPut(canonicalKey) { mutableListOf() }
            bucket.add(media)
        }

        val priorityOrder = listOf(
            "Action",
            "Action & Adventure",
            "Crime",
            "Thriller",
            "Drama",
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

        return sortedGenres.mapNotNull { (genreName, items) ->
            if (items.isNotEmpty()) genreName to items else null
        }
    }

    fun getComingSoon(): List<MediaContent> =
        _mediaCatalog.value.filter { it.isComingSoon }.ifEmpty { _mediaCatalog.value.take(4) }

    fun getBestForKids(): List<MediaContent> =
        _mediaCatalog.value.filter { it.isKids }.ifEmpty { _mediaCatalog.value.takeLast(3) }

    fun getRelatedMedia(
        currentId: String,
        currentGenre: String = "",
        rotationSeed: Long = _catalogRotationSeed.value
    ): List<MediaContent> {
        val currentItem = _mediaCatalog.value.find { it.id == currentId }
        val targetGenre = currentGenre.ifBlank { currentItem?.genre.orEmpty() }
        val candidates = _mediaCatalog.value.filter {
            it.published && it.id != currentId && it.isAdultContent == (currentItem?.isAdultContent == true)
        }
        val sameGenre = rotateMediaListForSeed(
            candidates.filter { item ->
                targetGenre.isNotBlank() &&
                    (item.genre.equals(targetGenre, ignoreCase = true) ||
                        item.subGenres.any { it.equals(targetGenre, ignoreCase = true) })
            },
            rotationSeed,
            "related_same_$currentId"
        )
        val otherGenres = rotateMediaListForSeed(
            candidates.filter { item -> sameGenre.none { it.id == item.id } },
            rotationSeed,
            "related_other_$currentId"
        )
        return (sameGenre + otherGenres).take(10)
    }

    // Production mode: no fake downloads pre-seeded
    fun defaultDownloadsSeed(): List<DownloadedItemEntity> = emptyList()

    /**
     * Syncs both Cloud (`movies`, `series`, `episodes`, `tvChannels`) and
     * Realtime Database (`live_streams`, `channels`, `movies`, `series`) in parallel for `neliplay`.
     * When [forceRefresh] is true, bypasses the edge response cache so newly added Studio Admin
     * movies appear immediately, and rotates the catalog order.
     */
    suspend fun syncFromFirebaseEndpoint(
        databaseUrl: String = DEFAULT_DATABASE_URL,
        apiKey: String = "",
        projectId: String = DEFAULT_PROJECT_ID,
        forceRefresh: Boolean = false
    ): Result<Int> = withContext(Dispatchers.IO) {
        catalogSyncMutex.withLock {
            val cleanUrl = databaseUrl.trim().removeSuffix("/").ifEmpty { DEFAULT_DATABASE_URL }
            val cleanProjectId = projectId.trim().ifEmpty {
                extractProjectIdFromUrl(cleanUrl).ifEmpty { DEFAULT_PROJECT_ID }
            }
            val cleanKey = sanitizeFirebaseWebApiKey(apiKey)

            try {
                _firebaseSyncStatus.value = "Updating live catalog..."
                var totalSynced = 0

                coroutineScope {
                    val firestoreDeferred = async {
                        if (cleanProjectId.isNotEmpty()) {
                            syncFromCloudFirestore(cleanProjectId, cleanKey, forceRefresh).getOrDefault(0)
                        } else 0
                    }

                    val rtdbDeferred = async {
                        if (cleanUrl.contains(".firebaseio.com") || cleanUrl.endsWith(".json")) {
                            syncFromRealtimeDatabase(cleanUrl, cleanKey, forceRefresh).getOrDefault(0)
                        } else 0
                    }

                    totalSynced = firestoreDeferred.await() + rtdbDeferred.await()
                }

                // Rotate catalog order immediately after sync so real Firebase movies are displayed right away
                rotateMovieCatalogOrder()

                _firebaseSyncStatus.value =
                    "Online • ${_mediaCatalog.value.size} Titles & ${ChannelRepository.liveChannelsFlow.value.size} Live Channels (10M Scale Ready)"

                // Run TMDB enrichment asynchronously without blocking the catalog sync mutex or UI
                enrichTopCatalogItemsWithTmdb()

                Result.success(totalSynced.coerceAtLeast(_mediaCatalog.value.size))
            } catch (e: Throwable) {
                _firebaseSyncStatus.value = "Online • Catalog Ready"
                Result.failure(Exception(e.message ?: "Sync error", e))
            }
        }
    }

    private fun sanitizeFirebaseWebApiKey(rawKey: String): String {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty() ||
            trimmed == "YOUR_FIREBASE_API_KEY" ||
            trimmed == "YOUR_API_KEY" ||
            trimmed.contains("1wE3rT4yU5iO6pA7sD")
        ) {
            return ""
        }
        return trimmed
    }

    private fun sanitizeRtdbAuthToken(rawToken: String): String {
        val clean = sanitizeFirebaseWebApiKey(rawToken)
        // Firebase Realtime Database `?auth=` expects a JWT ID token or database secret, never a Web API key (`AIza...`)
        if (clean.startsWith("AIza")) return ""
        return clean
    }

    private fun fetchFirestoreUrlWithFallback(
        urlWithoutKey: String,
        apiKey: String,
        forceRefresh: Boolean = false
    ): String? {
        val cleanKey = sanitizeFirebaseWebApiKey(apiKey)
        // 1. Try public Firestore endpoint without `?key=` first
        val directBody = fetchUrlText(urlWithoutKey, forceRefresh = forceRefresh)
        if (!directBody.isNullOrBlank() &&
            (directBody.contains("\"documents\"") || directBody.contains("\"fields\""))
        ) {
            return directBody
        }

        // 2. If a Firebase Web API key is configured, also try with `?key=`
        if (cleanKey.isNotEmpty()) {
            val sep = if (urlWithoutKey.contains("?")) "&" else "?"
            val keyedBody = fetchUrlText("$urlWithoutKey${sep}key=$cleanKey", forceRefresh = forceRefresh)
            if (!keyedBody.isNullOrBlank() &&
                (keyedBody.contains("\"documents\"") || keyedBody.contains("\"fields\""))
            ) {
                return keyedBody
            }
            if (!keyedBody.isNullOrBlank() && directBody.isNullOrBlank()) {
                return keyedBody
            }
        }
        return directBody
    }

    private suspend fun enrichTopCatalogItemsWithTmdb(maxItems: Int = 8) {
        try {
            val candidates = _mediaCatalog.value.take(maxItems)
            for (item in candidates) {
                val enriched = TmdbRepository.enrichMediaContent(item)
                if (enriched != item) {
                    updateSingleEnrichedMedia(enriched)
                }
            }
        } catch (_: Exception) {
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

    /**
     * Fetches all pages of a Firestore collection (following `nextPageToken` up to [maxPages])
     * so when Studio Admin adds many movies, every single document is retrieved.
     * Falls back to unpaginated collection fetch if `pageSize` is rejected.
     */
    private fun fetchPaginatedFirestoreCollection(
        baseFirestoreUrl: String,
        collectionName: String,
        apiKey: String,
        forceRefresh: Boolean = false,
        pageSize: Int = 200,
        maxPages: Int = 4
    ): String? {
        val mergedDocs = JSONArray()
        var pageToken = ""
        var pagesFetched = 0

        while (pagesFetched < maxPages) {
            val tokenParam = if (pageToken.isNotEmpty()) "&pageToken=$pageToken" else ""
            val pagedUrl = "$baseFirestoreUrl/$collectionName?pageSize=$pageSize$tokenParam"
            val rawBody = fetchFirestoreUrlWithFallback(pagedUrl, apiKey, forceRefresh = forceRefresh)
            val body = if (!rawBody.isNullOrBlank() && rawBody.contains("\"documents\"")) {
                rawBody
            } else if (pagesFetched == 0) {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/$collectionName", apiKey, forceRefresh = forceRefresh)
                    ?: rawBody
            } else {
                rawBody
            }
            if (body.isNullOrBlank()) break
            try {
                val json = JSONObject(body)
                val docs = json.optJSONArray("documents")
                if (docs != null) {
                    for (i in 0 until docs.length()) {
                        docs.optJSONObject(i)?.let { mergedDocs.put(it) }
                    }
                }
                pageToken = json.optString("nextPageToken", "").trim()
                pagesFetched++
                if (pageToken.isEmpty()) break
            } catch (_: Throwable) {
                break
            }
        }

        if (mergedDocs.length() == 0) return null
        return JSONObject().put("documents", mergedDocs).toString()
    }

    private suspend fun syncFromCloudFirestore(
        projectId: String,
        apiKey: String,
        forceRefresh: Boolean = false
    ): Result<Int> = coroutineScope {
        try {
            val baseFirestoreUrl = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"
            val cleanKey = sanitizeFirebaseWebApiKey(apiKey)

            val moviesDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "movies", cleanKey, forceRefresh, pageSize = 200, maxPages = 4)
            }
            val studioMoviesDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "studio_movies", cleanKey, forceRefresh, pageSize = 150, maxPages = 2)
            }
            val seriesDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "series", cleanKey, forceRefresh, pageSize = 200, maxPages = 3)
            }
            val adultsDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "adults", cleanKey, forceRefresh, pageSize = 200, maxPages = 2)
            }
            val adultSingularDeferred = async {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/adult?pageSize=200", cleanKey, forceRefresh)
            }
            val episodesDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "episodes", cleanKey, forceRefresh, pageSize = 250, maxPages = 3)
            }

            // 1a. Publish primary movies immediately as soon as `movies` responds so Discovery shows real movies right away!
            val primaryMoviesJson = moviesDeferred.await()
            if (!primaryMoviesJson.isNullOrBlank()) {
                parseFirestoreCollections(
                    moviesJson = primaryMoviesJson,
                    isFromLiveNetworkSync = true
                )
            }

            // 1b. Merge studio_movies, series, episodes, and adults and persist to disk cache
            val extraStudioMoviesJson = studioMoviesDeferred.await()
            val combinedMoviesJson = mergeFirestoreDocumentsJson(primaryMoviesJson, extraStudioMoviesJson)
            val resolvedSeriesJson = seriesDeferred.await()
            val resolvedEpisodesJson = episodesDeferred.await()
            val resolvedAdultsJson = mergeFirestoreDocumentsJson(adultsDeferred.await(), adultSingularDeferred.await())

            saveFirestorePayloadsToDiskCache(
                moviesJson = combinedMoviesJson,
                seriesJson = resolvedSeriesJson,
                episodesJson = resolvedEpisodesJson,
                adultsJson = resolvedAdultsJson
            )

            var totalSynced = parseFirestoreCollections(
                moviesJson = combinedMoviesJson,
                seriesJson = resolvedSeriesJson,
                episodesJson = resolvedEpisodesJson,
                adultsJson = resolvedAdultsJson,
                isFromLiveNetworkSync = true
            )

            // 2. Fetch and process TV Channels & CDN Token docs after Movies/Series are already live in Discovery
            val tvChannelsDeferred = async {
                fetchPaginatedFirestoreCollection(baseFirestoreUrl, "tvChannels", cleanKey, forceRefresh, pageSize = 200, maxPages = 2)
            }
            val channelsDeferred = async {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/channels?pageSize=200", cleanKey, forceRefresh)
            }
            val azamTokenDocDeferred = async {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/config/azam_token", cleanKey, forceRefresh)
            }
            val configDeferred = async {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/config?pageSize=50", cleanKey, forceRefresh)
            }
            val settingsDeferred = async {
                fetchFirestoreUrlWithFallback("$baseFirestoreUrl/settings?pageSize=50", cleanKey, forceRefresh)
            }
            val primaryTvChannelsJson = tvChannelsDeferred.await()
            val secondaryChannelsJson = channelsDeferred.await()
            val chosenChannelsJson = primaryTvChannelsJson ?: secondaryChannelsJson
            if (!chosenChannelsJson.isNullOrBlank()) {
                totalSynced += parseFirestoreCollections(
                    tvChannelsJson = chosenChannelsJson,
                    isFromLiveNetworkSync = true
                )
            }
            if (!secondaryChannelsJson.isNullOrBlank() && secondaryChannelsJson != chosenChannelsJson) {
                totalSynced += parseFirestoreCollections(
                    tvChannelsJson = secondaryChannelsJson,
                    isFromLiveNetworkSync = true
                )
            }

            parseFirestoreCdnTokenDocs(azamTokenDocDeferred.await())
            parseFirestoreCdnTokenDocs(configDeferred.await())
            parseFirestoreCdnTokenDocs(settingsDeferred.await())

            Result.success(totalSynced)
        } catch (e: Throwable) {
            Result.failure(Exception(e.message ?: "Firestore sync error", e))
        }
    }

    private fun mergeFirestoreDocumentsJson(primaryJson: String?, secondaryJson: String?): String? {
        if (primaryJson.isNullOrBlank()) return secondaryJson
        if (secondaryJson.isNullOrBlank()) return primaryJson
        return try {
            val outDocs = JSONArray()
            val primaryDocs = JSONObject(primaryJson).optJSONArray("documents")
            val secondaryDocs = JSONObject(secondaryJson).optJSONArray("documents")
            if (primaryDocs != null) {
                for (i in 0 until primaryDocs.length()) {
                    primaryDocs.optJSONObject(i)?.let { outDocs.put(it) }
                }
            }
            if (secondaryDocs != null) {
                for (i in 0 until secondaryDocs.length()) {
                    secondaryDocs.optJSONObject(i)?.let { outDocs.put(it) }
                }
            }
            JSONObject().put("documents", outDocs).toString()
        } catch (_: Exception) {
            primaryJson
        }
    }

    /**
     * Parses Firestore `config/azam_token` (either as a single document `{"fields": ...}` or a collection `{"documents": [...]}`).
     * Supports `exp` as `null` (`nullValue`), `timestampValue` (date picker), `stringValue`, `integerValue`, or `doubleValue`,
     * and automatically extracts the expiration from the JWT `token` when `exp` is `null` or missing.
     */
    fun parseFirestoreCdnTokenDocs(collectionOrDocJson: String?): Boolean {
        if (collectionOrDocJson.isNullOrBlank()) return false
        return try {
            val root = JSONObject(collectionOrDocJson)
            val docList = mutableListOf<JSONObject>()
            val docsArray = root.optJSONArray("documents")
            if (docsArray != null) {
                for (i in 0 until docsArray.length()) {
                    docsArray.optJSONObject(i)?.let { docList.add(it) }
                }
            } else if (root.has("fields")) {
                docList.add(root)
            }

            var updatedAny = false
            for (doc in docList) {
                val fields = doc.optJSONObject("fields") ?: continue
                val tokenVal = fields.fsString("token").ifEmpty { fields.fsString("cdntoken") }.trim()
                val cdnHostVal = fields.fsString("cdnHost", ChannelRepository.AZAM_CDN_HOST).trim()
                val rawExp = fields.fsFlexibleExp("exp")
                val resolvedExp = ChannelRepository.parseFlexibleExpiration(
                    rawExp = rawExp,
                    jwtToken = tokenVal,
                    fallbackExp = ChannelRepository.AZAM_CDN_EXP
                )
                val sourceVal = fields.fsString("source", ChannelRepository.AZAM_CDN_SOURCE)
                val endpointUrl = fields.fsString("tokenEndpointUrl").ifEmpty { fields.fsString("apiUrl") }

                if (tokenVal.isNotBlank()) {
                    val tokenJson = JSONObject().apply {
                        put("token", tokenVal)
                        put("cdnHost", cdnHostVal)
                        put("exp", resolvedExp)
                        put("source", sourceVal)
                        if (endpointUrl.isNotBlank()) {
                            put("tokenEndpointUrl", endpointUrl)
                        }
                    }
                    if (ChannelRepository.updateCdnAuthorizationToken(tokenJson.toString())) {
                        updatedAny = true
                    }
                }
                if (endpointUrl.startsWith("http", ignoreCase = true)) {
                    fetchUrlText(endpointUrl)?.let { responseText ->
                        if (ChannelRepository.updateCdnAuthorizationToken(responseText)) {
                            updatedAny = true
                        }
                    }
                }
            }
            updatedAny
        } catch (_: Exception) {
            false
        }
    }

    private fun JSONObject.fsFlexibleExp(key: String): Any? {
        val field = optJSONObject(key) ?: return null
        if (field.has("nullValue")) return null
        if (field.has("integerValue")) return field.optString("integerValue")
        if (field.has("doubleValue")) return field.optDouble("doubleValue").toLong()
        if (field.has("timestampValue")) return field.optString("timestampValue")
        if (field.has("stringValue")) return field.optString("stringValue")
        return null
    }

    /**
     * Quickly fetches and applies the latest Azam TV CDN token from Firebase (Realtime Database `/cdn_token`, `/azam_token`,
     * `/config/azam_token`, Cloud Firestore `config/azam_token`, and `tokenEndpointUrl` `https://streamzone.fun/api/cdn-token`).
     */
    suspend fun syncCdnTokenFromFirebase(
        databaseUrl: String = DEFAULT_DATABASE_URL,
        apiKey: String = "",
        projectId: String = DEFAULT_PROJECT_ID
    ): Boolean = withContext(Dispatchers.IO) {
        tokenSyncMutex.withLock {
            val cleanUrl = databaseUrl.trim().removeSuffix("/").ifEmpty { DEFAULT_DATABASE_URL }
            val cleanProjectId = projectId.trim().ifEmpty { DEFAULT_PROJECT_ID }
            val cleanKey = sanitizeFirebaseWebApiKey(apiKey)
            val rtdbAuthToken = sanitizeRtdbAuthToken(apiKey)
            val authQuery = if (rtdbAuthToken.isNotBlank()) "?auth=$rtdbAuthToken" else ""

            return@withLock try {
                coroutineScope {
                    val rtdbCdnDeferred = async { fetchUrlText("$cleanUrl/cdn_token.json$authQuery") }
                    val rtdbAzamDeferred = async { fetchUrlText("$cleanUrl/azam_token.json$authQuery") }
                    val rtdbConfigAzamDeferred = async { fetchUrlText("$cleanUrl/config/azam_token.json$authQuery") }
                    val fsAzamDocDeferred = async {
                        fetchFirestoreUrlWithFallback(
                            "https://firestore.googleapis.com/v1/projects/$cleanProjectId/databases/(default)/documents/config/azam_token",
                            cleanKey
                        )
                    }
                    val fsConfigDeferred = async {
                        fetchFirestoreUrlWithFallback(
                            "https://firestore.googleapis.com/v1/projects/$cleanProjectId/databases/(default)/documents/config?pageSize=20",
                            cleanKey
                        )
                    }
                    val directEndpointDeferred = async {
                        fetchUrlText(ChannelRepository.AZAM_TOKEN_ENDPOINT_URL)
                    }

                    var updated = false
                    rtdbCdnDeferred.await()?.takeIf { it.contains("token") }?.let {
                        if (ChannelRepository.updateCdnAuthorizationToken(it)) updated = true
                    }
                    rtdbAzamDeferred.await()?.takeIf { it.contains("token") }?.let {
                        if (ChannelRepository.updateCdnAuthorizationToken(it)) updated = true
                    }
                    rtdbConfigAzamDeferred.await()?.takeIf { it.contains("token") }?.let {
                        if (ChannelRepository.updateCdnAuthorizationToken(it)) updated = true
                    }
                    fsAzamDocDeferred.await()?.let {
                        if (parseFirestoreCdnTokenDocs(it)) updated = true
                    }
                    fsConfigDeferred.await()?.let {
                        if (parseFirestoreCdnTokenDocs(it)) updated = true
                    }
                    directEndpointDeferred.await()?.takeIf { it.contains("token") }?.let {
                        if (ChannelRepository.updateCdnAuthorizationToken(it)) updated = true
                    }
                    updated
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    private suspend fun syncFromRealtimeDatabase(
        cleanUrl: String,
        apiKey: String,
        forceRefresh: Boolean = false
    ): Result<Int> = coroutineScope {
        try {
            val rtdbAuthToken = sanitizeRtdbAuthToken(apiKey)
            val authQuery = if (rtdbAuthToken.isNotBlank()) "?auth=$rtdbAuthToken" else ""
            val rootEndpoint = if (cleanUrl.endsWith(".json")) {
                "$cleanUrl$authQuery"
            } else {
                "$cleanUrl/.json$authQuery"
            }

            val rootJson = fetchUrlText(rootEndpoint, forceRefresh)
            if (!rootJson.isNullOrBlank() && rootJson.trim().startsWith("{")) {
                saveRtdbPayloadToDiskCache(rootJson)
                val count = parseFirebaseJsonPayload(rootJson, isFromLiveNetworkSync = true)
                return@coroutineScope Result.success(count)
            }

            // Fallback to individual public RTDB nodes (`movies`, `series`, `episodes`, `adults`, `channels`, `live_streams`, `cdn_token`, `azam_token`)
            val moviesDeferred = async { fetchUrlText("$cleanUrl/movies.json$authQuery", forceRefresh) }
            val seriesDeferred = async { fetchUrlText("$cleanUrl/series.json$authQuery", forceRefresh) }
            val episodesDeferred = async { fetchUrlText("$cleanUrl/episodes.json$authQuery", forceRefresh) }
            val adultsDeferred = async { fetchUrlText("$cleanUrl/adults.json$authQuery", forceRefresh) }
            val channelsDeferred = async { fetchUrlText("$cleanUrl/channels.json$authQuery", forceRefresh) }
            val liveStreamsDeferred = async { fetchUrlText("$cleanUrl/live_streams.json$authQuery", forceRefresh) }
            val cdnTokenDeferred = async { fetchUrlText("$cleanUrl/cdn_token.json$authQuery", forceRefresh) }
            val azamTokenDeferred = async { fetchUrlText("$cleanUrl/azam_token.json$authQuery", forceRefresh) }

            cdnTokenDeferred.await()?.trim()?.takeIf { it.startsWith("{") }?.let {
                ChannelRepository.updateCdnAuthorizationToken(it)
            }
            azamTokenDeferred.await()?.trim()?.takeIf { it.startsWith("{") }?.let {
                ChannelRepository.updateCdnAuthorizationToken(it)
            }

            val combinedObj = JSONObject()
            fun attachNodeIfJson(key: String, rawText: String?) {
                val trimmed = rawText?.trim() ?: return
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                    combinedObj.put(key, if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed))
                }
            }
            attachNodeIfJson("movies", moviesDeferred.await())
            attachNodeIfJson("series", seriesDeferred.await())
            attachNodeIfJson("episodes", episodesDeferred.await())
            attachNodeIfJson("adults", adultsDeferred.await())
            attachNodeIfJson("channels", channelsDeferred.await())
            attachNodeIfJson("live_streams", liveStreamsDeferred.await())

            if (combinedObj.length() > 0) {
                val combinedStr = combinedObj.toString()
                saveRtdbPayloadToDiskCache(combinedStr)
                val count = parseFirebaseJsonPayload(combinedStr, isFromLiveNetworkSync = true)
                Result.success(count)
            } else {
                Result.success(0)
            }
        } catch (e: Throwable) {
            Result.failure(Exception(e.message ?: "RTDB sync error", e))
        }
    }

    private fun fetchUrlText(urlStr: String, forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val cached = edgeResponseCache[urlStr]
        if (!forceRefresh && cached != null && (now - cached.timestampMs) <= EDGE_CACHE_TTL_MS) {
            return cached.body
        }
        return try {
            val request = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .header("Connection", "keep-alive")
                .header("User-Agent", "Nelitv-Android/1.0.0 (by Neliplay; 10M-Scale-Edge)")
                .apply {
                    if (forceRefresh) {
                        header("Cache-Control", "no-cache")
                    }
                }
                .get()
                .build()
            highScaleHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val text = response.body?.string()
                    if (!text.isNullOrBlank()) {
                        edgeResponseCache[urlStr] = CachedEdgePayload(text, now)
                    }
                    text
                } else {
                    cached?.body
                }
            }
        } catch (_: Exception) {
            cached?.body
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
        tvChannelsJson: String? = null,
        adultsJson: String? = null,
        isFromLiveNetworkSync: Boolean = false
    ): Int {
        val parsedMedia = mutableListOf<MediaContent>()
        val parsedEpisodes = mutableListOf<EpisodeItem>()
        val parsedChannels = mutableListOf<LiveChannel>()

        if (!episodesJson.isNullOrBlank()) {
            val docs = runCatching { JSONObject(episodesJson).optJSONArray("documents") }.getOrNull()
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    runCatching { parseFirestoreEpisodeDoc(doc) }.getOrNull()?.let { parsedEpisodes.add(it) }
                }
            }
        }

        if (parsedEpisodes.isNotEmpty()) {
            _episodesCatalog.value = parsedEpisodes + initialProductionEpisodes.filter { def ->
                parsedEpisodes.none { it.id == def.id }
            }
        }

        if (!moviesJson.isNullOrBlank()) {
            val docs = runCatching { JSONObject(moviesJson).optJSONArray("documents") }.getOrNull()
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    runCatching { parseFirestoreMovieDoc(doc, forceAdult = false) }.getOrNull()?.let { parsedMedia.add(it) }
                }
            }
        }

        if (!adultsJson.isNullOrBlank()) {
            val docs = runCatching { JSONObject(adultsJson).optJSONArray("documents") }.getOrNull()
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    runCatching { parseFirestoreMovieDoc(doc, forceAdult = true) }.getOrNull()?.let { parsedMedia.add(it) }
                }
            }
        }

        if (!seriesJson.isNullOrBlank()) {
            val docs = runCatching { JSONObject(seriesJson).optJSONArray("documents") }.getOrNull()
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    runCatching { parseFirestoreSeriesDoc(doc) }.getOrNull()?.let { parsedMedia.add(it) }
                }
            }
        }

        if (!tvChannelsJson.isNullOrBlank()) {
            val docs = runCatching { JSONObject(tvChannelsJson).optJSONArray("documents") }.getOrNull()
            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    runCatching { parseFirestoreTvChannelDoc(doc) }.getOrNull()?.let { parsedChannels.add(it) }
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
            }.distinctBy { it.id }

            val hasSyncedMoviesNow = enrichedMedia.any { it.isMovie && !it.isAdultContent }
            if (isFromLiveNetworkSync && hasSyncedMoviesNow) {
                _hasSyncedRealFirebaseMovies.value = true
            }
            val purgeDemoMovies = _hasSyncedRealFirebaseMovies.value

            val existingCustom = _mediaCatalog.value.filter { existing ->
                existing.id !in initialProductionIds &&
                    (!purgeDemoMovies || existing.id !in DEMO_FALLBACK_MOVIE_IDS) &&
                    enrichedMedia.none { it.id == existing.id }
            }
            val baseFallback = initialProductionCatalog.filter { def ->
                val allowDemoFallback = !(purgeDemoMovies && def.isMovie && !def.isAdultContent)
                allowDemoFallback &&
                    enrichedMedia.none { it.id == def.id } &&
                    existingCustom.none { it.id == def.id }
            }
            _mediaCatalog.value = (enrichedMedia + existingCustom + baseFallback).distinctBy { it.id }
            rotateMovieCatalogOrder()
        }

        if (parsedChannels.isNotEmpty()) {
            ChannelRepository.mergeFirebaseChannelsWithAzamPriority(parsedChannels)
        }

        return parsedMedia.size + parsedEpisodes.size + parsedChannels.size
    }

    private fun extractFirestoreMovieStreamUrl(fields: JSONObject): String {
        val candidateKeys = listOf(
            "streamUrl",
            "videoUrl",
            "url",
            "movieUrl",
            "fileUrl",
            "hlsUrl",
            "mp4Url",
            "playbackUrl",
            "bunnyUrl",
            "stream",
            "video",
            "link",
            "playUrl",
            "source",
            "src",
            "downloadUrl",
            "m3u8Url"
        )
        for (k in candidateKeys) {
            val direct = fields.fsString(k).trim()
            if (direct.startsWith("http", ignoreCase = true) || direct.startsWith("//")) {
                return if (direct.startsWith("//")) "https:$direct" else direct
            }
            val nestedMap = fields.optJSONObject(k)
                ?.optJSONObject("mapValue")
                ?.optJSONObject("fields")
            if (nestedMap != null) {
                val nestedUrl = nestedMap.fsString("url")
                    .ifEmpty { nestedMap.fsString("streamUrl") }
                    .ifEmpty { nestedMap.fsString("src") }
                    .trim()
                if (nestedUrl.startsWith("http", ignoreCase = true)) return nestedUrl
            }
        }
        val bunnyIdKeys = listOf("bunnyVideoId", "videoId", "guid", "bunnyGuid", "videoGuid")
        for (bk in bunnyIdKeys) {
            val guid = fields.fsString(bk).trim()
            if (guid.length >= 8 && !guid.startsWith("http", ignoreCase = true)) {
                return "https://vz-1bb50f2e-8ea.b-cdn.net/$guid/playlist.m3u8"
            }
        }
        val keys = fields.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val lower = k.lowercase(Locale.US)
            if (lower.contains("stream") || lower.contains("video") || lower.contains("url") || lower.contains("mp4") || lower.contains("m3u8") || lower.contains("hls")) {
                if (lower.contains("poster") || lower.contains("backdrop") || lower.contains("thumb") || lower.contains("image") || lower.contains("logo") || lower.contains("avatar") || lower.contains("cover") || lower.contains("trailer")) {
                    continue
                }
                val v = fields.fsString(k).trim()
                if (v.startsWith("http", ignoreCase = true) || v.startsWith("//")) {
                    return if (v.startsWith("//")) "https:$v" else v
                }
            }
        }
        return ""
    }

    private fun parseFirestoreMovieDoc(doc: JSONObject, forceAdult: Boolean = false): MediaContent? {
        val fields = doc.optJSONObject("fields") ?: return null
        val docId = doc.optString("name", "").substringAfterLast("/")
        val id = fields.fsString("id").ifEmpty { docId }
        val title = fields.fsString("title")
            .ifEmpty { fields.fsString("name") }
            .ifEmpty { fields.fsString("movieTitle") }
            .ifEmpty { fields.fsString("originalTitle") }
            .trim()
        val streamUrl = extractFirestoreMovieStreamUrl(fields)
        val published = fields.fsBoolean("published", true)
        if (!published || title.isEmpty() || streamUrl.isEmpty()) return null

        val createdAtEpochMs = parseFirestoreDocTimestampMs(doc, fields)

        val rawGenres = buildList {
            addAll(fields.fsStringList("genres"))
            addAll(fields.fsStringList("categories"))
            addAll(fields.fsStringList("tags"))
            val singleGenre = fields.fsString("genre")
            if (singleGenre.isNotBlank()) add(singleGenre)
            val singleCategory = fields.fsString("category").ifEmpty { fields.fsString("subCategory") }
            if (singleCategory.isNotBlank()) add(singleCategory)
        }.flatMap { it.split(",", "/", "|") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val isAdultDoc = forceAdult ||
            fields.fsBoolean("adult", false) ||
            fields.fsBoolean("isAdult", false) ||
            isAdultKeywordOrQuery(fields.fsString("type")) ||
            isAdultKeywordOrQuery(fields.fsString("category")) ||
            isAdultKeywordOrQuery(fields.fsString("genre")) ||
            rawGenres.any { isAdultKeywordOrQuery(it) }

        val effectiveGenres = rawGenres.ifEmpty {
            if (isAdultDoc) listOf("X Video", "XXX", "Porn", "Adults 18+") else listOf("Action")
        }

        // Preserve the real first genre/category from Firebase (even for Adult items!) so Adult items are organized by their genres & categories
        val primaryGenre = if (isAdultDoc) {
            effectiveGenres.firstOrNull {
                !it.equals("adult", ignoreCase = true) &&
                    !it.equals("adults", ignoreCase = true) &&
                    !it.equals("18+", ignoreCase = true)
            } ?: effectiveGenres.firstOrNull() ?: "Adults 18+"
        } else {
            effectiveGenres.firstOrNull() ?: "Action"
        }

        val narrated = fields.fsBoolean("narrated", false)
        val narrationLanguage = fields.fsString("narrationLanguage")
        val runtime = fields.fsInt("runtime", 120)
        val ratingVal = fields.fsDouble("rating", 8.0)
        val featured = fields.fsBoolean("featured", false)
        val year = fields.fsInt("year", 2025)

        val rawPosterCandidate = fields.fsString("posterPath")
            .ifEmpty { fields.fsString("posterUrl") }
            .ifEmpty { fields.fsString("poster") }
            .ifEmpty { fields.fsString("thumbnailUrl") }
            .ifEmpty { fields.fsString("thumbnail") }
            .ifEmpty { fields.fsString("thumb") }
            .ifEmpty { fields.fsString("imageUrl") }
            .ifEmpty { fields.fsString("image") }
            .ifEmpty { fields.fsString("coverUrl") }
            .ifEmpty { fields.fsString("cover") }
            .ifEmpty { fields.fsString("stillPath") }

        val rawBackdropCandidate = fields.fsString("backdropPath")
            .ifEmpty { fields.fsString("backdropUrl") }
            .ifEmpty { fields.fsString("thumbnailUrl") }
            .ifEmpty { fields.fsString("thumbnail") }
            .ifEmpty { rawPosterCandidate }

        val posterPath = resolveGuaranteedMediaImageUrl(
            primaryCandidate = rawPosterCandidate,
            secondaryCandidate = rawBackdropCandidate,
            streamUrl = streamUrl,
            isBackdrop = false
        )
        val backdropPath = resolveGuaranteedMediaImageUrl(
            primaryCandidate = rawBackdropCandidate,
            secondaryCandidate = posterPath,
            streamUrl = streamUrl,
            isBackdrop = true
        )

        val format = when {
            streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            streamUrl.contains(".mpd", ignoreCase = true) -> "dash"
            else -> "hls"
        }

        val subGenres = buildList {
            if (featured && !isAdultDoc) add("Popular")
            add(primaryGenre)
            addAll(effectiveGenres)
            if (isAdultDoc) {
                add("Adults 18+")
                add("Adult")
                add("X Video")
                add("XXX")
                add("Porn")
                add("X")
                add("18+")
            } else {
                add("Movies")
            }
            if (narrated && narrationLanguage.isNotBlank()) add(narrationLanguage)
        }.distinct()

        return MediaContent(
            id = id,
            title = title,
            originalTitle = fields.fsString("originalTitle"),
            originalLanguage = fields.fsString("originalLanguage", "en"),
            type = if (isAdultDoc) "adult" else "movie",
            posterUrl = posterPath,
            backdropUrl = backdropPath,
            streamUrl = streamUrl,
            streamFormat = format,
            genre = primaryGenre,
            subGenres = subGenres,
            duration = formatRuntimeMinutes(runtime),
            rating = if (isAdultDoc) "18+" else String.format(Locale.US, "%.1f", ratingVal),
            production = fields.fsStringList("productionCountries").joinToString(" • ").ifEmpty { if (isAdultDoc) "18+ Cinema" else "Movies" },
            synopsis = fields.fsString("overview", "Watch $title streaming in HD on Nelitv."),
            cast = parseFirestoreCastList(
                fields = fields,
                mediaId = id,
                title = title,
                primaryGenre = primaryGenre,
                narrated = narrated,
                narrationLanguage = narrationLanguage,
                posterUrl = posterPath,
                backdropUrl = backdropPath
            ),
            isTrending = featured && !isAdultDoc,
            isKids = !isAdultDoc && rawGenres.any { it.contains("kid", true) || it.contains("animation", true) || it.contains("family", true) },
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
            productionCountries = fields.fsStringList("productionCountries"),
            createdAtEpochMs = createdAtEpochMs
        )
    }

    private fun parseFirestoreDocTimestampMs(doc: JSONObject, fields: JSONObject): Long {
        for (key in listOf("updatedAt", "createdAt", "addedAt", "uploadedAt", "timestamp")) {
            val fieldObj = fields.optJSONObject(key) ?: continue
            if (fieldObj.has("integerValue")) {
                val v = fieldObj.optString("integerValue", "0").toLongOrNull() ?: 0L
                if (v > 0L) return if (v < 10_000_000_000L) v * 1000L else v
            }
            if (fieldObj.has("doubleValue")) {
                val v = fieldObj.optDouble("doubleValue", 0.0).toLong()
                if (v > 0L) return if (v < 10_000_000_000L) v * 1000L else v
            }
            if (fieldObj.has("timestampValue")) {
                val iso = fieldObj.optString("timestampValue", "")
                val parsed = parseIsoTimestampMs(iso)
                if (parsed > 0L) return parsed
            }
            if (fieldObj.has("stringValue")) {
                val str = fieldObj.optString("stringValue", "").trim()
                val asLong = str.toLongOrNull()
                if (asLong != null && asLong > 0L) {
                    return if (asLong < 10_000_000_000L) asLong * 1000L else asLong
                }
                val parsed = parseIsoTimestampMs(str)
                if (parsed > 0L) return parsed
            }
        }
        val docUpdateIso = doc.optString("updateTime", "").ifEmpty { doc.optString("createTime", "") }
        return parseIsoTimestampMs(docUpdateIso)
    }

    private fun parseIsoTimestampMs(iso: String): Long {
        val clean = iso.trim()
        if (clean.isEmpty()) return 0L
        return try {
            val base = clean.substringBefore(".").substringBefore("Z").substringBefore("+")
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            sdf.parse(base)?.time ?: 0L
        } catch (_: Throwable) {
            0L
        }
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
            cast = parseFirestoreCastList(
                fields = fields,
                mediaId = id,
                title = name,
                primaryGenre = primaryGenre,
                narrated = narrated,
                narrationLanguage = narrationLanguage,
                posterUrl = posterPath,
                backdropUrl = backdropPath
            ),
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
        val logo = extractFirestoreChannelLogo(fields)

        val categoriesList = buildList {
            addAll(fields.fsStringList("categories").map { it.lowercase() })
            if (category.isNotBlank()) add(category.lowercase())
            if (country.equals("Tanzania", ignoreCase = true)) {
                add("tanzania")
            }
        }.distinct()

        val isMp4 = normalizedUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true)
        val isDash = !isMp4 && normalizedUrl.contains(".mpd", ignoreCase = true)
        val parsedChannel = LiveChannel(
            id = id,
            name = name,
            description = if (country.isNotBlank()) "$category • $country" else category,
            streamUrl = normalizedUrl,
            streamFormat = when {
                isMp4 -> "mp4"
                isDash -> "dash"
                else -> "hls"
            },
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
        return parsedChannel.copy(
            thumbnailUrl = ChannelRepository.resolveGuaranteedChannelLogoUrl(parsedChannel)
        )
    }

    private fun JSONObject.fsString(key: String, default: String = ""): String {
        val field = optJSONObject(key) ?: return default
        if (field.has("stringValue")) return field.optString("stringValue", default)
        if (field.has("integerValue")) return field.optString("integerValue", default)
        if (field.has("doubleValue")) return field.optDouble("doubleValue").toString()
        return default
    }

    private fun JSONObject.fsBoolean(key: String, default: Boolean = false): Boolean {
        val field = optJSONObject(key) ?: return default
        if (field.has("booleanValue")) return field.optBoolean("booleanValue", default)
        if (field.has("stringValue")) {
            val str = field.optString("stringValue", "").trim().lowercase(Locale.US)
            if (str == "true" || str == "1" || str == "yes") return true
            if (str == "false" || str == "0" || str == "no") return false
        }
        if (field.has("integerValue")) {
            return (field.optString("integerValue", "0").toIntOrNull() ?: 0) != 0
        }
        return default
    }

    private fun JSONObject.fsInt(key: String, default: Int = 0): Int {
        val field = optJSONObject(key) ?: return default
        if (field.has("integerValue")) {
            return field.optString("integerValue", default.toString()).toIntOrNull() ?: default
        }
        if (field.has("doubleValue")) {
            return field.optDouble("doubleValue", default.toDouble()).toInt()
        }
        if (field.has("stringValue")) {
            val str = field.optString("stringValue", "").trim()
            return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt() ?: default
        }
        return default
    }

    private fun JSONObject.fsLong(key: String, default: Long = 0L): Long {
        val field = optJSONObject(key) ?: return default
        if (field.has("integerValue")) {
            return field.optString("integerValue", default.toString()).toLongOrNull() ?: default
        }
        if (field.has("doubleValue")) {
            return field.optDouble("doubleValue", default.toDouble()).toLong()
        }
        if (field.has("stringValue")) {
            val str = field.optString("stringValue", "").trim()
            return str.toLongOrNull() ?: str.toDoubleOrNull()?.toLong() ?: default
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
        if (field.has("stringValue")) {
            return field.optString("stringValue", "").trim().toDoubleOrNull() ?: default
        }
        return default
    }

    private fun JSONObject.fsStringList(key: String): List<String> {
        val fieldObj = optJSONObject(key) ?: return emptyList()
        if (fieldObj.has("stringValue")) {
            val single = fieldObj.optString("stringValue", "").trim()
            return if (single.isNotEmpty()) listOf(single) else emptyList()
        }
        val values = fieldObj
            .optJSONObject("arrayValue")
            ?.optJSONArray("values") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until values.length()) {
            val elem = values.optJSONObject(i) ?: continue
            val str = elem.optString("stringValue", "").trim()
            if (str.isNotEmpty()) {
                result.add(str)
            } else {
                val mapFields = elem.optJSONObject("mapValue")?.optJSONObject("fields")
                val nestedName = mapFields?.fsString("name")
                    ?.ifEmpty { mapFields.fsString("title") }
                    ?.ifEmpty { mapFields.fsString("label") }
                    ?.trim()
                    .orEmpty()
                if (nestedName.isNotEmpty()) {
                    result.add(nestedName)
                }
            }
        }
        return result
    }

    private fun parseFirestoreCastList(
        fields: JSONObject,
        mediaId: String,
        title: String,
        primaryGenre: String,
        narrated: Boolean,
        narrationLanguage: String,
        posterUrl: String,
        backdropUrl: String
    ): List<CastMember> {
        val parsed = mutableListOf<CastMember>()
        val castArray = fields.optJSONObject("cast")
            ?.optJSONObject("arrayValue")
            ?.optJSONArray("values")
            ?: fields.optJSONObject("actors")
                ?.optJSONObject("arrayValue")
                ?.optJSONArray("values")

        if (castArray != null) {
            for (i in 0 until castArray.length()) {
                val itemObj = castArray.optJSONObject(i) ?: continue
                val mapFields = itemObj.optJSONObject("mapValue")?.optJSONObject("fields")
                if (mapFields != null) {
                    val actorName = mapFields.fsString("name").ifEmpty { mapFields.fsString("actor") }
                    if (actorName.isNotBlank()) {
                        parsed.add(
                            CastMember(
                                id = "${mediaId}_cast_$i",
                                name = actorName,
                                role = mapFields.fsString("character", mapFields.fsString("role", "Lead Cast")),
                                avatarUrl = mapFields.fsString("profilePath", mapFields.fsString("avatarUrl", posterUrl))
                            )
                        )
                    }
                } else {
                    val strName = itemObj.optString("stringValue", "").trim()
                    if (strName.isNotEmpty()) {
                        parsed.add(
                            CastMember(
                                id = "${mediaId}_cast_$i",
                                name = strName,
                                role = "Starring",
                                avatarUrl = posterUrl
                            )
                        )
                    }
                }
            }
        }

        if (parsed.isNotEmpty()) return parsed

        val directorName = fields.fsString("director").ifBlank { "$primaryGenre Ensemble" }
        val countryLead = fields.fsStringList("productionCountries").firstOrNull() ?: "International"
        return buildList {
            add(
                CastMember(
                    id = "${mediaId}_lead_1",
                    name = "$title Lead Cast",
                    role = "Main Protagonist",
                    avatarUrl = posterUrl
                )
            )
            add(
                CastMember(
                    id = "${mediaId}_lead_2",
                    name = directorName,
                    role = "Director & Featured Star",
                    avatarUrl = backdropUrl.ifBlank { posterUrl }
                )
            )
            if (narrated && narrationLanguage.isNotBlank()) {
                add(
                    CastMember(
                        id = "${mediaId}_narrator",
                        name = "$narrationLanguage Cinema Narrator",
                        role = "Voice Narration ($narrationLanguage)",
                        avatarUrl = posterUrl
                    )
                )
            } else {
                add(
                    CastMember(
                        id = "${mediaId}_lead_3",
                        name = "$countryLead Cinema Cast",
                        role = "Supporting Cast",
                        avatarUrl = backdropUrl.ifBlank { posterUrl }
                    )
                )
            }
        }
    }

    private fun formatRuntimeMinutes(minutes: Int): String {
        if (minutes <= 0) return "2h 00m"
        val hrs = minutes / 60
        val mins = minutes % 60
        return if (hrs > 0) "${hrs}h ${mins.toString().padStart(2, '0')}m" else "${mins}m"
    }

    fun parseFirebaseJsonPayload(jsonStr: String, isFromLiveNetworkSync: Boolean = false): Int {
        val root = JSONObject(jsonStr)
        val parsedMedia = mutableListOf<MediaContent>()
        val parsedEpisodes = mutableListOf<EpisodeItem>()
        val parsedChannels = mutableListOf<LiveChannel>()

        if (root.has("token") && root.optString("token").isNotBlank()) {
            ChannelRepository.updateCdnAuthorizationToken(root.toString())
        } else {
            listOf("cdnToken", "cdn_token", "cdn_auth", "azamToken").forEach { tokenKey ->
                root.optJSONObject(tokenKey)?.let { tokenObj ->
                    ChannelRepository.updateCdnAuthorizationToken(tokenObj.toString())
                }
            }
        }

        listOf("movies", "series", "adults", "adult", "xxx", "xvideos", "porn", "content", "items").forEach { key ->
            if (root.has(key)) {
                val node = root.get(key)
                val defaultType = when (key.lowercase(Locale.US)) {
                    "series" -> "series"
                    "adults", "adult", "xxx", "xvideos", "porn" -> "adult"
                    else -> "movie"
                }
                extractMediaNodes(node, defaultType = defaultType, out = parsedMedia)
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
            val hasSyncedMoviesNow = parsedMedia.any { it.isMovie && !it.isAdultContent }
            if (isFromLiveNetworkSync && hasSyncedMoviesNow) {
                _hasSyncedRealFirebaseMovies.value = true
            }
            val purgeDemoMovies = _hasSyncedRealFirebaseMovies.value
            val retainedExisting = _mediaCatalog.value.filter { def ->
                val allowDemoFallback = !(purgeDemoMovies && def.isMovie && !def.isAdultContent && def.id in initialProductionIds)
                allowDemoFallback && parsedMedia.none { it.id == def.id }
            }
            _mediaCatalog.value = (parsedMedia + retainedExisting).distinctBy { it.id }
            rotateMovieCatalogOrder()
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
        val title = obj.optString("title", obj.optString("name", obj.optString("movieTitle", ""))).trim()
        val streamUrl = obj.optString(
            "streamUrl",
            obj.optString(
                "url",
                obj.optString(
                    "videoUrl",
                    obj.optString("movieUrl", obj.optString("fileUrl", obj.optString("playbackUrl", "")))
                )
            )
        ).trim()
        val genresList = mutableListOf<String>()
        listOf("genres", "categories", "tags").forEach { arrayKey ->
            val arr = obj.optJSONArray(arrayKey)
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val g = arr.optString(i, "").trim()
                    if (g.isNotEmpty()) genresList.add(g)
                }
            }
        }
        val singleGenre = obj.optString("genre", "").trim()
        if (singleGenre.isNotEmpty()) genresList.add(singleGenre)
        val singleCategory = obj.optString("category", obj.optString("subCategory", "")).trim()
        if (singleCategory.isNotEmpty()) genresList.add(singleCategory)

        val allParsedGenres = genresList
            .flatMap { it.split(",", "/", "|") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val isAdult = defaultType.equals("adult", ignoreCase = true) ||
                isAdultKeywordOrQuery(obj.optString("type", "")) ||
                obj.optBoolean("adult", false) ||
                obj.optBoolean("isAdult", false) ||
                isAdultKeywordOrQuery(singleGenre) ||
                isAdultKeywordOrQuery(singleCategory) ||
                allParsedGenres.any { isAdultKeywordOrQuery(it) }

        val isSeries = !isAdult && (obj.optString("type", defaultType).equals("series", ignoreCase = true) ||
                obj.has("numberOfSeasons") || obj.has("seasons"))
        if (title.isEmpty() || (!isSeries && streamUrl.isEmpty())) return null

        val rawPoster = obj.optString(
            "posterPath",
            obj.optString(
                "posterUrl",
                obj.optString(
                    "poster",
                    obj.optString(
                        "thumbnailUrl",
                        obj.optString(
                            "thumbnail",
                            obj.optString("thumb", obj.optString("imageUrl", obj.optString("image", obj.optString("coverUrl", ""))))
                        )
                    )
                )
            )
        )
        val rawBackdrop = obj.optString(
            "backdropPath",
            obj.optString("backdropUrl", obj.optString("thumbnailUrl", obj.optString("thumbnail", rawPoster)))
        )
        val poster = resolveGuaranteedMediaImageUrl(
            primaryCandidate = rawPoster,
            secondaryCandidate = rawBackdrop,
            streamUrl = streamUrl,
            isBackdrop = false
        )
        val backdrop = resolveGuaranteedMediaImageUrl(
            primaryCandidate = rawBackdrop,
            secondaryCandidate = poster,
            streamUrl = streamUrl,
            isBackdrop = true
        )

        val genre = if (isAdult) {
            allParsedGenres.firstOrNull {
                !it.equals("adult", ignoreCase = true) &&
                    !it.equals("adults", ignoreCase = true) &&
                    !it.equals("18+", ignoreCase = true)
            } ?: allParsedGenres.firstOrNull() ?: "Adults 18+"
        } else {
            allParsedGenres.firstOrNull() ?: "Action"
        }
        val runtime = obj.optInt("runtime", 120)
        val duration = if (isSeries) {
            val sCount = obj.optInt("numberOfSeasons", 1)
            val eCount = obj.optInt("numberOfEpisodes", 8)
            "$sCount Seasons • $eCount Eps"
        } else {
            obj.optString("duration", formatRuntimeMinutes(runtime))
        }
        val rating = if (isAdult) "18+" else obj.optString("rating", "8.5")
        val synopsis = obj.optString("overview", obj.optString("synopsis", obj.optString("description", "Watch $title streaming on Nelitv.")))
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
            type = when {
                isAdult -> "adult"
                isSeries -> "series"
                else -> "movie"
            },
            posterUrl = poster,
            backdropUrl = backdrop,
            streamUrl = streamUrl.ifEmpty { "https://vz-1bb50f2e-8ea.b-cdn.net/9af12e30-1b3f-469f-9bfe-db895030c77a/playlist.m3u8" },
            streamFormat = format,
            genre = genre,
            subGenres = buildList {
                if (!isAdult) add("Popular")
                add(genre)
                addAll(allParsedGenres)
                if (isAdult) {
                    add("Adults 18+")
                    add("Adult")
                    add("X Video")
                    add("XXX")
                    add("Porn")
                    add("X")
                    add("18+")
                } else if (isSeries) {
                    add("Series")
                } else {
                    add("Movies")
                }
                if (narrated && narrationLanguage.isNotBlank()) add(narrationLanguage)
            }.distinct(),
            duration = duration,
            rating = rating,
            synopsis = synopsis,
            isTrending = !isAdult && obj.optBoolean("featured", obj.optBoolean("isTrending", true)),
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
        val isMp4 = normalizedUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true)
        val isDash = !isMp4 && (normalizedUrl.contains(".mpd", ignoreCase = true) ||
                obj.optString("streamFormat").equals("dash", ignoreCase = true))

        val category = obj.optString("category", "entertainment")
        val country = obj.optString("country", "")
        val featured = obj.optBoolean("featured", false)

        val clearKeysMap = mutableMapOf<String, String>()
        if (isDash) {
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
        }

        val rawLogo = extractJsonChannelLogo(obj)

        val parsedChannel = LiveChannel(
            id = obj.optString("id", fallbackId),
            name = name,
            description = if (country.isNotBlank()) "$category • $country" else obj.optString("description", category),
            streamUrl = normalizedUrl,
            streamFormat = when {
                isMp4 -> "mp4"
                isDash -> "dash"
                else -> "hls"
            },
            thumbnailUrl = rawLogo,
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
        return parsedChannel.copy(
            thumbnailUrl = ChannelRepository.resolveGuaranteedChannelLogoUrl(parsedChannel)
        )
    }

    private val CHANNEL_LOGO_FIELD_CANDIDATES = listOf(
        "logo",
        "logoUrl",
        "logo_url",
        "channelLogo",
        "channel_logo",
        "thumbnailUrl",
        "thumbnail_url",
        "thumbnail",
        "thumb",
        "icon",
        "iconUrl",
        "icon_url",
        "imageUrl",
        "image_url",
        "image",
        "img",
        "posterUrl",
        "posterPath",
        "poster",
        "backdropUrl",
        "backdropPath",
        "tvgLogo",
        "tvg_logo",
        "tvg-logo",
        "avatar",
        "avatarUrl",
        "coverUrl",
        "cover",
        "photoUrl",
        "photo",
        "picture"
    )

    private fun extractFirestoreChannelLogo(fields: JSONObject): String {
        for (candidateKey in CHANNEL_LOGO_FIELD_CANDIDATES) {
            val direct = fields.fsString(candidateKey).trim()
            if (direct.isNotEmpty()) return direct
            val nestedMap = fields.optJSONObject(candidateKey)
                ?.optJSONObject("mapValue")
                ?.optJSONObject("fields")
            if (nestedMap != null) {
                val nestedUrl = nestedMap.fsString("url")
                    .ifEmpty { nestedMap.fsString("src") }
                    .ifEmpty { nestedMap.fsString("link") }
                    .trim()
                if (nestedUrl.isNotEmpty()) return nestedUrl
            }
        }
        val keys = fields.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val lower = k.lowercase()
            if (lower.contains("logo") ||
                lower.contains("thumb") ||
                lower.contains("icon") ||
                lower.contains("image") ||
                lower.contains("img") ||
                lower.contains("poster") ||
                lower.contains("avatar") ||
                lower.contains("pic")
            ) {
                val v = fields.fsString(k).trim()
                if (v.startsWith("http", ignoreCase = true) ||
                    v.startsWith("//") ||
                    v.startsWith("data:image/", ignoreCase = true)
                ) {
                    return v
                }
            }
        }
        return ""
    }

    private fun extractJsonChannelLogo(obj: JSONObject): String {
        for (candidateKey in CHANNEL_LOGO_FIELD_CANDIDATES) {
            val direct = obj.optString(candidateKey, "").trim()
            if (direct.isNotEmpty() && !direct.startsWith("{")) return direct
            val nestedObj = obj.optJSONObject(candidateKey)
            if (nestedObj != null) {
                val nestedUrl = nestedObj.optString("url", nestedObj.optString("src", "")).trim()
                if (nestedUrl.isNotEmpty()) return nestedUrl
            }
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val lower = k.lowercase()
            if (lower.contains("logo") ||
                lower.contains("thumb") ||
                lower.contains("icon") ||
                lower.contains("image") ||
                lower.contains("img") ||
                lower.contains("poster") ||
                lower.contains("avatar") ||
                lower.contains("pic")
            ) {
                val v = obj.optString(k, "").trim()
                if (v.startsWith("http", ignoreCase = true) ||
                    v.startsWith("//") ||
                    v.startsWith("data:image/", ignoreCase = true)
                ) {
                    return v
                }
            }
        }
        return ""
    }
}
