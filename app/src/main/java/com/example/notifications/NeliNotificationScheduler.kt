package com.example.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.TmdbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone

/**
 * Daily Notification Scheduler for Neli TV in East Africa Time (`Africa/Dar_es_Salaam`, UTC+3):
 *
 * 1. Saa 1:00 Asubuhi (07:00 EAT): New Movie & Series notification (`SLOT_MORNING_MOVIE_SERIES`)
 *    -> Uses the real TMDB Movie/Series Poster (`posterUrl` / `backdropUrl`) synced in the app.
 * 2. Saa 7:00 Mchana (13:00 EAT): Azam TV & Live Channels (`SLOT_MIDDAY_AZAM_LIVE`)
 *    -> Rotates across the 6 official channel logos (Azam Sports 1 HD, Sinema Zetu, Azam Two, Crown TV, Wasafi TV, ZBC2).
 * 3. Saa 10:00 Jioni (16:00 EAT): Live Football & ZBC2 International Matches (`SLOT_AFTERNOON_AZAM_LIVE`)
 *    -> Sends Azam Sports 1 HD live match alert AND ZBC2 International Football match notification at 16:00 EAT every day.
 * 4. Saa 1:30 Usiku (19:30 EAT): Azam Two & Sinema Zetu (`SLOT_EVENING_AZAM_TWO_SINEMA`)
 *    -> Uses the official Azam Two (Image #3) and Sinema Zetu (Image #2) channel logos for evening Tamthilia & Filamu.
 */
object NeliNotificationScheduler {

    const val DAILY_NOTIFICATION_CHANNEL_ID = "neli_daily_schedule_channel"
    const val DAILY_NOTIFICATION_CHANNEL_NAME = "Neli TV Daily Movies & Azam TV Alerts"

    const val ACTION_DAILY_NOTIFICATION = "com.example.notifications.ACTION_DAILY_NOTIFICATION"
    const val EXTRA_SLOT_ID = "extra_slot_id"

    const val SLOT_MORNING_MOVIE_SERIES = 101           // Saa 1:00 Asubuhi (07:00 EAT) -> TMDB Movie / Series Poster
    const val SLOT_MIDDAY_AZAM_LIVE = 102               // Saa 7:00 Mchana (13:00 EAT) -> Azam TV & Partner Channels
    const val SLOT_AFTERNOON_AZAM_LIVE = 103            // Saa 10:00 Jioni (16:00 EAT) -> Azam Sports 1 + ZBC2 International Matches
    const val SLOT_EVENING_AZAM_TWO_SINEMA = 104        // Saa 1:30 Usiku (19:30 EAT) -> Azam Two & Sinema Zetu
    const val NOTIFICATION_ID_ZBC2_INTERNATIONAL = 105  // Saa 10:00 Jioni (16:00 EAT) -> ZBC2 International Match Alert
    const val NOTIFICATION_ID_ADMIN_SMS = 109           // Admin Broadcast SMS Notification

    val EAT_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Africa/Dar_es_Salaam")
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Represents the 6 Live TV channel logo specifications in the exact order provided by the user:
     * 1. Image 1: Azam Sports 1 HD
     * 2. Image 2: Sinema Zetu
     * 3. Image 3: Azam Two
     * 4. Image 4: Crown TV
     * 5. Image 5: Wasafi TV
     * 6. Image 6: ZBC2 (International Matches at Saa 10:00 Jioni / 16:00 EAT)
     */
    data class ChannelLogoNotificationSpec(
        val imageOrder: Int,
        val channelId: String,
        val channelName: String,
        val badgeText: String,
        val logoUrl: String,
        val primaryColorHex: Int,
        val secondaryColorHex: Int,
        val accentColorHex: Int
    )

