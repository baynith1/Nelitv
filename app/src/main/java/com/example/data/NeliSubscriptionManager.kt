package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscription plans available in the Premium tab:
 * - Kwa Siku: 500 TSh (24 hours)
 * - Kwa Wiki: 3,000 TSh (7 days)
 * - Kwa Mwezi: 10,000 TSh (30 days)
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
        id = "DAILY_500",
        titleSwahili = "Kwa Siku",
        subtitleSwahili = "Fungua channel zote za VIP kwa saa 24",
        amountTzs = 500,
        priceFormatted = "500 TSh",
        durationMillis = 24L * 60L * 60L * 1000L,
        durationLabel = "Siku 1 (Saa 24)",
        badgeText = "MAARUFU KWA SIKU"
    ),
    WEEKLY(
        id = "WEEKLY_3000",
        titleSwahili = "Kwa Wiki",
        subtitleSwahili = "Fungua channel zote za VIP kwa siku 7 mfululizo",
        amountTzs = 3000,
        priceFormatted = "3,000 TSh",
        durationMillis = 7L * 24L * 60L * 60L * 1000L,
        durationLabel = "Wiki 1 (Siku 7)",
        badgeText = "OFA BORA YA WIKI"
    ),
    MONTHLY(
        id = "MONTHLY_10000",
        titleSwahili = "Kwa Mwezi",
        subtitleSwahili = "Fungua channel zote za VIP kwa siku 30 bila kikomo",
        amountTzs = 10000,
        priceFormatted = "10,000 TSh",
        durationMillis = 30L * 24L * 60L * 60L * 1000L,
        durationLabel = "Mwezi 1 (Siku 30)",
        badgeText = "VIP FULL ACCESS"
    )
}

data class PremiumSubscriptionState(
    val isVerified: Boolean = false,
    val planId: String = "",
    val planTitle: String = "",
    val amountTzs: Int = 0,
    val phoneNumber: String = "",
    val orderId: String = "",
    val activatedAtMs: Long = 0L,
    val expiresAtMs: Long = 0L,
    val pendingOrderId: String = "",
    val pendingPlanId: String = "",
    val pendingPhone: String = "",
    val pendingAmountTzs: Int = 0
) {
    val isActiveNow: Boolean
        get() = isVerified && expiresAtMs > System.currentTimeMillis()

    val formattedExpiryDate: String
        get() {
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
    private const val KEY_ACTIVATED_AT = "activated_at_ms"
    private const val KEY_EXPIRES_AT = "expires_at_ms"

    private const val KEY_PENDING_ORDER_ID = "pending_order_id"
    private const val KEY_PENDING_PLAN_ID = "pending_plan_id"
    private const val KEY_PENDING_PHONE = "pending_phone"
    private const val KEY_PENDING_AMOUNT = "pending_amount_tzs"

    private val _subscriptionState = MutableStateFlow(PremiumSubscriptionState())
    val subscriptionState: StateFlow<PremiumSubscriptionState> = _subscriptionState.asStateFlow()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isVerified = prefs.getBoolean(KEY_IS_VERIFIED, false)
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        val now = System.currentTimeMillis()

        val stillValid = isVerified && expiresAt > now
        if (isVerified && !stillValid) {
            // Package expired -> clear verified status so user reverts to Free tier
            prefs.edit().putBoolean(KEY_IS_VERIFIED, false).apply()
        }

        _subscriptionState.value = PremiumSubscriptionState(
            isVerified = stillValid,
            planId = prefs.getString(KEY_PLAN_ID, "").orEmpty(),
            planTitle = prefs.getString(KEY_PLAN_TITLE, "").orEmpty(),
            amountTzs = prefs.getInt(KEY_AMOUNT_TZS, 0),
            phoneNumber = prefs.getString(KEY_PHONE, "").orEmpty(),
            orderId = prefs.getString(KEY_ORDER_ID, "").orEmpty(),
            activatedAtMs = prefs.getLong(KEY_ACTIVATED_AT, 0L),
            expiresAtMs = if (stillValid) expiresAt else 0L,
            pendingOrderId = prefs.getString(KEY_PENDING_ORDER_ID, "").orEmpty(),
            pendingPlanId = prefs.getString(KEY_PENDING_PLAN_ID, "").orEmpty(),
            pendingPhone = prefs.getString(KEY_PENDING_PHONE, "").orEmpty(),
            pendingAmountTzs = prefs.getInt(KEY_PENDING_AMOUNT, 0)
        )
    }

    fun isPremiumMemberActive(): Boolean {
        val current = _subscriptionState.value
        if (current.isVerified && current.expiresAtMs <= System.currentTimeMillis()) {
            _subscriptionState.value = current.copy(isVerified = false, expiresAtMs = 0L)
            return false
        }
        return current.isActiveNow
    }

    fun savePendingOrder(
        context: Context,
        plan: SubscriptionPlanType,
        phone: String,
        orderId: String
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_ORDER_ID, orderId)
            .putString(KEY_PENDING_PLAN_ID, plan.id)
            .putString(KEY_PENDING_PHONE, phone)
            .putInt(KEY_PENDING_AMOUNT, plan.amountTzs)
            .apply()

        _subscriptionState.value = _subscriptionState.value.copy(
            pendingOrderId = orderId,
            pendingPlanId = plan.id,
            pendingPhone = phone,
            pendingAmountTzs = plan.amountTzs
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
     */
    fun activateVerifiedSubscription(
        context: Context,
        plan: SubscriptionPlanType,
        phone: String,
        verifiedOrderId: String
    ): PremiumSubscriptionState {
        val now = System.currentTimeMillis()
        val current = _subscriptionState.value
        // If user already had active time remaining, extend from current expiry; otherwise from now
        val baseStart = if (current.isActiveNow && current.expiresAtMs > now) current.expiresAtMs else now
        val newExpiresAt = baseStart + plan.durationMillis

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_IS_VERIFIED, true)
            .putString(KEY_PLAN_ID, plan.id)
            .putString(KEY_PLAN_TITLE, plan.titleSwahili)
            .putInt(KEY_AMOUNT_TZS, plan.amountTzs)
            .putString(KEY_PHONE, phone)
            .putString(KEY_ORDER_ID, verifiedOrderId)
            .putLong(KEY_ACTIVATED_AT, now)
            .putLong(KEY_EXPIRES_AT, newExpiresAt)
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
            activatedAtMs = now,
            expiresAtMs = newExpiresAt,
            pendingOrderId = "",
            pendingPlanId = "",
            pendingPhone = "",
            pendingAmountTzs = 0
        )
        _subscriptionState.value = newState
        return newState
    }

    fun resetForTesting(context: Context? = null) {
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()?.clear()?.commit()
        _subscriptionState.value = PremiumSubscriptionState()
    }
}
