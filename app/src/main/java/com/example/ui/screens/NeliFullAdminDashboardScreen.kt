package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FreeForeverAccountStatus
import com.example.data.NeliAdminManager
import com.example.data.NeliFreeForeverAccountsManager
import com.example.data.NeliRealtimeAdminSnapshot
import com.example.data.NeliRealtimeAnalyticsManager
import com.example.data.local.UserAccountEntity
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.rememberNeliScreenProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class FullAdminSectionTab(val id: String, val label: String) {
    ALL("all", "Zote (Live Overview)"),
    USERS("users", "Watumiaji & VIP"),
    LOCATIONS("locations", "Mitaa & Mikoa (TZ)"),
    WATCHING("watching", "Wanaotazama Sasa"),
    HARAKAPAY("harakapay", "Mapato HarakaPay"),
    FREE_FOREVER("free_forever", "5 Free Forever VIP")
}

/**
 * FULL REAL-TIME ADMIN PANEL SCREEN (Separate from Mini Admin Panel):
 * Displays real-time telemetry across Tanzania:
 * - Total App Users, Current Online Users, Registered Users, Premium Users, Total Watching Now
 * - Locations across Tanzania (Cities, Districts & Exact Streets / Mitaa)
 * - Total Income from HarakaPay, Live Wallet Balance, Plan Revenue Breakdown, Mobile Money Networks & Transactions
 * - 5 Free Forever VIP Accounts (`user1@login.com` .. `user5@login.com`, Password `Free123`, Max 2 Devices) monitor & controls
 * - Live Channel Viewership, Active Viewer Sessions, Device Brands & Platform Health
 */
