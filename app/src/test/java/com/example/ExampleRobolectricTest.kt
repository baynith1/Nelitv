package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.MediaContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Nelitv", appName)
        assertEquals("com.nelitv.app", BuildConfig.APPLICATION_ID)

        val dishChannel = com.example.data.ChannelRepository.getChannelById("DC7152673681")
        assertNotNull(dishChannel)
        assertEquals("Dish on TV", dishChannel!!.name)
        assertEquals("DC7", dishChannel.channelTag)
        assertEquals("FAM", dishChannel.rating)
        assertEquals(8, dishChannel.scheduleEvents.size)
    }

    @Test
    fun `firestore movie series episode and tvChannel JSON documents parse properly`() {
        val movieDocJson = """
            {
              "documents": [
                {
                  "name": "projects/neli-tv/databases/(default)/documents/movies/mov_1788911132603_wdgav",
                  "fields": {
                    "id": {"stringValue": "mov_1788911132603_wdgav"},
                    "title": {"stringValue": "Never a Thief"},
                    "originalTitle": {"stringValue": "缉盗"},
                    "originalLanguage": {"stringValue": "zh"},
                    "narrated": {"booleanValue": true},
                    "narrationLanguage": {"stringValue": "Swahili"},
                    "downloadEnabled": {"booleanValue": true},
                    "featured": {"booleanValue": true},
                    "published": {"booleanValue": true},
                    "rating": {"doubleValue": 5.3},
                    "runtime": {"integerValue": "120"},
                    "year": {"integerValue": "2025"},
                    "posterPath": {"stringValue": "https://image.tmdb.org/t/p/w500/ui5Ujx256vAI5JbXzeTGVwMkVhs.jpg"},
                    "backdropPath": {"stringValue": "https://image.tmdb.org/t/p/original/vuq5EfA9ED9vnxQgEV4zWEAFmKJ.jpg"},
                    "streamUrl": {"stringValue": "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4"},
                    "genres": {"arrayValue": {"values": [{"stringValue": "Crime"}]}}
                  }
                }
              ]
            }
        """.trimIndent()

        val count = MediaContentRepository.parseFirestoreCollections(
            moviesJson = movieDocJson
        )
        assertEquals(1, count)
        val movie = MediaContentRepository.getMediaById("mov_1788911132603_wdgav")
        assertNotNull(movie)
        assertEquals("Never a Thief", movie!!.title)
        assertTrue(movie.narrated)
        assertEquals("Swahili", movie.narrationLanguage)
        assertTrue(movie.shouldAutoSkipSwahiliMovieIntro)
        assertTrue(movie.toPlayableChannel().isSwahiliNarratedMovie)
    }

    @Test
    fun `swahili 5m30s auto skip applies only to swahili narrated movies and never to series episodes adult or live tv`() {
        val swahiliMovie = com.example.model.MediaContent(
            id = "mov_1",
            title = "Kiswahili Action Movie",
            type = "movie",
            posterUrl = "",
            backdropUrl = "",
            streamUrl = "https://example.com/movie.mp4",
            genre = "Action",
            duration = "2h 00m",
            rating = "8.5",
            synopsis = "Narrated by DJ",
            releaseYear = "2025",
            narrated = true,
            narrationLanguage = "Kiswahili"
        )
        assertTrue(swahiliMovie.shouldAutoSkipSwahiliMovieIntro)
        assertTrue(swahiliMovie.toPlayableChannel().isSwahiliNarratedMovie)

        val swahiliSeries = swahiliMovie.copy(id = "ser_1", type = "series")
        org.junit.Assert.assertFalse(swahiliSeries.shouldAutoSkipSwahiliMovieIntro)
        org.junit.Assert.assertFalse(swahiliSeries.toPlayableChannel().isSwahiliNarratedMovie)

        val adultMovie = swahiliMovie.copy(id = "adult_1", genre = "Adult 18+")
        org.junit.Assert.assertFalse(adultMovie.shouldAutoSkipSwahiliMovieIntro)
        org.junit.Assert.assertFalse(adultMovie.toPlayableChannel().isSwahiliNarratedMovie)

        val episode = com.example.model.EpisodeItem(
            id = "ep_1",
            seriesId = "ser_1",
            seasonNumber = 1,
            episodeNumber = 1,
            name = "Pilot",
            overview = "",
            stillPath = "",
            streamUrl = "https://example.com/ep1.m3u8",
            runtime = 45,
            narrated = true,
            narrationLanguage = "Swahili"
        )
        org.junit.Assert.assertFalse(episode.toPlayableChannel("Series").isSwahiliNarratedMovie)
    }

    @Test
    fun `user signup with real name email and password automatically logs in and persists across login`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.NeliDatabase::class.java
        ).allowMainThreadQueries().build()
        val dao = db.mediaDao()

        val signUpRes = com.example.data.AuthRepository.signUpWithEmailAndPassword(
            context = context,
            dao = dao,
            realName = "Juma Bakari",
            email = "juma@nelitv.tz",
            password = "password123"
        )
        assertTrue(signUpRes.isSuccess)
        val registeredUser = signUpRes.getOrNull()
        assertNotNull(registeredUser)
        assertEquals("Juma Bakari", registeredUser!!.realName)
        assertTrue(registeredUser.isLoggedIn)

        // Log out and sign back in: real name and account data must not be lost
        dao.logoutAllUsers()
        val signInRes = com.example.data.AuthRepository.signInWithEmailAndPassword(
            context = context,
            dao = dao,
            email = "juma@nelitv.tz",
            password = "password123"
        )
        assertTrue(signInRes.isSuccess)
        assertEquals("Juma Bakari", signInRes.getOrNull()?.realName)

        // Verify Google Sign-In flow and google-services.json key & SHA-1/SHA-256 resolution
        val resolvedFirebaseKey = com.example.data.AuthRepository.resolveApiKey(context)
        assertTrue("Expected non-blank Firebase API key from google-services.json or BuildConfig", resolvedFirebaseKey.isNotBlank())
        val resolvedWebClientId = com.example.data.UserManager.resolveWebClientId(context)
        assertEquals(
            "39702563643-ffg3f9g17s23vjngij5umvtvujrd7g3m.apps.googleusercontent.com",
            resolvedWebClientId
        )
        val resolvedGoogleAppId = com.example.data.UserManager.resolveGoogleAppId(context)
        assertEquals(
            "1:39702563643:android:c7917211faf4e36e83051e",
            resolvedGoogleAppId
        )
        assertEquals(
            "39702563643-dnbrhh1gi2gkfibfokvugp1icln9toei.apps.googleusercontent.com",
            com.example.data.UserManager.ANDROID_OAUTH_CLIENT_ID
        )
        assertEquals(
            "77:B9:99:A6:A4:75:FB:2C:77:AE:5D:55:75:5D:26:34:75:DF:E3:CE",
            com.example.data.UserManager.SHA1_CERTIFICATE_FINGERPRINT
        )
        assertEquals(
            "77b999a6a475fb2c77ae5d55755d263475dfe3ce",
            com.example.data.UserManager.SHA1_CERTIFICATE_HASH
        )
        assertEquals(
            "EE:19:DC:24:4F:42:EA:40:6F:AE:75:CE:B1:56:51:21:7F:B2:A6:38:6B:04:F1:E6:4D:F2:C3:B3:38:E3:9A:C3",
            com.example.data.UserManager.SHA256_CERTIFICATE_FINGERPRINT
        )
        val assetLinksJson = context.assets.open("assetlinks.json").bufferedReader().use { it.readText() }
        assertTrue(assetLinksJson.contains("com.nelitv.app"))
        assertTrue(
            assetLinksJson.contains(
                "EE:19:DC:24:4F:42:EA:40:6F:AE:75:CE:B1:56:51:21:7F:B2:A6:38:6B:04:F1:E6:4D:F2:C3:B3:38:E3:9A:C3"
            )
        )

        dao.logoutAllUsers()
        val googleSignInRes = com.example.data.AuthRepository.signInWithGoogleAccount(
            context = context,
            dao = dao,
            email = "aibaynith@gmail.com",
            displayName = "Alex Michael Baineth"
        )
        assertTrue(googleSignInRes.isSuccess)
        val googleUser = googleSignInRes.getOrNull()
        assertNotNull(googleUser)
        assertEquals("aibaynith@gmail.com", googleUser!!.email)
        assertEquals("Alex Michael Baineth", googleUser.realName)
        assertTrue(googleUser.isLoggedIn)

        // Verify AuthenticationRepository encapsulates Google Sign-In, Email/Password Registration, and Session Management
        val authRepo = com.example.data.AuthenticationRepository(context, dao)
        assertTrue(authRepo.isUserSignedIn())
        assertEquals("aibaynith@gmail.com", authRepo.getCurrentUser()?.email)

        // Sign out and verify session is cleared while saved accounts remain available
        authRepo.signOut()
        org.junit.Assert.assertFalse(authRepo.isUserSignedIn())
        assertTrue(authRepo.getSavedAccounts().size >= 2)

        // Register a new account via AuthenticationRepository
        val repoRegRes = authRepo.registerWithEmailAndPassword(
            realName = "Amina Hassan",
            email = "amina@nelitv.tz",
            password = "securePassword99"
        )
        assertTrue(repoRegRes.isSuccess)
        assertTrue(authRepo.isUserSignedIn())
        assertEquals("amina@nelitv.tz", authRepo.getCurrentUser()?.email)
        assertEquals("Amina Hassan", authRepo.getCurrentUser()?.realName)

        // Switch active session back to Google account and verify session management
        val switchRes = authRepo.switchActiveSession("aibaynith@gmail.com")
        assertTrue(switchRes.isSuccess)
        assertEquals("aibaynith@gmail.com", authRepo.getCurrentUser()?.email)

        // Sign in with Google ID token via AuthenticationRepository
        val googleTokenRes = authRepo.signInWithGoogleIdToken(
            idToken = "mock.eyJlbWFpbCI6ImFpYmF5bml0aEBnbWFpbC5jb20iLCJuYW1lIjoiQWxleCBNaWNoYWVsIEJhaW5ldGgifQ.sig"
        )
        assertTrue(googleTokenRes.isSuccess)
        assertEquals("aibaynith@gmail.com", googleTokenRes.getOrNull()?.email)
        assertEquals("Alex Michael Baineth", googleTokenRes.getOrNull()?.realName)

        db.close()
    }

    @Test
    fun `azam tv channels use mpd streams with cdn authorization token and m3u8 streams remain m3u8`() {
        val allChannels = com.example.data.ChannelRepository.channels
        val azamChannels = com.example.data.ChannelRepository.azamPriorityChannels

        // Ensure extra Azam TV channels were added (17 Azam TV priority channels total)
        assertTrue(azamChannels.size >= 16)
        assertTrue(azamChannels.any { it.name.equals("Azam Xtra HD", ignoreCase = true) })
        assertTrue(azamChannels.any { it.name.equals("Azam Movies HD", ignoreCase = true) })
        assertTrue(azamChannels.any { it.name.equals("Clouds TV HD", ignoreCase = true) })
        assertTrue(azamChannels.any { it.name.equals("ITV Tanzania HD", ignoreCase = true) })

        // Ensure Azam Sports 1-4, Azam One, Azam Two, Sinema Zetu use .mpd links with cdntoken and clearkey
        val azamSports1 = allChannels.first { it.name.contains("Azam Sports 1", ignoreCase = true) }
        assertTrue(azamSports1.isDash)
        assertTrue(azamSports1.isClearKey)
        assertTrue(azamSports1.streamUrl.contains(".mpd?cdntoken="))
        assertTrue(azamSports1.streamUrl.startsWith(com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_HOST))

        // Ensure CDN token JSON payload updates token & host properly without expiredate enforcement
        val tokenJson = """
            {
              "token": "${com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}",
              "cdnHost": "https://cdnedgch2.azamtvltd.co.tz",
              "source": "cache"
            }
        """.trimIndent()
        assertTrue(com.example.data.ChannelRepository.updateCdnAuthorizationToken(tokenJson))
        assertEquals(null, com.example.data.ChannelRepository.AZAM_CDN_EXP)

        // Ensure .m3u8 channels remain .m3u8 untouched
        val hlsChannels = allChannels.filter { it.streamUrl.contains(".m3u8", ignoreCase = true) }
        assertTrue(hlsChannels.isNotEmpty())
        hlsChannels.forEach { ch ->
            assertEquals(
                ch.streamUrl,
                com.example.data.ChannelRepository.normalizeDashStreamUrl(ch.streamUrl)
            )
        }
    }

    @Test
    fun `daily east africa time notifications and home widget 2026 non-adult movies and azam channels are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify all 4 daily EAT notification slots (7:00, 13:00, 16:00, 19:30 Africa/Dar_es_Salaam)
        val slots = com.example.notifications.NeliNotificationScheduler.dailySlots
        assertEquals(4, slots.size)
        assertEquals(7, slots[0].hour24Eat)
        assertEquals(0, slots[0].minuteEat)
        assertEquals(13, slots[1].hour24Eat)
        assertEquals(0, slots[1].minuteEat)
        assertEquals(16, slots[2].hour24Eat)
        assertEquals(0, slots[2].minuteEat)
        assertEquals(19, slots[3].hour24Eat)
        assertEquals(30, slots[3].minuteEat)

        com.example.notifications.NeliNotificationScheduler.scheduleAllDailyNotifications(context)

        // 2. Verify top 3 2026 non-adult movies for Home Widget bottom row
        val movies2026 = MediaContentRepository.getLatest2026NonAdultMovies(3)
        assertEquals(3, movies2026.size)
        movies2026.forEach { movie ->
            assertEquals("2026", movie.releaseYear)
            org.junit.Assert.assertFalse(movie.isAdultContent)
            org.junit.Assert.assertFalse(movie.genre.contains("Adult", ignoreCase = true))
        }

        // 3. Verify Low Bando Saver mode exists in NetworkQualityMode
        val lowBandoMode = com.example.player.NetworkQualityMode.ULTRA_LOW_BANDO_SAVER
        assertTrue(lowBandoMode.label.contains("Low Bando", ignoreCase = true))

        // 4. Verify widget update executes cleanly without throwing
        com.example.widget.NeliHomeWidgetProvider.updateAllWidgets(context)
    }

    @Test
    fun `firestore config azam_token with null exp or timestampValue automatically extracts jwt exp and updates streams`() {
        // Exact structure from user's Firestore screenshot:
        // collection: config, document: azam_token, fields: cdnHost, exp: null, source: "cache", token: "eyJ..."
        val singleDocNullExpJson = """
            {
              "name": "projects/neli-tv/databases/(default)/documents/config/azam_token",
              "fields": {
                "cdnHost": { "stringValue": "https://cdnedgch2.azamtvltd.co.tz" },
                "exp": { "nullValue": null },
                "source": { "stringValue": "cache" },
                "token": { "stringValue": "${com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}" }
              }
            }
        """.trimIndent()

        val updatedWithNullExp = MediaContentRepository.parseFirestoreCdnTokenDocs(singleDocNullExpJson)
        assertTrue(updatedWithNullExp)
        // Expiredate is removed because Azam TV tokens are free and non-expiring
        assertEquals(null, com.example.data.ChannelRepository.AZAM_CDN_EXP)
        assertEquals("https://cdnedgch2.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST)

        // Also verify Firestore Timestamp / Date picker format ("timestampValue": "2026-09-26T08:49:42Z") is ignored cleanly
        val timestampDocJson = """
            {
              "documents": [
                {
                  "name": "projects/neli-tv/databases/(default)/documents/config/azam_token",
                  "fields": {
                    "cdnHost": { "stringValue": "https://cdnedgch2.azamtvltd.co.tz" },
                    "exp": { "timestampValue": "2026-09-26T08:49:42Z" },
                    "source": { "stringValue": "cache" },
                    "token": { "stringValue": "${com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}" }
                  }
                }
              ]
            }
        """.trimIndent()
        assertTrue(MediaContentRepository.parseFirestoreCdnTokenDocs(timestampDocJson))
        assertEquals(null, com.example.data.ChannelRepository.AZAM_CDN_EXP)
    }

    @Test
    fun `six live tv channel logos and zbc2 16_00 eat international match notifications are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val logos = com.example.notifications.NeliNotificationScheduler.liveChannelLogoSpecs
        assertEquals(6, logos.size)

        // 1st: Azam Sports 1 HD, 2nd: Sinema Zetu, 3rd: Azam Two, 4th: Crown TV, 5th: Wasafi TV, 6th: ZBC2
        assertEquals(1, logos[0].imageOrder)
        assertTrue(logos[0].channelName.contains("Azam Sports 1", ignoreCase = true))
        assertEquals(2, logos[1].imageOrder)
        assertTrue(logos[1].channelName.contains("Sinema Zetu", ignoreCase = true))
        assertEquals(3, logos[2].imageOrder)
        assertTrue(logos[2].channelName.contains("Azam Two", ignoreCase = true))
        assertEquals(4, logos[3].imageOrder)
        assertTrue(logos[3].channelName.contains("Crown", ignoreCase = true))
        assertEquals(5, logos[4].imageOrder)
        assertTrue(logos[4].channelName.contains("Wasafi", ignoreCase = true))
        assertEquals(6, logos[5].imageOrder)
        assertTrue(logos[5].channelName.contains("ZBC2", ignoreCase = true))
        logos.forEach { entry ->
            assertTrue(entry.logoUrl.startsWith("https://"))
        }

        // Trigger all 4 notification slots (including 16:00 EAT which posts both Azam Sports 1 HD and ZBC2 International Match)
        com.example.notifications.NeliNotificationScheduler.dispatchNotificationForSlot(
            context,
            com.example.notifications.NeliNotificationScheduler.SLOT_MORNING_MOVIE_SERIES
        )
        com.example.notifications.NeliNotificationScheduler.dispatchNotificationForSlot(
            context,
            com.example.notifications.NeliNotificationScheduler.SLOT_MIDDAY_AZAM_LIVE
        )
        com.example.notifications.NeliNotificationScheduler.dispatchNotificationForSlot(
            context,
            com.example.notifications.NeliNotificationScheduler.SLOT_AFTERNOON_AZAM_LIVE
        )
        com.example.notifications.NeliNotificationScheduler.dispatchNotificationForSlot(
            context,
            com.example.notifications.NeliNotificationScheduler.SLOT_EVENING_AZAM_TWO_SINEMA
        )
    }

    @Test
    fun `customer care details qr apk share github auto updates automatic widget and tokenEndpointUrl work properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify Customer Care constants in Account & 4 AccountInfoPageType entries (Cast removed)
        assertEquals("Neliplay Customercare", com.example.ui.screens.CUSTOMER_CARE_BRAND_NAME)
        assertEquals("Alex Michael Baineth", com.example.ui.screens.CUSTOMER_CARE_AGENT_NAME)
        assertEquals("+255760816851", com.example.ui.screens.CUSTOMER_CARE_PHONE)
        assertEquals(4, com.example.ui.screens.AccountInfoPageType.entries.size)

        // 2. Verify Share APK by Scan QR Code & Link + GitHub Release Auto-Update Manager
        assertEquals(
            "https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk",
            com.example.data.NeliAppUpdateManager.DEFAULT_APK_DOWNLOAD_URL
        )
        assertEquals(
            "https://github.com/baynith1/Nelitv/releases/tag/v1.0.0",
            com.example.data.NeliAppUpdateManager.DEFAULT_GITHUB_RELEASE_TAG_URL
        )
        val qrMatrix = com.example.ui.components.NeliQrCodeGenerator.encodeUrlToMatrix(
            com.example.data.NeliAppUpdateManager.DEFAULT_APK_DOWNLOAD_URL
        )
        assertEquals(33, qrMatrix.size) // Version 4-L (33x33) for 70-byte APK URL
        assertTrue(qrMatrix[0][0]) // Top-left finder pattern corner is dark

        // Verify GitHub Release JSON parsing & version comparison
        org.junit.Assert.assertFalse(
            com.example.data.NeliAppUpdateManager.isRemoteVersionNewer("v1.0.0", "v1.0.0")
        )
        assertTrue(
            com.example.data.NeliAppUpdateManager.isRemoteVersionNewer("v1.0.0", "v1.0.1")
        )
        val sampleReleaseJson = """
            {
              "tag_name": "v1.0.0",
              "name": "Neli TV v1.0.0 Official Release",
              "html_url": "https://github.com/baynith1/Nelitv/releases/tag/v1.0.0",
              "body": "Official Neli TV v1.0.0 release with Azam TV Live & Swahili Cinema",
              "published_at": "2026-09-26T00:00:00Z",
              "assets": [
                {
                  "name": "Nelitv.apk",
                  "browser_download_url": "https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk"
                }
              ]
            }
        """.trimIndent()
        val parsedRelease = com.example.data.NeliAppUpdateManager.parseGitHubReleaseJson(sampleReleaseJson)
        assertNotNull(parsedRelease)
        assertEquals("v1.0.0", parsedRelease!!.versionTag)
        assertEquals(
            "https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk",
            parsedRelease.apkDownloadUrl
        )
        org.junit.Assert.assertFalse(com.example.data.NeliAppUpdateManager.showHomepageUpdatePopup.value)

        // Verify Homepage Auto-Popup triggers automatically when a new update arrives on GitHub
        val newerReleaseJson = """
            {
              "tag_name": "v1.0.1",
              "name": "Neli TV v1.0.1 Official Release",
              "html_url": "https://github.com/baynith1/Nelitv/releases/tag/v1.0.1",
              "body": "New update with auto popup on homepage",
              "published_at": "2026-10-01T00:00:00Z",
              "assets": [
                {
                  "name": "Nelitv.apk",
                  "browser_download_url": "https://github.com/baynith1/Nelitv/releases/download/v1.0.1/Nelitv.apk"
                }
              ]
            }
        """.trimIndent()
        val parsedNewer = com.example.data.NeliAppUpdateManager.parseGitHubReleaseJson(
            rawJson = newerReleaseJson,
            currentVersionTag = "v1.0.0"
        )
        assertNotNull(parsedNewer)
        assertTrue(parsedNewer!!.isNewUpdateAvailable)
        assertTrue(com.example.data.NeliAppUpdateManager.showHomepageUpdatePopup.value)
        com.example.data.NeliAppUpdateManager.dismissHomepageUpdatePopup("v1.0.1")
        org.junit.Assert.assertFalse(com.example.data.NeliAppUpdateManager.showHomepageUpdatePopup.value)

        // Verify Package Conflict inspection & conflict-free in-app update resolution
        val conflictCheck = com.example.data.NeliAppUpdateManager.inspectApkPackageConflict(
            context = context,
            apkFile = null,
            targetVersionTag = "v1.0.0"
        )
        assertTrue(conflictCheck.wouldCausePackageConflict)
        assertTrue(com.example.data.NeliAppUpdateManager.resolvePackageConflictAndUpdate(context))
        org.junit.Assert.assertFalse(com.example.data.NeliAppUpdateManager.releaseInfo.value.isNewUpdateAvailable)
        assertTrue(
            com.example.data.NeliAppUpdateManager.apkDownloadStatusMessage.value
                .orEmpty()
                .contains("bila Package Conflict", ignoreCase = true)
        )
        com.example.data.NeliAppUpdateManager.parseGitHubReleaseJson(sampleReleaseJson, currentVersionTag = "v1.0.0")

        // Verify Account Widget Setup sets and displays widget on Home Screen
        assertTrue(
            com.example.widget.NeliHomeWidgetProvider.setAndShowWidgetOnHomeScreen(
                context = context,
                navigateToHomeScreen = false
            )
        )
        assertTrue(com.example.widget.NeliHomeWidgetProvider.isWidgetPinnedFlow.value)
        assertNotNull(com.example.widget.NeliHomeWidgetProvider.widgetSetupStatusMessage.value)

        // 3. Verify tokenEndpointUrl JSON payload updates ChannelRepository and TokenManager
        val combinedTokenJson = """
            {
              "token": "${com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}",
              "exp": 1790412582,
              "cdnHost": "https://cdnedgch2.azamtvltd.co.tz",
              "source": "cache",
              "tokenEndpointUrl": "https://streamzone.fun/api/cdn-token"
            }
        """.trimIndent()
        assertTrue(com.example.player.TokenManager.parseAndApplyTokenPayload(combinedTokenJson))
        assertEquals(
            "https://streamzone.fun/api/cdn-token",
            com.example.data.ChannelRepository.AZAM_TOKEN_ENDPOINT_URL
        )
        val injectedHeaders = com.example.player.TokenManager.buildExoPlayerHeaders(
            "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd"
        )
        assertEquals(
            "Bearer ${com.example.player.TokenManager.currentToken}",
            injectedHeaders["Authorization"]
        )
        assertEquals(
            com.example.player.TokenManager.currentToken,
            injectedHeaders["X-CDN-Token"]
        )
        assertTrue(com.example.player.TokenManager.isAuthenticationFailure(401))
        assertTrue(com.example.player.TokenManager.isAuthenticationFailure(403))

        // 4. Verify automatic home screen widget pin & update executes cleanly without widget notifications
        com.example.widget.NeliHomeWidgetProvider.ensureWidgetAutomaticallyPinnedAndUpdated(context)
        val widgetThumb = com.example.widget.NeliHomeWidgetProvider.renderFallbackWidgetThumb(
            title = "Azam Sports 1 HD",
            badge = "LIVE TV",
            targetW = 128,
            targetH = 84,
            primaryColor = 0xFF17103A.toInt(),
            accentColor = 0xFFF41B54.toInt()
        )
        assertEquals(128, widgetThumb.width)
        assertEquals(84, widgetThumb.height)

        // 5. Verify Theme Colour Changer (Black Mode <-> White Mode)
        com.example.ui.theme.NeliThemeManager.setThemeMode(context, true)
        assertTrue(com.example.ui.theme.NeliThemeManager.isLightMode)
        assertEquals("White (Light Mode)", com.example.ui.theme.NeliThemeManager.currentThemeLabel)
        com.example.ui.theme.NeliThemeManager.setThemeMode(context, false)
        org.junit.Assert.assertFalse(com.example.ui.theme.NeliThemeManager.isLightMode)
        assertEquals("Black (Dark Mode)", com.example.ui.theme.NeliThemeManager.currentThemeLabel)

        // 6. Verify TMDB Credits JSON parsing for real casters & profile URLs
        val sampleTmdbCredits = """
            {
              "cast": [
                {
                  "id": 73249,
                  "name": "Lee Jung-jae",
                  "character": "Seong Gi-hun / Player 456",
                  "profile_path": "/bA5M0e04g7h155X4r8r9.jpg"
                },
                {
                  "id": 25002,
                  "name": "Lee Byung-hun",
                  "character": "Front Man",
                  "profile_path": "/p8z8.jpg"
                }
              ]
            }
        """.trimIndent()
        val parsedCast = com.example.data.TmdbRepository.parseTmdbCreditsJson(sampleTmdbCredits)
        assertEquals(2, parsedCast.size)
        assertEquals("Lee Jung-jae", parsedCast[0].name)
        assertEquals("Seong Gi-hun / Player 456", parsedCast[0].role)
        assertTrue(parsedCast[0].avatarUrl.startsWith("https://image.tmdb.org/t/p/w185/"))

        // 7. Verify 10 Million Concurrent Users Scale Engine & Jitter
        assertEquals(10_000_000L, MediaContentRepository.MAX_CONCURRENT_USERS_CAPACITY)
        val jitteredDelay = MediaContentRepository.computeJitterDelayMsFor10MScale(180_000L)
        assertTrue(jitteredDelay in 180_000L..225_000L)
    }

    @Test
    fun `bottom menu 5 tabs homepage all channels priority discovery first genre deduplication and adult images work properly`() {
        // 1. Verify BottomNavTab has exactly Home, Discovery, Premium, Downloads, Account
        val tabs = com.example.ui.components.BottomNavTab.entries.map { it.label }
        assertEquals(listOf("Home", "Discovery", "Premium", "Downloads", "Account"), tabs)

        // 2. Verify Homepage includes all channels prioritized by Azam TV -> Tanzania -> Other
        val prioritized = com.example.data.ChannelRepository.getPrioritizedAllChannels()
        assertTrue(prioritized.size >= 36)
        assertTrue(prioritized.first().isAzamPriority)
        val firstNonTanzaniaIdx = prioritized.indexOfFirst { !it.isTanzaniaChannel }
        val lastAzamIdx = prioritized.indexOfLast { it.isAzamPriority }
        assertTrue(lastAzamIdx < firstNonTanzaniaIdx)

        // 3. Verify Discovery groups movies strictly by their 1st genre only (zero duplicate movies)
        val groupedMovies = MediaContentRepository.getMoviesStrictlyByFirstGenre()
        assertTrue(groupedMovies.isNotEmpty())
        val allAssignedIds = groupedMovies.flatMap { (_, movies) -> movies.map { it.id } }
        assertEquals(allAssignedIds.distinct().size, allAssignedIds.size)

        // 4. Verify Adult content always has non-blank poster and thumbnail URLs
        val adults = MediaContentRepository.getAdultContentCatalog()
        assertTrue(adults.isNotEmpty())
        adults.forEach { adult ->
            assertTrue(adult.posterUrl.startsWith("https://"))
            assertTrue(adult.backdropUrl.startsWith("https://"))
        }

        // 5. Verify expired CDN tokens are rejected so stale Firebase tokens never break Azam TV
        val expiredTokenJson = """
            {
              "token": "eyJhbGciOiJIUzUxMiJ9.eyJleHAiOiIxNjAwMDAwMDAwIn0.sig",
              "exp": 1600000000,
              "cdnHost": "https://cdnedgch2.azamtvltd.co.tz"
            }
        """.trimIndent()
        org.junit.Assert.assertFalse(
            com.example.data.ChannelRepository.updateCdnAuthorizationToken(expiredTokenJson)
        )

        // 6. Verify Pull-to-Refresh refreshes the Live TV channel list in-place while preserving priority
        val refreshedList = com.example.data.ChannelRepository.refreshLiveChannels()
        assertTrue(refreshedList.size >= 36)
        assertTrue(refreshedList.first().isAzamPriority)
        assertTrue(com.example.data.ChannelRepository.lastRefreshedEpochMs > 0L)

        // 6b. Verify https://streamzone.fun/api/channels backup API parses channels, attaches backupStreamUrl to existing channels, and adds extra channels
        assertEquals(
            "https://streamzone.fun/api/channels",
            com.example.data.ChannelRepository.DEFAULT_CHANNELS_BACKUP_API_URL
        )
        val sampleBackupApiPayload = """
            {
              "channels": [
                {
                  "id": "R17JUvbCEzu2eTbjnE74",
                  "name": "Azam Sports 1 HD",
                  "streamUrl": "https://cdnedgch2.azamtvltd.co.tz/live/eds/AzamSport1/DASH/AzamSport1.mpd",
                  "clearKey": "c31df1600afc33799ecac543331803f2:dd2101530e222f545997d4c553787f85",
                  "category": "Sports"
                },
                {
                  "id": "streamzone_extra_1",
                  "name": "StreamZone Extra Sports",
                  "streamUrl": "https://cdnedgch2.azamtvltd.co.tz/live/eds/StreamZoneSportsExtra/DASH/StreamZoneSportsExtra.mpd",
                  "category": "Sports"
                }
              ]
            }
        """.trimIndent()
        val parsedBackup = com.example.data.ChannelRepository.parseBackupChannelsApiPayload(sampleBackupApiPayload)
        assertEquals(2, parsedBackup.size)
        val mergedAzamSport1 = com.example.data.ChannelRepository.liveChannelsFlow.value.first {
            it.name.contains("Azam Sports 1", ignoreCase = true)
        }
        assertTrue(mergedAzamSport1.backupStreamUrl.contains("cdntoken="))
        assertNotNull(com.example.data.ChannelRepository.findBackupChannelFor(mergedAzamSport1))
        assertTrue(
            com.example.data.ChannelRepository.liveChannelsFlow.value.any {
                it.name == "StreamZone Extra Sports"
            }
        )

        // 7. Verify Discovery banner text ("Furahia Movie Nzuri kutoka kwa Madjs Wazuri")
        assertEquals(
            "Furahia Movie Nzuri kutoka kwa Madjs Wazuri",
            com.example.ui.screens.DISCOVERY_MADJS_BANNER_TEXT
        )

        // 8. Verify Offline Mode sends user directly to Download page & Movie/Series Watchpage Back -> Movie Details -> Discovery
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val vm = com.example.ui.NeliViewModel(app)

        // Offline mode directs immediately to Download tab
        vm.updateOfflineState(true)
        assertTrue(vm.isOfflineMode.value)
        assertEquals(com.example.ui.components.BottomNavTab.DOWNLOAD, vm.selectedTab.value)

        // Back online
        vm.updateOfflineState(false)
        vm.selectTab(com.example.ui.components.BottomNavTab.HOME)
        assertEquals(com.example.ui.components.BottomNavTab.HOME, vm.selectedTab.value)

        // User watches a Movie on Watchpage and presses Back -> must go to Movie Details page of that movie (never Homepage), then Back -> Discovery
        val sampleMovie = MediaContentRepository.mediaCatalog.value.first { it.isMovie && !it.isAdultContent }
        vm.onReturnFromWatchPage(sampleMovie.toPlayableChannel())
        assertEquals( sampleMovie.id, vm.selectedMediaId.value)
        assertEquals(com.example.ui.components.BottomNavTab.DISCOVERY, vm.selectedTab.value)

        // Pressing Back from Movie Details page sends user to Discovery
        vm.navigateBackFromMediaDetails()
        assertEquals(null, vm.selectedMediaId.value)
        assertEquals(com.example.ui.components.BottomNavTab.DISCOVERY, vm.selectedTab.value)

        // User watches a Series Episode on Watchpage and presses Back -> must go to Series Details page of that series, then Back -> Discovery
        val sampleEpisode = MediaContentRepository.episodesCatalog.value.first()
        vm.selectTab(com.example.ui.components.BottomNavTab.HOME)
        vm.onReturnFromWatchPage(sampleEpisode.toPlayableChannel("Series"))
        assertEquals(sampleEpisode.seriesId, vm.selectedMediaId.value)
        assertEquals(com.example.ui.components.BottomNavTab.DISCOVERY, vm.selectedTab.value)

        vm.navigateBackFromMediaDetails()
        assertEquals(null, vm.selectedMediaId.value)
        assertEquals(com.example.ui.components.BottomNavTab.DISCOVERY, vm.selectedTab.value)

        // 9. Verify Adult aliases ("X", "xxx", "X video", "porn") & Firebase Adult Genre/Category grouping and search
        assertTrue(MediaContentRepository.isAdultKeywordOrQuery("X"))
        assertTrue(MediaContentRepository.isAdultKeywordOrQuery("xxx"))
        assertTrue(MediaContentRepository.isAdultKeywordOrQuery("X video"))
        assertTrue(MediaContentRepository.isAdultKeywordOrQuery("porn"))
        assertTrue(MediaContentRepository.isAdultKeywordOrQuery("18+"))
        org.junit.Assert.assertFalse(MediaContentRepository.isAdultKeywordOrQuery("Box Office Action"))

        val adultFirestoreJson = """
            {
              "documents": [
                {
                  "name": "projects/neliplay/databases/(default)/documents/adults/adult_fb_1",
                  "fields": {
                    "id": {"stringValue": "adult_fb_1"},
                    "title": {"stringValue": "Late Night X Video Special"},
                    "streamUrl": {"stringValue": "https://vz-1bb50f2e-8ea.b-cdn.net/9d14eb59-d3a0-4b01-9010-ba9bc5492865/play_480p.mp4"},
                    "genre": {"stringValue": "X Video"},
                    "category": {"stringValue": "Porn"},
                    "genres": {"arrayValue": {"values": [{"stringValue": "X Video"}, {"stringValue": "XXX"}, {"stringValue": "Porn"}]}},
                    "published": {"booleanValue": true}
                  }
                }
              ]
            }
        """.trimIndent()
        MediaContentRepository.parseFirestoreCollections(adultsJson = adultFirestoreJson)
        val parsedAdult = MediaContentRepository.getMediaById("adult_fb_1")
        assertNotNull(parsedAdult)
        assertTrue(parsedAdult!!.isAdultContent)
        assertEquals("X Video", parsedAdult.primaryGenre)

        val groupedAdults = MediaContentRepository.getAdultsGroupedByGenreAndCategory()
        assertTrue(groupedAdults.any { it.first.equals("X Video", ignoreCase = true) })
        assertTrue(groupedAdults.any { it.first.equals("XXX", ignoreCase = true) })
        assertTrue(groupedAdults.any { it.first.equals("Porn", ignoreCase = true) })

        // Verify searching by "X", "xxx", "X video", "porn" matches adult items
        listOf("X", "xxx", "X video", "porn").forEach { query ->
            val results = MediaContentRepository.mediaCatalog.value.filter {
                MediaContentRepository.matchesMediaSearch(it, query, "All")
            }
            assertTrue("Expected adult search results for '$query'", results.isNotEmpty())
            assertTrue(results.all { it.isAdultContent })
        }

        // 10. Verify 5-tab Bottom Menu (Home, Discovery, Premium, Downloads, Account) and complete removal of ads
        val neliApp = app as? com.example.NeliApplication
        assertNotNull("Expected Application context to be NeliApplication", neliApp)

        val bottomTabs = com.example.ui.components.BottomNavTab.entries
        assertEquals(
            listOf(
                com.example.ui.components.BottomNavTab.HOME,
                com.example.ui.components.BottomNavTab.DISCOVERY,
                com.example.ui.components.BottomNavTab.PREMIUM,
                com.example.ui.components.BottomNavTab.DOWNLOAD,
                com.example.ui.components.BottomNavTab.ACCOUNT
            ),
            bottomTabs
        )
        assertEquals("Home", com.example.ui.components.BottomNavTab.HOME.label)
        assertEquals("Discovery", com.example.ui.components.BottomNavTab.DISCOVERY.label)
        assertEquals("Premium", com.example.ui.components.BottomNavTab.PREMIUM.label)
        assertEquals("Downloads", com.example.ui.components.BottomNavTab.DOWNLOAD.label)
        assertEquals("Account", com.example.ui.components.BottomNavTab.ACCOUNT.label)

        // Verify both old AdMob manager and Start.io ad manager are completely removed
        val adMobClassPresent = try {
            Class.forName("com.example.ads.NeliAdMobManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        org.junit.Assert.assertFalse("Expected old Google AdMob manager class to be removed", adMobClassPresent)

        val startIoClassPresent = try {
            Class.forName("com.example.ads.NeliStartIoAdManager")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        org.junit.Assert.assertFalse("Expected Start.io ad manager class to be removed", startIoClassPresent)

        // 11. Verify Homepage channel categories order (Azam TV -> Sports -> Entertainment -> Kids -> News -> Movies -> ...),
        // 6-channel vertical chunking, and guaranteed channel logos
        val homepageCategories = com.example.data.ChannelRepository.getChannelsGroupedByHomepageCategories()
        assertTrue(homepageCategories.size >= 6)
        val firstSixTitles = homepageCategories.take(6).map { it.first }
        assertEquals(
            listOf("Azam TV", "Sports", "Entertainment", "Kids", "News", "Movies"),
            firstSixTitles
        )
        // First category ("Azam TV") must contain all Azam TV priority channels
        assertTrue(homepageCategories.first().second.size >= 17)
        assertTrue(homepageCategories.first().second.all { it.isAzamPriority })

        // Verify All Channels vertical chunking groups channels into blocks of 6
        val sixChannelBlocks = com.example.data.ChannelRepository.getAllChannelsChunkedEverySixForAds()
        assertTrue(sixChannelBlocks.isNotEmpty())
        assertEquals(6, sixChannelBlocks.first().size)

        // Verify every channel in All Channels has a valid https:// logo URL and fallback logo URL
        com.example.data.ChannelRepository.getPrioritizedAllChannels().forEach { ch ->
            assertTrue("Channel ${ch.name} must have a valid logo URL", ch.thumbnailUrl.startsWith("https://"))
            val fallbackLogo = com.example.data.ChannelRepository.resolveFallbackChannelLogoUrl(ch)
            assertTrue("Channel ${ch.name} must have a valid fallback logo URL", fallbackLogo.startsWith("https://"))
        }

        // Verify Firestore tvChannels custom logo fields (e.g. channelLogo / iconUrl) are extracted and visible in All Channels
        val customChannelFirestoreJson = """
            {
              "documents": [
                {
                  "name": "projects/neli-tv/databases/(default)/documents/tvChannels/custom_tz_ch_1",
                  "fields": {
                    "id": {"stringValue": "custom_tz_ch_1"},
                    "name": {"stringValue": "Bongo Star TV"},
                    "streamUrl": {"stringValue": "https://example.com/live/bongostar.m3u8"},
                    "category": {"stringValue": "Entertainment"},
                    "country": {"stringValue": "Tanzania"},
                    "channelLogo": {"stringValue": "https://i.ibb.co/8gtr1n42/1000221072.jpg"},
                    "enabled": {"booleanValue": true},
                    "published": {"booleanValue": true}
                  }
                }
              ]
            }
        """.trimIndent()
        MediaContentRepository.parseFirestoreCollections(tvChannelsJson = customChannelFirestoreJson)
        val syncedChannel = com.example.data.ChannelRepository.getChannelById("custom_tz_ch_1")
        assertNotNull(syncedChannel)
        assertEquals("https://i.ibb.co/8gtr1n42/1000221072.jpg", syncedChannel!!.thumbnailUrl)
    }

    @Test
    fun `player double tap 10s seek zones cumulative seek and brightness volume vertical gestures work properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify Double-Tap Seek Zone detection (Left < 38% = Rewind 10s, Right > 62% = Forward 10s, Center = Toggle)
        assertEquals(
            com.example.ui.components.DoubleTapZone.LEFT_REWIND,
            com.example.ui.components.PlayerGestureHelper.resolveDoubleTapZone(150f, 1000f)
        )
        assertEquals(
            com.example.ui.components.DoubleTapZone.CENTER_TOGGLE,
            com.example.ui.components.PlayerGestureHelper.resolveDoubleTapZone(500f, 1000f)
        )
        assertEquals(
            com.example.ui.components.DoubleTapZone.RIGHT_FORWARD,
            com.example.ui.components.PlayerGestureHelper.resolveDoubleTapZone(820f, 1000f)
        )

        // 2. Verify Cumulative Multi-Tap Seek (-10s -> -20s -> -30s and +10s -> +20s -> +30s)
        val firstRewind = com.example.ui.components.PlayerGestureHelper.computeCumulativeSeekSeconds(
            existingZone = null,
            existingSeconds = 0,
            newZone = com.example.ui.components.DoubleTapZone.LEFT_REWIND
        )
        assertEquals(-10, firstRewind)

        val secondRewind = com.example.ui.components.PlayerGestureHelper.computeCumulativeSeekSeconds(
            existingZone = com.example.ui.components.DoubleTapZone.LEFT_REWIND,
            existingSeconds = firstRewind,
            newZone = com.example.ui.components.DoubleTapZone.LEFT_REWIND
        )
        assertEquals(-20, secondRewind)

        val firstForward = com.example.ui.components.PlayerGestureHelper.computeCumulativeSeekSeconds(
            existingZone = com.example.ui.components.DoubleTapZone.LEFT_REWIND,
            existingSeconds = secondRewind,
            newZone = com.example.ui.components.DoubleTapZone.RIGHT_FORWARD
        )
        assertEquals(10, firstForward)

        val secondForward = com.example.ui.components.PlayerGestureHelper.computeCumulativeSeekSeconds(
            existingZone = com.example.ui.components.DoubleTapZone.RIGHT_FORWARD,
            existingSeconds = firstForward,
            newZone = com.example.ui.components.DoubleTapZone.RIGHT_FORWARD
        )
        assertEquals(20, secondForward)

        // 3. Verify Vertical Swipe Gesture Type (Left half = Brightness, Right half = Volume)
        assertEquals(
            com.example.ui.components.GestureControlType.BRIGHTNESS,
            com.example.ui.components.PlayerGestureHelper.resolveVerticalGestureType(250f, 1000f)
        )
        assertEquals(
            com.example.ui.components.GestureControlType.VOLUME,
            com.example.ui.components.PlayerGestureHelper.resolveVerticalGestureType(750f, 1000f)
        )

        // 4. Verify Vertical Swipe Delta calculation (Swiping UP increases level, swiping DOWN decreases level)
        val increasedLevel = com.example.ui.components.PlayerGestureHelper.computeUpdatedGestureLevel(
            currentLevel = 0.50f,
            verticalDragDeltaPx = -130f, // Swipe UP
            containerHeightPx = 1000f
        )
        assertEquals(0.70f, increasedLevel, 0.01f)

        val decreasedLevel = com.example.ui.components.PlayerGestureHelper.computeUpdatedGestureLevel(
            currentLevel = increasedLevel,
            verticalDragDeltaPx = 195f, // Swipe DOWN
            containerHeightPx = 1000f
        )
        assertEquals(0.40f, decreasedLevel, 0.01f)

        // 5. Verify initial brightness & volume helpers run safely
        val initBrightness = com.example.ui.components.PlayerGestureHelper.readInitialScreenBrightness(null, context)
        assertTrue(initBrightness in 0.05f..1.0f)
        val initVolume = com.example.ui.components.PlayerGestureHelper.readInitialAudioVolume(context, 0.8f)
        assertTrue(initVolume in 0.0f..1.0f)

        // 6. Verify Screen Orientation Lock & Force Landscape Mode in Video Player Settings
        val defaultMode = com.example.ui.components.PlayerGestureHelper.readSavedOrientationMode(context)
        assertEquals(com.example.ui.components.PlayerOrientationMode.LOCKED_LANDSCAPE, defaultMode)
        assertTrue(defaultMode.isLandscapeLocked)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            defaultMode.activityOrientationConstant
        )

        // Toggle Landscape Lock -> Unlocked (Auto-Rotate)
        val unlockedMode = com.example.ui.components.PlayerGestureHelper.toggleLandscapeLock(defaultMode)
        assertEquals(com.example.ui.components.PlayerOrientationMode.AUTO_ROTATE, unlockedMode)
        org.junit.Assert.assertFalse(unlockedMode.isLandscapeLocked)

        // Toggle back -> Locked Landscape
        val relockedMode = com.example.ui.components.PlayerGestureHelper.toggleLandscapeLock(unlockedMode)
        assertEquals(com.example.ui.components.PlayerOrientationMode.LOCKED_LANDSCAPE, relockedMode)
        assertTrue(relockedMode.isLandscapeLocked)

        // Persist and read back custom orientation modes
        com.example.ui.components.PlayerGestureHelper.saveAndApplyOrientationMode(
            context,
            null,
            com.example.ui.components.PlayerOrientationMode.PORTRAIT
        )
        assertEquals(
            com.example.ui.components.PlayerOrientationMode.PORTRAIT,
            com.example.ui.components.PlayerGestureHelper.readSavedOrientationMode(context)
        )

        // Restore Force Landscape Lock via setForceLandscapeLocked(true)
        val forcedLandscape = com.example.ui.components.PlayerGestureHelper.setForceLandscapeLocked(true)
        com.example.ui.components.PlayerGestureHelper.saveAndApplyOrientationMode(context, null, forcedLandscape)
        assertEquals(
            com.example.ui.components.PlayerOrientationMode.LOCKED_LANDSCAPE,
            com.example.ui.components.PlayerGestureHelper.readSavedOrientationMode(context)
        )

        // 7. Verify Media3 ExoPlayer Adaptive Track Selection Strategy:
        //    - Prioritizes high-quality Full HD (1080p) streams when bandwidth and buffer are healthy
        //    - Dynamically downscales across tiers based on network bandwidth & buffering/rebuffer state
        //    - Smoothly scales back up when bandwidth and buffer recover
        assertEquals(3_500, com.example.player.LivePlayerController.ADAPTIVE_MIN_DURATION_FOR_QUALITY_INCREASE_MS)
        assertEquals(2_000, com.example.player.LivePlayerController.ADAPTIVE_MAX_DURATION_FOR_QUALITY_DECREASE_MS)
        assertEquals(0.85f, com.example.player.LivePlayerController.ADAPTIVE_BANDWIDTH_FRACTION, 0.001f)

        // High bandwidth (3.8 Mbps) + healthy buffer (6s) + not buffering -> Prioritizes FULL_HD_1080P
        val highQualityTier = com.example.player.LivePlayerController.computeAdaptiveQualityTier(
            estimatedBitrateBps = 3_800_000L,
            bufferedDurationMs = 6_000L,
            isBuffering = false,
            consecutiveRebufferCount = 0,
            isLowBandoNetwork = false
        )
        assertEquals(com.example.player.AdaptiveQualityTier.FULL_HD_1080P, highQualityTier)
        assertEquals(1920, highQualityTier.maxWidth)
        assertEquals(1080, highQualityTier.maxHeight)

        // Moderate HD bandwidth (1.6 Mbps) -> HD_720P
        val hd720Tier = com.example.player.LivePlayerController.computeAdaptiveQualityTier(
            estimatedBitrateBps = 1_600_000L,
            bufferedDurationMs = 5_000L,
            isBuffering = false,
            consecutiveRebufferCount = 0,
            isLowBandoNetwork = false
        )
        assertEquals(com.example.player.AdaptiveQualityTier.HD_720P, hd720Tier)

        // Active buffering stall with depleted buffer (< 1.5s) on high bandwidth -> Dynamically downscales from 1080p to 720p
        val bufferingDownscaledTier = com.example.player.LivePlayerController.computeAdaptiveQualityTier(
            estimatedBitrateBps = 3_800_000L,
            bufferedDurationMs = 400L,
            isBuffering = true,
            consecutiveRebufferCount = 0,
            isLowBandoNetwork = false
        )
        assertEquals(com.example.player.AdaptiveQualityTier.HD_720P, bufferingDownscaledTier)

        // Repeated rebuffer stalls (consecutiveRebufferCount = 2) -> Dynamically downscales 2 steps (1080p -> 480p SD)
        val multiRebufferTier = com.example.player.LivePlayerController.computeAdaptiveQualityTier(
            estimatedBitrateBps = 3_800_000L,
            bufferedDurationMs = 800L,
            isBuffering = true,
            consecutiveRebufferCount = 2,
            isLowBandoNetwork = false
        )
        assertEquals(com.example.player.AdaptiveQualityTier.STANDARD_480P, multiRebufferTier)

        // Severe bandwidth drop (220 kbps) -> Downscales to LOW_BANDO_240P with forceLowestBitrate = true
        val emergencyLowBandoTier = com.example.player.LivePlayerController.computeAdaptiveQualityTier(
            estimatedBitrateBps = 220_000L,
            bufferedDurationMs = 500L,
            isBuffering = true,
            consecutiveRebufferCount = 1,
            isLowBandoNetwork = true
        )
        assertEquals(com.example.player.AdaptiveQualityTier.LOW_BANDO_240P, emergencyLowBandoTier)
        assertTrue(emergencyLowBandoTier.forceLowestBitrate)

        // Verify LivePlayerController dynamically updates playbackInfo telemetry when evaluating adaptive track selection
        val testChannel = com.example.data.ChannelRepository.getPrioritizedAllChannels().first()
        val controller = com.example.player.LivePlayerController(context, testChannel)
        val downscaled = controller.evaluateAndApplyAdaptiveTrackSelection(
            estimatedBitrateBps = 500_000L,
            bufferedDurationMs = 600L,
            forceBufferingState = true,
            rebufferCountOverride = 1
        )
        assertEquals(com.example.player.AdaptiveQualityTier.LOW_BANDO_240P, downscaled)
        assertTrue(controller.playbackInfo.value.isDynamicallyDownscaled)
        assertEquals(500, controller.playbackInfo.value.estimatedBandwidthKbps)

        // Verify recovery back to FULL_HD_1080P when bandwidth & buffer recover
        val recovered = controller.evaluateAndApplyAdaptiveTrackSelection(
            estimatedBitrateBps = 4_200_000L,
            bufferedDurationMs = 9_000L,
            forceBufferingState = false,
            rebufferCountOverride = 0
        )
        assertEquals(com.example.player.AdaptiveQualityTier.FULL_HD_1080P, recovered)
        org.junit.Assert.assertFalse(controller.playbackInfo.value.isDynamicallyDownscaled)

        // 8. Verify Battery-Aware Adaptive Playback Strategy (CPU & Power Optimization):
        //    - Healthy battery (85% discharging, foreground) -> OPTIMAL_POWER (60fps, 1080p allowed)
        val optimalProfile = com.example.player.LivePlayerController.computeBatteryPowerProfile(
            batteryLevelPct = 85,
            isCharging = false,
            isOsPowerSaveMode = false,
            isBackgroundLoading = false,
            isPictureInPicture = false,
            optimizationMode = com.example.player.BatteryOptimizationMode.AUTO_BATTERY_AWARE
        )
        assertEquals(com.example.player.BatteryPowerProfile.OPTIMAL_POWER, optimalProfile)
        assertEquals(60, optimalProfile.maxFrameRate)
        org.junit.Assert.assertFalse(optimalProfile.isCpuSavingActive)

        //    - Low battery (20% discharging) -> LOW_BATTERY_SAVER (caps to 480p @ 30fps & throttles polling to 1.0s/1.2s)
        val lowBatProfile = com.example.player.LivePlayerController.computeBatteryPowerProfile(
            batteryLevelPct = 20,
            isCharging = false,
            isOsPowerSaveMode = false,
            isBackgroundLoading = false,
            isPictureInPicture = false,
            optimizationMode = com.example.player.BatteryOptimizationMode.AUTO_BATTERY_AWARE
        )
        assertEquals(com.example.player.BatteryPowerProfile.LOW_BATTERY_SAVER, lowBatProfile)
        assertEquals(30, lowBatProfile.maxFrameRate)
        assertEquals(com.example.player.AdaptiveQualityTier.STANDARD_480P, lowBatProfile.maxQualityTierCap)
        assertTrue(lowBatProfile.isCpuSavingActive)

        //    - Low battery (18%) while CHARGING -> OPTIMAL_POWER (full 1080p @ 60fps when plugged in)
        val chargingLowBatProfile = com.example.player.LivePlayerController.computeBatteryPowerProfile(
            batteryLevelPct = 18,
            isCharging = true,
            isOsPowerSaveMode = false,
            isBackgroundLoading = false,
            isPictureInPicture = false,
            optimizationMode = com.example.player.BatteryOptimizationMode.AUTO_BATTERY_AWARE
        )
        assertEquals(com.example.player.BatteryPowerProfile.OPTIMAL_POWER, chargingLowBatProfile)

        //    - Critical battery (9% discharging) -> CRITICAL_BATTERY_SAVER (caps to 360p @ 24fps, 1.5s/1.8s polling)
        val criticalBatProfile = com.example.player.LivePlayerController.computeBatteryPowerProfile(
            batteryLevelPct = 9,
            isCharging = false,
            isOsPowerSaveMode = false,
            isBackgroundLoading = false,
            isPictureInPicture = false,
            optimizationMode = com.example.player.BatteryOptimizationMode.AUTO_BATTERY_AWARE
        )
        assertEquals(com.example.player.BatteryPowerProfile.CRITICAL_BATTERY_SAVER, criticalBatProfile)
        assertEquals(24, criticalBatProfile.maxFrameRate)
        assertEquals(com.example.player.AdaptiveQualityTier.DATA_SAVER_360P, criticalBatProfile.maxQualityTierCap)

        //    - Background loading state -> BACKGROUND_LOADING_SAVER (240p @ 24fps cap & minimal CPU wakeups)
        val backgroundLoadProfile = com.example.player.LivePlayerController.computeBatteryPowerProfile(
            batteryLevelPct = 90,
            isCharging = true,
            isOsPowerSaveMode = false,
            isBackgroundLoading = true,
            isPictureInPicture = false,
            optimizationMode = com.example.player.BatteryOptimizationMode.AUTO_BATTERY_AWARE
        )
        assertEquals(com.example.player.BatteryPowerProfile.BACKGROUND_LOADING_SAVER, backgroundLoadProfile)
        assertEquals(24, backgroundLoadProfile.maxFrameRate)
        assertEquals(com.example.player.AdaptiveQualityTier.LOW_BANDO_240P, backgroundLoadProfile.maxQualityTierCap)

        //    - Verify LivePlayerController dynamically caps high-bandwidth 1080p stream to 480p @ 30fps on low battery
        val appliedLowBatteryProfile = controller.setDeviceBatterySnapshotOverrideForTesting(
            com.example.player.DeviceBatterySnapshot(
                batteryLevelPct = 19,
                isCharging = false,
                isOsPowerSaveMode = false,
                isBackgroundLoading = false,
                isPictureInPicture = false
            )
        )
        assertEquals(com.example.player.BatteryPowerProfile.LOW_BATTERY_SAVER, appliedLowBatteryProfile)
        assertEquals(com.example.player.AdaptiveQualityTier.STANDARD_480P, controller.playbackInfo.value.adaptiveQualityTier)
        assertEquals(30, controller.playbackInfo.value.activeMaxFrameRate)
        assertTrue(controller.playbackInfo.value.isCpuSavingActive)

        //    - Verify background loading state switches controller to BACKGROUND_LOADING_SAVER (240p @ 24fps)
        val appliedBgProfile = controller.setBackgroundLoadingState(
            isBackgroundLoading = true,
            isPictureInPicture = false
        )
        assertEquals(com.example.player.BatteryPowerProfile.BACKGROUND_LOADING_SAVER, appliedBgProfile)
        assertEquals(com.example.player.AdaptiveQualityTier.LOW_BANDO_240P, controller.playbackInfo.value.adaptiveQualityTier)
        assertEquals(24, controller.playbackInfo.value.activeMaxFrameRate)
        assertTrue(controller.playbackInfo.value.isBackgroundLoadingActive)

        //    - Verify returning to foreground while charging restores OPTIMAL_POWER (1080p @ 60fps)
        controller.setDeviceBatterySnapshotOverrideForTesting(
            com.example.player.DeviceBatterySnapshot(
                batteryLevelPct = 80,
                isCharging = true,
                isOsPowerSaveMode = false,
                isBackgroundLoading = false,
                isPictureInPicture = false
            )
        )
        assertEquals(com.example.player.BatteryPowerProfile.OPTIMAL_POWER, controller.playbackInfo.value.batteryPowerProfile)
        assertEquals(com.example.player.AdaptiveQualityTier.FULL_HD_1080P, controller.playbackInfo.value.adaptiveQualityTier)
        assertEquals(60, controller.playbackInfo.value.activeMaxFrameRate)
        org.junit.Assert.assertFalse(controller.playbackInfo.value.isCpuSavingActive)

        // 9. Verify Offline Download Manager:
        //    - Special NeliPlay folder auto-creation on device storage
        //    - Resumable partial download state across internet interruptions (never resets to 0%)
        //    - Completed download is immediately playable offline inside the app (never stuck in downloading state)
        val specialFolder = com.example.data.OfflineDownloadManager.ensureSpecialDeviceDownloadFolder(context)
        assertTrue(specialFolder.exists())
        assertEquals(com.example.data.OfflineDownloadManager.SPECIAL_DEVICE_FOLDER_NAME, specialFolder.name)
        assertTrue(
            "Downloads must be stored strictly inside app-internal filesDir",
            specialFolder.absolutePath.startsWith(context.filesDir.absolutePath)
        )
        assertEquals(
            "In-App Private Storage (Nelitv Only)",
            com.example.data.OfflineDownloadManager.SPECIAL_DEVICE_FOLDER_DISPLAY_PATH
        )

        val cleanName = com.example.data.OfflineDownloadManager.buildCleanDeviceFileName(
            title = "Never a Thief (Swahili)",
            id = "mov_test_resume_1",
            extension = "mp4"
        )
        assertTrue(cleanName.endsWith("_mov_test_resume_1.mp4"))

        // Simulate a partial MP4 download interrupted at 50% (500 KB out of 1,000 KB)
        val partFile = java.io.File(specialFolder, "mov_test_resume_1.mp4.part")
        val metaFile = java.io.File(specialFolder, "mov_test_resume_1.mp4.meta")
        partFile.writeBytes(ByteArray(500 * 1024) { 0x11 })
        metaFile.writeText("""{"expectedTotalBytes": ${1000 * 1024}, "sourceUrl": "https://example.com/movie.mp4"}""")

        val (resumedPct, resumedBytes) = com.example.data.OfflineDownloadManager.inspectPartialDownloadState(
            context = context,
            id = "mov_test_resume_1",
            fallbackPct = 1
        )
        assertEquals(500 * 1024L, resumedBytes)
        assertTrue("Expected resumed percentage around 49%, got $resumedPct", resumedPct in 48..51)

        // Simulate completion of the download in the special NeliPlay folder
        val completedFile = java.io.File(specialFolder, cleanName)
        partFile.copyTo(completedFile, overwrite = true)
        partFile.delete()
        metaFile.delete()

        val completedEntity = com.example.data.local.DownloadedItemEntity(
            id = "mov_test_resume_1",
            title = "Never a Thief (Swahili)",
            type = "movie",
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "https://example.com/backdrop.jpg",
            streamUrl = "https://example.com/movie.mp4",
            localFilePath = completedFile.absolutePath,
            genre = "Action",
            duration = "2h 00m",
            rating = "8.5",
            fileSizeLabel = "0.5 MB • Offline Ready",
            downloadStatus = "COMPLETED",
            progressPercent = 100
        )

        // Completed download must be valid on disk, NOT marked as currently downloading, and resolve to file:// URI
        assertTrue(com.example.data.OfflineDownloadManager.isDownloadFileValidOnDisk(completedEntity))
        org.junit.Assert.assertFalse(com.example.data.OfflineDownloadManager.isCurrentlyDownloading(completedEntity.id))

        val playableUri = com.example.data.OfflineDownloadManager.resolvePlayableUrl(
            streamUrl = completedEntity.streamUrl,
            localFilePath = completedEntity.localFilePath,
            context = context,
            itemId = completedEntity.id
        )
        assertTrue("Expected file:// URI for offline playback, got $playableUri", playableUri.startsWith("file:"))

        val resolvedByIdUri = com.example.data.OfflineDownloadManager.resolveLocalOfflineUriIfPresent(
            context = context,
            rawId = "vod_mov_test_resume_1",
            fallbackStreamUrl = completedEntity.streamUrl
        )
        assertTrue("Expected file:// URI when resolving by media ID, got $resolvedByIdUri", resolvedByIdUri.startsWith("file:"))

        // 10. Verify Bottom Navigation Tabs (Home, Discovery, Premium, Downloads, Account),
        //     HarakaPay TZS Subscription Plans (1,000 TSh for 2 days / 3,500 TSh for week / 15,000 TSh for month),
        //     Strict Payment Verification, Mini Admin Panel (Admin@login.com / 123456),
        //     Channel Lock for Free Users vs Open for Premium Members, and Smart TV Cast
        val navLabels = com.example.ui.components.BottomNavTab.entries.map { it.label }
        assertEquals(listOf("Home", "Discovery", "Premium", "Downloads", "Account"), navLabels)

        assertEquals(1000, com.example.data.SubscriptionPlanType.DAILY.amountTzs)
        assertEquals("Kwa Siku Mbili", com.example.data.SubscriptionPlanType.DAILY.titleSwahili)
        assertEquals(3500, com.example.data.SubscriptionPlanType.WEEKLY.amountTzs)
        assertEquals(15000, com.example.data.SubscriptionPlanType.MONTHLY.amountTzs)
        assertTrue(com.example.data.HarakaPayRepository.resolveApiKey().startsWith("hpk_"))

        com.example.data.NeliSubscriptionManager.initialize(context)
        com.example.data.NeliAdminManager.initialize(context)

        // Verify Admin credential check (strictly Admin@login.com and 123456)
        assertTrue(com.example.data.NeliAdminManager.isAdminCredentials("Admin@login.com", "123456"))
        org.junit.Assert.assertFalse(com.example.data.NeliAdminManager.isAdminCredentials("user@gmail.com", "123456"))
        org.junit.Assert.assertFalse(com.example.data.NeliAdminManager.isAdminCredentials("Admin@login.com", "wrong"))

        // Verify Channel Lock for Free Users vs Open for Verified Premium Members:
        // 1) Channels are initially FREE, user pays early while channels are free
        com.example.data.NeliSubscriptionManager.resetForTesting(context)
        com.example.data.NeliAdminManager.setSingleChannelLock(context, "azam_sports_1", false)
        val paidEarlyState = com.example.data.NeliSubscriptionManager.activateVerifiedSubscription(
            context = context,
            plan = com.example.data.SubscriptionPlanType.DAILY,
            phone = "0712345678",
            verifiedOrderId = "HP_ORD_EARLY_1"
        )
        assertTrue(paidEarlyState.isActiveNow)

        // 2) Later, Admin locks the channel -> because user already paid and subscription is active, it MUST NOT lock!
        com.example.data.NeliAdminManager.setSingleChannelLock(context, "azam_sports_1", true)
        org.junit.Assert.assertFalse(
            com.example.data.NeliAdminManager.isChannelLockedForUser(
                channelId = "azam_sports_1",
                currentUser = null,
                context = context
            )
        )

        // 3) Once subscription expires (nowMs > expiresAtMs) -> channel MUST lock!
        assertTrue(
            com.example.data.NeliAdminManager.isChannelLockedForUser(
                channelId = "azam_sports_1",
                currentUser = null,
                isPremiumActive = false,
                nowMs = paidEarlyState.expiresAtMs + 5_000L,
                context = context
            )
        )

        // 4) As soon as user pays again and payment succeeds -> channel MUST unlock immediately!
        com.example.data.NeliSubscriptionManager.activateVerifiedSubscription(
            context = context,
            plan = com.example.data.SubscriptionPlanType.WEEKLY,
            phone = "0712345678",
            verifiedOrderId = "HP_ORD_RENEW_2"
        )
        org.junit.Assert.assertFalse(
            com.example.data.NeliAdminManager.isChannelLockedForUser(
                channelId = "azam_sports_1",
                currentUser = null,
                context = context
            )
        )
        com.example.data.NeliAdminManager.setSingleChannelLock(context, "azam_sports_1", false)

        // Verify Admin Notification Bar publish, placement (Above/Below Slider on Home only) & clear
        com.example.data.NeliAdminManager.publishAdminSms(
            context = context,
            messageText = "Karibu Nelitv Live HD!",
            sendPushNotification = false,
            placement = com.example.data.AdminBannerPlacement.ABOVE_SLIDER
        )
        assertEquals("Karibu Nelitv Live HD!", com.example.data.NeliAdminManager.activeAdminSms.value?.message)
        assertEquals(
            com.example.data.AdminBannerPlacement.ABOVE_SLIDER,
            com.example.data.NeliAdminManager.adminBannerPlacement.value
        )
        com.example.data.NeliAdminManager.setAdminBannerPlacement(
            context,
            com.example.data.AdminBannerPlacement.BELOW_SLIDER
        )
        assertEquals(
            com.example.data.AdminBannerPlacement.BELOW_SLIDER,
            com.example.data.NeliAdminManager.adminBannerPlacement.value
        )
        com.example.data.NeliAdminManager.clearAdminSms(context)
        assertEquals(null, com.example.data.NeliAdminManager.activeAdminSms.value)

        // Verify Smart TV Cast Manager device discovery (no hardcoded Sebuleni/Chumbani TVs)
        com.example.player.NeliCastManager.refreshAvailableTvDevices(context)
        org.junit.Assert.assertFalse(
            com.example.player.NeliCastManager.availableDevices.value.any {
                it.name.contains("Sebuleni", ignoreCase = true) || it.name.contains("Chumbani", ignoreCase = true)
            }
        )

        // Verify Azam TV Language Switcher (Kiswahili Primary & Kiswahili-only preservation)
        val azamSportsChannel = com.example.data.ChannelRepository.channels.first {
            it.name.contains("Azam Sports 1", ignoreCase = true)
        }
        val sinemaZetuChannel = com.example.data.ChannelRepository.channels.first {
            it.name.contains("Sinema Zetu", ignoreCase = true)
        }
        assertTrue(azamSportsChannel.isAzamTvChannel)
        org.junit.Assert.assertFalse(azamSportsChannel.isKiswahiliOnlyProgram)
        assertTrue(sinemaZetuChannel.isAzamTvChannel)
        assertTrue(sinemaZetuChannel.isKiswahiliOnlyProgram)

        val sportsController = com.example.player.LivePlayerController(context, azamSportsChannel)
        assertEquals("sw", sportsController.playbackInfo.value.activeAudioLanguage)
        assertEquals("en", sportsController.switchAzamAudioLanguage("en"))
        assertEquals("en", sportsController.playbackInfo.value.activeAudioLanguage)
        assertEquals("sw", sportsController.switchAzamAudioLanguage("sw"))
        assertEquals("sw", sportsController.playbackInfo.value.activeAudioLanguage)
        sportsController.release()

        val swahiliOnlyController = com.example.player.LivePlayerController(context, sinemaZetuChannel)
        assertEquals("sw", swahiliOnlyController.playbackInfo.value.activeAudioLanguage)
        assertEquals("sw", swahiliOnlyController.switchAzamAudioLanguage("en"))
        assertEquals("sw", swahiliOnlyController.playbackInfo.value.activeAudioLanguage)
        swahiliOnlyController.release()

        // 12. Verify PaymentService (HarakaPay API USSD push initiation, order status verification, headers, and error handling)
        var capturedMethod = ""
        var capturedUrl = ""
        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedBody: String? = null

        val fakeTransport = com.example.data.PaymentHttpTransport { method, url, headers, requestBody ->
            capturedMethod = method
            capturedUrl = url
            capturedHeaders = headers
            capturedBody = requestBody
            when {
                url.endsWith("/api/v1/collect") -> com.example.data.PaymentHttpResponse(
                    statusCode = 200,
                    body = """{"success":true,"message":"USSD push sent","order_id":"HP_ORD_998877","amount":3000,"net_amount":2910,"fee":90}"""
                )
                url.endsWith("/api/v1/status/HP_ORD_998877") -> com.example.data.PaymentHttpResponse(
                    statusCode = 200,
                    body = """{"success":true,"payment":{"order_id":"HP_ORD_998877","status":"completed","amount":3000,"net_amount":2910,"fee_amount":90}}"""
                )
                url.endsWith("/api/v1/status/HP_ORD_FAIL") -> com.example.data.PaymentHttpResponse(
                    statusCode = 401,
                    body = """{"success":false,"error":"Invalid API key"}"""
                )
                else -> com.example.data.PaymentHttpResponse(statusCode = 404, body = """{"success":false,"error":"Not found"}""")
            }
        }

        val paymentService = com.example.data.PaymentService(
            apiKey = "hpk_93b63ba05db51f1963b174570c71762a73541195bdbc5538",
            baseUrl = "https://harakapay.net",
            httpTransport = fakeTransport
        )

        val collectResult = paymentService.initiateUssdPushPaymentBlocking(
            phone = "+255712345678",
            amount = 3000,
            description = "Nelitv Weekly VIP"
        )
        assertTrue(collectResult.isSuccess)
        assertEquals("POST", capturedMethod)
        assertEquals("https://harakapay.net/api/v1/collect", capturedUrl)
        assertEquals(
            "hpk_93b63ba05db51f1963b174570c71762a73541195bdbc5538",
            capturedHeaders[com.example.data.PaymentService.HEADER_API_KEY]
        )
        assertEquals("application/json", capturedHeaders[com.example.data.PaymentService.HEADER_CONTENT_TYPE])
        assertTrue(capturedBody.orEmpty().contains("0712345678"))
        assertEquals("HP_ORD_998877", collectResult.getOrNull()?.orderId)

        val statusResult = paymentService.verifyOrderStatusBlocking("HP_ORD_998877")
        assertTrue(statusResult.isSuccess)
        assertEquals("GET", capturedMethod)
        assertEquals("https://harakapay.net/api/v1/status/HP_ORD_998877", capturedUrl)
        assertTrue(statusResult.getOrNull()!!.isCompleted)

        // Verify error responses (invalid phone, amount < 100, and HTTP 401 error response)
        val badPhoneRes = paymentService.initiateUssdPushPaymentBlocking("12345", 500, "Test")
        assertTrue(badPhoneRes.isFailure)
        val badAmountRes = paymentService.initiateUssdPushPaymentBlocking("0712345678", 50, "Test")
        assertTrue(badAmountRes.isFailure)
        val unauthorizedStatusRes = paymentService.verifyOrderStatusBlocking("HP_ORD_FAIL")
        assertTrue(unauthorizedStatusRes.isFailure)

        // 13. Verify Automatic Ready Device IP payment without login or signup saves real data
        com.example.data.NeliSubscriptionManager.resetForTesting(context)
        val detectedIp = com.example.data.NeliSubscriptionManager.refreshDeviceIp(context)
        assertTrue(detectedIp.isNotBlank())

        val verifiedNoLoginState = com.example.data.NeliSubscriptionManager.activateVerifiedSubscription(
            context = context,
            plan = com.example.data.SubscriptionPlanType.WEEKLY,
            phone = "0712345678",
            verifiedOrderId = "HP_ORD_998877"
        )
        assertTrue(verifiedNoLoginState.isActiveNow)
        assertTrue(verifiedNoLoginState.isVerified)
        assertEquals(detectedIp, verifiedNoLoginState.deviceIpAddress)
        assertEquals("0712345678", verifiedNoLoginState.phoneNumber)
        assertEquals("HP_ORD_998877", verifiedNoLoginState.orderId)

        val dbSub = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            com.example.data.local.NeliDatabase::class.java
        ).allowMainThreadQueries().build()
        kotlinx.coroutines.runBlocking {
            val savedEntity = com.example.data.NeliSubscriptionManager.saveRealSubscriptionDataByDeviceIp(
                dao = dbSub.mediaDao(),
                state = verifiedNoLoginState
            )
            assertEquals(detectedIp, savedEntity.deviceIpAddress)
            val queriedEntity = dbSub.mediaDao().getDeviceSubscriptionByIp(detectedIp)
            assertNotNull(queriedEntity)
            assertTrue(queriedEntity!!.isVerified)
            assertEquals("HP_ORD_998877", queriedEntity.orderId)
            assertEquals(3500, queriedEntity.amountTzs)

            // Verify post-payment login/signup links user account for cross-device sync
            assertTrue(verifiedNoLoginState.requiresPostPaymentAuth)
            val linkedState = com.example.data.NeliSubscriptionManager.linkUserAccountToSubscription(
                context = context,
                uid = "uid_juma_99",
                email = "juma@nelitv.tz",
                realName = "Juma Bakari"
            )
            assertEquals("uid_juma_99", linkedState.linkedUserUid)
            assertEquals("juma@nelitv.tz", linkedState.linkedUserEmail)
            assertEquals("Juma Bakari", linkedState.linkedUserName)
            org.junit.Assert.assertFalse(linkedState.requiresPostPaymentAuth)

            // Verify multiple accounts on the same device have isolated subscriptions and payment phone numbers
            val secondUserState = com.example.data.NeliSubscriptionManager.switchActiveAccount(
                context = context,
                uid = "uid_neema_22",
                email = "neema@nelitv.tz",
                realName = "Neema Moses"
            )
            org.junit.Assert.assertFalse(secondUserState.isVerified)
            assertEquals("", secondUserState.phoneNumber)
            assertEquals("", secondUserState.pendingOrderId)
            assertEquals("neema@nelitv.tz", secondUserState.linkedUserEmail)

            // Switching back to Juma restores Juma's own verified subscription & phone number, with empty pendingOrderId
            val restoredJumaState = com.example.data.NeliSubscriptionManager.switchActiveAccount(
                context = context,
                uid = "uid_juma_99",
                email = "juma@nelitv.tz",
                realName = "Juma Bakari"
            )
            assertTrue(restoredJumaState.isVerified)
            assertEquals("0712345678", restoredJumaState.phoneNumber)
            assertEquals("", restoredJumaState.pendingOrderId)

            // Verify Admin can add and hide/unhide channels, and even newly added channels are NEVER deleted
            val addedChRes = com.example.data.NeliAdminManager.addChannelByAdmin(
                context = context,
                name = "Azam Extra Live HD",
                streamUrl = "https://example.com/live/azam_extra.m3u8",
                thumbnailUrl = "",
                category = "Sports"
            )
            assertTrue(addedChRes.isSuccess)
            val addedCh = addedChRes.getOrThrow()
            assertTrue(com.example.data.NeliAdminManager.customAddedChannels.value.any { it.id == addedCh.id })
            org.junit.Assert.assertFalse(com.example.data.NeliAdminManager.isChannelHidden(addedCh.id))

            // Hiding the channel (or calling removeAdminChannel) must NEVER delete it from customAddedChannels — only hide it!
            com.example.data.NeliAdminManager.removeAdminChannel(context, addedCh.id)
            assertTrue(com.example.data.NeliAdminManager.isChannelHidden(addedCh.id))
            assertTrue(com.example.data.NeliAdminManager.customAddedChannels.value.any { it.id == addedCh.id })
            org.junit.Assert.assertFalse(
                com.example.data.ChannelRepository.getPrioritizedAllChannels().any { it.id == addedCh.id }
            )
            assertTrue(
                com.example.data.ChannelRepository.getAllChannelsIncludingHiddenForAdmin().any { it.id == addedCh.id }
            )

            // Unhiding the channel restores it to the viewer channel list
            com.example.data.NeliAdminManager.setChannelHidden(context, addedCh.id, false)
            org.junit.Assert.assertFalse(com.example.data.NeliAdminManager.isChannelHidden(addedCh.id))
            assertTrue(
                com.example.data.ChannelRepository.getPrioritizedAllChannels().any { it.id == addedCh.id }
            )

            // Verify Cast Manager syncs user info, subscription status, and High-Quality Anti-Stutter Stream
            com.example.player.NeliCastManager.syncUserAndSubscriptionInfo(
                userName = linkedState.linkedUserName,
                email = linkedState.linkedUserEmail,
                planTitle = linkedState.planTitle,
                isVerified = linkedState.isActiveNow,
                deviceIp = linkedState.deviceIpAddress
            )
            assertEquals("Juma Bakari", com.example.player.NeliCastManager.userDisplayName.value)
            assertEquals("juma@nelitv.tz", com.example.player.NeliCastManager.userEmail.value)
            assertTrue(com.example.player.NeliCastManager.userSubscriptionBadge.value.contains("Premium VIP"))
            com.example.player.NeliCastManager.setCastStreamQuality("1080p Full HD • 60fps Anti-Stutter")
            assertEquals(
                "1080p Full HD • 60fps Anti-Stutter",
                com.example.player.NeliCastManager.castStreamQuality.value
            )
            com.example.player.NeliCastManager.triggerCastStreamBoost()
            assertTrue(com.example.player.NeliCastManager.isAntiStutterActive.value)
            assertEquals(100, com.example.player.NeliCastManager.castBufferHealthPercent.value)
        }
        dbSub.close()

        // 15. Verify Responsive Layout Profiles for small phones (Itel/Tecno), standard/large phones (Samsung/Infinix), and tablets
        val smallItelProfile = com.example.ui.theme.NeliResponsiveLayout.resolveProfileForDimensions(
            widthDp = 320,
            heightDp = 640,
            manufacturer = "itel",
            model = "itel A60"
        )
        assertTrue(smallItelProfile.isSmallPhone)
        assertEquals("Itel", smallItelProfile.deviceBrandLabel)
        assertEquals(2, smallItelProfile.liveChannelGridColumns)

        val infinixPhoneProfile = com.example.ui.theme.NeliResponsiveLayout.resolveProfileForDimensions(
            widthDp = 392,
            heightDp = 850,
            manufacturer = "Infinix",
            model = "Infinix NOTE 40"
        )
        assertEquals("Infinix", infinixPhoneProfile.deviceBrandLabel)
        assertEquals(2, infinixPhoneProfile.liveChannelGridColumns)

        val samsungTabletProfile = com.example.ui.theme.NeliResponsiveLayout.resolveProfileForDimensions(
            widthDp = 900,
            heightDp = 1280,
            manufacturer = "Samsung",
            model = "SM-X710"
        )
        assertTrue(samsungTabletProfile.isTabletOrFoldable)
        assertEquals("Samsung", samsungTabletProfile.deviceBrandLabel)
        assertEquals(4, samsungTabletProfile.liveChannelGridColumns)

        // 16. Verify new app logo is used for Admin notifications (not Azam channels), while automatic notifications remain intact
        assertEquals(
            R.drawable.img_nelitv_app_logo_1790873809763,
            com.example.notifications.NeliNotificationScheduler.adminNotificationAppLogoResId
        )
        val appLogoBmp = com.example.notifications.NeliNotificationScheduler.loadAppLogoBitmap(context)
        assertTrue(appLogoBmp.width > 0 && appLogoBmp.height > 0)
        assertEquals(4, com.example.notifications.NeliNotificationScheduler.dailySlots.size)
        assertEquals(6, com.example.notifications.NeliNotificationScheduler.liveChannelLogoSpecs.size)
        com.example.notifications.NeliNotificationScheduler.sendAdminBroadcastNotification(
            context,
            "Tangazo la Admin lenye Logo Mpya ya Nelitv"
        )

        // 14. Verify Live Stream Freeze / Stall Auto-Fix Engine automatically recovers stuck live stream
        val liveAutoFixController = com.example.player.LivePlayerController(context, azamSportsChannel)
        // Initial healthy tick at t = 1,000ms, position = 5,000ms
        org.junit.Assert.assertFalse(
            liveAutoFixController.checkAndAutoFixLiveStreamStall(
                currentPositionMs = 5_000L,
                playbackState = androidx.media3.common.Player.STATE_READY,
                isPlaying = true,
                elapsedRealtimeMs = 1_000L
            )
        )
        // Frozen position at 5,000ms for 2,500ms (t = 3,500ms) -> Auto-Fix must trigger automatically!
        assertTrue(
            liveAutoFixController.checkAndAutoFixLiveStreamStall(
                currentPositionMs = 5_000L,
                playbackState = androidx.media3.common.Player.STATE_READY,
                isPlaying = true,
                elapsedRealtimeMs = 3_500L
            )
        )
        assertEquals(1, liveAutoFixController.playbackInfo.value.liveAutoFixCount)
        assertTrue(
            liveAutoFixController.playbackInfo.value.lastAutoFixReason
                .orEmpty()
                .contains("live_position_frozen")
        )
        liveAutoFixController.release()

        // 17. Verify Free Forever 5 accounts (max 2 devices), 100% real analytics (confirmed payments only), and Premium Logout state clearing
        assertEquals(5, com.example.data.NeliFreeForeverAccountsManager.FREE_FOREVER_EMAILS.size)
        assertTrue(com.example.data.NeliFreeForeverAccountsManager.isFreeForeverEmail("user1@login.com"))
        assertTrue(com.example.data.NeliFreeForeverAccountsManager.isFreeForeverEmail("user5@login.com"))
        assertTrue(com.example.data.NeliFreeForeverAccountsManager.isValidFreeForeverPassword("Free123"))
        com.example.data.NeliFreeForeverAccountsManager.adminResetAllDevicesForAccount(context, "user1@login.com")
        com.example.data.NeliFreeForeverAccountsManager.adminAddTestDeviceSlot(context, "user1@login.com")
        com.example.data.NeliFreeForeverAccountsManager.adminAddTestDeviceSlot(context, "user1@login.com")
        // Attempt adding a 3rd slot -> remains capped at MAX_DEVICES_PER_ACCOUNT (2)
        com.example.data.NeliFreeForeverAccountsManager.adminAddTestDeviceSlot(context, "user1@login.com")
        val user1Status = com.example.data.NeliFreeForeverAccountsManager.accountsStatusFlow.value.first {
            it.email.equals("user1@login.com", ignoreCase = true)
        }
        assertEquals(2, user1Status.activeDeviceCount)
        assertTrue(user1Status.isFull)
        com.example.data.NeliFreeForeverAccountsManager.adminResetAllDevicesForAccount(context, "user1@login.com")

        // Verify logging out from a Premium account immediately clears the active session state so no Premium banner persists
        val loggedOutSubState = com.example.data.NeliSubscriptionManager.switchActiveAccount(
            context = context,
            uid = "",
            email = "",
            realName = ""
        )
        org.junit.Assert.assertFalse(loggedOutSubState.isVerified)
        org.junit.Assert.assertFalse(loggedOutSubState.isActiveNow)

        // Verify NeliRealtimeAnalyticsManager strictly ignores PENDING payments and only records CONFIRMED ("COMPLETED") payments
        com.example.data.NeliRealtimeAnalyticsManager.recordHarakaPayTransaction(
            context = context,
            orderId = "HP_PENDING_IGNORED_01",
            phoneNumber = "0712345678",
            plan = com.example.data.SubscriptionPlanType.WEEKLY,
            status = "PENDING"
        )
        com.example.data.NeliRealtimeAnalyticsManager.recordHarakaPayTransaction(
            context = context,
            orderId = "HP_CONFIRMED_TEST_01",
            phoneNumber = "0712345678",
            plan = com.example.data.SubscriptionPlanType.WEEKLY,
            status = "COMPLETED"
        )
        kotlinx.coroutines.runBlocking {
            com.example.data.NeliRealtimeAnalyticsManager.refreshRealtimeSnapshot(context, syncCloud = false)
        }
        val currentSnapshot = com.example.data.NeliRealtimeAnalyticsManager.snapshot.value
        org.junit.Assert.assertFalse(
            currentSnapshot.recentTransactions.any { it.orderId == "HP_PENDING_IGNORED_01" }
        )
        assertTrue(
            currentSnapshot.recentTransactions.any { it.orderId == "HP_CONFIRMED_TEST_01" && it.status == "COMPLETED" }
        )

        // 18. Verify AZAM-only SCAN TO CAST adapter (CastPlaybackPayload.fromLiveChannel), QR handshake, and remote commands
        assertEquals(18, com.example.data.ChannelRepository.hardcodedAzamChannels.size)
        assertTrue(com.example.data.ChannelRepository.isHardcodedAzamChannel(azamSportsChannel))
        val nonAzamCnn = com.example.data.ChannelRepository.channels.first { it.name.equals("CNN", ignoreCase = true) }
        org.junit.Assert.assertFalse(com.example.data.ChannelRepository.isHardcodedAzamChannel(nonAzamCnn))
        val moviePlayable = com.example.data.MediaContentRepository.mediaCatalog.value.first { !it.isSeries }.toPlayableChannel()
        org.junit.Assert.assertFalse(com.example.data.ChannelRepository.isHardcodedAzamChannel(moviePlayable))

        val castPayload = com.example.player.CastPlaybackPayload.fromLiveChannel(
            channel = azamSportsChannel,
            preferredAudioLanguageOverride = "sw",
            playbackVersion = 1
        )
        assertEquals(azamSportsChannel.id, castPayload.channelId)
        assertEquals("Azam Sports 1 HD", castPayload.channelName)
        assertEquals("dash", castPayload.streamFormat)
        assertEquals("application/dash+xml", castPayload.mimeType)
        // Must preserve exact resolved playable stream URL including token path & query
        assertTrue(castPayload.streamUrl.contains("/live/eds/AzamSport1/DASH/AzamSport1.mpd"))
        assertTrue(castPayload.streamUrl.contains("cdntoken="))
        assertTrue(castPayload.clearKeys.containsKey("c31df1600afc33799ecac543331803f2"))
        assertTrue(castPayload.clearKeyJwk.contains("\"keys\""))
        assertEquals("sw", castPayload.preferredAudioLanguage)
        assertEquals(1, castPayload.playbackVersion)
        assertTrue(castPayload.expiresAt > 0L)

        // Reject non-AZAM channel in ScanToCastManager
        val rejectedNonAzam = com.example.player.ScanToCastManager.startScanToCastSession(context, nonAzamCnn)
        assertTrue(rejectedNonAzam.isFailure)

        // Start valid AZAM Scan to Cast session
        val startedCastRes = com.example.player.ScanToCastManager.startScanToCastSession(
            context = context,
            channel = azamSportsChannel,
            preferredAudioLanguage = "sw"
        )
        assertTrue(startedCastRes.isSuccess)
        val castSession = startedCastRes.getOrThrow()
        assertEquals(16, castSession.sessionId.length)
        assertEquals(
            "${com.example.player.CastReceiverConfig.CAST_RECEIVER_BASE_URL}/cast/${castSession.sessionId}",
            castSession.qrCastUrl
        )
        assertEquals(
            com.example.player.ScanToCastConnectionStatus.WAITING_FOR_TV,
            castSession.connectionStatus
        )
        org.junit.Assert.assertFalse(castSession.receiverConnected)

        val sessionJson = castSession.toFirebaseSessionJson()
        assertEquals("WAITING_FOR_RECEIVER", sessionJson.getString("status"))
        assertEquals(azamSportsChannel.id, sessionJson.getString("channelId"))
        assertNotNull(sessionJson.getJSONObject("playback"))

        // Simulate QR Handshake from Web Receiver (receiverConnected = true)
        com.example.player.ScanToCastManager.applyRemoteSessionSnapshot(
            sessionId = castSession.sessionId,
            remoteJson = org.json.JSONObject().apply {
                put("receiverConnected", true)
                put("status", "CONNECTED")
                put("isPlaying", true)
                put("browserLastSeen", System.currentTimeMillis())
            }
        )
        assertTrue(com.example.player.ScanToCastManager.sessionState.value.receiverConnected)
        assertEquals(
            com.example.player.ScanToCastConnectionStatus.CONNECTED_TO_TV,
            com.example.player.ScanToCastManager.sessionState.value.connectionStatus
        )

        // Verify remote commands (PAUSE, PLAY, SET_QUALITY, MUTE, UNMUTE, SET_VOLUME, DISCONNECT)
        com.example.player.ScanToCastManager.sendPauseCommand()
        assertEquals("PAUSE", com.example.player.ScanToCastManager.sessionState.value.command)
        val pauseCmdId = com.example.player.ScanToCastManager.sessionState.value.commandId
        assertTrue(pauseCmdId.isNotBlank())

        com.example.player.ScanToCastManager.sendSetQualityCommand(com.example.player.CastQualityPreset.HIGH)
        assertEquals("SET_QUALITY", com.example.player.ScanToCastManager.sessionState.value.command)
        assertEquals(
            com.example.player.CastQualityPreset.HIGH,
            com.example.player.ScanToCastManager.sessionState.value.currentQuality
        )
        org.junit.Assert.assertNotEquals(pauseCmdId, com.example.player.ScanToCastManager.sessionState.value.commandId)

        com.example.player.ScanToCastManager.disconnectCastSession()
        assertEquals(
            com.example.player.ScanToCastConnectionStatus.IDLE,
            com.example.player.ScanToCastManager.sessionState.value.connectionStatus
        )

        // 19. Verify https://cast-nelitv.web.app domain, CameraScannerView QR decoder, QR camera connect with immediate live stream, and Admin "Pay to Watch" lock enforcement
        assertEquals("https://cast-nelitv.web.app", com.example.player.CastReceiverConfig.DEFAULT_RECEIVER_BASE_URL)
        val encodedQrMatrix = com.example.ui.components.IsoQrCodeEncoder.encodeByteModeEccL("https://cast-nelitv.web.app/cast/SESSION_QR_99")
        val decodedQrString = com.example.ui.components.CameraQrFrameDecoder.decodeQrFromBooleanBitmap(encodedQrMatrix)
        assertEquals("https://cast-nelitv.web.app/cast/SESSION_QR_99", decodedQrString)
        assertEquals(
            "SESSION_QR_99",
            com.example.player.CastReceiverConfig.extractSessionIdFromScannedQr(decodedQrString!!)
        )
        assertEquals(
            "TV_PAIR_777",
            com.example.player.CastReceiverConfig.extractSessionIdFromScannedQr("https://cast-nelitv.web.app/?code=TV_PAIR_777")
        )
        assertEquals(
            "HASH_SES_88",
            com.example.player.CastReceiverConfig.extractSessionIdFromScannedQr("https://cast-nelitv.web.app/#/cast/HASH_SES_88")
        )
        assertEquals(
            "JSON_SES_55",
            com.example.player.CastReceiverConfig.extractSessionIdFromScannedQr("{\"sessionId\":\"JSON_SES_55\"}")
        )
        com.example.data.NeliAdminManager.setSingleChannelLock(context, azamSportsChannel.id, locked = false)
        val scannedConnectRes = com.example.player.ScanToCastManager.connectToScannedQrSession(
            context = context,
            rawScannedQr = "https://cast-nelitv.web.app/cast/SESSION_QR_99",
            initialAzamChannel = azamSportsChannel
        )
        assertTrue(scannedConnectRes.isSuccess)
        val connectedQrState = com.example.player.ScanToCastManager.sessionState.value
        assertTrue(connectedQrState.receiverConnected)
        assertEquals("SESSION_QR_99", connectedQrState.sessionId)
        assertTrue(connectedQrState.isPlaying)
        assertNotNull(connectedQrState.playback)
        assertTrue(connectedQrState.playback!!.streamUrl.contains(".mpd"))
        assertTrue(connectedQrState.playback!!.streamUrl.contains("cdntoken="))
        val connectedFirebaseJson = connectedQrState.toFirebaseSessionJson()
        assertTrue(connectedFirebaseJson.optString("streamUrl").contains(".mpd"))
        assertNotNull(connectedFirebaseJson.optJSONObject("playback"))
        assertNotNull(connectedFirebaseJson.optJSONObject("clearKeys"))

        // When channel is FREE, casting to connected device works immediately
        com.example.data.NeliAdminManager.setSingleChannelLock(context, azamSportsChannel.id, locked = false)
        val freeCastRes = com.example.player.ScanToCastManager.castAzamChannelToConnectedDevice(
            context = context,
            channel = azamSportsChannel,
            currentUser = null
        )
        assertTrue(freeCastRes.isSuccess)
        org.junit.Assert.assertFalse(com.example.player.ScanToCastManager.sessionState.value.isPayToWatchLocked)
        assertNotNull(com.example.player.ScanToCastManager.sessionState.value.playback)

        // When Admin locks the channel and user has NOT paid, casting is blocked and writes "Pay to Watch"
        com.example.data.NeliSubscriptionManager.switchActiveAccount(
            context = context,
            uid = "",
            email = "",
            realName = ""
        )
        com.example.data.NeliAdminManager.setSingleChannelLock(context, azamSportsChannel.id, locked = true)
        val lockedCastRes = com.example.player.ScanToCastManager.castAzamChannelToConnectedDevice(
            context = context,
            channel = azamSportsChannel,
            currentUser = null
        )
        assertTrue(lockedCastRes.isFailure)
        assertTrue(com.example.player.ScanToCastManager.sessionState.value.isPayToWatchLocked)
        assertEquals("PAY_TO_WATCH", com.example.player.ScanToCastManager.sessionState.value.status)
        assertEquals("Pay to Watch", com.example.player.ScanToCastManager.sessionState.value.payToWatchMessage)
        assertEquals(null, com.example.player.ScanToCastManager.sessionState.value.playback)
        val lockedFirebaseJson = com.example.player.ScanToCastManager.sessionState.value.toFirebaseSessionJson()
        assertTrue(lockedFirebaseJson.optBoolean("payToWatch"))
        assertEquals("Pay to Watch", lockedFirebaseJson.optString("payToWatchMessage"))

        // Also verify NeliCastManager blocks standard TV casting with "Pay to Watch" when channel is locked
        val dummyTv = com.example.player.CastTvDevice(
            id = "tv_1",
            name = "Living Room Smart TV",
            subtitle = "Wireless Display",
            protocol = "Smart TV"
        )
        val standardCastAllowed = com.example.player.NeliCastManager.connectAndCastToTv(
            device = dummyTv,
            channel = azamSportsChannel,
            currentUser = null,
            context = context
        )
        org.junit.Assert.assertFalse(standardCastAllowed)
        assertTrue(com.example.player.NeliCastManager.isPayToWatchBlocked.value)
        assertEquals("Pay to Watch", com.example.player.NeliCastManager.statusMessage.value)

        // Unlock channel for clean state
        com.example.data.NeliAdminManager.setSingleChannelLock(context, azamSportsChannel.id, locked = false)
        com.example.player.ScanToCastManager.disconnectCastSession()

        completedFile.delete()
        controller.release()
    }
}
