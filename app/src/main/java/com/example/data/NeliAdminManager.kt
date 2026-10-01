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
    private const val KEY_CUSTOM_CHANNELS_JSON = "custom_channels_json"
    private const val KEY_ADMIN_SMS_TEXT = "admin_sms_text"
    private const val KEY_ADMIN_SMS_ID = "admin_sms_id"
    private const val KEY_ADMIN_SMS_TIME = "admin_sms_time"
    private const val KEY_ADMIN_SMS_PLACEMENT = "admin_sms_placement"

    private val _areAllChannelsLocked = MutableStateFlow(false)
    val areAllChannelsLocked: StateFlow<Boolean> = _areAllChannelsLocked.asStateFlow()

    private val _lockedChannelIds = MutableStateFlow<Set<String>>(emptySet())
    val lockedChannelIds: StateFlow<Set<String>> = _lockedChannelIds.asStateFlow()

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
        return !email.isNullOrBlank() && email.trim().equals(ADMIN_EMAIL, ignoreCase = true)
    }

    fun isAdminUser(user: UserAccountEntity?): Boolean {
        return user != null && user.isLoggedIn && isAdminEmail(user.email)
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _areAllChannelsLocked.value = prefs.getBoolean(KEY_LOCK_ALL_CHANNELS, false)
        val savedLocked = prefs.getStringSet(KEY_LOCKED_CHANNEL_IDS, emptySet())?.toSet() ?: emptySet()
        _lockedChannelIds.value = savedLocked

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

        if (parsedCustom.isNotEmpty()) {
            ChannelRepository.refreshLiveChannels()
        }
    }

    /**
     * Checks whether the Admin has marked this channel as locked for Free Users.
     */
    fun isChannelLockedByAdmin(channelId: String): Boolean {
        return _areAllChannelsLocked.value || _lockedChannelIds.value.contains(channelId)
    }

    /**
     * Determines whether a channel is locked for the current user right now:
     * - Reads real-time subscription status from [NeliSubscriptionManager]:
     *   If the user already paid earlier (even when channels were free) and their subscription is still
     *   active (`nowMs < expiresAtMs`), NEVER locks the channel (returns `false`) until the subscription expires!
     * - If the subscription has expired (`nowMs >= expiresAtMs`) or the user has not paid, returns `true`
     *   when the channel is locked by Admin, and immediately returns `false` as soon as payment succeeds.
     * - Admin User (`Admin@login.com`): NEVER locked (returns `false`).
     */
    fun isChannelLockedForUser(
        channelId: String,
        currentUser: UserAccountEntity? = null,
        isPremiumActive: Boolean = NeliSubscriptionManager.isPremiumMemberActive(),
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null
    ): Boolean {
        val activeSubscriptionNow = isPremiumActive && NeliSubscriptionManager.isPremiumMemberActive(nowMs = nowMs, context = context)
        if (activeSubscriptionNow || (!isPremiumActive && NeliSubscriptionManager.isPremiumMemberActive(nowMs = nowMs, context = context))) {
            return false
        }
        if (isAdminUser(currentUser)) return false
        return isChannelLockedByAdmin(channelId)
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

    fun removeAdminChannel(context: Context, channelId: String) {
        val updatedList = _customAddedChannels.value.filterNot { it.id == channelId }
        _customAddedChannels.value = updatedList
        saveCustomChannels(context, updatedList)
        setSingleChannelLock(context, channelId, false)
        ChannelRepository.refreshLiveChannels()
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

        if (sendPushNotification) {
            NeliNotificationScheduler.sendAdminBroadcastNotification(context, cleanText)
        }

        return Result.success(msg)
    }

    fun clearAdminSms(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_ADMIN_SMS_TEXT)
            .remove(KEY_ADMIN_SMS_ID)
            .remove(KEY_ADMIN_SMS_TIME)
            .apply()
        _activeAdminSms.value = null
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
        _customAddedChannels.value = emptyList()
        _activeAdminSms.value = null
    }
}
