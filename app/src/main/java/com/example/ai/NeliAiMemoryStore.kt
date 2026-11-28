package com.example.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistent learning & preference store for Neli AI.
 * Tracks:
 * - Watched channels, genre affinities, and custom taught aliases
 * - 30-day watched movie history (so random downloads NEVER repeat recently watched movies!)
 * - Hands-free mode, Outside-App Overlay Ball, Lock-Screen wake, Voice Cancellation & Captions
 * - Sequential movie playback queue
 */
object NeliAiMemoryStore {

    const val DEFAULT_NELI_AI_CENTER_URL = "https://nelitv.online/v1/neli-ai/command"
    const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

    private const val PREFS_NAME = "neli_ai_brain_memory_v1"
    private const val KEY_CHANNEL_COUNTS_JSON = "channel_play_counts_json"
    private const val KEY_GENRE_COUNTS_JSON = "genre_affinity_counts_json"
    private const val KEY_CUSTOM_ALIASES_JSON = "custom_aliases_json"
    private const val KEY_WATCHED_MOVIES_TS_JSON = "watched_movies_timestamps_json"
    private const val KEY_TOTAL_INTERACTIONS = "total_interactions"
    private const val KEY_HANDS_FREE_ENABLED = "hands_free_enabled"
    private const val KEY_VOICE_REPLY_ENABLED = "voice_reply_enabled"
    private const val KEY_OVERLAY_BALL_ENABLED = "overlay_ball_enabled"
    private const val KEY_LOCK_SCREEN_WAKE_ENABLED = "lock_screen_wake_enabled"
    private const val KEY_VOICE_CANCELLATION_ENABLED = "voice_cancellation_enabled"
    private const val KEY_CAPTIONS_ENABLED = "captions_enabled"
    private const val KEY_POWER_BUTTON_WAKE_ENABLED = "power_button_wake_enabled"
    private const val KEY_REMOTE_ENDPOINT_URL = "remote_endpoint_url"
    private const val KEY_CUSTOM_API_KEY = "custom_neli_ai_api_key"
    private const val KEY_SEQUENCE_QUEUE_JSON = "sequential_play_queue_json"
    private const val KEY_RELATIONSHIPS_JSON = "contact_relationships_json"
    private const val KEY_REMINDERS_JSON = "personal_reminders_json"
    private const val KEY_SAVED_PLACES_JSON = "saved_places_json"
    private const val KEY_PARKING_NOTE = "parking_location_note"
    private const val KEY_PARKING_SAVED_AT_MS = "parking_saved_at_ms"

    private val _learnedAliases = MutableStateFlow<Map<String, String>>(emptyMap())
    val learnedAliases: StateFlow<Map<String, String>> = _learnedAliases.asStateFlow()

    private val _channelPlayCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val channelPlayCounts: StateFlow<Map<String, Int>> = _channelPlayCounts.asStateFlow()

    private val _genreScores = MutableStateFlow<Map<String, Int>>(emptyMap())
    val genreScores: StateFlow<Map<String, Int>> = _genreScores.asStateFlow()

    private val _watchedMovieTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val watchedMovieTimestamps: StateFlow<Map<String, Long>> = _watchedMovieTimestamps.asStateFlow()

    private val _totalInteractions = MutableStateFlow(0)
    val totalInteractions: StateFlow<Int> = _totalInteractions.asStateFlow()

    private val _isHandsFreeEnabled = MutableStateFlow(false)
    val isHandsFreeEnabled: StateFlow<Boolean> = _isHandsFreeEnabled.asStateFlow()

    private val _isVoiceReplyEnabled = MutableStateFlow(true)
    val isVoiceReplyEnabled: StateFlow<Boolean> = _isVoiceReplyEnabled.asStateFlow()

    private val _isOverlayBallEnabled = MutableStateFlow(false)
    val isOverlayBallEnabled: StateFlow<Boolean> = _isOverlayBallEnabled.asStateFlow()

    private val _isLockScreenWakeEnabled = MutableStateFlow(false)
    val isLockScreenWakeEnabled: StateFlow<Boolean> = _isLockScreenWakeEnabled.asStateFlow()

