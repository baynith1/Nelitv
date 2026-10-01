package com.example.ui.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.NeliStartIoAdManager
import com.example.ads.StartIoAdFormat
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

/**
 * 1. START.IO BANNER AD SLOT (320x50 Standard Banner)
 * Embeds the real Start.io `Banner` view when an Activity context is available and provides
 * an interactive Start.io Banner container across Homepage, Discovery, Search, Downloads & Movie Details.
 */
@Composable
fun StartIoBannerAdSlot(
    placementTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nativeAds by NeliStartIoAdManager.nativeAds.collectAsState()
    val bannerPromo = remember(nativeAds, placementTag) {
        NeliStartIoAdManager.getNativeAdForSlot(placementTag.hashCode())
    }
    var sdkBannerLoaded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF13192B), Color(0xFF1A1030))
                )
            )
            .border(1.dp, NeliGenreCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable {
                NeliStartIoAdManager.handleNativeAdClick(context, bannerPromo)
            }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("startio_banner_ad_$placementTag")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(NeliGenreCyan)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AD • START.IO",
                        color = Color(0xFF090B10),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bannerPromo.title,
                        color = NeliTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = bannerPromo.description,
                        color = NeliTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NeliMagenta)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = bannerPromo.callToAction,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Embed native Start.io SDK Banner View when running in an Activity
        if (context is Activity) {
            AndroidView(
                factory = { ctx ->
                    NeliStartIoAdManager.createBannerAdView(
                        context = ctx,
                        onBannerReceived = { sdkBannerLoaded = true },
                        onBannerFailed = { sdkBannerLoaded = false }
                    ) ?: android.widget.FrameLayout(ctx)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (sdkBannerLoaded) 52.dp else 1.dp)
            )
        }
    }
}

/**
 * 2. START.IO NATIVE AD CARD (`StartAppNativeAd` / `NativeAdDetails`)
 * Blended in-feed Native Ad card for Discovery, Movie Details, Search & Account screens.
 */
@Composable
fun StartIoNativeAdCard(
    slotIndex: Int,
    placementTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nativeAds by NeliStartIoAdManager.nativeAds.collectAsState()
    val adItem = remember(nativeAds, slotIndex) {
        NeliStartIoAdManager.getNativeAdForSlot(slotIndex)
    }

    LaunchedEffect(adItem.id) {
        NeliStartIoAdManager.recordNativeAdImpression(context, adItem)
    }

    val imageRequest = remember(adItem.id, adItem.imageUrl) {
        ImageRequest.Builder(context)
            .data(adItem.imageUrl)
            .size(600, 320)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NeliSurface)
            .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable {
                NeliStartIoAdManager.handleNativeAdClick(context, adItem)
            }
            .padding(14.dp)
            .testTag("startio_native_ad_$placementTag"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeliMagenta)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SPONSORED • START.IO NATIVE AD",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "App ID: ${NeliStartIoAdManager.STARTIO_APP_ID}",
                    color = NeliGenreCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", adItem.rating),
                    color = NeliTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .height(74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeliSurfaceVariant)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = adItem.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = adItem.title,
                    color = NeliTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = adItem.description,
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${adItem.category} • ${adItem.installsLabel}",
                    color = NeliGenreCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    NeliStartIoAdManager.handleNativeAdClick(context, adItem)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = adItem.callToAction,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * 3. START.IO INLINE MUTED VIDEO / MREC AD CARD (300x250)
 * Placed after every 6 channels in the vertical "All Channels" feed on Homepage.
 * Muted by default so it never disrupts the user while scrolling.
 */
@Composable
fun StartIoInlineMutedVideoAdCard(
    blockIndex: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nativeAds by NeliStartIoAdManager.nativeAds.collectAsState()
    val adItem = remember(nativeAds, blockIndex) {
        NeliStartIoAdManager.getNativeAdForSlot(blockIndex)
    }
    var isMuted by rememberSaveable(blockIndex) { mutableStateOf(true) }
    var sdkMrecLoaded by remember { mutableStateOf(false) }

    val backdropRequest = remember(adItem.id, adItem.imageUrl) {
        ImageRequest.Builder(context)
            .data(adItem.imageUrl)
            .size(640, 360)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF101524))
            .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .clickable {
                NeliStartIoAdManager.handleNativeAdClick(context, adItem)
            }
            .padding(12.dp)
            .testTag("startio_inline_video_ad_$blockIndex"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeliGenreCyan)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "START.IO MREC / MUTED VIDEO AD",
                        color = Color(0xFF090B10),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = "Every 6 Channels • Block #${blockIndex + 1}",
                    color = NeliTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Mute / Unmute toggle (Muted by default)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeliSurfaceVariant)
                    .clickable { isMuted = !isMuted }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("startio_inline_ad_mute_toggle_$blockIndex"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (isMuted) "Muted Ad Audio" else "Unmuted Ad Audio",
                    tint = if (isMuted) Color(0xFFFBBF24) else Color(0xFF10B981),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (isMuted) "Muted" else "Audio On",
                    color = NeliTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(NeliSurfaceVariant)
        ) {
            AsyncImage(
                model = backdropRequest,
                contentDescription = adItem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x22000000), Color(0xDD090B10))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = adItem.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = adItem.description,
                        color = NeliTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeliMagenta)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = adItem.callToAction,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        if (context is Activity) {
            AndroidView(
                factory = { ctx ->
                    NeliStartIoAdManager.createMrecAdView(
                        context = ctx,
                        onMrecReceived = { sdkMrecLoaded = true },
                        onMrecFailed = { sdkMrecLoaded = false }
                    ) ?: android.widget.FrameLayout(ctx)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (sdkMrecLoaded) 250.dp else 1.dp)
            )
        }
    }
}

