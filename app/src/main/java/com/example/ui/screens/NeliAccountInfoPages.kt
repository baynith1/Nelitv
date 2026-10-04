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
    ABOUT_US("About Us • Kuhusu Nelitv"),
    CONTACT_US("System Information • Nelitv"),
    HELP_FAQ("Help Center & Maswali (FAQ)"),
    PRIVACY_TERMS("Privacy Policy & Terms of Service")
}

const val APP_BRAND_NAME = "Nelitv Official"

/**
 * Displays the Customer Care quick card, About Us, Contact Us, Help/FAQ,
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
                title = "About Us (Kuhusu Nelitv)",
                subtitle = "East Africa's #1 Cinema, Swahili Movies & Azam TV Live App",
                testTag = "about_us_page_button",
                onClick = { onOpenPage(AccountInfoPageType.ABOUT_US) }
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
                            badge = "NELIPLAY • OFFICIAL STREAMING PLATFORM",
                            title = "Welcome to NeliPlay (Neli TV)",
                            body = "NeliPlay is Tanzania and East Africa's premier entertainment & live television super-app. Built for ultra-fast streaming even on Low Bando (mobile data), NeliPlay brings together Live Sports, Tanzanian Television, Swahili Narrated Movies (DJuliot, Lufufu, DJ Afro, Mzee waupuuzi), International Series, and Offline Downloads in one unified cinema experience."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "KEY FEATURES",
                            title = "What You Get on NeliPlay",
                            body = "• Azam TV & Local Live Channels: Watch Azam Sports 1-4 HD, Azam One, Azam Two, Sinema Zetu, ZBC2 (including Saa 10:00 Jioni International Football), Wasafi TV, Crown TV, KIX & WWE.\n" +
                                "• Swahili Narrated Cinema: Latest 2025/2026 TMDB blockbusters and series translated into Kiswahili with automatic 5m 30s intro-skip on Swahili movies.\n" +
                                "• True Offline Storage: Download movies and episodes directly to your phone's internal storage to watch anytime without internet.\n" +
                                "• Automatic Cloud Sync, App Updates & Home Widget: Instant background token synchronization, automatic GitHub APK updates, and an always-ready Home Screen Widget."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "SYSTEM ARCHITECTURE",
                            title = "Cloud Server Infrastructure",
                            body = "Nelitv operates with automated real-time database cloud sync and high-availability CDN stream relays. Subscriptions and tokens update seamlessly across your devices and web without manual support intervention."
                        )
                    }
                }

                AccountInfoPageType.CONTACT_US -> {
                    item {
                        InfoCardBlock(
                            badge = "AUTOMATED PLATFORM",
                            title = "Self-Service Digital Platform",
                            body = "Nelitv is engineered as a fully self-healing, automated application. Subscription status, payments, and Live TV streams sync 24/7 directly with Firebase and cloud CDN relays."
                        )
                    }
                }

                AccountInfoPageType.HELP_FAQ -> {
                    item {
                        InfoCardBlock(
                            badge = "FAQ #1 • SHARE APK & AUTO UPDATES",
                            title = "How do I share the APK or check for new updates?",
                            body = "In My Space (Account), use the 'Share APK • Scan to Download & Link' card to let friends scan your QR code or share the direct GitHub APK link (https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk). Automatic App Updates check GitHub Releases automatically whenever a new version appears."
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
                            body = "All downloaded movies and series episodes play inside NeliPlay's Downloads tab and are also saved in your phone's Internal Storage / Movies / NeliPlay folder for direct offline access anytime."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "FAQ #4 • PICTURE-IN-PICTURE (PiP)",
                            title = "How does Picture-in-Picture work?",
                            body = "PiP works strictly OUTSIDE the app. When you are watching a Live TV channel or Movie and press the Home button or open an SMS/WhatsApp message, the video shrinks into a floating window on your phone screen. Pressing Back inside the app cleanly exits the player without triggering PiP."
                        )
                    }
                }

                AccountInfoPageType.PRIVACY_TERMS -> {
                    item {
                        InfoCardBlock(
                            badge = "PRIVACY POLICY",
                            title = "Your Privacy & Data Security",
                            body = "NeliPlay respects your privacy. Your account profile, watchlist, and offline downloads are stored securely on your device and synced with your personal NeliPlay account. We never sell or share your personal phone number or email with third-party advertisers."
                        )
                    }
                    item {
                        InfoCardBlock(
                            badge = "TERMS OF SERVICE",
                            title = "NeliPlay Usage & Streaming Terms",
                            body = "By using NeliPlay, you agree to use the platform for personal, non-commercial entertainment. Automated notifications and updates are designed to keep you updated on Live Channels, Football and new Cinema releases."
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
