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

    val currentUser: StateFlow<UserAccountEntity?> = authenticationRepository.currentUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val authSessionState: StateFlow<AuthSessionState> = authenticationRepository.authStateFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AuthSessionState.Unauthenticated
        )

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _googleFallbackMessage = MutableStateFlow<String?>(null)
    val googleFallbackMessage: StateFlow<String?> = _googleFallbackMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _showGoogleSignInSheet = MutableStateFlow(false)
    val showGoogleSignInSheet: StateFlow<Boolean> = _showGoogleSignInSheet.asStateFlow()

    private val _savedGoogleAccounts = MutableStateFlow<List<UserAccountEntity>>(emptyList())
    val savedGoogleAccounts: StateFlow<List<UserAccountEntity>> = _savedGoogleAccounts.asStateFlow()

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateOfflineState(false)
            // Automatically resume any downloads that were interrupted by network loss
            OfflineDownloadManager.resumeInterruptedDownloads(appContext, dao)
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
        // Auto-sync all Live TV channels immediately upon app entry so channels play without initial loading delay
        ChannelRepository.autoSyncAllChannelsNow()

        // Run non-UI initialization and background sync strictly on Dispatchers.IO so main UI thread stays 60/120fps
        viewModelScope.launch(Dispatchers.IO) {
            MediaContentRepository.initializeAndPrewarmFromCache(appContext)
            NeliAppUpdateManager.initialize(appContext)
            userManager.signInWithGoogleAutoOrPrimaryAccount(
                uiContext = appContext,
                forceInteractive = false
            )
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

        // Refresh Azam TV CDN token and Discovery catalog in background
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                performAutomaticSyncCycle()
                delay(10 * 60 * 1000L)
            }
        }
    }

    private val isSyncCycleRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile
    private var lastSyncCompletedAtMs: Long = 0L

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
            ChannelRepository.refreshLiveChannels()
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
        if (_selectedTab.value != BottomNavTab.PREMIUM && _selectedTab.value != BottomNavTab.ACCOUNT) {
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
    fun addDownload(media: MediaContent) {
        val effectiveDownloadUrl = media.downloadUrl.ifBlank { media.streamUrl }.ifBlank { media.playbackUrl }
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
            fileSizeLabel = "Starting turbo download • 1%",
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
        seriesPoster: String
    ) {
        val effectiveDownloadUrl = episode.downloadUrl.ifBlank { episode.streamUrl }.ifBlank { episode.playbackUrl }
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
            fileSizeLabel = "Starting turbo download • 1%",
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
        val entities = episodes.map { episode ->
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
     * Triggers Google Sign-In (selecting the primary/first Google account on the device or via CredentialManager).
     * When interactive sign-in needs user account selection (e.g., on an emulator or before SHA-1 OAuth setup),
     * opens the Google Account Sign-In Sheet & Device Account Chooser so Google Sign-In always works seamlessly.
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
                }
                is GoogleAutoSignInResult.PromptGoogleAccountSelection -> {
                    _savedGoogleAccounts.value = result.savedAccounts
                    _showGoogleSignInSheet.value = true
                    _googleFallbackMessage.value = null
                    _authError.value = null
                }
                is GoogleAutoSignInResult.UseEmailFallback -> {
                    _savedGoogleAccounts.value = userManager.getSavedAccounts()
                    _showGoogleSignInSheet.value = true
                    _googleFallbackMessage.value = null
                }
            }
        }
    }

    fun completeGoogleSignIn(email: String, displayName: String = "") {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = authenticationRepository.signInWithGoogleAccount(
                email = email,
                displayName = displayName
            )
            _isAuthLoading.value = false
            result.onSuccess { user ->
                _showGoogleSignInSheet.value = false
                _authError.value = null
                _googleFallbackMessage.value = null
                com.example.data.NeliSubscriptionManager.switchActiveAccount(
                    context = appContext,
                    uid = user.uid,
                    email = user.email,
                    realName = user.realName
                )
            }.onFailure { err ->
                _authError.value = err.message ?: "Please enter a valid Google email address."
            }
        }
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
                _authError.value = err.message ?: "Invalid email or password."
            }
        }
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
        ChannelRepository.refreshLiveChannels()

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ChannelRepository.refreshCdnTokenFromEndpointSync()
                ChannelRepository.refreshLiveChannels()
                delay(300)
            } catch (_: Exception) {
            } finally {
                _isRefreshingLiveTv.value = false
            }
        }
    }
}
