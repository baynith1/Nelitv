package com.example.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.data.local.UserAccountEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class FreeForeverDeviceSlot(
    val deviceId: String,
    val deviceName: String,
    val deviceIp: String,
    val locationCityStreet: String,
    val loginAtMs: Long,
    val lastActiveAtMs: Long
)

data class FreeForeverAccountStatus(
    val email: String,
    val displayName: String,
    val password: String = NeliFreeForeverAccountsManager.FREE_FOREVER_PASSWORD,
    val maxDevices: Int = NeliFreeForeverAccountsManager.MAX_DEVICES_PER_ACCOUNT,
    val activeDevices: List<FreeForeverDeviceSlot> = emptyList()
) {
    val activeDeviceCount: Int
        get() = activeDevices.size

    val isFull: Boolean
        get() = activeDevices.size >= maxDevices
}

/**
 * Manages the 5 Lifetime Free Forever VIP accounts:
 * 1. user1@login.com
 * 2. user2@login.com
 * 3. user3@login.com
 * 4. user4@login.com
 * 5. user5@login.com
 *
 * Rules:
 * - Password for all 5 accounts: `Free123`
 * - Unlocks ALL channels forever regardless of whether Admin has locked individual or all channels.
 * - Strict 2-Device Limit: Each account can only be logged in on a maximum of 2 devices at the same time.
 *   Synced with Firebase Realtime Database + local persistence.
 */
object NeliFreeForeverAccountsManager {
    const val FREE_FOREVER_PASSWORD: String = "Free123"
    const val MAX_DEVICES_PER_ACCOUNT: Int = 2

    private const val PREFS_NAME = "neli_free_forever_accounts_prefs"
    private const val KEY_DEVICES_PREFIX = "devices_json_"
    private const val RTDB_BASE_URL = "https://nelitv-48269-default-rtdb.firebaseio.com/nelitv_free_forever_devices"

    val FREE_FOREVER_EMAILS: List<String> = listOf(
        "user1@login.com",
        "user2@login.com",
        "user3@login.com",
        "user4@login.com",
        "user5@login.com"
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _accountsStatusFlow = MutableStateFlow<List<FreeForeverAccountStatus>>(
        FREE_FOREVER_EMAILS.mapIndexed { idx, email ->
            FreeForeverAccountStatus(
                email = email,
                displayName = "Free Forever VIP ${idx + 1}",
                activeDevices = emptyList()
            )
        }
    )
    val accountsStatusFlow: StateFlow<List<FreeForeverAccountStatus>> = _accountsStatusFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun isFreeForeverEmail(email: String?): Boolean {
        val clean = email?.trim()?.lowercase() ?: return false
        return FREE_FOREVER_EMAILS.any { it.equals(clean, ignoreCase = true) }
    }

    fun isFreeForeverUser(user: UserAccountEntity?): Boolean {
        if (user == null) return false
        return isFreeForeverEmail(user.email)
    }

    fun isValidFreeForeverPassword(password: String): Boolean {
        val clean = password.trim()
        return clean.equals(FREE_FOREVER_PASSWORD, ignoreCase = false) ||
            clean.equals(FREE_FOREVER_PASSWORD, ignoreCase = true)
    }

    fun resolveDisplayName(email: String): String {
        val clean = email.trim().lowercase()
        val index = FREE_FOREVER_EMAILS.indexOfFirst { it.equals(clean, ignoreCase = true) }
        return if (index >= 0) "Free Forever VIP ${index + 1}" else "Free Forever VIP"
    }

    fun resolveUidForEmail(email: String): String {
        val clean = email.trim().lowercase()
        val index = FREE_FOREVER_EMAILS.indexOfFirst { it.equals(clean, ignoreCase = true) }
        return if (index >= 0) "free_forever_vip_${index + 1}" else "free_forever_${clean.hashCode()}"
    }

    fun resolveCurrentDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty().trim()
        val brandModel = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
        return "${brandModel.ifBlank { "Android Device" }} (Android ${Build.VERSION.RELEASE ?: "14"})"
    }

    fun initialize(context: Context) {
        if (!isInitialized) {
            isInitialized = true
            refreshAllAccountsStatusFromLocal(context)
            ioScope.launch {
                syncAllAccountsFromCloud(context)
            }
        }
    }

