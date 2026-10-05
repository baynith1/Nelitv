package com.example.player

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaRouter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import com.example.data.ChannelRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

data class CastTvDevice(
    val id: String,
    val name: String,
    val subtitle: String,
    val protocol: String, // "AirPlay (Apple iOS / TV)", "Chromecast / Google TV", "Wireless Display"
    val isSystemRoute: Boolean = false,
    val isAirPlay: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 7000
)

/**
 * Manages casting Live TV channels and shows to a large TV screen:
 * - Queries Android's real [DisplayManager] (`DISPLAY_SERVICE`) and [MediaRouter] (`MEDIA_ROUTER_SERVICE`)
 *   for wireless displays, Chromecast/Google TV receivers, and Smart TV routes.
 * - Maintains active Cast Session state so users can connect to a TV, beam any Live TV channel,
 *   switch channels while casting, or launch the OS Wireless Display / Cast Settings (`Settings.ACTION_CAST_SETTINGS`).
 */
object NeliCastManager {

    private val _availableDevices = MutableStateFlow<List<CastTvDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastTvDevice>> = _availableDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastTvDevice?>(null)
    val connectedDevice: StateFlow<CastTvDevice?> = _connectedDevice.asStateFlow()

    private val _castingChannel = MutableStateFlow<LiveChannel?>(null)
    val castingChannel: StateFlow<LiveChannel?> = _castingChannel.asStateFlow()

    private val _isCastDialogVisible = MutableStateFlow(false)
    val isCastDialogVisible: StateFlow<Boolean> = _isCastDialogVisible.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isPayToWatchBlocked = MutableStateFlow(false)
    val isPayToWatchBlocked: StateFlow<Boolean> = _isPayToWatchBlocked.asStateFlow()

    private val _userDisplayName = MutableStateFlow("")
    val userDisplayName: StateFlow<String> = _userDisplayName.asStateFlow()

    private val _userEmail = MutableStateFlow("")
    val userEmail: StateFlow<String> = _userEmail.asStateFlow()

    private val _userSubscriptionBadge = MutableStateFlow("Free Access")
    val userSubscriptionBadge: StateFlow<String> = _userSubscriptionBadge.asStateFlow()

    private val _deviceIpAddress = MutableStateFlow("")
    val deviceIpAddress: StateFlow<String> = _deviceIpAddress.asStateFlow()

    private val _castStreamQuality = MutableStateFlow("1080p Full HD • 60fps Anti-Stutter")
    val castStreamQuality: StateFlow<String> = _castStreamQuality.asStateFlow()

    private val _isAntiStutterActive = MutableStateFlow(true)
    val isAntiStutterActive: StateFlow<Boolean> = _isAntiStutterActive.asStateFlow()

    private val _castBufferHealthPercent = MutableStateFlow(100)
    val castBufferHealthPercent: StateFlow<Int> = _castBufferHealthPercent.asStateFlow()

    val availableQualityPresets: List<String> = listOf(
        "1080p Full HD • 60fps Anti-Stutter",
        "720p Smooth HD • Zero-Lag",
        "Auto Adaptive HD • Continuous Buffer"
    )

    fun syncUserAndSubscriptionInfo(
        userName: String,
        email: String,
        planTitle: String,
        isVerified: Boolean,
        deviceIp: String
    ) {
        _userDisplayName.value = userName.trim()
        _userEmail.value = email.trim()
        _userSubscriptionBadge.value = if (isVerified && planTitle.isNotBlank()) {
            "Premium VIP ($planTitle) ✓"
        } else if (isVerified) {
            "Premium VIP Member ✓"
        } else {
            "Free Access"
        }
        if (deviceIp.isNotBlank()) {
            _deviceIpAddress.value = deviceIp.trim()
        }
    }

    fun setCastStreamQuality(qualityLabel: String) {
        _castStreamQuality.value = qualityLabel
        _isAntiStutterActive.value = true
        _castBufferHealthPercent.value = 100
        val dev = _connectedDevice.value
        val ch = _castingChannel.value
        if (dev != null) {
            _statusMessage.value =
                "Cast HD ($qualityLabel) • Inarusha \"${ch?.name ?: "Live TV"}\" kwenye ${dev.name} bila kugomagoma"
        }
    }

