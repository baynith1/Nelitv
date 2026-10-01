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

    const val BASE_URL = "https://harakapay.net"
    private const val FALLBACK_PROTOTYPE_KEY = "hpk_93b63ba05db51f1963b174570c71762a73541195bdbc5538"

    /**
     * Test hook for unit/Robolectric tests to intercept network requests deterministically without
     * affecting real production HTTP calls.
     */
    @Volatile
    var testCollectInterceptor: ((phone: String, amount: Int, description: String) -> HarakaPayCollectResponse)? = null

    @Volatile
    var testStatusInterceptor: ((orderId: String) -> HarakaPayStatusResponse)? = null

    fun resolveApiKey(): String {
        val fromBuildConfig = try {
            BuildConfig.HARAKAPAY_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        return if (fromBuildConfig.isNotBlank() && fromBuildConfig != "YOUR_HARAKAPAY_API_KEY") {
            fromBuildConfig
        } else {
            FALLBACK_PROTOTYPE_KEY
        }
    }

    /**
     * Normalizes Tanzanian mobile phone numbers into the `07XXXXXXXX` / `06XXXXXXXX` format expected by HarakaPay.
     * Examples:
     * - `+255 712 345 678` -> `0712345678`
     * - `255712345678` -> `0712345678`
     * - `0712345678` -> `0712345678`
     * - `712345678` -> `0712345678`
     */
    fun normalizeTzPhoneNumber(rawPhone: String): String {
        val digitsOnly = rawPhone.trim().filter { it.isDigit() }
        return when {
            digitsOnly.startsWith("255") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(3)
            digitsOnly.length == 9 && (digitsOnly.startsWith("7") || digitsOnly.startsWith("6")) -> "0$digitsOnly"
            else -> digitsOnly
        }
    }

    fun isValidTzPhoneNumber(rawPhone: String): Boolean {
        val normalized = normalizeTzPhoneNumber(rawPhone)
        return normalized.length == 10 && (normalized.startsWith("07") || normalized.startsWith("06"))
    }

    /**
     * Sends a USSD push payment collection request to the customer's phone (`POST /api/v1/collect`).
     */
    suspend fun collectPayment(
        phone: String,
        amount: Int,
        description: String
    ): Result<HarakaPayCollectResponse> = withContext(Dispatchers.IO) {
        val normalizedPhone = normalizeTzPhoneNumber(phone)
        if (!isValidTzPhoneNumber(normalizedPhone)) {
            return@withContext Result.failure(
                IllegalArgumentException("Tafadhali weka namba sahihi ya simu ya Tanzania (mfano: 0712345678 au 0655123456).")
            )
        }
        if (amount < 100) {
            return@withContext Result.failure(
                IllegalArgumentException("Kiasi cha chini cha malipo ni 100 TSh.")
            )
        }

        testCollectInterceptor?.let { interceptor ->
            val resp = interceptor(normalizedPhone, amount, description)
            return@withContext if (resp.success && resp.orderId.isNotBlank()) {
                Result.success(resp)
            } else {
                Result.failure(IllegalStateException(resp.errorMessage.ifBlank { resp.message.ifBlank { "Malipo yameshindikana kuanzishwa." } }))
            }
        }

        try {
            val apiKey = resolveApiKey()
            val url = URL("$BASE_URL/api/v1/collect")
            val payload = JSONObject().apply {
                put("phone", normalizedPhone)
                put("amount", amount)
                put("description", description)
            }

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", apiKey)
            }

            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            val rawBody = try {
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (rawBody.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("HarakaPay haijajibu (HTTP $responseCode). Hakikisha una internet kisha jaribu tena.")
                )
            }

            val json = JSONObject(rawBody)
            val success = json.optBoolean("success", false)
            val message = json.optString("message", "")
            val errorMsg = json.optString("error", json.optString("detail", message))
            val orderId = json.optString("order_id", "").trim()
            val respAmount = json.optInt("amount", amount)
            val netAmount = json.optInt("net_amount", respAmount)
            val fee = json.optInt("fee", (respAmount - netAmount).coerceAtLeast(0))

            if (success && orderId.isNotBlank()) {
                Result.success(
                    HarakaPayCollectResponse(
                        success = true,
                        message = message.ifBlank { "USSD push imetumwa kwenye simu yako ($normalizedPhone)." },
                        orderId = orderId,
                        amount = respAmount,
                        netAmount = netAmount,
                        fee = fee
                    )
                )
            } else {
                Result.failure(
                    IllegalStateException(
                        errorMsg.ifBlank { "Imeshindikana kutuma USSD push kwenye namba $normalizedPhone. Jaribu tena." }
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Tatizo la mtandao wakati wa kuwasiliana na HarakaPay: ${e.localizedMessage ?: "Angalia internet yako"}"
                )
            )
        }
    }

    /**
     * Checks payment verification status on HarakaPay (`GET /api/v1/status/{order_id}`).
     * Only when `payment.status == "completed"` is the user verified as paid.
     */
    suspend fun checkPaymentStatus(orderId: String): Result<HarakaPayStatusResponse> = withContext(Dispatchers.IO) {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Order ID haipo."))
        }

        testStatusInterceptor?.let { interceptor ->
            val resp = interceptor(cleanOrderId)
            return@withContext if (resp.success) {
                Result.success(resp)
            } else {
                Result.failure(IllegalStateException(resp.errorMessage.ifBlank { "Imeshindikana kuhakiki malipo." }))
            }
        }

        try {
            val apiKey = resolveApiKey()
            val url = URL("$BASE_URL/api/v1/status/$cleanOrderId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                doInput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", apiKey)
            }

            val responseCode = conn.responseCode
            val rawBody = try {
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (rawBody.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Imeshindikana kusoma hali ya malipo (HTTP $responseCode).")
                )
            }

            val json = JSONObject(rawBody)
            val success = json.optBoolean("success", responseCode in 200..299)
            val paymentObj = json.optJSONObject("payment") ?: json
            val statusStr = paymentObj.optString("status", json.optString("status", "pending")).trim().lowercase()
            val parsedOrderId = paymentObj.optString("order_id", cleanOrderId).ifBlank { cleanOrderId }
            val amount = paymentObj.optInt("amount", 0)
            val netAmount = paymentObj.optInt("net_amount", 0)
            val feeAmount = paymentObj.optInt("fee_amount", paymentObj.optInt("fee", 0))
            val createdAt = paymentObj.optString("created_at", "")
            val completedAt = paymentObj.optString("completed_at", "")
            val errorMsg = json.optString("error", json.optString("message", ""))

            Result.success(
                HarakaPayStatusResponse(
                    success = success,
                    orderId = parsedOrderId,
                    status = statusStr,
                    amount = amount,
                    netAmount = netAmount,
                    feeAmount = feeAmount,
                    createdAt = createdAt,
                    completedAt = completedAt,
                    errorMessage = errorMsg
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Imeshindikana kuhakiki malipo kwenye HarakaPay: ${e.localizedMessage ?: "Angalia internet yako"}"
                )
            )
        }
    }

    /**
     * Fetches wallet and float balance from HarakaPay (`GET /api/v1/balance`).
     */
    suspend fun getBalance(): Result<HarakaPayBalanceResponse> = withContext(Dispatchers.IO) {
        try {
            val apiKey = resolveApiKey()
            val url = URL("$BASE_URL/api/v1/balance")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                doInput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", apiKey)
            }

            val responseCode = conn.responseCode
            val rawBody = try {
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            } catch (_: Exception) {
                ""
            }

            if (rawBody.isBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("Imeshindikana kupata salio (HTTP $responseCode).")
                )
            }

            val json = JSONObject(rawBody)
            val success = json.optBoolean("success", false)
            val walletBalance = json.optLong("wallet_balance", 0L)
            val floatBalance = json.optLong("float_balance", 0L)
            val errorMsg = json.optString("error", json.optString("message", ""))

            if (success) {
                Result.success(
                    HarakaPayBalanceResponse(
                        success = true,
                        walletBalance = walletBalance,
                        floatBalance = floatBalance
                    )
                )
            } else {
                Result.failure(IllegalStateException(errorMsg.ifBlank { "Imeshindikana kupata salio la HarakaPay." }))
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Tatizo la mtandao: ${e.localizedMessage ?: "Jaribu tena"}"))
        }
    }
}
