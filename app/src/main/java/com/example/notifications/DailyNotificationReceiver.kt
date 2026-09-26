package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives scheduled daily notification alarms in East Africa Time (EAT) as well as device boot events.
 */
class DailyNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                NeliNotificationScheduler.scheduleAllDailyNotifications(context)
                com.example.widget.NeliHomeWidgetProvider.ensureWidgetAutomaticallyPinnedAndUpdated(context)
            }

            NeliNotificationScheduler.ACTION_DAILY_NOTIFICATION -> {
                val slotId = intent?.getIntExtra(
                    NeliNotificationScheduler.EXTRA_SLOT_ID,
                    NeliNotificationScheduler.SLOT_MORNING_MOVIE_SERIES
                ) ?: NeliNotificationScheduler.SLOT_MORNING_MOVIE_SERIES
                NeliNotificationScheduler.dispatchNotificationForSlot(context, slotId)
            }
        }
    }
}
