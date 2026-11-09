package com.example.ui.screens

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesizes a signature cinematic intro audio ("Ta-Dum!" cinematic bass impact and neon shimmer)
 * in NeliTv's own style using native Android AudioTrack PCM generation.
 * Plays zero-latency, studio-quality sound on app launch without needing external audio files.
 */
object NeliIntroAudioSynthesizer {

    private const val TAG = "NeliIntroAudio"
    private const val SAMPLE_RATE = 44100

    suspend fun playIntroChime() = withContext(Dispatchers.Default) {
        try {
            val durationSeconds = 1.6f
            val numSamples = (SAMPLE_RATE * durationSeconds).toInt()
            val buffer = ShortArray(numSamples)

            // Beat 1: Initial "Ta" impact at t = 0.0s (Deep resonant punch)
            // Beat 2: Climax "DUMMM" cinematic chord at t = 0.18s (Sub-bass drop + neon crystalline chime)
            val beat2SampleOffset = (SAMPLE_RATE * 0.18f).toInt()

            for (i in 0 until numSamples) {
                val t = i.toFloat() / SAMPLE_RATE
                var sample = 0.0

                // 1. First Impact: "Ta" (t: 0.0s - 0.25s)
                if (t < 0.25f) {
                    val envelope1 = exp((-t * 14.0).toDouble())
                    val bass1 = sin(2.0 * PI * 75.0 * t)
                    val click = sin(2.0 * PI * 320.0 * t) * 0.4
                    sample += (bass1 + click) * envelope1 * 0.55
                }

                // 2. Second Impact: "DUMMMM" (t >= 0.18s)
                if (i >= beat2SampleOffset) {
                    val t2 = (i - beat2SampleOffset).toFloat() / SAMPLE_RATE
                    val envelope2 = exp((-t2 * 2.6).toDouble())

                    // Deep cinema sub-bass impact (60Hz sliding smoothly to 48Hz)
                    val freq = 60.0 - (t2 * 8.0).coerceAtMost(12.0)
                    val subBass = sin(2.0 * PI * freq * t2)

                    // Cinematic warm octave body (120Hz & 240Hz)
                    val midBody = sin(2.0 * PI * 120.0 * t2) * 0.5 +
                        sin(2.0 * PI * 240.0 * t2) * 0.25

                    // Neon futuristic shimmer chime (440Hz, 880Hz, 1320Hz)
                    val shimmer = (sin(2.0 * PI * 440.0 * t2) * 0.25 +
                        sin(2.0 * PI * 880.0 * t2) * 0.15 +
                        sin(2.0 * PI * 1320.0 * t2) * 0.08) * exp((-t2 * 4.0).toDouble())

                    sample += (subBass * 0.65 + midBody * 0.35 + shimmer) * envelope2
                }

                // Soft limiter / saturation to prevent digital clipping
                val clamped = sample.coerceIn(-1.0, 1.0)
                buffer[i] = (clamped * 32767.0).toInt().toShort()
            }

            val minBufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val trackBufferSize = maxOf(buffer.size * 2, minBufferSize)

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build()

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Release after playback finishes
            withContext(Dispatchers.IO) {
                Thread.sleep((durationSeconds * 1000).toLong() + 100L)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.d(TAG, "Intro audio playback skipped: ${e.message}")
        }
    }
}
