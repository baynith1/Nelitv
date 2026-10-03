package com.example.data

import android.content.Context
import com.example.model.LiveChannel
import com.example.notifications.NeliNotificationScheduler
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Firebase Realtime Database Global State Manager:
 * 1. Global channel lock/availability manager (`/global_channel_state`):
 *    - Allows the 'admin' account to toggle channel locks, lock all channels, hide/unhide channels,
 *      add custom channels, and broadcast announcements.
 *    - Propagates instantly to all client apps (existing & brand-new first-time installs) in real-time.
 *    - Azam TV channels stay locally in code (`ChannelRepository.channels`) and are NOT uploaded to Firebase.
 * 2. User account & subscription cloud sync (`/users/{sanitizedEmail}/subscription`):
 *    - Syncs premium status and payment records across all devices linked with the user's account.
 *    - Restores premium membership even if the user clears app cache or logs in on a new device.
 * 3. Global broadcast notifications (`/global_channel_state/broadcast_sms`):
 *    - Admin announcements broadcast to all client apps and trigger local Android notifications.
 */
object FirebaseGlobalManager {

    const val RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"
    private const val NODE_GLOBAL_STATE = "global_channel_state"
    private const val NODE_USERS = "users"
    private const val PREFS_NOTIFIED = "neli_notified_sms_prefs"
    private const val KEY_LAST_NOTIFIED_SMS_ID = "last_notified_sms_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false
    private var appContext: Context? = null

    private fun getDatabase(): FirebaseDatabase {
        return try {
            FirebaseDatabase.getInstance(RTDB_URL)
        } catch (_: Throwable) {
            FirebaseDatabase.getInstance()
        }
    }

