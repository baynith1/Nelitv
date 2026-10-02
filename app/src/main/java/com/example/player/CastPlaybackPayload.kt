package com.example.player

import android.content.Context
import com.example.BuildConfig
import com.example.data.ChannelRepository
import com.example.model.LiveChannel
import org.json.JSONObject

/**
 * Single Android configuration for the Scan to Cast Web Receiver base URL.
 *
 * - Do NOT hardcode a fake production custom domain.
 * - Defaults to the configured `BuildConfig.CAST_RECEIVER_BASE_URL` (Firebase Hosting URL)
 *   and can be updated in one place when the Web Receiver is deployed.
 * - Constructs: `CAST_RECEIVER_BASE_URL + "/cast/" + sessionId`
 */
object CastReceiverConfig {
    private const val PREFS_NAME = "neli_scan_to_cast_config_prefs"
    private const val KEY_CUSTOM_BASE_URL = "custom_cast_receiver_base_url"

    /**
     * Configured Firebase Hosting base URL for the Scan to Cast Web Receiver.
     * Constructs: `CAST_RECEIVER_BASE_URL + "/cast/" + sessionId`
     */
    const val DEFAULT_RECEIVER_BASE_URL: String = "https://cast-nelitv.web.app"

    @Volatile
    var CAST_RECEIVER_BASE_URL: String = resolveConfiguredBaseUrl()

    private fun resolveConfiguredBaseUrl(): String {
        val fromBuildConfig = try {
            BuildConfig.CAST_RECEIVER_BASE_URL.trim()
        } catch (_: Throwable) {
            ""
        }
        return (if (fromBuildConfig.isNotBlank() &&
            !fromBuildConfig.equals("null", ignoreCase = true) &&
            !fromBuildConfig.contains("neliplay.web.app", ignoreCase = true)
        ) {
            fromBuildConfig
        } else {
            DEFAULT_RECEIVER_BASE_URL
        }).trimEnd('/')
    }

    fun loadSavedBaseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val custom = prefs.getString(KEY_CUSTOM_BASE_URL, null)?.trim()?.trimEnd('/').orEmpty()
        if ((custom.startsWith("http://", ignoreCase = true) || custom.startsWith("https://", ignoreCase = true)) &&
            !custom.contains("neliplay.web.app", ignoreCase = true)
        ) {
            CAST_RECEIVER_BASE_URL = custom
        } else {
            CAST_RECEIVER_BASE_URL = resolveConfiguredBaseUrl()
        }
        return CAST_RECEIVER_BASE_URL
    }

    fun updateReceiverBaseUrl(context: Context?, newBaseUrl: String): String {
        val cleaned = newBaseUrl.trim().trimEnd('/')
        val valid = if (cleaned.startsWith("http://", ignoreCase = true) || cleaned.startsWith("https://", ignoreCase = true)) {
            cleaned
        } else {
            resolveConfiguredBaseUrl()
        }
        CAST_RECEIVER_BASE_URL = valid
        if (context != null) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_BASE_URL, valid)
                .apply()
        }
        return CAST_RECEIVER_BASE_URL
    }

    /**
     * Constructs `CAST_RECEIVER_BASE_URL + "/cast/" + sessionId`.
     */
    fun buildCastUrl(sessionId: String, baseUrlOverride: String? = null): String {
        val base = (baseUrlOverride?.takeIf { it.isNotBlank() } ?: CAST_RECEIVER_BASE_URL)
            .trim()
            .trimEnd('/')
        return "$base/cast/${sessionId.trim()}"
    }

    /**
     * Extracts the `sessionId` from a QR code scanned from `https://cast-nelitv.web.app`:
     * - `https://cast-nelitv.web.app/cast/{sessionId}` -> `{sessionId}`
     * - `https://cast-nelitv.web.app/?sessionId={sessionId}` or `?session={sessionId}` -> `{sessionId}`
     * - JSON `{"sessionId":"..."}` -> `{sessionId}`
     * - Raw session code -> cleaned alphanumeric code
     */
    fun extractSessionIdFromScannedQr(rawScannedText: String): String {
        val trimmed = rawScannedText.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val fromJson = json.optString("sessionId", "").ifBlank {
                    json.optString("session", "")
                }.trim()
                if (fromJson.isNotBlank()) return sanitizeSessionId(fromJson)
            } catch (_: Exception) {
            }
        }

        if (trimmed.contains("/cast/", ignoreCase = true)) {
            val afterCast = trimmed.substringAfterLast("/cast/", "")
                .substringBefore("?")
                .substringBefore("#")
                .substringBefore("/")
                .trim()
            if (afterCast.isNotBlank()) return sanitizeSessionId(afterCast)
        }

        if (trimmed.contains("sessionId=", ignoreCase = true)) {
            val param = trimmed.substringAfter("sessionId=", "")
                .substringBefore("&")
                .substringBefore("#")
                .trim()
            if (param.isNotBlank()) return sanitizeSessionId(param)
        }

        if (trimmed.contains("session=", ignoreCase = true)) {
            val param = trimmed.substringAfter("session=", "")
                .substringBefore("&")
                .substringBefore("#")
                .trim()
            if (param.isNotBlank()) return sanitizeSessionId(param)
        }

        // If user scanned the root domain https://cast-nelitv.web.app without a path segment, return empty so a session is generated
        if (trimmed.equals(DEFAULT_RECEIVER_BASE_URL, ignoreCase = true) ||
            trimmed.equals("$DEFAULT_RECEIVER_BASE_URL/", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val lastSeg = trimmed.trimEnd('/').substringAfterLast('/', "")
                .substringBefore("?")
                .trim()
            if (lastSeg.isNotBlank() &&
                !lastSeg.contains(".") &&
                !lastSeg.equals("cast", ignoreCase = true)
            ) {
                return sanitizeSessionId(lastSeg)
            }
            return ""
        }

        return sanitizeSessionId(trimmed)
    }

    private fun sanitizeSessionId(raw: String): String {
        return raw.replace(Regex("[^A-Za-z0-9_-]"), "").take(48)
    }
}

