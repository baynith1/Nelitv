package com.example

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.view.LayoutInflater
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.example.ads.NeliAdManager
import com.example.data.NeliAdminManager
import com.example.data.NeliAppUpdateManager
import com.example.data.NeliTvRepository
import com.example.model.LiveChannel
import com.example.player.BatteryPowerProfile
import com.example.player.LivePlayerController
import com.example.player.Mp4CencDecryptor
import com.example.player.NeliLocalStreamEngine
import com.example.player.NeliPooledOkHttpDataSource
import com.example.ui.components.BottomNavTab
import com.example.ui.components.requiresMandatoryGoogleSignIn
import com.example.ui.theme.NeliThemeManager
import com.example.ui.tv.NeliTvDetector
import com.example.ui.tv.TvNavItems
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TvPlaybackRobolectricTest {

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NeliThemeManager.setTvModeSetting(context, null)
    }

    @Test
    fun `smart tv mode detects television disables ads skips mandatory login and uses optimal power`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NeliThemeManager.setTvModeSetting(context, true)

        assertTrue("TV mode must be active when forceTvMode is enabled", NeliTvDetector.isTelevision(context))
        val tvConfig = NeliTvDetector.resolveConfiguration(context)
        assertTrue(tvConfig.isTelevision)
        assertTrue(tvConfig.isTvDevice)

        // Ads must stay completely disabled on Smart TV so remote focus never traps and startup is 0ms
        assertFalse("AdMob must be disabled on Smart TV", NeliAdManager.isAdsAllowed(context))

        // Mandatory mobile Google Sign-In must never block Smart TV users
        assertFalse(
            "Smart TV must not force mobile sign-in wall",
            requiresMandatoryGoogleSignIn(currentUser = null, isTelevision = true)
        )

        // Verify UI_MODE_TYPE_TELEVISION hardware detection works cleanly
        val tvContext = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or Configuration.UI_MODE_TYPE_TELEVISION
            }
        )
        NeliThemeManager.setTvModeSetting(context, null)
        assertTrue("Hardware UI_MODE_TYPE_TELEVISION must be detected", NeliTvDetector.isHardwareTelevision(tvContext))
        assertTrue(NeliTvDetector.isTelevision(tvContext))
    }

    @Test
    fun `smart tv is 100 percent free with no account tab and all channels unlocked without login or premium`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NeliThemeManager.setTvModeSetting(context, true)

        // TV side navigation rail must NOT expose Account / Login / Premium
        assertFalse(
            "Smart TV navigation must not show Account/Login/Premium tab",
            TvNavItems.any { it.tab == BottomNavTab.ACCOUNT }
        )

        // Even if admin locks all channels for mobile free users, Smart TV remains 100% free & unlocked
        NeliAdminManager.setLockAllChannels(context, true)
        val vipAzamChannel = LiveChannel(
            id = "azam_sports_1_hd",
            slug = "AzamSport1",
            name = "Azam Sports 1 HD",
            description = "Live Sports",
            streamUrl = "https://1445733302.rsc.cdn77.org/live/eds/AzamSport1/DASH/AzamSport1.mpd",
            streamFormat = "dash",
            thumbnailUrl = "",
            encryptionType = "clearkey",
            categories = listOf("Sports"),
            isAzam = true,
            isLiveBroadcast = true
        )
        assertFalse(
            "Smart TV must unlock all channels with zero login and zero premium subscription",
            NeliAdminManager.isChannelLockedForUser(
                channelId = vipAzamChannel.id,
                channelObj = vipAzamChannel,
                currentUser = null,
                isPremiumActive = false,
                context = context
            )
        )

        // Clean up admin lock state
        NeliAdminManager.setLockAllChannels(context, false)
    }

    @Test
    fun `smart tv sanikisha update launches installer or unknown sources permission cleanly from cacheDir`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NeliThemeManager.setTvModeSetting(context, true)

        // Place a downloaded APK inside app cacheDir (Scoped Storage safe on Android 10-15 & TV)
        val cachedApk = File(context.cacheDir, "Nelitv-update.apk")
        cachedApk.writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
        try {
            NeliAppUpdateManager.installDownloadedApkNow(context)
            val nextIntent = Shadows.shadowOf(context as android.app.Application).nextStartedActivity
            assertNotNull("Pressing SANIKISHA must launch installer or unknown-sources permission activity", nextIntent)
            val validAction = nextIntent.action == Intent.ACTION_VIEW ||
                nextIntent.action == Intent.ACTION_INSTALL_PACKAGE ||
                nextIntent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
            assertTrue("Unexpected intent action on SANIKISHA: ${nextIntent.action}", validAction)
        } finally {
            cachedApk.delete()
        }
    }

    @Test
    fun `smart tv surface player view layout inflates cleanly for hardware zero copy video`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inflated = LayoutInflater.from(context).inflate(R.layout.neli_surface_player_view, null, false)
        assertTrue("TV layout must inflate to Media3 PlayerView", inflated is PlayerView)
        val playerView = inflated as PlayerView
        assertTrue("PlayerView must keep TV screen on during playback", playerView.keepScreenOn)
        assertFalse("Custom Compose controls replace default PlayerView controller", playerView.useController)
    }

    @Test
    fun `smart tv exoplayer initializes immediately with pooled okhttp datasource clearkey software decryption and channel switching`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NeliThemeManager.setTvModeSetting(context, true)

        // 1. Verify pooled OkHttp DataSource is returned for fast keep-alive segment reuse on TV
        val pooledFactory = NeliLocalStreamEngine.createPooledHttpDataSourceFactory(
            streamUrl = "https://1445733302.rsc.cdn77.org/live/eds/AzamSport1/DASH/AzamSport1.mpd",
            isLive = true
        )
        assertTrue(
            "Live TV must use NeliPooledOkHttpDataSource.Factory for warm TLS connection reuse",
            pooledFactory is NeliPooledOkHttpDataSource.Factory
        )

        // 2. Verify permanent token & dynamic CDN host URL builder (never hardcodes legacy azamtvltd host)
        assertTrue(NeliTvRepository.isTokenPermanent(neverExpires = true, expEpochSec = 2_524_608_000L))
        assertTrue(NeliTvRepository.isTokenPermanent(neverExpires = false, expEpochSec = 2_524_608_000L))
        val dynamicUrl = NeliTvRepository.buildDynamicAzamUrl(
            slug = "AzamSport1",
            cdnHost = "https://1445733302.rsc.cdn77.org",
            token = "permanent.jwt.token"
        )
        assertEquals("https://1445733302.rsc.cdn77.org/live/eds/AzamSport1/DASH/AzamSport1.mpd", dynamicUrl)

        // 3. Verify proxy URL headers include X-Neli-Client: nelitv-app
        val proxyUrl = NeliTvRepository.buildProxyStreamUrl(dynamicUrl)
        assertTrue(proxyUrl.startsWith("https://nelitv.online/api/proxy-stream?url="))
        val proxyHeaders = NeliTvRepository.buildStreamPlayerHeaders(proxyUrl)
        assertEquals("nelitv-app", proxyHeaders["X-Neli-Client"])

        // 4. Verify in-app software ClearKey MPD stripping (eliminates DrmHalHidl/CryptoHalHidl crashes on TV boxes)
        val sampleMpd = """
            <MPD>
              <Period>
                <AdaptationSet>
                  <ContentProtection schemeIdUri="urn:mpeg:dash:mp4protection:2011" value="cenc"/>
                  <ContentProtection schemeIdUri="urn:uuid:e2719d58-a985-b3c9-781a-b030af78d30e"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val decryptor = Mp4CencDecryptor(
            mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85")
        )
        val strippedMpd = decryptor.stripMpdContentProtection(sampleMpd)
        assertFalse("ContentProtection must be stripped for software CENC decryption", strippedMpd.contains("ContentProtection"))

        // 5. Verify LivePlayerController initializes ExoPlayer on TV with OPTIMAL_POWER and immediate playWhenReady
        val azamDashChannel = LiveChannel(
            id = "tv_azam_sports_1",
            slug = "AzamSport1",
            name = "Azam Sports 1 HD",
            description = "Live Sports",
            streamUrl = dynamicUrl,
            proxyStreamUrl = proxyUrl,
            streamFormat = "dash",
            thumbnailUrl = "",
            encryptionType = "clearkey",
            clearKeys = mapOf("c31df1600afc33799ecac543331803f2" to "dd2101530e222f545997d4c553787f85"),
            categories = listOf("Sports"),
            isAzam = true,
            isLiveBroadcast = true
        )
        val hlsChannel = LiveChannel(
            id = "tv_wasafi_hls",
            slug = "wasafi",
            name = "Wasafi TV",
            description = "Live Entertainment",
            streamUrl = "https://1445733302.rsc.cdn77.org/live/wasafi/index.m3u8",
            proxyStreamUrl = NeliTvRepository.buildProxyStreamUrl("https://1445733302.rsc.cdn77.org/live/wasafi/index.m3u8"),
            streamFormat = "hls",
            thumbnailUrl = "",
            encryptionType = "none",
            categories = listOf("Entertainment"),
            isLiveBroadcast = true
        )

        val controller = LivePlayerController(context, azamDashChannel)
        try {
            val player = controller.initializePlayer()
            assertNotNull("ExoPlayer must initialize on Smart TV", player)
            assertTrue("Smart TV player must start with playWhenReady=true immediately", player.playWhenReady)
            assertEquals("Smart TV player must not be muted", 1.0f, player.volume, 0.01f)
            assertEquals(
                "Smart TV must stay on OPTIMAL_POWER (60fps, never battery-throttled)",
                BatteryPowerProfile.OPTIMAL_POWER,
                controller.playbackInfo.value.batteryPowerProfile
            )
            assertTrue(
                "ExoPlayer must be preparing/buffering/ready (not stuck IDLE)",
                player.playbackState != Player.STATE_IDLE
            )

            // Switch seamlessly from DASH ClearKey channel to HLS channel via TV remote CH+/CH-
            controller.switchChannel(hlsChannel)
            assertEquals("tv_wasafi_hls", controller.currentChannel.value.id)
            assertTrue("Player must remain playWhenReady after channel switch", player.playWhenReady)
        } finally {
            controller.release()
        }
    }
}
