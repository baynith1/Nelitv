package com.example.player

import android.content.Context
import com.example.data.ChannelRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

enum class ScanToCastConnectionStatus(val statusKey: String, val displayTitle: String) {
    IDLE("IDLE", "SCAN TO CAST"),
    CREATING_SESSION("CREATING_SESSION", "Connecting to TV..."),
    WAITING_FOR_TV("WAITING_FOR_RECEIVER", "Waiting for TV..."),
    CONNECTED_TO_TV("CONNECTED", "Connected to TV"),
    PAY_TO_WATCH("PAY_TO_WATCH", "Pay to Watch"),
    DISCONNECTED("DISCONNECTED", "Disconnected"),
    EXPIRED("EXPIRED", "Session Expired"),
    ERROR("ERROR", "Connection Issue")
}

/**
 * Represents the complete `castSessions/{sessionId}` state synchronized between the Android
 * remote controller and the TV/PC Web Receiver (`https://cast-nelitv.web.app`) via Firebase Realtime Database.
 */
data class ScanToCastSessionState(
    val sessionId: String = "",
    val qrCastUrl: String = "",
    val connectionStatus: ScanToCastConnectionStatus = ScanToCastConnectionStatus.IDLE,
    val status: String = "IDLE",
    val channelId: String = "",
    val channelName: String = "",
    val channelLogoUrl: String = "",
    val playback: CastPlaybackPayload? = null,
    val command: String = "",
    val commandId: String = "",
    val isPlaying: Boolean = true,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val volume: Float = 1.0f,
    val muted: Boolean = false,
    val currentQuality: CastQualityPreset = CastQualityPreset.AUTO,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val mobileLastSeen: Long = 0L,
    val browserLastSeen: Long = 0L,
    val receiverConnected: Boolean = false,
    val isPayToWatchLocked: Boolean = false,
    val payToWatchMessage: String = "",
    val errorCode: String? = null,
    val userFriendlyError: String? = null,
    val isRefreshingToken: Boolean = false
) {
    val isSessionActive: Boolean
        get() = sessionId.isNotBlank() &&
            connectionStatus != ScanToCastConnectionStatus.IDLE &&
            connectionStatus != ScanToCastConnectionStatus.DISCONNECTED &&
            connectionStatus != ScanToCastConnectionStatus.EXPIRED

    fun toFirebaseSessionJson(): JSONObject {
        return JSONObject().apply {
            put("sessionId", sessionId)
            put("qrCastUrl", qrCastUrl)
            put("status", status)
            put("channelId", channelId)
            put("channelName", channelName)
            put("channelLogoUrl", channelLogoUrl)
            if (isPayToWatchLocked) {
                put("playback", JSONObject.NULL)
                put("streamUrl", "")
                put("manifestUrl", "")
                put("url", "")
                put("payToWatch", true)
                put("lockedByAdmin", true)
                put("payToWatchMessage", payToWatchMessage.ifBlank { "Pay to Watch" })
                put("errorCode", "PAY_TO_WATCH")
                put("error", payToWatchMessage.ifBlank { "Pay to Watch" })
            } else if (playback != null) {
                val playbackJson = playback.toJsonObject()
                put("playback", playbackJson)
                // Mirror playback stream & ClearKey properties at top-level so any Web Receiver version reads them directly
                put("streamUrl", playback.streamUrl)
                put("backupStreamUrl", playback.backupStreamUrl)
                put("manifestUrl", playback.streamUrl)
                put("url", playback.streamUrl)
                put("streamFormat", playback.streamFormat)
                put("mimeType", playback.mimeType)
                put("drmScheme", playback.drmScheme)
                put("encryptionType", playback.drmScheme)
                put("clearKeys", playbackJson.optJSONObject("clearKeys") ?: JSONObject())
                put("clearKeyJwk", playback.clearKeyJwk)
                put("preferredAudioLanguage", playback.preferredAudioLanguage)
                put("isLive", playback.isLive)
                put("expiresAt", playback.expiresAt)
                put("playbackVersion", playback.playbackVersion)
                put("payToWatch", false)
                put("lockedByAdmin", false)
                put("payToWatchMessage", "")
            }
            put("command", command)
            put("commandId", commandId)
            put("isPlaying", if (isPayToWatchLocked) false else isPlaying)
            put("currentPosition", currentPosition)
            put("duration", duration)
            put("volume", volume.toDouble())
            put("muted", muted)
            put("currentQuality", currentQuality.code)
            put("createdAt", createdAt)
            put("updatedAt", updatedAt)
            put("mobileLastSeen", mobileLastSeen)
            put("browserLastSeen", browserLastSeen)
            put("receiverConnected", receiverConnected)
            put("senderConnected", true)
            put("connected", receiverConnected)
        }
    }
}

/**
 * Manages the AZAM-only "Scan to Cast" flow:
 *
 * NeliTV Android app (Remote Controller)
 *   -> Hardcoded AZAM Channel
 *   -> Existing AZAM stream/token/ClearKey resolver (`CastPlaybackPayload.fromLiveChannel`)
 *   -> Temporary Firebase Cast Session (`https://neliplay-default-rtdb.firebaseio.com/castSessions/{sessionId}`)
 *   -> QR Handshake (`CAST_RECEIVER_BASE_URL + "/cast/" + sessionId`)
 *   -> Web Receiver (`receiverConnected = true`)
 *   -> Remote Controls (`PLAY`, `PAUSE`, `SEEK_BACK`, `SEEK_FORWARD`, `SEEK`, `SET_VOLUME`, `MUTE`, `UNMUTE`, `SET_QUALITY`, `DISCONNECT`)
 *   -> Automatic Token Refresh via existing `TokenManager` if the Web Receiver reports token expiry.
 *
 * Security & Privacy:
 * - Never logs stream tokens, JWTs, or ClearKeys.
 * - Survives app backgrounding, incoming phone calls, WhatsApp, and screen lock without keeping local ExoPlayer running.
 */
object ScanToCastManager {

    const val FIREBASE_RTDB_BASE_URL = "https://neliplay-default-rtdb.firebaseio.com"
    const val CAST_SESSIONS_NODE = "castSessions"

