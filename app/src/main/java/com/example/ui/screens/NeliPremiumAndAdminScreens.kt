package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import com.example.ui.theme.rememberNeliScreenProfile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import com.example.data.AdminBannerPlacement
import com.example.data.HarakaPayBalanceResponse
import com.example.data.HarakaPayRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
import com.example.data.PaymentService
import com.example.data.SubscriptionPlanType
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.player.CastTvDevice
import com.example.player.NeliCastManager
import com.example.ui.theme.NeliBackground
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliLiveRed
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private enum class PremiumCheckoutStep {
    CHOOSE_PLAN,
    ENTER_PHONE,
    WAITING_VERIFICATION,
    VERIFIED_SUCCESS
}

/**
 * Top Notification Banner for Admin SMS broadcasts.
 * Appears right at the top of the app header whenever Admin publishes an SMS announcement.
 */
@Composable
fun AdminTopSmsNotificationBanner(
    smsMessage: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = smsMessage.isNotBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF991B1B),
                            Color(0xFFBE185D),
                            Color(0xFF1E3A8A)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("admin_sms_top_banner"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_nelitv_app_logo_1790873809763),
                contentDescription = "Tangazo la Admin Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(7.dp))
                    .testTag("admin_sms_banner_logo")
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TANGAZO MUHIMU • NELITV",
                    color = Color(0xFFFDE047),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.7.sp
                )
                Text(
                    text = smsMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * PREMIUM TAB CONTENT:
 * - Clearly notes that the app is currently FREE to use.
 * - Includes Back buttons on EVERY Premium payment page and step.
 * - Offers 3 HarakaPay TZS subscription packages:
 *   1) Kwa Siku Mbili — 1,000 TSh
 *   2) Kwa Wiki — 3,500 TSh
 *   3) Kwa Mwezi — 15,000 TSh
 * - Flow: Choose Plan -> Click Next -> Enter Phone Number -> Click Pay ->
 *   Wait for USSD Push Verification (Auto-confirm polling + Manual "Ready" verification button) ->
 *   Post-Payment Login / Sign Up so the user can log in on any other device and sync their data & Cast profile.
 */