/**
 * Shared quality presets sent via `SET_QUALITY` to the Web Receiver.
 * Android does NOT invent resolutions or modify stream tokens; the Web Receiver selects
 * the actual DASH representation from the MPD (`AUTO`, `LOW`, `MEDIUM`, `HIGH`).
 */
enum class CastQualityPreset(val code: String, val label: String) {
    AUTO("AUTO", "Auto"),
    LOW("LOW", "Low"),
    MEDIUM("MEDIUM", "Medium"),
    HIGH("HIGH", "High");

    companion object {
        fun fromCode(raw: String?): CastQualityPreset {
            val clean = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.code == clean } ?: AUTO
        }
    }
}

/**
 * Shared remote control commands written to `castSessions/{sessionId}`.
 */
enum class CastRemoteCommand(val code: String) {
    PLAY("PLAY"),
    PAUSE("PAUSE"),
    SEEK("SEEK"),
    SEEK_BACK("SEEK_BACK"),
    SEEK_FORWARD("SEEK_FORWARD"),
    SET_VOLUME("SET_VOLUME"),
    MUTE("MUTE"),
    UNMUTE("UNMUTE"),
    SET_QUALITY("SET_QUALITY"),
    DISCONNECT("DISCONNECT")
}

/**
 * Adapter payload containing ONLY what the Web Receiver needs to play an AZAM TV channel via Shaka Player / DASH.
 *
 * Uses the exact resolved playable tokenized stream URL and ClearKey configuration produced by the existing
 * Android playback pipeline (`ChannelRepository` + `ClearKeyUtil`).
 * Never logs or modifies stream tokens or ClearKeys.
 */