    private const val SESSION_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val SESSION_ID_LENGTH = 16
    private const val POLL_INTERVAL_MS = 1_400L
    private const val SEEK_STEP_MS = 10_000L
    private const val SESSION_INACTIVITY_TIMEOUT_MS = 15 * 60 * 1000L // 15 minutes both-sides timeout
    private const val BROWSER_STALE_WARNING_MS = 90 * 1000L // 90 seconds browser heartbeat tolerance

    private val secureRandom = SecureRandom()
    private val commandCounter = AtomicLong(1L)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(6, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .retryOnConnectionFailure(true)
            .build()
    }

    private val _sessionState = MutableStateFlow(ScanToCastSessionState())
    val sessionState: StateFlow<ScanToCastSessionState> = _sessionState.asStateFlow()

    @Volatile
    private var activeChannelRef: LiveChannel? = null

    @Volatile
    private var syncJob: Job? = null

    @Volatile
    private var consecutiveNetworkFailures: Int = 0

    @Volatile
    private var lastHandledTokenRefreshMs: Long = 0L

    /**
     * Generates a high-entropy random sessionId safe for URLs and Firebase keys.
     */
    fun generateHighEntropySessionId(length: Int = SESSION_ID_LENGTH): String {
        val sb = StringBuilder(length)
        repeat(length) {
            val idx = secureRandom.nextInt(SESSION_ALPHABET.length)
            sb.append(SESSION_ALPHABET[idx])
        }
        return sb.toString()
    }

    /**
     * Generates a unique `commandId` to prevent duplicate command execution on the Web Receiver.
     */
    fun generateUniqueCommandId(commandCode: String): String {
        val seq = commandCounter.getAndIncrement()
        val rand = secureRandom.nextInt(9000) + 1000
        return "cmd_${commandCode.lowercase()}_${System.currentTimeMillis()}_${seq}_$rand"
    }

    /**
     * Returns true ONLY if [channel] is eligible for Scan to Cast (strictly existing hardcoded AZAM TV live channels).
     * Movies, Series, and non-AZAM Firebase Live TV channels return false.
     */
    fun isChannelSupportedForScanToCast(channel: LiveChannel?): Boolean {
        return ChannelRepository.isHardcodedAzamChannel(channel)
    }

    /**
     * Checks whether [channel] is locked by Admin for the current user (requires payment before casting).
     */
    fun isChannelLockedForCast(
        channel: LiveChannel?,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ): Boolean {
        if (channel == null) return false
        return NeliAdminManager.isChannelLockedForUser(
            channelId = channel.id,
            currentUser = currentUser,
            isPremiumActive = NeliSubscriptionManager.isPremiumMemberActive(context = context),
            context = context
        )
    }

    /**
     * Connects the Android phone to a TV/PC Web Receiver (`https://cast-nelitv.web.app`) after scanning
     * its QR code with the phone camera, and immediately provides the live tokenized AZAM TV stream.
     */
    fun connectToScannedQrSession(
        context: Context?,
        rawScannedQr: String,
        currentUser: UserAccountEntity? = null,
        initialAzamChannel: LiveChannel? = null,
        preferredAudioLanguage: String? = null
    ): Result<ScanToCastSessionState> {
        if (context != null) {
            CastReceiverConfig.loadSavedBaseUrl(context)
        }

        val extractedId = CastReceiverConfig.extractSessionIdFromScannedQr(rawScannedQr)
        val effectiveSessionId = extractedId.ifBlank { generateHighEntropySessionId() }
        val qrUrl = CastReceiverConfig.buildCastUrl(effectiveSessionId)
        val now = System.currentTimeMillis()

        val targetChannel = initialAzamChannel?.takeIf { isChannelSupportedForScanToCast(it) }
            ?: activeChannelRef?.takeIf { isChannelSupportedForScanToCast(it) }
            ?: ChannelRepository.hardcodedAzamChannels.firstOrNull {
                !isChannelLockedForCast(it, currentUser, context)
            }
            ?: ChannelRepository.hardcodedAzamChannels.firstOrNull()

        val isLocked = targetChannel != null && isChannelLockedForCast(
            channel = targetChannel,
            currentUser = currentUser,
            context = context
        )

        val resolvedChannel = targetChannel?.let { ChannelRepository.resolveHardcodedAzamChannel(it) }
        if (resolvedChannel != null && !isLocked) {
            activeChannelRef = resolvedChannel
        }

        val initialPayload = if (resolvedChannel != null && !isLocked) {
            CastPlaybackPayload.fromLiveChannel(
                channel = resolvedChannel,
                preferredAudioLanguageOverride = preferredAudioLanguage,
                playbackVersion = 1
            )
        } else {
            null
        }

        val initialCommand = if (isLocked) {
            CastRemoteCommand.PAUSE.code
        } else if (initialPayload != null) {
            CastRemoteCommand.PLAY.code
        } else {
            "CONNECT"
        }
        val initialCommandId = generateUniqueCommandId(initialCommand)

        val connectedSession = ScanToCastSessionState(
            sessionId = effectiveSessionId,
            qrCastUrl = qrUrl,
            connectionStatus = if (isLocked) {
                ScanToCastConnectionStatus.PAY_TO_WATCH
            } else {
                ScanToCastConnectionStatus.CONNECTED_TO_TV
            },
            status = if (isLocked) "PAY_TO_WATCH" else "CONNECTED",
            channelId = initialPayload?.channelId ?: resolvedChannel?.id.orEmpty(),
            channelName = initialPayload?.channelName ?: resolvedChannel?.name.orEmpty(),
            channelLogoUrl = initialPayload?.channelLogoUrl
                ?: resolvedChannel?.let { ChannelRepository.resolveGuaranteedChannelLogoUrl(it) }.orEmpty(),
            playback = initialPayload,
            command = initialCommand,
            commandId = initialCommandId,
            isPlaying = !isLocked && initialPayload != null,
            currentPosition = 0L,
            duration = 0L,
            volume = 1.0f,
            muted = false,
            currentQuality = CastQualityPreset.AUTO,
            createdAt = now,
            updatedAt = now,
            mobileLastSeen = now,
            browserLastSeen = now,
            receiverConnected = true,
            isPayToWatchLocked = isLocked,
            payToWatchMessage = if (isLocked) "Pay to Watch" else "",
            errorCode = if (isLocked) "PAY_TO_WATCH" else null,
            userFriendlyError = if (isLocked) "Pay to Watch" else null
        )

        consecutiveNetworkFailures = 0
        _sessionState.value = connectedSession

        syncJob?.cancel()
        syncJob = backgroundScope.launch {
            val fullPayloadJson = connectedSession.toFirebaseSessionJson()
            val patched = patchSessionInFirebase(effectiveSessionId, fullPayloadJson)
            if (!patched) {
                writeFullSessionToFirebase(connectedSession)
            }
            if (!isLocked && resolvedChannel != null) {
                refreshCastSessionTokenInternal(effectiveSessionId)
            }
            observeAndHeartbeatSessionLoop(effectiveSessionId)
        }

        return Result.success(connectedSession)
    }

