package com.example.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.example.MainActivity

/**
 * Registers NeliTv as a genuine Android Default Digital Assistant app.
 *
 * Once selected in Settings -> Apps -> Default apps -> Digital assistant app:
 * - Holding the Power / Shut Down button (Android 12+ Side Key -> Digital Assistant),
 * - Swiping from bottom corner / long-pressing Home, or
 * - Pressing the Assistant / Voice key on an Android TV remote
 * immediately invokes [NeliVoiceInteractionSession] to wake Neli AI anywhere (including Lock Screen).
 */
class NeliVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        NeliAiMemoryStore.initialize(applicationContext)
    }

    override fun onShutdown() {
        super.onShutdown()
    }
}

class NeliVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return NeliVoiceInteractionSession(this)
    }
}

class NeliVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        NeliDigitalAssistantHelper.wakeNeliAiFromSystemTrigger(context)
        // Dismiss the empty framework overlay promptly after launching Neli AI's own overlay or Activity
        hide()
    }
}

/**
 * Compliant [RecognitionService] stub required by Android 10–15 `voice-interaction-service`
 * declarations so NeliTv is listed cleanly in Default Digital Assistant settings.
 */
class NeliRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        // Trigger Neli AI assistant voice capture and inform caller cleanly
        NeliDigitalAssistantHelper.wakeNeliAiFromSystemTrigger(applicationContext)
        runCatching {
            listener?.readyForSpeech(Bundle())
            listener?.endOfSpeech()
            listener?.error(SpeechRecognizer.ERROR_NO_MATCH)
        }
    }

    override fun onCancel(listener: Callback?) {
        // No-op
    }

    override fun onStopListening(listener: Callback?) {
        runCatching {
            listener?.endOfSpeech()
        }
    }
}

/**
 * Detects rapid Power / Shut Down button double-taps (`ACTION_SCREEN_OFF` / `ACTION_SCREEN_ON`
 * occurring within [DOUBLE_TAP_WINDOW_MS]) so users can double-tap the Power button to wake Neli AI
 * even when outside NeliTv or on the Lock Screen.
 */
class NeliPowerButtonDoubleTapDetector(
    private val onDoubleTapDetected: () -> Unit
) {
    private var lastPowerToggleElapsedMs: Long = 0L
    private var lastTriggerElapsedMs: Long = 0L
    private var toggleCountInWindow: Int = 0
    private var isRegistered: Boolean = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action != Intent.ACTION_SCREEN_OFF && action != Intent.ACTION_SCREEN_ON) return
            if (!NeliAiMemoryStore.isPowerButtonWakeEnabled.value) return
            if (recordToggleAndCheckDoubleTap(SystemClock.elapsedRealtime())) {
                onDoubleTapDetected()
            }
        }
    }

    fun register(context: Context) {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            isRegistered = true
        }
    }

    fun unregister(context: Context) {
        if (!isRegistered) return
        runCatching { context.unregisterReceiver(receiver) }
        isRegistered = false
    }

    internal fun recordToggleAndCheckDoubleTap(nowElapsedMs: Long): Boolean {
        if (nowElapsedMs - lastTriggerElapsedMs < COOLDOWN_MS) {
            return false
        }
        if (lastPowerToggleElapsedMs > 0L && (nowElapsedMs - lastPowerToggleElapsedMs) in MIN_GAP_MS..DOUBLE_TAP_WINDOW_MS) {
            toggleCountInWindow++
        } else {
            toggleCountInWindow = 1
        }
        lastPowerToggleElapsedMs = nowElapsedMs

        if (toggleCountInWindow >= 2) {
            toggleCountInWindow = 0
            lastTriggerElapsedMs = nowElapsedMs
            return true
        }
        return false
    }

    companion object {
        const val MIN_GAP_MS = 80L
        const val DOUBLE_TAP_WINDOW_MS = 1_350L
        const val COOLDOWN_MS = 2_500L
    }
}

object NeliDigitalAssistantHelper {

    /**
     * Returns true if NeliTv is currently selected as Android's Default Digital Assistant app.
     */
    fun isNeliDefaultDigitalAssistant(context: Context): Boolean {
        return runCatching {
            val assistant = Settings.Secure.getString(context.contentResolver, "assistant").orEmpty()
            val voiceService = Settings.Secure.getString(context.contentResolver, "voice_interaction_service").orEmpty()
            val pkg = context.packageName
            assistant.contains(pkg, ignoreCase = true) || voiceService.contains(pkg, ignoreCase = true)
        }.getOrDefault(false)
    }

    /**
     * Opens Android's Default Digital Assistant settings screen so the user can choose NeliTv
     * (which also enables Hold Power Button -> Neli AI on Android 12+ devices).
     */
    fun openDigitalAssistantSettings(context: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val ok = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return
        }
    }

    /**
     * Opens Android's Power Button / Assist Gesture settings when available, falling back to
     * Default Digital Assistant settings.
     */
    fun openPowerButtonGestureSettings(context: Context) {
        val candidates = listOf(
            Intent("android.settings.ASSIST_GESTURE_SETTINGS"),
            Intent("android.settings.GESTURE_SETTINGS"),
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            val ok = runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
            if (ok) return
        }
    }

    /**
     * Wakes the screen cleanly (if locked/dimmed) and activates Neli AI voice listening
     * via the system overlay when available, or directly inside [MainActivity].
     */
    fun wakeNeliAiFromSystemTrigger(context: Context) {
        wakeScreenBriefly(context)
        if (!NeliAiSystemOverlayService.isNeliTvInForeground && Settings.canDrawOverlays(context)) {
            NeliAiSystemOverlayService.start(context, triggerVoiceImmediately = true)
        } else {
            runCatching {
                val launch = Intent(context, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
                    putExtra(NeliAiAssistActivity.EXTRA_START_NELI_AI_VOICE, true)
                }
                context.startActivity(launch)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenBriefly(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (!pm.isInteractive) {
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "NeliTv:NeliAiPowerWake"
                )
                wl.acquire(4_000L)
            }
        }
    }
}
