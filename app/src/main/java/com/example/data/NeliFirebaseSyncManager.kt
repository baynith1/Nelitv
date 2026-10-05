package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.player.TokenManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Centralized Firebase Realtime Database & Firestore synchronization manager for Nelitv.
 *
 * Synchronizes:
 * 1. Channels lock & visibility status across ALL user apps in real-time.
 * 2. Admin broadcast notifications & SMS banners across ALL user apps.
 * 3. User premium subscription & payments between Android app, Website, and Cloud.
 * 4. Azam CDN tokens 24/7.
 * 5. Mandatory in-app update checks.
 */
object NeliFirebaseSyncManager {

    private const val TAG = "NeliFirebaseSync"
    const val RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var isInitialized = false

    private val rtdb: FirebaseDatabase? by lazy {
        try {
            FirebaseDatabase.getInstance(RTDB_URL)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get FirebaseDatabase instance", e)
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get FirebaseFirestore instance", e)
            null
        }
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val appContext = context.applicationContext

        // 1. Listen for Channels Lock & Hide configuration in Realtime Database
        setupChannelsLockListener(appContext)

        // 2. Listen for Admin Broadcast SMS / Notification
        setupAdminNotificationListener(appContext)

        // 3. Listen for Azam 24/7 CDN tokens
        setupTokenListener()

        // 4. Listen for In-App Update configuration
        setupAppUpdateListener(appContext)

        // 5. Automatic background refresh service that fetches fresh tokens from Firebase every 30 minutes (24/7 stability)
        setupPeriodicThirtyMinuteTokenRefreshService(appContext)

        // 6. Initial REST sync fallback in case WebSocket takes time
        scope.launch {
            syncFromRestEndpoints(appContext)
        }
    }

    private const val TOKEN_REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes

    /**
     * Automatic background refresh service that fetches fresh tokens from Firebase every 30 minutes,
     * ensuring 24/7 stream stability.
     */
    private fun setupPeriodicThirtyMinuteTokenRefreshService(context: Context) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(TOKEN_REFRESH_INTERVAL_MS)
                try {
                    fetchAndApplyFreshFirebaseToken()
                } catch (e: Throwable) {
                    Log.w(TAG, "Periodic 30-minute token refresh error: ${e.message}")
                }
            }
        }
    }

    suspend fun fetchAndApplyFreshFirebaseToken(): Boolean {
        return withContext(Dispatchers.IO) {
            val tokenEndpoints = listOf(
                "$RTDB_URL/config/azam_token.json",
                "$RTDB_URL/azam_token.json",
                "$RTDB_URL/cdn_token.json"
            )
            var tokenFetched: String? = null
            var cdnHostFetched: String? = null

            for (endpoint in tokenEndpoints) {
                try {
                    val resp = executeGet(endpoint)
                    if (!resp.isNullOrBlank() && resp != "null") {
                        if (resp.trim().startsWith("{")) {
                            val obj = JSONObject(resp)
                            val token = obj.optString("token").ifBlank { obj.optString("cdn_token") }
                            if (token.isNotBlank()) {
                                tokenFetched = token
                                cdnHostFetched = obj.optString("cdnHost").ifBlank { null }
                                break
                            }
                        } else if (!resp.contains("{") && resp.trim().length > 20) {
                            tokenFetched = resp.replace("\"", "").trim()
                            break
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            if (tokenFetched.isNullOrBlank()) {
                try {
                    val resp = executeGet(ChannelRepository.DEFAULT_TOKEN_ENDPOINT_URL)
                    if (!resp.isNullOrBlank() && resp != "null" && resp.trim().startsWith("{")) {
                        val obj = JSONObject(resp)
                        val token = obj.optString("token").ifBlank { obj.optString("cdn_token") }
                        if (token.isNotBlank()) {
                            tokenFetched = token
                            cdnHostFetched = obj.optString("cdnHost").ifBlank { null }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            if (!tokenFetched.isNullOrBlank()) {
                val host = cdnHostFetched ?: ChannelRepository.DEFAULT_AZAM_CDN_HOST
                ChannelRepository.updateCdnAuthorizationToken(
                    jsonStr = """{"token":"$tokenFetched","cdnHost":"$host"}""",
                    isAuthoritativeTokenApi = true
                )
                TokenManager.syncStateFromRepository()
                Log.d(TAG, "24/7 background service refreshed Azam token successfully from Firebase (30 min interval)")
                true
            } else {
                false
            }
        }
    }

    private fun setupChannelsLockListener(context: Context) {
        try {
            val db = rtdb ?: return
            db.getReference("config/channels_lock").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val lockAll = snapshot.child("lockAll").getValue(Boolean::class.java) ?: false
                        val lockedList = mutableSetOf<String>()
                        snapshot.child("lockedIds").children.forEach { child ->
                            child.getValue(String::class.java)?.let { lockedList.add(it) }
                        }
                        val hiddenList = mutableSetOf<String>()
                        snapshot.child("hiddenIds").children.forEach { child ->
                            child.getValue(String::class.java)?.let { hiddenList.add(it) }
                        }
                        NeliAdminManager.applyCloudChannelLocks(
                            context = context,
                            lockAll = lockAll,
                            lockedIds = lockedList,
                            hiddenIds = hiddenList
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error applying channels lock from RTDB", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Channels lock listener cancelled: ${error.message}")
                }
            })

            // Also observe channels node
            db.getReference("channels").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val lockedFromChannels = mutableSetOf<String>()
                        for (child in snapshot.children) {
                            val channelId = child.key ?: continue
                            val isLocked = child.child("isLocked").getValue(Boolean::class.java) ?: false
                            if (isLocked) {
                                lockedFromChannels.add(channelId)
                            }
                        }
                        if (lockedFromChannels.isNotEmpty()) {
                            NeliAdminManager.mergeCloudLockedChannels(context, lockedFromChannels)
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error parsing channels node", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Throwable) {
            Log.e(TAG, "setupChannelsLockListener failed", e)
        }
    }

    private fun setupAdminNotificationListener(context: Context) {
        try {
            val db = rtdb ?: return
            db.getReference("config/admin_notification").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val message = snapshot.child("message").getValue(String::class.java)?.trim().orEmpty()
                        if (message.isBlank()) {
                            NeliAdminManager.clearAdminSms(context, notifyCloud = false)
                            return
                        }
                        val placementStr = snapshot.child("placement").getValue(String::class.java)
                        val placement = AdminBannerPlacement.fromKey(placementStr)
                        val sendPush = snapshot.child("sendPush").getValue(Boolean::class.java) ?: true
                        val id = snapshot.child("id").getValue(String::class.java) ?: "cloud_${System.currentTimeMillis()}"
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: System.currentTimeMillis()

                        NeliAdminManager.applyCloudAdminNotification(
                            context = context,
                            id = id,
                            message = message,
                            placement = placement,
                            sendPushNotification = sendPush,
                            createdAtMs = timestamp
                        )
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error applying admin notification from RTDB", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Throwable) {
            Log.e(TAG, "setupAdminNotificationListener failed", e)
        }
    }

    private fun setupTokenListener() {
        try {
            val db = rtdb ?: return
            val tokenListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val token = snapshot.child("token").getValue(String::class.java)
                            ?: snapshot.getValue(String::class.java)
                        if (!token.isNullOrBlank()) {
                            val cdnHost = snapshot.child("cdnHost").getValue(String::class.java)
                                ?: ChannelRepository.DEFAULT_AZAM_CDN_HOST
                            ChannelRepository.updateCdnAuthorizationToken(
                                jsonStr = """{"token":"$token","cdnHost":"$cdnHost"}""",
                                isAuthoritativeTokenApi = true
                            )
                            TokenManager.syncStateFromRepository()
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error updating token from RTDB", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            }

            db.getReference("config/azam_token").addValueEventListener(tokenListener)
            db.getReference("azam_token").addValueEventListener(tokenListener)
            db.getReference("cdn_token").addValueEventListener(tokenListener)
        } catch (e: Throwable) {
            Log.e(TAG, "setupTokenListener failed", e)
        }
    }

    private fun setupAppUpdateListener(context: Context) {
        try {
            val db = rtdb ?: return
            db.getReference("config/app_update").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val latestVersion = snapshot.child("version").getValue(String::class.java)?.trim().orEmpty()
                        val apkUrl = snapshot.child("apkUrl").getValue(String::class.java)?.trim().orEmpty()
                        val notes = snapshot.child("notes").getValue(String::class.java)?.trim().orEmpty()
                        val isMandatory = snapshot.child("mandatory").getValue(Boolean::class.java) ?: true

                        if (latestVersion.isNotBlank()) {
                            NeliAppUpdateManager.applyCloudUpdateInfo(
                                latestVersion = latestVersion,
                                apkUrl = apkUrl,
                                whatsNewNotes = notes,
                                isMandatory = isMandatory
                            )
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Error applying app update from RTDB", e)
                    }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Throwable) {
            Log.e(TAG, "setupAppUpdateListener failed", e)
        }
    }

    /**
     * Fallback REST sync to ensure data is fetched even if Realtime Database WebSocket is blocked or offline.
     */
    private fun syncFromRestEndpoints(context: Context) {
        try {
            // 1. Config channels lock
            val lockJson = executeGet("$RTDB_URL/config/channels_lock.json")
            if (!lockJson.isNullOrBlank() && lockJson != "null") {
                val obj = JSONObject(lockJson)
                val lockAll = obj.optBoolean("lockAll", false)
                val lockedIds = mutableSetOf<String>()
                val lockedArr = obj.optJSONArray("lockedIds")
                if (lockedArr != null) {
                    for (i in 0 until lockedArr.length()) {
                        lockedIds.add(lockedArr.getString(i))
                    }
                }
                val hiddenIds = mutableSetOf<String>()
                val hiddenArr = obj.optJSONArray("hiddenIds")
                if (hiddenArr != null) {
                    for (i in 0 until hiddenArr.length()) {
                        hiddenIds.add(hiddenArr.getString(i))
                    }
                }
                NeliAdminManager.applyCloudChannelLocks(context, lockAll, lockedIds, hiddenIds)
            }

            // 2. Admin notification
            val notifJson = executeGet("$RTDB_URL/config/admin_notification.json")
            if (!notifJson.isNullOrBlank() && notifJson != "null") {
                val obj = JSONObject(notifJson)
                val message = obj.optString("message", "").trim()
                if (message.isNotBlank()) {
                    val placement = AdminBannerPlacement.fromKey(obj.optString("placement", "BELOW_SLIDER"))
                    val sendPush = obj.optBoolean("sendPush", true)
                    val id = obj.optString("id", "cloud_notif")
                    val time = obj.optLong("timestamp", System.currentTimeMillis())
                    NeliAdminManager.applyCloudAdminNotification(
                        context = context,
                        id = id,
                        message = message,
                        placement = placement,
                        sendPushNotification = sendPush,
                        createdAtMs = time
                    )
                }
            }

            // 3. Tokens (config/azam_token or /azam_token or /cdn_token)
            val tokenEndpoints = listOf(
                "$RTDB_URL/config/azam_token.json",
                "$RTDB_URL/azam_token.json",
                "$RTDB_URL/cdn_token.json"
            )
            for (endpoint in tokenEndpoints) {
                val resp = executeGet(endpoint)
                if (!resp.isNullOrBlank() && resp != "null") {
                    val token = if (resp.startsWith("{")) {
                        val obj = JSONObject(resp)
                        obj.optString("token", "")
                    } else {
                        resp.trim('"', ' ', '\n')
                    }
                    if (token.isNotBlank()) {
                        ChannelRepository.updateCdnAuthorizationToken(
                            jsonStr = """{"token":"$token"}""",
                            isAuthoritativeTokenApi = true
                        )
                        TokenManager.syncStateFromRepository()
                        break
                    }
                }
            }

            // 4. In-App Update config from REST
            val updateJson = executeGet("$RTDB_URL/config/app_update.json")
            if (!updateJson.isNullOrBlank() && updateJson != "null") {
                val obj = JSONObject(updateJson)
                val ver = obj.optString("version", "")
                val url = obj.optString("apkUrl", "")
                val notes = obj.optString("notes", "")
                if (ver.isNotBlank()) {
                    NeliAppUpdateManager.applyCloudUpdateInfo(
                        latestVersion = ver,
                        apkUrl = url,
                        whatsNewNotes = notes,
                        isMandatory = obj.optBoolean("mandatory", true)
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "syncFromRestEndpoints failed", e)
        }
    }

    /**
     * Synchronizes and restores active subscription from Firebase Realtime Database and Firestore.
     * Called whenever a user logs in, signs up, or opens the app.
     */
    suspend fun syncUserSubscriptionFromFirebase(
        context: Context,
        uid: String,
        email: String,
        phone: String = ""
    ) {
        val cleanUid = uid.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPhone = phone.trim()

        if (cleanUid.isBlank() && cleanEmail.isBlank() && cleanPhone.isBlank()) return

        // 1. Try Firebase Realtime Database
        try {
            var subObj: JSONObject? = null

            // Check users/$uid/subscription
            if (cleanUid.isNotBlank()) {
                val resp = executeGet("$RTDB_URL/users/$cleanUid/subscription.json")
                if (!resp.isNullOrBlank() && resp != "null") {
                    subObj = JSONObject(resp)
                }
                if (subObj == null) {
                    val resp2 = executeGet("$RTDB_URL/subscriptions/$cleanUid.json")
                    if (!resp2.isNullOrBlank() && resp2 != "null") {
                        subObj = JSONObject(resp2)
                    }
                }
            }

            // Check payments_by_phone if phone is provided
            if (subObj == null && cleanPhone.isNotBlank()) {
                val safePhone = cleanPhone.replace("+", "")
                val resp = executeGet("$RTDB_URL/payments_by_phone/$safePhone.json")
                if (!resp.isNullOrBlank() && resp != "null") {
                    subObj = JSONObject(resp)
                }
            }

            if (subObj != null) {
                val isVerified = subObj.optBoolean("isVerified", false)
                val expiresAtMs = subObj.optLong("expiresAtMs", 0L)
                val now = System.currentTimeMillis()

                if (isVerified && expiresAtMs > now) {
                    val planId = subObj.optString("planId", "TWO_DAYS_1000")
                    val planTitle = subObj.optString("planTitle", "Premium Package")
                    val amountTzs = subObj.optInt("amountTzs", 1000)
                    val phoneNumber = subObj.optString("phoneNumber", cleanPhone)
                    val orderId = subObj.optString("orderId", "FIREBASE-SYNC")
                    val activatedAt = subObj.optLong("activatedAtMs", now)

                    val newState = PremiumSubscriptionState(
                        isVerified = true,
                        planId = planId,
                        planTitle = planTitle,
                        amountTzs = amountTzs,
                        phoneNumber = phoneNumber,
                        orderId = orderId,
                        deviceIpAddress = NeliSubscriptionManager.resolveDeviceIpAddress(context),
                        deviceId = NeliSubscriptionManager.resolveDeviceIdentityId(context),
                        activatedAtMs = activatedAt,
                        expiresAtMs = expiresAtMs,
                        linkedUserUid = cleanUid,
                        linkedUserEmail = cleanEmail,
                        linkedUserName = subObj.optString("linkedUserName", "")
                    )
                    NeliSubscriptionManager.restoreSubscriptionFromCloud(context, newState)
                    return
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error syncing subscription from RTDB", e)
        }

        // 2. Try Firestore fallback
        try {
            val fs = firestore ?: return
            if (cleanUid.isNotBlank()) {
                fs.collection("users").document(cleanUid).get().addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val subMap = doc.get("subscription") as? Map<*, *>
                        if (subMap != null) {
                            val isVerified = subMap["isVerified"] as? Boolean ?: false
                            val expiresAt = (subMap["expiresAtMs"] as? Number)?.toLong() ?: 0L
                            val now = System.currentTimeMillis()
                            if (isVerified && expiresAt > now) {
                                val state = PremiumSubscriptionState(
                                    isVerified = true,
                                    planId = subMap["planId"] as? String ?: "TWO_DAYS_1000",
                                    planTitle = subMap["planTitle"] as? String ?: "Premium Package",
                                    amountTzs = (subMap["amountTzs"] as? Number)?.toInt() ?: 1000,
                                    phoneNumber = subMap["phoneNumber"] as? String ?: cleanPhone,
                                    orderId = subMap["orderId"] as? String ?: "FIRESTORE-SYNC",
                                    deviceIpAddress = NeliSubscriptionManager.resolveDeviceIpAddress(context),
                                    deviceId = NeliSubscriptionManager.resolveDeviceIdentityId(context),
                                    activatedAtMs = (subMap["activatedAtMs"] as? Number)?.toLong() ?: now,
                                    expiresAtMs = expiresAt,
                                    linkedUserUid = cleanUid,
                                    linkedUserEmail = cleanEmail,
                                    linkedUserName = subMap["linkedUserName"] as? String ?: ""
                                )
                                NeliSubscriptionManager.restoreSubscriptionFromCloud(context, state)
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error syncing subscription from Firestore", e)
        }
    }

    /**
     * Saves user subscription and payment record to Firebase Realtime Database and Firestore.
     * Ensures payments made on mobile are immediately reflected on the website and across devices.
     */
    fun saveUserSubscriptionToFirebase(state: PremiumSubscriptionState) {
        scope.launch {
            val now = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("isVerified", state.isVerified)
                put("planId", state.planId)
                put("planTitle", state.planTitle)
                put("amountTzs", state.amountTzs)
                put("phoneNumber", state.phoneNumber)
                put("orderId", state.orderId)
                put("deviceIpAddress", state.deviceIpAddress)
                put("deviceId", state.deviceId)
                put("activatedAtMs", state.activatedAtMs)
                put("expiresAtMs", state.expiresAtMs)
                put("linkedUserUid", state.linkedUserUid)
                put("linkedUserEmail", state.linkedUserEmail)
                put("linkedUserName", state.linkedUserName)
                put("updatedAt", now)
            }.toString()

            val uid = state.linkedUserUid.ifBlank {
                FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            }
            val email = state.linkedUserEmail.ifBlank {
                FirebaseAuth.getInstance().currentUser?.email.orEmpty()
            }

            // 1. Write to RTDB
            if (uid.isNotBlank()) {
                executePut("$RTDB_URL/users/$uid/subscription.json", payload)
                executePut("$RTDB_URL/subscriptions/$uid.json", payload)
            }
            if (state.orderId.isNotBlank()) {
                executePut("$RTDB_URL/payments/${state.orderId}.json", payload)
            }
            if (state.phoneNumber.isNotBlank()) {
                val cleanPhone = state.phoneNumber.replace("+", "").replace(" ", "")
                executePut("$RTDB_URL/payments_by_phone/$cleanPhone.json", payload)
            }

            // 2. Write to Firestore
            try {
                val fs = firestore
                if (fs != null) {
                    val map = mutableMapOf<String, Any>(
                        "isVerified" to state.isVerified,
                        "planId" to state.planId,
                        "planTitle" to state.planTitle,
                        "amountTzs" to state.amountTzs,
                        "phoneNumber" to state.phoneNumber,
                        "orderId" to state.orderId,
                        "activatedAtMs" to state.activatedAtMs,
                        "expiresAtMs" to state.expiresAtMs,
                        "linkedUserUid" to uid,
                        "linkedUserEmail" to email,
                        "updatedAt" to now
                    )
                    if (uid.isNotBlank()) {
                        fs.collection("users").document(uid).set(
                            mapOf(
                                "email" to email,
                                "subscription" to map,
                                "updatedAt" to now
                            ),
                            SetOptions.merge()
                        )
                    }
                    if (state.orderId.isNotBlank()) {
                        fs.collection("payments").document(state.orderId).set(map, SetOptions.merge())
                    }
                    if (state.phoneNumber.isNotBlank()) {
                        val cleanPhone = state.phoneNumber.replace("+", "").replace(" ", "")
                        fs.collection("payments_by_phone").document(cleanPhone).set(map, SetOptions.merge())
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error writing subscription to Firestore", e)
            }
        }
    }

    /**
     * Pushes channel lock / hide state to Firebase Realtime Database & Firestore so ALL apps update.
     */
    fun pushChannelLockStateToFirebase(
        lockAll: Boolean,
        lockedIds: Set<String>,
        hiddenIds: Set<String>
    ) {
        scope.launch {
            val json = JSONObject().apply {
                put("lockAll", lockAll)
                put("lockedIds", JSONArray(lockedIds.toList()))
                put("hiddenIds", JSONArray(hiddenIds.toList()))
                put("updatedAt", System.currentTimeMillis())
            }.toString()

            // RTDB
            executePut("$RTDB_URL/config/channels_lock.json", json)

            // Update individual channels in RTDB
            for (id in lockedIds) {
                executePut("$RTDB_URL/channels/$id/isLocked.json", "true")
            }

            // Firestore
            try {
                firestore?.collection("config")?.document("channels_lock")?.set(
                    mapOf(
                        "lockAll" to lockAll,
                        "lockedIds" to lockedIds.toList(),
                        "hiddenIds" to hiddenIds.toList(),
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error pushing channels_lock to Firestore", e)
            }
        }
    }

    /**
     * Pushes Admin Broadcast Notification / SMS to Firebase so ALL apps display it.
     */
    fun pushAdminNotificationToFirebase(
        message: String,
        placement: AdminBannerPlacement,
        sendPush: Boolean
    ) {
        scope.launch {
            val id = "broadcast_${System.currentTimeMillis()}"
            val time = System.currentTimeMillis()
            val json = JSONObject().apply {
                put("id", id)
                put("message", message)
                put("placement", placement.key)
                put("sendPush", sendPush)
                put("timestamp", time)
            }.toString()

            executePut("$RTDB_URL/config/admin_notification.json", json)

            try {
                firestore?.collection("config")?.document("admin_notification")?.set(
                    mapOf(
                        "id" to id,
                        "message" to message,
                        "placement" to placement.key,
                        "sendPush" to sendPush,
                        "timestamp" to time
                    ),
                    SetOptions.merge()
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Error pushing admin_notification to Firestore", e)
            }
        }
    }

    fun clearAdminNotificationFromFirebase() {
        scope.launch {
            executeDelete("$RTDB_URL/config/admin_notification.json")
            try {
                firestore?.collection("config")?.document("admin_notification")?.delete()
            } catch (e: Throwable) {
                Log.e(TAG, "Error deleting admin_notification in Firestore", e)
            }
        }
    }

    /**
     * Pushes Azam CDN token to Firebase so all apps and website stay online 24/7.
     */
    fun pushCdnTokenToFirebase(token: String, cdnHost: String = ChannelRepository.DEFAULT_AZAM_CDN_HOST) {
        scope.launch {
            val json = JSONObject().apply {
                put("token", token)
                put("cdnHost", cdnHost)
                put("updatedAt", System.currentTimeMillis())
            }.toString()
            executePut("$RTDB_URL/config/azam_token.json", json)
            executePut("$RTDB_URL/azam_token.json", json)
            executePut("$RTDB_URL/cdn_token.json", json)
        }
    }

    private fun executeGet(urlStr: String): String? {
        return try {
            val request = Request.Builder().url(urlStr).get().build()
            httpClient.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun executePut(urlStr: String, json: String): Boolean {
        return try {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(urlStr).put(body).build()
            httpClient.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun executeDelete(urlStr: String): Boolean {
        return try {
            val request = Request.Builder().url(urlStr).delete().build()
            httpClient.newCall(request).execute().use { resp ->
                resp.isSuccessful
            }
        } catch (_: Throwable) {
            false
        }
    }
}
