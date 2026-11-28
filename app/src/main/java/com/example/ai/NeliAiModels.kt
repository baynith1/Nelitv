package com.example.ai

import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.player.NetworkQualityMode
import com.example.ui.components.BottomNavTab

enum class NeliAiVolumeDirection {
    UP,
    DOWN,
    MUTE,
    UNMUTE
}

enum class NeliAiBrightnessDirection {
    UP,
    DOWN,
    MAX,
    LOW,
    AUTO
}

enum class NeliSystemSettingType {
    LOW_POWER,
    WIFI,
    BLUETOOTH,
    DO_NOT_DISTURB,
    AIRPLANE_MODE,
    DISPLAY,
    SOUND,
    GENERAL,
    SCREENSHOT_HELP,
    RESTART_HELP,
    HANG_UP_HELP
}

sealed interface NeliAiAction {
    data object None : NeliAiAction
    data object WakeGreeting : NeliAiAction
    data object WakeDeviceScreen : NeliAiAction
    data class PlayChannel(val channel: LiveChannel) : NeliAiAction
    data class LockedChannelNeedsPayment(val channel: LiveChannel) : NeliAiAction
    data class OpenMedia(val media: MediaContent, val autoPlay: Boolean) : NeliAiAction
    data class SuggestMovies(val movies: List<MediaContent>) : NeliAiAction
    data class ShowMovieInfo(val media: MediaContent) : NeliAiAction
    data class DownloadRandomMovies(
        val requestedCount: Int,
        val movies: List<MediaContent>,
        val genreFilter: String? = null,
        val swahiliOnly: Boolean = false
    ) : NeliAiAction
    data class DownloadSpecificMovies(val movies: List<MediaContent>) : NeliAiAction
    data object DeleteAllDownloads : NeliAiAction
    data class DeleteSpecificDownloads(
        val downloadIds: List<String>,
        val titles: List<String>
    ) : NeliAiAction
    data class PlayMoviesInSequence(val movies: List<MediaContent>) : NeliAiAction
    data object SwitchNextChannel : NeliAiAction
    data object SwitchPreviousChannel : NeliAiAction
    data class SetQuality(val mode: NetworkQualityMode) : NeliAiAction
    data class AdjustVolume(val direction: NeliAiVolumeDirection) : NeliAiAction
    data class SetVolumePercent(val percent: Int) : NeliAiAction
    data class AdjustBrightness(val direction: NeliAiBrightnessDirection) : NeliAiAction
    data class SetBrightnessPercent(val percent: Int) : NeliAiAction
    data object TogglePlayPause : NeliAiAction
    data object ClosePlayer : NeliAiAction
    data object LockDeviceScreen : NeliAiAction
    data object OpenNeliTvApp : NeliAiAction
    data class NavigateTab(val tab: BottomNavTab) : NeliAiAction
    data class FilterLiveCategory(val category: String) : NeliAiAction
    data class OpenSearch(val query: String) : NeliAiAction

    // Full Siri-style Mobile Assistant Actions (Neli AI)
    data class ToggleFlashlight(val enabled: Boolean) : NeliAiAction
    data class SetDarkMode(val enabled: Boolean) : NeliAiAction
    data class ToggleDoNotDisturb(val enabled: Boolean) : NeliAiAction
    data class OpenSystemSetting(val settingType: NeliSystemSettingType) : NeliAiAction
    data class SetAlarm(val hour24: Int, val minute: Int, val label: String = "Neli AI Alarm") : NeliAiAction
    data class ShowOrDismissAlarms(val deleteAll: Boolean = false) : NeliAiAction
    data class SetTimer(val durationSeconds: Int, val label: String = "Neli AI Timer") : NeliAiAction
    data class PlaceCall(
        val contactOrNumber: String,
        val onSpeaker: Boolean = false,
        val videoCall: Boolean = false
    ) : NeliAiAction
    data class SendMessage(val recipient: String, val body: String) : NeliAiAction
    data class OpenMessagingOrMailApp(val targetType: String) : NeliAiAction // "mail" or "sms"
    data class OpenMapsDirections(val destination: String, val mode: String = "d") : NeliAiAction
    data class FindPlacesNearby(val query: String) : NeliAiAction
    data class LaunchInstalledApp(val appQuery: String) : NeliAiAction
    data class LaunchVisualIntelligence(val prompt: String = "") : NeliAiAction
    data object FindMyPhone : NeliAiAction
    data class PlayMusicOrMedia(val query: String) : NeliAiAction
    data class WebSearch(val query: String) : NeliAiAction
    data class OpenVoiceDictation(val initialPrompt: String = "") : NeliAiAction
    data class CompositeActions(val actions: List<NeliAiAction>) : NeliAiAction
}

