package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ContinueWatchingEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val posterUrl: String,
    val genre: String,
    val isLiveBroadcast: Boolean,
    val lastPositionMs: Long,
    val durationMs: Long,
    val progressPercent: Int,
    val lastLineLabel: String,
    val updatedAtMs: Long,
    val streamUrl: String = "",
    val seriesId: String = "",
    val isSeries: Boolean = false
)

data class UserMediaRating(
    val mediaId: String,
    val title: String,
    val stars: Int,
    val updatedAtMs: Long
)

data class AccountPreferencesState(
    val audioPreference: String = "AUTO", // "AUTO", "SWAHILI", "ORIGINAL"
    val dailyMovieNotificationsEnabled: Boolean = true,
    val liveSportsNotificationsEnabled: Boolean = true,
    val notificationSoundEnabled: Boolean = true
)

/**
 * Tracks real Continue Watching history (with exact last timestamp line + percentage),
 * user media ratings, loyalty points, audio preferences, and notification sound/category toggles.
 */
object NeliWatchProgressManager {

    private const val PREFS_NAME = "neli_watch_progress_prefs"
    private const val KEY_CONTINUE_WATCHING = "continue_watching_json"
    private const val KEY_RATINGS = "user_ratings_json"
    private const val KEY_AUDIO_PREF = "audio_preference"
    private const val KEY_NOTIF_MOVIES = "notif_daily_movies_enabled"
    private const val KEY_NOTIF_SPORTS = "notif_live_sports_enabled"
    private const val KEY_NOTIF_SOUND = "notif_sound_enabled"
    private const val MAX_CONTINUE_ITEMS = 12

    private val _continueWatching = MutableStateFlow<List<ContinueWatchingEntry>>(emptyList())
    val continueWatching: StateFlow<List<ContinueWatchingEntry>> = _continueWatching.asStateFlow()

    private val _userRatings = MutableStateFlow<List<UserMediaRating>>(emptyList())
    val userRatings: StateFlow<List<UserMediaRating>> = _userRatings.asStateFlow()