    private val _isVoiceCancellationEnabled = MutableStateFlow(true)
    val isVoiceCancellationEnabled: StateFlow<Boolean> = _isVoiceCancellationEnabled.asStateFlow()

    private val _isCaptionsEnabled = MutableStateFlow(true)
    val isCaptionsEnabled: StateFlow<Boolean> = _isCaptionsEnabled.asStateFlow()

    private val _isPowerButtonWakeEnabled = MutableStateFlow(true)
    val isPowerButtonWakeEnabled: StateFlow<Boolean> = _isPowerButtonWakeEnabled.asStateFlow()

    private val _remoteEndpointUrl = MutableStateFlow(DEFAULT_NELI_AI_CENTER_URL)
    val remoteEndpointUrl: StateFlow<String> = _remoteEndpointUrl.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _sequentialPlayQueueIds = MutableStateFlow<List<String>>(emptyList())
    val sequentialPlayQueueIds: StateFlow<List<String>> = _sequentialPlayQueueIds.asStateFlow()

    private val _contactRelationships = MutableStateFlow<Map<String, String>>(emptyMap())
    val contactRelationships: StateFlow<Map<String, String>> = _contactRelationships.asStateFlow()

    private val _reminders = MutableStateFlow<List<String>>(emptyList())
    val reminders: StateFlow<List<String>> = _reminders.asStateFlow()

    private val _savedPlaces = MutableStateFlow<Map<String, String>>(emptyMap())
    val savedPlaces: StateFlow<Map<String, String>> = _savedPlaces.asStateFlow()

    private val _parkingNote = MutableStateFlow("")
    val parkingNote: StateFlow<String> = _parkingNote.asStateFlow()

    private val _openDictationTrigger = MutableStateFlow(0)
    val openDictationTrigger: StateFlow<Int> = _openDictationTrigger.asStateFlow()

    fun requestOpenDictation() {
        _openDictationTrigger.value = _openDictationTrigger.value + 1
    }

    @Volatile
    var parkingSavedAtMs: Long = 0L

    // Session-level recent suggested movie IDs & active multi-turn conversation state
    @Volatile
    var lastSuggestedMovieIds: List<String> = emptyList()

    @Volatile
    var lastPendingConversationTopic: String = ""

    @Volatile
    var lastPendingPayload: String = ""

    @Volatile
    var lastFocusedMediaId: String = ""

