package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliThemeManager

@Composable
fun TopNavBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isLightMode = NeliThemeManager.isLightMode
    val headerBgColor = if (isLightMode) Color(0xFFFFFFFF) else Color(0xFF0B0D14)

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        color = headerBgColor,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_nav_bar")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(headerBgColor)
                .windowInsetsPadding(WindowInsets.statusBars)
                // Extra top breathing space below the phone's battery percentage, clock & network status bar
                .padding(top = 12.dp, bottom = 4.dp)
        ) {
            val isTablet = maxWidth >= 600.dp
            val horizontalPad = if (isTablet) 24.dp else 16.dp

            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTablet) 64.dp else 56.dp)
                        .padding(horizontal = horizontalPad),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Brand Logo and Title: NELITV (Mother Company: Neliplay)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("header_brand_logo")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isTablet) 40.dp else 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NeliMagenta, Color(0xFFBE123C))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LiveTv,
                                contentDescription = "Nelitv Logo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NELI",
                                    color = NeliTextPrimary,
                                    fontSize = if (isTablet) 20.sp else 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "TV",
                                    color = NeliMagenta,
                                    fontSize = if (isTablet) 20.sp else 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = "By Neliplay • Live TV & Cinema",
                                color = NeliTextSecondary,
                                fontSize = if (isTablet) 11.sp else 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Right side actions: Theme Colour Changer (Black <-> White) & Search Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { NeliThemeManager.toggleTheme(context) },
                            modifier = Modifier
                                .testTag("theme_toggle_icon_button")
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeliSurfaceVariant)
                                .border(1.dp, NeliBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = if (isLightMode) "Switch to Black Theme" else "Switch to White Theme",
                                tint = if (isLightMode) Color(0xFF0F172A) else NeliGenreCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier
                                .testTag("search_icon_button")
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeliSurfaceVariant)
                                .border(1.dp, NeliBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSearchOpen) Icons.Default.Clear else Icons.Default.Search,
                                contentDescription = if (isSearchOpen) "Close search" else "Search",
                                tint = if (isSearchOpen) NeliMagenta else NeliTextPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                // Expandable search bar
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPad, vertical = 8.dp),
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
                                    text = "Search movies, series, or live channels...",
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
                            shape = RoundedCornerShape(14.dp),
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
    }
}