    fun sanitizeEmailKey(email: String): String {
        return email.trim().lowercase()
            .replace(".", "_")
            .replace("@", "_at_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
            .replace("/", "_")
            .ifBlank { "anonymous_user" }
    }

    /**
     * Initializes real-time listener for global channel availability, admin announcements,
     * and custom channels on all client devices.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (isListening) return
        isListening = true

        // 1. Initial fast REST sync (guarantees immediate state even on fresh install before WebSocket handshake)
        scope.launch {
            fetchGlobalStateViaRest(context.applicationContext)
        }

        // 2. Persistent Firebase Realtime Database listener
        try {
            val db = getDatabase()
            val stateRef = db.getReference(NODE_GLOBAL_STATE)
            stateRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    processGlobalStateSnapshot(context.applicationContext, snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Fallback to REST on error
                    scope.launch {
                        fetchGlobalStateViaRest(context.applicationContext)
                    }
                }
            })
        } catch (_: Throwable) {
            scope.launch {
                fetchGlobalStateViaRest(context.applicationContext)
            }
        }
    }

    private fun processGlobalStateSnapshot(context: Context, snapshot: DataSnapshot) {
        try {
            val lockAll = snapshot.child("lock_all_channels").getValue(Boolean::class.java) ?: false

            val lockedMap = mutableSetOf<String>()
            snapshot.child("locked_channel_ids").children.forEach { child ->
                val locked = child.getValue(Boolean::class.java) ?: false
                val chId = child.key.orEmpty()
                if (locked && chId.isNotBlank()) {
                    lockedMap.add(chId)
                }
            }

            val hiddenMap = mutableSetOf<String>()
            snapshot.child("hidden_channel_ids").children.forEach { child ->
                val hidden = child.getValue(Boolean::class.java) ?: false
                val chId = child.key.orEmpty()
                if (hidden && chId.isNotBlank()) {
                    hiddenMap.add(chId)
                }
            }

            val customList = mutableListOf<LiveChannel>()
            snapshot.child("custom_channels").children.forEach { child ->
                val id = child.child("id").getValue(String::class.java) ?: child.key.orEmpty()
                val name = child.child("name").getValue(String::class.java).orEmpty()
                val streamUrl = child.child("streamUrl").getValue(String::class.java).orEmpty()
                val desc = child.child("description").getValue(String::class.java) ?: "Live TV HD"
                val thumb = child.child("thumbnailUrl").getValue(String::class.java) ?: "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png"
                val cat = child.child("category").getValue(String::class.java) ?: "entertainment"
                val format = child.child("streamFormat").getValue(String::class.java) ?: "hls"

                if (id.isNotBlank() && name.isNotBlank() && streamUrl.isNotBlank()) {
                    customList.add(
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
            }

            // Broadcast SMS announcement
            val smsSnap = snapshot.child("broadcast_sms")
            val smsId = smsSnap.child("id").getValue(String::class.java).orEmpty()
            val smsMessage = smsSnap.child("message").getValue(String::class.java).orEmpty()
            val smsCreatedAt = smsSnap.child("createdAtMs").getValue(Long::class.java) ?: 0L
            val smsPlacementKey = smsSnap.child("placement").getValue(String::class.java) ?: AdminBannerPlacement.BELOW_SLIDER.key
            val sendPush = smsSnap.child("sendPushNotification").getValue(Boolean::class.java) ?: true

            NeliAdminManager.applyCloudState(
                context = context,
                lockAll = lockAll,
                lockedIds = lockedMap,
                hiddenIds = hiddenMap,
                customChannels = customList,
                smsId = smsId,
                smsText = smsMessage,
                smsCreatedAtMs = smsCreatedAt,
                smsPlacementKey = smsPlacementKey
            )

            // Trigger local push notification on this device if not yet notified for this smsId
            if (smsMessage.isNotBlank() && smsId.isNotBlank() && sendPush) {
                checkAndTriggerDeviceNotification(context, smsId, smsMessage)
            }
        } catch (_: Throwable) {
        }
    }

    private suspend fun fetchGlobalStateViaRest(context: Context) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$RTDB_URL/$NODE_GLOBAL_STATE.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                if (jsonStr.isNotBlank() && jsonStr != "null") {
                    val root = JSONObject(jsonStr)
                    val lockAll = root.optBoolean("lock_all_channels", false)

                    val lockedIds = mutableSetOf<String>()
                    val lockedObj = root.optJSONObject("locked_channel_ids")
                    if (lockedObj != null) {
                        for (k in lockedObj.keys()) {
                            if (lockedObj.optBoolean(k, false)) lockedIds.add(k)
                        }
                    }

                    val hiddenIds = mutableSetOf<String>()
                    val hiddenObj = root.optJSONObject("hidden_channel_ids")
                    if (hiddenObj != null) {
                        for (k in hiddenObj.keys()) {
                            if (hiddenObj.optBoolean(k, false)) hiddenIds.add(k)
                        }
                    }

                    val customList = mutableListOf<LiveChannel>()
                    val customObj = root.optJSONObject("custom_channels")
                    if (customObj != null) {
                        for (k in customObj.keys()) {
                            val chObj = customObj.optJSONObject(k) ?: continue
                            val name = chObj.optString("name", "")
                            val streamUrl = chObj.optString("streamUrl", "")
                            if (name.isNotBlank() && streamUrl.isNotBlank()) {
                                customList.add(
                                    LiveChannel(
                                        id = chObj.optString("id", k),
                                        name = name,
                                        description = chObj.optString("description", "Live TV HD"),
                                        streamUrl = streamUrl,
                                        streamFormat = chObj.optString("streamFormat", "hls"),
                                        thumbnailUrl = chObj.optString("thumbnailUrl", "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png"),
                                        categories = listOf(chObj.optString("category", "entertainment").lowercase(), "tanzania"),
                                        language = "sw",
                                        encryptionType = "none",
                                        country = "Tanzania",
                                        featured = true,
                                        enabled = true,
                                        published = true
                                    )
                                )
                            }
                        }
                    }

                    val smsObj = root.optJSONObject("broadcast_sms")
                    val smsId = smsObj?.optString("id", "").orEmpty()
                    val smsMessage = smsObj?.optString("message", "").orEmpty()
                    val smsTime = smsObj?.optLong("createdAtMs", 0L) ?: 0L
                    val smsPlacement = smsObj?.optString("placement", AdminBannerPlacement.BELOW_SLIDER.key).orEmpty()
                    val sendPush = smsObj?.optBoolean("sendPushNotification", true) ?: true

                    withContext(Dispatchers.Main) {
                        NeliAdminManager.applyCloudState(
                            context = context,
                            lockAll = lockAll,
                            lockedIds = lockedIds,
                            hiddenIds = hiddenIds,
                            customChannels = customList,
                            smsId = smsId,
                            smsText = smsMessage,
                            smsCreatedAtMs = smsTime,
                            smsPlacementKey = smsPlacement
                        )
                    }

                    if (smsMessage.isNotBlank() && smsId.isNotBlank() && sendPush) {
                        checkAndTriggerDeviceNotification(context, smsId, smsMessage)
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun checkAndTriggerDeviceNotification(context: Context, smsId: String, message: String) {
        val prefs = context.getSharedPreferences(PREFS_NOTIFIED, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_SMS_ID, "").orEmpty()
        if (lastNotified != smsId) {
            prefs.edit().putString(KEY_LAST_NOTIFIED_SMS_ID, smsId).apply()
            NeliNotificationScheduler.sendAdminBroadcastNotification(context, message)
        }
    }

    // =========================================================================
    // ADMIN WRITE OPERATIONS TO FIREBASE
    // =========================================================================

    fun syncLockAllChannelsToCloud(lockAll: Boolean) {
        scope.launch {
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("lock_all_channels")
                    .setValue(lockAll)
            } catch (_: Throwable) {
            }
            writeRestValue("$NODE_GLOBAL_STATE/lock_all_channels", lockAll)
        }
    }

    fun syncSingleChannelLockToCloud(channelId: String, locked: Boolean) {
        if (channelId.isBlank()) return
        scope.launch {
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("locked_channel_ids")
                    .child(channelId)
                    .setValue(locked)
            } catch (_: Throwable) {
            }
            writeRestValue("$NODE_GLOBAL_STATE/locked_channel_ids/$channelId", locked)
        }
    }

    fun syncSingleChannelHiddenToCloud(channelId: String, hidden: Boolean) {
        if (channelId.isBlank()) return
        scope.launch {
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("hidden_channel_ids")
                    .child(channelId)
                    .setValue(hidden)
            } catch (_: Throwable) {
            }
            writeRestValue("$NODE_GLOBAL_STATE/hidden_channel_ids/$channelId", hidden)
        }
    }

    fun syncCustomChannelToCloud(channel: LiveChannel) {
        // Only custom channels added by admin are synced here. Azam TV channels stay locally in code.
        if (channel.id.isBlank() || channel.isAzamTvChannel) return
        scope.launch {
            val payload = mapOf(
                "id" to channel.id,
                "name" to channel.name,
                "description" to channel.description,
                "streamUrl" to channel.streamUrl,
                "streamFormat" to channel.streamFormat,
                "thumbnailUrl" to channel.thumbnailUrl,
                "category" to (channel.categories.firstOrNull() ?: "entertainment"),
                "createdAtMs" to System.currentTimeMillis()
            )
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("custom_channels")
                    .child(channel.id)
                    .setValue(payload)
            } catch (_: Throwable) {
            }
            writeRestObject("$NODE_GLOBAL_STATE/custom_channels/${channel.id}", JSONObject(payload))
        }
    }

    fun syncBroadcastSmsToCloud(sms: AdminBroadcastMessage) {
        scope.launch {
            val payload = mapOf(
                "id" to sms.id,
                "message" to sms.message,
                "createdAtMs" to sms.createdAtMs,
                "placement" to sms.placement.key,
                "sendPushNotification" to sms.sendAsPushNotification
            )
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("broadcast_sms")
                    .setValue(payload)
            } catch (_: Throwable) {
            }
            writeRestObject("$NODE_GLOBAL_STATE/broadcast_sms", JSONObject(payload))
        }
    }

    fun clearBroadcastSmsInCloud() {
        scope.launch {
            try {
                getDatabase().getReference(NODE_GLOBAL_STATE)
                    .child("broadcast_sms")
                    .removeValue()
            } catch (_: Throwable) {
            }
            writeRestObject("$NODE_GLOBAL_STATE/broadcast_sms", null)
        }
    }

    // =========================================================================
    // USER SUBSCRIPTION & ACCOUNT SYNC IN FIREBASE
    // =========================================================================

    /**
     * Syncs a verified premium subscription to Firebase under `/users/{sanitizedEmail}/subscription`
     * and `/payments/{orderId}` so that logging in on another device or after cache deletion
     * automatically restores active premium status.
     */
    fun syncUserSubscriptionToCloud(state: PremiumSubscriptionState) {
        val email = state.linkedUserEmail.trim().lowercase()
        if (email.isBlank() && state.linkedUserUid.isBlank()) return

        scope.launch {
            val payload = mapOf(
                "isVerified" to state.isVerified,
                "planId" to state.planId,
                "planTitle" to state.planTitle,
                "amountTzs" to state.amountTzs,
                "phoneNumber" to state.phoneNumber,
                "orderId" to state.orderId,
                "activatedAtMs" to state.activatedAtMs,
                "expiresAtMs" to state.expiresAtMs,
                "linkedUserEmail" to email,
                "linkedUserUid" to state.linkedUserUid,
                "updatedAtMs" to System.currentTimeMillis()
            )
            val jsonPayload = JSONObject(payload)

            if (email.isNotBlank()) {
                val emailKey = sanitizeEmailKey(email)
                try {
                    getDatabase().getReference(NODE_USERS)
                        .child(emailKey)
                        .child("subscription")
                        .setValue(payload)
                } catch (_: Throwable) {
                }
                writeRestObject("$NODE_USERS/$emailKey/subscription", jsonPayload)

                if (state.orderId.isNotBlank()) {
                    try {
                        getDatabase().getReference(NODE_USERS)
                            .child(emailKey)
                            .child("payments")
                            .child(state.orderId)
                            .setValue(payload)
                    } catch (_: Throwable) {
                    }
                    writeRestObject("$NODE_USERS/$emailKey/payments/${state.orderId}", jsonPayload)
                }
            }

            if (state.linkedUserUid.isNotBlank()) {
                try {
                    getDatabase().getReference(NODE_USERS)
                        .child(state.linkedUserUid)
                        .child("subscription")
                        .setValue(payload)
                } catch (_: Throwable) {
                }
                writeRestObject("$NODE_USERS/${state.linkedUserUid}/subscription", jsonPayload)
            }
        }
    }

    /**
     * Checks Firebase Realtime Database for an existing active premium subscription for [email] or [uid].
     * If found and not expired, restores it into local state.
     */
    suspend fun restoreUserSubscriptionFromCloud(
        context: Context,
        email: String,
        uid: String = ""
    ): PremiumSubscriptionState? = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return@withContext null

        val emailKey = sanitizeEmailKey(cleanEmail)
        val now = System.currentTimeMillis()

        // 1. Try reading from /users/{emailKey}/subscription.json
        try {
            val url = URL("$RTDB_URL/$NODE_USERS/$emailKey/subscription.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                if (resp.isNotBlank() && resp != "null") {
                    val obj = JSONObject(resp)
                    val isVerified = obj.optBoolean("isVerified", false)
                    val expiresAt = obj.optLong("expiresAtMs", 0L)
                    if (isVerified && expiresAt > now) {
                        return@withContext PremiumSubscriptionState(
                            isVerified = true,
                            planId = obj.optString("planId", "TWO_DAYS_1000"),
                            planTitle = obj.optString("planTitle", "VIP Access"),
                            amountTzs = obj.optInt("amountTzs", 1000),
                            phoneNumber = obj.optString("phoneNumber", ""),
                            orderId = obj.optString("orderId", ""),
                            activatedAtMs = obj.optLong("activatedAtMs", now),
                            expiresAtMs = expiresAt,
                            linkedUserEmail = cleanEmail,
                            linkedUserUid = obj.optString("linkedUserUid", uid),
                            requiresPostPaymentAuth = false
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 2. Try reading from /users/{uid}/subscription.json if uid provided
        if (uid.isNotBlank()) {
            try {
                val url = URL("$RTDB_URL/$NODE_USERS/$uid/subscription.json")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                if (conn.responseCode in 200..299) {
                    val resp = conn.inputStream.bufferedReader().use { it.readText() }
                    if (resp.isNotBlank() && resp != "null") {
                        val obj = JSONObject(resp)
                        val isVerified = obj.optBoolean("isVerified", false)
                        val expiresAt = obj.optLong("expiresAtMs", 0L)
                        if (isVerified && expiresAt > now) {
                            return@withContext PremiumSubscriptionState(
                                isVerified = true,
                                planId = obj.optString("planId", "TWO_DAYS_1000"),
                                planTitle = obj.optString("planTitle", "VIP Access"),
                                amountTzs = obj.optInt("amountTzs", 1000),
                                phoneNumber = obj.optString("phoneNumber", ""),
                                orderId = obj.optString("orderId", ""),
                                activatedAtMs = obj.optLong("activatedAtMs", now),
                                expiresAtMs = expiresAt,
                                linkedUserEmail = cleanEmail,
                                linkedUserUid = uid,
                                requiresPostPaymentAuth = false
                            )
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        null
    }

    private fun writeRestValue(path: String, value: Any) {
        try {
            val url = URL("$RTDB_URL/$path.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(value.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (_: Throwable) {
        }
    }

    private fun writeRestObject(path: String, json: JSONObject?) {
        try {
            val url = URL("$RTDB_URL/$path.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = if (json == null) "DELETE" else "PUT"
                if (json != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
            }
            if (json != null) {
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
            }
            conn.responseCode
        } catch (_: Throwable) {
        }
    }
}
