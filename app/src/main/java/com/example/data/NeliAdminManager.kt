package com.example.data

import android.content.Context
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.notifications.NeliNotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AdminBannerPlacement(val key: String, val labelSwahili: String) {
    ABOVE_SLIDER("ABOVE_SLIDER", "Juu ya Slider"),
    BELOW_SLIDER("BELOW_SLIDER", "Chini ya Slider");

    companion object {
        fun fromKey(key: String?): AdminBannerPlacement {
            return entries.find { it.key.equals(key, ignoreCase = true) } ?: BELOW_SLIDER
        }
    }
}

data class AdminBroadcastMessage(
    val id: String,
    val message: String,
    val createdAtMs: Long,
    val sendAsPushNotification: Boolean = true,
    val placement: AdminBannerPlacement = AdminBannerPlacement.BELOW_SLIDER
)

/**
 * Manages the Mini Admin Panel capabilities:
 * 1. Strict Admin Authentication check (`Admin@login.com` / `123456`).
 *    The Admin Panel is ONLY visible in Account when logged in with `Admin@login.com` and `123456`.
 * 2. Adding new Live TV channels (`addChannelByAdmin`) and removing custom channels.
 * 3. Locking / Unlocking channels for Free Users (`lockAllChannels`, `unlockAllChannels`, `setChannelLocked`):
 *    - By default, all channels are unlocked (free).
 *    - When Admin locks a single channel or all channels, Free Users cannot watch locked channels until they subscribe.
 *    - For verified Premium Members, all channels remain OPEN even if Admin locks them, until their package expires.
 * 4. Writing, adding & deleting Admin notifications (`publishAdminSms`, `clearAdminSms`) that sit strictly
 *    either ABOVE the Slider (`Juu ya Slider`) or BELOW the Slider (`Chini ya Slider`) on the Home page only.
 */
object NeliAdminManager {

    const val ADMIN_EMAIL = "Admin@login.com"
    const val ADMIN_PASSWORD = "123456"

    private const val PREFS_NAME = "neli_mini_admin_prefs"
    private const val KEY_LOCK_ALL_CHANNELS = "lock_all_channels"
    private const val KEY_LOCKED_CHANNEL_IDS = "locked_channel_ids"
    private const val KEY_HIDDEN_CHANNEL_IDS = "hidden_channel_ids"
    private const val KEY_CUSTOM_CHANNELS_JSON = "custom_channels_json"
    private const val KEY_ADMIN_SMS_TEXT = "admin_sms_text"
    private const val KEY_ADMIN_SMS_ID = "admin_sms_id"
    private const val KEY_ADMIN_SMS_TIME = "admin_sms_time"
    private const val KEY_ADMIN_SMS_PLACEMENT = "admin_sms_placement"

    private val _areAllChannelsLocked = MutableStateFlow(false)
    val areAllChannelsLocked: StateFlow<Boolean> = _areAllChannelsLocked.asStateFlow()

    private val _areAllAzamChannelsLocked = MutableStateFlow(false)
    val areAllAzamChannelsLocked: StateFlow<Boolean> = _areAllAzamChannelsLocked.asStateFlow()

    private val _isAdminSessionActive = MutableStateFlow(false)
    val isAdminSessionActive: StateFlow<Boolean> = _isAdminSessionActive.asStateFlow()

    fun setAdminSessionActive(context: Context? = null, active: Boolean) {
        _isAdminSessionActive.value = active
    }

    fun isAzamChannel(channel: LiveChannel?): Boolean {
        return channel?.isAzamPriority == true
    }

    fun getChannelMatchKeys(channelId: String): Set<String> {
        val channel = ChannelRepository.liveChannelsFlow.value.firstOrNull { it.id == channelId }
        val keys = mutableSetOf(channelId)
        if (channel != null) {
            keys.add(channel.name.lowercase().replace(" ", "_"))
        }
        return keys
    }

    private val _lockedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedChannelIds: StateFlow<Set<String>> = _lockedChannelIds.asStateFlow()

    private val _hiddenChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenChannelIds: StateFlow<Set<String>> = _hiddenChannelIds.asStateFlow()

    private val _customAddedChannels = MutableStateFlow<List<LiveChannel>>(emptyList())
    val customAddedChannels: StateFlow<List<LiveChannel>> = _customAddedChannels.asStateFlow()

