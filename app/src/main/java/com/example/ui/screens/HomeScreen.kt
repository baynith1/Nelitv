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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ads.NeliAdMobManager
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.OfflineDownloadManager
import com.example.model.LiveChannel
import com.example.ui.NeliViewModel
import com.example.ui.components.BottomNavTab
import com.example.ui.components.ChannelCard
import com.example.ui.components.LiveIndicatorBadge
import com.example.ui.components.NeliAdaptiveBannerAd
import com.example.ui.components.NeliBottomBar
import com.example.ui.components.NeliMutedInlineVideoAdCard
import com.example.ui.components.TopNavBar
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onChannelSelected: (LiveChannel) -> Unit,
    modifier: Modifier = Modifier,
    neliViewModel: NeliViewModel = viewModel()
) {
    val context = LocalContext.current
    val isOfflineMode by neliViewModel.isOfflineMode.collectAsState()
    val selectedTab by neliViewModel.selectedTab.collectAsState()
    val selectedMediaId by neliViewModel.selectedMediaId.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLiveCategory by rememberSaveable { mutableStateOf("All") }
    var selectedDiscoveryFilter by rememberSaveable { mutableStateOf("All") }
    var selectedSearchCategory by rememberSaveable { mutableStateOf("All") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    val mediaCatalog by neliViewModel.mediaCatalog.collectAsState()
    val episodesCatalog by neliViewModel.episodesCatalog.collectAsState()
    val liveChannels by neliViewModel.liveChannels.collectAsState()
    val isRefreshingLiveTv by neliViewModel.isRefreshingLiveTv.collectAsState()
    val downloads by neliViewModel.downloads.collectAsState()
    val downloadedIds by neliViewModel.downloadedIds.collectAsState()
    val downloadingIds by neliViewModel.downloadingIds.collectAsState()
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

    // Automatic Google Sign-In on first launch using the Activity context
    LaunchedEffect(Unit) {
        neliViewModel.refreshConnectivityState()
        neliViewModel.attemptAutoGoogleSignInIfNeeded(context)
    }

    val activeDetailMedia = remember(selectedMediaId, mediaCatalog) {
        selectedMediaId?.let { id -> mediaCatalog.find { it.id == id } }
    }

    // Handle back button:
    // - From Movie/Series Details page -> always go to Discovery page
    // - From open search -> close search
    // - From non-Home tab -> return to Home (or Download if offline)
    BackHandler(enabled = activeDetailMedia != null || isSearchOpen || selectedTab != BottomNavTab.HOME) {
        when {
            activeDetailMedia != null -> neliViewModel.navigateBackFromMediaDetails()
            isSearchOpen -> {
                isSearchOpen = false
                searchQuery = ""
            }
            selectedTab != BottomNavTab.HOME -> {
                neliViewModel.selectTab(if (isOfflineMode) BottomNavTab.DOWNLOAD else BottomNavTab.HOME)
            }
        }
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
        val recommendedMedia = remember(activeDetailMedia.id, activeDetailMedia.primaryGenre, mediaCatalog) {
            MediaContentRepository.getRelatedMedia(activeDetailMedia.id, activeDetailMedia.primaryGenre)
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
            onBack = { neliViewModel.navigateBackFromMediaDetails() },
            onPlayChannel = playWithOfflineResolution,
            onToggleWatchlist = { neliViewModel.toggleWatchlist(it) },
            onDownloadMedia = { media ->
                NeliAdMobManager.runDownloadWithInterstitialIfEligible(context) {
                    neliViewModel.addDownload(media)
                }
            },
            onDownloadEpisode = { ep ->
                NeliAdMobManager.runDownloadWithInterstitialIfEligible(context) {
                    neliViewModel.addEpisodeDownload(
                        episode = ep,
                        seriesTitle = activeDetailMedia.title,
                        seriesPoster = activeDetailMedia.posterUrl
                    )
                }
            },
            onSelectRecommendedMedia = { recommended ->
                neliViewModel.openMediaDetails(recommended.id)
            },
            onOpenDownloadsTab = {
                neliViewModel.dismissDownloadBanner()
                neliViewModel.selectTab(BottomNavTab.DOWNLOAD)
            },
            onDismissDownloadBanner = {
                neliViewModel.dismissDownloadBanner()
            },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    // Deterministic non-overlapping Column layout:
    // TopNavBar sits cleanly below the mobile status bar and NEVER overlaps the active tab content
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .testTag("home_screen")
    ) {
        TopNavBar(
            searchQuery = searchQuery,
            onSearchQueryChange = { query ->
                searchQuery = query
                if (query.isNotBlank() && selectedTab != BottomNavTab.SEARCH) {
                    neliViewModel.selectTab(BottomNavTab.SEARCH)
                }
            },
            isSearchOpen = isSearchOpen,
            onToggleSearch = {
                isSearchOpen = !isSearchOpen
                if (isSearchOpen && selectedTab != BottomNavTab.SEARCH) {
                    neliViewModel.selectTab(BottomNavTab.SEARCH)
                } else if (!isSearchOpen) {
                    searchQuery = ""
                }
            },
            activeTabLabel = selectedTab.label,
            isOfflineMode = isOfflineMode,
            onOfflineClick = {
                neliViewModel.selectTab(BottomNavTab.DOWNLOAD)
            },
            onBrandClick = {
                neliViewModel.selectTab(BottomNavTab.HOME)
            }
        )

        val activeBgDownloadEntry = downloadProgress.entries.firstOrNull()
        val activeDownloadCount = downloadProgress.size
        if (activeBgDownloadEntry != null && selectedTab != BottomNavTab.DOWNLOAD) {
            val activeId = activeBgDownloadEntry.key
            val activePct = if (activeDownloadCount > 1) {
                downloadProgress.values.sum() / activeDownloadCount
            } else {
                activeBgDownloadEntry.value
            }
            val activeTitle = if (activeDownloadCount > 1) {
                val names = downloadProgress.keys.mapNotNull { activeDownloadTitles[it] }.take(2).joinToString(", ")
                "$activeDownloadCount Downloads Active ($names)"
            } else {
                activeDownloadTitles[activeId] ?: "Movie / Episode"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E0B3B))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clickable { neliViewModel.selectTab(BottomNavTab.DOWNLOAD) }
                    .testTag("global_background_download_bar")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloading: $activeTitle",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$activePct% • View",
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
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
                    LiveTvHomeTab(
                        liveChannels = liveChannels,
                        selectedCategory = selectedLiveCategory,
                        isOfflineMode = isOfflineMode,
                        isRefreshing = isRefreshingLiveTv,
                        onRefresh = { neliViewModel.refreshLiveTvFeed() },
                        onOpenDownloads = { neliViewModel.selectTab(BottomNavTab.DOWNLOAD) },
                        onCategorySelected = { selectedLiveCategory = it },
                        onChannelSelected = playWithOfflineResolution
                    )
                }

                BottomNavTab.DISCOVERY -> {
                    DiscoveryTabContent(
                        selectedFilter = selectedDiscoveryFilter,
                        onFilterSelected = { selectedDiscoveryFilter = it },
                        mediaCatalog = mediaCatalog,
                        episodesCatalog = episodesCatalog,
                        onMediaSelected = { media -> neliViewModel.openMediaDetails(media.id) },
                        onPlayMedia = { media ->
                            if (media.isSeries) {
                                val ep = episodesCatalog.firstOrNull { it.seriesId == media.id }
                                if (ep != null) {
                                    playWithOfflineResolution(ep.toPlayableChannel(media.title))
                                } else {
                                    playWithOfflineResolution(media.toPlayableChannel())
                                }
                            } else {
                                playWithOfflineResolution(media.toPlayableChannel())
                            }
                        }
                    )
                }

                BottomNavTab.SEARCH -> {
                    SearchTabContent(
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        selectedCategory = selectedSearchCategory,
                        onCategorySelected = { selectedSearchCategory = it },
                        mediaList = mediaCatalog,
                        episodesList = episodesCatalog,
                        liveChannels = liveChannels,
                        onMediaSelected = { media -> neliViewModel.openMediaDetails(media.id) },
                        onChannelSelected = playWithOfflineResolution
                    )
                }

                BottomNavTab.DOWNLOAD -> {
                    DownloadTabContent(
                        downloads = downloads,
                        downloadProgress = downloadProgress,
                        downloadedIds = downloadedIds,
                        downloadingIds = downloadingIds,
                        mediaCatalog = mediaCatalog,
                        isOfflineMode = isOfflineMode,
                        downloadBannerMessage = downloadBannerMessage,
                        onDismissBanner = { neliViewModel.dismissDownloadBanner() },
                        onStartQuickDownload = { media ->
                            NeliAdMobManager.runDownloadWithInterstitialIfEligible(context) {
                                neliViewModel.addDownload(media)
                            }
                        },
                        onRetryDownload = { dl ->
                            neliViewModel.retryDownload(dl)
                        },
                        onCancelDownload = { id ->
                            neliViewModel.cancelDownload(id)
                        },
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
                                    isAdultContent = matchedMedia?.isAdultContent == true || dl.type.equals("adult", true)
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

        NeliBottomBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                neliViewModel.selectTab(tab)
            },
            activeDownloadCount = downloadingIds.size
        )
    }
}

/**
 * HOME TAB: Clean YouTube-style Live TV feed with Pull-to-Refresh displaying all channels prioritized by:
 * 1st: Azam TV Channels
 * 2nd: Tanzania Live TV Channels
 * 3rd: International Live TV Channels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTvHomeTab(
    liveChannels: List<LiveChannel>,
    selectedCategory: String,
    isOfflineMode: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenDownloads: () -> Unit,
    onCategorySelected: (String) -> Unit,
    onChannelSelected: (LiveChannel) -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()

    val allPrioritizedChannels = remember(liveChannels) {
        ChannelRepository.getPrioritizedAllChannels(liveChannels)
    }

    val azamChannels = remember(allPrioritizedChannels) {
        allPrioritizedChannels.filter { it.isAzamPriority }
    }

    val homepageCategoryShelves = remember(allPrioritizedChannels) {
        ChannelRepository.getChannelsGroupedByHomepageCategories(allPrioritizedChannels)
    }

    val featuredHeroChannels = remember(allPrioritizedChannels, azamChannels) {
        (azamChannels + ChannelRepository.homePageFeaturedChannels)
            .distinctBy { it.id }
            .ifEmpty { allPrioritizedChannels.take(12) }
    }

    val liveCategories = remember(allPrioritizedChannels, homepageCategoryShelves) {
        val cats = LinkedHashSet<String>()
        cats.add("All")
        homepageCategoryShelves.forEach { (catName, _) ->
            if (catName.isNotBlank()) cats.add(catName)
        }
        ChannelRepository.categories.forEach { if (it.isNotBlank()) cats.add(it) }
        allPrioritizedChannels.forEach { ch ->
            if (ch.category.isNotBlank()) cats.add(ch.category)
        }
        cats.toList()
    }

    val filteredChannels = remember(selectedCategory, allPrioritizedChannels) {
        if (selectedCategory.equals("All", ignoreCase = true)) {
            allPrioritizedChannels
        } else {
            ChannelRepository.getChannelsByCategory(selectedCategory)
        }
    }

    // Group All Channels into blocks of 6 channels so a Muted Video Ad is placed after every 6 channels in vertical view
    val sixChannelBlocks = remember(filteredChannels) {
        ChannelRepository.getAllChannelsChunkedEverySixForAds(filteredChannels)
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                containerColor = NeliSurface,
                color = Color(0xFFFF0033),
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .testTag("live_tv_pull_to_refresh_box")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("channels_grid"),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
        // 0. Subtle, non-intrusive offline connectivity banner informing why live streams might not load
        if (isOfflineMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1F1B14))
                        .border(1.dp, Color(0x55F59E0B), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("homepage_offline_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("homepage_offline_icon")
                    )
                    Text(
                        text = "You're offline • Live streams require an internet connection to load.",
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33F59E0B))
                            .clickable { onOpenDownloads() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("homepage_offline_downloads_button")
                    ) {
                        Text(
                            text = "Downloads",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 1. YouTube-style Top Filter Chips Row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_tv_category_chips")
            ) {
                items(liveCategories) { category ->
                    val isSelected = category.equals(selectedCategory, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) NeliTextPrimary else NeliSurfaceVariant
                            )
                            .clickable { onCategorySelected(category) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("live_cat_chip_$category")
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) NeliBackground else NeliTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Featured Live Spotlight Banner + Adaptive Banner down the slider
        if (featuredHeroChannels.isNotEmpty() && selectedCategory.equals("All", ignoreCase = true)) {
            item {
                LiveTvHeroBanner(
                    heroChannels = featuredHeroChannels,
                    onPlayChannel = onChannelSelected
                )
            }
            item(key = "home_ad_below_slider") {
                NeliAdaptiveBannerAd(placementKey = "home_below_slider")
            }
        }

        // 3. Ordered Category Shelves before "All Channels":
        // Starts with Azam TV (all Azam TV channels), then Sports, Entertainment, Kids, News, Movies,
        // Music, Documentary, Africa, Tanzania, and any other categories until all categories finish.
        if (selectedCategory.equals("All", ignoreCase = true) && homepageCategoryShelves.isNotEmpty()) {
            itemsIndexed(
                items = homepageCategoryShelves,
                key = { _, pair -> "home_cat_shelf_${pair.first}" }
            ) { catIndex, (categoryTitle, categoryChannels) ->
                val sectionTag = when {
                    categoryTitle.equals("Azam TV", ignoreCase = true) -> "homepage_curated_live_tv_section"
                    categoryTitle.equals("Tanzania", ignoreCase = true) -> "homepage_tanzania_live_tv_section"
                    else -> "homepage_category_section_${categoryTitle.lowercase().replace(" ", "_")}"
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag(sectionTag)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = categoryTitle,
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${categoryChannels.size} channels",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = categoryChannels,
                            key = { ch -> "home_cat_${categoryTitle}_${ch.id}" }
                        ) { channel ->
                            HomepageLiveChannelCard(
                                channel = channel,
                                onClick = { onChannelSelected(channel) }
                            )
                        }
                    }

                    if (catIndex == 0) {
                        NeliAdaptiveBannerAd(placementKey = "home_after_category_section")
                    } else if ((catIndex + 1) % 3 == 0) {
                        NeliAdaptiveBannerAd(
                            placementKey = "home_after_category_${categoryTitle.lowercase().replace(" ", "_")}"
                        )
                    }
                }
            }
        }

        // 4. All Live Channels Vertical Feed (placed after all category shelves finish, with Muted Video Ads after every 6 channels)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategory.equals("All", true)) {
                        "All Channels"
                    } else {
                        selectedCategory
                    },
                    color = NeliTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isRefreshing) "Refreshing..." else "${filteredChannels.size} live",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("refresh_live_tv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Live TV",
                            tint = NeliTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = sixChannelBlocks,
            key = { blockIdx, block -> "all_channels_block_${blockIdx}_${block.firstOrNull()?.id.orEmpty()}" }
        ) { blockIndex, sixChannels ->
            val rowsOfTwo = remember(sixChannels) { sixChannels.chunked(2) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("all_channels_vertical_block_$blockIndex")
            ) {
                for (rowItems in rowsOfTwo) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (channel in rowItems) {
                            Box(modifier = Modifier.weight(1f)) {
                                ChannelCard(
                                    channel = channel,
                                    onClick = { onChannelSelected(channel) }
                                )
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Embed Muted Video Ad after every 6 channels in All Channels vertical view
                if (sixChannels.size == 6 || blockIndex == sixChannelBlocks.lastIndex) {
                    NeliMutedInlineVideoAdCard(
                        placementKey = "all_channels_after_${(blockIndex + 1) * 6}"
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun LiveTvHeroBanner(
    heroChannels: List<LiveChannel>,
    onPlayChannel: (LiveChannel) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { heroChannels.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(heroChannels.size) {
        if (heroChannels.size > 1) {
            while (true) {
                delay(5500)
                val next = (pagerState.currentPage + 1) % heroChannels.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .testTag("azam_priority_hero_slider")
    ) {
        val isTablet = maxWidth >= 600.dp
        val bannerHeight = if (isTablet) 240.dp else 204.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeight)
            ) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 12.dp,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val channel = heroChannels[page]
                    val context = LocalContext.current
                    val primaryHeroLogo = remember(channel.id, channel.thumbnailUrl) {
                        ChannelRepository.resolveGuaranteedChannelLogoUrl(channel)
                    }
                    val fallbackHeroLogo = remember(channel.id, channel.name) {
                        ChannelRepository.resolveFallbackChannelLogoUrl(channel)
                    }
                    var activeHeroLogo by remember(channel.id, primaryHeroLogo) {
                        mutableStateOf(primaryHeroLogo)
                    }
                    var isHeroLogoLoaded by remember(channel.id, activeHeroLogo) {
                        mutableStateOf(false)
                    }
                    val heroLogoRequest = remember(channel.id, activeHeroLogo) {
                        ImageRequest.Builder(context)
                            .data(activeHeroLogo)
                            .size(280, 280)
                            .crossfade(false)
                            .build()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF14091E),
                                        Color(0xFF0E172C),
                                        Color(0xFF090A0F)
                                    )
                                )
                            )
                            .border(
                                width = 1.dp,
                                color = Color(0xFF2D364F),
                                shape = RoundedCornerShape(22.dp)
                            )
                            .clickable { onPlayChannel(channel) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isTablet) 132.dp else 102.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0xFF141B2D))
                                    .border(1.dp, Color(0xFF28324B), RoundedCornerShape(18.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isHeroLogoLoaded) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = NeliLiveRed.copy(alpha = 0.7f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = heroLogoRequest,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Fit,
                                        onSuccess = { isHeroLogoLoaded = true },
                                        onError = {
                                            if (activeHeroLogo != fallbackHeroLogo && fallbackHeroLogo.isNotBlank()) {
                                                activeHeroLogo = fallbackHeroLogo
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    LiveIndicatorBadge()
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(NeliSurfaceVariant)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = channel.category.uppercase(),
                                            color = NeliGenreCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                Text(
                                    text = channel.name,
                                    color = Color.White,
                                    fontSize = if (isTablet) 22.sp else 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = channel.description.ifBlank { "Watch ${channel.name} Live 24/7 in HD on Nelitv." },
                                    color = NeliTextSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeliLiveRed)
                                        .clickable { onPlayChannel(channel) }
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
                                        text = "Watch Live Now",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (heroChannels.size > 1) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val prev = if (pagerState.currentPage - 1 < 0) {
                                    heroChannels.size - 1
                                } else {
                                    pagerState.currentPage - 1
                                }
                                pagerState.animateScrollToPage(prev)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 20.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0D111C))
                            .border(1.dp, Color(0x44FFFFFF), CircleShape)
                            .testTag("hero_slider_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Slide",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val next = (pagerState.currentPage + 1) % heroChannels.size
                                pagerState.animateScrollToPage(next)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 20.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC0D111C))
                            .border(1.dp, Color(0x44FFFFFF), CircleShape)
                            .testTag("hero_slider_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Slide",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotCount = minOf(heroChannels.size, 8)
                for (i in 0 until dotCount) {
                    val isSelected = pagerState.currentPage == i
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isSelected) 22.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) NeliLiveRed else NeliSurfaceVariant)
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(i)
                                }
                            }
                            .testTag("hero_slider_dot_$i")
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
            .size(280, 160)
            .crossfade(false)
            .build()
    }

    Column(
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NeliSurface)
            .border(0.5.dp, Color(0xFF252D40), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("channel_card_${channel.id}")
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF161D2F))
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isLogoLoaded) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = NeliMagenta.copy(alpha = 0.55f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
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

            LiveIndicatorBadge(
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        Text(
            text = channel.name,
            color = NeliTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "${channel.category} • Live",
            color = NeliTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
