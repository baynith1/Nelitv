package com.example.data

import com.example.model.ChannelScheduleEvent
import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

typealias TokenManager = com.example.player.TokenManager

object ChannelRepository {

    const val DEFAULT_AZAM_CDN_HOST = "https://cdnedgch2.azamtvltd.co.tz"
    const val DEFAULT_AZAM_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MzAyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiYWQ2NTc1OGZlMjc0NWRhZiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.xIO2NbWKCzsUvnaovYl2ob-epo1u3kXYWG3OyJ9Y2oTwKoBGm94LcRWYZgRoQpwFlunKFHVP1xAi7D7j83Mveg=="
    const val AZAM_SPORT_1_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MjkxIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiNGVhN2EwM2VmN2EwMzQwNSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.2Qx1WInw0wNDddGQfHfh8kA-fAbrvjRYWxGBvt7ZBXIv2N5XwlHSSJAR_mTeeflTZKmr3b5IxZxvdQf6_JcNcQ=="
    const val AZAM_SPORT_2_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MjkzIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZDVkNzgyZmM4MDhmNjhhYiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.ySUpwzClcOiz0j0aV6T43cuw2Rc1vRUKAkHc7DofPiQNL-n3kKtLxKjBjPnefshiCeQ3aMyAUtzDiQ0gO7Ru3g=="
    const val AZAM_SPORT_3_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0Mjk2Iiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZWE3ZDAyYzNlN2UyOWUzMSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.Jxy_beEHRKb4_7EzBLl-JKWYdukRW7glsVmRrowjF_gf7ytaKkHLIxlF_Ni5XA6sptKMvWhr4Qv8BT6iGj3fcg=="
    const val AZAM_SPORT_4_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0Mjk3Iiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZjgyZmFlMzg1MWJmMGUwYiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.1es6sojSGK2bFlDIO6ti-AQf_YfnYA_1go9dHk_uUwFHMVpVJq8JYUxcckKwA6hBOfNv4CP9fmnZc4RBIKFMiQ=="
    const val AZAM_SPORT_5_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODMxMTI0Iiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZDg5NmIyMjljMzhiYTc4ZiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.EdIV5dQmapQhFTEtHzbapvQGP0WzUXXnKc3PlPWWWk788OS2N8Mu4fc45MDiaPo_zRACmfRzZwgk-IH0BRA74Q=="
    const val AZAM_ONE_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0Mjk4Iiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOWRiYjRjYWM3ZGE4MWM4ZCIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.NFjc0OiEJehylTD_Ili4gC-9EvzGsb7LmWJxtFgvEOsTIVIipARWhtoAm1J3U4xtogCiXYU3uZY1fD1mhnX-1Q=="
    const val AZAM_TWO_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MzAwIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiZWYwM2JhZjI3OWE4NTI1NiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.b-YVd1deg7AEi6pODOndBm5IxXHpBoXK_e1LnM9cbaled0nlItj3_Dxz6ktE2YXboPnoIuWrdr0f2AMa-46esw=="
    const val SINEMA_ZETU_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MzAyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiYWQ2NTc1OGZlMjc0NWRhZiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.xIO2NbWKCzsUvnaovYl2ob-epo1u3kXYWG3OyJ9Y2oTwKoBGm94LcRWYZgRoQpwFlunKFHVP1xAi7D7j83Mveg=="
    const val UTV_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwODc0MzA2Iiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiMjdiNTg2MTk3ZDVlYzM1NiIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODUxNDgzIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.IXI1wtDp9Xme9wwND-sPnYPurlKijYVOWBUxl6jD8nsd4EUTRbHBuui8BNGxMYP2DmOnCv3VMA54F2MwMt3Iiw=="
    const val AZAM_SECONDARY_CDN_TOKEN =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwMzc2NTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOTdmYmY3NWU2ZWU2MmJkYSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.zW07ft5kqXHOkbPguYYGgrBwihIVJIjoGvhVqTN3cjjWZEp4NWJfpQUQyDYUkyuhNVKmLYSHxO5EwEegjYQ84A=="
    const val AZAM_SECONDARY_CDN_TOKEN_ENCODED =
        "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJleHAiOiIxNzkwMzc2NTgyIiwic2lwIjoiIiwicGF0aCI6IiIsInNlc3Npb25fY2RuX2lkIjoiOTdmYmY3NWU2ZWU2MmJkYSIsInNlc3Npb25faWQiOiIiLCJjbGllbnRfaWQiOiI0ODQwODMyIiwiZGV2aWNlX2lkIjoiIiwibWF4X3Nlc3Npb25zIjowLCJzZXNzaW9uX2R1cmF0aW9uIjowLCJ1cmwiOiJodHRwczovLzEwMi4yMDguMjQ0LjkiLCJzZXNzaW9uX3RpbWVvdXQiOjAsImF1ZCI6IjciLCJzb3VyY2VzIjpbM119.zW07ft5kqXHOkbPguYYGgrBwihIVJIjoGvhVqTN3cjjWZEp4NWJfpQUQyDYUkyuhNVKmLYSHxO5EwEegjYQ84A%3D%3D"
    const val DEFAULT_AZAM_CDN_SOURCE = "cache"
    const val DEFAULT_TOKEN_ENDPOINT_URL = "https://streamzone.fun/api/cdn-token"
    const val DEFAULT_CHANNELS_BACKUP_API_URL = "https://streamzone.fun/api/channels"
    const val EXTENDED_TOKEN_EXPIRY_EPOCH_SEC = 4102444800L // 2100-01-01T00:00:00Z extended expiry

    /**
     * Known dummy/revoked test token fragments that must never overwrite the active Azam CDN token.
     */
    private val SUPERSEDED_TOKEN_FRAGMENTS = listOf(
        "eyJleHAiOiIxNjAwMDAwMDAwIn0"
    )

    @Volatile
    var AZAM_CDN_HOST: String = DEFAULT_AZAM_CDN_HOST
        private set

    @Volatile
    var AZAM_CDN_TOKEN: String = DEFAULT_AZAM_CDN_TOKEN
        private set

    /**
     * Azam TV tokens are treated as non-expiring (`null` = no client-side cutoff)
     * with [effectiveTokenExpiryEpochSec] extended to Year 2100.
     */
    @Volatile
    var AZAM_CDN_EXP: Long? = null
        private set

    @Volatile
    var effectiveTokenExpiryEpochSec: Long = EXTENDED_TOKEN_EXPIRY_EPOCH_SEC
        private set

    @Volatile
    private var hasAuthoritativeLiveToken: Boolean = false

    @Volatile
    var lastLiveTokenSyncEpochMs: Long = 0L
        private set

    @Volatile
    var AZAM_CDN_SOURCE: String = DEFAULT_AZAM_CDN_SOURCE
        private set

    @Volatile
    var AZAM_TOKEN_ENDPOINT_URL: String = DEFAULT_TOKEN_ENDPOINT_URL
        private set

    @Volatile
    var CHANNELS_BACKUP_API_URL: String = DEFAULT_CHANNELS_BACKUP_API_URL
        private set

    @Volatile
    private var cachedBackupApiChannels: List<LiveChannel> = emptyList()

    @Volatile
    var lastRefreshedEpochMs: Long = System.currentTimeMillis()
        private set

    private val _isAutoSynced = MutableStateFlow(false)
    val isAutoSynced: StateFlow<Boolean> = _isAutoSynced.asStateFlow()

    /**
     * Automatically syncs and pre-warms all Live TV channels immediately when the user enters the app
     * so that clicking any channel starts playback without initial sync delay.
     */
    fun autoSyncAllChannelsNow(): List<LiveChannel> {
        val synced = refreshLiveChannels()
        _isAutoSynced.value = true
        TokenManager.prewarmAllChannelsInBackground(synced)
        return synced
    }

    /**
     * Refreshes the Live TV channel feed in-place without restarting the app:
     * re-applies the latest Azam CDN host/token to all stream URLs and re-sorts
     * strictly by priority (Azam TV -> Tanzania -> International).
     */
    fun refreshLiveChannels(): List<LiveChannel> {
        val adminChannels = NeliAdminManager.customAddedChannels.value
        val current = (adminChannels + channels + _liveChannelsFlow.value).distinctBy { it.id }
            .filter { !NeliAdminManager.isChannelHidden(it.id) }
        val refreshed = mergeWithBackupChannels(
            getPrioritizedAllChannels(current).map { ch ->
                ch.copy(
                    streamUrl = normalizeDashStreamUrl(ch.streamUrl),
                    backupStreamUrl = if (ch.backupStreamUrl.isNotBlank()) normalizeDashStreamUrl(ch.backupStreamUrl) else ""
                )
            },
            cachedBackupApiChannels.filter { !NeliAdminManager.isChannelHidden(it.id) }
        ).filter { !NeliAdminManager.isChannelHidden(it.id) }
        _liveChannelsFlow.value = refreshed
        lastRefreshedEpochMs = System.currentTimeMillis()
        return refreshed
    }

    /**
     * Returns ALL channels (built-in + custom Admin channels, including hidden ones) for the Mini Admin Panel
     * so Admin can hide or unhide any channel without ever deleting it.
     */
    fun getAllChannelsIncludingHiddenForAdmin(): List<LiveChannel> {
        val combined = (NeliAdminManager.customAddedChannels.value + channels + cachedBackupApiChannels)
            .distinctBy { it.id }
        return combined.map { ch ->
            val resolvedLogo = resolveGuaranteedChannelLogoUrl(ch)
            if (resolvedLogo != ch.thumbnailUrl) ch.copy(thumbnailUrl = resolvedLogo) else ch
        }.sortedByDescending { it.priorityTier }
    }

    fun refreshCdnTokenFromEndpointSync(apiUrl: String = AZAM_TOKEN_ENDPOINT_URL): Boolean {
        val fetched = TokenManager.fetchLiveTokenBlocking(forceRefresh = true, apiUrl = apiUrl)
        return fetched.isNotBlank()
    }

    val categories = listOf(
        "All",
        "Azam TV",
        "Sports",
        "Entertainment",
        "Movies",
        "Kids",
        "News",
        "Music",
        "Africa",
        "Tanzania",
        "Documentary",
        "Other"
    )

