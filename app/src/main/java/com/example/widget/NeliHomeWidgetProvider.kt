package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository

/**
 * Neli TV Home Screen App Widget:
 * - Top Row (Juu): 3 Curated Live TV Channels — Azam Sports 1 HD, Azam Two, and WWE
 * - Bottom Row (Chini): 3 New 2026 Movies (strictly non-adult, any genre)
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Automatically restore / re-pin the widget if the user removes it while the app is still installed
        updateAllWidgets(context)
        requestPinWidget(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Automatically re-request widget pin so the widget remains on the home screen until the app is uninstalled
        requestPinWidget(context)
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

        private val TOP_WIDGET_CHANNEL_IDS = listOf(
            "R17JUvbCEzu2eTbjnE74",                 // 1. Azam Sports 1 HD
            "008ffe6e-a30f-4ed1-9ddb-4033dde18576", // 2. Azam Two
            "0d7274cb-6a3e-464d-8b8d-bc3c6d433cc2"  // 3. WWE
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
         * Ensures the NeliPlay Home Screen Widget is updated and automatically pinned to the user's home screen
         * upon app installation/launch if not already active.
         */
        fun ensureWidgetAutomaticallyPinnedAndUpdated(context: Context) {
            updateAllWidgets(context)
            if (!autoPinCheckedThisSession) {
                autoPinCheckedThisSession = true
                if (!isWidgetPinned(context)) {
                    val pinned = requestPinWidget(context)
                    if (pinned) {
                        try {
                            context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean(KEY_AUTO_PIN_REQUESTED, true)
                                .apply()
                        } catch (_: Exception) {
                        }
                    }
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

            // 1. TOP ROW: 3 Live Channels (Azam Sports 1 HD, Azam Two, WWE)
            val topChannels = TOP_WIDGET_CHANNEL_IDS.mapNotNull { id ->
                ChannelRepository.getChannelById(id)
            }

            val channelCardIds = listOf(
                R.id.widget_channel_card_1,
                R.id.widget_channel_card_2,
                R.id.widget_channel_card_3
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

            // 2. BOTTOM ROW: 3 New 2026 Non-Adult Movies
            val top2026Movies = MediaContentRepository.getLatest2026NonAdultMovies(limit = 3)
            val movieCardIds = listOf(
                R.id.widget_movie_card_1,
                R.id.widget_movie_card_2,
                R.id.widget_movie_card_3
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

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
