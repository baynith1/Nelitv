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
        assertEquals("Neli TV", appName)
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

        // Ensure CDN token JSON payload updates token & host properly
        val tokenJson = """
            {
              "token": "${com.example.data.ChannelRepository.DEFAULT_AZAM_CDN_TOKEN}",
              "exp": 1790412582,
              "cdnHost": "https://cdnedgch2.azamtvltd.co.tz",
              "source": "cache"
            }
        """.trimIndent()
        assertTrue(com.example.data.ChannelRepository.updateCdnAuthorizationToken(tokenJson))
        assertEquals(1790412582L, com.example.data.ChannelRepository.AZAM_CDN_EXP)

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
        // Must extract exp = 1790412582 directly from inside the JWT payload even though Firestore exp is null!
        assertEquals(1790412582L, com.example.data.ChannelRepository.AZAM_CDN_EXP)
        assertEquals("https://cdnedgch2.azamtvltd.co.tz", com.example.data.ChannelRepository.AZAM_CDN_HOST)

        // Also verify Firestore Timestamp / Date picker format ("timestampValue": "2026-09-26T08:49:42Z")
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
        assertEquals(1790412582L, com.example.data.ChannelRepository.AZAM_CDN_EXP)
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
}