@Composable
fun PremiumTabContent(
    currentUser: UserAccountEntity? = null,
    onNavigateToLoginOrSignUp: () -> Unit = {},
    onBack: () -> Unit = {},
    onSignInUser: (email: String, password: String) -> Unit = { _, _ -> },
    onSignUpUser: (realName: String, email: String, password: String) -> Unit = { _, _, _ -> },
    isAuthLoading: Boolean = false,
    authErrorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val paymentService = remember { PaymentService() }
    val screenProfile = rememberNeliScreenProfile()

    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow
    val detectedDeviceIp = remember(subState.deviceIpAddress) {
        subState.deviceIpAddress.ifBlank { NeliSubscriptionManager.resolveDeviceIpAddress(context) }
    }
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()

    LaunchedEffect(Unit) {
        NeliSubscriptionManager.refreshDeviceIp(context)
    }

    var selectedPlanId by rememberSaveable { mutableStateOf(SubscriptionPlanType.DAILY.id) }
    val selectedPlan = remember(selectedPlanId) {
        SubscriptionPlanType.entries.find { it.id == selectedPlanId } ?: SubscriptionPlanType.DAILY
    }

    // ALWAYS start on CHOOSE_PLAN ("Chagua Kifurushi") every time the user enters the Premium page!
    // Never memorize or jump directly to WAITING_VERIFICATION from a previous session or another account.
    var checkoutStep by remember {
        mutableStateOf(PremiumCheckoutStep.CHOOSE_PLAN)
    }

    // Every account MUST have its own payment number, and every time the user enters Premium they
    // start at CHOOSE_PLAN -> enter a fresh phone number -> then proceed to WAITING_VERIFICATION.
    var phoneInput by remember { mutableStateOf("") }
    var isSubmittingPayment by remember { mutableStateOf(false) }
    var isVerifyingStatus by remember { mutableStateOf(false) }
    var statusFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.email, currentUser?.uid) {
        checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
        phoneInput = ""
        statusFeedbackMessage = null
        isErrorFeedback = false
        NeliSubscriptionManager.clearPendingOrder(context)
        if (currentUser != null && currentUser.email.isNotBlank()) {
            NeliSubscriptionManager.switchActiveAccount(
                context = context,
                uid = currentUser.uid,
                email = currentUser.email,
                realName = currentUser.realName
            )
        }
    }

    val handleStepOrScreenBack: () -> Unit = {
        when (checkoutStep) {
            PremiumCheckoutStep.VERIFIED_SUCCESS -> {
                statusFeedbackMessage = null
                checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
            }
            PremiumCheckoutStep.WAITING_VERIFICATION -> {
                statusFeedbackMessage = null
                checkoutStep = PremiumCheckoutStep.ENTER_PHONE
            }
            PremiumCheckoutStep.ENTER_PHONE -> {
                statusFeedbackMessage = null
                checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
            }
            PremiumCheckoutStep.CHOOSE_PLAN -> {
                onBack()
            }
        }
    }

    BackHandler(onBack = handleStepOrScreenBack)

    // Automatic background verification polling while waiting on WAITING_VERIFICATION step
    LaunchedEffect(checkoutStep, subState.pendingOrderId) {
        val currentOrderId = subState.pendingOrderId
        if (checkoutStep == PremiumCheckoutStep.WAITING_VERIFICATION && currentOrderId.isNotBlank()) {
            while (isActive) {
                delay(4000L)
                val plan = SubscriptionPlanType.entries.find { it.id == subState.pendingPlanId } ?: selectedPlan
                val checkRes = paymentService.verifyOrderStatus(currentOrderId)
                checkRes.onSuccess { statusObj ->
                    if (statusObj.isCompleted) {
                        NeliSubscriptionManager.activateVerifiedSubscription(
                            context = context,
                            plan = plan,
                            phone = subState.pendingPhone.ifBlank { phoneInput },
                            verifiedOrderId = currentOrderId
                        )
                        isErrorFeedback = false
                        statusFeedbackMessage = "Malipo yamekamilika! Umethibitishwa kuwa Premium Member."
                        checkoutStep = PremiumCheckoutStep.VERIFIED_SUCCESS
                        return@LaunchedEffect
                    } else if (statusObj.isFailed) {
                        isErrorFeedback = true
                        statusFeedbackMessage = statusObj.errorMessage.ifBlank { "Malipo yameshindikana (${statusObj.status})." }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .testTag("premium_tab_screen"),
        contentPadding = PaddingValues(
            horizontal = screenProfile.horizontalPadding,
            vertical = screenProfile.verticalPadding
        ),
        verticalArrangement = Arrangement.spacedBy(screenProfile.cardSpacing)
    ) {
        // 0. Single Clean Back Button Header Bar on EVERY Premium Payment Page
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("premium_header_back_bar"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = handleStepOrScreenBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .border(1.dp, NeliBorder, CircleShape)
                        .testTag("premium_top_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Rudi Nyuma (Back)",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (checkoutStep) {
                            PremiumCheckoutStep.CHOOSE_PLAN -> "Chagua Kifurushi cha Premium"
                            PremiumCheckoutStep.ENTER_PHONE -> "Weka Namba ya Simu"
                            PremiumCheckoutStep.WAITING_VERIFICATION -> "Thibitisha Malipo"
                            PremiumCheckoutStep.VERIFIED_SUCCESS -> "Malipo Yamethibitishwa"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = when (checkoutStep) {
                            PremiumCheckoutStep.CHOOSE_PLAN -> "Chagua kifurushi chako (Siku Mbili, Wiki au Mwezi)"
                            PremiumCheckoutStep.ENTER_PHONE -> "${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted}"
                            PremiumCheckoutStep.WAITING_VERIFICATION -> "Kamilisha malipo kwenye simu yako"
                            PremiumCheckoutStep.VERIFIED_SUCCESS -> "Kifurushi chako cha VIP kiko hai sasa"
                        },
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 1. Active Premium Membership Status Card + Post-Payment Login/SignUp Sync Card
        if (isPremiumActive) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_premium_member_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A160B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.testTag("premium_verified_tick_badge")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x3310B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Premium Tick",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "✓ VERIFIED ACCOUNT",
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Premium Member ✓",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        if (currentUser != null) {
                            Text(
                                text = "Mwanachama: ${currentUser.realName} (${currentUser.email})",
                                color = NeliGenreCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (subState.linkedUserEmail.isNotBlank()) {
                            Text(
                                text = "Mwanachama: ${subState.linkedUserName.ifBlank { "VIP User" }} (${subState.linkedUserEmail})",
                                color = NeliGenreCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Kifurushi: ${subState.planTitle} • TZS ${subState.amountTzs}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Simu: ${subState.phoneNumber} • Order ID: ${subState.orderId}",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${subState.remainingDaysOrHoursLabel} (Inaisha: ${subState.formattedExpiryDate})",
                            color = Color(0xFF34D399),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Channels zote ziko WAZI kwako mpaka kifurushi chako kiishe!",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Post-Payment Login / Sign Up Card so user can sync across other devices & Cast
            item {
                PostPaymentLoginOrSignUpCard(
                    currentUser = currentUser,
                    subState = subState,
                    isAuthLoading = isAuthLoading,
                    authErrorMessage = authErrorMessage,
                    onSignInUser = onSignInUser,
                    onSignUpUser = onSignUpUser,
                    onNavigateToAccountTab = onNavigateToLoginOrSignUp
                )
            }
        }

        // 2. Step-by-Step Subscription Flow (Simple & Clean with a Single Top Back Button)
        item {
            when (checkoutStep) {
                PremiumCheckoutStep.CHOOSE_PLAN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SubscriptionPlanType.entries.forEach { plan ->
                            val isSelected = plan.id == selectedPlan.id
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedPlanId = plan.id }
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) NeliMagenta else NeliBorder,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .testTag("premium_plan_${plan.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1F122B) else NeliSurface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = plan.titleSwahili,
                                                color = NeliTextPrimary,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(NeliSurfaceVariant)
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = plan.badgeText,
                                                    color = NeliGenreCyan,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Text(
                                            text = plan.subtitleSwahili,
                                            color = NeliTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = plan.priceFormatted,
                                            color = if (isSelected) NeliMagenta else Color(0xFFFBBF24),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = if (isSelected) "Imechaguliwa ✓" else "Gusa kuchagua",
                                            color = if (isSelected) Color(0xFF34D399) else NeliTextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                NeliSubscriptionManager.refreshDeviceIp(context)
                                // Always clear phoneInput so the user must write their payment phone number for every payment
                                phoneInput = ""
                                statusFeedbackMessage = null
                                checkoutStep = PremiumCheckoutStep.ENTER_PHONE
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("premium_next_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                        ) {
                            Text(
                                text = "Endelea (${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted})",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                PremiumCheckoutStep.ENTER_PHONE -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("premium_enter_phone_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeliSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeliSurfaceVariant)
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Kifurushi ulichochagua:",
                                            color = NeliTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${selectedPlan.titleSwahili} (${selectedPlan.durationLabel})",
                                            color = NeliTextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = selectedPlan.priceFormatted,
                                        color = Color(0xFFFBBF24),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("premium_phone_input"),
                                label = { Text("Namba ya Simu (Mfano: 0712345678)") },
                                placeholder = { Text("0712345678") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = NeliMagenta
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = NeliTextPrimary,
                                    unfocusedTextColor = NeliTextPrimary,
                                    focusedBorderColor = NeliMagenta,
                                    unfocusedBorderColor = NeliBorder
                                )
                            )

                            Text(
                                text = "Utapokea ujumbe wa USSD Push kwenye simu yako (M-Pesa, Tigo Pesa / Mixx, Airtel Money au HaloPesa) kuweka PIN na kukamilisha malipo ya ${selectedPlan.priceFormatted}.",
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )

                            if (!statusFeedbackMessage.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isErrorFeedback) Color(0xFF3B1219) else Color(0xFF0E2923)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isErrorFeedback) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isErrorFeedback) Color(0xFFF87171) else Color(0xFF34D399),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = statusFeedbackMessage!!,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isSubmittingPayment) return@Button
                                    val cleanPhone = PaymentService.normalizePhoneNumber(phoneInput)
                                    if (!PaymentService.isValidPhoneNumber(cleanPhone)) {
                                        isErrorFeedback = true
                                        statusFeedbackMessage = "Tafadhali weka namba sahihi ya simu ya Tanzania (mfano 0712345678)."
                                        return@Button
                                    }
                                    isSubmittingPayment = true
                                    statusFeedbackMessage = null
                                    coroutineScope.launch {
                                        val collectRes = paymentService.initiateUssdPushPayment(
                                            phone = cleanPhone,
                                            amount = selectedPlan.amountTzs,
                                            description = "Nelitv Premium (${selectedPlan.titleSwahili})"
                                        )
                                        isSubmittingPayment = false
                                        collectRes.onSuccess { resp ->
                                            NeliSubscriptionManager.savePendingOrder(
                                                context = context,
                                                plan = selectedPlan,
                                                phone = cleanPhone,
                                                orderId = resp.orderId
                                            )
                                            isErrorFeedback = false
                                            statusFeedbackMessage = "Ombi la malipo limetumwa kwenye simu $cleanPhone! Weka PIN kwenye simu yako."
                                            checkoutStep = PremiumCheckoutStep.WAITING_VERIFICATION
                                        }.onFailure { err ->
                                            isErrorFeedback = true
                                            statusFeedbackMessage = err.message ?: "Imeshindwa kutuma USSD push. Jaribu tena."
                                        }
                                    }
                                },
                                enabled = !isSubmittingPayment,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("premium_pay_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                            ) {
                                if (isSubmittingPayment) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Inatuma USSD Push...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "Lipa ${selectedPlan.priceFormatted} Sasa",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                PremiumCheckoutStep.WAITING_VERIFICATION -> {
                    val pendingPlan = SubscriptionPlanType.entries.find { it.id == subState.pendingPlanId } ?: selectedPlan
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("premium_waiting_verification_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = NeliSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NeliGenreCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )

                            Text(
                                text = "Thibitisha Malipo Kwenye Simu",
                                color = NeliTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Weka PIN yako kwenye ujumbe wa USSD uliotumwa kwenye namba ${subState.pendingPhone.ifBlank { phoneInput }} kulipia ${pendingPlan.titleSwahili} (${pendingPlan.priceFormatted}).",
                                color = NeliTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            if (!statusFeedbackMessage.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isErrorFeedback) Color(0xFF3B1219) else Color(0xFF0E2923)
                                        )
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isErrorFeedback) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isErrorFeedback) Color(0xFFF87171) else Color(0xFF34D399),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = statusFeedbackMessage!!,
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val ordId = subState.pendingOrderId
                                    if (ordId.isBlank() || isVerifyingStatus) return@Button
                                    isVerifyingStatus = true
                                    statusFeedbackMessage = "Inahakiki malipo yako..."
                                    isErrorFeedback = false
                                    coroutineScope.launch {
                                        val checkRes = paymentService.verifyOrderStatus(ordId)
                                        isVerifyingStatus = false
                                        checkRes.onSuccess { statusObj ->
                                            if (statusObj.isCompleted) {
                                                NeliSubscriptionManager.activateVerifiedSubscription(
                                                    context = context,
                                                    plan = pendingPlan,
                                                    phone = subState.pendingPhone.ifBlank { phoneInput },
                                                    verifiedOrderId = ordId
                                                )
                                                isErrorFeedback = false
                                                statusFeedbackMessage = "Malipo yamethibitishwa! Sasa wewe ni Premium Member."
                                                checkoutStep = PremiumCheckoutStep.VERIFIED_SUCCESS
                                            } else {
                                                isErrorFeedback = true
                                                statusFeedbackMessage =
                                                    "Malipo bado hayajakamilika (${statusObj.status}). Weka PIN kwenye simu kisha bonyeza Thibitisha Malipo."
                                            }
                                        }.onFailure { err ->
                                            isErrorFeedback = true
                                            statusFeedbackMessage = err.message ?: "Imeshindikana kuhakiki malipo. Jaribu tena."
                                        }
                                    }
                                },
                                enabled = !isVerifyingStatus,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("premium_ready_verify_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                            ) {
                                if (isVerifyingStatus) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Inathibitisha...", color = Color.White, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Thibitisha Malipo",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                PremiumCheckoutStep.VERIFIED_SUCCESS -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("premium_verified_success_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2923))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(18.dp))
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Verified Tick",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "✓ VERIFIED",
                                    color = Color(0xFF34D399),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Malipo Yamethibitishwa ✓",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Sasa channels zote zimefunguliwa kwako mpaka kifurushi chako kiishe. Login au Sign Up hapa chini ili uweze kutumia kifurushi chako kwenye simu au TV nyingine yoyote.",
                                    color = Color(0xFFA7F3D0),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        phoneInput = ""
                                        checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
                                        onBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                                ) {
                                    Text("Endelea Kuangalia TV", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (!isPremiumActive) {
                            PostPaymentLoginOrSignUpCard(
                                currentUser = currentUser,
                                subState = subState,
                                isAuthLoading = isAuthLoading,
                                authErrorMessage = authErrorMessage,
                                onSignInUser = onSignInUser,
                                onSignUpUser = onSignUpUser,
                                onNavigateToAccountTab = onNavigateToLoginOrSignUp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Post-Payment Login or Sign Up Card:
 * Displayed right after a successful payment so the user can Log In or Sign Up and bind their
 * verified subscription & profile data across all devices and Smart TV Cast sessions.
 */
@Composable
fun PostPaymentLoginOrSignUpCard(
    currentUser: UserAccountEntity?,
    subState: com.example.data.PremiumSubscriptionState,
    isAuthLoading: Boolean,
    authErrorMessage: String?,
    onSignInUser: (email: String, password: String) -> Unit,
    onSignUpUser: (realName: String, email: String, password: String) -> Unit,
    onNavigateToAccountTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLinked = currentUser != null || subState.isLinkedToUserAccount
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var localValidationError by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("post_payment_auth_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141B2D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, NeliGenreCyan.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x2600E5FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isLinked) {
                            "Akaunti Yako Imeunganishwa na VIP & Cast HD ✓"
                        } else {
                            "Login au Sign Up (Baada ya Malipo)"
                        },
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Unganisha akaunti yako ili uweze ku-login kwenye simu au TV nyingine na data zako ziwepo kwenye Cast bila kugomagoma.",
                        color = NeliTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            if (isLinked) {
                val displayName = currentUser?.realName?.ifBlank { null }
                    ?: subState.linkedUserName.ifBlank { "VIP Member" }
                val displayEmail = currentUser?.email?.ifBlank { null }
                    ?: subState.linkedUserEmail
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0E2923))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("post_payment_synced_badge")
                ) {
                    Text(
                        text = "✓ Akaunti Imeunganishwa: $displayName ($displayEmail)\nUnaweza ku-login kwenye kifaa kingine chochote na kifurushi chako cha ${subState.planTitle} pamoja na Cast HD vitaendelea kufanya kazi!",
                        color = Color(0xFFA7F3D0),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            isSignUpMode = false
                            localValidationError = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isSignUpMode) NeliMagenta else NeliSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_payment_login_mode_button")
                    ) {
                        Text("Login", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            isSignUpMode = true
                            localValidationError = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSignUpMode) NeliMagenta else NeliSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_payment_signup_mode_button")
                    ) {
                        Text("Sign Up", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isSignUpMode) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Jina Kamili (Real Name)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary,
                            focusedBorderColor = NeliGenreCyan,
                            unfocusedBorderColor = NeliBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("post_payment_auth_name_input")
                    )
                }

                OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text("Barua Pepe (Email Address)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary,
                        focusedBorderColor = NeliGenreCyan,
                        unfocusedBorderColor = NeliBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_payment_auth_email_input")
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Nywila (Password)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = NeliTextPrimary,
                        unfocusedTextColor = NeliTextPrimary,
                        focusedBorderColor = NeliGenreCyan,
                        unfocusedBorderColor = NeliBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_payment_auth_password_input")
                )

                val errToShow = localValidationError ?: authErrorMessage
                if (!errToShow.isNullOrBlank()) {
                    Text(
                        text = errToShow,
                        color = Color(0xFFF87171),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cleanEmail = emailInput.trim()
                            val cleanPass = passwordInput.trim()
                            if (cleanEmail.isBlank() || cleanPass.length < 4) {
                                localValidationError = "Tafadhali weka Email sahihi na Password (angalau herufi 4)."
                                return@Button
                            }
                            localValidationError = null
                            if (isSignUpMode) {
                                val cleanName = nameInput.trim().ifBlank { cleanEmail.substringBefore("@") }
                                onSignUpUser(cleanName, cleanEmail, cleanPass)
                            } else {
                                onSignInUser(cleanEmail, cleanPass)
                            }
                        },
                        enabled = !isAuthLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_payment_auth_submit_button")
                    ) {
                        Text(
                            text = if (isSignUpMode) "Sign Up & Hifadhi Data" else "Login & Hifadhi Data",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToAccountTab,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("post_payment_go_to_account_button")
                    ) {
                        Text(
                            text = "Fungua Account Tab",
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * MINI ADMIN PANEL SCREEN:
 * - Strictly accessible ONLY when logged in with `Admin@login.com` (Password `123456`).
 * - Includes Back button (`admin_panel_back_button`) and hardware BackHandler.
 * - Allows Admin to:
 *   1) Write SMS announcements that appear on the Top Notification Bar & trigger device notifications.
 *   2) Lock / Unlock all channels or individual channels for Free Users (while Premium Members always keep access).
 *   3) Add new Live TV channels immediately to the app catalog.
 *   4) Check HarakaPay Merchant Wallet & Float balance in TZS.
 */
@Composable
fun MiniAdminPanelScreen(
    currentUser: UserAccountEntity?,
    allChannels: List<LiveChannel>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isAdmin = NeliAdminManager.isAdminUser(currentUser)

    BackHandler(onBack = onBack)

    if (!isAdmin) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(NeliBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = NeliLiveRed,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "Admin Access Only",
                    color = NeliTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Panel hii inaonekana kwa Admin pekee baada ya ku-login kwa Admin@login.com.",
                    color = NeliTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onBack) {
                    Text("Rudi Nyuma")
                }
            }
        }
        return
    }

    val activeSmsObj by NeliAdminManager.activeAdminSms.collectAsState()
    val currentSms = activeSmsObj?.message.orEmpty()
    val bannerPlacement by NeliAdminManager.adminBannerPlacement.collectAsState()
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val hiddenChannelIds by NeliAdminManager.hiddenChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()
    val customAddedChannels by NeliAdminManager.customAddedChannels.collectAsState()
    val adminAllChannels = remember(allChannels, customAddedChannels, hiddenChannelIds) {
        com.example.data.ChannelRepository.getAllChannelsIncludingHiddenForAdmin()
    }

    var smsInput by rememberSaveable { mutableStateOf(currentSms) }
    var sendPushNotification by rememberSaveable { mutableStateOf(true) }
    var adminFeedback by remember { mutableStateOf<String?>(null) }

    // New channel inputs
    var newChannelName by rememberSaveable { mutableStateOf("") }
    var newChannelStreamUrl by rememberSaveable { mutableStateOf("") }
    var newChannelLogoUrl by rememberSaveable { mutableStateOf("") }
    var newChannelCategory by rememberSaveable { mutableStateOf("Sports") }
    var newChannelDesc by rememberSaveable { mutableStateOf("") }
    var lockNewChannelForFree by rememberSaveable { mutableStateOf(false) }

    // HarakaPay Balance state
    var balanceState by remember { mutableStateOf<HarakaPayBalanceResponse?>(null) }
    var isLoadingBalance by remember { mutableStateOf(false) }

    val categories = remember {
        listOf("Sports", "Azam TV", "Entertainment", "Movies", "News", "Kids", "Music", "Tanzania")
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NeliBackground)
            .testTag("mini_admin_panel_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header with Back Button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NeliSurfaceVariant)
                        .testTag("admin_panel_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Account",
                        tint = NeliTextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mini Admin Panel",
                        color = NeliTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Logged in as ${currentUser?.email ?: "Admin@login.com"}",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (!adminFeedback.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0E2923))
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34D399),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = adminFeedback!!,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 1. Admin SMS Notification Bar & Push Broadcast Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeliSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24)
                        )
                        Text(
                            text = "1. Add au Futa Notification (Juu au Chini ya Slider)",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "Tangazo hili linakaa Juu ya Slider au Chini ya Slider kwenye ukurasa wa Home pekee (sio kwenye kurasa zingine) na pia linaweza kwenda kama Push Notification.",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )

                    if (currentSms.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F122B))
                                .border(1.dp, NeliMagenta.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Current Notification (${bannerPlacement.labelSwahili}):",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentSms,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = {
                                    smsInput = ""
                                    NeliAdminManager.clearAdminSms(context)
                                    adminFeedback = "Current notification imefutwa kikamilifu."
                                },
                                modifier = Modifier.testTag("admin_delete_active_sms_icon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Futa Current Notification",
                                    tint = NeliLiveRed
                                )
                            }
                        }
                    }

                    // Position Selector: Above Slider vs Below Slider (Home Page Only)
                    Text(
                        text = "Nafasi ya Tangazo kwenye Home Page:",
                        color = NeliTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AdminBannerPlacement.entries.forEach { option ->
                            val isSelected = bannerPlacement == option
                            val tag = if (option == AdminBannerPlacement.ABOVE_SLIDER) {
                                "admin_banner_above_slider_chip"
                            } else {
                                "admin_banner_below_slider_chip"
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) NeliMagenta else NeliSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isSelected) NeliMagenta else NeliBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        NeliAdminManager.setAdminBannerPlacement(context, option)
                                        adminFeedback = "Tangazo litakaa: ${option.labelSwahili} (Home pekee)"
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                                    .testTag(tag),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = option.labelSwahili,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = smsInput,
                        onValueChange = { smsInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sms_input"),
                        label = { Text("Andika Notification / Tangazo") },
                        placeholder = { Text("Mfano: Mechi ya Yanga vs Simba ipo LIVE Azam Sports 1 HD sasa hivi!") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = NeliTextPrimary,
                            unfocusedTextColor = NeliTextPrimary,
                            focusedBorderColor = NeliMagenta,
                            unfocusedBorderColor = NeliBorder
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tuma pia kama Push Notification kwenye simu",
                            color = NeliTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = sendPushNotification,
                            onCheckedChange = { sendPushNotification = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeliMagenta)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                if (smsInput.isNotBlank()) {
                                    NeliAdminManager.publishAdminSms(
                                        context = context,
                                        messageText = smsInput,
                                        sendPushNotification = sendPushNotification,
                                        placement = bannerPlacement
                                    )
                                    adminFeedback = "Notification imeongezwa (${bannerPlacement.labelSwahili} kwenye Home)!"
                                } else {
                                    adminFeedback = "Tafadhali andika ujumbe wa notification kwanza."
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_publish_sms_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Notification", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                smsInput = ""
                                NeliAdminManager.clearAdminSms(context)
                                adminFeedback = "Current notification imefutwa."
                            },
                            modifier = Modifier.testTag("admin_clear_sms_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = NeliLiveRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Futa Notification", color = NeliLiveRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Add New Live TV Channel Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeliSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = NeliGenreCyan
                        )
                        Text(
                            text = "2. Ongeza Channel Mpya (Add Channel)",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = { newChannelName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_name_input"),
                        label = { Text("Jina la Channel (Mfano: Azam Sports 4 HD)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newChannelStreamUrl,
                        onValueChange = { newChannelStreamUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_stream_url_input"),
                        label = { Text("Stream URL (.m3u8 / .mpd / .mp4)") },
                        placeholder = { Text("https://example.com/live/stream.m3u8") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newChannelLogoUrl,
                        onValueChange = { newChannelLogoUrl = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_channel_logo_url_input"),
                        label = { Text("Logo URL (Hiari)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newChannelDesc,
                        onValueChange = { newChannelDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Maelezo ya Kipindi / Channel (Hiari)") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Kategoria ya Channel:",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        categories.forEach { cat ->
                            val selected = cat.equals(newChannelCategory, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) NeliMagenta else NeliSurfaceVariant)
                                    .clickable { newChannelCategory = cat }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Funga channel hii kwa Free Users (Premium Only)",
                            color = NeliTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = lockNewChannelForFree,
                            onCheckedChange = { lockNewChannelForFree = it },
                            modifier = Modifier.testTag("admin_new_channel_lock_switch")
                        )
                    }

                    Button(
                        onClick = {
                            val addRes = NeliAdminManager.addChannelByAdmin(
                                context = context,
                                name = newChannelName,
                                streamUrl = newChannelStreamUrl,
                                thumbnailUrl = newChannelLogoUrl,
                                category = newChannelCategory,
                                description = newChannelDesc,
                                lockForFreeUsers = lockNewChannelForFree
                            )
                            addRes.onSuccess { added ->
                                adminFeedback = "Channel '${added.name}' imeongezwa kikamilifu kwenye Live TV!"
                                newChannelName = ""
                                newChannelStreamUrl = ""
                                newChannelLogoUrl = ""
                                newChannelDesc = ""
                            }.onFailure { err ->
                                adminFeedback = err.message ?: "Weka Jina la Channel na Stream URL kwanza."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_add_channel_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeliGenreCyan)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Channel kwenye App",
                            color = Color.Black,
                            fontWeight = FontWeight.Black
                        )
                    }

                    if (customAddedChannels.isNotEmpty()) {
                        HorizontalDivider(color = NeliBorder)
                        Text(
                            text = "Channels Zilizoongezwa na Admin (${customAddedChannels.size}) — Zinaweza Kufichwa tu (Hazifutwi):",
                            color = NeliTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        customAddedChannels.forEach { customCh ->
                            val isHidden = hiddenChannelIds.contains(customCh.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NeliSurfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = customCh.name,
                                        color = NeliTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isHidden) {
                                            "${customCh.category} • IMEFICHWA (Hidden)"
                                        } else {
                                            "${customCh.category} • INAONEKANA (Visible)"
                                        },
                                        color = if (isHidden) Color(0xFFFBBF24) else Color(0xFF34D399),
                                        fontSize = 11.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val nowHidden = NeliAdminManager.toggleChannelHidden(context, customCh.id)
                                        adminFeedback = if (nowHidden) {
                                            "Channel '${customCh.name}' imefichwa (Hidden)."
                                        } else {
                                            "Channel '${customCh.name}' imerudishwa hewani (Visible)."
                                        }
                                    },
                                    modifier = Modifier.testTag("admin_hide_channel_${customCh.id}")
                                ) {
                                    Icon(
                                        imageVector = if (isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isHidden) "Onyesha Channel" else "Ficha Channel",
                                        tint = if (isHidden) Color(0xFF34D399) else Color(0xFFFBBF24),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isHidden) "Onyesha" else "Ficha",
                                        color = if (isHidden) Color(0xFF34D399) else Color(0xFFFBBF24),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Lock / Unlock Channels for Free Users Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeliSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeliLiveRed
                        )
                        Text(
                            text = "3. Funga / Fungua Channels (Free vs Premium)",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "Ukifunga channel moja au zote hapa, Free Users wataombwa kulipia Premium. Kwa Premium Members waliodhibitishwa, channels zote zitaendelea kuwa WAZI mpaka kifurushi kiishe.",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeliSurfaceVariant)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Funga Channels ZOTE kwa Free Users",
                                color = NeliTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (lockAllForFree) {
                                    "ZIMEFUNGWA ZOTE kwa Free Users (Premium pekee)"
                                } else {
                                    "Ziko wazi (isipokuwa ulizofunga moja moja hapa chini)"
                                },
                                color = if (lockAllForFree) Color(0xFFFBBF24) else Color(0xFF34D399),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = lockAllForFree,
                            onCheckedChange = { enabled ->
                                NeliAdminManager.setLockAllChannels(context, enabled)
                                adminFeedback = if (enabled) {
                                    "Channels ZOTE zimefungwa kwa Free Users! Premium Members bado wanaona zote."
                                } else {
                                    "Channels zimefunguliwa kwa Free Users!"
                                }
                            },
                            modifier = Modifier.testTag("admin_lock_all_channels_switch"),
                            colors = SwitchDefaults.colors(checkedThumbColor = NeliLiveRed)
                        )
                    }
                }
            }
        }

        // Individual Channels Lock & Hide List (No channel can be deleted — only hidden or locked)
        items(
            items = adminAllChannels,
            key = { ch -> "admin_lock_row_${ch.id}" }
        ) { channel ->
            val isChannelLocked = lockAllForFree || lockedChannelIds.contains(channel.id)
            val isChannelHidden = hiddenChannelIds.contains(channel.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeliSurface)
                    .border(
                        width = 1.dp,
                        color = when {
                            isChannelHidden -> Color(0xFFF59E0B).copy(alpha = 0.6f)
                            isChannelLocked -> NeliLiveRed.copy(alpha = 0.5f)
                            else -> NeliBorder
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when {
                            isChannelHidden -> Icons.Default.VisibilityOff
                            isChannelLocked -> Icons.Default.Lock
                            else -> Icons.Default.LockOpen
                        },
                        contentDescription = null,
                        tint = when {
                            isChannelHidden -> Color(0xFFF59E0B)
                            isChannelLocked -> NeliLiveRed
                            else -> Color(0xFF34D399)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = channel.name,
                            color = NeliTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isChannelHidden -> "${channel.category} • IMEFICHWA (Hidden)"
                                isChannelLocked -> "${channel.category} • IMEFUNGWA (Free Users)"
                                else -> "${channel.category} • WAZI (Free & Premium)"
                            },
                            color = if (isChannelHidden || isChannelLocked) Color(0xFFFBBF24) else NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            val nowHidden = NeliAdminManager.toggleChannelHidden(context, channel.id)
                            adminFeedback = if (nowHidden) {
                                "${channel.name} imefichwa (Hidden)."
                            } else {
                                "${channel.name} inaonekana sasa (Visible)."
                            }
                        },
                        modifier = Modifier.testTag("admin_toggle_hide_${channel.id}")
                    ) {
                        Icon(
                            imageVector = if (isChannelHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isChannelHidden) "Onyesha Channel" else "Ficha Channel",
                            tint = if (isChannelHidden) Color(0xFF34D399) else Color(0xFFFBBF24)
                        )
                    }

                    Switch(
                        checked = isChannelLocked,
                        onCheckedChange = { shouldLock ->
                            NeliAdminManager.setSingleChannelLock(context, channel.id, shouldLock)
                            adminFeedback = if (shouldLock) {
                                "${channel.name} imefungwa kwa Free Users."
                            } else {
                                "${channel.name} imefunguliwa."
                            }
                        },
                        modifier = Modifier.testTag("admin_toggle_lock_${channel.id}")
                    )
                }
            }
        }

        // 4. HarakaPay Merchant Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NeliSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color(0xFF34D399)
                            )
                            Text(
                                text = "4. Salio la HarakaPay (TZS)",
                                color = NeliTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        IconButton(
                            onClick = {
                                if (isLoadingBalance) return@IconButton
                                isLoadingBalance = true
                                coroutineScope.launch {
                                    balanceState = HarakaPayRepository.getBalance().getOrNull()
                                    isLoadingBalance = false
                                }
                            },
                            modifier = Modifier.testTag("admin_refresh_balance_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Angalia Salio",
                                tint = NeliGenreCyan
                            )
                        }
                    }

                    if (isLoadingBalance) {
                        Text("Inaangalia salio HarakaPay...", color = NeliTextSecondary, fontSize = 12.sp)
                    } else if (balanceState != null) {
                        val bal = balanceState!!
                        if (bal.success) {
                            Text(
                                text = "Wallet Balance: TZS ${bal.walletBalance} • Float: TZS ${bal.floatBalance}",
                                color = Color(0xFF34D399),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = bal.errorMessage.ifBlank { "Imeshindwa kusoma salio." },
                                color = NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Gusa kitufe cha Refresh kuangalia Wallet Balance na Float Balance yako ya HarakaPay.",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * SMART TV CAST MODAL DIALOG:
 * Allows the user to cast Live TV channels from the top header Cast button or Player screen
 * onto a Large Smart TV (Chromecast, Miracast, Google TV, Android TV, Hisense/Samsung/LG Smart TV).
 */
@Composable
fun NeliCastModalSheet(
    availableChannels: List<LiveChannel>,
    currentChannel: LiveChannel? = null,
    onDismiss: () -> Unit,
    onSelectChannelToWatchAndCast: (LiveChannel) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val connectedDevice by NeliCastManager.connectedDevice.collectAsState()
    val isCasting = connectedDevice != null
    val connectedTvName = connectedDevice?.name
    val castingChannel by NeliCastManager.castingChannel.collectAsState()
    val discoveredDevices by NeliCastManager.availableDevices.collectAsState()
    val castStatusMessage by NeliCastManager.statusMessage.collectAsState()
    val castUserName by NeliCastManager.userDisplayName.collectAsState()
    val castUserEmail by NeliCastManager.userEmail.collectAsState()
    val castSubBadge by NeliCastManager.userSubscriptionBadge.collectAsState()
    val castDeviceIp by NeliCastManager.deviceIpAddress.collectAsState()
    val castStreamQuality by NeliCastManager.castStreamQuality.collectAsState()
    val castBufferHealth by NeliCastManager.castBufferHealthPercent.collectAsState()
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    var isSearchingTv by remember { mutableStateOf(true) }

    var selectedCastChannel by remember(currentChannel, castingChannel, availableChannels) {
        mutableStateOf(castingChannel ?: currentChannel ?: availableChannels.firstOrNull())
    }

    val triggerTvSearch: () -> Unit = {
        coroutineScope.launch {
            isSearchingTv = true
            NeliCastManager.refreshAvailableTvDevices(context)
            delay(550L)
            isSearchingTv = false
        }
    }

    LaunchedEffect(Unit) {
        isSearchingTv = true
        NeliCastManager.refreshAvailableTvDevices(context)
        delay(550L)
        isSearchingTv = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .testTag("cast_modal_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF101524)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(22.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCasting) Color(0x2234D399) else Color(0x2200E5FF)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
                                contentDescription = "Cast to Large TV",
                                tint = if (isCasting) Color(0xFF34D399) else NeliGenreCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Cast Kwenye Large TV",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (isCasting && !connectedTvName.isNullOrBlank()) {
                                    "Imeunganishwa: $connectedTvName"
                                } else {
                                    "Unganisha simu yako na Smart TV uangalie vipindi vya TV"
                                },
                                color = if (isCasting) Color(0xFF34D399) else NeliTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("cast_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Funga",
                            tint = NeliTextSecondary
                        )
                    }
                }

                // User Account & Subscription Info on Cast Card
                val effectiveUserName = castUserName.ifBlank { subState.linkedUserName }.ifBlank { "Nelitv Viewer" }
                val effectiveUserEmail = castUserEmail.ifBlank { subState.linkedUserEmail }.ifBlank { "Auto Device IP Profile" }
                val effectiveIp = castDeviceIp.ifBlank { subState.deviceIpAddress }.ifBlank {
                    NeliSubscriptionManager.resolveDeviceIpAddress(context)
                }
                val effectiveBadge = if (subState.isActiveNow) {
                    "Premium VIP (${subState.planTitle}) ✓"
                } else {
                    castSubBadge
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151E32))
                        .border(1.dp, NeliGenreCyan.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .testTag("cast_user_info_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "$effectiveUserName • $effectiveUserEmail",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = effectiveBadge,
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // High-Quality Anti-Stutter Cast Stream Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121A2A))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                        .testTag("cast_quality_anti_stutter_card"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Stream Quality: $castStreamQuality ($castBufferHealth% Buffer)",
                                color = Color(0xFFA7F3D0),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(NeliMagenta)
                                .clickable { NeliCastManager.triggerCastStreamBoost() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("cast_anti_stutter_boost_button")
                        ) {
                            Text(
                                text = "Anti-Stutter HD ✓",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NeliCastManager.availableQualityPresets.forEachIndexed { index, preset ->
                            val isSelectedQuality = castStreamQuality == preset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelectedQuality) Color(0xFF065F46) else NeliSurfaceVariant)
                                    .border(
                                        1.dp,
                                        if (isSelectedQuality) Color(0xFF34D399) else NeliBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { NeliCastManager.setCastStreamQuality(preset) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                    .testTag("cast_quality_chip_$index")
                            ) {
                                Text(
                                    text = preset,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelectedQuality) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                if (!castStatusMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF17223B))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = castStatusMessage!!,
                            color = NeliGenreCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Channel selector for casting
                Text(
                    text = "Chagua Kipindi / Channel ya Ku-Cast kwenye TV:",
                    color = NeliTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableChannels.take(12).forEach { ch ->
                        val isSelected = selectedCastChannel?.id == ch.id
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeliMagenta else NeliSurfaceVariant)
                                .clickable {
                                    selectedCastChannel = ch
                                    if (isCasting) {
                                        NeliCastManager.updateCastingChannel(ch)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("cast_select_channel_${ch.id}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = ch.name,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Discovered TV devices list (only real discovered TVs, no hardcoded room TVs)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isSearchingTv) {
                            "Inatafuta TV zilizo karibu..."
                        } else {
                            "TV Zilizopatikana (${discoveredDevices.size}):"
                        },
                        color = NeliTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = triggerTvSearch,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("cast_search_again_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan TVs",
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isSearchingTv) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeliSurface)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = NeliGenreCyan,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Inatafuta Smart TV / Wireless Display zilizo wazi kwenye Wi-Fi...",
                            color = NeliTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                } else if (discoveredDevices.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeliSurface)
                            .border(1.dp, NeliBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                            .testTag("cast_no_devices_found_box"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = NeliTextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "Hakuna TV iliyogunduliwa bado.",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Washa Smart TV / Cast kwenye TV yako (Wi-Fi moja na simu), kisha bonyeza 'Tafuta TV Tena' hapa chini. TV itakayoonekana utai-tap ili ku-connect moja kwa moja.",
                            color = NeliTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = triggerTvSearch,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tafuta TV Tena (Search)", fontSize = 12.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        discoveredDevices.forEach { device: CastTvDevice ->
                            val isThisDeviceConnected = isCasting && connectedTvName == device.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isThisDeviceConnected) Color(0xFF0E2923) else NeliSurface
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isThisDeviceConnected) Color(0xFF10B981) else NeliBorder,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        NeliCastManager.connectAndCastToTv(
                                            device = device,
                                            channel = selectedCastChannel
                                        )
                                        selectedCastChannel?.let { onSelectChannelToWatchAndCast(it) }
                                    }
                                    .padding(12.dp)
                                    .testTag("cast_device_item_${device.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isThisDeviceConnected) Icons.Default.CastConnected else Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = if (isThisDeviceConnected) Color(0xFF34D399) else NeliGenreCyan
                                    )
                                    Column {
                                        Text(
                                            text = device.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${device.protocol} • ${device.subtitle}",
                                            color = NeliTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = if (isThisDeviceConnected) "Connected ✓" else "Tap to Connect",
                                    color = if (isThisDeviceConnected) Color(0xFF34D399) else NeliMagenta,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }

                // Action buttons: Watch & Cast / System Wireless Display Settings / Disconnect
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            NeliCastManager.openSystemCastSettings(context)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cast_open_system_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsRemote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Wireless TV Settings", fontSize = 12.sp)
                    }

                    if (isCasting) {
                        Button(
                            onClick = {
                                NeliCastManager.disconnectCast()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeliLiveRed),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cast_disconnect_button")
                        ) {
                            Text("Stop Casting", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (selectedCastChannel != null) {
                        Button(
                            onClick = {
                                val targetDevice = discoveredDevices.firstOrNull()
                                if (targetDevice != null) {
                                    NeliCastManager.connectAndCastToTv(targetDevice, selectedCastChannel)
                                }
                                selectedCastChannel?.let { onSelectChannelToWatchAndCast(it) }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cast_start_watching_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cast & Play Now", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog displayed when a Free User clicks on a Live TV channel that has been locked by the Admin.
 * Includes a Back button at the top and bottom as well as updated subscription package prices.
 */
@Composable
fun LockedChannelPremiumDialog(
    channel: LiveChannel,
    onDismiss: () -> Unit,
    onGoToPremium: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("locked_channel_premium_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF121726))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(20.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NeliSurfaceVariant)
                            .testTag("locked_dialog_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Rudi Nyuma",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Premium Payment Required",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(40.dp))
                }

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0x22F59E0B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Channel Locked",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "${channel.name} Imefungwa kwa Free Users",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Channel hii imefungwa na Admin kwa watumiaji wa bure. Jiunge na Premium sasa (Kwa Siku Mbili 1,000 TSh, Kwa Wiki 3,500 TSh, au Kwa Mwezi 15,000 TSh) kupitia HarakaPay ili kufungua channels zote papo hapo!",
                    color = NeliTextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onGoToPremium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("locked_dialog_go_premium_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                ) {
                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Jiunge na Premium (1,000 TSh)",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}