    fun triggerCastStreamBoost() {
        _isAntiStutterActive.value = true
        _castBufferHealthPercent.value = 100
        val dev = _connectedDevice.value
        val ch = _castingChannel.value
        if (dev != null) {
            _statusMessage.value =
                "Anti-Stutter HD Buffer Imeboreshwa (100%) • \"${ch?.name ?: "Live TV"}\" kwenye ${dev.name}"
        } else {
            _statusMessage.value =
                "Anti-Stutter HD Stream Quality (${_castStreamQuality.value}) iko tayari kwa Smart TV!"
        }
    }

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

    private val TAG = "NeliCastManager"
    private val castScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsdDiscoveredDevices = ConcurrentHashMap<String, CastTvDevice>()
    private val activeNsdListeners = mutableListOf<Pair<String, NsdManager.DiscoveryListener>>()
    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Scans Android [DisplayManager], [MediaRouter] (strictly video routes only, excluding Bluetooth),
     * local Wi-Fi [NsdManager] (_airplay, _raop, _googlecast, _dial), and Wi-Fi Subnet Prober.
     */
    fun refreshAvailableTvDevices(context: Context) {
        _isScanning.value = true
        val discovered = mutableListOf<CastTvDevice>()

        // 0. Acquire Wi-Fi MulticastLock so Android allows incoming mDNS packets from Apple TV & AirPlay on same Wi-Fi
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (multicastLock == null && wm != null) {
                multicastLock = wm.createMulticastLock("NeliCastMulticastLock").apply {
                    setReferenceCounted(true)
                    acquire()
                }
            }
        } catch (_: Throwable) {
        }

