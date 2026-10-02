package com.example.data

import com.example.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Structured exception representing an API or HTTP error returned by HarakaPay (`https://harakapay.net`).
 */
class HarakaPayApiException(
    override val message: String,
    val httpStatusCode: Int = -1,
    val errorCode: String = "",
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Low-level HTTP response container for [PaymentService] so both real [HttpURLConnection] calls
 * and unit/Robolectric tests can inspect headers, status codes, and response bodies.
 */
data class PaymentHttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * Pluggable HTTP transport for [PaymentService]. Defaults to real [HttpURLConnection] over HTTPS.
 */
fun interface PaymentHttpTransport {
    @Throws(IOException::class)
    fun execute(
        method: String,
        url: String,
        headers: Map<String, String>,
        requestBody: String?
    ): PaymentHttpResponse
}

/**
 * Service class that communicates with the HarakaPay API (`https://harakapay.net`) to:
 * 1. Initiate USSD push payments (`POST /api/v1/collect`)
 * 2. Verify order payment status (`GET /api/v1/status/{order_id}`)
 * 3. Query merchant wallet & float balance (`GET /api/v1/balance`)
 *
 * Includes required authentication header (`X-API-Key`), JSON content headers, phone/amount validation,
 * and structured error handling for HTTP and API-level error responses.
 */
class PaymentService(
    private val apiKey: String = resolveConfiguredApiKey(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val webhookUrl: String? = null,
    private val httpTransport: PaymentHttpTransport = DefaultHttpUrlConnectionTransport
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://harakapay.net"
        const val HEADER_API_KEY = "X-API-Key"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val CONTENT_TYPE_JSON = "application/json"
        const val MIN_PAYMENT_AMOUNT_TZS = 100

        private const val DEFAULT_PROTOTYPE_API_KEY =
            "hpk_93b63ba05db51f1963b174570c71762a73541195bdbc5538"

        /**
         * Resolves the HarakaPay API Key from `BuildConfig.HARAKAPAY_API_KEY` (configured via the
         * AI Studio Secrets panel / `.env`), falling back to the configured key when placeholder is present.
         */
        fun resolveConfiguredApiKey(): String {
            val fromBuildConfig = try {
                BuildConfig.HARAKAPAY_API_KEY.trim()
            } catch (_: Throwable) {
                ""
            }
            return if (
                fromBuildConfig.isNotBlank() &&
                fromBuildConfig != "YOUR_HARAKAPAY_API_KEY" &&
                fromBuildConfig != "HARAKAPAY_API_KEY_DEFAULT_VALUE"
            ) {
                fromBuildConfig
            } else {
                DEFAULT_PROTOTYPE_API_KEY
            }
        }

        /**
         * Normalizes Tanzanian phone numbers into the `07XXXXXXXX` or `06XXXXXXXX` 10-digit format
         * required by HarakaPay.
         */
        fun normalizePhoneNumber(rawPhone: String): String {
            val digitsOnly = rawPhone.trim().filter { it.isDigit() }
            return when {
                digitsOnly.startsWith("255") && digitsOnly.length == 12 -> "0" + digitsOnly.substring(3)
                digitsOnly.length == 9 && (digitsOnly.startsWith("7") || digitsOnly.startsWith("6")) -> "0$digitsOnly"
                else -> digitsOnly
            }
        }

        fun isValidPhoneNumber(rawPhone: String): Boolean {
            val normalized = normalizePhoneNumber(rawPhone)
            return normalized.length == 10 && (normalized.startsWith("07") || normalized.startsWith("06"))
        }

        private val prewarmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        @Volatile
        private var lastPrewarmAtMs: Long = 0L

        /**
         * Shared high-speed OkHttpClient with a persistent keep-alive connection pool and HTTP/2
         * multiplexing so `https://harakapay.net` requests (`collect` & `status`) execute with
         * near-zero TLS handshake latency.
         */
        val turboHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(6, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        /**
         * Pre-warms the DNS + TCP + TLS connection to `https://harakapay.net` in the background
         * as soon as the user opens the Premium screen or enters their phone number, ensuring
         * instant USSD push dispatch when they tap Pay.
         */
        fun prewarmHarakaPayConnection(baseUrl: String = DEFAULT_BASE_URL) {
            val now = System.currentTimeMillis()
            if (now - lastPrewarmAtMs < 30_000L) return
            lastPrewarmAtMs = now
            prewarmScope.launch {
                try {
                    val req = Request.Builder()
                        .url("${baseUrl.trimEnd('/')}/api/v1/balance")
                        .head()
                        .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                        .header("Connection", "keep-alive")
                        .build()
                    turboHttpClient.newCall(req).execute().close()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Builds the required HTTP headers for every HarakaPay API request, including `X-API-Key`.
     */
    fun buildHeaders(includeJsonContentType: Boolean = false): Map<String, String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            throw HarakaPayApiException(
                message = "HarakaPay API key haipo (Missing X-API-Key). Tafadhali weka HARAKAPAY_API_KEY kwenye Secrets.",
                httpStatusCode = 401,
                errorCode = "missing_api_key"
            )
        }
        val headers = LinkedHashMap<String, String>()
        if (includeJsonContentType) {
            headers[HEADER_CONTENT_TYPE] = CONTENT_TYPE_JSON
        }
        headers[HEADER_ACCEPT] = CONTENT_TYPE_JSON
        headers[HEADER_API_KEY] = cleanKey
        return headers
    }

    /**
     * Initiates a USSD push payment request to the customer's mobile phone via
     * `POST https://harakapay.net/api/v1/collect`.
     *
     * @param phone Customer phone number in Tanzania (e.g. `0712345678` or `+255712345678`)
     * @param amount Payment amount in TZS (minimum 100 TZS)
     * @param description Payment description (e.g. `"Nelitv Premium (Kwa Wiki)"`)
     * @param customWebhookUrl Optional webhook callback URL for status notifications
     */
    suspend fun initiateUssdPushPayment(
        phone: String,
        amount: Int,
        description: String = "",
        customWebhookUrl: String? = webhookUrl
    ): Result<HarakaPayCollectResponse> = withContext(Dispatchers.IO) {
        initiateUssdPushPaymentBlocking(
            phone = phone,
            amount = amount,
            description = description,
            customWebhookUrl = customWebhookUrl
        )
    }

    /**
     * Alias for [initiateUssdPushPayment] matching HarakaPay's `collectPayment` naming.
     */
    suspend fun collectPayment(
        phone: String,
        amount: Int,
        description: String = "",
        customWebhookUrl: String? = webhookUrl
    ): Result<HarakaPayCollectResponse> = initiateUssdPushPayment(
        phone = phone,
        amount = amount,
        description = description,
        customWebhookUrl = customWebhookUrl
    )

    fun initiateUssdPushPaymentBlocking(
        phone: String,
        amount: Int,
        description: String = "",
        customWebhookUrl: String? = webhookUrl
    ): Result<HarakaPayCollectResponse> {
        val normalizedPhone = normalizePhoneNumber(phone)
        if (!isValidPhoneNumber(normalizedPhone)) {
            return Result.failure(
                HarakaPayApiException(
                    message = "Tafadhali weka namba sahihi ya simu ya Tanzania (mfano: 0712345678 au 0655123456).",
                    httpStatusCode = 400,
                    errorCode = "invalid_phone"
                )
            )
        }
        if (amount < MIN_PAYMENT_AMOUNT_TZS) {
            return Result.failure(
                HarakaPayApiException(
                    message = "Kiasi cha chini cha malipo ni $MIN_PAYMENT_AMOUNT_TZS TSh.",
                    httpStatusCode = 400,
                    errorCode = "invalid_amount"
                )
            )
        }

        // Allow test interceptor hook on HarakaPayRepository if set by existing tests
        HarakaPayRepository.testCollectInterceptor?.let { interceptor ->
            val resp = interceptor(normalizedPhone, amount, description)
            return if (resp.success && resp.orderId.isNotBlank()) {
                Result.success(resp)
            } else {
                Result.failure(
                    HarakaPayApiException(
                        message = resp.errorMessage.ifBlank {
                            resp.message.ifBlank { "Malipo yameshindikana kuanzishwa." }
                        },
                        httpStatusCode = 400,
                        errorCode = "collect_failed"
                    )
                )
            }
        }

        return try {
            val headers = buildHeaders(includeJsonContentType = true)
            val endpointUrl = "${baseUrl.trimEnd('/')}/api/v1/collect"
            val payload = JSONObject().apply {
                put("phone", normalizedPhone)
                put("amount", amount)
                if (description.isNotBlank()) {
                    put("description", description.trim())
                } else {
                    put("description", "Nelitv Subscription")
                }
                if (!customWebhookUrl.isNullOrBlank()) {
                    put("webhook_url", customWebhookUrl.trim())
                }
            }

            val httpResponse = httpTransport.execute(
                method = "POST",
                url = endpointUrl,
                headers = headers,
                requestBody = payload.toString()
            )

            parseCollectResponse(httpResponse, normalizedPhone, amount)
        } catch (apiEx: HarakaPayApiException) {
            Result.failure(apiEx)
        } catch (e: Exception) {
            Result.failure(
                HarakaPayApiException(
                    message = "Tatizo la mtandao wakati wa kuwasiliana na HarakaPay: ${e.localizedMessage ?: "Angalia internet yako"}",
                    errorCode = "network_error",
                    cause = e
                )
            )
        }
    }

    /**
     * Verifies the status of a HarakaPay order via `GET https://harakapay.net/api/v1/status/{order_id}`.
     */
    suspend fun verifyOrderStatus(orderId: String): Result<HarakaPayStatusResponse> =
        withContext(Dispatchers.IO) {
            verifyOrderStatusBlocking(orderId)
        }

    /**
     * Alias for [verifyOrderStatus] matching HarakaPay's `checkStatus` naming.
     */
    suspend fun checkStatus(orderId: String): Result<HarakaPayStatusResponse> =
        verifyOrderStatus(orderId)

    fun verifyOrderStatusBlocking(orderId: String): Result<HarakaPayStatusResponse> {
        val cleanOrderId = orderId.trim()
        if (cleanOrderId.isBlank()) {
            return Result.failure(
                HarakaPayApiException(
                    message = "Order ID haipo.",
                    httpStatusCode = 400,
                    errorCode = "missing_order_id"
                )
            )
        }

        // Allow test interceptor hook on HarakaPayRepository if set by existing tests
        HarakaPayRepository.testStatusInterceptor?.let { interceptor ->
            val resp = interceptor(cleanOrderId)
            return if (resp.success) {
                Result.success(resp)
            } else {
                Result.failure(
                    HarakaPayApiException(
                        message = resp.errorMessage.ifBlank { "Imeshindikana kuhakiki malipo." },
                        httpStatusCode = 400,
                        errorCode = "status_failed"
                    )
                )
            }
        }

        return try {
            val headers = buildHeaders(includeJsonContentType = false)
            val endpointUrl = "${baseUrl.trimEnd('/')}/api/v1/status/$cleanOrderId"
            val httpResponse = httpTransport.execute(
                method = "GET",
                url = endpointUrl,
                headers = headers,
                requestBody = null
            )

            parseStatusResponse(httpResponse, cleanOrderId)
        } catch (apiEx: HarakaPayApiException) {
            Result.failure(apiEx)
        } catch (e: Exception) {
            Result.failure(
                HarakaPayApiException(
                    message = "Imeshindikana kuhakiki malipo kwenye HarakaPay: ${e.localizedMessage ?: "Angalia internet yako"}",
                    errorCode = "network_error",
                    cause = e
                )
            )
        }
    }

    /**
     * Fetches wallet and float balance via `GET https://harakapay.net/api/v1/balance`.
     */
    suspend fun getBalance(): Result<HarakaPayBalanceResponse> = withContext(Dispatchers.IO) {
        getBalanceBlocking()
    }

    fun getBalanceBlocking(): Result<HarakaPayBalanceResponse> {
        return try {
            val headers = buildHeaders(includeJsonContentType = false)
            val endpointUrl = "${baseUrl.trimEnd('/')}/api/v1/balance"
            val httpResponse = httpTransport.execute(
                method = "GET",
                url = endpointUrl,
                headers = headers,
                requestBody = null
            )

            parseBalanceResponse(httpResponse)
        } catch (apiEx: HarakaPayApiException) {
            Result.failure(apiEx)
        } catch (e: Exception) {
            Result.failure(
                HarakaPayApiException(
                    message = "Tatizo la mtandao: ${e.localizedMessage ?: "Jaribu tena"}",
                    errorCode = "network_error",
                    cause = e
                )
            )
        }
    }

    private fun parseCollectResponse(
        httpResponse: PaymentHttpResponse,
        normalizedPhone: String,
        requestedAmount: Int
    ): Result<HarakaPayCollectResponse> {
        val code = httpResponse.statusCode
        val rawBody = httpResponse.body.trim()

        if (rawBody.isBlank()) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        fallbackMessage = "HarakaPay haijajibu (HTTP $code). Hakikisha una internet kisha jaribu tena."
                    ),
                    httpStatusCode = code,
                    errorCode = "empty_response"
                )
            )
        }

        val json = try {
            JSONObject(rawBody)
        } catch (e: Exception) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        fallbackMessage = "Majibu ya HarakaPay hayasomeki (HTTP $code)."
                    ),
                    httpStatusCode = code,
                    errorCode = "invalid_json",
                    cause = e
                )
            )
        }

        val success = json.optBoolean("success", false)
        val message = json.optString("message", "").trim()
        val apiError = extractApiErrorMessage(json, message)
        val orderId = json.optString("order_id", "").trim()
        val respAmount = json.optInt("amount", requestedAmount)
        val netAmount = json.optInt("net_amount", respAmount)
        val fee = json.optInt("fee", (respAmount - netAmount).coerceAtLeast(0))

        if (code !in 200..299 || !success || orderId.isBlank()) {
            val errMsg = mapHttpErrorMessage(
                statusCode = code,
                apiErrorMessage = apiError,
                fallbackMessage = "Imeshindikana kutuma USSD push kwenye namba $normalizedPhone. Jaribu tena."
            )
            return Result.failure(
                HarakaPayApiException(
                    message = errMsg,
                    httpStatusCode = code,
                    errorCode = json.optString("code", "collect_rejected")
                )
            )
        }

        return Result.success(
            HarakaPayCollectResponse(
                success = true,
                message = message.ifBlank { "USSD push sent to phone ($normalizedPhone)" },
                orderId = orderId,
                amount = respAmount,
                netAmount = netAmount,
                fee = fee
            )
        )
    }

    private fun parseStatusResponse(
        httpResponse: PaymentHttpResponse,
        cleanOrderId: String
    ): Result<HarakaPayStatusResponse> {
        val code = httpResponse.statusCode
        val rawBody = httpResponse.body.trim()

        if (rawBody.isBlank()) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        fallbackMessage = "Imeshindikana kusoma hali ya malipo (HTTP $code)."
                    ),
                    httpStatusCode = code,
                    errorCode = "empty_response"
                )
            )
        }

        val json = try {
            JSONObject(rawBody)
        } catch (e: Exception) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        fallbackMessage = "Majibu ya hali ya malipo hayasomeki (HTTP $code)."
                    ),
                    httpStatusCode = code,
                    errorCode = "invalid_json",
                    cause = e
                )
            )
        }

        val success = json.optBoolean("success", code in 200..299)
        val apiError = extractApiErrorMessage(json, "")

        if (code !in 200..299 || !success) {
            val errMsg = mapHttpErrorMessage(
                statusCode = code,
                apiErrorMessage = apiError,
                fallbackMessage = "Imeshindikana kuhakiki hali ya malipo kwa Order $cleanOrderId."
            )
            return Result.failure(
                HarakaPayApiException(
                    message = errMsg,
                    httpStatusCode = code,
                    errorCode = json.optString("code", "status_error")
                )
            )
        }

        val paymentObj = json.optJSONObject("payment") ?: json
        val statusStr = paymentObj.optString("status", json.optString("status", "pending"))
            .trim()
            .lowercase()
        val parsedOrderId = paymentObj.optString("order_id", cleanOrderId).ifBlank { cleanOrderId }
        val amount = paymentObj.optInt("amount", 0)
        val netAmount = paymentObj.optInt("net_amount", 0)
        val feeAmount = paymentObj.optInt("fee_amount", paymentObj.optInt("fee", 0))
        val createdAt = paymentObj.optString("created_at", "")
        val completedAt = paymentObj.optString("completed_at", "")

        return Result.success(
            HarakaPayStatusResponse(
                success = true,
                orderId = parsedOrderId,
                status = statusStr,
                amount = amount,
                netAmount = netAmount,
                feeAmount = feeAmount,
                createdAt = createdAt,
                completedAt = completedAt,
                errorMessage = apiError
            )
        )
    }

    private fun parseBalanceResponse(httpResponse: PaymentHttpResponse): Result<HarakaPayBalanceResponse> {
        val code = httpResponse.statusCode
        val rawBody = httpResponse.body.trim()

        if (rawBody.isBlank()) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        fallbackMessage = "Imeshindikana kupata salio (HTTP $code)."
                    ),
                    httpStatusCode = code,
                    errorCode = "empty_response"
                )
            )
        }

        val json = try {
            JSONObject(rawBody)
        } catch (e: Exception) {
            return Result.failure(
                HarakaPayApiException(
                    message = "Majibu ya salio la HarakaPay hayasomeki (HTTP $code).",
                    httpStatusCode = code,
                    errorCode = "invalid_json",
                    cause = e
                )
            )
        }

        val success = json.optBoolean("success", false)
        val walletBalance = json.optLong("wallet_balance", 0L)
        val floatBalance = json.optLong("float_balance", 0L)
        val apiError = extractApiErrorMessage(json, "")

        if (code !in 200..299 || !success) {
            return Result.failure(
                HarakaPayApiException(
                    message = mapHttpErrorMessage(
                        statusCode = code,
                        apiErrorMessage = apiError,
                        fallbackMessage = "Imeshindikana kupata salio la HarakaPay."
                    ),
                    httpStatusCode = code,
                    errorCode = "balance_error"
                )
            )
        }

        return Result.success(
            HarakaPayBalanceResponse(
                success = true,
                walletBalance = walletBalance,
                floatBalance = floatBalance
            )
        )
    }

    private fun extractApiErrorMessage(json: JSONObject, defaultMessage: String): String {
        val err = json.optString("error", "").trim()
        if (err.isNotBlank()) return err
        val detail = json.optString("detail", "").trim()
        if (detail.isNotBlank()) return detail
        val msg = json.optString("message", "").trim()
        if (msg.isNotBlank()) return msg
        return defaultMessage
    }

    private fun mapHttpErrorMessage(
        statusCode: Int,
        apiErrorMessage: String = "",
        fallbackMessage: String
    ): String {
        if (apiErrorMessage.isNotBlank()) {
            return apiErrorMessage
        }
        return when (statusCode) {
            400 -> "Ombi la malipo halijakamilika vizuri (HTTP 400). Hakikisha namba ya simu na kiasi ni sahihi."
            401, 403 -> "API Key ya HarakaPay haijakubaliwa (HTTP $statusCode). Hakikisha X-API-Key ni sahihi."
            404 -> "Taarifa ya malipo haijapatikana kwenye HarakaPay (HTTP 404)."
            422 -> "Taarifa za malipo zina makosa (HTTP 422). Angalia namba ya simu na kiasi."
            429 -> "Maombi ni mengi kwa wakati mmoja (HTTP 429). Tafadhali subiri sekunde chache ujaribu tena."
            in 500..599 -> "Seva ya HarakaPay inafanyiwa kazi kwa sasa (HTTP $statusCode). Jaribu tena baada ya muda mfupi."
            else -> fallbackMessage
        }
    }

    /**
     * High-speed production HTTP transport backed by a shared keep-alive [OkHttpClient] pool
     * (with automatic fallback to [HttpURLConnection]) for ultra-fast `harakapay.net` checkout.
     */
    object DefaultHttpUrlConnectionTransport : PaymentHttpTransport {
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

        override fun execute(
            method: String,
            url: String,
            headers: Map<String, String>,
            requestBody: String?
        ): PaymentHttpResponse {
            try {
                val reqBuilder = Request.Builder().url(url)
                headers.forEach { (key, value) ->
                    reqBuilder.header(key, value)
                }
                reqBuilder.header("Connection", "keep-alive")

                val upperMethod = method.uppercase()
                if (upperMethod == "POST" || upperMethod == "PUT" || upperMethod == "PATCH") {
                    val bodyBytes = (requestBody ?: "{}").toRequestBody(jsonMediaType)
                    reqBuilder.method(upperMethod, bodyBytes)
                } else {
                    reqBuilder.method(upperMethod, null)
                }

                turboHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val statusCode = response.code
                    val rawBody = response.body?.string().orEmpty()
                    val responseHeaders = mutableMapOf<String, String>()
                    response.headers.names().forEach { name ->
                        responseHeaders[name] = response.headers.values(name).joinToString(", ")
                    }
                    return PaymentHttpResponse(
                        statusCode = statusCode,
                        body = rawBody,
                        headers = responseHeaders
                    )
                }
            } catch (_: Throwable) {
                // Fallback to direct HttpURLConnection if needed
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 7_000
                    readTimeout = 9_000
                    doInput = true
                    setRequestProperty("Connection", "keep-alive")
                    headers.forEach { (key, value) ->
                        setRequestProperty(key, value)
                    }
                    if (requestBody != null) {
                        doOutput = true
                    }
                }

                if (requestBody != null) {
                    conn.outputStream.use { os ->
                        os.write(requestBody.toByteArray(Charsets.UTF_8))
                    }
                }

                val responseCode = conn.responseCode
                val rawBody = try {
                    val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                    stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                } catch (_: Exception) {
                    ""
                }

                val responseHeaders = mutableMapOf<String, String>()
                conn.headerFields?.forEach { (k, v) ->
                    if (k != null && !v.isNullOrEmpty()) {
                        responseHeaders[k] = v.joinToString(", ")
                    }
                }

                return PaymentHttpResponse(
                    statusCode = responseCode,
                    body = rawBody,
                    headers = responseHeaders
                )
            }
        }
    }
}