    /**
     * Pushes a "Pay to Watch" lock state to the casted device (`castSessions/{sessionId}`) when the Admin
     * has locked [channel] and the user has not paid yet.
     * Prevents any stream URL or ClearKey from being sent to the casted device.
     */
    fun notifyLockedChannelPayToWatch(
        context: Context?,
        channel: LiveChannel
    ): ScanToCastSessionState {
        if (context != null) {
            CastReceiverConfig.loadSavedBaseUrl(context)
        }
        val current = _sessionState.value
        val sessionId = current.sessionId.ifBlank { generateHighEntropySessionId() }
        val qrUrl = current.qrCastUrl.ifBlank { CastReceiverConfig.buildCastUrl(sessionId) }
        val now = System.currentTimeMillis()
        val cmdId = generateUniqueCommandId(CastRemoteCommand.PAUSE.code)
        val logoUrl = ChannelRepository.resolveGuaranteedChannelLogoUrl(channel)

        val lockedState = current.copy(
            sessionId = sessionId,
            qrCastUrl = qrUrl,
            connectionStatus = ScanToCastConnectionStatus.PAY_TO_WATCH,
            status = "PAY_TO_WATCH",
            channelId = channel.id,
            channelName = channel.name,
            channelLogoUrl = logoUrl,
            playback = null,
            command = CastRemoteCommand.PAUSE.code,
            commandId = cmdId,
            isPlaying = false,
            updatedAt = now,
            mobileLastSeen = now,
            receiverConnected = current.receiverConnected || current.sessionId.isNotBlank(),
            isPayToWatchLocked = true,
            payToWatchMessage = "Pay to Watch",
            errorCode = "PAY_TO_WATCH",
            userFriendlyError = "Pay to Watch"
        )
        _sessionState.value = lockedState

        backgroundScope.launch {
            val patchJson = JSONObject().apply {
                put("status", "PAY_TO_WATCH")
                put("channelId", channel.id)
                put("channelName", channel.name)
                put("channelLogoUrl", logoUrl)
                put("playback", JSONObject.NULL)
                put("command", CastRemoteCommand.PAUSE.code)
                put("commandId", cmdId)
                put("isPlaying", false)
                put("payToWatch", true)
                put("lockedByAdmin", true)
                put("payToWatchMessage", "Pay to Watch")
                put("errorCode", "PAY_TO_WATCH")
                put("error", "Pay to Watch")
                put("updatedAt", now)
                put("mobileLastSeen", now)
            }
            patchSessionInFirebase(sessionId, patchJson)
        }
        return lockedState
    }

    /**
     * Casts an AZAM channel directly to the connected TV/PC Web Receiver (`https://cast-nelitv.web.app`).
     * - If Admin locked the channel and the user hasn't paid: blocks casting and writes `"Pay to Watch"` to the casted device.
     * - If the channel is free (or user has active Premium / Free Forever VIP): resolves the exact tokenized
     *   AZAM stream URL & ClearKey and starts playback immediately on the casted device player.
     */
    fun castAzamChannelToConnectedDevice(
        context: Context?,
        channel: LiveChannel,
        currentUser: UserAccountEntity? = null,
        preferredAudioLanguage: String? = null
    ): Result<ScanToCastSessionState> {
        if (!isChannelSupportedForScanToCast(channel)) {
            val msg = "Scan to Cast handles hardcoded AZAM TV live channels only."
            return Result.failure(IllegalArgumentException(msg))
        }

        if (isChannelLockedForCast(channel = channel, currentUser = currentUser, context = context)) {
            val lockedState = notifyLockedChannelPayToWatch(context, channel)
            return Result.failure(IllegalStateException(lockedState.payToWatchMessage))
        }

        val current = _sessionState.value
        if (current.sessionId.isNotBlank()) {
            val canonical = ChannelRepository.resolveHardcodedAzamChannel(channel)
            activeChannelRef = canonical
            val nextVersion = (current.playback?.playbackVersion ?: 1) + 1
            val newPayload = CastPlaybackPayload.fromLiveChannel(
                channel = canonical,
                preferredAudioLanguageOverride = preferredAudioLanguage,
                playbackVersion = nextVersion
            )
            val now = System.currentTimeMillis()
            val cmdId = generateUniqueCommandId(CastRemoteCommand.PLAY.code)

            val updated = current.copy(
                connectionStatus = ScanToCastConnectionStatus.CONNECTED_TO_TV,
                status = "CONNECTED",
                channelId = newPayload.channelId,
                channelName = newPayload.channelName,
                channelLogoUrl = newPayload.channelLogoUrl,
                playback = newPayload,
                command = CastRemoteCommand.PLAY.code,
                commandId = cmdId,
                isPlaying = true,
                receiverConnected = true,
                isPayToWatchLocked = false,
                payToWatchMessage = "",
                updatedAt = now,
                mobileLastSeen = now,
                errorCode = null,
                userFriendlyError = null
            )
            _sessionState.value = updated

            syncJob?.cancel()
            syncJob = backgroundScope.launch {
                val patchJson = updated.toFirebaseSessionJson().apply {
                    put("errorCode", JSONObject.NULL)
                    put("error", JSONObject.NULL)
                }
                val patched = patchSessionInFirebase(current.sessionId, patchJson)
                if (!patched) {
                    writeFullSessionToFirebase(updated)
                }
                refreshCastSessionTokenInternal(current.sessionId)
                observeAndHeartbeatSessionLoop(current.sessionId)
            }
            return Result.success(updated)
        }

        return startScanToCastSession(
            context = context,
            channel = channel,
            preferredAudioLanguage = preferredAudioLanguage,
            currentUser = currentUser
        )
    }

