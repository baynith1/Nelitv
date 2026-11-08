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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Floating Neon Dock Bottom Navigation Bar styled to match the official Neliplay UI reference:
 * - Rounded top translucent dark-indigo glass dock with glowing border
 * - Glowing neon magenta pill indicator under active tab
 * - Clean system navigation bar insets clearance
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
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val safeBottomPadding = if (navBarBottom < 6.dp) 6.dp else navBarBottom

    val dockBgColor = if (isLightMode) Color(0xF2FFFFFF) else Color(0xF00A0F26)
    val dockBorderColor = if (isLightMode) Color(0x33FFB3C6) else Color(0x33263560)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(bottom = safeBottomPadding)
            .testTag("neli_bottom_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = dockBgColor,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .border(
                    width = 1.dp,
                    color = dockBorderColor,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF8E9BAE),
                        label = "icon_color"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) Color.White else Color(0xFF8E9BAE),
                        label = "text_color"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag(tab.tag)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTabSelected(tab) }
                            .padding(
                                horizontal = screenProfile.bottomBarHorizontalItemPadding,
                                vertical = 4.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = iconColor,
                                modifier = Modifier.size(screenProfile.bottomBarIconSize)
                            )
                            if (tab == BottomNavTab.DOWNLOAD && activeDownloadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFF0055))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = tab.label,
                            color = textColor,
                            fontSize = screenProfile.bottomBarFontSize,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Glowing neon magenta pill indicator under active tab (matching reference image)
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(2.5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Color(0xFFFF007A), Color(0xFFFF5E00))
                                        )
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(5.5.dp))
                        }
                    }
                }
            }
        }
    }
}
