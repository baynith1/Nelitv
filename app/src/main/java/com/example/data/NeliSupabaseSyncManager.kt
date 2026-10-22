package com.example.data

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Supabase Data & Payment Synchronization Manager for Nelitv.
 *
 * Project: Your Max Tv
 * URL: https://qqwlekumeltrdfvutmmt.supabase.co
 *
 * Manages:
 * 1. Synchronizing real-time subscriptions into public.nelitv_subscriptions on Supabase.
 * 2. Recording completed payment transactions into public.nelitv_payments.
 * 3. Querying & validating active subscriptions by phone number so users who paid
 *    on another device, another app, or on web (Nelitv.online) automatically unlock VIP.
 */
object NeliSupabaseSyncManager {

    private const val TAG = "NeliSupabaseSync"
    const val SUPABASE_URL = "https://qqwlekumeltrdfvutmmt.supabase.co"
    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFxd2xla3VtZWx0cmRmdnV0bW10Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE5OTU3ODYsImV4cCI6MjEwNzU3MTc4Nn0.6FDAcRqDLJ90g3CnskVxHZZm_F9t883nSGAhmCK1Ht8"
    const val SUPABASE_PUBLISHABLE_KEY = "sb_publishable_RU9QIfJMuQzk_w4eJRGiKA_akpI_o7S"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    data class SupabaseSubscriptionRecord(
        val phoneNumber: String,
        val planId: String,
        val premiumStatus: String,
        val amountPaid: Double,
        val activatedAtMs: Long,
        val expiresAtMs: Long,
        val userEmail: String,
        val userId: String,
        val lastDeviceType: String
    ) {
        val isActive: Boolean
            get() = premiumStatus.equals("active", ignoreCase = true) && expiresAtMs > System.currentTimeMillis()
    }

