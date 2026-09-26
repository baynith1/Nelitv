package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    DISCOVERY("Discovery", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_tab_discovery"),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, "nav_tab_search"),
    DOWNLOAD("Download", Icons.Filled.Download, Icons.Outlined.Download, "nav_tab_download"),
    ACCOUNT("Account", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_account")
}

/**
 * Clean YouTube-style Bottom Navigation Bar with safe system navigation bar clearance.
 */
@Composable
fun NeliBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    activeDownloadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isLightMode = NeliThemeManager.isLightMode
    val barBgColor = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF0F0F0F)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBgColor)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag("neli_bottom_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(NeliBorder)
        )
        Surface(
            color = barBgColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val tintColor by animateColorAsState(
                        targetValue = if (isSelected) NeliTextPrimary else NeliTextSecondary,
                        label = "nav_tint"
                    )

                    Column(
                        modifier = Modifier
                            .testTag(tab.tag)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = tintColor,
                                modifier = Modifier.size(24.dp)
                            )
                            if (tab == BottomNavTab.DOWNLOAD && activeDownloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFFF0033))
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            color = tintColor,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