    val liveChannelLogoSpecs: List<ChannelLogoNotificationSpec> = listOf(
        // 1st Image: Azam Sports 1 HD
        ChannelLogoNotificationSpec(
            imageOrder = 1,
            channelId = "R17JUvbCEzu2eTbjnE74",
            channelName = "Azam Sports 1 HD",
            badgeText = "AZAM SPORTS 1 HD • MPIRA LIVE",
            logoUrl = "https://i.ibb.co/B29Xvb5P/azam-sport-1-01.png",
            primaryColorHex = 0xFF0A1931.toInt(),
            secondaryColorHex = 0xFF1E3A8A.toInt(),
            accentColorHex = 0xFFE11D48.toInt()
        ),
        // 2nd Image: Sinema Zetu
        ChannelLogoNotificationSpec(
            imageOrder = 2,
            channelId = "f56ca8c1-3d3f-4dd2-8d9d-b0b54b559f6e",
            channelName = "Sinema Zetu",
            badgeText = "SINEMA ZETU • BONGO MOVIES 24/7",
            logoUrl = "https://i.ibb.co/twBGTs4s/1000221073.jpg",
            primaryColorHex = 0xFF062C1E.toInt(),
            secondaryColorHex = 0xFF047857.toInt(),
            accentColorHex = 0xFFF59E0B.toInt()
        ),
        // 3rd Image: Azam Two
        ChannelLogoNotificationSpec(
            imageOrder = 3,
            channelId = "008ffe6e-a30f-4ed1-9ddb-4033dde18576",
            channelName = "Azam Two",
            badgeText = "AZAM TWO • TAMTHILIA & BURUDANI",
            logoUrl = "https://i.ibb.co/Z6sdp2tg/1000221074.jpg",
            primaryColorHex = 0xFF17103A.toInt(),
            secondaryColorHex = 0xFF3730A3.toInt(),
            accentColorHex = 0xFFF97316.toInt()
        ),
        // 4th Image: Crown TV
        ChannelLogoNotificationSpec(
            imageOrder = 4,
            channelId = "bba104f4-f5ac-41c9-aa36-7af71aaa1993",
            channelName = "Crown Tv",
            badgeText = "CROWN TV • MUZIKI & VIPINDI LIVE",
            logoUrl = "https://i.ibb.co/GfWDtdQT/1000221075.png",
            primaryColorHex = 0xFF1F1608.toInt(),
            secondaryColorHex = 0xFF78350F.toInt(),
            accentColorHex = 0xFFFBBF24.toInt()
        ),
        // 5th Image: Wasafi TV
        ChannelLogoNotificationSpec(
            imageOrder = 5,
            channelId = "80e54146-1d9b-4c91-8f71-de0ea4866833",
            channelName = "Wasafi",
            badgeText = "WASAFI TV • BURUDANI & MUZIKI LIVE",
            logoUrl = "https://i.ibb.co/W4PYYhRV/157731247083407-Y3-Jvc-Cwx-MTQ0-LDg5-NCww-LDU4-OA.png",
            primaryColorHex = 0xFF082026.toInt(),
            secondaryColorHex = 0xFF0F766E.toInt(),
            accentColorHex = 0xFF2DD4BF.toInt()
        ),
        // 6th Image: ZBC2 (International Football Matches at Saa 10:00 Jioni / 16:00 EAT)
        ChannelLogoNotificationSpec(
            imageOrder = 6,
            channelId = "b502217f-a9d0-4aef-99e6-8a784adedc65",
            channelName = "ZBC2",
            badgeText = "ZBC2 • INTERNATIONAL MATCHES LIVE",
            logoUrl = "https://i.imgur.com/5HqOXH0.jpeg",
            primaryColorHex = 0xFF071E26.toInt(),
            secondaryColorHex = 0xFF0369A1.toInt(),
            accentColorHex = 0xFF10B981.toInt()
        )
    )

    fun getLogoSpecForChannel(channelIdOrName: String): ChannelLogoNotificationSpec {
        val clean = channelIdOrName.trim()
        return liveChannelLogoSpecs.firstOrNull {
            it.channelId.equals(clean, ignoreCase = true) ||
                it.channelName.equals(clean, ignoreCase = true) ||
                clean.contains(it.channelName, ignoreCase = true)
        } ?: liveChannelLogoSpecs.first()
    }

    data class DailySlotSpec(
        val slotId: Int,
        val hour24Eat: Int,
        val minuteEat: Int,
        val swahiliLabel: String
    )

