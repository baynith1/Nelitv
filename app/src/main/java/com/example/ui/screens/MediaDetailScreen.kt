package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.MediaContentRepository
import com.example.data.TmdbRepository
import com.example.model.CastMember
import com.example.model.DownloadQualityOption
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.components.NeliAdaptiveBannerAd
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliDurationViolet
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

@Composable
fun MediaDetailScreen(
    media: MediaContent,
    episodes: List<EpisodeItem>,
    recommendedMedia: List<MediaContent> = emptyList(),
    isInWatchlist: Boolean,
    isDownloaded: Boolean,
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadBannerMessage: String? = null,
    onBack: () -> Unit,
    onPlayChannel: (LiveChannel) -> Unit,
    onToggleWatchlist: (MediaContent) -> Unit,
    onDownloadMedia: (MediaContent) -> Unit,
    onDownloadEpisode: (EpisodeItem) -> Unit,
    onSelectRecommendedMedia: (MediaContent) -> Unit = {},
    onOpenDownloadsTab: () -> Unit = {},
    onDismissDownloadBanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var tmdbEnrichedMedia by remember(media) { mutableStateOf(media) }
    LaunchedEffect(media.id) {
        val enriched = TmdbRepository.enrichMediaContent(media)
        if (enriched != media) {
            tmdbEnrichedMedia = enriched
            MediaContentRepository.updateSingleEnrichedMedia(enriched)
        }
    }
    @Suppress("NAME_SHADOWING")
    val media = tmdbEnrichedMedia

    val availableSeasons = remember(media.seasons, episodes) {
        if (media.seasons.isNotEmpty()) {
            media.seasons.map { it.seasonNumber }.distinct().sorted()
        } else {
            episodes.map { it.seasonNumber }.distinct().sorted()
        }
    }

    var selectedSeasonNumber by remember(media.id, availableSeasons) {
        mutableIntStateOf(availableSeasons.lastOrNull() ?: 1)
    }

    val filteredEpisodes = remember(episodes, selectedSeasonNumber, availableSeasons) {
        val bySeason = episodes.filter { it.seasonNumber == selectedSeasonNumber }
        bySeason.ifEmpty { episodes }
    }

    var activeEpisodeIndex by remember(media.id, selectedSeasonNumber, filteredEpisodes) {
        mutableIntStateOf(0)
    }

    val currentVodEpisode = filteredEpisodes.getOrNull(
        activeEpisodeIndex.coerceIn(0, (filteredEpisodes.size - 1).coerceAtLeast(0))
    )

    val mediaDownloadingPct = if (media.isSeries && currentVodEpisode != null) {
        downloadProgress[currentVodEpisode.id] ?: downloadProgress[media.id]
    } else {
        downloadProgress[media.id]
    }
    val isMediaAlreadyDownloaded = if (media.isSeries && currentVodEpisode != null) {
        downloadedIds.contains(currentVodEpisode.id)
    } else {
        isDownloaded || downloadedIds.contains(media.id)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .testTag("media_detail_screen")
    ) {
        // Pinned Status-Bar-Safe Top Bar so content never collides with phone's top status bar or notification icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF120426))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("detail_back_button")
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA2B1055))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Text(
                text = media.title,
                color = NeliTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )

            IconButton(
                onClick = { onToggleWatchlist(media) },
                modifier = Modifier
                    .testTag("detail_watchlist_button")
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA2B1055))
            ) {
                Icon(
                    imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                    tint = if (isInWatchlist) NeliMagenta else Color.White
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Responsive Hero Backdrop Header
            item {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val isTablet = maxWidth >= 600.dp
                    val headerHeight = if (isTablet) 300.dp else (maxWidth * 0.56f).coerceIn(210.dp, 260.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(media.backdropUrl.ifBlank { media.posterUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = media.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            error = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(NeliSurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = null,
                                        tint = NeliMagenta,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x5514052B),
                                            Color(0x4414052B),
                                            NeliBackground
                                        )
                                    )
                                )
                        )

                        // Floating Play Button on Backdrop
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(NeliMagenta)
                                .clickable {
                                    if (media.isSeries && currentVodEpisode != null) {
                                        onPlayChannel(currentVodEpisode.toPlayableChannel(media.title))
                                    } else {
                                        onPlayChannel(media.toPlayableChannel())
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Now",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                }
            }

        // Live Download Confirmation / Progress Banner
        if (!downloadBannerMessage.isNullOrBlank() || mediaDownloadingPct != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliCardPurple)
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .testTag("download_status_banner"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Icon(
                                imageVector = if (mediaDownloadingPct != null && mediaDownloadingPct < 100) {
                                    Icons.Default.Downloading
                                } else {
                                    Icons.Default.DownloadDone
                                },
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = downloadBannerMessage
                                    ?: "Downloading ${media.title} (${mediaDownloadingPct ?: 0}%)...",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "View Downloads →",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenDownloadsTab() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("view_downloads_shortcut")
                            )
                            IconButton(
                                onClick = onDismissDownloadBanner,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = NeliTextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    if (mediaDownloadingPct != null) {
                        LinearProgressIndicator(
                            progress = { (mediaDownloadingPct.coerceIn(0, 100)) / 100f },
                            color = NeliMagenta,
                            trackColor = NeliSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Title, Badges & Action Buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeliGenreCyan)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = media.genre.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeliDurationViolet)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = media.duration,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeliMagenta)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = media.rating,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (media.narrated && media.narrationLanguage.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = media.narrationLanguage.uppercase(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = media.title,
                    color = NeliTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                if (media.originalTitle.isNotBlank() && media.originalTitle != media.title) {
                    Text(
                        text = "${media.originalTitle} • ${media.releaseYear}",
                        color = NeliTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Play & Offline Download (with Quality Picker) Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (media.isSeries && currentVodEpisode != null) {
                                onPlayChannel(currentVodEpisode.toPlayableChannel(media.title))
                            } else {
                                onPlayChannel(media.toPlayableChannel())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("detail_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (media.isSeries && currentVodEpisode != null) {
                                "Watch S${currentVodEpisode.seasonNumber}E${currentVodEpisode.episodeNumber}"
                            } else if (media.isSeries) {
                                "Watch Series"
                            } else {
                                "Watch Movie"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (media.downloadEnabled) {
                        OutlinedButton(
                            onClick = {
                                if (isMediaAlreadyDownloaded) {
                                    onOpenDownloadsTab()
                                } else if (mediaDownloadingPct == null) {
                                    // Immediately start downloading using the streaming link's quality
                                    if (media.isSeries && currentVodEpisode != null) {
                                        onDownloadEpisode(currentVodEpisode)
                                    } else {
                                        onDownloadMedia(media)
                                    }
                                }
                            },
                            enabled = mediaDownloadingPct == null,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("detail_download_button")
                        ) {
                            Icon(
                                imageVector = when {
                                    isMediaAlreadyDownloaded -> Icons.Default.CheckCircle
                                    mediaDownloadingPct != null -> Icons.Default.Downloading
                                    else -> Icons.Default.Download
                                },
                                contentDescription = null,
                                tint = if (isMediaAlreadyDownloaded) Color(0xFF10B981) else NeliGenreCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    mediaDownloadingPct != null && mediaDownloadingPct < 100 -> "Downloading $mediaDownloadingPct%"
                                    isMediaAlreadyDownloaded -> "Downloaded"
                                    else -> "Download"
                                },
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Synopsis",
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = media.synopsis,
                    color = NeliTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )

                if (media.productionCountries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Production: ${media.productionCountries.joinToString(", ")}",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // MOVIE DETAILS: AdMob Banner above Cast & Crew
        item(key = "detail_admob_above_cast_${media.id}") {
            NeliAdaptiveBannerAd(
                placementKey = "movie_detail_above_cast_${media.id}"
            )
        }

        // Cast & Crew Section
        if (media.cast.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .testTag("detail_cast_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cast & Crew",
                            color = NeliTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${media.cast.size} Credits",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(media.cast, key = { it.id }) { member ->
                            CastMemberCard(member = member)
                        }
                    }
                }
            }
        }

        // Series Seasons & Episodes Section
        if (media.isSeries) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Seasons & Episodes",
                            color = NeliTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        if (filteredEpisodes.size > 1) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeliCardPurple)
                                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        filteredEpisodes.forEach { ep ->
                                            onDownloadEpisode(ep)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("download_all_season_episodes_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download All Season Episodes",
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Multi-Download Season (${filteredEpisodes.size})",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    if (media.seasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(media.seasons, key = { it.seasonNumber }) { season ->
                                val isSelected = season.seasonNumber == selectedSeasonNumber
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) NeliMagenta else NeliCardPurple)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) NeliMagenta else Color(0x44A855F7),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { selectedSeasonNumber = season.seasonNumber }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${season.name} (${season.episodeCount} Eps)",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            items(filteredEpisodes, key = { it.id }) { ep ->
                val isEpDownloaded = downloadedIds.contains(ep.id)
                val epProgress = downloadProgress[ep.id]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeliSurface)
                        .border(1.dp, Color(0x33A855F7), RoundedCornerShape(16.dp))
                        .clickable {
                            val idx = filteredEpisodes.indexOfFirst { it.id == ep.id }
                            if (idx >= 0) activeEpisodeIndex = idx
                            onPlayChannel(ep.toPlayableChannel(media.title))
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeliSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(ep.stillPath.ifBlank { media.backdropUrl })
                                .crossfade(true)
                                .build(),
                            contentDescription = ep.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(NeliMagenta.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Episode",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "S${ep.seasonNumber} • E${ep.episodeNumber}",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (ep.narrated && ep.narrationLanguage.isNotBlank()) {
                                Text(
                                    text = "• ${ep.narrationLanguage}",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "• ${ep.durationLabel}",
                                color = NeliTextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = ep.name,
                            color = NeliTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (ep.overview.isNotBlank()) {
                            Text(
                                text = ep.overview,
                                color = NeliTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (ep.downloadEnabled) {
                        IconButton(
                            onClick = {
                                if (isEpDownloaded) {
                                    onOpenDownloadsTab()
                                } else if (epProgress == null) {
                                    onDownloadEpisode(ep)
                                }
                            },
                            enabled = epProgress == null,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("download_episode_${ep.id}")
                        ) {
                            Icon(
                                imageVector = when {
                                    isEpDownloaded -> Icons.Default.CheckCircle
                                    epProgress != null -> Icons.Default.Downloading
                                    else -> Icons.Default.Download
                                },
                                contentDescription = if (isEpDownloaded) "Downloaded Offline" else "Download Episode",
                                tint = if (isEpDownloaded) Color(0xFF10B981) else NeliGenreCyan
                            )
                        }
                    }
                }
            }
        }

        // Recommended Movies & Series Section
        if (recommendedMedia.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .testTag("detail_recommended_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recommended Movies & Series",
                            color = NeliTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "More Like This",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(recommendedMedia, key = { "rec_${it.id}" }) { recItem ->
                            MediaPosterCard(
                                media = recItem,
                                onClick = { onSelectRecommendedMedia(recItem) }
                            )
                        }
                    }
                }
            }
        }

        // MOVIE DETAILS / SERIES: One banner below the main movie information / recommendations
        item(key = "detail_admob_banner_${media.id}") {
            NeliAdaptiveBannerAd(
                placementKey = if (media.isSeries) {
                    "series_detail_below_section_${media.id}"
                } else {
                    "movie_detail_below_recommendations_${media.id}"
                }
            )
        }
        }
    }
}

@Composable
private fun CastMemberCard(member: CastMember) {
    Column(
        modifier = Modifier
            .width(114.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x33A855F7), RoundedCornerShape(16.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NeliCardPurple)
                .border(1.5.dp, NeliMagenta.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (member.avatarUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(member.avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NeliMagenta,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                )
            } else {
                Text(
                    text = member.name.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Text(
            text = member.name,
            color = NeliTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = member.role,
            color = NeliGenreCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DownloadQualityDialog(
    title: String,
    isEpisode: Boolean,
    selectedQuality: DownloadQualityOption,
    onSelectQuality: (DownloadQualityOption) -> Unit,
    onConfirmDownload: (DownloadQualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(NeliSurface)
                .border(1.5.dp, NeliMagenta.copy(alpha = 0.7f), RoundedCornerShape(22.dp))
                .padding(20.dp)
                .testTag("download_quality_dialog"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Select Download Quality",
                            color = NeliTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = title,
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = NeliTextSecondary
                    )
                }
            }

            Text(
                text = "Choose video quality to save directly to your phone's internal storage for offline viewing without internet:",
                color = NeliTextSecondary,
                fontSize = 12.sp
            )

            DownloadQualityOption.entries.forEach { option ->
                val isSelected = option == selectedQuality
                val sizeEstimate = if (isEpisode) option.estimatedEpisodeSize else option.estimatedMovieSize

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) NeliCardPurple else NeliSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) NeliMagenta else Color(0x33A855F7),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable {
                            onSelectQuality(option)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("quality_option_${option.qualityKey}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectQuality(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = NeliMagenta,
                                unselectedColor = NeliTextSecondary
                            )
                        )
                        Column {
                            Text(
                                text = option.label,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = option.description,
                                color = NeliTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) NeliMagenta else Color(0x552B1055))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = sizeEstimate,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { onConfirmDownload(selectedQuality) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp)
                        .testTag("confirm_download_quality_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Download ${selectedQuality.resolutionBadge}",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