    val channels: List<LiveChannel> = listOf(
        // 1. AZAM SPORT 1 HD (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "R17JUvbCEzu2eTbjnE74",
            name = "Azam Sports 1 HD",
            description = "mpira live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_1_TOKEN/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_1_TOKEN/live/eds/AzamSport1/DASH/AzamSport1.mpd?cdntoken=$AZAM_SPORT_1_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85"),
            country = "Tanzania",
            featured = true
        ),
        // 2. AZAM SPORT 2 HD (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f",
            name = "Azam Sports 2 HD",
            description = "mpira live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_2_TOKEN/live/eds/AzamSport2/DASH/AzamSport2.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_2_TOKEN/live/eds/AzamSport2/DASH/AzamSport2.mpd?cdntoken=$AZAM_SPORT_2_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Y7Cj3Wtj/azam-sport-2-01.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("739e7499125b31cc9948da8057b84cf9" to "1b7d44d798c351acc02f33ddfbb7682a"),
            country = "Tanzania",
            featured = true
        ),
        // 3. AZAM SPORT 3 HD (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "1c976127-e8a4-4bd6-8e73-5da0edce369b",
            name = "Azam Sports 3 HD",
            description = "mpiraa live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_3_TOKEN/live/eds/AzamSport3/DASH/AzamSport3.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_3_TOKEN/live/eds/AzamSport3/DASH/AzamSport3.mpd?cdntoken=$AZAM_SPORT_3_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/2YfLQ545/1000221070.png",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("2f12d7b889de381a9fb5326ca3aa166d" to "51c2d733a54306fdf89acd4c9d4f6005"),
            country = "Tanzania",
            featured = true
        ),
        // 4. AZAM SPORT 4 HD (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c",
            name = "Azam Sports 4 HD",
            description = "Mpira Live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_4_TOKEN/live/eds/AzamSport4/DASH/AzamSport4.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_4_TOKEN/live/eds/AzamSport4/DASH/AzamSport4.mpd?cdntoken=$AZAM_SPORT_4_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("fbcac74e355b3b5d9b6cbc56c7597dbf" to "5f980e872b378d88b46f656a806205c3"),
            country = "Tanzania",
            featured = true
        ),
        // 5. AZAM SPORT 5 HD (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "azam_sport_5_hd",
            name = "Azam Sports 5 HD",
            description = "Mpira Live • Azam TV",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_5_TOKEN/live/eds/AzamSport5/DASH/AzamSport5.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_SPORT_5_TOKEN/live/eds/AzamSport5/DASH/AzamSport5.mpd?cdntoken=$AZAM_SPORT_5_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/SwtFQNsh/1000221063.jpg",
            categories = listOf("sport", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf(
                "fbcac74e355b3b5d9b6cbc56c7597dbf" to "5f980e872b378d88b46f656a806205c3",
                "c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85",
                "739e7499125b31cc9948da8057b84cf9" to "1b7d44d798c351acc02f33ddfbb7682a",
                "2f12d7b889de381a9fb5326ca3aa166d" to "51c2d733a54306fdf89acd4c9d4f6005"
            ),
            country = "Tanzania",
            featured = true
        ),
        // 6. AZAM ONE (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "c405ae74-c4c5-4842-9f26-130ce380b307",
            name = "Azam One",
            description = "Burudani na Filamu za Afrika Mashariki",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_ONE_TOKEN/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_ONE_TOKEN/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$AZAM_ONE_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1"),
            country = "Tanzania",
            featured = true
        ),
        // 7. AZAM TWO (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            name = "Azam Two",
            description = "Tamthilia za Kiswahili na Burudani Live",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_TWO_TOKEN/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_TWO_TOKEN/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$AZAM_TWO_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("18f515fd536b3e728e9844e77d9f0fa8" to "c96dfd9b65560f87a1a95b8aeda0432d"),
            country = "Tanzania",
            featured = true
        ),
        // 8. SINEMA ZETU (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            name = "Sinema Zetu",
            description = "Filamu za Kiswahili & Bongo Movies 24/7",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$SINEMA_ZETU_TOKEN/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$SINEMA_ZETU_TOKEN/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$SINEMA_ZETU_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e"),
            country = "Tanzania",
            featured = true
        ),
        // 9. AZAM XTRA HD (Added Azam TV Channel #1 - MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "azam_xtra_hd_14",
            name = "Azam Xtra HD",
            description = "Tamthilia, Reality & Vipindi Maalum vya Azam TV",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_ONE_TOKEN/live/eds/AzamOne/DASH/AzamOne.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$AZAM_TWO_TOKEN/live/eds/AzamTwo/DASH/AzamTwo.mpd?cdntoken=$AZAM_TWO_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("entertainment", "movies", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf(
                "b5cbe1bb5acf3c7f9995be428245cfcd" to "89f1188a11e5e000d4443eb27ca378e1",
                "18f515fd536b3e728e9844e77d9f0fa8" to "c96dfd9b65560f87a1a95b8aeda0432d"
            ),
            country = "Tanzania",
            featured = true
        ),
        // 10. AZAM MOVIES HD (Added Azam TV Channel #2 - MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "azam_movies_hd_15",
            name = "Azam Movies HD",
            description = "Sinema Mpya & Action Cinema 24/7 • Azam TV",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$SINEMA_ZETU_TOKEN/live/eds/SinemaZetu/DASH/SinemaZetu.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/KIXMovies/DASH/KIXMovies.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            categories = listOf("movies", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf(
                "d628ae37a8f0336b970f250d9699461e" to "1194c3d60bb494aabe9114ca46c2738e",
                "a7e155b282f33335ae8d553f169f443c" to "c3fdcfd5d509f1ed8550d76a525e34e5"
            ),
            country = "Tanzania",
            featured = true
        ),
        // 11. CLOUDS TV HD (Added Azam TV Channel #3 - MPD + ClearKey + CDN Token)
        LiveChannel(
            id = "azam_clouds_tv_16",
            name = "Clouds TV HD",
            description = "The People's Station • Burudani & Muziki Live (Azam TV)",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$UTV_TOKEN/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf(
                "31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894",
                "8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"
            ),
            country = "Tanzania",
            featured = true
        ),
        // 12. ITV TANZANIA HD (Added Azam TV Channel #4 - MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "azam_itv_tz_17",
            name = "ITV Tanzania HD",
            description = "Super Brand • Habari, Tamthilia & Vipindi Live (Azam TV)",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$UTV_TOKEN/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC/DASH/ZBC.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("news", "entertainment", "tanzania"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf(
                "31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894",
                "e91fec140bc5316f919b4dc9c16287d7" to "79884fad0dbfcdc43d3e33c82a1f1cfa"
            ),
            country = "Tanzania",
            featured = true
        ),
        // 13. UTV (MPD + ClearKey + Embedded Path Token)
        LiveChannel(
            id = "d7b415f7-d024-4c7f-a570-653367e8dc5c",
            name = "UTV",
            description = "Habari, Michezo na Burudani • Azam TV",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$UTV_TOKEN/live/eds/UTV/DASH/UTV.mpd?cdntoken=$DEFAULT_AZAM_CDN_TOKEN",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/tok_$UTV_TOKEN/live/eds/UTV/DASH/UTV.mpd?cdntoken=$UTV_TOKEN",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/N2nCDLwD/1000221071.png",
            categories = listOf("entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("31b8fc6289fe3ca698588a59d845160c" to "f8c4e73f419cb80db3bdf4a974e31894"),
            country = "Tanzania",
            featured = true
        ),
        // 14. ZBC2 (DASH + ClearKey + Query Token)
        LiveChannel(
            id = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            name = "ZBC2",
            description = "Zanzibar Broadcasting Corporation 2",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC2/DASH/ZBC2.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC2/DASH/ZBC2-mp4a_160000=20000-p=363288647000000-init.mp4?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            categories = listOf("tanzania", "entertainment"),
            language = "sw",
            encryptionType = "clearkey",
            clearKeys = mapOf("f38b03549d023f3a883997328537bf4d" to "90f9d2d9cc26009c437905692ad70730"),
            country = "Tanzania",
            featured = true
        ),
        // 15. ZBC (DASH + ClearKey + Query Token)
        LiveChannel(
            id = "101e9c3f-de90-47fb-a69c-342be8a0bb80",
            name = "ZBC",
            description = "Zanzibar Broadcasting Corporation",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC/DASH/ZBC.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/ZBC/DASH/ZBC-mp4a_160000_eng=20000-p=363288645000000-init.mp4?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/xtynWQsN/1000221078.png",
            categories = listOf("tanzania", "other"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e91fec140bc5316f919b4dc9c16287d7" to "79884fad0dbfcdc43d3e33c82a1f1cfa"),
            country = "Tanzania",
            featured = true
        ),
        // 16. KIX (DASH + ClearKey + Query Token)
        LiveChannel(
            id = "4f36f2d1-ff7f-467b-b4f2-6f303263f28b",
            name = "KIX",
            description = "Action Movies • Azam TV",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/KIXMovies/DASH/KIXMovies.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/KIXMovies/DASH/KIXMovies-mp4a_160000_eng=20000-p=363288550000000-3702924726973333.mp4?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/ch6qGQT3/KIX-logo-svg.png",
            categories = listOf("movies"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("a7e155b282f33335ae8d553f169f443c" to "c3fdcfd5d509f1ed8550d76a525e34e5"),
            featured = true
        ),
        // 17. Crown Tv (DASH + ClearKey + Query Token)
        LiveChannel(
            id = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            name = "Crown Tv",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/CrownTv/DASH/CrownTv.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/CrownTv/DASH/CrownTv-mp4a_160000_eng=20000-p=363288529000000-3702929489866667.mp4?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            categories = listOf("music", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("e3eb6e0656ec3c22aa308aaa3f82c565" to "a7634d6defd2c255135095c45bc442fb"),
            country = "Tanzania",
            featured = true
        ),
        // 18. Wasafi (DASH + ClearKey + Query Token)
        LiveChannel(
            id = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            name = "Wasafi",
            description = "Muziki na Burudani Live • Tanzania",
            streamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV.mpd?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            backupStreamUrl = "https://cdnedgch2.azamtvltd.co.tz/live/eds/WasafiTV/DASH/WasafiTV-mp4a_160000=20000-p=363288641000000-init.mp4?cdntoken=$AZAM_SECONDARY_CDN_TOKEN_ENCODED",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            categories = listOf("music", "entertainment", "tanzania"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("8714fe102679348e9c76cfd315dacaa0" to "a8b86ceda831061c13c7c4c67bd77f8e"),
            country = "Tanzania",
            featured = true
        ),
        // 18. POP Animation Network (Tanzania Live TV - HLS M3U8)
        LiveChannel(
            id = "tv_1788953482934_twaqe",
            name = "POP Animation Network",
            description = "Kids • Tanzania Live TV",
            streamUrl = "https://narrative-popkids-rakuten.amagi.tv/1080p/index.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSDr0lcgVyDkVpdt6fh7yCYby-shZUQ3K3e_pBwt6j8HA&s=10",
            categories = listOf("kids", "tanzania"),
            language = "en",
            encryptionType = "none",
            country = "Tanzania",
            featured = true,
            enabled = true,
            published = true
        ),
        // 19. Baby Tv (MPD + ClearKey)
        LiveChannel(
            id = "68354b4a-d06c-4c8b-b6be-85cc3e95a43b",
            name = "Baby Tv",
            description = "",
            streamUrl = "https://cache07.zapitv.com/live/eds_c2/BABYTV/dash_live_ez/BABYTV.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/B0D1MgZ/Baby-TV-logo.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("7556319f3ce54d51b955fa0887ee7388" to "d36a3893e796b5934d17fd866f133a82")
        ),
        // 20. TNT SPORTS 1 HD (MPD + ClearKey)
        LiveChannel(
            id = "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c",
            name = "TNT SPORTS 1  HD",
            description = "",
            streamUrl = "https://live-pv-ta.amazon.fastly-edge.com/lhr-nitro/live/clients/dash/enc/fb6jy4pxts/out/v1/f8fa17f087564f51aa4d5c700be43ec4/cenc.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("f288380ca4cef9ad3f27a92a08e9bb8b" to "9f18d26291d9230833501f7f822f6875")
        ),
        // 21. TNT SPORTS 2 HD (MPD + ClearKey)
        LiveChannel(
            id = "ba2b232c-a79d-4130-b5d8-4883fbce7a1d",
            name = "TNT SPORTS 2 HD",
            description = "",
            streamUrl = "https://live-pv-ta.amazon.fastly-edge.com/lhr-nitro/live/clients/dash/enc/fb6jy4pxts/out/v1/f8fa17f087564f51aa4d5c700be43ec4/cenc.mpd",
            streamFormat = "dash",
            thumbnailUrl = "https://i.ibb.co/R4NBCtMm/4ae6eef1-b7ab-4fcd-bc20-18ac49124d95.jpg",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "clearkey",
            clearKeys = mapOf("f288380ca4cef9ad3f27a92a08e9bb8b" to "9f18d26291d9230833501f7f822f6875")
        ),
        // 22. Cartoon network (M3U8 HLS)
        LiveChannel(
            id = "80212719-1b5c-45b3-9e52-b59e533705f1",
            name = "Cartoon network",
            description = "",
            streamUrl = "https://cdn4.skygo.mn/live/disk1/Cartoon_Network/HLSv3-FTA/Cartoon_Network.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/8gV4tq4g/1000221079.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 23. 1 KZN TV (M3U8 HLS)
        LiveChannel(
            id = "8Q6H024OmdN8zIZTG4Zz",
            name = "1 KZN TV",
            description = "ENJOY STREAM",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:1kzn.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://starsat.co.za/wp-content/uploads/2023/08/1KZN-TV.png",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 24. YAHOO FINANCE (M3U8 HLS)
        LiveChannel(
            id = "Vshw4JeUaRpzDcNdlzcn",
            name = "YAHOO FINANCE",
            description = "WATCH NOW",
            streamUrl = "https://d1ewctnvcwvvvu.cloudfront.net/240p-cc/index.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://images-cdn3.welcomesoftware.com/assets/yahoo+finance.jpg/Zz0yNWFjNTk0NjlkMmQxMWVmYjhlNjFlYTY2MTI5N2IyNg==?width=768&height=430",
            categories = listOf("other"),
            language = "en",
            encryptionType = "none"
        ),
        // 25. BBC NEWS (M3U8 HLS)
        LiveChannel(
            id = "9mFG4jZCtlXCEKfv5TF6",
            name = "BBC NEWS",
            description = "NEWS",
            streamUrl = "https://vs-hls-push-ww-live.akamaized.net/x=4/i=urn:bbc:pips:service:bbc_news_channel_hd/t=3840/v=pv14/b=5070016/main.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/v4JamQ9B-PUiJHjmZQs9UwTaoLQW8vijJMMpV5QvA2wHQ6iwWM8Q1s6O4jgTl0dtDigVWAi7SA=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 26. AMC Absolute Reality (M3U8 HLS)
        LiveChannel(
            id = "bhlB91ftosCPbTd2OMmP",
            name = "AMC Absolute Reality",
            description = "",
            streamUrl = "https://amc-absolutereality-1-us.plex.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/fYM014TZ/download-2.png",
            categories = listOf("other"),
            language = "en",
            encryptionType = "none"
        ),
        // 27. CAPE TOWN TV (M3U8 HLS)
        LiveChannel(
            id = "IoDxhM3b7jd3c7lU5vkI",
            name = "CAPE TOWN TV",
            description = "CAPETOWN TV",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:ctv.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://capetowntv.org/wp-content/uploads/2023/01/cropped-Web-icon.png",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 28. Anime X Hidive (M3U8 HLS)
        LiveChannel(
            id = "O56rdn4uSDFd8rMFQpgT",
            name = "Anime X Hidive",
            description = "",
            streamUrl = "https://amc-anime-x-hidive-1-us.tablo.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/d0hG0N1/hidive-logo.png",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 29. CNN (M3U8 HLS)
        LiveChannel(
            id = "DBSsfqWBXJRSnbCtaImk",
            name = "CNN",
            description = "NEWS",
            streamUrl = "https://turnerlive.warnermediacdn.com/hls/live/586495/cnngo/cnn_slate/VIDEO_0_3564000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://cdn-1.webcatalog.io/catalog/cnn-international/cnn-international-icon-unplated.png?v=1716987984587",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 30. GOSPER CARTOONS (M3U8 HLS)
        LiveChannel(
            id = "0ZPw7ulYy8ILOyos8ywF",
            name = "GOSPER CARTOONS",
            description = "KIDS",
            streamUrl = "https://stmv1.srvif.com/gospelcartoon/gospelcartoon/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://www.cxtv.com.br/img/Tvs/Logo/webp-l/f0a3c3c7b9a651e847d86dd71bbb5551.webp",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 31. BEK TV Sports West (M3U8 HLS)
        LiveChannel(
            id = "JgV8VrShS7f1hXJvQiUn",
            name = "BEK TV Sports West",
            description = "",
            streamUrl = "https://cdn3.wowza.com/5/ZWQ1K2NYTmpFbGsr/BEK-WOWZA-1/smil:BEKPRIMEW.smil/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcRouVhLTmPWd6crbON-IgQpS1fBerasj88W1Npd06GUfA&s=10",
            categories = listOf("sport"),
            language = "en",
            encryptionType = "none"
        ),
        // 32. WWE (M3U8 HLS)
        LiveChannel(
            id = "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2",
            name = "WWE",
            description = "",
            streamUrl = "https://dpv6ceilvhyrw.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-7ufe851j0vqbg/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/zW4Jvp6z/WWE-official-logo-svg.png",
            categories = listOf("sport", "entertainment"),
            language = "en",
            encryptionType = "none",
            featured = true
        ),
        // 33. CBEEBIES (M3U8 HLS)
        LiveChannel(
            id = "fbc3ebe6-29c5-418a-89cd-3aa17a05e337",
            name = "CBEEBIES",
            description = "",
            streamUrl = "https://cdn4.skygo.mn/live/disk1/Cbeebies/HLSv3-FTA/Cbeebies.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/FLXj9vtv/cbeebies-cbbc-television-channel-itv3-apps.jpg",
            categories = listOf("kids"),
            language = "en",
            encryptionType = "none"
        ),
        // 34. SABC NEWS (M3U8 HLS)
        LiveChannel(
            id = "sVQCJNWwkyHclXVuCPpL",
            name = "SABC NEWS",
            description = "INDEPENDENT. IMPARTIAL",
            streamUrl = "https://sabconetanw.cdn.mangomolo.com/news/smil:news.stream.smil/chunklist_b250000_t64MjQwcA==.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/x5Bgc7UBYZvOHvp3UG91lZbV6ND0YCOi2a026vLPDGIU5GF2qp5JnHnCeLyPegWhEjM5njpN=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("news"),
            language = "en",
            encryptionType = "none"
        ),
        // 35. SOWETO TV (M3U8 HLS)
        LiveChannel(
            id = "W7mBoCT7HiRMU5rHVL4J",
            name = "SOWETO TV",
            description = "HI",
            streamUrl = "https://cdn.freevisiontv.co.za/sttv/smil:soweto.stream.smil/chunklist_b480000.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcTHIRfIk1qiekejwXNifTZCXGm0hS1cLmxiqg&s",
            categories = listOf("africa"),
            language = "en",
            encryptionType = "none"
        ),
        // 36. WILD (M3U8 HLS)
        LiveChannel(
            id = "nTlfO9ki3eZrGmTmS8am",
            name = "WILD",
            description = "TELEVISION NETWORK",
            streamUrl = "https://dfhsahpa45kk2.cloudfront.net/scheduler/scheduleMaster/476/variant/22100055.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://yt3.googleusercontent.com/ZBZVBI42wYc6U1UTNnIkdjrdtB1WKUeAXDhbTwvDk8zjbeGIvMmdxAHOtzOzC75wHtITnqvSGw=s900-c-k-c0x00ffffff-no-rj",
            categories = listOf("documentary"),
            language = "en",
            encryptionType = "none"
        ),
        // 37. Dish on TV (DC7 • DStv / Showmax Linear Entertainment Channel)
        LiveChannel(
            id = "DC7152673681",
            name = "Dish on TV",
            description = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
            streamUrl = "https://amc-absolutereality-1-us.plex.wurl.tv/playlist.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://i.ibb.co/8gtr1n42/1000221072.jpg",
            categories = listOf("other", "entertainment"),
            language = "en",
            encryptionType = "none",
            channelTag = "DC7",
            rating = "FAM",
            scheduleEvents = listOf(
                ChannelScheduleEvent(
                    id = "DC7152673681",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-09-30T10:00:00.000+0000",
                    endDateTime = "2026-09-30T16:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "2843879f-7c62-42e2-a026-bb423fb13e5f",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673680",
                    nextEventId = "DC7152673682"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673682",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-09-30T16:00:00.000+0000",
                    endDateTime = "2026-09-30T22:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "af000a5f-0e5b-4ed8-a0bd-dddb603f1175",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673681",
                    nextEventId = "DC7152673683"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673683",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-09-30T22:00:00.000+0000",
                    endDateTime = "2026-10-01T04:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "9d88e656-9666-4472-919d-bc2a88ffa3a0",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673682",
                    nextEventId = "DC7152673684"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673684",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-10-01T04:00:00.000+0000",
                    endDateTime = "2026-10-01T10:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "3f1520b7-9d33-4ba3-9428-9a3f202742c9",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673683",
                    nextEventId = "DC7152673685"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673685",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-10-01T10:00:00.000+0000",
                    endDateTime = "2026-10-01T16:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "d68b2d9b-398d-4421-92ba-9abe575ebed2",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673684",
                    nextEventId = "DC7152673686"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673686",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-10-01T16:00:00.000+0000",
                    endDateTime = "2026-10-01T22:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "6e41c9c2-8b14-4d0e-9e12-54c9b2e13c86",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673685",
                    nextEventId = "DC7152673687"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673687",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-10-01T22:00:00.000+0000",
                    endDateTime = "2026-10-02T04:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "7b52d8d3-9c25-4e1f-af23-65dac3f24d97",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673686",
                    nextEventId = "DC7152673688"
                ),
                ChannelScheduleEvent(
                    id = "DC7152673688",
                    correlationId = "0x841EDF",
                    correlationGroupId = "0x00000000",
                    mainTitle = "Dish on TV",
                    synopsis = "This video channel is a complimentary and supportive entertainment channel to Dish on TV, showcasing the best programming , highlights and so much more on DStv. To access the Dish on TV Application, press OK on your remote control.",
                    startDateTime = "2026-10-02T04:00:00.000+0000",
                    endDateTime = "2026-10-02T10:00:00.000+0000",
                    duration = "06:00:00",
                    rating = "FAM",
                    ratingSummary = "FAM",
                    genres = listOf("Other"),
                    channelTag = "DC7",
                    scheduleId = "8c63e9e4-ad36-4f20-b034-76ebd4035ea8",
                    product = "DStv",
                    scheduling = "Linear",
                    packages = "DSTV_NOW,SHOWMAX",
                    pulseHost = "https://za-mc.videoplaza.tv",
                    streamable = true,
                    previousEventId = "DC7152673687",
                    nextEventId = "DC7152673689"
                )
            )
        )
    )

    private val _liveChannelsFlow = MutableStateFlow(channels)
    val liveChannelsFlow: StateFlow<List<LiveChannel>> = _liveChannelsFlow.asStateFlow()

    private val homepageChannelIds = listOf(
        "R17JUvbCEzu2eTbjnE74",                 // Azam Sports 1 HD
        "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f", // Azam Sports 2 HD
        "1c976127-e8a4-4bd6-8e73-5da0edce369b", // Azam Sports 3 HD
        "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c", // Azam Sports 4 HD
        "azam_sport_5_hd",                      // Azam Sports 5 HD
        "c405ae74-c4c5-4842-9f26-130ce380b307", // Azam One
        "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // Azam Two
        "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e", // Sinema Zetu
        "azam_xtra_hd_14",                      // Azam Xtra HD
        "azam_movies_hd_15",                    // Azam Movies HD
        "azam_clouds_tv_16",                    // Clouds TV HD (Azam TV)
        "azam_itv_tz_17",                       // ITV Tanzania HD (Azam TV)
        "d7b415f7-d024-4c7f-a570-653367e8dc5c", // UTV
        "4f36f2d1-ff7f-467b-b4f2-6f303263f28b", // KIX
        "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2"  // WWE
    )

    /**
     * Curated Live TV channels for the Homepage, including the expanded Azam TV bouquet.
     */
    val homePageFeaturedChannels: List<LiveChannel>
        get() {
            val source = _liveChannelsFlow.value
            return homepageChannelIds.mapNotNull { targetId ->
                source.find { it.id == targetId } ?: channels.find { it.id == targetId }
            }
        }

    /**
     * All Azam TV priority channels placed first for the Live TV screen and slider.
     */
    val azamPriorityChannels: List<LiveChannel>
        get() = _liveChannelsFlow.value.filter { it.isAzamPriority }.ifEmpty { channels.filter { it.isAzamPriority } }

    /**
     * Strictly the hardcoded AZAM TV live channels defined in [channels].
     * Used as the exclusive source of truth for Scan to Cast.
     */
    val hardcodedAzamChannels: List<LiveChannel>
        get() = channels.filter {
            it.isLiveBroadcast &&
                it.isAzamPriority &&
                it.streamUrl.contains("azamtvltd.co.tz", ignoreCase = true)
        }

    /**
     * Returns true ONLY if [channel] is one of the existing hardcoded AZAM TV live channels.
     * Strictly returns false for Movies, Series, Episodes, Offline Downloads, and non-AZAM Firebase Live TV channels.
     */
    fun isHardcodedAzamChannel(channel: LiveChannel?): Boolean {
        if (channel == null) return false
        if (!channel.isLiveBroadcast) return false
        if (channel.seriesId.isNotBlank() || channel.episodeId.isNotBlank()) return false
        if (channel.id.startsWith("mov_", ignoreCase = true) ||
            channel.id.startsWith("ser_", ignoreCase = true) ||
            channel.id.startsWith("ep_", ignoreCase = true) ||
            channel.id.startsWith("dl_", ignoreCase = true) ||
            channel.streamUrl.startsWith("file:", ignoreCase = true)
        ) {
            return false
        }
        val targetMatchKey = normalizeChannelMatchKey(channel.name)
        return hardcodedAzamChannels.any { hardcoded ->
            hardcoded.id.equals(channel.id, ignoreCase = true) ||
                (targetMatchKey.isNotEmpty() && normalizeChannelMatchKey(hardcoded.name) == targetMatchKey)
        }
    }

    /**
     * Resolves [channel] against the canonical hardcoded AZAM channel definition in [hardcodedAzamChannels]
     * while preserving the exact tokenized stream URL produced by the existing Android playback pipeline.
     */
    fun resolveHardcodedAzamChannel(channel: LiveChannel): LiveChannel {
        val targetMatchKey = normalizeChannelMatchKey(channel.name)
        val hardcoded = hardcodedAzamChannels.firstOrNull { cand ->
            cand.id.equals(channel.id, ignoreCase = true) ||
                (targetMatchKey.isNotEmpty() && normalizeChannelMatchKey(cand.name) == targetMatchKey)
        } ?: return channel

        val activeMatch = _liveChannelsFlow.value.firstOrNull { cand ->
            cand.id.equals(hardcoded.id, ignoreCase = true)
        }
        val rawPrimaryUrl = when {
            channel.streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
                channel.streamUrl.contains("/live/eds/", ignoreCase = true) -> channel.streamUrl
            activeMatch != null && activeMatch.streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) -> activeMatch.streamUrl
            else -> hardcoded.streamUrl
        }
        val rawBackupUrl = when {
            channel.backupStreamUrl.isNotBlank() -> channel.backupStreamUrl
            activeMatch != null && activeMatch.backupStreamUrl.isNotBlank() -> activeMatch.backupStreamUrl
            else -> hardcoded.backupStreamUrl
        }
        return hardcoded.copy(
            streamUrl = resolvePlayableAzamManifestUrl(rawPrimaryUrl),
            backupStreamUrl = if (rawBackupUrl.isNotBlank()) resolvePlayableAzamManifestUrl(rawBackupUrl) else "",
            thumbnailUrl = resolveGuaranteedChannelLogoUrl(hardcoded),
            clearKeys = if (hardcoded.clearKeys.isNotEmpty()) hardcoded.clearKeys else channel.clearKeys
        )
    }

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
        val combinedSource = (NeliAdminManager.customAddedChannels.value + source.ifEmpty { channels }).distinctBy { it.id }
        val active = combinedSource
            .filter { it.enabled && it.published && !NeliAdminManager.isChannelHidden(it.id) }
            .ifEmpty { channels.filter { !NeliAdminManager.isChannelHidden(it.id) } }
        return active
            .map { ch ->
                val resolvedLogo = resolveGuaranteedChannelLogoUrl(ch)
                if (resolvedLogo != ch.thumbnailUrl) ch.copy(thumbnailUrl = resolvedLogo) else ch
            }
            .sortedByDescending { it.priorityTier }
    }

    /**
     * Groups channels into vertical chunks of 6 channels so an inline Start.io MREC / Muted Video Ad
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
     * Checks whether a candidate token string is a known superseded or revoked token
     * so stale cloud config documents never overwrite a working Azam TV token.
     */
    fun isSupersededOrRevokedToken(candidateToken: String): Boolean {
        val clean = candidateToken.trim()
        if (clean.isEmpty()) return true
        return SUPERSEDED_TOKEN_FRAGMENTS.any { fragment ->
            clean.contains(fragment, ignoreCase = false)
        }
    }

    /**
     * Extracts the `exp` claim (epoch seconds) from a JWT token string if present.
     */
    fun extractJwtExpEpochSeconds(jwtToken: String): Long? {
        return try {
            val parts = jwtToken.trim().split(".")
            if (parts.size < 2) return null
            val payloadBase64 = parts[1]
            val padded = when (payloadBase64.length % 4) {
                2 -> "$payloadBase64=="
                3 -> "$payloadBase64="
                else -> payloadBase64
            }
            val decodedBytes = try {
                java.util.Base64.getUrlDecoder().decode(padded)
            } catch (_: Throwable) {
                android.util.Base64.decode(
                    padded,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
                )
            }
            val payloadJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
            val expStr = payloadJson.optString("exp", "").trim()
            expStr.toLongOrNull() ?: payloadJson.optLong("exp", 0L).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Updates the active CDN authorization token and edge host from a JSON payload or raw JWT:
     * `{"token": "...", "cdnHost": "https://cdnedgch2.azamtvltd.co.tz", "source": "cache"}`
     * Prevents stale Firebase tokens from overwriting a fresher token with a newer JWT `exp`.
     */
    fun updateCdnAuthorizationToken(
        jsonStr: String,
        isAuthoritativeTokenApi: Boolean = false
    ): Boolean {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return false
        return try {
            var newToken = ""
            var newHost = ""
            var newSource = AZAM_CDN_SOURCE
            var newEndpoint = ""
            var parsedExp: Long? = null

            if (trimmed.startsWith("eyJ") && !trimmed.startsWith("{")) {
                newToken = trimmed.removeSurrounding("\"")
            } else {
                val root = JSONObject(trimmed)
                val targetObj = root.optJSONObject("data")
                    ?: root.optJSONObject("result")
                    ?: root.optJSONObject("payload")
                    ?: root.optJSONObject("cdn_token")
                    ?: root.optJSONObject("azam_token")
                    ?: root.optJSONObject("cdnToken")
                    ?: root

                val tokenKeys = listOf(
                    "token",
                    "cdnToken",
                    "cdntoken",
                    "cdn_token",
                    "azamToken",
                    "azam_token",
                    "access_token",
                    "jwt",
                    "value"
                )
                for (k in tokenKeys) {
                    val v = targetObj.optString(k, "").trim()
                    if (v.isNotEmpty() && !v.startsWith("{")) {
                        newToken = v
                        break
                    }
                    val rootVal = root.optString(k, "").trim()
                    if (rootVal.isNotEmpty() && !rootVal.startsWith("{")) {
                        newToken = rootVal
                        break
                    }
                }
                newHost = targetObj.optString(
                    "cdnHost",
                    targetObj.optString("cdn_host", root.optString("cdnHost", root.optString("cdn_host", "")))
                ).trim().removeSuffix("/")
                newSource = targetObj.optString("source", root.optString("source", AZAM_CDN_SOURCE))
                    .ifBlank { DEFAULT_AZAM_CDN_SOURCE }
                newEndpoint = targetObj.optString(
                    "tokenEndpointUrl",
                    targetObj.optString("apiUrl", root.optString("tokenEndpointUrl", root.optString("apiUrl", "")))
                ).trim()
                val rawExp = targetObj.optLong("exp", root.optLong("exp", 0L))
                if (rawExp > 0L) {
                    parsedExp = rawExp
                }
            }

            if (newEndpoint.startsWith("http", ignoreCase = true)) {
                AZAM_TOKEN_ENDPOINT_URL = newEndpoint
            }
            if (newToken.isNotEmpty() && !isSupersededOrRevokedToken(newToken)) {
                val currentExp = extractJwtExpEpochSeconds(AZAM_CDN_TOKEN) ?: 0L
                val candidateExp = extractJwtExpEpochSeconds(newToken) ?: parsedExp ?: 0L

                val shouldReplaceToken = when {
                    isAuthoritativeTokenApi -> true
                    !hasAuthoritativeLiveToken && candidateExp >= currentExp -> true
                    !hasAuthoritativeLiveToken && currentExp == 0L -> true
                    candidateExp > currentExp -> true
                    AZAM_CDN_TOKEN == DEFAULT_AZAM_CDN_TOKEN -> true
                    else -> false
                }

                if (shouldReplaceToken) {
                    AZAM_CDN_TOKEN = newToken
                    if (isAuthoritativeTokenApi) {
                        hasAuthoritativeLiveToken = true
                        lastLiveTokenSyncEpochMs = System.currentTimeMillis()
                    }
                }
                if (newHost.startsWith("http", ignoreCase = true)) {
                    AZAM_CDN_HOST = newHost
                }
                // Keep AZAM_CDN_EXP null so client never blocks on expiry, and extend effectiveTokenExpiryEpochSec
                AZAM_CDN_EXP = null
                effectiveTokenExpiryEpochSec = maxOf(
                    EXTENDED_TOKEN_EXPIRY_EPOCH_SEC,
                    (candidateExp.takeIf { it > 0L } ?: EXTENDED_TOKEN_EXPIRY_EPOCH_SEC) + 315_360_000L
                )
                AZAM_CDN_SOURCE = newSource
                cachedBackupApiChannels = cachedBackupApiChannels.map { ch ->
                    val resolvedKeys = resolveClearKeysForChannel(ch)
                    ch.copy(
                        streamUrl = normalizeDashStreamUrl(ch.streamUrl),
                        backupStreamUrl = if (ch.backupStreamUrl.isNotBlank()) normalizeDashStreamUrl(ch.backupStreamUrl) else "",
                        clearKeys = resolvedKeys,
                        encryptionType = if (resolvedKeys.isNotEmpty()) "clearkey" else ch.encryptionType
                    )
                }
                _liveChannelsFlow.value = _liveChannelsFlow.value.map { ch ->
                    val resolvedKeys = resolveClearKeysForChannel(ch)
                    ch.copy(
                        streamUrl = normalizeDashStreamUrl(ch.streamUrl),
                        backupStreamUrl = if (ch.backupStreamUrl.isNotBlank()) normalizeDashStreamUrl(ch.backupStreamUrl) else "",
                        clearKeys = resolvedKeys,
                        encryptionType = if (resolvedKeys.isNotEmpty()) "clearkey" else ch.encryptionType
                    )
                }
                com.example.player.TokenManager.syncStateFromRepository()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun mergeFirebaseChannelsWithAzamPriority(remoteChannels: List<LiveChannel>) {
        val activeRemote = remoteChannels.filter { it.enabled && it.published }.map { rem ->
            val remKey = normalizeChannelMatchKey(rem.name)
            val remPath = extractStreamIdentityPath(rem.streamUrl)
            val matchingLocal = channels.find {
                it.id.equals(rem.id, ignoreCase = true) ||
                    it.name.equals(rem.name, ignoreCase = true) ||
                    (remKey.isNotEmpty() && normalizeChannelMatchKey(it.name) == remKey) ||
                    (remPath.isNotEmpty() && extractStreamIdentityPath(it.streamUrl) == remPath)
            }
            val mergedKeys = if (rem.clearKeys.isNotEmpty()) {
                matchingLocal?.clearKeys.orEmpty() + rem.clearKeys
            } else {
                matchingLocal?.clearKeys.orEmpty()
            }
            val normalizedUrl = resolvePlayableAzamManifestUrl(rem.streamUrl)
            val isDash = normalizedUrl.contains(".mpd", ignoreCase = true) ||
                normalizedUrl.contains("/live/eds/", ignoreCase = true)
            val resolvedLogo = rem.thumbnailUrl.trim()
                .ifEmpty { matchingLocal?.thumbnailUrl.orEmpty() }
                .let { candidate ->
                    if (candidate.isNotBlank()) candidate else resolveGuaranteedChannelLogoUrl(rem)
                }
            rem.copy(
                streamUrl = normalizedUrl,
                streamFormat = if (isDash) "dash" else rem.streamFormat,
                thumbnailUrl = resolvedLogo,
                encryptionType = if (mergedKeys.isNotEmpty()) "clearkey" else rem.encryptionType,
                clearKeys = mergedKeys
            )
        }
        val azamFirst = channels.filter { it.isAzamPriority }.map { ch ->
            val chKey = normalizeChannelMatchKey(ch.name)
            val matchingRemote = activeRemote.find {
                it.id.equals(ch.id, ignoreCase = true) ||
                    it.name.equals(ch.name.trim(), ignoreCase = true) ||
                    (chKey.isNotEmpty() && normalizeChannelMatchKey(it.name) == chKey)
            }
            val mergedLogo = matchingRemote?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: ch.thumbnailUrl
            val mergedKeys = if (matchingRemote != null && matchingRemote.clearKeys.isNotEmpty()) {
                ch.clearKeys + matchingRemote.clearKeys
            } else {
                ch.clearKeys
            }
            val preferredPrimaryUrl = selectPreferredAzamStreamUrl(
                localUrl = ch.streamUrl,
                remoteOrBackupUrl = matchingRemote?.streamUrl.orEmpty()
            )
            ch.copy(
                streamUrl = preferredPrimaryUrl,
                backupStreamUrl = matchingRemote?.streamUrl?.takeIf { it.isNotBlank() }?.let { resolvePlayableAzamManifestUrl(it) } ?: ch.backupStreamUrl,
                clearKeys = mergedKeys,
                encryptionType = if (mergedKeys.isNotEmpty()) "clearkey" else ch.encryptionType,
                thumbnailUrl = resolveGuaranteedChannelLogoUrl(ch.copy(thumbnailUrl = mergedLogo))
            )
        }
        val remainingLocal = channels.filterNot { it.isAzamPriority }.map { ch ->
            ch.copy(
                streamUrl = resolvePlayableAzamManifestUrl(ch.streamUrl),
                thumbnailUrl = resolveGuaranteedChannelLogoUrl(ch)
            )
        }
        val newRemote = activeRemote.filter { rem ->
            val remKey = normalizeChannelMatchKey(rem.name)
            azamFirst.none {
                it.id.equals(rem.id, ignoreCase = true) ||
                    it.name.equals(rem.name.trim(), ignoreCase = true) ||
                    (remKey.isNotEmpty() && normalizeChannelMatchKey(it.name) == remKey)
            }
        }
        val combinedRemaining = (newRemote + remainingLocal.filter { loc ->
            val locKey = normalizeChannelMatchKey(loc.name)
            newRemote.none {
                it.id == loc.id ||
                    it.name.equals(loc.name, ignoreCase = true) ||
                    (locKey.isNotEmpty() && normalizeChannelMatchKey(it.name) == locKey)
            }
        }).sortedByDescending { it.priorityTier }

        // Strictly order: 17 Azam priority channels -> Tanzania Live TV -> Featured -> Other Live TV
        _liveChannelsFlow.value = mergeWithBackupChannels(azamFirst + combinedRemaining, cachedBackupApiChannels)
    }

    fun getCachedBackupApiChannels(): List<LiveChannel> = cachedBackupApiChannels

    /**
     * Returns all known ClearKey `KID -> Key` pairs across all built-in and backup channels.
     */
    fun getAllKnownClearKeys(): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        channels.forEach { ch ->
            ch.clearKeys.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) map[k.trim().lowercase()] = v.trim().lowercase()
            }
        }
        cachedBackupApiChannels.forEach { ch ->
            ch.clearKeys.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) map[k.trim().lowercase()] = v.trim().lowercase()
            }
        }
        return map
    }

    /**
     * Resolves the complete ClearKey map for a channel:
     * 1. The channel's own `clearKeys` (or matching backup/built-in channel's `clearKeys`) come first
     *    so `Mp4CencDecryptor.fallbackKeyBytes` is the exact channel key.
     * 2. All other known `KID -> Key` entries follow so any multi-key or switched representation is decrypted by KID.
     */
    fun resolveClearKeysForChannel(channel: LiveChannel): Map<String, String> {
        val primaryMap = LinkedHashMap<String, String>()
        channel.clearKeys.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                primaryMap[k.trim().lowercase()] = v.trim().lowercase()
            }
        }
        val targetKey = normalizeChannelMatchKey(channel.name)
        val targetPath = extractStreamIdentityPath(channel.streamUrl)
        val backupMatch = cachedBackupApiChannels.firstOrNull { cand ->
            cand.id.equals(channel.id, ignoreCase = true) ||
                (targetKey.isNotEmpty() && normalizeChannelMatchKey(cand.name) == targetKey) ||
                (targetPath.isNotEmpty() && extractStreamIdentityPath(cand.streamUrl) == targetPath)
        }
        backupMatch?.clearKeys?.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                primaryMap[k.trim().lowercase()] = v.trim().lowercase()
            }
        }
        val builtInMatch = channels.firstOrNull { loc ->
            loc.id.equals(channel.id, ignoreCase = true) ||
                (targetKey.isNotEmpty() && normalizeChannelMatchKey(loc.name) == targetKey) ||
                (targetPath.isNotEmpty() && extractStreamIdentityPath(loc.streamUrl) == targetPath)
        }
        builtInMatch?.clearKeys?.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank() && !primaryMap.containsKey(k.trim().lowercase())) {
                primaryMap[k.trim().lowercase()] = v.trim().lowercase()
            }
        }
        // If this is a DASH or Azam channel, also include all known KID->Key mappings for KID lookup
        if (primaryMap.isNotEmpty() || channel.isDash || channel.isAzamPriority) {
            getAllKnownClearKeys().forEach { (k, v) ->
                if (!primaryMap.containsKey(k)) {
                    primaryMap[k] = v
                }
            }
        }
        return primaryMap
    }

    fun normalizeChannelMatchKey(nameOrId: String): String {
        return nameOrId.lowercase()
            .replace("tanzania", "")
            .replace("sports", "sport")
            .replace("channel", "")
            .replace("hd", "")
            .replace("tv", "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun extractStreamIdentityPath(url: String): String {
        val base = url.substringBefore("?").trim().lowercase()
        if (base.isEmpty()) return ""
        val liveEdsIdx = base.indexOf("/live/eds/")
        if (liveEdsIdx >= 0) {
            val afterEds = base.substring(liveEdsIdx + "/live/eds/".length)
            val channelDir = afterEds.substringBefore("/").trim()
            if (channelDir.isNotEmpty()) {
                return "/live/eds/$channelDir/dash/$channelDir.mpd"
            }
        }
        val liveIdx = base.indexOf("/live/")
        return if (liveIdx >= 0) base.substring(liveIdx) else base
    }

    private fun extractEmbeddedTokenFromUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val basePath = trimmed.substringBefore("?")
        if (basePath.contains("/tok_", ignoreCase = true)) {
            val tokPart = basePath.substringAfter("/tok_").substringBefore("/").trim()
            if (tokPart.startsWith("eyJ")) {
                return tokPart.replace("%3D", "=", ignoreCase = true)
            }
        }
        val query = trimmed.substringAfter("?", "")
        val queryTok = query.split("&")
            .firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()
        return queryTok.replace("%3D", "=", ignoreCase = true)
    }

    private fun selectPreferredAzamStreamUrl(localUrl: String, remoteOrBackupUrl: String): String {
        val cleanLocal = localUrl.trim()
        val cleanRemote = remoteOrBackupUrl.trim()
        if (cleanRemote.isEmpty()) return resolvePlayableAzamManifestUrl(cleanLocal)
        if (cleanLocal.isEmpty()) return resolvePlayableAzamManifestUrl(cleanRemote)

        val localHasTokPath = cleanLocal.substringBefore("?").contains("/tok_", ignoreCase = true)
        val remoteHasTokPath = cleanRemote.substringBefore("?").contains("/tok_", ignoreCase = true)
        val localExp = extractJwtExpEpochSeconds(extractEmbeddedTokenFromUrl(cleanLocal)) ?: 0L
        val remoteExp = extractJwtExpEpochSeconds(extractEmbeddedTokenFromUrl(cleanRemote)) ?: 0L

        return when {
            remoteExp > localExp && (remoteHasTokPath || !localHasTokPath) -> resolvePlayableAzamManifestUrl(cleanRemote)
            localHasTokPath && !remoteHasTokPath -> resolvePlayableAzamManifestUrl(cleanLocal)
            localExp >= remoteExp && localExp > 0L -> resolvePlayableAzamManifestUrl(cleanLocal)
            else -> resolvePlayableAzamManifestUrl(cleanRemote)
        }
    }

    /**
     * Converts Azam TV `/live/eds/<Channel>/DASH/<Segment>.mp4` (such as `-init.mp4` segment URLs)
     * into the playable DASH manifest path `/live/eds/<Channel>/DASH/<Channel>.mpd` while preserving
     * any `/tok_<JWT>/` path prefix and `?cdntoken=` query parameters.
     */
    fun resolvePlayableAzamManifestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return trimmed
        val basePath = trimmed.substringBefore("?")
        val query = trimmed.substringAfter("?", "")

        val rewrittenPath = if (
            basePath.contains("/live/eds/", ignoreCase = true) &&
            basePath.contains("/DASH/", ignoreCase = true) &&
            (basePath.endsWith(".mp4", ignoreCase = true) || basePath.endsWith(".m4s", ignoreCase = true))
        ) {
            val edsIdx = basePath.indexOf("/live/eds/", ignoreCase = true)
            val prefixBeforeEds = basePath.substring(0, edsIdx)
            val afterEds = basePath.substring(edsIdx + "/live/eds/".length)
            val channelDir = afterEds.substringBefore("/").trim()
            if (channelDir.isNotEmpty()) {
                "$prefixBeforeEds/live/eds/$channelDir/DASH/$channelDir.mpd"
            } else {
                basePath
            }
        } else {
            basePath
        }

        val combined = if (query.isNotEmpty()) "$rewrittenPath?$query" else rewrittenPath
        return normalizeDashStreamUrl(combined)
    }

    /**
     * Parses the JSON response from `https://streamzone.fun/api/channels` into [LiveChannel] items,
     * caches them for backup failover, and merges them with the active channel catalog.
     */
    fun parseBackupChannelsApiPayload(jsonStr: String): List<LiveChannel> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val itemObjects = mutableListOf<JSONObject>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { itemObjects.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                // If root also embeds token/cdnHost metadata, apply it
                val embeddedToken = root.optString("token")
                    .ifBlank { root.optString("cdnToken") }
                    .ifBlank { root.optString("cdntoken") }
                if (embeddedToken.isNotBlank()) {
                    val tokenPayload = JSONObject().apply {
                        put("token", embeddedToken)
                        val host = root.optString("cdnHost")
                        if (host.isNotBlank()) put("cdnHost", host)
                    }
                    updateCdnAuthorizationToken(tokenPayload.toString())
                }

                val arrayKeys = listOf("channels", "data", "items", "results", "streams", "list")
                var foundArray = false
                for (key in arrayKeys) {
                    val arr = root.optJSONArray(key)
                    if (arr != null) {
                        foundArray = true
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { itemObjects.add(it) }
                        }
                        break
                    }
                }
                if (!foundArray) {
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val childObj = root.optJSONObject(k)
                        if (childObj != null) {
                            if (!childObj.has("id")) {
                                childObj.put("id", k)
                            }
                            itemObjects.add(childObj)
                        }
                    }
                }
            }

            val parsed = itemObjects.mapIndexedNotNull { index, obj ->
                parseSingleBackupChannelObject(obj, index)
            }
            if (parsed.isNotEmpty()) {
                cachedBackupApiChannels = parsed
                _liveChannelsFlow.value = mergeWithBackupChannels(_liveChannelsFlow.value, parsed)
            }
            parsed
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parses a DStv / Showmax / Linear EPG schedule payload (`total`, `count`, `page`, `items`)
     * into [ChannelScheduleEvent] items and attaches them to the matching [LiveChannel] by `channel_tag` or `main_title`.
     */
    fun parseChannelSchedulePayload(jsonStr: String): List<ChannelScheduleEvent> {
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()
        return try {
            val itemObjects = mutableListOf<JSONObject>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { itemObjects.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val itemsArr = root.optJSONArray("items")
                    ?: root.optJSONArray("schedule")
                    ?: root.optJSONArray("events")
                    ?: root.optJSONArray("data")
                if (itemsArr != null) {
                    for (i in 0 until itemsArr.length()) {
                        itemsArr.optJSONObject(i)?.let { itemObjects.add(it) }
                    }
                } else if (root.has("main_title") || root.has("channel_tag") || root.has("schedule_id")) {
                    itemObjects.add(root)
                }
            }

            val events = itemObjects.mapNotNull { obj ->
                val id = obj.optString("id").trim()
                val adRequestObj = obj.optJSONObject("ad_request")
                val acpObj = adRequestObj?.optJSONObject("acp")
                val mainTitle = obj.optString("main_title")
                    .ifBlank { acpObj?.optString("title").orEmpty() }
                    .ifBlank { obj.optString("title") }
                    .trim()
                val channelTag = obj.optString("channel_tag").trim()
                if (id.isEmpty() && mainTitle.isEmpty() && channelTag.isEmpty()) return@mapNotNull null

                val genresList = mutableListOf<String>()
                val genresArr = obj.optJSONArray("genres")
                if (genresArr != null) {
                    for (i in 0 until genresArr.length()) {
                        val g = genresArr.optString(i).trim()
                        if (g.isNotEmpty()) genresList.add(g)
                    }
                }
                val timeShiftObj = obj.optJSONObject("time_shift_stream")
                val featuresObj = obj.optJSONObject("features")

                ChannelScheduleEvent(
                    id = id.ifEmpty { "${channelTag}_${obj.optString("schedule_id")}" },
                    genRef = obj.optString("gen_ref"),
                    correlationId = obj.optString("correlation_id"),
                    correlationGroupId = obj.optString("correlation_group_id"),
                    mainTitle = mainTitle.ifEmpty { channelTag },
                    episodeTitle = obj.optString("episode_title"),
                    synopsis = obj.optString("synopsis"),
                    seasonNumber = obj.optString("season_number", "0"),
                    episodeNumber = obj.optString("episode_number", "0"),
                    live = obj.optBoolean("live", false),
                    startDateTime = obj.optString("start_date_time"),
                    endDateTime = obj.optString("end_date_time"),
                    duration = obj.optString("duration"),
                    maturityClassification = obj.optString("maturity_classification"),
                    rating = obj.optString("rating"),
                    ratingSummary = obj.optString("rating_summary"),
                    year = obj.optInt("year", 0),
                    genres = genresList,
                    channelTag = channelTag,
                    blockStream = obj.optBoolean("block_stream", false),
                    scheduleId = obj.optString("schedule_id"),
                    product = acpObj?.optString("product").orEmpty(),
                    scheduling = acpObj?.optString("scheduling").orEmpty(),
                    packages = acpObj?.optString("package").orEmpty(),
                    pulseHost = adRequestObj?.optString("pulse_host").orEmpty(),
                    timeShiftDashUrl = timeShiftObj?.optString("dash").orEmpty(),
                    timeShiftHlsUrl = timeShiftObj?.optString("hls").orEmpty(),
                    timeShiftMssUrl = timeShiftObj?.optString("mss").orEmpty(),
                    streamable = featuresObj?.optBoolean("streamable", true) ?: true,
                    recordable = featuresObj?.optBoolean("recordable", false) ?: false,
                    allowPastEventRewatch = featuresObj?.optBoolean("allowPastEventRewatch", false) ?: false,
                    previousEventId = obj.optString("previous_event_id"),
                    nextEventId = obj.optString("next_event_id"),
                    restricted = obj.optBoolean("restricted", false),
                    ageRestriction = obj.optInt("age_restriction", 0)
                )
            }

            if (events.isNotEmpty()) {
                val first = events.first()
                val updatedChannels = _liveChannelsFlow.value.map { ch ->
                    val matchesTag = first.channelTag.isNotEmpty() &&
                        (ch.channelTag.equals(first.channelTag, ignoreCase = true) ||
                            ch.id.startsWith(first.channelTag, ignoreCase = true))
                    val matchesTitle = first.mainTitle.isNotEmpty() &&
                        ch.name.equals(first.mainTitle, ignoreCase = true)
                    if (matchesTag || matchesTitle) {
                        val timeShiftStream = events.firstNotNullOfOrNull { ev ->
                            ev.timeShiftDashUrl.ifBlank { ev.timeShiftHlsUrl }
                                .ifBlank { ev.timeShiftMssUrl }
                                .takeIf { it.isNotBlank() }
                        }
                        ch.copy(
                            description = first.synopsis.ifBlank { ch.description },
                            channelTag = first.channelTag.ifBlank { ch.channelTag },
                            rating = first.rating.ifBlank { ch.rating },
                            streamUrl = timeShiftStream ?: ch.streamUrl,
                            scheduleEvents = events
                        )
                    } else {
                        ch
                    }
                }
                _liveChannelsFlow.value = updatedChannels
            }
            events
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSingleBackupChannelObject(obj: JSONObject, index: Int): LiveChannel? {
        val adRequestObj = obj.optJSONObject("ad_request")
        val acpObj = adRequestObj?.optJSONObject("acp")
        val name = obj.optString("name")
            .ifBlank { obj.optString("title") }
            .ifBlank { obj.optString("main_title") }
            .ifBlank { acpObj?.optString("title").orEmpty() }
            .ifBlank { obj.optString("channelName") }
            .ifBlank { obj.optString("channel_name") }
            .ifBlank { obj.optString("label") }
            .trim()

        val timeShiftObj = obj.optJSONObject("time_shift_stream")
        val rawUrl = obj.optString("streamUrl")
            .ifBlank { obj.optString("stream_url") }
            .ifBlank { obj.optString("url") }
            .ifBlank { obj.optString("manifestUrl") }
            .ifBlank { obj.optString("manifest_url") }
            .ifBlank { obj.optString("mpdUrl") }
            .ifBlank { obj.optString("mpd") }
            .ifBlank { obj.optString("dashUrl") }
            .ifBlank { obj.optString("hlsUrl") }
            .ifBlank { obj.optString("m3u8Url") }
            .ifBlank { obj.optString("m3u8") }
            .ifBlank { obj.optString("playUrl") }
            .ifBlank { obj.optString("src") }
            .ifBlank { obj.optString("stream") }
            .ifBlank { obj.optString("link") }
            .ifBlank { timeShiftObj?.optString("dash").orEmpty() }
            .ifBlank { timeShiftObj?.optString("hls").orEmpty() }
            .ifBlank { timeShiftObj?.optString("mss").orEmpty() }
            .trim()

        if (name.isBlank() || rawUrl.isBlank()) return null

        val rawBackupUrl = obj.optString("backupStreamUrl")
            .ifBlank { obj.optString("backup_url") }
            .ifBlank { obj.optString("backupUrl") }
            .ifBlank { obj.optString("fallbackUrl") }
            .ifBlank { obj.optString("altUrl") }
            .trim()

        val clearKeysMap = extractClearKeysMapFromChannelJson(obj)
        val normalizedUrl = resolvePlayableAzamManifestUrl(rawUrl)
        val normalizedBackupUrl = if (rawBackupUrl.isNotBlank()) {
            resolvePlayableAzamManifestUrl(rawBackupUrl)
        } else {
            ""
        }

        val matchingLocal = channels.find {
            it.name.equals(name, ignoreCase = true) ||
                normalizeChannelMatchKey(it.name) == normalizeChannelMatchKey(name) ||
                (extractStreamIdentityPath(it.streamUrl).isNotEmpty() &&
                    extractStreamIdentityPath(it.streamUrl) == extractStreamIdentityPath(normalizedUrl))
        }

        val rawId = obj.optString("id")
            .ifBlank { obj.optString("_id") }
            .ifBlank { obj.optString("channelId") }
            .ifBlank { obj.optString("channel_id") }
            .ifBlank { obj.optString("slug") }
            .ifBlank { matchingLocal?.id.orEmpty() }
            .ifBlank {
                "backup_ch_${name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')}_$index"
            }

        val rawLogo = obj.optString("thumbnailUrl")
            .ifBlank { obj.optString("logoUrl") }
            .ifBlank { obj.optString("logo_url") }
            .ifBlank { obj.optString("logo") }
            .ifBlank { obj.optString("icon") }
            .ifBlank { obj.optString("image") }
            .ifBlank { obj.optString("posterUrl") }
            .ifBlank { obj.optString("thumbnail") }
            .ifBlank { obj.optString("tvgLogo") }
            .ifBlank { obj.optString("tvg_logo") }
            .ifBlank { matchingLocal?.thumbnailUrl.orEmpty() }
            .trim()

        val categories = extractCategoriesFromChannelJson(obj, name, matchingLocal?.categories.orEmpty())
        val finalClearKeys = if (clearKeysMap.isNotEmpty()) clearKeysMap else matchingLocal?.clearKeys.orEmpty()
        val isDash = normalizedUrl.contains(".mpd", ignoreCase = true) ||
            normalizedUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
            normalizedUrl.contains("/live/eds/", ignoreCase = true)

        val isAzam = normalizedUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
            normalizedUrl.contains("cdntoken=", ignoreCase = true) ||
            normalizedUrl.contains("/live/eds/", ignoreCase = true) ||
            matchingLocal?.isAzamPriority == true

        val country = obj.optString("country").ifBlank { matchingLocal?.country ?: "Tanzania" }
        val description = obj.optString("description")
            .ifBlank { obj.optString("synopsis") }
            .ifBlank { obj.optString("tagline") }
            .ifBlank { matchingLocal?.description.orEmpty() }
            .ifBlank { "${categories.firstOrNull() ?: "Live TV"} • $country" }
        val channelTag = obj.optString("channel_tag").ifBlank { matchingLocal?.channelTag.orEmpty() }
        val rating = obj.optString("rating").ifBlank { matchingLocal?.rating.orEmpty() }

        val provisional = LiveChannel(
            id = rawId,
            name = name,
            description = description,
            streamUrl = normalizedUrl,
            backupStreamUrl = normalizedBackupUrl,
            streamFormat = if (isDash) "dash" else "hls",
            thumbnailUrl = rawLogo,
            categories = categories,
            language = obj.optString("language").ifBlank { matchingLocal?.language ?: "sw" },
            country = country,
            featured = obj.optBoolean("featured", obj.optBoolean("isFeatured", matchingLocal?.featured ?: isAzam)),
            encryptionType = if (finalClearKeys.isNotEmpty()) "clearkey" else "none",
            clearKeys = finalClearKeys,
            enabled = obj.optBoolean("enabled", true),
            published = obj.optBoolean("published", true),
            channelTag = channelTag,
            rating = rating
        )
        val guaranteedLogo = if (rawLogo.isNotBlank()) rawLogo else resolveGuaranteedChannelLogoUrl(provisional)
        return provisional.copy(thumbnailUrl = guaranteedLogo)
    }

    private fun extractClearKeysMapFromChannelJson(obj: JSONObject): Map<String, String> {
        val result = mutableMapOf<String, String>()

        fun parseColonPair(pairStr: String) {
            val clean = pairStr.trim()
            if (clean.contains(":") && !clean.startsWith("{") && !clean.startsWith("http", ignoreCase = true)) {
                val kid = clean.substringBefore(":").trim()
                val key = clean.substringAfter(":").trim()
                if (kid.isNotEmpty() && key.isNotEmpty()) {
                    result[kid] = key
                }
            }
        }

        parseColonPair(obj.optString("clearKey"))
        parseColonPair(obj.optString("clear_key"))
        parseColonPair(obj.optString("drmKey"))
        parseColonPair(obj.optString("drm_key"))
        parseColonPair(obj.optString("key"))

        val directKid = obj.optString("kid")
            .ifBlank { obj.optString("keyId") }
            .ifBlank { obj.optString("key_id") }
            .trim()
        val directKey = obj.optString("keyValue")
            .ifBlank { obj.optString("key_value") }
            .ifBlank { obj.optString("k") }
            .ifBlank {
                val k = obj.optString("key").trim()
                if (!k.contains(":")) k else ""
            }
            .trim()
        if (directKid.isNotEmpty() && directKey.isNotEmpty()) {
            result[directKid] = directKey
        }

        val mapFields = listOf("clearKeys", "clear_keys", "keys", "drm", "clearKey", "clearkey")
        for (field in mapFields) {
            val nested = obj.optJSONObject(field) ?: continue
            parseColonPair(nested.optString("clearKey"))
            parseColonPair(nested.optString("key"))

            val nestedKid = nested.optString("kid")
                .ifBlank { nested.optString("keyId") }
                .ifBlank { nested.optString("key_id") }
                .trim()
            val nestedKey = nested.optString("keyValue")
                .ifBlank { nested.optString("key") }
                .ifBlank { nested.optString("k") }
                .ifBlank { nested.optString("value") }
                .trim()
            if (nestedKid.isNotEmpty() && nestedKey.isNotEmpty() && !nestedKey.contains(":")) {
                result[nestedKid] = nestedKey
            }

            val innerKeysObj = nested.optJSONObject("clearKeys") ?: nested.optJSONObject("keys") ?: nested
            val keysIter = innerKeysObj.keys()
            while (keysIter.hasNext()) {
                val k = keysIter.next()
                if (k.length >= 16 && k !in listOf("type", "scheme", "licenseUrl", "clearKey", "keyId")) {
                    val v = innerKeysObj.optString(k).trim()
                    if (v.length >= 16) {
                        result[k] = v
                    }
                }
            }
        }
        return result
    }

    private fun extractCategoriesFromChannelJson(
        obj: JSONObject,
        name: String,
        fallbackCategories: List<String>
    ): List<String> {
        val list = mutableListOf<String>()
        val arr = obj.optJSONArray("categories")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.optString(i).trim()
                if (item.isNotEmpty()) list.add(item)
            }
        }
        val singleCat = obj.optString("category")
            .ifBlank { obj.optString("group") }
            .ifBlank { obj.optString("genre") }
            .ifBlank { obj.optString("type") }
            .trim()
        if (singleCat.isNotEmpty() && list.none { it.equals(singleCat, ignoreCase = true) }) {
            list.add(singleCat)
        }
        if (list.isNotEmpty()) return list
        if (fallbackCategories.isNotEmpty()) return fallbackCategories

        val lower = name.lowercase()
        return when {
            lower.contains("sport") || lower.contains("arena") || lower.contains("espn") || lower.contains("tbc 2") -> listOf("Sports")
            lower.contains("news") || lower.contains("bbc") || lower.contains("jazeera") || lower.contains("cnn") -> listOf("News")
            lower.contains("sinema") || lower.contains("movie") || lower.contains("action") || lower.contains("film") -> listOf("Movies")
            lower.contains("kid") || lower.contains("cartoon") || lower.contains("baby") || lower.contains("nick") -> listOf("Kids")
            lower.contains("music") || lower.contains("trace") || lower.contains("mtv") || lower.contains("wasafi") -> listOf("Music")
            lower.contains("wild") || lower.contains("nat geo") || lower.contains("discovery") || lower.contains("history") -> listOf("Documentary")
            else -> listOf("Entertainment")
        }
    }

    /**
     * Merges primary channels (already in app / Firebase) with backup channels from `https://streamzone.fun/api/channels`.
     * - Channels already in the app keep their primary entry and receive `backupStreamUrl` (and any missing ClearKeys/logo)
     *   from the backup API so the player can seamlessly fail over if needed.
     * - Any extra channels in `https://streamzone.fun/api/channels` not yet in the primary list are added as well.
     */
    fun mergeWithBackupChannels(
        primaryChannels: List<LiveChannel>,
        backupChannels: List<LiveChannel> = cachedBackupApiChannels
    ): List<LiveChannel> {
        if (backupChannels.isEmpty()) return primaryChannels

        val backupById = backupChannels.associateBy { it.id.lowercase() }
        val backupByPath = backupChannels
            .filter { extractStreamIdentityPath(it.streamUrl).isNotEmpty() }
            .associateBy { extractStreamIdentityPath(it.streamUrl) }
        val backupByKey = backupChannels
            .filter { normalizeChannelMatchKey(it.name).isNotEmpty() }
            .associateBy { normalizeChannelMatchKey(it.name) }

        val matchedBackupIds = mutableSetOf<String>()

        val enrichedPrimary = primaryChannels.map { primary ->
            val primaryKey = normalizeChannelMatchKey(primary.name)
            val primaryPath = extractStreamIdentityPath(primary.streamUrl)
            val matchedBackup = backupById[primary.id.lowercase()]
                ?: (if (primaryKey.isNotEmpty()) backupByKey[primaryKey] else null)
                ?: (if (primaryPath.isNotEmpty()) {
                    backupByPath[primaryPath]?.takeIf { candidate ->
                        val candKey = normalizeChannelMatchKey(candidate.name)
                        candKey == primaryKey || candKey.contains(primaryKey) || primaryKey.contains(candKey)
                    }
                } else null)

            if (matchedBackup != null) {
                matchedBackupIds.add(matchedBackup.id)
                val normalizedBackupUrl = resolvePlayableAzamManifestUrl(matchedBackup.streamUrl)
                val normalizedPrimaryUrl = selectPreferredAzamStreamUrl(
                    localUrl = primary.streamUrl,
                    remoteOrBackupUrl = matchedBackup.streamUrl
                )
                val mergedClearKeys = resolveClearKeysForChannel(
                    primary.copy(clearKeys = matchedBackup.clearKeys + primary.clearKeys)
                )
                primary.copy(
                    streamUrl = normalizedPrimaryUrl,
                    backupStreamUrl = normalizedBackupUrl.ifBlank { primary.backupStreamUrl },
                    clearKeys = mergedClearKeys,
                    encryptionType = if (mergedClearKeys.isNotEmpty()) "clearkey" else primary.encryptionType,
                    thumbnailUrl = primary.thumbnailUrl.ifBlank { matchedBackup.thumbnailUrl }
                )
            } else {
                val resolvedKeys = resolveClearKeysForChannel(primary)
                if (resolvedKeys.isNotEmpty() && primary.clearKeys.isEmpty()) {
                    primary.copy(
                        clearKeys = resolvedKeys,
                        encryptionType = "clearkey"
                    )
                } else {
                    primary
                }
            }
        }

        val existingKeys = enrichedPrimary.map { normalizeChannelMatchKey(it.name) }.toSet()
        val existingPaths = enrichedPrimary.map { extractStreamIdentityPath(it.streamUrl) }
            .filter { it.isNotEmpty() }
            .toSet()

        val extraBackupChannels = backupChannels.filter { backup ->
            val key = normalizeChannelMatchKey(backup.name)
            val path = extractStreamIdentityPath(backup.streamUrl)
            backup.id !in matchedBackupIds &&
                (key.isEmpty() || key !in existingKeys) &&
                (path.isEmpty() || path !in existingPaths)
        }.sortedByDescending { it.priorityTier }

        return enrichedPrimary + extraBackupChannels
    }

    /**
     * Finds a matching backup channel from `https://streamzone.fun/api/channels` (or built-in fallback)
     * when a channel stream encounters an error during playback.
     */
    fun findBackupChannelFor(channel: LiveChannel): LiveChannel? {
        val targetKey = normalizeChannelMatchKey(channel.name)
        val targetPath = extractStreamIdentityPath(channel.streamUrl)

        return cachedBackupApiChannels.firstOrNull { candidate ->
            candidate.id.equals(channel.id, ignoreCase = true) ||
                (targetKey.isNotEmpty() && normalizeChannelMatchKey(candidate.name) == targetKey) ||
                (targetPath.isNotEmpty() && extractStreamIdentityPath(candidate.streamUrl) == targetPath)
        } ?: channels.firstOrNull { builtIn ->
            builtIn.id.equals(channel.id, ignoreCase = true) ||
                (targetKey.isNotEmpty() && normalizeChannelMatchKey(builtIn.name) == targetKey)
        }
    }

    /**
     * Resolves the exact channel-specific token for an Azam CDN `/live/eds/<ChannelDir>/` stream path.
     * Some Azam TV channels embed their token in `/tok_<JWT>/` in the path, while others use `?cdntoken=<JWT>`.
     */
    fun resolveChannelSpecificToken(urlOrPath: String): String {
        val lower = urlOrPath.lowercase()
        return when {
            lower.contains("/azamsport1/") -> AZAM_SPORT_1_TOKEN
            lower.contains("/azamsport2/") -> AZAM_SPORT_2_TOKEN
            lower.contains("/azamsport3/") -> AZAM_SPORT_3_TOKEN
            lower.contains("/azamsport4/") -> AZAM_SPORT_4_TOKEN
            lower.contains("/azamsport5/") -> AZAM_SPORT_5_TOKEN
            lower.contains("/azamone/") -> AZAM_ONE_TOKEN
            lower.contains("/azamtwo/") -> AZAM_TWO_TOKEN
            lower.contains("/sinemazetu/") -> SINEMA_ZETU_TOKEN
            lower.contains("/utv/") -> UTV_TOKEN
            lower.contains("/zbc2/") -> AZAM_SECONDARY_CDN_TOKEN
            lower.contains("/zbc/") -> AZAM_SECONDARY_CDN_TOKEN
            lower.contains("/kixmovies/") -> AZAM_SECONDARY_CDN_TOKEN
            lower.contains("/crowntv/") -> AZAM_SECONDARY_CDN_TOKEN
            lower.contains("/wasafitv/") -> AZAM_SECONDARY_CDN_TOKEN
            else -> AZAM_CDN_TOKEN
        }
    }

    /**
     * Returns true if this Azam channel path uses an embedded `/tok_<JWT>/` path token as its primary format.
     */
    fun usesEmbeddedPathTokenByDefault(urlOrPath: String): Boolean {
        val lower = urlOrPath.lowercase()
        return lower.contains("/azamsport1/") ||
            lower.contains("/azamsport2/") ||
            lower.contains("/azamsport3/") ||
            lower.contains("/azamsport4/") ||
            lower.contains("/azamsport5/") ||
            lower.contains("/azamone/") ||
            lower.contains("/azamtwo/") ||
            lower.contains("/sinemazetu/") ||
            lower.contains("/utv/")
    }

    /**
     * Normalizes Azam TV & CDN stream URLs (`.mpd` manifests and `.mp4` / `.m4s` CDN streams/segments):
     * - Rewrites `https://cdnblncr.azamtvltd.co.tz` to [AZAM_CDN_HOST] (`https://cdnedgch2.azamtvltd.co.tz`)
     * - Preserves embedded `/tok_<JWT>/` path tokens for channels that embed their token inside the URL path
     * - Preserves channel-specific `?cdntoken=` query tokens for channels that use query-parameter tokens
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

        val isAzamCdn = basePath.contains("azamtvltd.co.tz", ignoreCase = true) ||
                basePath.contains("/live/eds/", ignoreCase = true)
        val hasCdnTokenParam = existingQuery.contains("cdntoken=", ignoreCase = true)
        val hasPathTokenPrefix = basePath.contains("/tok_", ignoreCase = true)
        val isCdnMp4OrMpd = isAzamCdn ||
                hasCdnTokenParam ||
                hasPathTokenPrefix ||
                (basePath.endsWith(".mpd", ignoreCase = true) && isAzamCdn) ||
                (basePath.endsWith(".mp4", ignoreCase = true) && isAzamCdn)

        if (!isCdnMp4OrMpd) {
            return rewrittenHostUrl
        }

        val pathToken = if (hasPathTokenPrefix) {
            basePath.substringAfter("/tok_").substringBefore("/").trim()
                .replace("%3D", "=", ignoreCase = true)
        } else {
            ""
        }

        val embeddedTokenParam = existingQuery
            .split("&")
            .firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()

        val effectiveToken = when {
            embeddedTokenParam.isNotEmpty() && !isSupersededOrRevokedToken(embeddedTokenParam) -> embeddedTokenParam
            else -> AZAM_CDN_TOKEN
        }

        val otherParams = existingQuery
            .split("&")
            .filter { it.isNotBlank() && !it.startsWith("cdntoken=", ignoreCase = true) }

        val finalQuery = (otherParams + "cdntoken=$effectiveToken").joinToString("&")
        return "$basePath?$finalQuery"
    }

    fun getChannelById(id: String): LiveChannel? =
        NeliAdminManager.customAddedChannels.value.find { it.id == id }
            ?: _liveChannelsFlow.value.find { it.id == id }
            ?: cachedBackupApiChannels.find { it.id == id }
            ?: channels.find { it.id == id }

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
                    channel.channelTag.contains(trimmed, ignoreCase = true) ||
                    channel.country.contains(trimmed, ignoreCase = true)

            matchesCategory && matchesName && channel.enabled && channel.published && !NeliAdminManager.isChannelHidden(channel.id)
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
