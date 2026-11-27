package com.example.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.OfflineDownloadManager
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.NeliDatabase
import com.example.model.MediaContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Production Outside-App & Lock-Screen Neli AI System Overlay Service.
 *
 * Works smoothly over WhatsApp, YouTube, Home Screen, and Lock Screen without interfering with underlying apps:
 * - Renders a sleek circular 3D Particle/Wave Mesh AI Ball docked at the bottom-center (NO typing box!).
 * - Color-coded states:
 *   - Blue = User Speaking (Listening)
 *   - White-Yellow = AI Processing (Thinking)
 *   - Pink pulsing in beat = AI Talking (Speaking)
 * - Displays live captions above the ball.
 * - Pauses other apps' music/video via transient AudioFocus when "Hey Neli" activates,
 *   and resumes automatically if no command arrives within 5 seconds.
 * - Shows a sleek floating Movie Suggestion Popup with checkboxes (tick -> Download Selected / Play in Sequence)
 *   and Movie Info Card WITHOUT opening the full NeliTv app!
 * - Executes background downloads ("Hey Neli download 5 random movies") & screen lock ("Hey Neli lock my screen")
 *   even when outside the app or on the Lock Screen!
 */
class NeliAiSystemOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.example.ai.ACTION_START_OVERLAY"
        const val ACTION_STOP = "com.example.ai.ACTION_STOP_OVERLAY"
        const val ACTION_TRIGGER_VOICE = "com.example.ai.ACTION_TRIGGER_VOICE"
        const val ACTION_APP_FOREGROUND_CHANGED = "com.example.ai.ACTION_APP_FOREGROUND_CHANGED"
        const val EXTRA_IN_APP = "extra_in_app"

        const val EXTRA_LAUNCH_CHANNEL_ID = "neli_ai_launch_channel_id"
        const val EXTRA_LAUNCH_MOVIE_IDS = "neli_ai_launch_movie_ids"
        const val EXTRA_LAUNCH_OPEN_VIP = "neli_ai_open_vip"

        private const val NOTIF_CHANNEL_ID = "neli_ai_handsfree_channel"
        private const val NOTIF_ID = 7742

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var isNeliTvInForeground: Boolean = false

        fun start(context: Context, triggerVoiceImmediately: Boolean = false) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, NeliAiSystemOverlayService::class.java).apply {
                action = if (triggerVoiceImmediately) ACTION_TRIGGER_VOICE else ACTION_START
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, NeliAiSystemOverlayService::class.java))
            }
        }

        fun notifyAppForegroundState(context: Context, inForeground: Boolean) {
            isNeliTvInForeground = inForeground
            if (!isRunning) return
            runCatching {
                val intent = Intent(context, NeliAiSystemOverlayService::class.java).apply {
                    action = ACTION_APP_FOREGROUND_CHANGED
                    putExtra(EXTRA_IN_APP, inForeground)
                }
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var ballView: NeliAiMeshBallView? = null
    private var ballParams: WindowManager.LayoutParams? = null
    private var captionView: LinearLayout? = null
    private var captionTextView: TextView? = null
    private var captionStatusView: TextView? = null
    private var popupOverlayView: View? = null

    private var voiceController: NeliAiVoiceController? = null
    private val autoHideCaptionRunnable = Runnable { removeCaptionBubble() }
    private var powerButtonDoubleTapDetector: NeliPowerButtonDoubleTapDetector? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        NeliAiMemoryStore.initialize(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        startForegroundNotification()
        initVoiceController()
        powerButtonDoubleTapDetector = NeliPowerButtonDoubleTapDetector {
            mainHandler.post {
                NeliDigitalAssistantHelper.wakeNeliAiFromSystemTrigger(this)
            }
        }.also { it.register(this) }
        if (!isNeliTvInForeground) {
            attachFloatingBall()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APP_FOREGROUND_CHANGED -> {
                val inApp = intent.getBooleanExtra(EXTRA_IN_APP, false)
                isNeliTvInForeground = inApp
                if (inApp) {
                    // Inside NeliTv app: NO floating AI ball (in-app uses the glowing edge border!)
                    removeAllOverlayViews()
                    voiceController?.stopListening()
                } else if (NeliAiMemoryStore.isOverlayBallEnabled.value && Settings.canDrawOverlays(this)) {
                    attachFloatingBall()
                    if (NeliAiMemoryStore.isHandsFreeEnabled.value || NeliAiMemoryStore.isLockScreenWakeEnabled.value) {
                        voiceController?.startPassiveWakeListening()
                    }
                }
            }
            ACTION_TRIGGER_VOICE -> {
                if (!isNeliTvInForeground && Settings.canDrawOverlays(this)) {
                    attachFloatingBall()
                }
                showCaptionBubble("Listening...", "Speak your command to Neli AI", Color.parseColor("#00D4FF"))
                voiceController?.startActiveCommandWindow(NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
            }
            else -> {
                if (!isNeliTvInForeground && Settings.canDrawOverlays(this)) {
                    attachFloatingBall()
                    if (NeliAiMemoryStore.isHandsFreeEnabled.value || NeliAiMemoryStore.isLockScreenWakeEnabled.value) {
                        voiceController?.startPassiveWakeListening()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.removeCallbacksAndMessages(null)
        powerButtonDoubleTapDetector?.unregister(this)
        powerButtonDoubleTapDetector = null
        removeAllOverlayViews()
        voiceController?.release()
        voiceController = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm != null) {
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Neli AI Hands-Free Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Hey Neli hands-free voice and outside-app overlay active."
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }

        val openAppPi = PendingIntent.getActivity(
            this,
            101,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Neli AI is listening hands-free")
            .setContentText("Say \"Hey Neli\" anytime — even outside NeliTv or on lock screen.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            }.onFailure {
                startForeground(NOTIF_ID, notification)
            }
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun initVoiceController() {
        voiceController = NeliAiVoiceController(
            context = this,
            onRecognizedQuery = { transcript -> handleOutsideAppVoiceQuery(transcript) },
            onWakeWordDetected = { chained ->
                if (!isNeliTvInForeground && Settings.canDrawOverlays(this)) {
                    attachFloatingBall()
                    showCaptionBubble(
                        status = "Neli AI Listening...",
                        text = chained ?: "I'm listening...",
                        accent = Color.parseColor("#00D4FF")
                    )
                }
            },
            onCommandWindowExpired = {
                // 5s silence elapsed -> AudioFocus already abandoned so paused music/video resumes!
                removeCaptionBubble()
            },
            onSpeechFinished = {
                mainHandler.postDelayed(autoHideCaptionRunnable, 3_500L)
            }
        ).apply {
            pauseExternalAppsOnWake = true
            voiceReplyEnabled = NeliAiMemoryStore.isVoiceReplyEnabled.value
            voiceCancellationEnabled = NeliAiMemoryStore.isVoiceCancellationEnabled.value
        }

        serviceScope.launch {
            voiceController?.voiceState?.collectLatest { state ->
                ballView?.updateVoiceState(state)
                if (NeliAiMemoryStore.isCaptionsEnabled.value && !isNeliTvInForeground) {
                    when (state) {
                        is NeliAiVoiceState.Listening -> {
                            showCaptionBubble(
                                status = "Listening...",
                                text = state.partialText.ifBlank { "Speak your command..." },
                                accent = Color.parseColor("#00D4FF")
                            )
                        }
                        is NeliAiVoiceState.Thinking -> {
                            showCaptionBubble(
                                status = "Thinking...",
                                text = "Working on your request...",
                                accent = Color.parseColor("#FACC15")
                            )
                        }
                        is NeliAiVoiceState.Speaking -> {
                            showCaptionBubble(
                                status = "Neli AI",
                                text = state.text,
                                accent = Color.parseColor("#FF2E93")
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun handleOutsideAppVoiceQuery(transcript: String) {
        serviceScope.launch {
            val isOnline = isNetworkOnline()
            val dao = NeliDatabase.getInstance(applicationContext).mediaDao()
            val downloadedIds = withContext(Dispatchers.IO) {
                runCatching { dao.getAllDownloads().first().map { it.id }.toSet() }.getOrDefault(emptySet())
            }
            val liveChannels = ChannelRepository.getPrioritizedAllChannels(ChannelRepository.liveChannelsFlow.value)
            val mediaCatalog = MediaContentRepository.mediaCatalog.value
            val isPremium = NeliSubscriptionManager.subscriptionState.value.isActiveNow

            val snapshot = NeliAiContextSnapshot(
                liveChannels = liveChannels,
                mediaCatalog = mediaCatalog,
                activePlayerChannel = null,
                isSmartTv = false,
                isPremiumActive = isPremium,
                hourOfDay24 = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                isOnline = isOnline,
                isOutsideApp = true,
                downloadedMediaIds = downloadedIds,
                watchedWithin30DaysIds = NeliAiMemoryStore.getWatchedMovieIdsWithin30Days(),
                isChannelLockedCheck = { ch ->
                    NeliAdminManager.isChannelLockedForUser(
                        channelId = ch.id,
                        channelObj = ch,
                        isPremiumActive = isPremium,
                        context = applicationContext
                    )
                }
            )

            val response = NeliAiEngine.processCommand(
                rawQuery = transcript,
                snapshot = snapshot,
                context = applicationContext,
                inputMode = "voice"
            )

            showCaptionBubble(
                status = "Neli AI",
                text = response.replyText,
                accent = Color.parseColor("#FF2E93")
            )

            val listenAfter = response.action is NeliAiAction.WakeGreeting
            executeOutsideAppAction(response.action)
            voiceController?.speak(response.replySpeech, listenForFollowUpAfter = listenAfter)
        }
    }

    private fun executeOutsideAppAction(action: NeliAiAction) {
        when (action) {
            NeliAiAction.None,
            NeliAiAction.WakeGreeting -> Unit

            NeliAiAction.LockDeviceScreen -> {
                val locked = NeliDeviceAdminReceiver.lockDeviceNow(this)
                if (!locked) {
                    showCaptionBubble(
                        status = "Permission Needed",
                        text = "Enable Screen Lock permission in Account -> NeliTv AI Settings.",
                        accent = Color.parseColor("#F59E0B")
                    )
                }
            }

            NeliAiAction.OpenNeliTvApp -> {
                launchMainActivity { }
            }

            is NeliAiAction.PlayChannel -> {
                launchMainActivity {
                    putExtra(EXTRA_LAUNCH_CHANNEL_ID, action.channel.id)
                }
            }

            is NeliAiAction.LockedChannelNeedsPayment -> {
                showCaptionBubble(
                    status = "VIP Required",
                    text = "${action.channel.name} requires VIP payment first to unlock.",
                    accent = Color.parseColor("#F59E0B")
                )
            }

            is NeliAiAction.SuggestMovies -> {
                // Outside-App Interactive Movie Picker Popup (DOES NOT force open full app!)
                showMoviePickerPopup(action.movies)
            }

            is NeliAiAction.ShowMovieInfo -> {
                // Outside-App Movie Info Popup (DOES NOT force open full app!)
                showMovieInfoPopup(action.media)
            }

            is NeliAiAction.DownloadRandomMovies -> {
                enqueueBackgroundDownloads(action.movies)
            }

            is NeliAiAction.DownloadSpecificMovies -> {
                enqueueBackgroundDownloads(action.movies)
            }

            is NeliAiAction.OpenMedia -> {
                launchMainActivity {
                    putStringArrayListExtra(EXTRA_LAUNCH_MOVIE_IDS, arrayListOf(action.media.id))
                }
            }

            is NeliAiAction.PlayMoviesInSequence -> {
                val ids = ArrayList(action.movies.map { it.id })
                NeliAiMemoryStore.setSequentialPlayQueue(this, ids.drop(1))
                launchMainActivity {
                    putStringArrayListExtra(EXTRA_LAUNCH_MOVIE_IDS, ids)
                }
            }

            else -> Unit
        }
    }

    private fun enqueueBackgroundDownloads(movies: List<MediaContent>) {
        if (movies.isEmpty()) return
        serviceScope.launch(Dispatchers.IO) {
            val dao = NeliDatabase.getInstance(applicationContext).mediaDao()
            movies.forEach { media ->
                val url = media.streamUrl.trim()
                if (url.isNotBlank()) {
                    val entity = DownloadedItemEntity(
                        id = media.id,
                        title = media.title,
                        type = if (media.isSeries) "series" else "movie",
                        posterUrl = media.posterUrl.ifBlank { media.backdropUrl },
                        backdropUrl = media.backdropUrl.ifBlank { media.posterUrl },
                        streamUrl = url,
                        genre = media.primaryGenre.ifBlank { media.genre },
                        duration = media.duration,
                        rating = media.rating,
                        fileSizeLabel = "Queued by Neli AI",
                        downloadStatus = "DOWNLOADING",
                        progressPercent = 1
                    )
                    runCatching {
                        dao.upsertDownload(entity)
                        OfflineDownloadManager.enqueueBackgroundDownload(applicationContext, dao, entity)
                    }
                }
            }
        }
    }

    private fun launchMainActivity(configure: Intent.() -> Unit) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            configure()
        }
        runCatching { startActivity(intent) }
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun attachFloatingBall() {
        if (ballView != null || !Settings.canDrawOverlays(this)) return
        val wm = windowManager ?: return
        val ballSizePx = dp(74)
        val view = NeliAiMeshBallView(this)
        val params = WindowManager.LayoutParams(
            ballSizePx,
            ballSizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(24)
            x = 0
        }

        // Smooth drag + single-tap to activate voice
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (touchY - event.rawY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        params.x = initX + dx
                        params.y = (initY + dy).coerceAtLeast(dp(12))
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        showCaptionBubble("Listening...", "Speak your command to Neli AI", Color.parseColor("#00D4FF"))
                        voiceController?.startActiveCommandWindow(NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            wm.addView(view, params)
            ballView = view
            ballParams = params
        }
    }

    private fun showCaptionBubble(status: String, text: String, accent: Int) {
        if (!Settings.canDrawOverlays(this) || isNeliTvInForeground) return
        mainHandler.removeCallbacks(autoHideCaptionRunnable)
        val wm = windowManager ?: return

        if (captionView == null) {
            val container = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EC121622"))
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(1), accent)
                }
            }
            val statusTv = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(accent)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }
            val bodyTv = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                setTextColor(Color.WHITE)
                maxLines = 4
            }
            container.addView(statusTv)
            container.addView(bodyTv)

            val params = WindowManager.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(108)
            }
            runCatching {
                wm.addView(container, params)
                captionView = container
                captionStatusView = statusTv
                captionTextView = bodyTv
            }
        }

        captionStatusView?.text = status
        captionStatusView?.setTextColor(accent)
        captionTextView?.text = text
        (captionView?.background as? GradientDrawable)?.setStroke(dp(1), accent)
    }

    private fun removeCaptionBubble() {
        val v = captionView ?: return
        runCatching { windowManager?.removeView(v) }
        captionView = null
        captionStatusView = null
        captionTextView = null
    }

    /**
     * Outside-App Zero-Tick Hands-Free Movie Picks Popup
     * (Tap any movie to watch immediately, say "Download all" / "Play them" / "Play number 1", or tap Download All / Play All!)
     */
    private fun showMoviePickerPopup(movies: List<MediaContent>) {
        if (!Settings.canDrawOverlays(this) || movies.isEmpty()) return
        removePopupOverlay()
        val wm = windowManager ?: return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5101420"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#00D4FF"))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = "Neli AI Movie Picks (No Ticking Needed)"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(10), dp(4), dp(4), dp(4))
            setOnClickListener { removePopupOverlay() }
        }
        headerRow.addView(titleTv)
        headerRow.addView(closeBtn)
        root.addView(headerRow)

        val voiceHintTv = TextView(this).apply {
            text = "Say \"Play number 1\", \"Download all\", \"Play them\" — or tap any movie:"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(0, dp(4), 0, dp(6))
        }
        root.addView(voiceHintTv)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(200)
            ).apply { topMargin = dp(4) }
        }
        val listLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        movies.forEachIndexed { index, movie ->
            val itemRow = TextView(this).apply {
                text = "${index + 1}.  ${movie.title}  •  ${movie.primaryGenre.ifBlank { movie.genre }} (${movie.releaseYear.ifBlank { "HD" }})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1F30"))
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
                setOnClickListener {
                    removePopupOverlay()
                    executeOutsideAppAction(NeliAiAction.OpenMedia(movie, autoPlay = true))
                }
            }
            listLayout.addView(itemRow)
        }
        scroll.addView(listLayout)
        root.addView(scroll)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val dlBtn = Button(this).apply {
            text = "Download All (${movies.size})"
            setBackgroundColor(Color.parseColor("#00D4FF"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                enqueueBackgroundDownloads(movies)
                voiceController?.speak("Downloading all ${movies.size} movies in the background!")
                removePopupOverlay()
            }
        }
        val playSeqBtn = Button(this).apply {
            text = "Play All"
            setBackgroundColor(Color.parseColor("#FF2E93"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            setOnClickListener {
                removePopupOverlay()
                executeOutsideAppAction(NeliAiAction.PlayMoviesInSequence(movies))
            }
        }
        actionsRow.addView(dlBtn)
        actionsRow.addView(playSeqBtn)
        root.addView(actionsRow)

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        runCatching {
            wm.addView(root, params)
            popupOverlayView = root
        }
    }

    private fun showMovieInfoPopup(movie: MediaContent) {
        if (!Settings.canDrawOverlays(this)) return
        removePopupOverlay()
        val wm = windowManager ?: return

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5101420"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#FF2E93"))
            }
        }
        val titleTv = TextView(this).apply {
            text = movie.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val metaTv = TextView(this).apply {
            text = "${movie.releaseYear.ifBlank { "2026" }} • ${movie.primaryGenre.ifBlank { movie.genre }} • ★ ${movie.rating.ifBlank { "HD" }}"
            setTextColor(Color.parseColor("#00D4FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(4), 0, dp(8))
        }
        val bodyTv = TextView(this).apply {
            text = movie.synopsis.ifBlank { "Available on NeliTv." }
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            maxLines = 5
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }
        val dlBtn = Button(this).apply {
            text = "Download"
            setBackgroundColor(Color.parseColor("#00D4FF"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                enqueueBackgroundDownloads(listOf(movie))
                voiceController?.speak("Downloading ${movie.title} now!")
                removePopupOverlay()
            }
        }
        val watchBtn = Button(this).apply {
            text = "Watch"
            setBackgroundColor(Color.parseColor("#FF2E93"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
            setOnClickListener {
                removePopupOverlay()
                executeOutsideAppAction(NeliAiAction.OpenMedia(movie, autoPlay = true))
            }
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            setBackgroundColor(Color.parseColor("#1E293B"))
            setTextColor(Color.WHITE)
            setOnClickListener { removePopupOverlay() }
        }
        btnRow.addView(dlBtn)
        btnRow.addView(watchBtn)
        btnRow.addView(closeBtn)

        root.addView(titleTv)
        root.addView(metaTv)
        root.addView(bodyTv)
        root.addView(btnRow)

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.9f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        runCatching {
            wm.addView(root, params)
            popupOverlayView = root
        }
    }

    private fun removePopupOverlay() {
        val v = popupOverlayView ?: return
        runCatching { windowManager?.removeView(v) }
        popupOverlayView = null
    }

    private fun removeAllOverlayViews() {
        removePopupOverlay()
        removeCaptionBubble()
        ballView?.let { v ->
            runCatching { windowManager?.removeView(v) }
        }
        ballView = null
        ballParams = null
    }

    private fun isNetworkOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}

/**
 * Custom Hardware-Accelerated Android View drawing the circular 3D Particle & Wave Mesh AI Ball
 * outside NeliTv (Blue = Listening, White-Yellow = Thinking, Pink pulsing in beat = Speaking).
 */
private class NeliAiMeshBallView(context: Context) : View(context) {
    private var voiceState: NeliAiVoiceState = NeliAiVoiceState.HandsFreeStandby
    private var phase = 0f
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private val frameRunnable = object : Runnable {
        override fun run() {
            val speed = when (voiceState) {
                is NeliAiVoiceState.Thinking -> 0.16f
                is NeliAiVoiceState.Speaking -> 0.13f
                is NeliAiVoiceState.Listening -> 0.10f
                else -> 0.045f
            }
            phase = (phase + speed) % (2.0 * PI).toFloat()
            invalidate()
            postDelayed(this, 33L)
        }
    }

    fun updateVoiceState(state: NeliAiVoiceState) {
        voiceState = state
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        removeCallbacks(frameRunnable)
        post(frameRunnable)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frameRunnable)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val maxR = minOf(cx, cy) * 0.92f
        val (primary, secondary, accent, energy) = when (val st = voiceState) {
            is NeliAiVoiceState.Listening -> Quad(
                Color.parseColor("#00D4FF"),
                Color.parseColor("#2563EB"),
                Color.parseColor("#60A5FA"),
                (0.45f + (st.rmsDb / 12f) * 0.55f).coerceIn(0.45f, 1f)
            )
            is NeliAiVoiceState.Thinking -> Quad(
                Color.parseColor("#FEF08A"),
                Color.parseColor("#FFFFFF"),
                Color.parseColor("#FACC15"),
                0.85f
            )
            is NeliAiVoiceState.Speaking -> Quad(
                Color.parseColor("#FF2E93"),
                Color.parseColor("#F472B6"),
                Color.parseColor("#FF7EB6"),
                st.beatAmplitude.coerceIn(0.4f, 1f)
            )
            else -> Quad(
                Color.parseColor("#38BDF8"),
                Color.parseColor("#9333EA"),
                Color.parseColor("#EC4899"),
                0.35f
            )
        }

        val beatScale = if (voiceState is NeliAiVoiceState.Speaking) {
            0.94f + 0.12f * energy
        } else {
            0.96f + 0.04f * sin(phase)
        }
        val baseR = maxR * 0.76f * beatScale

        // Outer neon halo
        bgPaint.shader = RadialGradient(
            cx, cy, maxR * 1.05f,
            intArrayOf(
                Color.argb((140 * energy).toInt().coerceIn(50, 210), Color.red(primary), Color.green(primary), Color.blue(primary)),
                Color.TRANSPARENT
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, maxR * 1.05f, bgPaint)

        // Core sphere
        bgPaint.shader = RadialGradient(
            cx - baseR * 0.2f, cy - baseR * 0.2f, baseR,
            intArrayOf(
                Color.argb(135, Color.red(secondary), Color.green(secondary), Color.blue(secondary)),
                Color.parseColor("#0B0E1A")
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, baseR, bgPaint)

        // 3D Wave Mesh Rings
        for (rIdx in 0 until 5) {
            val frac = (rIdx + 1) / 5f
            val ringR = baseR * (0.4f + frac * 0.58f)
            path.reset()
            val steps = 32
            for (s in 0..steps) {
                val theta = (s.toFloat() / steps) * (2.0 * PI).toFloat()
                val amp = baseR * (0.06f + 0.11f * energy)
                val deform = sin(theta * 3f + phase + rIdx * 0.9f) * amp
                val rr = ringR + deform
                val x = cx + cos(theta) * rr
                val y = cy + sin(theta) * rr * (0.85f + 0.15f * cos(phase + rIdx))
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            ringPaint.color = if (rIdx % 2 == 0) primary else accent
            ringPaint.alpha = (95 + (135 * frac)).toInt().coerceIn(70, 235)
            ringPaint.strokeWidth = 2.2f + frac * 1.6f
            canvas.drawPath(path, ringPaint)
        }

        // Particle cloud dots
        for (i in 0 until 28) {
            val angle = i * 2.39996f + phase
            val dist = baseR * (0.28f + ((i % 9) / 9f) * 0.72f)
            val px = cx + cos(angle) * dist
            val py = cy + sin(angle) * dist
            dotPaint.color = when (i % 3) {
                0 -> primary
                1 -> secondary
                else -> accent
            }
            dotPaint.alpha = (145 + (100 * energy)).toInt().coerceIn(120, 255)
            canvas.drawCircle(px, py, 2.4f + (i % 2), dotPaint)
        }

        // Luminous outer ring
        ringPaint.shader = LinearGradient(cx - baseR, cy - baseR, cx + baseR, cy + baseR, primary, accent, Shader.TileMode.CLAMP)
        ringPaint.strokeWidth = 3.2f
        ringPaint.alpha = 230
        canvas.drawCircle(cx, cy, baseR, ringPaint)
        ringPaint.shader = null
    }

    private data class Quad(val a: Int, val b: Int, val c: Int, val energy: Float)
}
