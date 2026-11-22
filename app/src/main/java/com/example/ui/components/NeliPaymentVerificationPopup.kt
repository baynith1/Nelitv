package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val NELI_USSD_RESEND_COOLDOWN_SEC = 30

private enum class PaymentPopupStage {
    SENDING_USSD,
    WAITING_FOR_PIN,
    USSD_SEND_FAILED,
    SUCCESS_TICK
}

private sealed interface DualPaymentCheckResult {
    data class VerifiedPaid(
        val verifiedOrderId: String,
        val supabaseRecord: NeliSupabaseSyncManager.SupabaseSubscriptionRecord? = null
    ) : DualPaymentCheckResult

    data class NotYetPaid(
        val latestOrderFailed: Boolean = false,
        val failedStatusLabel: String = "",
        val bothSourcesErrored: Boolean = false
    ) : DualPaymentCheckResult
}

/**
 * Production Payment Verification Popup opened immediately when the user taps Pay on a selected plan:
 * - Sends USSD push via HarakaPay and automatically polls BOTH HarakaPay AND Supabase every 3.5s.
 * - As soon as user enters their mobile money PIN and payment completes -> displays an animated green
 *   Tick checkmark, unlocks Active Member VIP, saves to Supabase, auto-closes popup, and returns to Homepage.
 * - Includes "Nimelipa • Refresh" button that actively queries BOTH HarakaPay and Supabase (resilient if one
 *   service has a temporary error), unlocking ONLY when genuinely paid.
 * - Includes "Omba USSD Upya" button with single-flight lock, pre-verification check, and 30-second
 *   non-overlapping cooldown so second USSD prompts never collide with the first.
 * - Includes top-right "X" button to cancel and close cleanly with ZERO changes to the user's Premium badge.
 */
