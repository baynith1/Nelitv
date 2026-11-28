package com.example.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Full Siri-Grade Mobile Assistant NLU & Execution Planner for Neli AI.
 *
 * Handles instantaneously on-device (< 10ms) with zero API keys:
 * - 25+ English & Swahili Screen Wake phrases ("Hey Neli open screen", "Washa kioo", etc.)
 * - Multi-step compound commands ("Turn on dark mode and set brightness to 50%")
 * - Device Settings (Flashlight / Lumos / Nox, Dark/Light Mode, DND, Low Power, Wi-Fi, Bluetooth, Airplane, exact Volume & Brightness %)
 * - Alarms, Timers, & Persistent Personal / Location Reminders
 * - Contacts Relationships ("Kamal is my brother"), Calls on Speaker, Video Calls, Messages & Mail
 * - Parking Memory & Find My Phone Beacon
 * - Maps Directions, Home Navigation, Nearby Places & ETA
 * - Installed App Launcher ("Open WhatsApp", "Open Camera", "Open Spotify", etc.)
 * - Visual Intelligence ("What is this?", "Identify this object", "Visual search")
 * - Exact Math / Percentage Evaluator, Unit & Currency Converter, 10-Language Translator
 * - Live Weather via zero-key wttr.in API (fast 2s timeout), Time/Date/Battery & On-Screen Context
 */
object NeliSiriAssistantEngine {