    /**
     * Starts a new temporary Scan to Cast session for a hardcoded AZAM TV channel:
     * 1. Validates that the channel is a hardcoded AZAM channel.
     * 2. Checks whether Admin locked the channel for this user (`Pay to Watch`).
     * 3. Resolves the exact playable tokenized stream URL & ClearKey via `CastPlaybackPayload.fromLiveChannel`.
     * 4. Generates a high-entropy random `sessionId`.
     * 5. Builds `CAST_RECEIVER_BASE_URL + "/cast/" + sessionId`.
     * 6. Writes `castSessions/{sessionId}` to Firebase Realtime Database.
     * 7. Starts the background handshake & remote control observer loop.
     */
    fun startScanToCastSession(
        context: Context?,
        channel: LiveChannel,
        preferredAudioLanguage: String? = null,
        currentUser: UserAccountEntity? = null
    ): Result<ScanToCastSessionState> {
        if (!isChannelSupportedForScanToCast(channel)) {
            val msg = "Scan to Cast handles hardcoded AZAM TV live channels only."
            _sessionState.value = ScanToCastSessionState(
                connectionStatus = ScanToCastConnectionStatus.ERROR,
                status = "ERROR",
                errorCode = "UNSUPPORTED_CHANNEL",
                userFriendlyError = msg
            )
            return Result.failure(IllegalArgumentException(msg))
        }

        if (isChannelLockedForCast(channel = channel, currentUser = currentUser, context = context)) {
            val lockedState = notifyLockedChannelPayToWatch(context, channel)
            return Result.failure(IllegalStateException(lockedState.payToWatchMessage))
        }

        if (context != null) {
            CastReceiverConfig.loadSavedBaseUrl(context)
        }

        val canonicalChannel = ChannelRepository.resolveHardcodedAzamChannel(channel)
        activeChannelRef = canonicalChannel

        val payload = CastPlaybackPayload.fromLiveChannel(
            channel = canonicalChannel,
            preferredAudioLanguageOverride = preferredAudioLanguage,
            playbackVersion = 1
        )

        if (payload.streamUrl.isBlank()) {
            val msg = "Stream unavailable for ${canonicalChannel.name}. Please try refreshing the channel."
            _sessionState.value = ScanToCastSessionState(
                connectionStatus = ScanToCastConnectionStatus.ERROR,
                status = "ERROR",
                channelId = canonicalChannel.id,
                channelName = canonicalChannel.name,
                channelLogoUrl = canonicalChannel.thumbnailUrl,
                errorCode = "STREAM_UNAVAILABLE",
                userFriendlyError = msg
            )
            return Result.failure(IllegalStateException(msg))
        }

        val newSessionId = generateHighEntropySessionId()
        val qrUrl = CastReceiverConfig.buildCastUrl(newSessionId)
        val now = System.currentTimeMillis()
        val initialCommandId = generateUniqueCommandId("INIT")

        val initialSession = ScanToCastSessionState(
            sessionId = newSessionId,
            qrCastUrl = qrUrl,
            connectionStatus = ScanToCastConnectionStatus.WAITING_FOR_TV,
            status = "WAITING_FOR_RECEIVER",
            channelId = payload.channelId,
            channelName = payload.channelName,
            channelLogoUrl = payload.channelLogoUrl,
            playback = payload,
            command = "PLAY",
            commandId = initialCommandId,
            isPlaying = true,
            currentPosition = 0L,
            duration = 0L,
            volume = 1.0f,
            muted = false,
            currentQuality = CastQualityPreset.AUTO,
            createdAt = now,
            updatedAt = now,
            mobileLastSeen = now,
            browserLastSeen = 0L,
            receiverConnected = false,
            isPayToWatchLocked = false,
            payToWatchMessage = "",
            errorCode = null,
            userFriendlyError = null
        )

        consecutiveNetworkFailures = 0
        _sessionState.value = initialSession

        syncJob?.cancel()
        syncJob = backgroundScope.launch {
            val createdOk = writeFullSessionToFirebase(initialSession)
            if (!createdOk) {
                consecutiveNetworkFailures++
                _sessionState.value = _sessionState.value.copy(
                    userFriendlyError = "Connecting to Firebase cast signaling... Make sure internet is active."
                )
            }
            observeAndHeartbeatSessionLoop(newSessionId)
        }

        return Result.success(initialSession)
    }

    /**
     * Switches the active Scan to Cast session to another hardcoded AZAM channel without needing to rescan QR.
     */
    fun switchCastAzamChannel(
        channel: LiveChannel,
        preferredAudioLanguage: String? = null,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ) {
        if (!isChannelSupportedForScanToCast(channel)) return
        if (isChannelLockedForCast(channel = channel, currentUser = currentUser, context = context)) {
            notifyLockedChannelPayToWatch(context, channel)
            return
        }

        val current = _sessionState.value
        if (!current.isSessionActive) {
            startScanToCastSession(context, channel, preferredAudioLanguage, currentUser)
            return
        }

        val canonical = ChannelRepository.resolveHardcodedAzamChannel(channel)
        activeChannelRef = canonical
        val nextVersion = (current.playback?.playbackVersion ?: 1) + 1
        val newPayload = CastPlaybackPayload.fromLiveChannel(
            channel = canonical,
            preferredAudioLanguageOverride = preferredAudioLanguage,
            playbackVersion = nextVersion
        )
        val now = System.currentTimeMillis()
        val cmdId = generateUniqueCommandId("PLAY")

        val updated = current.copy(
            connectionStatus = if (current.receiverConnected) {
                ScanToCastConnectionStatus.CONNECTED_TO_TV
            } else {
                current.connectionStatus
            },
            status = if (current.receiverConnected) "CONNECTED" else current.status,
            channelId = newPayload.channelId,
            channelName = newPayload.channelName,
            channelLogoUrl = newPayload.channelLogoUrl,
            playback = newPayload,
            command = CastRemoteCommand.PLAY.code,
            commandId = cmdId,
            isPlaying = true,
            isPayToWatchLocked = false,
            payToWatchMessage = "",
            updatedAt = now,
            mobileLastSeen = now,
            errorCode = null,
            userFriendlyError = null
        )
        _sessionState.value = updated

        backgroundScope.launch {
            val patchJson = updated.toFirebaseSessionJson().apply {
                put("errorCode", JSONObject.NULL)
                put("error", JSONObject.NULL)
            }
            patchSessionInFirebase(current.sessionId, patchJson)
            refreshCastSessionTokenInternal(current.sessionId)
        }
    }

