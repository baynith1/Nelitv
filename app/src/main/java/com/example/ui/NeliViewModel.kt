package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.ChannelRepository
import com.example.data.MediaContentRepository
import com.example.data.OfflineDownloadManager
import com.example.data.local.DownloadedItemEntity
import com.example.data.local.FirebaseConfigEntity
import com.example.data.local.NeliDatabase
import com.example.data.local.UserAccountEntity
import com.example.data.local.WatchlistItemEntity
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

    val mediaCatalog: StateFlow<List<MediaContent>> = MediaContentRepository.mediaCatalog
    val episodesCatalog: StateFlow<List<EpisodeItem>> = MediaContentRepository.episodesCatalog
    val liveChannels: StateFlow<List<LiveChannel>> = ChannelRepository.liveChannelsFlow
    val firebaseSyncStatus: StateFlow<String> = MediaContentRepository.firebaseSyncStatus
    val downloadProgress: StateFlow<Map<String, Int>> = OfflineDownloadManager.downloadProgress

    val downloads: StateFlow<List<DownloadedItemEntity>> = dao.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val downloadedIds: StateFlow<Set<String>> = downloads
        .map { list -> list.map { it.id }.toSet() }
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

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    init {
        viewModelScope.launch {
            val apiKey = AuthRepository.resolveApiKey(appContext)
            MediaContentRepository.syncFromFirebaseEndpoint(
                databaseUrl = MediaContentRepository.DEFAULT_DATABASE_URL,
                apiKey = apiKey,
                projectId = MediaContentRepository.DEFAULT_PROJECT_ID
            )
        }
    }

    /**
     * Downloads a movie or series to the phone's internal storage (`offline_media`).
     * Strictly prevents downloading the same movie twice.
     */
    fun addDownload(media: MediaContent) {
        if (!media.downloadEnabled) return
        viewModelScope.launch {
            if (dao.isDownloaded(media.id) || OfflineDownloadManager.isCurrentlyDownloading(media.id)) {
                return@launch
            }
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
                streamUrl = media.streamUrl,
                genre = media.genre,
                duration = media.duration,
                rating = media.rating,
                fileSizeLabel = if (media.isSeries) "850 MB" else "680 MB"
            )
            OfflineDownloadManager.downloadMediaOffline(appContext, dao, item)
        }
    }

    /**
     * Downloads an episode to the phone's internal storage (`offline_media`).
     * Strictly prevents downloading the same episode twice.
     */
    fun addEpisodeDownload(episode: EpisodeItem, seriesTitle: String, seriesPoster: String) {
        if (!episode.downloadEnabled) return
        viewModelScope.launch {
            if (dao.isDownloaded(episode.id) || OfflineDownloadManager.isCurrentlyDownloading(episode.id)) {
                return@launch
            }
            val item = DownloadedItemEntity(
                id = episode.id,
                title = "$seriesTitle • S${episode.seasonNumber}E${episode.episodeNumber}: ${episode.name}",
                type = "series",
                posterUrl = episode.stillPath.ifBlank { seriesPoster },
                backdropUrl = episode.stillPath.ifBlank { seriesPoster },
                streamUrl = episode.streamUrl,
                genre = if (episode.narrated) "Series • ${episode.narrationLanguage}" else "Series",
                duration = episode.durationLabel,
                rating = "HD",
                fileSizeLabel = "420 MB"
            )
            OfflineDownloadManager.downloadMediaOffline(appContext, dao, item)
        }
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

    fun signUpUser(realName: String, email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            val result = AuthRepository.signUpWithEmailAndPassword(
                context = appContext,
                dao = dao,
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
            val result = AuthRepository.signInWithEmailAndPassword(
                context = appContext,
                dao = dao,
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
            dao.logoutAllUsers()
        }
    }

    fun clearAuthError() {
        _authError.value = null
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
