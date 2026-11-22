package com.example.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.MediaContentRepository
import com.example.model.MediaContent
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliNeonPurple
import com.example.ui.theme.NeliWarmOrange
import java.util.Calendar
import kotlin.math.abs

/**
 * Manages the Scheduled Recommended Movie Popup & Missed-Window Notification rules in East Africa Time (`Africa/Dar_es_Salaam`, UTC+3):
 *
 * Strictly 4 daily windows (never interrupts the user on random opens):
 * - Saa 4:00 Asubuhi -> 10:00 EAT (`10:00 – 10:59 EAT`)
 * - Saa 8:00 Mchana  -> 14:00 EAT (`14:00 – 14:59 EAT`)
 * - Saa 10:00 Jioni  -> 16:00 EAT (`16:00 – 16:59 EAT`)
 * - Saa 2:00 Usiku   -> 20:00 EAT (`20:00 – 20:59 EAT`)
 *
 * - If the user opens the app during one of these 4 windows and hasn't seen that slot yet today,
 *   the transparent Recommended Movie popup appears ONCE and marks that slot handled.
 * - If the window passes without the user opening the app, `NeliNotificationScheduler` automatically
 *   delivers that exact slot's Recommended Movie as a rich poster notification with sound and marks the slot handled.
 */
object NeliRecommendedEntryManager {

    val EAT_TIME_ZONE: java.util.TimeZone = java.util.TimeZone.getTimeZone("Africa/Dar_es_Salaam")

    /**
     * Exact East Africa Time (24-hour) slots:
     * - 10 = Saa 4:00 Asubuhi (10:00 AM EAT)
     * - 14 = Saa 8:00 Mchana  (2:00 PM EAT)
     * - 16 = Saa 10:00 Jioni  (4:00 PM EAT)
     * - 20 = Saa 2:00 Usiku   (8:00 PM EAT)
     */
    val DAILY_POPUP_HOURS_EAT: List<Int> = listOf(10, 14, 16, 20)

    const val FOUR_HOURS_MS = 4L * 60L * 60L * 1000L
    private const val PREFS_NAME = "neli_recommended_entry_prefs"
    private const val KEY_LAST_SHOWN_MS = "last_recommended_shown_ms"
    private const val KEY_DEVICE_SALT = "device_recommendation_salt"
    private const val KEY_SESSION_COUNTER = "recommended_session_counter"
    private const val KEY_LAST_MOVIE_ID = "last_recommended_movie_id"
    private const val KEY_HANDLED_SLOT_PREFIX = "handled_eat_slot_"

    fun buildDailySlotKey(slotHourEat: Int, nowMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance(EAT_TIME_ZONE).apply { timeInMillis = nowMs }
        val year = cal.get(Calendar.YEAR)
        val doy = cal.get(Calendar.DAY_OF_YEAR)
        return "${KEY_HANDLED_SLOT_PREFIX}${year}_${doy}_$slotHourEat"
    }

    fun getSlotSwahiliLabel(slotHourEat: Int): String = when (slotHourEat) {
        10 -> "Saa 4:00 Asubuhi (EAT)"
        14 -> "Saa 8:00 Mchana (EAT)"
        16 -> "Saa 10:00 Jioni (EAT)"
        20 -> "Saa 2:00 Usiku (EAT)"
        else -> "Pendekezo la Leo"
    }

    fun hasHandledSlotToday(
        context: Context,
        slotHourEat: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(buildDailySlotKey(slotHourEat, nowMs), false)
    }

