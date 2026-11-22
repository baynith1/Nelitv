package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliThemeManager
import com.example.ui.theme.NeliWarmOrange
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
 * Floating Pill Capsule Bottom Navigation Bar:
 * - Semi-translucent obsidian glass pill floating safely ABOVE the device's gesture or 3-button navigation bar
 * - Uses [WindowInsets.navigationBarsIgnoringVisibility] so the dock NEVER overlaps system navigation buttons even in immersive mode
 * - Active tab expands smoothly with Neli TV's signature magenta-to-orange gradient highlight & crisp label
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeliBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    activeDownloadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isLightMode = NeliThemeManager.isLightMode
    val screenProfile = rememberNeliScreenProfile()
    val isNarrow = screenProfile.isSmallPhone || screenProfile.widthDp < 380

    // Measure both visible and hidden system navigation bar insets so the floating dock ALWAYS clears
    // 3-button navigation bars and gesture handles on Samsung, ZTE, Infinix, and Pixel devices.
    val visibleNavBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val persistentNavBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val safeNavBottom = maxOf(visibleNavBottom, persistentNavBottom, 16.dp) + 8.dp

    val dockBgColor = if (isLightMode) Color(0xF5FFFFFF) else Color(0xEC10162B)
    val dockBorderBrush = if (isLightMode) {
        Brush.horizontalGradient(listOf(Color(0x55FF2E7E), Color(0x44FF7A00), Color(0x4400E5FF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0x66FF007A), Color(0x448B00FF), Color(0x5500E5FF)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(
                start = if (isNarrow) 14.dp else 20.dp,
                end = if (isNarrow) 14.dp else 20.dp,
                top = 6.dp,
                bottom = safeNavBottom
            )
            .testTag("neli_bottom_bar"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = dockBgColor,
            shape = RoundedCornerShape(34.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = Color(0x88FF007A),
                    spotColor = Color(0x66000000)
                )
                .clip(RoundedCornerShape(34.dp))
                .border(
                    width = 1.dp,
                    brush = dockBorderBrush,
                    shape = RoundedCornerShape(34.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = if (isNarrow) 6.dp else 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val iconTint by animateColorAsState(
                        targetValue = when {
                            isSelected -> Color.White
                            isLightMode -> Color(0xFF6B5875)
                            else -> Color(0xFF8C9BB5)
                        },
                        label = "floating_nav_icon_tint"
                    )
                    val horizontalPillPadding by animateDpAsState(
                        targetValue = if (isSelected) (if (isNarrow) 12.dp else 16.dp) else 10.dp,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "floating_nav_pill_padding"
                    )

                    Box(
                        modifier = Modifier
                            .testTag(tab.tag)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(
                                        Brush.horizontalGradient(
                                            listOf(NeliMagenta, NeliWarmOrange)
                                        )
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTabSelected(tab) }
                            .padding(horizontal = horizontalPillPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label,
                                    tint = iconTint,
                                    modifier = Modifier.size(if (isSelected) 22.dp else 23.dp)
                                )
                                if (tab == BottomNavTab.DOWNLOAD && activeDownloadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF))
                                    )
                                }
                            }

                            if (isSelected) {
                                Text(
                                    text = if (tab == BottomNavTab.DOWNLOAD && isNarrow) "Saved" else tab.label,
                                    color = Color.White,
                                    fontSize = if (isNarrow) 11.sp else 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
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
    }
}
