package com.example.model

data class CastMember(
    val id: String,
    val name: String,
    val role: String,
    val avatarUrl: String
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
    val viewsCount: Long = 0L
) {
    val durationLabel: String
        get() = if (runtime > 0) "${runtime}m" else "45m"

    fun toPlayableChannel(seriesTitle: String = ""): LiveChannel {
        val displayTitle = if (seriesTitle.isNotBlank()) {
            "$seriesTitle • S${seasonNumber}E${episodeNumber}: $name"
        } else {
            "S${seasonNumber}E${episodeNumber} • $name"
        }
        val format = when {
            playbackType.equals("mp4", ignoreCase = true) || streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            playbackType.equals("dash", ignoreCase = true) || streamUrl.contains(".mpd", ignoreCase = true) -> "dash"
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
            streamUrl = streamUrl,
            streamFormat = format,
            thumbnailUrl = stillPath,
            categories = listOf("Series", if (narrated) narrationLanguage else "Drama"),
            language = if (narrated) "sw" else "en",
            encryptionType = "none",
            isLiveBroadcast = false
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
    val seasons: List<SeriesSeason> = emptyList()
) {
    val isSeries: Boolean
        get() = type.equals("series", ignoreCase = true) || type.equals("tv_show", ignoreCase = true)

    val isMovie: Boolean
        get() = !isSeries

    val posterPath: String
        get() = posterUrl

    val backdropPath: String
        get() = backdropUrl

    val overview: String
        get() = synopsis

    fun toPlayableChannel(): LiveChannel {
        val detectedFormat = when {
            streamFormat.equals("mp4", ignoreCase = true) ||
                    streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true) -> "mp4"
            streamFormat.equals("dash", ignoreCase = true) ||
                    streamUrl.contains(".mpd", ignoreCase = true) -> "dash"
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
            streamUrl = streamUrl,
            streamFormat = detectedFormat,
            thumbnailUrl = backdropUrl.ifBlank { posterUrl },
            categories = subGenres,
            language = if (narrated) "sw" else originalLanguage,
            encryptionType = "none",
            isLiveBroadcast = false
        )
    }
}
