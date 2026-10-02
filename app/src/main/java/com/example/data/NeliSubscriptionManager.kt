package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import com.example.data.local.DeviceSubscriptionEntity
import com.example.data.local.NeliDatabase
import com.example.data.local.NeliMediaDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscription plans available in the Premium tab:
 * - Kwa Siku Mbili: 1,000 TSh (48 hours / 2 days)
 * - Kwa Wiki: 3,500 TSh (7 days)
 * - Kwa Mwezi: 15,000 TSh (30 days)
 */
enum class SubscriptionPlanType(
    val id: String,
    val titleSwahili: String,
    val subtitleSwahili: String,
    val amountTzs: Int,
    val priceFormatted: String,
    val durationMillis: Long,
    val durationLabel: String,
    val badgeText: String
) {
    DAILY(
        id = "TWO_DAYS_1000",
        titleSwahili = "Kwa Siku Mbili",
        subtitleSwahili = "Fungua channel zote za VIP kwa siku 2 (saa 48)",
        amountTzs = 1000,
        priceFormatted = "1,000 TSh",
        durationMillis = 2L * 24L * 60L * 60L * 1000L,
        durationLabel = "Siku 2 (Saa 48)",
        badgeText = "MAARUFU KWA SIKU 2"
    ),
    WEEKLY(
        id = "WEEKLY_3500",
        titleSwahili = "Kwa Wiki",
        subtitleSwahili = "Fungua channel zote za VIP kwa siku 7 mfululizo",
        amountTzs = 3500,
        priceFormatted = "3,500 TSh",
        durationMillis = 7L * 24L * 60L * 60L * 1000L,
        durationLabel = "Wiki 1 (Siku 7)",
        badgeText = "OFA BORA YA WIKI"
    ),
    MONTHLY(
        id = "MONTHLY_15000",
        titleSwahili = "Kwa Mwezi",
        subtitleSwahili = "Fungua channel zote za VIP kwa siku 30 bila kikomo",
        amountTzs = 15000,
        priceFormatted = "15,000 TSh",
        durationMillis = 30L * 24L * 60L * 60L * 1000L,
        durationLabel = "Mwezi 1 (Siku 30)",
        badgeText = "VIP FULL ACCESS"
    );

    companion object {
        val TWO_DAYS: SubscriptionPlanType get() = DAILY
    }
}

data class PremiumSubscriptionState(
    val isVerified: Boolean = false,
    val planId: String = "",
    val planTitle: String = "",
    val amountTzs: Int = 0,
    val phoneNumber: String = "",
    val orderId: String = "",
    val deviceIpAddress: String = "",
    val deviceId: String = "",
    val activatedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val linkedUserUid: String = "",
    val linkedUserEmail: String = "",
    val linkedUserName: String = "",
    val requiresPostPaymentAuth: Boolean = false,
    val pendingOrderId: String = "",
    val pendingPlanId: String = "",
    val pendingPhone: String = "",
    val pendingAmountTzs: Int = 0
) {
    val isFreeForeverAccount: Boolean
        get() = NeliFreeForeverAccountsManager.isFreeForeverEmail(linkedUserEmail) || planId == "free_forever"

    val isActiveNow: Boolean
        get() = isFreeForeverAccount || (isVerified && expiresAtMs > System.currentTimeMillis())

    val isLinkedToUserAccount: Boolean
        get() = linkedUserEmail.isNotBlank() || linkedUserUid.isNotBlank()

    val formattedExpiryDate: String
        get() {
            if (isFreeForeverAccount) return "Bure Milele (Haishi Muda ∞)"
            if (expiresAtMs <= 0L) return ""
            return try {
                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                sdf.format(Date(expiresAtMs))
            } catch (_: Exception) {
                ""
            }
        }

    val remainingDaysOrHoursLabel: String
        get() {
            if (isFreeForeverAccount) return "Bure Milele (Free Forever • Max Vifaa 2)"
            val remainingMs = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remainingMs == 0L) return "Imeisha muda"
            val totalHours = remainingMs / (1000L * 60L * 60L)
            val days = totalHours / 24L
            val hours = totalHours % 24L
            val minutes = (remainingMs / (1000L * 60L)) % 60L
            return when {
                days > 0 -> "Siku $days na Saa $hours zimebaki"
                hours > 0 -> "Saa $hours na Dakika $minutes zimebaki"
                else -> "Dakika ${minutes.coerceAtLeast(1)} zimebaki"
            }
        }
}

