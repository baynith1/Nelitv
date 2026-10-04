package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

/**
 * Suppresses benign ExoPlayer / Media3 logs in production.
 */
object NativeLogSuppressor {

    @Volatile
    private var initialized = false

    @OptIn(UnstableApi::class)
    fun suppressNonFatalNativeLogs() {
        if (initialized) return
        initialized = true
        try {
            androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        } catch (_: Throwable) {
        }
    }
}

