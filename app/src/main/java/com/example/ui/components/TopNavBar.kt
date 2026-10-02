package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.AdminTopSmsNotificationBanner
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager

/**
 * Redesigned Non-Overlapping Direct Top Header Bar:
 * - Displays Admin SMS Notification Bar at the top when published by Admin.
 * - Header contains: Left = Brand Logo, Right = Search Button + Cast to Large TV Button.
 */
@Composable
fun TopNavBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    activeTabLabel: String = "Live TV",
    isOfflineMode: Boolean = false,
    onOfflineClick: () -> Unit = {},
    onBrandClick: () -> Unit = {},
    showScanToCastCamIcon: Boolean = true,
    isScanToCastActive: Boolean = false,
    onScanToCastCamClick: () -> Unit = {},
    isCastActive: Boolean = false,
    onCastClick: () -> Unit = {},
    adminSmsMessage: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isLightMode = NeliThemeManager.isLightMode
    val headerBgColor = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF0B0D14)

    // Guaranteed status bar top safe padding so header never collides with mobile status banner
    // even immediately after returning from fullscreen video when WindowInsets.statusBars is transiently 0.dp
    val systemStatusBarResDp = remember(context) {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) {
            val px = context.resources.getDimensionPixelSize(resId)
            val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
            (px / density).dp
        } else {
            32.dp
        }
    }
    val systemStatusBarTop = WindowInsets.statusBars.union(WindowInsets.displayCutout).asPaddingValues().calculateTopPadding()
    val minSafeTop = if (systemStatusBarResDp < 32.dp) 32.dp else systemStatusBarResDp
    val safeTopPadding = if (systemStatusBarTop < minSafeTop) minSafeTop else systemStatusBarTop

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBgColor)
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .padding(top = safeTopPadding)
            .testTag("top_nav_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Brand Logo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBrandClick() }
                    .padding(vertical = 4.dp)
                    .testTag("header_brand_logo")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_nelitv_app_logo_1790873809763),
                    contentDescription = "Neliplay Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x5500E5FF), RoundedCornerShape(8.dp))
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Neli",
                        color = NeliTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp
                    )
                    Text(
                        text = "play",
                        color = Color(0xFFFF2E7E),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp
                    )
                }
            }

            // Right: Search Button -> Cast Button (Clean header: Logo -> Search -> Google Cast)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .testTag("search_icon_button")
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSearchOpen) NeliMagenta.copy(alpha = 0.16f) else Color.Transparent
                        )
                ) {
                    Icon(
                        imageVector = if (isSearchOpen) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = if (isSearchOpen) "Close search" else "Search Azam TV channels",
                        tint = if (isSearchOpen) NeliMagenta else NeliTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onCastClick,
                    modifier = Modifier
                        .testTag("cast_icon_button")
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCastActive) Color(0x2634D399) else Color.Transparent
                        )
                ) {
                    Icon(
                        imageVector = if (isCastActive) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = if (isCastActive) "Connected to Large TV" else "Cast to Large TV",
                        tint = if (isCastActive) Color(0xFF34D399) else NeliTextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSearchOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 720.dp)
                        .focusRequester(focusRequester)
                        .testTag("search_text_field"),
                    placeholder = {
                        Text(
                            text = "Search NeliTV",
                            color = NeliTextSecondary,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = NeliMagenta
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = NeliTextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NeliSurface,
                        unfocusedContainerColor = NeliSurface,
                        focusedBorderColor = NeliMagenta,
                        unfocusedBorderColor = NeliBorder,
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary,
                        cursorColor = NeliMagenta
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
            }
        }

        HorizontalDivider(
            color = NeliBorder,
            thickness = 1.dp
        )
    }
}