    /**
     * Sends `PLAY` command to the Web Receiver.
     */
    fun sendPlayCommand() {
        sendRemoteCommand(
            command = CastRemoteCommand.PLAY,
            stateUpdater = { it.copy(isPlaying = true) },
            extraFields = { put("isPlaying", true) }
        )
    }

    /**
     * Sends `PAUSE` command to the Web Receiver.
     */
    fun sendPauseCommand() {
        sendRemoteCommand(
            command = CastRemoteCommand.PAUSE,
            stateUpdater = { it.copy(isPlaying = false) },
            extraFields = { put("isPlaying", false) }
        )
    }

    /**
     * Toggles Play / Pause on the Web Receiver.
     */
    fun togglePlayPause() {
        if (_sessionState.value.isPlaying) {
            sendPauseCommand()
        } else {
            sendPlayCommand()
        }
    }

    /**
     * Sends `SEEK_BACK` (and updates target position) to the Web Receiver.
     */
    fun sendSeekBackCommand(offsetMs: Long = SEEK_STEP_MS) {
        val currentPos = _sessionState.value.currentPosition
        val targetPos = (currentPos - offsetMs).coerceAtLeast(0L)
        sendRemoteCommand(
            command = CastRemoteCommand.SEEK_BACK,
            stateUpdater = { it.copy(currentPosition = targetPos) },
            extraFields = {
                put("currentPosition", targetPos)
                put("seekOffsetMs", -offsetMs)
                put("seekOffsetSeconds", -(offsetMs / 1000L))
            }
        )
    }

    /**
     * Sends `SEEK_FORWARD` (and updates target position) to the Web Receiver.
     */
    fun sendSeekForwardCommand(offsetMs: Long = SEEK_STEP_MS) {
        val currentPos = _sessionState.value.currentPosition
        val duration = _sessionState.value.duration
        val rawTarget = currentPos + offsetMs
        val targetPos = if (duration > 0L) rawTarget.coerceAtMost(duration) else rawTarget
        sendRemoteCommand(
            command = CastRemoteCommand.SEEK_FORWARD,
            stateUpdater = { it.copy(currentPosition = targetPos) },
            extraFields = {
                put("currentPosition", targetPos)
                put("seekOffsetMs", offsetMs)
                put("seekOffsetSeconds", offsetMs / 1000L)
            }
        )
    }

    /**
     * Sends `SEEK` to an explicit position (in milliseconds) to the Web Receiver.
     */
    fun sendSeekToPositionCommand(positionMs: Long) {
        val targetPos = positionMs.coerceAtLeast(0L)
        sendRemoteCommand(
            command = CastRemoteCommand.SEEK,
            stateUpdater = { it.copy(currentPosition = targetPos) },
            extraFields = {
                put("currentPosition", targetPos)
            }
        )
    }

    /**
     * Sends `SET_VOLUME` (`0.0f..1.0f`) to the Web Receiver.
     */
    fun sendSetVolumeCommand(volumeFraction: Float) {
        val clamped = volumeFraction.coerceIn(0f, 1f)
        val unmuted = if (clamped > 0f) false else _sessionState.value.muted
        sendRemoteCommand(
            command = CastRemoteCommand.SET_VOLUME,
            stateUpdater = { it.copy(volume = clamped, muted = unmuted) },
            extraFields = {
                put("volume", clamped.toDouble())
                put("muted", unmuted)
            }
        )
    }

    /**
     * Sends `MUTE` command to the Web Receiver.
     */
    fun sendMuteCommand() {
        sendRemoteCommand(
            command = CastRemoteCommand.MUTE,
            stateUpdater = { it.copy(muted = true) },
            extraFields = { put("muted", true) }
        )
    }

    /**
     * Sends `UNMUTE` command to the Web Receiver.
     */
    fun sendUnmuteCommand() {
        sendRemoteCommand(
            command = CastRemoteCommand.UNMUTE,
            stateUpdater = { it.copy(muted = false) },
            extraFields = { put("muted", false) }
        )
    }

    /**
     * Toggles `MUTE` / `UNMUTE` on the Web Receiver.
     */
    fun toggleMute() {
        if (_sessionState.value.muted) {
            sendUnmuteCommand()
        } else {
            sendMuteCommand()
        }
    }

    /**
     * Sends `SET_QUALITY` (`AUTO`, `LOW`, `MEDIUM`, `HIGH`) to the Web Receiver.
     * Android does NOT modify the stream URL or token; the Web Receiver selects the
     * corresponding representation from the DASH MPD.
     */
    fun sendSetQualityCommand(quality: CastQualityPreset) {
        sendRemoteCommand(
            command = CastRemoteCommand.SET_QUALITY,
            stateUpdater = { it.copy(currentQuality = quality) },
            extraFields = {
                put("currentQuality", quality.code)
            }
        )
    }

    /**
     * Refreshes an expired AZAM token using the existing `TokenManager` and updates
     * `castSessions/{sessionId}/playback` in Firebase while preserving the selected channel
     * and current playback position.
     */
    fun refreshCastSessionTokenAsync() {
        val current = _sessionState.value
        val sessionId = current.sessionId
        if (sessionId.isBlank()) return

        backgroundScope.launch {
            refreshCastSessionTokenInternal(sessionId)
        }
    }

