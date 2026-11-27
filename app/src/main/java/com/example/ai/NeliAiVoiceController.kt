package com.example.ai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.sin

/**
 * Production Hands-Free Voice I/O Controller for Neli AI on Android Mobile, Tablet & Smart TV.
 *
 * Features:
 * - Free 10-14 yr Female English Voice (`Locale.US`, pitch `1.28f`, speech rate `1.04f`, utteranceId `"neli"`).
 * - Wake-word gating ("Hey Neli" / "Neli") + hardware AcousticEchoCanceler / NoiseSuppressor awareness
 *   so movie & live TV audio does not falsely trigger Neli AI.
 * - 5-second command timeout window (auto-unmutes player / resumes paused external audio apps if no command).
 * - Transient Exclusive AudioFocus control to pause other media apps (Spotify, YouTube, music) while listening,
 *   and automatically release focus so they resume seamlessly afterward.
 * - Live speaking beat pulse emitter so the Pink AI visual dances in rhythm with Neli's voice.
 */
class NeliAiVoiceController(
    private val context: Context,
    private val onRecognizedQuery: (String) -> Unit,
    private val onWakeWordDetected: (chainedCommand: String?) -> Unit = {},
    private val onCommandWindowExpired: () -> Unit = {},
    private val onSpeechFinished: () -> Unit = {}
) {
    companion object {
        const val NELI_VOICE_PITCH = 1.28f
        const val NELI_VOICE_SPEECH_RATE = 1.04f
        const val NELI_UTTERANCE_ID = "neli"
        const val DEFAULT_COMMAND_WINDOW_MS = 30_000L
        const val MANUAL_TAP_COMMAND_WINDOW_MS = 30_000L

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

    /**
     * When true, passive standby listening ignores background TV/movie speech unless
     * the wake word ("Hey Neli" / "Neli") is explicitly spoken!
     */
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
        if (!isReleased && handsFreeActive) {
            startListeningInternal(isPassiveWakeScan = passiveWakeWordGateEnabled && !isInActiveCommandWindow)
        }
    }

    private val commandWindowTimeoutRunnable = Runnable {
        if (isReleased || !isInActiveCommandWindow) return@Runnable
        isInActiveCommandWindow = false
        runCatching { speechRecognizer?.cancel() }
        abandonTransientAudioFocus()
        _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
        onCommandWindowExpired()
        if (handsFreeActive) {
            scheduleHandsFreeRelisten(900L)
        }
    }

    private val speakingBeatRunnable = object : Runnable {
        override fun run() {
            if (isReleased) return
            val current = _voiceState.value
            if (current is NeliAiVoiceState.Speaking) {
                speakingBeatTick++
                val wave = (0.55f + 0.45f * sin(speakingBeatTick * 0.75).toFloat()).coerceIn(0.15f, 1.0f)
                _voiceState.value = current.copy(beatAmplitude = wave)
                mainHandler.postDelayed(this, 75L)
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
                        val preferredFemale = engine.voices
                            ?.filter { v ->
                                v.locale.language.equals("en", ignoreCase = true) &&
                                    v.locale.country.equals("US", ignoreCase = true) &&
                                    !v.isNetworkConnectionRequired
                            }
                            ?.firstOrNull { v ->
                                val lower = v.name.lowercase(Locale.ROOT)
                                lower.contains("female") ||
                                    lower.contains("sfg") ||
                                    lower.contains("tpf") ||
                                    lower.contains("iob") ||
                                    lower.contains("en-us-x-sfg") ||
                                    lower.contains("en-us-x-tpf")
                            }
                        if (preferredFemale != null) {
                            engine.voice = preferredFemale
                        }
                    }
                    // Free 10-14 yr Female English Voice profile
                    engine.setPitch(NELI_VOICE_PITCH)
                    engine.setSpeechRate(NELI_VOICE_SPEECH_RATE)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post {
                                if (!isReleased) {
                                    mainHandler.removeCallbacks(speakingBeatRunnable)
                                    mainHandler.post(speakingBeatRunnable)
                                }
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                mainHandler.removeCallbacks(speakingBeatRunnable)
                                if (isReleased) return@post
                                if (!isInActiveCommandWindow) {
                                    abandonTransientAudioFocus()
                                }
                                onSpeechFinished()
                                if (handsFreeActive) {
                                    _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                                    scheduleHandsFreeRelisten(650L)
                                } else if (!isInActiveCommandWindow) {
                                    _voiceState.value = NeliAiVoiceState.Idle
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                mainHandler.removeCallbacks(speakingBeatRunnable)
                                if (isReleased) return@post
                                abandonTransientAudioFocus()
                                onSpeechFinished()
                                _voiceState.value = if (handsFreeActive) {
                                    NeliAiVoiceState.HandsFreeStandby
                                } else {
                                    NeliAiVoiceState.Idle
                                }
                            }
                        }
                    })
                    isTtsReady = true
                }
            }
        }
    }

    /**
     * Pauses music/video playing in other apps (Spotify, YouTube, WhatsApp audio, etc.)
     * while Neli AI listens for a command.
     */
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

    /**
     * Releases transient AudioFocus so paused music/video apps resume automatically.
     */
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
     * Starts an active 30-second command window (e.g., after "Hey Neli" or manual trigger).
     * If no command arrives within [timeoutMs], [onCommandWindowExpired] fires automatically.
     */
    fun startActiveCommandWindow(timeoutMs: Long = DEFAULT_COMMAND_WINDOW_MS) {
        consecutiveSilentErrors = 0
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

    fun startPassiveWakeListening() {
        passiveWakeWordGateEnabled = true
        handsFreeActive = true
        isInActiveCommandWindow = false
        activeCommandWindowDeadlineElapsedMs = 0L
        cancelCommandTimeout()
        mainHandler.post {
            if (!isReleased) {
                _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                startListeningInternal(isPassiveWakeScan = true)
            }
        }
    }

    fun startListening() {
        startActiveCommandWindow(timeoutMs = DEFAULT_COMMAND_WINDOW_MS)
    }

    fun stopListening() {
        cancelPendingRelisten()
        cancelCommandTimeout()
        isInActiveCommandWindow = false
        activeCommandWindowDeadlineElapsedMs = 0L
        abandonTransientAudioFocus()
        mainHandler.post {
            runCatching { speechRecognizer?.stopListening() }
            runCatching { speechRecognizer?.cancel() }
            if (_voiceState.value is NeliAiVoiceState.Listening) {
                _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
            }
        }
    }

    fun speak(text: String, listenForFollowUpAfter: Boolean = false) {
        val clean = text.trim()
        cancelCommandTimeout()
        if (clean.isBlank() || !voiceReplyEnabled || !isTtsReady || isReleased) {
            if (listenForFollowUpAfter && !isReleased) {
                startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                return
            }
            abandonTransientAudioFocus()
            onSpeechFinished()
            if (handsFreeActive && !isReleased) {
                _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                scheduleHandsFreeRelisten(750L)
            } else {
                _voiceState.value = NeliAiVoiceState.Idle
            }
            return
        }
        mainHandler.post {
            if (isReleased) return@post
            cancelPendingRelisten()
            runCatching { speechRecognizer?.cancel() }
            if (pauseExternalAppsOnWake) {
                acquireTransientAudioFocus()
            }
            _voiceState.value = NeliAiVoiceState.Speaking(clean, beatAmplitude = 0.7f)
            mainHandler.removeCallbacks(speakingBeatRunnable)
            mainHandler.post(speakingBeatRunnable)
            if (listenForFollowUpAfter) {
                isInActiveCommandWindow = true
                // Ensure we begin listening shortly after greeting finishes
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            mainHandler.removeCallbacks(speakingBeatRunnable)
                            if (isReleased) return@post
                            restoreDefaultTtsListener()
                            startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            mainHandler.removeCallbacks(speakingBeatRunnable)
                            if (isReleased) return@post
                            restoreDefaultTtsListener()
                            startActiveCommandWindow(DEFAULT_COMMAND_WINDOW_MS)
                        }
                    }
                })
            }
            runCatching {
                tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, NELI_UTTERANCE_ID)
            }.onFailure {
                mainHandler.removeCallbacks(speakingBeatRunnable)
                abandonTransientAudioFocus()
                _voiceState.value = NeliAiVoiceState.Idle
            }
        }
    }

    private fun restoreDefaultTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    mainHandler.removeCallbacks(speakingBeatRunnable)
                    if (isReleased) return@post
                    abandonTransientAudioFocus()
                    onSpeechFinished()
                    if (handsFreeActive) {
                        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                        scheduleHandsFreeRelisten(650L)
                    } else {
                        _voiceState.value = NeliAiVoiceState.Idle
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    mainHandler.removeCallbacks(speakingBeatRunnable)
                    if (isReleased) return@post
                    abandonTransientAudioFocus()
                    onSpeechFinished()
                    _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
                }
            }
        })
    }

    fun stopSpeaking() {
        mainHandler.removeCallbacks(speakingBeatRunnable)
        runCatching { tts?.stop() }
        if (_voiceState.value is NeliAiVoiceState.Speaking) {
            _voiceState.value = NeliAiVoiceState.Idle
        }
    }

    fun setThinking() {
        cancelCommandTimeout()
        isInActiveCommandWindow = false
        _voiceState.value = NeliAiVoiceState.Thinking
    }

    fun release() {
        isReleased = true
        handsFreeActive = false
        isInActiveCommandWindow = false
        cancelPendingRelisten()
        cancelCommandTimeout()
        mainHandler.removeCallbacks(speakingBeatRunnable)
        abandonTransientAudioFocus()
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
            runCatching { speechRecognizer?.destroy() }
            speechRecognizer = null
            runCatching { tts?.stop() }
            runCatching { tts?.shutdown() }
            tts = null
        }
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
                // Shorter silence window during passive wake-word scan to filter out continuous movie dialogue
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isPassiveWakeScan) 1100L else 1500L
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    if (isPassiveWakeScan) 900L else 1200L
                )
                // Prefer local / voice-optimized recognition if supported
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
                if (remaining < 10_000L) {
                    activeCommandWindowDeadlineElapsedMs = now + 10_000L
                    scheduleCommandTimeout(10_000L)
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
                            // Keep waiting smoothly across the full 30-second window (Android's native recognizer
                            // fires interim timeouts every ~5-7s of silence, so seamlessly restart until deadline!)
                            _voiceState.value = NeliAiVoiceState.Listening(rmsDb = 2.0f, partialText = "")
                            mainHandler.postDelayed({
                                if (!isReleased && isInActiveCommandWindow) {
                                    startListeningInternal(isPassiveWakeScan = false)
                                }
                            }, 220L)
                            return
                        }
                        // Full 30-sec window elapsed with no speech -> expire cleanly & resume audio!
                        cancelCommandTimeout()
                        isInActiveCommandWindow = false
                        activeCommandWindowDeadlineElapsedMs = 0L
                        abandonTransientAudioFocus()
                        _voiceState.value = if (handsFreeActive) NeliAiVoiceState.HandsFreeStandby else NeliAiVoiceState.Idle
                        onCommandWindowExpired()
                        if (handsFreeActive) {
                            scheduleHandsFreeRelisten(1_000L)
                        }
                    } else if (handsFreeActive) {
                        consecutiveSilentErrors++
                        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                        val backoff = (1_000L + (consecutiveSilentErrors.coerceAtMost(4) * 350L))
                        scheduleHandsFreeRelisten(backoff)
                    } else {
                        _voiceState.value = NeliAiVoiceState.Idle
                    }
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    handsFreeActive = false
                    isInActiveCommandWindow = false
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
                        _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                        scheduleHandsFreeRelisten(1_400L)
                    } else {
                        _voiceState.value = NeliAiVoiceState.Idle
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (isReleased) return
            consecutiveSilentErrors = 0
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
                if (handsFreeActive) scheduleHandsFreeRelisten(900L)
                return
            }

            // Passive wake-word gating (filters out movie / live TV audio!)
            if (passiveWakeWordGateEnabled && !isInActiveCommandWindow) {
                val wakeCandidate = matches.firstOrNull { NeliAiEngine.containsWakeWord(it) }
                if (wakeCandidate == null) {
                    // Background movie/TV dialogue without "Hey Neli" / "Neli" -> ignore silently!
                    _voiceState.value = NeliAiVoiceState.HandsFreeStandby
                    if (handsFreeActive) scheduleHandsFreeRelisten(550L)
                    return
                }
                val chained = NeliAiEngine.stripWakeWord(wakeCandidate)
                if (pauseExternalAppsOnWake) {
                    acquireTransientAudioFocus()
                }
                if (chained.isBlank()) {
                    // Pure wake word ("Hey Neli" / "Neli") -> alert host (mute player / pop glow) & greet!
                    onWakeWordDetected(null)
                    onRecognizedQuery(wakeCandidate)
                } else {
                    // Chained sentence ("Hey Neli play Azam Two") -> alert host & execute directly!
                    onWakeWordDetected(chained)
                    _voiceState.value = NeliAiVoiceState.Thinking
                    onRecognizedQuery(wakeCandidate)
                }
                return
            }

            // Active command window received a spoken command
            cancelCommandTimeout()
            isInActiveCommandWindow = false
            _voiceState.value = NeliAiVoiceState.Thinking
            onRecognizedQuery(topTranscript)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (partial.isBlank()) return

            // Instant wake-word early detection during passive scan (mutes player immediately as user says "Hey Neli"!)
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

    private fun scheduleHandsFreeRelisten(delayMs: Long) {
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
