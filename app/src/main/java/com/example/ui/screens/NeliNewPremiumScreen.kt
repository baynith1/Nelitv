package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.HarakaPayRepository
import com.example.data.NeliSubscriptionManager
import com.example.data.NeliSupabaseSyncManager
import com.example.data.SubscriptionPlanType
import com.example.data.local.UserAccountEntity
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliWarmOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PremiumPageMode {
    LIPIA_VIFURUSHI,
    NIMESHALIPA_INGIA
}

/**
 * Dedicated Production Premium Page opened from the Top Header VIP Crown Icon:
 * - Prominent Back button returning straight to the Home Page.
 * - Two clean tabs:
 *   1) "Lipia Vifurushi": Phone number input on top + compact mobile-fittable 3-tier TZS prices
 *      (1,000 TSh / 2 days, 3,000 TSh / week, 10,000 TSh / month) + real HarakaPay USSD push payment
 *      + automatic storage in Supabase (`nelitv_subscriptions` & `nelitv_payments`).
 *   2) "Nimeshalipa (Ingia)": Enter the phone number used to pay (even when logged in via Gmail)
 *      and tap "Open VIP" -> checks Supabase live; if active, unlocks VIP immediately; if NOT active,
 *      keeps VIP closed and switches directly to the "Lipia Vifurushi" payment tab with phone pre-filled.
 */
