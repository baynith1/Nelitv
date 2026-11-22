package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.data.ContinueWatchingEntry
import com.example.data.MediaContentRepository
import com.example.data.NeliWatchProgressManager
import com.example.data.PremiumSubscriptionState
import com.example.data.local.UserAccountEntity
import com.example.data.local.WatchlistItemEntity
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliWarmOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Subscription (Streaming) Card for the redesigned Movie & Live TV Account Page:
 * - Current plan tier & renewal/expiry date
 * - Step-by-step Mobile Payment Instructions (strictly NO mention of Haraka Pay)
 */
@Composable
fun NeliAccountSubscriptionSection(
    subState: PremiumSubscriptionState,
    isVerifiedPremiumMember: Boolean,
    isFreeForeverVip: Boolean,
    isSpecialAdFree: Boolean,
    nowMs: Long,
    modifier: Modifier = Modifier
) {
    val tierTitle = when {
        isSpecialAdFree -> "Special VIP Ad-Free"
        isFreeForeverVip -> "VIP Forever (No Ads)"
        isVerifiedPremiumMember -> subState.planTitle.ifBlank { "Premium Streaming Tier" }
        else -> "Free Streaming Tier"
    }
    val renewalLabel = remember(subState.expiresAtMs, isVerifiedPremiumMember, isFreeForeverVip, isSpecialAdFree) {
        when {
            isSpecialAdFree || isFreeForeverVip -> "Lifetime Active • No Renewal Needed"
            isVerifiedPremiumMember && subState.expiresAtMs > 0L -> {
                val fmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                "Renews / Expires: ${fmt.format(Date(subState.expiresAtMs))}"
            }
            else -> "Upgrade anytime for Ad-Free HD & VIP channels"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NeliSurface)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(NeliMagenta.copy(alpha = 0.65f), NeliWarmOrange.copy(alpha = 0.55f))),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
            .testTag("account_subscription_streaming_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(NeliMagenta, NeliWarmOrange))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Subscription Tier",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Subscription (Streaming)",
                        color = NeliTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = tierTitle,
                        color = if (isVerifiedPremiumMember) NeliMagenta else NeliGenreCyan,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isVerifiedPremiumMember) Color(0x2E10B981) else Color(0x2EFF2E7E))
                    .border(
                        1.dp,
                        if (isVerifiedPremiumMember) Color(0xFF34D399) else NeliMagenta,
                        CircleShape
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isVerifiedPremiumMember) "ACTIVE" else "FREE",
                    color = if (isVerifiedPremiumMember) Color(0xFF34D399) else NeliMagenta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Text(
            text = if (isVerifiedPremiumMember && !isFreeForeverVip && !isSpecialAdFree && subState.expiresAtMs > nowMs) {
                "$renewalLabel • Left: ${subState.formatLiveCountdown(nowMs)}"
            } else {
                renewalLabel
            },
            color = NeliTextSecondary,
            fontSize = 12.sp
        )

        // Step-by-Step Payment Instructions (Mobile Money SMS confirmation & auto-reopen)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NeliSurfaceVariant)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                .padding(12.dp)
                .testTag("account_payment_guide_box"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "How to Pay & Activate Your Plan",
                color = NeliTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )

            val steps = listOf(
                "1. Choose your preferred streaming plan.",
                "2. Enter your mobile phone number.",
                "3. Tap the Pay button to submit your request.",
                "4. Wait for the confirmation SMS prompt on your mobile phone and approve it.",
                "5. The app will automatically reopen and activate your subscription right away."
            )
            steps.forEach { stepText ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(14.dp)
                    )
                    Text(
                        text = stepText,
                        color = NeliTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Library & Activity Card:
 * - Continue Watching (with exact last line timestamp + percentage ended watching + glowing resume bar)
 * - Watchlist & Favorites/Bookmarks
 * - Quick Star Ratings & Reviews
 */
@Composable
fun NeliAccountLibraryAndActivitySection(
    watchlist: List<WatchlistItemEntity>,
    onResumeContinueWatching: (ContinueWatchingEntry) -> Unit,
    onPlayWatchlistItem: (WatchlistItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val continueWatchingList by NeliWatchProgressManager.continueWatching.collectAsState()
    val userRatings by NeliWatchProgressManager.userRatings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("account_library_activity_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = NeliMagenta,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "Library & Activity",
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Continue Watching • Watchlist • Favorites & Ratings",
                    color = NeliTextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // 1. Continue Watching (shows exact last line + percentage where you ended watching)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Continue Watching (${continueWatchingList.size})",
                    color = NeliTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (continueWatchingList.isNotEmpty()) {
                    Text(
                        text = "Tap to resume",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (continueWatchingList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliSurfaceVariant)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Movies & episodes you watch will appear here with the exact minute & percentage where you stopped.",
                        color = NeliTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    continueWatchingList.take(5).forEach { entry ->
                        val posterReq = remember(entry.id, entry.posterUrl) {
                            ImageRequest.Builder(context)
                                .data(entry.posterUrl.ifBlank { MediaContentRepository.resolveGuaranteedMediaImageUrl("", "", entry.streamUrl) })
                                .size(180, 240)
                                .crossfade(true)
                                .build()
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeliSurfaceVariant)
                                .border(1.dp, NeliMagenta.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { onResumeContinueWatching(entry) }
                                .padding(10.dp)
                                .testTag("continue_watching_card_${entry.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(56.dp)
                                    .height(76.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF141A33))
                            ) {
                                AsyncImage(
                                    model = posterReq,
                                    contentDescription = entry.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xCCFF007A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.title,
                                        color = NeliTextPrimary,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(NeliMagenta.copy(alpha = 0.22f))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${entry.progressPercent}%",
                                            color = NeliMagenta,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                // Exact last line & timestamp where user ended watching
                                Text(
                                    text = entry.lastLineLabel,
                                    color = NeliGenreCyan,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                LinearProgressIndicator(
                                    progress = { (entry.progressPercent / 100f).coerceIn(0.02f, 1f) },
                                    color = NeliMagenta,
                                    trackColor = Color(0x33FFFFFF),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(CircleShape)
                                )

                                // Quick inline 5-star rating for watched items
                                val currentStars = userRatings.firstOrNull { it.mediaId == entry.id }?.stars ?: 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = if (currentStars > 0) "Your rating:" else "Rate:",
                                        color = NeliTextSecondary,
                                        fontSize = 10.sp
                                    )
                                    for (star in 1..5) {
                                        Icon(
                                            imageVector = if (star <= currentStars) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Rate $star stars",
                                            tint = if (star <= currentStars) Color(0xFFFBBF24) else NeliTextSecondary,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    NeliWatchProgressManager.saveUserRating(context, entry.id, entry.title, star)
                                                }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { NeliWatchProgressManager.removeContinueWatching(context, entry.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove from Continue Watching",
                                    tint = NeliTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0x22FFFFFF))

        // 2. Watchlist & Favorites / Bookmarks
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Watchlist & Favorites (${watchlist.size})",
                    color = NeliTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (watchlist.isEmpty()) {
                Text(
                    text = "Tap the Bookmark/Favorite icon on any Movie, Series, or Live Channel to save it here.",
                    color = NeliTextSecondary,
                    fontSize = 12.sp
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(watchlist, key = { it.id }) { wItem ->
                        Column(
                            modifier = Modifier
                                .width(116.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(NeliSurfaceVariant)
                                .border(1.dp, Color(0x33A855F7), RoundedCornerShape(14.dp))
                                .clickable { onPlayWatchlistItem(wItem) }
                                .padding(8.dp)
                                .testTag("account_watchlist_card_${wItem.id}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF151B36))
                            ) {
                                AsyncImage(
                                    model = wItem.posterUrl,
                                    contentDescription = wItem.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = NeliMagenta,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(15.dp)
                                )
                            }
                            Text(
                                text = wItem.title,
                                color = NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = wItem.genre.ifBlank { wItem.type.uppercase() },
                                color = NeliGenreCyan,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Audio Quality / Language Preference selector inside Playback & Preferences.
 */
@Composable
fun NeliAccountAudioPreferenceRow(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by NeliWatchProgressManager.preferences.collectAsState()
    val audioOptions = listOf("AUTO" to "Auto Audio", "SWAHILI" to "Swahili", "ORIGINAL" to "Original HD")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                tint = NeliWarmOrange,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Audio Track & Sound Quality",
                color = NeliTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            audioOptions.forEach { (key, label) ->
                val selected = prefs.audioPreference.equals(key, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) NeliWarmOrange else NeliSurfaceVariant)
                        .border(
                            1.dp,
                            if (selected) NeliWarmOrange else Color(0x44A855F7),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { NeliWatchProgressManager.setAudioPreference(context, key) }
                        .padding(vertical = 9.dp, horizontal = 6.dp)
                        .testTag("audio_pref_${key.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else NeliTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Account & Security Card:
 * - Login method (Email / Phone / Google) & Password Protection badge
 * - Connected Devices & Active Sessions
 * - Notification Toggles (Daily Movies, Live Sports, Notification Sound)
 */
@Composable
fun NeliAccountSecurityAndNotificationsSection(
    currentUser: UserAccountEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by NeliWatchProgressManager.preferences.collectAsState()
    val deviceName = remember {
        val mfg = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        if (model.startsWith(mfg, ignoreCase = true)) model else "$mfg $model".trim().ifBlank { "Android Device" }
    }
    val loginCredentialSummary = remember(currentUser) {
        val email = currentUser?.email.orEmpty()
        when {
            email.endsWith("@nelitv.phone", ignoreCase = true) -> "Phone: ${email.substringBefore("@")}"
            email.isNotBlank() -> email
            else -> "Not signed in"
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("account_security_notifications_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Color(0xFF34D399),
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = "Account & Security",
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Credentials • Active Session • Notifications",
                    color = NeliTextSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // Active Connected Device & Session Box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NeliSurfaceVariant)
                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = "Connected Device",
                tint = NeliGenreCyan,
                modifier = Modifier.size(22.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "This Device: $deviceName (Android ${Build.VERSION.RELEASE})",
                    color = NeliTextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = loginCredentialSummary,
                    color = NeliGenreCyan,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x2E10B981))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    color = Color(0xFF34D399),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        HorizontalDivider(color = Color(0x22FFFFFF))

        // Notification Toggles
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = NeliMagenta,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Notifications & Alerts",
                color = NeliTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        SecurityToggleRow(
            title = "Daily Movie Recommendations",
            subtitle = "Get movie poster notifications for fresh picks",
            checked = prefs.dailyMovieNotificationsEnabled,
            onCheckedChange = { NeliWatchProgressManager.setNotificationToggles(context, dailyMovies = it) },
            testTag = "toggle_daily_movie_notifications"
        )
        SecurityToggleRow(
            title = "Live Sports & Big Match Alerts",
            subtitle = "Azam Sports kickoff reminders & breaking live alerts",
            checked = prefs.liveSportsNotificationsEnabled,
            onCheckedChange = { NeliWatchProgressManager.setNotificationToggles(context, liveSports = it) },
            testTag = "toggle_live_sports_notifications"
        )
        SecurityToggleRow(
            title = "Notification Sound & Vibration",
            subtitle = "Play audible alert chime when notifications arrive",
            checked = prefs.notificationSoundEnabled,
            onCheckedChange = { NeliWatchProgressManager.setNotificationToggles(context, soundEnabled = it) },
            testTag = "toggle_notification_sound"
        )
    }
}

@Composable
private fun SecurityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(text = title, color = NeliTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = NeliTextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NeliMagenta
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

/**
 * Watch NeliTv on PC & Desktop Section placed cleanly at the bottom of the Account page
 * (migrated from the TopNavBar header icon).
 */
@Composable
fun NeliWatchOnPcAccountSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF101935), Color(0xFF0D1329))
                )
            )
            .border(1.2.dp, Color(0x6600E5FF), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("account_watch_on_pc_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0x2600E5FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = "Watch on PC",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Watch NeliTv on PC & Desktop",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Nelitv.online • Web Browser & Computer App",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Text(
            text = "Nelitv inapatikana pia kwenye Kompyuta (PC & Laptop), Desktop na Web Browser! Fungua Chrome, Edge au Firefox na tembelea Nelitv.online ili kutazama Live TV na Movies kwenye skrini kubwa.",
            color = Color(0xFFCBD5E1),
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )

        Button(
            onClick = {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://nelitv.online"))
                    context.startActivity(webIntent)
                } catch (_: Throwable) {
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("open_web_app_button")
        ) {
            Icon(
                imageVector = Icons.Default.OpenInBrowser,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Fungua Nelitv.online (PC Web App)",
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = 13.5.sp
            )
        }
    }
}
