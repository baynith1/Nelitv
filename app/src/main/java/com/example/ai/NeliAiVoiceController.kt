package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Siri-Grade Low-Latency Voice I/O Controller for Neli AI on Android Mobile, Tablet & Smart TV.
 *
 * Key upgrades:
 * - Energetic Young Fast Female Voice (`Locale.US`, pitch `1.22f`, speech rate `1.28f`,
 *   neural local female voice selection + silent TTS pre-warm for near-zero first-reply latency).
 * - Two-Stage Quiet Siri Microphone:
 *   - Stage 1 (Standby): Quiet on-device `AudioRecord` PCM voice-activity energy monitor with
 *     hardware `NoiseSuppressor` + `AcousticEchoCanceler` — ZERO every-second `SpeechRecognizer`
 *     restarts, ZERO beeps, and ZERO UI flicker until real speech is heard!
 *   - Stage 2 (Wake & One-Shot Capture): Wakes `SpeechRecognizer` ONLY when speech energy fires,
 *     verifies "Hey Neli" / "Neli", captures the request quickly (`700ms` silence cutoff), and
 *     IMMEDIATELY shuts down the mic while executing & replying.
 * - Exact Audio-to-Caption Sync: [onSpeechStarted] and [NeliAiVoiceState.Speaking] emit STRICTLY
 *   inside [UtteranceProgressListener.onStart] so captions never appear before Neli's voice starts.
 */
