package com.example.model

enum class DownloadQualityOption(
    val qualityKey: String,
    val label: String,
    val resolutionBadge: String,
    val description: String,
    val estimatedMovieSize: String,
    val estimatedEpisodeSize: String
) {
    LOW_360P(
        qualityKey = "360p",
        label = "Data Saver (360p)",
        resolutionBadge = "360p SD",
        description = "Fastest download • Uses minimal mobile data & storage",
        estimatedMovieSize = "280 MB",
        estimatedEpisodeSize = "160 MB"
    ),
    STANDARD_480P(
        qualityKey = "480p",
        label = "Standard Quality (480p)",
        resolutionBadge = "480p SD",
        description = "Balanced clarity & file size for mobile screens",
        estimatedMovieSize = "480 MB",
        estimatedEpisodeSize = "290 MB"
    ),
    HIGH_720P(
        qualityKey = "720p",
        label = "High Definition (720p HD)",
        resolutionBadge = "720p HD",
        description = "Sharp HD picture quality for offline cinema viewing",
        estimatedMovieSize = "780 MB",
        estimatedEpisodeSize = "450 MB"
    ),
    FULL_HD_1080P(
        qualityKey = "1080p",
        label = "Full HD (1080p)",
        resolutionBadge = "1080p FHD",
        description = "Maximum cinema resolution & audio fidelity",
        estimatedMovieSize = "1.3 GB",
        estimatedEpisodeSize = "720 MB"
    );

    fun resolveQualityStreamUrl(originalUrl: String): String {
        val clean = originalUrl.trim()
        if (clean.contains("b-cdn.net", ignoreCase = true)) {
            val baseDir = clean.substringBeforeLast("/")
            if (baseDir.startsWith("http")) {
                return "$baseDir/play_${qualityKey}.mp4"
            }
        }
        if (clean.contains("play_360p.mp4") || clean.contains("play_480p.mp4") ||
            clean.contains("play_720p.mp4") || clean.contains("play_1080p.mp4")
        ) {
            return clean.replace(Regex("play_\\d+p\\.mp4"), "play_${qualityKey}.mp4")
        }
        return clean
    }
}

data class CastMember(
    val id: String,
    val name: String,
    val role: String,
    val avatarUrl: String
)

/**
 * Represents a category inside the Firestore `categories` collection.
 */
data class FirestoreCategoryItem(
    val id: String,
    val name: String,
    val nameSw: String = name
)

/**
 * Represents a notification inside the Firestore `notifications` collection.
 */
data class FirestoreNotificationItem(
    val id: String,
    val title: String,
    val message: String,
    val imageUrl: String = "",
    val mediaId: String = "",
    val channelId: String = "",
    val createdAtEpochMs: Long = 0L
)

/**
 * Represents a season inside a Firestore `series` document (`seasons` array of maps).
 */
data class SeriesSeason(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val airDate: String = "",
    val overview: String = "",
    val posterPath: String = ""
)

/**
 * Represents an episode inside the Firestore `episodes` collection.
 */
data class EpisodeItem(
    val id: String,
    val seriesId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String,
    val stillPath: String,
    val streamUrl: String,
    val playbackType: String = "m3u8", // "m3u8", "mp4", "dash"
    val runtime: Int = 45,
    val airDate: String = "",
    val narrated: Boolean = false,
    val narrationLanguage: String = "",
    val downloadEnabled: Boolean = true,
    val published: Boolean = true,
    val viewsCount: Long = 0L,
    val playbackUrl: String = streamUrl,
    val downloadUrl: String = streamUrl,
    val subtitleUrl: String = "",
    val allowStreaming: Boolean = true,
    val allowDownload: Boolean = downloadEnabled
) {
    val title: String
        get() = name

    val posterUrl: String
        get() = stillPath

    val durationLabel: String
        get() = if (runtime > 0) "${runtime}m" else "45m"

    fun toPlayableChannel(seriesTitle: String = ""): LiveChannel {
        val effectiveStreamUrl = streamUrl.ifBlank { playbackUrl }.ifBlank { downloadUrl }
        val displayTitle = if (seriesTitle.isNotBlank()) {
            "$seriesTitle • S${seasonNumber}E${episodeNumber}: $name"
        } else {
            "S${seasonNumber}E${episodeNumber} • $name"
        }
        val format = when {
            playbackType.equals("mp4", ignoreCase = true) || effectiveStreamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            playbackType.equals("dash", ignoreCase = true) || effectiveStreamUrl.contains(".mpd", ignoreCase = true) -> "dash"
            else -> "hls"
        }
        return LiveChannel(
            id = "ep_$id",
            name = displayTitle,
            description = if (narrated && narrationLanguage.isNotBlank()) {
                "Narrated in $narrationLanguage • $durationLabel"
            } else {
                durationLabel
            },
            streamUrl = effectiveStreamUrl,
            streamFormat = format,
            thumbnailUrl = stillPath,
            categories = listOf("Series", if (narrated) narrationLanguage else "Drama"),
            language = if (narrated) "sw" else "en",
            encryptionType = "none",
            isLiveBroadcast = false,
            seriesId = seriesId,
            episodeId = id,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            isSwahiliNarratedMovie = false,
            isAdultContent = false
        )
    }
}

