package com.example.data

import android.app.Activity
import android.content.Context
import com.example.data.local.NeliDatabase
import com.example.data.local.NeliMediaDao
import com.example.data.local.UserAccountEntity
import kotlinx.coroutines.flow.Flow

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
 * Facade over [AuthenticationRepository] that coordinates user authentication,
 * automatic/interactive Google Sign-In, Email/Password registration & login,
 * and active user session management.
 */
class UserManager(
    private val context: Context,
    private val dao: NeliMediaDao = NeliDatabase.getInstance(context).mediaDao(),
    val authenticationRepository: AuthenticationRepository = AuthenticationRepository(context, dao)
) {

    val currentUserFlow: Flow<UserAccountEntity?>
        get() = authenticationRepository.currentUserFlow

    val authStateFlow: Flow<AuthSessionState>
        get() = authenticationRepository.authStateFlow

    suspend fun registerWithEmailAndPassword(
        realName: String,
        email: String,
        password: String
    ): Result<UserAccountEntity> = authenticationRepository.registerWithEmailAndPassword(
        realName = realName,
        email = email,
        password = password
    )

    suspend fun loginWithEmailAndPassword(
        email: String,
        password: String
    ): Result<UserAccountEntity> = authenticationRepository.signInWithEmailAndPassword(
        email = email,
        password = password
    )

    suspend fun completeGoogleAccountSignIn(
        email: String,
        displayName: String = "",
        idToken: String = ""
    ): Result<UserAccountEntity> = authenticationRepository.signInWithGoogleAccount(
        email = email,
        displayName = displayName,
        idToken = idToken
    )

    suspend fun getSavedAccounts(): List<UserAccountEntity> =
        authenticationRepository.getSavedAccounts()

    suspend fun getCurrentUser(): UserAccountEntity? =
        authenticationRepository.getCurrentUser()

    suspend fun isUserSignedIn(): Boolean =
        authenticationRepository.isUserSignedIn()

    suspend fun switchActiveSession(email: String): Result<UserAccountEntity> =
        authenticationRepository.switchActiveSession(email)

    suspend fun refreshSessionToken(): Result<UserAccountEntity> =
        authenticationRepository.refreshSessionToken()

    suspend fun signInWithGoogleAutoOrPrimaryAccount(
        uiContext: Context = context,
        forceInteractive: Boolean = false
    ): GoogleAutoSignInResult = authenticationRepository.signInWithGoogle(
        uiContext = uiContext,
        forceInteractive = forceInteractive
    )

    suspend fun signOut() {
        authenticationRepository.signOut()
    }

    companion object {
        const val NELIPLAY_PROJECT_ID = AuthenticationRepository.NELIPLAY_PROJECT_ID
        const val NELIPLAY_APP_ID = AuthenticationRepository.NELIPLAY_APP_ID
        const val NELIPLAY_RTDB_URL = AuthenticationRepository.NELIPLAY_RTDB_URL
        const val NELIPLAY_STORAGE_BUCKET = AuthenticationRepository.NELIPLAY_STORAGE_BUCKET
        const val NELIPLAY_GCM_SENDER_ID = AuthenticationRepository.NELIPLAY_GCM_SENDER_ID
        const val DEFAULT_WEB_CLIENT_ID = AuthenticationRepository.DEFAULT_WEB_CLIENT_ID
        const val ANDROID_OAUTH_CLIENT_ID = AuthenticationRepository.ANDROID_OAUTH_CLIENT_ID
        const val SHA1_CERTIFICATE_FINGERPRINT = AuthenticationRepository.SHA1_CERTIFICATE_FINGERPRINT
        const val SHA1_CERTIFICATE_HASH = AuthenticationRepository.SHA1_CERTIFICATE_HASH
        const val SHA256_CERTIFICATE_FINGERPRINT = AuthenticationRepository.SHA256_CERTIFICATE_FINGERPRINT
        const val SHA256_CERTIFICATE_HASH = AuthenticationRepository.SHA256_CERTIFICATE_HASH

        fun findActivity(context: Context?): Activity? =
            AuthenticationRepository.findActivity(context)

        fun isRunningOnEmulator(): Boolean =
            AuthenticationRepository.isRunningOnEmulator()

        fun ensureFirebaseInitialized(context: Context) =
            AuthenticationRepository.ensureFirebaseInitialized(context)

        fun resolveGoogleAppId(context: Context): String =
            AuthenticationRepository.resolveGoogleAppId(context)

        fun resolveWebClientId(context: Context): String =
            AuthenticationRepository.resolveWebClientId(context)
    }
}