sealed interface NeliAiCardItem {
    val id: String
    val title: String
    val subtitle: String
    val imageUrl: String
    val badge: String

    data class ChannelCard(
        val channel: LiveChannel,
        override val badge: String = if (channel.isLiveBroadcast) "LIVE" else "TV"
    ) : NeliAiCardItem {
        override val id: String = "ch_${channel.id}"
        override val title: String = channel.name
        override val subtitle: String = channel.currentProgram.ifBlank {
            channel.category
        }
        override val imageUrl: String = channel.thumbnailUrl
    }

    data class MediaCard(
        val media: MediaContent,
        override val badge: String = when {
            media.isSwahiliNarratedMovie -> "TAFSIRI"
            media.isSeries -> "SERIES"
            else -> media.releaseYear.ifBlank { "MOVIE" }
        }
    ) : NeliAiCardItem {
        override val id: String = "vod_${media.id}"
        override val title: String = media.title
        override val subtitle: String = buildString {
            append(media.primaryGenre.ifBlank { media.genre })
            if (media.rating.isNotBlank()) {
                append(" • ★ ")
                append(media.rating)
            }
        }
        override val imageUrl: String = media.posterUrl.ifBlank { media.backdropUrl }
    }
}

data class NeliAiMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val replySpeech: String = text,
    val intentTag: String = "",
    val sourceTag: String = "",
    val confidence: Float = 1f,
    val cards: List<NeliAiCardItem> = emptyList(),
    val quickReplies: List<String> = emptyList(),
    val timestampMs: Long = System.currentTimeMillis()
)

data class NeliAiCommandResponse(
    val intent: String,
    val confidence: Float,
    val replyText: String,
    val replySpeech: String,
    val sourceTag: String,
    val action: NeliAiAction = NeliAiAction.None,
    val cards: List<NeliAiCardItem> = emptyList(),
    val quickReplies: List<String> = emptyList(),
    val expectsFollowUp: Boolean = false
)

data class NeliAiDownloadedItem(
    val id: String,
    val title: String,
    val type: String = "movie"
)

data class NeliAiContextSnapshot(
    val liveChannels: List<LiveChannel>,
    val mediaCatalog: List<MediaContent>,
    val activePlayerChannel: LiveChannel? = null,
    val isSmartTv: Boolean = false,
    val isPremiumActive: Boolean = false,
    val userName: String = "",
    val hourOfDay24: Int = 12,
    val isOnline: Boolean = true,
    val isOutsideApp: Boolean = false,
    val downloadedMediaIds: Set<String> = emptySet(),
    val downloadedItems: List<NeliAiDownloadedItem> = emptyList(),
    val watchedWithin30DaysIds: Set<String> = emptySet(),
    val isChannelLockedCheck: (LiveChannel) -> Boolean = { false }
)

sealed interface NeliAiVoiceState {
    data object Idle : NeliAiVoiceState
    data class Listening(val rmsDb: Float = 0f, val partialText: String = "") : NeliAiVoiceState
    data object Thinking : NeliAiVoiceState
    data class Speaking(val text: String, val beatAmplitude: Float = 0.6f) : NeliAiVoiceState
    data object HandsFreeStandby : NeliAiVoiceState
    data class Notice(val message: String) : NeliAiVoiceState
}

data class NeliAiBrainSnapshot(
    val liveChannelsCount: Int,
    val moviesCount: Int,
    val seriesCount: Int,
    val learnedAliases: Map<String, String>,
    val topWatchedChannels: List<Pair<String, Int>>,
    val totalInteractions: Int,
    val remoteEndpointUrl: String,
    val hasApiKeyConfigured: Boolean = false,
    val isHandsFreeEnabled: Boolean,
    val isVoiceReplyEnabled: Boolean,
    val isOverlayBallEnabled: Boolean = false,
    val isLockScreenWakeEnabled: Boolean = false,
    val isVoiceCancellationEnabled: Boolean = true,
    val isCaptionsEnabled: Boolean = true,
    val isPowerButtonWakeEnabled: Boolean = true,
    val totalCommandsSupported: Int = NeliAiCommandCatalog.totalCommandCount
)
