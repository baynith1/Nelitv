package com.example.ai

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager

/**
 * Allows Android to list Neli AI as an eligible Mobile Assistant (`ACTION_ASSIST`)
 * and cleanly wakes/turns on the device screen when triggered via "Hey Neli open screen"
 * or any lock-screen wake phrase.
 */
class NeliAiAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the physical display turns ON and shows over lock screen immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            runCatching {
                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                km?.requestDismissKeyguard(this, null)
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        val wakeScreenOnly = intent?.getBooleanExtra(EXTRA_WAKE_SCREEN_ONLY, false) == true
        if (!wakeScreenOnly) {
            NeliDigitalAssistantHelper.wakeNeliAiFromSystemTrigger(this)
        }
        finish()
    }

    companion object {
        const val EXTRA_START_NELI_AI_VOICE = "neli_ai_start_voice_from_assist"
        const val EXTRA_WAKE_SCREEN_ONLY = "neli_ai_wake_screen_only"
    }
}