class NeliAiVoiceController(
    private val context: Context,
    private val onRecognizedQuery: (String) -> Unit,
    private val onWakeWordDetected: (chainedCommand: String?) -> Unit = {},
    private val onCommandWindowExpired: () -> Unit = {},
    private val onSpeechStarted: (captionText: String) -> Unit = {},
    private val onSpeechFinished: () -> Unit = {}
) {
    companion object {
        // Young energetic fast-speaking female voice tuning
        const val NELI_VOICE_PITCH = 1.22f
        const val NELI_VOICE_SPEECH_RATE = 1.28f
        const val NELI_UTTERANCE_ID = "neli"
        private const val PREWARM_UTTERANCE_ID = "neli_prewarm"
        const val DEFAULT_COMMAND_WINDOW_MS = 30_000L
        const val MANUAL_TAP_COMMAND_WINDOW_MS = 30_000L

        private const val VAD_SAMPLE_RATE = 16_000
        private const val VAD_RMS_THRESHOLD = 1450.0
        private const val VAD_REQUIRED_CONSECUTIVE_FRAMES = 2
        private const val VAD_COOLDOWN_AFTER_FALSE_WAKE_MS = 1_400L

        fun isHardwareVoiceCancellationAvailable(): Boolean {
            return runCatching {
                AcousticEchoCanceler.isAvailable() || NoiseSuppressor.isAvailable()
            }.getOrDefault(false)
        }

        /**
         * Resolves a real system [RecognitionService] (e.g., Google Speech Services / Samsung / AOSP)
         * and strictly excludes NeliTv's own [NeliRecognitionService] stub so [SpeechRecognizer] never
         * binds to itself and fails with ERROR_NO_MATCH on phones or emulators.
         */
        fun resolveExternalSpeechComponent(context: Context): ComponentName? {
            return runCatching {
                val intent = Intent(RecognitionService.SERVICE_INTERFACE)
                @Suppress("DEPRECATION")
                val services = context.packageManager.queryIntentServices(intent, 0)
                val external = services.filter { info ->
                    val svc = info.serviceInfo ?: return@filter false
                    svc.packageName != context.packageName
                }
                val preferred = external.firstOrNull { info ->
                    val pkg = info.serviceInfo.packageName.lowercase(Locale.US)
                    pkg.contains("google") || pkg.contains("tts") || pkg.contains("speech")
                } ?: external.firstOrNull()
                preferred?.serviceInfo?.let { svc ->
                    ComponentName(svc.packageName, svc.name)
                }
            }.getOrNull()
        }

        /**
         * Ranks installed local offline English female voices to pick the brightest, most natural
         * youthful female neural voice (excluding deep/male profiles).
         */
        internal fun pickBestEnergeticFemaleVoice(voices: Set<Voice>?): Voice? {
            if (voices.isNullOrEmpty()) return null
            val candidates = voices.filter { v ->
                val lang = v.locale?.language.orEmpty()
                lang.equals("en", ignoreCase = true) && !v.isNetworkConnectionRequired
            }
            if (candidates.isEmpty()) return null
            return candidates.maxByOrNull { v ->
                val n = v.name.lowercase(Locale.ROOT)
                var score = 0
                if (v.locale?.country.equals("US", ignoreCase = true)) score += 30
                else if (v.locale?.country.equals("GB", ignoreCase = true)) score += 18
                if (v.quality >= Voice.QUALITY_HIGH) score += 25
                if (v.latency <= Voice.LATENCY_NORMAL) score += 10
                when {
                    n.contains("en-us-x-tpc") || n.contains("tpc") -> score += 65
                    n.contains("en-us-x-tpf") || n.contains("tpf") -> score += 62
                    n.contains("en-us-x-iob") || n.contains("iob") -> score += 56
                    n.contains("en-us-x-sfg") || n.contains("sfg") -> score += 52
                    n.contains("en-us-x-iog") || n.contains("iog") -> score += 48
                    n.contains("en-gb-x-gba") || n.contains("gba") -> score += 44
                    n.contains("female") -> score += 40
                }
                if (n.contains("male") && !n.contains("female")) score -= 80
                if (n.contains("iom") || n.contains("tpd") || n.contains("iol") || n.contains("gbb")) score -= 75
                score
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasTransientAudioFocus = false

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var isReleased = false
    private var consecutiveSilentErrors = 0
    private var speakingBeatTick = 0

    // Stage-1 quiet PCM voice-activity monitor (prevents every-second SpeechRecognizer wakeups)
    private val vadRunning = AtomicBoolean(false)
    @Volatile
    private var vadThread: Thread? = null
    @Volatile
    private var lastFalseWakeElapsedMs: Long = 0L

    // Exact audio-to-caption synchronization state
    @Volatile
    private var pendingDisplayCaption: String = ""
    @Volatile
    private var pendingListenFollowUpAfterSpeech: Boolean = false
    @Volatile
    private var speechCaptionDispatched: Boolean = false

    @Volatile
    var passiveWakeWordGateEnabled: Boolean = false

    @Volatile
    var isInActiveCommandWindow: Boolean = false
        private set

    @Volatile
    private var activeCommandWindowDeadlineElapsedMs: Long = 0L

    private val _voiceState = MutableStateFlow<NeliAiVoiceState>(NeliAiVoiceState.Idle)
    val voiceState: StateFlow<NeliAiVoiceState> = _voiceState.asStateFlow()

    private val _isSpeechAvailable = MutableStateFlow(false)
    val isSpeechAvailable: StateFlow<Boolean> = _isSpeechAvailable.asStateFlow()

    var handsFreeActive: Boolean = false
        set(value) {
            field = value
            if (!value) {
                stopQuietVadMonitor()
                cancelPendingRelisten()
                cancelCommandTimeout()
                if (_voiceState.value is NeliAiVoiceState.HandsFreeStandby) {
                    _voiceState.value = NeliAiVoiceState.Idle
                }
            }
        }

    var voiceReplyEnabled: Boolean = true
    var voiceCancellationEnabled: Boolean = true
    var pauseExternalAppsOnWake: Boolean = false

    private val relistenRunnable = Runnable {
        if (!isReleased && handsFreeActive && !isInActiveCommandWindow) {
            enterQuietStandbyOrFallback()
        }
    }

    private val commandWindowTimeoutRunnable = Runnable {
        if (isReleased || !isInActiveCommandWindow) return@Runnable
        isInActiveCommandWindow = false
        shutdownActiveMicNow()
        abandonTransientAudioFocus()
        _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
        onCommandWindowExpired()
        if (handsFreeActive) {
            scheduleHandsFreeStandby(700L)
        }
    }

    private val speakingBeatRunnable = object : Runnable {
        override fun run() {
            if (isReleased) return
            val current = _voiceState.value
            if (current is NeliAiVoiceState.Speaking) {
                speakingBeatTick++
                val wave = (0.55f + 0.45f * sin(speakingBeatTick * 0.85).toFloat()).coerceIn(0.18f, 1.0f)
                _voiceState.value = current.copy(beatAmplitude = wave)
                mainHandler.postDelayed(this, 65L)
            }
        }
    }

    init {
        _isSpeechAvailable.value = resolveExternalSpeechComponent(context) != null || runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)
        initTts()
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS && !isReleased) {
                    val engine = tts ?: return@TextToSpeech
                    engine.language = Locale.US
                    runCatching {
                        pickBestEnergeticFemaleVoice(engine.voices)?.let { best ->
                            engine.voice = best
                        }
                    }
                    // Young energetic fast-speaking female voice profile
                    engine.setPitch(NELI_VOICE_PITCH)
                    engine.setSpeechRate(NELI_VOICE_SPEECH_RATE)
                    engine.setOnUtteranceProgressListener(createUnifiedTtsListener())
                    isTtsReady = true
                    // Pre-warm audio pipeline silently so first spoken response has near-zero delay
                    runCatching {
                        engine.playSilentUtterance(1L, TextToSpeech.QUEUE_FLUSH, PREWARM_UTTERANCE_ID)
                    }
                }
            }
        }
    }

    private fun createUnifiedTtsListener(): UtteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            if (utteranceId == PREWARM_UTTERANCE_ID) return
            mainHandler.post {
                if (isReleased) return@post
                val caption = pendingDisplayCaption
                speechCaptionDispatched = true
                _voiceState.value = NeliAiVoiceState.Speaking(caption, beatAmplitude = 0.76f)
                if (caption.isNotBlank()) {
                    onSpeechStarted(caption)
                }
                mainHandler.removeCallbacks(speakingBeatRunnable)
                mainHandler.post(speakingBeatRunnable)
            }
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId == PREWARM_UTTERANCE_ID) return
            mainHandler.post {
                mainHandler.removeCallbacks(speakingBeatRunnable)
                if (isReleased) return@post
                val shouldListenFollowUp = pendingListenFollowUpAfterSpeech
                pendingListenFollowUpAfterSpeech = false
                if (!speechCaptionDispatched && pendingDisplayCaption.isNotBlank()) {
                    speechCaptionDispatched = true
                    onSpeechStarted(pendingDisplayCaption)
                }
                if (shouldListenFollowUp) {
                    startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                    return@post
                }
                if (!isInActiveCommandWindow) {
                    abandonTransientAudioFocus()
                }
                onSpeechFinished()
                if (handsFreeActive) {
                    _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                    scheduleHandsFreeStandby(500L)
                } else if (!isInActiveCommandWindow) {
                    _voiceState.value = NeliAiVoiceState.Idle
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            if (utteranceId == PREWARM_UTTERANCE_ID) return
            mainHandler.post {
                mainHandler.removeCallbacks(speakingBeatRunnable)
                if (isReleased) return@post
                val shouldListenFollowUp = pendingListenFollowUpAfterSpeech
                pendingListenFollowUpAfterSpeech = false
                if (!speechCaptionDispatched && pendingDisplayCaption.isNotBlank()) {
                    speechCaptionDispatched = true
                    onSpeechStarted(pendingDisplayCaption)
                }
                if (shouldListenFollowUp) {
                    startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                    return@post
                }
                abandonTransientAudioFocus()
                onSpeechFinished()
                if (handsFreeActive) {
                    _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                    scheduleHandsFreeStandby(700L)
                } else {
                    _voiceState.value = NeliAiVoiceState.Idle
                }
            }
        }
    }

    fun acquireTransientAudioFocus() {
        if (hasTransientAudioFocus) return
        runCatching {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener { }
                    .build()
                audioFocusRequest = req
                val res = am.requestAudioFocus(req)
                hasTransientAudioFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val res = am.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
                hasTransientAudioFocus = res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }
    }

    fun abandonTransientAudioFocus() {
        if (!hasTransientAudioFocus) return
        hasTransientAudioFocus = false
        runCatching {
            val am = audioManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    /**
     * Starts an active command window (e.g., after "Hey Neli", tap, or power-button wake).
     * Stops quiet standby monitor first so SpeechRecognizer gets exclusive mic cleanly.
     */
    fun startActiveCommandWindow(timeoutMs: Long = DEFAULT_COMMAND_WINDOW_MS) {
        consecutiveSilentErrors = 0
        stopQuietVadMonitor()
        cancelPendingRelisten()
        isInActiveCommandWindow = true
        activeCommandWindowDeadlineElapsedMs = SystemClock.elapsedRealtime() + timeoutMs
        if (pauseExternalAppsOnWake) {
            acquireTransientAudioFocus()
        }
        scheduleCommandTimeout(timeoutMs)
        mainHandler.post {
            stopSpeaking()
            startListeningInternal(isPassiveWakeScan = false)
        }
    }

    /**
     * Enters Siri-style quiet hands-free standby: does NOT spam SpeechRecognizer every second.
     * Listens silently via local PCM voice-activity energy gate and only opens SpeechRecognizer
     * when real human speech occurs.
     */
    fun startPassiveWakeListening() {
        passiveWakeWordGateEnabled = true
        handsFreeActive = true
        isInActiveCommandWindow = false
        activeCommandWindowDeadlineElapsedMs = 0L
        cancelCommandTimeout()
        mainHandler.post {
            if (!isReleased) {
                shutdownActiveMicNow()
                _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                enterQuietStandbyOrFallback()
            }
        }
    }

    fun startListening() {
        startActiveCommandWindow(timeoutMs = DEFAULT_COMMAND_WINDOW_MS)
    }

    fun stopListening() {
        stopQuietVadMonitor()
        cancelPendingRelisten()
        cancelCommandTimeout()
        isInActiveCommandWindow = false
        activeCommandWindowDeadlineElapsedMs = 0L
        abandonTransientAudioFocus()
        mainHandler.post {
            shutdownActiveMicNow()
            if (_voiceState.value is NeliAiVoiceState.Listening) {
                _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
            }
        }
    }

    /**
     * Speaks [text] with Neli's energetic fast female voice.
     * Guarantees [displayCaption] is emitted strictly when audio playback starts (`onStart`),
     * eliminating caption-before-voice lag!
     */
    fun speak(
        text: String,
        listenForFollowUpAfter: Boolean = false,
        displayCaption: String = text
    ) {
        val cleanSpeech = text.trim()
        val cleanCaption = displayCaption.trim().ifBlank { cleanSpeech }
        cancelCommandTimeout()
        stopQuietVadMonitor()

        if (cleanSpeech.isBlank() || !voiceReplyEnabled || !isTtsReady || isReleased) {
            if (cleanCaption.isNotBlank() && !isReleased) {
                mainHandler.post { onSpeechStarted(cleanCaption) }
            }
            if (listenForFollowUpAfter && !isReleased) {
                startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                return
            }
            abandonTransientAudioFocus()
            onSpeechFinished()
            if (handsFreeActive && !isReleased) {
                _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                scheduleHandsFreeStandby(600L)
            } else {
                _voiceState.value = NeliAiVoiceState.Idle
            }
            return
        }

        mainHandler.post {
            if (isReleased) return@post
            cancelPendingRelisten()
            // Immediately shut down mic before speaking so mic is completely OFF during reply
            shutdownActiveMicNow()
            if (pauseExternalAppsOnWake) {
                acquireTransientAudioFocus()
            }
            pendingDisplayCaption = cleanCaption
            pendingListenFollowUpAfterSpeech = listenForFollowUpAfter
            speechCaptionDispatched = false
            if (listenForFollowUpAfter) {
                isInActiveCommandWindow = true
            }
            runCatching {
                val engine = tts ?: error("TTS unavailable")
                engine.setPitch(NELI_VOICE_PITCH)
                engine.setSpeechRate(NELI_VOICE_SPEECH_RATE)
                engine.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, NELI_UTTERANCE_ID)
            }.onFailure {
                mainHandler.removeCallbacks(speakingBeatRunnable)
                if (!speechCaptionDispatched && cleanCaption.isNotBlank()) {
                    speechCaptionDispatched = true
                    onSpeechStarted(cleanCaption)
                }
                abandonTransientAudioFocus()
                _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
            }
        }
    }

    fun stopSpeaking() {
        pendingListenFollowUpAfterSpeech = false
        mainHandler.removeCallbacks(speakingBeatRunnable)
        runCatching { tts?.stop() }
        if (_voiceState.value is NeliAiVoiceState.Speaking) {
            _voiceState.value = NeliAiVoiceState.Idle
        }
    }

    fun setThinking() {
        cancelCommandTimeout()
        stopQuietVadMonitor()
        isInActiveCommandWindow = false
        shutdownActiveMicNow()
        _voiceState.value = NeliAiVoiceState.Thinking
    }

    fun release() {
        isReleased = true
        handsFreeActive = false
        isInActiveCommandWindow = false
        stopQuietVadMonitor()
        cancelPendingRelisten()
        cancelCommandTimeout()
        mainHandler.removeCallbacks(speakingBeatRunnable)
        abandonTransientAudioFocus()
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
        }
    }

    private fun shutdownActiveMicNow() {
        runCatching { speechRecognizer?.stopListening() }
        runCatching { speechRecognizer?.cancel() }
    }

    /**
     * Stage 1 of the Two-Stage Siri Wake Engine:
     * Runs a lightweight local PCM energy detector (`AudioRecord`) so Android's SpeechRecognizer
     * does NOT beep or blink every second in silence.
     */
    private fun enterQuietStandbyOrFallback() {
        if (isReleased || !handsFreeActive || isInActiveCommandWindow) return
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasMic) return
        if (resolveExternalSpeechComponent(context) == null) return

        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
        if (!startQuietVadMonitor()) {
            // Emulator / hardware fallback where AudioRecord is unavailable:
            // Use gentle backoff instead of spamming SpeechRecognizer every 550ms
            startListeningInternal(isPassiveWakeScan = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startQuietVadMonitor(): Boolean {
        if (vadRunning.get() || isReleased || !handsFreeActive || isInActiveCommandWindow) return true
        val minBuf = AudioRecord.getMinBufferSize(
            VAD_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return false

        val bufferSize = maxOf(minBuf, VAD_SAMPLE_RATE / 5) // ~100ms chunks
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                VAD_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull() ?: return false

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            return false
        }

        vadRunning.set(true)
        vadThread = thread(isDaemon = true, name = "NeliQuietWakeGate") {
            var ns: NoiseSuppressor? = null
            var aec: AcousticEchoCanceler? = null
            val pcm = ShortArray(1024)
            var hotFrames = 0
            try {
                if (voiceCancellationEnabled) {
                    runCatching {
                        if (NoiseSuppressor.isAvailable()) {
                            ns = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
                        }
                    }
                    runCatching {
                        if (AcousticEchoCanceler.isAvailable()) {
                            aec = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
                        }
                    }
                }
                record.startRecording()
                while (vadRunning.get() && !isReleased && handsFreeActive && !isInActiveCommandWindow) {
                    val read = record.read(pcm, 0, pcm.size)
                    if (read <= 0) {
                        Thread.sleep(40L)
                        continue
                    }
                    if (SystemClock.elapsedRealtime() - lastFalseWakeElapsedMs < VAD_COOLDOWN_AFTER_FALSE_WAKE_MS) {
                        hotFrames = 0
                        continue
                    }
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val s = pcm[i].toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read)
                    if (rms >= VAD_RMS_THRESHOLD) {
                        hotFrames++
                        if (hotFrames >= VAD_REQUIRED_CONSECUTIVE_FRAMES) {
                            // Real human speech detected -> hand off cleanly to Stage 2 wake ASR!
                            vadRunning.set(false)
                            break
                        }
                    } else {
                        hotFrames = 0
                    }
                }
            } catch (_: Throwable) {
                vadRunning.set(false)
            } finally {
                runCatching { ns?.release() }
                runCatching { aec?.release() }
                runCatching { record.stop() }
                runCatching { record.release() }
            }

            if (!isReleased && handsFreeActive && !isInActiveCommandWindow && hotFrames >= VAD_REQUIRED_CONSECUTIVE_FRAMES) {
                mainHandler.post {
                    if (!isReleased && handsFreeActive && !isInActiveCommandWindow) {
                        startListeningInternal(isPassiveWakeScan = true)
                    }
                }
            }
        }
        return true
    }

    private fun stopQuietVadMonitor() {
        vadRunning.set(false)
        vadThread = null
    }

    private fun startListeningInternal(isPassiveWakeScan: Boolean) {
        if (isReleased) return
        val externalComponent = resolveExternalSpeechComponent(context)
        if (externalComponent == null) {
            _isSpeechAvailable.value = false
            if (!isPassiveWakeScan) {
                isInActiveCommandWindow = false
                cancelCommandTimeout()
                abandonTransientAudioFocus()
                _voiceState.value = NeliAiVoiceState.Notice(
                    "No microphone speech engine detected here (common in emulators). Install on a real phone/TV or tap a quick command."
                )
            }
            return
        }
        _isSpeechAvailable.value = true

        runCatching {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context, externalComponent).apply {
                    setRecognitionListener(createListener())
                }
            } else {
                speechRecognizer?.cancel()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.US.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Ultra-snappy Siri silence thresholds for instant response turnaround!
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isPassiveWakeScan) 600L else 700L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isPassiveWakeScan) 420L else 500L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                    if (isPassiveWakeScan) 650L else 900L
                )
                if (voiceCancellationEnabled) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                }
            }
            if (!isPassiveWakeScan) {
                _voiceState.value = NeliAiVoiceState.Listening(rmsDb = 2.0f, partialText = "")
            } else {
                _voiceState.value = NeliAiVoiceState.HandsFreeStandby
            }
            speechRecognizer?.startListening(intent)
        }.onFailure {
            _voiceState.value = NeliAiVoiceState.Notice("Could not start microphone listening.")
        }
    }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isInActiveCommandWindow || !passiveWakeWordGateEnabled) {
                _voiceState.value = NeliAiVoiceState.Listening(rmsDb = 2f, partialText = "")
            }
        }

        override fun onBeginningOfSpeech() {
            consecutiveSilentErrors = 0
            if (isInActiveCommandWindow) {
                val now = SystemClock.elapsedRealtime()
                val remaining = activeCommandWindowDeadlineElapsedMs - now
                if (remaining < 8_000L) {
                    activeCommandWindowDeadlineElapsedMs = now + 8_000L
                    scheduleCommandTimeout(8_000L)
                }
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val current = _voiceState.value
            if (current is NeliAiVoiceState.Listening) {
                _voiceState.value = current.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (isInActiveCommandWindow || !passiveWakeWordGateEnabled) {
                _voiceState.value = NeliAiVoiceState.Thinking
            }
        }

        override fun onError(error: Int) {
            if (isReleased) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (isInActiveCommandWindow) {
                        val remainingMs = activeCommandWindowDeadlineElapsedMs - SystemClock.elapsedRealtime()
                        if (remainingMs > 1_200L) {
                            _voiceState.value = NeliAiVoiceState.Listening(rmsDb = 2.0f, partialText = "")
                            mainHandler.postDelayed({
                                if (!isReleased && isInActiveCommandWindow) {
                                    startListeningInternal(isPassiveWakeScan = false)
                                }
                            }, 180L)
                            return
                        }
                        cancelCommandTimeout()
                        isInActiveCommandWindow = false
                        activeCommandWindowDeadlineElapsedMs = 0L
                        shutdownActiveMicNow()
                        abandonTransientAudioFocus()
                        _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
                        onCommandWindowExpired()
                        if (handsFreeActive) {
                            scheduleHandsFreeStandby(700L)
                        }
                    } else if (handsFreeActive) {
                        // Stage 2 wake check timed out -> shut down SpeechRecognizer and return to QUIET Stage 1!
                        consecutiveSilentErrors++
                        lastFalseWakeElapsedMs = SystemClock.elapsedRealtime()
                        shutdownActiveMicNow()
                        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                        scheduleHandsFreeStandby(600L)
                    } else {
                        _voiceState.value = NeliAiVoiceState.Idle
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    handsFreeActive = false
                    isInActiveCommandWindow = false
                    stopQuietVadMonitor()
                    cancelCommandTimeout()
                    abandonTransientAudioFocus()
                    _voiceState.value = NeliAiVoiceState.Notice("Please allow Microphone permission in NeliTv AI Settings.")
                }
                else -> {
                    val wasManualWindow = isInActiveCommandWindow
                    if (isInActiveCommandWindow) {
                        isInActiveCommandWindow = false
                        cancelCommandTimeout()
                        abandonTransientAudioFocus()
                        onCommandWindowExpired()
                    }
                    shutdownActiveMicNow()
                    if (wasManualWindow && !handsFreeActive) {
                        val reason = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "Microphone audio unavailable on this device/emulator."
                            SpeechRecognizer.ERROR_NETWORK,
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Voice recognition needs an internet connection."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Microphone is busy — tap again in a second."
                            else -> "Couldn't reach voice engine — test on a real device or tap a quick command."
                        }
                        _voiceState.value = NeliAiVoiceState.Notice(reason)
                    } else if (handsFreeActive) {
                        lastFalseWakeElapsedMs = SystemClock.elapsedRealtime()
                        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                        scheduleHandsFreeStandby(1_200L)
                    } else {
                        _voiceState.value = NeliAiVoiceState.Idle
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (isReleased) return
            consecutiveSilentErrors = 0
            // Immediately shut down active mic right after receiving final speech result (true Siri behavior!)
            shutdownActiveMicNow()

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val topTranscript = matches.firstOrNull()?.trim().orEmpty()
            if (topTranscript.isBlank()) {
                if (isInActiveCommandWindow) {
                    isInActiveCommandWindow = false
                    cancelCommandTimeout()
                    abandonTransientAudioFocus()
                    onCommandWindowExpired()
                }
                _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
                if (handsFreeActive) scheduleHandsFreeStandby(700L)
                return
            }

            // Stage 2 passive wake-word verification (filters out movie / TV / ambient speech)
            if (passiveWakeWordGateEnabled && !isInActiveCommandWindow) {
                val wakeCandidate = matches.firstOrNull { NeliAiEngine.containsWakeWord(it) }
                if (wakeCandidate == null) {
                    // Not "Hey Neli" / "Neli" -> silently return to quiet Stage-1 standby!
                    lastFalseWakeElapsedMs = SystemClock.elapsedRealtime()
                    _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                    if (handsFreeActive) scheduleHandsFreeStandby(400L)
                    return
                }
                val chained = NeliAiEngine.stripWakeWord(wakeCandidate)
                if (pauseExternalAppsOnWake) {
                    acquireTransientAudioFocus()
                }
                if (chained.isBlank()) {
                    onWakeWordDetected(null)
                    onRecognizedQuery(wakeCandidate)
                } else {
                    onWakeWordDetected(chained)
                    _voiceState.value = NeliAiVoiceState.Thinking
                    onRecognizedQuery(wakeCandidate)
                }
                return
            }

            // Active command window received a spoken command -> close mic & process immediately!
            cancelCommandTimeout()
            isInActiveCommandWindow = false
            _voiceState.value = NeliAiVoiceState.Thinking
            onRecognizedQuery(topTranscript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (partial.isBlank()) return

            // Instant wake-word early detection during Stage 2 scan
            if (passiveWakeWordGateEnabled && !isInActiveCommandWindow && NeliAiEngine.containsWakeWord(partial)) {
                if (pauseExternalAppsOnWake) {
                    acquireTransientAudioFocus()
                }
                onWakeWordDetected(NeliAiEngine.stripWakeWord(partial).ifBlank { null })
                _voiceState.value = NeliAiVoiceState.Listening(rmsDb = 6f, partialText = partial)
                return
            }

            val current = _voiceState.value
            if (current is NeliAiVoiceState.Listening) {
                _voiceState.value = current.copy(partialText = partial)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun scheduleHandsFreeStandby(delayMs: Long) {
        cancelPendingRelisten()
        mainHandler.postDelayed(relistenRunnable, delayMs)
    }

    private fun cancelPendingRelisten() {
        mainHandler.removeCallbacks(relistenRunnable)
    }

    private fun scheduleCommandTimeout(delayMs: Long) {
        cancelCommandTimeout()
        mainHandler.postDelayed(commandWindowTimeoutRunnable, delayMs)
    }

    private fun cancelCommandTimeout() {
        mainHandler.removeCallbacks(commandWindowTimeoutRunnable)
    }
}