    /**
     * Formats timestamp in milliseconds to ISO 8601 UTC string for Postgres TIMESTAMPTZ.
     */
    fun formatIsoUtc(timestampMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(timestampMs))
    }

    /**
     * Parses ISO 8601 timestamp string returned by Supabase into milliseconds.
     */
    fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            val formats = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            for (fmt in formats) {
                try {
                    val sdf = SimpleDateFormat(fmt, Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val date = sdf.parse(isoString)
                    if (date != null) return date.time
                } catch (_: Throwable) {
                }
            }
            0L
        } catch (_: Throwable) {
            0L
        }
    }

    /**
     * Normalizes a phone number for consistent lookup in Tanzania.
     * E.g. "0712345678", "+255712345678", "255712345678" -> standard 0-leading and 255-leading forms.
     */
    fun getPhoneSearchVariants(rawPhone: String): List<String> {
        val digitsOnly = rawPhone.filter { it.isDigit() }
        val variants = mutableSetOf<String>()
        if (digitsOnly.isNotBlank()) {
            variants.add(digitsOnly)
        }
        if (digitsOnly.startsWith("255") && digitsOnly.length == 12) {
            variants.add("0" + digitsOnly.substring(3))
            variants.add("+" + digitsOnly)
        } else if (digitsOnly.startsWith("0") && digitsOnly.length == 10) {
            variants.add("255" + digitsOnly.substring(1))
            variants.add("+255" + digitsOnly.substring(1))
        }
        return variants.toList()
    }

    /**
     * Synchronizes a subscription and payment record to Supabase asynchronously.
     */
    fun syncSubscriptionAndPayment(
        phoneNumber: String,
        planId: String,
        amountTzs: Int,
        orderId: String,
        activatedAtMs: Long,
        expiresAtMs: Long,
        userEmail: String = "",
        userId: String = "",
        providerReference: String = "",
        isTvDevice: Boolean = false
    ) {
        scope.launch {
            try {
                val cleanPhone = phoneNumber.trim()
                if (cleanPhone.isBlank()) return@launch

                val deviceType = if (isTvDevice) "Smart TV (Android Leanback)" else "Android Phone / Mobile"

                // 1. Upsert into public.nelitv_subscriptions
                val subJson = JSONObject().apply {
                    put("phone_number", cleanPhone)
                    put("plan_id", planId)
                    put("premium_status", "active")
                    put("amount_paid", amountTzs.toDouble())
                    put("activated_at", formatIsoUtc(activatedAtMs))
                    put("expires_at", formatIsoUtc(expiresAtMs))
                    put("last_device_type", deviceType)
                    put("updated_at", formatIsoUtc(System.currentTimeMillis()))
                    if (userId.isNotBlank()) put("user_id", userId)
                    if (userEmail.isNotBlank()) put("user_email", userEmail)
                }

                val subRequest = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/nelitv_subscriptions?on_conflict=phone_number")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(subJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(subRequest).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    Log.d(TAG, "Supabase subscription upsert response code=${resp.code}: $body")
                }

                // 2. Insert into public.nelitv_payments
                val payJson = JSONObject().apply {
                    put("order_id", orderId.ifBlank { "ORD-${System.currentTimeMillis()}" })
                    put("phone_number", cleanPhone)
                    put("amount", amountTzs.toDouble())
                    put("plan_id", planId)
                    put("status", "completed")
                    put("payment_method", "harakapay")
                    put("provider_reference", providerReference.ifBlank { "HPAY-${orderId}" })
                    put("completed_at", formatIsoUtc(System.currentTimeMillis()))
                    if (userId.isNotBlank()) put("user_id", userId)
                    if (userEmail.isNotBlank()) put("user_email", userEmail)
                }

                val payRequest = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/nelitv_payments")
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(payJson.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(payRequest).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    Log.d(TAG, "Supabase payment record response code=${resp.code}: $body")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error syncing subscription & payment to Supabase", e)
            }
        }
    }

    /**
     * Queries Supabase for an existing subscription matching [phoneNumber].
     * Checks multiple Tanzanian phone formats (e.g. 07XXXXXXXX, 2557XXXXXXXX, +2557XXXXXXXX).
     *
     * Returns the matching [SupabaseSubscriptionRecord] if found, or null if no record exists.
     */
    suspend fun querySubscriptionByPhone(rawPhone: String): Result<SupabaseSubscriptionRecord?> =
        withContext(Dispatchers.IO) {
            try {
                val cleanPhone = rawPhone.trim()
                if (cleanPhone.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Namba ya simu haiwezi kuwa tupu"))
                }

                val variants = getPhoneSearchVariants(cleanPhone)
                // Build PostgREST filter: or=(phone_number.eq.07...,phone_number.eq.255...)
                val orFilter = variants.joinToString(separator = ",") { "phone_number.eq.$it" }
                val encodedFilter = java.net.URLEncoder.encode("($orFilter)", "UTF-8")

                val url = "$SUPABASE_URL/rest/v1/nelitv_subscriptions?or=$encodedFilter&order=expires_at.desc&limit=1"

                val request = Request.Builder()
                    .url(url)
                    .addHeader("apikey", SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                    .addHeader("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val respCode = response.code
                    val respBody = response.body?.string().orEmpty()
                    Log.d(TAG, "querySubscriptionByPhone code=$respCode response=$respBody")

                    if (!response.isSuccessful) {
                        return@withContext Result.failure(Exception("Supabase query failed: HTTP $respCode $respBody"))
                    }

                    val jsonArray = JSONArray(respBody)
                    if (jsonArray.length() == 0) {
                        // Also try looking up the latest completed payment if subscription table was not updated
                        val payRecord = queryPaymentByPhoneFallback(variants)
                        return@withContext Result.success(payRecord)
                    }

                    val row = jsonArray.getJSONObject(0)
                    val phone = row.optString("phone_number", cleanPhone)
                    val planId = row.optString("plan_id", "TWO_DAYS_1000")
                    val status = row.optString("premium_status", "active")
                    val amount = row.optDouble("amount_paid", 0.0)
                    val activatedAt = parseIsoTimestamp(row.optString("activated_at"))
                    val expiresAt = parseIsoTimestamp(row.optString("expires_at"))
                    val email = row.optString("user_email", "")
                    val uid = row.optString("user_id", "")
                    val device = row.optString("last_device_type", "")

                    val record = SupabaseSubscriptionRecord(
                        phoneNumber = phone,
                        planId = planId,
                        premiumStatus = status,
                        amountPaid = amount,
                        activatedAtMs = if (activatedAt > 0L) activatedAt else System.currentTimeMillis(),
                        expiresAtMs = expiresAt,
                        userEmail = email,
                        userId = uid,
                        lastDeviceType = device
                    )
                    Result.success(record)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Exception querying subscription by phone on Supabase", e)
                Result.failure(e)
            }
        }

    /**
     * Fallback query against public.nelitv_payments if the user paid on web or another client
     * and only a payment record was written.
     */
    private fun queryPaymentByPhoneFallback(variants: List<String>): SupabaseSubscriptionRecord? {
        try {
            val orFilter = variants.joinToString(separator = ",") { "phone_number.eq.$it" }
            val encodedFilter = java.net.URLEncoder.encode("($orFilter)", "UTF-8")
            val url = "$SUPABASE_URL/rest/v1/nelitv_payments?or=$encodedFilter&status=eq.completed&order=completed_at.desc&limit=1"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                val array = JSONArray(body)
                if (array.length() == 0) return null

                val payRow = array.getJSONObject(0)
                val phone = payRow.optString("phone_number")
                val amount = payRow.optDouble("amount", 1000.0)
                val planId = payRow.optString("plan_id", "TWO_DAYS_1000")
                val email = payRow.optString("user_email", "")
                val uid = payRow.optString("user_id", "")
                val completedAt = parseIsoTimestamp(payRow.optString("completed_at"))
                val durationMs = when {
                    amount >= 10000 -> 30L * 24L * 60L * 60L * 1000L
                    amount >= 3000 -> 7L * 24L * 60L * 60L * 1000L
                    else -> 2L * 24L * 60L * 60L * 1000L
                }
                val expiresAt = (if (completedAt > 0L) completedAt else System.currentTimeMillis()) + durationMs

                return SupabaseSubscriptionRecord(
                    phoneNumber = phone,
                    planId = planId,
                    premiumStatus = if (expiresAt > System.currentTimeMillis()) "active" else "expired",
                    amountPaid = amount,
                    activatedAtMs = if (completedAt > 0L) completedAt else System.currentTimeMillis(),
                    expiresAtMs = expiresAt,
                    userEmail = email,
                    userId = uid,
                    lastDeviceType = "Payment Gateway Webhook"
                )
            }
        } catch (_: Throwable) {
            return null
        }
    }
}