    private val weatherHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1800, TimeUnit.MILLISECONDS)
            .readTimeout(2200, TimeUnit.MILLISECONDS)
            .build()
    }

    /**
     * 28+ Screen-Wake phrases (English + Swahili) so saying "Hey Neli open screen" or
     * "Neli wake up my phone" lights up the display immediately.
     */
    val wakeScreenPhrases: List<String> = listOf(
        "open screen",
        "open the screen",
        "open my screen",
        "wake screen",
        "wake the screen",
        "wake my screen",
        "wake up screen",
        "wake up my screen",
        "wake up phone",
        "wake up my phone",
        "wake my phone",
        "wake phone",
        "wake up device",
        "wake device",
        "wake up",
        "turn on screen",
        "turn on the screen",
        "turn on my screen",
        "turn screen on",
        "switch on screen",
        "light up screen",
        "light up the screen",
        "light up my screen",
        "light up my phone",
        "screen on",
        "display on",
        "turn on display",
        "show screen",
        "show my screen",
        "open my phone",
        "open phone",
        "unlock screen",
        "unlock my screen",
        "unlock my phone",
        "open lock screen",
        "brighten screen",
        "washa kioo",
        "washe kioo",
        "washa simu",
        "fungua kioo",
        "fungua simu",
        "amka neli",
        "amka",
        "amsha kioo",
        "amsha simu",
        "onyesha kioo",
        "tokea kioo"
    )

    private val numberFormat = DecimalFormat("#,##0.##")

    private val translationTable: Map<String, Map<String, String>> = mapOf(
        "thank you" to mapOf(
            "german" to "Danke",
            "french" to "Merci",
            "spanish" to "Gracias",
            "swahili" to "Asante",
            "kiswahili" to "Asante",
            "arabic" to "Shukran",
            "italian" to "Grazie",
            "portuguese" to "Obrigado",
            "chinese" to "Xie xie",
            "japanese" to "Arigato",
            "korean" to "Gamsahamnida"
        ),
        "thanks" to mapOf(
            "german" to "Danke",
            "french" to "Merci",
            "spanish" to "Gracias",
            "swahili" to "Asante",
            "arabic" to "Shukran",
            "italian" to "Grazie",
            "portuguese" to "Obrigado",
            "chinese" to "Xie xie",
            "japanese" to "Arigato",
            "korean" to "Gomawo"
        ),
        "hello" to mapOf(
            "german" to "Hallo",
            "french" to "Bonjour",
            "spanish" to "Hola",
            "swahili" to "Habari",
            "kiswahili" to "Habari",
            "arabic" to "Marhaba",
            "italian" to "Ciao",
            "portuguese" to "Ola",
            "chinese" to "Ni hao",
            "japanese" to "Konnichiwa",
            "korean" to "Annyeonghaseyo"
        ),
        "good morning" to mapOf(
            "german" to "Guten Morgen",
            "french" to "Bonjour",
            "spanish" to "Buenos dias",
            "swahili" to "Habari za asubuhi",
            "arabic" to "Sabah al-khayr",
            "italian" to "Buongiorno",
            "portuguese" to "Bom dia",
            "chinese" to "Zao shang hao",
            "japanese" to "Ohayo gozaimasu",
            "korean" to "Joheun achim"
        ),
        "good night" to mapOf(
            "german" to "Gute Nacht",
            "french" to "Bonne nuit",
            "spanish" to "Buenas noches",
            "swahili" to "Usiku mwema",
            "arabic" to "Tusbih ala khayr",
            "italian" to "Buonanotte",
            "portuguese" to "Boa noite",
            "chinese" to "Wan an",
            "japanese" to "Oyasumi nasai",
            "korean" to "Jal ja"
        ),
        "i love you" to mapOf(
            "german" to "Ich liebe dich",
            "french" to "Je t'aime",
            "spanish" to "Te quiero",
            "swahili" to "Nakupenda",
            "arabic" to "Uhibbuka",
            "italian" to "Ti amo",
            "portuguese" to "Eu te amo",
            "chinese" to "Wo ai ni",
            "japanese" to "Aishiteru",
            "korean" to "Saranghae"
        ),
        "please" to mapOf(
            "german" to "Bitte",
            "french" to "S'il vous plait",
            "spanish" to "Por favor",
            "swahili" to "Tafadhali",
            "arabic" to "Min fadlik",
            "italian" to "Per favore",
            "portuguese" to "Por favor",
            "chinese" to "Qing",
            "japanese" to "Onegaishimasu",
            "korean" to "Jebal"
        ),
        "sorry" to mapOf(
            "german" to "Es tut mir leid",
            "french" to "Desole",
            "spanish" to "Lo siento",
            "swahili" to "Samahani",
            "arabic" to "Aasif",
            "italian" to "Mi dispiace",
            "portuguese" to "Desculpe",
            "chinese" to "Dui bu qi",
            "japanese" to "Gomen nasai",
            "korean" to "Mianhae"
        ),
        "goodbye" to mapOf(
            "german" to "Auf Wiedersehen",
            "french" to "Au revoir",
            "spanish" to "Adios",
            "swahili" to "Kwaheri",
            "arabic" to "Ma'a as-salama",
            "italian" to "Arrivederci",
            "portuguese" to "Tchau",
            "chinese" to "Zai jian",
            "japanese" to "Sayonara",
            "korean" to "Annyeong"
        ),
        "how are you" to mapOf(
            "german" to "Wie geht es dir",
            "french" to "Comment ca va",
            "spanish" to "Como estas",
            "swahili" to "Habari yako",
            "arabic" to "Kayfa haluka",
            "italian" to "Come stai",
            "portuguese" to "Tudo bem",
            "chinese" to "Ni hao ma",
            "japanese" to "Ogenki desu ka",
            "korean" to "Jal jinae"
        ),
        "yes" to mapOf(
            "german" to "Ja",
            "french" to "Oui",
            "spanish" to "Si",
            "swahili" to "Ndio",
            "arabic" to "Na'am",
            "italian" to "Si",
            "portuguese" to "Sim",
            "chinese" to "Shi",
            "japanese" to "Hai",
            "korean" to "Ne"
        ),
        "no" to mapOf(
            "german" to "Nein",
            "french" to "Non",
            "spanish" to "No",
            "swahili" to "Hapana",
            "arabic" to "La",
            "italian" to "No",
            "portuguese" to "Nao",
            "chinese" to "Bu",
            "japanese" to "Iie",
            "korean" to "Aniyo"
        ),
        "water" to mapOf(
            "german" to "Wasser",
            "french" to "Eau",
            "spanish" to "Agua",
            "swahili" to "Maji",
            "arabic" to "Maa",
            "italian" to "Acqua",
            "portuguese" to "Agua",
            "chinese" to "Shui",
            "japanese" to "Mizu",
            "korean" to "Mul"
        )
    )

    private val launchableAppKeywords: List<String> = listOf(
        "whatsapp", "youtube", "spotify", "instagram", "tiktok", "facebook",
        "chrome", "gmail", "google maps", "maps", "telegram", "twitter",
        "netflix", "calendar", "calculator", "clock", "camera", "kamera",
        "messages", "sms", "settings", "mipangilio", "play store", "photos", "gallery"
    )

    fun isWakeScreenIntent(lower: String): Boolean {
        val clean = lower.trim()
        if (clean.isBlank()) return false
        return wakeScreenPhrases.any { phrase ->
            clean == phrase || clean.contains(phrase)
        }
    }

    /**
     * Synchronous ultra-fast Siri NLU evaluator (< 10ms).
     * Returns null when the query belongs to NeliTv channels/movies/downloads instead.
     */
    fun evaluateSync(
        cleaned: String,
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?,
        allowCompoundSplit: Boolean = true
    ): NeliAiCommandResponse? {
        if (lower.isBlank()) return null

        // 0. Pending Siri conversational follow-ups (e.g. answering home address / parking note)
        detectSiriFollowUp(cleaned, lower, context)?.let { return it }

        // 1. Multi-step compound commands ("Turn on dark mode and set brightness to 50%")
        if (allowCompoundSplit && (lower.contains(" and ") || lower.contains(" then "))) {
            val parts = cleaned.split(Regex("\\s+(?:and|then)\\s+", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.length >= 3 }
            if (parts.size == 2) {
                val r1 = evaluateSync(parts[0], normalize(parts[0]), snapshot, context, allowCompoundSplit = false)
                val r2 = evaluateSync(parts[1], normalize(parts[1]), snapshot, context, allowCompoundSplit = false)
                if (r1 != null && r2 != null &&
                    r1.action != NeliAiAction.None &&
                    r2.action != NeliAiAction.None
                ) {
                    val combinedReply = "${r1.replyText} Also, ${r2.replyText.replaceFirstChar { it.lowercase() }}"
                    val combinedSpeech = "${r1.replySpeech} And ${r2.replySpeech.replaceFirstChar { it.lowercase() }}"
                    return NeliAiCommandResponse(
                        intent = "SIRI_MULTI_STEP",
                        confidence = 0.98f,
                        replyText = combinedReply,
                        replySpeech = combinedSpeech,
                        sourceTag = "Neli AI Multi-Step",
                        action = NeliAiAction.CompositeActions(listOf(r1.action, r2.action))
                    )
                }
            }
        }

        // 2. Wake Screen (28+ phrases)
        if (isWakeScreenIntent(lower)) {
            return NeliAiCommandResponse(
                intent = "WAKE_SCREEN",
                confidence = 0.99f,
                replyText = "Screen is awake! What can I do for you?",
                replySpeech = "Screen is awake! What do you need?",
                sourceTag = "Neli Screen Wake",
                action = NeliAiAction.WakeDeviceScreen,
                expectsFollowUp = true
            )
        }

        // 3. Flashlight (including fun Siri phrases "Lumos" / "Nox" + Swahili "Washa tochi")
        detectFlashlightIntent(lower)?.let { return it }

        // 4. Exact Brightness % or Volume % ("Set brightness to 50%", "Set volume to 80%")
        detectExactPercentControl(lower)?.let { return it }

        // 5. Dark Mode / Light Mode, Do Not Disturb, Low Power, Wi-Fi, Bluetooth, Airplane, Screenshot, Restart, Hang Up
        detectDeviceSettingsIntent(lower, context)?.let { return it }

        // 6. Alarms & Timers
        detectAlarmOrTimerIntent(cleaned, lower)?.let { return it }

        // 7. Persistent Reminders & Location Reminders
        detectReminderIntent(cleaned, lower, context)?.let { return it }

        // 8. Contacts Relationships ("Kamal is my brother"), Calls (Speaker / Video), Messages, Mail
        detectRelationshipOrCommunicationIntent(cleaned, lower, context)?.let { return it }

        // 9. Parking Memory & Find My Phone
        detectParkingOrFindMyPhoneIntent(cleaned, lower, context)?.let { return it }

        // 10. Navigation, Home Address, Directions, Nearby Places & ETA
        detectNavigationOrPlacesIntent(cleaned, lower, context)?.let { return it }

        // 11. Visual Intelligence ("What is this?", "Identify this object", "Visual search")
        detectVisualIntelligenceIntent(cleaned, lower)?.let { return it }

        // 11B. Voice Dictation & Transcription ("Start dictation", "Transcribe", "Voice to text", "Wispr Flow", "Superwhisper", "Andika kwa sauti")
        detectVoiceDictationIntent(cleaned, lower)?.let { return it }

        // 12. Open Any Installed App ("Open WhatsApp", "Open Camera", "Open YouTube")
        detectInstalledAppLaunchIntent(lower)?.let { return it }

        // 13. Answers: Math & Percentage, Unit & Currency Conversion, Translation, Time/Date/Battery, On-Screen Context
        detectMathIntent(cleaned, lower)?.let { return it }
        detectUnitOrCurrencyConversion(lower)?.let { return it }
        detectTranslationIntent(cleaned, lower)?.let { return it }
        detectTimeDateBatteryOrScreenContext(lower, snapshot, context)?.let { return it }

        // 14. Music & Song Recognition ("Play <song> song", "What song is playing?")
        detectMusicIntent(cleaned, lower)?.let { return it }

        return null
    }

    /**
     * Async Siri evaluator for fast online lookups (Live Weather via zero-key wttr.in).
     */
    suspend fun evaluateAsync(
        cleaned: String,
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        if (!snapshot.isOnline) return null
        if (isWeatherIntent(lower)) {
            val city = extractWeatherCity(cleaned, lower)
            return fetchLiveWeather(city)
        }
        return null
    }

    private fun detectSiriFollowUp(
        cleaned: String,
        lower: String,
        context: Context?
    ): NeliAiCommandResponse? {
        val topic = NeliAiMemoryStore.lastPendingConversationTopic
        if (topic.isBlank()) return null

        if (topic == "ASK_HOME_ADDRESS") {
            if (lower in setOf("cancel", "never mind", "no", "hapana", "basi")) {
                NeliAiMemoryStore.lastPendingConversationTopic = ""
                return NeliAiCommandResponse(
                    intent = "HOME_ADDRESS_CANCELLED",
                    confidence = 0.95f,
                    replyText = "Okay, cancelled saving your home address.",
                    replySpeech = "Okay, no problem.",
                    sourceTag = "Neli Maps"
                )
            }
            NeliAiMemoryStore.savePlaceAddress(context, "home", cleaned)
            NeliAiMemoryStore.lastPendingConversationTopic = ""
            return NeliAiCommandResponse(
                intent = "NAVIGATE_HOME_SAVED",
                confidence = 0.98f,
                replyText = "Saved your home address as $cleaned! Opening directions home now.",
                replySpeech = "Got it! Starting directions home to $cleaned now.",
                sourceTag = "Neli Maps",
                action = NeliAiAction.OpenMapsDirections(destination = cleaned, mode = "d")
            )
        }

        if (topic == "ASK_PARKING_NOTE") {
            NeliAiMemoryStore.saveParkingNote(context, cleaned)
            NeliAiMemoryStore.lastPendingConversationTopic = ""
            return NeliAiCommandResponse(
                intent = "PARKING_SAVED",
                confidence = 0.98f,
                replyText = "Got it! I saved your parking spot: $cleaned.",
                replySpeech = "Saved! I'll remember you parked at $cleaned.",
                sourceTag = "Neli Parking Memory"
            )
        }

        return null
    }

    private fun detectFlashlightIntent(lower: String): NeliAiCommandResponse? {
        val turnOn = lower == "lumos" ||
            lower.contains("lumos") ||
            containsAny(
                lower,
                listOf(
                    "turn on flashlight", "turn on the flashlight", "flashlight on", "torch on",
                    "turn on torch", "turn on the torch", "switch on flashlight", "open flashlight",
                    "washa tochi", "washa taa", "fungua tochi"
                )
            )
        if (turnOn) {
            return NeliAiCommandResponse(
                intent = "FLASHLIGHT_ON",
                confidence = 0.99f,
                replyText = "Flashlight is on!",
                replySpeech = "Flashlight on!",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.ToggleFlashlight(true)
            )
        }

        val turnOff = lower == "nox" ||
            lower.contains("nox") ||
            containsAny(
                lower,
                listOf(
                    "turn off flashlight", "turn off the flashlight", "flashlight off", "torch off",
                    "turn off torch", "turn off the torch", "switch off flashlight", "close flashlight",
                    "zima tochi", "zima taa", "funga tochi"
                )
            )
        if (turnOff) {
            return NeliAiCommandResponse(
                intent = "FLASHLIGHT_OFF",
                confidence = 0.99f,
                replyText = "Flashlight is off!",
                replySpeech = "Flashlight off!",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.ToggleFlashlight(false)
            )
        }
        return null
    }

    private fun detectExactPercentControl(lower: String): NeliAiCommandResponse? {
        val pctMatch = Regex("\\b(\\d{1,3})\\s*(?:%|percent|asilimia)\\b").find(lower)
            ?: Regex("(?:brightness|mwanga|volume|sauti)\\s+(?:to|at|kwa)?\\s*(\\d{1,3})\\b").find(lower)
        val pct = pctMatch?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val clamped = pct.coerceIn(0, 100)

        if (containsAny(lower, listOf("brightness", "mwanga", "screen light"))) {
            return NeliAiCommandResponse(
                intent = "SET_BRIGHTNESS_PERCENT",
                confidence = 0.99f,
                replyText = "Setting brightness to $clamped%.",
                replySpeech = "Brightness set to $clamped percent.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.SetBrightnessPercent(clamped)
            )
        }
        if (containsAny(lower, listOf("volume", "sauti", "sound"))) {
            return NeliAiCommandResponse(
                intent = "SET_VOLUME_PERCENT",
                confidence = 0.99f,
                replyText = "Setting volume to $clamped%.",
                replySpeech = "Volume set to $clamped percent.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.SetVolumePercent(clamped)
            )
        }
        return null
    }

    private fun detectDeviceSettingsIntent(lower: String, context: Context?): NeliAiCommandResponse? {
        // Dark Mode / Light Mode
        if (containsAny(lower, listOf("turn on dark mode", "enable dark mode", "switch to dark mode", "dark mode on", "dark theme", "weka dark mode"))) {
            return NeliAiCommandResponse(
                intent = "DARK_MODE_ON",
                confidence = 0.99f,
                replyText = "Dark Mode is now on!",
                replySpeech = "Dark mode on!",
                sourceTag = "Neli Appearance",
                action = NeliAiAction.SetDarkMode(true)
            )
        }
        if (containsAny(lower, listOf("turn off dark mode", "disable dark mode", "turn on light mode", "enable light mode", "switch to light mode", "light mode on", "light theme", "weka light mode"))) {
            return NeliAiCommandResponse(
                intent = "LIGHT_MODE_ON",
                confidence = 0.99f,
                replyText = "Light Mode is now on!",
                replySpeech = "Light mode on!",
                sourceTag = "Neli Appearance",
                action = NeliAiAction.SetDarkMode(false)
            )
        }

        // Do Not Disturb
        if (containsAny(lower, listOf("turn on do not disturb", "enable do not disturb", "do not disturb on", "turn on dnd", "usinisumbue"))) {
            return NeliAiCommandResponse(
                intent = "DND_ON",
                confidence = 0.99f,
                replyText = "Turning on Do Not Disturb.",
                replySpeech = "Do Not Disturb is on.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.ToggleDoNotDisturb(true)
            )
        }
        if (containsAny(lower, listOf("turn off do not disturb", "disable do not disturb", "do not disturb off", "turn off dnd"))) {
            return NeliAiCommandResponse(
                intent = "DND_OFF",
                confidence = 0.99f,
                replyText = "Turning off Do Not Disturb.",
                replySpeech = "Do Not Disturb is off.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.ToggleDoNotDisturb(false)
            )
        }

        // Low Power / Battery Saver
        if (containsAny(lower, listOf("low power mode", "battery saver", "power saving", "save battery", "okoa chaji"))) {
            val batteryNote = context?.let { " ${NeliDeviceControlExecutor.getBatterySummary(it)}" }.orEmpty()
            return NeliAiCommandResponse(
                intent = "OPEN_BATTERY_SAVER",
                confidence = 0.98f,
                replyText = "Opening Battery Saver settings.$batteryNote",
                replySpeech = "Opening Battery Saver.$batteryNote",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.OpenSystemSetting(NeliSystemSettingType.LOW_POWER)
            )
        }

        // Wi-Fi
        if (containsAny(lower, listOf("turn on wi fi", "turn off wi fi", "turn on wifi", "turn off wifi", "wifi settings", "wi fi settings", "open wifi", "washa wifi", "zima wifi"))) {
            return NeliAiCommandResponse(
                intent = "OPEN_WIFI_SETTINGS",
                confidence = 0.98f,
                replyText = "Opening your Wi-Fi controls now.",
                replySpeech = "Opening Wi-Fi controls.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.OpenSystemSetting(NeliSystemSettingType.WIFI)
            )
        }

        // Bluetooth
        if (containsAny(lower, listOf("turn on bluetooth", "turn off bluetooth", "bluetooth settings", "open bluetooth", "washa bluetooth", "zima bluetooth"))) {
            return NeliAiCommandResponse(
                intent = "OPEN_BLUETOOTH_SETTINGS",
                confidence = 0.98f,
                replyText = "Opening Bluetooth settings now.",
                replySpeech = "Opening Bluetooth settings.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.OpenSystemSetting(NeliSystemSettingType.BLUETOOTH)
            )
        }

        // Airplane mode
        if (containsAny(lower, listOf("airplane mode", "flight mode", "aeroplane mode"))) {
            return NeliAiCommandResponse(
                intent = "OPEN_AIRPLANE_SETTINGS",
                confidence = 0.98f,
                replyText = "Opening Airplane Mode settings.",
                replySpeech = "Opening Airplane Mode settings.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.OpenSystemSetting(NeliSystemSettingType.AIRPLANE_MODE)
            )
        }

        // Screenshot / Restart / Hang up helpers (Play-compliant guidance + Accessibility shortcut)
        if (containsAny(lower, listOf("take a screenshot", "take screenshot", "screenshot", "piga picha ya kioo"))) {
            return NeliAiCommandResponse(
                intent = "SCREENSHOT_ASSIST",
                confidence = 0.96f,
                replyText = "Press Power + Volume Down together to capture a screenshot instantly!",
                replySpeech = "Press Power and Volume Down together to snap a screenshot!",
                sourceTag = "Neli Device Assist",
                action = NeliAiAction.None
            )
        }
        if (containsAny(lower, listOf("restart my phone", "reboot my phone", "restart device", "zima simu"))) {
            return NeliAiCommandResponse(
                intent = "RESTART_ASSIST",
                confidence = 0.96f,
                replyText = "For security, Android requires holding the Power button to confirm restarting your phone.",
                replySpeech = "Hold the Power button to confirm restarting your phone.",
                sourceTag = "Neli Device Assist",
                action = NeliAiAction.None
            )
        }
        if (containsAny(lower, listOf("hang up", "end the call", "end call", "kata simu"))) {
            return NeliAiCommandResponse(
                intent = "HANG_UP_ASSIST",
                confidence = 0.96f,
                replyText = "Tap the red End Call button, or enable Power Button Ends Call in Accessibility settings.",
                replySpeech = "Tap End Call, or turn on Power Button Ends Call in Accessibility.",
                sourceTag = "Neli Device Assist",
                action = NeliAiAction.OpenSystemSetting(NeliSystemSettingType.HANG_UP_HELP)
            )
        }

        return null
    }

    private fun detectAlarmOrTimerIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        // Delete / dismiss / show alarms
        if (containsAny(lower, listOf("delete all my alarms", "delete all alarms", "clear all alarms", "remove all alarms", "cancel all alarms", "futa alarm zote"))) {
            return NeliAiCommandResponse(
                intent = "DISMISS_ALL_ALARMS",
                confidence = 0.99f,
                replyText = "Opening your alarms to dismiss or delete them now.",
                replySpeech = "Opening your alarms so you can clear them.",
                sourceTag = "Neli Clock",
                action = NeliAiAction.ShowOrDismissAlarms(deleteAll = true)
            )
        }
        if (containsAny(lower, listOf("show my alarms", "show alarms", "what alarms do i have", "check my alarms"))) {
            return NeliAiCommandResponse(
                intent = "SHOW_ALARMS",
                confidence = 0.98f,
                replyText = "Showing your alarms now.",
                replySpeech = "Here are your alarms.",
                sourceTag = "Neli Clock",
                action = NeliAiAction.ShowOrDismissAlarms(deleteAll = false)
            )
        }

        // Set Timer ("Set a timer for 20 minutes", "Timer for 45 seconds", "Set timer for 1 hour 30 minutes")
        if (containsAny(lower, listOf("timer", "countdown", "kipima muda"))) {
            val hours = Regex("(\\d+)\\s*(?:hour|hours|hr|hrs|saa)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val mins = Regex("(\\d+)\\s*(?:minute|minutes|min|mins|dakika)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val secs = Regex("(\\d+)\\s*(?:second|seconds|sec|secs|sekunde)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val totalSecs = (hours * 3600) + (mins * 60) + secs
            if (totalSecs > 0) {
                val label = buildString {
                    if (hours > 0) append("${hours}h ")
                    if (mins > 0) append("${mins}m ")
                    if (secs > 0) append("${secs}s")
                }.trim()
                return NeliAiCommandResponse(
                    intent = "SET_TIMER",
                    confidence = 0.99f,
                    replyText = "Timer set for $label and counting down!",
                    replySpeech = "Timer set for $label, starting now!",
                    sourceTag = "Neli Clock",
                    action = NeliAiAction.SetTimer(durationSeconds = totalSecs, label = "Neli Timer ($label)")
                )
            }
        }

        // Set Alarm ("Wake me up at 9:00 AM", "Set alarm for 6:30 PM", "Alarm at 7")
        val isAlarmCommand = containsAny(lower, listOf("wake me up", "set an alarm", "set alarm", "alarm for", "alarm at", "weka alarm", "niamshe saa"))
        if (isAlarmCommand) {
            val timeRegex = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)?\\b", RegexOption.IGNORE_CASE)
            val m = timeRegex.find(cleaned)
            if (m != null) {
                var hour = m.groupValues[1].toIntOrNull() ?: 7
                val minute = m.groupValues[2].toIntOrNull() ?: 0
                val meridiem = m.groupValues[3].lowercase(Locale.ROOT).replace(".", "")
                if (meridiem == "pm" && hour in 1..11) hour += 12
                if (meridiem == "am" && hour == 12) hour = 0
                hour = hour.coerceIn(0, 23)
                val formatted = format12Hour(hour, minute)
                return NeliAiCommandResponse(
                    intent = "SET_ALARM",
                    confidence = 0.99f,
                    replyText = "Alarm set for $formatted!",
                    replySpeech = "Done! Your alarm is set for $formatted.",
                    sourceTag = "Neli Clock",
                    action = NeliAiAction.SetAlarm(hour24 = hour, minute = minute, label = "Neli AI Alarm")
                )
            }
        }
        return null
    }

    private fun detectReminderIntent(
        cleaned: String,
        lower: String,
        context: Context?
    ): NeliAiCommandResponse? {
        if (containsAny(lower, listOf("clear reminders", "delete all reminders", "futa vikumbusho"))) {
            NeliAiMemoryStore.clearReminders(context)
            return NeliAiCommandResponse(
                intent = "CLEAR_REMINDERS",
                confidence = 0.98f,
                replyText = "All your Neli AI reminders have been cleared.",
                replySpeech = "Done! All reminders cleared.",
                sourceTag = "Neli Reminders"
            )
        }

        if (containsAny(lower, listOf("my reminders", "show reminders", "read reminders", "what are my reminders", "vikumbusho vyangu"))) {
            val list = NeliAiMemoryStore.reminders.value
            val reply = if (list.isEmpty()) {
                "You don't have any saved reminders right now. Say \"Remind me to buy milk\" to add one!"
            } else {
                val joined = list.mapIndexed { idx, r -> "${idx + 1}. $r" }.joinToString("; ")
                "You have ${list.size} reminder${if (list.size == 1) "" else "s"}: $joined."
            }
            return NeliAiCommandResponse(
                intent = "LIST_REMINDERS",
                confidence = 0.98f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "Neli Reminders"
            )
        }

        val reminderMatch = Regex(
            "(?:remind me to|remind me|add reminder to|add a reminder to|nikumbushe)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (reminderMatch != null) {
            val task = reminderMatch.groupValues[1].trim().trimEnd('.', '!')
            if (task.length >= 2) {
                NeliAiMemoryStore.addReminder(context, task)
                return NeliAiCommandResponse(
                    intent = "ADD_REMINDER",
                    confidence = 0.99f,
                    replyText = "Reminder saved: \"$task\".",
                    replySpeech = "Got it! I'll remind you to $task.",
                    sourceTag = "Neli Reminders"
                )
            }
        }
        return null
    }

    private fun detectRelationshipOrCommunicationIntent(
        cleaned: String,
        lower: String,
        context: Context?
    ): NeliAiCommandResponse? {
        // Teach relationship: "Kamal is my brother", "Sarah is my mom"
        val relMatch = Regex(
            "^([a-zA-Z][a-zA-Z\\s]{1,28})\\s+is\\s+my\\s+(brother|sister|mom|mother|dad|father|wife|husband|boss|friend|partner|son|daughter|uncle|aunt|cousin)\\b",
            RegexOption.IGNORE_CASE
        ).find(cleaned.trim())
        if (relMatch != null) {
            val name = relMatch.groupValues[1].trim()
            val relation = relMatch.groupValues[2].trim().lowercase(Locale.ROOT)
            NeliAiMemoryStore.saveRelationship(context, relation, name)
            return NeliAiCommandResponse(
                intent = "SAVE_RELATIONSHIP",
                confidence = 0.99f,
                replyText = "Got it! I'll remember that $name is your $relation.",
                replySpeech = "Remembered! $name is your $relation.",
                sourceTag = "Neli Contacts Memory"
            )
        }

        // Ask relationship: "Who is my brother?"
        val askRel = Regex("who is my\\s+(brother|sister|mom|mother|dad|father|wife|husband|boss|friend|partner|son|daughter)\\b", RegexOption.IGNORE_CASE)
            .find(cleaned)
        if (askRel != null) {
            val rel = askRel.groupValues[1].lowercase(Locale.ROOT)
            val person = NeliAiMemoryStore.resolveRelationshipName(rel)
            val reply = if (person != null) {
                "$person is your $rel."
            } else {
                "You haven't told me who your $rel is yet. Say \"Kamal is my $rel\" and I'll remember!"
            }
            return NeliAiCommandResponse(
                intent = "QUERY_RELATIONSHIP",
                confidence = 0.98f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "Neli Contacts Memory"
            )
        }

        // Read mail / last message
        if (containsAny(lower, listOf("read my new mail", "read my mail", "check my email", "check my mail", "read my email", "open my mail", "soma email"))) {
            return NeliAiCommandResponse(
                intent = "OPEN_MAIL",
                confidence = 0.97f,
                replyText = "Opening your Email inbox now.",
                replySpeech = "Opening your mail now.",
                sourceTag = "Neli Communications",
                action = NeliAiAction.OpenMessagingOrMailApp("mail")
            )
        }
        if (containsAny(lower, listOf("read my last message", "read my messages", "check my messages", "open my messages", "soma meseji"))) {
            return NeliAiCommandResponse(
                intent = "OPEN_MESSAGES",
                confidence = 0.97f,
                replyText = "Opening your Messages app now.",
                replySpeech = "Opening your messages now.",
                sourceTag = "Neli Communications",
                action = NeliAiAction.OpenMessagingOrMailApp("sms")
            )
        }

        // Send message ("Send a message to Kamal saying I'm on my way", "Text Mom hello")
        val smsWithBody = Regex(
            "(?:send (?:a )?(?:text |sms )?message to|text|message|tuma meseji kwa)\\s+([a-zA-Z0-9+\\s]{2,28}?)\\s+(?:saying|that|message|:)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (smsWithBody != null) {
            val rawRecipient = smsWithBody.groupValues[1].trim()
            val resolvedRecipient = NeliAiMemoryStore.resolveRelationshipName(rawRecipient) ?: rawRecipient
            val body = smsWithBody.groupValues[2].trim()
            return NeliAiCommandResponse(
                intent = "SEND_MESSAGE",
                confidence = 0.98f,
                replyText = "Opening Messages to $resolvedRecipient: \"$body\".",
                replySpeech = "Ready to message $resolvedRecipient.",
                sourceTag = "Neli Communications",
                action = NeliAiAction.SendMessage(recipient = resolvedRecipient, body = body)
            )
        }
        val smsSimple = Regex(
            "^(?:send (?:a )?(?:text |sms )?message to|text|tuma meseji kwa)\\s+([a-zA-Z0-9+\\s]{2,28})$",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (smsSimple != null) {
            val rawRecipient = smsSimple.groupValues[1].trim()
            val resolvedRecipient = NeliAiMemoryStore.resolveRelationshipName(rawRecipient) ?: rawRecipient
            return NeliAiCommandResponse(
                intent = "SEND_MESSAGE",
                confidence = 0.97f,
                replyText = "Opening Messages for $resolvedRecipient.",
                replySpeech = "Opening a message to $resolvedRecipient.",
                sourceTag = "Neli Communications",
                action = NeliAiAction.SendMessage(recipient = resolvedRecipient, body = "")
            )
        }

        // Video Call / FaceTime ("FaceTime Sarah", "Video call my mom")
        val videoMatch = Regex("^(?:facetime|face time|video call|piga video call)\\s+(.+)$", RegexOption.IGNORE_CASE)
            .find(cleaned.trim())
        if (videoMatch != null) {
            val rawTarget = videoMatch.groupValues[1].trim()
            val target = NeliAiMemoryStore.resolveRelationshipName(rawTarget) ?: rawTarget
            return NeliAiCommandResponse(
                intent = "VIDEO_CALL",
                confidence = 0.98f,
                replyText = "Starting a video call to $target!",
                replySpeech = "Starting video call to $target.",
                sourceTag = "Neli Communications",
                action = NeliAiAction.PlaceCall(contactOrNumber = target, onSpeaker = true, videoCall = true)
            )
        }

        // Voice Call ("Call Kamal on speaker", "Call my brother", "Dial 0712345678", "Piga simu ...")
        val callMatch = Regex(
            "^(?:call|dial|phone|ring|piga simu kwa|piga simu)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(cleaned.trim())
        if (callMatch != null) {
            val rawRest = callMatch.groupValues[1].trim()
            val onSpeaker = rawRest.contains("speaker", ignoreCase = true) || rawRest.contains("spika", ignoreCase = true)
            val rawTarget = rawRest
                .replace(Regex("\\s*(?:on\\s+)?(?:speakerphone|speaker|spika)\\s*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (rawTarget.isNotBlank()) {
                val target = NeliAiMemoryStore.resolveRelationshipName(rawTarget) ?: rawTarget
                val speakerLabel = if (onSpeaker) " on speaker" else ""
                return NeliAiCommandResponse(
                    intent = "PLACE_CALL",
                    confidence = 0.98f,
                    replyText = "Opening dialer to call $target$speakerLabel!",
                    replySpeech = "Calling $target$speakerLabel.",
                    sourceTag = "Neli Communications",
                    action = NeliAiAction.PlaceCall(contactOrNumber = target, onSpeaker = onSpeaker, videoCall = false)
                )
            }
        }

        return null
    }

    private fun detectParkingOrFindMyPhoneIntent(
        cleaned: String,
        lower: String,
        context: Context?
    ): NeliAiCommandResponse? {
        // Save parking location ("I parked my car at level 2", "Remember where I parked ...")
        val parkSave = Regex(
            "(?:i parked(?: my car| my vehicle)? (?:at|in|on|near)|save my parking(?: spot)? (?:at|as)|remember (?:that )?i parked (?:at|in|near))\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (parkSave != null) {
            val spot = parkSave.groupValues[1].trim().trimEnd('.')
            NeliAiMemoryStore.saveParkingNote(context, spot)
            return NeliAiCommandResponse(
                intent = "SAVE_PARKING",
                confidence = 0.99f,
                replyText = "Saved! You parked at $spot.",
                replySpeech = "Got it! I'll remember you parked at $spot.",
                sourceTag = "Neli Parking Memory"
            )
        }

        if (containsAny(lower, listOf("where did i park", "where is my car", "where is my vehicle", "my parking spot", "nimepaki wapi"))) {
            val saved = NeliAiMemoryStore.parkingNote.value
            return if (saved.isNotBlank()) {
                NeliAiCommandResponse(
                    intent = "RECALL_PARKING",
                    confidence = 0.99f,
                    replyText = "You parked at: $saved.",
                    replySpeech = "You parked at $saved.",
                    sourceTag = "Neli Parking Memory"
                )
            } else {
                NeliAiMemoryStore.lastPendingConversationTopic = "ASK_PARKING_NOTE"
                NeliAiCommandResponse(
                    intent = "ASK_PARKING_NOTE",
                    confidence = 0.96f,
                    replyText = "You haven't saved a parking spot yet. Tell me where you parked and I'll remember it!",
                    replySpeech = "You haven't saved a parking spot yet. Where did you park?",
                    sourceTag = "Neli Parking Memory",
                    expectsFollowUp = true
                )
            }
        }

        // Find My Phone ("Where is my phone?", "Find my phone", "Simu yangu iko wapi")
        if (containsAny(lower, listOf("where is my phone", "wheres my phone", "find my phone", "ring my phone", "simu yangu iko wapi"))) {
            val bat = context?.let { " ${NeliDeviceControlExecutor.getBatterySummary(it)}" }.orEmpty()
            return NeliAiCommandResponse(
                intent = "FIND_MY_PHONE",
                confidence = 0.99f,
                replyText = "Right here! Flashing your light and chiming now!$bat",
                replySpeech = "I'm right here! Flashing and chiming now!$bat",
                sourceTag = "Neli Find My",
                action = NeliAiAction.FindMyPhone
            )
        }

        return null
    }

    private fun detectNavigationOrPlacesIntent(
        cleaned: String,
        lower: String,
        context: Context?
    ): NeliAiCommandResponse? {
        // Save Home / Work address
        val saveHome = Regex(
            "(?:set|save)\\s+my\\s+(home|work)\\s+(?:address\\s+)?(?:to|as)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (saveHome != null) {
            val label = saveHome.groupValues[1].lowercase(Locale.ROOT)
            val addr = saveHome.groupValues[2].trim()
            NeliAiMemoryStore.savePlaceAddress(context, label, addr)
            return NeliAiCommandResponse(
                intent = "SAVE_PLACE_ADDRESS",
                confidence = 0.99f,
                replyText = "Saved your $label address as $addr!",
                replySpeech = "Saved your $label address!",
                sourceTag = "Neli Maps"
            )
        }

        // Take me home / Navigate home
        if (containsAny(lower, listOf("take me home", "directions home", "navigate home", "drive me home", "nipeleke nyumbani"))) {
            val savedHome = NeliAiMemoryStore.getPlaceAddress("home")
            return if (savedHome.isNotBlank()) {
                NeliAiCommandResponse(
                    intent = "NAVIGATE_HOME",
                    confidence = 0.99f,
                    replyText = "Starting directions home to $savedHome!",
                    replySpeech = "Starting directions home now!",
                    sourceTag = "Neli Maps",
                    action = NeliAiAction.OpenMapsDirections(destination = savedHome, mode = "d")
                )
            } else {
                NeliAiMemoryStore.lastPendingConversationTopic = "ASK_HOME_ADDRESS"
                NeliAiCommandResponse(
                    intent = "ASK_HOME_ADDRESS",
                    confidence = 0.96f,
                    replyText = "What's your home address? Tell me and I'll save it and start directions!",
                    replySpeech = "What's your home address? Tell me and I'll guide you there!",
                    sourceTag = "Neli Maps",
                    expectsFollowUp = true
                )
            }
        }

        // Directions / ETA to <place>
        val dirMatch = Regex(
            "(?:directions to|navigate to|take me to|route to|how do i get to|what'?s my eta to|eta to|nipeleke)\\s+(.+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (dirMatch != null) {
            val destination = dirMatch.groupValues[1].trim().trimEnd('?', '.')
            if (destination.length >= 2) {
                val mode = when {
                    lower.contains("walk") -> "w"
                    lower.contains("bike") || lower.contains("bicycle") -> "b"
                    else -> "d"
                }
                return NeliAiCommandResponse(
                    intent = "MAPS_DIRECTIONS",
                    confidence = 0.98f,
                    replyText = "Opening Maps directions and live ETA to $destination!",
                    replySpeech = "Opening directions to $destination now.",
                    sourceTag = "Neli Maps",
                    action = NeliAiAction.OpenMapsDirections(destination = destination, mode = mode)
                )
            }
        }

        // Nearby places ("Find coffee near me", "Nearest gas station", "Restaurants near me")
        val nearMe = Regex(
            "(?:find|search|where is the nearest|nearest|closest|show me)?\\s*(.+?)\\s+(?:near me|nearby|around here|close by)$",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (nearMe != null) {
            val place = nearMe.groupValues[1].trim()
                .removePrefix("a ")
                .removePrefix("some ")
                .removePrefix("good ")
                .trim()
            if (place.length >= 2 && !place.contains("movie", ignoreCase = true) && !place.contains("channel", ignoreCase = true)) {
                return NeliAiCommandResponse(
                    intent = "FIND_NEARBY",
                    confidence = 0.98f,
                    replyText = "Searching for $place near you on Maps!",
                    replySpeech = "Finding $place near you right now.",
                    sourceTag = "Neli Maps",
                    action = NeliAiAction.FindPlacesNearby(place)
                )
            }
        }

        return null
    }

    private fun detectVisualIntelligenceIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        if (containsAny(
                lower,
                listOf(
                    "what is this", "identify this", "identify object", "visual intelligence",
                    "visual search", "scan with camera", "scan this", "look at this",
                    "google lens", "open lens", "hii ni nini", "tambua hii"
                )
            )
        ) {
            return NeliAiCommandResponse(
                intent = "VISUAL_INTELLIGENCE",
                confidence = 0.98f,
                replyText = "Opening Visual Intelligence camera! Point at any object, text, or landmark.",
                replySpeech = "Opening Visual Intelligence! Point your camera at it.",
                sourceTag = "Neli Visual Intelligence",
                action = NeliAiAction.LaunchVisualIntelligence(cleaned)
            )
        }
        return null
    }

    private fun detectVoiceDictationIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        if (containsAny(
                lower,
                listOf(
                    "start dictation", "open dictation", "voice dictation", "dictate",
                    "transcribe", "transcription", "speech to text", "voice to text",
                    "wispr flow", "superwhisper", "neli flow", "andika kwa sauti",
                    "type for me", "take a note by voice"
                )
            )
        ) {
            return NeliAiCommandResponse(
                intent = "OPEN_VOICE_DICTATION",
                confidence = 0.99f,
                replyText = "Opening Neli Flow Dictation & Privacy Voice-to-Text now!",
                replySpeech = "Opening Flow Dictation! Speak freely and I'll clean and copy your text.",
                sourceTag = "Neli Flow Dictation",
                action = NeliAiAction.OpenVoiceDictation(cleaned)
            )
        }
        return null
    }

    private fun detectInstalledAppLaunchIntent(lower: String): NeliAiCommandResponse? {
        val openAppMatch = Regex("^(?:open|launch|start|fungua)\\s+(.+)$", RegexOption.IGNORE_CASE).find(lower)
            ?: return null
        val target = openAppMatch.groupValues[1]
            .removePrefix("the ")
            .removeSuffix(" app")
            .trim()
        if (target.isBlank() || target.contains("nelitv") || target.contains("neli tv") || target.contains("screen")) {
            return null
        }
        val matchedApp = launchableAppKeywords.firstOrNull { kw ->
            target == kw || target.startsWith("$kw ")
        } ?: return null

        val displayName = matchedApp.split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { it.uppercase() }
        }
        return NeliAiCommandResponse(
            intent = "LAUNCH_INSTALLED_APP",
            confidence = 0.98f,
            replyText = "Opening $displayName now!",
            replySpeech = "Opening $displayName!",
            sourceTag = "Neli App Launcher",
            action = NeliAiAction.LaunchInstalledApp(matchedApp)
        )
    }

    private fun detectMathIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        // 1. Percentage: "What is 6.4% of 250,000?" or "15 percent of 80"
        val pctOf = Regex(
            "([\\d,]+(?:\\.\\d+)?)\\s*(?:%|percent)\\s+of\\s+([\\d,]+(?:\\.\\d+)?)",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (pctOf != null) {
            val p = pctOf.groupValues[1].replace(",", "").toDoubleOrNull()
            val base = pctOf.groupValues[2].replace(",", "").toDoubleOrNull()
            if (p != null && base != null) {
                val res = (p / 100.0) * base
                val formatted = numberFormat.format(res)
                return NeliAiCommandResponse(
                    intent = "MATH_PERCENTAGE",
                    confidence = 0.99f,
                    replyText = "${pctOf.groupValues[1]}% of ${pctOf.groupValues[2]} is $formatted.",
                    replySpeech = "$formatted!",
                    sourceTag = "Neli Smart Calculator"
                )
            }
        }

        // 2. Square root: "square root of 144"
        val sqrtMatch = Regex("square root of\\s+([\\d,]+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(cleaned)
        if (sqrtMatch != null) {
            val n = sqrtMatch.groupValues[1].replace(",", "").toDoubleOrNull()
            if (n != null && n >= 0) {
                val formatted = numberFormat.format(sqrt(n))
                return NeliAiCommandResponse(
                    intent = "MATH_SQRT",
                    confidence = 0.99f,
                    replyText = "The square root of ${sqrtMatch.groupValues[1]} is $formatted.",
                    replySpeech = "The square root is $formatted.",
                    sourceTag = "Neli Smart Calculator"
                )
            }
        }

        // 3. Arithmetic: "what is 45 times 12", "125 + 375", "1000 divided by 8"
        val normalizedMath = cleaned
            .replace(",", "")
            .replace(Regex("\\b(?:plus|add)\\b", RegexOption.IGNORE_CASE), "+")
            .replace(Regex("\\b(?:minus|subtract)\\b", RegexOption.IGNORE_CASE), "-")
            .replace(Regex("\\b(?:times|multiplied by|x)\\b", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("\\b(?:divided by|over)\\b", RegexOption.IGNORE_CASE), "/")
        val exprMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)").find(normalizedMath)
        if (exprMatch != null && !lower.contains("channel") && !lower.contains("azam")) {
            val a = exprMatch.groupValues[1].toDoubleOrNull()
            val op = exprMatch.groupValues[2]
            val b = exprMatch.groupValues[3].toDoubleOrNull()
            if (a != null && b != null) {
                val result = when (op) {
                    "+" -> a + b
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (abs(b) > 1e-9) a / b else null
                    else -> null
                }
                if (result != null) {
                    val formatted = numberFormat.format(result)
                    return NeliAiCommandResponse(
                        intent = "MATH_CALCULATION",
                        confidence = 0.99f,
                        replyText = "${numberFormat.format(a)} $op ${numberFormat.format(b)} = $formatted.",
                        replySpeech = "That equals $formatted.",
                        sourceTag = "Neli Smart Calculator"
                    )
                }
            }
        }
        return null
    }

    private fun detectUnitOrCurrencyConversion(lower: String): NeliAiCommandResponse? {
        val convMatch = Regex(
            "(?:convert\\s+)?(\\d+(?:\\.\\d+)?)\\s*(km|kilometers?|miles?|mi|meters?|m|feet|ft|cm|centimeters?|inches?|in|kg|kilograms?|pounds?|lbs?|celsius|c|fahrenheit|f|liters?|litres?|gallons?|usd|dollars?|eur|euros?|gbp|pounds?|tzs|shillings?|kes)\\s+(?:to|in|into)\\s+(km|kilometers?|miles?|mi|meters?|m|feet|ft|cm|centimeters?|inches?|in|kg|kilograms?|pounds?|lbs?|celsius|c|fahrenheit|f|liters?|litres?|gallons?|usd|dollars?|eur|euros?|gbp|tzs|shillings?|kes)\\b",
            RegexOption.IGNORE_CASE
        ).find(lower) ?: return null

        val value = convMatch.groupValues[1].toDoubleOrNull() ?: return null
        val from = canonicalUnit(convMatch.groupValues[2])
        val to = canonicalUnit(convMatch.groupValues[3])
        val converted: Double? = when ("$from->$to") {
            "km->mi" -> value * 0.621371
            "mi->km" -> value * 1.60934
            "m->ft" -> value * 3.28084
            "ft->m" -> value * 0.3048
            "cm->in" -> value * 0.393701
            "in->cm" -> value * 2.54
            "kg->lb" -> value * 2.20462
            "lb->kg" -> value * 0.453592
            "c->f" -> (value * 9.0 / 5.0) + 32.0
            "f->c" -> (value - 32.0) * 5.0 / 9.0
            "l->gal" -> value * 0.264172
            "gal->l" -> value * 3.78541
            "usd->tzs" -> value * 2650.0
            "tzs->usd" -> value / 2650.0
            "usd->kes" -> value * 129.0
            "kes->usd" -> value / 129.0
            "usd->eur" -> value * 0.92
            "eur->usd" -> value / 0.92
            "usd->gbp" -> value * 0.79
            "gbp->usd" -> value / 0.79
            else -> null
        }

        if (converted != null) {
            val out = numberFormat.format(converted)
            val reply = "${numberFormat.format(value)} ${from.uppercase(Locale.ROOT)} is about $out ${to.uppercase(Locale.ROOT)}."
            return NeliAiCommandResponse(
                intent = "UNIT_CONVERSION",
                confidence = 0.99f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "Neli Smart Converter"
            )
        }
        return null
    }

    private fun canonicalUnit(raw: String): String = when (raw.lowercase(Locale.ROOT)) {
        "km", "kilometer", "kilometers" -> "km"
        "mi", "mile", "miles" -> "mi"
        "m", "meter", "meters" -> "m"
        "ft", "feet" -> "ft"
        "cm", "centimeter", "centimeters" -> "cm"
        "in", "inch", "inches" -> "in"
        "kg", "kilogram", "kilograms" -> "kg"
        "lb", "lbs", "pound", "pounds" -> "lb"
        "c", "celsius" -> "c"
        "f", "fahrenheit" -> "f"
        "l", "liter", "liters", "litre", "litres" -> "l"
        "gal", "gallon", "gallons" -> "gal"
        "usd", "dollar", "dollars" -> "usd"
        "eur", "euro", "euros" -> "eur"
        "gbp" -> "gbp"
        "tzs", "shilling", "shillings" -> "tzs"
        "kes" -> "kes"
        else -> raw.lowercase(Locale.ROOT)
    }

    private fun detectTranslationIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        val m = Regex(
            "(?:how (?:do you |to )?say|translate)\\s+[\"']?(.+?)[\"']?\\s+(?:in|to|into)\\s+([a-zA-Z]+)",
            RegexOption.IGNORE_CASE
        ).find(cleaned) ?: return null

        val phrase = m.groupValues[1].trim().lowercase(Locale.ROOT)
        val lang = m.groupValues[2].trim().lowercase(Locale.ROOT)
        val langDisplay = lang.replaceFirstChar { it.uppercase() }
        val direct = translationTable[phrase]?.get(lang)
            ?: translationTable.entries.firstOrNull { (k, _) -> phrase.contains(k) }?.value?.get(lang)

        if (direct != null) {
            return NeliAiCommandResponse(
                intent = "TRANSLATE_PHRASE",
                confidence = 0.99f,
                replyText = "\"$phrase\" in $langDisplay is \"$direct\".",
                replySpeech = "In $langDisplay, you say $direct!",
                sourceTag = "Neli Translator"
            )
        }

        // Open quick Google Translate search if phrase isn't in offline dictionary
        return NeliAiCommandResponse(
            intent = "TRANSLATE_WEB",
            confidence = 0.92f,
            replyText = "Translating \"$phrase\" into $langDisplay for you!",
            replySpeech = "Looking up $phrase in $langDisplay.",
            sourceTag = "Neli Translator",
            action = NeliAiAction.WebSearch("translate $phrase to $lang")
        )
    }

    private fun detectTimeDateBatteryOrScreenContext(
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?
    ): NeliAiCommandResponse? {
        // Time
        if (containsAny(lower, listOf("what time is it", "whats the time", "what is the time", "tell me the time", "saa ngapi", "ni saa ngapi"))) {
            val now = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            return NeliAiCommandResponse(
                intent = "TELL_TIME",
                confidence = 0.99f,
                replyText = "It's $now.",
                replySpeech = "It's $now!",
                sourceTag = "Neli Clock"
            )
        }

        // Date / Day
        if (containsAny(lower, listOf("what day is it", "what is today s date", "whats todays date", "what date is it", "leo tarehe ngapi", "leo siku gani"))) {
            val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).format(Date())
            return NeliAiCommandResponse(
                intent = "TELL_DATE",
                confidence = 0.99f,
                replyText = "Today is $today.",
                replySpeech = "Today is $today.",
                sourceTag = "Neli Calendar"
            )
        }

        // Battery level
        if (containsAny(lower, listOf("battery level", "battery percentage", "how much battery", "chaji ngapi", "betri"))) {
            val summary = context?.let { NeliDeviceControlExecutor.getBatterySummary(it) }
                ?: "Check the top status bar for your battery percentage."
            return NeliAiCommandResponse(
                intent = "BATTERY_STATUS",
                confidence = 0.99f,
                replyText = summary,
                replySpeech = summary,
                sourceTag = "Neli Device Status"
            )
        }

        // On-Screen Context & Accessibility ("What's on my screen?", "Read aloud")
        if (containsAny(lower, listOf("what is on my screen", "whats on my screen", "on my screen", "read aloud", "read this screen", "nini kipo kwenye kioo"))) {
            val active = snapshot.activePlayerChannel
            val desc = when {
                active != null -> {
                    val prog = if (active.currentProgram.isNotBlank()) " showing ${active.currentProgram}" else ""
                    "You are watching ${active.name}$prog on NeliTv."
                }
                snapshot.isOutsideApp -> "You are outside NeliTv. I can control your phone settings, set alarms, message contacts, or launch any app!"
                else -> "You are in NeliTv with ${snapshot.liveChannels.size} live channels, ${snapshot.mediaCatalog.size} movies and series, and ${snapshot.downloadedMediaIds.size} downloads ready."
            }
            return NeliAiCommandResponse(
                intent = "ON_SCREEN_CONTEXT",
                confidence = 0.98f,
                replyText = desc,
                replySpeech = desc,
                sourceTag = "Neli Screen Awareness"
            )
        }

        // Flip a coin / Roll a die (classic Siri fun utilities)
        if (containsAny(lower, listOf("flip a coin", "toss a coin"))) {
            val side = if (System.currentTimeMillis() % 2L == 0L) "Heads" else "Tails"
            return NeliAiCommandResponse(
                intent = "FLIP_COIN",
                confidence = 0.99f,
                replyText = "It's $side!",
                replySpeech = "It landed on $side!",
                sourceTag = "Neli Quick Utility"
            )
        }
        if (containsAny(lower, listOf("roll a die", "roll a dice", "roll dice"))) {
            val roll = ((System.currentTimeMillis() % 6L).toInt() + 1)
            return NeliAiCommandResponse(
                intent = "ROLL_DICE",
                confidence = 0.99f,
                replyText = "You rolled a $roll!",
                replySpeech = "You rolled a $roll!",
                sourceTag = "Neli Quick Utility"
            )
        }

        return null
    }

    private fun detectMusicIntent(cleaned: String, lower: String): NeliAiCommandResponse? {
        if (containsAny(lower, listOf("what song is playing", "what song is this", "identify this song", "who sings this", "wimbo gani huu"))) {
            return NeliAiCommandResponse(
                intent = "SONG_IDENTIFY",
                confidence = 0.96f,
                replyText = "Opening music search so you can identify the song playing around you!",
                replySpeech = "Opening song search now!",
                sourceTag = "Neli Music",
                action = NeliAiAction.WebSearch("what song is playing")
            )
        }

        val musicMatch = Regex(
            "^(?:play (?:the )?(?:song|music|track|playlist|album)|cheza (?:wimbo|muziki)(?: wa)?)\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(cleaned.trim())
        if (musicMatch != null) {
            val query = musicMatch.groupValues[1].trim()
            if (query.length >= 2) {
                return NeliAiCommandResponse(
                    intent = "PLAY_MUSIC",
                    confidence = 0.98f,
                    replyText = "Playing \"$query\" in your music app now!",
                    replySpeech = "Playing $query!",
                    sourceTag = "Neli Music",
                    action = NeliAiAction.PlayMusicOrMedia(query)
                )
            }
        }
        return null
    }

    private fun isWeatherIntent(lower: String): Boolean {
        return containsAny(
            lower,
            listOf(
                "weather", "forecast", "temperature", "will it rain", "is it raining",
                "hali ya hewa", "mvua", "joto"
            )
        )
    }

    private fun extractWeatherCity(cleaned: String, lower: String): String {
        val m = Regex("(?:weather|forecast|temperature|hali ya hewa)\\s+(?:in|at|for|ya)\\s+([a-zA-Z\\s]{2,32})", RegexOption.IGNORE_CASE)
            .find(cleaned)
        return m?.groupValues?.get(1)?.trim()?.trimEnd('?', '.') ?: ""
    }

    private suspend fun fetchLiveWeather(city: String): NeliAiCommandResponse = withContext(Dispatchers.IO) {
        try {
            val encodedCity = if (city.isBlank()) "" else URLEncoder.encode(city, "UTF-8")
            val url = "https://wttr.in/$encodedCity?format=j1"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "NeliTv-Assistant/1.0")
                .build()
            weatherHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
                val body = resp.body?.string().orEmpty()
                val json = JSONObject(body)
                val current = json.getJSONArray("current_condition").getJSONObject(0)
                val tempC = current.optString("temp_C", "")
                val feelsC = current.optString("FeelsLikeC", tempC)
                val desc = current.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value", "") ?: ""
                val area = json.optJSONArray("nearest_area")?.optJSONObject(0)
                    ?.optJSONArray("areaName")?.optJSONObject(0)?.optString("value", "")
                    .orEmpty()
                val placeLabel = city.ifBlank { area.ifBlank { "your area" } }
                val reply = "In $placeLabel right now, it's $tempC°C ($desc), feeling like $feelsC°C."
                NeliAiCommandResponse(
                    intent = "LIVE_WEATHER",
                    confidence = 0.99f,
                    replyText = reply,
                    replySpeech = "In $placeLabel right now, it's $tempC degrees and $desc.",
                    sourceTag = "Neli Live Weather"
                )
            }
        } catch (_: Exception) {
            val query = if (city.isBlank()) "weather today" else "weather in $city"
            NeliAiCommandResponse(
                intent = "WEATHER_SEARCH_FALLBACK",
                confidence = 0.92f,
                replyText = "Opening live weather forecast for ${city.ifBlank { "your location" }}!",
                replySpeech = "Opening today's weather forecast.",
                sourceTag = "Neli Weather",
                action = NeliAiAction.WebSearch(query)
            )
        }
    }

    private fun format12Hour(hour24: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
        }
        return SimpleDateFormat("h:mm a", Locale.US).format(cal.time)
    }

    private fun normalize(input: String): String =
        input.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }
}
