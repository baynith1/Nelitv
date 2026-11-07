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
import com.example.R
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

    const val SLOT_MORNING_NEWS = 101                   // Saa 12:30 Asubuhi (06:30 EAT) -> News (TBC 1, BBC, CNN, UTV)
    const val SLOT_MORNING_MOVIE = 102                  // Saa 4:00 Asubuhi (10:00 EAT) -> Movie #1
    const val SLOT_MIDDAY_MOVIE = 103                   // Saa 7:00 Mchana (13:00 EAT) -> Movie #2
    const val SLOT_AFTERNOON_SPORTS = 104               // Saa 10:00 Jioni (16:00 EAT) -> Azam Sports 1 & 3 HD
    const val SLOT_NIGHT_MOVIE_AZAM_TWO = 105           // Saa 2:00 Usiku (20:00 EAT) -> Movie #3 & Azam Two
    const val SLOT_LATE_NIGHT_SINEMA_ZETU = 106         // Saa 4:00 Usiku (22:00 EAT) -> Sinema Zetu & Azam Two

    // Backward-compatible slot aliases
    const val SLOT_MORNING_MOVIE_SERIES = SLOT_MORNING_MOVIE
    const val SLOT_MIDDAY_AZAM_LIVE = SLOT_MIDDAY_MOVIE
    const val SLOT_AFTERNOON_AZAM_LIVE = SLOT_AFTERNOON_SPORTS
    const val SLOT_EVENING_AZAM_TWO_SINEMA = SLOT_NIGHT_MOVIE_AZAM_TWO
    const val NOTIFICATION_ID_ZBC2_INTERNATIONAL = 107
    const val NOTIFICATION_ID_ADMIN_SMS = 109

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
            slotId = SLOT_MORNING_NEWS,
            hour24Eat = 6,
            minuteEat = 30,
            swahiliLabel = "Saa 12:30 Asubuhi (EAT) • Habari: TBC 1, BBC, CNN, UTV"
        ),
        DailySlotSpec(
            slotId = SLOT_MORNING_MOVIE,
            hour24Eat = 10,
            minuteEat = 0,
            swahiliLabel = "Saa 4:00 Asubuhi (EAT) • Movie #1 ya Siku"
        ),
        DailySlotSpec(
            slotId = SLOT_MIDDAY_MOVIE,
            hour24Eat = 13,
            minuteEat = 0,
            swahiliLabel = "Saa 7:00 Mchana (EAT) • Movie #2 ya Mchana"
        ),
        DailySlotSpec(
            slotId = SLOT_AFTERNOON_SPORTS,
            hour24Eat = 16,
            minuteEat = 0,
            swahiliLabel = "Saa 10:00 Jioni (EAT) • Azam Sports 1 & 3 HD Live"
        ),
        DailySlotSpec(
            slotId = SLOT_NIGHT_MOVIE_AZAM_TWO,
            hour24Eat = 20,
            minuteEat = 0,
            swahiliLabel = "Saa 2:00 Usiku (EAT) • Movie #3 & Azam Two Tamthilia"
        ),
        DailySlotSpec(
            slotId = SLOT_LATE_NIGHT_SINEMA_ZETU,
            hour24Eat = 22,
            minuteEat = 0,
            swahiliLabel = "Saa 4:00 Usiku (EAT) • Sinema Zetu & Azam Two Usiku"
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
            SLOT_MORNING_NEWS -> {
                // 1. Saa 12:30 Asubuhi (06:30 EAT): News on TBC 1, BBC, CNN, UTV & Now Global
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_MORNING_NEWS,
                    title = "📺 Habari za Asubuhi: TBC 1, BBC & UTV Live!",
                    message = "Fuatilia matukio ya kitaifa na kimataifa moja kwa moja kupitia TBC 1, BBC News na UTV hewani sasa!",
                    badgeLabel = "HABARI ZA ASUBUHI • 24/7 LIVE",
                    subtitleLabel = "TBC 1 • BBC • CNN • UTV • NOW GLOBAL",
                    primaryImageUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSso9jJvEyNK7Co1mEWcXrrAgdA09c7aPjbgrIQY5VyEQ&s",
                    backdropImageUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSso9jJvEyNK7Co1mEWcXrrAgdA09c7aPjbgrIQY5VyEQ&s",
                    primaryColorHex = 0xFF0A1B2A.toInt(),
                    secondaryColorHex = 0xFF0284C7.toInt(),
                    accentColorHex = 0xFF38BDF8.toInt(),
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = "tbc_1_live"
                )
            }

            SLOT_MORNING_MOVIE -> {
                // 2. Saa 4:00 Asubuhi (10:00 EAT): Movie #1 ya Siku (Non-repeating)
                val nonAdultCatalog = MediaContentRepository.mediaCatalog.value
                    .filter { it.published && !it.isAdultContent && it.posterUrl.isNotBlank() }
                    .ifEmpty { MediaContentRepository.mediaCatalog.value.filter { it.published && !it.isAdultContent } }
                val movie1 = if (nonAdultCatalog.isNotEmpty()) {
                    nonAdultCatalog[(dayOfYear * 3) % nonAdultCatalog.size]
                } else null

                val title = if (movie1 != null) "🎬 Movie ya Asubuhi: ${movie1.title}" else "🎬 NeliTv: Movie Mpya ya Asubuhi"
                val body = if (movie1 != null) {
                    "Tazama ${movie1.title} (${movie1.releaseYear}) sasa bure ndani ya NeliTv!"
                } else {
                    "Fungua NeliTv kutazama movie mpya za leo zilizotafsiriwa!"
                }
                val poster = movie1?.posterUrl?.ifBlank { movie1.backdropUrl }.orEmpty()
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_MORNING_MOVIE,
                    title = title,
                    message = body,
                    badgeLabel = "MOVIE #1 YA LEO • SWAHILI",
                    subtitleLabel = movie1?.let { "${it.genre} • ★ ${it.rating}" } ?: "Cinema",
                    primaryImageUrl = poster,
                    backdropImageUrl = movie1?.backdropUrl.orEmpty(),
                    primaryColorHex = 0xFF140D26.toInt(),
                    secondaryColorHex = 0xFF7C3AED.toInt(),
                    accentColorHex = 0xFFFF007A.toInt(),
                    isChannelLogo = false,
                    launchMediaId = movie1?.id,
                    launchChannelId = null
                )
            }

            SLOT_MIDDAY_MOVIE -> {
                // 3. Saa 7:00 Mchana (13:00 EAT): Movie #2 ya Mchana (Non-repeating)
                val nonAdultCatalog = MediaContentRepository.mediaCatalog.value
                    .filter { it.published && !it.isAdultContent && it.posterUrl.isNotBlank() }
                    .ifEmpty { MediaContentRepository.mediaCatalog.value.filter { it.published && !it.isAdultContent } }
                val movie2 = if (nonAdultCatalog.isNotEmpty()) {
                    nonAdultCatalog[(dayOfYear * 3 + 1) % nonAdultCatalog.size]
                } else null

                val title = if (movie2 != null) "🍿 Movie ya Mchana: ${movie2.title}" else "🍿 NeliTv: Burudika na Movie Mchana Huu"
                val body = if (movie2 != null) {
                    "Mchana mwema! Pata burudani ya ${movie2.title} (${movie2.genre} • ★ ${movie2.rating})!"
                } else {
                    "Tazama filamu bora za leo kwenye NeliTv bila bando kubwa!"
                }
                val poster = movie2?.posterUrl?.ifBlank { movie2.backdropUrl }.orEmpty()
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_MIDDAY_MOVIE,
                    title = title,
                    message = body,
                    badgeLabel = "MOVIE #2 YA LEO • BURUDANI",
                    subtitleLabel = movie2?.let { "${it.genre} • ${it.releaseYear}" } ?: "NeliTv Movies",
                    primaryImageUrl = poster,
                    backdropImageUrl = movie2?.backdropUrl.orEmpty(),
                    primaryColorHex = 0xFF1F0D1E.toInt(),
                    secondaryColorHex = 0xFFDB2777.toInt(),
                    accentColorHex = 0xFFFF7A00.toInt(),
                    isChannelLogo = false,
                    launchMediaId = movie2?.id,
                    launchChannelId = null
                )
            }

            SLOT_AFTERNOON_SPORTS -> {
                // 4. Saa 10:00 Jioni (16:00 EAT): Azam Sports 1 & 3 HD Live Matches + ZBC2
                val azamSport1Spec = liveChannelLogoSpecs[0]
                val logo = ChannelRepository.getChannelById(azamSport1Spec.channelId)?.thumbnailUrl ?: azamSport1Spec.logoUrl
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_AFTERNOON_SPORTS,
                    title = "⚽ Azam Sports 1 & 3 HD Live Now!",
                    message = "Soka mbashara limeanza saa 10 jioni hii! Tazama mechi za ligi na kombe live bila kukwama.",
                    badgeLabel = "AZAM SPORTS LIVE • SAA 10 JIONI",
                    subtitleLabel = "LIGI KUU & MECHI LIVE",
                    primaryImageUrl = logo,
                    backdropImageUrl = logo,
                    primaryColorHex = azamSport1Spec.primaryColorHex,
                    secondaryColorHex = azamSport1Spec.secondaryColorHex,
                    accentColorHex = azamSport1Spec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = azamSport1Spec.channelId
                )
            }

            SLOT_NIGHT_MOVIE_AZAM_TWO -> {
                // 5. Saa 2:00 Usiku (20:00 EAT): Movie #3 ya Usiku (Non-repeating) + Azam Two Tamthilia
                val nonAdultCatalog = MediaContentRepository.mediaCatalog.value
                    .filter { it.published && !it.isAdultContent && it.posterUrl.isNotBlank() }
                    .ifEmpty { MediaContentRepository.mediaCatalog.value.filter { it.published && !it.isAdultContent } }
                val movie3 = if (nonAdultCatalog.isNotEmpty()) {
                    nonAdultCatalog[(dayOfYear * 3 + 2) % nonAdultCatalog.size]
                } else null

                val title = if (movie3 != null) "📺 Azam Two & Movie ya Usiku: ${movie3.title}" else "📺 Azam Two Tamthilia Live Usiku"
                val body = "Tamthilia za Azam Two ziko hewani, pamoja na filamu ya ${movie3?.title ?: "usiku"}. Bonyeza kutazama live!"
                val azamTwoSpec = liveChannelLogoSpecs[2]
                val logo = ChannelRepository.getChannelById(azamTwoSpec.channelId)?.thumbnailUrl ?: azamTwoSpec.logoUrl
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_NIGHT_MOVIE_AZAM_TWO,
                    title = title,
                    message = body,
                    badgeLabel = "AZAM TWO & MOVIE #3 • USIKU",
                    subtitleLabel = "TAMTHILIA & FILAMU",
                    primaryImageUrl = logo,
                    backdropImageUrl = movie3?.backdropUrl.orEmpty(),
                    primaryColorHex = azamTwoSpec.primaryColorHex,
                    secondaryColorHex = azamTwoSpec.secondaryColorHex,
                    accentColorHex = azamTwoSpec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = movie3?.id,
                    launchChannelId = azamTwoSpec.channelId
                )
            }

            SLOT_LATE_NIGHT_SINEMA_ZETU -> {
                // 6. Saa 4:00 Usiku (22:00 EAT): Sinema Zetu & Azam Two Late Night Cinema
                val sinemaZetuSpec = liveChannelLogoSpecs[1]
                val logo = ChannelRepository.getChannelById(sinemaZetuSpec.channelId)?.thumbnailUrl ?: sinemaZetuSpec.logoUrl
                postRichMediaNotification(
                    context = appContext,
                    notificationId = SLOT_LATE_NIGHT_SINEMA_ZETU,
                    title = "🎭 Sinema Zetu & Azam Two Live!",
                    message = "Filamu kali za Bongo na vipindi bora vya usiku viko hewani sasa kwenye Sinema Zetu!",
                    badgeLabel = "SINEMA ZETU • USIKU 24/7",
                    subtitleLabel = "BONGO MOVIES & BURUDANI",
                    primaryImageUrl = logo,
                    backdropImageUrl = logo,
                    primaryColorHex = sinemaZetuSpec.primaryColorHex,
                    secondaryColorHex = sinemaZetuSpec.secondaryColorHex,
                    accentColorHex = sinemaZetuSpec.accentColorHex,
                    isChannelLogo = true,
                    launchMediaId = null,
                    launchChannelId = sinemaZetuSpec.channelId
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
     * Drawable resource ID of the official Nelitv app logo used for Admin notifications.
     */
    val adminNotificationAppLogoResId: Int = R.drawable.img_nelitv_app_logo_1790873809763

    /**
     * Loads the official Nelitv app logo bitmap (blue/cyan 3D ribbon 'N' on black) for Admin notifications.
     */
    fun loadAppLogoBitmap(context: Context): Bitmap {
        val decoded = try {
            BitmapFactory.decodeResource(context.resources, adminNotificationAppLogoResId)
        } catch (_: Exception) {
            null
        }
        if (decoded != null && decoded.width > 0 && decoded.height > 0) {
            return decoded
        }
        // Fallback high-contrast rendering of the cyan/blue ribbon 'N' logo on #000000 black
        val size = 256
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.BLACK)
        val nPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                48f, 44f, 208f, 212f,
                intArrayOf(0xFF00F0FF.toInt(), 0xFF0088FF.toInt(), 0xFF0044FF.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.STROKE
            strokeWidth = 38f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawLine(68f, 196f, 68f, 60f, nPaint)
        canvas.drawLine(68f, 60f, 188f, 196f, nPaint)
        canvas.drawLine(188f, 196f, 188f, 60f, nPaint)
        return bmp
    }

    /**
     * Sends an immediate Android system notification with the Admin SMS message written in the Mini Admin Panel.
     * Uses the official Nelitv app logo (and never Azam channel logos).
     */
    fun sendAdminBroadcastNotification(context: Context, smsMessage: String) {
        val cleanMsg = smsMessage.trim()
        if (cleanMsg.isBlank()) return
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val appLogoBitmap = loadAppLogoBitmap(appContext)
        postRichMediaNotification(
            context = appContext,
            notificationId = NOTIFICATION_ID_ADMIN_SMS,
            title = "📢 Taarifa Muhimu • Nelitv",
            message = cleanMsg,
            badgeLabel = "NELITV ADMIN • TAARIFA MPYA",
            subtitleLabel = cleanMsg.take(48),
            primaryImageUrl = "",
            backdropImageUrl = "",
            primaryColorHex = 0xFF000000.toInt(),
            secondaryColorHex = 0xFF04142E.toInt(),
            accentColorHex = 0xFF00C6FF.toInt(),
            isChannelLogo = true,
            launchMediaId = null,
            launchChannelId = null,
            localLogoBitmap = appLogoBitmap
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
        launchChannelId: String?,
        localLogoBitmap: Bitmap? = null
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
                sourceBitmap = localLogoBitmap,
                title = title,
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                accentColorHex = accentColorHex,
                isChannelLogo = isChannelLogo
            )
            val initialBanner = renderNotificationBannerBitmap(
                logoOrPosterBitmap = localLogoBitmap,
                backdropBitmap = localLogoBitmap,
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
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_play, "Watch ▶", pendingIntent)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(notificationId, initialNotification)

            // Trigger floating banner across apps if permitted
            NeliFloatingOverlayService.showFloatingBannerIfPermitted(
                context = context,
                channelId = launchChannelId ?: "R17JUvbCEzu2eTbjnE74",
                channelName = "Azam TV",
                title = title.ifBlank { "Azam TV Live Now!" },
                subtitle = message.ifBlank { "Sports, Movies & More" },
                mediaId = launchMediaId
            )

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
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                            .addAction(android.R.drawable.ic_media_play, "Watch ▶", pendingIntent)
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
