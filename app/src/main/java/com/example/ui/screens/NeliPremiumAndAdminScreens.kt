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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
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
import com.example.data.HarakaPayBalanceResponse
import com.example.data.HarakaPayRepository
import com.example.data.NeliAdminManager
import com.example.data.NeliSubscriptionManager
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
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = "Tangazo la Admin",
                    tint = Color(0xFFFDE047),
                    modifier = Modifier.size(16.dp)
                )
            }
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
 * - Offers 3 HarakaPay TZS subscription packages:
 *   1) Kwa Siku — 500 TSh
 *   2) Kwa Wiki — 3,000 TSh
 *   3) Kwa Mwezi — 10,000 TSh
 * - Flow: Choose Plan -> Click Next -> Enter Phone Number -> Click Pay ->
 *   Wait for USSD Push Verification (Auto-confirm polling + Manual "Ready" verification button).
 * - Only grants Premium Member status once payment is strictly verified (`completed`) by HarakaPay.
 */
@Composable
fun PremiumTabContent(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()
    val isPremiumActive = subState.isActiveNow
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()

    var selectedPlanId by rememberSaveable { mutableStateOf(SubscriptionPlanType.DAILY.id) }
    val selectedPlan = remember(selectedPlanId) {
        SubscriptionPlanType.entries.find { it.id == selectedPlanId } ?: SubscriptionPlanType.DAILY
    }

    var checkoutStep by rememberSaveable {
        mutableStateOf(
            if (subState.pendingOrderId.isNotBlank() && !isPremiumActive) {
                PremiumCheckoutStep.WAITING_VERIFICATION
            } else {
                PremiumCheckoutStep.CHOOSE_PLAN
            }
        )
    }

    var phoneInput by rememberSaveable { mutableStateOf(subState.pendingPhone) }
    var isSubmittingPayment by remember { mutableStateOf(false) }
    var isVerifyingStatus by remember { mutableStateOf(false) }
    var statusFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isErrorFeedback by remember { mutableStateOf(false) }

    // Keep step synced if pendingOrderId appears
    LaunchedEffect(subState.pendingOrderId) {
        if (subState.pendingOrderId.isNotBlank() && checkoutStep == PremiumCheckoutStep.ENTER_PHONE) {
            checkoutStep = PremiumCheckoutStep.WAITING_VERIFICATION
        }
    }

    // Automatic background verification polling while waiting on WAITING_VERIFICATION step
    LaunchedEffect(checkoutStep, subState.pendingOrderId) {
        val currentOrderId = subState.pendingOrderId
        if (checkoutStep == PremiumCheckoutStep.WAITING_VERIFICATION && currentOrderId.isNotBlank()) {
            while (isActive) {
                delay(4000L)
                val plan = SubscriptionPlanType.entries.find { it.id == subState.pendingPlanId } ?: selectedPlan
                val checkRes = HarakaPayRepository.checkPaymentStatus(currentOrderId)
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Free App Notice Banner + Premium Header
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("premium_free_notice_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2E))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x2200E5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Premium",
                                tint = NeliGenreCyan,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nelitv VIP & Premium",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Malipo ya Haraka kwa Simu (HarakaPay TZS)",
                                color = NeliGenreCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0E2923))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "NOTE: Kwa sasa App ni FREE kabisa kutumia! Hata hivyo, ukiwa Premium Member unafungua channels zote hata kama Admin amezifunga kwa watumiaji wa bure mpaka kifurushi chako kiishe.",
                            color = Color(0xFFA7F3D0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (lockAllForFree || lockedChannelIds.isNotEmpty()) {
                        val lockSummary = if (lockAllForFree) {
                            "Channels zote zimefungwa kwa Free Users — Jiunge na Premium kufungua zote sasa!"
                        } else {
                            "Channels ${lockedChannelIds.size} zimefungwa kwa Free Users — Premium inafungua zote!"
                        }
                        Text(
                            text = lockSummary,
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 2. Active Premium Membership Status Card (if user has verified active subscription)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified Premium",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "PREMIUM MEMBER (VERIFIED)",
                                color = Color(0xFFFBBF24),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
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
                            text = "Channels zote ziko WAZI kwako hata kama Admin akizifunga kwa Free Users!",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 3. Step-by-Step Subscription Flow
        item {
            when (checkoutStep) {
                PremiumCheckoutStep.CHOOSE_PLAN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Hatua ya 1: Chagua Kifurushi cha Premium",
                            color = NeliTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

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
                                text = "Next • Endelea (${selectedPlan.titleSwahili} - ${selectedPlan.priceFormatted})",
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Hatua ya 2: Weka Namba ya Simu",
                                    color = NeliTextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                OutlinedButton(
                                    onClick = {
                                        statusFeedbackMessage = null
                                        checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
                                    },
                                    modifier = Modifier.testTag("premium_step_back_button")
                                ) {
                                    Text("Badili Kifurushi", fontSize = 12.sp)
                                }
                            }

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
                                    val cleanPhone = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                    if (!HarakaPayRepository.isValidTzPhoneNumber(cleanPhone)) {
                                        isErrorFeedback = true
                                        statusFeedbackMessage = "Tafadhali weka namba sahihi ya simu ya Tanzania (mfano 0712345678)."
                                        return@Button
                                    }
                                    isSubmittingPayment = true
                                    statusFeedbackMessage = null
                                    coroutineScope.launch {
                                        val collectRes = HarakaPayRepository.collectPayment(
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
                                        text = "Pay • Lipa ${selectedPlan.priceFormatted} Sasa",
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
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NeliGenreCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(42.dp)
                            )

                            Text(
                                text = "Inasubiri Uthibitisho wa Malipo...",
                                color = NeliTextPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Ujumbe wa USSD umetumwa kwenye simu ${subState.pendingPhone.ifBlank { phoneInput }} kwa kiasi cha ${pendingPlan.priceFormatted} (Order ID: ${subState.pendingOrderId.ifBlank { "---" }}).",
                                color = NeliTextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeliSurfaceVariant)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "1. Weka PIN yako ya malipo kwenye ujumbe ulioingia kwenye simu.\n2. App inafanya Auto-Confirm yenyewe mara tu ukilipa.\n3. Ikiwa umeshalipa kwenye simu na haijabadilika bado, bonyeza kitufe cha 'Ready (Thibitisha Malipo)' hapa chini.",
                                    color = NeliTextPrimary,
                                    fontSize = 12.sp
                                )
                            }

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
                                    statusFeedbackMessage = "Inahakiki malipo yako HarakaPay..."
                                    isErrorFeedback = false
                                    coroutineScope.launch {
                                        val checkRes = HarakaPayRepository.checkPaymentStatus(ordId)
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
                                                    "Malipo bado hayajakamilika (Status: ${statusObj.status}). Kama bado hujalipa kwenye simu, weka PIN kwanza kisha bonyeza Ready."
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
                                        text = "Ready • Nimeshalipa (Thibitisha Malipo)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    NeliSubscriptionManager.clearPendingOrder(context)
                                    statusFeedbackMessage = null
                                    checkoutStep = PremiumCheckoutStep.ENTER_PHONE
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("premium_cancel_pending_button")
                            ) {
                                Text("Badili Namba ya Simu / Anza Upya", color = NeliTextSecondary)
                            }
                        }
                    }
                }

                PremiumCheckoutStep.VERIFIED_SUCCESS -> {
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
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(54.dp)
                            )
                            Text(
                                text = "Hongera! Wewe sasa ni Premium Member",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Malipo yako yamethibitishwa kikamilifu. Channels zote za Live TV sasa ziko WAZI kwako bila kikomo!",
                                color = Color(0xFFA7F3D0),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    checkoutStep = PremiumCheckoutStep.CHOOSE_PLAN
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                            ) {
                                Text("Sawa, Endelea Kuangalia TV", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
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
    val lockedChannelIds by NeliAdminManager.lockedChannelIds.collectAsState()
    val lockAllForFree by NeliAdminManager.areAllChannelsLocked.collectAsState()
    val customAddedChannels by NeliAdminManager.customAddedChannels.collectAsState()

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
                            text = "1. Andika SMS / Notification ya Juu ya App",
                            color = NeliTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "SMS hii itapita juu kabisa ya App kama Notification Bar na pia inaenda kama Notification kwenye simu za watumiaji.",
                        color = NeliTextSecondary,
                        fontSize = 12.sp
                    )

                    OutlinedTextField(
                        value = smsInput,
                        onValueChange = { smsInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_sms_input"),
                        label = { Text("Ujumbe wa SMS / Tangazo la Juu") },
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
                                        sendPushNotification = sendPushNotification
                                    )
                                    adminFeedback = "SMS imechapishwa juu ya App na kutumwa kama Notification!"
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_publish_sms_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tuma SMS Juu", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (currentSms.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    smsInput = ""
                                    NeliAdminManager.clearAdminSms(context)
                                    adminFeedback = "SMS ya juu ya App imeondolewa."
                                },
                                modifier = Modifier.testTag("admin_clear_sms_button")
                            ) {
                                Text("Futa SMS", color = NeliLiveRed)
                            }
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
                            text = "Channels Zilizoongezwa na Admin (${customAddedChannels.size}):",
                            color = NeliTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        customAddedChannels.forEach { customCh ->
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
                                        text = customCh.category,
                                        color = NeliTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        NeliAdminManager.removeAdminChannel(context, customCh.id)
                                        adminFeedback = "Channel '${customCh.name}' imeondolewa."
                                    },
                                    modifier = Modifier.testTag("admin_remove_channel_${customCh.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Futa Channel",
                                        tint = NeliLiveRed
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

        // Individual Channels Lock List
        items(
            items = allChannels,
            key = { ch -> "admin_lock_row_${ch.id}" }
        ) { channel ->
            val isChannelLocked = lockAllForFree || lockedChannelIds.contains(channel.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeliSurface)
                    .border(
                        width = 1.dp,
                        color = if (isChannelLocked) NeliLiveRed.copy(alpha = 0.5f) else NeliBorder,
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
                        imageVector = if (isChannelLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = if (isChannelLocked) NeliLiveRed else Color(0xFF34D399),
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
                            text = if (isChannelLocked) {
                                "${channel.category} • IMEFUNGWA (Free Users)"
                            } else {
                                "${channel.category} • WAZI (Free & Premium)"
                            },
                            color = if (isChannelLocked) Color(0xFFFBBF24) else NeliTextSecondary,
                            fontSize = 11.sp
                        )
                    }
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
    val connectedDevice by NeliCastManager.connectedDevice.collectAsState()
    val isCasting = connectedDevice != null
    val connectedTvName = connectedDevice?.name
    val castingChannel by NeliCastManager.castingChannel.collectAsState()
    val discoveredDevices by NeliCastManager.availableDevices.collectAsState()
    val castStatusMessage by NeliCastManager.statusMessage.collectAsState()

    var selectedCastChannel by remember(currentChannel, castingChannel, availableChannels) {
        mutableStateOf(castingChannel ?: currentChannel ?: availableChannels.firstOrNull())
    }

    LaunchedEffect(Unit) {
        NeliCastManager.refreshAvailableTvDevices(context)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
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

                // Discovered TV devices list
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Smart TV Zinazopatikana (${discoveredDevices.size}):",
                        color = NeliTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { NeliCastManager.refreshAvailableTvDevices(context) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan TVs",
                            tint = NeliGenreCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
                                text = if (isThisDeviceConnected) "Connected ✓" else "Connect",
                                color = if (isThisDeviceConnected) Color(0xFF34D399) else NeliMagenta,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
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
                    text = "Channel hii imefungwa na Admin kwa watumiaji wa bure. Jiunge na Premium sasa (Kwa Siku 500 TSh, Kwa Wiki 3,000 TSh, au Kwa Mwezi 10,000 TSh) kupitia HarakaPay ili kufungua channels zote papo hapo!",
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
                        text = "Jiunge na Premium (500 TSh)",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rudi Nyuma", color = NeliTextSecondary)
                }
            }
        }
    }
}
