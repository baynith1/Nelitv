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
            val packagesToCheck = listOf(context.packageName, "com.example", "com.nelitv.app").distinct()
            for (pkg in packagesToCheck) {
                val resId = context.resources.getIdentifier("google_api_key", "string", pkg)
                if (resId != 0) {
                    val resKey = context.getString(resId).trim()
                    if (resKey.isNotBlank() && resKey != "YOUR_FIREBASE_API_KEY") {
                        return resKey
                    }
                }
            }
        }
        return ""
    }

    /**
     * Authenticates or registers a user via Google Sign-In, syncs their Google profile
     * with Firebase Auth (`neliplay`), Firestore (`/users/{uid}`), and Realtime Database (`/presence/{uid}`),
     * and logs them in locally in Room.
     */
    suspend fun signInWithGoogleAccount(
        context: Context,
        dao: NeliMediaDao,
        email: String,
        displayName: String = "",
        googleIdToken: String = "",
        firebaseUidOverride: String = "",
        authenticatedFirebaseIdToken: String = ""
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val rawEmail = email.trim().lowercase()
        if (rawEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter or select your Google email address."))
        }
        val normalizedEmail = if (!rawEmail.contains("@")) {
            "$rawEmail@gmail.com"
        } else {
            rawEmail
        }
        if (!normalizedEmail.contains("@") || !normalizedEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid Google email address (e.g. name@gmail.com)."))
        }

        val existingLocal = dao.getAccountByEmail(normalizedEmail)
        val resolvedName = displayName.trim().takeIf { it.length >= 2 }
            ?: existingLocal?.realName?.takeIf { it.isNotBlank() }
            ?: deriveDisplayNameFromEmail(normalizedEmail)

        // When FirebaseAuth.signInWithCredential(GoogleAuthProvider) already succeeded on device,
        // persist that genuine Firebase UID & ID token directly and sync profile/presence best-effort.
        val cleanFbUid = firebaseUidOverride.trim()
        val cleanFbToken = authenticatedFirebaseIdToken.trim()
        if (cleanFbUid.isNotBlank()) {
            if (cleanFbToken.isNotBlank()) {
                try {
                    saveRemoteUserProfile(cleanFbUid, resolvedName, normalizedEmail, cleanFbToken)
                    updateRealtimePresence(cleanFbUid, resolvedName, cleanFbToken)
                } catch (_: Throwable) {
                }
            }
            syncUserDirectoryToFirebaseRtdb(
                uid = cleanFbUid,
                username = resolvedName,
                email = normalizedEmail,
                idToken = cleanFbToken
            )
            val verifiedEntity = UserAccountEntity(
                uid = cleanFbUid,
                realName = resolvedName,
                email = normalizedEmail,
                passwordHash = existingLocal?.passwordHash?.takeIf { it.isNotBlank() } ?: "google_oauth_account",
                idToken = cleanFbToken.ifBlank { googleIdToken.ifBlank { existingLocal?.idToken.orEmpty() } },
                refreshToken = existingLocal?.refreshToken.orEmpty(),
                isLoggedIn = true,
                createdAt = existingLocal?.createdAt ?: System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            dao.logoutAllUsers()
            dao.upsertUserAccount(verifiedEntity)
            return@withContext Result.success(verifiedEntity)
        }

        val apiKey = resolveApiKey(context)
        val deterministicGoogleSecret = "GglAuth_${sha256("neliplay_google_$normalizedEmail").take(20)}!"

        if (apiKey.isNotBlank()) {
            val idpResult = if (googleIdToken.isNotBlank()) {
                signInWithGoogleIdpRemote(apiKey, googleIdToken, resolvedName, normalizedEmail)
            } else {
                null
            }
            val remoteAccount = idpResult
                ?: (loginRemoteAccount(apiKey, normalizedEmail, deterministicGoogleSecret, resolvedName) as? RemoteAuthOutcome.Success)?.account
                ?: (registerRemoteAccount(apiKey, resolvedName, normalizedEmail, deterministicGoogleSecret) as? RemoteAuthOutcome.Success)?.account

            if (remoteAccount != null) {
                val merged = remoteAccount.copy(
                    realName = remoteAccount.realName.ifBlank { resolvedName },
                    email = normalizedEmail,
                    passwordHash = existingLocal?.passwordHash?.takeIf { it.isNotBlank() } ?: "google_oauth_account",
                    idToken = remoteAccount.idToken.ifBlank { googleIdToken },
                    isLoggedIn = true,
                    createdAt = existingLocal?.createdAt ?: System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.logoutAllUsers()
                dao.upsertUserAccount(merged)
                return@withContext Result.success(merged)
            }
        }

        val uid = existingLocal?.uid
            ?: "google_${UUID.nameUUIDFromBytes(normalizedEmail.toByteArray()).toString().replace("-", "").take(16)}"

        val localEntity = UserAccountEntity(
            uid = uid,
            realName = resolvedName,
            email = normalizedEmail,
            passwordHash = existingLocal?.passwordHash?.takeIf { it.isNotBlank() } ?: "google_oauth_account",
            idToken = googleIdToken.ifBlank { existingLocal?.idToken.orEmpty() },
            refreshToken = existingLocal?.refreshToken.orEmpty(),
            isLoggedIn = true,
            createdAt = existingLocal?.createdAt ?: System.currentTimeMillis(),
            lastLoginAt = System.currentTimeMillis()
        )
        dao.logoutAllUsers()
        dao.upsertUserAccount(localEntity)
        Result.success(localEntity)
    }

    fun deriveDisplayNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@")
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
        if (localPart.isEmpty()) return "Neli Viewer"
        return localPart.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    private fun signInWithGoogleIdpRemote(
        apiKey: String,
        googleIdToken: String,
        fallbackName: String,
        fallbackEmail: String
    ): UserAccountEntity? {
        return try {
            val idpUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=$apiKey"
            val payload = JSONObject().apply {
                put("postBody", "id_token=$googleIdToken&providerId=google.com")
                put("requestUri", "http://localhost")
                put("returnIdpCredential", true)
                put("returnSecureToken", true)
            }
            val responseJson = postJson(idpUrl, payload.toString()) ?: return null
            val resObj = JSONObject(responseJson)
            val uid = resObj.optString("localId", "")
            val idToken = resObj.optString("idToken", "")
            val refreshToken = resObj.optString("refreshToken", "")
            val email = resObj.optString("email", fallbackEmail).ifBlank { fallbackEmail }
            val displayName = resObj.optString("displayName", fallbackName).ifBlank { fallbackName }
            if (uid.isEmpty() || idToken.isEmpty()) return null

            saveRemoteUserProfile(uid, displayName, email, idToken)
            updateRealtimePresence(uid, displayName, idToken)

            UserAccountEntity(
                uid = uid,
                realName = displayName,
                email = email,
                passwordHash = "google_oauth_account",
                idToken = idToken,
                refreshToken = refreshToken,
                isLoggedIn = true
            )
        } catch (_: Exception) {
            null
        }
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
            return@withContext Result.failure(IllegalArgumentException("Please enter a username (at least 2 characters)."))
        }
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }
        if (cleanPassword.length < 6) {
            return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
        }

        // If user attempts to register using one of the 5 Free Forever VIP emails, authenticate via Free Forever rules
        if (NeliFreeForeverAccountsManager.isFreeForeverEmail(cleanEmail)) {
            return@withContext signInWithEmailAndPassword(
                context = context,
                dao = dao,
                email = cleanEmail,
                password = cleanPassword
            )
        }

        val apiKey = resolveApiKey(context)
        val passwordHash = sha256(cleanPassword)

        // Try remote Firebase IdentityToolkit registration if API key is available
        if (apiKey.isNotBlank() && !AuthenticationRepository.isRunningInRobolectricTest()) {
            val remoteOutcome = registerRemoteAccount(apiKey, cleanName, cleanEmail, cleanPassword)
            when (remoteOutcome) {
                is RemoteAuthOutcome.Success -> {
                    dao.logoutAllUsers()
                    val entity = remoteOutcome.account.copy(
                        realName = cleanName,
                        email = cleanEmail,
                        passwordHash = passwordHash,
                        isLoggedIn = true,
                        lastLoginAt = System.currentTimeMillis()
                    )
                    dao.upsertUserAccount(entity)
                    syncUserDirectoryToFirebaseRtdb(
                        uid = entity.uid,
                        username = entity.realName,
                        email = entity.email,
                        idToken = entity.idToken
                    )
                    return@withContext Result.success(entity)
                }
                is RemoteAuthOutcome.AuthRejected -> {
                    return@withContext Result.failure(IllegalArgumentException(remoteOutcome.userMessage))
                }
                RemoteAuthOutcome.Unavailable -> {
                    // Fall through to local Room + Firebase RTDB sync when offline or provider unavailable
                }
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
        syncUserDirectoryToFirebaseRtdb(
            uid = account.uid,
            username = account.realName,
            email = account.email
        )
        Result.success(account)
    }

    /**
     * Resolves a user-entered login identifier (either an email address OR a username)
     * into the normalized email address using local Room + Firebase RTDB username index.
     */
    suspend fun resolveEmailForLoginIdentifier(
        dao: NeliMediaDao,
        identifier: String
    ): String? = withContext(Dispatchers.IO) {
        val raw = identifier.trim()
        if (raw.isBlank()) return@withContext null
        val lower = raw.lowercase()

        // Direct email address
        if (lower.contains("@") && lower.contains(".")) {
            return@withContext lower
        }

        // Built-in Free Forever & Admin username shortcuts (user1..user5, admin)
        if (lower in listOf("user1", "user2", "user3", "user4", "user5")) {
            return@withContext "$lower@login.com"
        }
        if (lower == "admin") {
            return@withContext NeliAdminManager.ADMIN_EMAIL.lowercase()
        }

        // Local Room lookup by saved username
        val localMatch = try {
            dao.getAccountByUsername(raw)
        } catch (_: Throwable) {
            null
        }
        if (localMatch != null && localMatch.email.isNotBlank()) {
            return@withContext localMatch.email.trim().lowercase()
        }

        // Remote Firebase RTDB username -> email lookup so username login works across devices
        fetchEmailByUsernameFromFirebaseRtdb(raw)
    }

    /**
     * Signs in an existing user with Email OR Username and Password and restores their saved Username & profile data.
     */
    suspend fun signInWithEmailAndPassword(
        context: Context,
        dao: NeliMediaDao,
        email: String,
        password: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val rawIdentifier = email.trim()
        val cleanPassword = password.trim()

        if (rawIdentifier.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your email or username."))
        }
        if (cleanPassword.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Please enter your password."))
        }

        val cleanEmail = resolveEmailForLoginIdentifier(dao, rawIdentifier)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Account not found for \"$rawIdentifier\". Please check your email/username or Sign Up.")
            )

        // Dedicated Admin Login verification (Email: Admin@login.com, Password: 123456)
        if (NeliAdminManager.isAdminEmail(cleanEmail)) {
            if (NeliAdminManager.isAdminCredentials(cleanEmail, cleanPassword)) {
                val existingAdmin = dao.getAccountByEmail(NeliAdminManager.ADMIN_EMAIL)
                val prevActive = dao.getActiveUserOnce()
                if (prevActive != null && NeliFreeForeverAccountsManager.isFreeForeverUser(prevActive)) {
                    NeliFreeForeverAccountsManager.releaseDeviceOnLogout(context, prevActive.email)
                }
                dao.logoutAllUsers()
                val adminAccount = UserAccountEntity(
                    uid = "admin_neli_master",
                    realName = existingAdmin?.realName?.takeIf { it.isNotBlank() } ?: "Admin Nelitv",
                    email = NeliAdminManager.ADMIN_EMAIL,
                    passwordHash = sha256(cleanPassword),
                    isLoggedIn = true,
                    createdAt = existingAdmin?.createdAt ?: System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(adminAccount)
                NeliAdminManager.setAdminSessionActive(context, true)
                NeliSubscriptionManager.switchActiveAccount(
                    context = context,
                    uid = adminAccount.uid,
                    email = adminAccount.email,
                    realName = adminAccount.realName
                )
                return@withContext Result.success(adminAccount)
            } else {
                return@withContext Result.failure(
                    IllegalArgumentException("Incorrect Admin password for Admin@login.com.")
                )
            }
        }

        // Dedicated 5 Free Forever VIP Accounts (user1@login.com .. user5@login.com, Password: Free123, Max 2 Devices)
        if (NeliFreeForeverAccountsManager.isFreeForeverEmail(cleanEmail)) {
            if (!NeliFreeForeverAccountsManager.isValidFreeForeverPassword(cleanPassword)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Password isiyo sahihi kwa akaunti ya bure ($cleanEmail). Password sahihi ni Free123.")
                )
            }
            val prevActive = dao.getActiveUserOnce()
            if (prevActive != null &&
                NeliFreeForeverAccountsManager.isFreeForeverUser(prevActive) &&
                !prevActive.email.equals(cleanEmail, ignoreCase = true)
            ) {
                NeliFreeForeverAccountsManager.releaseDeviceOnLogout(context, prevActive.email)
            }

            val deviceSlotResult = NeliFreeForeverAccountsManager.verifyAndRegisterDeviceLogin(context, cleanEmail)
            if (deviceSlotResult.isFailure) {
                val errMsg = deviceSlotResult.exceptionOrNull()?.message
                    ?: "Akaunti hii ($cleanEmail) imefika kikomo cha vifaa 2 (Max 2 Devices)."
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            val freeUid = NeliFreeForeverAccountsManager.resolveUidForEmail(cleanEmail)
            val existingLocalFree = dao.getAccountByEmail(cleanEmail)
            val cloudSavedName = fetchUsernameFromFirebaseRtdb(freeUid)
            val resolvedFreeName = existingLocalFree?.realName?.takeIf { it.isNotBlank() }
                ?: cloudSavedName.takeIf { it.isNotBlank() }
                ?: NeliFreeForeverAccountsManager.resolveDisplayName(cleanEmail)

            dao.logoutAllUsers()
            val freeAccount = UserAccountEntity(
                uid = freeUid,
                realName = resolvedFreeName,
                email = cleanEmail,
                passwordHash = sha256(NeliFreeForeverAccountsManager.FREE_FOREVER_PASSWORD),
                isLoggedIn = true,
                createdAt = existingLocalFree?.createdAt ?: System.currentTimeMillis(),
                lastLoginAt = System.currentTimeMillis()
            )
            dao.upsertUserAccount(freeAccount)
            syncUserDirectoryToFirebaseRtdb(
                uid = freeAccount.uid,
                username = freeAccount.realName,
                email = freeAccount.email
            )
            return@withContext Result.success(freeAccount)
        }

        val apiKey = resolveApiKey(context)
        val passwordHash = sha256(cleanPassword)
        val existingLocal = dao.getAccountByEmail(cleanEmail)

        var remoteRejectionMessage: String? = null
        if (apiKey.isNotBlank() && !AuthenticationRepository.isRunningInRobolectricTest()) {
            when (val remoteOutcome = loginRemoteAccount(apiKey, cleanEmail, cleanPassword, existingLocal?.realName)) {
                is RemoteAuthOutcome.Success -> {
                    dao.logoutAllUsers()
                    val entity = remoteOutcome.account.copy(
                        realName = remoteOutcome.account.realName.ifBlank {
                            existingLocal?.realName ?: cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                        },
                        passwordHash = passwordHash,
                        isLoggedIn = true,
                        lastLoginAt = System.currentTimeMillis()
                    )
                    dao.upsertUserAccount(entity)
                    syncUserDirectoryToFirebaseRtdb(
                        uid = entity.uid,
                        username = entity.realName,
                        email = entity.email,
                        idToken = entity.idToken
                    )
                    return@withContext Result.success(entity)
                }
                is RemoteAuthOutcome.AuthRejected -> {
                    remoteRejectionMessage = remoteOutcome.userMessage
                }
                RemoteAuthOutcome.Unavailable -> Unit
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
                syncUserDirectoryToFirebaseRtdb(
                    uid = updated.uid,
                    username = updated.realName,
                    email = updated.email,
                    idToken = updated.idToken
                )
                return@withContext Result.success(updated)
            } else {
                return@withContext Result.failure(
                    IllegalArgumentException(
                        remoteRejectionMessage ?: "Incorrect password. Please try again."
                    )
                )
            }
        }

        return@withContext Result.failure(
            IllegalArgumentException(
                remoteRejectionMessage
                    ?: "Account not found for $cleanEmail. Please create an account first."
            )
        )
    }

    /**
     * Updates the currently signed-in user's username locally in Room AND syncs the change to
     * Firebase Auth, Firestore `/users/{uid}`, and Realtime Database `/nelitv_users` + `/nelitv_usernames`.
     */
    suspend fun updateUsername(
        context: Context,
        dao: NeliMediaDao,
        newUsername: String
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanName = newUsername.trim()
        if (cleanName.length < 2) {
            return@withContext Result.failure(IllegalArgumentException("Username must be at least 2 characters."))
        }
        val activeUser = dao.getActiveUserOnce()
            ?: return@withContext Result.failure(IllegalStateException("Please sign in first to edit your username."))

        val previousName = activeUser.realName
        val updated = activeUser.copy(
            realName = cleanName,
            lastLoginAt = System.currentTimeMillis()
        )
        dao.upsertUserAccount(updated)

        // Update Firebase Auth profile + Firestore + Realtime presence when token is present
        val apiKey = resolveApiKey(context)
        if (apiKey.isNotBlank() && updated.idToken.isNotBlank()) {
            try {
                val updateProfileUrl = "https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey"
                val updatePayload = JSONObject().apply {
                    put("idToken", updated.idToken)
                    put("displayName", cleanName)
                    put("returnSecureToken", false)
                }
                postJson(updateProfileUrl, updatePayload.toString())
                saveRemoteUserProfile(updated.uid, cleanName, updated.email, updated.idToken)
                updateRealtimePresence(updated.uid, cleanName, updated.idToken)
            } catch (_: Throwable) {
            }
        }

        // Sync updated username mapping to Firebase RTDB so login by new username works immediately
        syncUserDirectoryToFirebaseRtdb(
            uid = updated.uid,
            username = cleanName,
            email = updated.email,
            idToken = updated.idToken,
            previousUsername = previousName
        )

        NeliSubscriptionManager.switchActiveAccount(
            context = context,
            uid = updated.uid,
            email = updated.email,
            realName = updated.realName
        )

        Result.success(updated)
    }

    fun syncAuthenticatedAccountToFirebase(
        uid: String,
        username: String,
        email: String,
        idToken: String = ""
    ) {
        if (idToken.isNotBlank()) {
            saveRemoteUserProfile(uid, username, email, idToken)
            updateRealtimePresence(uid, username, idToken)
        }
        syncUserDirectoryToFirebaseRtdb(
            uid = uid,
            username = username,
            email = email,
            idToken = idToken
        )
    }

    fun normalizeUsernameKey(username: String): String {
        return username.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")
    }

    private fun syncUserDirectoryToFirebaseRtdb(
        uid: String,
        username: String,
        email: String,
        idToken: String = "",
        previousUsername: String? = null
    ) {
        if (AuthenticationRepository.isRunningInRobolectricTest()) return
        val cleanUid = uid.trim()
        val cleanName = username.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanUid.isBlank() || cleanName.isBlank() || cleanEmail.isBlank()) return

        val authQuery = if (idToken.isNotBlank()) "?auth=$idToken" else ""
        val now = System.currentTimeMillis()
        val isFreeVip = NeliFreeForeverAccountsManager.isAdFreeVipEmail(cleanEmail)

        try {
            val userUrl = "$NELIPLAY_RTDB_URL/nelitv_users/$cleanUid.json$authQuery"
            val userPayload = JSONObject().apply {
                put("uid", cleanUid)
                put("username", cleanName)
                put("displayName", cleanName)
                put("email", cleanEmail)
                put("isAdFreeVip", isFreeVip)
                put("verifiedPinkTick", isFreeVip)
                put("updatedAtMs", now)
            }.toString()
            putJson(userUrl, userPayload)
        } catch (_: Throwable) {
        }

        val newKey = normalizeUsernameKey(cleanName)
        if (newKey.isNotBlank()) {
            try {
                val usernameUrl = "$NELIPLAY_RTDB_URL/nelitv_usernames/$newKey.json$authQuery"
                val usernamePayload = JSONObject().apply {
                    put("uid", cleanUid)
                    put("username", cleanName)
                    put("email", cleanEmail)
                    put("updatedAtMs", now)
                }.toString()
                putJson(usernameUrl, usernamePayload)
            } catch (_: Throwable) {
            }
        }

        val prevKey = previousUsername?.let { normalizeUsernameKey(it) }.orEmpty()
        if (prevKey.isNotBlank() && prevKey != newKey) {
            try {
                val oldUrl = "$NELIPLAY_RTDB_URL/nelitv_usernames/$prevKey.json$authQuery"
                val conn = (URL(oldUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "DELETE"
                }
                conn.responseCode
                conn.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun fetchEmailByUsernameFromFirebaseRtdb(username: String): String? {
        if (AuthenticationRepository.isRunningInRobolectricTest()) return null
        val key = normalizeUsernameKey(username)
        if (key.isBlank()) return null
        var conn: HttpURLConnection? = null
        return try {
            val url = "$NELIPLAY_RTDB_URL/nelitv_usernames/$key.json"
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (body.isBlank() || body == "null") {
                    null
                } else {
                    JSONObject(body).optString("email", "").trim().lowercase().takeIf { it.contains("@") }
                }
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun fetchUsernameFromFirebaseRtdb(uid: String): String {
        if (AuthenticationRepository.isRunningInRobolectricTest()) return ""
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return ""
        var conn: HttpURLConnection? = null
        return try {
            val url = "$NELIPLAY_RTDB_URL/nelitv_users/$cleanUid.json"
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (body.isBlank() || body == "null") {
                    ""
                } else {
                    val obj = JSONObject(body)
                    obj.optString("username", "").ifBlank { obj.optString("displayName", "") }.trim()
                }
            } else {
                ""
            }
        } catch (_: Throwable) {
            ""
        } finally {
            conn?.disconnect()
        }
    }

    private fun putJson(urlStr: String, jsonBody: String) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "PUT"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
        } catch (_: Throwable) {
        } finally {
            conn?.disconnect()
        }
    }

    private sealed class RemoteAuthOutcome {
        data class Success(val account: UserAccountEntity) : RemoteAuthOutcome()
        data class AuthRejected(val errorCode: String, val userMessage: String) : RemoteAuthOutcome()
        data object Unavailable : RemoteAuthOutcome()
    }

    private data class HttpJsonResponse(
        val statusCode: Int,
        val body: String?
    )

    private fun parseIdentityToolkitErrorCode(errorBody: String?): String {
        if (errorBody.isNullOrBlank()) return ""
        return try {
            JSONObject(errorBody).optJSONObject("error")?.optString("message", "").orEmpty().trim()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun registerRemoteAccount(
        apiKey: String,
        realName: String,
        email: String,
        password: String
    ): RemoteAuthOutcome {
        return try {
            val signUpUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val resp = postJsonWithStatus(signUpUrl, payload.toString()) ?: return RemoteAuthOutcome.Unavailable
            if (resp.statusCode !in 200..299) {
                val code = parseIdentityToolkitErrorCode(resp.body)
                return when {
                    code.startsWith("EMAIL_EXISTS") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "This email is already registered. Please switch to Login or tap Continue with Google."
                    )
                    code.startsWith("WEAK_PASSWORD") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "Password is too weak. Please use at least 6 characters."
                    )
                    code.startsWith("INVALID_EMAIL") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "Please enter a valid email address."
                    )
                    code.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "Too many attempts. Please wait a moment or tap Continue with Google."
                    )
                    else -> RemoteAuthOutcome.Unavailable
                }
            }

            val responseJson = resp.body ?: return RemoteAuthOutcome.Unavailable
            val resObj = JSONObject(responseJson)
            val uid = resObj.optString("localId", "")
            val idToken = resObj.optString("idToken", "")
            val refreshToken = resObj.optString("refreshToken", "")
            if (uid.isEmpty() || idToken.isEmpty()) return RemoteAuthOutcome.Unavailable

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

            RemoteAuthOutcome.Success(
                UserAccountEntity(
                    uid = uid,
                    realName = realName,
                    email = email,
                    passwordHash = sha256(password),
                    idToken = idToken,
                    refreshToken = refreshToken,
                    isLoggedIn = true
                )
            )
        } catch (_: Exception) {
            RemoteAuthOutcome.Unavailable
        }
    }

    private fun loginRemoteAccount(
        apiKey: String,
        email: String,
        password: String,
        cachedName: String?
    ): RemoteAuthOutcome {
        return try {
            val signInUrl = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey"
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("returnSecureToken", true)
            }
            val resp = postJsonWithStatus(signInUrl, payload.toString()) ?: return RemoteAuthOutcome.Unavailable
            if (resp.statusCode !in 200..299) {
                val code = parseIdentityToolkitErrorCode(resp.body)
                return when {
                    code.startsWith("INVALID_LOGIN_CREDENTIALS") ||
                        code.startsWith("INVALID_PASSWORD") ||
                        code.startsWith("EMAIL_NOT_FOUND") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "Incorrect email or password. If you signed up with Google, please tap Continue with Google."
                    )
                    code.startsWith("USER_DISABLED") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "This account has been disabled. Contact support@nelitv.online for help."
                    )
                    code.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") -> RemoteAuthOutcome.AuthRejected(
                        code,
                        "Too many failed login attempts. Please wait a moment or tap Continue with Google."
                    )
                    else -> RemoteAuthOutcome.Unavailable
                }
            }

            val responseJson = resp.body ?: return RemoteAuthOutcome.Unavailable
            val resObj = JSONObject(responseJson)
            val uid = resObj.optString("localId", "")
            val idToken = resObj.optString("idToken", "")
            val refreshToken = resObj.optString("refreshToken", "")
            val displayName = resObj.optString("displayName", "")
            if (uid.isEmpty() || idToken.isEmpty()) return RemoteAuthOutcome.Unavailable

            val remoteProfileName = fetchRemoteUserProfileName(uid, idToken)
            val resolvedName = listOf(displayName, remoteProfileName, cachedName.orEmpty())
                .firstOrNull { it.isNotBlank() }
                ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

            updateRealtimePresence(uid, resolvedName, idToken)

            RemoteAuthOutcome.Success(
                UserAccountEntity(
                    uid = uid,
                    realName = resolvedName,
                    email = email,
                    passwordHash = sha256(password),
                    idToken = idToken,
                    refreshToken = refreshToken,
                    isLoggedIn = true
                )
            )
        } catch (_: Exception) {
            RemoteAuthOutcome.Unavailable
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

    private fun postJsonWithStatus(urlStr: String, jsonBody: String): HttpJsonResponse? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            HttpJsonResponse(statusCode = code, body = body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun postJson(urlStr: String, jsonBody: String): String? {
        val resp = postJsonWithStatus(urlStr, jsonBody) ?: return null
        return if (resp.statusCode in 200..299) resp.body else null
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
