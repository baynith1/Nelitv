package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AuthRepository
import com.example.data.NeliFreeForeverAccountsManager
import com.example.data.NeliTvRepository
import com.example.data.local.NeliDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthSecurityAndServerSyncTest {

    private lateinit var context: Context
    private lateinit var db: NeliDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NeliDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `free forever and admin wrong password never leaks password or account role`() = runBlocking {
        val dao = db.mediaDao()
        val testIdentifiers = listOf(
            "user1@login.com",
            "user2@login.com",
            "user3@login.com",
            "user4@login.com",
            "user5@login.com",
            "user1",
            "Admin@login.com",
            "admin"
        )

        testIdentifiers.forEach { identifier ->
            val loginResult = AuthRepository.signInWithEmailAndPassword(
                context = context,
                dao = dao,
                email = identifier,
                password = "fake_wrong_password"
            )
            assertTrue("Login must fail for wrong password on $identifier", loginResult.isFailure)
            val err = loginResult.exceptionOrNull()?.message.orEmpty()
            assertFalse("Error message must NEVER leak Free123: $err", err.contains("Free123", ignoreCase = true))
            assertFalse("Error message must NEVER leak 123456: $err", err.contains("123456", ignoreCase = true))
            assertFalse("Error message must NEVER leak password hint: $err", err.contains("Password sahihi", ignoreCase = true))
            assertFalse("Error message must NEVER leak free account role: $err", err.contains("akaunti ya bure", ignoreCase = true))
            assertFalse("Error message must NEVER leak Admin role: $err", err.contains("Admin", ignoreCase = true))
        }

        // Sign-up attempt using a Free Forever email with fake password must also never leak Free123
        val signUpAttempt = AuthRepository.signUpWithEmailAndPassword(
            context = context,
            dao = dao,
            realName = "Test User",
            email = "user1@login.com",
            password = "wrongPassword99"
        )
        assertTrue(signUpAttempt.isFailure)
        val signUpErr = signUpAttempt.exceptionOrNull()?.message.orEmpty()
        assertFalse("Sign-up error must NEVER leak Free123: $signUpErr", signUpErr.contains("Free123", ignoreCase = true))
        assertFalse("Sign-up error must NEVER leak account role: $signUpErr", signUpErr.contains("akaunti ya bure", ignoreCase = true))

        // Valid login with real Free Forever password still succeeds
        NeliFreeForeverAccountsManager.adminResetAllDevicesForAccount(context, "user1@login.com")
        val validLogin = AuthRepository.signInWithEmailAndPassword(
            context = context,
            dao = dao,
            email = "user1@login.com",
            password = NeliFreeForeverAccountsManager.FREE_FOREVER_PASSWORD
        )
        assertTrue("Valid Free Forever login must succeed", validLogin.isSuccess)
    }

    @Test
    fun `nelitv server bootstrap parses announcement https update features and epg`() {
        val samplePayload = """
            {
              "token": "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjI1MjQ2MDgwMDB9.sig",
              "cdnHost": "https://cdn.nelitv.online",
              "neverExpires": true,
              "announcement": {
                "id": "ann_2026_1",
                "message": "Mechi kubwa leo saa 10 jioni!",
                "enabled": true
              },
              "appUpdate": {
                "latestVersion": "v2026.12.01",
                "apkUrl": "https://nelitv.online/downloads/Nelitv.apk",
                "notes": "Kasi mpya ya TV na chaneli mpya",
                "mandatory": false
              },
              "features": ["epg_live", "proxy_failover", "smart_tv_free"],
              "channels": [
                {
                  "id": "azam_sports_1",
                  "name": "Azam Sports 1 HD",
                  "streamUrl": "https://nelitv.online/live/azam1/index.m3u8",
                  "currentProgram": "Simba vs Yanga Live",
                  "nextProgram": "Uchambuzi wa Mechi"
                }
              ]
            }
        """.trimIndent()

        val parsed = NeliTvRepository.parseAppBootstrapJson(
            rawJson = samplePayload,
            isFromCache = false,
            syncedAtMs = 1_700_000_000_000L
        )
        assertNotNull(parsed)
        val state = parsed!!
        assertEquals("Mechi kubwa leo saa 10 jioni!", state.serverAnnouncement)
        assertEquals("ann_2026_1", state.serverAnnouncementId)
        assertEquals("v2026.12.01", state.serverUpdateVersion)
        assertEquals("https://nelitv.online/downloads/Nelitv.apk", state.serverUpdateApkUrl)
        assertTrue(state.serverFeatures.contains("epg_live"))
        assertTrue(state.serverFeatures.contains("smart_tv_free"))
        assertEquals(1, state.channels.size)
        assertEquals("Simba vs Yanga Live", state.channels.first().currentProgram)
        assertEquals("Uchambuzi wa Mechi", state.channels.first().nextProgram)

        // Insecure http:// APK URLs must be rejected
        val insecureUpdatePayload = """
            {
              "appUpdate": {
                "latestVersion": "v9.9.9",
                "apkUrl": "http://insecure.example.com/app.apk"
              },
              "channels": []
            }
        """.trimIndent()
        val insecureParsed = NeliTvRepository.parseAppBootstrapJson(
            rawJson = insecureUpdatePayload,
            isFromCache = false,
            syncedAtMs = 1_700_000_000_000L
        )
        assertNotNull(insecureParsed)
        assertEquals("", insecureParsed!!.serverUpdateApkUrl)
    }

    @Test
    fun `mergeServerChannelLists combines primary and supplementary nelitv channels without duplicates`() {
        val primary = (1..42).map { i ->
            com.example.model.LiveChannel(
                id = "ch_$i",
                slug = "channel-$i",
                name = "Neli Channel $i",
                description = "Live TV",
                streamUrl = "https://nelitv.online/live/ch$i/index.m3u8",
                streamFormat = "hls",
                thumbnailUrl = ""
            )
        }
        val supplementary = (35..63).map { i ->
            com.example.model.LiveChannel(
                id = if (i <= 42) "ch_$i" else "extra_ch_$i",
                slug = "channel-$i",
                name = "Neli Channel $i",
                description = "Live TV",
                streamUrl = "https://nelitv.online/live/ch$i/index.m3u8",
                streamFormat = "hls",
                thumbnailUrl = ""
            )
        }

        val merged = NeliTvRepository.mergeServerChannelLists(primary, supplementary)
        assertEquals("All 63 unique channels must appear immediately", 63, merged.size)
        assertEquals("ch_1", merged.first().id)
        assertEquals("extra_ch_63", merged.last().id)
    }

    @Test
    fun `youtube style seamless abr uses stable hysteresis and never forces mandatory login`() {
        // Verify YouTube-style network stability ABR hysteresis constants
        assertTrue(
            "Mobile upgrade stability window should be >= 18s to avoid rapid 480p->720p->1080p loading",
            com.example.player.LivePlayerController.ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS >= 18_000
        )
        assertTrue(
            "Smart TV upgrade stability window should be >= 25s",
            com.example.player.LivePlayerController.TV_ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS >= 25_000
        )
        assertTrue(
            "Bandwidth headroom fraction should leave >= 25% margin against brief dips",
            com.example.player.LivePlayerController.ADAPTIVE_BANDWIDTH_FRACTION <= 0.75f
        )

        // Verify login & sign-up are 100% optional on both mobile and TV
        assertFalse(com.example.ui.components.requiresMandatoryGoogleSignIn(currentUser = null, isTelevision = false))
        assertFalse(com.example.ui.components.requiresMandatoryGoogleSignIn(currentUser = null, isTelevision = true))

        // Verify payment without login unlocks VIP locally without demanding post-payment sign-in
        com.example.data.NeliSubscriptionManager.resetForTesting(context)
        val activated = com.example.data.NeliSubscriptionManager.activateVerifiedSubscription(
            context = context,
            plan = com.example.data.SubscriptionPlanType.entries.first(),
            phone = "0712345678",
            verifiedOrderId = "ORD_NO_LOGIN_TEST"
        )
        assertTrue(activated.isVerified)
        assertTrue(activated.isActiveNow)
        assertFalse("Paying without login must never require post-payment login", activated.requiresPostPaymentAuth)
    }
}