    private suspend fun refreshCastSessionTokenInternal(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        val current = _sessionState.value
        if (current.sessionId != sessionId || sessionId.isBlank()) return@withContext false

        _sessionState.value = current.copy(
            isRefreshingToken = true,
            userFriendlyError = null
        )

        return@withContext try {
            // 1. Refresh token using existing Android TokenManager
            TokenManager.fetchLiveToken(forceRefresh = true)
            ChannelRepository.refreshLiveChannels()

            // 2. Resolve updated playable stream URL for the active AZAM channel
            val baseChannel = activeChannelRef
                ?: ChannelRepository.getChannelById(current.channelId)
                ?: ChannelRepository.hardcodedAzamChannels.firstOrNull { it.id == current.channelId }

            if (baseChannel == null) {
                _sessionState.value = _sessionState.value.copy(
                    isRefreshingToken = false,
                    userFriendlyError = "Channel definition unavailable for token refresh."
                )
                return@withContext false
            }

            val resolvedChannel = ChannelRepository.resolveHardcodedAzamChannel(baseChannel)
            activeChannelRef = resolvedChannel

            val nextPlaybackVersion = (current.playback?.playbackVersion ?: 1) + 1
            val refreshedPayload = CastPlaybackPayload.fromLiveChannel(
                channel = resolvedChannel,
                preferredAudioLanguageOverride = current.playback?.preferredAudioLanguage,
                playbackVersion = nextPlaybackVersion
            )

            val now = System.currentTimeMillis()
            val refreshedJson = refreshedPayload.toJsonObject()
            val patchObj = JSONObject().apply {
                put("playback", refreshedJson)
                put("streamUrl", refreshedPayload.streamUrl)
                put("backupStreamUrl", refreshedPayload.backupStreamUrl)
                put("manifestUrl", refreshedPayload.streamUrl)
                put("url", refreshedPayload.streamUrl)
                put("streamFormat", refreshedPayload.streamFormat)
                put("mimeType", refreshedPayload.mimeType)
                put("drmScheme", refreshedPayload.drmScheme)
                put("encryptionType", refreshedPayload.drmScheme)
                put("clearKeys", refreshedJson.optJSONObject("clearKeys") ?: JSONObject())
                put("clearKeyJwk", refreshedPayload.clearKeyJwk)
                put("preferredAudioLanguage", refreshedPayload.preferredAudioLanguage)
                put("isLive", refreshedPayload.isLive)
                put("expiresAt", refreshedPayload.expiresAt)
                put("playbackVersion", refreshedPayload.playbackVersion)
                put("status", if (current.receiverConnected) "CONNECTED" else "WAITING_FOR_RECEIVER")
                put("updatedAt", now)
                put("mobileLastSeen", now)
                put("tokenExpired", false)
                put("needsTokenRefresh", false)
                put("error", JSONObject.NULL)
                put("errorCode", JSONObject.NULL)
            }

            val patched = patchSessionInFirebase(sessionId, patchObj)
            _sessionState.value = _sessionState.value.copy(
                playback = refreshedPayload,
                isRefreshingToken = false,
                status = if (_sessionState.value.receiverConnected) "CONNECTED" else "WAITING_FOR_RECEIVER",
                connectionStatus = if (_sessionState.value.receiverConnected) {
                    ScanToCastConnectionStatus.CONNECTED_TO_TV
                } else {
                    ScanToCastConnectionStatus.WAITING_FOR_TV
                },
                updatedAt = now,
                mobileLastSeen = now,
                errorCode = null,
                userFriendlyError = if (patched) null else "Could not push refreshed stream to Firebase."
            )
            patched
        } catch (_: Exception) {
            _sessionState.value = _sessionState.value.copy(
                isRefreshingToken = false,
                userFriendlyError = "Token refresh failed. Please check your connection and try again."
            )
            false
        }
    }

    /**
     * Sends `DISCONNECT` command to `castSessions/{sessionId}` so the Web Receiver stops playback,
     * releases Shaka Player resources, clears receiver state, and returns to the QR screen,
     * while Android returns to normal NeliTV state.
     */
    fun disconnectCastSession() {
        val current = _sessionState.value
        val sessionId = current.sessionId
        syncJob?.cancel()
        syncJob = null
        activeChannelRef = null

        val now = System.currentTimeMillis()
        val disconnectCmdId = generateUniqueCommandId(CastRemoteCommand.DISCONNECT.code)

        _sessionState.value = ScanToCastSessionState(
            sessionId = "",
            qrCastUrl = "",
            connectionStatus = ScanToCastConnectionStatus.IDLE,
            status = "IDLE"
        )

        if (sessionId.isNotBlank()) {
            backgroundScope.launch {
                val disconnectPatch = JSONObject().apply {
                    put("command", CastRemoteCommand.DISCONNECT.code)
                    put("commandId", disconnectCmdId)
                    put("status", "DISCONNECTED")
                    put("isPlaying", false)
                    put("receiverConnected", false)
                    put("updatedAt", now)
                    put("mobileLastSeen", now)
                }
                patchSessionInFirebase(sessionId, disconnectPatch)
            }
        }
    }

    private fun sendRemoteCommand(
        command: CastRemoteCommand,
        stateUpdater: (ScanToCastSessionState) -> ScanToCastSessionState,
        extraFields: JSONObject.() -> Unit = {}
    ) {
        val current = _sessionState.value
        val sessionId = current.sessionId
        if (sessionId.isBlank()) return

        val now = System.currentTimeMillis()
        val cmdId = generateUniqueCommandId(command.code)
        val updatedState = stateUpdater(current).copy(
            command = command.code,
            commandId = cmdId,
            updatedAt = now,
            mobileLastSeen = now,
            errorCode = null,
            userFriendlyError = null
        )
        _sessionState.value = updatedState

        backgroundScope.launch {
            val patchObj = JSONObject().apply {
                put("command", command.code)
                put("commandId", cmdId)
                put("updatedAt", now)
                put("mobileLastSeen", now)
                extraFields()
            }
            val ok = patchSessionInFirebase(sessionId, patchObj)
            if (!ok) {
                _sessionState.value = _sessionState.value.copy(
                    userFriendlyError = "Unable to reach Firebase cast session. Retrying automatically..."
                )
            }
        }
    }

