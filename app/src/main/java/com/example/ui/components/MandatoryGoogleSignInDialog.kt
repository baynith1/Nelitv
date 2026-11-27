package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.R
import com.example.data.local.UserAccountEntity
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliWarmOrange
import com.example.ui.theme.rememberNeliScreenProfile
import kotlinx.coroutines.delay

/**
 * Login & Sign-Up are now 100% optional on all devices (Phone, Tablet, and Smart TV).
 * Users enter the app immediately, allow notifications, watch channels, or pay for VIP freely
 * (with automatic local cache + Supabase sync), and may sign in anytime via the Account tab.
 */
fun requiresMandatoryGoogleSignIn(
    currentUser: UserAccountEntity?,
    isTelevision: Boolean = false
): Boolean = false

private enum class FullPageAuthStep {
    CHOOSER,
    PHONE_FORM,
    EMAIL_FORM
}

/**
 * Full-Page Onboarding Sign-Up / Login Experience (NOT a popup):
 * - Top: Official NeliTv logo + app name ("NeliTv")
 * - Main Options Page:
 *   1. Continue with Google (immediately launches Google Account picker & logs in)
 *   2. Phone Number (navigates to dedicated neat Phone Sign-Up / Login page with Back button)
 *   3. Email (navigates to dedicated neat Email Sign-Up / Login page with Back button)
 * - Strictly removes Guest / Incognito login from UI.
 */