@Composable
fun NeliPaymentVerificationPopup(
    normalizedPhone: String,
    selectedPlan: SubscriptionPlanType,
    currentUser: UserAccountEntity?,
    wasAlreadyActiveBeforePopup: Boolean,
    onPaymentConfirmedReturnHome: () -> Unit,
    onCancelledByUser: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(PaymentPopupStage.SENDING_USSD) }
    var trackedOrderIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var ussdAttemptCount by remember { mutableIntStateOf(0) }
    var ussdResendCooldownSec by remember { mutableIntStateOf(0) }
    var isUssdRequestInFlight by remember { mutableStateOf(false) }
    var isManualRefreshing by remember { mutableStateOf(false) }
    var hasConfirmedPayment by remember { mutableStateOf(false) }
    val sessionStartedAtMs = remember { mutableLongStateOf(System.currentTimeMillis()) }

    var popupInfoNote by remember {
        mutableStateOf("Inatuma ujumbe wa malipo (USSD) kwenye $normalizedPhone...")
    }
    var popupWarningNote by remember { mutableStateOf<String?>(null) }

    // Animated scale for the green checkmark tick upon verified payment
    val tickScale = remember { Animatable(0.2f) }

    suspend fun verifyAcrossHarakaPayAndSupabase(
        orderIdsSnapshot: List<String>
    ): DualPaymentCheckResult = coroutineScope {
        val harakaDeferred = async {
            var latestFailed = false
            var failedLabel = ""
            var anyHarakaSuccessCall = false

            for ((index, orderId) in orderIdsSnapshot.withIndex()) {
                val res = HarakaPayRepository.checkPaymentStatus(orderId)
                val statusObj = res.getOrNull()
                if (statusObj != null) {
                    anyHarakaSuccessCall = true
                    if (statusObj.isCompleted) {
                        return@async Triple(orderId, false, "")
                    }
                    if (index == 0 && statusObj.isFailed) {
                        latestFailed = true
                        failedLabel = statusObj.status
                    }
                }
            }
            Triple(if (anyHarakaSuccessCall) "" else null, latestFailed, failedLabel)
        }

        val supabaseOrderDeferred = async {
            NeliSupabaseSyncManager.queryCompletedPaymentByOrderIds(orderIdsSnapshot)
        }

        val supabaseSubDeferred = async {
            NeliSupabaseSyncManager.querySubscriptionByPhone(normalizedPhone)
        }

        val harakaTriple = harakaDeferred.await()
        val harakaMatchedOrder = harakaTriple.first
        if (!harakaMatchedOrder.isNullOrBlank()) {
            return@coroutineScope DualPaymentCheckResult.VerifiedPaid(
                verifiedOrderId = harakaMatchedOrder
            )
        }

        val supabaseOrderRes = supabaseOrderDeferred.await()
        val supabaseMatchedOrderId = supabaseOrderRes.getOrNull()
        if (!supabaseMatchedOrderId.isNullOrBlank()) {
            return@coroutineScope DualPaymentCheckResult.VerifiedPaid(
                verifiedOrderId = supabaseMatchedOrderId
            )
        }

        val supabaseSubRes = supabaseSubDeferred.await()
        val subRecord = supabaseSubRes.getOrNull()
        if (subRecord != null && subRecord.isActive) {
            val isFreshThisSession = subRecord.activatedAtMs >= (sessionStartedAtMs.longValue - 60_000L)
            if (!wasAlreadyActiveBeforePopup || isFreshThisSession) {
                return@coroutineScope DualPaymentCheckResult.VerifiedPaid(
                    verifiedOrderId = orderIdsSnapshot.firstOrNull().orEmpty().ifBlank {
                        "SB-${subRecord.activatedAtMs}"
                    },
                    supabaseRecord = subRecord
                )
            }
        }

        val harakaErrored = harakaTriple.first == null && orderIdsSnapshot.isNotEmpty()
        val supabaseErrored = supabaseOrderRes.isFailure && supabaseSubRes.isFailure
        DualPaymentCheckResult.NotYetPaid(
            latestOrderFailed = harakaTriple.second,
            failedStatusLabel = harakaTriple.third,
            bothSourcesErrored = harakaErrored && supabaseErrored
        )
    }

    suspend fun activateConfirmedPaymentAndShowTick(verified: DualPaymentCheckResult.VerifiedPaid) {
        if (hasConfirmedPayment) return
        hasConfirmedPayment = true
        isManualRefreshing = false
        isUssdRequestInFlight = false
        popupWarningNote = null

        val activated = if (verified.supabaseRecord != null && verified.supabaseRecord.isActive) {
            NeliSubscriptionManager.activateVerifiedSubscriptionFromSupabase(
                context = context,
                record = verified.supabaseRecord
            )
        } else {
            NeliSubscriptionManager.activateVerifiedSubscription(
                context = context,
                plan = selectedPlan,
                phone = normalizedPhone,
                verifiedOrderId = verified.verifiedOrderId
            )
        }

        if (currentUser != null && currentUser.email.isNotBlank()) {
            NeliSubscriptionManager.linkUserAccountToSubscription(
                context = context,
                uid = currentUser.uid,
                email = currentUser.email,
                realName = currentUser.realName
            )
        }

        NeliSupabaseSyncManager.syncSubscriptionAndPaymentNow(
            phoneNumber = normalizedPhone,
            planId = selectedPlan.id,
            amountTzs = selectedPlan.amountTzs,
            orderId = verified.verifiedOrderId,
            activatedAtMs = activated.activatedAtMs,
            expiresAtMs = activated.expiresAtMs,
            userEmail = currentUser?.email.orEmpty(),
            userId = currentUser?.uid.orEmpty(),
            providerReference = "HPAY-${verified.verifiedOrderId}"
        )

        stage = PaymentPopupStage.SUCCESS_TICK
    }

    suspend fun sendSingleFlightUssdPush(isResend: Boolean) {
        if (isUssdRequestInFlight || hasConfirmedPayment) return
        isUssdRequestInFlight = true
        popupWarningNote = null

        // Before sending a second USSD push, always verify the previous USSD wasn't just paid!
        if (isResend && trackedOrderIds.isNotEmpty()) {
            popupInfoNote = "Inahakiki ombi lililopita kabla ya kutuma USSD mpya..."
            val preCheck = verifyAcrossHarakaPayAndSupabase(trackedOrderIds)
            if (preCheck is DualPaymentCheckResult.VerifiedPaid) {
                activateConfirmedPaymentAndShowTick(preCheck)
                return
            }
        }

        if (!isResend) {
            stage = PaymentPopupStage.SENDING_USSD
            popupInfoNote = "Inatuma ujumbe wa malipo (USSD) kwenye $normalizedPhone..."
        } else {
            popupInfoNote = "Inatuma ombi jipya la USSD kwenye $normalizedPhone..."
        }

        val res = HarakaPayRepository.collectPayment(
            phone = normalizedPhone,
            amount = selectedPlan.amountTzs,
            description = "Nelitv ${selectedPlan.titleSwahili} VIP"
        )
        isUssdRequestInFlight = false

        res.onSuccess { collect ->
            ussdAttemptCount += 1
            trackedOrderIds = (listOf(collect.orderId) + trackedOrderIds).distinct()
            NeliSubscriptionManager.savePendingOrder(
                context = context,
                plan = selectedPlan,
                phone = normalizedPhone,
                orderId = collect.orderId
            )
            ussdResendCooldownSec = NELI_USSD_RESEND_COOLDOWN_SEC
            stage = PaymentPopupStage.WAITING_FOR_PIN
            popupWarningNote = null
            popupInfoNote = if (ussdAttemptCount <= 1) {
                "Ujumbe wa USSD umetumwa kwenye $normalizedPhone. Weka namba yako ya siri (PIN) kuthibitisha ${selectedPlan.priceFormatted}."
            } else {
                "Ujumbe mpya wa USSD (Ombi #$ussdAttemptCount) umetumwa kwenye $normalizedPhone. Weka PIN yako kuthibitisha ${selectedPlan.priceFormatted}."
            }
        }.onFailure { err ->
            ussdResendCooldownSec = 0
            if (trackedOrderIds.isEmpty()) {
                stage = PaymentPopupStage.USSD_SEND_FAILED
            } else {
                stage = PaymentPopupStage.WAITING_FOR_PIN
            }
            popupWarningNote = err.message?.takeIf { it.isNotBlank() }
                ?: "Imeshindikana kutuma USSD. Angalia mtandao kisha bonyeza 'Omba USSD Upya'."
        }
    }

    // 1) Fire initial USSD push immediately when popup opens
    LaunchedEffect(Unit) {
        sendSingleFlightUssdPush(isResend = false)
    }

    // 2) Cooldown countdown timer so consecutive USSD pushes NEVER overlap on the user's phone
    LaunchedEffect(ussdResendCooldownSec, stage) {
        if (ussdResendCooldownSec > 0 && stage == PaymentPopupStage.WAITING_FOR_PIN) {
            delay(1_000L)
            ussdResendCooldownSec = (ussdResendCooldownSec - 1).coerceAtLeast(0)
        }
    }

    // 3) Automatic live background poller (checks BOTH HarakaPay and Supabase every 3.5s)
    LaunchedEffect(stage, trackedOrderIds) {
        if (stage != PaymentPopupStage.WAITING_FOR_PIN || trackedOrderIds.isEmpty()) return@LaunchedEffect
        var polls = 0
        while (polls < 60 && stage == PaymentPopupStage.WAITING_FOR_PIN && !hasConfirmedPayment) {
            delay(3_500L)
            polls++
            if (hasConfirmedPayment || stage != PaymentPopupStage.WAITING_FOR_PIN) break
            val check = verifyAcrossHarakaPayAndSupabase(trackedOrderIds)
            when (check) {
                is DualPaymentCheckResult.VerifiedPaid -> {
                    activateConfirmedPaymentAndShowTick(check)
                    break
                }
                is DualPaymentCheckResult.NotYetPaid -> {
                    if (check.latestOrderFailed) {
                        ussdResendCooldownSec = 0
                        popupWarningNote =
                            "Ombi la USSD halikukamilika (${check.failedStatusLabel.ifBlank { "imesitishwa" }}). Bonyeza 'Omba USSD Upya' kupata ujumbe mwingine wa kuweka PIN."
                    }
                }
            }
        }
    }

    // 4) Success Tick animation -> Auto-close popup -> Automatic return to Homepage!
    LaunchedEffect(stage) {
        if (stage == PaymentPopupStage.SUCCESS_TICK) {
            tickScale.snapTo(0.2f)
            tickScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            delay(1_250L)
            android.widget.Toast.makeText(
                context,
                "Karibu Active Member! Kifurushi chako kimefunguliwa.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onPaymentConfirmedReturnHome()
        }
    }

    Dialog(
        onDismissRequest = {
            // Only explicit X button cancels to prevent accidental tap dismissal while entering USSD PIN
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF131936), Color(0xFF0A0E22))
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        brush = Brush.horizontalGradient(
                            if (stage == PaymentPopupStage.SUCCESS_TICK) {
                                listOf(Color(0xFF34D399), Color(0xFF00E5FF))
                            } else {
                                listOf(NeliMagenta, NeliGenreCyan)
                            }
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp)
                    .testTag("payment_verification_popup"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row with Package Summary & Explicit X Close/Cancel Button (no change to VIP badge!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (stage) {
                                PaymentPopupStage.SUCCESS_TICK -> "Malipo Yamefanikiwa!"
                                PaymentPopupStage.SENDING_USSD -> "Inatuma USSD..."
                                PaymentPopupStage.USSD_SEND_FAILED -> "Tatizo la Kutuma USSD"
                                PaymentPopupStage.WAITING_FOR_PIN -> "Thibitisha Malipo (PIN)"
                            },
                            color = if (stage == PaymentPopupStage.SUCCESS_TICK) Color(0xFF34D399) else Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${selectedPlan.titleSwahili} • ${selectedPlan.priceFormatted} • $normalizedPhone",
                            color = Color(0xFFFFE57F),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (stage != PaymentPopupStage.SUCCESS_TICK) {
                        IconButton(
                            onClick = {
                                // Cancel payment cleanly: clear pending order ONLY, ZERO change to active VIP badge!
                                NeliSubscriptionManager.clearPendingOrder(context)
                                onCancelledByUser()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x24FFFFFF))
                                .testTag("payment_popup_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Ghairi na Funga",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Center Visual Indicator (Animated Green Tick OR Live Waiting Spinner)
                if (stage == PaymentPopupStage.SUCCESS_TICK) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(tickScale.value)
                            .clip(CircleShape)
                            .background(Color(0x2910B981))
                            .border(2.dp, Color(0xFF34D399), CircleShape)
                            .testTag("payment_popup_success_tick"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Malipo Yamethibitishwa",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(62.dp)
                        )
                    }

                    Text(
                        text = "Hongera! Umekuwa Active Member",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Kifurushi chako cha ${selectedPlan.priceFormatted} kimefunguliwa na kuhifadhiwa. Inarudi ukurasa wa mwanzo...",
                        color = Color(0xFFA7F3D0),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0x2400E5FF))
                            .border(1.4.dp, Color(0x6600E5FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (stage == PaymentPopupStage.USSD_SEND_FAILED) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Hitilafu",
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(38.dp)
                            )
                        } else {
                            CircularProgressIndicator(
                                color = NeliGenreCyan,
                                trackColor = Color(0x33FFFFFF),
                                strokeWidth = 3.2.dp,
                                modifier = Modifier.size(56.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    // Status & Instruction Text
                    Text(
                        text = popupInfoNote,
                        color = NeliTextPrimary,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center
                    )

                    if (stage == PaymentPopupStage.WAITING_FOR_PIN) {
                        Text(
                            text = "Baada ya kuweka PIN, popup hii itajifunga yenyewe na kuonyesha tiki ya kijani.",
                            color = NeliGenreCyan,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Warning / Unpaid Feedback Banner inside Popup
                    popupWarningNote?.let { warn ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x24FB7185))
                                .border(1.dp, Color(0x88FB7185), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFFB7185),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = warn,
                                color = Color(0xFFFECDD3),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Button 1: "Nimelipa • Refresh" (Checks BOTH HarakaPay AND Supabase!)
                    Button(
                        onClick = {
                            if (isManualRefreshing || isUssdRequestInFlight || hasConfirmedPayment) return@Button
                            isManualRefreshing = true
                            popupWarningNote = null
                            scope.launch {
                                val result = verifyAcrossHarakaPayAndSupabase(trackedOrderIds)
                                isManualRefreshing = false
                                when (result) {
                                    is DualPaymentCheckResult.VerifiedPaid -> {
                                        activateConfirmedPaymentAndShowTick(result)
                                    }
                                    is DualPaymentCheckResult.NotYetPaid -> {
                                        if (result.latestOrderFailed) {
                                            ussdResendCooldownSec = 0
                                            popupWarningNote =
                                                "Malipo hayakukamilika (${result.failedStatusLabel.ifBlank { "imesitishwa" }}). Bonyeza 'Omba USSD Upya' ili utumiwe ujumbe mwingine wa kuweka PIN."
                                        } else if (result.bothSourcesErrored) {
                                            popupWarningNote =
                                                "Mtandao umeyumba wakati wa kukagua HarakaPay na Supabase. Subiri sekunde chache kisha bonyeza 'Nimelipa • Refresh' tena."
                                        } else {
                                            popupWarningNote =
                                                "Malipo bado hayajasoma. Hakikisha umeweka namba yako ya siri (PIN) kwenye ujumbe wa simu — kama hujapata ujumbe bonyeza 'Omba USSD Upya'."
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isManualRefreshing && !isUssdRequestInFlight && stage != PaymentPopupStage.SENDING_USSD,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(listOf(Color(0xFF059669), Color(0xFF00B4D8)))
                            )
                            .testTag("payment_popup_refresh_button")
                    ) {
                        if (isManualRefreshing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Inakagua Malipo...",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Nimelipa • Refresh",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.5.sp
                            )
                        }
                    }

                    // Button 2: "Omba USSD Upya" (Sends clean non-overlapping 2nd USSD prompt)
                    val canResendUssd = !isUssdRequestInFlight && !isManualRefreshing && ussdResendCooldownSec == 0
                    OutlinedButton(
                        onClick = {
                            if (!canResendUssd) return@OutlinedButton
                            scope.launch {
                                sendSingleFlightUssdPush(isResend = true)
                            }
                        },
                        enabled = canResendUssd,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("payment_popup_resend_ussd_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = if (canResendUssd) Color(0xFFFFD54F) else NeliTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUssdRequestInFlight) {
                                "Inatuma USSD Upya..."
                            } else if (ussdResendCooldownSec > 0) {
                                "Omba USSD Upya (${ussdResendCooldownSec}s)"
                            } else {
                                "Omba USSD Upya"
                            },
                            color = if (canResendUssd) Color(0xFFFFD54F) else NeliTextSecondary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.5.sp
                        )
                    }

                    Text(
                        text = "Ukighairi kulipa, bonyeza X juu kufunga — akaunti yako haitabadilika.",
                        color = NeliTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