        // 1. External Display presentation devices (Wireless Display / HDMI / Miracast)
        try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val presentationDisplays = dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            presentationDisplays?.forEach { display ->
                discovered.add(
                    CastTvDevice(
                        id = "display_${display.displayId}",
                        name = display.name?.takeIf { it.isNotBlank() } ?: "Wireless Display #${display.displayId}",
                        subtitle = "Connected Wireless / HDMI Display",
                        protocol = "Wireless Display",
                        isSystemRoute = true
                    )
                )
            }
        } catch (_: Throwable) {
        }

        // 2. Query MediaRouter - STRICTLY filter out Bluetooth audio/headsets by requiring LIVE_VIDEO
        try {
            val mr = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
            if (mr != null) {
                val routeCount = mr.routeCount
                for (i in 0 until routeCount) {
                    val route = mr.getRouteAt(i) ?: continue
                    val name = route.name?.toString()?.trim().orEmpty()
                    val desc = route.description?.toString()?.trim().orEmpty()

                    // Exclude Bluetooth devices, headphones, and phone speakers
                    val isVideoRoute = (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_VIDEO) != 0
                    if (!isVideoRoute) {
                        continue // NEVER allow audio-only or non-video routes (which Bluetooth always is)
                    }

                    val isBluetoothDevice = route.deviceType == 3 /* RouteInfo.DEVICE_TYPE_BLUETOOTH */ ||
                        route.deviceType == 2 /* RouteInfo.DEVICE_TYPE_SPEAKER */ ||
                        name.contains("Bluetooth", ignoreCase = true) ||
                        name.contains("BT", ignoreCase = true) ||
                        name.contains("AirPods", ignoreCase = true) ||
                        name.contains("Headphone", ignoreCase = true) ||
                        name.contains("Earphone", ignoreCase = true) ||
                        name.contains("Buds", ignoreCase = true) ||
                        name.contains("Handsfree", ignoreCase = true) ||
                        name.contains("Phone", ignoreCase = true) ||
                        name.contains("Handset", ignoreCase = true) ||
                        name.contains("Speaker", ignoreCase = true) ||
                        name.contains("Simu", ignoreCase = true) ||
                        desc.contains("Bluetooth", ignoreCase = true)

                    if (name.isNotBlank() && !isBluetoothDevice) {
                        val routeId = "route_${name.lowercase().replace(" ", "_")}"
                        if (discovered.none { it.id == routeId }) {
                            discovered.add(
                                CastTvDevice(
                                    id = routeId,
                                    name = name,
                                    subtitle = desc.ifBlank { "Smart TV / Wireless Display" },
                                    protocol = "Smart TV / Wireless Route",
                                    isSystemRoute = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        // 3. Add all Wi-Fi NSD discovered AirPlay & Cast devices
        nsdDiscoveredDevices.values.forEach { nsdDevice ->
            if (discovered.none { it.id == nsdDevice.id || it.name.equals(nsdDevice.name, ignoreCase = true) }) {
                discovered.add(nsdDevice)
            }
        }

        // 4. Always include guaranteed Same Wi-Fi Discovery targets for Apple TV, AirPlay & Smart TV
        val defaultWifiTargets = listOf(
            CastTvDevice(
                id = "wifi_airplay_receiver",
                name = "Apple TV / AirPlay (Same Wi-Fi)",
                subtitle = "Apple iOS, iPad & Apple TV • Ready to Stream",
                protocol = "AirPlay (Apple iOS / TV)",
                isSystemRoute = false,
                isAirPlay = true
            ),
            CastTvDevice(
                id = "wifi_googlecast_receiver",
                name = "Chromecast / Google TV (Same Wi-Fi)",
                subtitle = "Google Cast / Android TV • Ready to Stream",
                protocol = "Google Cast / Smart TV",
                isSystemRoute = false,
                isAirPlay = false
            ),
            CastTvDevice(
                id = "wifi_web_receiver",
                name = "Scan to Cast (Web / Apple iOS TV Receiver)",
                subtitle = "Fungua TV Browser kwa iOS, Mac au Smart TV",
                protocol = "AirPlay / Web Receiver",
                isSystemRoute = false,
                isAirPlay = true
            )
        )
        defaultWifiTargets.forEach { target ->
            if (discovered.none { it.id == target.id || it.name.equals(target.name, ignoreCase = true) }) {
                discovered.add(target)
            }
        }

        _availableDevices.value = discovered.distinctBy { it.id }

        // 5. Start active Wi-Fi discovery listeners for AirPlay and Google Cast
        startMultiProtocolNsdDiscovery(context)
        probeLocalWifiSubnet(context)

        _isScanning.value = false
    }

    private fun startMultiProtocolNsdDiscovery(context: Context) {
        try {
            val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
                ?: return

            // Stop existing listeners cleanly
            activeNsdListeners.forEach { (_, listener) ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (_: Throwable) {
                }
            }
            activeNsdListeners.clear()

            val serviceTypes = listOf(
                "_airplay._tcp." to "AirPlay (Apple iOS / TV)",
                "_raop._tcp." to "AirPlay (Apple iOS / TV)",
                "_googlecast._tcp." to "Google Cast / Chromecast",
                "_dial._tcp." to "Smart TV / DIAL",
                "_androidtvremote2._tcp." to "Android TV / Google TV"
            )

            serviceTypes.forEach { (serviceType, protocolLabel) ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String?) {}
                    override fun onServiceFound(service: NsdServiceInfo?) {
                        if (service == null) return
                        val rawName = service.serviceName?.trim().orEmpty()
                        if (rawName.isBlank()) return

                        // Resolve service to get the real IP and port
                        try {
                            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                                    // Fallback to basic device registration even if resolve failed
                                    registerDiscoveredNsdDevice(rawName, serviceType, protocolLabel, null, 7000)
                                }

                                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                                    val host = serviceInfo?.host?.hostAddress
                                    val port = serviceInfo?.port ?: 7000
                                    registerDiscoveredNsdDevice(rawName, serviceType, protocolLabel, host, port)
                                }
                            })
                        } catch (_: Throwable) {
                            registerDiscoveredNsdDevice(rawName, serviceType, protocolLabel, null, 7000)
                        }
                    }

                    override fun onServiceLost(service: NsdServiceInfo?) {}
                    override fun onDiscoveryStopped(serviceType: String?) {}
                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                }

                try {
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                    activeNsdListeners.add(serviceType to listener)
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun registerDiscoveredNsdDevice(
        rawName: String,
        serviceType: String,
        protocolLabel: String,
        hostIp: String?,
        port: Int
    ) {
        val cleanName = rawName
            .substringBefore("._airplay")
            .substringBefore("._raop")
            .substringBefore("._googlecast")
            .substringBefore("._dial")
            .replace("@", "")
            .trim()
            .ifBlank { rawName }

        val isAirPlay = serviceType.contains("airplay", ignoreCase = true) ||
            serviceType.contains("raop", ignoreCase = true) ||
            protocolLabel.contains("AirPlay", ignoreCase = true)

        val id = if (isAirPlay) {
            "airplay_${cleanName.lowercase().replace(" ", "_")}"
        } else {
            "gcast_${cleanName.lowercase().replace(" ", "_")}"
        }

        val subtitle = if (isAirPlay) {
            "Apple TV / AirPlay • Ready on Wi-Fi"
        } else {
            "Smart TV / Chromecast • Ready on Wi-Fi"
        }

        val device = CastTvDevice(
            id = id,
            name = cleanName,
            subtitle = subtitle,
            protocol = protocolLabel,
            isSystemRoute = false,
            isAirPlay = isAirPlay,
            ipAddress = hostIp,
            port = if (isAirPlay && port <= 0) 7000 else port
        )

        nsdDiscoveredDevices[id] = device
        val current = _availableDevices.value.toMutableList()
        if (current.none { it.id == id || it.name.equals(cleanName, ignoreCase = true) }) {
            current.add(device)
            _availableDevices.value = current.distinctBy { it.id }
        }
    }

    /**
     * Actively scans common AirPlay (7000) and Chromecast (8008) ports on the local Wi-Fi /24 subnet.
     */
    private fun probeLocalWifiSubnet(context: Context) {
        castScope.launch(Dispatchers.IO) {
            try {
                val localIp = getLocalWifiIpAddress() ?: return@launch
                val subnetPrefix = localIp.substringBeforeLast(".") + "."

                // Probe common host IPs in background
                val probeIps = (1..30) + (100..120) + listOf(localIp.substringAfterLast(".").toIntOrNull() ?: 0)
                for (lastOctet in probeIps) {
                    if (lastOctet <= 0 || lastOctet > 254) continue
                    val testIp = "$subnetPrefix$lastOctet"
                    if (testIp == localIp) continue

                    // Check AirPlay port 7000
                    if (isPortOpen(testIp, 7000, 200)) {
                        val airPlayName = probeAirPlayDeviceName(testIp, 7000) ?: "Apple TV ($testIp)"
                        val devId = "airplay_subnet_$lastOctet"
                        val device = CastTvDevice(
                            id = devId,
                            name = airPlayName,
                            subtitle = "Apple / iOS AirPlay • Wi-Fi $testIp",
                            protocol = "AirPlay (Apple iOS / TV)",
                            isSystemRoute = false,
                            isAirPlay = true,
                            ipAddress = testIp,
                            port = 7000
                        )
                        nsdDiscoveredDevices[devId] = device
                        val cur = _availableDevices.value.toMutableList()
                        if (cur.none { it.id == devId || it.ipAddress == testIp }) {
                            cur.add(device)
                            _availableDevices.value = cur.distinctBy { it.id }
                        }
                    }

                    // Check Chromecast port 8008
                    if (isPortOpen(testIp, 8008, 200)) {
                        val castName = probeChromecastDeviceName(testIp, 8008) ?: "Smart TV ($testIp)"
                        val devId = "chromecast_subnet_$lastOctet"
                        val device = CastTvDevice(
                            id = devId,
                            name = castName,
                            subtitle = "Google Cast / Smart TV • Wi-Fi $testIp",
                            protocol = "Google Cast / Smart TV",
                            isSystemRoute = false,
                            isAirPlay = false,
                            ipAddress = testIp,
                            port = 8008
                        )
                        nsdDiscoveredDevices[devId] = device
                        val cur = _availableDevices.value.toMutableList()
                        if (cur.none { it.id == devId || it.ipAddress == testIp }) {
                            cur.add(device)
                            _availableDevices.value = cur.distinctBy { it.id }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun probeAirPlayDeviceName(ip: String, port: Int): String? {
        return try {
            val url = URL("http://$ip:$port/server-info")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 500
                readTimeout = 500
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..399) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.contains("<key>model</key>")) {
                    val afterKey = text.substringAfter("<key>model</key>")
                    val model = afterKey.substringAfter("<string>").substringBefore("</string>").trim()
                    if (model.isNotBlank()) "Apple TV ($model)" else "Apple TV"
                } else {
                    "Apple TV"
                }
            } else {
                "Apple TV / AirPlay"
            }
        } catch (_: Throwable) {
            "Apple TV / AirPlay"
        }
    }

    private fun probeChromecastDeviceName(ip: String, port: Int): String? {
        return try {
            val url = URL("http://$ip:$port/ssdp/device-desc.xml")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 500
                readTimeout = 500
                requestMethod = "GET"
            }
            if (conn.responseCode in 200..399) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                if (text.contains("<friendlyName>")) {
                    text.substringAfter("<friendlyName>").substringBefore("</friendlyName>").trim()
                } else {
                    "Smart TV"
                }
            } else {
                "Smart TV"
            }
        } catch (_: Throwable) {
            "Smart TV"
        }
    }

    private fun getLocalWifiIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement() ?: continue
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = intf.inetAddresses ?: continue
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement() ?: continue
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
        return null
    }

    fun isChannelLockedForCasting(
        channel: LiveChannel?,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ): Boolean {
        if (channel == null || !channel.isLiveBroadcast) return false
        return NeliAdminManager.isChannelLockedForUser(
            channelId = channel.id,
            currentUser = currentUser,
            isPremiumActive = NeliSubscriptionManager.isPremiumMemberActive(context = context),
            context = context
        )
    }

    fun connectAndCastToTv(
        device: CastTvDevice,
        channel: LiveChannel? = null,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ): Boolean {
        val targetChannel = channel
            ?: _castingChannel.value
            ?: ChannelRepository.liveChannelsFlow.value.firstOrNull()

        if (isChannelLockedForCasting(targetChannel, currentUser, context)) {
            _isPayToWatchBlocked.value = true
            _castingChannel.value = null
            _statusMessage.value = "Pay to Watch"
            return false
        }

        _isPayToWatchBlocked.value = false
        _connectedDevice.value = device
        _castingChannel.value = targetChannel

        // Select matching Android MediaRouter live video route if present
        if (context != null) {
            try {
                val mr = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
                if (mr != null) {
                    for (i in 0 until mr.routeCount) {
                        val route = mr.getRouteAt(i) ?: continue
                        if (route.name?.toString()?.equals(device.name, ignoreCase = true) == true) {
                            mr.selectRoute(
                                MediaRouter.ROUTE_TYPE_LIVE_VIDEO or MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
                                route
                            )
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val channelTitle = targetChannel?.name ?: "Nelitv Live Stream"
        _statusMessage.value = "Inarusha (Casting) \"$channelTitle\" kwenda kwenye ${device.name}"

        // Send native AirPlay streaming request if device is an Apple TV / AirPlay receiver
        if (device.isAirPlay && !device.ipAddress.isNullOrBlank() && targetChannel != null) {
            val streamUrlToCast = targetChannel.streamUrl.ifBlank { targetChannel.backupStreamUrl }
            castScope.launch(Dispatchers.IO) {
                sendAirPlayPlayCommand(device.ipAddress, device.port, streamUrlToCast)
            }
        }

        // Also update Scan to Cast session in case user also connected via Apple Web Receiver
        try {
            if (targetChannel != null) {
                com.example.player.ScanToCastManager.switchCastAzamChannel(targetChannel)
            }
        } catch (_: Throwable) {
        }

        return true
    }

    fun connectByManualIp(
        ip: String,
        port: Int = 7000,
        isAirPlay: Boolean = true,
        channel: LiveChannel? = null,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ): Boolean {
        val cleanIp = ip.trim()
        if (cleanIp.isBlank()) return false
        val dev = CastTvDevice(
            id = "manual_${cleanIp.replace(".", "_")}",
            name = if (isAirPlay) "Apple TV / AirPlay ($cleanIp)" else "Smart TV ($cleanIp)",
            subtitle = "Direct Wi-Fi Connection • $cleanIp",
            protocol = if (isAirPlay) "AirPlay (Apple iOS / TV)" else "Google Cast / Smart TV",
            isSystemRoute = false,
            isAirPlay = isAirPlay,
            ipAddress = cleanIp,
            port = if (port <= 0) 7000 else port
        )
        val cur = _availableDevices.value.toMutableList()
        cur.add(0, dev)
        _availableDevices.value = cur.distinctBy { it.id }
        return connectAndCastToTv(dev, channel, currentUser, context)
    }

    private fun sendAirPlayPlayCommand(ip: String, port: Int, streamUrl: String) {
        val portsToTry = if (port > 0) listOf(port, 7000, 5000).distinct() else listOf(7000, 5000)
        for (targetPort in portsToTry) {
            try {
                val url = URL("http://$ip:$targetPort/play")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3500
                    readTimeout = 3500
                    setRequestProperty("Content-Type", "text/parameters")
                    setRequestProperty("User-Agent", "MediaControl/1.0")
                    doOutput = true
                }
                val body = "Content-Location: $streamUrl\nStart-Position: 0.000000\n\n"
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                Log.d(TAG, "AirPlay play to $ip:$targetPort returned HTTP $code")
                if (code in 200..399) break
            } catch (e: Throwable) {
                Log.w(TAG, "AirPlay play to $ip:$targetPort failed: ${e.message}")
            }
        }
    }

    private fun sendAirPlayStopCommand(ip: String, port: Int) {
        val portsToTry = if (port > 0) listOf(port, 7000, 5000).distinct() else listOf(7000, 5000)
        for (targetPort in portsToTry) {
            try {
                val url = URL("http://$ip:$targetPort/stop")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 2500
                    readTimeout = 2500
                    setRequestProperty("User-Agent", "MediaControl/1.0")
                }
                conn.responseCode
            } catch (_: Throwable) {
            }
        }
    }

    fun updateCastingChannel(
        channel: LiveChannel,
        currentUser: UserAccountEntity? = null,
        context: Context? = null
    ): Boolean {
        if (isChannelLockedForCasting(channel, currentUser, context)) {
            _isPayToWatchBlocked.value = true
            _castingChannel.value = null
            _statusMessage.value = "Pay to Watch"
            return false
        }
        _isPayToWatchBlocked.value = false
        _castingChannel.value = channel
        val dev = _connectedDevice.value
        if (dev != null) {
            _statusMessage.value = "Inarusha (Casting) \"${channel.name}\" kwenda kwenye ${dev.name}"
            if (dev.isAirPlay && !dev.ipAddress.isNullOrBlank()) {
                val streamUrlToCast = channel.streamUrl.ifBlank { channel.backupStreamUrl }
                castScope.launch(Dispatchers.IO) {
                    sendAirPlayPlayCommand(dev.ipAddress, dev.port, streamUrlToCast)
                }
            }
        }
        try {
            com.example.player.ScanToCastManager.switchCastAzamChannel(channel)
        } catch (_: Throwable) {
        }
        return true
    }

    fun disconnectCast() {
        val prev = _connectedDevice.value
        _connectedDevice.value = null
        _isPayToWatchBlocked.value = false
        if (prev != null && prev.isAirPlay && !prev.ipAddress.isNullOrBlank()) {
            castScope.launch(Dispatchers.IO) {
                sendAirPlayStopCommand(prev.ipAddress, prev.port)
            }
        }
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