    /**
     * Returns the currently active EAT slot hour (`10`, `14`, `16`, or `20`) ONLY if the current
     * East Africa Time falls within that 1-hour window AND that slot has not yet been shown as a popup
     * or sent as a missed-window notification today. Otherwise returns `null` (no popup!).
     */
    fun getActivePopupSlotIfDue(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Int? {
        val cal = Calendar.getInstance(EAT_TIME_ZONE).apply { timeInMillis = nowMs }
        val currentHourEat = cal.get(Calendar.HOUR_OF_DAY)
        if (currentHourEat !in DAILY_POPUP_HOURS_EAT) return null
        return if (!hasHandledSlotToday(context, currentHourEat, nowMs)) currentHourEat else null
    }

    fun shouldTriggerAfterResume(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = getActivePopupSlotIfDue(context, nowMs) != null

    fun markSlotHandled(
        context: Context,
        slotHourEat: Int,
        movieId: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextSession = prefs.getInt(KEY_SESSION_COUNTER, 0) + 1
        prefs.edit()
            .putBoolean(buildDailySlotKey(slotHourEat, nowMs), true)
            .putLong(KEY_LAST_SHOWN_MS, nowMs)
            .putInt(KEY_SESSION_COUNTER, nextSession)
            .putString(KEY_LAST_MOVIE_ID, movieId)
            .apply()
    }

    fun markOverlayShown(
        context: Context,
        movieId: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cal = Calendar.getInstance(EAT_TIME_ZONE).apply { timeInMillis = nowMs }
        val currentHourEat = cal.get(Calendar.HOUR_OF_DAY)
        markSlotHandled(context, currentHourEat, movieId, nowMs)
    }

    fun getOrCreateDeviceSalt(context: Context): Int {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getInt(KEY_DEVICE_SALT, 0)
        if (existing != 0) return existing
        val generated = ((System.nanoTime() xor (Math.random() * Int.MAX_VALUE).toLong()).toInt())
            .let { if (it == 0) 7919 else abs(it) }
        prefs.edit().putInt(KEY_DEVICE_SALT, generated).apply()
        return generated
    }

    /**
     * Picks a fresh non-adult movie with artwork tailored to the current EAT day + slot hour + user/device salt.
     */
    fun pickRecommendedMovieForUser(
        context: Context,
        catalog: List<MediaContent>,
        userIdentifier: String = "",
        nowMs: Long = System.currentTimeMillis(),
        slotHourEat: Int? = null
    ): MediaContent? {
        val eligible = catalog.filter {
            it.published && !it.isAdultContent &&
                (it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank()) &&
                it.id !in MediaContentRepository.DEMO_FALLBACK_MOVIE_IDS
        }.ifEmpty {
            catalog.filter { it.published && !it.isAdultContent }
        }
        if (eligible.isEmpty()) return null

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val salt = getOrCreateDeviceSalt(context)
        val lastMovieId = prefs.getString(KEY_LAST_MOVIE_ID, "").orEmpty()

        val cal = Calendar.getInstance(EAT_TIME_ZONE).apply { timeInMillis = nowMs }
        val dayKey = cal.get(Calendar.YEAR) * 400 + cal.get(Calendar.DAY_OF_YEAR)
        val effectiveSlot = slotHourEat ?: cal.get(Calendar.HOUR_OF_DAY)
        val userHash = if (userIdentifier.isNotBlank()) abs(userIdentifier.lowercase().hashCode()) else 0
        val combinedSeed = abs(dayKey * 37 + salt * 13 + userHash * 7 + effectiveSlot * 23)

        var chosenIndex = combinedSeed % eligible.size
        if (eligible.size > 1 && eligible[chosenIndex].id == lastMovieId) {
            chosenIndex = (chosenIndex + 1) % eligible.size
        }
        return eligible[chosenIndex]
    }
}

/**
 * Translucent full-screen overlay showcasing a fresh personalized Recommended Movie on app entry
 * or after 4 hours, complete with a prominent X close button, poster artwork, and Play / Details triggers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeliRecommendedEntryOverlay(
    movie: MediaContent?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onWatchNow: (MediaContent) -> Unit,
    onOpenDetails: (MediaContent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = visible && movie != null,
        enter = fadeIn() + scaleIn(initialScale = 0.93f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
        modifier = modifier.fillMaxSize()
    ) {
        val targetMovie = movie ?: return@AnimatedVisibility
        val posterUrl = remember(targetMovie.id, targetMovie.posterUrl, targetMovie.backdropUrl) {
            MediaContentRepository.resolveGuaranteedMediaImageUrl(
                targetMovie.posterUrl,
                targetMovie.backdropUrl,
                targetMovie.streamUrl
            )
        }
        val posterReq = remember(targetMovie.id, posterUrl) {
            ImageRequest.Builder(context)
                .data(posterUrl)
                .size(640, 960)
                .crossfade(true)
                .build()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBD040611))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 22.dp, vertical = 28.dp)
                .testTag("recommended_entry_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 390.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume click inside card */ }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 28.dp,
                            shape = RoundedCornerShape(28.dp),
                            ambientColor = NeliMagenta.copy(alpha = 0.55f),
                            spotColor = NeliNeonPurple.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xEE0D1226))
                        .border(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(NeliMagenta, NeliWarmOrange, NeliGenreCyan)
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                ) {
                    // Top Poster Stage with X Close Button & Recommended Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.86f)
                            .background(Color(0xFF141B36))
                    ) {
                        AsyncImage(
                            model = posterReq,
                            contentDescription = targetMovie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom gradient fade into dark obsidian glass card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0xCC0D1226),
                                            Color(0xFF0D1226)
                                        )
                                    )
                                )
                        )

                        // Top-left Recommended For This Time Pill
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                )
                                .padding(horizontal = 11.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "RECOMMENDED NOW",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Prominent Top-Right X Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC060916))
                                .border(1.2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                                .testTag("recommended_overlay_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Recommendation",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Movie Title + Metadata sitting cleanly over the bottom fade
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = targetMovie.title,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                OverlayMetaChip(text = targetMovie.releaseYear.ifBlank { "2026" })
                                OverlayMetaChip(text = targetMovie.primaryGenre.ifBlank { "Cinema" })
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xCC1B2444))
                                        .border(1.dp, NeliMagenta.copy(alpha = 0.5f), CircleShape)
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB800),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = targetMovie.rating.ifBlank { "8.2" },
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                if (targetMovie.narrated && targetMovie.narrationLanguage.isNotBlank()) {
                                    OverlayMetaChip(
                                        text = targetMovie.narrationLanguage.uppercase(),
                                        accentColor = NeliGenreCyan
                                    )
                                }
                            }
                        }
                    }

                    // Description & Action Buttons
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (targetMovie.synopsis.isNotBlank()) {
                            Text(
                                text = targetMovie.synopsis,
                                color = Color(0xFFC5D0E6),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onWatchNow(targetMovie) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.35f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                    )
                                    .testTag("recommended_overlay_watch_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Watch Now",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            OutlinedButton(
                                onClick = { onOpenDetails(targetMovie) },
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(0.95f)
                                    .heightIn(min = 48.dp)
                                    .testTag("recommended_overlay_details_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Details",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayMetaChip(
    text: String,
    accentColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Color(0xCC1B2444))
            .border(0.8.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}