@Composable
fun NeliNewPremiumScreen(
    currentUser: UserAccountEntity?,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        NeliSubscriptionManager.initialize(context)
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val isActiveVip = NeliSubscriptionManager.isPremiumMemberActive(nowMs = nowMs, context = context) ||
        com.example.data.NeliFreeForeverAccountsManager.isSpecialAdFreeUser(currentUser)

    var activeMode by remember { mutableStateOf(PremiumPageMode.LIPIA_VIFURUSHI) }
    var phoneInput by remember {
        mutableStateOf(subState.phoneNumber.ifBlank { subState.pendingPhone })
    }
    var selectedPlan by remember { mutableStateOf(SubscriptionPlanType.DAILY) }

    // Payment execution states (HarakaPay Production + Supabase persistence)
    var isProcessingPayment by remember { mutableStateOf(false) }
    var activePendingOrderId by remember { mutableStateOf(subState.pendingOrderId) }
    var statusInfoMessage by remember { mutableStateOf<String?>(null) }
    var statusErrorMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingSupabaseVip by remember { mutableStateOf(false) }

    BackHandler { onBackToHome() }

    // Automatic background status poller whenever a HarakaPay USSD push order is pending
    LaunchedEffect(activePendingOrderId) {
        val orderId = activePendingOrderId.trim()
        if (orderId.isBlank()) return@LaunchedEffect
        var attempts = 0
        while (attempts < 45 && activePendingOrderId == orderId) {
            delay(3_500L)
            attempts++
            val statusRes = HarakaPayRepository.checkPaymentStatus(orderId)
            val statusObj = statusRes.getOrNull()
            if (statusObj != null && statusObj.isCompleted) {
                val paidPhone = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput.ifBlank { subState.pendingPhone })
                val paidPlan = SubscriptionPlanType.fromId(
                    subState.pendingPlanId.ifBlank { selectedPlan.id }
                )
                val activated = NeliSubscriptionManager.activateVerifiedSubscription(
                    context = context,
                    plan = paidPlan,
                    phone = paidPhone,
                    verifiedOrderId = orderId
                )
                if (currentUser != null && currentUser.email.isNotBlank()) {
                    NeliSubscriptionManager.linkUserAccountToSubscription(
                        context = context,
                        uid = currentUser.uid,
                        email = currentUser.email,
                        realName = currentUser.realName
                    )
                }
                NeliSupabaseSyncManager.syncSubscriptionAndPaymentNow(
                    phoneNumber = paidPhone,
                    planId = paidPlan.id,
                    amountTzs = paidPlan.amountTzs,
                    orderId = orderId,
                    activatedAtMs = activated.activatedAtMs,
                    expiresAtMs = activated.expiresAtMs,
                    userEmail = currentUser?.email.orEmpty(),
                    userId = currentUser?.uid.orEmpty(),
                    providerReference = "HPAY-$orderId"
                )
                activePendingOrderId = ""
                isProcessingPayment = false
                statusInfoMessage = null
                statusErrorMessage = null
                statusSuccessMessage = "Hongera! Malipo ya ${paidPlan.priceFormatted} yamethibitishwa. Karibu Active Member!"
                android.widget.Toast.makeText(
                    context,
                    "Hongera! Umekuwa Active Member wa NeliTv VIP.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                delay(700L)
                onBackToHome()
                break
            } else if (statusObj != null && statusObj.isFailed) {
                NeliSubscriptionManager.clearPendingOrder(context)
                activePendingOrderId = ""
                isProcessingPayment = false
                statusInfoMessage = null
                statusErrorMessage = "Malipo hayakukamilika (${statusObj.status}). Hakikisha una salio kisha jaribu tena."
                break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0D20), Color(0xFF060916), Color(0xFF040610))
                )
            )
            .testTag("new_premium_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar with Back Button to Home
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FFFFFF))
                            .testTag("premium_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Rudi Home",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "NeliTv Premium",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "VIP Bila Matangazo • Kasi Zaidi",
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isActiveVip) Color(0x3310B981) else Color(0x2EFFD54F))
                        .border(
                            1.dp,
                            if (isActiveVip) Color(0xFF34D399) else Color(0xFFFFD54F),
                            CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isActiveVip) "VIP ACTIVE" else "BURE",
                        color = if (isActiveVip) Color(0xFF34D399) else Color(0xFFFFE57F),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Active VIP Membership Banner (if active)
                    if (isActiveVip) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0C2A24), Color(0xFF141B36))
                                    )
                                )
                                .border(1.2.dp, Color(0xFF34D399), RoundedCornerShape(18.dp))
                                .padding(14.dp)
                                .testTag("premium_active_vip_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Active VIP",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(30.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "VIP Member Active • ${subState.planTitle.ifBlank { "VIP Pass" }}",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                val countdownText = if (subState.expiresAtMs > nowMs) {
                                    "Muda uliobaki: ${subState.formatLiveCountdown(nowMs)}"
                                } else {
                                    "Akaunti yako ya VIP ipo wazi"
                                }
                                Text(
                                    text = countdownText,
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (subState.phoneNumber.isNotBlank()) {
                                    Text(
                                        text = "Namba: ${subState.phoneNumber} • Cloud Synced",
                                        color = NeliTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Dual Mode Switcher: "Lipia Vifurushi" | "Nimeshalipa (Ingia)"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurface)
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PremiumSegmentButton(
                            title = "Lipia Vifurushi",
                            selected = activeMode == PremiumPageMode.LIPIA_VIFURUSHI,
                            onClick = {
                                activeMode = PremiumPageMode.LIPIA_VIFURUSHI
                                statusErrorMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("premium_tab_pay")
                        )
                        PremiumSegmentButton(
                            title = "Nimeshalipa (Ingia)",
                            selected = activeMode == PremiumPageMode.NIMESHALIPA_INGIA,
                            onClick = {
                                activeMode = PremiumPageMode.NIMESHALIPA_INGIA
                                statusErrorMessage = null
                                statusInfoMessage = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("premium_tab_already_paid")
                        )
                    }

                    // Status Messages (Success / Error / Pending Info)
                    statusSuccessMessage?.let { msg ->
                        PremiumFeedbackCard(
                            text = msg,
                            accentColor = Color(0xFF34D399),
                            icon = Icons.Default.CheckCircle
                        )
                    }
                    statusErrorMessage?.let { err ->
                        PremiumFeedbackCard(
                            text = err,
                            accentColor = Color(0xFFFB7185),
                            icon = Icons.Default.ErrorOutline
                        )
                    }
                    statusInfoMessage?.let { info ->
                        PremiumFeedbackCard(
                            text = info,
                            accentColor = NeliGenreCyan,
                            icon = Icons.Default.CloudDone
                        )
                    }

                    if (activeMode == PremiumPageMode.LIPIA_VIFURUSHI) {
                        // TAB 1: LIPIA VIFURUSHI (Phone Input + Compact Fittable Prices + Pay Button)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(NeliSurface)
                                .border(1.dp, Color(0x44FF007A), RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Chagua Kifurushi & Lipia",
                                        color = NeliTextPrimary,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "M-Pesa • Tigo Pesa • Airtel Money • HaloPesa",
                                        color = NeliGenreCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 1) Phone Number Input on top
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { raw ->
                                    phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                    statusErrorMessage = null
                                },
                                label = { Text("Namba ya Simu ya Malipo (mf. 0712345678)") },
                                placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = NeliGenreCyan
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeliMagenta,
                                    unfocusedBorderColor = Color(0x44FFFFFF),
                                    focusedTextColor = NeliTextPrimary,
                                    unfocusedTextColor = NeliTextPrimary,
                                    focusedLabelColor = NeliGenreCyan,
                                    unfocusedLabelColor = NeliTextSecondary,
                                    cursorColor = NeliMagenta
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("premium_pay_phone_input")
                            )

                            // 2) Small, Fittable 3-Column Price Selector for EVERY mobile UI
                            Text(
                                text = "Chagua Bei (TSh):",
                                color = NeliTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CompactPriceChipCard(
                                    plan = SubscriptionPlanType.DAILY,
                                    shortDuration = "Siku 2",
                                    shortPrice = "1,000",
                                    tagLabel = "2 DAYS",
                                    selected = selectedPlan == SubscriptionPlanType.DAILY,
                                    onClick = { selectedPlan = SubscriptionPlanType.DAILY },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("premium_price_1000")
                                )
                                CompactPriceChipCard(
                                    plan = SubscriptionPlanType.WEEKLY,
                                    shortDuration = "Wiki 1",
                                    shortPrice = "3,000",
                                    tagLabel = "POPULAR",
                                    selected = selectedPlan == SubscriptionPlanType.WEEKLY,
                                    onClick = { selectedPlan = SubscriptionPlanType.WEEKLY },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("premium_price_3000")
                                )
                                CompactPriceChipCard(
                                    plan = SubscriptionPlanType.MONTHLY,
                                    shortDuration = "Mwezi 1",
                                    shortPrice = "10,000",
                                    tagLabel = "VIP",
                                    selected = selectedPlan == SubscriptionPlanType.MONTHLY,
                                    onClick = { selectedPlan = SubscriptionPlanType.MONTHLY },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("premium_price_10000")
                                )
                            }

                            // Selected plan summary line
                            Text(
                                text = "Umechagua: ${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted} (${selectedPlan.durationLabel})",
                                color = Color(0xFFFFE57F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // 3) Pay Button (Real HarakaPay Production USSD Push + Supabase Sync)
                            Button(
                                onClick = {
                                    val normalizedPhone = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                    if (!HarakaPayRepository.isValidTzPhoneNumber(normalizedPhone)) {
                                        statusErrorMessage = "Tafadhali weka namba sahihi ya simu ya Tanzania (mf. 0712345678)."
                                        return@Button
                                    }
                                    statusErrorMessage = null
                                    statusSuccessMessage = null
                                    isProcessingPayment = true
                                    statusInfoMessage = "Inatuma ombi la malipo kwenye $normalizedPhone..."

                                    scope.launch {
                                        val res = HarakaPayRepository.collectPayment(
                                            phone = normalizedPhone,
                                            amount = selectedPlan.amountTzs,
                                            description = "Nelitv ${selectedPlan.titleSwahili} VIP"
                                        )
                                        res.onSuccess { collect ->
                                            NeliSubscriptionManager.savePendingOrder(
                                                context = context,
                                                plan = selectedPlan,
                                                phone = normalizedPhone,
                                                orderId = collect.orderId
                                            )
                                            activePendingOrderId = collect.orderId
                                            statusInfoMessage =
                                                "Ombi limetumwa kwenye $normalizedPhone! Ingiza PIN kwenye ujumbe wa simu yako kuthibitisha ${selectedPlan.priceFormatted}. App itawasha VIP moja kwa moja."
                                        }.onFailure { err ->
                                            isProcessingPayment = false
                                            statusInfoMessage = null
                                            statusErrorMessage = err.message
                                                ?: "Imeshindikana kutuma ombi la malipo. Angalia mtandao na jaribu tena."
                                        }
                                    }
                                },
                                enabled = !isProcessingPayment,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                    )
                                    .testTag("premium_pay_submit_button")
                            ) {
                                if (isProcessingPayment && activePendingOrderId.isBlank()) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.4.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Inatuma Ombi...",
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.5.sp
                                    )
                                } else {
                                    Text(
                                        text = "Pay • ${selectedPlan.priceFormatted}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.5.sp
                                    )
                                }
                            }

                            // Manual instant verify button while waiting for USSD SMS confirmation
                            if (activePendingOrderId.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val checkOrderId = activePendingOrderId
                                        statusInfoMessage = "Inahakiki malipo yako sasa..."
                                        scope.launch {
                                            val stRes = HarakaPayRepository.checkPaymentStatus(checkOrderId)
                                            val st = stRes.getOrNull()
                                            if (st != null && st.isCompleted) {
                                                val paidPhone = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                                val activated = NeliSubscriptionManager.activateVerifiedSubscription(
                                                    context = context,
                                                    plan = selectedPlan,
                                                    phone = paidPhone,
                                                    verifiedOrderId = checkOrderId
                                                )
                                                if (currentUser != null && currentUser.email.isNotBlank()) {
                                                    NeliSubscriptionManager.linkUserAccountToSubscription(
                                                        context = context,
                                                        uid = currentUser.uid,
                                                        email = currentUser.email,
                                                        realName = currentUser.realName
                                                    )
                                                }
                                                NeliSupabaseSyncManager.syncSubscriptionAndPaymentNow(
                                                    phoneNumber = paidPhone,
                                                    planId = selectedPlan.id,
                                                    amountTzs = selectedPlan.amountTzs,
                                                    orderId = checkOrderId,
                                                    activatedAtMs = activated.activatedAtMs,
                                                    expiresAtMs = activated.expiresAtMs,
                                                    userEmail = currentUser?.email.orEmpty(),
                                                    userId = currentUser?.uid.orEmpty(),
                                                    providerReference = "HPAY-$checkOrderId"
                                                )
                                                activePendingOrderId = ""
                                                isProcessingPayment = false
                                                statusInfoMessage = null
                                                statusErrorMessage = null
                                                statusSuccessMessage = "Malipo yamethibitishwa! Karibu Active Member."
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Hongera! Umekuwa Active Member wa NeliTv VIP.",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                delay(600L)
                                                onBackToHome()
                                            } else if (st != null && st.isFailed) {
                                                NeliSubscriptionManager.clearPendingOrder(context)
                                                activePendingOrderId = ""
                                                isProcessingPayment = false
                                                statusInfoMessage = null
                                                statusErrorMessage = "Malipo yamekataliwa au muda umeisha. Jaribu tena."
                                            } else {
                                                statusInfoMessage = "Bado tunasubiri uthibitisho wa PIN kwenye simu yako..."
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("premium_manual_verify_button")
                                ) {
                                    Text(
                                        text = "Nimemaliza Kulipa • Thibitisha Sasa",
                                        color = NeliGenreCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // TAB 2: NIMESHALIPA (INGIA) — Verify phone against Supabase & Open VIP (or route to Pay!)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(NeliSurface)
                                .border(1.dp, Color(0x5500E5FF), RoundedCornerShape(20.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    tint = NeliGenreCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Nimeshalipa (Ingia VIP)",
                                        color = NeliTextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Hata kama umeingia kwa Gmail, andika namba uliyolipia hapa.",
                                        color = NeliTextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { raw ->
                                    phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                    statusErrorMessage = null
                                },
                                label = { Text("Namba ya Simu Uliyolipia") },
                                placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = NeliGenreCyan
                                    )
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeliGenreCyan,
                                    unfocusedBorderColor = Color(0x44FFFFFF),
                                    focusedTextColor = NeliTextPrimary,
                                    unfocusedTextColor = NeliTextPrimary,
                                    focusedLabelColor = NeliGenreCyan,
                                    unfocusedLabelColor = NeliTextSecondary,
                                    cursorColor = NeliGenreCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("premium_verify_phone_input")
                            )

                            // Open VIP Button — Checks Supabase Live!
                            Button(
                                onClick = {
                                    val normalized = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                    if (!HarakaPayRepository.isValidTzPhoneNumber(normalized)) {
                                        statusErrorMessage = "Weka namba sahihi ya simu uliyolipia (mf. 0712345678)."
                                        return@Button
                                    }
                                    statusErrorMessage = null
                                    statusSuccessMessage = null
                                    isCheckingSupabaseVip = true

                                    scope.launch {
                                        val lookup = NeliSupabaseSyncManager.querySubscriptionByPhone(normalized)
                                        isCheckingSupabaseVip = false
                                        val record = lookup.getOrNull()
                                        if (record != null && record.isActive) {
                                            NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(
                                                context = context,
                                                record = record
                                            )
                                            if (currentUser != null && currentUser.email.isNotBlank()) {
                                                NeliSubscriptionManager.linkUserAccountToSubscription(
                                                    context = context,
                                                    uid = currentUser.uid,
                                                    email = currentUser.email,
                                                    realName = currentUser.realName
                                                )
                                            }
                                            android.widget.Toast.makeText(
                                                context,
                                                "Karibu Active Member! VIP yako imefunguliwa.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            onBackToHome()
                                        } else {
                                            // Not an active member -> Do NOT open VIP, take user automatically to Lipia Sasa page!
                                            statusSuccessMessage = null
                                            statusErrorMessage =
                                                "Namba $normalized haijalipiwa au kifurushi kimeisha. Lipia sasa hapa chini."
                                            activeMode = PremiumPageMode.LIPIA_VIFURUSHI
                                        }
                                    }
                                },
                                enabled = !isCheckingSupabaseVip,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(Color(0xFF00B4D8), Color(0xFF7C3AED)))
                                    )
                                    .testTag("premium_open_vip_button")
                            ) {
                                if (isCheckingSupabaseVip) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.4.dp,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Inakagua VIP...",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Open VIP",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            // Direct shortcut to Lipia Kifurushi carrying over the entered phone number
                            OutlinedButton(
                                onClick = {
                                    statusErrorMessage = null
                                    activeMode = PremiumPageMode.LIPIA_VIFURUSHI
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("premium_switch_to_pay_button")
                            ) {
                                Text(
                                    text = "Lipia Kifurushi Sasa",
                                    color = Color(0xFFFFD54F),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Friendly note: channels remain open while VIP unlocks ad-free & fast cloud membership
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NeliSurfaceVariant)
                            .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Faida za NeliTv VIP",
                            color = NeliTextPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "• Tazama bila matangazo (Zero Ads)\n• Hifadhi ya malipo mtandaoni — fungua VIP kwenye simu yoyote kwa namba yako\n• Channels zote ziko wazi kwa sasa huku ukiunga mkono NeliTv HD.",
                            color = NeliTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun PremiumSegmentButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else NeliTextSecondary,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactPriceChipCard(
    plan: SubscriptionPlanType,
    shortDuration: String,
    shortPrice: String,
    tagLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Color(0x38FF007A) else NeliSurfaceVariant
            )
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) NeliMagenta else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) NeliMagenta else Color(0x2800E5FF))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = tagLabel,
                color = if (selected) Color.White else NeliGenreCyan,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = shortDuration,
            color = NeliTextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = shortPrice,
            color = if (selected) Color(0xFFFFE57F) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "TSh",
            color = NeliTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun PremiumFeedbackCard(
    text: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
