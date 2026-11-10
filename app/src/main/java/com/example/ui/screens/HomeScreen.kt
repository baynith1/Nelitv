package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import com.example.ads.NeliAdManager
import com.example.ads.StartIoBannerAd
import com.example.ads.StartIoNativeCardAd
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.example.R
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.AdminBannerPlacement
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.OfflineDownloadManager
import com.example.model.LiveChannel
import com.example.player.NeliCastManager
import androidx.compose.foundation.layout.fillMaxHeight
import com.example.ui.NeliViewModel
import com.example.ui.components.BottomNavTab
import com.example.ui.components.ChannelCard
import com.example.ui.components.LiveIndicatorBadge
import com.example.ui.components.NeliBottomBar
import com.example.ui.components.NeliHomepageAutoUpdatePopupDialog
import com.example.ui.components.TopNavBar
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliMainBackgroundBrush
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager
import com.example.ui.theme.rememberNeliScreenProfile
import com.example.ui.tv.NeliTvDetector
import com.example.ui.tv.NeliTvSideNavigationRail
import com.example.ui.tv.NeliTvTopBar
import com.example.ui.tv.tvGeminiFocusable
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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLiveCategory by rememberSaveable { mutableStateOf("All") }
    var selectedDiscoveryFilter by rememberSaveable { mutableStateOf("All") }
    var selectedSearchCategory by rememberSaveable { mutableStateOf("All") }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }

    val liveChannels by neliViewModel.liveChannels.collectAsState()
    val mediaCatalog by neliViewModel.mediaCatalog.collectAsState()
    val episodesCatalog by neliViewModel.episodesCatalog.collectAsState()
    val catalogRotationSeed by neliViewModel.catalogRotationSeed.collectAsState()
    val isRefreshingLiveTv by neliViewModel.isRefreshingLiveTv.collectAsState()
    val isRefreshingDiscovery by neliViewModel.isRefreshingDiscovery.collectAsState()
    val selectedMediaId by neliViewModel.selectedMediaId.collectAsState()
    val downloads by neliViewModel.downloads.collectAsState()
    val downloadProgress by neliViewModel.downloadProgress.collectAsState()
    val downloadedIds by neliViewModel.downloadedIds.collectAsState()
    val downloadingIds by neliViewModel.downloadingIds.collectAsState()
    val downloadBannerMessage by neliViewModel.downloadBannerMessage.collectAsState()
    val watchlist by neliViewModel.watchlistItems.collectAsState()
    val watchlistIds by neliViewModel.watchlistIds.collectAsState()
    val firebaseConfig by neliViewModel.firebaseConfig.collectAsState()
    val currentUser by neliViewModel.currentUser.collectAsState()
    val authError by neliViewModel.authError.collectAsState()
    val googleFallbackMessage by neliViewModel.googleFallbackMessage.collectAsState()
    val isAuthLoading by neliViewModel.isAuthLoading.collectAsState()
    val showGoogleSignInSheet by neliViewModel.showGoogleSignInSheet.collectAsState()
    val savedGoogleAccounts by neliViewModel.savedGoogleAccounts.collectAsState()

    val connectedCastDevice by NeliCastManager.connectedDevice.collectAsState()
    val isCastActive = connectedCastDevice != null
    val activeAdminSms by NeliAdminManager.activeAdminSms.collectAsState()
    val adminSmsMessage = activeAdminSms?.message.orEmpty()
    val adminBannerPlacement by NeliAdminManager.adminBannerPlacement.collectAsState()
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()

    var showCastDialog by rememberSaveable { mutableStateOf(false) }
    var lockedChannelToPrompt by remember { mutableStateOf<LiveChannel?>(null) }
    var pendingUnlockedChannelAfterPayment by remember { mutableStateOf<LiveChannel?>(null) }

    // Continuously evaluate subscription status & expiry in real time (every 1s) so channels stay unlocked while active,
    // lock immediately when subscription expires, and unlock immediately when payment succeeds.
    LaunchedEffect(subState.isVerified, subState.expiresAtMs, lockAllForFree, lockedChannelIds) {
        NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
        if (subState.isVerified && subState.expiresAtMs > 0L) {
            lockedChannelToPrompt = null
            while (true) {
                val remaining = subState.expiresAtMs - System.currentTimeMillis()
                if (remaining <= 0L) {
                    NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
                    break
                }
                delay(minOf(remaining + 100L, 1_000L).coerceAtLeast(250L))
                NeliSubscriptionManager.expireSubscriptionIfNeeded(context)
            }
        }
    }

    val handleChannelSelection: (LiveChannel) -> Unit = { ch ->
        lockedChannelToPrompt = null
        pendingUnlockedChannelAfterPayment = null
        if (isCastActive) {
            NeliCastManager.startCastingMedia(
                channel = ch,
                currentUser = currentUser,
                context = context
            )
            // Stream and audio play on the Smart TV; phone acts as remote control and remains free for user
        } else {
            val activity = context as? Activity
            if (activity != null) {
                NeliAdManager.showChannelOpeningAd(activity, ch.name) {
                    onChannelSelected(ch)
                }
            } else {
                onChannelSelected(ch)
            }
        }
    }

    val selectedMedia = remember(selectedMediaId, mediaCatalog) {
        val id = selectedMediaId ?: return@remember null
        mediaCatalog.find { it.id == id } ?: MediaContentRepository.getMediaById(id)
    }

    LaunchedEffect(Unit) {
        neliViewModel.refreshConnectivityState()
        NeliSubscriptionManager.initialize(context)
        NeliAdminManager.initialize(context)
    }

    if (showCastDialog) {
        NeliCastModalSheet(
            availableChannels = liveChannels.ifEmpty { ChannelRepository.getPrioritizedAllChannels() },
            currentUser = currentUser,
            onDismiss = { showCastDialog = false },
            onGoToPremium = {
                showCastDialog = false
                isSearchOpen = false
                neliViewModel.selectTab(BottomNavTab.SEARCH)
            },
            onSelectChannelToWatchAndCast = { selectedCh ->
                onChannelSelected(selectedCh)
            }
        )
    }

    // Handle back button:
    // - From MediaDetailScreen -> return to Discovery
    // - From open search or non-Home tab -> return to Homepage
    BackHandler(enabled = selectedMedia != null || isSearchOpen || selectedTab != BottomNavTab.HOME) {
        if (selectedMedia != null) {
            neliViewModel.navigateBackFromMediaDetails()
        } else if (isSearchOpen) {
            isSearchOpen = false
            searchQuery = ""
        } else {
            neliViewModel.selectTab(BottomNavTab.HOME)
        }
    }

    if (selectedMedia != null) {
        val seriesEpisodes = remember(selectedMedia.id, episodesCatalog) {
            if (selectedMedia.isSeries) {
                MediaContentRepository.getEpisodesForSeries(selectedMedia.id)
            } else {
                emptyList()
            }
        }
        val recommended = remember(selectedMedia.id, selectedMedia.genre, mediaCatalog) {
            MediaContentRepository.getRelatedMedia(selectedMedia.id, selectedMedia.genre)
        }
        Box(modifier = modifier.fillMaxSize()) {
            MediaDetailScreen(
                media = selectedMedia,
                episodes = seriesEpisodes,
                recommendedMedia = recommended,
                isInWatchlist = watchlistIds.contains(selectedMedia.id),
                isDownloaded = downloadedIds.contains(selectedMedia.id),
                downloadedIds = downloadedIds,
                downloadProgress = downloadProgress,
                downloadBannerMessage = downloadBannerMessage,
                onBack = { neliViewModel.navigateBackFromMediaDetails() },
                onPlayChannel = { playable ->
                    val resolvedChannel = OfflineDownloadManager.resolveOfflineAwareChannel(
                        context = context,
                        channel = playable,
                        downloads = downloads
                    )
                    handleChannelSelection(resolvedChannel)
                },
                onToggleWatchlist = { neliViewModel.toggleWatchlist(it) },
                onDownloadMedia = { neliViewModel.addDownload(it) },
                onDownloadEpisode = { ep ->
                    neliViewModel.addEpisodeDownload(
                        episode = ep,
                        seriesTitle = selectedMedia.title,
                        seriesPoster = selectedMedia.posterUrl
                    )
                },
                onDownloadMediaWithQuality = { media, quality ->
                    neliViewModel.addDownload(media, quality)
                },
                onDownloadEpisodeWithQuality = { ep, quality ->
                    neliViewModel.addEpisodeDownload(
                        episode = ep,
                        seriesTitle = selectedMedia.title,
                        seriesPoster = selectedMedia.posterUrl,
                        quality = quality
                    )
                },
                onSelectRecommendedMedia = { rec ->
                    neliViewModel.openMediaDetails(rec.id)
                },
                onOpenDownloadsTab = {
                    neliViewModel.selectTab(BottomNavTab.DOWNLOAD)
                },
                onDismissDownloadBanner = {
                    neliViewModel.dismissDownloadBanner()
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val isTvDevice = remember(context, NeliThemeManager.forceTvMode) {
        NeliTvDetector.isTelevision(context)
    }

    val mainTabsContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            if (isSearchOpen) {
                SearchTabContent(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedCategory = selectedSearchCategory,
                    onCategorySelected = { selectedSearchCategory = it },
                    mediaList = mediaCatalog,
                    episodesList = episodesCatalog,
                    liveChannels = liveChannels,
                    onMediaSelected = { media ->
                        isSearchOpen = false
                        neliViewModel.openMediaDetails(media.id)
                    },
                    onChannelSelected = { ch ->
                        handleChannelSelection(ch)
                    }
                )
            } else {
                when (selectedTab) {
                    BottomNavTab.DISCOVERY -> {
                        DiscoveryTabContent(
                            selectedFilter = selectedDiscoveryFilter,
                            onFilterSelected = { selectedDiscoveryFilter = it },
                            mediaCatalog = mediaCatalog,
                            episodesCatalog = episodesCatalog,
                            onMediaSelected = { media ->
                                neliViewModel.openMediaDetails(media.id)
                            },
                            onPlayMedia = { media ->
                                val playable = OfflineDownloadManager.resolveOfflineAwareChannel(
                                    context = context,
                                    channel = media.toPlayableChannel(),
                                    downloads = downloads
                                )
                                onChannelSelected(playable)
                            },
                            catalogRotationSeed = catalogRotationSeed,
                            isRefreshing = isRefreshingDiscovery,
                            onRefreshDiscovery = { neliViewModel.refreshDiscoveryCatalog(forceNetworkSync = true) },
                            onRotateMovies = { neliViewModel.rotateDiscoveryMovies() }
                        )
                    }

                    BottomNavTab.SEARCH -> {
                        SearchScreen(
                            channels = liveChannels,
                            mediaCatalog = mediaCatalog,
                            onChannelSelected = { ch -> handleChannelSelection(ch) },
                            onMediaSelected = { media ->
                                neliViewModel.openMediaDetails(media.id)
                            }
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
                            onStartQuickDownload = { media -> neliViewModel.addDownload(media) },
                            onPauseDownload = { id -> neliViewModel.pauseDownload(id) },
                            onRetryDownload = { item -> neliViewModel.retryDownload(item) },
                            onCancelDownload = { id -> neliViewModel.cancelDownload(id) },
                            onPlayDownloaded = { entity ->
                                val resolvedPath = OfflineDownloadManager.resolvePlayableUriForDownloadedEntity(
                                    context = context,
                                    entity = entity
                                )
                                onChannelSelected(
                                    LiveChannel(
                                        id = "dl_${entity.id}",
                                        name = entity.title,
                                        description = "${entity.genre} • ${entity.duration} • Offline In-App Playback",
                                        streamUrl = resolvedPath,
                                        streamFormat = if (resolvedPath.endsWith(".m3u8", true)) "hls" else "mp4",
                                        thumbnailUrl = entity.posterUrl,
                                        categories = listOf(entity.genre.lowercase()),
                                        isLiveBroadcast = false
                                    )
                                )
                            },
                            onPlayQuickMediaOffline = { media ->
                                val playable = OfflineDownloadManager.resolveOfflineAwareChannel(
                                    context = context,
                                    channel = media.toPlayableChannel(),
                                    downloads = downloads
                                )
                                onChannelSelected(playable)
                            },
                            onDeleteDownload = { id -> neliViewModel.deleteDownload(id) }
                        )
                    }

                    BottomNavTab.ACCOUNT -> {
                        AccountTabContent(
                            currentUser = currentUser,
                            authError = authError,
                            googleFallbackMessage = googleFallbackMessage,
                            isAuthLoading = isAuthLoading,
                            showGoogleSignInSheet = showGoogleSignInSheet,
                            savedGoogleAccounts = savedGoogleAccounts,
                            firebaseConfig = firebaseConfig,
                            watchlist = watchlist,
                            downloadsCount = downloads.count { it.downloadStatus == "COMPLETED" },
                            onSignInWithGoogle = {
                                neliViewModel.signInWithGoogle(context)
                            },
                            onChooseGoogleAccount = {
                                neliViewModel.chooseGoogleAccount(context)
                            },
                            onCompleteGoogleSignIn = { googleEmail, googleName ->
                                neliViewModel.completeGoogleSignIn(googleEmail, googleName)
                            },
                            onDismissGoogleSignInSheet = {
                                neliViewModel.dismissGoogleSignInSheet()
                            },
                            onSignUp = { realName, email, password ->
                                neliViewModel.signUpUser(realName, email, password)
                            },
                            onSignIn = { email, password ->
                                neliViewModel.signInUser(email, password)
                            },
                            onSignInWithPhone = { phone, name, password ->
                                neliViewModel.signInWithPhone(phone, name, password)
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
                                    handleChannelSelection(existingChannel)
                                } else {
                                    val existingMedia = MediaContentRepository.getMediaById(wItem.id)
                                    if (existingMedia != null) {
                                        neliViewModel.openMediaDetails(existingMedia.id)
                                    }
                                }
                            }
                        )
                    }

                    BottomNavTab.HOME -> {
                        LiveTvHomeTab(
                            liveChannels = liveChannels,
                            selectedCategory = selectedLiveCategory,
                            isOfflineMode = isOfflineMode,
                            isRefreshing = isRefreshingLiveTv,
                            onRefresh = { neliViewModel.refreshLiveTvFeed() },
                            onOpenDownloads = { neliViewModel.selectTab(BottomNavTab.DOWNLOAD) },
                            onCategorySelected = { selectedLiveCategory = it },
                            onChannelSelected = { ch -> handleChannelSelection(ch) },
                            adminSmsMessage = adminSmsMessage,
                            adminBannerPlacement = adminBannerPlacement
                        )
                    }
                }
            }
        }
    }

    if (isTvDevice) {
        // Smart TV Layout: Side Navigation Rail (Home, Discovery, Plans, Downloads, Account) + TV TopBar + Content (NO cast button, NO bottom menu)
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(NeliBackground)
                .testTag("home_screen_tv")
        ) {
            NeliTvSideNavigationRail(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    isSearchOpen = false
                    neliViewModel.selectTab(tab)
                },
                activeDownloadCount = downloadingIds.size
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                NeliTvTopBar(
                    activeTabLabel = when (selectedTab) {
                        BottomNavTab.HOME -> "Live TV & Sports"
                        BottomNavTab.DISCOVERY -> "Discovery (Movies & Series)"
                        BottomNavTab.SEARCH -> "Search & Explore"
                        BottomNavTab.DOWNLOAD -> "Downloads"
                        BottomNavTab.ACCOUNT -> "Account & Settings"
                    },
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    isSearchOpen = isSearchOpen,
                    onToggleSearch = {
                        isSearchOpen = !isSearchOpen
                        if (!isSearchOpen) searchQuery = ""
                    },
                    adminSmsMessage = adminSmsMessage
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    mainTabsContent()
                }
            }
        }
    } else {
        // Mobile Layout: TopNavBar (No Cast button) + Main Content + NeliBottomBar
        val infiniteTransition = rememberInfiniteTransition(label = "wallpaper_ambient_drift")
        // Ultra-smooth, slow organic ambient background animation
        val bgOffsetX by infiniteTransition.animateFloat(
            initialValue = -20f,
            targetValue = 20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 24000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bg_offset_x"
        )
        val bgOffsetY by infiniteTransition.animateFloat(
            initialValue = -14f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 28000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bg_offset_y"
        )
        val bgScale by infiniteTransition.animateFloat(
            initialValue = 1.04f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 32000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bg_scale"
        )
        val ambientColorPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 26000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ambient_color_phase"
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (NeliThemeManager.isLightMode) Color(0xFFFFF7F6) else Color(0xFF070A1A))
        ) {
            // Authentic Neon Ribbon Wallpaper with smooth organic floating motion
            if (!NeliThemeManager.isLightMode) {
                Image(
                    painter = painterResource(id = R.drawable.neli_bg_wallpaper),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(bgScale)
                        .offset(x = bgOffsetX.dp, y = bgOffsetY.dp)
                )

                // Slow and smooth organic morphing ambient gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x35FF2E7E).copy(alpha = 0.18f + ambientColorPhase * 0.12f),
                                    Color(0x2500E5FF).copy(alpha = 0.22f - ambientColorPhase * 0.10f),
                                    Color(0x18180A30),
                                    Color.Transparent
                                ),
                                center = Offset(
                                    x = 420f + bgOffsetX * 10f,
                                    y = 650f + bgOffsetY * 10f
                                ),
                                radius = 1050f
                            )
                        )
                )

                // Secondary soft floating aura for smooth visual richness
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0x1A6D28D9),
                                    Color.Transparent,
                                    Color(0x1500E5FF)
                                ),
                                start = Offset(bgOffsetX * 12f, bgOffsetY * 12f),
                                end = Offset(1000f - bgOffsetX * 12f, 1800f - bgOffsetY * 12f)
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_screen")
            ) {
                TopNavBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        if (!isSearchOpen) {
                            isSearchOpen = true
                        }
                    },
                    isSearchOpen = isSearchOpen,
                    onToggleSearch = {
                        if (isSearchOpen) {
                            isSearchOpen = false
                            searchQuery = ""
                        } else {
                            isSearchOpen = true
                        }
                    },
                    activeTabLabel = selectedTab.label,
                    isOfflineMode = isOfflineMode,
                    onOfflineClick = {
                        isSearchOpen = false
                        neliViewModel.selectTab(BottomNavTab.DOWNLOAD)
                    },
                    onBrandClick = {
                        isSearchOpen = false
                        searchQuery = ""
                        neliViewModel.selectTab(BottomNavTab.HOME)
                    },
                    onPremiumClick = {
                        isSearchOpen = false
                        searchQuery = ""
                        neliViewModel.selectTab(BottomNavTab.SEARCH)
                    },
                    isCastActive = isCastActive,
                    onCastClick = { showCastDialog = true },
                    adminSmsMessage = adminSmsMessage
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    mainTabsContent()
                }

                NeliBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        isSearchOpen = false
                        neliViewModel.selectTab(tab)
                    },
                    activeDownloadCount = downloadingIds.size
                )
            }

            // Launch welcome / v2.5 update dialog with notification prompt
            NeliV25WelcomeUpdateDialog()
        }
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
    onChannelSelected: (LiveChannel) -> Unit,
    adminSmsMessage: String = "",
    adminBannerPlacement: AdminBannerPlacement = AdminBannerPlacement.BELOW_SLIDER
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
            .take(10)
            .ifEmpty { allPrioritizedChannels.take(10) }
    }

    val liveCategories = listOf(
        "All Channels",
        "Sports",
        "Kids",
        "Movies",
        "Music",
        "News",
        "Other"
    )

    val filteredChannels = remember(selectedCategory, allPrioritizedChannels) {
        when {
            selectedCategory.equals("All", true) || selectedCategory.equals("All Channels", true) -> {
                allPrioritizedChannels
            }
            selectedCategory.equals("Sports", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("sports", true) || it.equals("sport", true) || it.equals("football", true) || it.equals("europe", true) } ||
                        ch.name.contains("sport", ignoreCase = true) ||
                        ch.name.contains("sk moments", ignoreCase = true)
                }
            }
            selectedCategory.equals("Kids", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("kids", true) || it.equals("kid", true) || it.contains("cartoon", true) || it.contains("animation", true) } ||
                        ch.name.contains("tom and jerry", ignoreCase = true) ||
                        ch.name.contains("pop kids", ignoreCase = true)
                }
            }
            selectedCategory.equals("Movies", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("movies", true) || it.equals("movie", true) || it.contains("cinema", true) } ||
                        ch.name.contains("movie", ignoreCase = true) ||
                        ch.name.contains("cinema", ignoreCase = true) ||
                        ch.name.contains("sinema", ignoreCase = true)
                }
            }
            selectedCategory.equals("Music", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("music", true) } ||
                        ch.name.contains("trace", ignoreCase = true) ||
                        ch.name.contains("wasafi", ignoreCase = true)
                }
            }
            selectedCategory.equals("News", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("news", true) } ||
                        ch.name.contains("news", ignoreCase = true) ||
                        ch.name.contains("global", ignoreCase = true) ||
                        ch.name.contains("habari", ignoreCase = true)
                }
            }
            selectedCategory.equals("Other", true) -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals("other", true) || it.equals("entertainment", true) } ||
                        ch.categories.isEmpty()
                }
            }
            else -> {
                allPrioritizedChannels.filter { ch ->
                    ch.categories.any { it.equals(selectedCategory, true) }
                }
            }
        }
    }

    // Group All Channels into blocks of 6 channels so a Muted Video Ad is placed after every 6 channels in vertical view
    val sixChannelBlocks = remember(filteredChannels) {
        ChannelRepository.getAllChannelsChunkedEverySixForAds(filteredChannels)
    }
    val screenProfile = rememberNeliScreenProfile()
    val columnsPerRow = screenProfile.liveChannelGridColumns

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
                        text = "You're offline • Azam TV live streams require an internet connection to play.",
                        color = Color(0xFFFDE68A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
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
                    val isSelected = category.equals(selectedCategory, ignoreCase = true) ||
                        (category == "All Channels" && (selectedCategory.equals("All", true) || selectedCategory.equals("All Channels", true)))
                    Box(
                        modifier = Modifier
                            .tvGeminiFocusable(
                                shape = RoundedCornerShape(20.dp),
                                focusScale = 1.12f,
                                borderWidth = 2.dp,
                                onClick = { onCategorySelected(if (category == "All Channels") "All" else category) }
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSelected) {
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFFF1B6B), Color(0xFFFF5E00))
                                    )
                                } else {
                                    androidx.compose.ui.graphics.SolidColor(Color(0xFF0F152E))
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0xFFFF2E7E) else Color(0x332B3B66),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onCategorySelected(if (category == "All Channels") "All" else category) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("live_cat_chip_$category")
                    ) {
                        Text(
                            text = category,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Featured Live Spotlight Banner + Admin Notification (Strictly Above or Below Slider on Home Page ONLY)
        if (featuredHeroChannels.isNotEmpty() && selectedCategory.equals("All", ignoreCase = true)) {
            if (adminSmsMessage.isNotBlank() && adminBannerPlacement == AdminBannerPlacement.ABOVE_SLIDER) {
                item(key = "admin_sms_above_slider") {
                    AdminTopSmsNotificationBanner(
                        smsMessage = adminSmsMessage,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            }

            item {
                LiveTvHeroBanner(
                    heroChannels = featuredHeroChannels,
                    onPlayChannel = onChannelSelected
                )
            }

            if (adminSmsMessage.isNotBlank() && adminBannerPlacement == AdminBannerPlacement.BELOW_SLIDER) {
                item(key = "admin_sms_below_slider") {
                    AdminTopSmsNotificationBanner(
                        smsMessage = adminSmsMessage,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }
            }

            // Start.io Banner Ad below hero slider (matching user mockup)
            item(key = "home_startio_banner_below_hero") {
                StartIoBannerAd(
                    adTag = "home_below_hero",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                )
            }
        } else if (adminSmsMessage.isNotBlank()) {
            item(key = "admin_sms_home_filtered") {
                AdminTopSmsNotificationBanner(
                    smsMessage = adminSmsMessage,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }

        // 3. All Live Channels Vertical Grid Feed (strictly vertical scroll without horizontal rows)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFF2A55), Color(0xFFFF7A00), Color(0xFF00E5FF))
                                )
                            )
                    )
                    Text(
                        text = if (selectedCategory.equals("All", true)) {
                            "All Channels"
                        } else {
                            selectedCategory
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isRefreshing) "Refreshing..." else "${filteredChannels.size} live",
                        color = Color(0xFF8E9BAE),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0077FF))
                            .testTag("refresh_live_tv_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Live TV",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = sixChannelBlocks,
            key = { blockIdx, block -> "all_channels_block_${blockIdx}_${block.firstOrNull()?.id.orEmpty()}" },
            contentType = { _, _ -> "six_channel_block" }
        ) { blockIndex, sixChannels ->
            val rowsInBlock = remember(sixChannels, columnsPerRow) { sixChannels.chunked(columnsPerRow) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("all_channels_vertical_block_$blockIndex")
            ) {
                rowsInBlock.forEachIndexed { rowIndex, rowItems ->
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
                        val emptySlots = (columnsPerRow - rowItems.size).coerceAtLeast(0)
                        repeat(emptySlots) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Native sponsored channel card inserted after every 2 channel blocks
                if (blockIndex % 2 == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        StartIoNativeCardAd()
                    }
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
    val screenProfile = rememberNeliScreenProfile()

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
        val isTablet = maxWidth >= 600.dp || screenProfile.isTabletOrFoldable
        val bannerHeight = screenProfile.heroBannerHeight

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
                    beyondViewportPageCount = 0,
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
                            .size(260, 260)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
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
                                        Color(0xFF160A26),
                                        Color(0xFF0C132E),
                                        Color(0xFF060914)
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFF007A),
                                        Color(0xFF9400D3),
                                        Color(0xFF0055FF),
                                        Color(0xFF00E5FF)
                                    )
                                ),
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
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color(0xFF19254F), Color(0xFF0F1738))
                                        )
                                    )
                                    .border(1.dp, Color(0x33283E70), RoundedCornerShape(18.dp))
                                    .padding(10.dp),
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
                                            .background(Color(0xFF0055FF))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = channel.category.uppercase(),
                                            color = Color.White,
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
                                    color = Color(0xFFB4C2DC),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFFFF007A),
                                                    Color(0xFFFF5E00)
                                                )
                                            )
                                        )
                                        .clickable { onPlayChannel(channel) }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
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
                            .background(if (isSelected) Color(0xFFFF007A) else Color(0xFF232D50))
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
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow
    val isLockedByAdmin = lockAllForFree || lockedChannelIds.contains(channel.id)
    val isLockedForCurrentUser = isLockedByAdmin && !isPremiumActive

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
            .size(240, 140)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }

    val screenProfile = rememberNeliScreenProfile()

    Column(
        modifier = Modifier
            .width(screenProfile.horizontalChannelCardWidth)
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

            if (isLockedByAdmin) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isLockedForCurrentUser) Color(0xDD991B1B) else Color(0xDD065F46))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isLockedForCurrentUser) Icons.Default.Lock else Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFDE047),
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text = if (isLockedForCurrentUser) "LOCKED" else "VIP",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black
                    )
                }
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