    private suspend fun observeAndHeartbeatSessionLoop(sessionId: String) {
        while (backgroundScope.isActive && _sessionState.value.sessionId == sessionId) {
            delay(POLL_INTERVAL_MS)
            val current = _sessionState.value
            if (current.sessionId != sessionId) break

            val now = System.currentTimeMillis()

            // 1. Check if session has exceeded inactivity timeout on both sides
            val lastActivity = maxOf(current.mobileLastSeen, current.browserLastSeen, current.createdAt)
            if (lastActivity > 0L && now - lastActivity > SESSION_INACTIVITY_TIMEOUT_MS) {
                _sessionState.value = current.copy(
                    connectionStatus = ScanToCastConnectionStatus.EXPIRED,
                    status = "EXPIRED",
                    errorCode = "SESSION_EXPIRED",
                    userFriendlyError = "Cast session expired due to inactivity. Tap Scan to Cast to start a new session."
                )
                patchSessionInFirebase(
                    sessionId,
                    JSONObject().apply {
                        put("status", "EXPIRED")
                        put("updatedAt", now)
                    }
                )
                break
            }

            // 2. Read latest session state written by the Web Receiver
            val remoteJson = fetchSessionFromFirebase(sessionId)
            if (remoteJson == null) {
                consecutiveNetworkFailures++
                // Do not kill the session on short network interruptions (Section 17)
                if (consecutiveNetworkFailures >= 6) {
                    _sessionState.value = _sessionState.value.copy(
                        userFriendlyError = "Network connection is unstable. Keeping cast session alive..."
                    )
                }
                continue
            }

            if (remoteJson.optBoolean("_emptyNode", false)) {
                // Ensure the session document exists in Firebase for the Web Receiver
                writeFullSessionToFirebase(_sessionState.value)
                continue
            }

            consecutiveNetworkFailures = 0
            applyRemoteSessionSnapshot(sessionId, remoteJson, now)

            // 3. Update mobileLastSeen heartbeat periodically so Web Receiver knows controller is alive
            val latest = _sessionState.value
            if (now - latest.mobileLastSeen >= POLL_INTERVAL_MS * 2) {
                val heartbeatPatch = JSONObject().apply {
                    put("mobileLastSeen", now)
                }
                patchSessionInFirebase(sessionId, heartbeatPatch)
                _sessionState.value = _sessionState.value.copy(mobileLastSeen = now)
            }
        }
    }

