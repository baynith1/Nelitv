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
    val isLockedDefault: Boolean = false,
    val embeddedHtml: String = ""
) {
    val isEmbedded: Boolean
        get() = embeddedHtml.isNotBlank() || streamFormat.equals("embedded", ignoreCase = true)

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
     * 1. Azam One (Free)
     * 2. Azam Two
     * 3. Sinema Zetu (Free)
     * 4. Tom and Jerry (Free • Juu Juu)
     * 5. Azam Sports 3 HD (Free)
     * 6. Azam Sports 4 HD (Free)
     * 7. UTV (Free)
     * 8. Azam Sports 1 HD
     * 9. Azam Sports 2 HD
     * 10. All remaining Azam TV channels (Highest priority over non-Azam channels)
     * Followed by other Free channels: ZBC, Movie Korea, Now Global, Trace Latina, Movie Sphere, Pop Kids, Firebase
     * Followed by all remaining Sports channels & other bouquets
     */
    val priorityTier: Int
        get() = when {
            // Excluded channels (User prompt 1, 6, 9)
            id == "baynith_sports_hd" ||
                (id != "baynith_sport_1" && id != "baynith_sport_2" && name.equals("baynith sports hd", ignoreCase = true)) ||
                id == "azam_sport_5_hd" || name.contains("azam sport 5", ignoreCase = true) || name.contains("azm sport 5", ignoreCase = true) ||
                id.contains("azam_xtra") || id.contains("azam_movies") || id.contains("azam_clouds") || id.contains("azam_itv") ||
                name.contains("itv tanzania", ignoreCase = true) || name.contains("azam xtra", ignoreCase = true) ||
                name.contains("clouds tv", ignoreCase = true) || name.contains("azam movie", ignoreCase = true) ||
                id == "80212719-1b5c-45b3-9e52-b59e533705f1" || name.contains("Cartoon Network", ignoreCase = true) ||
                id.startsWith("fb_") || id.startsWith("firebase_") || categories.contains("firebase") -> -1

            // 1. Azam 1 followed by Azam 2 and Sinema Zetu
            id == "c405ae74-c4c5-4842-9f26-130ce380b307" || name.equals("Azam One", ignoreCase = true) || name.equals("Azam 1", ignoreCase = true) -> 3000
            id == "008ffe6e-a30f-4ed1-9ddb-4033dde18576" || name.equals("Azam Two", ignoreCase = true) || name.equals("Azam 2", ignoreCase = true) -> 2950
            id == "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e" || name.equals("Sinema Zetu", ignoreCase = true) -> 2900

            // Down after Azam One, Azam Two and Sinema Zetu: Baynith Sport 1 & Baynith Sport 2
            id == "baynith_sport_1" || name.equals("Baynith Sport 1", ignoreCase = true) || name.equals("Baynith Sports 1", ignoreCase = true) -> 2850
            id == "baynith_sport_2" || name.equals("Baynith Sport 2", ignoreCase = true) || name.equals("Baynith Sports 2", ignoreCase = true) -> 2800

            // 2. Azam sports channel From Azam sport 1 to 4
            id == "R17JUvbCEzu2eTbjnE74" || name.contains("Azam Sport 1", ignoreCase = true) || name.contains("Azam Sports 1", ignoreCase = true) -> 2750
            id == "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f" || name.contains("Azam Sport 2", ignoreCase = true) || name.contains("Azam Sports 2", ignoreCase = true) -> 2700
            id == "1c976127-e8a4-4bd6-8e73-5da0edce369b" || name.contains("Azam Sport 3", ignoreCase = true) || name.contains("Azam Sports 3", ignoreCase = true) -> 2650
            id == "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c" || name.contains("Azam Sport 4", ignoreCase = true) || name.contains("Azam Sports 4", ignoreCase = true) -> 2600

            // 3. Kids channels only 3: Tom and Jerry, TinyPop kids, and Pop network (POP Animation)
            id == "tom_and_jerry_channel" || name.contains("Tom and Jerry", ignoreCase = true) -> 2550
            id == "tinypop_kids_channel" || name.contains("TinyPop", ignoreCase = true) -> 2500
            id == "tv_1788953482934_twaqe" || id == "pop_kids_channel" || name.contains("POP Animation", ignoreCase = true) || name.contains("Pop Kids", ignoreCase = true) || name.contains("Pop network", ignoreCase = true) -> 2450

            // 4. Followed By UTV, kix, mbc 2, Zbc and zbc 2
            id == "d7b415f7-d024-4c7f-a570-653367e8dc5c" || name.equals("UTV", ignoreCase = true) -> 2350
            id == "4f36f2d1-ff7f-467b-b4f2-6f303263f28b" || name.equals("KIX", ignoreCase = true) || name.contains("Kix", ignoreCase = true) -> 2300
            id == "mbc_2_channel" || name.equals("MBC 2", ignoreCase = true) || name.contains("MBC 2", ignoreCase = true) -> 2250
            id == "101e9c3f-de90-47fb-a69c-342be8a0bb80" || name.equals("ZBC", ignoreCase = true) || (name.contains("ZBC", ignoreCase = true) && !name.contains("2")) -> 2200
            id == "b502217f-a9d0-4aef-99e6-8a784adedc65" || name.equals("ZBC2", ignoreCase = true) || name.equals("ZBC 2", ignoreCase = true) -> 2150

            // Under ZBC 2: TBC 1, TBC 2 and Tanzania Safari Channel
            id == "tbc_1_live" || name.contains("TBC 1", ignoreCase = true) -> 2100
            id == "tbc_2_live" || name.contains("TBC 2", ignoreCase = true) -> 2050
            id == "tanzania_safari_channel_live" || name.contains("Tanzania Safari", ignoreCase = true) || name.contains("Safari Channel", ignoreCase = true) -> 2000

            // 5. Followed by Wasafi and clown tv with trace Latina and now global
            id == "f989cbb6-46a9-4b68-b765-b1a37c0fa31e" || name.contains("Wasafi", ignoreCase = true) -> 1950
            id == "dc8114f0-ec7b-40fa-8640-5e36a6cfd767" || name.contains("Crown Tv", ignoreCase = true) || name.contains("Clown", ignoreCase = true) -> 1900
            id == "trace_latina_channel" || name.contains("Trace Latina", ignoreCase = true) -> 1850
            id == "now_global_channel" || name.contains("Now Global", ignoreCase = true) -> 1800

            // 6. Followed by Movie box, movie sphere, zee cinema, comedy, movie korea, movie room and sony movie one
            id == "movie_box_channel" || name.contains("Movie Box", ignoreCase = true) -> 1750
            id == "movie_sphere_channel" || name.contains("Movie Sphere", ignoreCase = true) -> 1700
            id == "zee_cinema_channel" || name.contains("Zee Cinema", ignoreCase = true) -> 1650
            id == "comedy_channel" || name.contains("Comedy", ignoreCase = true) -> 1600
            id == "movie_korea_channel" || name.contains("Movie Korea", ignoreCase = true) -> 1550
            id == "movie_room_channel" || name.contains("Movie Room", ignoreCase = true) -> 1500
            id == "sony_movie_action_channel" || name.contains("Sony Movie", ignoreCase = true) -> 1450

            // 7. WWE, TNT sports 1 and 2 HD
            id == "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2" || name.contains("WWE", ignoreCase = true) -> 1400
            id == "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c" || name.contains("TNT SPORTS 1", ignoreCase = true) -> 1350
            id == "ba2b232c-a79d-4130-b5d8-4883fbce7a1d" || name.contains("TNT SPORTS 2", ignoreCase = true) -> 1300

            // Remaining Azam TV channels
            isAzamPriority || categories.any { it.equals("azam tv", ignoreCase = true) || it.equals("azam", ignoreCase = true) } ||
                streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) -> 900

            // Remove all channels added apart from this of azam tv and user-requested channels
            else -> -1
        }
}
