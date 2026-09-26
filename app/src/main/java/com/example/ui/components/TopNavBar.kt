package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary

@Composable
fun TopNavBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    Surface(
        color = Color(0xFF0B0D14),
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("top_nav_bar")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0D14))
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
                    // Brand Logo and Title
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
                                contentDescription = "Neli TV Logo",
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
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "PLAY",
                                    color = NeliMagenta,
                                    fontSize = if (isTablet) 20.sp else 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                )
                            }
                            Text(
                                text = "Azam TV • Cinema • Low Bando",
                                color = NeliTextSecondary,
                                fontSize = if (isTablet) 11.sp else 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Right side action: Search Button only
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