/**
 * 4. ACCOUNT PAGE START.IO ADS CENTER, ALL AD FORMATS & STEP-BY-STEP INSTRUCTIONS CARD
 * Displays App ID `209957114`, `app-ads.txt` verification (`start.io, 161782875, DIRECT`),
 * interactive controls for Banner, Native, MREC, Interstitial & Rewarded Video Ads,
 * and complete step-by-step instructions.
 */
@Composable
fun NeliStartIoAdsCenterAndInstructionsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adStatusMessage by NeliStartIoAdManager.adStatusMessage.collectAsState()
    val testAdsEnabled by NeliStartIoAdManager.testAdsEnabled.collectAsState()
    val interstitialCount by NeliStartIoAdManager.interstitialShownCount.collectAsState()
    val rewardedCount by NeliStartIoAdManager.rewardedVideoCompletedCount.collectAsState()
    val vipBoostUntilMs by NeliStartIoAdManager.vipBoostActiveUntilMs.collectAsState()

    val isAppAdsVerified = remember { NeliStartIoAdManager.isStartIoAppAdsTxtVerified(context) }
    val sellerLinesCount = remember { NeliStartIoAdManager.readAppAdsTxtLines(context).size }
    var showInstructions by rememberSaveable { mutableStateOf(true) }
    var showAppAdsTxtPreview by rememberSaveable { mutableStateOf(false) }

    val isVipBoostActive = vipBoostUntilMs > System.currentTimeMillis()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF121A2E), Color(0xFF1A1030))
                )
            )
            .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(16.dp)
            .testTag("startio_ads_center_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Row
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeliMagenta),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column {
                    Text(
                        text = "Start.io Ads Center & Instructions",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "App ID: ${NeliStartIoAdManager.STARTIO_APP_ID} • Direct ID: ${NeliStartIoAdManager.STARTIO_PUBLISHER_ID}",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2610B981))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isAppAdsVerified) "VERIFIED ✓" else "ACTIVE",
                    color = Color(0xFF10B981),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Status Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x2210B981))
                .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                .padding(10.dp)
                .testTag("startio_status_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = adStatusMessage,
                    color = Color(0xFFA7F3D0),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "app-ads.txt: ${NeliStartIoAdManager.STARTIO_DIRECT_APP_ADS_ENTRY} ($sellerLinesCount authorized lines)",
                    color = NeliTextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        if (isVipBoostActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33F59E0B))
                    .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "VIP Ultra-HD Stream Boost Active (Earned via Start.io Rewarded Video!)",
                    color = Color(0xFFFDE68A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // All 6 Start.io Ad Types Grid/List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NeliSurfaceVariant)
                .border(1.dp, NeliBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "ACTIVE START.IO AD FORMATS (APP ID: 209957114)",
                color = NeliGenreCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )

            StartIoAdFormat.entries.forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeliSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(NeliCardPurple)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = format.formatBadge,
                                    color = NeliGenreCyan,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = format.title,
                                color = NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = format.placementSummary,
                            color = NeliTextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Interactive Action Buttons: Test Interstitial, Watch Rewarded Video, Refresh Native Ads & Copy app-ads.txt
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    NeliStartIoAdManager.showInterstitialAd(context, forceShow = true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("startio_show_interstitial_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Interstitial ($interstitialCount)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Button(
                onClick = {
                    NeliStartIoAdManager.showRewardedVideoAd(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("startio_show_rewarded_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Rewarded ($rewardedCount)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    NeliStartIoAdManager.copyAppAdsTxtToClipboard(context)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("startio_copy_app_ads_txt_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Copy app-ads.txt ($sellerLinesCount)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    NeliStartIoAdManager.loadNativeAds(context, count = 3)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("startio_refresh_native_ads_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Refresh Native Ads",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Toggle Test Ads Mode
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeliSurfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Start.io Test Ads Mode",
                    color = NeliTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Keep OFF for real earnings with App ID 209957114; turn ON only when testing ad placements",
                    color = NeliTextSecondary,
                    fontSize = 10.sp
                )
            }
            Switch(
                checked = testAdsEnabled,
                onCheckedChange = { enabled ->
                    NeliStartIoAdManager.setTestAdsMode(context, enabled)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NeliMagenta
                ),
                modifier = Modifier.testTag("startio_test_ads_switch")
            )
        }

        // Collapsible Step-by-Step Start.io Setup & Monetization Instructions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeliCardPurple)
                .clickable { showInstructions = !showInstructions }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("startio_toggle_instructions_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Start.io Setup & Monetization Instructions (Step 1–5)",
                color = NeliGenreCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Icon(
                imageVector = if (showInstructions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = "Toggle Instructions",
                tint = NeliGenreCyan
            )
        }

        if (showInstructions) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C101C))
                    .border(1.dp, Color(0x3300D2FF), RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .testTag("startio_instructions_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                NeliStartIoAdManager.setupInstructions.forEach { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeliSurface)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Step ${step.stepNumber}: ${step.title}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = step.details,
                            color = NeliTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF080A12))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = step.codeOrConfigSnippet,
                                color = NeliGenreCyan,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAppAdsTxtPreview = !showAppAdsTxtPreview },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("startio_toggle_app_ads_preview_button")
                ) {
                    Text(
                        text = if (showAppAdsTxtPreview) {
                            "Hide app-ads.txt Seller Lines"
                        } else {
                            "View All $sellerLinesCount app-ads.txt Seller Lines (start.io, 161782875, DIRECT)"
                        },
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showAppAdsTxtPreview) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF080A12))
                            .border(1.dp, NeliBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                            .testTag("startio_app_ads_txt_box")
                    ) {
                        Text(
                            text = NeliStartIoAdManager.embeddedAppAdsTxtContent,
                            color = Color(0xFFA7F3D0),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}
