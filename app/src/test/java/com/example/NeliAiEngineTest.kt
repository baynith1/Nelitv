package com.example

import com.example.ai.NeliAiAction
import com.example.ai.NeliAiBrightnessDirection
import com.example.ai.NeliAiCommandCatalog
import com.example.ai.NeliAiContextSnapshot
import com.example.ai.NeliAiEngine
import com.example.ai.NeliAiMemoryStore
import com.example.ai.NeliAiVolumeDirection
import com.example.ai.NeliCommandCategory
import com.example.data.ChannelHealthGuardian
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.player.NetworkQualityMode
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
class NeliAiEngineTest {

    private val sampleChannels = listOf(
        LiveChannel(
            id = "c1",
            name = "Azam One",
            description = "Tanzania Entertainment",
            streamUrl = "https://nelitv.online/stream/azam1.m3u8",
            backupStreamUrl = "https://backup.nelitv.online/stream/azam1_b.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://nelitv.online/img/azam1.png",
            categories = listOf("Azam TV", "Entertainment"),
            currentProgram = "Habari Saa Mbili",
            nextProgram = "Tamthilia ya Jioni",
            isFromNeliTvServer = true
        ),
        LiveChannel(
            id = "c2",
            name = "Azam Sports 1 HD",
            description = "NBC Premier League Live",
            streamUrl = "https://nelitv.online/stream/as1.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://nelitv.online/img/as1.png",
            categories = listOf("Sports", "Azam TV"),
            currentProgram = "Yanga vs Simba Live",
            nextProgram = "Highlights",
            isFromNeliTvServer = true
        ),
        LiveChannel(
            id = "c3",
            name = "Azam Two",
            description = "Azam Two VIP",
            streamUrl = "https://nelitv.online/stream/azam2.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://nelitv.online/img/azam2.png",
            categories = listOf("Azam TV"),
            currentProgram = "Bongo Drama",
            isFromNeliTvServer = true
        ),
        LiveChannel(
            id = "c4",
            name = "Tom and Jerry",
            description = "Kids Cartoon",
            streamUrl = "https://nelitv.online/stream/tj.m3u8",
            streamFormat = "hls",
            thumbnailUrl = "https://nelitv.online/img/tj.png",
            categories = listOf("Kids"),
            isFromNeliTvServer = true
        )
    )

    private val sampleMedia = (1..8).map { i ->
        MediaContent(
            id = "m$i",
            title = if (i == 1) "Mission Zanzibar 2026" else "Neli Action Film $i",
            type = "movie",
            posterUrl = "https://cdn.example.com/m$i.jpg",
            backdropUrl = "https://cdn.example.com/m${i}_bg.jpg",
            streamUrl = "https://cdn.example.com/m$i.mp4",
            genre = if (i % 2 == 0) "Comedy" else "Action",
            duration = "1h 55m",
            rating = "8.7",
            synopsis = "Thrilling story #$i on NeliTv.",
            isTrending = i <= 3,
            releaseYear = "2026",
            narrated = i == 1,
            narrationLanguage = if (i == 1) "Swahili" else "English"
        )
    }

    private fun snapshot(
        active: LiveChannel? = null,
        isOnline: Boolean = true,
        isSmartTv: Boolean = false,
        isPremiumActive: Boolean = false,
        downloadedIds: Set<String> = emptySet(),
        watched30Ids: Set<String> = emptySet(),
        lockedCheck: (LiveChannel) -> Boolean = { false }
    ) = NeliAiContextSnapshot(
        liveChannels = sampleChannels,
        mediaCatalog = sampleMedia,
        activePlayerChannel = active,
        isSmartTv = isSmartTv,
        isPremiumActive = isPremiumActive,
        userName = "Alex",
        hourOfDay24 = 20,
        isOnline = isOnline,
        downloadedMediaIds = downloadedIds,
        watchedWithin30DaysIds = watched30Ids,
        isChannelLockedCheck = lockedCheck
    )

    @Before
    fun setup() {
        NeliAiMemoryStore.resetMemoryForTesting()
        ChannelHealthGuardian.resetForTesting()
    }

    @Test
    fun wakeGreetingsAndCommandCatalog_exceedProductionThresholds() {
        assertTrue(NeliAiCommandCatalog.wakeGreetings.size >= 50)
        assertTrue(NeliAiCommandCatalog.totalCommandCount >= 1500)
        assertTrue(NeliAiCommandCatalog.countByCategory(NeliCommandCategory.CHANNEL_AND_MOVIE) >= 100)
        assertTrue(NeliAiCommandCatalog.countByCategory(NeliCommandCategory.APP_AND_PERSONAL) >= 100)
        assertTrue(NeliAiCommandCatalog.countByCategory(NeliCommandCategory.SUGGEST_SEARCH_DOWNLOAD) >= 1000)

        val wake = NeliAiEngine.evaluateLocally("Hey Neli", snapshot())
        assertEquals(NeliAiAction.WakeGreeting, wake.action)
        assertTrue(wake.replySpeech.isNotBlank())
    }