/**
 * Represents a Movie (`movies` collection) or Series (`series` collection) from Cloud Firestore.
 */
data class MediaContent(
    val id: String,
    val title: String,
    val type: String, // "movie" or "series"
    val posterUrl: String,
    val backdropUrl: String,
    val streamUrl: String,
    val streamFormat: String = "hls", // "hls", "mp4", "dash"
    val genre: String,
    val subGenres: List<String> = listOf(genre),
    val duration: String,
    val rating: String,
    val director: String = "Neli Studios",
    val screenplay: String = "Original",
    val production: String = "International",
    val synopsis: String,
    val cast: List<CastMember> = emptyList(),
    val isTrending: Boolean = false,
    val isComingSoon: Boolean = false,
    val isKids: Boolean = false,
    val releaseYear: String = "2025",
    // Cloud Firestore specific fields
    val originalTitle: String = "",
    val originalLanguage: String = "en",
    val narrated: Boolean = false,
    val narrationLanguage: String = "",
    val downloadEnabled: Boolean = true,
    val featured: Boolean = false,
    val published: Boolean = true,
    val runtimeMinutes: Int = 0,
    val tmdbId: Long = 0L,
    val viewsCount: Long = 0L,
    val voteCount: Long = 0L,
    val watchlistsCount: Long = 0L,
    val productionCountries: List<String> = emptyList(),
    val numberOfSeasons: Int = 0,
    val numberOfEpisodes: Int = 0,
    val firstAirDate: String = "",
    val lastAirDate: String = "",
    val seasons: List<SeriesSeason> = emptyList(),
    val createdAtEpochMs: Long = 0L,
    val thumbnailUrl: String = posterUrl,
    val imageUrl: String = posterUrl,
    val playbackUrl: String = streamUrl,
    val downloadUrl: String = streamUrl,
    val subtitleUrl: String = "",
    val categoryId: String = type,
    val allowStreaming: Boolean = true,
    val allowDownload: Boolean = downloadEnabled,
    val youtubeVideoId: String = "",
    val youtubeUrl: String = ""
) {
    val isSeries: Boolean =
        type.equals("series", ignoreCase = true) ||
            type.equals("tv_show", ignoreCase = true) ||
            categoryId.equals("series", ignoreCase = true)

    val isAdult: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        type.equals("adult", ignoreCase = true) ||
            com.example.data.MediaContentRepository.isAdultKeywordOrQuery(type) ||
            com.example.data.MediaContentRepository.isAdultKeywordOrQuery(categoryId) ||
            com.example.data.MediaContentRepository.isAdultKeywordOrQuery(genre) ||
            subGenres.any { com.example.data.MediaContentRepository.isAdultKeywordOrQuery(it) }
    }

    val isMovie: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        !isSeries && !isAdult
    }

    /**
     * Strictly true ONLY for Movies narrated in Swahili/Kiswahili (DJ narrated movies with 5m30s intro ads).
     * Never true for Adult, Live TV, Series, or Episodes.
     */
    val isSwahiliNarratedMovie: Boolean by lazy(LazyThreadSafetyMode.PUBLICATION) {
        isMovie &&
            !isSeries &&
            !isAdult &&
            narrated &&
            (narrationLanguage.contains("swahili", ignoreCase = true) ||
                narrationLanguage.equals("sw", ignoreCase = true) ||
                screenplay.contains("swahili", ignoreCase = true))
    }

    val shouldAutoSkipSwahiliMovieIntro: Boolean
        get() = isSwahiliNarratedMovie

    val isAdultContent: Boolean
        get() = isAdult

    /**
     * Strictly returns ONLY the first primary genre for this media item so movies are never duplicated across genres.
     */
    val primaryGenre: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        com.example.data.MediaContentRepository.extractPrimaryGenre(this)
    }

    val posterPath: String
        get() = posterUrl

    val backdropPath: String
        get() = backdropUrl

    val overview: String
        get() = synopsis

    fun toPlayableChannel(): LiveChannel {
        val effectiveStreamUrl = streamUrl.ifBlank { playbackUrl }.ifBlank { downloadUrl }
        val cleanPath = effectiveStreamUrl.substringBefore("?").lowercase()
        val detectedFormat = when {
            cleanPath.endsWith(".mp4") || cleanPath.endsWith(".ts") ||
                    streamFormat.equals("mp4", ignoreCase = true) -> "mp4"
            cleanPath.endsWith(".mpd") || streamFormat.equals("dash", ignoreCase = true) -> "dash"
            else -> "hls"
        }
        return LiveChannel(
            id = "vod_$id",
            name = title,
            description = buildString {
                append(genre)
                append(" • ")
                append(duration)
                if (narrated && narrationLanguage.isNotBlank()) {
                    append(" • ")
                    append(narrationLanguage)
                }
            },
            streamUrl = effectiveStreamUrl,
            streamFormat = detectedFormat,
            thumbnailUrl = backdropUrl.ifBlank { posterUrl },
            categories = subGenres,
            language = if (narrated) "sw" else originalLanguage,
            encryptionType = "none",
            isLiveBroadcast = false,
            seriesId = if (isSeries) id else "",
            isSwahiliNarratedMovie = isSwahiliNarratedMovie,
            isAdultContent = isAdult
        )
    }
}