/**
 * Manages user Premium Subscription state verified via HarakaPay (`https://harakapay.net`).
 *
 * - Users can pay WITHOUT logging in or signing up: the app automatically reads the real
 *   Device IP (`deviceIpAddress`) and Device ID (`deviceId`) to store and preserve their
 *   real payment and verified Premium Member state even when not logged in.
 * - A user ONLY becomes a Premium Member after HarakaPay confirms `payment.status == "completed"`.
 * - While a user's Premium package is active (`isActiveNow == true`), ALL channels remain unlocked
 *   even if the Admin locks channels for free users, until the package expires (`expiresAtMs`).
 */
object NeliSubscriptionManager {

    private const val PREFS_NAME = "neli_harakapay_subscription_prefs"
    private const val KEY_IS_VERIFIED = "is_verified"
    private const val KEY_PLAN_ID = "plan_id"
    private const val KEY_PLAN_TITLE = "plan_title"
    private const val KEY_AMOUNT_TZS = "amount_tzs"
    private const val KEY_PHONE = "phone_number"
    private const val KEY_ORDER_ID = "order_id"
    private const val KEY_DEVICE_IP = "device_ip_address"
    private const val KEY_DEVICE_ID = "device_hardware_id"
    private const val KEY_ACTIVATED_AT = "activated_at_ms"
    private const val KEY_EXPIRES_AT = "expires_at_ms"
    private const val KEY_LINKED_UID = "linked_user_uid"
    private const val KEY_LINKED_EMAIL = "linked_user_email"
    private const val KEY_LINKED_NAME = "linked_user_name"
    private const val KEY_REQUIRES_POST_PAYMENT_AUTH = "requires_post_payment_auth"

    private const val KEY_PENDING_ORDER_ID = "pending_order_id"
    private const val KEY_PENDING_PLAN_ID = "pending_plan_id"
    private const val KEY_PENDING_PHONE = "pending_phone"
    private const val KEY_PENDING_AMOUNT = "pending_amount_tzs"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _subscriptionState = MutableStateFlow(PremiumSubscriptionState())
    val subscriptionState: StateFlow<PremiumSubscriptionState> = _subscriptionState.asStateFlow()

    /**
     * Automatically detects the device's real IPv4/IPv6 network IP address from Android
     * [ConnectivityManager] link properties and active [NetworkInterface]s so the user can pay
     * and store real subscription data without needing to log in or sign up.
     */
    fun resolveDeviceIpAddress(context: Context? = null): String {
        // 1. Query ConnectivityManager active network link addresses
        if (context != null) {
            try {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val activeNet = cm?.activeNetwork
                val linkProps = activeNet?.let { cm.getLinkProperties(it) }
                val ipv4FromLink = linkProps?.linkAddresses
                    ?.mapNotNull { it.address }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
                if (!ipv4FromLink.isNullOrBlank()) {
                    return ipv4FromLink
                }
            } catch (_: Throwable) {
            }
        }

        // 2. Query active NetworkInterfaces for non-loopback IPv4 address
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    if (!intf.isUp || intf.isLoopback) continue
                    val addrs = intf.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrBlank()) {
                                return host
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 3. Fallback to any non-loopback address or deterministic device local IP
        return "10.0.2.15"
    }

    /**
     * Resolves a persistent real device identity combining Android Secure ID and hardware model
     * so payments made without login/signup are reliably bound to this device.
     */
    fun resolveDeviceIdentityId(context: Context? = null): String {
        val androidId = try {
            context?.applicationContext?.contentResolver?.let { resolver ->
                Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
            }.orEmpty()
        } catch (_: Throwable) {
            ""
        }
        val modelTag = "${Build.MANUFACTURER}_${Build.MODEL}".replace(" ", "_")
        return if (androidId.isNotBlank()) {
            "${modelTag}_$androidId"
        } else {
            modelTag
        }
    }

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isVerified = prefs.getBoolean(KEY_IS_VERIFIED, false)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        val liveIp = resolveDeviceIpAddress(context)
        val liveDeviceId = resolveDeviceIdentityId(context)
        val savedIp = prefs.getString(KEY_DEVICE_IP, "").orEmpty().ifBlank { liveIp }
        val savedDeviceId = prefs.getString(KEY_DEVICE_ID, "").orEmpty().ifBlank { liveDeviceId }

