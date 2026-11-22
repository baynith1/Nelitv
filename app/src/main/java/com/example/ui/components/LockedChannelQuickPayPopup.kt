package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.HarakaPayRepository
import com.example.data.NeliSubscriptionManager
import com.example.data.NeliSupabaseSyncManager
import com.example.data.SubscriptionPlanType
import com.example.data.local.UserAccountEntity
import com.example.model.LiveChannel
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.ui.theme.NeliWarmOrange
import kotlinx.coroutines.launch

private enum class QuickPayPopupStep {
    SELECT_PLAN,
    ENTER_PHONE,
    ENTER_PAID_NUMBER,
    VERIFY_PAYMENT
}

/**
 * Smooth 3-step in-place VIP Payment Popup flow triggered directly when tapping a locked channel:
 *  Step 1 (SELECT_PLAN): Tap plan amount right in the popup (1,000 TSh Siku / 3,000 TSh Wiki / 10,000 TSh Mwezi) + Pay.
 *  Step 2 (ENTER_PHONE): Enter mobile money phone number (M-Pesa / Tigo Pesa / Airtel Money / HaloPesa) + Pay.
 *  Step 3 (VERIFY_PAYMENT): Live HarakaPay USSD push & dual HarakaPay+Supabase verification popup with green tick,
 *                           Nimelipa Refresh, Omba USSD Upya, and X cancel button.
 *
 * Every popup step includes a 48.dp X close button to cancel cleanly without navigating away to the Premium page.
 */
