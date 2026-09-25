package com.example.data

import android.accounts.AccountManager
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.data.local.NeliDatabase
import com.example.data.local.NeliMediaDao
import com.example.data.local.UserAccountEntity
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

sealed interface GoogleAutoSignInResult {
    data class Success(
        val account: UserAccountEntity,
        val autoSelectedPrimaryGoogleAccount: Boolean = true
    ) : GoogleAutoSignInResult

    data class UseEmailFallback(
        val reasonMessage: String = "Google Sign-In could not complete. Use email instead, or register a new account below."
    ) : GoogleAutoSignInResult
}

/**
 * Handles user authentication, YouTube-style automatic Google Account sign-in on first launch
 * (selecting the first Google account on the device when multiple exist), and Email/Password
 * registration & login with automatic session persistence.
 */
class UserManager(
    private val context: Context,
    private val dao: NeliMediaDao = NeliDatabase.getInstance(context).mediaDao()
) {

    init {
        ensureFirebaseInitialized(context)
    }

    /**
     * Registers a new user with Real Name, Email, and Password, updates Firebase Auth if configured,
     * and automatically logs the user in immediately upon registration.
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

        // Try FirebaseAuth SDK registration if available
        val firebaseAccount = tryFirebaseSdkSignUp(cleanName, cleanEmail, cleanPassword)
        if (firebaseAccount != null) {
            dao.logoutAllUsers()
            dao.upsertUserAccount(firebaseAccount)
            return@withContext Result.success(firebaseAccount)
        }

        // Fallback to REST / Local persistence with automatic login
        AuthRepository.signUpWithEmailAndPassword(
            context = context,
            dao = dao,
            realName = cleanName,
            email = cleanEmail,
            password = cleanPassword
        )
    }

    /**
     * Logs in an existing user with Email and Password and restores their saved Real Name & profile.
     */
    suspend fun loginWithEmailAndPassword(
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
     * YouTube-style automatic Google Sign-In on first app launch or when the user taps "Continue with Google".
     *
     * 1. If a user is already logged in locally (and `forceAccountSwitch == false`), keeps the session active.
     * 2. Checks if the phone has one or more Google accounts via `AccountManager` or `FirebaseAuth` and
     *    selects the FIRST Google account on the phone to log in / register automatically.
     * 3. Attempts Android `CredentialManager` with `GetGoogleIdOption` (`autoSelectEnabled = true`).
     * 4. If Google Sign-In fails or no Google account exists on the device, returns `UseEmailFallback`
     *    prompting the user to "Use email instead" or register an account.
     */
    suspend fun signInWithGoogleAutoOrPrimaryAccount(
        uiContext: Context = context,
        forceInteractive: Boolean = false
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
                    ?: deriveDisplayNameFromEmail(email)
                val entity = UserAccountEntity(
                    uid = currentFbUser.uid,
                    realName = name,
                    email = email,
                    passwordHash = "google_oauth_account",
                    isLoggedIn = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.logoutAllUsers()
                dao.upsertUserAccount(entity)
                return@withContext GoogleAutoSignInResult.Success(
                    account = entity,
                    autoSelectedPrimaryGoogleAccount = true
                )
            }

            // 2. Check device Google accounts (picks the FIRST Google account on the phone if multiple exist)
            val deviceGoogleAccount = findFirstDeviceGoogleAccount(context)
            if (deviceGoogleAccount != null) {
                val existing = dao.getAccountByEmail(deviceGoogleAccount.email)
                val entity = UserAccountEntity(
                    uid = existing?.uid ?: "google_${UUID.nameUUIDFromBytes(deviceGoogleAccount.email.toByteArray()).toString().replace("-", "").take(16)}",
                    realName = existing?.realName?.takeIf { it.isNotBlank() } ?: deviceGoogleAccount.displayName,
                    email = deviceGoogleAccount.email,
                    passwordHash = existing?.passwordHash ?: "google_device_account",
                    isLoggedIn = true,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.logoutAllUsers()
                dao.upsertUserAccount(entity)
                return@withContext GoogleAutoSignInResult.Success(
                    account = entity,
                    autoSelectedPrimaryGoogleAccount = true
                )
            }

            // 3. Query Android CredentialManager with autoSelectEnabled = true
            val credentialManager = CredentialManager.create(uiContext)
            val webClientId = resolveWebClientId(uiContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(true)
                .setServerClientId(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = uiContext
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id.trim().lowercase()
                val displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                    ?: deriveDisplayNameFromEmail(email)
                val idToken = googleIdTokenCredential.idToken

                // Exchange with FirebaseAuth if available
                val fbUid = signInFirebaseWithGoogleIdToken(idToken)
                val uid = fbUid
                    ?: "google_${UUID.nameUUIDFromBytes(email.toByteArray()).toString().replace("-", "").take(16)}"

                val entity = UserAccountEntity(
                    uid = uid,
                    realName = displayName,
                    email = email,
                    passwordHash = "google_credential_manager",
                    idToken = idToken,
                    isLoggedIn = true,
                    lastLoginAt = System.currentTimeMillis()
                )
                dao.logoutAllUsers()
                dao.upsertUserAccount(entity)
                return@withContext GoogleAutoSignInResult.Success(
                    account = entity,
                    autoSelectedPrimaryGoogleAccount = true
                )
            }

            GoogleAutoSignInResult.UseEmailFallback(
                "No Google account responded on this device. Use email instead, or register a new account below."
            )
        } catch (_: Throwable) {
            GoogleAutoSignInResult.UseEmailFallback(
                "Google Sign-In is unavailable on this device. Use email instead, or register a new account below."
            )
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            getFirebaseAuthOrNull()?.signOut()
        } catch (_: Throwable) {}
        dao.logoutAllUsers()
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
                displayName = deriveDisplayNameFromEmail(email)
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun deriveDisplayNameFromEmail(email: String): String {
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

    private suspend fun tryFirebaseSdkSignUp(
        realName: String,
        email: String,
        password: String
    ): UserAccountEntity? {
        val auth = getFirebaseAuthOrNull() ?: return null
        return try {
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
            suspendCancellableCoroutine { cont ->
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (!cont.isActive) return@addOnCompleteListener
                        val user = if (task.isSuccessful) task.result?.user else null
                        if (user != null) {
                            val resolvedName = user.displayName?.takeIf { it.isNotBlank() }
                                ?: cachedName?.takeIf { it.isNotBlank() }
                                ?: deriveDisplayNameFromEmail(email)
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
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun signInFirebaseWithGoogleIdToken(idToken: String): String? {
        val auth = getFirebaseAuthOrNull() ?: return null
        return try {
            suspendCancellableCoroutine<String?> { cont ->
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { task ->
                        if (cont.isActive) {
                            cont.resume(if (task.isSuccessful) task.result?.user?.uid else null)
                        }
                    }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun getFirebaseAuthOrNull(): FirebaseAuth? {
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
        private const val NELIPLAY_PROJECT_ID = "neliplay"
        private const val NELIPLAY_APP_ID = "1:39702563643:android:7dfc69461cbc615483051e"
        private const val NELIPLAY_RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"
        private const val NELIPLAY_STORAGE_BUCKET = "neliplay.firebasestorage.app"
        private const val NELIPLAY_GCM_SENDER_ID = "39702563643"

        fun ensureFirebaseInitialized(context: Context) {
            try {
                if (FirebaseApp.getApps(context).isNotEmpty()) return
                val apiKey = AuthRepository.resolveApiKey(context)
                if (apiKey.isBlank()) return
                val options = FirebaseOptions.Builder()
                    .setProjectId(NELIPLAY_PROJECT_ID)
                    .setApplicationId(NELIPLAY_APP_ID)
                    .setApiKey(apiKey)
                    .setDatabaseUrl(NELIPLAY_RTDB_URL)
                    .setStorageBucket(NELIPLAY_STORAGE_BUCKET)
                    .setGcmSenderId(NELIPLAY_GCM_SENDER_ID)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            } catch (_: Throwable) {
                // Ignore if already initialized or if API key is not yet provided in Secrets
            }
        }

        fun resolveWebClientId(context: Context): String {
            return try {
                val resId = context.resources.getIdentifier(
                    "default_web_client_id",
                    "string",
                    context.packageName
                )
                if (resId != 0) {
                    context.getString(resId).trim().ifEmpty {
                        "39702563643-neliplay.apps.googleusercontent.com"
                    }
                } else {
                    "39702563643-neliplay.apps.googleusercontent.com"
                }
            } catch (_: Throwable) {
                "39702563643-neliplay.apps.googleusercontent.com"
            }
        }
    }
}
