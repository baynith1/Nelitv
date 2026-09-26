package com.example.data

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface GoogleAutoSignInResult {
    data class Success(
        val account: UserAccountEntity,
        val autoSelectedPrimaryGoogleAccount: Boolean = true
    ) : GoogleAutoSignInResult

    data class PromptGoogleAccountSelection(
        val suggestedEmail: String = "",
        val suggestedName: String = "",
        val savedAccounts: List<UserAccountEntity> = emptyList()
    ) : GoogleAutoSignInResult

    data class UseEmailFallback(
        val reasonMessage: String = "Google Sign-In could not complete. Use email instead, or register a new account below."
    ) : GoogleAutoSignInResult
}

/**
 * Handles user authentication, YouTube-style automatic Google Account sign-in on first launch
 * (selecting the first Google account on the device when multiple exist), interactive Google Sign-In
 * via CredentialManager / Android System Account Picker / In-App Google Account Sheet, and
 * Email/Password registration & login with automatic session persistence.
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
     * Completes Google Sign-In for a selected or entered Google Account (`@gmail.com` or Google Workspace),
     * syncing the account to Firebase (`neliplay`) and persisting the active user in Room.
     */
    suspend fun completeGoogleAccountSignIn(
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

    suspend fun getSavedAccounts(): List<UserAccountEntity> = withContext(Dispatchers.IO) {
        try {
            dao.getAllSavedAccounts()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * YouTube-style automatic Google Sign-In on first app launch or when the user taps "Continue with Google".
     *
     * 1. If a user is already logged in locally (and `forceInteractive == false`), keeps the session active.
     * 2. Checks active `FirebaseAuth` user if already signed in.
     * 3. Checks if the phone has a visible Google account via `AccountManager` and logs in / syncs with Firebase.
     * 4. Attempts Android `CredentialManager` (`GetSignInWithGoogleOption` & `GetGoogleIdOption`) on the `Activity` context.
     * 5. If `forceInteractive == true` and `CredentialManager` does not return a credential (e.g. on an emulator
     *    or when SHA-1 is not yet added to Firebase Console), returns `PromptGoogleAccountSelection` so the UI
     *    opens the Android System Google Account Picker or In-App Google Account Sign-In Sheet immediately.
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

            // 2. Check device Google accounts visible via AccountManager (picks the FIRST Google account on the phone)
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

            val savedAccounts = dao.getAllSavedAccounts()
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
            val savedAccounts = try {
                dao.getAllSavedAccounts()
            } catch (_: Throwable) {
                emptyList()
            }
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

    private suspend fun tryCredentialManagerGoogleSignIn(
        activity: Activity,
        forceInteractive: Boolean
    ): UserAccountEntity? {
        val credentialManager = CredentialManager.create(activity)
        val webClientId = resolveWebClientId(activity)

        // Try explicit SignInWithGoogle option first when interactive, then One-Tap GetGoogleIdOption
        val requests = buildList {
            if (forceInteractive) {
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                    add(GetCredentialRequest.Builder().addCredentialOption(signInOption).build())
                } catch (_: Throwable) {}
            }
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .setServerClientId(webClientId)
                    .build()
                add(GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build())
            } catch (_: Throwable) {}
        }

        for (request in requests) {
            val response: GetCredentialResponse? = try {
                withTimeoutOrNull(6000L) {
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
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = try {
                    GoogleIdTokenCredential.createFrom(credential.data)
                } catch (_: Throwable) {
                    null
                } ?: continue

                val email = googleIdTokenCredential.id.trim().lowercase()
                val displayName = googleIdTokenCredential.displayName?.takeIf { it.isNotBlank() }
                    ?: deriveDisplayNameFromEmail(email)
                val idToken = googleIdTokenCredential.idToken

                // Exchange with FirebaseAuth if available
                signInFirebaseWithGoogleIdToken(idToken)

                val synced = AuthRepository.signInWithGoogleAccount(
                    context = context,
                    dao = dao,
                    email = email,
                    displayName = displayName,
                    googleIdToken = idToken
                ).getOrNull()

                if (synced != null) {
                    return synced
                }
            }
        }
        return null
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
        return AuthRepository.deriveDisplayNameFromEmail(email)
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
        const val NELIPLAY_PROJECT_ID = "neliplay"
        const val NELIPLAY_APP_ID = "1:39702563643:android:7dfc69461cbc615483051e"
        const val NELIPLAY_RTDB_URL = "https://neliplay-default-rtdb.firebaseio.com"
        const val NELIPLAY_STORAGE_BUCKET = "neliplay.firebasestorage.app"
        const val NELIPLAY_GCM_SENDER_ID = "39702563643"
        const val DEFAULT_WEB_CLIENT_ID = "39702563643-neliplay.apps.googleusercontent.com"

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
                // Ignore if already initialized
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