        // Persist automatic device IP and device ID immediately so real device metadata is always ready
        prefs.edit()
            .putString(KEY_DEVICE_IP, liveIp.ifBlank { savedIp })
            .putString(KEY_DEVICE_ID, savedDeviceId)
            .apply()

        val stillValid = isVerified && expiresAt > now
        if (isVerified && !stillValid) {
            // Package expired -> clear verified status so user reverts to Free tier
            prefs.edit().putBoolean(KEY_IS_VERIFIED, false).apply()
        }

        val linkedUid = prefs.getString(KEY_LINKED_UID, "").orEmpty()
        val linkedEmail = prefs.getString(KEY_LINKED_EMAIL, "").orEmpty()
        val linkedName = prefs.getString(KEY_LINKED_NAME, "").orEmpty()
        val isFreeForever = NeliFreeForeverAccountsManager.isFreeForeverEmail(linkedEmail)
        val effectiveValid = isFreeForever || stillValid
        val effectiveExpiresAt = if (isFreeForever) now + 3_153_600_000_000L else if (stillValid) expiresAt else 0L
        val requiresAuth = effectiveValid && !isFreeForever && linkedEmail.isBlank() && linkedUid.isBlank()

        // Clear any stale pending order on app initialization so opening Premium always starts at CHOOSE_PLAN
        prefs.edit()
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_PLAN_ID)
            .remove(KEY_PENDING_PHONE)
            .remove(KEY_PENDING_AMOUNT)
            .apply()

        _subscriptionState.value = PremiumSubscriptionState(
            isVerified = effectiveValid,
            planId = if (isFreeForever) "free_forever" else prefs.getString(KEY_PLAN_ID, "").orEmpty(),
            planTitle = if (isFreeForever) "Bure Milele VIP (Max 2 Vifaa)" else prefs.getString(KEY_PLAN_TITLE, "").orEmpty(),
            amountTzs = if (isFreeForever) 0 else prefs.getInt(KEY_AMOUNT_TZS, 0),
            phoneNumber = if (isFreeForever) "Free Forever (2 Devices)" else prefs.getString(KEY_PHONE, "").orEmpty(),
            orderId = if (isFreeForever) "FREE-FOREVER-VIP" else prefs.getString(KEY_ORDER_ID, "").orEmpty(),
            deviceIpAddress = liveIp.ifBlank { savedIp },
            deviceId = savedDeviceId,
            activatedAtMs = prefs.getLong(KEY_ACTIVATED_AT, now),
            expiresAtMs = effectiveExpiresAt,
            linkedUserUid = linkedUid,
            linkedUserEmail = linkedEmail,
            linkedUserName = linkedName,
            requiresPostPaymentAuth = requiresAuth,
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
    }

    private fun accountKeyPrefix(email: String): String {
        val clean = email.trim().lowercase().replace(Regex("[^a-z0-9@._-]"), "_")
        return "acc_${clean}_"
    }

    private fun saveAccountSubscriptionToPrefs(
        context: Context,
        email: String,
        state: PremiumSubscriptionState
    ) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) return
        val prefix = accountKeyPrefix(cleanEmail)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(prefix + KEY_IS_VERIFIED, state.isVerified)
            .putString(prefix + KEY_PLAN_ID, state.planId)
            .putString(prefix + KEY_PLAN_TITLE, state.planTitle)
            .putInt(prefix + KEY_AMOUNT_TZS, state.amountTzs)
            .putString(prefix + KEY_PHONE, state.phoneNumber)
            .putString(prefix + KEY_ORDER_ID, state.orderId)
            .putLong(prefix + KEY_ACTIVATED_AT, state.activatedAtMs)
            .putLong(prefix + KEY_EXPIRES_AT, state.expiresAtMs)
            .putString(prefix + KEY_LINKED_UID, state.linkedUserUid)
            .putString(prefix + KEY_LINKED_EMAIL, state.linkedUserEmail.ifBlank { email.trim() })
            .putString(prefix + KEY_LINKED_NAME, state.linkedUserName)
            .apply()
    }

    private fun loadAccountSubscriptionFromPrefs(
        context: Context,
        uid: String,
        email: String,
        realName: String
    ): PremiumSubscriptionState {
        val cleanEmail = email.trim().lowercase()
        val now = System.currentTimeMillis()
        val detectedIp = resolveDeviceIpAddress(context)
        val deviceId = resolveDeviceIdentityId(context)

        if (NeliFreeForeverAccountsManager.isFreeForeverEmail(cleanEmail)) {
            val foreverExpiry = now + 3_153_600_000_000L
            return PremiumSubscriptionState(
                isVerified = true,
                planId = "free_forever",
                planTitle = "Bure Milele VIP (Max 2 Vifaa)",
                amountTzs = 0,
                phoneNumber = "Free Forever (Max 2 Devices)",
                orderId = "FREE-FOREVER-VIP",
                deviceIpAddress = detectedIp,
                deviceId = deviceId,
                activatedAtMs = now,
                expiresAtMs = foreverExpiry,
                linkedUserUid = uid.trim().ifBlank { NeliFreeForeverAccountsManager.resolveUidForEmail(cleanEmail) },
                linkedUserEmail = cleanEmail,
                linkedUserName = realName.trim().ifBlank { NeliFreeForeverAccountsManager.resolveDisplayName(cleanEmail) },
                requiresPostPaymentAuth = false,
                pendingOrderId = "",
                pendingPlanId = "",
                pendingPhone = "",
                pendingAmountTzs = 0
            )
        }

        val prefix = accountKeyPrefix(cleanEmail)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isVerified = prefs.getBoolean(prefix + KEY_IS_VERIFIED, false)
        val expiresAt = prefs.getLong(prefix + KEY_EXPIRES_AT, 0L)
        val stillValid = isVerified && expiresAt > now
        if (isVerified && !stillValid) {
            prefs.edit().putBoolean(prefix + KEY_IS_VERIFIED, false).apply()
        }
        return PremiumSubscriptionState(
            isVerified = stillValid,
            planId = if (stillValid) prefs.getString(prefix + KEY_PLAN_ID, "").orEmpty() else "",
            planTitle = if (stillValid) prefs.getString(prefix + KEY_PLAN_TITLE, "").orEmpty() else "",
            amountTzs = if (stillValid) prefs.getInt(prefix + KEY_AMOUNT_TZS, 0) else 0,
            phoneNumber = if (stillValid) prefs.getString(prefix + KEY_PHONE, "").orEmpty() else "",
            orderId = if (stillValid) prefs.getString(prefix + KEY_ORDER_ID, "").orEmpty() else "",
            deviceIpAddress = detectedIp,
            deviceId = deviceId,
            activatedAtMs = if (stillValid) prefs.getLong(prefix + KEY_ACTIVATED_AT, 0L) else 0L,
            expiresAtMs = if (stillValid) expiresAt else 0L,
            linkedUserUid = uid.trim(),
            linkedUserEmail = email.trim(),
            linkedUserName = realName.trim(),
            requiresPostPaymentAuth = false,
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
    }

    /**
     * Switches the active subscription context whenever a user logs in, registers, or signs out on the device.
     * Every account has its own isolated subscription and payment phone number — logging in with a different
     * account on the same device NEVER reuses the previous account's subscription, phone number, or pending order.
     */
    fun switchActiveAccount(
        context: Context,
        uid: String,
        email: String,
        realName: String
    ): PremiumSubscriptionState {
        val cleanEmail = email.trim()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (cleanEmail.isBlank()) {
            // Signed out -> clear active account session and pending orders so next user starts fresh
            val detectedIp = resolveDeviceIpAddress(context)
            val deviceId = resolveDeviceIdentityId(context)
            prefs.edit()
                .putBoolean(KEY_IS_VERIFIED, false)
                .remove(KEY_PLAN_ID)
                .remove(KEY_PLAN_TITLE)
                .remove(KEY_AMOUNT_TZS)
                .remove(KEY_PHONE)
                .remove(KEY_ORDER_ID)
                .remove(KEY_ACTIVATED_AT)
                .remove(KEY_EXPIRES_AT)
                .remove(KEY_LINKED_UID)
                .remove(KEY_LINKED_EMAIL)
                .remove(KEY_LINKED_NAME)
                .putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, false)
                .remove(KEY_PENDING_ORDER_ID)
                .remove(KEY_PENDING_PLAN_ID)
                .remove(KEY_PENDING_PHONE)
                .remove(KEY_PENDING_AMOUNT)
                .apply()
            val signedOutState = PremiumSubscriptionState(
                deviceIpAddress = detectedIp,
                deviceId = deviceId
            )
            _subscriptionState.value = signedOutState
            return signedOutState
        }

        val current = _subscriptionState.value
        // If the user JUST paid without logging in (requiresPostPaymentAuth == true and no account linked yet),
        // bind that fresh payment to this account; otherwise load strictly this account's own subscription!
        if (current.isVerified && current.isActiveNow && current.requiresPostPaymentAuth && current.linkedUserEmail.isBlank()) {
            return linkUserAccountToSubscription(
                context = context,
                uid = uid,
                email = cleanEmail,
                realName = realName
            )
        }

        val accountState = loadAccountSubscriptionFromPrefs(
            context = context,
            uid = uid,
            email = cleanEmail,
            realName = realName
        )
        _subscriptionState.value = accountState
        prefs.edit()
            .putBoolean(KEY_IS_VERIFIED, accountState.isVerified)
            .putString(KEY_PLAN_ID, accountState.planId)
            .putString(KEY_PLAN_TITLE, accountState.planTitle)
            .putInt(KEY_AMOUNT_TZS, accountState.amountTzs)
            .putString(KEY_PHONE, accountState.phoneNumber)
            .putString(KEY_ORDER_ID, accountState.orderId)
            .putLong(KEY_ACTIVATED_AT, accountState.activatedAtMs)
            .putLong(KEY_EXPIRES_AT, accountState.expiresAtMs)
            .putString(KEY_LINKED_UID, accountState.linkedUserUid)
            .putString(KEY_LINKED_EMAIL, accountState.linkedUserEmail)
            .putString(KEY_LINKED_NAME, accountState.linkedUserName)
            .putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, false)
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_PLAN_ID)
            .remove(KEY_PENDING_PHONE)
            .remove(KEY_PENDING_AMOUNT)
            .apply()

        if (!accountState.isVerified) {
            ioScope.launch {
                try {
                    val dao = NeliDatabase.getInstance(context).mediaDao()
                    val existingSub = dao.getDeviceSubscriptionByUserEmail(cleanEmail)
                    val now = System.currentTimeMillis()
                    if (existingSub != null && existingSub.isVerified && existingSub.expiresAtMs > now) {
                        val restored = accountState.copy(
                            isVerified = true,
                            planId = existingSub.planId,
                            planTitle = existingSub.planTitle,
                            amountTzs = existingSub.amountTzs,
                            phoneNumber = existingSub.phoneNumber,
                            orderId = existingSub.orderId,
                            activatedAtMs = existingSub.activatedAtMs,
                            expiresAtMs = existingSub.expiresAtMs,
                            requiresPostPaymentAuth = false
                        )
                        saveAccountSubscriptionToPrefs(context, cleanEmail, restored)
                        if (_subscriptionState.value.linkedUserEmail.equals(cleanEmail, ignoreCase = true)) {
                            _subscriptionState.value = restored
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return accountState
    }

    fun refreshDeviceIp(context: Context): String {
        val detectedIp = resolveDeviceIpAddress(context)
        val deviceId = resolveDeviceIdentityId(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DEVICE_IP, detectedIp)
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()
        _subscriptionState.value = _subscriptionState.value.copy(
            deviceIpAddress = detectedIp,
            deviceId = deviceId
        )
        return detectedIp
    }

    /**
     * Checks and enforces real-time subscription status at [nowMs]:
     * - If the user paid earlier (even when channels were free) and `nowMs < expiresAtMs`, returns `true`
     *   so channels remain unlocked until `expiresAtMs`.
     * - If `nowMs >= expiresAtMs` or the user has not paid, updates state to expired/unverified and returns `false`
     *   so locked channels immediately lock until payment succeeds.
     */
    fun isPremiumMemberActive(
        nowMs: Long = System.currentTimeMillis(),
        context: Context? = null
    ): Boolean {
        val current = _subscriptionState.value
        if (current.isFreeForeverAccount) {
            return true
        }
        if (current.isVerified && current.expiresAtMs <= nowMs) {
            _subscriptionState.value = current.copy(
                isVerified = false,
                expiresAtMs = 0L,
                requiresPostPaymentAuth = false
            )
            context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                ?.edit()
                ?.putBoolean(KEY_IS_VERIFIED, false)
                ?.putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, false)
                ?.apply()
            return false
        }
        return current.isVerified && current.expiresAtMs > nowMs
    }

    /**
     * Evaluates whether the active subscription has expired as of [nowMs] and updates [_subscriptionState]
     * so UI components observing [subscriptionState] automatically re-lock channels when the subscription ends.
     */
    fun expireSubscriptionIfNeeded(
        context: Context? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        return !isPremiumMemberActive(nowMs = nowMs, context = context)
    }

    fun savePendingOrder(
        context: Context,
        plan: SubscriptionPlanType,
        phone: String,
        orderId: String
    ) {
        val detectedIp = resolveDeviceIpAddress(context)
        val deviceId = resolveDeviceIdentityId(context)
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_ORDER_ID, orderId)
            .putString(KEY_PENDING_PLAN_ID, plan.id)
            .putString(KEY_PENDING_PHONE, phone)
            .putInt(KEY_PENDING_AMOUNT, plan.amountTzs)
            .putString(KEY_DEVICE_IP, detectedIp)
            .putString(KEY_DEVICE_ID, deviceId)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            pendingOrderId = orderId,
            pendingPlanId = plan.id,
            pendingPhone = phone,
            pendingAmountTzs = plan.amountTzs,
            deviceIpAddress = detectedIp,
            deviceId = deviceId
        )
    }

    fun clearPendingOrder(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_PLAN_ID)
            .remove(KEY_PENDING_PHONE)
            .remove(KEY_PENDING_AMOUNT)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
    }

    /**
     * Activates Premium Membership ONLY after HarakaPay verifies `payment.status == "completed"`.
     * Saves real payment data bound to the user's automatic Device IP (`deviceIpAddress`) and
     * Device ID (`deviceId`) so even users who have not logged in or signed up keep their
     * verified Premium Member status and real data.
     * Also flags `requiresPostPaymentAuth = true` if the user has not yet linked an account so
     * they can log in or sign up to sync their subscription across other devices and Cast sessions.
     */
    fun activateVerifiedSubscription(
        context: Context,
        plan: SubscriptionPlanType,
        phone: String,
        verifiedOrderId: String
    ): PremiumSubscriptionState {
        val now = System.currentTimeMillis()
        val current = _subscriptionState.value
        val detectedIp = resolveDeviceIpAddress(context)
        val deviceId = resolveDeviceIdentityId(context)
        // If user already had active time remaining, extend from current expiry; otherwise from now
        val baseStart = if (current.isActiveNow && current.expiresAtMs > now) current.expiresAtMs else now
        val newExpiresAt = baseStart + plan.durationMillis

        val needsPostPaymentAuth = current.linkedUserEmail.isBlank() && current.linkedUserUid.isBlank()

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_VERIFIED, true)
            .putString(KEY_PLAN_ID, plan.id)
            .putString(KEY_PLAN_TITLE, plan.titleSwahili)
            .putInt(KEY_AMOUNT_TZS, plan.amountTzs)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ORDER_ID, verifiedOrderId)
            .putString(KEY_DEVICE_IP, detectedIp)
            .putString(KEY_DEVICE_ID, deviceId)
            .putLong(KEY_ACTIVATED_AT, now)
            .putLong(KEY_EXPIRES_AT, newExpiresAt)
            .putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, needsPostPaymentAuth)
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_PLAN_ID)
            .remove(KEY_PENDING_PHONE)
            .remove(KEY_PENDING_AMOUNT)
            .apply()

        val newState = PremiumSubscriptionState(
            isVerified = true,
            planId = plan.id,
            planTitle = plan.titleSwahili,
            amountTzs = plan.amountTzs,
            phoneNumber = phone,
            orderId = verifiedOrderId,
            deviceIpAddress = detectedIp,
            deviceId = deviceId,
            activatedAtMs = now,
            expiresAtMs = newExpiresAt,
            linkedUserUid = current.linkedUserUid,
            linkedUserEmail = current.linkedUserEmail,
            linkedUserName = current.linkedUserName,
            requiresPostPaymentAuth = needsPostPaymentAuth,
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
        _subscriptionState.value = newState
        if (newState.linkedUserEmail.isNotBlank()) {
            saveAccountSubscriptionToPrefs(context, newState.linkedUserEmail, newState)
        }
        NeliRealtimeAnalyticsManager.recordHarakaPayTransaction(
            context = context,
            orderId = verifiedOrderId,
            phoneNumber = phone,
            plan = plan,
            status = "COMPLETED",
            userEmail = newState.linkedUserEmail
        )

        ioScope.launch {
            try {
                val dao = NeliDatabase.getInstance(context).mediaDao()
                val activeUser = dao.getActiveUserOnce()
                if (activeUser != null && activeUser.email.isNotBlank()) {
                    linkUserAccountToSubscription(
                        context = context,
                        uid = activeUser.uid,
                        email = activeUser.email,
                        realName = activeUser.realName
                    )
                } else {
                    saveRealSubscriptionDataByDeviceIp(dao, newState)
                }
            } catch (_: Throwable) {
            }
        }
        return newState
    }

    /**
     * Links a logged-in or newly signed-up user account to the active subscription
     * (only if the subscription was unlinked or already belongs to this same account).
     * If a DIFFERENT user account logs in on the same device, switches to that account's
     * own isolated subscription state so accounts never share payment phone numbers or subscriptions.
     */
    fun linkUserAccountToSubscription(
        context: Context? = null,
        uid: String,
        email: String,
        realName: String
    ): PremiumSubscriptionState {
        val cleanEmail = email.trim()
        val current = _subscriptionState.value

        // If the current in-memory subscription belongs to a DIFFERENT account, switch to this account's own state!
        if (context != null &&
            cleanEmail.isNotBlank() &&
            current.linkedUserEmail.isNotBlank() &&
            !current.linkedUserEmail.equals(cleanEmail, ignoreCase = true)
        ) {
            return switchActiveAccount(
                context = context,
                uid = uid,
                email = cleanEmail,
                realName = realName
            )
        }

        val updated = current.copy(
            linkedUserUid = uid.trim(),
            linkedUserEmail = cleanEmail,
            linkedUserName = realName.trim(),
            requiresPostPaymentAuth = false,
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
        _subscriptionState.value = updated

        if (context != null) {
            if (updated.isVerified && cleanEmail.isNotBlank()) {
                saveAccountSubscriptionToPrefs(context, cleanEmail, updated)
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_LINKED_UID, updated.linkedUserUid)
                .putString(KEY_LINKED_EMAIL, updated.linkedUserEmail)
                .putString(KEY_LINKED_NAME, updated.linkedUserName)
                .putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, false)
                .remove(KEY_PENDING_ORDER_ID)
                .remove(KEY_PENDING_PLAN_ID)
                .remove(KEY_PENDING_PHONE)
                .remove(KEY_PENDING_AMOUNT)
                .apply()

            ioScope.launch {
                try {
                    val dao = NeliDatabase.getInstance(context).mediaDao()
                    if (updated.isVerified) {
                        saveRealSubscriptionDataByDeviceIp(dao, updated)
                    } else if (cleanEmail.isNotBlank()) {
                        val existingSub = dao.getDeviceSubscriptionByUserEmail(cleanEmail)
                        val now = System.currentTimeMillis()
                        if (existingSub != null && existingSub.isVerified && existingSub.expiresAtMs > now) {
                            val restored = updated.copy(
                                isVerified = true,
                                planId = existingSub.planId,
                                planTitle = existingSub.planTitle,
                                amountTzs = existingSub.amountTzs,
                                phoneNumber = existingSub.phoneNumber,
                                orderId = existingSub.orderId,
                                activatedAtMs = existingSub.activatedAtMs,
                                expiresAtMs = existingSub.expiresAtMs,
                                requiresPostPaymentAuth = false
                            )
                            saveAccountSubscriptionToPrefs(context, cleanEmail, restored)
                            _subscriptionState.value = restored
                            prefs.edit()
                                .putBoolean(KEY_IS_VERIFIED, true)
                                .putString(KEY_PLAN_ID, restored.planId)
                                .putString(KEY_PLAN_TITLE, restored.planTitle)
                                .putInt(KEY_AMOUNT_TZS, restored.amountTzs)
                                .putString(KEY_PHONE, restored.phoneNumber)
                                .putString(KEY_ORDER_ID, restored.orderId)
                                .putLong(KEY_ACTIVATED_AT, restored.activatedAtMs)
                                .putLong(KEY_EXPIRES_AT, restored.expiresAtMs)
                                .apply()
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return updated
    }

    suspend fun saveRealSubscriptionDataByDeviceIp(
        dao: NeliMediaDao,
        state: PremiumSubscriptionState = _subscriptionState.value
    ): DeviceSubscriptionEntity {
        val ip = state.deviceIpAddress.ifBlank { resolveDeviceIpAddress(null) }
        val devId = state.deviceId.ifBlank { resolveDeviceIdentityId(null) }
        val entity = DeviceSubscriptionEntity(
            deviceIpAddress = ip,
            deviceId = devId,
            isVerified = state.isVerified,
            planId = state.planId,
            planTitle = state.planTitle,
            amountTzs = state.amountTzs,
            phoneNumber = state.phoneNumber,
            orderId = state.orderId,
            activatedAtMs = state.activatedAtMs,
            expiresAtMs = state.expiresAtMs,
            linkedUserUid = state.linkedUserUid,
            linkedUserEmail = state.linkedUserEmail,
            linkedUserName = state.linkedUserName,
            updatedAtMs = System.currentTimeMillis()
        )
        dao.upsertDeviceSubscription(entity)
        return entity
    }

    suspend fun restoreFromDatabaseByDeviceIp(
        context: Context,
        dao: NeliMediaDao
    ): Boolean {
        val now = System.currentTimeMillis()
        val ip = _subscriptionState.value.deviceIpAddress.ifBlank { resolveDeviceIpAddress(context) }
        val existing = dao.getDeviceSubscriptionByIp(ip) ?: dao.getLatestDeviceSubscription()
        if (existing != null && existing.isVerified && existing.expiresAtMs > now) {
            val restored = _subscriptionState.value.copy(
                isVerified = true,
                planId = existing.planId,
                planTitle = existing.planTitle,
                amountTzs = existing.amountTzs,
                phoneNumber = existing.phoneNumber,
                orderId = existing.orderId,
                deviceIpAddress = existing.deviceIpAddress.ifBlank { ip },
                deviceId = existing.deviceId,
                activatedAtMs = existing.activatedAtMs,
                expiresAtMs = existing.expiresAtMs,
                linkedUserUid = existing.linkedUserUid,
                linkedUserEmail = existing.linkedUserEmail,
                linkedUserName = existing.linkedUserName,
                requiresPostPaymentAuth = existing.linkedUserEmail.isBlank() && existing.linkedUserUid.isBlank()
            )
            _subscriptionState.value = restored
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_IS_VERIFIED, true)
                .putString(KEY_PLAN_ID, restored.planId)
                .putString(KEY_PLAN_TITLE, restored.planTitle)
                .putInt(KEY_AMOUNT_TZS, restored.amountTzs)
                .putString(KEY_PHONE, restored.phoneNumber)
                .putString(KEY_ORDER_ID, restored.orderId)
                .putLong(KEY_ACTIVATED_AT, restored.activatedAtMs)
                .putLong(KEY_EXPIRES_AT, restored.expiresAtMs)
                .putString(KEY_LINKED_UID, restored.linkedUserUid)
                .putString(KEY_LINKED_EMAIL, restored.linkedUserEmail)
                .putString(KEY_LINKED_NAME, restored.linkedUserName)
                .putBoolean(KEY_REQUIRES_POST_PAYMENT_AUTH, restored.requiresPostPaymentAuth)
                .apply()
            return true
        }
        return false
    }

    fun resetForTesting(context: Context? = null) {
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.commit()
        _subscriptionState.value = PremiumSubscriptionState(
            deviceIpAddress = resolveDeviceIpAddress(context),
            deviceId = resolveDeviceIdentityId(context)
        )
    }
}
