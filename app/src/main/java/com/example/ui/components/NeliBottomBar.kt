package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliThemeManager
import com.example.ui.theme.rememberNeliScreenProfile

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    DISCOVERY("Discovery", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_tab_discovery"),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, "nav_tab_search"),
    DOWNLOAD("Downloads", Icons.Filled.Download, Icons.Outlined.Download, "nav_tab_download"),
    ACCOUNT("Account", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_account")
}

/**
 * Full-Width Docked YouTube-Style Mobile Bottom Navigation Bar:
 * - Anchored flush at the very bottom edge of the mobile screen (zero floating gap)
 * - Background extends cleanly behind the Android system gesture/3-button navigation area
 *   via internal [WindowInsets.navigationBars] padding
 * - Compact 56.dp row with crisp icon + always-visible label for every tab and >=48.dp touch targets
 */
@Composable
fun NeliBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    activeDownloadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isLightMode = NeliThemeManager.isLightMode
    val screenProfile = rememberNeliScreenProfile()
    val isNarrow = screenProfile.isSmallPhone || screenProfile.widthDp < 360

    val barBgColor = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF0A0D1A)
    val dividerColor = if (isLightMode) Color(0x1A000000) else Color(0x22FFFFFF)
    val activePillColor = if (isLightMode) Color(0x1FFF007A) else Color(0x2EFF007A)

    Surface(
        color = barBgColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("neli_bottom_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            HorizontalDivider(
                thickness = 0.75.dp,
                color = dividerColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val itemColor by animateColorAsState(
                        targetValue = when {
                            isSelected && isLightMode -> Color(0xFF140D1B)
                            isSelected -> Color.White
                            isLightMode -> Color(0xFF6B5D75)
                            else -> Color(0xFF94A3B8)
                        },
                        label = "yt_bottom_nav_tint"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = when {
                            isSelected -> if (isLightMode) NeliMagenta else Color.White
                            isLightMode -> Color(0xFF6B5D75)
                            else -> Color(0xFF94A3B8)
                        },
                        label = "yt_bottom_nav_icon_tint"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag(tab.tag)
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSelected) activePillColor else Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                            if (tab == BottomNavTab.DOWNLOAD && activeDownloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 3.dp, end = 8.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                )
                            }
                        }

                        Text(
                            text = if (tab == BottomNavTab.DOWNLOAD && isNarrow) "Saved" else tab.label,
                            color = itemColor,
                            fontSize = if (isNarrow) 9.5.sp else 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
