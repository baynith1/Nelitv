package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.local.UserAccountEntity
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.rememberNeliScreenProfile

/**
 * Helper determining whether the current user session requires the mandatory Sign-In popup:
 * - First launch / fresh install (`currentUser == null`)
 * - App update where user has not signed in (`currentUser == null`)
 * - Legacy users who previously used the old local "Choose Account" picker without real Google/Firebase sign-in
 *   (`uid.startsWith("google_") && idToken.isBlank() && !isAdFreeVip && !isAdmin`)
 */
fun requiresMandatoryGoogleSignIn(
    currentUser: UserAccountEntity?,
    isTelevision: Boolean = false
): Boolean {
    // Smart TV / Android TV / TV Box users play immediately with zero login or sign-up gates.
    if (isTelevision) {
        return false
    }
    if (currentUser == null || !currentUser.isLoggedIn || currentUser.email.isBlank()) {
        return true
    }
    val email = currentUser.email.trim().lowercase()
    if (com.example.data.NeliAdminManager.isAdminEmail(email) ||
        com.example.data.NeliFreeForeverAccountsManager.isAdFreeVipEmail(email)
    ) {
        return false
    }
    val isLegacyLocalAccountPicker = currentUser.uid.startsWith("google_", ignoreCase = true) &&
        currentUser.idToken.isBlank()
    return isLegacyLocalAccountPicker
}

/**
 * Simple, unclosable Sign-In / Sign-Up popup displayed on first launch, app update, or for
 * legacy account-picker users. Supports:
 * 1. Continue with Google (real CredentialManager + Firebase Auth)
 * 2. By Email:
 *    - Login with Email or Username + Password (synced with Firebase)
 *    - Sign Up with Username + Email + Password (synced with Firebase)
 */
@Composable
fun MandatoryGoogleSignInDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onSignInWithEmail: (emailOrUsername: String, password: String) -> Unit,
    onSignUpWithEmail: (username: String, email: String, password: String) -> Unit
) {
    val screenProfile = rememberNeliScreenProfile()
    var showEmailMode by rememberSaveable { mutableStateOf(false) }
    var isRegisterMode by rememberSaveable { mutableStateOf(false) }
    var usernameInput by rememberSaveable { mutableStateOf("") }
    var emailOrUsernameInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* Strictly unclosable until user signs in */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (screenProfile.isSmallPhone) 0.94f else 0.90f)
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        listOf(NeliMagenta, Color(0xFF7C3AED), NeliGenreCyan)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .testTag("mandatory_google_signin_dialog"),
            color = Color(0xFF0C1022),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (screenProfile.isSmallPhone) 16.dp else 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Brand Badge
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0x33FF2E7E), Color(0x3300E5FF))
                            )
                        )
                        .border(1.5.dp, NeliMagenta.copy(alpha = 0.7f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.neli_logo),
                        contentDescription = "NeliTV Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }

                // Title & subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Karibu NeliTV",
                            color = Color.White,
                            fontSize = if (screenProfile.isSmallPhone) 19.sp else 22.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Verified Pink Tick",
                            tint = Color(0xFFFF2E7E),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Sign in or Sign up with Google or Email to watch Live TV, Movies & Series.",
                        color = Color(0xFFB8C3D9),
                        fontSize = if (screenProfile.isSmallPhone) 12.sp else 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Primary Google Sign-In Button
                Button(
                    onClick = { if (!isLoading) onContinueWithGoogle() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF111827)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("mandatory_google_signin_button")
                ) {
                    if (isLoading && !showEmailMode) {
                        CircularProgressIndicator(
                            color = NeliMagenta,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Signing in with Google...",
                            color = Color(0xFF111827),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF2E7E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            color = Color(0xFF111827),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Divider + Clear "By Email" Option Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
                    Text(
                        text = "OR",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
                }

                OutlinedButton(
                    onClick = { showEmailMode = !showEmailMode },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (showEmailMode) NeliMagenta else NeliGenreCyan.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("mandatory_toggle_email_mode")
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = if (showEmailMode) NeliMagenta else NeliGenreCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showEmailMode) "Hide Email Login / Sign Up" else "Login or Sign Up by Email",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = Color(0xFFFF6B8A),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x26FF2E7E))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                if (showEmailMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF131932))
                            .border(1.dp, NeliBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Login vs Sign Up by Email Tabs
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!isRegisterMode) NeliMagenta else Color(0xFF1E2645))
                                    .clickable { isRegisterMode = false }
                                    .padding(vertical = 10.dp)
                                    .testTag("mandatory_email_login_tab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Login",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isRegisterMode) NeliMagenta else Color(0xFF1E2645))
                                    .clickable { isRegisterMode = true }
                                    .padding(vertical = 10.dp)
                                    .testTag("mandatory_email_signup_tab"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Sign Up",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        if (isRegisterMode) {
                            OutlinedTextField(
                                value = usernameInput,
                                onValueChange = { usernameInput = it },
                                label = { Text("Username", fontSize = 12.sp) },
                                placeholder = { Text("Enter your username", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = NeliGenreCyan)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("mandatory_email_username_input"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeliMagenta,
                                    unfocusedBorderColor = NeliBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = NeliSurface,
                                    unfocusedContainerColor = NeliSurface
                                )
                            )
                        }

                        OutlinedTextField(
                            value = emailOrUsernameInput,
                            onValueChange = { emailOrUsernameInput = it },
                            label = {
                                Text(
                                    text = if (isRegisterMode) "Email Address" else "Email or Username",
                                    fontSize = 12.sp
                                )
                            },
                            placeholder = {
                                Text(
                                    text = if (isRegisterMode) "name@email.com" else "Email or Username",
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = NeliGenreCyan)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (isRegisterMode) KeyboardType.Email else KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mandatory_email_identifier_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeliMagenta,
                                unfocusedBorderColor = NeliBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NeliSurface,
                                unfocusedContainerColor = NeliSurface
                            )
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Password", fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = NeliGenreCyan)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (isPasswordVisible) "Hide Password" else "Show Password",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("mandatory_email_password_input"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeliMagenta,
                                unfocusedBorderColor = NeliBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NeliSurface,
                                unfocusedContainerColor = NeliSurface
                            )
                        )

                        Button(
                            onClick = {
                                if (!isLoading) {
                                    if (isRegisterMode) {
                                        onSignUpWithEmail(
                                            usernameInput.trim(),
                                            emailOrUsernameInput.trim(),
                                            passwordInput
                                        )
                                    } else {
                                        onSignInWithEmail(emailOrUsernameInput.trim(), passwordInput)
                                    }
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mandatory_email_submit_button")
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Text(
                                    text = if (isRegisterMode) "Sign Up with Email" else "Login with Email",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
