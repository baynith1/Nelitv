package com.example.notifications

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NeliSplitScreenNotifier {

    private val _isUnsupportedNoticeVisible = MutableStateFlow(false)
    val isUnsupportedNoticeVisible: StateFlow<Boolean> = _isUnsupportedNoticeVisible.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun showUnsupportedSplitScreenToast(context: Context? = null) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        _isUnsupportedNoticeVisible.value = true

        val runnable = Runnable {
            _isUnsupportedNoticeVisible.value = false
        }
        dismissRunnable = runnable
        mainHandler.postDelayed(runnable, 3000L)

        // Also trigger floating overlay banner if permission is granted
        if (context != null) {
            try {
                NeliFloatingOverlayService.showSplitScreenOverlayToast(context)
            } catch (_: Throwable) {
            }
        }
    }

    fun dismiss() {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        _isUnsupportedNoticeVisible.value = false
    }

    fun isSplitScreenSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val method = ActivityManager::class.java.getMethod("supportsMultiWindow", Context::class.java)
            method.invoke(null, context) as? Boolean ?: true
        } catch (_: Throwable) {
            true
        }
    }
}

/**
 * 3-Second Floating Notification:
 * "Your device does not support split screen"
 * Styled with NeliTv logo, neon border, and auto-dismisses after 3 seconds without disturbing the UI.
 */
@Composable
fun FloatingSplitScreenUnsupportedNotice(
    modifier: Modifier = Modifier
) {
    val isVisible by NeliSplitScreenNotifier.isUnsupportedNoticeVisible.collectAsState()

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(3000L)
            NeliSplitScreenNotifier.dismiss()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF1E1035), Color(0xFF0F172A), Color(0xFF1A0B2E))
                        )
                    )
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(Color(0xFF9333EA), Color(0xFF00E5FF), Color(0xFFEC4899))),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("floating_split_screen_unsupported_notice")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_nelitv_app_logo_1790873809763),
                        contentDescription = "NeliTv Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                    )

                    Column {
                        Text(
                            text = "NELITV • SPLIT SCREEN",
                            color = Color(0xFFFDE047),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = "Your device does not support split screen",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
