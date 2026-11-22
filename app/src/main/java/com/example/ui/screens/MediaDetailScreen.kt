package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.AdMobBannerAd
import com.example.ads.AdMobNativeCardAd
import com.example.ads.NeliAdManager
import com.example.data.MediaContentRepository
import com.example.data.TmdbRepository
import com.example.model.CastMember
import com.example.model.DownloadQualityOption
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliWarmOrange
import com.example.ui.theme.rememberNeliScreenProfile

@OptIn(ExperimentalLayoutApi::class)
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
    onDownloadMediaWithQuality: (MediaContent, DownloadQualityOption) -> Unit = { m, _ -> onDownloadMedia(m) },
    onDownloadEpisodeWithQuality: (EpisodeItem, DownloadQualityOption) -> Unit = { ep, _ -> onDownloadEpisode(ep) },
    onSelectRecommendedMedia: (MediaContent) -> Unit = {},
    onOpenDownloadsTab: () -> Unit = {},
    onDismissDownloadBanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var showQualityDialogForMedia by remember { mutableStateOf(false) }
    var pendingEpisodeForQualityDialog by remember { mutableStateOf<EpisodeItem?>(null) }
    var selectedQualityOption by remember { mutableStateOf(DownloadQualityOption.HIGH_720P) }

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

    val detailContext = LocalContext.current
    val screenProfile = rememberNeliScreenProfile()
    val isNarrow = screenProfile.isSmallPhone || screenProfile.widthDp < 380

    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val floatingHeaderTopPadding = maxOf(cutoutTop, statusTop, 14.dp)

    val triggerPrimaryPlay: () -> Unit = {
        if (media.isSeries && currentVodEpisode != null) {
            onPlayChannel(currentVodEpisode.toPlayableChannel(media.title))
        } else {
            onPlayChannel(media.toPlayableChannel())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("media_detail_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 84.dp)
        ) {
            // 1. Full-Bleed Cinema Hero Artwork with Floating Glass Top Controls & Overlapping Circular Play Trigger
            item(key = "detail_cinema_hero") {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isTablet = maxWidth >= 600.dp
                    val heroHeight = if (isTablet) 360.dp else (maxWidth * 0.78f).coerceIn(275.dp, 345.dp)
                    val heroBackdropUrl = remember(media.id, media.backdropUrl, media.posterUrl, media.streamUrl) {
                        MediaContentRepository.resolveGuaranteedMediaImageUrl(
                            media.backdropUrl,
                            media.posterUrl,
                            media.streamUrl
                        )
                    }
                    val heroRequest = remember(media.id, heroBackdropUrl) {
                        ImageRequest.Builder(detailContext)
                            .data(heroBackdropUrl)
                            .crossfade(true)
                            .build()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(heroHeight + 28.dp)
                    ) {
                        // Rounded bottom-edge Cinema Backdrop
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(heroHeight)
                                .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
                                .background(NeliSurfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = NeliMagenta.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                            )
                            AsyncImage(
                                model = heroRequest,
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Atmospheric top + bottom gradient scrim for effortless readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color(0x88070913),
                                                0.28f to Color.Transparent,
                                                0.68f to Color(0x55070913),
                                                1.0f to NeliBackground
                                            )
                                        )
                                    )
                            )

                            // Floating Glass Back & Bookmark Buttons over Full-Bleed Artwork
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = floatingHeaderTopPadding)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .testTag("detail_back_button")
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x880D1226))
                                        .border(1.dp, Color(0x44FFFFFF), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleWatchlist(media) },
                                    modifier = Modifier
                                        .testTag("detail_watchlist_button")
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x880D1226))
                                        .border(
                                            width = 1.dp,
                                            color = if (isInWatchlist) NeliMagenta else Color(0x44FFFFFF),
                                            shape = CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = if (isInWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                                        tint = if (isInWatchlist) NeliMagenta else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        // Centered Overlapping Circular Play Button (matching Reference Image 1 right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .size(66.dp)
                                .shadow(
                                    elevation = 16.dp,
                                    shape = CircleShape,
                                    ambientColor = NeliMagenta,
                                    spotColor = NeliMagenta
                                )
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(NeliMagenta, NeliWarmOrange)
                                    )
                                )
                                .border(3.dp, NeliBackground, CircleShape)
                                .clickable(onClick = triggerPrimaryPlay),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Now",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // 2. Clean Structured Metadata Header: Release Year, Title, Pill Chips, Rating Row & Gradient CTA
            item(key = "detail_metadata_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isNarrow) 16.dp else 20.dp)
                        .padding(top = 8.dp)
                ) {
                    // Eyebrow release date / original title
                    val eyebrowText = buildString {
                        if (media.releaseYear.isNotBlank()) append(media.releaseYear)
                        if (media.originalTitle.isNotBlank() && media.originalTitle != media.title) {
                            if (isNotEmpty()) append("  •  ")
                            append(media.originalTitle)
                        }
                    }
                    if (eyebrowText.isNotBlank()) {
                        Text(
                            text = eyebrowText,
                            color = NeliTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Bold Cinema Title
                    Text(
                        text = media.title,
                        color = NeliTextPrimary,
                        fontSize = if (isNarrow) 23.sp else 27.sp,
                        lineHeight = if (isNarrow) 28.sp else 33.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sleek Wrapped Metadata Pills (never overflows on Samsung narrow screens!)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (media.duration.isNotBlank()) {
                            DetailMetadataPill(label = media.duration)
                        }
                        if (media.primaryGenre.isNotBlank()) {
                            DetailMetadataPill(label = media.primaryGenre)
                        }
                        DetailMetadataPill(label = if (media.isSeries) "Series" else "Movie")
                        if (media.narrated && media.narrationLanguage.isNotBlank()) {
                            DetailMetadataPill(
                                label = media.narrationLanguage,
                                accentColor = Color(0xFF10B981),
                                leadingIcon = Icons.Default.GraphicEq
                            )
                        } else {
                            DetailMetadataPill(label = "HD")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Rating & Social Proof Strip (Image 1 & 2 right inspired)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurface.copy(alpha = 0.85f))
                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${media.rating}/10",
                                color = NeliTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "IMDb",
                                color = NeliTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = null,
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (media.narrated) "Swahili DJ" else "Top Pick",
                                    color = NeliTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Primary Full-Width Neli Gradient CTA + Sleek Download Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(NeliMagenta, NeliWarmOrange)
                                    )
                                )
                                .clickable(onClick = triggerPrimaryPlay)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("detail_play_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = when {
                                        isMediaAlreadyDownloaded -> "Watch Offline"
                                        media.isSeries && currentVodEpisode != null -> "Watch S${currentVodEpisode.seasonNumber}E${currentVodEpisode.episodeNumber}"
                                        media.isSeries -> "Watch Series"
                                        else -> "Watch Movie"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (media.isDownloadPermitted) {
                            val isActivelyDownloadingThis = !isMediaAlreadyDownloaded && mediaDownloadingPct != null && mediaDownloadingPct < 100
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 52.dp)
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(NeliSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isMediaAlreadyDownloaded) NeliMagenta else NeliGenreCyan.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(26.dp)
                                    )
                                    .clickable(enabled = !isActivelyDownloadingThis) {
                                        if (isMediaAlreadyDownloaded) {
                                            triggerPrimaryPlay()
                                        } else if (!isActivelyDownloadingThis) {
                                            if (media.isSeries && currentVodEpisode != null) {
                                                pendingEpisodeForQualityDialog = currentVodEpisode
                                            } else {
                                                showQualityDialogForMedia = true
                                            }
                                        }
                                    }
                                    .padding(horizontal = if (isNarrow) 14.dp else 18.dp, vertical = 12.dp)
                                    .testTag("detail_download_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isMediaAlreadyDownloaded -> Icons.Default.CheckCircle
                                            isActivelyDownloadingThis -> Icons.Default.Downloading
                                            else -> Icons.Default.Download
                                        },
                                        contentDescription = null,
                                        tint = if (isMediaAlreadyDownloaded) Color(0xFFFF4DA6) else NeliGenreCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = when {
                                            isActivelyDownloadingThis -> "$mediaDownloadingPct%"
                                            isMediaAlreadyDownloaded -> "Offline"
                                            else -> "Download"
                                        },
                                        color = NeliTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Google AdMob Banner Ad in Movie Details page
                    AdMobBannerAd(
                        adTag = "detail_page_banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                    )
                }
            }

            // 3. Cast & Crew Horizontal Rounded Portrait Cards (matching Reference Image 1 right)
            if (media.cast.isNotEmpty()) {
                item(key = "detail_cast_section_item") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("detail_cast_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isNarrow) 16.dp else 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cast",
                                color = NeliTextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "${media.cast.size} Credits",
                                color = NeliTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = if (isNarrow) 16.dp else 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(media.cast, key = { it.id }) { member ->
                                CastMemberCard(member = member)
                            }
                        }
                    }
                }
            }

            // 4. Synopsis Section (clean cinema typography like Reference Image 1 & 2 right)
            item(key = "detail_synopsis_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isNarrow) 16.dp else 20.dp)
                        .padding(top = 20.dp)
                ) {
                    Text(
                        text = "Synopsis",
                        color = NeliTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = media.synopsis,
                        color = NeliTextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )

                    if (media.productionCountries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Production • ${media.productionCountries.joinToString(", ")}",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 5. Google AdMob Native Advanced Ad Card in Media Details
            item(key = "detail_native_sponsored_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isNarrow) 16.dp else 20.dp, vertical = 12.dp)
                ) {
                    AdMobNativeCardAd(
                        adTag = "detail_page",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 6. Series Seasons & Episodes Section
            if (media.isSeries) {
                item(key = "detail_seasons_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isNarrow) 16.dp else 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes",
                                color = NeliTextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.weight(1f)
                            )

                            val downloadableEpisodes = filteredEpisodes.filter { it.isDownloadPermitted }
                            if (downloadableEpisodes.size > 1) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NeliCardPurple)
                                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            val act = detailContext as? android.app.Activity
                                            val batchAction = {
                                                downloadableEpisodes.forEach { ep ->
                                                    onDownloadEpisode(ep)
                                                }
                                            }
                                            if (act != null) {
                                                NeliAdManager.showDownloadRewardedAd(act) {
                                                    batchAction()
                                                }
                                            } else {
                                                batchAction()
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
                                        text = "Download All (${filteredEpisodes.size})",
                                        color = NeliGenreCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        if (media.seasons.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = if (isNarrow) 16.dp else 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(media.seasons, key = { it.seasonNumber }) { season ->
                                    val isSelected = season.seasonNumber == selectedSeasonNumber
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSelected) {
                                                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                                } else {
                                                    androidx.compose.ui.graphics.SolidColor(NeliSurfaceVariant)
                                                }
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) Color.Transparent else Color(0x33FFFFFF),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable { selectedSeasonNumber = season.seasonNumber }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "${season.name} (${season.episodeCount})",
                                            color = if (isSelected) Color.White else NeliTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                items(filteredEpisodes, key = { it.id }) { ep ->
                    val isEpDownloaded = downloadedIds.contains(ep.id)
                    val epProgress = downloadProgress[ep.id]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isNarrow) 16.dp else 20.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(NeliSurface)
                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
                            .clickable {
                                val idx = filteredEpisodes.indexOfFirst { it.id == ep.id }
                                if (idx >= 0) activeEpisodeIndex = idx
                                onPlayChannel(ep.toPlayableChannel(media.title))
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val epThumbUrl = remember(ep.id, ep.stillPath, media.backdropUrl, ep.streamUrl) {
                            MediaContentRepository.resolveGuaranteedMediaImageUrl(
                                ep.stillPath,
                                media.backdropUrl,
                                ep.streamUrl
                            )
                        }
                        val epThumbRequest = remember(ep.id, epThumbUrl) {
                            ImageRequest.Builder(detailContext)
                                .data(epThumbUrl)
                                .size(320, 180)
                                .crossfade(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .width(if (isNarrow) 104.dp else 120.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeliSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = epThumbRequest,
                                contentDescription = ep.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(NeliMagenta.copy(alpha = 0.92f)),
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
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "• ${ep.durationLabel}",
                                    color = NeliTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false
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

                        if (ep.isDownloadPermitted) {
                            val isEpActivelyDownloading = !isEpDownloaded && epProgress != null && epProgress < 100
                            IconButton(
                                onClick = {
                                    if (isEpDownloaded) {
                                        val idx = filteredEpisodes.indexOfFirst { it.id == ep.id }
                                        if (idx >= 0) activeEpisodeIndex = idx
                                        onPlayChannel(ep.toPlayableChannel(media.title))
                                    } else if (!isEpActivelyDownloading) {
                                        pendingEpisodeForQualityDialog = ep
                                    }
                                },
                                enabled = !isEpActivelyDownloading,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("download_episode_${ep.id}")
                            ) {
                                Icon(
                                    imageVector = when {
                                        isEpDownloaded -> Icons.Default.CheckCircle
                                        isEpActivelyDownloading -> Icons.Default.Downloading
                                        else -> Icons.Default.Download
                                    },
                                    contentDescription = if (isEpDownloaded) "Play Downloaded Episode Offline" else "Download Episode",
                                    tint = if (isEpDownloaded) Color(0xFF10B981) else NeliGenreCyan
                                )
                            }
                        }
                    }
                }
            }

            // 7. Recommended Movies & Series ("For You")
            if (recommendedMedia.isNotEmpty()) {
                item(key = "detail_recommended_section_item") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .testTag("detail_recommended_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (isNarrow) 16.dp else 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "For You",
                                color = NeliTextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "More Like This",
                                color = NeliGenreCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = if (isNarrow) 16.dp else 20.dp),
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
        }
    }

    if (showQualityDialogForMedia) {
        DownloadQualityDialog(
            title = media.title,
            isEpisode = false,
            selectedQuality = selectedQualityOption,
            onSelectQuality = { selectedQualityOption = it },
            onConfirmDownload = { quality ->
                showQualityDialogForMedia = false
                val startDownload = {
                    onDownloadMediaWithQuality(media, quality)
                }
                val act = detailContext as? android.app.Activity
                if (act != null) {
                    NeliAdManager.showDownloadRewardedAd(act) {
                        startDownload()
                    }
                } else {
                    startDownload()
                }
            },
            onDismiss = { showQualityDialogForMedia = false }
        )
    }

    val epToDownload = pendingEpisodeForQualityDialog
    if (epToDownload != null) {
        DownloadQualityDialog(
            title = "${media.title} • S${epToDownload.seasonNumber}E${epToDownload.episodeNumber}",
            isEpisode = true,
            selectedQuality = selectedQualityOption,
            onSelectQuality = { selectedQualityOption = it },
            onConfirmDownload = { quality ->
                pendingEpisodeForQualityDialog = null
                val startDownload = {
                    onDownloadEpisodeWithQuality(epToDownload, quality)
                }
                val act = detailContext as? android.app.Activity
                if (act != null) {
                    NeliAdManager.showDownloadRewardedAd(act) {
                        startDownload()
                    }
                } else {
                    startDownload()
                }
            },
            onDismiss = { pendingEpisodeForQualityDialog = null }
        )
    }
}

@Composable
private fun DetailMetadataPill(
    label: String,
    accentColor: Color? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                accentColor?.copy(alpha = 0.22f) ?: NeliSurfaceVariant
            )
            .border(
                width = 1.dp,
                color = accentColor?.copy(alpha = 0.55f) ?: Color(0x33FFFFFF),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = accentColor ?: NeliTextPrimary,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            color = accentColor ?: NeliTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CastMemberCard(member: CastMember) {
    Column(
        modifier = Modifier.width(102.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(NeliCardPurple)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = member.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (member.avatarUrl.isNotBlank()) {
                val castContext = LocalContext.current
                val avatarReq = remember(member.id, member.avatarUrl) {
                    ImageRequest.Builder(castContext)
                        .data(member.avatarUrl)
                        .size(220, 220)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = avatarReq,
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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

        if (member.role.isNotBlank()) {
            Text(
                text = member.role,
                color = NeliTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DownloadQualityDialog(
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
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
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
                        .heightIn(min = 48.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = { onConfirmDownload(selectedQuality) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.4f)
                        .heightIn(min = 48.dp)
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
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
