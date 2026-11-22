package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import com.example.ui.tv.tvGeminiFocusable
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.math.absoluteValue
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.ui.theme.rememberNeliScreenProfile
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.FirebaseConfigEntity
import com.example.data.local.UserAccountEntity
import com.example.data.local.WatchlistItemEntity
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.ui.tv.tvCardGrow
import com.example.ui.tv.tvButtonGrow
import com.example.ui.tv.tvTileGrow
import com.example.ui.tv.LocalTvConfiguration
import com.example.model.MediaContent
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ChannelCard
import com.example.ui.components.NeliShareApkAndAutoUpdateSection
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliDurationViolet
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliNeonPurple
import com.example.ui.theme.NeliResponsiveLayout
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceHighlight
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextMuted
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager
import com.example.ui.theme.NeliWarmOrange

@Composable
fun MediaPosterCard(
    media: MediaContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenProfile = rememberNeliScreenProfile()
    val primaryImageUrl = remember(media.id, media.posterUrl, media.backdropUrl, media.streamUrl) {
        MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.posterUrl,
            media.backdropUrl,
            media.streamUrl
        )
    }
    val posterRequest = remember(media.id, primaryImageUrl) {
        ImageRequest.Builder(context)
            .data(primaryImageUrl)
            .size(300, 450)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .width(screenProfile.posterCardWidth)
            .tvGeminiFocusable(
                shape = RoundedCornerShape(16.dp),
                focusScale = 1.08f,
                borderWidth = 2.5.dp,
                onClick = onClick
            )
            .clip(RoundedCornerShape(16.dp))
            .background(NeliSurface)
            .border(
                width = 1.dp,
                color = if (media.isAdultContent) Color(0x66EF4444) else Color(0x33A855F7),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .testTag("media_card_${media.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(NeliSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = NeliMagenta.copy(alpha = 0.35f),
                modifier = Modifier.size(34.dp)
            )

            AsyncImage(
                model = posterRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE614052B))
                        )
                    )
            )

            // Top-left: Adult 18+, Swahili Narration, or Type badge
            if (media.isAdultContent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xEEEF4444))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "18+ ADULT",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else if (media.narrated && media.narrationLanguage.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xDD10B981))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Text(
                        text = media.narrationLanguage.uppercase(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(NeliDurationViolet.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (media.isSeries) "SERIES" else "MOVIE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Top-right: Rating badge
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeliMagenta.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = media.rating,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = media.title,
                color = NeliTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = media.primaryGenre,
                    color = NeliGenreCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = media.releaseYear,
                    color = NeliTextSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Guaranteed Poster & Thumbnail card specifically for Adult content so both poster and landscape
 * thumbnail images are always clearly visible.
 */
@Composable
fun AdultMediaPosterThumbnailCard(
    media: MediaContent,
    onSelectDetails: () -> Unit,
    onPlayDirect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val posterImageUrl = remember(media.id, media.posterUrl, media.backdropUrl, media.streamUrl) {
        MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.posterUrl,
            media.backdropUrl,
            media.streamUrl
        )
    }
    val thumbnailImageUrl = remember(media.id, media.backdropUrl, media.posterUrl, media.streamUrl) {
        MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.backdropUrl,
            media.posterUrl,
            media.streamUrl
        )
    }

    val thumbnailRequest = remember(media.id, thumbnailImageUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailImageUrl)
            .size(400, 225)
            .crossfade(false)
            .build()
    }
    val posterRequest = remember(media.id, posterImageUrl) {
        ImageRequest.Builder(context)
            .data(posterImageUrl)
            .size(120, 180)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .width(230.dp)
            .tvGeminiFocusable(
                shape = RoundedCornerShape(16.dp),
                focusScale = 1.08f,
                borderWidth = 2.5.dp,
                onClick = onSelectDetails
            )
            .clip(RoundedCornerShape(16.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x66EF4444), RoundedCornerShape(16.dp))
            .clickable { onSelectDetails() }
            .testTag("adult_card_${media.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(NeliSurfaceVariant)
        ) {
            // Full landscape thumbnail / backdrop image
            AsyncImage(
                model = thumbnailRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x22000000), Color(0xCC090A0F))
                        )
                    )
            )

            // Inset Poster Image on bottom-left so both Poster & Thumbnail are visible
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .width(46.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = posterRequest,
                    contentDescription = "${media.title} Poster",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 18+ Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xEEEF4444))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "18+ ADULT • HD",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // Play button overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NeliMagenta)
                    .clickable { onPlayDirect() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Adult Video",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = media.title,
                color = NeliTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${media.primaryGenre} • ${media.duration}",
                    color = NeliGenreCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = media.rating,
                    color = Color(0xFFEF4444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

const val DISCOVERY_MADJS_BANNER_TEXT = "Furahia Movie Nzuri kutoka kwa Madjs Wazuri"

/**
 * DISCOVERY TAB:
 * Contains Movies, Series, and Adults.
 * - Top banner displays "Furahia Movie Nzuri kutoka kwa Madjs Wazuri" (no slider at the top).
 * - Movies are grouped strictly by their FIRST genre (`movie.primaryGenre`) so a movie with
 *   multiple genres (e.g. Action, Animation, Drama) is placed ONLY in its first genre ("Action")
 *   and is NEVER duplicated in any other genre section.
 * - Series are displayed in their dedicated Series sections.
 * - Adults content always displays visible Poster & Thumbnail images.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiscoveryTabContent(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    mediaCatalog: List<MediaContent>,
    episodesCatalog: List<EpisodeItem>,
    onMediaSelected: (MediaContent) -> Unit,
    onPlayMedia: (MediaContent) -> Unit,
    modifier: Modifier = Modifier,
    catalogRotationSeed: Long = 0L,
    isRefreshing: Boolean = false,
    onRefreshDiscovery: () -> Unit = { MediaContentRepository.rotateMovieCatalogOrder() },
    onRotateMovies: () -> Unit = { MediaContentRepository.rotateMovieCatalogOrder() }
) {
    val context = LocalContext.current
    val screenProfile = rememberNeliScreenProfile()
    val discoverySections = listOf("All", "Movies", "Series", "Adults")
    val liveRotationSeed by MediaContentRepository.catalogRotationSeed.collectAsState()
    val hasSyncedRealMovies by MediaContentRepository.hasSyncedRealFirebaseMovies.collectAsState()
    val effectiveSeed = if (catalogRotationSeed != 0L) catalogRotationSeed else liveRotationSeed
    val pullToRefreshState = rememberPullToRefreshState()

    // Pre-warm from cache immediately; only sync from network if catalog has not been loaded yet
    LaunchedEffect(hasSyncedRealMovies) {
        if (!hasSyncedRealMovies) {
            MediaContentRepository.initializeAndPrewarmFromCache(context)
            val apiKey = com.example.data.AuthRepository.resolveApiKey(context)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID,
                forceRefresh = false
            )
        }
    }

    // Exclude synthetic demo fallback movies from Discovery so users only see real movies from Firebase
    val discoveryCatalog = remember(mediaCatalog, hasSyncedRealMovies) {
        val withoutDemo = mediaCatalog.filter { it.id !in MediaContentRepository.DEMO_FALLBACK_MOVIE_IDS }
        withoutDemo.ifEmpty { mediaCatalog }
    }

    // Rotating spotlight mix (Studio Admin new additions + rotated catalog picks)
    val rotatingSpotlightMovies = remember(discoveryCatalog, effectiveSeed, hasSyncedRealMovies) {
        MediaContentRepository.getRotatingSpotlightMovies(
            catalog = discoveryCatalog,
            rotationSeed = effectiveSeed,
            limit = 14
        )
    }

    // Strictly deduplicated movies grouped ONLY by their first genre, rotated dynamically by effectiveSeed
    val moviesByFirstGenre = remember(discoveryCatalog, effectiveSeed, hasSyncedRealMovies) {
        MediaContentRepository.getMoviesStrictlyByFirstGenre(
            catalog = discoveryCatalog,
            rotationSeed = effectiveSeed
        )
    }

    val seriesList = remember(mediaCatalog, effectiveSeed) {
        val rawSeries = mediaCatalog.filter { it.published && it.isSeries && !it.isAdultContent }
            .distinctBy { it.id }
        MediaContentRepository.rotateMediaListForSeed(rawSeries, effectiveSeed, "discovery_series")
    }

    val adultList = remember(mediaCatalog) {
        MediaContentRepository.getAdultContentCatalog(mediaCatalog)
    }

    var selectedAdultCategory by rememberSaveable { mutableStateOf("All") }

    val adultCategories = remember(mediaCatalog) {
        MediaContentRepository.getAdultGenreAndCategoryFilters(mediaCatalog)
    }

    val adultsByGenreAndCategory = remember(mediaCatalog, selectedAdultCategory) {
        MediaContentRepository.getAdultsGroupedByGenreAndCategory(
            catalog = mediaCatalog,
            selectedAdultCategory = selectedAdultCategory
        )
    }

    // Hero 3D stacked Recommended showcase: freshly rotates a rich mix of Movies, Animations, and Series every session
    val heroShowcaseItems = remember(selectedFilter, discoveryCatalog, rotatingSpotlightMovies, seriesList, adultList, effectiveSeed) {
        when {
            selectedFilter.equals("Series", ignoreCase = true) -> seriesList.take(8)
            selectedFilter.equals("Adults", ignoreCase = true) -> adultList.take(8)
            else -> {
                val animations = discoveryCatalog.filter { media ->
                    media.published && !media.isAdultContent && (
                        media.genre.contains("animation", ignoreCase = true) ||
                            media.genre.contains("anime", ignoreCase = true) ||
                            media.genre.contains("cartoon", ignoreCase = true) ||
                            media.genre.contains("family", ignoreCase = true)
                        )
                }
                val rotatedAnimations = MediaContentRepository.rotateMediaListForSeed(
                    animations,
                    effectiveSeed + 17L,
                    "hero_recommended_animation"
                )
                val rotatedMovies = MediaContentRepository.rotateMediaListForSeed(
                    rotatingSpotlightMovies.filter { !it.isAdultContent },
                    effectiveSeed + 31L,
                    "hero_recommended_movies"
                )
                val rotatedSeries = MediaContentRepository.rotateMediaListForSeed(
                    seriesList,
                    effectiveSeed + 53L,
                    "hero_recommended_series"
                )
                // Interleave Movie -> Animation -> Series -> Movie -> Animation so every session feels fresh & diverse
                val mixed = mutableListOf<MediaContent>()
                val maxRounds = 4
                for (i in 0 until maxRounds) {
                    rotatedMovies.getOrNull(i)?.let { mixed.add(it) }
                    rotatedAnimations.getOrNull(i)?.let { mixed.add(it) }
                    rotatedSeries.getOrNull(i)?.let { mixed.add(it) }
                }
                (mixed + rotatedMovies + rotatedSeries).distinctBy { it.id }.take(9)
            }
        }
    }

    // Popular vertical list items (cinema showcase below "For you")
    val popularListItems = remember(selectedFilter, rotatingSpotlightMovies, seriesList) {
        when {
            selectedFilter.equals("Series", ignoreCase = true) -> seriesList.drop(2).take(4).ifEmpty { seriesList.take(4) }
            selectedFilter.equals("Adults", ignoreCase = true) -> emptyList()
            else -> rotatingSpotlightMovies.drop(3).take(4).ifEmpty { rotatingSpotlightMovies.take(4) }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshDiscovery,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = NeliSurface,
                color = NeliMagenta,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("discovery_pull_to_refresh_box")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("discovery_tab_screen"),
            contentPadding = PaddingValues(bottom = 116.dp)
        ) {
            // 1. Sleek Typographic Top Category Strip (Trending | Movies | Series | Adults 18+) + Compact Shuffle Circle
            item(key = "discovery_top_tabs") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = screenProfile.horizontalPadding, end = screenProfile.horizontalPadding, top = 6.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(if (screenProfile.isSmallPhone) 14.dp else 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("discovery_filter_chips")
                    ) {
                        items(discoverySections) { section ->
                            val isSelected = section.equals(selectedFilter, ignoreCase = true)
                            val label = when (section) {
                                "All" -> "Trending"
                                "Movies" -> "Movies"
                                "Series" -> "Series"
                                "Adults" -> "Adults 18+"
                                else -> section
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onFilterSelected(section)
                                        onRotateMovies()
                                    }
                                    .padding(vertical = 6.dp, horizontal = 2.dp)
                                    .testTag("discovery_chip_${section.lowercase()}")
                            ) {
                                Text(
                                    text = label,
                                    color = when {
                                        isSelected && section == "Adults" -> Color(0xFFEF4444)
                                        isSelected -> NeliTextPrimary
                                        else -> NeliTextSecondary.copy(alpha = 0.72f)
                                    },
                                    fontSize = if (isSelected) {
                                        if (screenProfile.isSmallPhone) 15.sp else 17.sp
                                    } else {
                                        if (screenProfile.isSmallPhone) 13.sp else 14.sp
                                    },
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 22.dp else 0.dp)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (section == "Adults") {
                                                Brush.horizontalGradient(listOf(Color(0xFFEF4444), NeliWarmOrange))
                                            } else {
                                                Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                            }
                                        )
                                )
                            }
                        }
                    }

                    // Compact circular shuffle button (never wraps vertically on narrow Samsung devices)
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .border(1.dp, NeliMagenta.copy(alpha = 0.45f), CircleShape)
                            .clickable { onRefreshDiscovery() }
                            .testTag("discovery_rotate_movies_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Badilisha Movies",
                            tint = NeliMagenta,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Compact Swahili DJs Eyebrow Banner (Preserves DISCOVERY_MADJS_BANNER_TEXT, 100% Samsung-safe)
            item(key = "discovery_madjs_eyebrow") {
                DiscoveryMadjsBanner(
                    totalMovieCount = rotatingSpotlightMovies.size,
                    onRotateClick = onRefreshDiscovery
                )
            }

            // 3. 3D Stacked Center-Focused Hero Poster Carousel (Inspired by cinema showcase in Neli TV palette)
            if (heroShowcaseItems.isNotEmpty()) {
                item(key = "discovery_hero_3d_carousel") {
                    DiscoveryHeroPosterCarousel(
                        items = heroShowcaseItems,
                        onMediaSelected = onMediaSelected,
                        onPlayMedia = onPlayMedia
                    )
                }
            }

            // 4. "For you" / "Now Showing" Tall Rounded Poster Rail
            if ((selectedFilter.equals("All", true) || selectedFilter.equals("Movies", true)) && rotatingSpotlightMovies.size > 1) {
                item(key = "discovery_rotating_spotlight_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("discovery_rotating_spotlight_row")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "For you",
                                    color = NeliTextPrimary,
                                    fontSize = if (screenProfile.isSmallPhone) 17.sp else 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(NeliMagenta.copy(alpha = 0.18f))
                                        .border(1.dp, NeliMagenta.copy(alpha = 0.45f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SWAHILI HD",
                                        color = NeliMagenta,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(NeliSurfaceVariant)
                                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.35f), CircleShape)
                                    .clickable { onRotateMovies() }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("spotlight_shuffle_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Badilisha Movies",
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Badilisha",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = rotatingSpotlightMovies,
                                key = { "spotlight_${it.id}" }
                            ) { movie ->
                                DiscoveryTallPosterCard(
                                    media = movie,
                                    onClick = { onMediaSelected(movie) }
                                )
                            }
                        }

                        com.example.ads.AdMobBannerAd(
                            adTag = "discovery_spotlight_banner",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                }
            }

            // 5. "Popular" Vertical Feature List Cards (Cinema Poster + IMDb Rating + Genre Pills)
            if (popularListItems.isNotEmpty() && !selectedFilter.equals("Adults", true)) {
                item(key = "discovery_popular_section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = screenProfile.horizontalPadding,
                                end = screenProfile.horizontalPadding,
                                top = 14.dp,
                                bottom = 6.dp
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Popular",
                            color = NeliTextPrimary,
                            fontSize = if (screenProfile.isSmallPhone) 17.sp else 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                        Text(
                            text = "Top Rated",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                items(
                    items = popularListItems,
                    key = { "popular_list_${it.id}" }
                ) { movie ->
                    DiscoveryPopularListCard(
                        media = movie,
                        onClick = { onMediaSelected(movie) },
                        modifier = Modifier.padding(
                            horizontal = screenProfile.horizontalPadding,
                            vertical = 5.dp
                        )
                    )
                }
            }

            // 6. MOVIES SECTION (Grouped Strictly by First Genre — Zero Duplication)
            if (selectedFilter.equals("All", true) || selectedFilter.equals("Movies", true)) {
                itemsIndexed(
                    items = moviesByFirstGenre,
                    key = { _, pair -> "first_genre_${pair.first}" }
                ) { index, (firstGenreName, genreMovies) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("movie_first_genre_row_$firstGenreName")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenProfile.horizontalPadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = firstGenreName,
                                color = NeliTextPrimary,
                                fontSize = if (screenProfile.isSmallPhone) 16.sp else 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(NeliSurfaceVariant)
                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${genreMovies.size}",
                                    color = NeliTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing)
                        ) {
                            items(genreMovies, key = { "movie_${firstGenreName}_${it.id}" }) { movie ->
                                DiscoveryTallPosterCard(
                                    media = movie,
                                    onClick = { onMediaSelected(movie) }
                                )
                            }
                        }

                        com.example.ads.AdMobBannerAd(
                            adTag = "discovery_movie_cat_$index",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp)
                        )

                        if (index % 2 == 0) {
                            com.example.ads.AdMobNativeCardAd(
                                adTag = "discovery_movie_native_$index",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = screenProfile.horizontalPadding, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Banner between Movies and Series
            item(key = "discovery_banner_between_movies_and_series") {
                com.example.ads.AdMobBannerAd(
                    adTag = "discovery_movies_series",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // 7. SERIES SECTION
            if ((selectedFilter.equals("All", true) || selectedFilter.equals("Series", true)) && seriesList.isNotEmpty()) {
                item(key = "discovery_series_section_item") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .testTag("discovery_series_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Series & Episodes",
                                color = NeliTextPrimary,
                                fontSize = if (screenProfile.isSmallPhone) 16.sp else 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${seriesList.size} Series • ${episodesCatalog.size} Eps",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing)
                        ) {
                            items(seriesList, key = { "disc_series_${it.id}" }) { series ->
                                DiscoveryTallPosterCard(
                                    media = series,
                                    onClick = { onMediaSelected(series) }
                                )
                            }
                        }
                    }
                }
            }

            item(key = "discovery_banner_between_series_and_adults") {
                com.example.ads.AdMobBannerAd(
                    adTag = "discovery_series_adults",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }

            // 8. ADULTS (18+) SECTION ORGANIZED BY GENRES & CATEGORIES
            if ((selectedFilter.equals("All", true) || selectedFilter.equals("Adults", true)) && adultList.isNotEmpty()) {
                item(key = "discovery_adults_header_item") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .testTag("discovery_adults_section")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "18+ ADULTS",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Text(
                                    text = "Adults • Genres",
                                    color = NeliTextPrimary,
                                    fontSize = if (screenProfile.isSmallPhone) 15.sp else 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${adultList.size}",
                                color = NeliTextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("adult_genre_category_chips")
                        ) {
                            items(adultCategories, key = { "adult_cat_chip_$it" }) { cat ->
                                val isCatSelected = cat.equals(selectedAdultCategory, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isCatSelected) Color(0xFFEF4444) else NeliSurfaceVariant
                                        )
                                        .clickable { selectedAdultCategory = cat }
                                        .padding(horizontal = 13.dp, vertical = 6.dp)
                                        .testTag("adult_cat_chip_${cat.lowercase()}")
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isCatSelected) Color.White else NeliTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                itemsIndexed(
                    items = adultsByGenreAndCategory,
                    key = { _, pair -> "adult_genre_section_${pair.first}" }
                ) { idx, (adultCategoryName, categoryItems) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("adult_genre_row_$adultCategoryName")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = screenProfile.horizontalPadding, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x33EF4444))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "18+",
                                        color = Color(0xFFEF4444),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                Text(
                                    text = adultCategoryName,
                                    color = NeliTextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${categoryItems.size}",
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = screenProfile.horizontalPadding, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing)
                        ) {
                            items(categoryItems, key = { "disc_adult_${adultCategoryName}_${it.id}" }) { adultMedia ->
                                AdultMediaPosterThumbnailCard(
                                    media = adultMedia,
                                    onSelectDetails = { onMediaSelected(adultMedia) },
                                    onPlayDirect = { onPlayMedia(adultMedia) }
                                )
                            }
                        }

                        com.example.ads.AdMobBannerAd(
                            adTag = "discovery_adult_cat_$idx",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 3D Stacked Center-Focused Hero Poster Carousel for Discovery.
 * Side posters subtly peek and tilt behind the active center card, with centered year,
 * bold title, translucent metadata pills, and Neli gradient dot indicators below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryHeroPosterCarousel(
    items: List<MediaContent>,
    onMediaSelected: (MediaContent) -> Unit,
    onPlayMedia: (MediaContent) -> Unit
) {
    if (items.isEmpty()) return
    val context = LocalContext.current
    val isLight = NeliThemeManager.isLightMode
    val initialPage = if (items.size >= 3) 1 else 0
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { items.size })
    val currentHero = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]

    // Automatically slide to the next recommended movie/animation/series every 3 seconds
    LaunchedEffect(pagerState, items.size) {
        if (items.size > 1) {
            while (true) {
                kotlinx.coroutines.delay(3_000L)
                if (!pagerState.isScrollInProgress) {
                    val nextPage = (pagerState.currentPage + 1) % items.size
                    try {
                        pagerState.animateScrollToPage(nextPage)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    val heroCategoryLabel = remember(currentHero.id, currentHero.genre, currentHero.type, currentHero.isAdultContent) {
        when {
            currentHero.isAdultContent -> "18+ EXCLUSIVE"
            currentHero.genre.contains("animation", ignoreCase = true) ||
                currentHero.genre.contains("anime", ignoreCase = true) ||
                currentHero.genre.contains("cartoon", ignoreCase = true) -> "ANIMATION"
            currentHero.isSeries -> "SERIES"
            else -> "MOVIE"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp)
            .testTag("discovery_hero_carousel")
    ) {
        val narrow = NeliResponsiveLayout.isNarrowViewport(maxWidth.value.toInt())
        val cardWidth = (maxWidth * if (narrow) 0.56f else 0.54f).coerceIn(176.dp, 242.dp)
        val sidePadding = ((maxWidth - cardWidth) / 2f).coerceAtLeast(24.dp)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Glowing RECOMMENDED spotlight header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(NeliMagenta.copy(alpha = 0.24f), NeliWarmOrange.copy(alpha = 0.20f))
                            )
                        )
                        .border(1.dp, NeliMagenta.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 11.dp, vertical = 5.dp)
                        .testTag("discovery_recommended_badge")
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NeliMagenta)
                    )
                    Text(
                        text = "RECOMMENDED",
                        color = if (isLight) NeliMagenta else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.9.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(NeliGenreCyan.copy(alpha = 0.16f))
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.55f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$heroCategoryLabel • AUTO",
                        color = NeliGenreCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = (-22).dp,
                beyondViewportPageCount = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardWidth * 1.46f + 16.dp)
            ) { page ->
                val media = items[page]
                val rawOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = rawOffset.absoluteValue.coerceIn(0f, 1.5f)
                val scale = lerp(0.83f, 1f, 1f - absOffset.coerceAtMost(1f))
                val cardAlpha = lerp(0.54f, 1f, 1f - absOffset.coerceAtMost(1f))
                val rotation = (-rawOffset * 6.5f).coerceIn(-9f, 9f)
                val elevationZ = 10f - absOffset * 5f

                val posterUrl = remember(media.id, media.posterUrl, media.backdropUrl, media.streamUrl) {
                    MediaContentRepository.resolveGuaranteedMediaImageUrl(
                        media.posterUrl,
                        media.backdropUrl,
                        media.streamUrl
                    )
                }
                val posterReq = remember(media.id, posterUrl) {
                    ImageRequest.Builder(context)
                        .data(posterUrl)
                        .size(480, 720)
                        .crossfade(true)
                        .build()
                }

                Box(
                    modifier = Modifier
                        .zIndex(elevationZ)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            alpha = cardAlpha
                            rotationZ = rotation
                        }
                        .width(cardWidth)
                        .aspectRatio(0.68f)
                        .shadow(
                            elevation = if (absOffset < 0.3f) 22.dp else 8.dp,
                            shape = RoundedCornerShape(26.dp),
                            ambientColor = NeliMagenta.copy(alpha = 0.45f),
                            spotColor = NeliMagenta.copy(alpha = 0.5f)
                        )
                        .clip(RoundedCornerShape(26.dp))
                        .background(NeliSurfaceVariant)
                        .border(
                            width = if (absOffset < 0.3f) 1.5.dp else 1.dp,
                            brush = if (absOffset < 0.3f) {
                                Brush.linearGradient(
                                    listOf(
                                        NeliMagenta.copy(alpha = 0.78f),
                                        NeliWarmOrange.copy(alpha = 0.55f),
                                        NeliGenreCyan.copy(alpha = 0.45f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            },
                            shape = RoundedCornerShape(26.dp)
                        )
                        .clickable { onMediaSelected(media) }
                        .testTag("hero_carousel_card_${media.id}")
                ) {
                    AsyncImage(
                        model = posterReq,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Subtle bottom cinema vignette
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC060812))
                                )
                            )
                    )

                    // Top-left Swahili / Adult pill on active card
                    if (absOffset < 0.45f) {
                        if (media.isAdultContent) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xEEEF4444))
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "18+ ADULT",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else if (media.narrated && media.narrationLanguage.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xCC0B1020))
                                    .border(1.dp, NeliGenreCyan.copy(alpha = 0.55f), CircleShape)
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = media.narrationLanguage.uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Compact quick-play button on bottom-right of active hero poster
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(NeliMagenta, NeliWarmOrange))
                                )
                                .clickable { onPlayMedia(media) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play ${media.title}",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Centered Release Year
            Text(
                text = currentHero.releaseYear.ifBlank { "2025" },
                color = NeliTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Centered Bold Title
            Text(
                text = currentHero.title,
                color = NeliTextPrimary,
                fontSize = if (narrow) 18.sp else 21.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Centered Translucent Metadata Pills (Genre • Duration/Swahili • ★ Rating)
            val pillBg = if (isLight) NeliSurfaceVariant else Color(0xFF1A213C).copy(alpha = 0.92f)
            val pillBorder = if (isLight) NeliBorder else Color.White.copy(alpha = 0.12f)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.dp, pillBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentHero.primaryGenre.ifBlank { "Cinema" },
                        color = NeliTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.dp, pillBorder, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentHero.duration.ifBlank { if (currentHero.isSeries) "Series" else "HD" },
                        color = NeliTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(pillBg)
                        .border(1.dp, NeliMagenta.copy(alpha = 0.35f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB800),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = currentHero.rating.ifBlank { "8.0" },
                        color = NeliTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sleek Dot Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { idx, _ ->
                    val isActive = idx == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .width(if (isActive) 18.dp else 5.dp)
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                } else {
                                    Brush.horizontalGradient(
                                        listOf(
                                            NeliTextMuted.copy(alpha = 0.45f),
                                            NeliTextMuted.copy(alpha = 0.45f)
                                        )
                                    )
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Modern Tall Cinema Poster Card for "For you" and First-Genre horizontal rails.
 * Clean rounded artwork with Swahili/Type badge, crisp title & gold star rating below.
 */
@Composable
private fun DiscoveryTallPosterCard(
    media: MediaContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val screenProfile = rememberNeliScreenProfile()
    val cardWidth = if (screenProfile.isSmallPhone) 124.dp else 138.dp
    val primaryImageUrl = remember(media.id, media.posterUrl, media.backdropUrl, media.streamUrl) {
        MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.posterUrl,
            media.backdropUrl,
            media.streamUrl
        )
    }
    val posterRequest = remember(media.id, primaryImageUrl) {
        ImageRequest.Builder(context)
            .data(primaryImageUrl)
            .size(300, 450)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .width(cardWidth)
            .tvGeminiFocusable(
                shape = RoundedCornerShape(18.dp),
                focusScale = 1.06f,
                borderWidth = 2.dp,
                onClick = onClick
            )
            .clickable(onClick = onClick)
            .testTag("media_card_${media.id}"),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(18.dp))
                .background(NeliSurfaceVariant)
                .border(
                    width = 1.dp,
                    color = if (media.isAdultContent) Color(0x66EF4444) else NeliBorder.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = NeliMagenta.copy(alpha = 0.3f),
                modifier = Modifier.size(30.dp)
            )
            AsyncImage(
                model = posterRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Subtle bottom vignette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xBF060812)))
                    )
            )

            // Top-left Swahili or Series/18+ pill
            when {
                media.isAdultContent -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xEEEF4444))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "18+",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                media.narrated && media.narrationLanguage.isNotBlank() -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0B1020))
                            .border(0.8.dp, NeliGenreCyan.copy(alpha = 0.6f), CircleShape)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = media.narrationLanguage.uppercase(),
                            color = NeliGenreCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                media.isSeries -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(NeliNeonPurple.copy(alpha = 0.9f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SERIES",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = media.title,
                color = NeliTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = "${media.rating.ifBlank { "7.8" }}/10",
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "• ${media.releaseYear}",
                    color = NeliTextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Cinema "Popular" Horizontal Feature Card (Poster thumbnail on left + Title, IMDb rating, Genre pills, Duration on right).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryPopularListCard(
    media: MediaContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val primaryImageUrl = remember(media.id, media.posterUrl, media.backdropUrl, media.streamUrl) {
        MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.posterUrl,
            media.backdropUrl,
            media.streamUrl
        )
    }
    val posterRequest = remember(media.id, primaryImageUrl) {
        ImageRequest.Builder(context)
            .data(primaryImageUrl)
            .size(220, 330)
            .crossfade(false)
            .build()
    }
    val genreTags = remember(media.id, media.genre, media.primaryGenre) {
        media.genre.split(",", "/", "•", "|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(media.primaryGenre.ifBlank { "Cinema" }) }
            .take(3)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .tvCardGrow(shape = RoundedCornerShape(18.dp), onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(NeliSurface)
            .border(1.dp, NeliBorder.copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("popular_movie_card_${media.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(74.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(13.dp))
                .background(NeliSurfaceVariant)
        ) {
            AsyncImage(
                model = posterRequest,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = media.title,
                color = NeliTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${media.rating.ifBlank { "8.0" }}/10 IMDb",
                    color = NeliTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                genreTags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(NeliMagenta.copy(alpha = 0.14f))
                            .border(0.8.dp, NeliMagenta.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tag.uppercase(),
                            color = if (NeliThemeManager.isLightMode) NeliMagenta else Color(0xFFFFB3D9),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = media.duration.ifBlank { "HD" },
                    color = NeliTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                if (media.narrated && media.narrationLanguage.isNotBlank()) {
                    Text(
                        text = "• ${media.narrationLanguage}",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryMadjsBanner(
    totalMovieCount: Int = 0,
    onRotateClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        NeliMagenta.copy(alpha = 0.16f),
                        NeliNeonPurple.copy(alpha = 0.14f),
                        NeliSurfaceVariant.copy(alpha = 0.8f)
                    )
                )
            )
            .border(1.dp, NeliMagenta.copy(alpha = 0.32f), CircleShape)
            .clickable { onRotateClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
            .testTag("discovery_madjs_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeliMagenta, NeliWarmOrange))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = "Swahili DJs",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = DISCOVERY_MADJS_BANNER_TEXT,
            color = NeliTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (totalMovieCount > 0) {
            Text(
                text = "HD",
                color = NeliGenreCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * SEARCH PAGE:
 * Searches Live TV channels, Movies, Series, and Adults in a unified responsive grid.
 */
@Composable
fun SearchTabContent(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    mediaList: List<MediaContent>,
    episodesList: List<EpisodeItem>,
    liveChannels: List<LiveChannel> = emptyList(),
    onMediaSelected: (MediaContent) -> Unit,
    onChannelSelected: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier
) {
    val searchFilterTabs = remember {
        listOf(
            "All",
            "Live TV",
            "Movies",
            "Series",
            "Adults",
            "Swahili",
            "Sports",
            "Action",
            "Entertainment",
            "News",
            "Kids"
        )
    }

    val allChannels = if (liveChannels.isNotEmpty()) liveChannels else ChannelRepository.channels

    val filteredChannels = remember(searchQuery, selectedCategory, allChannels) {
        if (selectedCategory.equals("Movies", true) ||
            selectedCategory.equals("Series", true) ||
            selectedCategory.equals("Adults", true)
        ) {
            emptyList()
        } else {
            val catFilter = if (selectedCategory.equals("Live TV", true)) {
                "All"
            } else {
                selectedCategory
            }
            ChannelRepository.filterChannels(searchQuery, catFilter)
        }
    }

    val filteredMedia = remember(searchQuery, selectedCategory, mediaList, episodesList) {
        if (selectedCategory.equals("Live TV", true)) {
            emptyList()
        } else {
            mediaList.filter { item ->
                MediaContentRepository.matchesMediaSearch(item, searchQuery, selectedCategory)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_tab_screen")
    ) {
        CategoryChipRow(
            categories = searchFilterTabs,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        if (filteredChannels.isEmpty() && filteredMedia.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = NeliTextSecondary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No matching channels, movies or series found",
                        color = NeliTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("channels_grid")
            ) {
                if (filteredChannels.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "search_header_channels"
                    ) {
                        Text(
                            text = "Live TV Channels (${filteredChannels.size})",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    items(filteredChannels, key = { "search_ch_${it.id}" }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onChannelSelected(channel) }
                        )
                    }
                }

                if (filteredMedia.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "search_header_media"
                    ) {
                        Text(
                            text = "Movies, Series & Adults (${filteredMedia.size})",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                        )
                    }

                    items(filteredMedia, key = { "search_media_${it.id}" }) { media ->
                        MediaPosterCard(
                            media = media,
                            onClick = { onMediaSelected(media) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadTabContent(
    downloads: List<DownloadedItemEntity>,
    downloadProgress: Map<String, Int> = emptyMap(),
    downloadedIds: Set<String> = emptySet(),
    downloadingIds: Set<String> = emptySet(),
    mediaCatalog: List<MediaContent> = emptyList(),
    isOfflineMode: Boolean = false,
    downloadBannerMessage: String? = null,
    onDismissBanner: () -> Unit = {},
    onStartQuickDownload: (MediaContent) -> Unit = {},
    onPauseDownload: (String) -> Unit = {},
    onRetryDownload: (DownloadedItemEntity) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onPlayDownloaded: (DownloadedItemEntity) -> Unit,
    onPlayQuickMediaOffline: (MediaContent) -> Unit = {},
    onDeleteDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenProfile = rememberNeliScreenProfile()
    val rotationSeed by MediaContentRepository.catalogRotationSeed.collectAsState()
    val downloadableCatalog = remember(mediaCatalog, downloadedIds, rotationSeed) {
        val raw = mediaCatalog.filter { it.published && it.isDownloadPermitted && !it.isAdultContent }
        MediaContentRepository.rotateMediaListForSeed(raw, rotationSeed, "downloadable_catalog")
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = screenProfile.maxFormWidth)
                .padding(horizontal = screenProfile.horizontalPadding)
                .testTag("download_tab_screen"),
            verticalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing),
            contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp)
        ) {
            if (isOfflineMode) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeliCardPurple)
                            .border(1.dp, Color(0xFFFF2E7E), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                            .testTag("offline_mode_auto_redirect_banner"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = Color(0xFFFF4DA6),
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline mode",
                                color = NeliTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Watch your downloaded videos without internet",
                                color = NeliTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Clean Header (No jittery overall progress bar or bulky storage banner)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloads",
                        color = NeliTextPrimary,
                        fontSize = if (screenProfile.isSmallPhone) 18.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )

                    if (downloads.isNotEmpty()) {
                        Text(
                            text = "${downloads.size} ${if (downloads.size == 1) "video" else "videos"}",
                            color = NeliTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (downloads.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurface)
                            .border(0.5.dp, Color(0xFF252D40), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            tint = NeliTextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No downloads yet",
                            color = NeliTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Downloaded movies & series appear here so you can watch offline anytime.",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(downloads, key = { it.id }) { item ->
                    val isCompleted = item.downloadStatus == "COMPLETED" && item.localFilePath.isNotBlank()
                    val activePct = downloadProgress[item.id]
                    val isDownloading = !isCompleted && (
                        activePct != null ||
                            item.downloadStatus == "DOWNLOADING" ||
                            downloadingIds.contains(item.id)
                        )
                    val isPausedError = !isCompleted && !isDownloading && item.downloadStatus == "PAUSED_ERROR"
                    val displayPct = if (isCompleted) {
                        100
                    } else {
                        (activePct ?: item.progressPercent).coerceIn(0, 99)
                    }
                    // Prioritize movie poster first for a clean vertical movie poster look
                    val posterUrl = MediaContentRepository.resolveGuaranteedMediaImageUrl(
                        item.posterUrl,
                        item.backdropUrl,
                        item.streamUrl
                    )
                    val posterWidth = if (screenProfile.isSmallPhone) 64.dp else 74.dp

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvCardGrow(
                                shape = RoundedCornerShape(16.dp),
                                onClick = {
                                    when {
                                        isCompleted -> onPlayDownloaded(item)
                                        isPausedError -> onRetryDownload(item)
                                    }
                                }
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurface)
                            .border(
                                width = 1.dp,
                                color = when {
                                    isCompleted -> Color(0xFFFF2E7E).copy(alpha = 0.55f)
                                    isDownloading -> NeliGenreCyan.copy(alpha = 0.55f)
                                    isPausedError -> Color(0xFFF59E0B).copy(alpha = 0.65f)
                                    else -> Color(0x33A855F7)
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = isCompleted || isPausedError) {
                                when {
                                    isCompleted -> onPlayDownloaded(item)
                                    isPausedError -> onRetryDownload(item)
                                }
                            }
                            .padding(if (screenProfile.isSmallPhone) 10.dp else 12.dp)
                            .testTag("download_item_${item.id}"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Top Row: Clean Movie Poster (no overlapping progress circle) + Movie Details & MB Size
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val dlContext = LocalContext.current
                            val dlPosterReq = remember(item.id, posterUrl) {
                                ImageRequest.Builder(dlContext)
                                    .data(posterUrl)
                                    .size(220, 330)
                                    .crossfade(false)
                                    .build()
                            }
                            Box(
                                modifier = Modifier
                                    .width(posterWidth)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeliSurfaceVariant)
                            ) {
                                AsyncImage(
                                    model = dlPosterReq,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.title.replace("\n", " "),
                                    color = NeliTextPrimary,
                                    fontSize = if (screenProfile.isSmallPhone) 14.sp else 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "${item.genre} • ${item.duration}",
                                    color = NeliTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Current downloading MB / Total movie MB + Percentage status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when {
                                            isDownloading -> item.fileSizeLabel.ifBlank { "Starting download..." }
                                            isPausedError -> item.fileSizeLabel.ifBlank { "Paused" }
                                            else -> item.fileSizeLabel.ifBlank { "Ready Offline" }
                                        },
                                        color = when {
                                            isDownloading -> NeliGenreCyan
                                            isPausedError -> Color(0xFFF59E0B)
                                            else -> Color(0xFFFF4DA6)
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Text(
                                        text = when {
                                            isCompleted -> "Ready"
                                            isPausedError -> "Paused • $displayPct%"
                                            else -> "$displayPct%"
                                        },
                                        color = when {
                                            isCompleted -> Color(0xFFFF4DA6)
                                            isPausedError -> Color(0xFFF59E0B)
                                            else -> Color.White
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Smooth Download Timeline Progress Bar until complete
                        if (isDownloading || isPausedError) {
                            LinearProgressIndicator(
                                progress = { (displayPct / 100f).coerceIn(0f, 1f) },
                                color = if (isPausedError) Color(0xFFF59E0B) else NeliMagenta,
                                trackColor = NeliSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }

                        // Dedicated Action Row: Pause / Resume / Play + Delete (never cramped!)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when {
                                isCompleted -> {
                                    Button(
                                        onClick = { onPlayDownloaded(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvButtonGrow(shape = RoundedCornerShape(10.dp))
                                            .height(40.dp)
                                            .testTag("play_offline_btn_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Play Offline",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                isDownloading -> {
                                    OutlinedButton(
                                        onClick = { onPauseDownload(item.id) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, NeliGenreCyan.copy(alpha = 0.7f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvButtonGrow(shape = RoundedCornerShape(10.dp))
                                            .height(40.dp)
                                            .testTag("pause_download_btn_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Pause,
                                            contentDescription = "Pause Download",
                                            tint = NeliGenreCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Pause",
                                            color = NeliTextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                                isPausedError -> {
                                    Button(
                                        onClick = { onRetryDownload(item) },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .tvButtonGrow(shape = RoundedCornerShape(10.dp))
                                            .height(40.dp)
                                            .testTag("resume_download_btn_${item.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Resume Download",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Resume",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isDownloading) {
                                        onCancelDownload(item.id)
                                    } else {
                                        onDeleteDownload(item.id)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, NeliMagenta.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("delete_download_btn_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Download",
                                    tint = NeliMagenta,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Delete",
                                    color = NeliMagenta,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Recommended Downloads inside the Download tab
            if (downloadableCatalog.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Available to Download",
                        color = NeliTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                items(downloadableCatalog.take(12), key = { "quick_dl_${it.id}" }) { media ->
                    val isAlreadyDownloaded = downloadedIds.contains(media.id)
                    val isCurrentlyDownloading = downloadingIds.contains(media.id) || downloadProgress.containsKey(media.id)
                    val livePct = downloadProgress[media.id] ?: 0
                    val thumbUrl = MediaContentRepository.resolveGuaranteedMediaImageUrl(
                        media.posterUrl,
                        media.backdropUrl,
                        media.streamUrl
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeliSurface)
                            .border(1.dp, Color(0xFF252D40), RoundedCornerShape(14.dp))
                            .padding(10.dp)
                            .testTag("quick_download_row_${media.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val quickContext = LocalContext.current
                        val quickThumbReq = remember(media.id, thumbUrl) {
                            ImageRequest.Builder(quickContext)
                                .data(thumbUrl)
                                .size(168, 222)
                                .crossfade(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 52.dp, height = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeliSurfaceVariant)
                        ) {
                            AsyncImage(
                                model = quickThumbReq,
                                contentDescription = media.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.title,
                                color = NeliTextPrimary,
                                fontSize = if (screenProfile.isSmallPhone) 13.sp else 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${media.primaryGenre} • ${media.duration}",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCurrentlyDownloading) {
                                Text(
                                    text = "Downloading ($livePct%)...",
                                    color = NeliMagenta,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (isAlreadyDownloaded) {
                                    val matchingEntity = downloads.find { it.id == media.id }
                                    if (matchingEntity != null) {
                                        onPlayDownloaded(matchingEntity)
                                    } else {
                                        onPlayQuickMediaOffline(media)
                                    }
                                } else if (!isCurrentlyDownloading) {
                                    onStartQuickDownload(media)
                                }
                            },
                            enabled = !isCurrentlyDownloading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isAlreadyDownloaded -> Color(0xFFFF2E7E)
                                    isCurrentlyDownloading -> NeliSurfaceVariant
                                    else -> NeliMagenta
                                }
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("quick_download_btn_${media.id}")
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.Default.PlayArrow else Icons.Default.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when {
                                    isAlreadyDownloaded -> "Play"
                                    isCurrentlyDownloading -> "$livePct%"
                                    else -> "Download"
                                },
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class AccountAuthMethodType {
    PHONE,
    GOOGLE,
    EMAIL
}

@Composable
fun AccountTabContent(
    currentUser: UserAccountEntity?,
    authError: String?,
    googleFallbackMessage: String? = null,
    isAuthLoading: Boolean,
    showGoogleSignInSheet: Boolean = false,
    savedGoogleAccounts: List<UserAccountEntity> = emptyList(),
    firebaseConfig: FirebaseConfigEntity,
    watchlist: List<WatchlistItemEntity>,
    downloadsCount: Int,
    onSignInWithGoogle: () -> Unit = {},
    onChooseGoogleAccount: () -> Unit = {},
    onCompleteGoogleSignIn: (email: String, displayName: String) -> Unit = { _, _ -> },
    onDismissGoogleSignInSheet: () -> Unit = {},
    onSignUp: (realName: String, email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignInAsGuest: (guestName: String) -> Unit = {},
    onSignInWithPhone: (phoneNumber: String, realName: String, password: String) -> Unit = { _, _, _ -> },
    onSignInWithPhoneCredentials: (phoneOrUsername: String, password: String) -> Unit = { _, _ -> },
    onSignUpWithPhone: (username: String, phoneNumber: String, password: String) -> Unit = { _, _, _ -> },
    onSignOut: () -> Unit,
    onClearAuthError: () -> Unit,
    onUpdateNetworkPreferences: (networkMode: String, allowMobileData: Boolean) -> Unit,
    onPlayWatchlistItem: (WatchlistItemEntity) -> Unit,
    onResumeContinueWatching: (com.example.data.ContinueWatchingEntry) -> Unit = {},
    usernameUpdateMessage: String? = null,
    onUpdateUsername: (String) -> Unit = {},
    onClearUsernameUpdateMessage: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeInfoPage by rememberSaveable { mutableStateOf<AccountInfoPageType?>(null) }
    var showEditUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var editUsernameDraft by rememberSaveable(currentUser?.uid, showEditUsernameDialog) {
        mutableStateOf(currentUser?.realName.orEmpty())
    }
    var selectedAuthMethod by rememberSaveable { mutableStateOf(AccountAuthMethodType.PHONE) }
    var phoneAuthIsRegister by rememberSaveable { mutableStateOf(false) }
    var phoneAuthNumber by rememberSaveable { mutableStateOf("") }
    var phoneAuthName by rememberSaveable { mutableStateOf("") }
    var phoneAuthPassword by rememberSaveable { mutableStateOf("") }
    var isPhonePasswordVisible by rememberSaveable { mutableStateOf(false) }

    var isRegisterMode by rememberSaveable { mutableStateOf(true) }
    var realName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var googleSheetEmail by rememberSaveable(showGoogleSignInSheet) {
        mutableStateOf(
            savedGoogleAccounts.firstOrNull()?.email
                ?: email.takeIf { it.contains("@") }.orEmpty()
        )
    }
    var googleSheetName by rememberSaveable(showGoogleSignInSheet) {
        mutableStateOf(
            savedGoogleAccounts.firstOrNull()?.realName
                ?: realName
        )
    }

    var selectedQualityMode by remember(firebaseConfig.networkMode) {
        mutableStateOf(firebaseConfig.networkMode.ifBlank { "AUTO_ADAPTIVE" })
    }
    var allowMobileData by remember(firebaseConfig.allowMobileData) {
        mutableStateOf(firebaseConfig.allowMobileData)
    }

    if (showGoogleSignInSheet && currentUser == null) {
        AlertDialog(
            onDismissRequest = onDismissGoogleSignInSheet,
            containerColor = NeliSurface,
            titleContentColor = NeliTextPrimary,
            textContentColor = NeliTextSecondary,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            color = NeliMagenta,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                    Column {
                        Text(
                            text = "Sign in with Google",
                            color = NeliTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Continue to NeliTV",
                            color = NeliGenreCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("google_sign_in_modal_sheet"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismissGoogleSignInSheet()
                            onSignInWithGoogle()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pick_device_google_account_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Choose Google Account from Phone",
                            color = NeliTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (savedGoogleAccounts.isNotEmpty()) {
                        Text(
                            text = "Chagua akaunti (Select an account):",
                            color = NeliTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        savedGoogleAccounts.take(10).forEach { acc ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeliSurfaceVariant)
                                    .border(1.dp, Color(0x44A855F7), RoundedCornerShape(12.dp))
                                    .clickable {
                                        onCompleteGoogleSignIn(acc.email, acc.realName)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(NeliMagenta),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = acc.realName.take(1).uppercase().ifEmpty { "G" },
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = acc.realName,
                                        color = NeliTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = acc.email,
                                        color = NeliGenreCyan,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = googleSheetEmail,
                        onValueChange = {
                            googleSheetEmail = it
                            onClearAuthError()
                        },
                        label = { Text("Google Email (@gmail.com)") },
                        placeholder = { Text("yourname@gmail.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = null,
                                tint = NeliMagenta
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sheet_email_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeliMagenta,
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )

                    OutlinedTextField(
                        value = googleSheetName,
                        onValueChange = {
                            googleSheetName = it
                            onClearAuthError()
                        },
                        label = { Text("Full Name (Optional)") },
                        placeholder = { Text("Your Display Name") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = NeliMagenta
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sheet_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeliMagenta,
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done
                        )
                    )

                    if (!authError.isNullOrBlank()) {
                        Text(
                            text = authError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCompleteGoogleSignIn(googleSheetEmail, googleSheetName)
                    },
                    enabled = !isAuthLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("confirm_google_dialog_sign_in_button")
                ) {
                    Text(
                        text = "Continue with Google",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissGoogleSignInSheet
                ) {
                    Text(
                        text = "Cancel",
                        color = NeliTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    var isAdminPanelOpen by rememberSaveable { mutableStateOf(false) }
    var isFullAdminDashboardOpen by rememberSaveable { mutableStateOf(false) }
    val accountContext = LocalContext.current
    val isCurrentUserAdmin = NeliAdminManager.isAdminUser(currentUser)
    val isFreeForeverVipUser = com.example.data.NeliFreeForeverAccountsManager.isFreeForeverUser(currentUser)
    val isSpecialAdFreeUser = com.example.data.NeliFreeForeverAccountsManager.isSpecialAdFreeUser(currentUser)
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    var accountCurrentTimeMs by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    val isVerifiedPremiumMember = subState.isActiveAt(accountCurrentTimeMs) || isFreeForeverVipUser || isSpecialAdFreeUser

    // Auto-Ready Device IP & real-time 1-second subscription countdown / expiry check
    LaunchedEffect(subState.isVerified, subState.expiresAtMs) {
        com.example.data.NeliFreeForeverAccountsManager.initialize(accountContext)
        NeliSubscriptionManager.refreshDeviceIp(accountContext)
        while (true) {
            val now = System.currentTimeMillis()
            accountCurrentTimeMs = now
            if (subState.isVerified && !subState.isFreeForeverAccount && !subState.isSpecialAdFreeAccount && subState.expiresAtMs in 1..now) {
                NeliSubscriptionManager.expireSubscriptionIfNeeded(accountContext, now)
            }
            kotlinx.coroutines.delay(1000L)
        }
    }

    if (isFullAdminDashboardOpen && isCurrentUserAdmin) {
        FullAdminDashboardScreen(
            currentUser = currentUser,
            onBack = { isFullAdminDashboardOpen = false },
            onSwitchToMiniAdminPanel = {
                isFullAdminDashboardOpen = false
                isAdminPanelOpen = true
            },
            modifier = modifier
        )
        return
    }

    if (isAdminPanelOpen && isCurrentUserAdmin) {
        val allLiveChannels by ChannelRepository.liveChannelsFlow.collectAsState()
        MiniAdminPanelScreen(
            currentUser = currentUser,
            allChannels = allLiveChannels.ifEmpty { ChannelRepository.getPrioritizedAllChannels() },
            onBack = { isAdminPanelOpen = false },
            onOpenFullRealtimeAdminPanel = {
                isAdminPanelOpen = false
                isFullAdminDashboardOpen = true
            },
            modifier = modifier
        )
        return
    }

    if (activeInfoPage != null) {
        AccountInfoDetailScreen(
            pageType = activeInfoPage!!,
            onBack = { activeInfoPage = null },
            modifier = modifier
        )
        return
    }

    val screenProfile = rememberNeliScreenProfile()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = screenProfile.horizontalPadding)
            .testTag("account_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing),
        contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp)
    ) {
        if (currentUser != null) {
            // Logged-in User Profile Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvCardGrow(shape = RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF391473), Color(0xFF1F0940))
                            )
                        )
                        .border(1.dp, NeliMagenta.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                        .padding(if (screenProfile.isSmallPhone) 14.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (screenProfile.isSmallPhone) 52.dp else 60.dp)
                                .clip(CircleShape)
                                .background(NeliMagenta),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.realName.trim().take(1).uppercase().ifEmpty { "N" },
                                color = Color.White,
                                fontSize = if (screenProfile.isSmallPhone) 20.sp else 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentUser.realName,
                                    color = Color.White,
                                    fontSize = if (screenProfile.isSmallPhone) 16.sp else 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Icon(
                                    imageVector = if (isVerifiedPremiumMember) Icons.Default.Verified else Icons.Default.CheckCircle,
                                    contentDescription = if (isVerifiedPremiumMember) "Verified Pink Tick" else "Active Member",
                                    tint = Color(0xFFFF2E7E),
                                    modifier = Modifier
                                        .size(19.dp)
                                        .testTag("account_pink_verification_tick")
                                )
                            }
                            if (isVerifiedPremiumMember) {
                                Row(
                                    modifier = Modifier
                                        .padding(top = 4.dp, bottom = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33FF2E7E))
                                        .border(1.dp, Color(0xFFFF4DA6), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .testTag("account_premium_verified_badge"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Verified Pink Tick",
                                        tint = Color(0xFFFF4DA6),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = when {
                                            isSpecialAdFreeUser -> "VIP Ad-Free ✓ (No Ads)"
                                            isFreeForeverVipUser -> "Free VIP Ad-Free ✓ (No Ads)"
                                            else -> "Premium Member ✓ (${subState.planTitle.ifBlank { "VIP" }})"
                                        },
                                        color = Color(0xFFFFB3D9),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            val cwEntries by com.example.data.NeliWatchProgressManager.continueWatching.collectAsState()
                            val loyaltyPoints = remember(cwEntries.size, watchlist.size, downloadsCount, isVerifiedPremiumMember) {
                                com.example.data.NeliWatchProgressManager.computeLoyaltyPoints(
                                    continueWatchingCount = cwEntries.size,
                                    watchlistCount = watchlist.size,
                                    downloadsCount = downloadsCount,
                                    isPremium = isVerifiedPremiumMember
                                )
                            }
                            val loyaltyTier = remember(loyaltyPoints, isVerifiedPremiumMember) {
                                com.example.data.NeliWatchProgressManager.resolveLoyaltyTierLabel(loyaltyPoints, isVerifiedPremiumMember)
                            }
                            val displayIdentifier = remember(currentUser.email) {
                                if (currentUser.email.endsWith("@nelitv.phone", ignoreCase = true)) {
                                    "Phone • ${currentUser.email.substringBefore("@")}"
                                } else {
                                    currentUser.email
                                }
                            }
                            Text(
                                text = displayIdentifier,
                                color = NeliGenreCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$loyaltyTier • $loyaltyPoints pts",
                                color = Color(0xFFFBBF24),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("account_loyalty_points_label")
                            )
                            Text(
                                text = when {
                                    isSpecialAdFreeUser -> "Special VIP Ad-Free Account (Bila Matangazo)"
                                    isFreeForeverVipUser -> "Free Forever VIP • No Ads (Max 2 Devices)"
                                    isVerifiedPremiumMember -> "Muda Uliobaki: ${subState.formatLiveCountdown(accountCurrentTimeMs)}"
                                    else -> "Free Streaming Member • Account Synced"
                                },
                                color = if (isVerifiedPremiumMember) Color(0xFFFF4DA6) else NeliTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isVerifiedPremiumMember) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Quick Edit Username Icon Button
                        IconButton(
                            onClick = {
                                editUsernameDraft = currentUser.realName
                                onClearUsernameUpdateMessage()
                                onClearAuthError()
                                showEditUsernameDialog = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x26FF2E7E))
                                .border(1.dp, Color(0x66FF4DA6), CircleShape)
                                .testTag("edit_username_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Username",
                                tint = Color(0xFFFF4DA6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Inline confirmation banner when username is saved & synced
                    if (!usernameUpdateMessage.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x26FF2E7E))
                                .border(1.dp, Color(0xFFFF4DA6), RoundedCornerShape(10.dp))
                                .clickable { onClearUsernameUpdateMessage() }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("username_updated_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color(0xFFFF4DA6),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = usernameUpdateMessage,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Edit Username Dialog
                    if (showEditUsernameDialog) {
                        AlertDialog(
                            onDismissRequest = { showEditUsernameDialog = false },
                            containerColor = NeliSurface,
                            titleContentColor = NeliTextPrimary,
                            textContentColor = NeliTextSecondary,
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = Color(0xFFFF4DA6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Edit Username",
                                        color = NeliTextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        text = "Change your display username. You can also use this username to log in anytime.",
                                        color = NeliTextSecondary,
                                        fontSize = 12.sp
                                    )
                                    OutlinedTextField(
                                        value = editUsernameDraft,
                                        onValueChange = { editUsernameDraft = it },
                                        label = { Text("Username") },
                                        placeholder = { Text("Enter new username") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = NeliGenreCyan
                                            )
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("edit_username_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeliMagenta,
                                            unfocusedBorderColor = Color(0x44A855F7),
                                            focusedTextColor = NeliTextPrimary,
                                            unfocusedTextColor = NeliTextPrimary
                                        )
                                    )
                                    if (!authError.isNullOrBlank()) {
                                        Text(
                                            text = authError,
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val clean = editUsernameDraft.trim()
                                        if (clean.length >= 2) {
                                            onUpdateUsername(clean)
                                            showEditUsernameDialog = false
                                        }
                                    },
                                    enabled = !isAuthLoading && editUsernameDraft.trim().length >= 2,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.testTag("save_username_button")
                                ) {
                                    Text(
                                        text = "Save",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEditUsernameDialog = false }) {
                                    Text(
                                        text = "Cancel",
                                        color = NeliTextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        )
                    }

                    // Admin-Only Full Real-Time Admin Panel & Mini Admin Panel Buttons (Strictly visible ONLY when logged in with Admin@login.com)
                    if (isCurrentUserAdmin) {
                        Button(
                            onClick = { isFullAdminDashboardOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_full_admin_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Real-Time Admin Panel",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Real-Time Admin Panel (Live Data)",
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Button(
                            onClick = { isAdminPanelOpen = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_admin_panel_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Mini Admin Panel",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Mini Admin Panel (Admin Only)",
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSignInWithGoogle()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("switch_account_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = NeliGenreCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Switch Account",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                isAdminPanelOpen = false
                                isFullAdminDashboardOpen = false
                                onSignOut()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 46.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = NeliMagenta,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Sign Out",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        } else {
            // When user is logged out, show Google Account Sign In, Phone Number, AND By Email (Login / Sign Up)
            item {
                NeliGoogleAccountAuthSection(
                    authError = authError,
                    isAuthLoading = isAuthLoading,
                    savedGoogleAccounts = savedGoogleAccounts,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onChooseGoogleAccount = onChooseGoogleAccount,
                    onCompleteGoogleSignIn = onCompleteGoogleSignIn,
                    onSignInByEmail = onSignIn,
                    onSignUpByEmail = onSignUp,
                    onSignInByPhone = onSignInWithPhoneCredentials,
                    onSignUpByPhone = onSignUpWithPhone,
                    onSignInAsGuest = onSignInAsGuest,
                    onClearAuthError = onClearAuthError
                )
            }
        }

        // 1. Share APK (Display QR Code for Share APK & Auto-Update)
        item {
            NeliShareApkAndAutoUpdateSection()
        }

        // 2. Subscription (Streaming): Plan Tier, Renewal Date & Step-by-Step Phone Payment Guide
        item {
            NeliAccountSubscriptionSection(
                subState = subState,
                isVerifiedPremiumMember = isVerifiedPremiumMember,
                isFreeForeverVip = isFreeForeverVipUser,
                isSpecialAdFree = isSpecialAdFreeUser,
                nowMs = accountCurrentTimeMs
            )
        }

        // 3. Library & Activity: Continue Watching (with exact last line + %), Watchlist, Favorites & Ratings
        item {
            NeliAccountLibraryAndActivitySection(
                watchlist = watchlist,
                onResumeContinueWatching = onResumeContinueWatching,
                onPlayWatchlistItem = onPlayWatchlistItem
            )
        }

        // Theme Colour Changer Card (Black Mode <-> White Mode)
        item {
            val context = LocalContext.current
            val isLightWhiteMode = NeliThemeManager.isLightMode
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeliSurface)
                    .border(1.dp, Color(0x44A855F7), RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .testTag("account_theme_changer_card"),
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
                        Icon(
                            imageVector = if (isLightWhiteMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = NeliMagenta
                        )
                        Column {
                            Text(
                                text = "Appearance",
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = NeliThemeManager.currentThemeLabel,
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isLightWhiteMode,
                        onCheckedChange = { whiteMode ->
                            NeliThemeManager.setThemeMode(context, whiteMode)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeliMagenta
                        ),
                        modifier = Modifier.testTag("account_theme_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isLightWhiteMode) NeliMagenta else NeliSurfaceVariant)
                            .border(
                                1.dp,
                                if (!isLightWhiteMode) NeliMagenta else Color(0x44A855F7),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                NeliThemeManager.setThemeMode(context, false)
                            }
                            .padding(vertical = 10.dp)
                            .testTag("theme_black_mode_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = if (!isLightWhiteMode) Color.White else NeliTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Black Theme",
                                color = if (!isLightWhiteMode) Color.White else NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isLightWhiteMode) NeliMagenta else NeliSurfaceVariant)
                            .border(
                                1.dp,
                                if (isLightWhiteMode) NeliMagenta else Color(0x44A855F7),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                NeliThemeManager.setThemeMode(context, true)
                            }
                            .padding(vertical = 10.dp)
                            .testTag("theme_white_mode_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LightMode,
                                contentDescription = null,
                                tint = if (isLightWhiteMode) Color.White else NeliTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "White Theme",
                                color = if (isLightWhiteMode) Color.White else NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Picture-in-Picture (PiP) Background Mode Allow / Disallow Card
        item {
            val context = LocalContext.current
            val isPipAllowed = NeliThemeManager.isPipModeAllowed
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeliSurface)
                    .border(1.dp, Color(0x44A855F7), RoundedCornerShape(20.dp))
                    .padding(16.dp)
                    .testTag("account_pip_mode_card"),
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
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = null,
                            tint = NeliGenreCyan
                        )
                        Column {
                            Text(
                                text = "Picture-in-Picture (PiP) Mode",
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = NeliThemeManager.currentPipStatusLabel,
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = isPipAllowed,
                        onCheckedChange = { allowed ->
                            NeliThemeManager.setPipModeAllowed(context, allowed)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeliMagenta
                        ),
                        modifier = Modifier.testTag("account_pip_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPipAllowed) NeliMagenta else NeliSurfaceVariant)
                            .border(
                                1.dp,
                                if (isPipAllowed) NeliMagenta else Color(0x44A855F7),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                NeliThemeManager.setPipModeAllowed(context, true)
                            }
                            .padding(vertical = 10.dp)
                            .testTag("pip_allow_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isPipAllowed) Color.White else NeliTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Allow PiP Mode",
                                color = if (isPipAllowed) Color.White else NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (!isPipAllowed) NeliMagenta else NeliSurfaceVariant)
                            .border(
                                1.dp,
                                if (!isPipAllowed) NeliMagenta else Color(0x44A855F7),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                NeliThemeManager.setPipModeAllowed(context, false)
                            }
                            .padding(vertical = 10.dp)
                            .testTag("pip_disallow_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (!isPipAllowed) Color.White else NeliTextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Disallow PiP Mode",
                                color = if (!isPipAllowed) Color.White else NeliTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }

        // Auto Quality Control & Mobile Data Streaming Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeliSurface)
                    .border(1.dp, Color(0x44A855F7), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.HighQuality,
                        contentDescription = null,
                        tint = NeliGenreCyan
                    )
                    Column {
                        Text(
                            text = "Auto Quality & Mobile Data Control",
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically adjusts video resolution according to your internet speed",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Quality options row (including Low Bando Saver)
                val qualityModes = listOf(
                    "AUTO_ADAPTIVE" to "Auto",
                    "ULTRA_LOW_BANDO_SAVER" to "Low Bando",
                    "LOW_DATA" to "360p Saver",
                    "HIGH_HD" to "Full HD"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    qualityModes.forEach { (key, label) ->
                        val isSelected = selectedQualityMode == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeliMagenta else NeliSurfaceVariant)
                                .border(
                                    1.dp,
                                    if (isSelected) NeliMagenta else Color(0x44A855F7),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    selectedQualityMode = key
                                    onUpdateNetworkPreferences(key, allowMobileData)
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else NeliTextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Mobile Data Toggle
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
                        Icon(
                            imageVector = Icons.Default.NetworkCell,
                            contentDescription = null,
                            tint = NeliMagenta
                        )
                        Column {
                            Text(
                                text = "Stream Live TV on Mobile Data",
                                color = NeliTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Watch Azam TV live channels smoothly on 3G/4G/5G mobile data and Wi-Fi",
                                color = NeliTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = allowMobileData,
                        onCheckedChange = { enabled ->
                            allowMobileData = enabled
                            onUpdateNetworkPreferences(selectedQualityMode, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeliMagenta
                        )
                    )
                }

                HorizontalDivider(color = Color(0x22FFFFFF))

                // Audio Quality & Language Track Preference
                NeliAccountAudioPreferenceRow()
            }
        }

        // Account & Security: Credentials, Connected Device/Session & Notification Toggles
        item {
            NeliAccountSecurityAndNotificationsSection(currentUser = currentUser)
        }

        // Information & Relevant Pages (About Us, Help & FAQ, Terms & Privacy)
        item {
            NeliAccountSupportAndPagesSection(
                onOpenPage = { pageType -> activeInfoPage = pageType }
            )
        }

        // Watch NeliTv on PC & Desktop Section (moved from Top Header Bar to the bottom of Account Page)
        item {
            NeliWatchOnPcAccountSection()
        }
    }
}

/**
 * 100% Google Account Sign In Section for Nelitv (Free Forever With Ads, No Phone or Password forms).
 * When user taps, shows all Google accounts inside the mobile device so they can choose.
 */
@Composable
fun NeliGoogleAccountAuthSection(
    authError: String?,
    isAuthLoading: Boolean,
    savedGoogleAccounts: List<UserAccountEntity>,
    onSignInWithGoogle: () -> Unit,
    onChooseGoogleAccount: () -> Unit,
    onCompleteGoogleSignIn: (email: String, displayName: String) -> Unit,
    onSignInByEmail: (emailOrUsername: String, password: String) -> Unit = { _, _ -> },
    onSignUpByEmail: (username: String, email: String, password: String) -> Unit = { _, _, _ -> },
    onSignInByPhone: (phoneOrUsername: String, password: String) -> Unit = { _, _ -> },
    onSignUpByPhone: (username: String, phoneNumber: String, password: String) -> Unit = { _, _, _ -> },
    onSignInAsGuest: (guestName: String) -> Unit = {},
    onClearAuthError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf("GOOGLE") } // "GOOGLE", "PHONE", or "EMAIL"
    var isPhoneSignUp by rememberSaveable { mutableStateOf(false) }
    var phoneSignUpUsername by rememberSaveable { mutableStateOf("") }
    var phoneOrUsernameInput by rememberSaveable { mutableStateOf("") }
    var phonePasswordInput by rememberSaveable { mutableStateOf("") }
    var isPhonePasswordVisible by rememberSaveable { mutableStateOf(false) }

    var isEmailSignUp by rememberSaveable { mutableStateOf(false) }
    var emailSignUpUsername by rememberSaveable { mutableStateOf("") }
    var emailOrUsernameInput by rememberSaveable { mutableStateOf("") }
    var emailPasswordInput by rememberSaveable { mutableStateOf("") }
    var isEmailPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .tvCardGrow(shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(NeliSurface)
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFF9333EA), Color(0xFF00E5FF), Color(0xFFEC4899))),
                RoundedCornerShape(22.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header: Sign In / Sign Up
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF9333EA), Color(0xFF00E5FF))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (selectedTab) {
                        "EMAIL" -> Icons.Default.Email
                        "PHONE" -> Icons.Default.Phone
                        else -> Icons.Default.Person
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sign In / Sign Up",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Choose Google, Phone Number, or Email",
                    color = NeliGenreCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Method Selector: Google | Phone | By Email
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NeliSurfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == "GOOGLE") NeliMagenta else Color.Transparent)
                    .clickable {
                        selectedTab = "GOOGLE"
                        onClearAuthError()
                    }
                    .padding(vertical = 10.dp)
                    .testTag("auth_method_google_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Google",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == "PHONE") Color(0xFF10B981) else Color.Transparent)
                    .clickable {
                        selectedTab = "PHONE"
                        onClearAuthError()
                    }
                    .padding(vertical = 10.dp)
                    .testTag("auth_method_phone_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Phone",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selectedTab == "EMAIL") NeliMagenta else Color.Transparent)
                    .clickable {
                        selectedTab = "EMAIL"
                        onClearAuthError()
                    }
                    .padding(vertical = 10.dp)
                    .testTag("auth_method_email_tab"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "By Email",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }

        if (!authError.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33EF4444))
                    .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = authError,
                    color = Color(0xFFFCA5A5),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (selectedTab == "GOOGLE") {
            // BIG GOOGLE BUTTON (Real Jetpack Credential Manager Google Sign-In)
            Button(
                onClick = {
                    onSignInWithGoogle()
                },
                enabled = !isAuthLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF9333EA), Color(0xFF00E5FF), Color(0xFFEC4899))
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("big_google_signup_login_button")
            ) {
                if (isAuthLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color(0xFF4285F4),
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Sign Up / Login with Google",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Saved Accounts from previous authenticated sessions
            val accountsToShow = remember(savedGoogleAccounts) {
                savedGoogleAccounts.distinctBy { it.email.lowercase() }
            }

            if (accountsToShow.isNotEmpty()) {
                Text(
                    text = "Saved Accounts:",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    accountsToShow.take(5).forEach { saved ->
                        val accEmail = saved.email
                        val accName = saved.realName
                        val cleanName = if (accName.isNotBlank()) accName else accEmail.substringBefore("@")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeliSurfaceVariant)
                                .border(1.dp, Color(0x33A855F7), RoundedCornerShape(12.dp))
                                .clickable(enabled = !isAuthLoading) {
                                    onSignInWithGoogle()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("saved_account_item_${accEmail}"),
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
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E293B))
                                        .border(1.5.dp, Color(0xFF00E5FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cleanName.take(1).uppercase().ifEmpty { "G" },
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = cleanName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = accEmail,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFF9333EA), Color(0xFFEC4899))
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Login",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == "PHONE") {
            // PHONE NUMBER SECTION: Login (Phone or Username + Password) OR Sign Up (Username + Phone + Password)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_phone_auth_section"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isPhoneSignUp = false
                            onClearAuthError()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!isPhoneSignUp) Color(0xFF34D399) else Color(0x44A855F7)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!isPhoneSignUp) Color(0x2610B981) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("account_phone_login_mode_btn")
                    ) {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            isPhoneSignUp = true
                            onClearAuthError()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isPhoneSignUp) Color(0xFF34D399) else Color(0x44A855F7)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isPhoneSignUp) Color(0x2610B981) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("account_phone_signup_mode_btn")
                    ) {
                        Text(
                            text = "Sign Up",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isPhoneSignUp) {
                    OutlinedTextField(
                        value = phoneSignUpUsername,
                        onValueChange = {
                            phoneSignUpUsername = it
                            onClearAuthError()
                        },
                        label = { Text("Username") },
                        placeholder = { Text("Choose a username") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF34D399))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF34D399),
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_phone_username_input")
                    )
                }

                OutlinedTextField(
                    value = phoneOrUsernameInput,
                    onValueChange = {
                        phoneOrUsernameInput = it
                        onClearAuthError()
                    },
                    label = { Text(if (isPhoneSignUp) "Phone Number" else "Phone Number or Username") },
                    placeholder = { Text(if (isPhoneSignUp) "0712345678" else "0712345678 or username") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF34D399))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPhoneSignUp) KeyboardType.Phone else KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Color(0x44A855F7),
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_phone_identifier_input")
                )

                OutlinedTextField(
                    value = phonePasswordInput,
                    onValueChange = {
                        phonePasswordInput = it
                        onClearAuthError()
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF34D399))
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPhonePasswordVisible = !isPhonePasswordVisible }) {
                            Icon(
                                imageVector = if (isPhonePasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPhonePasswordVisible) "Hide Password" else "Show Password",
                                tint = NeliTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (isPhonePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF34D399),
                        unfocusedBorderColor = Color(0x44A855F7),
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_phone_password_input")
                )

                Button(
                    onClick = {
                        if (!isAuthLoading) {
                            if (isPhoneSignUp) {
                                onSignUpByPhone(
                                    phoneSignUpUsername.trim(),
                                    phoneOrUsernameInput.trim(),
                                    phonePasswordInput
                                )
                            } else {
                                onSignInByPhone(phoneOrUsernameInput.trim(), phonePasswordInput)
                            }
                        }
                    },
                    enabled = !isAuthLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("account_phone_submit_button")
                ) {
                    if (isAuthLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = if (isPhoneSignUp) "Sign Up with Phone" else "Login with Phone",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            // BY EMAIL SECTION: Login (Email or Username + Password) OR Sign Up (Username + Email + Password)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_email_auth_section"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isEmailSignUp = false
                            onClearAuthError()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (!isEmailSignUp) NeliGenreCyan else Color(0x44A855F7)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!isEmailSignUp) Color(0x2600E5FF) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("account_email_login_mode_btn")
                    ) {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            isEmailSignUp = true
                            onClearAuthError()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isEmailSignUp) NeliGenreCyan else Color(0x44A855F7)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isEmailSignUp) Color(0x2600E5FF) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("account_email_signup_mode_btn")
                    ) {
                        Text(
                            text = "Sign Up",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isEmailSignUp) {
                    OutlinedTextField(
                        value = emailSignUpUsername,
                        onValueChange = {
                            emailSignUpUsername = it
                            onClearAuthError()
                        },
                        label = { Text("Username") },
                        placeholder = { Text("Choose a username") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = NeliGenreCyan)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeliMagenta,
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_email_username_input")
                    )
                }

                OutlinedTextField(
                    value = emailOrUsernameInput,
                    onValueChange = {
                        emailOrUsernameInput = it
                        onClearAuthError()
                    },
                    label = { Text(if (isEmailSignUp) "Email Address" else "Email or Username") },
                    placeholder = { Text(if (isEmailSignUp) "name@email.com" else "Email or Username") },
                    leadingIcon = {
                        Icon(Icons.Default.Email, contentDescription = null, tint = NeliGenreCyan)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isEmailSignUp) KeyboardType.Email else KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeliMagenta,
                        unfocusedBorderColor = Color(0x44A855F7),
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_email_identifier_input")
                )

                OutlinedTextField(
                    value = emailPasswordInput,
                    onValueChange = {
                        emailPasswordInput = it
                        onClearAuthError()
                    },
                    label = { Text("Password") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = NeliGenreCyan)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isEmailPasswordVisible = !isEmailPasswordVisible }) {
                            Icon(
                                imageVector = if (isEmailPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isEmailPasswordVisible) "Hide Password" else "Show Password",
                                tint = NeliTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (isEmailPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeliMagenta,
                        unfocusedBorderColor = Color(0x44A855F7),
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_email_password_input")
                )

                Button(
                    onClick = {
                        if (!isAuthLoading) {
                            if (isEmailSignUp) {
                                onSignUpByEmail(
                                    emailSignUpUsername.trim(),
                                    emailOrUsernameInput.trim(),
                                    emailPasswordInput
                                )
                            } else {
                                onSignInByEmail(emailOrUsernameInput.trim(), emailPasswordInput)
                            }
                        }
                    },
                    enabled = !isAuthLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("account_email_submit_button")
                ) {
                    if (isAuthLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isEmailSignUp) "Sign Up with Email" else "Login with Email",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