    @Volatile
    var lastListedDownloadIds: List<String> = emptyList()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                _channelPlayCounts.value = parseIntMap(prefs.getString(KEY_CHANNEL_COUNTS_JSON, null))
                _genreScores.value = parseIntMap(prefs.getString(KEY_GENRE_COUNTS_JSON, null))
                _learnedAliases.value = parseStringMap(prefs.getString(KEY_CUSTOM_ALIASES_JSON, null))
                _watchedMovieTimestamps.value = parseLongMap(prefs.getString(KEY_WATCHED_MOVIES_TS_JSON, null))
                _totalInteractions.value = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0)
                _isHandsFreeEnabled.value = prefs.getBoolean(KEY_HANDS_FREE_ENABLED, false)
                _isVoiceReplyEnabled.value = prefs.getBoolean(KEY_VOICE_REPLY_ENABLED, true)
                _isOverlayBallEnabled.value = prefs.getBoolean(KEY_OVERLAY_BALL_ENABLED, false)
                _isLockScreenWakeEnabled.value = prefs.getBoolean(KEY_LOCK_SCREEN_WAKE_ENABLED, false)
                _isVoiceCancellationEnabled.value = prefs.getBoolean(KEY_VOICE_CANCELLATION_ENABLED, true)
                _isCaptionsEnabled.value = prefs.getBoolean(KEY_CAPTIONS_ENABLED, true)
                _isPowerButtonWakeEnabled.value = prefs.getBoolean(KEY_POWER_BUTTON_WAKE_ENABLED, true)
                val savedUrl = prefs.getString(KEY_REMOTE_ENDPOINT_URL, null)
                _remoteEndpointUrl.value = savedUrl ?: DEFAULT_NELI_AI_CENTER_URL
                _customApiKey.value = prefs.getString(KEY_CUSTOM_API_KEY, "").orEmpty()
                _sequentialPlayQueueIds.value = parseStringList(prefs.getString(KEY_SEQUENCE_QUEUE_JSON, null))
                _contactRelationships.value = parseStringMap(prefs.getString(KEY_RELATIONSHIPS_JSON, null))
                _reminders.value = parseStringList(prefs.getString(KEY_REMINDERS_JSON, null))
                _savedPlaces.value = parseStringMap(prefs.getString(KEY_SAVED_PLACES_JSON, null))
                _parkingNote.value = prefs.getString(KEY_PARKING_NOTE, "").orEmpty()
                parkingSavedAtMs = prefs.getLong(KEY_PARKING_SAVED_AT_MS, 0L)
                initialized = true
            } catch (_: Exception) {
                initialized = true
            }
        }
    }

    fun resolveApiKey(): String {
        val override = _customApiKey.value.trim()
        if (override.isNotBlank()) return override
        val fromBuildConfig = runCatching { com.example.BuildConfig.NELI_AI_API_KEY.trim() }.getOrDefault("")
        if (fromBuildConfig.isNotBlank() && !fromBuildConfig.startsWith("YOUR_", ignoreCase = true)) {
            return fromBuildConfig
        }
        return ""
    }

    fun setCustomApiKey(context: Context?, rawKey: String) {
        val clean = rawKey.trim()
        _customApiKey.value = clean
        editPref(context) { putString(KEY_CUSTOM_API_KEY, clean) }
    }

    fun recordInteraction(context: Context?) {
        val next = _totalInteractions.value + 1
        _totalInteractions.value = next
        editPref(context) { putInt(KEY_TOTAL_INTERACTIONS, next) }
    }

    fun recordChannelPlayed(context: Context?, channelName: String, categories: List<String>) {
        if (channelName.isBlank()) return
        val updatedChannels = _channelPlayCounts.value.toMutableMap()
        updatedChannels[channelName] = (updatedChannels[channelName] ?: 0) + 1
        _channelPlayCounts.value = updatedChannels

        val updatedGenres = _genreScores.value.toMutableMap()
        categories.filter { it.isNotBlank() }.forEach { cat ->
            val key = cat.trim().lowercase()
            updatedGenres[key] = (updatedGenres[key] ?: 0) + 1
        }
        _genreScores.value = updatedGenres

        editPref(context) {
            putString(KEY_CHANNEL_COUNTS_JSON, serializeIntMap(updatedChannels))
            putString(KEY_GENRE_COUNTS_JSON, serializeIntMap(updatedGenres))
        }
    }

    /**
     * Records a movie ID as watched with the current timestamp so Neli AI's smart random
     * download command avoids re-downloading movies watched in the past 30 days.
     */
    fun recordMovieWatched(context: Context?, mediaId: String, nowMs: Long = System.currentTimeMillis()) {
        val cleanId = mediaId.trim()
        if (cleanId.isBlank()) return
        val updated = _watchedMovieTimestamps.value.toMutableMap()
        updated[cleanId] = nowMs
        // Prune entries older than 90 days to keep storage tiny
        val cutoff90 = nowMs - (THIRTY_DAYS_MS * 3L)
        updated.entries.removeAll { it.value < cutoff90 }
        _watchedMovieTimestamps.value = updated
        editPref(context) {
            putString(KEY_WATCHED_MOVIES_TS_JSON, serializeLongMap(updated))
        }
    }

    fun getWatchedMovieIdsWithin30Days(nowMs: Long = System.currentTimeMillis()): Set<String> {
        val cutoff = nowMs - THIRTY_DAYS_MS
        return _watchedMovieTimestamps.value
            .filterValues { it >= cutoff }
            .keys
    }

    fun recordGenreInterest(context: Context?, genre: String) {
        val clean = genre.trim().lowercase()
        if (clean.isBlank()) return
        val updatedGenres = _genreScores.value.toMutableMap()
        updatedGenres[clean] = (updatedGenres[clean] ?: 0) + 2
        _genreScores.value = updatedGenres
        editPref(context) { putString(KEY_GENRE_COUNTS_JSON, serializeIntMap(updatedGenres)) }
    }

    fun teachAlias(context: Context?, triggerPhrase: String, targetName: String): Boolean {
        val cleanTrigger = triggerPhrase.trim().lowercase()
        val cleanTarget = targetName.trim()
        if (cleanTrigger.length < 2 || cleanTarget.length < 2) return false
        val updated = _learnedAliases.value.toMutableMap()
        updated[cleanTrigger] = cleanTarget
        _learnedAliases.value = updated
        editPref(context) { putString(KEY_CUSTOM_ALIASES_JSON, serializeStringMap(updated)) }
        return true
    }

    fun removeAlias(context: Context?, triggerPhrase: String) {
        val cleanTrigger = triggerPhrase.trim().lowercase()
        val updated = _learnedAliases.value.toMutableMap()
        if (updated.remove(cleanTrigger) != null) {
            _learnedAliases.value = updated
            editPref(context) { putString(KEY_CUSTOM_ALIASES_JSON, serializeStringMap(updated)) }
        }
    }

    fun setHandsFreeEnabled(context: Context?, enabled: Boolean) {
        _isHandsFreeEnabled.value = enabled
        editPref(context) { putBoolean(KEY_HANDS_FREE_ENABLED, enabled) }
    }

    fun setVoiceReplyEnabled(context: Context?, enabled: Boolean) {
        _isVoiceReplyEnabled.value = enabled
        editPref(context) { putBoolean(KEY_VOICE_REPLY_ENABLED, enabled) }
    }

    fun setOverlayBallEnabled(context: Context?, enabled: Boolean) {
        _isOverlayBallEnabled.value = enabled
        editPref(context) { putBoolean(KEY_OVERLAY_BALL_ENABLED, enabled) }
    }

    fun setLockScreenWakeEnabled(context: Context?, enabled: Boolean) {
        _isLockScreenWakeEnabled.value = enabled
        editPref(context) { putBoolean(KEY_LOCK_SCREEN_WAKE_ENABLED, enabled) }
    }

    fun setVoiceCancellationEnabled(context: Context?, enabled: Boolean) {
        _isVoiceCancellationEnabled.value = enabled
        editPref(context) { putBoolean(KEY_VOICE_CANCELLATION_ENABLED, enabled) }
    }

    fun setCaptionsEnabled(context: Context?, enabled: Boolean) {
        _isCaptionsEnabled.value = enabled
        editPref(context) { putBoolean(KEY_CAPTIONS_ENABLED, enabled) }
    }

    fun setPowerButtonWakeEnabled(context: Context?, enabled: Boolean) {
        _isPowerButtonWakeEnabled.value = enabled
        editPref(context) { putBoolean(KEY_POWER_BUTTON_WAKE_ENABLED, enabled) }
    }

    fun setSequentialPlayQueue(context: Context?, mediaIds: List<String>) {
        val clean = mediaIds.filter { it.isNotBlank() }.distinct()
        _sequentialPlayQueueIds.value = clean
        editPref(context) { putString(KEY_SEQUENCE_QUEUE_JSON, JSONArray(clean).toString()) }
    }

    fun popNextFromSequentialQueue(context: Context?): String? {
        val current = _sequentialPlayQueueIds.value
        if (current.isEmpty()) return null
        val next = current.first()
        val rest = current.drop(1)
        _sequentialPlayQueueIds.value = rest
        editPref(context) { putString(KEY_SEQUENCE_QUEUE_JSON, JSONArray(rest).toString()) }
        return next
    }

    fun setRemoteEndpointUrl(context: Context?, rawUrl: String): Boolean {
        val clean = rawUrl.trim()
        if (clean.isNotEmpty() && !clean.startsWith("https://", ignoreCase = true)) {
            return false
        }
        _remoteEndpointUrl.value = clean
        editPref(context) { putString(KEY_REMOTE_ENDPOINT_URL, clean) }
        return true
    }

    fun topWatchedChannels(limit: Int = 5): List<Pair<String, Int>> {
        return _channelPlayCounts.value.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }

    fun topFavoriteGenres(limit: Int = 3): List<String> {
        return _genreScores.value.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    fun saveRelationship(context: Context?, relationship: String, personName: String) {
        val cleanRel = relationship.trim().lowercase()
        val cleanName = personName.trim()
        if (cleanRel.isBlank() || cleanName.isBlank()) return
        val updated = _contactRelationships.value.toMutableMap()
        updated[cleanRel] = cleanName
        _contactRelationships.value = updated
        editPref(context) { putString(KEY_RELATIONSHIPS_JSON, serializeStringMap(updated)) }
    }

    fun resolveRelationshipName(rawQuery: String): String? {
        val norm = rawQuery.trim().lowercase()
            .removePrefix("my ")
            .removePrefix("wangu ")
            .trim()
        return _contactRelationships.value[norm]
            ?: _contactRelationships.value.entries.firstOrNull { (rel, _) -> norm.contains(rel) }?.value
    }

    fun addReminder(context: Context?, reminderText: String) {
        val clean = reminderText.trim()
        if (clean.isBlank()) return
        val updated = (_reminders.value + clean).distinct().takeLast(30)
        _reminders.value = updated
        editPref(context) { putString(KEY_REMINDERS_JSON, JSONArray(updated).toString()) }
    }

    fun clearReminders(context: Context?) {
        _reminders.value = emptyList()
        editPref(context) { putString(KEY_REMINDERS_JSON, "[]") }
    }

    fun savePlaceAddress(context: Context?, label: String, address: String) {
        val cleanLabel = label.trim().lowercase()
        val cleanAddr = address.trim()
        if (cleanLabel.isBlank() || cleanAddr.isBlank()) return
        val updated = _savedPlaces.value.toMutableMap()
        updated[cleanLabel] = cleanAddr
        _savedPlaces.value = updated
        editPref(context) { putString(KEY_SAVED_PLACES_JSON, serializeStringMap(updated)) }
    }

    fun getPlaceAddress(label: String): String =
        _savedPlaces.value[label.trim().lowercase()].orEmpty()

    fun saveParkingNote(context: Context?, note: String, nowMs: Long = System.currentTimeMillis()) {
        val clean = note.trim()
        if (clean.isBlank()) return
        _parkingNote.value = clean
        parkingSavedAtMs = nowMs
        editPref(context) {
            putString(KEY_PARKING_NOTE, clean)
            putLong(KEY_PARKING_SAVED_AT_MS, nowMs)
        }
    }

    fun resetMemoryForTesting() {
        _learnedAliases.value = emptyMap()
        _channelPlayCounts.value = emptyMap()
        _genreScores.value = emptyMap()
        _watchedMovieTimestamps.value = emptyMap()
        _totalInteractions.value = 0
        _isHandsFreeEnabled.value = false
        _isVoiceReplyEnabled.value = true
        _isOverlayBallEnabled.value = false
        _isLockScreenWakeEnabled.value = false
        _isVoiceCancellationEnabled.value = true
        _isCaptionsEnabled.value = true
        _isPowerButtonWakeEnabled.value = true
        _remoteEndpointUrl.value = DEFAULT_NELI_AI_CENTER_URL
        _customApiKey.value = ""
        _sequentialPlayQueueIds.value = emptyList()
        _contactRelationships.value = emptyMap()
        _reminders.value = emptyList()
        _savedPlaces.value = emptyMap()
        _parkingNote.value = ""
        parkingSavedAtMs = 0L
        lastSuggestedMovieIds = emptyList()
        lastPendingConversationTopic = ""
        lastPendingPayload = ""
        lastFocusedMediaId = ""
        lastListedDownloadIds = emptyList()
        initialized = false
    }

    private fun editPref(context: Context?, block: android.content.SharedPreferences.Editor.() -> Unit) {
        context?.let {
            runCatching {
                it.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .apply(block)
                    .apply()
            }
        }
    }

    private fun parseIntMap(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optInt(k, 0)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseLongMap(raw: String?): Map<String, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Long>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.optLong(k, 0L)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseStringMap(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k, "")
                if (v.isNotBlank()) result[k] = v
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "")
                    if (v.isNotBlank()) add(v)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun serializeIntMap(map: Map<String, Int>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun serializeLongMap(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    private fun serializeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
