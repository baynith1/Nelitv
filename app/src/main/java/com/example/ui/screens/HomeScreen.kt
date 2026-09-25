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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.OfflineDownloadManager
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.NeliViewModel
import com.example.ui.components.BottomNavTab
import com.example.ui.components.LiveIndicatorBadge
import com.example.ui.components.NeliBottomBar
import com.example.ui.components.TopNavBar
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

@Composable
fun HomeScreen(
    onChannelSelected: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
    neliViewModel: NeliViewModel = viewModel()
) {
    val context = LocalContext.current
    val isOfflineMode by neliViewModel.isOfflineMode.collectAsState()

    var selectedTab by rememberSaveable {
        mutableStateOf(if (isOfflineMode) BottomNavTab.DOWNLOAD else BottomNavTab.HOME)
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var selectedGenreTab by rememberSaveable { mutableStateOf("Popular") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var selectedMediaId by rememberSaveable { mutableStateOf<String?>(null) }

    val mediaCatalog by neliViewModel.mediaCatalog.collectAsState()
    val episodesCatalog by neliViewModel.episodesCatalog.collectAsState()
    val liveChannels by neliViewModel.liveChannels.collectAsState()
    val downloads by neliViewModel.downloads.collectAsState()
    val downloadedIds by neliViewModel.downloadedIds.collectAsState()
    val downloadProgress by neliViewModel.downloadProgress.collectAsState()
    val activeDownloadTitles by neliViewModel.activeDownloadTitles.collectAsState()
    val downloadBannerMessage by neliViewModel.downloadBannerMessage.collectAsState()
    val watchlist by neliViewModel.watchlistItems.collectAsState()
    val watchlistIds by neliViewModel.watchlistIds.collectAsState()
    val firebaseConfig by neliViewModel.firebaseConfig.collectAsState()
    val currentUser by neliViewModel.currentUser.collectAsState()
    val authError by neliViewModel.authError.collectAsState()
    val googleFallbackMessage by neliViewModel.googleFallbackMessage.collectAsState()
    val isAuthLoading by neliViewModel.isAuthLoading.collectAsState()

    // Automatically direct user to the Downloads page when entering the app without internet
    LaunchedEffect(isOfflineMode) {
        if (isOfflineMode) {
            selectedMediaId = null
            selectedTab = BottomNavTab.DOWNLOAD
        }
    }

    // YouTube-style automatic Google Sign-In on first launch using the Activity context
    LaunchedEffect(Unit) {
        neliViewModel.attemptAutoGoogleSignInIfNeeded(context)
    }

    val activeDetailMedia = remember(selectedMediaId, mediaCatalog) {
        selectedMediaId?.let { id -> mediaCatalog.find { it.id == id } }
    }

    // Helper that resolves offline internal storage path if a movie/episode was already downloaded
    val playWithOfflineResolution: (LiveChannel) -> Unit = { playable ->
        val cleanId = playable.id.removePrefix("vod_").removePrefix("ep_").removePrefix("dl_")
        val localEntry = downloads.find { it.id == cleanId || it.id == playable.id }
        if (localEntry != null && localEntry.localFilePath.isNotBlank()) {
            val resolvedUrl = OfflineDownloadManager.resolvePlayableUrl(
                streamUrl = playable.streamUrl,
                localFilePath = localEntry.localFilePath,
                context = context
            )
            onChannelSelected(playable.copy(streamUrl = resolvedUrl))
        } else {
            onChannelSelected(playable)
        }
    }

    if (activeDetailMedia != null) {
        val seriesEpisodes = remember(activeDetailMedia.id, episodesCatalog) {
            MediaContentRepository.getEpisodesForSeries(activeDetailMedia.id)
        }
        val recommendedMedia = remember(activeDetailMedia.id, activeDetailMedia.genre, mediaCatalog) {
            MediaContentRepository.getRelatedMedia(activeDetailMedia.id, activeDetailMedia.genre)
        }
        MediaDetailScreen(
            media = activeDetailMedia,
            episodes = seriesEpisodes,
            recommendedMedia = recommendedMedia,
            isInWatchlist = watchlistIds.contains(activeDetailMedia.id),
            isDownloaded = downloadedIds.contains(activeDetailMedia.id),
            downloadedIds = downloadedIds,
            downloadProgress = downloadProgress,
            downloadBannerMessage = downloadBannerMessage,
            onBack = { selectedMediaId = null },
            onPlayChannel = playWithOfflineResolution,
            onToggleWatchlist = { neliViewModel.toggleWatchlist(it) },
            onDownloadMedia = { media, quality ->
                neliViewModel.addDownload(media, quality)
            },
            onDownloadEpisode = { ep, quality ->
                neliViewModel.addEpisodeDownload(
                    episode = ep,
                    seriesTitle = activeDetailMedia.title,
                    seriesPoster = activeDetailMedia.posterUrl,
                    quality = quality
                )
            },
            onSelectRecommendedMedia = { recommended ->
                selectedMediaId = recommended.id
            },
            onOpenDownloadsTab = {
                neliViewModel.dismissDownloadBanner()
                selectedMediaId = null
                selectedTab = BottomNavTab.DOWNLOAD
            },
            onDismissDownloadBanner = {
                neliViewModel.dismissDownloadBanner()
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        containerColor = NeliBackground,
        topBar = {
            TopNavBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { query ->
                    searchQuery = query
                    if (query.isNotBlank() && selectedTab != BottomNavTab.SEARCH) {
                        selectedTab = BottomNavTab.SEARCH
                    }
                },
                isSearchOpen = isSearchOpen,
                onToggleSearch = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                totalChannels = liveChannels.size
            )
        },
        bottomBar = {
            NeliBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { innerPadding ->
        val activeBgDownloadEntry = downloadProgress.entries.firstOrNull()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activeBgDownloadEntry != null) {
                val activeId = activeBgDownloadEntry.key
                val activePct = activeBgDownloadEntry.value
                val activeTitle = activeDownloadTitles[activeId] ?: "Movie / Episode"
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E0B3B))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { selectedTab = BottomNavTab.DOWNLOAD }
                        .testTag("global_background_download_bar")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background Download: $activeTitle",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$activePct%",
                            color = NeliGenreCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (activePct.coerceIn(0, 100)) / 100f },
                        color = NeliMagenta,
                        trackColor = NeliSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when (selectedTab) {
                BottomNavTab.HOME -> {
                    HomeTabBody(
                        selectedGenreTab = selectedGenreTab,
                        onGenreTabSelected = { selectedGenreTab = it },
                        mediaCatalog = mediaCatalog,
                        episodesCatalog = episodesCatalog,
                        onMediaSelected = { media -> selectedMediaId = media.id },
                        onChannelSelected = playWithOfflineResolution,
                        onOpenAllLiveTv = { selectedTab = BottomNavTab.LIVE_TV }
                    )
                }

                BottomNavTab.SEARCH -> {
                    SearchTabContent(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        mediaList = mediaCatalog,
                        episodesList = episodesCatalog,
                        onMediaSelected = { media -> selectedMediaId = media.id },
                        onChannelSelected = playWithOfflineResolution
                    )
                }

                BottomNavTab.LIVE_TV -> {
                    LiveTvTabContent(
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it },
                        onChannelSelected = playWithOfflineResolution
                    )
                }

                BottomNavTab.DOWNLOAD -> {
                    DownloadTabContent(
                        downloads = downloads,
                        downloadProgress = downloadProgress,
                        isOfflineMode = isOfflineMode,
                        onPlayDownloaded = { dl ->
                            val resolvedUrl = OfflineDownloadManager.resolvePlayableUrl(
                                streamUrl = dl.streamUrl,
                                localFilePath = dl.localFilePath,
                                context = context
                            )
                            val format = when {
                                resolvedUrl.startsWith("file:", true) ||
                                    resolvedUrl.startsWith("/") -> {
                                    if (resolvedUrl.endsWith(".m3u8", true)) "hls" else "mp4"
                                }
                                dl.streamUrl.substringBefore("?").endsWith(".mp4", true) -> "mp4"
                                dl.streamUrl.contains(".mpd", true) -> "dash"
                                else -> "hls"
                            }
                            val matchedEpisode = episodesCatalog.find { it.id == dl.id }
                            val matchedMedia = mediaCatalog.find { it.id == dl.id }
                            val isSwahiliMovie = matchedMedia?.shouldAutoSkipSwahiliMovieIntro == true
                            onChannelSelected(
                                LiveChannel(
                                    id = "dl_${dl.id}",
                                    name = dl.title.replace("\n", " "),
                                    description = "${dl.genre} • ${dl.duration} • ${dl.fileSizeLabel}",
                                    streamUrl = resolvedUrl,
                                    streamFormat = format,
                                    thumbnailUrl = dl.backdropUrl.ifBlank { dl.posterUrl },
                                    categories = listOf(dl.genre, "Offline"),
                                    isLiveBroadcast = false,
                                    seriesId = matchedEpisode?.seriesId ?: (if (matchedMedia?.isSeries == true) matchedMedia.id else ""),
                                    episodeId = matchedEpisode?.id ?: "",
                                    seasonNumber = matchedEpisode?.seasonNumber ?: 0,
                                    episodeNumber = matchedEpisode?.episodeNumber ?: 0,
                                    isSwahiliNarratedMovie = isSwahiliMovie,
                                    isAdultContent = matchedMedia?.isAdultContent == true
                                )
                            )
                        },
                        onDeleteDownload = { neliViewModel.deleteDownload(it) }
                    )
                }

                BottomNavTab.ACCOUNT -> {
                    AccountTabContent(
                        currentUser = currentUser,
                        authError = authError,
                        googleFallbackMessage = googleFallbackMessage,
                        isAuthLoading = isAuthLoading,
                        firebaseConfig = firebaseConfig,
                        watchlist = watchlist,
                        downloadsCount = downloads.size,
                        onSignInWithGoogle = {
                            neliViewModel.signInWithGoogle(context)
                        },
                        onSignUp = { realName, email, password ->
                            neliViewModel.signUpUser(realName, email, password)
                        },
                        onSignIn = { email, password ->
                            neliViewModel.signInUser(email, password)
                        },
                        onSignOut = {
                            neliViewModel.signOutUser()
                        },
                        onClearAuthError = {
                            neliViewModel.clearAuthError()
                        },
                        onUpdateNetworkPreferences = { mode, allowMobile ->
                            neliViewModel.updateNetworkPreferences(mode, allowMobile)
                        },
                        onPlayWatchlistItem = { wItem ->
                            val existingChannel = ChannelRepository.getChannelById(wItem.id)
                            if (existingChannel != null) {
                                playWithOfflineResolution(existingChannel)
                            } else {
                                val format = when {
                                    wItem.streamUrl.substringBefore("?").endsWith(".mp4", true) -> "mp4"
                                    wItem.streamUrl.contains(".mpd", true) -> "dash"
                                    else -> "hls"
                                }
                                playWithOfflineResolution(
                                    LiveChannel(
                                        id = wItem.id,
                                        name = wItem.title,
                                        description = wItem.genre,
                                        streamUrl = wItem.streamUrl,
                                        streamFormat = format,
                                        thumbnailUrl = wItem.posterUrl,
                                        categories = listOf(wItem.genre),
                                        isLiveBroadcast = wItem.type.equals("live", true)
                                    )
                                )
                            }
                        }
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun HomeTabBody(
    selectedGenreTab: String,
    onGenreTabSelected: (String) -> Unit,
    mediaCatalog: List<MediaContent>,
    episodesCatalog: List<EpisodeItem>,
    onMediaSelected: (MediaContent) -> Unit,
    onChannelSelected: (LiveChannel) -> Unit,
    onOpenAllLiveTv: () -> Unit
) {
    // Only the 7 curated channels for the Homepage:
    // Azam Sports 1, Azam Sports 2, Azam One, Azam Two, Sinema Zetu, KIX, and WWE
    val homepageLiveChannels = remember {
        ChannelRepository.homePageFeaturedChannels
    }

    val featuredHeroMedia = remember(mediaCatalog) {
        val featured = mediaCatalog.filter { it.published && (it.featured || it.isTrending) }
        featured.ifEmpty { mediaCatalog.filter { it.published } }.take(8)
    }

    val genreFilteredMedia = remember(selectedGenreTab, mediaCatalog) {
        MediaContentRepository.filterByGenreTab(selectedGenreTab)
    }

    val genreSections = remember(mediaCatalog) {
        MediaContentRepository.getMediaGroupedByGenre(mediaCatalog)
    }

    val seriesList = remember(mediaCatalog) {
        mediaCatalog.filter { it.isSeries && it.published }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("channels_grid"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Responsive Mobile Hero Banner at the Top
        if (featuredHeroMedia.isNotEmpty()) {
            item {
                ResponsiveCinemaHeroBanner(
                    heroMediaList = featuredHeroMedia,
                    episodesCatalog = episodesCatalog,
                    onPlayMedia = { media ->
                        if (media.isSeries) {
                            val ep = episodesCatalog.firstOrNull { it.seriesId == media.id }
                            if (ep != null) {
                                onChannelSelected(ep.toPlayableChannel(media.title))
                            } else {
                                onChannelSelected(media.toPlayableChannel())
                            }
                        } else {
                            onChannelSelected(media.toPlayableChannel())
                        }
                    },
                    onOpenDetails = onMediaSelected
                )
            }
        }

        // 2. Curated Homepage Live TV Row:
        // Strictly ONLY Azam Sports 1 & 2, Azam One & Two, Sinema Zetu, KIX, and WWE
        if (homepageLiveChannels.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("homepage_curated_live_tv_section")
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
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = null,
                                tint = NeliLiveRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Featured Live TV",
                                color = NeliTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Text(
                            text = "See All Channels →",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenAllLiveTv() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(homepageLiveChannels, key = { "home_live_${it.id}" }) { channel ->
                            HomepageLiveChannelCard(
                                channel = channel,
                                onClick = { onChannelSelected(channel) }
                            )
                        }
                    }
                }
            }
        }

        // 3. Genre Filter Bar + Filtered Movies & Series Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
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
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = NeliMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Explore by Genre",
                            color = NeliTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "${mediaCatalog.size} Titles",
                        color = NeliGenreCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MediaContentRepository.homeGenreTabs) { tab ->
                        val isSelected = tab.equals(selectedGenreTab, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeliMagenta else NeliSurface)
                                .border(
                                    1.dp,
                                    if (isSelected) NeliMagenta else Color(0x44A855F7),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { onGenreTabSelected(tab) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tab,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                            )
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(genreFilteredMedia, key = { "genre_tab_${it.id}" }) { media ->
                        MediaPosterCard(
                            media = media,
                            onClick = { onMediaSelected(media) }
                        )
                    }
                }
            }
        }

        // 4. Featured Series & Episodes Row
        if (seriesList.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Series",
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${seriesList.size} Series • ${episodesCatalog.size} Episodes",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(seriesList, key = { "ser_row_${it.id}" }) { series ->
                            MediaPosterCard(
                                media = series,
                                onClick = { onMediaSelected(series) }
                            )
                        }
                    }
                }
            }
        }

        // 5. All Dynamic Genre Sections from the Real Movie & Series Catalog
        items(genreSections, key = { "section_${it.first}" }) { (genreTitle, genreItems) ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
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
                        if (genreTitle.contains("Swahili", ignoreCase = true)) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = genreTitle,
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "${genreItems.size} Titles",
                        color = NeliTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(genreItems, key = { "${genreTitle}_${it.id}" }) { item ->
                        MediaPosterCard(
                            media = item,
                            onClick = { onMediaSelected(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponsiveCinemaHeroBanner(
    heroMediaList: List<MediaContent>,
    episodesCatalog: List<EpisodeItem>,
    onPlayMedia: (MediaContent) -> Unit,
    onOpenDetails: (MediaContent) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { heroMediaList.size })

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp)
            .testTag("azam_priority_hero_slider")
    ) {
        // Responsive height based on mobile viewport width so it fits compact & expanded screens naturally
        val bannerHeight = (maxWidth * 0.56f).coerceIn(210.dp, 260.dp)

        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
            ) { page ->
                val media = heroMediaList[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                        .background(NeliSurfaceVariant)
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(NeliMagenta.copy(alpha = 0.8f), NeliGenreCyan.copy(alpha = 0.7f))
                            ),
                            shape = RoundedCornerShape(22.dp)
                        )
                        .clickable { onOpenDetails(media) }
                ) {
                    // Full-bleed Backdrop Artwork
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
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF3B1278), Color(0xFF1E073E))
                                        )
                                    )
                            )
                        }
                    )

                    // Multi-stop cinema gradient overlay for legibility
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x3314052B),
                                        Color(0xAA14052B),
                                        Color(0xF514052B)
                                    )
                                )
                            )
                    )

                    // Content Overlay inside Banner
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top Badges Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeliMagenta)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (media.isSeries) "FEATURED SERIES" else "FEATURED MOVIE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            if (media.narrated && media.narrationLanguage.isNotBlank()) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xDD10B981))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = media.narrationLanguage.uppercase(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xAA2B1055))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = media.rating,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Bottom Title, Metadata & Action Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = media.title,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${media.genre} • ${media.duration} • ${media.releaseYear}",
                                color = NeliGenreCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeliMagenta)
                                        .clickable { onPlayMedia(media) }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Watch Now",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xBB2B1055))
                                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(12.dp))
                                        .clickable { onOpenDetails(media) }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Details",
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

            // Pager Indicator Dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotCount = minOf(heroMediaList.size, 8)
                for (i in 0 until dotCount) {
                    val isSelected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isSelected) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) NeliMagenta else NeliSurfaceVariant)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomepageLiveChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NeliSurface)
            .border(1.dp, Color(0x44A855F7), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}")
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF14052B))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(channel.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = NeliMagenta,
                        modifier = Modifier.size(34.dp)
                    )
                }
            )

            LiveIndicatorBadge(
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        Text(
            text = channel.name,
            color = NeliTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(NeliCardPurple)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = NeliMagenta,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Watch Live",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
