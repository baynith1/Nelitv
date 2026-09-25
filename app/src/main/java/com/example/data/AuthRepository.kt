package com.example.data

import android.content.Context
import com.example.BuildConfig
import com.example.data.local.NeliMediaDao
import com.example.data.local.UserAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

object AuthRepository {

    private const val NELIPLAY_PROJECT_ID = "neliplay"
    private const val NELIPLAY_RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"

    fun resolveApiKey(context: Context? = null, customKey: String = ""): String {
        if (customKey.isNotBlank() && customKey != "YOUR_FIREBASE_API_KEY") {
            return customKey.trim()
        }
        val buildConfigKey = try {
            BuildConfig.FIREBASE_API_KEY.trim()
        } catch (_: Throwable) {
            ""
        }
        if (buildConfigKey.isNotBlank() && buildConfigKey != "YOUR_FIREBASE_API_KEY") {
            return buildConfigKey
        }
        if (context != null) {
            val resId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
            if (resId != 0) {
                val resKey = context.getString(resId).trim()
                if (resKey.isNotBlank()) return resKey
            }
        }
        return ""
    }

    /**
     * Registers a new user with Real Name, Email, and Password, saves their profile data,
     * and automatically logs them in immediately upon registration.
     */
    suspend fun signUpWithEmailAndPassword(
        context: Context,
        dao: NeliMediaDao,
        realName: String,
        email: String,
        password: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanName = realName.trim()
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = password.trim()

        if (cleanName.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (cleanPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        val apiKey = resolveApiKey(context)
        val passwordHash = sha256(cleanPassword)

        // Try remote registration if API key is available
        if (apiKey.isNotBlank()) {
            val remoteResult = registerRemoteAccount(apiKey, cleanName, cleanEmail, cleanPassword)
            if (remoteResult != null) {
                dao.logoutAllUsers()
                val entity = remoteResult.copy(
                    realName = cleanName,
                    email = cleanEmail,
                    passwordHash = passwordHash,
                    isLoggedIn = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(entity)
                return@withContext Result.success(entity)
            }
        }

        // Check if account already exists locally
        val existingLocal = dao.getAccountByEmail(cleanEmail)
        val uid = existingLocal?.uid ?: "usr_${UUID.randomUUID().toString().replace("-", "").take(16)}"

        dao.logoutAllUsers()
        val account = UserAccountEntity(
            uid = uid,
            realName = cleanName,
            email = cleanEmail,
            passwordHash = passwordHash,
            isLoggedIn = true,
            createdAt = existingLocal?.createdAt ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        dao.upsertUserAccount(account)
        Result.success(account)
    }

    /**
     * Signs in an existing user with Email and Password and restores their saved Real Name & profile data.
     */
    suspend fun signInWithEmailAndPassword(
        context: Context,
        dao: NeliMediaDao,
        email: String,
        password: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val cleanPassword = password.trim()

        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (cleanPassword.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val apiKey = resolveApiKey(context)
        val passwordHash = sha256(cleanPassword)
        val existingLocal = dao.getAccountByEmail(cleanEmail)

        if (apiKey.isNotBlank()) {
            val remoteResult = loginRemoteAccount(apiKey, cleanEmail, cleanPassword, existingLocal?.realName)
            if (remoteResult != null) {
                dao.logoutAllUsers()
                val entity = remoteResult.copy(
                    realName = remoteResult.realName.ifBlank {
                        existingLocal?.realName ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                    },
                    passwordHash = passwordHash,
                    isLoggedIn = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(entity)
                return@withContext Result.success(entity)
            }
        }

        if (existingLocal != null) {
            if (existingLocal.passwordHash == passwordHash) {
                dao.logoutAllUsers()
                val updated = existingLocal.copy(
                    isLoggedIn = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(updated)
                return@withContext Result.success(updated)
            } else {
                return@withContext Result.failure(IllegalArgumentException("Incorrect password. Please try again."))
            }
        }

        return@withContext Result.failure(
            IllegalArgumentException("Account not found for $cleanEmail. Please create an account first.")
        )
    }

    private fun registerRemoteAccount(
        apiKey: String,
        realName: String,
        email: String,
        password: String
    ): UserAccountEntity? {
        return try {
            val signUpUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val responseJson = postJson(signUpUrl, payload.toString()) ?: return null
            val resObj = JSONObject(responseJson)
            val uid = resObj.optString("localId", "")
            val idToken = resObj.optString("idToken", "")
            val refreshToken = resObj.optString("refreshToken", "")
            if (uid.isEmpty() || idToken.isEmpty()) return null

            // Update displayName on Auth profile
            val updateProfileUrl = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey"
            val updatePayload = JSONObject().apply {
                put("idToken", idToken)
                put("displayName", realName)
                put("returnSecureToken", false)
            }
            postJson(updateProfileUrl, updatePayload.toString())

            // Save user profile document to /users/{uid} (excluding restricted premium/role keys per security rules)
            saveRemoteUserProfile(uid, realName, email, idToken)
            updateRealtimePresence(uid, realName, idToken)

            UserAccountEntity(
                uid = uid,
                realName = realName,
                email = email,
                passwordHash = sha256(password),
                idToken = idToken,
                refreshToken = refreshToken,
                isLoggedIn = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun loginRemoteAccount(
        apiKey: String,
        email: String,
        password: String,
        cachedName: String?
    ): UserAccountEntity? {
        return try {
            val signInUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val responseJson = postJson(signInUrl, payload.toString()) ?: return null
            val resObj = JSONObject(responseJson)
            val uid = resObj.optString("localId", "")
            val idToken = resObj.optString("idToken", "")
            val refreshToken = resObj.optString("refreshToken", "")
            val displayName = resObj.optString("displayName", "")
            if (uid.isEmpty() || idToken.isEmpty()) return null

            val remoteProfileName = fetchRemoteUserProfileName(uid, idToken)
            val resolvedName = listOf(displayName, remoteProfileName, cachedName.orEmpty())
                .firstOrNull { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

            updateRealtimePresence(uid, resolvedName, idToken)

            UserAccountEntity(
                uid = uid,
                realName = resolvedName,
                email = email,
                passwordHash = sha256(password),
                idToken = idToken,
                refreshToken = refreshToken,
                isLoggedIn = true
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveRemoteUserProfile(uid: String, realName: String, email: String, idToken: String) {
        try {
            val docUrl = "https://firestore.googleapis.com/v1/projects/$NELIPLAY_PROJECT_ID/databases/(default)/documents/users/$uid?updateMask.fieldPaths=name&updateMask.fieldPaths=displayName&updateMask.fieldPaths=email"
            val fieldsObj = JSONObject().apply {
                put("name", JSONObject().put("stringValue", realName))
                put("displayName", JSONObject().put("stringValue", realName))
                put("email", JSONObject().put("stringValue", email))
            }
            val body = JSONObject().put("fields", fieldsObj).toString()
            val conn = (URL(docUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "PATCH"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (_: Exception) {}
    }

    private fun fetchRemoteUserProfileName(uid: String, idToken: String): String {
        return try {
            val docUrl = "https://firestore.googleapis.com/v1/projects/$NELIPLAY_PROJECT_ID/databases/(default)/documents/users/$uid"
            val conn = (URL(docUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $idToken")
            }
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val fields = JSONObject(text).optJSONObject("fields")
                val name = fields?.optJSONObject("name")?.optString("stringValue", "").orEmpty()
                val displayName = fields?.optJSONObject("displayName")?.optString("stringValue", "").orEmpty()
                name.ifBlank { displayName }
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateRealtimePresence(uid: String, realName: String, idToken: String) {
        try {
            val presenceUrl = "$NELIPLAY_RTDB_URL/presence/$uid.json?auth=$idToken"
            val body = JSONObject().apply {
                put("online", true)
                put("name", realName)
                put("updatedAt", System.currentTimeMillis())
            }.toString()
            val conn = (URL(presenceUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (_: Exception) {}
    }

    private fun postJson(urlStr: String, jsonBody: String): String? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
