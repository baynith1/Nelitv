package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeliBorder
import com.example.ui.theme.NeliCardPurple
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliSurface
import com.example.ui.theme.NeliSurfaceVariant
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import com.example.widget.NeliHomeWidgetProvider

enum class AccountInfoPageType(val title: String) {
    ABOUT_US("About Us • Kuhusu NeliTV"),
    CONTACT_US("Contact Us • Nairobi Broadcast Company"),
    HELP_FAQ("Help Center & Maswali (FAQ)"),
    PRIVACY_TERMS("Privacy Policy & Terms of Service")
}

const val APP_BRAND_NAME = "Nelitv Official"
const val COMPANY_NAME = "Nairobi Broadcast Company"
const val SUPPORT_EMAIL = "support@nelitv.online"
const val SUPPORT_PHONE = "+254853553584"
const val SERVICE_REGIONS = "Tanzania and Kenya"

/**
 * Displays the Nairobi Broadcast Company support card, About Us, Contact Us, Help/FAQ,
 * and Privacy & Terms cards inside the Account ("My Space") tab.
 */
@Composable
fun NeliAccountSupportAndPagesSection(
    onOpenPage: (AccountInfoPageType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("account_support_and_pages_section"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Official Nairobi Broadcast Company & Support Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF161A36), Color(0xFF1F1230))
                    )
                )
                .border(1.dp, NeliBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
                .testTag("nelitv_company_support_card"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "NeliTV Support",
                    tint = NeliGenreCyan,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "NeliTV • $COMPANY_NAME",
                    color = NeliTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "NeliTV imejengwa na $COMPANY_NAME na kwa sasa inafanya kazi nchini Tanzania na Kenya pekee. Wasiliana nasi kwa msaada wowote:",
                color = NeliTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
                            context.startActivity(emailIntent)
                        } catch (_: Exception) {
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("support_email_button")
                ) {
                    Text(
                        text = SUPPORT_EMAIL,
                        color = NeliGenreCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE"))
                            context.startActivity(dialIntent)
                        } catch (_: Exception) {
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("support_phone_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call NeliTV Support",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = SUPPORT_PHONE,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // Navigation Rows for Relevant Information Pages
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NeliSurface)
                .border(1.dp, NeliBorder, RoundedCornerShape(20.dp))
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Information & Help Center",
                color = NeliTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AccountPageLinkRow(
                icon = Icons.Default.Info,
                iconTint = NeliMagenta,
                title = "About Us (Kuhusu NeliTV)",
                subtitle = "Built by $COMPANY_NAME • Tanzania & Kenya",
                testTag = "about_us_page_button",
                onClick = { onOpenPage(AccountInfoPageType.ABOUT_US) }
            )

            AccountPageLinkRow(
                icon = Icons.Default.SupportAgent,
                iconTint = NeliGenreCyan,
                title = "Contact Us (Wasiliana Nasi)",
                subtitle = "$SUPPORT_EMAIL • $SUPPORT_PHONE",
                testTag = "contact_us_page_button",
                onClick = { onOpenPage(AccountInfoPageType.CONTACT_US) }
            )

            AccountPageLinkRow(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                iconTint = Color(0xFFF59E0B),
                title = "Help Center & FAQ (Maswali)",
                subtitle = "Low Bando Saver, Offline Downloads, APK Share & Auto Updates",
                testTag = "faq_page_button",
                onClick = { onOpenPage(AccountInfoPageType.HELP_FAQ) }
            )

            AccountPageLinkRow(
                icon = Icons.Default.Policy,
                iconTint = Color(0xFFA855F7),
                title = "Privacy Policy & Terms of Service",
                subtitle = "User data protection, streaming rights & community rules",
                testTag = "privacy_terms_page_button",
                onClick = { onOpenPage(AccountInfoPageType.PRIVACY_TERMS) }
            )

            AccountPageLinkRow(
                icon = Icons.Default.Widgets,
                iconTint = NeliMagenta,
                title = "Home Screen Widget (Auto-Active)",
                subtitle = "Top: Azam Sports 1, Azam Two, WWE • Bottom: 2026 Movies",
                testTag = "refresh_home_widget_row",
                onClick = {
                    NeliHomeWidgetProvider.updateAllWidgets(context)
                    NeliHomeWidgetProvider.requestPinWidget(context)
                }
            )
        }
    }
}

@Composable
private fun AccountPageLinkRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NeliSurfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NeliTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = NeliTextSecondary,
                fontSize = 11.sp
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NeliTextSecondary
        )
    }
}

/**
 * Full-screen detail view for About Us, Contact Us, Help/FAQ, or Privacy & Terms inside the Account tab.
 */