    /**
     * Parses a remote `castSessions/{sessionId}` JSON object from Firebase Realtime Database
     * and updates [_sessionState] (including QR handshake detection, receiver telemetry,
     * token refresh requests, and error reporting).
     */
    internal fun applyRemoteSessionSnapshot(
        sessionId: String,
        remoteJson: JSONObject,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val current = _sessionState.value
        if (current.sessionId != sessionId) return

        val remoteStatus = remoteJson.optString("status", current.status).trim()
        val remoteReceiverConnected = remoteJson.optBoolean("receiverConnected", current.receiverConnected)
        val remoteBrowserLastSeen = remoteJson.optLong("browserLastSeen", current.browserLastSeen)
        val remoteIsPlaying = remoteJson.optBoolean("isPlaying", current.isPlaying)
        val remotePosition = remoteJson.optLong("currentPosition", current.currentPosition)
        val remoteDuration = remoteJson.optLong("duration", current.duration)
        val remoteVolume = remoteJson.optDouble("volume", current.volume.toDouble()).toFloat().coerceIn(0f, 1f)
        val remoteMuted = remoteJson.optBoolean("muted", current.muted)
        val remoteQualityCode = remoteJson.optString("currentQuality", current.currentQuality.code)
        val remoteQuality = CastQualityPreset.fromCode(remoteQualityCode)

        val remoteErrorRaw = remoteJson.optString("errorCode", "").ifBlank {
            remoteJson.optString("error", "")
        }.trim()

        val remotePayToWatch = remoteJson.optBoolean("payToWatch", current.isPayToWatchLocked) ||
            remoteStatus.equals("PAY_TO_WATCH", ignoreCase = true) ||
            remoteErrorRaw.equals("PAY_TO_WATCH", ignoreCase = true)

        if (remotePayToWatch || current.isPayToWatchLocked) {
            _sessionState.value = current.copy(
                connectionStatus = ScanToCastConnectionStatus.PAY_TO_WATCH,
                status = "PAY_TO_WATCH",
                playback = null,
                isPlaying = false,
                isPayToWatchLocked = true,
                payToWatchMessage = "Pay to Watch",
                errorCode = "PAY_TO_WATCH",
                userFriendlyError = "Pay to Watch"
            )
            return
        }

        val needsTokenRefresh = remoteJson.optBoolean("tokenExpired", false) ||
            remoteJson.optBoolean("needsTokenRefresh", false) ||
            remoteStatus.equals("TOKEN_EXPIRED", ignoreCase = true) ||
            remoteErrorRaw.equals("TOKEN_EXPIRED", ignoreCase = true)

        if (needsTokenRefresh && nowMs - lastHandledTokenRefreshMs > 5_000L) {
            lastHandledTokenRefreshMs = nowMs
            backgroundScope.launch {
                refreshCastSessionTokenInternal(sessionId)
            }
            return
        }

        // Check if Web Receiver disconnected or session expired remotely
        if (remoteStatus.equals("DISCONNECTED", ignoreCase = true) && current.receiverConnected && !remoteReceiverConnected) {
            _sessionState.value = current.copy(
                connectionStatus = ScanToCastConnectionStatus.DISCONNECTED,
                status = "DISCONNECTED",
                receiverConnected = false,
                isPlaying = false,
                errorCode = "RECEIVER_DISCONNECTED",
                userFriendlyError = "TV/PC browser disconnected from the cast session."
            )
            return
        }

        if (remoteStatus.equals("EXPIRED", ignoreCase = true)) {
            _sessionState.value = current.copy(
                connectionStatus = ScanToCastConnectionStatus.EXPIRED,
                status = "EXPIRED",
                receiverConnected = false,
                isPlaying = false,
                errorCode = "SESSION_EXPIRED",
                userFriendlyError = "Cast session has expired. Please start a new Scan to Cast session."
            )
            return
        }

        val mappedError = if (remoteErrorRaw.isNotBlank() && !remoteErrorRaw.equals("null", ignoreCase = true)) {
            mapCastErrorToUserFriendlyMessage(remoteErrorRaw)
        } else {
            null
        }

        val effectiveConnected = current.receiverConnected ||
            remoteReceiverConnected ||
            remoteStatus.equals("CONNECTED", ignoreCase = true) ||
            remoteStatus.equals("PLAYING", ignoreCase = true) ||
            remoteStatus.equals("PAUSED", ignoreCase = true) ||
            remoteStatus.equals("BUFFERING", ignoreCase = true) ||
            remoteStatus.equals("READY", ignoreCase = true) ||
            remoteStatus.equals("STREAMING", ignoreCase = true)

        val browserStale = effectiveConnected &&
            remoteBrowserLastSeen > 0L &&
            (nowMs - remoteBrowserLastSeen > BROWSER_STALE_WARNING_MS)

        val nextConnectionStatus = when {
            mappedError != null && !effectiveConnected -> ScanToCastConnectionStatus.ERROR
            effectiveConnected -> ScanToCastConnectionStatus.CONNECTED_TO_TV
            else -> ScanToCastConnectionStatus.WAITING_FOR_TV
        }

        val effectiveStatusString = when {
            effectiveConnected && current.status == "WAITING_FOR_RECEIVER" -> "CONNECTED"
            else -> remoteStatus.ifBlank { current.status }
        }

        val staleWarning = if (browserStale && mappedError == null) {
            "Waiting for TV browser response... Make sure the cast tab is open on your TV/PC."
        } else {
            mappedError
        }

        _sessionState.value = current.copy(
            connectionStatus = nextConnectionStatus,
            status = effectiveStatusString,
            receiverConnected = effectiveConnected,
            isPlaying = remoteIsPlaying,
            currentPosition = remotePosition,
            duration = remoteDuration,
            volume = remoteVolume,
            muted = remoteMuted,
            currentQuality = remoteQuality,
            browserLastSeen = remoteBrowserLastSeen,
            updatedAt = remoteJson.optLong("updatedAt", current.updatedAt),
            errorCode = remoteErrorRaw.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) },
            userFriendlyError = staleWarning
        )
    }

    /**
     * Maps error codes reported by Firebase or the Web Receiver to clear, user-friendly messages (Section 20).
     */
    fun mapCastErrorToUserFriendlyMessage(errorCodeOrMessage: String): String {
        val upper = errorCodeOrMessage.trim().uppercase()
        return when {
            upper.contains("INVALID_SESSION") || upper.contains("NOT_FOUND") ->
                "Invalid cast session. Please tap Scan to Cast to generate a new QR code."
            upper.contains("EXPIRED") && !upper.contains("TOKEN") ->
                "Cast session has expired. Please start a new Scan to Cast session."
            upper.contains("RECEIVER_DISCONNECTED") || upper.contains("DISCONNECTED") ->
                "TV/PC browser disconnected from the cast session."
            upper.contains("FIREBASE") || upper.contains("NETWORK") || upper.contains("OFFLINE") ->
                "Firebase signaling is temporarily unavailable. Check your internet connection."
            upper.contains("TOKEN_EXPIRED") || upper.contains("403") || upper.contains("401") ->
                "Stream token expired. Refreshing AZAM stream authorization automatically..."
            upper.contains("STREAM_UNAVAILABLE") || upper.contains("404") || upper.contains("MANIFEST") ->
                "AZAM stream is temporarily unavailable on the TV browser."
            upper.contains("BROWSER_UNSUPPORTED") || upper.contains("EME") || upper.contains("MSE") ->
                "This TV/PC browser does not support encrypted DASH playback. Please use Chrome, Edge, or a modern Smart TV browser."
            upper.contains("CORS") ->
                "Browser CORS restriction detected while loading the AZAM stream on TV/PC."
            upper.contains("CLEARKEY") || upper.contains("DRM") || upper.contains("KEY") ->
                "ClearKey DRM initialization failed on the TV/PC browser."
            upper.contains("DASH") || upper.contains("SHAKA") || upper.contains("PLAYBACK") ->
                "DASH playback error occurred on the TV/PC browser. Tap Retry or switch quality."
            else -> errorCodeOrMessage.trim().ifBlank {
                "An unexpected cast issue occurred. Please try again."
            }
        }
    }

    private fun buildSessionEndpointUrl(sessionId: String): String {
        return "$FIREBASE_RTDB_BASE_URL/$CAST_SESSIONS_NODE/${sessionId.trim()}.json"
    }

    private fun writeFullSessionToFirebase(session: ScanToCastSessionState): Boolean {
        if (session.sessionId.isBlank()) return false
        return try {
            val bodyStr = session.toFirebaseSessionJson().toString()
            val request = Request.Builder()
                .url(buildSessionEndpointUrl(session.sessionId))
                .put(bodyStr.toRequestBody(jsonMediaType))
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun patchSessionInFirebase(sessionId: String, patchJson: JSONObject): Boolean {
        if (sessionId.isBlank()) return false
        return try {
            val bodyStr = patchJson.toString()
            val request = Request.Builder()
                .url(buildSessionEndpointUrl(sessionId))
                .patch(bodyStr.toRequestBody(jsonMediaType))
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchSessionFromFirebase(sessionId: String): JSONObject? {
        if (sessionId.isBlank()) return null
        return try {
            val request = Request.Builder()
                .url(buildSessionEndpointUrl(sessionId))
                .get()
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val raw = response.body?.string()?.trim().orEmpty()
                if (raw.isBlank() || raw == "null") {
                    JSONObject().apply {
                        put("_emptyNode", true)
                    }
                } else {
                    JSONObject(raw)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
