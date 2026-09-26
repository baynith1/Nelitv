package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service that keeps single and multi-downloads running at full speed in the background
 * even when the user exits Neli TV or switches to other apps.
 */
class NeliDownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_FOREGROUND) {
            stopForegroundSafely()
            stopSelf()
            return START_NOT_STICKY
        }

        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1)?.coerceAtLeast(1) ?: 1
        val summaryTitle = intent?.getStringExtra(EXTRA_SUMMARY_TITLE)
            ?: if (activeCount > 1) "Downloading $activeCount videos in background" else "Background download active"
        val avgProgress = intent?.getIntExtra(EXTRA_AVG_PROGRESS, 1)?.coerceIn(0, 100) ?: 1

        val notification = buildSummaryNotification(
            context = this,
            title = summaryTitle,
            activeCount = activeCount,
            avgProgress = avgProgress
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SUMMARY_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(SUMMARY_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }

        return START_STICKY
    }

    private fun stopForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val SUMMARY_CHANNEL_ID = "neli_multi_download_service_channel"
        private const val SUMMARY_CHANNEL_NAME = "Neli TV Multi-Download Service"
        private const val SUMMARY_NOTIFICATION_ID = 9901

        const val ACTION_UPDATE_FOREGROUND = "com.example.data.ACTION_UPDATE_FOREGROUND"
        const val ACTION_STOP_FOREGROUND = "com.example.data.ACTION_STOP_FOREGROUND"
        const val EXTRA_ACTIVE_COUNT = "extra_active_count"
        const val EXTRA_SUMMARY_TITLE = "extra_summary_title"
        const val EXTRA_AVG_PROGRESS = "extra_avg_progress"

        fun startOrUpdate(
            context: Context,
            activeCount: Int,
            summaryTitle: String,
            avgProgress: Int
        ) {
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, NeliDownloadService::class.java).apply {
                    action = ACTION_UPDATE_FOREGROUND
                    putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                    putExtra(EXTRA_SUMMARY_TITLE, summaryTitle)
                    putExtra(EXTRA_AVG_PROGRESS, avgProgress)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun stopIfIdle(context: Context) {
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, NeliDownloadService::class.java).apply {
                    action = ACTION_STOP_FOREGROUND
                }
                appContext.stopService(intent)
            } catch (_: Exception) {
            }
        }

        private fun buildSummaryNotification(
            context: Context,
            title: String,
            activeCount: Int,
            avgProgress: Int
        ): Notification {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
                val channel = NotificationChannel(
                    SUMMARY_CHANNEL_ID,
                    SUMMARY_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps multi-downloads active in the background when outside the app"
                }
                nm.createNotificationChannel(channel)
            }

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                SUMMARY_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, SUMMARY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(
                    if (activeCount > 1) {
                        "$activeCount parallel downloads • $avgProgress% average • Continues outside app"
                    } else {
                        "$avgProgress% • Background download continues outside app"
                    }
                )
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setProgress(100, avgProgress.coerceIn(0, 100), false)
                .setContentIntent(pi)
                .build()
        }
    }
}
