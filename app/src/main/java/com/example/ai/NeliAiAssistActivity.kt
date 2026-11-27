package com.example.ai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.example.MainActivity

/**
 * Allows Android to list Neli AI as an eligible Mobile Assistant (`ACTION_ASSIST`).
 * When invoked by long-pressing Home / Assistant gesture, wakes Neli AI overlay directly
 * (or launches NeliTv with voice listening if overlay permission isn't granted yet).
 */
class NeliAiAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NeliDigitalAssistantHelper.wakeNeliAiFromSystemTrigger(this)
        finish()
    }

    companion object {
        const val EXTRA_START_NELI_AI_VOICE = "neli_ai_start_voice_from_assist"
    }
}