@Composable
fun MandatoryGoogleSignInDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onContinueWithGoogle: () -> Unit,
    onSignInWithEmail: (emailOrUsername: String, password: String) -> Unit,
    onSignUpWithEmail: (username: String, email: String, password: String) -> Unit,
    onSignInAsGuest: (guestName: String) -> Unit = {},
    onSignInWithPhone: (phoneOrUsername: String, password: String) -> Unit = { _, _ -> },
    onSignUpWithPhone: (username: String, phoneNumber: String, password: String) -> Unit = { _, _, _ -> },
    onClearError: () -> Unit = {}
) {
    val screenProfile = rememberNeliScreenProfile()
    var authStep by rememberSaveable { mutableStateOf(FullPageAuthStep.CHOOSER) }

    // Email sub-page form state
    var isEmailSignUp by rememberSaveable { mutableStateOf(true) }
    var emailUsernameInput by rememberSaveable { mutableStateOf("") }
    var emailIdentifierInput by rememberSaveable { mutableStateOf("") }
    var emailPasswordInput by rememberSaveable { mutableStateOf("") }
    var isEmailPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Phone sub-page form state (identical mechanism to Email)
    var isPhoneSignUp by rememberSaveable { mutableStateOf(true) }
    var phoneUsernameInput by rememberSaveable { mutableStateOf("") }
    var phoneIdentifierInput by rememberSaveable { mutableStateOf("") }
    var phonePasswordInput by rememberSaveable { mutableStateOf("") }
    var isPhonePasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Hardware Back inside Phone or Email sub-pages returns smoothly to the Auth Options page
    BackHandler(enabled = authStep != FullPageAuthStep.CHOOSER) {
        onClearError()
        authStep = FullPageAuthStep.CHOOSER
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF06091A),
                        Color(0xFF0E1028),
                        Color(0xFF170B2A),
                        Color(0xFF070A18)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("mandatory_google_signin_dialog")
    ) {
        // Subtle ambient neon glow accents
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(NeliMagenta.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(NeliGenreCyan.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )

        AnimatedContent(
            targetState = authStep,
            transitionSpec = {
                if (targetState != FullPageAuthStep.CHOOSER) {
                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith
                        (slideOutHorizontally { it / 3 } + fadeOut())
                }
            },
            label = "full_page_auth_step"
        ) { currentStep ->
            when (currentStep) {
                FullPageAuthStep.CHOOSER -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = if (screenProfile.isSmallPhone) 20.dp else 28.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 440.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Top Brand Logo + App Name below
                            NeliAuthBrandHeader(isSmallPhone = screenProfile.isSmallPhone)

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Sign Up or Login to watch Live TV, Swahili Movies & Series seamlessly across all your devices.",
                                color = Color(0xFFB8C4DC),
                                fontSize = if (screenProfile.isSmallPhone) 12.sp else 13.5.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Option 1: Continue with Google (Direct Google Account Picker + Auto-Login)
                            Button(
                                onClick = {
                                    if (!isLoading) {
                                        onClearError()
                                        onContinueWithGoogle()
                                    }
                                },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF0F172A)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = NeliMagenta, spotColor = NeliMagenta)
                                    .testTag("mandatory_google_signin_button")
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        color = NeliMagenta,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Signing in with Google...",
                                        color = Color(0xFF0F172A),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.linearGradient(listOf(NeliMagenta, NeliWarmOrange))
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "G",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Continue with Google",
                                        color = Color(0xFF0F172A),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Divider
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
                                Text(
                                    text = "OR CONTINUE WITH",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0x33FFFFFF))
                            }

                            // Option 2: Phone Number (Opens dedicated full-page Phone form)
                            OutlinedButton(
                                onClick = {
                                    onClearError()
                                    authStep = FullPageAuthStep.PHONE_FORM
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.3.dp, Color(0xFF34D399).copy(alpha = 0.85f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0x1810B981)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("mandatory_open_phone_auth_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Phone Number",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Phone Number",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Option 3: Email (Opens dedicated full-page Email form)
                            OutlinedButton(
                                onClick = {
                                    onClearError()
                                    authStep = FullPageAuthStep.EMAIL_FORM
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.3.dp, NeliGenreCyan.copy(alpha = 0.85f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0x1800E5FF)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("mandatory_toggle_email_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Email",
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Continue with Email",
                                    color = Color.White,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!errorMessage.isNullOrBlank()) {
                                NeliAuthErrorBox(errorMessage = errorMessage)
                            }
                        }
                    }
                }

                FullPageAuthStep.PHONE_FORM -> {
                    NeliCredentialSubPage(
                        title = if (isPhoneSignUp) "Sign Up with Phone" else "Login with Phone",
                        subtitle = if (isPhoneSignUp) {
                            "Create your NeliTv account using your Username, Phone Number & Password."
                        } else {
                            "Welcome back! Enter your Phone Number or Username and Password."
                        },
                        accentColor = Color(0xFF34D399),
                        isSignUp = isPhoneSignUp,
                        onToggleMode = { signUp ->
                            isPhoneSignUp = signUp
                            onClearError()
                        },
                        username = phoneUsernameInput,
                        onUsernameChange = { phoneUsernameInput = it; onClearError() },
                        identifier = phoneIdentifierInput,
                        onIdentifierChange = { phoneIdentifierInput = it; onClearError() },
                        identifierLabel = if (isPhoneSignUp) "Phone Number" else "Phone Number or Username",
                        identifierPlaceholder = if (isPhoneSignUp) "e.g. 0712345678" else "0712345678 or username",
                        identifierKeyboardType = if (isPhoneSignUp) KeyboardType.Phone else KeyboardType.Text,
                        identifierIcon = Icons.Default.Phone,
                        password = phonePasswordInput,
                        onPasswordChange = { phonePasswordInput = it; onClearError() },
                        isPasswordVisible = isPhonePasswordVisible,
                        onTogglePasswordVisibility = { isPhonePasswordVisible = !isPhonePasswordVisible },
                        submitLabel = if (isPhoneSignUp) "Create Account with Phone" else "Login with Phone",
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onBack = {
                            onClearError()
                            authStep = FullPageAuthStep.CHOOSER
                        },
                        onSubmit = {
                            if (!isLoading) {
                                if (isPhoneSignUp) {
                                    onSignUpWithPhone(
                                        phoneUsernameInput.trim(),
                                        phoneIdentifierInput.trim(),
                                        phonePasswordInput
                                    )
                                } else {
                                    onSignInWithPhone(
                                        phoneIdentifierInput.trim(),
                                        phonePasswordInput
                                    )
                                }
                            }
                        },
                        tagPrefix = "mandatory_phone"
                    )
                }

                FullPageAuthStep.EMAIL_FORM -> {
                    NeliCredentialSubPage(
                        title = if (isEmailSignUp) "Sign Up with Email" else "Login with Email",
                        subtitle = if (isEmailSignUp) {
                            "Create your NeliTv account using your Username, Email & Password."
                        } else {
                            "Welcome back! Enter your Email or Username and Password."
                        },
                        accentColor = NeliGenreCyan,
                        isSignUp = isEmailSignUp,
                        onToggleMode = { signUp ->
                            isEmailSignUp = signUp
                            onClearError()
                        },
                        username = emailUsernameInput,
                        onUsernameChange = { emailUsernameInput = it; onClearError() },
                        identifier = emailIdentifierInput,
                        onIdentifierChange = { emailIdentifierInput = it; onClearError() },
                        identifierLabel = if (isEmailSignUp) "Email Address" else "Email or Username",
                        identifierPlaceholder = if (isEmailSignUp) "name@email.com" else "name@email.com or username",
                        identifierKeyboardType = if (isEmailSignUp) KeyboardType.Email else KeyboardType.Text,
                        identifierIcon = Icons.Default.Email,
                        password = emailPasswordInput,
                        onPasswordChange = { emailPasswordInput = it; onClearError() },
                        isPasswordVisible = isEmailPasswordVisible,
                        onTogglePasswordVisibility = { isEmailPasswordVisible = !isEmailPasswordVisible },
                        submitLabel = if (isEmailSignUp) "Sign Up with Email" else "Login with Email",
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onBack = {
                            onClearError()
                            authStep = FullPageAuthStep.CHOOSER
                        },
                        onSubmit = {
                            if (!isLoading) {
                                if (isEmailSignUp) {
                                    onSignUpWithEmail(
                                        emailUsernameInput.trim(),
                                        emailIdentifierInput.trim(),
                                        emailPasswordInput
                                    )
                                } else {
                                    onSignInWithEmail(
                                        emailIdentifierInput.trim(),
                                        emailPasswordInput
                                    )
                                }
                            }
                        },
                        tagPrefix = "mandatory_email"
                    )
                }
            }
        }
    }
}

