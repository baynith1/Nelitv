package com.example.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.AuthSessionState
import com.example.data.AuthenticationRepository
import com.example.data.ChannelRepository
import com.example.data.GoogleAutoSignInResult
import com.example.data.MediaContentRepository
import com.example.data.NeliAppUpdateManager
import com.example.data.NeliSubscriptionManager
import com.example.data.NeliSupabaseSyncManager
import com.example.data.OfflineDownloadManager
import com.example.data.UserManager
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.FirebaseConfigEntity
import com.example.data.local.NeliDatabase
import com.example.data.local.UserAccountEntity
import com.example.data.local.WatchlistItemEntity
import com.example.model.DownloadQualityOption
import com.example.model.EpisodeItem
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.components.BottomNavTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NeliViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = NeliDatabase.getInstance(application).mediaDao()
    val authenticationRepository = AuthenticationRepository(appContext, dao)
    val userManager = UserManager(appContext, dao, authenticationRepository)

    val mediaCatalog: StateFlow<List<MediaContent>> = MediaContentRepository.mediaCatalog
    val episodesCatalog: StateFlow<List<EpisodeItem>> = MediaContentRepository.episodesCatalog
    val catalogRotationSeed: StateFlow<Long> = MediaContentRepository.catalogRotationSeed
    val liveChannels: StateFlow<List<LiveChannel>> = ChannelRepository.liveChannelsFlow
    val firebaseSyncStatus: StateFlow<String> = MediaContentRepository.firebaseSyncStatus
    val downloadProgress: StateFlow<Map<String, Int>> = OfflineDownloadManager.downloadProgress
    val activeDownloadTitles: StateFlow<Map<String, String>> = OfflineDownloadManager.activeDownloadTitles
    val downloadBannerMessage: StateFlow<String?> = OfflineDownloadManager.downloadBannerMessage

    private val _isOfflineMode = MutableStateFlow(!OfflineDownloadManager.isDeviceOnline(appContext))
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // Navigation state: always start on Homepage (Azam TV Live Streaming)
    private val _selectedTab = MutableStateFlow(BottomNavTab.HOME)
    val selectedTab: StateFlow<BottomNavTab> = _selectedTab.asStateFlow()

    // Preserved Home Live TV scroll position & selected category so returning from Player stays on the exact last channel
    val homeLiveTvListState: androidx.compose.foundation.lazy.LazyListState =
        androidx.compose.foundation.lazy.LazyListState()

    private val _selectedLiveCategory = MutableStateFlow("All")
    val selectedLiveCategory: StateFlow<String> = _selectedLiveCategory.asStateFlow()

    fun selectLiveCategory(category: String) {
        _selectedLiveCategory.value = category
    }

    private val _selectedMediaId = MutableStateFlow<String?>(null)
    val selectedMediaId: StateFlow<String?> = _selectedMediaId.asStateFlow()

    private val _isRefreshingLiveTv = MutableStateFlow(false)
    val isRefreshingLiveTv: StateFlow<Boolean> = _isRefreshingLiveTv.asStateFlow()

    private val _isRefreshingDiscovery = MutableStateFlow(false)
    val isRefreshingDiscovery: StateFlow<Boolean> = _isRefreshingDiscovery.asStateFlow()

    val downloads: StateFlow<List<DownloadedItemEntity>> = combine(
        dao.getAllDownloads(),
        OfflineDownloadManager.activeDownloadEntities,
        OfflineDownloadManager.downloadProgress
    ) { dbList, activeMap, progressMap ->
        val mergedById = LinkedHashMap<String, DownloadedItemEntity>()
        // 1. Live active in-memory items take priority while actively downloading
        activeMap.values.forEach { activeItem ->
            if (activeItem.downloadStatus != "COMPLETED") {
                val livePct = (progressMap[activeItem.id] ?: activeItem.progressPercent).coerceIn(1, 99)
                mergedById[activeItem.id] = activeItem.copy(progressPercent = livePct)
            }
        }
        // 2. Database items (COMPLETED always wins over any stale in-memory state, followed by DOWNLOADING and PAUSED_ERROR)
        dbList.forEach { dbItem ->
            val isCompletedValid = dbItem.downloadStatus == "COMPLETED" &&
                dbItem.localFilePath.isNotBlank()
            if (isCompletedValid) {
                mergedById[dbItem.id] = dbItem.copy(
                    downloadStatus = "COMPLETED",
                    progressPercent = 100
                )
            } else if (!mergedById.containsKey(dbItem.id)) {
                val isActivelyDownloading = OfflineDownloadManager.isCurrentlyDownloading(dbItem.id)
                val isPausedOrRetryable = dbItem.downloadStatus == "PAUSED_ERROR" ||
                    (dbItem.downloadStatus == "DOWNLOADING" && !isActivelyDownloading)
                if (isActivelyDownloading) {
                    val livePct = (progressMap[dbItem.id] ?: dbItem.progressPercent).coerceIn(1, 99)
                    mergedById[dbItem.id] = dbItem.copy(progressPercent = livePct)
                } else if (isPausedOrRetryable) {
                    val savedPct = dbItem.progressPercent.coerceIn(1, 99)
                    mergedById[dbItem.id] = dbItem.copy(
                        downloadStatus = "PAUSED_ERROR",
                        progressPercent = savedPct,
                        fileSizeLabel = if (dbItem.fileSizeLabel.contains("Resume", ignoreCase = true) ||
                            dbItem.fileSizeLabel.contains("Retry", ignoreCase = true)
                        ) {
                            dbItem.fileSizeLabel
                        } else {
                            "Paused at $savedPct% • Tap Resume to continue"
                        }
                    )
                }
            }
        }
        mergedById.values.sortedByDescending { it.downloadedAt }
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val downloadedIds: StateFlow<Set<String>> = dao.getAllDownloads()
        .map { list ->
            list.filter { it.downloadStatus == "COMPLETED" && it.localFilePath.isNotBlank() }
                .map { it.id }
                .toSet()
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val downloadingIds: StateFlow<Set<String>> = combine(
        downloads,
        OfflineDownloadManager.downloadProgress
    ) { list, progressMap ->
        val completedSet = list.filter { it.downloadStatus == "COMPLETED" }.map { it.id }.toSet()
        val fromList = list.filter {
            it.downloadStatus != "COMPLETED" &&
                (it.downloadStatus == "DOWNLOADING" || OfflineDownloadManager.isCurrentlyDownloading(it.id))
        }.map { it.id }
        ((fromList + progressMap.keys) - completedSet).toSet()
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptySet()
    )

    val watchlistItems: StateFlow<List<WatchlistItemEntity>> = dao.getAllWatchlist()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val watchlistIds: StateFlow<Set<String>> = watchlistItems
        .map { list -> list.map { it.id }.toSet() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val firebaseConfig: StateFlow<FirebaseConfigEntity> = dao.getFirebaseConfig()
        .map { it ?: FirebaseConfigEntity() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FirebaseConfigEntity()
        )

    private val _hasResolvedInitialAuth = MutableStateFlow(false)
    val hasResolvedInitialAuth: StateFlow<Boolean> = _hasResolvedInitialAuth.asStateFlow()

    val currentUser: StateFlow<UserAccountEntity?> = authenticationRepository.currentUserFlow
        .map { user ->
            _hasResolvedInitialAuth.value = true
            user
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val authSessionState: StateFlow<AuthSessionState> = authenticationRepository.authStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AuthSessionState.Unauthenticated
        )

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _usernameUpdateMessage = MutableStateFlow<String?>(null)
    val usernameUpdateMessage: StateFlow<String?> = _usernameUpdateMessage.asStateFlow()

    private val _googleFallbackMessage = MutableStateFlow<String?>(null)
    val googleFallbackMessage: StateFlow<String?> = _googleFallbackMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _showGoogleSignInSheet = MutableStateFlow(false)
    val showGoogleSignInSheet: StateFlow<Boolean> = _showGoogleSignInSheet.asStateFlow()

    private val _savedGoogleAccounts = MutableStateFlow<List<UserAccountEntity>>(emptyList())
    val savedGoogleAccounts: StateFlow<List<UserAccountEntity>> = _savedGoogleAccounts.asStateFlow()

    companion object {
        private val isSyncCycleRunning = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile
        private var lastSyncCompletedAtMs: Long = 0L
    }

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateOfflineState(false)
            // Automatically resume any downloads that were interrupted by network loss
            OfflineDownloadManager.resumeInterruptedDownloads(appContext, dao)
            // Reload AdMob inventory on mobile/tablet when internet returns (no-op on Smart TV)
            com.example.ads.NeliAdManager.onNetworkRestored(appContext)
            // Automatically sync Azam TV Cloud Token, catalog, and GitHub app updates when internet returns
            triggerAutomaticBackgroundSync()
        }

        override fun onLost(network: Network) {
            val nowOffline = !OfflineDownloadManager.isDeviceOnline(appContext)
            if (nowOffline != _isOfflineMode.value) {
                updateOfflineState(nowOffline)
            }
        }

        override fun onUnavailable() {
            if (!_isOfflineMode.value) {
                updateOfflineState(true)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val nowOffline = !hasInternet
            if (nowOffline != _isOfflineMode.value) {
                updateOfflineState(nowOffline)
            }
        }
    }

    init {
        registerConnectivityMonitor()

        com.example.data.NeliTvRepository.preloadOnStartup(appContext)

        viewModelScope.launch(Dispatchers.IO) {
            MediaContentRepository.initializeAndPrewarmFromCache(appContext)
            NeliAppUpdateManager.initialize(appContext)
            com.example.data.NeliSubscriptionManager.initialize(appContext)
            try {
                com.example.data.NeliSubscriptionManager.restoreFromDatabaseByDeviceIp(appContext, dao)
            } catch (_: Throwable) {
            }
            try {
                _savedGoogleAccounts.value = userManager.getSavedAccounts()
            } catch (_: Throwable) {
            }
            // Hydrate active VIP subscription from Supabase if a phone number is cached locally (works without login)
            try {
                val subNow = com.example.data.NeliSubscriptionManager.subscriptionState.value
                val savedPhone = subNow.phoneNumber.trim()
                if (!com.example.data.NeliSubscriptionManager.isNumberManuallyRemoved(appContext) &&
                    savedPhone.isNotBlank() &&
                    com.example.data.HarakaPayRepository.isValidTzPhoneNumber(
                        com.example.data.HarakaPayRepository.normalizeTzPhoneNumber(savedPhone)
                    )
                ) {
                    val record = NeliSupabaseSyncManager.querySubscriptionByPhone(savedPhone).getOrNull()
                    if (record != null && record.isActive) {
                        com.example.data.NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(
                            context = appContext,
                            record = record
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // Automatically switch active account subscription context & sync Cast user info
        viewModelScope.launch(Dispatchers.IO) {
            var hadLoggedInUser = false
            combine(
                authenticationRepository.currentUserFlow,
                com.example.data.NeliSubscriptionManager.subscriptionState
            ) { user, subState ->
                Pair(user, subState)
            }.collect { (user, subState) ->
                if (user != null && user.email.isNotBlank()) {
                    hadLoggedInUser = true
                    if (!subState.linkedUserEmail.equals(user.email, ignoreCase = true) || subState.requiresPostPaymentAuth) {
                        com.example.data.NeliSubscriptionManager.switchActiveAccount(
                            context = appContext,
                            uid = user.uid,
                            email = user.email,
                            realName = user.realName
                        )
                    }
                } else if (user == null) {
                    if (hadLoggedInUser || subState.linkedUserEmail.isNotBlank() || subState.isFreeForeverAccount) {
                        hadLoggedInUser = false
                        com.example.data.NeliSubscriptionManager.switchActiveAccount(
                            context = appContext,
                            uid = "",
                            email = "",
                            realName = ""
                        )
                    }
                }
                com.example.player.NeliCastManager.syncUserAndSubscriptionInfo(
                    userName = user?.realName.orEmpty(),
                    email = user?.email.orEmpty(),
                    planTitle = if (user != null) subState.planTitle else "",
                    isVerified = user != null && subState.isActiveNow,
                    deviceIp = subState.deviceIpAddress
                )
            }
        }

        // Refresh NeliTv bootstrap & Discovery catalog silently every 20 minutes in background (never blocks UI)
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                performAutomaticSyncCycle()
                val refreshSec = com.example.data.NeliTvRepository.bootstrapState.value
                    .refreshIntervalSeconds
                    .coerceIn(900L, 1_800L)
                delay(refreshSec * 1_000L)
            }
        }
    }

    private fun triggerAutomaticBackgroundSync() {
        val now = System.currentTimeMillis()
        if (now - lastSyncCompletedAtMs < 15_000L) return
        viewModelScope.launch(Dispatchers.IO) {
            performAutomaticSyncCycle()
        }
    }

    private suspend fun performAutomaticSyncCycle() = withContext(Dispatchers.IO) {
        if (!isSyncCycleRunning.compareAndSet(false, true)) return@withContext
        try {
            lastSyncCompletedAtMs = System.currentTimeMillis()
            try {
                com.example.data.NeliTvRepository.fetchAppBootstrap(forceRefresh = false)
            } catch (_: Exception) {
                ChannelRepository.refreshLiveChannels()
            }
            com.example.data.ChannelHealthGuardian.runBackgroundHealthAudit(liveChannels.value)
            val apiKey = AuthRepository.resolveApiKey(appContext)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID,
                forceRefresh = false
            )
            com.example.notifications.NeliNotificationScheduler.scheduleAllDailyNotifications(appContext)
            com.example.widget.NeliHomeWidgetProvider.updateAllWidgets(appContext)
            NeliAppUpdateManager.checkForUpdates(appContext, triggeredByUser = false)
        } catch (_: Exception) {
        } finally {
            isSyncCycleRunning.set(false)
        }
    }

    private fun registerConnectivityMonitor() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
    }

    fun updateOfflineState(isOffline: Boolean) {
        _isOfflineMode.value = isOffline
        if (isOffline) {
            _selectedMediaId.value = null
            _selectedTab.value = BottomNavTab.DOWNLOAD
        }
    }

    fun refreshConnectivityState() {
        val currentlyOffline = !OfflineDownloadManager.isDeviceOnline(appContext)
        if (currentlyOffline != _isOfflineMode.value) {
            updateOfflineState(currentlyOffline)
        }
    }

    fun selectTab(tab: BottomNavTab) {
        _selectedMediaId.value = null
        _selectedTab.value = tab
    }

    fun openMediaDetails(mediaId: String?) {
        _selectedMediaId.value = mediaId
        if (mediaId != null) {
            _selectedTab.value = BottomNavTab.DISCOVERY
        }
    }

    fun navigateBackFromMediaDetails() {
        _selectedMediaId.value = null
        _selectedTab.value = BottomNavTab.DISCOVERY
    }

    fun onReturnFromWatchPage(watchedChannel: LiveChannel) {
        if (!watchedChannel.isLiveBroadcast) {
            val resolvedMedia = MediaContentRepository.resolveMediaForPlaybackChannel(watchedChannel)
            if (resolvedMedia != null) {
                _selectedMediaId.value = resolvedMedia.id
                _selectedTab.value = BottomNavTab.DISCOVERY
                return
            }
        }
        _selectedMediaId.value = null
        if (_selectedTab.value != BottomNavTab.SEARCH && _selectedTab.value != BottomNavTab.ACCOUNT) {
            _selectedTab.value = BottomNavTab.HOME
        }
    }

    fun dismissDownloadBanner() {
        OfflineDownloadManager.dismissBannerMessage()
    }

    /**
     * Downloads a movie or series to the phone's internal storage (`offline_media`) using its streaming link directly.
     * Runs in a persistent background scope so it continues even when the user exits the app.
     */
    fun addDownload(
        media: MediaContent,
        quality: DownloadQualityOption = DownloadQualityOption.STANDARD_480P
    ) {
        if (!media.isDownloadPermitted) return
        val rawDownloadUrl = media.downloadUrl.ifBlank { media.streamUrl }.ifBlank { media.playbackUrl }
        val effectiveDownloadUrl = quality.resolveQualityStreamUrl(rawDownloadUrl, media.qualityUrls)
        if (media.isYoutubeSource && com.example.player.NeliEmbedPlayerHelper.isYoutubeOrEmbedNeliUrl(effectiveDownloadUrl)) {
            return
        }
        val guaranteedPoster = MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.posterUrl,
            media.backdropUrl,
            effectiveDownloadUrl
        )
        val guaranteedBackdrop = MediaContentRepository.resolveGuaranteedMediaImageUrl(
            media.backdropUrl,
            media.posterUrl,
            effectiveDownloadUrl
        )
        val item = DownloadedItemEntity(
            id = media.id,
            title = if (media.narrated && media.narrationLanguage.isNotBlank()) {
                "${media.title} (${media.narrationLanguage})"
            } else {
                media.title
            },
            type = media.type,
            posterUrl = guaranteedPoster,
            backdropUrl = guaranteedBackdrop,
            streamUrl = ChannelRepository.normalizeDashStreamUrl(effectiveDownloadUrl),
            genre = media.primaryGenre,
            duration = media.duration,
            rating = media.rating,
            fileSizeLabel = "Starting ${quality.resolutionBadge} download • 1%",
            downloadStatus = "DOWNLOADING",
            progressPercent = 1
        )
        OfflineDownloadManager.enqueueBackgroundDownload(
            context = appContext,
            dao = dao,
            item = item
        )
    }

    /**
     * Downloads an episode to the phone's internal storage (`offline_media`) using its streaming link directly.
     * Runs in a persistent background scope so it continues even when the user exits the app.
     */
    fun addEpisodeDownload(
        episode: EpisodeItem,
        seriesTitle: String,
        seriesPoster: String,
        quality: DownloadQualityOption = DownloadQualityOption.STANDARD_480P
    ) {
        if (!episode.isDownloadPermitted) return
        val rawDownloadUrl = episode.downloadUrl.ifBlank { episode.streamUrl }.ifBlank { episode.playbackUrl }
        val effectiveDownloadUrl = quality.resolveQualityStreamUrl(rawDownloadUrl, episode.qualityUrls)
        if (episode.isYoutubeSource && com.example.player.NeliEmbedPlayerHelper.isYoutubeOrEmbedNeliUrl(effectiveDownloadUrl)) {
            return
        }
        val poster = MediaContentRepository.resolveGuaranteedMediaImageUrl(
            episode.stillPath,
            seriesPoster,
            effectiveDownloadUrl
        )
        val item = DownloadedItemEntity(
            id = episode.id,
            title = "$seriesTitle • S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.name}",
            type = "series",
            posterUrl = poster,
            backdropUrl = poster,
            streamUrl = ChannelRepository.normalizeDashStreamUrl(effectiveDownloadUrl),
            genre = if (episode.narrated) "Series • ${episode.narrationLanguage}" else "Series",
            duration = episode.durationLabel,
            rating = "HD",
            fileSizeLabel = "Starting ${quality.resolutionBadge} download • 1%",
            downloadStatus = "DOWNLOADING",
            progressPercent = 1
        )
        OfflineDownloadManager.enqueueBackgroundDownload(
            context = appContext,
            dao = dao,
            item = item
        )
    }

    /**
     * Enqueues multiple Series episodes for simultaneous background multi-download.
     */
    fun addMultipleEpisodeDownloads(
        episodes: List<EpisodeItem>,
        seriesTitle: String,
        seriesPoster: String
    ) {
        val entities = episodes
            .filter { ep ->
                ep.isDownloadPermitted &&
                    !(ep.isYoutubeSource && com.example.player.NeliEmbedPlayerHelper.isYoutubeOrEmbedNeliUrl(
                        ep.downloadUrl.ifBlank { ep.streamUrl }.ifBlank { ep.playbackUrl }
                    ))
            }
            .map { episode ->
            val effectiveDownloadUrl = episode.downloadUrl.ifBlank { episode.streamUrl }.ifBlank { episode.playbackUrl }
            val poster = MediaContentRepository.resolveGuaranteedMediaImageUrl(
                episode.stillPath,
                seriesPoster,
                effectiveDownloadUrl
            )
            DownloadedItemEntity(
                id = episode.id,
                title = "$seriesTitle • S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.name}",
                type = "series",
                posterUrl = poster,
                backdropUrl = poster,
                streamUrl = ChannelRepository.normalizeDashStreamUrl(effectiveDownloadUrl),
                genre = if (episode.narrated) "Series • ${episode.narrationLanguage}" else "Series",
                duration = episode.durationLabel,
                rating = "HD",
                fileSizeLabel = "Starting turbo download • 1%",
                downloadStatus = "DOWNLOADING",
                progressPercent = 1
            )
        }
        if (entities.isEmpty()) return
        OfflineDownloadManager.enqueueMultipleBackgroundDownloads(
            context = appContext,
            dao = dao,
            items = entities
        )
    }

    fun retryDownload(item: DownloadedItemEntity) {
        OfflineDownloadManager.retryDownload(
            context = appContext,
            dao = dao,
            item = item
        )
    }

    fun pauseDownload(id: String) {
        OfflineDownloadManager.pauseDownload(
            context = appContext,
            dao = dao,
            id = id
        )
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch {
            OfflineDownloadManager.deleteOfflineDownload(dao, id, appContext)
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            OfflineDownloadManager.deleteOfflineDownload(dao, id, appContext)
        }
    }

    fun toggleWatchlist(media: MediaContent) {
        viewModelScope.launch {
            if (dao.isInWatchlist(media.id)) {
                dao.deleteWatchlistById(media.id)
            } else {
                dao.insertWatchlist(
                    WatchlistItemEntity(
                        id = media.id,
                        title = media.title,
                        type = media.type,
                        posterUrl = media.posterUrl,
                        streamUrl = media.streamUrl,
                        genre = media.genre,
                        duration = media.duration,
                        rating = media.rating
                    )
                )
            }
        }
    }

    fun toggleChannelFavorite(channel: LiveChannel) {
        viewModelScope.launch {
            if (dao.isInWatchlist(channel.id)) {
                dao.deleteWatchlistById(channel.id)
            } else {
                dao.insertWatchlist(
                    WatchlistItemEntity(
                        id = channel.id,
                        title = channel.name,
                        type = "live",
                        posterUrl = channel.thumbnailUrl,
                        streamUrl = channel.streamUrl,
                        genre = channel.category,
                        duration = "LIVE",
                        rating = "LIVE"
                    )
                )
            }
        }
    }

    private var hasAttemptedActivityAutoSignIn = false

    fun attemptAutoGoogleSignInIfNeeded(uiContext: Context) {
        if (hasAttemptedActivityAutoSignIn) return
        hasAttemptedActivityAutoSignIn = true
        viewModelScope.launch {
            userManager.signInWithGoogleAutoOrPrimaryAccount(
                uiContext = uiContext,
                forceInteractive = false
            )
        }
    }

    /**
     * Triggers real interactive Google Sign-In via Android CredentialManager + Firebase Auth.
     */
    fun signInWithGoogle(uiContext: Context = appContext) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null

            val result = authenticationRepository.signInWithGoogle(
                uiContext = uiContext,
                forceInteractive = true
            )
            _isAuthLoading.value = false
            when (result) {
                is GoogleAutoSignInResult.Success -> {
                    _showGoogleSignInSheet.value = false
                    _googleFallbackMessage.value = null
                    _authError.value = null
                    _savedGoogleAccounts.value = userManager.getSavedAccounts()
                    NeliSubscriptionManager.switchActiveAccount(
                        context = appContext,
                        uid = result.account.uid,
                        email = result.account.email,
                        realName = result.account.realName
                    )
                }
                is GoogleAutoSignInResult.PromptGoogleAccountSelection -> {
                    _savedGoogleAccounts.value = result.savedAccounts
                    _showGoogleSignInSheet.value = true
                    _googleFallbackMessage.value = null
                    _authError.value = null
                }
                is GoogleAutoSignInResult.Cancelled -> {
                    _showGoogleSignInSheet.value = false
                    _googleFallbackMessage.value = null
                    _authError.value = null
                }
                is GoogleAutoSignInResult.UseEmailFallback -> {
                    _savedGoogleAccounts.value = userManager.getSavedAccounts()
                    _showGoogleSignInSheet.value = false
                    _authError.value = result.reasonMessage
                }
            }
        }
    }

    /**
     * Launches real CredentialManager Google Sign-In account selector so user chooses and authenticates a Google account.
     */
    fun chooseGoogleAccount(uiContext: Context = appContext) {
        signInWithGoogle(uiContext)
    }

    fun completeGoogleSignIn(
        email: String = "",
        displayName: String = "",
        uiContext: Context = appContext
    ) {
        signInWithGoogle(uiContext)
    }

    fun dismissGoogleSignInSheet() {
        _showGoogleSignInSheet.value = false
    }

    fun signUpUser(realName: String, email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.registerWithEmailAndPassword(
                realName = realName,
                email = email,
                password = password
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                com.example.data.NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
            }.onFailure { err ->
                _authError.value = err.message ?: "Unable to create account."
            }
        }
    }

    fun signInUser(email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signInWithEmailAndPassword(
                email = email,
                password = password
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                com.example.data.NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
            }.onFailure { err ->
                _authError.value = err.message ?: "Invalid email/username or password."
            }
        }
    }

    fun signInAsGuest(guestName: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signInAsGuest(guestName)
            _isAuthLoading.value = false
            result.onSuccess { user ->
                com.example.data.NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
            }.onFailure { err ->
                _authError.value = err.message ?: "Tafadhali andika jina lako (angalau herufi 2)."
            }
        }
    }

    fun updateUsername(newUsername: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _usernameUpdateMessage.value = null
            val result = authenticationRepository.updateUsername(newUsername)
            _isAuthLoading.value = false
            result.onSuccess { updated ->
                _savedGoogleAccounts.value = userManager.getSavedAccounts()
                _usernameUpdateMessage.value = "Username updated to \"${updated.realName}\" & synced to Firebase!"
            }.onFailure { err ->
                _authError.value = err.message ?: "Unable to update username."
            }
        }
    }

    fun clearUsernameUpdateMessage() {
        _usernameUpdateMessage.value = null
    }

    fun signInWithPhone(phoneNumber: String, realName: String = "", password: String = "") {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signInOrRegisterWithPhoneNumber(
                phoneNumber = phoneNumber,
                realName = realName,
                password = password
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                val cleanPhone = phoneNumber.filter { it.isDigit() }
                NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
                // Automatically restore active VIP subscription from Supabase for this phone number
                viewModelScope.launch(Dispatchers.IO) {
                    val subResult = NeliSupabaseSyncManager.querySubscriptionByPhone(cleanPhone)
                    subResult.onSuccess { record ->
                        if (record != null && record.isActive) {
                            NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(appContext, record)
                        }
                    }
                }
            }.onFailure { err ->
                _authError.value = err.message ?: "Haikuweza kuingia kwa namba ya simu."
            }
        }
    }

    fun signUpWithPhone(username: String, phoneNumber: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signUpWithPhoneNumber(
                username = username,
                phoneNumber = phoneNumber,
                password = password
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                val cleanPhone = phoneNumber.filter { it.isDigit() }
                NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
                if (cleanPhone.length >= 9) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val subResult = NeliSupabaseSyncManager.querySubscriptionByPhone(cleanPhone)
                        subResult.onSuccess { record ->
                            if (record != null && record.isActive) {
                                NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(appContext, record)
                            }
                        }
                    }
                }
            }.onFailure { err ->
                _authError.value = err.message ?: "Unable to create phone account."
            }
        }
    }

    fun signInWithPhoneCredentials(phoneOrUsername: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signInWithPhoneNumber(
                phoneOrUsername = phoneOrUsername,
                password = password
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                val cleanPhone = user.email.substringBefore("@").filter { it.isDigit() }
                NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
                if (cleanPhone.length >= 9) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val subResult = NeliSupabaseSyncManager.querySubscriptionByPhone(cleanPhone)
                        subResult.onSuccess { record ->
                            if (record != null && record.isActive) {
                                NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(appContext, record)
                            }
                        }
                    }
                }
            }.onFailure { err ->
                _authError.value = err.message ?: "Invalid phone/username or password."
            }
        }
    }

    fun trySilentGoogleAutoSignIn(uiContext: Context) {
        attemptAutoGoogleSignInIfNeeded(uiContext)
    }

    fun signOutUser() {
        viewModelScope.launch {
            _authError.value = null
            _googleFallbackMessage.value = null
            authenticationRepository.signOut()
            com.example.data.NeliSubscriptionManager.switchActiveAccount(
                context = appContext,
                uid = "",
                email = "",
                realName = ""
            )
            try {
                _savedGoogleAccounts.value = userManager.getSavedAccounts()
            } catch (_: Throwable) {
            }
        }
    }

    fun clearAuthError() {
        _authError.value = null
        _googleFallbackMessage.value = null
    }

    fun updateNetworkPreferences(networkMode: String, allowMobileData: Boolean) {
        viewModelScope.launch {
            val current = firebaseConfig.value
            dao.saveFirebaseConfig(
                current.copy(
                    networkMode = networkMode,
                    allowMobileData = allowMobileData,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun refreshCatalog() {
        MediaContentRepository.rotateMovieCatalogOrder()
        viewModelScope.launch {
            val apiKey = AuthRepository.resolveApiKey(appContext)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID,
                forceRefresh = true
            )
            com.example.widget.NeliHomeWidgetProvider.updateAllWidgets(appContext)
        }
    }

    /**
     * Immediately shuffles/rotates all movie shelves and spotlight picks on the Discovery page.
     */
    fun rotateDiscoveryMovies() {
        MediaContentRepository.rotateMovieCatalogOrder()
    }

    /**
     * Pull-to-refresh / live Studio Admin sync handler for the Discovery (Movies & Series) tab.
     * Immediately rotates the movie shelves so the UI feels alive and responsive, then fetches
     * any newly added Studio Admin movies from Cloud Firestore & Realtime Database.
     */
    fun refreshDiscoveryCatalog(forceNetworkSync: Boolean = true) {
        MediaContentRepository.rotateMovieCatalogOrder()
        if (!forceNetworkSync) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val apiKey = AuthRepository.resolveApiKey(appContext)
                    MediaContentRepository.syncFromFirebaseEndpoint(
                        databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                        apiKey = apiKey,
                        projectId = MediaContentRepository.DEFAULT_PROJECT_ID,
                        forceRefresh = false
                    )
                } catch (_: Exception) {
                }
            }
            return
        }
        if (_isRefreshingDiscovery.value) return
        _isRefreshingDiscovery.value = true
        viewModelScope.launch {
            try {
                val apiKey = AuthRepository.resolveApiKey(appContext)
                MediaContentRepository.syncFromFirebaseEndpoint(
                    databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                    apiKey = apiKey,
                    projectId = MediaContentRepository.DEFAULT_PROJECT_ID,
                    forceRefresh = true
                )
                MediaContentRepository.rotateMovieCatalogOrder()
                com.example.widget.NeliHomeWidgetProvider.updateAllWidgets(appContext)
                delay(300)
            } catch (_: Exception) {
            } finally {
                _isRefreshingDiscovery.value = false
            }
        }
    }

    /**
     * Pull-to-refresh handler for the Homepage Live TV list.
     * Immediately refreshes local prioritized channels & CDN tokens, then syncs any
     * updated tokens/channels from the cloud without requiring an app restart.
     */
    fun refreshLiveTvFeed() {
        if (_isRefreshingLiveTv.value) return
        _isRefreshingLiveTv.value = true
        refreshConnectivityState()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                com.example.data.NeliTvRepository.fetchAppBootstrap(forceRefresh = true)
                ChannelRepository.refreshLiveChannels()
                delay(250)
            } catch (_: Exception) {
            } finally {
                _isRefreshingLiveTv.value = false
            }
        }
    }
}
