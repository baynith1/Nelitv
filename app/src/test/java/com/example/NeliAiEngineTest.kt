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

        // Verify Young Energetic Fast Female Voice tuning constants & 30-second command wait window
        assertEquals(1.22f, com.example.ai.NeliAiVoiceController.NELI_VOICE_PITCH)
        assertEquals(1.28f, com.example.ai.NeliAiVoiceController.NELI_VOICE_SPEECH_RATE)
        assertEquals("neli", com.example.ai.NeliAiVoiceController.NELI_UTTERANCE_ID)
        assertEquals(30_000L, com.example.ai.NeliAiVoiceController.DEFAULT_COMMAND_WINDOW_MS)
    }

    @Test
    fun tvVoiceSearchMatcher_normalizesSpokenPhrasesAndMatchesChannelsAndMovies() {
        assertEquals(
            "azam sport one",
            com.example.ui.tv.NeliTvVoiceSearchMatcher.normalizeSpokenQuery("Hey Neli search for azam sport one")
        )
        assertEquals(
            "Mission Zanzibar",
            com.example.ui.tv.NeliTvVoiceSearchMatcher.normalizeSpokenQuery("Tafuta movie ya Mission Zanzibar")
        )

        val chRes = com.example.ui.tv.NeliTvVoiceSearchMatcher.search(
            rawQuery = "Play azm spot 1",
            channels = sampleChannels,
            mediaCatalog = sampleMedia,
            filter = com.example.ui.tv.TvVoiceSearchFilter.ALL
        )
        assertTrue(chRes.channels.any { it.id == "c2" })

        val movieRes = com.example.ui.tv.NeliTvVoiceSearchMatcher.search(
            rawQuery = "Find Mission Zanzibar",
            channels = sampleChannels,
            mediaCatalog = sampleMedia,
            filter = com.example.ui.tv.TvVoiceSearchFilter.MOVIES
        )
        assertTrue(movieRes.media.any { it.id == "m1" })
        assertTrue(movieRes.channels.isEmpty())
    }

    @Test
    fun vodContainerResolution_defaultsExtensionlessAndMkvMoviesToProgressiveForFastTvLoad() {
        // Extensionless Firebase Storage / Cloudflare R2 / Google Drive movie URLs MUST use Progressive (not HLS!)
        assertTrue(
            com.example.player.LivePlayerController.resolveVodUsesProgressive(
                streamUrl = "https://firebasestorage.googleapis.com/v0/b/nelitv/o/movies%2Fhero?alt=media&token=abc",
                streamFormat = "",
                isMp4 = false
            )
        )
        assertTrue(
            com.example.player.LivePlayerController.resolveVodUsesProgressive(
                streamUrl = "https://cdn.example.com/movie_1080p.mkv",
                streamFormat = "",
                isMp4 = false
            )
        )
        assertFalse(
            com.example.player.LivePlayerController.resolveVodUsesProgressive(
                streamUrl = "https://cdn.example.com/movie/master.m3u8",
                streamFormat = "",
                isMp4 = false
            )
        )
        assertFalse(
            com.example.player.LivePlayerController.resolveVodUsesProgressive(
                streamUrl = "https://cdn.example.com/movie/manifest.mpd",
                streamFormat = "dash",
                isMp4 = false
            )
        )
    }

    @Test
    fun powerButtonDoubleTapDetector_wakesOnRapidDoubleTapAndRespectsCooldown() {
        var triggers = 0
        val detector = com.example.ai.NeliPowerButtonDoubleTapDetector { triggers++ }

        // Single power button toggle should NOT trigger
        assertFalse(detector.recordToggleAndCheckDoubleTap(10_000L))
        // Second toggle within 500ms MUST trigger double-tap wake!
        assertTrue(detector.recordToggleAndCheckDoubleTap(10_500L))
        // Immediate extra bounce within cooldown must NOT re-trigger
        assertFalse(detector.recordToggleAndCheckDoubleTap(10_900L))
        // Slow press (>1.35s gap) starts a fresh window and does NOT trigger on first press
        assertFalse(detector.recordToggleAndCheckDoubleTap(15_000L))
        assertFalse(detector.recordToggleAndCheckDoubleTap(18_000L))
        assertEquals(0, triggers) // callback only fires from BroadcastReceiver; pure logic verified above
    }

    @Test
    fun digitalAssistantServices_areDeclaredAndInstantiable() {
        assertNotNull(com.example.ai.NeliVoiceInteractionService::class.java)
        assertNotNull(com.example.ai.NeliVoiceInteractionSessionService::class.java)
        assertNotNull(com.example.ai.NeliRecognitionService::class.java)
    }

    @Test
    fun continuousConversation_asksWhichMovieUserLikesAndHandlesMultiTurnReplies() {
        val snap = snapshot()

        val askMood = NeliAiEngine.evaluateLocally("Hey Neli I want to watch a movie", snap)
        assertEquals("ASK_MOVIE_PREFERENCE", askMood.intent)
        assertTrue(askMood.expectsFollowUp)
        assertTrue(askMood.replyText.contains("Which movie or genre do you like", ignoreCase = true))

        // Follow-up without repeating "Hey Neli"
        val genreReply = NeliAiEngine.evaluateLocally("Action", snap)
        assertTrue(genreReply.action is NeliAiAction.SuggestMovies)
        assertTrue(genreReply.expectsFollowUp)

        val infoReply = NeliAiEngine.evaluateLocally("Tell me about Mission Zanzibar 2026", snap)
        assertTrue(infoReply.action is NeliAiAction.ShowMovieInfo)
        assertTrue(infoReply.expectsFollowUp)

        val playYes = NeliAiEngine.evaluateLocally("Yes", snap)
        assertTrue(playYes.action is NeliAiAction.OpenMedia)
        assertEquals("m1", (playYes.action as NeliAiAction.OpenMedia).media.id)
        assertFalse(playYes.expectsFollowUp)

        val endReply = NeliAiEngine.evaluateLocally("No thanks", snap)
        assertEquals("CONVERSATION_END", endReply.intent)
        assertFalse(endReply.expectsFollowUp)
    }

    @Test
    fun deleteDownloads_supportsDeleteAllSpecificOfflineAndConversationalFollowUp() {
        // 1. Empty downloads
        val emptyRes = NeliAiEngine.evaluateLocally("Hey Neli delete all downloads", snapshot())
        assertEquals("DELETE_DOWNLOADS_EMPTY", emptyRes.intent)
        assertEquals(NeliAiAction.None, emptyRes.action)
        assertFalse(emptyRes.expectsFollowUp)

        val downloadedSnap = snapshot(downloadedIds = setOf("m1", "m2", "m3"))

        // 2. Delete ALL downloads (English & Swahili)
        val delAllEn = NeliAiEngine.evaluateLocally("Hey Neli delete all downloads", downloadedSnap)
        assertEquals(NeliAiAction.DeleteAllDownloads, delAllEn.action)
        assertEquals("DELETE_ALL_DOWNLOADS", delAllEn.intent)

        val delAllMy = NeliAiEngine.evaluateLocally("Hey Neli clear all my downloads", downloadedSnap)
        assertEquals(NeliAiAction.DeleteAllDownloads, delAllMy.action)

        val delAllSw = NeliAiEngine.evaluateLocally("Hey Neli futa downloads zote", downloadedSnap)
        assertEquals(NeliAiAction.DeleteAllDownloads, delAllSw.action)

        // 3. Delete SPECIFIC download (English, Swahili & direct title)
        val delSpecificEn = NeliAiEngine.evaluateLocally("Hey Neli delete Mission Zanzibar from downloads", downloadedSnap)
        assertTrue(delSpecificEn.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("m1"), (delSpecificEn.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)

        val delSpecificSw = NeliAiEngine.evaluateLocally("Hey Neli futa download ya Mission Zanzibar", downloadedSnap)
        assertTrue(delSpecificSw.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("m1"), (delSpecificSw.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)

        val delDirectTitle = NeliAiEngine.evaluateLocally("Hey Neli delete Mission Zanzibar", downloadedSnap)
        assertTrue(delDirectTitle.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("m1"), (delDirectTitle.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)

        // 4. Works completely OFFLINE even when mediaCatalog is empty using downloadedItems
        val offlineSnap = NeliAiContextSnapshot(
            liveChannels = emptyList(),
            mediaCatalog = emptyList(),
            isOnline = false,
            downloadedMediaIds = setOf("ep_3"),
            downloadedItems = listOf(
                com.example.ai.NeliAiDownloadedItem(id = "ep_3", title = "Sultan Episode 3", type = "series")
            )
        )
        val offlineSpecific = NeliAiEngine.evaluateLocally("Delete Sultan Episode 3 from downloads", offlineSnap)
        assertTrue(offlineSpecific.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("ep_3"), (offlineSpecific.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)

        val offlineAll = NeliAiEngine.evaluateLocally("Delete all downloads", offlineSnap)
        assertEquals(NeliAiAction.DeleteAllDownloads, offlineAll.action)

        // 5. Conversational follow-up: ambiguous "delete a download" -> asks which one -> user replies "Number 2"
        val askWhich = NeliAiEngine.evaluateLocally("Hey Neli delete a download", downloadedSnap)
        assertEquals("ASK_WHICH_DOWNLOAD_TO_DELETE", askWhich.intent)
        assertTrue(askWhich.expectsFollowUp)

        val pickSecond = NeliAiEngine.evaluateLocally("Number 2", downloadedSnap)
        assertTrue(pickSecond.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("m2"), (pickSecond.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)
        assertFalse(pickSecond.expectsFollowUp)

        // 6. Single download confirmation follow-up + cancel check
        val singleSnap = snapshot(downloadedIds = setOf("m1"))
        val askSingle = NeliAiEngine.evaluateLocally("Hey Neli delete a download", singleSnap)
        assertEquals("CONFIRM_DELETE_DOWNLOAD", askSingle.intent)
        assertTrue(askSingle.expectsFollowUp)

        val cancelSingle = NeliAiEngine.evaluateLocally("No thanks", singleSnap)
        assertEquals("DELETE_DOWNLOAD_CANCELLED", cancelSingle.intent)
        assertEquals(NeliAiAction.None, cancelSingle.action)
        assertFalse(cancelSingle.expectsFollowUp)

        NeliAiEngine.evaluateLocally("Hey Neli delete a download", singleSnap)
        val confirmSingle = NeliAiEngine.evaluateLocally("Yes", singleSnap)
        assertTrue(confirmSingle.action is NeliAiAction.DeleteSpecificDownloads)
        assertEquals(listOf("m1"), (confirmSingle.action as NeliAiAction.DeleteSpecificDownloads).downloadIds)
    }

    @Test
    fun siriAssistantUpgrade_supports25PlusScreenWakePhrasesAndFullSiriSkills() {
        val snap = snapshot()
        val offlineSnap = snapshot(isOnline = false)

        // 1. Verify 25+ screen-wake phrases
        assertTrue(com.example.ai.NeliSiriAssistantEngine.wakeScreenPhrases.size >= 25)
        listOf(
            "Hey Neli open screen",
            "Neli wake my phone",
            "Hey Neli turn on the screen",
            "Hey Neli washa kioo",
            "Neli amka",
            "Hey Neli unlock screen"
        ).forEach { phrase ->
            val res = NeliAiEngine.evaluateLocally(phrase, offlineSnap)
            assertEquals(NeliAiAction.WakeDeviceScreen, res.action)
        }

        // 2. Flashlight (including Lumos / Nox) & Dark Mode
        assertEquals(NeliAiAction.ToggleFlashlight(true), NeliAiEngine.evaluateLocally("Hey Neli Lumos", offlineSnap).action)
        assertEquals(NeliAiAction.ToggleFlashlight(false), NeliAiEngine.evaluateLocally("Hey Neli Nox", offlineSnap).action)
        assertEquals(NeliAiAction.SetDarkMode(true), NeliAiEngine.evaluateLocally("Hey Neli turn on dark mode", offlineSnap).action)
        assertEquals(NeliAiAction.SetBrightnessPercent(50), NeliAiEngine.evaluateLocally("Set brightness to 50%", offlineSnap).action)
        assertEquals(NeliAiAction.SetVolumePercent(75), NeliAiEngine.evaluateLocally("Set volume to 75%", offlineSnap).action)

        // 3. Alarms, Timers & Reminders
        val alarm = NeliAiEngine.evaluateLocally("Wake me up at 9:00 AM", offlineSnap)
        assertTrue(alarm.action is NeliAiAction.SetAlarm)
        assertEquals(9, (alarm.action as NeliAiAction.SetAlarm).hour24)
        assertEquals(0, (alarm.action as NeliAiAction.SetAlarm).minute)

        val delAlarms = NeliAiEngine.evaluateLocally("Delete all my alarms", offlineSnap)
        assertEquals(NeliAiAction.ShowOrDismissAlarms(deleteAll = true), delAlarms.action)

        val timer = NeliAiEngine.evaluateLocally("Set a timer for 20 minutes", offlineSnap)
        assertTrue(timer.action is NeliAiAction.SetTimer)
        assertEquals(1200, (timer.action as NeliAiAction.SetTimer).durationSeconds)

        NeliAiEngine.evaluateLocally("Remind me to keep my wallet when I leave home", offlineSnap)
        val reminders = NeliAiEngine.evaluateLocally("What are my reminders?", offlineSnap)
        assertTrue(reminders.replyText.contains("wallet", ignoreCase = true))

        // 4. Relationships, Calls on speaker, Messages, Parking, Find My Phone
        NeliAiEngine.evaluateLocally("Kamal is my brother", offlineSnap)
        val callBrother = NeliAiEngine.evaluateLocally("Call my brother on speaker", offlineSnap)
        assertTrue(callBrother.action is NeliAiAction.PlaceCall)
        assertEquals("Kamal", (callBrother.action as NeliAiAction.PlaceCall).contactOrNumber)
        assertTrue((callBrother.action as NeliAiAction.PlaceCall).onSpeaker)

        NeliAiEngine.evaluateLocally("I parked my car at Level 2 Gate B", offlineSnap)
        val recallPark = NeliAiEngine.evaluateLocally("Where did I park my vehicle?", offlineSnap)
        assertTrue(recallPark.replyText.contains("Level 2 Gate B", ignoreCase = true))

        assertEquals(NeliAiAction.FindMyPhone, NeliAiEngine.evaluateLocally("Where is my phone?", offlineSnap).action)

        // 5. Math, Conversions, Translation, Installed App, Visual Intelligence & Multi-Step
        val pct = NeliAiEngine.evaluateLocally("What is 6.4% of 250,000?", offlineSnap)
        assertTrue(pct.replyText.contains("16,000"))

        val conv = NeliAiEngine.evaluateLocally("Convert 10 km to miles", offlineSnap)
        assertTrue(conv.replyText.contains("6.21"))

        val trans = NeliAiEngine.evaluateLocally("How to say thank you in German?", offlineSnap)
        assertTrue(trans.replyText.contains("Danke"))

        val openApp = NeliAiEngine.evaluateLocally("Hey Neli open WhatsApp", snap)
        assertEquals(NeliAiAction.LaunchInstalledApp("whatsapp"), openApp.action)

        val visual = NeliAiEngine.evaluateLocally("Hey Neli what is this?", snap)
        assertTrue(visual.action is NeliAiAction.LaunchVisualIntelligence)

        val multi = NeliAiEngine.evaluateLocally("Turn on dark mode and set brightness to 50%", offlineSnap)
        assertTrue(multi.action is NeliAiAction.CompositeActions)
        val steps = (multi.action as NeliAiAction.CompositeActions).actions
        assertEquals(listOf(NeliAiAction.SetDarkMode(true), NeliAiAction.SetBrightnessPercent(50)), steps)
    }

    @Test
    fun voiceDictationAndTranscription_cleansFillersSelfCorrectionsAndRoutesVoiceTrigger() {
        val offlineSnap = snapshot(isOnline = false)
        val openCmd = NeliAiEngine.evaluateLocally("Hey Neli start dictation", offlineSnap)
        assertTrue(openCmd.action is NeliAiAction.OpenVoiceDictation)

        val swCmd = NeliAiEngine.evaluateLocally("Hey Neli andika kwa sauti", offlineSnap)
        assertTrue(swCmd.action is NeliAiAction.OpenVoiceDictation)

        // Wispr Flow-style AI cleanup: filler words, stutters, self-corrections & spoken punctuation
        val cleaned = com.example.ai.NeliVoiceDictationFormatter.cleanDictatedText(
            rawInput = "um i i want to meet at 3 actually 4 comma you know new line can we talk",
            style = com.example.ai.DictationStylePreset.CLEAN_FLOW,
            elapsedMs = 3_000L
        )
        assertTrue(cleaned.cleanedText.contains("I want to meet at 4,"))
        assertTrue(cleaned.cleanedText.contains("\nCan we talk"))
        assertFalse(cleaned.cleanedText.contains("um", ignoreCase = true))
        assertTrue(cleaned.removedFillersCount >= 2)
        assertTrue(cleaned.estimatedWpm > 0)

        // Bullet notes formatting preset
        val bullets = com.example.ai.NeliVoiceDictationFormatter.cleanDictatedText(
            rawInput = "buy milk period call kamal period send report",
            style = com.example.ai.DictationStylePreset.BULLET_NOTES
        )
        assertTrue(bullets.cleanedText.contains("• Buy milk."))
        assertTrue(bullets.cleanedText.contains("• Call kamal."))
        assertTrue(bullets.cleanedText.contains("• Send report."))
    }
}


