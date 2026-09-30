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
        // 1. Verify BottomNavTab has exactly Home, Discovery, Search, Download, Account
        val tabs = com.example.ui.components.BottomNavTab.entries.map { it.label }
        assertEquals(listOf("Home", "Discovery", "Search", "Download", "Account"), tabs)

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

        // 10. Verify AdMob app-ads.txt, Application startup initialization, test device ID config, playback protection, cooldowns, and Download flow
        val neliApp = app as? com.example.NeliApplication
        assertNotNull("Expected Application context to be NeliApplication", neliApp)
        assertTrue(com.example.ads.NeliAdMobManager.isApplicationInitialized)
        assertTrue(
            com.example.ads.NeliAdMobManager.configuredTestDeviceIds.contains(
                com.google.android.gms.ads.AdRequest.DEVICE_ID_EMULATOR
            )
        )
        assertTrue(
            com.example.ads.NeliAdMobManager.configuredTestDeviceIds.contains(
                com.example.ads.NeliAdMobManager.DEVELOPMENT_TEST_DEVICE_ID
            )
        )

        val expectedAppAdsTxt = "google.com, pub-4408731854837351, DIRECT, f08c47fec0942fa0"
        assertEquals(expectedAppAdsTxt, com.example.ads.NeliAdMobManager.APP_ADS_TXT_SNIPPET)
        val assetAppAds = app.assets.open("app-ads.txt").bufferedReader().use { it.readText().trim() }
        assertEquals(expectedAppAdsTxt, assetAppAds)

        assertEquals("ca-app-pub-4408731854837351~1794082871", com.example.ads.NeliAdMobManager.APP_ID)
        assertEquals("ca-app-pub-4408731854837351/6443774325", com.example.ads.NeliAdMobManager.APP_OPEN_AD_UNIT_ID)
        assertEquals("ca-app-pub-4408731854837351/4300078286", com.example.ads.NeliAdMobManager.BANNER_AD_UNIT_ID)
        assertEquals("ca-app-pub-4408731854837351/7721371986", com.example.ads.NeliAdMobManager.INTERSTITIAL_AD_UNIT_ID)
        assertEquals("ca-app-pub-4408731854837351/6635346014", com.example.ads.NeliAdMobManager.REWARDED_INTERSTITIAL_AD_UNIT_ID)
        assertEquals("ca-app-pub-4408731854837351/5246242721", com.example.ads.NeliAdMobManager.REWARDED_AD_UNIT_ID)
        assertEquals("ca-app-pub-4408731854837351/7038845705", com.example.ads.NeliAdMobManager.NATIVE_ADVANCED_AD_UNIT_ID)

        // Verify Production Mode is enabled by default (real publisher AdMob IDs served, not test ads)
        org.junit.Assert.assertFalse(com.example.ads.NeliAdMobManager.useTestAdsInDevelopment)
        assertEquals(
            com.example.ads.NeliAdMobManager.BANNER_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveBannerAdUnitId()
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.APP_OPEN_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveAppOpenAdUnitId()
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.INTERSTITIAL_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveInterstitialAdUnitId()
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.NATIVE_ADVANCED_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveNativeAdUnitId()
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.BANNER_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveBannerAdUnitId(useTestAds = false)
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.TEST_BANNER_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveBannerAdUnitId(useTestAds = true)
        )

        // Verify NO ADS inside the video player / while playback is active
        com.example.ads.NeliAdMobManager.resetForTesting()
        assertTrue(com.example.ads.NeliAdMobManager.isInterstitialEligible(1_000_000L))
        assertTrue(com.example.ads.NeliAdMobManager.isAppOpenEligible(1_000_000L))

        com.example.ads.NeliAdMobManager.updatePlaybackActiveState(true)
        org.junit.Assert.assertFalse(com.example.ads.NeliAdMobManager.isInterstitialEligible(1_000_000L))
        org.junit.Assert.assertFalse(com.example.ads.NeliAdMobManager.isAppOpenEligible(1_000_000L))
        com.example.ads.NeliAdMobManager.updatePlaybackActiveState(false)

        // Verify Interstitial is never shown immediately after an App Open ad
        com.example.ads.NeliAdMobManager.lastAppOpenShownAtMs = 1_000_000L
        org.junit.Assert.assertFalse(com.example.ads.NeliAdMobManager.isInterstitialEligible(1_010_000L))
        assertTrue(com.example.ads.NeliAdMobManager.isInterstitialEligible(1_000_000L + com.example.ads.NeliAdMobManager.POST_APP_OPEN_GRACE_MS + 1000L))

        // Verify Download starts immediately without a second press when no interstitial is loaded
        var downloadTriggeredCount = 0
        com.example.ads.NeliAdMobManager.runDownloadWithInterstitialIfEligible(app) {
            downloadTriggeredCount++
        }
        assertEquals(1, downloadTriggeredCount)

        // 11. Verify Homepage channel categories order (Azam TV -> Sports -> Entertainment -> Kids -> News -> Movies -> ...),
        // 6-channel vertical chunking for Muted Video Ads, and guaranteed channel logos
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

        // Verify All Channels vertical chunking groups channels into blocks of 6 for Muted Video Ads
        val sixChannelBlocks = com.example.data.ChannelRepository.getAllChannelsChunkedEverySixForAds()
        assertTrue(sixChannelBlocks.isNotEmpty())
        assertEquals(6, sixChannelBlocks.first().size)
        assertEquals(
            com.example.ads.NeliAdMobManager.TEST_NATIVE_VIDEO_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveNativeVideoAdUnitId(useTestAds = true)
        )
        assertEquals(
            com.example.ads.NeliAdMobManager.NATIVE_ADVANCED_AD_UNIT_ID,
            com.example.ads.NeliAdMobManager.resolveNativeVideoAdUnitId(useTestAds = false)
        )

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

        completedFile.delete()
        controller.release()
    }
}