@Composable
fun FullAdminDashboardScreen(
    currentUser: UserAccountEntity?,
    onBack: () -> Unit,
    onSwitchToMiniAdminPanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val screenProfile = rememberNeliScreenProfile()

    BackHandler(onBack = onBack)

    val isAuthorizedAdmin = NeliAdminManager.isAdminUser(currentUser)
    val snapshot by NeliRealtimeAnalyticsManager.snapshot.collectAsState()
    val freeForeverAccounts by NeliFreeForeverAccountsManager.accountsStatusFlow.collectAsState()

    var selectedSectionId by rememberSaveable { mutableStateOf(FullAdminSectionTab.ALL.id) }
    val selectedSection = remember(selectedSectionId) {
        FullAdminSectionTab.entries.find { it.id == selectedSectionId } ?: FullAdminSectionTab.ALL
    }

    var locationSearchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCityFilter by rememberSaveable { mutableStateOf("Zote") }
    var userSearchQuery by rememberSaveable { mutableStateOf("") }
    var isRefreshingManual by remember { mutableStateOf(false) }
    var adminActionBanner by remember { mutableStateOf<String?>(null) }

    // Continuous real-time refresh every 5 seconds while Admin Panel is open
    LaunchedEffect(isAuthorizedAdmin) {
        if (!isAuthorizedAdmin) return@LaunchedEffect
        NeliFreeForeverAccountsManager.initialize(context)
        NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = true)
        while (isActive) {
            delay(5000L)
            NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = false)
        }
    }

    if (!isAuthorizedAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NeliBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = NeliLiveRed,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Ukurasa huu ni wa Admin pekee (Admin@login.com)",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onBack) {
                    Text("Rudi Nyuma")
                }
            }
        }
        return
    }

    val citiesList = remember(snapshot.tanzaniaLocations) {
        listOf("Zote") + snapshot.tanzaniaLocations.map { it.city }.distinct()
    }

    val filteredLocations = remember(snapshot.tanzaniaLocations, selectedCityFilter, locationSearchQuery) {
        snapshot.tanzaniaLocations.filter { loc ->
            val matchesCity = selectedCityFilter == "Zote" || loc.city.equals(selectedCityFilter, ignoreCase = true)
            val q = locationSearchQuery.trim()
            val matchesSearch = q.isBlank() ||
                loc.city.contains(q, ignoreCase = true) ||
                loc.district.contains(q, ignoreCase = true) ||
                loc.street.contains(q, ignoreCase = true) ||
                loc.topWatchedChannel.contains(q, ignoreCase = true)
            matchesCity && matchesSearch
        }
    }

    val filteredUsers = remember(snapshot.registeredUsersList, userSearchQuery) {
        val q = userSearchQuery.trim()
        if (q.isBlank()) {
            snapshot.registeredUsersList
        } else {
            snapshot.registeredUsersList.filter { u ->
                u.fullName.contains(q, ignoreCase = true) ||
                    u.email.contains(q, ignoreCase = true) ||
                    u.city.contains(q, ignoreCase = true) ||
                    u.street.contains(q, ignoreCase = true) ||
                    u.accountType.contains(q, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .testTag("full_realtime_admin_panel_screen"),
        contentPadding = PaddingValues(
            horizontal = screenProfile.horizontalPadding,
            vertical = 14.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Header Bar with Live Indicator & Switch to Mini Admin Panel
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0F172A),
                                Color(0xFF1E1B4B),
                                Color(0xFF311042)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("full_admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Rudi Nyuma",
                            tint = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Text(
                                text = "REAL-TIME ADMIN COMMAND CENTER",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.7.sp
                            )
                        }
                        Text(
                            text = "Admin Panel Kuu • Live Data",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "Imesasishwa: ${snapshot.formattedLastUpdated} • Tanzania Live Telemetry",
                            color = NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            if (!isRefreshingManual) {
                                isRefreshingManual = true
                                coroutineScope.launch {
                                    NeliFreeForeverAccountsManager.syncAllAccountsFromCloud(context)
                                    NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = true)
                                    isRefreshingManual = false
                                    adminActionBanner = "Data zote za Real-Time zimesasishwa sasa hivi!"
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7))
                            .testTag("full_admin_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Live Data",
                            tint = Color.White
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onSwitchToMiniAdminPanel,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("switch_to_mini_admin_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fungua Mini Admin Panel (Channels & SMS)",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!adminActionBanner.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF064E3B))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = adminActionBanner!!,
                            color = Color(0xFFA7F3D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { adminActionBanner = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Funga",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Section Filter Tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("full_admin_section_tabs"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FullAdminSectionTab.entries.forEach { tab ->
                    val selected = tab.id == selectedSection.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) NeliMagenta else NeliSurfaceVariant
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) Color.White.copy(alpha = 0.4f) else NeliBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedSectionId = tab.id }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("full_admin_tab_${tab.id}")
                    ) {
                        Text(
                            text = tab.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 3. 6 Real-Time Hero KPI Cards (Always visible in ALL and USERS)
        if (selectedSection == FullAdminSectionTab.ALL || selectedSection == FullAdminSectionTab.USERS) {
            item {
                RealtimeKpiGridSection(snapshot = snapshot)
            }
        }

        // 4. 5 Free Forever VIP Accounts (user1@login.com .. user5@login.com, Password: Free123, Max 2 Devices)
        if (selectedSection == FullAdminSectionTab.ALL ||
            selectedSection == FullAdminSectionTab.FREE_FOREVER ||
            selectedSection == FullAdminSectionTab.USERS
        ) {
            item {
                FreeForeverAccountsAdminCard(
                    accounts = freeForeverAccounts,
                    onAddTestDevice = { email ->
                        NeliFreeForeverAccountsManager.adminAddTestDeviceSlot(context, email)
                        coroutineScope.launch {
                            NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = false)
                        }
                        adminActionBanner = "Kifaa kimeongezwa kwenye $email (Kikomo: Vifaa 2)."
                    },
                    onRemoveDevice = { email, devId ->
                        NeliFreeForeverAccountsManager.adminRemoveDeviceFromAccount(context, email, devId)
                        coroutineScope.launch {
                            NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = false)
                        }
                        adminActionBanner = "Kifaa kimeondolewa kwenye $email."
                    },
                    onResetAccountDevices = { email ->
                        NeliFreeForeverAccountsManager.adminResetAllDevicesForAccount(context, email)
                        coroutineScope.launch {
                            NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = false)
                        }
                        adminActionBanner = "Vifaa vyote vya $email vimefutwa (0/2 Vifaa sasa)!"
                    }
                )
            }
        }

        // 5. HarakaPay Total Income, Packages, Mobile Money Networks & Live Transactions
        if (selectedSection == FullAdminSectionTab.ALL || selectedSection == FullAdminSectionTab.HARAKAPAY) {
            item {
                HarakaPayIncomeAnalyticsCard(snapshot = snapshot)
            }
        }

        // 6. Total Watching Now (Top Channels & Live Viewer Sessions)
        if (selectedSection == FullAdminSectionTab.ALL || selectedSection == FullAdminSectionTab.WATCHING) {
            item {
                LiveWatchingAnalyticsCard(snapshot = snapshot)
            }
        }

        // 7. Tanzania Locations (Cities, Districts & Streets / Mitaa na Mikoa)
        if (selectedSection == FullAdminSectionTab.ALL || selectedSection == FullAdminSectionTab.LOCATIONS) {
            item {
                TanzaniaStreetsAndCitiesCard(
                    snapshot = snapshot,
                    citiesList = citiesList,
                    selectedCity = selectedCityFilter,
                    onSelectCity = { selectedCityFilter = it },
                    searchQuery = locationSearchQuery,
                    onSearchQueryChange = { locationSearchQuery = it },
                    filteredLocations = filteredLocations
                )
            }
        }

        // 8. Registered & Premium Users Directory
        if (selectedSection == FullAdminSectionTab.ALL || selectedSection == FullAdminSectionTab.USERS) {
            item {
                RegisteredAndPremiumUsersCard(
                    snapshot = snapshot,
                    searchQuery = userSearchQuery,
                    onSearchQueryChange = { userSearchQuery = it },
                    filteredUsers = filteredUsers
                )
            }
        }

        // 9. "And More" Platform Telemetry (Device Brands, Network, Cast, CDN & Server Health)
        if (selectedSection == FullAdminSectionTab.ALL) {
            item {
                PlatformAndDeviceTelemetryCard(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun RealtimeKpiGridSection(snapshot: NeliRealtimeAdminSnapshot) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("realtime_kpi_grid_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "TAKWIMU KUU ZA REAL-TIME (LIVE METRICS)",
            color = NeliGenreCyan,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealtimeKpiMetricCard(
                title = "Total App Users",
                value = "%,d".format(snapshot.totalAppUsers),
                subtitle = "+${snapshot.todayNewAppUsers} wapya leo Tanzania",
                accentColor = Color(0xFF38BDF8),
                icon = Icons.Default.PhoneAndroid,
                testTag = "kpi_total_app_users",
                modifier = Modifier.weight(1f)
            )
            RealtimeKpiMetricCard(
                title = "Current Users (Online)",
                value = "%,d".format(snapshot.currentOnlineUsers),
                subtitle = "Peak leo: ${snapshot.peakOnlineToday} online",
                accentColor = Color(0xFF10B981),
                icon = Icons.Default.Visibility,
                testTag = "kpi_current_online_users",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealtimeKpiMetricCard(
                title = "Registered Users",
                value = "%,d".format(snapshot.registeredUsersCount),
                subtitle = "Waliosajiliwa + 5 Free VIP",
                accentColor = Color(0xFFA855F7),
                icon = Icons.Default.Person,
                testTag = "kpi_registered_users",
                modifier = Modifier.weight(1f)
            )
            RealtimeKpiMetricCard(
                title = "Premium Users (VIP)",
                value = "%,d".format(snapshot.premiumUsersCount),
                subtitle = "Waliolipia + ${snapshot.freeForeverActiveUsersCount} Bure Milele",
                accentColor = Color(0xFFF59E0B),
                icon = Icons.Default.WorkspacePremium,
                testTag = "kpi_premium_users",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RealtimeKpiMetricCard(
                title = "Total Watching Sasa",
                value = "%,d".format(snapshot.totalWatchingNow),
                subtitle = "Live TV: ${snapshot.liveTvWatchingNow} • Movies: ${snapshot.moviesAndSeriesWatchingNow}",
                accentColor = NeliLiveRed,
                icon = Icons.Default.PlayArrow,
                testTag = "kpi_total_watching_now",
                modifier = Modifier.weight(1f)
            )
            RealtimeKpiMetricCard(
                title = "Total Income (HarakaPay)",
                value = snapshot.formatTzs(snapshot.totalHarakaPayIncomeTzs),
                subtitle = "Leo: ${snapshot.formatTzs(snapshot.todayHarakaPayIncomeTzs)}",
                accentColor = Color(0xFF34D399),
                icon = Icons.Default.AccountBalanceWallet,
                testTag = "kpi_total_harakapay_income",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RealtimeKpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Text(
                    text = title,
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FreeForeverAccountsAdminCard(
    accounts: List<FreeForeverAccountStatus>,
    onAddTestDevice: (email: String) -> Unit,
    onRemoveDevice: (email: String, deviceId: String) -> Unit,
    onResetAccountDevices: (email: String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("admin_free_forever_accounts_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x2610B981)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "5 FREE FOREVER VIP ACCOUNTS (MAX 2 DEVICES)",
                        color = Color(0xFF34D399),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Bure Milele hata Admin akifunga channels • Password zote: Free123 • Kila akaunti mwisho vifaa 2",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            accounts.forEachIndexed { idx, acc ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeliSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (acc.isFull) Color(0xFFF59E0B) else Color(0xFF10B981).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                        .testTag("free_forever_account_row_${idx + 1}"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}. ${acc.email}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF065F46))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "FREE FOREVER ∞",
                                        color = Color(0xFFFDE047),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                            Text(
                                text = "Password: ${acc.password} • Channels zote WAZI milele",
                                color = NeliGenreCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (acc.isFull) Color(0xFF7C2D12) else Color(0xFF064E3B)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${acc.activeDeviceCount}/${acc.maxDevices} Vifaa",
                                color = if (acc.isFull) Color(0xFFFDE047) else Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (acc.activeDevices.isEmpty()) {
                        Text(
                            text = "Hakuna kifaa kilichoingia sasa hivi (Slots 2 ziko wazi).",
                            color = NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    } else {
                        acc.activeDevices.forEachIndexed { dIdx, slot ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F172A))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Kifaa ${dIdx + 1}: ${slot.deviceName}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "IP: ${slot.deviceIp} • ${slot.locationCityStreet}",
                                        color = NeliTextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveDevice(acc.email, slot.deviceId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Ondoa Kifaa",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!acc.isFull) {
                            OutlinedButton(
                                onClick = { onAddTestDevice(acc.email) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("add_test_device_${idx + 1}"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Devices,
                                    contentDescription = null,
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "+1 Kifaa (Test Slot)",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (acc.activeDeviceCount > 0) {
                            Button(
                                onClick = { onResetAccountDevices(acc.email) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF991B1B)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .testTag("reset_devices_${idx + 1}"),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Futa Vifaa (Reset 0/2)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HarakaPayIncomeAnalyticsCard(snapshot: NeliRealtimeAdminSnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("admin_harakapay_income_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x26F59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MAPATO YA HARAKAPAY (REAL-TIME INCOME)",
                        color = Color(0xFFFBBF24),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Jumla ya Mapato • Vifurushi • M-Pesa / Mixx / Airtel / HaloPesa",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Total Income & Period Summary Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF064E3B), Color(0xFF1E293B))
                        )
                    )
                    .border(1.dp, Color(0xFF34D399).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "JUMLA KUU YA MAPATO (TOTAL HARAKAPAY INCOME)",
                    color = Color(0xFFA7F3D0),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = snapshot.formatTzs(snapshot.totalHarakaPayIncomeTzs),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Mapato ya Leo", color = NeliTextSecondary, fontSize = 10.sp)
                        Text(
                            text = snapshot.formatTzs(snapshot.todayHarakaPayIncomeTzs),
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Wiki Hii", color = NeliTextSecondary, fontSize = 10.sp)
                        Text(
                            text = snapshot.formatTzs(snapshot.weeklyHarakaPayIncomeTzs),
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text("Mwezi Huu", color = NeliTextSecondary, fontSize = 10.sp)
                        Text(
                            text = snapshot.formatTzs(snapshot.monthlyHarakaPayIncomeTzs),
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Salio la HarakaPay Wallet: ${snapshot.harakaPayWalletBalance}",
                        color = Color(0xFFFDE047),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Miamala: ${snapshot.completedHarakaPayTransactions}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Package Breakdown
            Text(
                text = "Mchanganuo wa Mapato kwa Kifurushi:",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PackageIncomeMiniBox(
                    title = "Siku Mbili (1,000)",
                    subscribers = "${snapshot.dailyPlanSubscribers} Wateja",
                    revenue = snapshot.formatTzs(snapshot.dailyPlanRevenueTzs),
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                PackageIncomeMiniBox(
                    title = "Kwa Wiki (3,500)",
                    subscribers = "${snapshot.weeklyPlanSubscribers} Wateja",
                    revenue = snapshot.formatTzs(snapshot.weeklyPlanRevenueTzs),
                    color = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f)
                )
                PackageIncomeMiniBox(
                    title = "Kwa Mwezi (15,000)",
                    subscribers = "${snapshot.monthlyPlanSubscribers} Wateja",
                    revenue = snapshot.formatTzs(snapshot.monthlyPlanRevenueTzs),
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }

            // Mobile Money Networks Breakdown
            Text(
                text = "Mitandao ya Simu Tanzania (Mobile Money):",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            snapshot.mobileMoneyBreakdown.forEach { net ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(NeliSurfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${net.networkName} (${net.transactionsCount} malipo)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${snapshot.formatTzs(net.totalAmountTzs)} (${net.sharePercent}%)",
                            color = Color(0xFF34D399),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (net.sharePercent / 100f).coerceIn(0f, 1f) },
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            // Recent HarakaPay Transactions (Confirmed Payments Only)
            Text(
                text = "Miamala Iliyothibitishwa ya HarakaPay (Confirmed Payments Only):",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (snapshot.recentTransactions.isEmpty()) {
                Text(
                    text = "Hakuna malipo yaliyothibitishwa (Confirmed Payments) kwa sasa. Malipo yataonekana hapa mara tu mteja akithibitisha malipo kwenye HarakaPay.",
                    color = NeliTextSecondary,
                    fontSize = 11.sp
                )
            } else {
                snapshot.recentTransactions.take(10).forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F172A))
                            .border(0.5.dp, NeliBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${tx.planTitle} • ${tx.formattedAmount}",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "✓ ${tx.status}",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${tx.phoneNumber} (${tx.mobileNetwork}) • ${tx.orderId}",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "📍 ${tx.cityAndStreet} • ${tx.formattedTime}",
                                color = NeliTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageIncomeMiniBox(
    title: String,
    subscribers: String,
    revenue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(NeliSurfaceVariant)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = revenue,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subscribers,
            color = NeliTextSecondary,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun TanzaniaStreetsAndCitiesCard(
    snapshot: NeliRealtimeAdminSnapshot,
    citiesList: List<String>,
    selectedCity: String,
    onSelectCity: (String) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredLocations: List<com.example.data.TanzaniaLocationStat>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeliGenreCyan.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("admin_tanzania_locations_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x2600E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MAENEO YA WATUMIAJI TANZANIA (STREETS & CITIES)",
                        color = NeliGenreCyan,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Mikoa, Wilaya na Mitaa yote ya Tanzania yenye watumiaji live sasa hivi",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            // Current Device Detected Street Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Eneo Lako Sasa (Detected Tanzania Location):",
                        color = NeliTextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "${snapshot.currentDeviceCity} • ${snapshot.currentDeviceStreet}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Search Street or City
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_location_search_input"),
                placeholder = { Text("Tafuta Mtaa au Mkoa (mfano: Kariakoo, Sinza, Arusha, Mwanza)...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NeliGenreCyan
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NeliTextPrimary,
                    unfocusedTextColor = NeliTextPrimary,
                    focusedBorderColor = NeliGenreCyan,
                    unfocusedBorderColor = NeliBorder
                )
            )

            // City Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                citiesList.forEach { city ->
                    val isSelected = city == selectedCity
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else NeliSurfaceVariant)
                            .clickable { onSelectCity(city) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = city,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                        )
                    }
                }
            }

            // Streets & Cities List
            filteredLocations.forEach { loc ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (loc.isCurrentDeviceLocation) Color(0xFF112233) else NeliSurfaceVariant)
                        .border(
                            width = 1.dp,
                            color = if (loc.isCurrentDeviceLocation) NeliGenreCyan else NeliBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .testTag("tz_location_row_${loc.id}"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${loc.city} (${loc.district})",
                                    color = NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (loc.isCurrentDeviceLocation) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF065F46))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "ULIPO SASA",
                                            color = Color(0xFFFDE047),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Mtaa: ${loc.street}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${loc.onlineNow} Online Sasa",
                                color = Color(0xFF34D399),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Jumla: ${loc.totalUsers} Users",
                                color = NeliTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📺 Wanaotazama: ${loc.watchingNow} • 👑 VIP: ${loc.premiumUsers}",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Top: ${loc.topWatchedChannel}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWatchingAnalyticsCard(snapshot: NeliRealtimeAdminSnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeliLiveRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("admin_live_watching_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NeliLiveRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = NeliLiveRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TOTAL WATCHING RIGHT NOW (${snapshot.totalWatchingNow} LIVE)",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Live TV: ${snapshot.liveTvWatchingNow} watazamaji • Movies & Series: ${snapshot.moviesAndSeriesWatchingNow} watazamaji",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "Channels Zinazotazamwa Zaidi Sasa Hivi:",
                color = NeliGenreCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (snapshot.topWatchedChannels.isEmpty()) {
                Text(
                    text = "Hakuna anayetazama kipindi au channel kwa sasa (0 Watching Now).",
                    color = NeliTextSecondary,
                    fontSize = 11.sp
                )
            } else {
                snapshot.topWatchedChannels.take(8).forEach { ch ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeliSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ch.channelName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• ${ch.category}",
                                    color = NeliTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = "${ch.viewersNow} watching (${ch.sharePercent}%)",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (ch.sharePercent / 100f).coerceIn(0.08f, 1f) },
                            color = NeliLiveRed,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            Text(
                text = "Watazamaji Live Sasa Hivi (Mitaa & Vifaa vyao):",
                color = Color(0xFFFBBF24),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            snapshot.activeViewerSessions.forEach { session ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A))
                        .border(
                            width = 0.5.dp,
                            color = if (session.isCurrentDevice) NeliGenreCyan else NeliBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${session.userName} (${session.userEmail})",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = session.accountTier,
                            color = when (session.accountTier) {
                                "FREE FOREVER VIP" -> Color(0xFF34D399)
                                "PREMIUM VIP" -> Color(0xFFFBBF24)
                                else -> NeliGenreCyan
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Text(
                        text = "▶️ Anatazama: ${session.watchingTitle} (${session.watchingCategory})",
                        color = Color(0xFFFDE047),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "📍 ${session.city} • ${session.street}",
                        color = NeliGenreCyan,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "📱 ${session.deviceModel} • IP: ${session.deviceIp} • ${session.networkType}",
                        color = NeliTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisteredAndPremiumUsersCard(
    snapshot: NeliRealtimeAdminSnapshot,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredUsers: List<com.example.data.RegisteredUserSummary>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeliMagenta.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .testTag("admin_registered_users_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "ORODHA YA REGISTERED & PREMIUM USERS (${snapshot.registeredUsersCount})",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_user_search_input"),
                placeholder = { Text("Tafuta mtumiaji kwa jina, email au mtaa...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = NeliMagenta
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NeliTextPrimary,
                    unfocusedTextColor = NeliTextPrimary,
                    focusedBorderColor = NeliMagenta,
                    unfocusedBorderColor = NeliBorder
                )
            )

            filteredUsers.forEach { user ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeliSurfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.fullName,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = user.email,
                                color = NeliGenreCyan,
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when (user.accountType) {
                                        "FREE FOREVER VIP" -> Color(0xFF065F46)
                                        "PREMIUM VIP" -> Color(0xFF78350F)
                                        "ADMIN" -> Color(0xFF7F1D1D)
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = user.accountType,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = "📱 ${user.activeDevicesLabel} • 📺 ${user.watchingNow}",
                        color = Color(0xFFFBBF24),
                        fontSize = 11.sp
                    )
                    Text(
                        text = "📍 ${user.city} • ${user.street} (${user.lastActiveLabel})",
                        color = NeliTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformAndDeviceTelemetryCard(snapshot: NeliRealtimeAdminSnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NeliBorder, RoundedCornerShape(18.dp))
            .testTag("admin_platform_telemetry_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NeliSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "TAKWIMU ZAIDI ZA MFUMO (SYSTEM, CAST & DEVICES)",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Smart TV Cast Active:", color = NeliTextSecondary, fontSize = 12.sp)
                Text("${snapshot.activeCastDevicesNow} Smart TVs", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Offline Saved Downloads:", color = NeliTextSecondary, fontSize = 12.sp)
                Text("${snapshot.totalSavedOfflineDownloads} Videos", color = Color(0xFFFBBF24), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mtandao (4G/5G vs Wi-Fi):", color = NeliTextSecondary, fontSize = 12.sp)
                Text("${snapshot.mobileDataUsersPercent}% Mobile • ${snapshot.wifiUsersPercent}% Wi-Fi", color = NeliGenreCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Azam TV HD CDN Status:", color = NeliTextSecondary, fontSize = 12.sp)
                Text(snapshot.azamCdnStatusLabel, color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = NeliBorder)

            Text(
                text = "Aina za Simu za Watumiaji Tanzania:",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            snapshot.topDeviceBrands.forEach { brand ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = brand.brandName,
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "${brand.usersCount} watumiaji (${brand.sharePercent}%)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
