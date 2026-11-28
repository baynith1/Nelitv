package com.example.ai

import android.app.Activity
import android.app.NotificationManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.example.player.LivePlayerController
import com.example.ui.theme.NeliThemeManager
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Executes real Android device-control, settings, alarm/timer, communication, maps,
 * installed-app launch, visual-intelligence, and screen-wake actions for Neli AI
 * using strictly Google Play-compliant Android APIs and system Intents.
 */
object NeliDeviceControlExecutor {

    private val commonAppPackageHints: Map<String, List<String>> = mapOf(
        "whatsapp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "youtube" to listOf("com.google.android.youtube", "com.google.android.youtube.tv"),
        "spotify" to listOf("com.spotify.music"),
        "instagram" to listOf("com.instagram.android"),
        "tiktok" to listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
        "facebook" to listOf("com.facebook.katana", "com.facebook.lite"),
        "chrome" to listOf("com.android.chrome"),
        "gmail" to listOf("com.google.android.gm"),
        "mail" to listOf("com.google.android.gm"),
        "maps" to listOf("com.google.android.apps.maps"),
        "google maps" to listOf("com.google.android.apps.maps"),
        "telegram" to listOf("org.telegram.messenger"),
        "x" to listOf("com.twitter.android"),
        "twitter" to listOf("com.twitter.android"),
        "netflix" to listOf("com.netflix.mediaclient"),
        "calendar" to listOf("com.google.android.calendar"),
        "calculator" to listOf("com.google.android.calculator", "com.android.calculator2"),
        "clock" to listOf("com.google.android.deskclock", "com.android.deskclock"),
        "messages" to listOf("com.google.android.apps.messaging", "com.android.mms"),
        "sms" to listOf("com.google.android.apps.messaging", "com.android.mms"),
        "settings" to listOf("com.android.settings"),
        "play store" to listOf("com.android.vending"),
        "photos" to listOf("com.google.android.apps.photos"),
        "gallery" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d")
    )

