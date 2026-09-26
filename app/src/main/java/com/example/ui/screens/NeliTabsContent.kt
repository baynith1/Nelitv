package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.FirebaseConfigEntity
import com.example.ui.components.NeliAdaptiveBannerAd
import com.example.ui.components.NeliNativeSearchAd
import com.example.data.local.UserAccountEntity
import com.example.data.local.WatchlistItemEntity
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.components.CategoryChipRow
import com.example.ui.components.ChannelCard
import com.example.ui.components.NeliShareApkAndAutoUpdateSection
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliDurationViolet
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager

@Composable
fun MediaPosterCard(
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
            .size(300, 450)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = modifier
            .width(152.dp)
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
@Composable
fun DiscoveryTabContent(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    mediaCatalog: List<MediaContent>,
    episodesCatalog: List<EpisodeItem>,
    onMediaSelected: (MediaContent) -> Unit,
    onPlayMedia: (MediaContent) -> Unit,
    modifier: Modifier = Modifier
) {
    val discoverySections = listOf("All", "Movies", "Series", "Adults")

    // Strictly deduplicated movies grouped ONLY by their first genre
    val moviesByFirstGenre = remember(mediaCatalog) {
        MediaContentRepository.getMoviesStrictlyByFirstGenre(mediaCatalog)
    }

    val seriesList = remember(mediaCatalog) {
        mediaCatalog.filter { it.published && it.isSeries && !it.isAdultContent }
            .distinctBy { it.id }
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("discovery_tab_screen"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. YouTube-style Top Filter Bar (All | Movies | Series | Adults)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discovery_filter_chips")
            ) {
                items(discoverySections) { section ->
                    val isSelected = section.equals(selectedFilter, ignoreCase = true)
                    val badgeColor = if (section == "Adults") Color(0xFFEF4444) else NeliTextPrimary
                    val textColor = if (isSelected) {
                        if (section == "Adults") Color.White else NeliSurface
                    } else {
                        NeliTextPrimary
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) badgeColor else NeliSurfaceVariant)
                            .clickable { onFilterSelected(section) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("discovery_chip_${section.lowercase()}")
                    ) {
                        val countLabel = when (section) {
                            "Movies" -> "Movies"
                            "Series" -> "Series"
                            "Adults" -> "Adults 18+"
                            else -> "All"
                        }
                        Text(
                            text = countLabel,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Static Discovery Banner ("Furahia Movie Nzuri kutoka kwa Madjs Wazuri") — replaces top slider
        item {
            DiscoveryMadjsBanner()
        }

        // 3. MOVIES SECTION (Grouped by First Genre) + Banner after a content genre section
        if (selectedFilter.equals("All", true) || selectedFilter.equals("Movies", true)) {
            itemsIndexed(
                items = moviesByFirstGenre,
                key = { _, pair -> "first_genre_${pair.first}" }
            ) { index, (firstGenreName, genreMovies) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("movie_first_genre_row_$firstGenreName")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = firstGenreName,
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "${genreMovies.size}",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(genreMovies, key = { "movie_${firstGenreName}_${it.id}" }) { movie ->
                            MediaPosterCard(
                                media = movie,
                                onClick = { onMediaSelected(movie) }
                            )
                        }
                    }

                    // Discovery: Banner in every movie genre category section
                    NeliAdaptiveBannerAd(
                        placementKey = if (index == 0) {
                            "discovery_after_genre_section"
                        } else {
                            "discovery_after_genre_${firstGenreName.lowercase().replace(" ", "_")}"
                        }
                    )
                }
            }
        }

        // 4. SERIES SECTION + One banner below an appropriate section
        if ((selectedFilter.equals("All", true) || selectedFilter.equals("Series", true)) && seriesList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("discovery_series_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Series & Episodes",
                            color = NeliTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${seriesList.size} Series • ${episodesCatalog.size} Episodes",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(seriesList, key = { "disc_series_${it.id}" }) { series ->
                            MediaPosterCard(
                                media = series,
                                onClick = { onMediaSelected(series) }
                            )
                        }
                    }

                    // SERIES: One banner below the Series section
                    NeliAdaptiveBannerAd(placementKey = "series_below_section")
                }
            }
        }

        // 5. ADULTS (18+) SECTION ORGANIZED BY GENRES & CATEGORIES WITH GUARANTEED POSTER & THUMBNAIL IMAGES
        if ((selectedFilter.equals("All", true) || selectedFilter.equals("Adults", true)) && adultList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("discovery_adults_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFEF4444))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "18+ ADULTS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "Adults • Genres & Categories",
                                color = NeliTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${adultList.size}",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    // Adult Genre & Category Filter Bar (All | X Video | XXX | Porn | Erotic Romance | Firebase Categories...)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("adult_genre_category_chips")
                    ) {
                        items(adultCategories, key = { "adult_cat_chip_$it" }) { cat ->
                            val isCatSelected = cat.equals(selectedAdultCategory, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isCatSelected) Color(0xFFEF4444) else NeliSurfaceVariant
                                    )
                                    .clickable { selectedAdultCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("adult_cat_chip_${cat.lowercase()}")
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isCatSelected) Color.White else NeliTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Organized Adult Genre & Category Rows
            items(
                items = adultsByGenreAndCategory,
                key = { "adult_genre_section_${it.first}" }
            ) { (adultCategoryName, categoryItems) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag("adult_genre_row_$adultCategoryName")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x33EF4444))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "18+",
                                    color = Color(0xFFEF4444),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = adultCategoryName,
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "${categoryItems.size}",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(categoryItems, key = { "disc_adult_${adultCategoryName}_${it.id}" }) { adultMedia ->
                            AdultMediaPosterThumbnailCard(
                                media = adultMedia,
                                onSelectDetails = { onMediaSelected(adultMedia) },
                                onPlayDirect = { onPlayMedia(adultMedia) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryMadjsBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2B0B3F),
                        Color(0xFF141B36),
                        Color(0xFF0E121B)
                    )
                )
            )
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("discovery_madjs_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFF0033)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Swahili DJs",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = DISCOVERY_MADJS_BANNER_TEXT,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Sinema na Series zilizotafsiriwa kwa Kiswahili • HD",
                    color = NeliGenreCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * SEARCH TAB:
 * Everything is searchable (Live TV, Movies, Series, Adults, and Episodes) in a vertical multi-column grid.
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
    val searchFilterTabs = remember(mediaList) {
        val tabs = LinkedHashSet<String>()
        tabs.addAll(
            listOf(
                "All",
                "Live TV",
                "Movies",
                "Series",
                "Adults",
                "X Video",
                "XXX",
                "Porn",
                "Swahili",
                "Action",
                "Sports",
                "Drama"
            )
        )
        MediaContentRepository.getAdultGenreAndCategoryFilters(mediaList)
            .filter { !it.equals("All", ignoreCase = true) }
            .forEach { tabs.add(it) }
        tabs.toList()
    }

    val allChannels = if (liveChannels.isNotEmpty()) liveChannels else ChannelRepository.channels

    val filteredChannels = remember(searchQuery, selectedCategory, allChannels) {
        if (selectedCategory.equals("Movies", true) ||
            selectedCategory.equals("Series", true) ||
            selectedCategory.equals("Adults", true) ||
            MediaContentRepository.isAdultKeywordOrQuery(selectedCategory) ||
            MediaContentRepository.isAdultKeywordOrQuery(searchQuery)
        ) {
            emptyList()
        } else {
            val catFilter = if (selectedCategory.equals("Live TV", true) || selectedCategory.equals("Swahili", true)) {
                "All"
            } else {
                selectedCategory
            }
            ChannelRepository.filterChannels(searchQuery, catFilter)
        }
    }

    val filteredMedia = remember(searchQuery, selectedCategory, mediaList) {
        if (selectedCategory.equals("Live TV", true)) {
            emptyList()
        } else {
            mediaList.filter { item ->
                MediaContentRepository.matchesMediaSearch(
                    item = item,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory
                )
            }
        }
    }

    val filteredEpisodes = remember(searchQuery, selectedCategory, episodesList) {
        val q = searchQuery.trim()
        if (selectedCategory.equals("Live TV", true) ||
            selectedCategory.equals("Movies", true) ||
            selectedCategory.equals("Adults", true) ||
            MediaContentRepository.isAdultKeywordOrQuery(selectedCategory) ||
            MediaContentRepository.isAdultKeywordOrQuery(q)
        ) {
            emptyList()
        } else if (q.isEmpty() && !selectedCategory.equals("Series", true)) {
            emptyList()
        } else {
            episodesList.filter { ep ->
                q.isEmpty() ||
                    ep.name.contains(q, ignoreCase = true) ||
                    ep.overview.contains(q, ignoreCase = true) ||
                    ep.narrationLanguage.contains(q, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_tab_screen")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_tab_input"),
                placeholder = {
                    Text(
                        text = "Search channels, movies, series...",
                        color = NeliTextSecondary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NeliTextSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NeliSurfaceVariant,
                    unfocusedContainerColor = NeliSurfaceVariant,
                    focusedBorderColor = NeliMagenta,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = NeliTextPrimary,
                    unfocusedTextColor = NeliTextPrimary
                )
            )
        }

        CategoryChipRow(
            categories = searchFilterTabs,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        if (filteredMedia.isEmpty() && filteredEpisodes.isEmpty() && filteredChannels.isEmpty()) {
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
                        text = "No matching channels, movies, series, or adult titles found",
                        color = NeliTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // Vertical multi-column layout only — no horizontal scrolling in Search
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 155.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("channels_grid")
            ) {
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
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    items(filteredMedia, key = { "search_media_${it.id}" }) { media ->
                        MediaPosterCard(
                            media = media,
                            onClick = { onMediaSelected(media) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // SEARCH: Native ad between result groups occasionally
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "search_native_ad_between_groups"
                    ) {
                        NeliNativeSearchAd(placementKey = "search_between_result_groups")
                    }
                }

                if (filteredEpisodes.isNotEmpty()) {
                    item(
                        span = { GridItemSpan(maxLineSpan) },
                        key = "search_header_episodes"
                    ) {
                        Text(
                            text = "Series Episodes (${filteredEpisodes.size})",
                            color = NeliGenreCyan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    items(filteredEpisodes, key = { "search_ep_${it.id}" }) { ep ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeliCardPurple)
                                .border(1.dp, NeliMagenta.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable { onChannelSelected(ep.toPlayableChannel()) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = "S${ep.seasonNumber}E${ep.episodeNumber} • ${ep.name}",
                                    color = NeliTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ep.runtime} min • ${if (ep.narrated) ep.narrationLanguage else "HD"}",
                                    color = NeliGenreCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

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
    onRetryDownload: (DownloadedItemEntity) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onPlayDownloaded: (DownloadedItemEntity) -> Unit,
    onDeleteDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadableCatalog = remember(mediaCatalog, downloadedIds) {
        mediaCatalog.filter { it.published && it.downloadEnabled }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("download_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        if (isOfflineMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliCardPurple)
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .testTag("offline_mode_auto_redirect_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Offline mode",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Watch your downloaded videos without internet",
                            color = NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        if (!downloadBannerMessage.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1038))
                        .border(1.dp, NeliMagenta.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .clickable { onDismissBanner() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = downloadBannerMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Header Card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Downloads",
                    color = NeliTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                if (downloads.isNotEmpty()) {
                    Text(
                        text = "${downloads.size} videos",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliSurface)
                        .border(0.5.dp, Color(0xFF252D40), RoundedCornerShape(14.dp))
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
                        text = "Videos you download will appear here.",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(downloads, key = { it.id }) { item ->
                val activePct = downloadProgress[item.id]
                val isDownloading = activePct != null ||
                    item.downloadStatus == "DOWNLOADING" ||
                    downloadingIds.contains(item.id)
                val isPausedError = !isDownloading && item.downloadStatus == "PAUSED_ERROR"
                val displayPct = (activePct ?: item.progressPercent).coerceIn(1, 100)
                val imageUrl = MediaContentRepository.resolveGuaranteedMediaImageUrl(
                    item.backdropUrl,
                    item.posterUrl,
                    item.streamUrl
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NeliSurface)
                        .border(
                            width = 1.dp,
                            color = when {
                                isDownloading -> NeliGenreCyan.copy(alpha = 0.7f)
                                isPausedError -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                else -> Color(0x33A855F7)
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = !isDownloading && !isPausedError) {
                            onPlayDownloaded(item)
                        }
                        .padding(12.dp)
                        .testTag("download_item_${item.id}")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val dlContext = LocalContext.current
                        val dlThumbReq = remember(item.id, imageUrl) {
                            ImageRequest.Builder(dlContext)
                                .data(imageUrl)
                                .size(300, 188)
                                .crossfade(false)
                                .build()
                        }
                        Box(
                            modifier = Modifier
                                .width(104.dp)
                                .aspectRatio(16f / 10f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeliSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = dlThumbReq,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isDownloading -> NeliGenreCyan
                                            isPausedError -> Color(0xFFEF4444)
                                            else -> NeliMagenta
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDownloading) {
                                    Text(
                                        text = "$displayPct%",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Offline",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title.replace("\n", " "),
                                color = NeliTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${item.genre} • ${item.duration}",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        isDownloading -> Icons.Default.Download
                                        else -> Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isDownloading -> NeliGenreCyan
                                        isPausedError -> Color(0xFFEF4444)
                                        else -> Color(0xFF10B981)
                                    },
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = when {
                                        isDownloading -> item.fileSizeLabel.ifBlank {
                                            "Background downloading ($displayPct%)..."
                                        }
                                        else -> item.fileSizeLabel
                                    },
                                    color = when {
                                        isDownloading -> NeliGenreCyan
                                        isPausedError -> Color(0xFFEF4444)
                                        else -> Color(0xFF10B981)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isPausedError) {
                            Button(
                                onClick = { onRetryDownload(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (isDownloading) {
                                    onCancelDownload(item.id)
                                } else {
                                    onDeleteDownload(item.id)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Remove Download",
                                tint = NeliMagenta
                            )
                        }
                    }

                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { displayPct / 100f },
                            color = NeliMagenta,
                            trackColor = NeliSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                }
            }
        }

        // Download page / DOWNLOADS: One adaptive banner in a natural position
        item(key = "downloads_natural_ad_banner") {
            NeliAdaptiveBannerAd(placementKey = "downloads_natural_position")
        }

        // Recommended Downloads inside the Download tab
        if (downloadableCatalog.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Available to Download",
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                            .size(width = 56.dp, height = 74.dp)
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${media.primaryGenre} • ${media.duration} • ${if (media.narrated) media.narrationLanguage else "HD"}",
                            color = NeliGenreCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isCurrentlyDownloading) {
                            Text(
                                text = "Downloading in background ($livePct%)...",
                                color = NeliMagenta,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!isAlreadyDownloaded && !isCurrentlyDownloading) {
                                onStartQuickDownload(media)
                            }
                        },
                        enabled = !isAlreadyDownloaded && !isCurrentlyDownloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isAlreadyDownloaded -> Color(0xFF10B981)
                                isCurrentlyDownloading -> NeliSurfaceVariant
                                else -> NeliMagenta
                            }
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(40.dp)
                            .testTag("quick_download_btn_${media.id}")
                    ) {
                        Icon(
                            imageVector = if (isAlreadyDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isAlreadyDownloaded -> "Saved"
                                isCurrentlyDownloading -> "$livePct%"
                                else -> "Download"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccountTabContent(
    currentUser: UserAccountEntity?,
    authError: String?,
    googleFallbackMessage: String? = null,
    isAuthLoading: Boolean,
    firebaseConfig: FirebaseConfigEntity,
    watchlist: List<WatchlistItemEntity>,
    downloadsCount: Int,
    onSignInWithGoogle: () -> Unit = {},
    onSignUp: (realName: String, email: String, password: String) -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignOut: () -> Unit,
    onClearAuthError: () -> Unit,
    onUpdateNetworkPreferences: (networkMode: String, allowMobileData: Boolean) -> Unit,
    onPlayWatchlistItem: (WatchlistItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeInfoPage by rememberSaveable { mutableStateOf<AccountInfoPageType?>(null) }
    var isRegisterMode by rememberSaveable { mutableStateOf(true) }
    var realName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    var selectedQualityMode by remember(firebaseConfig.networkMode) {
        mutableStateOf(firebaseConfig.networkMode.ifBlank { "AUTO_ADAPTIVE" })
    }
    var allowMobileData by remember(firebaseConfig.allowMobileData) {
        mutableStateOf(firebaseConfig.allowMobileData)
    }

    if (activeInfoPage != null) {
        AccountInfoDetailScreen(
            pageType = activeInfoPage!!,
            onBack = { activeInfoPage = null },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("account_tab_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        if (currentUser != null) {
            // Logged-in User Profile Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF391473), Color(0xFF1F0940))
                            )
                        )
                        .border(1.dp, NeliMagenta.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(NeliMagenta),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.realName.trim().take(1).uppercase().ifEmpty { "N" },
                                color = Color.White,
                                fontSize = 24.sp,
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
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active Member",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = currentUser.email,
                                color = NeliGenreCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Account Synced • $downloadsCount Offline • ${watchlist.size} Watchlist",
                                color = NeliTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = NeliMagenta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign Out",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            // Sign Up / Sign In Authentication Card (Automatic login upon registration)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(NeliSurface)
                        .border(1.dp, Color(0x44A855F7), RoundedCornerShape(22.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeliMagenta),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isRegisterMode) "Create Account" else "Sign In",
                                color = NeliTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Sign in to sync your watchlist and downloads",
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // One-Tap Google Account Sign-In Button
                    Button(
                        onClick = onSignInWithGoogle,
                        enabled = !isAuthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = NeliCardPurple),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .border(1.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .testTag("google_sign_in_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = NeliMagenta,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    // Fallback notice when Google Sign-In fails or is unavailable: "Use email instead" / Register
                    if (!googleFallbackMessage.isNullOrBlank()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x33F59E0B))
                                .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("google_fallback_email_notice"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = googleFallbackMessage,
                                color = Color(0xFFFDE68A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        isRegisterMode = false
                                        onClearAuthError()
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("use_email_instead_button")
                                ) {
                                    Text(
                                        text = "Use Email Instead",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        isRegisterMode = true
                                        onClearAuthError()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("fallback_register_button")
                                ) {
                                    Text(
                                        text = "Register Account",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Mode Switcher Tabs (Sign Up / Sign In)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeliSurfaceVariant)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isRegisterMode) NeliMagenta else Color.Transparent)
                                .clickable {
                                    isRegisterMode = true
                                    onClearAuthError()
                                }
                                .padding(vertical = 10.dp)
                                .testTag("mode_signup_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign Up",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isRegisterMode) NeliMagenta else Color.Transparent)
                                .clickable {
                                    isRegisterMode = false
                                    onClearAuthError()
                                }
                                .padding(vertical = 10.dp)
                                .testTag("mode_signin_tab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
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

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = realName,
                            onValueChange = {
                                realName = it
                                onClearAuthError()
                            },
                            label = { Text("Full Name (Real Name)") },
                            placeholder = { Text("Enter your full name") },
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
                                .testTag("auth_real_name_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeliMagenta,
                                unfocusedBorderColor = Color(0x44A855F7),
                                focusedTextColor = NeliTextPrimary,
                                unfocusedTextColor = NeliTextPrimary
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            onClearAuthError()
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
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
                            .testTag("auth_email_input"),
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
                        value = password,
                        onValueChange = {
                            password = it
                            onClearAuthError()
                        },
                        label = { Text("Password") },
                        placeholder = { Text("At least 6 characters") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NeliMagenta
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                    tint = NeliTextSecondary
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeliMagenta,
                            unfocusedBorderColor = Color(0x44A855F7),
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        )
                    )

                    Button(
                        onClick = {
                            if (isRegisterMode) {
                                onSignUp(realName, email, password)
                            } else {
                                onSignIn(email, password)
                            }
                        },
                        enabled = !isAuthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_submit_button")
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = if (isRegisterMode) "Register & Sign In Automatically" else "Sign In",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
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
                                color = Color.White,
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
                                text = "Stream & Download on Mobile Data",
                                color = NeliTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Watch movies, series & live TV smoothly on 3G/4G/5G mobile data and Wi-Fi",
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
            }
        }

        // Share APK (Scan to Download QR + Share Link) & Check/Auto-Update from GitHub Release (v1.0.0)
        item {
            NeliShareApkAndAutoUpdateSection()
        }

        // Customer Care (+255760816851 • Neliplay Customercare • Alex Michael Baineth), About Us, Contact Us & Relevant Pages
        item {
            NeliAccountSupportAndPagesSection(
                onOpenPage = { pageType -> activeInfoPage = pageType }
            )
        }

        // My Watchlist Section
        if (watchlist.isNotEmpty()) {
            item {
                Text(
                    text = "My Watchlist (${watchlist.size})",
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            items(watchlist, key = { it.id }) { wItem ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliSurface)
                        .border(1.dp, Color(0x33A855F7), RoundedCornerShape(14.dp))
                        .clickable { onPlayWatchlistItem(wItem) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = wItem.title,
                            color = NeliTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${wItem.genre} • ${wItem.duration}",
                            color = NeliGenreCyan,
                            fontSize = 11.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = NeliMagenta
                    )
                }
            }
        }
    }
}