    @Test
    fun voiceChannelCommands_tolerateMisspellingsAndEnforceVipLocks() {
        // Misspelling tolerance: "azm spot 1" -> Azam Sports 1 HD
        val r1 = NeliAiEngine.evaluateLocally("Hey Neli play azm spot 1", snapshot())
        assertTrue(r1.action is NeliAiAction.PlayChannel)
        assertEquals("c2", (r1.action as NeliAiAction.PlayChannel).channel.id)

        // Locked channel on unpaid mobile -> MUST tell user to pay first!
        val unpaidSnap = snapshot(
            isSmartTv = false,
            isPremiumActive = false,
            lockedCheck = { it.id == "c3" }
        )
        val lockedRes = NeliAiEngine.evaluateLocally("Hey Neli open Azam Two", unpaidSnap)
        assertTrue(lockedRes.action is NeliAiAction.LockedChannelNeedsPayment)
        assertTrue(lockedRes.replyText.contains("VIP", ignoreCase = true))

        // Same channel when user HAS paid VIP -> opens immediately!
        val paidSnap = snapshot(
            isSmartTv = false,
            isPremiumActive = true,
            lockedCheck = { it.id == "c3" }
        )
        val paidRes = NeliAiEngine.evaluateLocally("Hey Neli open Azam Two", paidSnap)
        assertTrue(paidRes.action is NeliAiAction.PlayChannel)
        assertEquals("c3", (paidRes.action as NeliAiAction.PlayChannel).channel.id)
    }

    @Test
    fun movieSuggestions_neverFalselyPlayAndListFreshOptions() {
        val res = NeliAiEngine.evaluateLocally("Hey Neli suggest me movies to watch", snapshot())
        assertTrue(res.action is NeliAiAction.SuggestMovies)
        assertFalse(res.replyText.startsWith("Playing", ignoreCase = true))
        val suggested = (res.action as NeliAiAction.SuggestMovies).movies
        assertTrue(suggested.size >= 3)

        // Zero-tick hands-free follow-ups on suggested movies (no checkboxes needed!)
        val pickSecond = NeliAiEngine.evaluateLocally("Hey Neli play number 2", snapshot())
        assertTrue(pickSecond.action is NeliAiAction.OpenMedia)
        assertEquals(suggested[1].id, (pickSecond.action as NeliAiAction.OpenMedia).media.id)

        val dlAll = NeliAiEngine.evaluateLocally("Hey Neli download all", snapshot())
        assertTrue(dlAll.action is NeliAiAction.DownloadSpecificMovies)
        assertEquals(suggested.size, (dlAll.action as NeliAiAction.DownloadSpecificMovies).movies.size)

        val playAll = NeliAiEngine.evaluateLocally("Hey Neli play them", snapshot())
        assertTrue(playAll.action is NeliAiAction.PlayMoviesInSequence)
        assertEquals(suggested.size, (playAll.action as NeliAiAction.PlayMoviesInSequence).movies.size)
    }

    @Test
    fun handsFreeRandomMovie_autoPlaysFreshUnwatchedMovieWithZeroTicking() {
        val snap = snapshot(downloadedIds = setOf("m1", "m2"), watched30Ids = setOf("m3"))
        val res = NeliAiEngine.evaluateLocally("Hey Neli play a random movie", snap)
        assertTrue(res.action is NeliAiAction.OpenMedia)
        val openAction = res.action as NeliAiAction.OpenMedia
        assertTrue(openAction.autoPlay)
        assertFalse(openAction.media.id in setOf("m1", "m2", "m3"))

        val swRes = NeliAiEngine.evaluateLocally("Hey Neli weka movie yoyote", snap)
        assertTrue(swRes.action is NeliAiAction.OpenMedia)
    }

    @Test
    fun smartRandomDownload_excludesAlreadyDownloadedAnd30DayWatchedMovies() {
        NeliAiMemoryStore.recordMovieWatched(null, "m2")
        val snap = snapshot(
            downloadedIds = setOf("m1", "m3"),
            watched30Ids = setOf("m4")
        )
        val res = NeliAiEngine.evaluateLocally("Hey Neli download me 5 random movies", snap)
        assertTrue(res.action is NeliAiAction.DownloadRandomMovies)
        val picked = (res.action as NeliAiAction.DownloadRandomMovies).movies
        // Remaining fresh pool is m5, m6, m7, m8 (4 movies) — NONE of m1, m2, m3, m4 may appear!
        assertEquals(4, picked.size)
        val pickedIds = picked.map { it.id }.toSet()
        assertFalse("m1" in pickedIds)
        assertFalse("m2" in pickedIds)
        assertFalse("m3" in pickedIds)
        assertFalse("m4" in pickedIds)
    }

