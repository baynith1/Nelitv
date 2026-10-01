package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HarakaPayCollectResponse(
    val success: Boolean,
    val message: String,
    val orderId: String,
    val amount: Int,
    val netAmount: Int,
    val fee: Int,
    val errorMessage: String = ""
)

data class HarakaPayStatusResponse(
    val success: Boolean,
    val orderId: String,
    val status: String, // "completed", "pending", "failed", etc.
    val amount: Int,
    val netAmount: Int,
    val feeAmount: Int,
    val createdAt: String,
    val completedAt: String,
    val errorMessage: String = ""
) {
    val isCompleted: Boolean
        get() = status.equals("completed", ignoreCase = true) ||
            status.equals("paid", ignoreCase = true) ||
            status.equals("success", ignoreCase = true)

    val isFailed: Boolean
        get() = status.equals("failed", ignoreCase = true) ||
            status.equals("cancelled", ignoreCase = true) ||
            status.equals("canceled", ignoreCase = true) ||
            status.equals("expired", ignoreCase = true) ||
            status.equals("rejected", ignoreCase = true)
}

data class HarakaPayBalanceResponse(
    val success: Boolean,
    val walletBalance: Long,
    val floatBalance: Long,
    val errorMessage: String = ""
)

/**
 * Real HarakaPay API client (`https://harakapay.net`) for collecting TZS mobile money payments
 * via USSD push and verifying payment completion status.
 *
 * Endpoints:
 * 1. POST `https://harakapay.net/api/v1/collect`
 * 2. GET  `https://harakapay.net/api/v1/status/{order_id}`
 * 3. GET  `https://harakapay.net/api/v1/balance`
 */
object HarakaPayRepository {

    const val BASE_URL = PaymentService.DEFAULT_BASE_URL

    /**
     * Test hook for unit/Robolectric tests to intercept network requests deterministically without
     * affecting real production HTTP calls.
     */
    @Volatile
    var testCollectInterceptor: ((phone: String, amount: Int, description: String) -> HarakaPayCollectResponse)? = null

    @Volatile
    var testStatusInterceptor: ((orderId: String) -> HarakaPayStatusResponse)? = null

    val paymentService: PaymentService
        get() = PaymentService(apiKey = resolveApiKey(), baseUrl = BASE_URL)

    fun resolveApiKey(): String = PaymentService.resolveConfiguredApiKey()

    /**
     * Normalizes Tanzanian mobile phone numbers into the `07XXXXXXXX` / `06XXXXXXXX` format expected by HarakaPay.
     */
    fun normalizeTzPhoneNumber(rawPhone: String): String =
        PaymentService.normalizePhoneNumber(rawPhone)

    fun isValidTzPhoneNumber(rawPhone: String): Boolean =
        PaymentService.isValidPhoneNumber(rawPhone)

    /**
     * Sends a USSD push payment collection request to the customer's phone (`POST /api/v1/collect`)
     * via [PaymentService].
     */
    suspend fun collectPayment(
        phone: String,
        amount: Int,
        description: String
    ): Result<HarakaPayCollectResponse> =
        paymentService.initiateUssdPushPayment(
            phone = phone,
            amount = amount,
            description = description
        )

    /**
     * Checks payment verification status on HarakaPay (`GET /api/v1/status/{order_id}`)
     * via [PaymentService].
     */
    suspend fun checkPaymentStatus(orderId: String): Result<HarakaPayStatusResponse> =
        paymentService.verifyOrderStatus(orderId)

    /**
     * Fetches wallet and float balance from HarakaPay (`GET /api/v1/balance`)
     * via [PaymentService].
     */
    suspend fun getBalance(): Result<HarakaPayBalanceResponse> =
        paymentService.getBalance()
}