@Composable
fun AccountInfoDetailScreen(
    pageType: AccountInfoPageType,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("account_info_detail_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NeliSurfaceVariant)
                    .testTag("account_info_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = NeliTextPrimary
                )
            }

            Text(
                text = pageType.title,
                color = NeliTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            when (pageType) {
                AccountInfoPageType.ABOUT_US -> {
                    item {
                        InfoCardBlock(
                            badge = "NELITV • NAIROBI BROADCAST COMPANY",
                            title = "Kuhusu NeliTV (About NeliTV)",
                            body = "NeliTV ni jukwaa rasmi la burudani na matangazo ya moja kwa moja ya TV lililojengwa na $COMPANY_NAME. Kwa sasa huduma za NeliTV zinapatikana na kufanya kazi nchini Tanzania na Kenya pekee.\n\n" +
                                "NeliTV is built by $COMPANY_NAME and currently operates in $SERVICE_REGIONS only. Designed for fast, smooth streaming even on Low Bando (mobile data), NeliTV brings together Live Sports, East African Television, Swahili Narrated Movies, International Series, and Offline Downloads in one modern app."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "KEY FEATURES",
                            title = "Huduma Unazopata Kwenye NeliTV",
                            body = "• Live TV & Sports: Tazama Azam Sports 1-4 HD, Azam One, Azam Two, Sinema Zetu, ZBC2, Wasafi TV, Crown TV, beIN Sports, ESPN 7, MUTV, FIFA+ Women, NBC Sports NOW, KIX & WWE.\n" +
                                "• Swahili Cinema & Series: Filamu mpya za 2025/2026 zilizotafsiriwa Kiswahili pamoja na tamthilia za kimataifa.\n" +
                                "• Offline Downloads: Pakua filamu na vipindi utazame bila intaneti muda wowote.\n" +
                                "• Auto-Renewing CDN & Cloud Sync: Mfumo wa kisasa unaoji-renew CDN keys moja kwa moja ili ufurahie matangazo bila kukwama."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "OFFICIAL CONTACT • NAIROBI BROADCAST COMPANY",
                            title = "Mawasiliano Rasmi",
                            body = "Kampuni: $COMPANY_NAME\nEneo la Huduma: $SERVICE_REGIONS (Tanzania na Kenya kwa sasa pekee)\nBarua Pepe (Email): $SUPPORT_EMAIL\nSimu / WhatsApp: $SUPPORT_PHONE"
                        )
                    }
                }

                AccountInfoPageType.CONTACT_US -> {
                    item {
                        InfoCardBlock(
                            badge = "NAIROBI BROADCAST COMPANY",
                            title = "Wasiliana na NeliTV Support",
                            body = "NeliTV imejengwa na kuendeshwa na $COMPANY_NAME kwa watumiaji wa Tanzania na Kenya pekee kwa sasa.\n\n" +
                                "Kwa msaada wa kiufundi, matangazo, ushirikiano au maoni yoyote, wasiliana nasi kupitia:\n" +
                                "• Email: $SUPPORT_EMAIL\n" +
                                "• Simu: $SUPPORT_PHONE\n" +
                                "• Eneo la Huduma: Tanzania na Kenya pekee"
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")))
                                    } catch (_: Exception) {
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Email: $SUPPORT_EMAIL", color = NeliGenreCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$SUPPORT_PHONE")))
                                    } catch (_: Exception) {
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeliMagenta),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Piga: $SUPPORT_PHONE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                AccountInfoPageType.HELP_FAQ -> {
                    item {
                        InfoCardBlock(
                            badge = "FAQ #1 • SERVICE AVAILABILITY",
                            title = "NeliTV inafanya kazi nchi zipi?",
                            body = "NeliTV imejengwa na $COMPANY_NAME na kwa sasa inafanya kazi rasmi nchini Tanzania na Kenya pekee. Kwa maswali zaidi tuandikie kupitia $SUPPORT_EMAIL au piga $SUPPORT_PHONE."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "FAQ #2 • LOW BANDO SAVER",
                            title = "How does Low Bando Saver save mobile data?",
                            body = "In My Space -> Auto Quality & Mobile Data Control, select 'Low Bando'. The player automatically optimizes video bitrate so you can watch full football matches and movies smoothly using minimal MBs."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "FAQ #3 • OFFLINE DOWNLOADS",
                            title = "Where are my downloaded movies saved?",
                            body = "All downloaded movies and series episodes play inside NeliTV's Downloads tab and are securely stored in NeliTV's private in-app offline storage for direct offline access anytime."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "FAQ #4 • PICTURE-IN-PICTURE (PiP)",
                            title = "How does Picture-in-Picture work?",
                            body = "PiP works strictly OUTSIDE the app for standard Live TV and Movies. When you press the Home button, the video shrinks into a floating window on your phone screen. Pressing Back inside the app cleanly exits the player without triggering PiP."
                        )
                    }
                }

                AccountInfoPageType.PRIVACY_TERMS -> {
                    item {
                        InfoCardBlock(
                            badge = "PRIVACY POLICY",
                            title = "Your Privacy & Data Security",
                            body = "NeliTV ($COMPANY_NAME) respects your privacy. Your account profile, watchlist, and offline downloads are stored securely on your device and synced with your personal NeliTV account. We never sell or share your personal phone number or email with third-party data brokers."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "TERMS OF SERVICE",
                            title = "NeliTV Usage & Streaming Terms",
                            body = "By using NeliTV, built by $COMPANY_NAME for users in $SERVICE_REGIONS, you agree to use the platform for personal, non-commercial entertainment. For inquiries or support contact $SUPPORT_EMAIL or $SUPPORT_PHONE."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCardBlock(
    badge: String,
    title: String,
    body: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NeliSurface)
            .border(1.dp, NeliBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = badge,
            color = NeliGenreCyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = title,
            color = NeliTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = body,
            color = NeliTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