    @Test
    fun offlineMode_repliesCurrentlyOfflineForOnlineRequestsButKeepsLocalControls() {
        val offlineSnap = snapshot(isOnline = false)
        val onlineReq = NeliAiEngine.evaluateLocally("Hey Neli suggest best movies", offlineSnap)
        assertEquals("OFFLINE_NOTICE", onlineReq.intent)
        assertTrue(onlineReq.replyText.contains("offline", ignoreCase = true))

        val lockReq = NeliAiEngine.evaluateLocally("Hey Neli lock my screen", offlineSnap)
        assertEquals(NeliAiAction.LockDeviceScreen, lockReq.action)
    }

    @Test
    fun brightnessMovieInfoAndPlayerControls_workHandsFree() {
        val brightUp = NeliAiEngine.evaluateLocally("Hey Neli increase brightness", snapshot(sampleChannels[0]))
        assertEquals(NeliAiAction.AdjustBrightness(NeliAiBrightnessDirection.UP), brightUp.action)

        val info = NeliAiEngine.evaluateLocally("Hey Neli tell me about Mission Zanzibar 2026", snapshot())
        assertTrue(info.action is NeliAiAction.ShowMovieInfo)
        assertEquals("m1", (info.action as NeliAiAction.ShowMovieInfo).media.id)

        val nextRes = NeliAiEngine.evaluateLocally("Channel inayofuata", snapshot(sampleChannels[0]))
        assertEquals(NeliAiAction.SwitchNextChannel, nextRes.action)

        val qualityRes = NeliAiEngine.evaluateLocally("Weka 480p", snapshot(sampleChannels[0]))
        assertEquals(NeliAiAction.SetQuality(NetworkQualityMode.STANDARD_480P), qualityRes.action)

        val volRes = NeliAiEngine.evaluateLocally("Increase voice", snapshot(sampleChannels[0]))
        assertEquals(NeliAiAction.AdjustVolume(NeliAiVolumeDirection.UP), volRes.action)
    }

    @Test
    fun channelHealthGuardian_triesDistinctBackupBeforeProxyingDeadPrimary() {
        val ch = sampleChannels[0]
        val next = ChannelHealthGuardian.getNextFailoverCandidate(
            channel = ch,
            failedUrl = ch.streamUrl,
            triedUrls = setOf(ch.streamUrl),
            originalPrimaryUrl = ch.streamUrl,
            originalBackupUrl = ch.backupStreamUrl
        )
        assertEquals("https://backup.nelitv.online/stream/azam1_b.m3u8", next)

        ChannelHealthGuardian.markStreamHealthy(ch.id, next!!)
        assertEquals(next, ChannelHealthGuardian.resolvePreferredStreamUrl(ch))
    }

    @Test
    fun neliAiCenterContract_buildsPayloadAndParsesSpeechTextEnAndActions() {
        val tvSnap = snapshot(isSmartTv = true)
        val payload = NeliAiEngine.buildNeliAiCenterPayload(
            query = "Play Azam Sports 1",
            inputMode = "voice",
            snapshot = tvSnap
        )
        assertEquals("nelitv-tv", payload.getString("app_id"))
        assertEquals("android-tv", payload.getString("platform"))
        assertEquals("voice", payload.getString("input_mode"))

        val serverJson = """
            {
              "intent": "PLAY_CHANNEL",
              "confidence": 0.98,
              "speech_text_en": "Opening Azam Sports 1 HD right now!",
              "action": {
                "type": "PLAY_CHANNEL",
                "channel_name": "Azam Sports 1 HD"
              }
            }
        """.trimIndent()
        val parsed = NeliAiEngine.parseNeliAiCenterResponse(serverJson, snapshot())
        assertNotNull(parsed)
        assertEquals("Opening Azam Sports 1 HD right now!", parsed!!.replySpeech)
        assertTrue(parsed.action is NeliAiAction.PlayChannel)

        // Verify Free 10-14 yr Female English Voice tuning constants
        assertEquals(1.28f, com.example.ai.NeliAiVoiceController.NELI_VOICE_PITCH)
        assertEquals(1.04f, com.example.ai.NeliAiVoiceController.NELI_VOICE_SPEECH_RATE)
        assertEquals("neli", com.example.ai.NeliAiVoiceController.NELI_UTTERANCE_ID)
    }
}