    /**
     * Turns on the physical display immediately (even from sleep or lock screen) using
     * PowerManager wake lock + turn-screen-on Assist Activity fallback.
     */
    @Suppress("DEPRECATION")
    fun wakeScreenNow(context: Context): Boolean {
        return runCatching {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wasOff = pm?.isInteractive == false
            if (pm != null) {
                val flags = PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE
                val wakeLock = runCatching { pm.newWakeLock(flags, "NeliTv:NeliAiWakeScreen") }
                    .getOrElse {
                        pm.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "NeliTv:NeliAiWakeScreenFallback"
                        )
                    }
                wakeLock.acquire(6_500L)
            }
            if (wasOff || context !is Activity) {
                val intent = Intent(context.applicationContext, NeliAiAssistActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(NeliAiAssistActivity.EXTRA_WAKE_SCREEN_ONLY, true)
                }
                runCatching { context.applicationContext.startActivity(intent) }
            }
            true
        }.getOrDefault(false)
    }

    /**
     * Executes any Siri-grade device/system [NeliAiAction]. Returns true if handled.
     */
    fun execute(
        context: Context,
        action: NeliAiAction,
        playerController: LivePlayerController? = null
    ): Boolean {
        return when (action) {
            NeliAiAction.WakeDeviceScreen -> {
                wakeScreenNow(context)
                true
            }

            is NeliAiAction.SetVolumePercent -> {
                setVolumePercent(context, playerController, action.percent)
                true
            }

            is NeliAiAction.SetBrightnessPercent -> {
                setBrightnessPercent(context, action.percent)
                true
            }

            is NeliAiAction.ToggleFlashlight -> {
                toggleFlashlight(context, action.enabled)
                true
            }

            is NeliAiAction.SetDarkMode -> {
                // Dark mode ON => lightMode = false; Light mode ON => lightMode = true
                NeliThemeManager.setThemeMode(context.applicationContext, lightWhiteMode = !action.enabled)
                true
            }

            is NeliAiAction.ToggleDoNotDisturb -> {
                toggleDoNotDisturb(context, action.enabled)
                true
            }

            is NeliAiAction.OpenSystemSetting -> {
                openSystemSetting(context, action.settingType)
                true
            }

            is NeliAiAction.SetAlarm -> {
                setAlarm(context, action.hour24, action.minute, action.label)
                true
            }

            is NeliAiAction.ShowOrDismissAlarms -> {
                showOrDismissAlarms(context, action.deleteAll)
                true
            }

            is NeliAiAction.SetTimer -> {
                setTimer(context, action.durationSeconds, action.label)
                true
            }

            is NeliAiAction.PlaceCall -> {
                placeCall(context, action.contactOrNumber, action.onSpeaker, action.videoCall)
                true
            }

            is NeliAiAction.SendMessage -> {
                sendMessage(context, action.recipient, action.body)
                true
            }

            is NeliAiAction.OpenMessagingOrMailApp -> {
                openMessagingOrMailApp(context, action.targetType)
                true
            }

            is NeliAiAction.OpenMapsDirections -> {
                openMapsDirections(context, action.destination, action.mode)
                true
            }

            is NeliAiAction.FindPlacesNearby -> {
                findPlacesNearby(context, action.query)
                true
            }

            is NeliAiAction.LaunchInstalledApp -> {
                launchInstalledApp(context, action.appQuery)
                true
            }

            is NeliAiAction.LaunchVisualIntelligence -> {
                launchVisualIntelligence(context)
                true
            }

            NeliAiAction.FindMyPhone -> {
                runFindMyPhoneBeacon(context)
                true
            }

            is NeliAiAction.PlayMusicOrMedia -> {
                playMusicOrMedia(context, action.query)
                true
            }

            is NeliAiAction.WebSearch -> {
                openWebSearch(context, action.query)
                true
            }

            is NeliAiAction.CompositeActions -> {
                action.actions.forEach { child ->
                    execute(context, child, playerController)
                }
                true
            }

            else -> false
        }
    }

    fun getBatterySummary(context: Context): String {
        return runCatching {
            val bm = context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                bm?.isCharging == true
            } else {
                false
            }
            when {
                pct != null && charging -> "Your battery is at $pct% and charging."
                pct != null -> "Your battery is at $pct%."
                else -> "I checked your device battery status."
            }
        }.getOrDefault("I checked your device battery status.")
    }

    private fun setVolumePercent(
        context: Context,
        playerController: LivePlayerController?,
        percent: Int
    ) {
        val clamped = percent.coerceIn(0, 100)
        runCatching {
            val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (am != null) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val target = ((max * clamped) / 100f).roundToInt().coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            }
            playerController?.setVolume((clamped / 100f).coerceIn(0f, 1f))
        }
    }

    private fun setBrightnessPercent(context: Context, percent: Int) {
        val clamped = percent.coerceIn(5, 100)
        val fraction = clamped / 100f
        var applied = false
        val activity = context as? Activity
        if (activity != null) {
            runCatching {
                val attrs = activity.window.attributes
                attrs.screenBrightness = fraction
                activity.window.attributes = attrs
                applied = true
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(context)) {
            runCatching {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                )
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    (fraction * 255f).roundToInt().coerceIn(10, 255)
                )
                applied = true
            }
        }
        if (!applied) {
            openSystemSetting(context, NeliSystemSettingType.DISPLAY)
        }
    }

    private fun toggleFlashlight(context: Context, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return runCatching {
            val cm = context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val backFlashId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(backFlashId, enabled)
            true
        }.getOrDefault(false)
    }

    private fun toggleDoNotDisturb(context: Context, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.isNotificationPolicyAccessGranted) {
                runCatching {
                    nm.setInterruptionFilter(
                        if (enabled) {
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        } else {
                            NotificationManager.INTERRUPTION_FILTER_ALL
                        }
                    )
                    return
                }
            }
            launchIntentSafely(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } else {
            launchIntentSafely(context, Intent(Settings.ACTION_SOUND_SETTINGS))
        }
    }

    private fun openSystemSetting(context: Context, settingType: NeliSystemSettingType) {
        val primaryIntent = when (settingType) {
            NeliSystemSettingType.WIFI -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            }
            NeliSystemSettingType.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            NeliSystemSettingType.LOW_POWER -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            NeliSystemSettingType.DO_NOT_DISTURB -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                } else {
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                }
            }
            NeliSystemSettingType.AIRPLANE_MODE -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            NeliSystemSettingType.DISPLAY -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            NeliSystemSettingType.SOUND -> Intent(Settings.ACTION_SOUND_SETTINGS)
            NeliSystemSettingType.SCREENSHOT_HELP,
            NeliSystemSettingType.RESTART_HELP,
            NeliSystemSettingType.HANG_UP_HELP -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            NeliSystemSettingType.GENERAL -> Intent(Settings.ACTION_SETTINGS)
        }
        if (!launchIntentSafely(context, primaryIntent)) {
            val fallback = when (settingType) {
                NeliSystemSettingType.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
                else -> Intent(Settings.ACTION_SETTINGS)
            }
            launchIntentSafely(context, fallback)
        }
    }

    private fun setAlarm(context: Context, hour24: Int, minute: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour24.coerceIn(0, 23))
            putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (!launchIntentSafely(context, intent)) {
            launchIntentSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }
    }

    private fun showOrDismissAlarms(context: Context, deleteAll: Boolean) {
        if (deleteAll && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val dismissIntent = Intent(AlarmClock.ACTION_DISMISS_ALARM)
            if (launchIntentSafely(context, dismissIntent)) return
        }
        launchIntentSafely(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    private fun setTimer(context: Context, durationSeconds: Int, label: String) {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds.coerceAtLeast(1))
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        if (!launchIntentSafely(context, intent)) {
            launchIntentSafely(context, Intent(AlarmClock.ACTION_SHOW_TIMERS))
        }
    }

    private fun placeCall(
        context: Context,
        contactOrNumber: String,
        onSpeaker: Boolean,
        videoCall: Boolean
    ) {
        if (onSpeaker) {
            runCatching {
                val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                @Suppress("DEPRECATION")
                am?.isSpeakerphoneOn = true
            }
        }
        val digits = contactOrNumber.filter { it.isDigit() || it == '+' }
        if (videoCall) {
            // Try Google Meet / WhatsApp video first, then fall back to standard dialer
            val videoPkg = listOf("com.google.android.apps.tachyon", "com.whatsapp")
                .firstOrNull { pkg ->
                    context.packageManager.getLaunchIntentForPackage(pkg) != null
                }
            if (videoPkg != null && digits.length < 6) {
                val launch = context.packageManager.getLaunchIntentForPackage(videoPkg)
                if (launch != null && launchIntentSafely(context, launch)) return
            }
        }
        val dialUri = if (digits.length >= 3) Uri.parse("tel:$digits") else Uri.parse("tel:")
        val dialIntent = Intent(Intent.ACTION_DIAL, dialUri)
        launchIntentSafely(context, dialIntent)
    }

    private fun sendMessage(context: Context, recipient: String, body: String) {
        val digits = recipient.filter { it.isDigit() || it == '+' }
        val smsUri = if (digits.length >= 3) Uri.parse("smsto:$digits") else Uri.parse("smsto:")
        val intent = Intent(Intent.ACTION_SENDTO, smsUri).apply {
            if (body.isNotBlank()) {
                putExtra("sms_body", body)
            }
        }
        if (!launchIntentSafely(context, intent)) {
            openMessagingOrMailApp(context, "sms")
        }
    }

    private fun openMessagingOrMailApp(context: Context, targetType: String) {
        val category = if (targetType.equals("mail", ignoreCase = true)) {
            Intent.CATEGORY_APP_EMAIL
        } else {
            Intent.CATEGORY_APP_MESSAGING
        }
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
        if (!launchIntentSafely(context, intent)) {
            launchInstalledApp(context, if (targetType == "mail") "gmail" else "messages")
        }
    }

    private fun openMapsDirections(context: Context, destination: String, mode: String) {
        val encoded = Uri.encode(destination.trim())
        val navIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$encoded&mode=$mode")).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (launchIntentSafely(context, navIntent)) return
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        if (launchIntentSafely(context, geoIntent)) return
        launchIntentSafely(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$encoded"))
        )
    }

    private fun findPlacesNearby(context: Context, query: String) {
        val encoded = Uri.encode(query.trim())
        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        if (launchIntentSafely(context, geoIntent)) return
        launchIntentSafely(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"))
        )
    }

    private fun launchInstalledApp(context: Context, rawQuery: String): Boolean {
        val q = rawQuery.trim().lowercase(Locale.ROOT)
        if (q.isBlank()) return false
        val pm = context.packageManager

        if (q == "camera" || q == "kamera") {
            if (launchIntentSafely(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) return true
            if (launchIntentSafely(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))) return true
        }

        // 1. Fast check known package hints
        commonAppPackageHints.entries.firstOrNull { (alias, _) ->
            q == alias || q.contains(alias)
        }?.value?.forEach { pkg ->
            pm.getLaunchIntentForPackage(pkg)?.let { launch ->
                if (launchIntentSafely(context, launch)) return true
            }
        }

        // 2. Scan installed launchable apps by label / package name
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val apps = runCatching {
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        }.getOrDefault(emptyList())

        val exactMatch = apps.firstOrNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty().lowercase(Locale.ROOT)
            label == q
        }
        val partialMatch = exactMatch ?: apps.firstOrNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty().lowercase(Locale.ROOT)
            val pkg = info.activityInfo?.packageName.orEmpty().lowercase(Locale.ROOT)
            (label.isNotBlank() && (label.contains(q) || q.contains(label))) || pkg.contains(q)
        }

        val targetPkg = partialMatch?.activityInfo?.packageName ?: return false
        val launchIntent = pm.getLaunchIntentForPackage(targetPkg) ?: return false
        return launchIntentSafely(context, launchIntent)
    }

    private fun launchVisualIntelligence(context: Context) {
        val pm = context.packageManager
        // Prefer Google Lens if installed on the phone
        val lensPkg = pm.getLaunchIntentForPackage("com.google.ar.lens")
        if (lensPkg != null && launchIntentSafely(context, lensPkg)) return

        val googleLensIntent = Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).apply {
            setPackage("com.google.android.googlequicksearchbox")
        }
        if (launchIntentSafely(context, googleLensIntent)) return

        // Fallback to system camera for visual inspection
        if (!launchIntentSafely(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) {
            launchIntentSafely(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE))
        }
    }

    private fun runFindMyPhoneBeacon(context: Context) {
        wakeScreenNow(context)
        thread(name = "NeliFindMyPhoneBeacon") {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 95)
                try {
                    repeat(3) {
                        toggleFlashlight(context, true)
                        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
                        Thread.sleep(380L)
                        toggleFlashlight(context, false)
                        Thread.sleep(220L)
                    }
                } finally {
                    toggleFlashlight(context, false)
                    tone.release()
                }
            }
        }
    }

    private fun playMusicOrMedia(context: Context, query: String) {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        }
        if (!launchIntentSafely(context, intent)) {
            openWebSearch(context, "$query song")
        }
    }

    private fun openWebSearch(context: Context, query: String) {
        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
        }
        if (launchIntentSafely(context, searchIntent)) return
        val browserIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        )
        launchIntentSafely(context, browserIntent)
    }

    private fun launchIntentSafely(context: Context, intent: Intent): Boolean {
        return runCatching {
            val launch = Intent(intent).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(launch)
            true
        }.getOrDefault(false)
    }
}
