package com.example.data

import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object ChannelRepository {

    const val DEFAULT_AZAM_CDN_HOST = "https://cdnedgch2.azamtvltd.co.tz"
    const val DEFAULT_AZAM_LOAD_BALANCER_HOST = "https://cdnblncr.azamtvltd.co.tz"
    const val DEFAULT_AZAM_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwMzc2NTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOTdmYmY3NWU2ZWU2MmJkYSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.zW07ft5kqXHOkbPguYYGgrBwihIVJIjoGvhVqTN3cjjWZEp4NWJfpQUQyDYUkyuhNVKmLYSHxO5EwEegjYQ84A=="
    const val DEFAULT_AZAM_CDN_TOKEN_ENCODED =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwMzc2NTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOTdmYmY3NWU2ZWU2MmJkYSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.zW07ft5kqXHOkbPguYYGgrBwihIVJIjoGvhVqTN3cjjWZEp4NWJfpQUQyDYUkyuhNVKmLYSHxO5EwEegjYQ84A%3D%3D"
    const val FALLBACK_AZAM_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwNDEyNTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZDE5NDU4MjNlZWUyZjEzNCIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.KVTVBBod8HqPeilMn-Pc-zKhn3ecdoJcuztd4u092LvlLxWoZQCqFiQqLPXMso7loCQjejhKD4xxOOysa4NFkQ=="
    const val DEFAULT_AZAM_CDN_EXP = 0L
    const val DEFAULT_AZAM_CDN_SOURCE = "forever_free"
    const val DEFAULT_TOKEN_ENDPOINT_URL = "https://streamzone.fun/api/cdn-token"

    @Volatile
    var AZAM_CDN_HOST: String = DEFAULT_AZAM_CDN_HOST
        private set

    @Volatile
    var AZAM_CDN_TOKEN: String = DEFAULT_AZAM_CDN_TOKEN
        private set

    @Volatile
    var AZAM_CDN_EXP: Long = DEFAULT_AZAM_CDN_EXP
        private set

    @Volatile
    var AZAM_CDN_SOURCE: String = DEFAULT_AZAM_CDN_SOURCE
        private set

    @Volatile
    var AZAM_TOKEN_ENDPOINT_URL: String = DEFAULT_TOKEN_ENDPOINT_URL
        private set

    @Volatile
    var lastRefreshedEpochMs: Long = System.currentTimeMillis()
        private set

    /**
     * Refreshes the Live TV channel feed in-place without restarting the app:
     * re-applies the latest Azam CDN host/token to all stream URLs and re-sorts
     * strictly by priority (Azam TV -> Tanzania -> International).
     */
    fun refreshLiveChannels(): List<LiveChannel> {
        val current = _liveChannelsFlow.value.ifEmpty { channels }
        val refreshed = getPrioritizedAllChannels(current).map { ch ->
            ch.copy(streamUrl = normalizeDashStreamUrl(ch.streamUrl))
        }
        _liveChannelsFlow.value = refreshed
        lastRefreshedEpochMs = System.currentTimeMillis()
        return refreshed
    }

    val categories = listOf(
        "All",
        "Azam TV",
        "Sports",
        "Entertainment",
        "Movies",
        "News",
        "Music",
        "Tanzania"
    )

    val channels: List<LiveChannel> = listOf(
        // 1. AZAM SPORT 1 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "R17JUvbCEzu2eTbjnE74",
            name = "Azam Sports 1 HD",
            description = "mpira live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85"),
            country = "Tanzania",
            featured = true
        ),
        // 2. AZAM SPORT 2 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f",
            name = "Azam Sports 2 HD",
            description = "mpira live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport2/DASH/AzamSport2.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("739e7499125b31cc9948da8057b84cf9" to "1b7d44d798c351acc02f33ddfbb7682a"),
            country = "Tanzania",
            featured = true
        ),
        // 3. AZAM SPORT 3 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "1c976127-e8a4-4bd6-8e73-5da0edce369b",
            name = "Azam Sports 3 HD",
            description = "mpiraa live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport3/DASH/AzamSport3.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/2YfLQ545/1000221070.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("2f12d7b889de381a9fb5326ca3aa166d" to "51c2d733a54306fdf89acd4c9d4f6005"),
            country = "Tanzania",
            featured = true
        ),
        // 4. AZAM SPORT 4 HD (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c",
            name = "Azam Sports 4 HD",
            description = "Mpira Live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamSport4/DASH/AzamSport4.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("fbcac74e355b3b5d9b6cbc56c7597dbf" to "5f980e872b378d88b46f656a806205c3"),
            country = "Tanzania",
            featured = true
        ),
        // 5. AZAM ONE (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "c405ae74-c4c5-4842-9f26-130ce380b307",
            name = "Azam One",
            description = "Burudani na Filamu za Afrika Mashariki",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1"),
            country = "Tanzania",
            featured = true
        ),
        // 6. AZAM TWO (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            name = "Azam Two",
            description = "Tamthilia za Kiswahili na Burudani Live",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("18f515fd536b3e728e9844e77d9f0fa8" to "c96dfd9b65560f87a1a95b8aeda0432d"),
            country = "Tanzania",
            featured = true
        ),
        // 7. SINEMA ZETU (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            name = "Sinema Zetu",
            description = "Filamu za Kiswahili & Bongo Movies 24/7",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e"),
            country = "Tanzania",
            featured = true
        ),
        // 8. UTV (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "d7b415f7-d024-4c7f-a570-653367e8dc5c",
            name = "UTV",
            description = "Habari, Michezo na Burudani • Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("news", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894"),
            country = "Tanzania",
            featured = true
        ),
        // 9. ZBC2 (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            name = "ZBC2",
            description = "Zanzibar Broadcasting Corporation 2",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/ZBC2/DASH/ZBC2.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            categories = listOf("tanzania", "entertainment"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("f38b03549d023f3a883997328537bf4d" to "90f9d2d9cc26009c437905692ad70730"),
            country = "Tanzania",
            featured = true
        ),
        // 10. ZBC (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "101e9c3f-de90-47fb-a69c-342be8a0bb80",
            name = "ZBC",
            description = "Zanzibar Broadcasting Corporation",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/ZBC/DASH/ZBC.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/xtynWQsN/1000221078.png",
            categories = listOf("news", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e91fec140bc5316f919b4dc9c16287d7" to "79884fad0dbfcdc43d3e33c82a1f1cfa"),
            country = "Tanzania",
            featured = true
        ),
        // 11. KIX (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "4f36f2d1-ff7f-467b-b4f2-6f303263f28b",
            name = "KIX",
            description = "Action Movies • Azam TV",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/KIXMovies/DASH/KIXMovies.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png",
            categories = listOf("movies", "entertainment"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("a7e155b282f33335ae8d553f169f443c" to "c3fdcfd5d509f1ed8550d76a525e34e5"),
            country = "Tanzania",
            featured = true
        ),
        // 12. Crown Tv (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            name = "Crown Tv",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/CrownTv/DASH/CrownTv.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e3eb6e0656ec3c22aa308aaa3f82c565" to "a7634d6defd2c255135095c45bc442fb"),
            country = "Tanzania",
            featured = true
        ),
        // 13. Wasafi (MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            name = "Wasafi",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "$DEFAULT_AZAM_CDN_HOST/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"),
            country = "Tanzania",
            featured = true
        )
    )

    private val _liveChannelsFlow = MutableStateFlow(channels)
    val liveChannelsFlow: StateFlow<List<LiveChannel>> = _liveChannelsFlow.asStateFlow()

    private val homepageChannelIds = channels.map { it.id }

    /**
     * Curated Azam TV channels for the Homepage.
     */
    val homePageFeaturedChannels: List<LiveChannel>
        get() {
            val source = _liveChannelsFlow.value
            return homepageChannelIds.mapNotNull { targetId ->
                source.find { it.id == targetId } ?: channels.find { it.id == targetId }
            }
        }

    /**
     * All Azam TV channels placed first for the Live TV screen and slider.
     */
    val azamPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value.filter { it.isAzamPriority }.ifEmpty { channels }

    /**
     * All Tanzania Live TV channels (including Azam bouquet + Tanzania Firestore channels).
     */
    val tanzaniaPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value
            .filter { it.isTanzaniaChannel && it.enabled && it.published }
            .sortedByDescending { it.priorityTier }

    /**
     * Returns ALL enabled & published Live TV channels ordered strictly by priority:
     * 1st: Azam TV channels (`isAzamPriority`, tier 3)
     * 2nd: Tanzania channels (`isTanzaniaChannel`, tier 2)
     * 3rd: Featured & Other International channels (tiers 1 & 0)
     */
    fun getPrioritizedAllChannels(source: List<LiveChannel> = _liveChannelsFlow.value): List<LiveChannel> {
        val active = source.filter { it.enabled && it.published }.ifEmpty { channels }
        return active
            .map { ch ->
                val resolvedLogo = resolveGuaranteedChannelLogoUrl(ch)
                if (resolvedLogo != ch.thumbnailUrl) ch.copy(thumbnailUrl = resolvedLogo) else ch
            }
            .sortedByDescending { it.priorityTier }
    }

    /**
     * Groups channels into vertical chunks of 6 channels so an inline Muted Video Ad / AdMob Ad
     * is embedded after every 6 channels in the All Channels vertical view.
     */
    fun getAllChannelsChunkedEverySixForAds(
        source: List<LiveChannel> = _liveChannelsFlow.value
    ): List<List<LiveChannel>> {
        val list = if (source.isEmpty()) getPrioritizedAllChannels() else source
        return list.chunked(6)
    }

    fun getChannelsByCategory(category: String): List<LiveChannel> = filterChannels("", category)

    /**
     * Ordered homepage category sections requested before "All Channels":
     * 1. Azam TV (all Azam TV channels)
     * 2. Sports
     * 3. Entertainment
     * 4. Kids
     * 5. News
     * 6. Movies
     * ...followed by Music, Documentary, Africa, Tanzania, and any remaining categories.
     */
    val homepageOrderedCategoryNames: List<String> = listOf(
        "Azam TV",
        "Sports",
        "Entertainment",
        "Kids",
        "News",
        "Movies",
        "Music",
        "Documentary",
        "Africa",
        "Tanzania",
        "Other"
    )

    /**
     * Groups Live TV channels into ordered category shelves for the Homepage before "All Channels":
     * Starts with Azam TV (all Azam TV channels), then Sports, Entertainment, Kids, News, Movies,
     * and remaining categories until all categories are shown.
     */
    fun getChannelsGroupedByHomepageCategories(
        source: List<LiveChannel> = _liveChannelsFlow.value
    ): List<Pair<String, List<LiveChannel>>> {
        val active = getPrioritizedAllChannels(source).map { ch ->
            val resolvedLogo = resolveGuaranteedChannelLogoUrl(ch)
            if (resolvedLogo != ch.thumbnailUrl) ch.copy(thumbnailUrl = resolvedLogo) else ch
        }
        val result = mutableListOf<Pair<String, List<LiveChannel>>>()
        val seenCategoryKeys = mutableSetOf<String>()

        for (categoryName in homepageOrderedCategoryNames) {
            val matching = when (categoryName.lowercase()) {
                "azam tv" -> active.filter { it.isAzamPriority }
                "sports" -> active.filter { ch ->
                    ch.categories.any { it.equals("sport", true) || it.equals("sports", true) } ||
                        ch.name.contains("sport", ignoreCase = true)
                }
                "entertainment" -> active.filter { ch ->
                    ch.categories.any { it.equals("entertainment", true) }
                }
                "kids" -> active.filter { ch ->
                    ch.categories.any {
                        it.equals("kids", true) ||
                            it.equals("kid", true) ||
                            it.contains("cartoon", true) ||
                            it.contains("animation", true)
                    }
                }
                "news" -> active.filter { ch ->
                    ch.categories.any { it.equals("news", true) } ||
                        ch.name.contains("news", ignoreCase = true)
                }
                "movies" -> active.filter { ch ->
                    ch.categories.any {
                        it.equals("movies", true) ||
                            it.equals("movie", true) ||
                            it.contains("cinema", true)
                    } || ch.name.contains("sinema", ignoreCase = true) ||
                        ch.name.contains("movies", ignoreCase = true)
                }
                "music" -> active.filter { ch ->
                    ch.categories.any { it.equals("music", true) }
                }
                "documentary" -> active.filter { ch ->
                    ch.categories.any { it.equals("documentary", true) }
                }
                "africa" -> active.filter { ch ->
                    ch.categories.any { it.equals("africa", true) }
                }
                "tanzania" -> active.filter { it.isTanzaniaChannel }
                "other" -> active.filter { ch ->
                    ch.categories.isEmpty() || ch.categories.any { it.equals("other", true) }
                }
                else -> active.filter { ch ->
                    ch.categories.any { it.equals(categoryName, true) }
                }
            }
            if (matching.isNotEmpty()) {
                result.add(categoryName to matching)
                seenCategoryKeys.add(categoryName.lowercase())
            }
        }

        // Also include any custom Firebase channel categories not already in the standard list
        val extraCategories = LinkedHashSet<String>()
        active.forEach { ch ->
            ch.categories.forEach { rawCat ->
                val clean = rawCat.trim()
                val lower = clean.lowercase()
                if (clean.isNotEmpty() &&
                    lower != "sport" &&
                    lower !in seenCategoryKeys
                ) {
                    extraCategories.add(clean.replaceFirstChar { it.uppercase() })
                }
            }
        }
        for (extraCat in extraCategories) {
            val matching = active.filter { ch ->
                ch.categories.any { it.equals(extraCat, ignoreCase = true) }
            }
            if (matching.isNotEmpty()) {
                result.add(extraCat to matching)
            }
        }

        return result
    }

    /**
     * Guarantees every channel has a valid, non-blank logo URL in both category rows and All Channels.
     */
    fun resolveGuaranteedChannelLogoUrl(channel: LiveChannel): String {
        val raw = channel.thumbnailUrl.trim()
        if (raw.startsWith("//")) {
            return "https:$raw"
        }
        if (raw.startsWith("http://", ignoreCase = true)) {
            return "https://" + raw.removePrefix("http://").removePrefix("HTTP://")
        }
        if (raw.startsWith("https://", ignoreCase = true) || raw.startsWith("data:image/", ignoreCase = true)) {
            return raw
        }
        return resolveFallbackChannelLogoUrl(channel)
    }

    /**
     * Reliable fallback logo URL if a channel has no logo or if its remote logo URL fails to load.
     */
    fun resolveFallbackChannelLogoUrl(channel: LiveChannel): String {
        val localMatch = channels.find {
            it.id.equals(channel.id, ignoreCase = true) ||
                it.name.equals(channel.name.trim(), ignoreCase = true) ||
                channel.name.contains(it.name, ignoreCase = true) ||
                it.name.contains(channel.name.trim(), ignoreCase = true)
        }
        if (localMatch != null &&
            localMatch.thumbnailUrl.isNotBlank() &&
            !localMatch.thumbnailUrl.equals(channel.thumbnailUrl.trim(), ignoreCase = true)
        ) {
            return localMatch.thumbnailUrl
        }
        val lowerName = channel.name.lowercase()
        val lowerCats = channel.categories.joinToString(" ").lowercase()
        return when {
            lowerName.contains("azam sport 1") || lowerName.contains("azam sports 1") ->
                "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png"
            lowerName.contains("azam sport 2") || lowerName.contains("azam sports 2") ->
                "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png"
            lowerName.contains("azam sport 3") || lowerName.contains("azam sports 3") ->
                "https://i.ibb.co/2YfLQ545/1000221070.png"
            lowerName.contains("azam sport") || lowerCats.contains("sport") ->
                "https://i.ibb.co/SwtFQNsh/1000221063.jpg"
            lowerName.contains("azam one") || lowerName.contains("azam xtra") ->
                "https://i.ibb.co/8gtr1n42/1000221072.jpg"
            lowerName.contains("azam two") || lowerName.contains("clouds") ->
                "https://i.ibb.co/Z6sdp2tg/1000221074.jpg"
            lowerName.contains("sinema") || lowerName.contains("azam movies") || lowerCats.contains("movie") ->
                "https://i.ibb.co/twBGTs4s/1000221073.jpg"
            lowerName.contains("utv") || lowerName.contains("itv") || lowerCats.contains("news") ->
                "https://i.ibb.co/N2nCDLwD/1000221071.png"
            lowerName.contains("zbc") ->
                "https://i.ibb.co/xtynWQsN/1000221078.png"
            lowerName.contains("wasafi") || lowerCats.contains("music") ->
                "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png"
            lowerName.contains("crown") ->
                "https://i.ibb.co/GfWDtdQT/1000221075.png"
            lowerName.contains("kix") ->
                "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png"
            lowerCats.contains("kid") || lowerName.contains("cartoon") || lowerName.contains("baby") ->
                "https://i.ibb.co/8gV4tq4g/1000221079.png"
            else -> "https://i.ibb.co/8gtr1n42/1000221072.jpg"
        }
    }

    /**
     * Automatically extracts the `exp` (expiration epoch seconds) embedded inside an Azam TV JWT token
     * (`eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwNDEyNTgyIi...`), so even if `exp` is `null`
     * or omitted in Firestore (`config/azam_token`), the exact expiration is decoded automatically.
     */
    fun extractJwtExpSeconds(jwtToken: String): Long? {
        return try {
            val parts = jwtToken.trim().split(".")
            if (parts.size < 2) return null
            val payloadPart = parts[1]
            val padded = payloadPart
                .replace('-', '+')
                .replace('_', '/')
                .let { s ->
                    val mod = s.length % 4
                    if (mod == 0) s else s + "=".repeat(4 - mod)
                }
            val decodedBytes = java.util.Base64.getDecoder().decode(padded)
            val payloadJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
            val expStr = payloadJson.optString("exp", "").trim()
            expStr.toLongOrNull() ?: payloadJson.optLong("exp", 0L).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `exp` flexibly whether it is `null`, a Unix epoch number, a numeric string,
     * a Firestore ISO-8601 `timestamp` (date/time picker), or missing (auto-extracted from JWT).
     * Always prefers the embedded JWT `exp` when it is newer so a stale Firestore timestamp never downgrades a valid JWT.
     */
    fun parseFlexibleExpiration(rawExp: Any?, jwtToken: String, fallbackExp: Long = AZAM_CDN_EXP): Long {
        val jwtExp = extractJwtExpSeconds(jwtToken)
        if (rawExp == null || rawExp == JSONObject.NULL) {
            return jwtExp ?: fallbackExp
        }
        var parsedExp: Long? = null
        when (rawExp) {
            is Number -> {
                val num = rawExp.toLong()
                if (num > 0L) parsedExp = if (num > 10_000_000_000L) num / 1000L else num
            }
            is String -> {
                val clean = rawExp.trim()
                if (clean.isEmpty() || clean.equals("null", ignoreCase = true)) {
                    return jwtExp ?: fallbackExp
                }
                clean.toLongOrNull()?.let { num ->
                    if (num > 0L) parsedExp = if (num > 10_000_000_000L) num / 1000L else num
                }
                if (parsedExp == null) {
                    // Try parsing ISO-8601 date/time from Firestore timestamp picker (e.g. "2026-10-25T18:00:00Z")
                    try {
                        val instant = java.time.Instant.parse(clean)
                        if (instant.epochSecond > 0L) parsedExp = instant.epochSecond
                    } catch (_: Exception) {
                    }
                }
            }
        }
        val candidate = parsedExp ?: return (jwtExp ?: fallbackExp)
        return if (jwtExp != null && jwtExp > candidate) jwtExp else candidate
    }

    /**
     * Updates the active CDN authorization token and edge host from a JSON payload
     * without enforcing any expiration date restriction (token is forever free until manually updated).
     */
    fun updateCdnAuthorizationToken(jsonStr: String): Boolean {
        return try {
            val obj = JSONObject(jsonStr)
            val newToken = obj.optString("token", obj.optString("cdntoken", "")).trim()
            val newHost = obj.optString("cdnHost", "").trim().removeSuffix("/")
            val newSource = obj.optString("source", AZAM_CDN_SOURCE).ifBlank { DEFAULT_AZAM_CDN_SOURCE }
            val newEndpoint = obj.optString("tokenEndpointUrl", obj.optString("apiUrl", "")).trim()
            if (newEndpoint.startsWith("http", ignoreCase = true)) {
                AZAM_TOKEN_ENDPOINT_URL = newEndpoint
            }
            if (newToken.isNotEmpty()) {
                AZAM_CDN_TOKEN = newToken
                if (newHost.startsWith("http", ignoreCase = true)) {
                    AZAM_CDN_HOST = newHost
                }
                // Token is forever-free with no expiration date until manually replaced
                AZAM_CDN_EXP = 0L
                AZAM_CDN_SOURCE = newSource
                _liveChannelsFlow.value = channels.map { ch ->
                    ch.copy(
                        streamUrl = normalizeDashStreamUrl(ch.streamUrl),
                        thumbnailUrl = resolveGuaranteedChannelLogoUrl(ch)
                    )
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Fetches a fresh Azam CDN token directly from [AZAM_TOKEN_ENDPOINT_URL] without using Firebase/Firestore.
     */
    fun refreshCdnTokenFromEndpointSync(): Boolean {
        return try {
            val conn = (java.net.URL(AZAM_TOKEN_ENDPOINT_URL).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 4500
                readTimeout = 4500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                conn.disconnect()
                updateCdnAuthorizationToken(body)
            } else {
                conn.disconnect()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Keeps only the built-in Azam TV channels inside the app (never overwritten by Firestore/Firebase).
     */
    fun mergeFirebaseChannelsWithAzamPriority(remoteChannels: List<LiveChannel>) {
        _liveChannelsFlow.value = channels.map { ch ->
            ch.copy(
                streamUrl = normalizeDashStreamUrl(ch.streamUrl),
                thumbnailUrl = resolveGuaranteedChannelLogoUrl(ch)
            )
        }
    }

    /**
     * Normalizes Azam TV & CDN stream URLs (`.mpd` manifests and `.mp4` / `.m4s` CDN streams/segments):
     * - Rewrites `https://cdnblncr.azamtvltd.co.tz` to [AZAM_CDN_HOST] (`https://cdnedgch2.azamtvltd.co.tz`)
     * - Attaches `?cdntoken=[AZAM_CDN_TOKEN]` to Azam TV `.mpd` and `.mp4` CDN links
     * - Leaves external `.m3u8` HLS links untouched unless they use `azamtvltd.co.tz` or `cdntoken=`.
     */
    fun normalizeDashStreamUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed

        val rewrittenHostUrl = trimmed
            .replace("http://cdnblncr.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("https://cdnblncr.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("http://cdnedgch2.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)
            .replace("https://cdnedgch2.azamtvltd.co.tz", AZAM_CDN_HOST, ignoreCase = true)

        val basePath = rewrittenHostUrl.substringBefore("?")
        val existingQuery = rewrittenHostUrl.substringAfter("?", "")

        val isAzamCdn = basePath.contains("azamtvltd.co.tz", ignoreCase = true)
        val hasCdnTokenParam = existingQuery.contains("cdntoken=", ignoreCase = true)
        val isCdnMp4OrMpd = isAzamCdn ||
                hasCdnTokenParam ||
                (basePath.endsWith(".mpd", ignoreCase = true) && isAzamCdn) ||
                (basePath.endsWith(".mp4", ignoreCase = true) && isAzamCdn)

        if (!isCdnMp4OrMpd) {
            return rewrittenHostUrl
        }

        val otherParams = existingQuery
            .split("&")
            .filter { it.isNotBlank() && !it.startsWith("cdntoken=", ignoreCase = true) }

        val finalQuery = (otherParams + "cdntoken=$AZAM_CDN_TOKEN").joinToString("&")
        return "$basePath?$finalQuery"
    }

    fun getChannelById(id: String): LiveChannel? =
        _liveChannelsFlow.value.find { it.id == id } ?: channels.find { it.id == id }

    fun filterChannels(query: String, category: String): List<LiveChannel> {
        val trimmed = query.trim()
        val sourceList = _liveChannelsFlow.value
        val filtered = sourceList.filter { channel ->
            val matchesCategory = when (category.lowercase()) {
                "all" -> true
                "azam tv", "azam" -> channel.isAzamPriority
                "sports" -> channel.categories.any { it.equals("sport", ignoreCase = true) || it.equals("sports", ignoreCase = true) }
                "entertainment" -> channel.categories.any { it.equals("entertainment", ignoreCase = true) }
                "movies" -> channel.categories.any { it.equals("movies", ignoreCase = true) || it.equals("movie", ignoreCase = true) }
                "kids" -> channel.categories.any { it.equals("kids", ignoreCase = true) || it.equals("kid", ignoreCase = true) }
                "news" -> channel.categories.any { it.equals("news", ignoreCase = true) }
                "music" -> channel.categories.any { it.equals("music", ignoreCase = true) }
                "africa" -> channel.categories.any { it.equals("africa", ignoreCase = true) }
                "tanzania" -> channel.isTanzaniaChannel
                "documentary" -> channel.categories.any { it.equals("documentary", ignoreCase = true) }
                "other" -> channel.categories.isEmpty() || channel.categories.any { it.equals("other", ignoreCase = true) }
                else -> channel.categories.any { it.equals(category, ignoreCase = true) }
            }

            val matchesName = trimmed.isEmpty() ||
                    channel.name.contains(trimmed, ignoreCase = true) ||
                    channel.country.contains(trimmed, ignoreCase = true)

            matchesCategory && matchesName && channel.enabled && channel.published
        }

        // Prioritize Azam TV channels first (3), then Tanzania channels (2), then featured (1), then other channels (0)
        return filtered
            .map { ch ->
                val resolvedLogo = resolveGuaranteedChannelLogoUrl(ch)
                if (resolvedLogo != ch.thumbnailUrl) ch.copy(thumbnailUrl = resolvedLogo) else ch
            }
            .sortedByDescending { it.priorityTier }
    }
}
