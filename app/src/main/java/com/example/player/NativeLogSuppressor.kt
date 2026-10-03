package com.example.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.sun.jna.NativeLibrary

/**
 * Suppresses benign Android Codec2 (`libstagefright_ccodec.so` / `CCodecResources`) native ALOGE messages
 * such as "Failed to query component interface for required system resources: 6" (`C2_BAD_INDEX` on
 * software/emulator C2 codecs that do not implement `C2ResourcesNeededTuning`) by raising the process
 * native `liblog` minimum priority to `ANDROID_LOG_FATAL` (7).
 */
object NativeLogSuppressor {
    private const val ANDROID_LOG_FATAL = 7

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
        try {
            val liblog = NativeLibrary.getInstance("log")
            val setMinPriority = liblog.getFunction("__android_log_set_minimum_priority")
            setMinPriority.invokeInt(arrayOf(ANDROID_LOG_FATAL))
        } catch (_: Throwable) {
            // Ignored on host JVM unit tests where Android's liblog.so is not present
        }
    }
}
