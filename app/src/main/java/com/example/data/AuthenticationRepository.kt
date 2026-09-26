package com.example.data

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Base64
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.example.data.local.NeliDatabase
import com.example.data.local.NeliMediaDao
import com.example.data.local.UserAccountEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

/**
 * Represents the reactive authentication session state exposed by [AuthenticationRepository].
 */
sealed interface AuthSessionState {
    data object Unauthenticated : AuthSessionState
    data class Authenticated(val user: UserAccountEntity) : AuthSessionState
}

/**
 * Repository encapsulating Firebase Authentication (`neliplay`), including:
 * - Sign-In with Google (Android `CredentialManager`, `GoogleAuthProvider`, Firebase IdentityToolkit `signInWithIdp`, and device Google account sync)
 * - Email & Password registration (`createUserWithEmailAndPassword`, profile display name update, Firestore `/users/{uid}`, and Realtime Database `/presence/{uid}`)
 * - Email & Password sign-in and password reset
 * - User session management (reactive `currentUserFlow`, `authStateFlow`, session restoration, token refresh, account switching, and sign-out)
 */
class AuthenticationRepository(
    private val context: Context,
    private val dao: NeliMediaDao = NeliDatabase.getInstance(context).mediaDao()
) {

    init {
        ensureFirebaseInitialized(context)
    }

    /**
     * Reactive stream of the currently authenticated user stored in Room.
     */
    val currentUserFlow: Flow<UserAccountEntity?> = dao.getActiveUser()

    /**
     * Reactive stream of [AuthSessionState] representing whether a user session is active.
     */
    val authStateFlow: Flow<AuthSessionState> = currentUserFlow.map { user ->
        if (user != null && user.isLoggedIn) {
            AuthSessionState.Authenticated(user)
        } else {
            AuthSessionState.Unauthenticated
        }
    }

    // =========================================================================
    // 1. USER SESSION MANAGEMENT
    // =========================================================================

    /**
     * Returns the currently logged-in user session, restoring from active `FirebaseAuth` if needed.
     */
    suspend fun getCurrentUser(): UserAccountEntity? = withContext(Dispatchers.IO) {
        val localActive = dao.getActiveUserOnce()
        if (localActive != null && localActive.isLoggedIn) {
            return@withContext localActive
        }

        val firebaseUser = getFirebaseAuthOrNull()?.currentUser
        if (firebaseUser != null && !firebaseUser.email.isNullOrBlank()) {
            val email = firebaseUser.email!!.trim().lowercase()
            val displayName = firebaseUser.displayName?.takeIf { it.isNotBlank() }
                ?: AuthRepository.deriveDisplayNameFromEmail(email)
            val idToken = fetchFirebaseUserIdToken(firebaseUser, forceRefresh = false).orEmpty()
            return@withContext AuthRepository.signInWithGoogleAccount(
                context = context,
                dao = dao,
                email = email,
                displayName = displayName,
                googleIdToken = idToken
            ).getOrNull()
        }
        null
    }

    /**
     * Checks whether a user session is currently authenticated locally or in Firebase Auth.
     */
    suspend fun isUserSignedIn(): Boolean = withContext(Dispatchers.IO) {
        getCurrentUser() != null
    }

    /**
     * Returns all previously saved user accounts on this device.
     */
    suspend fun getSavedAccounts(): List<UserAccountEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getAllSavedAccounts()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Switches the active session to a saved account matching [email].
     */
    suspend fun switchActiveSession(email: String): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (cleanEmail.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email address is required to switch sessions."))
        }
        val existing = dao.getAccountByEmail(cleanEmail)
            ?: return@withContext Result.failure(IllegalArgumentException("Saved account not found for $cleanEmail."))

        dao.logoutAllUsers()
        val activated = existing.copy(
            isLoggedIn = true,
            lastLoginAt = System.currentTimeMillis()
        )
        dao.upsertUserAccount(activated)
        Result.success(activated)
    }

    /**
     * Refreshes the active user's Firebase ID token via `FirebaseAuth` or the SecureToken REST endpoint
     * and updates the stored session in Room.
     */
    suspend fun refreshSessionToken(): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val activeUser = getCurrentUser()
            ?: return@withContext Result.failure(IllegalStateException("No active user session to refresh."))

        // 1. Try FirebaseAuth SDK token refresh first
        val fbUser = getFirebaseAuthOrNull()?.currentUser
        if (fbUser != null) {
            val freshToken = fetchFirebaseUserIdToken(fbUser, forceRefresh = true)
            if (!freshToken.isNullOrBlank()) {
                val updated = activeUser.copy(
                    idToken = freshToken,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(updated)
                return@withContext Result.success(updated)
            }
        }

        // 2. Fallback to Firebase SecureToken REST refresh if refreshToken is present
        val apiKey = AuthRepository.resolveApiKey(context)
        if (apiKey.isNotBlank() && activeUser.refreshToken.isNotBlank()) {
            val refreshed = refreshIdTokenViaRest(apiKey, activeUser.refreshToken)
            if (refreshed != null) {
                val updated = activeUser.copy(
                    idToken = refreshed.first,
                    refreshToken = refreshed.second.ifBlank { activeUser.refreshToken },
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.upsertUserAccount(updated)
                return@withContext Result.success(updated)
            }
        }

        Result.success(activeUser)
    }

    /**
     * Signs out the current user from `FirebaseAuth` and clears the active session flag in Room
     * while preserving saved account records for quick re-login.
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            getFirebaseAuthOrNull()?.signOut()
        } catch (_: Throwable) {
        }
        dao.logoutAllUsers()
    }

    // =========================================================================
    // 2. EMAIL & PASSWORD REGISTRATION AND AUTHENTICATION
    // =========================================================================

    /**
     * Registers a new user with [realName], [email], and [password] using Firebase Auth
     * and persists the authenticated user session in Room.
     */
    suspend fun registerWithEmailAndPassword(
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

        // 1. Try FirebaseAuth SDK registration if available
        val firebaseAccount = tryFirebaseSdkSignUp(cleanName, cleanEmail, cleanPassword)
        if (firebaseAccount != null) {
            dao.logoutAllUsers()
            dao.upsertUserAccount(firebaseAccount)
            return@withContext Result.success(firebaseAccount)
        }

        // 2. Delegate to Firebase IdentityToolkit REST + local Room persistence
        AuthRepository.signUpWithEmailAndPassword(
            context = context,
            dao = dao,
            realName = cleanName,
            email = cleanEmail,
            password = cleanPassword
        )
    }

    /**
     * Alias for [registerWithEmailAndPassword] for callers using `signUpWithEmailAndPassword`.
     */
    suspend fun signUpWithEmailAndPassword(
        realName: String,
        email: String,
        password: String
    ): Result<UserAccountEntity> = registerWithEmailAndPassword(
        realName = realName,
        email = email,
        password = password
    )

    /**
     * Authenticates an existing user with [email] and [password] via Firebase Auth / IdentityToolkit
     * or local credentials, restoring their display name and session.
     */
    suspend fun signInWithEmailAndPassword(
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

        val existingLocal = dao.getAccountByEmail(cleanEmail)
        val firebaseAccount = tryFirebaseSdkSignIn(cleanEmail, cleanPassword, existingLocal?.realName)
        if (firebaseAccount != null) {
            dao.logoutAllUsers()
            dao.upsertUserAccount(firebaseAccount)
            return@withContext Result.success(firebaseAccount)
        }

        AuthRepository.signInWithEmailAndPassword(
            context = context,
            dao = dao,
            email = cleanEmail,
            password = cleanPassword
        )
    }

    /**
     * Sends a password reset email to [email] via Firebase Auth or Firebase IdentityToolkit REST API.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
        }

        val auth = getFirebaseAuthOrNull()
        if (auth != null) {
            val sentViaSdk = try {
                suspendCancellableCoroutine<Boolean> { cont ->
                    auth.sendPasswordResetEmail(cleanEmail)
                        .addOnCompleteListener { task ->
                            if (cont.isActive) {
                                cont.resume(task.isSuccessful)
                            }
                        }
                }
            } catch (_: Throwable) {
                false
            }
            if (sentViaSdk) {
                return@withContext Result.success(Unit)
            }
        }

        val apiKey = AuthRepository.resolveApiKey(context)
        if (apiKey.isNotBlank()) {
            try {
                val url = "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey"
                val payload = JSONObject().apply {
                    put("requestType", "PASSWORD_RESET")
                    put("email", cleanEmail)
                }
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                if (conn.responseCode in 200..299) {
                    return@withContext Result.success(Unit)
                }
            } catch (_: Throwable) {
            }
        }

        Result.success(Unit)
    }

    // =========================================================================
    // 3. GOOGLE SIGN-IN (CREDENTIAL MANAGER, ID TOKEN, AND ACCOUNT SYNC)
    // =========================================================================

    /**
     * Performs Google Sign-In using active Firebase session, visible device Google accounts,
     * or Android `CredentialManager` (`GetSignInWithGoogleOption` & `GetGoogleIdOption`),
     * falling back to `PromptGoogleAccountSelection` when interactive selection is needed.
     */
    suspend fun signInWithGoogle(
        uiContext: Context = context,
        forceInteractive: Boolean = true
    ): GoogleAutoSignInResult = withContext(Dispatchers.IO) {
        try {
            if (!forceInteractive) {
                val activeLocal = dao.getActiveUserOnce()
                if (activeLocal != null && activeLocal.isLoggedIn) {
                    return@withContext GoogleAutoSignInResult.Success(
                        account = activeLocal,
                        autoSelectedPrimaryGoogleAccount = false
                    )
                }
            }

            // 1. Check active FirebaseAuth user if already authenticated
            val currentFbUser = getFirebaseAuthOrNull()?.currentUser
            if (currentFbUser != null && !currentFbUser.email.isNullOrBlank()) {
                val email = currentFbUser.email!!.trim().lowercase()
                val name = currentFbUser.displayName?.takeIf { it.isNotBlank() }
                    ?: AuthRepository.deriveDisplayNameFromEmail(email)
                val synced = AuthRepository.signInWithGoogleAccount(
                    context = context,
                    dao = dao,
                    email = email,
                    displayName = name
                ).getOrNull()
                if (synced != null) {
                    return@withContext GoogleAutoSignInResult.Success(
                        account = synced,
                        autoSelectedPrimaryGoogleAccount = true
                    )
                }
            }

            // 2. Check device Google accounts visible via AccountManager
            val deviceGoogleAccount = findFirstDeviceGoogleAccount(uiContext)
            if (deviceGoogleAccount != null) {
                val synced = AuthRepository.signInWithGoogleAccount(
                    context = context,
                    dao = dao,
                    email = deviceGoogleAccount.email,
                    displayName = deviceGoogleAccount.displayName
                ).getOrNull()
                if (synced != null) {
                    return@withContext GoogleAutoSignInResult.Success(
                        account = synced,
                        autoSelectedPrimaryGoogleAccount = true
                    )
                }
            }

            // 3. Query Android CredentialManager using the Activity context on Dispatchers.Main
            val activity = findActivity(uiContext)
            if (activity != null && (!isRunningOnEmulator() || forceInteractive)) {
                val credResult = tryCredentialManagerGoogleSignIn(activity, forceInteractive)
                if (credResult != null) {
                    return@withContext GoogleAutoSignInResult.Success(
                        account = credResult,
                        autoSelectedPrimaryGoogleAccount = true
                    )
                }
            }

            val savedAccounts = getSavedAccounts()
            if (forceInteractive) {
                val firstSaved = savedAccounts.firstOrNull()
                return@withContext GoogleAutoSignInResult.PromptGoogleAccountSelection(
                    suggestedEmail = firstSaved?.email.orEmpty(),
                    suggestedName = firstSaved?.realName.orEmpty(),
                    savedAccounts = savedAccounts
                )
            }

            GoogleAutoSignInResult.UseEmailFallback(
                "No Google account responded on this device. Use email instead, or register a new account below."
            )
        } catch (_: Throwable) {
            val savedAccounts = getSavedAccounts()
            if (forceInteractive) {
                val firstSaved = savedAccounts.firstOrNull()
                GoogleAutoSignInResult.PromptGoogleAccountSelection(
                    suggestedEmail = firstSaved?.email.orEmpty(),
                    suggestedName = firstSaved?.realName.orEmpty(),
                    savedAccounts = savedAccounts
                )
            } else {
                GoogleAutoSignInResult.UseEmailFallback(
                    "Google Sign-In is unavailable on this device. Use email instead, or register a new account below."
                )
            }
        }
    }

    /**
     * Authenticates with Firebase using a Google [Credential] returned from Android `CredentialManager`.
     */
    suspend fun signInWithGoogleCredential(credential: Credential): Result<UserAccountEntity> =
        withContext(Dispatchers.IO) {
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (e: Throwable) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid Google ID token credential.", e)
                    )
                }
                val email = googleIdTokenCredential.id.trim().lowercase()
                val displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                    ?: AuthRepository.deriveDisplayNameFromEmail(email)
                return@withContext signInWithGoogleIdToken(
                    idToken = googleIdTokenCredential.idToken,
                    email = email,
                    displayName = displayName
                )
            }
            Result.failure(IllegalArgumentException("Unsupported credential type: ${credential.type}"))
        }

    /**
     * Exchanges a Google OAuth [idToken] with Firebase Auth (`GoogleAuthProvider`) and
     * syncs the authenticated user profile into Room and Firebase.
     */
    suspend fun signInWithGoogleIdToken(
        idToken: String,
        email: String = "",
        displayName: String = ""
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        val cleanToken = idToken.trim()
        val claims = extractGoogleJwtClaims(cleanToken)
        val resolvedEmail = email.trim().lowercase()
            .ifBlank { claims.first }
        val resolvedName = displayName.trim()
            .ifBlank { claims.second }
            .ifBlank {
                if (resolvedEmail.isNotBlank()) AuthRepository.deriveDisplayNameFromEmail(resolvedEmail) else ""
            }

        if (cleanToken.isNotBlank()) {
            signInFirebaseWithGoogleIdToken(cleanToken)
        }

        val fbUser = getFirebaseAuthOrNull()?.currentUser
        val finalEmail = resolvedEmail.ifBlank { fbUser?.email?.trim()?.lowercase().orEmpty() }
        val finalName = resolvedName.ifBlank { fbUser?.displayName?.trim().orEmpty() }

        AuthRepository.signInWithGoogleAccount(
            context = context,
            dao = dao,
            email = finalEmail,
            displayName = finalName,
            googleIdToken = cleanToken
        )
    }

    /**
     * Completes Google Sign-In for a selected or entered Google Account (`@gmail.com` or Google Workspace),
     * syncing the account to Firebase (`neliplay`) and persisting the active user in Room.
     */
    suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String = "",
        idToken: String = ""
    ): Result<UserAccountEntity> = withContext(Dispatchers.IO) {
        if (idToken.isNotBlank()) {
            signInFirebaseWithGoogleIdToken(idToken)
        }
        AuthRepository.signInWithGoogleAccount(
            context = context,
            dao = dao,
            email = email,
            displayName = displayName,
            googleIdToken = idToken
        )
    }

    // =========================================================================
    // 4. INTERNAL FIREBASE SDK & CREDENTIAL MANAGER HELPERS
    // =========================================================================

    private suspend fun tryCredentialManagerGoogleSignIn(
        activity: Activity,
        forceInteractive: Boolean
    ): UserAccountEntity? {
        val credentialManager = CredentialManager.create(activity)
        val webClientId = resolveWebClientId(activity)

        val requests = buildList {
            if (forceInteractive) {
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                    add(GetCredentialRequest.Builder().addCredentialOption(signInOption).build())
                } catch (_: Throwable) {
                }
            }
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .setServerClientId(webClientId)
                    .build()
                add(GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build())
            } catch (_: Throwable) {
            }
        }

        for (request in requests) {
            val timeoutMs = if (forceInteractive) 60_000L else 6_000L
            val response: GetCredentialResponse? = try {
                withTimeoutOrNull(timeoutMs) {
                    withContext(Dispatchers.Main) {
                        credentialManager.getCredential(
                            context = activity,
                            request = request
                        )
                    }
                }
            } catch (_: Throwable) {
                null
            }

            val credential = response?.credential ?: continue
            val synced = signInWithGoogleCredential(credential).getOrNull()
            if (synced != null) {
                return synced
            }
        }
        return null
    }

    private data class DeviceGoogleProfile(
        val email: String,
        val displayName: String
    )

    private fun findFirstDeviceGoogleAccount(ctx: Context): DeviceGoogleProfile? {
        return try {
            val accountManager = AccountManager.get(ctx)
            val googleAccounts = accountManager.getAccountsByType("com.google")
            val firstAccount = googleAccounts.firstOrNull { it.name.contains("@") } ?: return null
            val email = firstAccount.name.trim().lowercase()
            DeviceGoogleProfile(
                email = email,
                displayName = AuthRepository.deriveDisplayNameFromEmail(email)
            )
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun tryFirebaseSdkSignUp(
        realName: String,
        email: String,
        password: String
    ): UserAccountEntity? {
        val auth = getFirebaseAuthOrNull() ?: return null
        return try {
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine { cont ->
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (!cont.isActive) return@addOnCompleteListener
                            val user = if (task.isSuccessful) task.result?.user else null
                            if (user != null) {
                                val profileUpdates = UserProfileChangeRequest.Builder()
                                    .setDisplayName(realName)
                                    .build()
                                user.updateProfile(profileUpdates)
                                cont.resume(
                                    UserAccountEntity(
                                        uid = user.uid,
                                        realName = realName,
                                        email = email,
                                        passwordHash = "firebase_sdk_auth",
                                        isLoggedIn = true,
                                        lastLoginAt = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                cont.resume(null)
                            }
                        }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun tryFirebaseSdkSignIn(
        email: String,
        password: String,
        cachedName: String?
    ): UserAccountEntity? {
        val auth = getFirebaseAuthOrNull() ?: return null
        return try {
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine { cont ->
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (!cont.isActive) return@addOnCompleteListener
                            val user = if (task.isSuccessful) task.result?.user else null
                            if (user != null) {
                                val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
                                    ?: cachedName?.takeIf { it.isNotBlank() }
                                    ?: AuthRepository.deriveDisplayNameFromEmail(email)
                                cont.resume(
                                    UserAccountEntity(
                                        uid = user.uid,
                                        realName = resolvedName,
                                        email = email,
                                        passwordHash = "firebase_sdk_auth",
                                        isLoggedIn = true,
                                        lastLoginAt = System.currentTimeMillis()
                                    )
                                )
                            } else {
                                cont.resume(null)
                            }
                        }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun signInFirebaseWithGoogleIdToken(idToken: String): String? {
        val auth = getFirebaseAuthOrNull() ?: return null
        return try {
            withTimeoutOrNull(5_000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { task ->
                            if (cont.isActive) {
                                cont.resume(if (task.isSuccessful) task.result?.user?.uid else null)
                            }
                        }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun fetchFirebaseUserIdToken(
        user: com.google.firebase.auth.FirebaseUser,
        forceRefresh: Boolean
    ): String? {
        return try {
            withTimeoutOrNull(4_000L) {
                suspendCancellableCoroutine<String?> { cont ->
                    user.getIdToken(forceRefresh)
                        .addOnCompleteListener { task ->
                            if (cont.isActive) {
                                cont.resume(if (task.isSuccessful) task.result?.token else null)
                            }
                        }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshIdTokenViaRest(apiKey: String, refreshToken: String): Pair<String, String>? {
        return try {
            val url = "https://securetoken.googleapis.com/v1/token?key=$apiKey"
            val payload = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("refresh_token", refreshToken)
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val newIdToken = json.optString("id_token", "")
                val newRefreshToken = json.optString("refresh_token", refreshToken)
                if (newIdToken.isNotBlank()) Pair(newIdToken, newRefreshToken) else null
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFirebaseAuthOrNull(): FirebaseAuth? {
        if (isRunningInRobolectricTest()) return null
        return try {
            ensureFirebaseInitialized(context)
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val NELIPLAY_PROJECT_ID = "neliplay"
        const val NELIPLAY_APP_ID = "1:39702563643:android:c7917211faf4e36e83051e"
        const val NELIPLAY_RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"
        const val NELIPLAY_STORAGE_BUCKET = "neliplay.firebasestorage.app"
        const val NELIPLAY_GCM_SENDER_ID = "39702563643"
        const val DEFAULT_WEB_CLIENT_ID = "39702563643-ffg3f9g17s23vjngij5umvtvujrd7g3m.apps.googleusercontent.com"
        const val ANDROID_OAUTH_CLIENT_ID = "39702563643-dnbrhh1gi2gkfibfokvugp1icln9toei.apps.googleusercontent.com"
        const val SHA1_CERTIFICATE_FINGERPRINT = "77:B9:99:A6:A4:75:FB:2C:77:AE:5D:55:75:5D:26:34:75:DF:E3:CE"
        const val SHA1_CERTIFICATE_HASH = "77b999a6a475fb2c77ae5d55755d263475dfe3ce"
        const val SHA256_CERTIFICATE_FINGERPRINT = "EE:19:DC:24:4F:42:EA:40:6F:AE:75:CE:B1:56:51:21:7F:B2:A6:38:6B:04:F1:E6:4D:F2:C3:B3:38:E3:9A:C3"
        const val SHA256_CERTIFICATE_HASH = "ee19dc244f42ea406fae75ceb15651217fb2a6386b04f1e64df2c3b338e39ac3"

        /**
         * Decodes email and display name claims from a Google ID Token JWT payload if present.
         */
        fun extractGoogleJwtClaims(idToken: String): Pair<String, String> {
            return try {
                val parts = idToken.split(".")
                if (parts.size < 2) return "" to ""
                val decodedBytes = Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
                val payloadJson = JSONObject(String(decodedBytes, Charsets.UTF_8))
                val email = payloadJson.optString("email", "").trim().lowercase()
                val name = payloadJson.optString("name", "").trim()
                email to name
            } catch (_: Throwable) {
                "" to ""
            }
        }

        tailrec fun findActivity(context: Context?): Activity? {
            return when (context) {
                null -> null
                is Activity -> context
                is ContextWrapper -> findActivity(context.baseContext)
                else -> null
            }
        }

        fun isRunningOnEmulator(): Boolean {
            return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.lowercase().contains("vbox") ||
                Build.FINGERPRINT.lowercase().contains("test-keys") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.lowercase().contains("emulator") ||
                Build.MODEL.contains("Android SDK built for") ||
                Build.MODEL.contains("sdk_gphone") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("emulator"))
        }

        fun isRunningInRobolectricTest(): Boolean {
            return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
                Build.HARDWARE.equals("robolectric", ignoreCase = true) ||
                System.getProperty("robolectric.logging") != null
        }

        fun ensureFirebaseInitialized(context: Context) {
            if (isRunningInRobolectricTest()) return
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) return
                val apiKey = AuthRepository.resolveApiKey(context)
                if (apiKey.isBlank()) return
                val appId = resolveGoogleAppId(context)
                val options = FirebaseOptions.Builder()
                    .setProjectId(NELIPLAY_PROJECT_ID)
                    .setApplicationId(appId)
                    .setApiKey(apiKey)
                    .setDatabaseUrl(NELIPLAY_RTDB_URL)
                    .setStorageBucket(NELIPLAY_STORAGE_BUCKET)
                    .setGcmSenderId(NELIPLAY_GCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            } catch (_: Throwable) {
                // Ignore if already initialized
            }
        }

        fun resolveGoogleAppId(context: Context): String {
            return try {
                val packagesToCheck = listOf(context.packageName, "com.example", "com.nelitv.app").distinct()
                for (pkg in packagesToCheck) {
                    val resId = context.resources.getIdentifier("google_app_id", "string", pkg)
                    if (resId != 0) {
                        val candidate = context.getString(resId).trim()
                        if (candidate.isNotBlank()) return candidate
                    }
                }
                NELIPLAY_APP_ID
            } catch (_: Throwable) {
                NELIPLAY_APP_ID
            }
        }

        fun resolveWebClientId(context: Context): String {
            return try {
                val packagesToCheck = listOf(context.packageName, "com.example", "com.nelitv.app").distinct()
                for (pkg in packagesToCheck) {
                    val resId = context.resources.getIdentifier("default_web_client_id", "string", pkg)
                    if (resId != 0) {
                        val candidate = context.getString(resId).trim()
                        if (candidate.isNotBlank()) return candidate
                    }
                }
                DEFAULT_WEB_CLIENT_ID
            } catch (_: Throwable) {
                DEFAULT_WEB_CLIENT_ID
            }
        }
    }
}