    fun refreshAllAccountsStatusFromLocal(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updatedList = FREE_FOREVER_EMAILS.mapIndexed { idx, email ->
            val slots = readDeviceSlotsFromPrefs(prefs, email)
            FreeForeverAccountStatus(
                email = email,
                displayName = "Free Forever VIP ${idx + 1}",
                activeDevices = slots
            )
        }
        _accountsStatusFlow.value = updatedList
    }

    /**
     * Verifies whether the current device is allowed to log in to [email] under the strict 2-device limit.
     * - If the account already has < 2 devices, or this device is already one of the 2 registered devices,
     *   registers/updates this device slot and returns [Result.success].
     * - If 2 OTHER devices are already logged into [email], returns [Result.failure] blocking login.
     */
    suspend fun verifyAndRegisterDeviceLogin(
        context: Context,
        email: String
    ): Result<FreeForeverAccountStatus> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!isFreeForeverEmail(cleanEmail)) {
            return@withContext Result.failure(IllegalArgumentException("Not a Free Forever account: $cleanEmail"))
        }

        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDeviceId = NeliSubscriptionManager.resolveDeviceIdentityId(appCtx)
        val currentDeviceName = resolveCurrentDeviceName()
        val currentIp = NeliSubscriptionManager.resolveDeviceIpAddress(appCtx)
        val currentCityStreet = NeliRealtimeAnalyticsManager.resolveCurrentDeviceTanzaniaLocationLabel(appCtx)
        val now = System.currentTimeMillis()

        // 1. Pull latest device slots from Cloud RTDB if reachable, otherwise use local state
        val cloudSlots = fetchAccountDevicesFromCloud(cleanEmail)
        val localSlots = readDeviceSlotsFromPrefs(prefs, cleanEmail)
        val mergedCurrentSlots = mergeDeviceSlots(localSlots, cloudSlots)

        // 2. Check if this device is already registered on this account
        val existingIdx = mergedCurrentSlots.indexOfFirst { it.deviceId == currentDeviceId }
        val updatedSlots = if (existingIdx >= 0) {
            val mutable = mergedCurrentSlots.toMutableList()
            val prev = mutable[existingIdx]
            mutable[existingIdx] = prev.copy(
                deviceName = currentDeviceName,
                deviceIp = currentIp,
                locationCityStreet = currentCityStreet,
                lastActiveAtMs = now
            )
            mutable
        } else {
            // Check 2-device limit
            if (mergedCurrentSlots.size >= MAX_DEVICES_PER_ACCOUNT) {
                saveDeviceSlotsToPrefs(prefs, cleanEmail, mergedCurrentSlots)
                refreshAllAccountsStatusFromLocal(appCtx)
                val activeNames = mergedCurrentSlots.joinToString(", ") { it.deviceName }
                return@withContext Result.failure(
                    IllegalStateException(
                        "Akaunti hii ($cleanEmail) imefika kikomo cha vifaa $MAX_DEVICES_PER_ACCOUNT " +
                            "(Vifaa vilivyopo: $activeNames). Akaunti moja inaruhusiwa kuingia kwenye vifaa 2 tu. " +
                            "Tafadhali Logout kwenye kifaa kimoja au tumia akaunti nyingine ya bure."
                    )
                )
            }
            mergedCurrentSlots + FreeForeverDeviceSlot(
                deviceId = currentDeviceId,
                deviceName = currentDeviceName,
                deviceIp = currentIp,
                locationCityStreet = currentCityStreet,
                loginAtMs = now,
                lastActiveAtMs = now
            )
        }

        // 3. Persist updated device slots locally & push to Firebase RTDB
        saveDeviceSlotsToPrefs(prefs, cleanEmail, updatedSlots)
        pushAccountDevicesToCloud(cleanEmail, updatedSlots)
        refreshAllAccountsStatusFromLocal(appCtx)

        val status = FreeForeverAccountStatus(
            email = cleanEmail,
            displayName = resolveDisplayName(cleanEmail),
            activeDevices = updatedSlots
        )
        Result.success(status)
    }

    /**
     * Releases the current device slot when a user signs out of one of the 5 Free Forever accounts,
     * allowing another device to log in.
     */
    suspend fun releaseDeviceOnLogout(context: Context, email: String) = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!isFreeForeverEmail(cleanEmail)) return@withContext
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentDeviceId = NeliSubscriptionManager.resolveDeviceIdentityId(appCtx)

        val cloudSlots = fetchAccountDevicesFromCloud(cleanEmail)
        val localSlots = readDeviceSlotsFromPrefs(prefs, cleanEmail)
        val merged = mergeDeviceSlots(localSlots, cloudSlots)
        val remaining = merged.filterNot { it.deviceId == currentDeviceId }

        saveDeviceSlotsToPrefs(prefs, cleanEmail, remaining)
        pushAccountDevicesToCloud(cleanEmail, remaining)
        refreshAllAccountsStatusFromLocal(appCtx)
    }

    /**
     * Admin action: Removes a specific device from a Free Forever account so a new device can log in.
     */
    fun adminRemoveDeviceFromAccount(context: Context, email: String, deviceId: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isFreeForeverEmail(cleanEmail)) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = readDeviceSlotsFromPrefs(prefs, cleanEmail)
        val remaining = current.filterNot { it.deviceId == deviceId }
        saveDeviceSlotsToPrefs(prefs, cleanEmail, remaining)
        refreshAllAccountsStatusFromLocal(appCtx)
        ioScope.launch {
            pushAccountDevicesToCloud(cleanEmail, remaining)
        }
    }

    /**
     * Admin action: Resets all connected devices (0/2) for a Free Forever account.
     */
    fun adminResetAllDevicesForAccount(context: Context, email: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isFreeForeverEmail(cleanEmail)) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        saveDeviceSlotsToPrefs(prefs, cleanEmail, emptyList())
        refreshAllAccountsStatusFromLocal(appCtx)
        ioScope.launch {
            pushAccountDevicesToCloud(cleanEmail, emptyList())
        }
    }

    /**
     * Admin helper: Adds a secondary device slot on [email] (up to 2 devices) to test or reserve a slot.
     */
    fun adminAddTestDeviceSlot(context: Context, email: String) {
        val cleanEmail = email.trim().lowercase()
        if (!isFreeForeverEmail(cleanEmail)) return
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = readDeviceSlotsFromPrefs(prefs, cleanEmail)
        if (current.size >= MAX_DEVICES_PER_ACCOUNT) return
        val now = System.currentTimeMillis()
        val slotNum = current.size + 1
        val sampleCities = listOf(
            "Dar es Salaam • Kariakoo, Mtaa wa Msimbazi",
            "Arusha • Sakina, Barabara ya Namanga",
            "Mwanza • Nyamagana, Mtaa wa Kirumba",
            "Dodoma • Area C, Barabara ya Bunge",
            "Mbeya • Mwanjelwa, Mtaa wa Soweto"
        )
        val sampleModels = listOf(
            "Samsung Galaxy A54 5G (Android 14)",
            "Tecno Camon 30 Premier (Android 14)",
            "Infinix Note 40 Pro (Android 14)",
            "Xiaomi Redmi Note 13 (Android 14)",
            "Oppo Reno 11F 5G (Android 14)"
        )
        val idx = (cleanEmail.hashCode().and(0x7FFFFFFF) + slotNum) % sampleCities.size
        val newSlot = FreeForeverDeviceSlot(
            deviceId = "tz_device_${cleanEmail.substringBefore("@")}_slot_${slotNum}_${now % 10000}",
            deviceName = sampleModels[idx],
            deviceIp = "197.250.${40 + idx}.${10 + slotNum * 17}",
            locationCityStreet = sampleCities[idx],
            loginAtMs = now - 600_000L,
            lastActiveAtMs = now
        )
        val updated = (current + newSlot).take(MAX_DEVICES_PER_ACCOUNT)
        saveDeviceSlotsToPrefs(prefs, cleanEmail, updated)
        refreshAllAccountsStatusFromLocal(appCtx)
        ioScope.launch {
            pushAccountDevicesToCloud(cleanEmail, updated)
        }
    }

    suspend fun syncAllAccountsFromCloud(context: Context) = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        FREE_FOREVER_EMAILS.forEach { email ->
            val cloud = fetchAccountDevicesFromCloud(email)
            if (cloud != null) {
                val local = readDeviceSlotsFromPrefs(prefs, email)
                val merged = mergeDeviceSlots(local, cloud)
                saveDeviceSlotsToPrefs(prefs, email, merged)
            }
        }
        refreshAllAccountsStatusFromLocal(appCtx)
    }

    private fun mergeDeviceSlots(
        local: List<FreeForeverDeviceSlot>,
        cloud: List<FreeForeverDeviceSlot>?
    ): List<FreeForeverDeviceSlot> {
        if (cloud == null) return local.take(MAX_DEVICES_PER_ACCOUNT)
        if (cloud.isEmpty() && local.isEmpty()) return emptyList()
        // Prefer cloud list when non-empty, merging any local active device if cloud hasn't synced yet
        val byId = LinkedHashMap<String, FreeForeverDeviceSlot>()
        cloud.forEach { byId[it.deviceId] = it }
        local.forEach { slot ->
            val existing = byId[slot.deviceId]
            if (existing == null && byId.size < MAX_DEVICES_PER_ACCOUNT) {
                byId[slot.deviceId] = slot
            } else if (existing != null && slot.lastActiveAtMs > existing.lastActiveAtMs) {
                byId[slot.deviceId] = slot
            }
        }
        return byId.values.take(MAX_DEVICES_PER_ACCOUNT)
    }

    private fun accountKey(email: String): String {
        return email.trim().lowercase().replace(Regex("[^a-z0-9]"), "_")
    }

    private fun readDeviceSlotsFromPrefs(
        prefs: android.content.SharedPreferences,
        email: String
    ): List<FreeForeverDeviceSlot> {
        val raw = prefs.getString(KEY_DEVICES_PREFIX + accountKey(email), null) ?: return emptyList()
        return parseDeviceSlotsJson(raw)
    }

    private fun saveDeviceSlotsToPrefs(
        prefs: android.content.SharedPreferences,
        email: String,
        slots: List<FreeForeverDeviceSlot>
    ) {
        val arr = JSONArray()
        slots.take(MAX_DEVICES_PER_ACCOUNT).forEach { slot ->
            val obj = JSONObject()
            obj.put("deviceId", slot.deviceId)
            obj.put("deviceName", slot.deviceName)
            obj.put("deviceIp", slot.deviceIp)
            obj.put("locationCityStreet", slot.locationCityStreet)
            obj.put("loginAtMs", slot.loginAtMs)
            obj.put("lastActiveAtMs", slot.lastActiveAtMs)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_DEVICES_PREFIX + accountKey(email), arr.toString()).apply()
    }

    private fun parseDeviceSlotsJson(rawJson: String): List<FreeForeverDeviceSlot> {
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<FreeForeverDeviceSlot>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val deviceId = obj.optString("deviceId").trim()
                if (deviceId.isBlank()) continue
                list.add(
                    FreeForeverDeviceSlot(
                        deviceId = deviceId,
                        deviceName = obj.optString("deviceName", "Android Device"),
                        deviceIp = obj.optString("deviceIp", "197.250.0.1"),
                        locationCityStreet = obj.optString("locationCityStreet", "Dar es Salaam • Kariakoo"),
                        loginAtMs = obj.optLong("loginAtMs", System.currentTimeMillis()),
                        lastActiveAtMs = obj.optLong("lastActiveAtMs", System.currentTimeMillis())
                    )
                )
            }
            list.take(MAX_DEVICES_PER_ACCOUNT)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchAccountDevicesFromCloud(email: String): List<FreeForeverDeviceSlot>? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$RTDB_BASE_URL/${accountKey(email)}.json")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (body.isBlank() || body == "null") {
                    emptyList()
                } else {
                    val root = JSONObject(body)
                    val arr = root.optJSONArray("devices") ?: JSONArray()
                    parseDeviceSlotsJson(arr.toString())
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun pushAccountDevicesToCloud(email: String, slots: List<FreeForeverDeviceSlot>) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$RTDB_BASE_URL/${accountKey(email)}.json")
            val arr = JSONArray()
            slots.take(MAX_DEVICES_PER_ACCOUNT).forEach { slot ->
                val obj = JSONObject().apply {
                    put("deviceId", slot.deviceId)
                    put("deviceName", slot.deviceName)
                    put("deviceIp", slot.deviceIp)
                    put("locationCityStreet", slot.locationCityStreet)
                    put("loginAtMs", slot.loginAtMs)
                    put("lastActiveAtMs", slot.lastActiveAtMs)
                }
                arr.put(obj)
            }
            val payload = JSONObject().apply {
                put("email", email)
                put("maxDevices", MAX_DEVICES_PER_ACCOUNT)
                put("activeDeviceCount", slots.size.coerceAtMost(MAX_DEVICES_PER_ACCOUNT))
                put("updatedAtMs", System.currentTimeMillis())
                put("devices", arr)
            }
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 4500
                readTimeout = 4500
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }
            conn.responseCode
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }
    }
}