@Composable
fun LockedChannelQuickPayPopup(
    channel: LiveChannel,
    currentUser: UserAccountEntity?,
    onDismiss: () -> Unit,
    onPaymentUnlockedChannel: (LiveChannel) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val subState by NeliSubscriptionManager.subscriptionState.collectAsState()

    var currentStep by remember(channel.id) { mutableStateOf(QuickPayPopupStep.SELECT_PLAN) }
    var selectedPlan by remember(channel.id) { mutableStateOf(SubscriptionPlanType.DAILY) }
    var phoneInput by remember(channel.id) {
        mutableStateOf(subState.phoneNumber.ifBlank { subState.pendingPhone })
    }
    var phoneError by remember(channel.id) { mutableStateOf<String?>(null) }
    var paidLookupError by remember(channel.id) { mutableStateOf<String?>(null) }
    var isVerifyingPaidNumber by remember(channel.id) { mutableStateOf(false) }
    var normalizedPhoneForUssd by remember(channel.id) { mutableStateOf("") }
    val wasAlreadyActiveBeforePopup = remember(channel.id) {
        NeliSubscriptionManager.isPremiumMemberActive(context = context)
    }

    if (currentStep == QuickPayPopupStep.VERIFY_PAYMENT && normalizedPhoneForUssd.isNotBlank()) {
        // Step 3: Live USSD Push + Auto-Verify (HarakaPay & Supabase) + Green Tick -> Unlock Channel
        NeliPaymentVerificationPopup(
            normalizedPhone = normalizedPhoneForUssd,
            selectedPlan = selectedPlan,
            currentUser = currentUser,
            wasAlreadyActiveBeforePopup = wasAlreadyActiveBeforePopup,
            onPaymentConfirmedReturnHome = {
                onPaymentUnlockedChannel(channel)
            },
            onCancelledByUser = {
                onDismiss()
            }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 390.dp)
                .testTag("locked_channel_premium_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1324))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1C1335), Color(0xFF0D1120))
                        )
                    )
                    .border(1.4.dp, Color(0xCCF59E0B), RoundedCornerShape(24.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState.ordinal > initialState.ordinal) {
                            (slideInHorizontally(tween(220)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(180)))
                        } else {
                            (slideInHorizontally(tween(220)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(180)))
                        }
                    },
                    label = "quick_pay_popup_step_transition"
                ) { step ->
                    when (step) {
                        QuickPayPopupStep.SELECT_PLAN -> {
                            SelectPlanPopupStepContent(
                                channel = channel,
                                selectedPlan = selectedPlan,
                                onSelectPlan = { selectedPlan = it },
                                onPayClick = {
                                    phoneError = null
                                    currentStep = QuickPayPopupStep.ENTER_PHONE
                                },
                                onAlreadyPaidClick = {
                                    paidLookupError = null
                                    currentStep = QuickPayPopupStep.ENTER_PAID_NUMBER
                                },
                                onDismiss = onDismiss
                            )
                        }

                        QuickPayPopupStep.ENTER_PHONE -> {
                            EnterPhonePopupStepContent(
                                selectedPlan = selectedPlan,
                                phoneInput = phoneInput,
                                phoneError = phoneError,
                                onPhoneChange = { raw ->
                                    phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                    phoneError = null
                                },
                                onBackToPlans = {
                                    phoneError = null
                                    currentStep = QuickPayPopupStep.SELECT_PLAN
                                },
                                onConfirmPay = {
                                    val normalized = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                    if (!HarakaPayRepository.isValidTzPhoneNumber(normalized)) {
                                        phoneError = "Weka namba sahihi ya Tanzania (mf. 0712345678)."
                                    } else {
                                        phoneError = null
                                        normalizedPhoneForUssd = normalized
                                        currentStep = QuickPayPopupStep.VERIFY_PAYMENT
                                    }
                                },
                                onDismiss = onDismiss
                            )
                        }

                        QuickPayPopupStep.ENTER_PAID_NUMBER -> {
                            EnterPaidNumberPopupStepContent(
                                savedPhoneNumber = subState.phoneNumber,
                                isFreeForever = subState.isFreeForeverAccount,
                                phoneInput = phoneInput,
                                lookupError = paidLookupError,
                                isChecking = isVerifyingPaidNumber,
                                onPhoneChange = { raw ->
                                    phoneInput = raw.filter { it.isDigit() || it == '+' }.take(13)
                                    paidLookupError = null
                                },
                                onBackToPlans = {
                                    paidLookupError = null
                                    currentStep = QuickPayPopupStep.SELECT_PLAN
                                },
                                onRemoveCurrentNumber = {
                                    NeliSubscriptionManager.removeSavedPaymentNumber(context)
                                    phoneInput = ""
                                    paidLookupError = null
                                    android.widget.Toast.makeText(
                                        context,
                                        "Namba imeondolewa. Weka namba mpya iliyolipiwa.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onUnlockWithPaidNumber = {
                                    val normalized = HarakaPayRepository.normalizeTzPhoneNumber(phoneInput)
                                    if (!HarakaPayRepository.isValidTzPhoneNumber(normalized)) {
                                        paidLookupError = "Weka namba sahihi ya simu uliyolipia (mf. 0712345678)."
                                        return@EnterPaidNumberPopupStepContent
                                    }
                                    paidLookupError = null
                                    isVerifyingPaidNumber = true
                                    scope.launch {
                                        val lookup = NeliSupabaseSyncManager.querySubscriptionByPhone(normalized)
                                        isVerifyingPaidNumber = false
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
                                                "Karibu Active Member! Channel za VIP zimefunguliwa.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                            onPaymentUnlockedChannel(channel)
                                        } else {
                                            paidLookupError =
                                                "Namba $normalized haijalipiwa au kifurushi kimeisha. Lipia sasa kufungua VIP."
                                        }
                                    }
                                },
                                onSwitchToPay = {
                                    paidLookupError = null
                                    currentStep = QuickPayPopupStep.SELECT_PLAN
                                },
                                onDismiss = onDismiss
                            )
                        }

                        QuickPayPopupStep.VERIFY_PAYMENT -> {
                            // Handled above by NeliPaymentVerificationPopup
                            Spacer(modifier = Modifier.height(1.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectPlanPopupStepContent(
    channel: LiveChannel,
    selectedPlan: SubscriptionPlanType,
    onSelectPlan: (SubscriptionPlanType) -> Unit,
    onPayClick: () -> Unit,
    onAlreadyPaidClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_pay_step_select_plan"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: VIP Badge + X Close button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x2EF59E0B))
                    .border(1.dp, Color(0x88F59E0B), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "VIP CHANNEL",
                    color = Color(0xFFFDE68A),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Black
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("locked_dialog_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Funga",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Lock Emblem + Channel Title
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Color(0x44F59E0B), Color(0x18FF007A)))
                )
                .border(1.2.dp, Color(0x99FBBF24), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Channel Imefungwa",
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Chagua kifurushi hapa chini ufungue channel ZOTE za VIP papo hapo",
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 16.5.sp,
                textAlign = TextAlign.Center
            )
        }

        // Interactive Selectable Plan Cards (Siku 1, Wiki 1, Mwezi 1)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPlanOptionTile(
                plan = SubscriptionPlanType.DAILY,
                tagLabel = "SIKU",
                durationLabel = "Siku 1",
                priceShort = "1,000",
                selected = selectedPlan == SubscriptionPlanType.DAILY,
                onClick = { onSelectPlan(SubscriptionPlanType.DAILY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_pay_plan_1000")
            )
            QuickPlanOptionTile(
                plan = SubscriptionPlanType.WEEKLY,
                tagLabel = "WIKI",
                durationLabel = "Wiki 1",
                priceShort = "3,000",
                selected = selectedPlan == SubscriptionPlanType.WEEKLY,
                onClick = { onSelectPlan(SubscriptionPlanType.WEEKLY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_pay_plan_3000")
            )
            QuickPlanOptionTile(
                plan = SubscriptionPlanType.MONTHLY,
                tagLabel = "MWEZI",
                durationLabel = "Mwezi 1",
                priceShort = "10,000",
                selected = selectedPlan == SubscriptionPlanType.MONTHLY,
                onClick = { onSelectPlan(SubscriptionPlanType.MONTHLY) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_pay_plan_10000")
            )
        }

        Text(
            text = "Umechagua: ${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted} (${selectedPlan.durationLabel})",
            color = Color(0xFFFDE68A),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        // Primary Pay Button -> Opens Enter Phone Popup smoothly
        Button(
            onClick = onPayClick,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                )
                .testTag("quick_pay_proceed_to_phone_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Icon(
                imageVector = Icons.Default.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pay • ${selectedPlan.priceFormatted}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }

        // Secondary action: Already Paid -> Enter / Swap Paid VIP Number directly inside popup
        OutlinedButton(
            onClick = onAlreadyPaidClick,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.1.dp, Color(0x8800E5FF)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0x1600E5FF),
                contentColor = NeliGenreCyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("quick_pay_already_paid_button")
        ) {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = null,
                tint = NeliGenreCyan,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Nimeshalipa? Weka Namba ya VIP",
                color = NeliGenreCyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EnterPhonePopupStepContent(
    selectedPlan: SubscriptionPlanType,
    phoneInput: String,
    phoneError: String?,
    onPhoneChange: (String) -> Unit,
    onBackToPlans: () -> Unit,
    onConfirmPay: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_pay_step_enter_phone"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        // Header: Back to Plans | Selected Plan Pill | X Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackToPlans,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("quick_pay_phone_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Badili Kifurushi",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x2EFF007A))
                    .border(1.dp, Color(0x88FF007A), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted}",
                    color = Color(0xFFFFE57F),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("quick_pay_phone_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Funga",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Weka Namba ya Malipo",
                color = Color.White,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "M-Pesa • Tigo Pesa • Airtel Money • HaloPesa",
                color = NeliGenreCyan,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = phoneInput,
            onValueChange = onPhoneChange,
            label = { Text("Namba ya Simu (mf. 0712345678)") },
            placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = NeliGenreCyan
                )
            },
            isError = phoneError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeliMagenta,
                unfocusedBorderColor = Color(0x55FFFFFF),
                errorBorderColor = Color(0xFFFB7185),
                focusedTextColor = NeliTextPrimary,
                unfocusedTextColor = NeliTextPrimary,
                focusedLabelColor = NeliGenreCyan,
                unfocusedLabelColor = NeliTextSecondary,
                cursorColor = NeliMagenta
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_pay_phone_input")
        )

        if (phoneError != null) {
            Text(
                text = phoneError,
                color = Color(0xFFFB7185),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Text(
            text = "Utapokea ujumbe wa kuweka namba yako ya siri (PIN) kwenye simu yako kuthibitisha ${selectedPlan.priceFormatted}.",
            color = NeliTextSecondary,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onConfirmPay,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(listOf(NeliMagenta, NeliWarmOrange))
                )
                .testTag("quick_pay_confirm_send_ussd_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text(
                text = "Pay • ${selectedPlan.priceFormatted}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun EnterPaidNumberPopupStepContent(
    savedPhoneNumber: String,
    isFreeForever: Boolean,
    phoneInput: String,
    lookupError: String?,
    isChecking: Boolean,
    onPhoneChange: (String) -> Unit,
    onBackToPlans: () -> Unit,
    onRemoveCurrentNumber: () -> Unit,
    onUnlockWithPaidNumber: () -> Unit,
    onSwitchToPay: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("quick_pay_step_enter_paid_number"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: Back | INGIA VIP Pill | X Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackToPlans,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("quick_pay_paid_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Rudi Vifurushi",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x2400E5FF))
                    .border(1.dp, Color(0x8800E5FF), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "INGIA VIP",
                    color = NeliGenreCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FFFFFF))
                    .testTag("quick_pay_paid_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Funga",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Weka Namba Iliyolipiwa",
                color = Color.White,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Andika namba ya simu uliyolipia ili kufungua channel ZOTE za VIP au ubadilishe namba.",
                color = NeliTextSecondary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center
            )
        }

        OutlinedTextField(
            value = phoneInput,
            onValueChange = onPhoneChange,
            label = { Text("Namba Iliyolipiwa (mf. 0712345678)") },
            placeholder = { Text("07XXXXXXXX / 06XXXXXXXX") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = NeliGenreCyan
                )
            },
            isError = lookupError != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeliGenreCyan,
                unfocusedBorderColor = Color(0x55FFFFFF),
                errorBorderColor = Color(0xFFFB7185),
                focusedTextColor = NeliTextPrimary,
                unfocusedTextColor = NeliTextPrimary,
                focusedLabelColor = NeliGenreCyan,
                unfocusedLabelColor = NeliTextSecondary,
                cursorColor = NeliGenreCyan
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_pay_paid_phone_input")
        )

        if (lookupError != null) {
            Text(
                text = lookupError,
                color = Color(0xFFFB7185),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onUnlockWithPaidNumber,
            enabled = !isChecking,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF00B4D8), Color(0xFF7C3AED)))
                )
                .testTag("quick_pay_unlock_paid_button"),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            if (isChecking) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.4.dp,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Inakagua VIP...",
                    color = Color.White,
                    fontSize = 14.5.sp,
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
                    text = "Fungua VIP",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (lookupError != null) {
            OutlinedButton(
                onClick = onSwitchToPay,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0x99FBBF24)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("quick_pay_paid_switch_to_plans_button")
            ) {
                Text(
                    text = "Lipia Kifurushi Sasa",
                    color = Color(0xFFFDE68A),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (savedPhoneNumber.isNotBlank() && !isFreeForever) {
            OutlinedButton(
                onClick = onRemoveCurrentNumber,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xAAFB7185)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0x18FB7185),
                    contentColor = Color(0xFFFDA4AF)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("quick_pay_remove_current_number_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove Number",
                    tint = Color(0xFFFB7185),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Remove Number ($savedPhoneNumber)",
                    color = Color(0xFFFDA4AF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun QuickPlanOptionTile(
    plan: SubscriptionPlanType,
    tagLabel: String,
    durationLabel: String,
    priceShort: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) Color(0x3DFF007A) else Color(0xFF151C32)
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFFBBF24) else Color(0x33FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
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
            text = durationLabel,
            color = NeliTextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = priceShort,
            color = if (selected) Color(0xFFFDE047) else Color.White,
            fontSize = 15.sp,
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
