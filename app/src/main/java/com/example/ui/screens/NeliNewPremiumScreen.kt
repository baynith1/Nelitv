package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Compact Single-Screen Production Premium Page:
 * Everything (Header, Active VIP Real-Time Countdown + Remove Number, Dual Tabs,
 * Phone Input, 3-Tier Plan Cards, Primary CTA, and VIP Benefits Strip) fits cleanly
 * on ONE screen without vertical scrolling on phones, while preserving IME safety.
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
            val tickNow = System.currentTimeMillis()
            nowMs = tickNow
            NeliSubscriptionManager.expireSubscriptionIfNeeded(context, tickNow)
            delay(1_000L)
        }
    }

    // Real-time cloud sync with Supabase across ALL people & ALL devices (by phone or logged-in email)
    LaunchedEffect(subState.phoneNumber, currentUser?.email) {
        if (NeliSubscriptionManager.isNumberManuallyRemoved(context)) return@LaunchedEffect
        val candidatePhone = subState.phoneNumber.trim()
        val candidateEmail = currentUser?.email?.trim().orEmpty()
        val record = when {
            candidatePhone.isNotBlank() && HarakaPayRepository.isValidTzPhoneNumber(
                HarakaPayRepository.normalizeTzPhoneNumber(candidatePhone)
            ) -> NeliSupabaseSyncManager.querySubscriptionByPhone(candidatePhone).getOrNull()
            candidateEmail.isNotBlank() -> NeliSupabaseSyncManager.querySubscriptionByEmail(candidateEmail).getOrNull()
            else -> null
        }
        if (record != null && record.isActive) {
            NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(
                context = context,
                record = record
            )
            nowMs = System.currentTimeMillis()
        }
    }

    val isActiveVip = NeliSubscriptionManager.isPremiumMemberActive(nowMs = nowMs, context = context) ||
        com.example.data.NeliFreeForeverAccountsManager.isSpecialAdFreeUser(currentUser)
    val countdownBreakdown = remember(subState.expiresAtMs, subState.isVerified, subState.planId, nowMs) {
        subState.remainingBreakdown(nowMs)
    }

    var activeMode by remember { mutableStateOf(PremiumPageMode.LIPIA_VIFURUSHI) }
    var phoneInput by remember {
        mutableStateOf(subState.phoneNumber.ifBlank { subState.pendingPhone })
    }
    var selectedPlan by remember { mutableStateOf(SubscriptionPlanType.DAILY) }

    var activePaymentPopupPhone by remember { mutableStateOf<String?>(null) }
    var activePaymentPopupPlan by remember { mutableStateOf<SubscriptionPlanType?>(null) }
    var wasActiveBeforePopup by remember { mutableStateOf(false) }
    var statusInfoMessage by remember { mutableStateOf<String?>(null) }
    var statusErrorMessage by remember { mutableStateOf<String?>(null) }
    var statusSuccessMessage by remember { mutableStateOf<String?>(null) }
    var isCheckingSupabaseVip by remember { mutableStateOf(false) }

    val handleRemoveSavedNumber: () -> Unit = {
        NeliSubscriptionManager.removeSavedPaymentNumber(context)
        phoneInput = ""
        nowMs = System.currentTimeMillis()
        statusSuccessMessage = null
        statusErrorMessage = null
        statusInfoMessage = "Namba imeondolewa. Channel za VIP zimefungwa hadi uweke namba mpya."
        android.widget.Toast.makeText(
            context,
            "Namba imeondolewa • Channel za VIP zimefungwa",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    BackHandler { onBackToHome() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B0F24), Color(0xFF060916), Color(0xFF040610))
                )
            )
            .testTag("new_premium_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Compact Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBackToHome,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0x22FFFFFF))
                            .testTag("premium_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Rudi Home",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "NeliTv Premium",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "VIP • Bila Matangazo • Kasi Zaidi",
                            color = Color(0xFFFFD54F),
                            fontSize = 10.5.sp,
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
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isActiveVip) "VIP ACTIVE" else "BURE",
                        color = if (isActiveVip) Color(0xFF34D399) else Color(0xFFFFE57F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Single-Page Fit Content Area (no scroll needed on phones; scroll only activates if keyboard opens)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // 1) Compact Active VIP Dashboard + Real-Time Countdown + Remove Number
                    if (isActiveVip) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0E2C24), Color(0xFF101935))
                                    )
                                )
                                .border(1.2.dp, Color(0xCC34D399), RoundedCornerShape(16.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("premium_active_vip_banner"),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = "Active VIP",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "ACTIVE VIP • ${subState.planTitle.ifBlank { "VIP Pass" }}",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0x2834D399))
                                        .border(1.dp, Color(0x8834D399), CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF34D399))
                                    )
                                    Text(
                                        text = "LIVE",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            // Compact 4-Box Real-Time Countdown Strip
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("premium_realtime_countdown_box")
                            ) {
                                if (subState.isFreeForeverAccount) {
                                    Text(
                                        text = "∞  Bure Milele • VIP Active (Vifaa Vyote)",
                                        color = Color(0xFFFDE047),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                } else {
                                    val units = listOf(
                                        "SIKU" to countdownBreakdown.days.toString(),
                                        "SAA" to String.format(java.util.Locale.US, "%02d", countdownBreakdown.hours),
                                        "DAKIKA" to String.format(java.util.Locale.US, "%02d", countdownBreakdown.minutes),
                                        "SEKUNDE" to String.format(java.util.Locale.US, "%02d", countdownBreakdown.seconds)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        units.forEach { (label, value) ->
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFF0A1120))
                                                    .border(1.dp, Color(0x4434D399), RoundedCornerShape(10.dp))
                                                    .padding(vertical = 5.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = value,
                                                    color = Color(0xFFFDE047),
                                                    fontSize = 15.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    maxLines = 1
                                                )
                                                Text(
                                                    text = label,
                                                    color = NeliTextSecondary,
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Footer Row: Saved Number + Expiry on left | Remove Number action on right
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (subState.phoneNumber.isNotBlank()) {
                                        Text(
                                            text = "Namba: ${subState.phoneNumber}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (subState.formattedExpiryDate.isNotBlank() && !subState.isFreeForeverAccount) {
                                        Text(
                                            text = "Inaisha: ${subState.formattedExpiryDate}",
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (!subState.isFreeForeverAccount) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = handleRemoveSavedNumber,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xCCFB7185)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0x22FB7185),
                                            contentColor = Color(0xFFFDA4AF)
                                        ),
                                        modifier = Modifier
                                            .heightIn(min = 40.dp)
                                            .testTag("premium_remove_number_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove Number",
                                            tint = Color(0xFFFB7185),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Remove",
                                            color = Color(0xFFFDA4AF),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2) Dual Mode Switcher: "Lipia Vifurushi" | "Nimeshalipa (Ingia)"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeliSurface)
                            .border(1.dp, Color(0x2FFFFFFF), RoundedCornerShape(14.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
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

                    // 3) Compact Single-Line/Two-Line Feedback Banner (only when present)
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

                    // 4) Main Action Card (Animated smooth switch between Pay & Already Paid)
                    AnimatedContent(
                        targetState = activeMode,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "premium_mode_switch"
                    ) { mode ->
                        if (mode == PremiumPageMode.LIPIA_VIFURUSHI) {
                            // TAB 1: LIPIA VIFURUSHI
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(NeliSurface)
                                    .border(1.dp, Color(0x44FF007A), RoundedCornerShape(18.dp))
                                    .padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Chagua Kifurushi & Lipia",
                                            color = NeliTextPrimary,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "M-Pesa • Tigo Pesa • Airtel Money • HaloPesa",
                                            color = NeliGenreCyan,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { raw ->
                                        phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                        statusErrorMessage = null
                                    },
                                    label = { Text("Namba ya Simu (mf. 0712345678)", fontSize = 12.sp) },
                                    placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = NeliGenreCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(12.dp),
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

                                // 3-Column Compact Price Selector (Siku 1 / Wiki 1 / Mwezi 1)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactPriceChipCard(
                                        plan = SubscriptionPlanType.DAILY,
                                        shortDuration = "Siku 1",
                                        shortPrice = "1,000",
                                        tagLabel = "SIKU",
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
                                        tagLabel = "WIKI",
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
                                        tagLabel = "MWEZI",
                                        selected = selectedPlan == SubscriptionPlanType.MONTHLY,
                                        onClick = { selectedPlan = SubscriptionPlanType.MONTHLY },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("premium_price_10000")
                                    )
                                }

                                Text(
                                    text = "Umechagua: ${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted} (${selectedPlan.durationLabel})",
                                    color = Color(0xFFFFE57F),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Button(
                                    onClick = {
                                        val normalizedPhone = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                        if (!HarakaPayRepository.isValidTzPhoneNumber(normalizedPhone)) {
                                            statusErrorMessage =
                                                "Tafadhali weka namba sahihi ya simu ya Tanzania (mf. 0712345678)."
                                            return@Button
                                        }
                                        statusErrorMessage = null
                                        statusSuccessMessage = null
                                        statusInfoMessage = null
                                        wasActiveBeforePopup = isActiveVip
                                        activePaymentPopupPhone = normalizedPhone
                                        activePaymentPopupPlan = selectedPlan
                                    },
                                    enabled = activePaymentPopupPhone == null,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(13.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(
                                            Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                                        )
                                        .testTag("premium_pay_submit_button")
                                ) {
                                    Text(
                                        text = "Pay • ${selectedPlan.priceFormatted}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        } else {
                            // TAB 2: NIMESHALIPA (INGIA VIP)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(NeliSurface)
                                    .border(1.dp, Color(0x5500E5FF), RoundedCornerShape(18.dp))
                                    .padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = NeliGenreCyan,
                                        modifier = Modifier.size(21.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Nimeshalipa (Ingia VIP)",
                                            color = NeliTextPrimary,
                                            fontSize = 14.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "Andika namba uliyolipia kufungua au kubadilisha VIP.",
                                            color = NeliTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = { raw ->
                                        phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                        statusErrorMessage = null
                                    },
                                    label = { Text("Namba ya Simu Uliyolipia", fontSize = 12.sp) },
                                    placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = NeliGenreCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(12.dp),
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

                                Button(
                                    onClick = {
                                        val normalized = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                        if (!HarakaPayRepository.isValidTzPhoneNumber(normalized)) {
                                            statusErrorMessage =
                                                "Weka namba sahihi ya simu uliyolipia (mf. 0712345678)."
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
                                                statusSuccessMessage = null
                                                statusErrorMessage =
                                                    "Namba $normalized haijalipiwa au kifurushi kimeisha. Lipia sasa."
                                                activeMode = PremiumPageMode.LIPIA_VIFURUSHI
                                            }
                                        }
                                    },
                                    enabled = !isCheckingSupabaseVip,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(13.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(
                                            Brush.horizontalGradient(listOf(Color(0xFF00B4D8), Color(0xFF7C3AED)))
                                        )
                                        .testTag("premium_open_vip_button")
                                ) {
                                    if (isCheckingSupabaseVip) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
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
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(7.dp))
                                        Text(
                                            text = "Open VIP",
                                            color = Color.White,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.5.sp
                                        )
                                    }
                                }

                                // Side-by-side secondary buttons when a number is saved, or full-width pay shortcut
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            statusErrorMessage = null
                                            activeMode = PremiumPageMode.LIPIA_VIFURUSHI
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0x88FFD54F)),
                                        contentPadding = PaddingValues(horizontal = 10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("premium_switch_to_pay_button")
                                    ) {
                                        Text(
                                            text = "Lipia Kifurushi",
                                            color = Color(0xFFFFD54F),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (subState.phoneNumber.isNotBlank() && !subState.isFreeForeverAccount) {
                                        OutlinedButton(
                                            onClick = handleRemoveSavedNumber,
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xCCFB7185)),
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = Color(0x1DFB7185),
                                                contentColor = Color(0xFFFDA4AF)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("premium_tab_remove_number_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove Number",
                                                tint = Color(0xFFFB7185),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = "Remove Number",
                                                color = Color(0xFFFDA4AF),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5) Compact 3-Column VIP Highlights Strip (Zero-scroll summary)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(NeliSurfaceVariant)
                            .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CompactVipBenefitPill(
                            icon = Icons.Default.LiveTv,
                            label = "Channel Zote",
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.weight(1f)
                        )
                        CompactVipBenefitPill(
                            icon = Icons.Default.Verified,
                            label = "Zero Ads",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.weight(1f)
                        )
                        CompactVipBenefitPill(
                            icon = Icons.Default.Devices,
                            label = "Vifaa Vyote",
                            tint = NeliGenreCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Live Payment Verification Popup (USSD Push, Dual HarakaPay+Supabase Check, Animated Green Tick -> Auto-Home)
        val popupPhone = activePaymentPopupPhone
        val popupPlan = activePaymentPopupPlan
        if (popupPhone != null && popupPlan != null) {
            com.example.ui.components.NeliPaymentVerificationPopup(
                normalizedPhone = popupPhone,
                selectedPlan = popupPlan,
                currentUser = currentUser,
                wasAlreadyActiveBeforePopup = wasActiveBeforePopup,
                onPaymentConfirmedReturnHome = {
                    activePaymentPopupPhone = null
                    activePaymentPopupPlan = null
                    onBackToHome()
                },
                onCancelledByUser = {
                    activePaymentPopupPhone = null
                    activePaymentPopupPlan = null
                    statusSuccessMessage = null
                    statusErrorMessage = null
                    statusInfoMessage = "Umeghairi malipo. Unaweza kulipia tena wakati wowote."
                }
            )
        }
    }
}

@Composable
private fun CompactVipBenefitPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = NeliTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
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
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) {
                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) Color.White else NeliTextSecondary,
            fontSize = 12.sp,
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
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (selected) Color(0x38FF007A) else NeliSurfaceVariant
            )
            .border(
                width = if (selected) 1.8.dp else 1.dp,
                color = if (selected) NeliMagenta else Color(0x33FFFFFF),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (selected) NeliMagenta else Color(0x2800E5FF))
                .padding(horizontal = 6.dp, vertical = 1.5.dp)
        ) {
            Text(
                text = tagLabel,
                color = if (selected) Color.White else NeliGenreCyan,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
        Text(
            text = shortDuration,
            color = NeliTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = shortPrice,
            color = if (selected) Color(0xFFFFE57F) else Color.White,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = "TSh",
            color = NeliTextSecondary,
            fontSize = 9.5.sp,
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
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
