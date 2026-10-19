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
    val scheduleEvents: List<ChannelScheduleEvent> = emptyList(),
    val isLockedDefault: Boolean = false
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
        get() = categories.any { it.equals("azam tv", ignoreCase = true) || it.equals("azam", ignoreCase = true) } ||
                id.startsWith("azam_", ignoreCase = true) ||
                id == "baynith_sports_hd" ||
                id == "R17JUvbCEzu2eTbjnE74" ||
                id == "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f" ||
                id == "1c976127-e8a4-4bd6-8e73-5da0edce369b" ||
                id == "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c" ||
                id == "c405ae74-c4c5-4842-9f26-130ce380b307" ||
                id == "008ffe6e-a30f-4ed1-9ddb-4033dde18576" ||
                id == "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e" ||
                id == "tom_and_jerry_channel" ||
                id == "pop_kids_channel" ||
                streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
                name.contains("Baynith", ignoreCase = true) ||
                name.contains("Azam", ignoreCase = true) ||
                name.contains("Sinema Zetu", ignoreCase = true) ||
                name.contains("UTV", ignoreCase = true) ||
                name.contains("ZBC", ignoreCase = true) ||
                name.contains("Crown", ignoreCase = true) ||
                name.contains("Wasafi", ignoreCase = true) ||
                name.contains("KIX", ignoreCase = true) ||
                name.contains("Tom and Jerry", ignoreCase = true) ||
                name.contains("Pop Kids", ignoreCase = true)

    /**
     * True for all Azam TV channels that support the in-player Language Switcher
     * (Kiswahili primary vs English).
     */
    val isAzamTvChannel: Boolean
        get() = isAzamPriority

    /**
     * True when the current channel / program is strictly in Kiswahili.
     */
    val isKiswahiliOnlyProgram: Boolean
        get() = language.equals("sw", ignoreCase = true) ||
                language.equals("swa", ignoreCase = true) ||
                isSwahiliNarratedMovie ||
                name.contains("Sinema Zetu", ignoreCase = true) ||
                name.contains("Azam Two", ignoreCase = true) ||
                name.contains("UTV", ignoreCase = true) ||
                name.contains("ZBC", ignoreCase = true) ||
                name.contains("Crown", ignoreCase = true) ||
                name.contains("Wasafi", ignoreCase = true) ||
                description.contains("Kiswahili", ignoreCase = true) ||
                description.contains("Bongo", ignoreCase = true) ||
                description.contains("Tamthilia", ignoreCase = true)

    val isTanzaniaChannel: Boolean
        get() = isAzamPriority ||
                country.equals("Tanzania", ignoreCase = true) ||
                categories.any { it.equals("tanzania", ignoreCase = true) } ||
                language.equals("sw", ignoreCase = true)

    /**
     * Priority score used to sort Live TV channels strictly according to user brief:
     * 1. Baynith Sports Hd (Free)
     * 2. Sinema Zetu (Free)
     * 3. Azam One (Free)
     * 4. Azam Two
     * 5. Azam Sports 1 HD
     * 6. Azam Sports 2 HD
     * 7. Azam Sports 3 HD
     * 8. Azam Sports 4 HD (Free)
     * 9. Azam Sports 5 HD (Free)
     * Followed by all remaining Sports channels (Bein Sport, B Sport, Sky sports, ziggo, sk 5/3/6, sport tv 1, sk moments, etc.)
     * Followed by other Azam TV / entertainment channels
     * Followed by Movies, Tanzania, and International channels.
     */
    val priorityTier: Int
        get() = when {
            id == "baynith_sports_hd" || name.contains("Baynith Sports", ignoreCase = true) -> 1000
            id == "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e" || name.equals("Sinema Zetu", ignoreCase = true) -> 950
            id == "c405ae74-c4c5-4842-9f26-130ce380b307" || name.equals("Azam One", ignoreCase = true) -> 900
            id == "008ffe6e-a30f-4ed1-9ddb-4033dde18576" || name.equals("Azam Two", ignoreCase = true) -> 850
            id == "R17JUvbCEzu2eTbjnE74" || name.contains("Azam Sport 1", ignoreCase = true) || name.contains("Azam Sports 1", ignoreCase = true) -> 800
            id == "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f" || name.contains("Azam Sport 2", ignoreCase = true) || name.contains("Azam Sports 2", ignoreCase = true) -> 750
            id == "1c976127-e8a4-4bd6-8e73-5da0edce369b" || name.contains("Azam Sport 3", ignoreCase = true) || name.contains("Azam Sports 3", ignoreCase = true) -> 700
            id == "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c" || name.contains("Azam Sport 4", ignoreCase = true) || name.contains("Azam Sports 4", ignoreCase = true) -> 650
            id == "azam_sport_5_hd" || name.contains("Azam Sport 5", ignoreCase = true) || name.contains("Azam Sports 5", ignoreCase = true) -> 600
            id in listOf("bein_sport_live", "b_sport_live", "b_esports_live", "sky_sports_live", "ziggo_sport_4_live", "sk_5_hd_live", "sk_3_hd_live", "sk_6_hd_live") -> 550
            categories.any { it.equals("sports", ignoreCase = true) || it.equals("sport", ignoreCase = true) || it.equals("europe", ignoreCase = true) } -> 500
            isAzamPriority -> 400
            categories.any { it.equals("movies", ignoreCase = true) || it.equals("movie", ignoreCase = true) } -> 300
            isTanzaniaChannel -> 200
            featured -> 100
            else -> 0
        }
}
