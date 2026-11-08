package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.NeliAdManager
import com.example.ads.StartIoBannerAd
import com.example.ads.StartIoNativeCardAd
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager

private enum class SearchCategoryFilter(val label: String) {
    ALL("All"),
    LIVE_TV("Live TV"),
    MOVIES("Movies"),
    SERIES("Series"),
    SWAHILI_DJ("DJ Swahili"),
    SPORTS("Sports"),
    KIDS("Kids")
}

@Composable
fun SearchScreen(
    channels: List<LiveChannel>,
    mediaCatalog: List<MediaContent>,
    onChannelSelected: (LiveChannel) -> Unit,
    onMediaSelected: (MediaContent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isLightMode = NeliThemeManager.isLightMode
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(SearchCategoryFilter.ALL) }

    val cleanQuery = query.trim().lowercase()

    // Filtered Live Channels
    val filteredChannels by remember(cleanQuery, selectedFilter, channels) {
        derivedStateOf {
            if (selectedFilter == SearchCategoryFilter.MOVIES || selectedFilter == SearchCategoryFilter.SERIES) {
                emptyList()
            } else {
                channels.filter { ch ->
                    val matchesQuery = cleanQuery.isEmpty() ||
                        ch.name.lowercase().contains(cleanQuery) ||
                        ch.description.lowercase().contains(cleanQuery) ||
                        ch.categories.any { it.lowercase().contains(cleanQuery) }

                    val matchesFilter = when (selectedFilter) {
                        SearchCategoryFilter.ALL -> true
                        SearchCategoryFilter.LIVE_TV -> true
                        SearchCategoryFilter.SPORTS -> ch.categories.any { it.contains("sport", ignoreCase = true) }
                        SearchCategoryFilter.KIDS -> ch.categories.any { it.contains("kid", ignoreCase = true) }
                        SearchCategoryFilter.SWAHILI_DJ -> ch.language.equals("sw", ignoreCase = true)
                        else -> false
                    }
                    matchesQuery && matchesFilter
                }
            }
        }
    }

    // Filtered Movies & Series
    val filteredMedia by remember(cleanQuery, selectedFilter, mediaCatalog) {
        derivedStateOf {
            if (selectedFilter == SearchCategoryFilter.LIVE_TV || selectedFilter == SearchCategoryFilter.SPORTS) {
                emptyList()
            } else {
                mediaCatalog.filter { media ->
                    val matchesQuery = cleanQuery.isEmpty() ||
                        media.title.lowercase().contains(cleanQuery) ||
                        media.synopsis.lowercase().contains(cleanQuery) ||
                        media.genre.lowercase().contains(cleanQuery) ||
                        media.cast.any { it.name.lowercase().contains(cleanQuery) } ||
                        media.narrationLanguage.lowercase().contains(cleanQuery)

                    val matchesFilter = when (selectedFilter) {
                        SearchCategoryFilter.ALL -> true
                        SearchCategoryFilter.MOVIES -> !media.isSeries
                        SearchCategoryFilter.SERIES -> media.isSeries
                        SearchCategoryFilter.SWAHILI_DJ -> media.narrated
                        SearchCategoryFilter.KIDS -> media.genre.contains("Animation", ignoreCase = true) ||
                            media.genre.contains("Family", ignoreCase = true)
                        else -> false
                    }
                    matchesQuery && matchesFilter
                }
            }
        }
    }

    val isSearchActive = cleanQuery.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        // Top Search Input Glass Card
        Surface(
            color = if (isLightMode) Color(0xF7FFFFFF) else Color(0xF00A0F26),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isLightMode) Color(0x33FF2E7E) else Color(0x3300E5FF)
            )
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_text_input"),
                placeholder = {
                    Text(
                        text = "Search movies, live TV, series, DJ Swahili...",
                        color = NeliTextSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (cleanQuery.isNotEmpty()) NeliGenreCyan else NeliTextSecondary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                focusManager.clearFocus()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = NeliTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = NeliTextPrimary,
                    unfocusedTextColor = NeliTextPrimary,
                    cursorColor = NeliGenreCyan
                )
            )
        }

        // Category Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SearchCategoryFilter.entries) { filter ->
                val isSelected = selectedFilter == filter
                val chipBg = if (isSelected) {
                    Brush.horizontalGradient(listOf(Color(0xFFFF2E7E), Color(0xFFFF6B00)))
                } else {
                    Brush.horizontalGradient(
                        if (isLightMode) listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                        else listOf(Color(0x221E293B), Color(0x33334155))
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .border(
                            1.dp,
                            if (isSelected) Color(0xFFFF2E7E) else Color(0x22FFFFFF),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        color = if (isSelected) Color.White else NeliTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Search Results OR Recommendations
        if (isSearchActive) {
            // Live Search Results
            val totalResults = filteredChannels.size + filteredMedia.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Results ($totalResults)",
                    color = NeliTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "for \"$query\"",
                    color = NeliGenreCyan,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (totalResults == 0) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = NeliTextSecondary.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Hakuna kilichopatikana kwa \"$query\"",
                            color = NeliTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Jaribu kutafuta jina la channel (mfano: Azam Sports, Sinema Zetu) au movie (mfano: Action, Bongo, DJ)",
                            color = NeliTextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.widthIn(max = 280.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Channels Section
                    if (filteredChannels.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LiveTv, contentDescription = null, tint = Color(0xFFFF2E7E), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Live TV Channels (${filteredChannels.size})", color = NeliTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        items(filteredChannels, key = { "ch_${it.id}" }) { channel ->
                            SearchChannelCard(
                                channel = channel,
                                onClick = {
                                    (context as? Activity)?.let { act ->
                                        NeliAdManager.showChannelOpeningAd(act, channel.name) {
                                            onChannelSelected(channel)
                                        }
                                    } ?: onChannelSelected(channel)
                                }
                            )
                        }
                    }

                    // Native Ad Placement in Search
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StartIoNativeCardAd(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    // Movies Section
                    if (filteredMedia.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Default.Movie, contentDescription = null, tint = NeliGenreCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Movies & Series (${filteredMedia.size})", color = NeliTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        items(filteredMedia, key = { "media_${it.id}" }) { media ->
                            SearchMediaCard(
                                media = media,
                                onClick = { onMediaSelected(media) }
                            )
                        }
                    }
                }
            }
        } else {
            // Default: Trending, Recommendations & Genres
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_recommendations_list"),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Recommended Live TV Channels
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🔥 Trending Live TV",
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(channels.take(10), key = { "rec_ch_${it.id}" }) { channel ->
                                SearchChannelCard(
                                    channel = channel,
                                    modifier = Modifier.width(140.dp),
                                    onClick = {
                                        (context as? Activity)?.let { act ->
                                            NeliAdManager.showChannelOpeningAd(act, channel.name) {
                                                onChannelSelected(channel)
                                            }
                                        } ?: onChannelSelected(channel)
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. Start.io Banner Ad in Search
                item {
                    StartIoBannerAd(
                        adTag = "search_results_banner",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }

                // 3. Recommended Movies & Series
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🎬 Recommended Cinema",
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(mediaCatalog.take(10), key = { "rec_media_${it.id}" }) { media ->
                                SearchMediaCard(
                                    media = media,
                                    modifier = Modifier.width(130.dp),
                                    onClick = { onMediaSelected(media) }
                                )
                            }
                        }
                    }
                }

                // 4. Start.io Native Ad in Search
                item {
                    StartIoNativeCardAd(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchChannelCard(
    channel: LiveChannel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("search_channel_card_${channel.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0101935)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33263560))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(channel.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = channel.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )

                // Live Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE50914))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = channel.name,
                color = NeliTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Watch Free • Ads",
                color = Color(0xFF00E5FF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SearchMediaCard(
    media: MediaContent,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("search_media_card_${media.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xF0101935)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33263560))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Color(0xFF1E293B))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(media.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Badge top right (HD / DJ SWAHILI)
                if (media.narrated) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFF2E7E))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("DJ SWAHILI", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = media.title,
                    color = NeliTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = if (media.isSeries) "Series • Free" else "Movie • Free",
                    color = NeliTextSecondary,
                    fontSize = 9.sp
                )
            }
        }
    }
}
