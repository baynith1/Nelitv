package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextSecondary

enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_tab_home"),
    SEARCH("Search", Icons.Filled.Search, Icons.Outlined.Search, "nav_tab_search"),
    LIVE_TV("Live TV", Icons.Filled.LiveTv, Icons.Outlined.LiveTv, "nav_tab_live_tv"),
    DOWNLOAD("Download", Icons.Filled.Download, Icons.Outlined.Download, "nav_tab_download"),
    ACCOUNT("Account", Icons.Filled.Person, Icons.Outlined.Person, "nav_tab_account")
}

@Composable
fun NeliBottomBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 20.dp, shape = barShape, spotColor = NeliMagenta)
            .clip(barShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF280D52),
                        Color(0xFF391473),
                        Color(0xFF280D52)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0x44A855F7),
                        Color(0x99FF1E8E),
                        Color(0x44A855F7)
                    )
                ),
                shape = barShape
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("neli_bottom_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val tintColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else NeliTextSecondary.copy(alpha = 0.7f),
                    label = "nav_tint"
                )

                Column(
                    modifier = Modifier
                        .testTag(tab.tag)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = tab.label,
                        tint = if (isSelected) Color.White else tintColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        color = tintColor,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(NeliMagenta)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}
