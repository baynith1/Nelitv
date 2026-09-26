package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.ChannelRepository
import com.example.model.LiveChannel
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

@Composable
fun ChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryLogoUrl = remember(channel.id, channel.thumbnailUrl) {
        ChannelRepository.resolveGuaranteedChannelLogoUrl(channel)
    }
    val fallbackLogoUrl = remember(channel.id, channel.name) {
        ChannelRepository.resolveFallbackChannelLogoUrl(channel)
    }
    var activeLogoUrl by remember(channel.id, primaryLogoUrl) {
        mutableStateOf(primaryLogoUrl)
    }
    var isLogoLoaded by remember(channel.id, activeLogoUrl) {
        mutableStateOf(false)
    }

    val thumbRequest = remember(channel.id, activeLogoUrl) {
        ImageRequest.Builder(context)
            .data(activeLogoUrl)
            .size(360, 200)
            .crossfade(false)
            .build()
    }
    val avatarRequest = remember(channel.id, activeLogoUrl) {
        ImageRequest.Builder(context)
            .data(activeLogoUrl)
            .size(84, 84)
            .crossfade(false)
            .build()
    }

    Card(
        modifier = modifier
            .testTag("channel_card_${channel.id}")
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(0.5.dp, NeliBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = NeliSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 16:9 YouTube-style Live Thumbnail Stage with high-visibility logo container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF141B2D), Color(0xFF1D253B))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!isLogoLoaded) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = NeliMagenta.copy(alpha = 0.65f),
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = channel.name.take(12).uppercase(),
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }

                // Luminous inner frame so both dark and light transparent PNG channel logos pop clearly
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(6.dp)
                        .testTag("channel_logo_stage_${channel.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = thumbRequest,
                        contentDescription = "${channel.name} logo",
                        contentScale = ContentScale.Fit,
                        onSuccess = { isLogoLoaded = true },
                        onError = {
                            if (activeLogoUrl != fallbackLogoUrl && fallbackLogoUrl.isNotBlank()) {
                                activeLogoUrl = fallbackLogoUrl
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // YouTube-style LIVE badge at bottom-right
                LiveIndicatorBadge(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }

            // YouTube-style Channel Avatar + Title & Category Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E263C))
                        .border(0.5.dp, NeliBorder, CircleShape)
                        .padding(3.dp)
                        .testTag("channel_avatar_logo_${channel.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (!isLogoLoaded) {
                        Text(
                            text = channel.name.trim().take(2).uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    AsyncImage(
                        model = avatarRequest,
                        contentDescription = "${channel.name} icon",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        color = NeliTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${channel.category} • Live",
                        color = NeliTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun LiveIndicatorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFF0033))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp
        )
    }
}