    private val _preferences = MutableStateFlow(AccountPreferencesState())
    val preferences: StateFlow<AccountPreferencesState> = _preferences.asStateFlow()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        val appContext = context.applicationContext
        try {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _continueWatching.value = parseContinueWatching(prefs.getString(KEY_CONTINUE_WATCHING, null))
            _userRatings.value = parseRatings(prefs.getString(KEY_RATINGS, null))
            _preferences.value = AccountPreferencesState(
                audioPreference = prefs.getString(KEY_AUDIO_PREF, "AUTO") ?: "AUTO",
                dailyMovieNotificationsEnabled = prefs.getBoolean(KEY_NOTIF_MOVIES, true),
                liveSportsNotificationsEnabled = prefs.getBoolean(KEY_NOTIF_SPORTS, true),
                notificationSoundEnabled = prefs.getBoolean(KEY_NOTIF_SOUND, true)
            )
            initialized = true
        } catch (_: Exception) {
        }
    }

    fun recordPlaybackProgress(
        context: Context,
        id: String,
        title: String,
        subtitle: String = "",
        posterUrl: String,
        genre: String = "",
        isLiveBroadcast: Boolean = false,
        positionMs: Long,
        durationMs: Long,
        streamUrl: String = "",
        seriesId: String = "",
        isSeries: Boolean = false
    ) {
        if (id.isBlank() || title.isBlank()) return
        initialize(context)
        val safePos = positionMs.coerceAtLeast(0L)
        val safeDur = durationMs.coerceAtLeast(0L)
        val pct = when {
            isLiveBroadcast -> 100
            safeDur > 1_000L -> ((safePos * 100L) / safeDur).toInt().coerceIn(1, 99)
            safePos > 0L -> 5
            else -> 1
        }
        val lastLine = if (isLiveBroadcast) {
            "Live TV • Watched recently"
        } else if (safeDur > 1_000L) {
            "Stopped at ${formatTimeMs(safePos)} / ${formatTimeMs(safeDur)} • $pct%"
        } else {
            "Stopped at ${formatTimeMs(safePos)} • $pct%"
        }

        val entry = ContinueWatchingEntry(
            id = id,
            title = title,
            subtitle = subtitle,
            posterUrl = posterUrl,
            genre = genre.ifBlank { if (isLiveBroadcast) "Live TV" else if (isSeries) "Series" else "Movie" },
            isLiveBroadcast = isLiveBroadcast,
            lastPositionMs = safePos,
            durationMs = safeDur,
            progressPercent = pct,
            lastLineLabel = lastLine,
            updatedAtMs = System.currentTimeMillis(),
            streamUrl = streamUrl,
            seriesId = seriesId,
            isSeries = isSeries
        )

        val updated = buildList {
            add(entry)
            addAll(_continueWatching.value.filterNot { it.id == id })
        }.take(MAX_CONTINUE_ITEMS)

        _continueWatching.value = updated
        persistContinueWatching(context.applicationContext, updated)
    }

    fun removeContinueWatching(context: Context, id: String) {
        if (id.isBlank()) return
        initialize(context)
        val updated = _continueWatching.value.filterNot { it.id == id }
        _continueWatching.value = updated
        persistContinueWatching(context.applicationContext, updated)
    }

    fun getResumePositionMs(context: Context, id: String): Long {
        initialize(context)
        val entry = _continueWatching.value.firstOrNull { it.id == id && !it.isLiveBroadcast }
            ?: return 0L
        if (entry.progressPercent >= 96) return 0L
        return entry.lastPositionMs.coerceAtLeast(0L)
    }

    fun saveUserRating(context: Context, mediaId: String, title: String, stars: Int) {
        if (mediaId.isBlank()) return
        initialize(context)
        val clamped = stars.coerceIn(1, 5)
        val item = UserMediaRating(
            mediaId = mediaId,
            title = title,
            stars = clamped,
            updatedAtMs = System.currentTimeMillis()
        )
        val updated = buildList {
            add(item)
            addAll(_userRatings.value.filterNot { it.mediaId == mediaId })
        }.take(25)
        _userRatings.value = updated
        persistRatings(context.applicationContext, updated)
    }

    fun setAudioPreference(context: Context, pref: String) {
        initialize(context)
        val updated = _preferences.value.copy(audioPreference = pref)
        _preferences.value = updated
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUDIO_PREF, pref)
            .apply()
    }

    fun setNotificationToggles(
        context: Context,
        dailyMovies: Boolean = _preferences.value.dailyMovieNotificationsEnabled,
        liveSports: Boolean = _preferences.value.liveSportsNotificationsEnabled,
        soundEnabled: Boolean = _preferences.value.notificationSoundEnabled
    ) {
        initialize(context)
        val updated = _preferences.value.copy(
            dailyMovieNotificationsEnabled = dailyMovies,
            liveSportsNotificationsEnabled = liveSports,
            notificationSoundEnabled = soundEnabled
        )
        _preferences.value = updated
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_MOVIES, dailyMovies)
            .putBoolean(KEY_NOTIF_SPORTS, liveSports)
            .putBoolean(KEY_NOTIF_SOUND, soundEnabled)
            .apply()
    }

    fun computeLoyaltyPoints(
        watchlistCount: Int,
        downloadsCount: Int,
        continueWatchingCount: Int = _continueWatching.value.size,
        ratingsCount: Int = _userRatings.value.size,
        isPremium: Boolean
    ): Int {
        val base = 120
        val activityPoints = (continueWatchingCount * 45) +
            (watchlistCount * 20) +
            (downloadsCount * 35) +
            (ratingsCount * 30)
        val bonus = if (isPremium) 500 else 0
        return base + activityPoints + bonus
    }

    fun resolveLoyaltyTierLabel(points: Int, isPremium: Boolean): String = when {
        isPremium -> "VIP Gold Member"
        points >= 600 -> "Cinema Gold"
        points >= 300 -> "Cinema Silver"
        else -> "Neli Explorer"
    }

    fun formatTimeMs(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun persistContinueWatching(context: Context, items: List<ContinueWatchingEntry>) {
        try {
            val arr = JSONArray()
            items.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("title", item.title)
                        put("subtitle", item.subtitle)
                        put("posterUrl", item.posterUrl)
                        put("genre", item.genre)
                        put("isLiveBroadcast", item.isLiveBroadcast)
                        put("lastPositionMs", item.lastPositionMs)
                        put("durationMs", item.durationMs)
                        put("progressPercent", item.progressPercent)
                        put("lastLineLabel", item.lastLineLabel)
                        put("updatedAtMs", item.updatedAtMs)
                        put("streamUrl", item.streamUrl)
                        put("seriesId", item.seriesId)
                        put("isSeries", item.isSeries)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONTINUE_WATCHING, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun parseContinueWatching(raw: String?): List<ContinueWatchingEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    val title = obj.optString("title")
                    if (id.isNotBlank() && title.isNotBlank()) {
                        add(
                            ContinueWatchingEntry(
                                id = id,
                                title = title,
                                subtitle = obj.optString("subtitle"),
                                posterUrl = obj.optString("posterUrl"),
                                genre = obj.optString("genre", "Movie"),
                                isLiveBroadcast = obj.optBoolean("isLiveBroadcast", false),
                                lastPositionMs = obj.optLong("lastPositionMs", 0L),
                                durationMs = obj.optLong("durationMs", 0L),
                                progressPercent = obj.optInt("progressPercent", 1).coerceIn(1, 100),
                                lastLineLabel = obj.optString("lastLineLabel"),
                                updatedAtMs = obj.optLong("updatedAtMs", 0L),
                                streamUrl = obj.optString("streamUrl"),
                                seriesId = obj.optString("seriesId"),
                                isSeries = obj.optBoolean("isSeries", false)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistRatings(context: Context, items: List<UserMediaRating>) {
        try {
            val arr = JSONArray()
            items.forEach { r ->
                arr.put(
                    JSONObject().apply {
                        put("mediaId", r.mediaId)
                        put("title", r.title)
                        put("stars", r.stars)
                        put("updatedAtMs", r.updatedAtMs)
                    }
                )
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RATINGS, arr.toString())
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun parseRatings(raw: String?): List<UserMediaRating> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("mediaId")
                    if (id.isNotBlank()) {
                        add(
                            UserMediaRating(
                                mediaId = id,
                                title = obj.optString("title"),
                                stars = obj.optInt("stars", 5).coerceIn(1, 5),
                                updatedAtMs = obj.optLong("updatedAtMs", 0L)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
