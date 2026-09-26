package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.TmdbRepository
import com.example.notifications.NeliNotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Nelitv (by Neliplay) Home Screen App Widget:
 * - Top Row (Juu): 3 Curated Live TV Channels — Azam Sports 1 HD, Azam Two, and WWE (with real channel logos via URL)
 * - Bottom Row (Chini): 3 New 2026 Movies (strictly non-adult, with real TMDB movie posters via URL)
 * - Silent persistence: Once pinned on the user's home screen, it never shows annoying pin prompts or widget notifications again.
 */
class NeliHomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.widget.ACTION_REFRESH_WIDGET"
        private const val WIDGET_PREFS = "neli_widget_auto_pin_prefs"
        private const val KEY_AUTO_PIN_REQUESTED = "auto_pin_requested"

        @Volatile
        private var autoPinCheckedThisSession = false

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

        private val TOP_WIDGET_CHANNEL_IDS = listOf(
            "R17JUvbCEzu2eTbjnE74",                 // 1. Azam Sports 1 HD
            "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // 2. Azam Two
            "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2"  // 3. WWE
        )

        private val TOP_WIDGET_CHANNEL_LOGO_URLS = listOf(
            "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            "https://upload.wikimedia.org/wikipedia/commons/thumb/0/03/WWE_Logo.svg/512px-WWE_Logo.svg.png"
        )

        fun isWidgetPinned(context: Context): Boolean {
            return try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, NeliHomeWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                ids != null && ids.isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

        /**
         * Ensures the Nelitv Home Screen Widget is updated with channel logos and TMDB movie posters.
         * Requests widget pinning ONLY ONCE on initial install if not already pinned, and never shows
         * repeated widget notifications or prompts once the widget is placed on the home screen.
         */
        fun ensureWidgetAutomaticallyPinnedAndUpdated(context: Context) {
            updateAllWidgets(context)
            if (isWidgetPinned(context)) {
                // Widget is already on the home screen — mark as pinned and never show pin prompts again
                try {
                    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_AUTO_PIN_REQUESTED, true)
                        .apply()
                } catch (_: Exception) {
                }
                return
            }

            if (!autoPinCheckedThisSession) {
                autoPinCheckedThisSession = true
                val prefs = try {
                    context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                } catch (_: Exception) {
                    null
                }
                val alreadyRequestedOnce = prefs?.getBoolean(KEY_AUTO_PIN_REQUESTED, false) == true
                if (!alreadyRequestedOnce) {
                    prefs?.edit()?.putBoolean(KEY_AUTO_PIN_REQUESTED, true)?.apply()
                    requestPinWidget(context)
                }
            }
        }

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, NeliHomeWidgetProvider::class.java)
                val ids = appWidgetManager.getAppWidgetIds(componentName)
                if (ids != null && ids.isNotEmpty()) {
                    for (id in ids) {
                        updateSingleWidget(context, appWidgetManager, id)
                    }
                }
            } catch (_: Exception) {
            }
        }

        fun requestPinWidget(context: Context): Boolean {
            if (isWidgetPinned(context)) return true
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    if (appWidgetManager.isRequestPinAppWidgetSupported) {
                        val provider = ComponentName(context, NeliHomeWidgetProvider::class.java)
                        return appWidgetManager.requestPinAppWidget(provider, null, null)
                    }
                }
                false
            } catch (_: Exception) {
                false
            }
        }

        fun updateSingleWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = buildRemoteViews(context)
            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Asynchronously fetch any uncached Channel Logo or TMDB Movie Poster URLs and refresh widget images
            widgetScope.launch {
                val topChannels = TOP_WIDGET_CHANNEL_IDS.mapNotNull { id ->
                    ChannelRepository.getChannelById(id)
                }
                val top2026Movies = MediaContentRepository.getLatest2026NonAdultMovies(limit = 3)

                var downloadedAnyNewBitmap = false
                for (i in 0 until 3) {
                    val ch = topChannels.getOrNull(i)
                    val logoUrl = ch?.thumbnailUrl?.takeIf { it.startsWith("http", true) }
                        ?: TOP_WIDGET_CHANNEL_LOGO_URLS.getOrNull(i).orEmpty()
                    if (logoUrl.isNotBlank() && !bitmapCache.containsKey(logoUrl)) {
                        val bmp = downloadAndScaleWidgetBitmap(
                            urlStr = logoUrl,
                            targetW = 128,
                            targetH = 84,
                            crop = false,
                            fallbackTitle = ch?.name ?: "LIVE"
                        )
                        if (bmp != null) {
                            bitmapCache[logoUrl] = bmp
                            downloadedAnyNewBitmap = true
                        }
                    }
                }

                for (i in 0 until 3) {
                    val movie = top2026Movies.getOrNull(i)
                    var posterUrl = movie?.posterUrl?.ifBlank { movie.backdropUrl }.orEmpty()
                    if (!posterUrl.startsWith("http", true) && movie != null) {
                        val enriched = TmdbRepository.enrichMediaContent(movie)
                        posterUrl = enriched.posterUrl.ifBlank { enriched.backdropUrl }
                        MediaContentRepository.updateSingleEnrichedMedia(enriched)
                    }
                    if (posterUrl.startsWith("http", true) && !bitmapCache.containsKey(posterUrl)) {
                        val bmp = downloadAndScaleWidgetBitmap(
                            urlStr = posterUrl,
                            targetW = 128,
                            targetH = 96,
                            crop = true,
                            fallbackTitle = movie?.title ?: "2026"
                        )
                        if (bmp != null) {
                            bitmapCache[posterUrl] = bmp
                            downloadedAnyNewBitmap = true
                        }
                    }
                }

                if (downloadedAnyNewBitmap) {
                    try {
                        val updatedViews = buildRemoteViews(context)
                        appWidgetManager.updateAppWidget(appWidgetId, updatedViews)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        private fun buildRemoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.neli_home_widget)

            // Brand header opens MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                1000,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_brand_title, openAppPendingIntent)

            // Refresh button updates widget content
            val refreshIntent = Intent(context, NeliHomeWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            // 1. TOP ROW: 3 Live Channels (Azam Sports 1 HD, Azam Two, WWE) with Channel Logos
            val topChannels = TOP_WIDGET_CHANNEL_IDS.mapNotNull { id ->
                ChannelRepository.getChannelById(id)
            }

            val channelCardIds = listOf(
                R.id.widget_channel_card_1,
                R.id.widget_channel_card_2,
                R.id.widget_channel_card_3
            )
            val channelImgIds = listOf(
                R.id.widget_channel_img_1,
                R.id.widget_channel_img_2,
                R.id.widget_channel_img_3
            )
            val channelTitleIds = listOf(
                R.id.widget_channel_title_1,
                R.id.widget_channel_title_2,
                R.id.widget_channel_title_3
            )
            val channelSubIds = listOf(
                R.id.widget_channel_sub_1,
                R.id.widget_channel_sub_2,
                R.id.widget_channel_sub_3
            )

            for (i in 0 until 3) {
                val ch = topChannels.getOrNull(i)
                if (ch != null) {
                    views.setTextViewText(channelTitleIds[i], ch.name)
                    val subtitle = when (i) {
                        0 -> "Mpira Live ▶"
                        1 -> "Tamthilia ▶"
                        else -> "Live 24/7 ▶"
                    }
                    views.setTextViewText(channelSubIds[i], subtitle)

                    val logoUrl = ch.thumbnailUrl.takeIf { it.startsWith("http", true) }
                        ?: NeliNotificationScheduler.getLogoSpecForChannel(ch.id).logoUrl
                    val cachedLogo = bitmapCache[logoUrl] ?: renderFallbackWidgetThumb(
                        title = ch.name,
                        badge = "LIVE TV",
                        targetW = 128,
                        targetH = 84,
                        primaryColor = 0xFF17103A.toInt(),
                        accentColor = 0xFFF41B54.toInt()
                    )
                    views.setImageViewBitmap(channelImgIds[i], cachedLogo)

                    val channelIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_LAUNCH_CHANNEL_ID, ch.id)
                    }
                    val channelPi = PendingIntent.getActivity(
                        context,
                        2000 + i,
                        channelIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(channelCardIds[i], channelPi)
                }
            }

            // 2. BOTTOM ROW: 3 New 2026 Non-Adult Movies with TMDB Poster Images
            val top2026Movies = MediaContentRepository.getLatest2026NonAdultMovies(limit = 3)
            val movieCardIds = listOf(
                R.id.widget_movie_card_1,
                R.id.widget_movie_card_2,
                R.id.widget_movie_card_3
            )
            val movieImgIds = listOf(
                R.id.widget_movie_img_1,
                R.id.widget_movie_img_2,
                R.id.widget_movie_img_3
            )
            val movieBadgeIds = listOf(
                R.id.widget_movie_badge_1,
                R.id.widget_movie_badge_2,
                R.id.widget_movie_badge_3
            )
            val movieTitleIds = listOf(
                R.id.widget_movie_title_1,
                R.id.widget_movie_title_2,
                R.id.widget_movie_title_3
            )
            val movieGenreIds = listOf(
                R.id.widget_movie_genre_1,
                R.id.widget_movie_genre_2,
                R.id.widget_movie_genre_3
            )

            for (i in 0 until 3) {
                val movie = top2026Movies.getOrNull(i)
                if (movie != null) {
                    views.setTextViewText(movieBadgeIds[i], "2026 • NEW")
                    views.setTextViewText(movieTitleIds[i], movie.title)
                    views.setTextViewText(
                        movieGenreIds[i],
                        "${movie.genre} • ${if (movie.narrated) "Swahili" else "HD"}"
                    )

                    val posterUrl = movie.posterUrl.ifBlank { movie.backdropUrl }
                    val cachedPoster = bitmapCache[posterUrl] ?: renderFallbackWidgetThumb(
                        title = movie.title,
                        badge = "2026",
                        targetW = 128,
                        targetH = 96,
                        primaryColor = 0xFF0F172A.toInt(),
                        accentColor = 0xFF00D2FF.toInt()
                    )
                    views.setImageViewBitmap(movieImgIds[i], cachedPoster)

                    val movieIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra(MainActivity.EXTRA_LAUNCH_MEDIA_ID, movie.id)
                    }
                    val moviePi = PendingIntent.getActivity(
                        context,
                        3000 + i,
                        movieIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(movieCardIds[i], moviePi)
                }
            }

            return views
        }

        private fun downloadAndScaleWidgetBitmap(
            urlStr: String,
            targetW: Int,
            targetH: Int,
            crop: Boolean,
            fallbackTitle: String
        ): Bitmap? {
            return try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Nelitv-Widget/1.0.0")
                    doInput = true
                }
                val raw = if (conn.responseCode in 200..299) {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                } else null
                if (raw == null) return null

                val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(255, 16, 20, 32)
                }
                canvas.drawRoundRect(RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), 14f, 14f, bgPaint)

                val scale = if (crop) {
                    maxOf(targetW.toFloat() / raw.width.toFloat(), targetH.toFloat() / raw.height.toFloat())
                } else {
                    minOf((targetW - 10).toFloat() / raw.width.toFloat(), (targetH - 10).toFloat() / raw.height.toFloat())
                }
                val drawW = (raw.width * scale).toInt().coerceAtMost(targetW)
                val drawH = (raw.height * scale).toInt().coerceAtMost(targetH)
                val left = (targetW - drawW) / 2
                val top = (targetH - drawH) / 2
                val dst = Rect(left, top, left + drawW, top + drawH)
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(raw, null, dst, bmpPaint)
                out
            } catch (_: Exception) {
                null
            }
        }

        fun renderFallbackWidgetThumb(
            title: String,
            badge: String,
            targetW: Int,
            targetH: Int,
            primaryColor: Int,
            accentColor: Int
        ): Bitmap {
            val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, targetW.toFloat(), targetH.toFloat(),
                    primaryColor, accentColor, Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), 14f, 14f, bgPaint)

            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val label = title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().take(10)
            canvas.drawText(label.ifBlank { badge }, targetW / 2f, targetH / 2f + 6f, textPaint)
            return out
        }
    }
}