    val dailySlots: List<DailySlotSpec> = listOf(
        DailySlotSpec(
            slotId = SLOT_MORNING_MOVIE_SERIES,
            hour24Eat = 7,
            minuteEat = 0,
            swahiliLabel = "Saa 1:00 Asubuhi (EAT) • New Movies & Series (TMDB Poster)"
        ),
        DailySlotSpec(
            slotId = SLOT_MIDDAY_AZAM_LIVE,
            hour24Eat = 13,
            minuteEat = 0,
            swahiliLabel = "Saa 7:00 Mchana (EAT) • Azam Sports 1, Crown TV & Wasafi TV"
        ),
        DailySlotSpec(
            slotId = SLOT_AFTERNOON_AZAM_LIVE,
            hour24Eat = 16,
            minuteEat = 0,
            swahiliLabel = "Saa 10:00 Jioni (EAT) • Azam Sports 1 & ZBC2 International Matches"
        ),
        DailySlotSpec(
            slotId = SLOT_EVENING_AZAM_TWO_SINEMA,
            hour24Eat = 19,
            minuteEat = 30,
            swahiliLabel = "Saa 1:30 Usiku (EAT) • Azam Two & Sinema Zetu"
        )
    )

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                DAILY_NOTIFICATION_CHANNEL_ID,
                DAILY_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mandatory automatic daily EAT notifications with TMDB movie posters and Azam TV / ZBC2 / Sinema Zetu / Crown TV / Wasafi TV channel logos"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules all 4 daily EAT notification windows using AlarmManager and automatically starts
     * notifications immediately upon app install / first launch of the day without requiring manual user triggers.
     */
    fun scheduleAllDailyNotifications(context: Context) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        for (slot in dailySlots) {
            scheduleSingleSlot(appContext, slot)
        }
        dispatchAutoInstallOrDailyStartupNotificationIfNeeded(appContext)
    }

    private fun dispatchAutoInstallOrDailyStartupNotificationIfNeeded(context: Context) {
        try {
            val prefs = context.getSharedPreferences("neli_auto_alerts_prefs", Context.MODE_PRIVATE)
            val eatCal = Calendar.getInstance(EAT_TIME_ZONE)
            val todayKey = "${eatCal.get(Calendar.YEAR)}_${eatCal.get(Calendar.DAY_OF_YEAR)}"
            val lastAutoDate = prefs.getString("last_auto_notification_day", "")
            if (lastAutoDate != todayKey) {
                prefs.edit().putString("last_auto_notification_day", todayKey).apply()
                triggerInstantPreviewNotification(context)
            }
        } catch (_: Exception) {
        }
    }

    fun scheduleSingleSlot(
        context: Context,
        slot: DailySlotSpec,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val nextTriggerMs = computeNextTriggerTimeMillis(
                nowMillis = nowMillis,
                hour24Eat = slot.hour24Eat,
                minuteEat = slot.minuteEat
            )

            val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
                action = ACTION_DAILY_NOTIFICATION
                putExtra(EXTRA_SLOT_ID, slot.slotId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                slot.slotId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMs,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    nextTriggerMs,
                    pendingIntent
                )
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Computes the next occurrence in milliseconds for the given 24-hour time in East Africa Time (`Africa/Dar_es_Salaam`).
     */
    fun computeNextTriggerTimeMillis(
        nowMillis: Long,
        hour24Eat: Int,
        minuteEat: Int
    ): Long {
        val calendar = Calendar.getInstance(EAT_TIME_ZONE).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour24Eat)
            set(Calendar.MINUTE, minuteEat)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= nowMillis + 5_000L) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    /**
     * Dispatches the notification for the specified [slotId] and automatically schedules tomorrow's alarm.
     */
    fun dispatchNotificationForSlot(context: Context, slotId: Int) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)

        val dayOfYear = Calendar.getInstance(EAT_TIME_ZONE).get(Calendar.DAY_OF_YEAR)

        when (slotId) {
            SLOT_MORNING_MOVIE_SERIES -> {
                // Saa 1:00 Asubuhi (07:00 EAT): Movie & Series Notification with real TMDB Poster synced in the app
                val nonAdultCatalog = MediaContentRepository.mediaCatalog.value
                    .filter { it.published && !it.isAdultContent && it.posterUrl.isNotBlank() }
                    .ifEmpty {
                        MediaContentRepository.mediaCatalog.value.filter { it.published && !it.isAdultContent }
                    }
                val featuredPick = if (nonAdultCatalog.isNotEmpty()) {
                    nonAdultCatalog[dayOfYear % nonAdultCatalog.size]
                } else null

                val title = if (featuredPick != null) {
                    "🎬 Filamu & Series Mpya: ${featuredPick.title}"
                } else {
                    "🎬 Habari za Asubuhi! Movie & Series Mpya Kwenye Neli TV"
                }
                val body = if (featuredPick != null) {
                    "Tazama au pakua ${featuredPick.title} (${featuredPick.genre} • ${featuredPick.releaseYear}) sasa kwenye Neli TV!"
                } else {
                    "Fungua Neli TV kutazama na kupakua Movie na Series mpya zilizotafsiriwa kwa Kiswahili!"
                }
                val tmdbPosterUrl = featuredPick?.posterUrl?.ifBlank { featuredPick.backdropUrl }.orEmpty()
                val tmdbBackdropUrl = featuredPick?.backdropUrl?.ifBlank { tmdbPosterUrl }.orEmpty()

                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_MORNING_MOVIE_SERIES,
                    title = title,
                    message = body,
                    badgeLabel = if (featuredPick?.isSeries == true) "TMDB SERIES • SWAHILI" else "TMDB MOVIE • SWAHILI",
                    subtitleLabel = featuredPick?.let { "${it.genre} • ★ ${it.rating} • ${it.releaseYear}" } ?: "Neli TV Cinema",
                    primaryImageUrl = tmdbPosterUrl,
                    backdropImageUrl = tmdbBackdropUrl,
                    primaryColorHex = 0xFF0D111A.toInt(),
                    secondaryColorHex = 0xFF1E1B4B.toInt(),
                    accentColorHex = 0xFFF41B54.toInt(),
                    isChannelLogo = false,
                    launchMediaId = featuredPick?.id,
                    launchChannelId = null
                )
            }

            SLOT_MIDDAY_AZAM_LIVE -> {
                // Saa 7:00 Mchana (13:00 EAT): Rotates across the 6 channel logos (Azam Sports 1, Crown TV, Wasafi TV, Azam Two, Sinema Zetu, ZBC2)
                val middaySpecs = listOf(
                    liveChannelLogoSpecs[0], // 1. Azam Sports 1 HD
                    liveChannelLogoSpecs[3], // 4. Crown TV
                    liveChannelLogoSpecs[4], // 5. Wasafi TV
                    liveChannelLogoSpecs[2], // 3. Azam Two
                    liveChannelLogoSpecs[1], // 2. Sinema Zetu
                    liveChannelLogoSpecs[5]  // 6. ZBC2
                )
                val spec = middaySpecs[dayOfYear % middaySpecs.size]
                val channelObj = ChannelRepository.getChannelById(spec.channelId)
                val logoUrl = channelObj?.thumbnailUrl?.ifBlank { spec.logoUrl } ?: spec.logoUrl

                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_MIDDAY_AZAM_LIVE,
                    title = "📺 Saa 7 Mchana Live: ${spec.channelName}",
                    message = "Tazama matangazo mbashara ya ${spec.channelName} (Mpira, Muziki na Burudani HD) kupitia Neli TV hata kwa bando dogo!",
                    badgeLabel = spec.badgeText,
                    subtitleLabel = "LIVE STREAM • LOW BANDO SAVER",
                    primaryImageUrl = logoUrl,
                    backdropImageUrl = logoUrl,
                    primaryColorHex = spec.primaryColorHex,
                    secondaryColorHex = spec.secondaryColorHex,
                    accentColorHex = spec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = spec.channelId
                )
            }

            SLOT_AFTERNOON_AZAM_LIVE -> {
                // Saa 10:00 Jioni (16:00 EAT):
                // 1) Azam Sports 1 HD (Image #1) Live Football notification
                val azamSport1Spec = liveChannelLogoSpecs[0] // Image 1: Azam Sports 1 HD
                val azamSport1Logo = ChannelRepository.getChannelById(azamSport1Spec.channelId)
                    ?.thumbnailUrl?.ifBlank { azamSport1Spec.logoUrl } ?: azamSport1Spec.logoUrl

                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_AFTERNOON_AZAM_LIVE,
                    title = "⚽ Saa 10 Jioni Live: ${azamSport1Spec.channelName}",
                    message = "Mechi ya leo imeanza! Tazama soka mbashara kwenye ${azamSport1Spec.channelName} kupitia Neli TV bila kukwama.",
                    badgeLabel = azamSport1Spec.badgeText,
                    subtitleLabel = "SAA 10:00 JIONI • SOKA MBASHARA",
                    primaryImageUrl = azamSport1Logo,
                    backdropImageUrl = azamSport1Logo,
                    primaryColorHex = azamSport1Spec.primaryColorHex,
                    secondaryColorHex = azamSport1Spec.secondaryColorHex,
                    accentColorHex = azamSport1Spec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = azamSport1Spec.channelId
                )

                // 2) ZBC2 (Image #6) Daily Saa 10:00 Jioni International Matches Notification
                val zbc2Spec = liveChannelLogoSpecs[5] // Image 6: ZBC2
                val zbc2Logo = ChannelRepository.getChannelById(zbc2Spec.channelId)
                    ?.thumbnailUrl?.ifBlank { zbc2Spec.logoUrl } ?: zbc2Spec.logoUrl

                postRichMediaNotification(
                    context = appContext,
                    notificationId = NOTIFICATION_ID_ZBC2_INTERNATIONAL,
                    title = "🌍 Saa 10 Jioni • ZBC2: Mechi za International Live!",
                    message = "ZBC2 wanaonesha mechi za kimataifa (International Matches) sasa hivi saa 10 jioni! Bonyeza kutazama live kwenye Neli TV.",
                    badgeLabel = zbc2Spec.badgeText,
                    subtitleLabel = "SAA 10:00 JIONI • INTERNATIONAL FOOTBALL",
                    primaryImageUrl = zbc2Logo,
                    backdropImageUrl = zbc2Logo,
                    primaryColorHex = zbc2Spec.primaryColorHex,
                    secondaryColorHex = zbc2Spec.secondaryColorHex,
                    accentColorHex = zbc2Spec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = zbc2Spec.channelId
                )
            }

            SLOT_EVENING_AZAM_TWO_SINEMA -> {
                // Saa 1:30 Usiku (19:30 EAT): Azam Two (Image #3) & Sinema Zetu (Image #2)
                val sinemaZetuSpec = liveChannelLogoSpecs[1] // Image 2: Sinema Zetu
                val azamTwoSpec = liveChannelLogoSpecs[2]    // Image 3: Azam Two
                val activeSpec = if (dayOfYear % 2 == 0) azamTwoSpec else sinemaZetuSpec
                val channelObj = ChannelRepository.getChannelById(activeSpec.channelId)
                val logoUrl = channelObj?.thumbnailUrl?.ifBlank { activeSpec.logoUrl } ?: activeSpec.logoUrl

                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_EVENING_AZAM_TWO_SINEMA,
                    title = "🍿 Saa 1:30 Usiku: ${activeSpec.channelName} (Azam Two & Sinema Zetu)",
                    message = "Muda wa Tamthilia kali za Kiswahili na Bongo Movies kwenye Azam Two na Sinema Zetu. Bonyeza kutazama live sasa!",
                    badgeLabel = activeSpec.badgeText,
                    subtitleLabel = "SAA 1:30 USIKU • TAMTHILIA & FILAMU",
                    primaryImageUrl = logoUrl,
                    backdropImageUrl = logoUrl,
                    primaryColorHex = activeSpec.primaryColorHex,
                    secondaryColorHex = activeSpec.secondaryColorHex,
                    accentColorHex = activeSpec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = activeSpec.channelId
                )
            }
        }

        // Reschedule the next day's alarm for this slot
        dailySlots.find { it.slotId == slotId }?.let { spec ->
            scheduleSingleSlot(appContext, spec)
        }
    }

    /**
     * Triggers an immediate notification preview and ensures all daily EAT alarms are scheduled.
     */
    fun triggerInstantPreviewNotification(context: Context) {
        val appContext = context.applicationContext
        scheduleAllDailyNotifications(appContext)
        val activeAdminSms = com.example.data.NeliAdminManager.activeAdminSms.value
        if (activeAdminSms != null && activeAdminSms.message.isNotBlank()) {
            sendAdminBroadcastNotification(appContext, activeAdminSms.message)
        }
        dispatchNotificationForSlot(appContext, SLOT_AFTERNOON_AZAM_LIVE)
    }

    /**
     * Sends an immediate Android system notification with the Admin SMS message written in the Mini Admin Panel.
     */
    fun sendAdminBroadcastNotification(context: Context, smsMessage: String) {
        val cleanMsg = smsMessage.trim()
        if (cleanMsg.isBlank()) return
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val spec = liveChannelLogoSpecs.first()
        postRichMediaNotification(
            context = appContext,
            notificationId = NOTIFICATION_ID_ADMIN_SMS,
            title = "📢 Taarifa Muhimu • Nelitv",
            message = cleanMsg,
            badgeLabel = "NELITV ADMIN • TAARIFA MPYA",
            subtitleLabel = cleanMsg.take(48),
            primaryImageUrl = spec.logoUrl,
            backdropImageUrl = spec.logoUrl,
            primaryColorHex = 0xFF14052B.toInt(),
            secondaryColorHex = 0xFF3B0764.toInt(),
            accentColorHex = 0xFFFF2E7E.toInt(),
            isChannelLogo = true,
            launchMediaId = null,
            launchChannelId = null
        )
    }

    private fun postRichMediaNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        badgeLabel: String,
        subtitleLabel: String,
        primaryImageUrl: String,
        backdropImageUrl: String,
        primaryColorHex: Int,
        secondaryColorHex: Int,
        accentColorHex: Int,
        isChannelLogo: Boolean,
        launchMediaId: String?,
        launchChannelId: String?
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!launchChannelId.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_LAUNCH_CHANNEL_ID, launchChannelId)
                }
                if (!launchMediaId.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_LAUNCH_MEDIA_ID, launchMediaId)
                }
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 1. Immediate synchronous branded notification with generated BigPicture banner & LargeIcon
            val initialLargeIcon = renderNotificationIconBitmap(
                sourceBitmap = null,
                title = title,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                accentColorHex = accentColorHex,
                isChannelLogo = isChannelLogo
            )
            val initialBanner = renderNotificationBannerBitmap(
                logoOrPosterBitmap = null,
                backdropBitmap = null,
                title = title,
                badgeLabel = badgeLabel,
                subtitleLabel = subtitleLabel,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                accentColorHex = accentColorHex,
                isChannelLogo = isChannelLogo
            )

            val initialNotification = NotificationCompat.Builder(context, DAILY_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(message)
                .setSubText(badgeLabel)
                .setLargeIcon(initialLargeIcon)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(initialBanner)
                        .bigLargeIcon(initialLargeIcon)
                        .setBigContentTitle(title)
                        .setSummaryText(message)
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(notificationId, initialNotification)

            // 2. Asynchronously fetch the real TMDB poster or Channel Logo image from URL (or TMDB API) and upgrade the notification
            if (primaryImageUrl.startsWith("http", ignoreCase = true) || !launchMediaId.isNullOrBlank()) {
                notificationScope.launch {
                    try {
                        var resolvedPrimaryUrl = primaryImageUrl
                        var resolvedBackdropUrl = backdropImageUrl
                        if (!resolvedPrimaryUrl.startsWith("http", ignoreCase = true) && !launchMediaId.isNullOrBlank()) {
                            MediaContentRepository.getMediaById(launchMediaId)?.let { media ->
                                val enriched = TmdbRepository.enrichMediaContent(media)
                                resolvedPrimaryUrl = enriched.posterUrl.ifBlank { enriched.backdropUrl }
                                resolvedBackdropUrl = enriched.backdropUrl.ifBlank { resolvedPrimaryUrl }
                            }
                        }
                        if (!resolvedPrimaryUrl.startsWith("http", ignoreCase = true)) return@launch

                        val downloadedPrimary = downloadBitmap(resolvedPrimaryUrl) ?: return@launch
                        val downloadedBackdrop = if (resolvedBackdropUrl.isNotBlank() && resolvedBackdropUrl != resolvedPrimaryUrl) {
                            downloadBitmap(resolvedBackdropUrl) ?: downloadedPrimary
                        } else {
                            downloadedPrimary
                        }

                        val richLargeIcon = renderNotificationIconBitmap(
                            sourceBitmap = downloadedPrimary,
                            title = title,
                            primaryColorHex = primaryColorHex,
                            secondaryColorHex = secondaryColorHex,
                            accentColorHex = accentColorHex,
                            isChannelLogo = isChannelLogo
                        )
                        val richBanner = renderNotificationBannerBitmap(
                            logoOrPosterBitmap = downloadedPrimary,
                            backdropBitmap = downloadedBackdrop,
                            title = title,
                            badgeLabel = badgeLabel,
                            subtitleLabel = subtitleLabel,
                            primaryColorHex = primaryColorHex,
                            secondaryColorHex = secondaryColorHex,
                            accentColorHex = accentColorHex,
                            isChannelLogo = isChannelLogo
                        )

                        val updatedNotification = NotificationCompat.Builder(context, DAILY_NOTIFICATION_CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_media_play)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setSubText(badgeLabel)
                            .setLargeIcon(richLargeIcon)
                            .setStyle(
                                NotificationCompat.BigPictureStyle()
                                    .bigPicture(richBanner)
                                    .bigLargeIcon(richLargeIcon)
                                    .setBigContentTitle(title)
                                    .setSummaryText(message)
                            )
                            .setOnlyAlertOnce(true)
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .build()

                        nm.notify(notificationId, updatedNotification)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun downloadBitmap(urlStr: String): Bitmap? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 7000
                requestMethod = "GET"
                doInput = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a crisp 192x192 square LargeIcon featuring either the downloaded Channel Logo / TMDB Movie Poster
     * or a stylized monogram badge.
     */
    fun renderNotificationIconBitmap(
        sourceBitmap: Bitmap?,
        title: String,
        primaryColorHex: Int,
        secondaryColorHex: Int,
        accentColorHex: Int,
        isChannelLogo: Boolean
    ): Bitmap {
        val size = 192
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                primaryColorHex, secondaryColorHex, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 32f, 32f, bgPaint)

        if (sourceBitmap != null) {
            val pad = if (isChannelLogo) 18 else 0
            val dst = computeCenterFitRect(
                srcW = sourceBitmap.width,
                srcH = sourceBitmap.height,
                boxLeft = pad,
                boxTop = pad,
                boxRight = size - pad,
                boxBottom = size - pad,
                crop = !isChannelLogo
            )
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(sourceBitmap, null, dst, bmpPaint)
        } else {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val cleanInitials = title.replace(Regex("[^A-Za-z0-9 ]"), "").trim().take(4).uppercase()
            canvas.drawText(cleanInitials.ifEmpty { "NELI" }, size / 2f, size / 2f + 16f, textPaint)
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = accentColorHex
        }
        canvas.drawRoundRect(RectF(2f, 2f, size - 2f, size - 2f), 32f, 32f, borderPaint)
        return out
    }

    /**
     * Renders a 960x480 widescreen BigPicture notification banner containing:
     * - For Movies/Series: The TMDB Backdrop + TMDB Poster card on the left + Title/Genre/Rating on the right.
     * - For Live TV (Azam Sports 1, Sinema Zetu, Azam Two, Crown TV, Wasafi TV, ZBC2):
     *   The official Channel Logo prominently displayed on a broadcast stage with LIVE badge and channel details.
     */
    fun renderNotificationBannerBitmap(
        logoOrPosterBitmap: Bitmap?,
        backdropBitmap: Bitmap?,
        title: String,
        badgeLabel: String,
        subtitleLabel: String,
        primaryColorHex: Int,
        secondaryColorHex: Int,
        accentColorHex: Int,
        isChannelLogo: Boolean
    ): Bitmap {
        val width = 960
        val height = 480
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                primaryColorHex, secondaryColorHex, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // If Movie/Series has a TMDB backdrop, draw it across the background with a dark cinema scrim
        if (!isChannelLogo && backdropBitmap != null) {
            val bgRect = computeCenterFitRect(
                srcW = backdropBitmap.width,
                srcH = backdropBitmap.height,
                boxLeft = 0,
                boxTop = 0,
                boxRight = width,
                boxBottom = height,
                crop = true
            )
            canvas.drawBitmap(backdropBitmap, null, bgRect, bmpPaint)
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), 0f,
                    Color.argb(235, 9, 10, 15),
                    Color.argb(175, 9, 10, 15),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
        }

        // Left visual stage for Channel Logo or TMDB Movie Poster
        val cardLeft = 40f
        val cardTop = 44f
        val cardRight = if (isChannelLogo) 360f else 310f
        val cardBottom = height - 44f
        val stagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 12, 16, 28)
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 28f, 28f, stagePaint)

        if (logoOrPosterBitmap != null) {
            val innerPad = if (isChannelLogo) 24 else 8
            val dstRect = computeCenterFitRect(
                srcW = logoOrPosterBitmap.width,
                srcH = logoOrPosterBitmap.height,
                boxLeft = (cardLeft + innerPad).toInt(),
                boxTop = (cardTop + innerPad).toInt(),
                boxRight = (cardRight - innerPad).toInt(),
                boxBottom = (cardBottom - innerPad).toInt(),
                crop = !isChannelLogo
            )
            canvas.drawBitmap(logoOrPosterBitmap, null, dstRect, bmpPaint)
        }

        val stageBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = accentColorHex
        }
        canvas.drawRoundRect(RectF(cardLeft, cardTop, cardRight, cardBottom), 28f, 28f, stageBorder)

        // Right side text details
        val textStartX = cardRight + 36f
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColorHex
        }
        canvas.drawRoundRect(RectF(textStartX, 76f, (textStartX + 440f).coerceAtMost(width - 40f), 124f), 14f, 14f, pillPaint)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(badgeLabel.take(34), textStartX + 18f, 108f, badgeTextPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cleanTitle = title.replace(Regex("^[^A-Za-z0-9]+"), "").trim()
        canvas.drawText(cleanTitle.take(26), textStartX, 196f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 210, 255)
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(subtitleLabel.take(36), textStartX, 252f, subPaint)

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(203, 213, 225)
            textSize = 23f
        }
        canvas.drawText("Neli TV • Tap to Watch Live in HD / Low Bando", textStartX, 318f, footerPaint)

        return out
    }

    private fun computeCenterFitRect(
        srcW: Int,
        srcH: Int,
        boxLeft: Int,
        boxTop: Int,
        boxRight: Int,
        boxBottom: Int,
        crop: Boolean
    ): Rect {
        val boxW = (boxRight - boxLeft).coerceAtLeast(1)
        val boxH = (boxBottom - boxTop).coerceAtLeast(1)
        if (srcW <= 0 || srcH <= 0) return Rect(boxLeft, boxTop, boxRight, boxBottom)

        val scale = if (crop) {
            maxOf(boxW.toFloat() / srcW.toFloat(), boxH.toFloat() / srcH.toFloat())
        } else {
            minOf(boxW.toFloat() / srcW.toFloat(), boxH.toFloat() / srcH.toFloat())
        }
        val drawW = (srcW * scale).toInt().coerceAtMost(boxW)
        val drawH = (srcH * scale).toInt().coerceAtMost(boxH)
        val left = boxLeft + (boxW - drawW) / 2
        val top = boxTop + (boxH - drawH) / 2
        return Rect(left, top, left + drawW, top + drawH)
    }
}
