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
import com.example.data.ChannelRepository
import com.example.data.GoogleAutoSignInResult
import com.example.data.MediaContentRepository
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NeliViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = NeliDatabase.getInstance(application).mediaDao()
    val userManager = UserManager(appContext, dao)

    val mediaCatalog: StateFlow<List<MediaContent>> = MediaContentRepository.mediaCatalog
    val episodesCatalog: StateFlow<List<EpisodeItem>> = MediaContentRepository.episodesCatalog
    val liveChannels: StateFlow<List<LiveChannel>> = ChannelRepository.liveChannelsFlow
    val firebaseSyncStatus: StateFlow<String> = MediaContentRepository.firebaseSyncStatus
    val downloadProgress: StateFlow<Map<String, Int>> = OfflineDownloadManager.downloadProgress
    val activeDownloadTitles: StateFlow<Map<String, String>> = OfflineDownloadManager.activeDownloadTitles
    val downloadBannerMessage: StateFlow<String?> = OfflineDownloadManager.downloadBannerMessage

    private val _isOfflineMode = MutableStateFlow(!OfflineDownloadManager.isDeviceOnline(appContext))
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    val downloads: StateFlow<List<DownloadedItemEntity>> = dao.getAllDownloads()
        .map { list ->
            val invalidItems = list.filter {
                (it.downloadStatus == "COMPLETED" && !OfflineDownloadManager.isDownloadFileValidOnDisk(it)) ||
                    (it.downloadStatus == "DOWNLOADING" && !OfflineDownloadManager.isCurrentlyDownloading(it.id))
            }
            if (invalidItems.isNotEmpty()) {
                viewModelScope.launch {
                    OfflineDownloadManager.purgeInvalidDownloads(dao, invalidItems)
                }
            }
            list.filter {
                OfflineDownloadManager.isDownloadFileValidOnDisk(it) ||
                    OfflineDownloadManager.isCurrentlyDownloading(it.id)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val downloadedIds: StateFlow<Set<String>> = downloads
        .map { list ->
            list.filter { OfflineDownloadManager.isDownloadFileValidOnDisk(it) }
                .map { it.id }
                .toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val downloadingIds: StateFlow<Set<String>> = downloads
        .map { list ->
            list.filter { it.downloadStatus == "DOWNLOADING" && OfflineDownloadManager.isCurrentlyDownloading(it.id) }
                .map { it.id }
                .toSet()
        }
        .stateIn(
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

    val currentUser: StateFlow<UserAccountEntity?> = dao.getActiveUser()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _googleFallbackMessage = MutableStateFlow<String?>(null)
    val googleFallbackMessage: StateFlow<String?> = _googleFallbackMessage.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOfflineMode.value = false
        }

        override fun onLost(network: Network) {
            _isOfflineMode.value = !OfflineDownloadManager.isDeviceOnline(appContext)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOfflineMode.value = !hasInternet
        }
    }

    init {
        registerConnectivityMonitor()

        // 1. YouTube-style automatic Google Sign-In on first launch if no account is signed in yet
        viewModelScope.launch {
            userManager.signInWithGoogleAutoOrPrimaryAccount(
                uiContext = appContext,
                forceInteractive = false
            )
        }

        // 2. Sync live catalog if online
        viewModelScope.launch {
            val apiKey = AuthRepository.resolveApiKey(appContext)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID
            )
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

    fun refreshConnectivityState() {
        _isOfflineMode.value = !OfflineDownloadManager.isDeviceOnline(appContext)
    }

    fun dismissDownloadBanner() {
        OfflineDownloadManager.dismissBannerMessage()
    }

    /**
     * Downloads a movie or series to the phone's internal storage (`offline_media`) using its streaming link directly.
     * Runs in a persistent background scope so it continues even when the user exits the app.
     */
    fun addDownload(media: MediaContent) {
        val item = DownloadedItemEntity(
            id = media.id,
            title = if (media.narrated && media.narrationLanguage.isNotBlank()) {
                "${media.title} (${media.narrationLanguage})"
            } else {
                media.title
            },
            type = media.type,
            posterUrl = media.posterUrl,
            backdropUrl = media.backdropUrl,
            streamUrl = ChannelRepository.normalizeDashStreamUrl(media.streamUrl),
            genre = media.genre,
            duration = media.duration,
            rating = media.rating,
            fileSizeLabel = "Starting download • 1%",
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
        val item = DownloadedItemEntity(
            id = episode.id,
            title = "$seriesTitle • S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.name}",
            type = "series",
            posterUrl = episode.stillPath.ifBlank { seriesPoster },
            backdropUrl = episode.stillPath.ifBlank { seriesPoster },
            streamUrl = ChannelRepository.normalizeDashStreamUrl(episode.streamUrl),
            genre = if (episode.narrated) "Series • ${episode.narrationLanguage}" else "Series",
            duration = episode.durationLabel,
            rating = "HD",
            fileSizeLabel = "Starting download • 1%",
            downloadStatus = "DOWNLOADING",
            progressPercent = 1
        )
        OfflineDownloadManager.enqueueBackgroundDownload(
            context = appContext,
            dao = dao,
            item = item
        )
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            OfflineDownloadManager.deleteOfflineDownload(dao, id)
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
     * Triggers Google Sign-In (selecting the primary/first Google account on the device).
     * If Google Sign-In fails or no Google account is configured on the device, sets a helpful
     * "Use email instead" fallback message so the user can sign in or register with Email & Password.
     */
    fun signInWithGoogle(uiContext: Context = appContext) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null

            val result = userManager.signInWithGoogleAutoOrPrimaryAccount(
                uiContext = uiContext,
                forceInteractive = true
            )
            _isAuthLoading.value = false
            when (result) {
                is GoogleAutoSignInResult.Success -> {
                    _googleFallbackMessage.value = null
                    _authError.value = null
                }
                is GoogleAutoSignInResult.UseEmailFallback -> {
                    _googleFallbackMessage.value = result.reasonMessage
                }
            }
        }
    }

    fun signUpUser(realName: String, email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = userManager.registerWithEmailAndPassword(
                realName = realName,
                email = email,
                password = password
            )
            _isAuthLoading.value = false
            result.onFailure { err ->
                _authError.value = err.message ?: "Unable to create account."
            }
        }
    }

    fun signInUser(email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            _googleFallbackMessage.value = null
            val result = userManager.loginWithEmailAndPassword(
                email = email,
                password = password
            )
            _isAuthLoading.value = false
            result.onFailure { err ->
                _authError.value = err.message ?: "Invalid email or password."
            }
        }
    }

    fun signOutUser() {
        viewModelScope.launch {
            _authError.value = null
            _googleFallbackMessage.value = null
            userManager.signOut()
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
        viewModelScope.launch {
            val apiKey = AuthRepository.resolveApiKey(appContext)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID
            )
        }
    }
}
