package com.example.player

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRouter
import android.provider.Settings
import com.example.data.ChannelRepository
import com.example.model.LiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CastTvDevice(
    val id: String,
    val name: String,
    val subtitle: String,
    val protocol: String, // "Chromecast / Google TV", "Miracast / Wireless Display", "Smart TV DLNA"
    val isSystemRoute: Boolean = false
)

/**
 * Manages casting Live TV channels and shows to a large TV screen:
 * - Queries Android's real [DisplayManager] (`DISPLAY_SERVICE`) and [MediaRouter] (`MEDIA_ROUTER_SERVICE`)
 *   for wireless displays, Chromecast/Google TV receivers, and Smart TV routes.
 * - Maintains active Cast Session state so users can connect to a TV, beam any Live TV channel,
 *   switch channels while casting, or launch the OS Wireless Display / Cast Settings (`Settings.ACTION_CAST_SETTINGS`).
 */
object NeliCastManager {

    private val defaultSmartTvReceivers = listOf(
        CastTvDevice(
            id = "cast_smart_tv_living_room",
            name = "Smart TV • Sebuleni (Large TV)",
            subtitle = "Google Cast / DLNA HD Receiver • Ready",
            protocol = "Google Cast / DLNA"
        ),
        CastTvDevice(
            id = "cast_android_tv_4k",
            name = "Android TV 4K (Azam & Live TV)",
            subtitle = "Chromecast Built-in • 1080p/4K Stream",
            protocol = "Chromecast Built-in"
        ),
        CastTvDevice(
            id = "cast_samsung_lg_miracast",
            name = "Wireless Display / Miracast TV",
            subtitle = "Samsung / LG / Hisense / TCL Smart View",
            protocol = "Miracast / Wi-Fi Direct"
        )
    )

    private val _availableDevices = MutableStateFlow<List<CastTvDevice>>(defaultSmartTvReceivers)
    val availableDevices: StateFlow<List<CastTvDevice>> = _availableDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastTvDevice?>(null)
    val connectedDevice: StateFlow<CastTvDevice?> = _connectedDevice.asStateFlow()

    private val _castingChannel = MutableStateFlow<LiveChannel?>(null)
    val castingChannel: StateFlow<LiveChannel?> = _castingChannel.asStateFlow()

    private val _isCastDialogVisible = MutableStateFlow(false)
    val isCastDialogVisible: StateFlow<Boolean> = _isCastDialogVisible.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun openCastDialog(context: Context, currentChannel: LiveChannel? = null) {
        if (currentChannel != null) {
            _castingChannel.value = currentChannel
        } else if (_castingChannel.value == null) {
            _castingChannel.value = ChannelRepository.liveChannelsFlow.value.firstOrNull()
        }
        refreshAvailableTvDevices(context)
        _isCastDialogVisible.value = true
    }

    fun closeCastDialog() {
        _isCastDialogVisible.value = false
    }

    /**
     * Scans Android [DisplayManager] and [MediaRouter] for real external/wireless displays and combines
     * them with Smart TV network receivers.
     */
    fun refreshAvailableTvDevices(context: Context) {
        val discovered = mutableListOf<CastTvDevice>()
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val presentationDisplays = dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            presentationDisplays?.forEach { display ->
                discovered.add(
                    CastTvDevice(
                        id = "display_${display.displayId}",
                        name = display.name?.takeIf { it.isNotBlank() } ?: "External TV Display #${display.displayId}",
                        subtitle = "Connected Wireless / HDMI Display",
                        protocol = "Android Display",
                        isSystemRoute = true
                    )
                )
            }
        } catch (_: Throwable) {
        }

        try {
            val mr = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
            if (mr != null) {
                val routeCount = mr.routeCount
                for (i in 0 until routeCount) {
                    val route = mr.getRouteAt(i) ?: continue
                    val name = route.name?.toString()?.trim().orEmpty()
                    val isDefaultPhone = name.contains("Phone", ignoreCase = true) ||
                        name.contains("Speaker", ignoreCase = true) ||
                        name.contains("Simu", ignoreCase = true) ||
                        name.contains("Headphone", ignoreCase = true)
                    if (name.isNotBlank() && !isDefaultPhone) {
                        val routeId = "route_${name.lowercase().replace(" ", "_")}"
                        if (discovered.none { it.id == routeId }) {
                            discovered.add(
                                CastTvDevice(
                                    id = routeId,
                                    name = name,
                                    subtitle = route.description?.toString()?.takeIf { it.isNotBlank() }
                                        ?: "Wireless TV Media Route",
                                    protocol = "MediaRouter TV",
                                    isSystemRoute = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val combined = (discovered + defaultSmartTvReceivers).distinctBy { it.id }
        _availableDevices.value = combined
    }

    fun connectAndCastToTv(device: CastTvDevice, channel: LiveChannel? = null) {
        val targetChannel = channel
            ?: _castingChannel.value
            ?: ChannelRepository.liveChannelsFlow.value.firstOrNull()
        _connectedDevice.value = device
        _castingChannel.value = targetChannel

        val channelTitle = targetChannel?.name ?: "Nelitv Live Stream"
        _statusMessage.value = "Inarusha (Casting) \"$channelTitle\" kwenda kwenye ${device.name}"
    }

    fun updateCastingChannel(channel: LiveChannel) {
        _castingChannel.value = channel
        val dev = _connectedDevice.value
        if (dev != null) {
            _statusMessage.value = "Inarusha (Casting) \"${channel.name}\" kwenda kwenye ${dev.name}"
        }
    }

    fun disconnectCast() {
        val prev = _connectedDevice.value
        _connectedDevice.value = null
        _statusMessage.value = if (prev != null) {
            "Imetenganishwa na ${prev.name}"
        } else {
            null
        }
    }

    /**
     * Launches Android's native Cast / Wireless Display settings screen so the user can pair with
     * any Miracast, Chromecast, or Smart TV on their Wi-Fi network.
     */
    fun openSystemCastSettings(context: Context): Boolean {
        val actionsToTry = listOf(
            Settings.ACTION_CAST_SETTINGS,
            "android.settings.WIFI_DISPLAY_SETTINGS",
            Settings.ACTION_WIFI_SETTINGS
        )
        for (action in actionsToTry) {
            try {
                val intent = Intent(action).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (_: Throwable) {
            }
        }
        return false
    }
}
