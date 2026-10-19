package com.example.ui.tv

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.BottomNavTab
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

data class TvNavItem(
    val tab: BottomNavTab,
    val title: String,
    val icon: ImageVector,
    val tag: String
)

val TvNavItems = listOf(
    TvNavItem(BottomNavTab.HOME, "Home", Icons.Default.Home, "tv_nav_home"),
    TvNavItem(BottomNavTab.DISCOVERY, "Discovery", Icons.Default.Explore, "tv_nav_discovery"),
    TvNavItem(BottomNavTab.PREMIUM, "Plans", Icons.Default.WorkspacePremium, "tv_nav_plans"),
    TvNavItem(BottomNavTab.DOWNLOAD, "Downloads", Icons.Default.Download, "tv_nav_downloads"),
    TvNavItem(BottomNavTab.ACCOUNT, "Account", Icons.Default.Person, "tv_nav_account")
)

/**
 * Smart TV Side Navigation Panel / Rail:
 * - Replaces bottom menu on Smart TV.
 * - Displays Home, Discovery, Plans, Downloads, and Account.
 * - Supports D-Pad remote navigation, scale growth, and the Gemini glowing boundary effect.
 */
@Composable
fun NeliTvSideNavigationRail(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit,
    activeDownloadCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0A0C13),
        modifier = modifier
            .width(220.dp)
            .fillMaxHeight()
            .border(
                width = 0.5.dp,
                color = NeliBorder.copy(alpha = 0.6f),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
            )
            .testTag("tv_side_navigation_rail")
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 24.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TV Brand Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_nelitv_app_logo_1790873809763),
                        contentDescription = "NeliTV Logo",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Neli",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "TV",
                                color = NeliMagenta,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Text(
                            text = "SMART TV",
                            color = NeliGenreCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Items: Home, Discovery, Plans, Downloads, Account
                TvNavItems.forEach { item ->
                    val isSelected = selectedTab == item.tab
                    val itemBgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF26103F) else Color.Transparent,
                        label = "tv_nav_bg"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) NeliMagenta else NeliTextSecondary,
                        label = "tv_nav_icon"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(itemBgColor)
                            .tvGeminiFocusable(
                                shape = RoundedCornerShape(12.dp),
                                focusScale = 1.05f,
                                onClick = { onTabSelected(item.tab) }
                            )
                            .clickable { onTabSelected(item.tab) }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag(item.tag),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (item.tab == BottomNavTab.DOWNLOAD && activeDownloadCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = NeliMagenta) {
                                        Text(
                                            text = "$activeDownloadCount",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = iconTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = item.title,
                            color = if (isSelected) NeliTextPrimary else NeliTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }

            // Bottom TV status footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "D-Pad & Remote Ready",
                    color = NeliTextSecondary.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }
        }
    }
}
