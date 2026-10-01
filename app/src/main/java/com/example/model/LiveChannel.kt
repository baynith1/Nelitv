package com.example.model

/**
 * Represents a linear EPG / schedule event item (e.g., DStv / Showmax schedule payload).
 */
data class ChannelScheduleEvent(
    val id: String,
    val genRef: String = "",
    val correlationId: String = "",
    val correlationGroupId: String = "",
    val mainTitle: String,
    val episodeTitle: String = "",
    val synopsis: String = "",
    val seasonNumber: String = "0",
    val episodeNumber: String = "0",
    val live: Boolean = false,
    val startDateTime: String = "",
    val endDateTime: String = "",
    val duration: String = "",
    val maturityClassification: String = "",
    val rating: String = "",
    val ratingSummary: String = "",
    val year: Int = 0,
    val genres: List<String> = emptyList(),
    val channelTag: String = "",
    val blockStream: Boolean = false,
    val scheduleId: String = "",
    val product: String = "",
    val scheduling: String = "",
    val packages: String = "",
    val pulseHost: String = "",
    val timeShiftDashUrl: String = "",
    val timeShiftHlsUrl: String = "",
    val timeShiftMssUrl: String = "",
    val streamable: Boolean = true,
    val recordable: Boolean = false,
    val allowPastEventRewatch: Boolean = false,
    val previousEventId: String = "",
    val nextEventId: String = "",
    val restricted: Boolean = false,
    val ageRestriction: Int = 0
)

/**
 * Represents a Live TV channel in Neli TV.
 *
 * Supports both built-in priority channels and Cloud Firestore `tvChannels` documents:
 * - Tier 3 (Highest): Initial primary channels (`isAzamPriority == true`)
 * - Tier 2 (Second): Channels from Tanzania (`isTanzaniaChannel == true`)
 * - Tier 1 (Third): Featured channels (`featured == true`)
 * - Tier 0 (Lowest): Other Live TV channels
 */
data class LiveChannel(
    val id: String,
    val name: String,
    val description: String,
    val streamUrl: String,
    val streamFormat: String, // "dash", "hls", or "mp4"
    val thumbnailUrl: String,
    val categories: List<String> = emptyList(),
    val language: String = "en",
    val encryptionType: String = "none", // "clearkey" or "none"
    val clearKeys: Map<String, String> = emptyMap(),
    val clearKeyId: String? = clearKeys.keys.firstOrNull(),
    val clearKey: String? = clearKeys.values.firstOrNull(),
    val isLiveBroadcast: Boolean = true,
    val country: String = "",
    val featured: Boolean = false,
    val enabled: Boolean = true,
    val published: Boolean = true,
    val seriesId: String = "",
    val episodeId: String = "",
    val seasonNumber: Int = 0,
    val episodeNumber: Int = 0,
    val isSwahiliNarratedMovie: Boolean = false,
    val isAdultContent: Boolean = false,
    val backupStreamUrl: String = "",
    val channelTag: String = "",
    val rating: String = "",
    val scheduleEvents: List<ChannelScheduleEvent> = emptyList()
) {
    val category: String
        get() = categories.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Live TV"

    private val cleanUrlPath: String
        get() = streamUrl.substringBefore("?").lowercase()

    private val isAzamDashSegmentPath: Boolean
        get() = cleanUrlPath.contains("/live/eds/") && cleanUrlPath.contains("/dash/")

    val isDash: Boolean
        get() = isAzamDashSegmentPath || (
                !cleanUrlPath.endsWith(".m3u8") &&
                !cleanUrlPath.endsWith(".mp4") &&
                !cleanUrlPath.endsWith(".ts") &&
                !streamUrl.startsWith("file:", ignoreCase = true) &&
                (cleanUrlPath.endsWith(".mpd") ||
                        cleanUrlPath.contains(".mpd/") ||
                        cleanUrlPath.contains("/dash/") ||
                        isClearKey ||
                        streamFormat.equals("dash", ignoreCase = true))
                )

    val isHls: Boolean
        get() = cleanUrlPath.endsWith(".m3u8") ||
                cleanUrlPath.contains(".m3u8/") ||
                (!isDash &&
                        !cleanUrlPath.endsWith(".mp4") &&
                        !cleanUrlPath.endsWith(".ts") &&
                        !streamUrl.startsWith("file:", ignoreCase = true) &&
                        (streamFormat.equals("hls", ignoreCase = true) ||
                                streamFormat.equals("m3u8", ignoreCase = true)))

    val isMp4: Boolean
        get() = !isAzamDashSegmentPath && (
                cleanUrlPath.endsWith(".mp4") ||
                cleanUrlPath.endsWith(".ts") ||
                cleanUrlPath.endsWith(".mkv") ||
                cleanUrlPath.endsWith(".webm") ||
                streamUrl.startsWith("file:", ignoreCase = true) ||
                (!isDash && !isHls && streamFormat.equals("mp4", ignoreCase = true))
                )

    /**
     * Strictly true ONLY for Movies narrated in Swahili/Kiswahili.
     * Never true for Live TV, Adult content, Series, or Episodes.
     */
    val shouldAutoSkipSwahiliMovieIntro: Boolean
        get() = isSwahiliNarratedMovie &&
                !isLiveBroadcast &&
                !isAdultContent &&
                seriesId.isBlank() &&
                episodeId.isBlank() &&
                !id.startsWith("ep_", ignoreCase = true) &&
                !id.startsWith("ser_", ignoreCase = true)

    val isClearKey: Boolean
        get() = encryptionType.equals("clearkey", ignoreCase = true) && clearKeys.isNotEmpty()

    val isAzamPriority: Boolean
        get() = id.startsWith("azam_", ignoreCase = true) ||
                streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
                name.contains("Azam", ignoreCase = true) ||
                name.contains("Sinema Zetu", ignoreCase = true) ||
                name.contains("UTV", ignoreCase = true) ||
                name.contains("ZBC", ignoreCase = true) ||
                name.contains("Crown", ignoreCase = true) ||
                name.contains("Wasafi", ignoreCase = true) ||
                name.contains("Clouds", ignoreCase = true) ||
                name.contains("ITV", ignoreCase = true) ||
                name.contains("KIX", ignoreCase = true)

    val isTanzaniaChannel: Boolean
        get() = isAzamPriority ||
                country.equals("Tanzania", ignoreCase = true) ||
                categories.any { it.equals("tanzania", ignoreCase = true) } ||
                language.equals("sw", ignoreCase = true)

    /**
     * Priority score used to sort Live TV channels:
     * 3 = Primary initial channels (Azam TV bouquet)
     * 2 = Other Tanzania Live TV channels (`country == "Tanzania"`)
     * 1 = Featured international channels
     * 0 = Other Live TV channels (lowest priority)
     */
    val priorityTier: Int
        get() = when {
            isAzamPriority -> 3
            isTanzaniaChannel -> 2
            featured -> 1
            else -> 0
        }
}