@Composable
private fun NeliAuthBrandHeader(isSmallPhone: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isSmallPhone) 82.dp else 94.dp)
                .shadow(22.dp, RoundedCornerShape(24.dp), ambientColor = NeliMagenta, spotColor = NeliGenreCyan)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1038), Color(0xFF0C1530))
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        listOf(NeliWarmOrange, NeliMagenta, Color(0xFF9333EA), NeliGenreCyan, NeliWarmOrange)
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.neli_logo),
                contentDescription = "NeliTv Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(if (isSmallPhone) 62.dp else 72.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        // App Name directly beneath the logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Neli",
                color = Color.White,
                fontSize = if (isSmallPhone) 30.sp else 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp
            )
            Text(
                text = "Tv",
                color = NeliMagenta,
                fontSize = if (isSmallPhone) 30.sp else 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "Verified",
                tint = Color(0xFFFF2E7E),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun NeliCredentialSubPage(
    title: String,
    subtitle: String,
    accentColor: Color,
    isSignUp: Boolean,
    onToggleMode: (Boolean) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    identifierLabel: String,
    identifierPlaceholder: String,
    identifierKeyboardType: KeyboardType,
    identifierIcon: androidx.compose.ui.graphics.vector.ImageVector,
    password: String,
    onPasswordChange: (String) -> Unit,
    isPasswordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    submitLabel: String,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    tagPrefix: String
) {
    val screenProfile = rememberNeliScreenProfile()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (screenProfile.isSmallPhone) 18.dp else 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Back Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF141B35))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("${tagPrefix}_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Sign-In Options",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Back",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.neli_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Neli", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text("Tv", color = NeliMagenta, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Neat Full-Page Form Card
        Surface(
            color = Color(0xFF0E1328),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(listOf(accentColor.copy(alpha = 0.7f), NeliMagenta.copy(alpha = 0.6f))),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFFB8C4DC),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }

                // Segmented Login / Sign Up Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF171E3A))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSignUp) NeliMagenta else Color.Transparent)
                            .clickable { onToggleMode(false) }
                            .padding(vertical = 11.dp)
                            .testTag("${tagPrefix}_login_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Login",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSignUp) NeliMagenta else Color.Transparent)
                            .clickable { onToggleMode(true) }
                            .padding(vertical = 11.dp)
                            .testTag("${tagPrefix}_signup_tab"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign Up",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (isSignUp) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Username", fontSize = 12.sp) },
                        placeholder = { Text("Choose a username", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = accentColor)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${tagPrefix}_username_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = NeliBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NeliSurface,
                            unfocusedContainerColor = NeliSurface
                        )
                    )
                }

                OutlinedTextField(
                    value = identifier,
                    onValueChange = onIdentifierChange,
                    label = { Text(identifierLabel, fontSize = 12.sp) },
                    placeholder = { Text(identifierPlaceholder, fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(identifierIcon, contentDescription = null, tint = accentColor)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = identifierKeyboardType,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${tagPrefix}_identifier_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = NeliBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = NeliSurface,
                        unfocusedContainerColor = NeliSurface
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password", fontSize = 12.sp) },
                    placeholder = { Text("At least 6 characters", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = accentColor)
                    },
                    trailingIcon = {
                        IconButton(onClick = onTogglePasswordVisibility) {
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
                        .testTag("${tagPrefix}_password_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = NeliBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = NeliSurface,
                        unfocusedContainerColor = NeliSurface
                    )
                )

                if (!errorMessage.isNullOrBlank()) {
                    NeliAuthErrorBox(errorMessage = errorMessage)
                }

                Button(
                    onClick = onSubmit,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("${tagPrefix}_submit_button")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = submitLabel,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NeliAuthErrorBox(errorMessage: String) {
    Text(
        text = errorMessage,
        color = Color(0xFFFF8AA4),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x2EFF2E7E))
            .border(1.dp, Color(0x66FF2E7E), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/**
 * Full-Page Welcome Screen shown ONLY to first-time users right after signing up / logging in.
 */
@Composable
fun NeliFirstTimeWelcomeScreen(
    userName: String,
    onStartWatching: () -> Unit
) {
    val cleanName = userName.trim().ifBlank { "Friend" }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF070A1B), Color(0xFF1B0B34), Color(0xFF070A1B))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .testTag("neli_first_time_welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            NeliAuthBrandHeader(isSmallPhone = false)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Welcome, $cleanName!",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Your account is ready. Enjoy unlimited Live TV, Swahili Movies, Animations & Series in Full HD.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF121731))
                    .border(1.dp, NeliMagenta.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WelcomeHighlightRow(
                    icon = Icons.Default.LiveTv,
                    tint = NeliGenreCyan,
                    title = "Live TV & Sports",
                    subtitle = "Azam Sports, Entertainment, Kids & World Channels live in HD."
                )
                WelcomeHighlightRow(
                    icon = Icons.Default.Movie,
                    tint = NeliMagenta,
                    title = "Swahili Movies & Series",
                    subtitle = "Fresh narrated movies, animations & series updated daily."
                )
                WelcomeHighlightRow(
                    icon = Icons.Default.CheckCircle,
                    tint = Color(0xFF34D399),
                    title = "Continue Watching & Offline Downloads",
                    subtitle = "Pick up right where you stopped and watch anytime."
                )
            }

            Button(
                onClick = onStartWatching,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("welcome_start_watching_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Start Watching",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun WelcomeHighlightRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.5.sp, lineHeight = 16.sp)
        }
    }
}

/**
 * Subtle greeting banner shown for returning / updated users who already used the app before.
 * Says "Continue enjoying our app" cleanly instead of re-showing the first-time Welcome page.
 */
@Composable
fun NeliReturningUserContinueBanner(
    visible: Boolean,
    userName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(4_500L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = NeliMagenta)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xF0151A36), Color(0xF02B0E42))
                    )
                )
                .border(1.2.dp, NeliMagenta.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("returning_user_continue_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.neli_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userName.isNotBlank()) "Welcome back, $userName" else "Welcome back to NeliTv",
                    color = NeliGenreCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Continue enjoying our app",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