    private val _activeAdminSms = MutableStateFlow<AdminBroadcastMessage?>(null)
    val activeAdminSms: StateFlow<AdminBroadcastMessage?> = _activeAdminSms.asStateFlow()

    private val _adminBannerPlacement = MutableStateFlow(AdminBannerPlacement.BELOW_SLIDER)
    val adminBannerPlacement: StateFlow<AdminBannerPlacement> = _adminBannerPlacement.asStateFlow()

    fun isAdminCredentials(email: String, password: String): Boolean {
        return email.trim().equals(ADMIN_EMAIL, ignoreCase = true) && password.trim() == ADMIN_PASSWORD
    }

    fun isAdminEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val e = email.trim().lowercase()
        return e == ADMIN_EMAIL.lowercase() ||
            e == "baynithcinema@gmail.com" ||
            e == "baynithromanapp@gmail.com" ||
            e == "admin@neliplay.tz" ||
            e == "admin@neliplay.com"
    }

    fun isAdminUser(user: UserAccountEntity?): Boolean {
        return user != null && user.isLoggedIn && isAdminEmail(user.email)
    }

    val DEFAULT_LOCKED_CHANNEL_IDS = setOf(
        "R17JUvbCEzu2eTbjnE74", // Azam Sports 1 HD (Zakulipia)
        "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f", // Azam Sports 2 HD (Zakulipia)
        "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // Azam Two (Zakulipia)
        "bein_sport_live", // Bein Sport
        "b_sport_live", // B Sport
        "b_esports_live", // B Esports
        "sky_sports_live", // Sky sports
        "ziggo_sport_4_live", // ziggo sport 4
        "sk_5_hd_live", // sk 5 hd
        "sk_3_hd_live", // sk 3 hd
        "sk_6_hd_live", // sk 6 hd
        "4f36f2d1-ff7f-467b-b4f2-6f303263f28b", // KIX (Action Movies)
        "sport_tv_1_europe", // Sport tv.1
        "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c", // TNT SPORTS 1 HD
        "ba2b232c-a79d-4130-b5d8-4883fbce7a1d", // TNT SPORTS 2 HD
        "mbc_2_channel", // MBC 2
        "sony_movie_action_channel", // Sony Movie Action (Sony Movie One)
        "comedy_channel", // Comedy
        "zee_cinema_channel", // Zee Cinema
        "movie_box_channel", // Movie Box
        "movie_room_channel" // Movie Room
    )

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _areAllChannelsLocked.value = prefs.getBoolean(KEY_LOCK_ALL_CHANNELS, false)
        val existingSaved = prefs.getStringSet(KEY_LOCKED_CHANNEL_IDS, emptySet())?.toSet() ?: emptySet()
        val savedLocked = (existingSaved + DEFAULT_LOCKED_CHANNEL_IDS)
        _lockedChannelIds.value = savedLocked
        prefs.edit().putStringSet(KEY_LOCKED_CHANNEL_IDS, savedLocked).apply()
        val savedHidden = prefs.getStringSet(KEY_HIDDEN_CHANNEL_IDS, emptySet())?.toSet() ?: emptySet()
        _hiddenChannelIds.value = savedHidden

        val customJson = prefs.getString(KEY_CUSTOM_CHANNELS_JSON, "").orEmpty()
        val parsedCustom = parseCustomChannelsJson(customJson)
        _customAddedChannels.value = parsedCustom

        val savedPlacement = AdminBannerPlacement.fromKey(prefs.getString(KEY_ADMIN_SMS_PLACEMENT, AdminBannerPlacement.BELOW_SLIDER.key))
        _adminBannerPlacement.value = savedPlacement

        val smsText = prefs.getString(KEY_ADMIN_SMS_TEXT, "").orEmpty().trim()
        if (smsText.isNotBlank()) {
            _activeAdminSms.value = AdminBroadcastMessage(
                id = prefs.getString(KEY_ADMIN_SMS_ID, "sms_default").orEmpty(),
                message = smsText,
                createdAtMs = prefs.getLong(KEY_ADMIN_SMS_TIME, System.currentTimeMillis()),
                placement = savedPlacement
            )
        } else {
            _activeAdminSms.value = null
        }

        if (parsedCustom.isNotEmpty() || savedHidden.isNotEmpty()) {
            ChannelRepository.refreshLiveChannels()
        }

        NeliFirebaseSyncManager.initialize(context)
    }

    fun applyCloudChannelLocks(
        context: Context,
        lockAll: Boolean,
        lockedIds: Set<String>,
        hiddenIds: Set<String>
    ) {
        _areAllChannelsLocked.value = lockAll
        _lockedChannelIds.value = lockedIds
        _hiddenChannelIds.value = hiddenIds
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOCK_ALL_CHANNELS, lockAll)
            .putStringSet(KEY_LOCKED_CHANNEL_IDS, lockedIds)
            .putStringSet(KEY_HIDDEN_CHANNEL_IDS, hiddenIds)
            .apply()
        ChannelRepository.refreshLiveChannels()
    }

    fun mergeCloudLockedChannels(context: Context, newLockedIds: Set<String>) {
        val merged = _lockedChannelIds.value.toMutableSet().apply { addAll(newLockedIds) }
        _lockedChannelIds.value = merged
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_LOCKED_CHANNEL_IDS, merged).apply()
    }

    fun applyCloudAdminNotification(
        context: Context,
        id: String,
        message: String,
        placement: AdminBannerPlacement,
        sendPushNotification: Boolean,
        createdAtMs: Long
    ) {
        val currentId = _activeAdminSms.value?.id
        if (currentId == id && _activeAdminSms.value?.message == message) return
        val msg = AdminBroadcastMessage(
            id = id,
            message = message,
            createdAtMs = createdAtMs,
            sendAsPushNotification = sendPushNotification,
            placement = placement
        )
        _adminBannerPlacement.value = placement
        _activeAdminSms.value = msg

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ADMIN_SMS_TEXT, message)
            .putString(KEY_ADMIN_SMS_ID, id)
            .putLong(KEY_ADMIN_SMS_TIME, createdAtMs)
            .putString(KEY_ADMIN_SMS_PLACEMENT, placement.key)
            .apply()

        if (sendPushNotification) {
            NeliNotificationScheduler.sendAdminBroadcastNotification(context, message)
        }
    }

    fun isChannelExplicitlyFree(channel: LiveChannel): Boolean {
        val name = channel.name.trim().lowercase()
        val id = channel.id.trim()

        // 1. Azam One na Sinema Zetu ni bure
        if (name.contains("azam one") || id == "c405ae74-c4c5-4842-9f26-130ce380b307") return true
        if (name.contains("sinema zetu") || id == "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e") return true

        // 2. Azam Sport 3 and 4 ni bure kwa upande wa Azam TV
        if (name.contains("azam sport 3") || name.contains("azam sports 3") || id == "1c976127-e8a4-4bd6-8e73-5da0edce369b") return true
        if (name.contains("azam sport 4") || name.contains("azam sports 4") || id == "244bcd50-b3bf-4d5e-8419-08cc7bad1a7c") return true

        // 3. UTV ni bure na ZBC ni bure
        if (name.contains("utv") || id == "d7b415f7-d024-4c7f-a570-653367e8dc5c") return true
        if (name.contains("zbc") || id.contains("zbc")) return true

        // 4. Movie Korea, Now Global, Trace Latina, Movie Sphere ni bure
        if (name.contains("movie korea") || id == "movie_korea_channel") return true
        if (name.contains("now global") || id == "now_global_channel") return true
        if (name.contains("trace latina") || id == "trace_latina_channel") return true
        if (name.contains("movie sphere") || id == "movie_sphere_channel") return true

        // 5. Channel ya Tom and Jerry ni bure
        if (name.contains("tom and jerry") || name.contains("toma and jerry") || id == "tom_and_jerry_channel") return true

        // 6. Za Firebase ziache nazo ni bure (Pop kids, na nyingine zote za Firebase)
        if (id.startsWith("fb_") || id.startsWith("firebase_") || channel.categories.contains("firebase") ||
            name.contains("pop kids") || id == "pop_kids_channel" || id == "tv_1788953482934_twaqe"
        ) return true

        // WWE, Wasafi na Crown Tv
        if (name.contains("wwe") || id == "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2") return true
        if (name.contains("wasafi") || id == "wasafi_tv_live_stream_tz" || id == "80e54146-1d9b-4c91-8f71-de0ea4866833") return true
        if (name.contains("crown") || name.contains("clown") || id == "bba104f4-f5ac-41c9-aa36-7af71aaa1993") return true
        if (name.contains("sk moments") || id == "sk_moments_europe") return true

        return false
    }

    fun isChannelLockedByDefault(channel: LiveChannel): Boolean {
        if (isChannelExplicitlyFree(channel)) return false

        val name = channel.name.trim().lowercase()
        val id = channel.id.trim()

        // Azam TV zilizobaki ni zakulipia zote:
        // Azam Two ni zakulipia
        if (name.contains("azam two") || id == "008ffe6e-a30f-4ed1-9ddb-4033dde18576") return true
        // Azam Sport 1 na 2 ni zakulipia
        if (id == "R17JUvbCEzu2eTbjnE74" || id == "f74ba826-f031-4e64-9ec1-f7ffa4e6ec0f") return true
        if ((name.contains("azam sport") || name.contains("azam sports")) &&
            (name.contains("1") || name.contains("2"))) return true
        if (name.contains("kix") || id == "4f36f2d1-ff7f-467b-b4f2-6f303263f28b") return true

        // Sports channels locked until subscription:
        if (name.contains("bein sport") || id == "bein_sport_live") return true
        if (name.contains("b sport") || id == "b_sport_live") return true
        if (name.contains("b esports") || name.contains("b esport") || id == "b_esports_live") return true
        if (name.contains("sky sport") || name.contains("sky sports") || id == "sky_sports_live") return true
        if (name.contains("ziggo sport") || id == "ziggo_sport_4_live") return true
        if (name.contains("sk 5") || name.contains("sk5") || id == "sk_5_hd_live") return true
        if (name.contains("sk 3") || name.contains("sk3") || id == "sk_3_hd_live") return true
        if (name.contains("sk 6") || name.contains("sk6") || id == "sk_6_hd_live") return true

        // Za Europe: Sport tv.1 & TNT Sports ni premium zifunge
        if (name.contains("sport tv.1") || name.contains("sport tv 1") || id == "sport_tv_1_europe") return true
        if (name.contains("tnt sport") || name.contains("tnt sports") ||
            id == "ee2ba70e-c28b-40ce-8ba1-0c20f133d04c" || id == "ba2b232c-a79d-4130-b5d8-4883fbce7a1d") return true

        // Movie channels zilizobaki
        if (name.contains("mbc 2") || name.contains("mbc2") || id == "mbc_2_channel") return true
        if (name.contains("sony movie") || id == "sony_movie_action_channel") return true
        if (name.contains("comedy") || id == "comedy_channel") return true
        if (name.contains("zee cinema") || id == "zee_cinema_channel") return true
        if (name.contains("movie box") || id == "movie_box_channel") return true
        if (name.contains("movie room") || id == "movie_room_channel") return true

        return channel.isLockedDefault
    }

    /**
     * Checks whether the Admin has marked this channel as locked for Free Users.
     */
    fun isChannelLockedByAdmin(channelId: String, channelObj: LiveChannel? = null): Boolean {
        val channel = channelObj ?: ChannelRepository.getChannelById(channelId)
        if (channel != null && isChannelExplicitlyFree(channel)) return false
        if (_areAllChannelsLocked.value) return true

        if (_lockedChannelIds.value.contains(channelId)) return true
        if (DEFAULT_LOCKED_CHANNEL_IDS.contains(channelId)) return true

        if (channel != null) {
            if (channel.isLockedDefault) return true
            if (isChannelLockedByDefault(channel)) return true
            val nameMatch = channel.name.lowercase().trim().replace(" ", "_")
            if (_lockedChannelIds.value.contains(nameMatch) || DEFAULT_LOCKED_CHANNEL_IDS.contains(nameMatch)) return true
            val idMatch = channel.id.lowercase().trim()
            if (_lockedChannelIds.value.contains(idMatch) || DEFAULT_LOCKED_CHANNEL_IDS.contains(idMatch)) return true
        }
        return false
    }

    /**
     * Determines whether a channel is locked for the current user right now:
     * - Reads real-time subscription status from [NeliSubscriptionManager]:
     *   If the user already paid earlier and their subscription is still active (`nowMs < expiresAtMs`),
     *   NEVER locks the channel until the subscription expires.
     * - Free users without active subscription see locked channels with Lock icon / Pay to Watch.
     * - Admin User (`Admin@login.com` or `baynithcinema@gmail.com`): NEVER locked.
     */
    fun isChannelLockedForUser(
        channelId: String,
        channelObj: LiveChannel? = null,
        currentUser: UserAccountEntity? = null,
        isPremiumActive: Boolean = NeliSubscriptionManager.isPremiumMemberActive(),
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null
    ): Boolean {
        if (isAdminUser(currentUser)) return false
        if (NeliFreeForeverAccountsManager.isFreeForeverUser(currentUser)) return false
        if (NeliSubscriptionManager.subscriptionState.value.isFreeForeverAccount) return false

        val channel = channelObj ?: ChannelRepository.getChannelById(channelId)
        if (channel != null && isChannelExplicitlyFree(channel)) return false

        val hasActiveSubscription = isPremiumActive && NeliSubscriptionManager.isPremiumMemberActive(nowMs = nowMs, context = context)
        if (hasActiveSubscription) {
            return false
        }
        return isChannelLockedByAdmin(channelId, channel)
    }

    fun setLockAllChannels(context: Context, lockAll: Boolean) {
        // Re-evaluate active subscription state so users who already paid while channels were free stay unlocked
        NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allIds = if (lockAll) {
            ChannelRepository.liveChannelsFlow.value.map { it.id }.toSet()
        } else {
            emptySet()
        }
        prefs.edit()
            .putBoolean(KEY_LOCK_ALL_CHANNELS, lockAll)
            .putStringSet(KEY_LOCKED_CHANNEL_IDS, allIds)
            .apply()
        _areAllChannelsLocked.value = lockAll
        _lockedChannelIds.value = allIds
        NeliFirebaseSyncManager.pushChannelLockStateToFirebase(lockAll, allIds, _hiddenChannelIds.value)
    }

    fun setLockAllAzamChannels(context: Context, lockAllAzam: Boolean) {
        _areAllAzamChannelsLocked.value = lockAllAzam
        val azamIds = ChannelRepository.liveChannelsFlow.value.filter { it.isAzamPriority }.map { it.id }.toSet()
        val currentSet = _lockedChannelIds.value.toMutableSet()
        if (lockAllAzam) {
            currentSet.addAll(azamIds)
        } else {
            currentSet.removeAll(azamIds)
        }
        _lockedChannelIds.value = currentSet
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_LOCKED_CHANNEL_IDS, currentSet).apply()
        NeliFirebaseSyncManager.pushChannelLockStateToFirebase(_areAllChannelsLocked.value, currentSet, _hiddenChannelIds.value)
    }

    fun applyCloudState(
        context: Context,
        lockAll: Boolean,
        lockAllAzam: Boolean = false,
        lockedIds: Set<String>,
        hiddenIds: Set<String>,
        customChannels: List<LiveChannel> = emptyList(),
        smsId: String = "",
        smsText: String = "",
        smsCreatedAtMs: Long = 0L,
        smsPlacementKey: String = AdminBannerPlacement.BELOW_SLIDER.key
    ) {
        applyCloudChannelLocks(context, lockAll, lockedIds, hiddenIds)
        if (lockAllAzam) {
            setLockAllAzamChannels(context, true)
        }
        if (customChannels.isNotEmpty()) {
            _customAddedChannels.value = customChannels
        }
        if (smsText.isNotBlank()) {
            val placement = AdminBannerPlacement.entries.find { it.key == smsPlacementKey }
                ?: AdminBannerPlacement.BELOW_SLIDER
            applyCloudAdminNotification(context, smsId, smsText, placement, false, smsCreatedAtMs)
        }
    }

    fun toggleSingleChannelLock(context: Context, channelId: String) {
        NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
        val currentSet = _lockedChannelIds.value.toMutableSet()
        val currentlyLocked = _areAllChannelsLocked.value || currentSet.contains(channelId)
        if (_areAllChannelsLocked.value) {
            // Populate all current channel IDs first, then remove this one
            currentSet.addAll(ChannelRepository.liveChannelsFlow.value.map { it.id })
            currentSet.remove(channelId)
            _areAllChannelsLocked.value = false
        } else if (currentlyLocked) {
            currentSet.remove(channelId)
        } else {
            currentSet.add(channelId)
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOCK_ALL_CHANNELS, _areAllChannelsLocked.value)
            .putStringSet(KEY_LOCKED_CHANNEL_IDS, currentSet)
            .apply()
        _lockedChannelIds.value = currentSet.toSet()
        NeliFirebaseSyncManager.pushChannelLockStateToFirebase(_areAllChannelsLocked.value, currentSet, _hiddenChannelIds.value)
    }

    fun setSingleChannelLock(context: Context, channelId: String, locked: Boolean) {
        NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
        val currentSet = _lockedChannelIds.value.toMutableSet()
        if (!locked && _areAllChannelsLocked.value) {
            currentSet.addAll(ChannelRepository.liveChannelsFlow.value.map { it.id })
            currentSet.remove(channelId)
            _areAllChannelsLocked.value = false
        } else if (locked) {
            currentSet.add(channelId)
        } else {
            currentSet.remove(channelId)
        }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_LOCK_ALL_CHANNELS, _areAllChannelsLocked.value)
            .putStringSet(KEY_LOCKED_CHANNEL_IDS, currentSet)
            .apply()
        _lockedChannelIds.value = currentSet.toSet()
        NeliFirebaseSyncManager.pushChannelLockStateToFirebase(_areAllChannelsLocked.value, currentSet, _hiddenChannelIds.value)
    }

    /**
     * Adds a new Live TV channel from the Mini Admin Panel and immediately publishes it to [ChannelRepository].
     */
    fun addChannelByAdmin(
        context: Context,
        name: String,
        streamUrl: String,
        thumbnailUrl: String,
        category: String = "Entertainment",
        description: String = "Live TV HD",
        lockForFreeUsers: Boolean = false
    ): Result<LiveChannel> {
        val cleanName = name.trim()
        val cleanStream = streamUrl.trim()
        val cleanThumb = thumbnailUrl.trim().ifBlank {
            "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png"
        }
        val cleanCategory = category.trim().ifBlank { "Entertainment" }
        val cleanDesc = description.trim().ifBlank { "Watch $cleanName Live HD" }

        if (cleanName.isBlank()) {
            return Result.failure(IllegalArgumentException("Tafadhali andika jina la Channel."))
        }
        if (cleanStream.isBlank() || (!cleanStream.startsWith("http://", true) && !cleanStream.startsWith("https://", true))) {
            return Result.failure(IllegalArgumentException("Tafadhali weka Stream URL sahihi inayoanza na https:// au http://"))
        }

        val format = when {
            cleanStream.contains(".mpd", ignoreCase = true) || cleanStream.contains("/dash/", ignoreCase = true) -> "dash"
            cleanStream.contains(".mp4", ignoreCase = true) -> "mp4"
            else -> "hls"
        }

        val newChannel = LiveChannel(
            id = "admin_ch_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            name = cleanName,
            description = cleanDesc,
            streamUrl = cleanStream,
            streamFormat = format,
            thumbnailUrl = cleanThumb,
            categories = listOf(cleanCategory.lowercase(), "tanzania"),
            language = "sw",
            encryptionType = "none",
            country = "Tanzania",
            featured = true,
            enabled = true,
            published = true
        )

        val updatedList = listOf(newChannel) + _customAddedChannels.value
        _customAddedChannels.value = updatedList
        saveCustomChannels(context, updatedList)

        if (lockForFreeUsers) {
            setSingleChannelLock(context, newChannel.id, true)
        }

        ChannelRepository.refreshLiveChannels()
        return Result.success(newChannel)
    }

    /**
     * Checks whether the Admin has hidden this channel from the viewer catalog.
     * Note: Channels can NEVER be deleted (even newly added channels); they can only be hidden or unhidden.
     */
    fun isChannelHidden(channelId: String): Boolean {
        if (ChannelRepository.isExcludedChannel("", channelId)) return true
        return _hiddenChannelIds.value.contains(channelId)
    }

    fun setChannelHidden(context: Context, channelId: String, hidden: Boolean) {
        val currentSet = _hiddenChannelIds.value.toMutableSet()
        if (hidden) {
            currentSet.add(channelId)
        } else {
            currentSet.remove(channelId)
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet(KEY_HIDDEN_CHANNEL_IDS, currentSet)
            .apply()
        _hiddenChannelIds.value = currentSet.toSet()
        ChannelRepository.refreshLiveChannels()
        NeliFirebaseSyncManager.pushChannelLockStateToFirebase(_areAllChannelsLocked.value, _lockedChannelIds.value, currentSet)
    }

    fun toggleChannelHidden(context: Context, channelId: String): Boolean {
        val nowHidden = !isChannelHidden(channelId)
        setChannelHidden(context, channelId, nowHidden)
        return nowHidden
    }

    /**
     * Channels cannot be deleted in Admin (even newly added channels).
     * Calling this hides the channel instead of deleting it.
     */
    fun removeAdminChannel(context: Context, channelId: String) {
        setChannelHidden(context, channelId, true)
    }

    fun setAdminBannerPlacement(context: Context, placement: AdminBannerPlacement) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ADMIN_SMS_PLACEMENT, placement.key).apply()
        _adminBannerPlacement.value = placement
        _activeAdminSms.value = _activeAdminSms.value?.copy(placement = placement)
    }

    /**
     * Publishes an SMS / Announcement message from the Mini Admin Panel:
     * 1. Displays it strictly Above or Below the Hero Slider on the Home page only.
     * 2. Sends it as an Android system notification when [sendPushNotification] is true.
     */
    fun publishAdminSms(
        context: Context,
        messageText: String,
        sendPushNotification: Boolean = true,
        placement: AdminBannerPlacement = _adminBannerPlacement.value
    ): Result<AdminBroadcastMessage> {
        val cleanText = messageText.trim()
        if (cleanText.isBlank()) {
            return Result.failure(IllegalArgumentException("Tafadhali andika ujumbe wa SMS kwanza."))
        }

        _adminBannerPlacement.value = placement
        val msg = AdminBroadcastMessage(
            id = "sms_${System.currentTimeMillis()}",
            message = cleanText,
            createdAtMs = System.currentTimeMillis(),
            sendAsPushNotification = sendPushNotification,
            placement = placement
        )
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_ADMIN_SMS_TEXT, msg.message)
            .putString(KEY_ADMIN_SMS_ID, msg.id)
            .putLong(KEY_ADMIN_SMS_TIME, msg.createdAtMs)
            .putString(KEY_ADMIN_SMS_PLACEMENT, placement.key)
            .apply()

        _activeAdminSms.value = msg

        NeliFirebaseSyncManager.pushAdminNotificationToFirebase(cleanText, placement, sendPushNotification)

        if (sendPushNotification) {
            NeliNotificationScheduler.sendAdminBroadcastNotification(context, cleanText)
        }

        return Result.success(msg)
    }

    fun clearAdminSms(context: Context, notifyCloud: Boolean = true) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ADMIN_SMS_TEXT)
            .remove(KEY_ADMIN_SMS_ID)
            .remove(KEY_ADMIN_SMS_TIME)
            .apply()
        _activeAdminSms.value = null
        if (notifyCloud) {
            NeliFirebaseSyncManager.clearAdminNotificationFromFirebase()
        }
    }

    private fun saveCustomChannels(context: Context, channels: List<LiveChannel>) {
        try {
            val arr = JSONArray()
            channels.forEach { ch ->
                val obj = JSONObject().apply {
                    put("id", ch.id)
                    put("name", ch.name)
                    put("description", ch.description)
                    put("streamUrl", ch.streamUrl)
                    put("streamFormat", ch.streamFormat)
                    put("thumbnailUrl", ch.thumbnailUrl)
                    put("category", ch.categories.firstOrNull() ?: "entertainment")
                }
                arr.put(obj)
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CUSTOM_CHANNELS_JSON, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun parseCustomChannelsJson(rawJson: String): List<LiveChannel> {
        if (rawJson.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<LiveChannel>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").ifBlank { continue }
                val name = obj.optString("name", "").ifBlank { continue }
                val streamUrl = obj.optString("streamUrl", "").ifBlank { continue }
                val desc = obj.optString("description", "Live TV HD")
                val format = obj.optString("streamFormat", "hls")
                val thumb = obj.optString("thumbnailUrl", "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png")
                val cat = obj.optString("category", "entertainment")
                list.add(
                    LiveChannel(
                        id = id,
                        name = name,
                        description = desc,
                        streamUrl = streamUrl,
                        streamFormat = format,
                        thumbnailUrl = thumb,
                        categories = listOf(cat.lowercase(), "tanzania"),
                        language = "sw",
                        encryptionType = "none",
                        country = "Tanzania",
                        featured = true,
                        enabled = true,
                        published = true
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun resetForTesting(context: Context? = null) {
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.commit()
        _areAllChannelsLocked.value = false
        _lockedChannelIds.value = emptySet()
        _hiddenChannelIds.value = emptySet()
        _customAddedChannels.value = emptyList()
        _activeAdminSms.value = null
    }
}