data class CastPlaybackPayload(
    val channelId: String,
    val channelName: String,
    val channelLogoUrl: String,
    val streamUrl: String,
    val backupStreamUrl: String,
    val streamFormat: String,
    val mimeType: String,
    val clearKeys: Map<String, String>,
    val clearKeyJwk: String,
    val preferredAudioLanguage: String,
    val playbackVersion: Int = 1,
    val expiresAt: Long
) {
    fun toJsonObject(): JSONObject {
        val clearKeysObj = JSONObject()
        clearKeys.forEach { (kid, key) ->
            clearKeysObj.put(kid, key)
        }
        return JSONObject().apply {
            put("channelId", channelId)
            put("channelName", channelName)
            put("channelLogoUrl", channelLogoUrl)
            put("streamUrl", streamUrl)
            put("backupStreamUrl", backupStreamUrl)
            put("streamFormat", streamFormat)
            put("mimeType", mimeType)
            put("clearKeys", clearKeysObj)
            put("clearKeyJwk", clearKeyJwk)
            put("preferredAudioLanguage", preferredAudioLanguage)
            put("playbackVersion", playbackVersion)
            put("expiresAt", expiresAt)
        }
    }

    companion object {
        private const val DEFAULT_SESSION_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours

        /**
         * Creates a [CastPlaybackPayload] from an existing hardcoded AZAM [LiveChannel]
         * using the existing Android AZAM stream/token/ClearKey resolution pipeline.
         *
         * Preserves the exact resolved playable stream URL (including `/tok_<JWT>/` path prefix
         * and `?cdntoken=<JWT>` query parameter) unchanged.
         */
        fun fromLiveChannel(
            channel: LiveChannel,
            preferredAudioLanguageOverride: String? = null,
            playbackVersion: Int = 1
        ): CastPlaybackPayload {
            val canonicalAzam = ChannelRepository.resolveHardcodedAzamChannel(channel)

            // 1. Resolve exact playable tokenized stream URL via existing AZAM playback pipeline
            val resolvedStreamUrl = ChannelRepository.resolvePlayableAzamManifestUrl(canonicalAzam.streamUrl)
            val resolvedBackupUrl = if (canonicalAzam.backupStreamUrl.isNotBlank()) {
                ChannelRepository.resolvePlayableAzamManifestUrl(canonicalAzam.backupStreamUrl)
            } else {
                val backupMatch = ChannelRepository.findBackupChannelFor(canonicalAzam)
                backupMatch?.backupStreamUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ChannelRepository.resolvePlayableAzamManifestUrl(it) }
                    .orEmpty()
            }

            // 2. Resolve ClearKeys & W3C ClearKey JWK JSON via existing ChannelRepository & ClearKeyUtil
            val channelPrimaryClearKeys = canonicalAzam.clearKeys
                .filter { (k, v) -> k.isNotBlank() && v.isNotBlank() }
                .map { (k, v) -> k.trim().lowercase() to v.trim().lowercase() }
                .toMap(LinkedHashMap())

            val resolvedAllClearKeys = if (channelPrimaryClearKeys.isNotEmpty()) {
                channelPrimaryClearKeys
            } else {
                ChannelRepository.resolveClearKeysForChannel(canonicalAzam)
            }

            val jwkJson = if (resolvedAllClearKeys.isNotEmpty()) {
                ClearKeyUtil.createClearKeyJwkJson(resolvedAllClearKeys)
            } else {
                ""
            }

            // 3. Resolve stream format and MIME type for Shaka Player / browser DASH playback
            val resolvedFormat = when {
                canonicalAzam.isDash -> "dash"
                canonicalAzam.isHls -> "hls"
                else -> canonicalAzam.streamFormat.lowercase().ifBlank { "dash" }
            }
            val resolvedMimeType = when (resolvedFormat) {
                "dash" -> "application/dash+xml"
                "hls", "m3u8" -> "application/x-mpegURL"
                else -> "video/mp4"
            }

            // 4. Resolve preferred audio language (Kiswahili vs English)
            val preferredLang = when {
                !preferredAudioLanguageOverride.isNullOrBlank() -> preferredAudioLanguageOverride.trim().lowercase()
                canonicalAzam.isKiswahiliOnlyProgram -> "sw"
                canonicalAzam.language.isNotBlank() -> canonicalAzam.language.trim().lowercase()
                else -> "sw"
            }

            // 5. Compute expiration timestamp (epoch ms)
            val nowMs = System.currentTimeMillis()
            val tokenInUrl = extractTokenFromResolvedUrl(resolvedStreamUrl)
            val jwtExpSec = ChannelRepository.extractJwtExpEpochSeconds(tokenInUrl)
            val expiresAtMs = if (jwtExpSec != null && jwtExpSec * 1000L > nowMs) {
                jwtExpSec * 1000L
            } else {
                nowMs + DEFAULT_SESSION_TTL_MS
            }

            val resolvedLogo = ChannelRepository.resolveGuaranteedChannelLogoUrl(canonicalAzam)

            return CastPlaybackPayload(
                channelId = canonicalAzam.id,
                channelName = canonicalAzam.name,
                channelLogoUrl = resolvedLogo,
                streamUrl = resolvedStreamUrl,
                backupStreamUrl = resolvedBackupUrl,
                streamFormat = resolvedFormat,
                mimeType = resolvedMimeType,
                clearKeys = resolvedAllClearKeys,
                clearKeyJwk = jwkJson,
                preferredAudioLanguage = preferredLang,
                playbackVersion = playbackVersion.coerceAtLeast(1),
                expiresAt = expiresAtMs
            )
        }

        private fun extractTokenFromResolvedUrl(url: String): String {
            val basePath = url.substringBefore("?")
            if (basePath.contains("/tok_", ignoreCase = true)) {
                val tok = basePath.substringAfter("/tok_").substringBefore("/").trim()
                if (tok.isNotEmpty()) return tok
            }
            val query = url.substringAfter("?", "")
            return query.split("&")
                .firstOrNull { it.startsWith("cdntoken=", ignoreCase = true) }
                ?.substringAfter("=")
                ?.trim()
                .orEmpty()
        }
    }
}
