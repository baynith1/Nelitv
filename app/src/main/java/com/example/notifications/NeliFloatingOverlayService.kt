package com.example.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.example.MainActivity
import com.example.R

/**
 * NeliTv Floating Promotional Banner Service:
 * Floats a sleek neon banner across other apps (e.g. YouTube, Facebook) or the Android home screen:
 * - NeliTv neon logo on left
 * - Title: "Azam TV Live Now!"
 * - Short Description: "News, Sports, Movies & More"
 * - Azam TV logo / Movie artwork on right
 * - Vibrant gradient "Watch ▶" action button
 * - (X) Close button
 * - Auto-dismisses in 8–12 seconds, or when user taps close
 * - Tapping "Watch" immediately launches NeliTv directly to the channel/movie
 */
class NeliFloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_MEDIA_ID = "extra_media_id"

        fun showFloatingBannerIfPermitted(
            context: Context,
            channelId: String = "R17JUvbCEzu2eTbjnE74",
            channelName: String = "Azam TV",
            title: String = "Azam TV Live Now!",
            subtitle: String = "News, Sports, Movies & More",
            mediaId: String? = null
        ) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                    val intent = Intent(context, NeliFloatingOverlayService::class.java).apply {
                        putExtra(EXTRA_CHANNEL_ID, channelId)
                        putExtra(EXTRA_CHANNEL_NAME, channelName)
                        putExtra(EXTRA_TITLE, title)
                        putExtra(EXTRA_SUBTITLE, subtitle)
                        mediaId?.let { putExtra(EXTRA_MEDIA_ID, it) }
                    }
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = intent?.getStringExtra(EXTRA_CHANNEL_ID) ?: "R17JUvbCEzu2eTbjnE74"
        val channelName = intent?.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Azam TV"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Azam TV Live Now!"
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE) ?: "News, Sports, Movies & More"
        val mediaId = intent?.getStringExtra(EXTRA_MEDIA_ID)

        showOverlay(channelId, channelName, title, subtitle, mediaId)
        return START_NOT_STICKY
    }

    private fun showOverlay(
        channelId: String,
        channelName: String,
        title: String,
        subtitle: String,
        mediaId: String?
    ) {
        removeOverlay()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val marginPx = (14 * density).toInt()
            val heightPx = (78 * density).toInt()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                heightPx,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (45 * density).toInt()
            }

            // Build layout programmatically with rich neon styling
            val bannerLayout = android.widget.FrameLayout(this).apply {
                setPadding(marginPx, 0, marginPx, 0)
            }

            val cardContainer = android.widget.RelativeLayout(this).apply {
                id = View.generateViewId()
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 18 * density
                    colors = intArrayOf(
                        0xFF120C26.toInt(),
                        0xFF0A0F22.toInt(),
                        0xFF060B1C.toInt()
                    )
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                    setStroke((1.5f * density).toInt(), 0xFF00E5FF.toInt())
                }
                background = bg
                setPadding((10 * density).toInt(), (8 * density).toInt(), (10 * density).toInt(), (8 * density).toInt())
            }

            // 1. NeliTv Logo
            val logoImg = ImageView(this).apply {
                id = View.generateViewId()
                setImageResource(R.drawable.img_nelitv_app_logo_1790873809763)
                scaleType = ImageView.ScaleType.CENTER_CROP
                val logoParams = android.widget.RelativeLayout.LayoutParams(
                    (42 * density).toInt(),
                    (42 * density).toInt()
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_START)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                    marginEnd = (10 * density).toInt()
                }
                layoutParams = logoParams
            }
            cardContainer.addView(logoImg)

            // 2. Close Button (X)
            val closeBtn = TextView(this).apply {
                id = View.generateViewId()
                text = "✕"
                textSize = 14f
                setTextColor(0xFFCBD5E1.toInt())
                val closeParams = android.widget.RelativeLayout.LayoutParams(
                    (26 * density).toInt(),
                    (26 * density).toInt()
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                }
                layoutParams = closeParams
                gravity = Gravity.CENTER
                setOnClickListener {
                    removeOverlay()
                    stopSelf()
                }
            }
            cardContainer.addView(closeBtn)

            // 3. Watch Button
            val watchBtn = TextView(this).apply {
                id = View.generateViewId()
                text = "▶ Watch"
                textSize = 11f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                val btnBg = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 12 * density
                    colors = intArrayOf(0xFFFF007A.toInt(), 0xFFFF7A00.toInt())
                    orientation = android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT
                }
                background = btnBg
                setPadding((12 * density).toInt(), (5 * density).toInt(), (12 * density).toInt(), (5 * density).toInt())
                val watchParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                    addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM)
                }
                layoutParams = watchParams
                setOnClickListener {
                    launchNeliApp(channelId, mediaId)
                    removeOverlay()
                    stopSelf()
                }
            }
            cardContainer.addView(watchBtn)

            // 4. Texts in Center
            val textLayout = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                val txtParams = android.widget.RelativeLayout.LayoutParams(
                    android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                    android.widget.RelativeLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    addRule(android.widget.RelativeLayout.END_OF, logoImg.id)
                    addRule(android.widget.RelativeLayout.START_OF, watchBtn.id)
                    addRule(android.widget.RelativeLayout.CENTER_VERTICAL)
                    marginEnd = (8 * density).toInt()
                }
                layoutParams = txtParams
            }

            val titleTv = TextView(this).apply {
                text = title
                textSize = 13f
                setTextColor(0xFFFFFFFF.toInt())
                setTypeface(null, android.graphics.Typeface.BOLD)
                isSingleLine = true
            }
            textLayout.addView(titleTv)

            val subtitleTv = TextView(this).apply {
                text = subtitle
                textSize = 10f
                setTextColor(0xFF94A3B8.toInt())
                isSingleLine = true
            }
            textLayout.addView(subtitleTv)

            cardContainer.addView(textLayout)
            bannerLayout.addView(cardContainer)

            cardContainer.setOnClickListener {
                launchNeliApp(channelId, mediaId)
                removeOverlay()
                stopSelf()
            }

            windowManager?.addView(bannerLayout, params)
            overlayView = bannerLayout

            // Auto-dismiss in 10 seconds as specified in brief (8–12 seconds)
            dismissRunnable = Runnable {
                removeOverlay()
                stopSelf()
            }
            mainHandler.postDelayed(dismissRunnable!!, 10_000L)

        } catch (e: Exception) {
            stopSelf()
        }
    }

    private fun launchNeliApp(channelId: String, mediaId: String?) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                if (!mediaId.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_LAUNCH_MEDIA_ID, mediaId)
                } else {
                    putExtra(MainActivity.EXTRA_LAUNCH_CHANNEL_ID, channelId)
                }
            }
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}
