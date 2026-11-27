package com.example.ai

import android.content.Context
import com.example.data.ChannelHealthGuardian
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.player.NetworkQualityMode
import com.example.ui.components.BottomNavTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Production Neli AI Intelligence Engine (Zero Paid External LLM APIs).
 *
 * Combines:
 * 1. Neli AI Center (`POST https://nelitv.online/v1/neli-ai/command`) when online & configured
 * 2. Direct real-time understanding of NeliTv app data (Live TV, Firebase Movies/Series, Downloads, 30-day Watch History, VIP Status)
 * 3. 55+ wake-word greetings ("Hey Neli" / "Neli") + 1,500+ command grammar + fuzzy closest-command matcher
 * 4. Offline awareness, Paid/Locked VIP channel enforcement, Non-duplicate random movie batch downloads,
 *    Outside-app movie suggestions & info popups, Brightness/Volume/Quality control, and Screen Lock.
 */
object NeliAiEngine {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private val wakePrefixRegex = Regex(
        "^(hey|hi|hello|halo|habari|mambo|ok|okay|sawa|tafadhali|please|naomba)?\\s*(neli\\s*tv\\s*ai|neli\\s*ai|nelly\\s*ai|neli|nelly|neil|nely)[,.:!?\\s]*",
        RegexOption.IGNORE_CASE
    )

    private val wakeOnlyRegex = Regex(
        "^(hey|hi|hello|halo|ok|okay)?\\s*(neli\\s*tv\\s*ai|neli\\s*ai|nelly\\s*ai|neli|nelly|nely)[,.:!?\\s]*$",
        RegexOption.IGNORE_CASE
    )

    private val builtInChannelSynonyms: Map<String, List<String>> = mapOf(
        "azam one" to listOf("azam 1", "azam moja", "azamu moja", "azm 1", "azm one", "azam one", "asam 1", "asam one"),
        "azam two" to listOf("azam 2", "azam mbili", "azamu mbili", "azm 2", "azm two", "azam two", "azam to", "asam 2", "asam two"),
        "sinema zetu" to listOf("sinema zetu", "cinema zetu", "sinema", "bongo movie tv", "cinema yetu"),
        "azam sports 1" to listOf("azam sport 1", "azam sports 1", "azam spot 1", "azm sport 1", "spoti moja", "sport moja", "mpira 1", "nbc premier"),
        "azam sports 2" to listOf("azam sport 2", "azam sports 2", "azam spot 2", "azm sport 2", "spoti mbili", "sport mbili"),
        "azam sports 3" to listOf("azam sport 3", "azam sports 3", "azam spot 3", "spoti tatu", "sport tatu"),
        "azam sports 4" to listOf("azam sport 4", "azam sports 4", "azam spot 4", "spoti nne", "sport nne"),
        "tom and jerry" to listOf("tom and jerry", "tom end jeri", "tom na jerry", "katuni ya paka"),
        "wasafi" to listOf("wasafi", "wasafi tv", "wcb"),
        "utv" to listOf("utv", "u tv", "habari azam"),
        "zbc" to listOf("zbc", "zbc 1", "zanzibar"),
        "zbc2" to listOf("zbc2", "zbc 2", "zbc mbili"),
        "wwe" to listOf("wwe", "mieleka", "wrestling"),
        "super sport" to listOf("super sport", "supersport", "baynith 1"),
        "epl league tv" to listOf("epl", "premier league", "ligi kuu uingereza", "baynith 2")
    )

    private val genreKeywords: Map<String, List<String>> = mapOf(
        "sports" to listOf("mpira", "soka", "sport", "sports", "football", "ligi", "mechi", "michezo", "mieleka", "wrestling"),
        "kids" to listOf("katuni", "watoto", "kids", "cartoon", "animation", "tom and jerry", "mr bean"),
        "movies" to listOf("movie", "movies", "filamu", "sinema", "cinema", "bongo", "tafsiri", "dj", "swahili"),
        "music" to listOf("muziki", "music", "nyimbo", "burudani", "wasafi", "trace"),
        "news" to listOf("habari", "news", "taarifa", "matangazo", "siasa")
    )

    private val vodGenreSynonyms: Map<String, List<String>> = mapOf(
        "action" to listOf("action", "mapigano", "vita", "akishoni", "risasi", "ngumi"),
        "comedy" to listOf("comedy", "vichekesho", "kuchekesha", "komedi", "funny"),
        "horror" to listOf("horror", "kutisha", "mizimu", "wachawi", "scary"),
        "romance" to listOf("romance", "mapenzi", "love", "mahaba"),
        "drama" to listOf("drama", "tamthilia", "maisha"),
        "sci-fi" to listOf("sci-fi", "scifi", "sci fi", "sayansi", "space", "future"),
        "animation" to listOf("animation", "katuni", "cartoon", "watoto", "kids"),
        "thriller" to listOf("thriller", "suspense", "upelelezi", "mystery")
    )

    fun containsWakeWord(transcript: String): Boolean {
        val norm = normalizeText(transcript)
        return norm.startsWith("hey neli") ||
            norm.startsWith("hey nelly") ||
            norm.startsWith("hey nely") ||
            norm.startsWith("neli") ||
            norm.startsWith("nelly") ||
            norm.contains(" hey neli") ||
            norm.contains(" hey nelly") ||
            norm.contains(" neli ")
    }

    /**
     * Extracts the command after a wake word ("Hey Neli play Azam Two" -> "play Azam Two").
     * Returns "" if the user only said the wake word ("Hey Neli" / "Neli").
     */
    fun stripWakeWord(rawTranscript: String): String {
        val trimmed = rawTranscript.trim()
        if (wakeOnlyRegex.matches(trimmed)) return ""
        return trimmed.replace(wakePrefixRegex, "").trim()
    }

    /**
     * Processes a user command via Neli AI Center when online & configured (falling back seamlessly
     * to our rich On-Device Neli AI Brain that reads real NeliTv data).
     */
    suspend fun processCommand(
        rawQuery: String,
        snapshot: NeliAiContextSnapshot,
        context: Context? = null,
        inputMode: String = "voice"
    ): NeliAiCommandResponse {
        NeliAiMemoryStore.recordInteraction(context)

        // Handle pure wake word or offline / local-critical actions immediately without waiting for network
        val cleaned = stripWakeWord(rawQuery)
        if (rawQuery.isNotBlank() && cleaned.isBlank()) {
            return buildWakeGreetingResponse(snapshot)
        }

        val lower = normalizeText(cleaned)

        // Always handle local device / offline / smart-download / suggestion / locked-channel rules locally first
        // so app data (30-day watch history, Room downloads, VIP locks, brightness, screen lock) is 100% accurate.
        val localResult = evaluateLocally(rawQuery, snapshot, context)
        if (localResult.confidence >= 0.80f || !snapshot.isOnline) {
            return localResult
        }

        // For open-ended questions when online & Neli AI Center API key is configured, ask Neli AI Center
        val remoteUrl = NeliAiMemoryStore.remoteEndpointUrl.value
        val apiKey = NeliAiMemoryStore.resolveApiKey()
        if (snapshot.isOnline && remoteUrl.startsWith("https://", ignoreCase = true) && apiKey.isNotBlank()) {
            val remote = tryQueryCustomRemoteAi(
                endpointUrl = remoteUrl,
                apiKey = apiKey,
                query = rawQuery,
                inputMode = inputMode,
                snapshot = snapshot
            )
            if (remote != null) return enforceVipLockOnResponse(remote, snapshot)
        }

        return localResult
    }

    /**
     * Deterministic, production-grade On-Device Neli AI Brain reading real NeliTv data.
     */
    fun evaluateLocally(
        rawQuery: String,
        snapshot: NeliAiContextSnapshot,
        context: Context? = null,
        allowClosestFallback: Boolean = true
    ): NeliAiCommandResponse {
        val original = rawQuery.trim()
        if (original.isBlank() || wakeOnlyRegex.matches(original)) {
            return buildWakeGreetingResponse(snapshot)
        }

        val cleaned = stripWakeWord(original).ifBlank { original }
        val lower = normalizeText(cleaned)

        // 0. Always-offline-capable local device controls (lock screen, brightness, volume, open downloads, open app)
        if (isLockScreenIntent(lower)) {
            return NeliAiCommandResponse(
                intent = "LOCK_SCREEN",
                confidence = 0.99f,
                replyText = "Locking your screen now.",
                replySpeech = "Locking your screen now.",
                sourceTag = "Neli Device Control",
                action = NeliAiAction.LockDeviceScreen
            )
        }

        if (isOpenNeliTvAppOnlyIntent(lower)) {
            return NeliAiCommandResponse(
                intent = "OPEN_NELITV_APP",
                confidence = 0.98f,
                replyText = "Opening NeliTv for you now!",
                replySpeech = "Opening NeliTv now.",
                sourceTag = "Neli App Launcher",
                action = NeliAiAction.OpenNeliTvApp
            )
        }

        detectBrightnessIntent(lower)?.let { return it }
        detectPlayerControlIntent(lower, snapshot)?.let { return it }
        detectNavigationIntent(lower)?.let { nav ->
            if (nav.intent == "NAV_DOWNLOADS" || snapshot.isOnline) return nav
        }

        // 1. Offline Gate: If user is offline and asks an online request/question -> "I'm currently offline"
        if (!snapshot.isOnline) {
            return NeliAiCommandResponse(
                intent = "OFFLINE_NOTICE",
                confidence = 0.99f,
                replyText = "I'm currently offline. Please connect to the internet to stream channels, search movies, or download.",
                replySpeech = "I'm currently offline. Please check your internet connection.",
                sourceTag = "Neli Offline Guard",
                action = NeliAiAction.None,
                quickReplies = listOf("Open downloads", "Lock my screen", "Brightness down")
            )
        }

        // 2. Teach / Train Neli AI custom alias
        parseTeachCommand(cleaned)?.let { (trigger, target) ->
            val matchedChannel = findBestChannelMatches(target, snapshot.liveChannels, 1).firstOrNull()?.first
            val resolvedTarget = matchedChannel?.name ?: target
            NeliAiMemoryStore.teachAlias(context, trigger, resolvedTarget)
            val reply = "Got it! Next time you say \"$trigger\", I will open $resolvedTarget right away."
            return NeliAiCommandResponse(
                intent = "TEACH_ALIAS",
                confidence = 0.99f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "Neli Memory",
                cards = matchedChannel?.let { listOf(NeliAiCardItem.ChannelCard(it)) } ?: emptyList(),
                quickReplies = listOf("Open $trigger", "Suggest movies", "What's on now?")
            )
        }

        // 3. Check user-taught custom aliases
        val learnedAliases = NeliAiMemoryStore.learnedAliases.value
        val matchedAlias = learnedAliases.entries.firstOrNull { (trigger, _) ->
            val normTrigger = normalizeText(trigger)
            lower == normTrigger ||
                lower.contains("weka $normTrigger") ||
                lower.contains("fungua $normTrigger") ||
                lower.contains("cheza $normTrigger") ||
                lower.contains("play $normTrigger") ||
                lower.contains("open $normTrigger")
        }
        if (matchedAlias != null) {
            val channelHit = findBestChannelMatches(matchedAlias.value, snapshot.liveChannels, 3)
            if (channelHit.isNotEmpty()) {
                return buildChannelPlayResponse(
                    top = channelHit.first().first,
                    alternatives = channelHit.map { it.first },
                    confidence = 0.98f,
                    shouldAutoPlay = true,
                    snapshot = snapshot,
                    context = context,
                    sourceTag = "Neli Memory + Live API"
                )
            }
        }

        // 4. Smart Batch / Random / Specific Movie Downloads ("download me 5 random movies", "download all", "download <movie>")
        if (isDownloadIntent(lower)) {
            return buildSmartDownloadResponse(cleaned, lower, snapshot, context)
        }

        // 5. Zero-Tick Hands-Free Follow-Up on Suggested Movies ("play them", "play all", "play number 1..5", "open first")
        detectRecentSuggestionFollowUp(lower, snapshot)?.let { return it }

        // 6. Hands-Free Auto-Play Random Movie ("play a random movie", "random movie", "weka movie yoyote") — NO ticking required!
        if (isAutoPlayRandomMovieIntent(lower)) {
            return buildAutoPlayRandomMovieResponse(lower, snapshot, context)
        }

        // 7. Movie Info & Search (works inside and outside the app!)
        if (isMovieInfoOrSearchIntent(lower)) {
            val infoHit = buildMovieInfoResponse(cleaned, lower, snapshot)
            if (infoHit != null) return infoHit
        }

        // 8. Movie Suggestions & Recommendations (lists fresh movies with zero-tick voice & 1-tap actions!)
        if (isMovieSuggestionIntent(lower)) {
            return buildMovieSuggestionResponse(lower, snapshot, context)
        }

        // 8. "Change to best movie" / "Play best movie"
        if (containsAny(lower, listOf("change to best movie", "play best movie", "put best movie", "weka movie bora"))) {
            val bestMovie = pickFreshRandomMovies(snapshot, count = 1, preferTopRated = true).firstOrNull()
                ?: snapshot.mediaCatalog.firstOrNull { !it.isAdult && it.streamUrl.isNotBlank() }
            if (bestMovie != null) {
                return NeliAiCommandResponse(
                    intent = "PLAY_BEST_MOVIE",
                    confidence = 0.95f,
                    replyText = "Switching to one of our top movies: \"${bestMovie.title}\" (${bestMovie.primaryGenre})!",
                    replySpeech = "Switching to ${bestMovie.title}.",
                    sourceTag = "Neli Smart Player",
                    action = NeliAiAction.OpenMedia(bestMovie, autoPlay = true),
                    cards = listOf(NeliAiCardItem.MediaCard(bestMovie))
                )
            }
        }

        // 9. Identity, Personal Data, App Stats, Channel Health, & Conversational FAQ
        detectMetaOrFaqIntent(lower, snapshot)?.let { return it }

        // 10. EPG / Live Schedule
        if (containsAny(lower, listOf("sasa hivi", "kipindi gani", "kinacheza nini", "whats on", "what is on", "epg", "ratiba", "kinachofuata", "live now"))) {
            return buildEpgNowPlayingResponse(lower, snapshot)
        }

        // 11. General Recommendation ("Nipendekezee", "Recommend something", "What should I watch")
        if (containsAny(lower, listOf("nipendekezee", "pendekeza", "niangalie nini", "recommend", "suggest", "suggestion", "kizuri leo", "cha kuangalia", "bored", "what to watch"))) {
            return buildMovieSuggestionResponse(lower, snapshot, context)
        }

        // 12. Explicit Movie / Series / Genre VOD browse or direct open
        val asksForMovieOrSeries = containsAny(
            lower,
            listOf("movie", "movies", "filamu", "series", "tamthilia", "tafsiri", "kutafsiriwa", "dj", "swahili movie", "episode", "2026", "2025")
        ) || vodGenreSynonyms.values.flatten().any { lower.contains(it) }

        val asksForLiveChannel = containsAny(
            lower,
            listOf("channel", "chaneli", "tv", "live", "azam", "azm", "asam", "sinema zetu", "wasafi", "utv", "zbc", "tbc", "wwe", "supersport", "epl", "bein", "espn", "tom and jerry")
        )

        if (asksForMovieOrSeries && !asksForLiveChannel) {
            return buildMovieOrSeriesResponse(cleaned, lower, snapshot, context)
        }

        // 13. Direct Channel vs Movie Title Matching (with strong fuzzy misspelling tolerance!)
        val channelMatches = findBestChannelMatches(cleaned, snapshot.liveChannels, limit = 5)
        val bestChannelScore = channelMatches.firstOrNull()?.second ?: 0

        val movieMatches = findBestMediaMatches(cleaned, snapshot.mediaCatalog, limit = 5)
        val bestMovieScore = movieMatches.firstOrNull()?.second ?: 0

        if (bestChannelScore >= 42 && bestChannelScore >= bestMovieScore) {
            val shouldAutoPlay = containsAny(lower, listOf("weka", "fungua", "cheza", "play", "open", "switch", "badili", "nataka", "niwekee", "watch", "change to")) ||
                bestChannelScore >= 72
            return buildChannelPlayResponse(
                top = channelMatches.first().first,
                alternatives = channelMatches.map { it.first },
                confidence = (bestChannelScore / 100f).coerceIn(0.65f, 0.99f),
                shouldAutoPlay = shouldAutoPlay,
                snapshot = snapshot,
                context = context,
                sourceTag = "NeliTV Live API (${snapshot.liveChannels.size} ch)"
            )
        }

        // 14. Category browse for Live TV
        val matchedCategory = detectLiveCategoryFromText(lower)
        if (matchedCategory != null) {
            NeliAiMemoryStore.recordGenreInterest(context, matchedCategory)
            val catChannels = filterChannelsByCategory(snapshot.liveChannels, matchedCategory).take(5)
            if (catChannels.isNotEmpty()) {
                val top = catChannels.first()
                val shouldPlayFirst = containsAny(lower, listOf("weka", "cheza", "play", "fungua", "open"))
                if (shouldPlayFirst) {
                    return buildChannelPlayResponse(
                        top = top,
                        alternatives = catChannels,
                        confidence = 0.88f,
                        shouldAutoPlay = true,
                        snapshot = snapshot,
                        context = context,
                        sourceTag = "NeliTV Live API"
                    )
                }
                val reply = "Here are top $matchedCategory channels on NeliTv. Say \"Open ${top.name}\" to watch!"
                return NeliAiCommandResponse(
                    intent = "CATEGORY_LIVE_TV",
                    confidence = 0.88f,
                    replyText = reply,
                    replySpeech = reply,
                    sourceTag = "NeliTV Live API",
                    action = NeliAiAction.FilterLiveCategory(matchedCategory),
                    cards = catChannels.map { NeliAiCardItem.ChannelCard(it) },
                    quickReplies = listOf("Open ${top.name}", "Suggest movies", "What's on now?")
                )
            }
        }

        // 15. Movie/Series fallback if matched by title/synopsis
        if (bestMovieScore >= 38) {
            return buildMovieMatchReply(movieMatches, lower, snapshot)
        }

        // 16. Greetings
        if (containsAny(lower, listOf("mambo", "habari", "salam", "shikamoo", "hello", "hi", "hey", "vipi", "niaje", "poa", "good morning", "good evening"))) {
            return buildWakeGreetingResponse(snapshot)
        }

        // 17. Closest-Command Automatic Matcher (1,500+ command grammar!)
        if (allowClosestFallback) {
            val closest = NeliAiCommandCatalog.findClosestCommand(cleaned)
            if (closest != null && closest.similarityScore >= 0.54f) {
                val resolved = evaluateLocally(
                    rawQuery = closest.template.canonicalQuery,
                    snapshot = snapshot,
                    context = context,
                    allowClosestFallback = false
                )
                return resolved.copy(
                    sourceTag = "${resolved.sourceTag} (Smart Match)",
                    confidence = (resolved.confidence * closest.similarityScore).coerceIn(0.62f, 0.94f)
                )
            }
        }

        // 18. Friendly never-dead-end fallback
        val topLive = snapshot.liveChannels.take(3).map { NeliAiCardItem.ChannelCard(it) }
        val topVod = pickFreshRandomMovies(snapshot, count = 2).map { NeliAiCardItem.MediaCard(it) }
        return NeliAiCommandResponse(
            intent = "FALLBACK_GUIDANCE",
            confidence = 0.45f,
            replyText = "I didn't catch \"$cleaned\" directly, but I can open any of ${snapshot.liveChannels.size} live channels, suggest movies, or download random movies for you. Try saying \"Open Azam Sports 1\" or \"Suggest me movies\"!",
            replySpeech = "I didn't quite catch that. Try saying open Azam Sports 1, suggest me movies, or download 5 random movies.",
            sourceTag = "Neli AI Local Brain",
            cards = topLive + topVod,
            quickReplies = listOf("Suggest me movies", "Download 5 random movies", "Open Azam Sports 1")
        )
    }

    private fun buildChannelPlayResponse(
        top: LiveChannel,
        alternatives: List<LiveChannel>,
        confidence: Float,
        shouldAutoPlay: Boolean,
        snapshot: NeliAiContextSnapshot,
        context: Context?,
        sourceTag: String
    ): NeliAiCommandResponse {
        // Enforce VIP payment lock on Mobile/Tablet (Smart TV is always 100% free!)
        val isLockedAndUnpaid = !snapshot.isSmartTv &&
            !snapshot.isPremiumActive &&
            snapshot.isChannelLockedCheck(top)

        if (isLockedAndUnpaid) {
            val msg = "${top.name} is a VIP channel. Please pay to unlock VIP access first, then I will open ${top.name} for you right away!"
            return NeliAiCommandResponse(
                intent = "CHANNEL_LOCKED_VIP",
                confidence = 0.98f,
                replyText = msg,
                replySpeech = "${top.name} requires a VIP subscription. Please pay first to unlock and watch it.",
                sourceTag = "Neli VIP Guard",
                action = NeliAiAction.LockedChannelNeedsPayment(top),
                cards = listOf(NeliAiCardItem.ChannelCard(top, badge = "VIP LOCK")),
                quickReplies = listOf("Unlock VIP now", "Suggest free movies", "Open free channel")
            )
        }

        NeliAiMemoryStore.recordChannelPlayed(context, top.name, top.categories)
        val epgNote = if (top.currentProgram.isNotBlank()) " Now playing: ${top.currentProgram}." else ""
        val reply = if (shouldAutoPlay) {
            "Opening ${top.name} now!$epgNote"
        } else {
            "Found ${top.name} on NeliTv Live.$epgNote Tap or say \"Open ${top.name}\" to watch."
        }
        return NeliAiCommandResponse(
            intent = "PLAY_CHANNEL",
            confidence = confidence,
            replyText = reply,
            replySpeech = if (shouldAutoPlay) "Opening ${top.name}.$epgNote" else "Found ${top.name}.",
            sourceTag = sourceTag,
            action = if (shouldAutoPlay) NeliAiAction.PlayChannel(top) else NeliAiAction.None,
            cards = alternatives.map { NeliAiCardItem.ChannelCard(it) },
            quickReplies = listOf("Next channel", "Volume up", "Suggest movies")
        )
    }

    private fun enforceVipLockOnResponse(
        response: NeliAiCommandResponse,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse {
        val action = response.action
        if (action is NeliAiAction.PlayChannel &&
            !snapshot.isSmartTv &&
            !snapshot.isPremiumActive &&
            snapshot.isChannelLockedCheck(action.channel)
        ) {
            val ch = action.channel
            val msg = "${ch.name} is a VIP channel. Please pay first to unlock it!"
            return response.copy(
                intent = "CHANNEL_LOCKED_VIP",
                replyText = msg,
                replySpeech = "${ch.name} requires VIP payment first before I can open it.",
                action = NeliAiAction.LockedChannelNeedsPayment(ch),
                cards = listOf(NeliAiCardItem.ChannelCard(ch, badge = "VIP LOCK"))
            )
        }
        return response
    }

    /**
     * Suggests random fresh movies WITHOUT falsely claiming "Playing <movie>".
     * User can then say "Open <movie>" / "Download <movie>" or tick checkboxes in the popup.
     */
    private fun buildMovieSuggestionResponse(
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?
    ): NeliAiCommandResponse {
        val matchedGenre = vodGenreSynonyms.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key
        val swahiliOnly = containsAny(lower, listOf("swahili", "kiswahili", "tafsiri", "dj", "bongo"))
        if (matchedGenre != null) {
            NeliAiMemoryStore.recordGenreInterest(context, matchedGenre)
        }

        val count = extractRequestedCount(lower, defaultCount = 5).coerceIn(3, 6)
        val picked = pickFreshRandomMovies(
            snapshot = snapshot,
            count = count,
            genreFilter = matchedGenre,
            swahiliOnly = swahiliOnly
        )

        if (picked.isEmpty()) {
            return NeliAiCommandResponse(
                intent = "SUGGEST_MOVIES_EMPTY",
                confidence = 0.85f,
                replyText = "Our movie catalog is still loading. Meanwhile, you can watch live movie channels like Sinema Zetu!",
                replySpeech = "Our movie catalog is still syncing. Try again in a moment or watch Sinema Zetu live.",
                sourceTag = "Neli Movie Picker"
            )
        }

        NeliAiMemoryStore.lastSuggestedMovieIds = picked.map { it.id }
        val spokenNames = picked.take(4).joinToString(", ") { it.title }
        val styleLabel = buildString {
            if (swahiliOnly) append("Swahili ")
            if (matchedGenre != null) append("${matchedGenre.replaceFirstChar { it.uppercase() }} ")
        }
        val promptTail = "No tapping needed — just say \"Play them\", \"Download all\", \"Play number 1\", or \"Play a random movie\"!"
        val reply = "Here are ${picked.size} fresh ${styleLabel}movies for you: $spokenNames. $promptTail"
        val speech = "Here are ${picked.size} fresh ${styleLabel}movies: $spokenNames. Say play them, download all, or play number 1."

        return NeliAiCommandResponse(
            intent = "SUGGEST_MOVIES",
            confidence = 0.96f,
            replyText = reply,
            replySpeech = speech,
            sourceTag = "Neli Smart Recommender",
            action = NeliAiAction.SuggestMovies(picked),
            cards = picked.map { NeliAiCardItem.MediaCard(it) },
            quickReplies = listOf("Play them", "Download all", "Play a random movie")
        )
    }

    /**
     * Hands-free instant random movie auto-play when user is away from the phone (no ticking needed!).
     */
    private fun buildAutoPlayRandomMovieResponse(
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?
    ): NeliAiCommandResponse {
        val matchedGenre = vodGenreSynonyms.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key
        val swahiliOnly = containsAny(lower, listOf("swahili", "kiswahili", "tafsiri", "dj", "bongo"))
        if (matchedGenre != null) {
            NeliAiMemoryStore.recordGenreInterest(context, matchedGenre)
        }

        val picked = pickFreshRandomMovies(
            snapshot = snapshot,
            count = 1,
            genreFilter = matchedGenre,
            swahiliOnly = swahiliOnly,
            requireDownloadableStream = true
        ).firstOrNull() ?: pickFreshRandomMovies(snapshot = snapshot, count = 1).firstOrNull()

        if (picked == null) {
            return NeliAiCommandResponse(
                intent = "RANDOM_MOVIE_EMPTY",
                confidence = 0.85f,
                replyText = "Our movie catalog is still syncing. Try again in a moment!",
                replySpeech = "Our movie catalog is still syncing. Please try again shortly.",
                sourceTag = "Neli Smart Auto-Play"
            )
        }

        NeliAiMemoryStore.recordMovieWatched(context, picked.id)
        val reply = "Picked a fresh unwatched movie for you: \"${picked.title}\" (${picked.primaryGenre.ifBlank { picked.genre }}). Playing it now!"
        val speech = "Playing ${picked.title} for you now."
        return NeliAiCommandResponse(
            intent = "AUTO_PLAY_RANDOM_MOVIE",
            confidence = 0.97f,
            replyText = reply,
            replySpeech = speech,
            sourceTag = "Neli Hands-Free Auto-Play",
            action = NeliAiAction.OpenMedia(picked, autoPlay = !picked.isSeries),
            cards = listOf(NeliAiCardItem.MediaCard(picked)),
            quickReplies = listOf("Next random movie", "Download ${picked.title}", "Volume up")
        )
    }

    /**
     * Handles zero-tick voice follow-ups after movie suggestions:
     * - "play them" / "play all" / "play in sequence" / "cheza zote"
     * - "play number 1".."5" / "open first" / "play the second one" / "ya kwanza"
     */
    private fun detectRecentSuggestionFollowUp(
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        val recent = NeliAiMemoryStore.lastSuggestedMovieIds
            .mapNotNull { id -> snapshot.mediaCatalog.firstOrNull { it.id == id } }

        if (containsAny(
                lower,
                listOf(
                    "play in sequence", "play them in sequence", "play all", "play them",
                    "watch them", "watch all", "cheza zote", "cheza kwa mpangilio",
                    "play suggested", "play all suggested"
                )
            )
        ) {
            val queue = recent.ifEmpty { pickFreshRandomMovies(snapshot, count = 3) }
            if (queue.isNotEmpty()) {
                val names = queue.joinToString(", ") { it.title }
                return NeliAiCommandResponse(
                    intent = "PLAY_MOVIES_SEQUENCE",
                    confidence = 0.97f,
                    replyText = "Playing ${queue.size} movies in sequence: $names.",
                    replySpeech = "Playing ${queue.size} movies in sequence, starting with ${queue.first().title}.",
                    sourceTag = "Neli Hands-Free Queue",
                    action = NeliAiAction.PlayMoviesInSequence(queue),
                    cards = queue.map { NeliAiCardItem.MediaCard(it) }
                )
            }
        }

        if (recent.isNotEmpty()) {
            val pickedIndex = when {
                containsAny(lower, listOf("number 1", "number one", "first one", "open first", "play first", "ya kwanza", "namba moja", "namba 1")) -> 0
                containsAny(lower, listOf("number 2", "number two", "second one", "open second", "play second", "ya pili", "namba mbili", "namba 2")) -> 1
                containsAny(lower, listOf("number 3", "number three", "third one", "open third", "play third", "ya tatu", "namba tatu", "namba 3")) -> 2
                containsAny(lower, listOf("number 4", "number four", "fourth one", "open fourth", "play fourth", "ya nne", "namba nne", "namba 4")) -> 3
                containsAny(lower, listOf("number 5", "number five", "fifth one", "open fifth", "play fifth", "ya tano", "namba tano", "namba 5")) -> 4
                else -> -1
            }
            if (pickedIndex in recent.indices) {
                val chosen = recent[pickedIndex]
                return NeliAiCommandResponse(
                    intent = "OPEN_SUGGESTED_BY_INDEX",
                    confidence = 0.97f,
                    replyText = "Opening #${pickedIndex + 1}: \"${chosen.title}\" for you now!",
                    replySpeech = "Playing ${chosen.title}.",
                    sourceTag = "Neli Hands-Free Picker",
                    action = NeliAiAction.OpenMedia(chosen, autoPlay = !chosen.isSeries),
                    cards = listOf(NeliAiCardItem.MediaCard(chosen))
                )
            }
        }
        return null
    }

    /**
     * Handles:
     * - "Download all" / "Download them" / "Pakua zote" (downloads recent suggestions immediately — zero ticking!)
     * - "Download me 5 random movies" (excludes already downloaded + watched within last 30 days!)
     * - "Download <specific movie title>"
     */
    private fun buildSmartDownloadResponse(
        cleaned: String,
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?
    ): NeliAiCommandResponse {
        // Zero-tick instant download of recently suggested movies ("download all", "download them", "pakua zote")
        if (containsAny(lower, listOf("download all", "download them", "download those", "download suggested", "pakua zote", "pakua hizo"))) {
            val recent = NeliAiMemoryStore.lastSuggestedMovieIds
                .mapNotNull { id -> snapshot.mediaCatalog.firstOrNull { it.id == id } }
                .filter { it.id !in snapshot.downloadedMediaIds && it.streamUrl.isNotBlank() }
            if (recent.isNotEmpty()) {
                val names = recent.take(4).joinToString(", ") { it.title }
                return NeliAiCommandResponse(
                    intent = "DOWNLOAD_SUGGESTED_ALL",
                    confidence = 0.97f,
                    replyText = "Downloading all ${recent.size} suggested movies in the background: $names!",
                    replySpeech = "Downloading all ${recent.size} movies for you now.",
                    sourceTag = "Neli Hands-Free Downloader",
                    action = NeliAiAction.DownloadSpecificMovies(recent),
                    cards = recent.map { NeliAiCardItem.MediaCard(it) }
                )
            }
        }

        val wantsRandomOrMultiple = containsAny(
            lower,
            listOf("random", "movies", "filamu", "for later", "watching later", "unwatched", "some", "best", "new", "action", "comedy", "horror", "swahili", "all", "them")
        ) || Regex("\\b([2-9]|10|two|three|four|five|six|seven|eight|nine|ten|mbili|tatu|nne|tano)\\b").containsMatchIn(lower)

        val matchedGenre = vodGenreSynonyms.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key
        val swahiliOnly = containsAny(lower, listOf("swahili", "kiswahili", "tafsiri", "dj"))

        // Check if user named a specific movie title (e.g. "download John Wick")
        if (!wantsRandomOrMultiple) {
            val specificHits = findBestMediaMatches(cleaned, snapshot.mediaCatalog, 3)
            val topHit = specificHits.firstOrNull()
            if (topHit != null && topHit.second >= 50) {
                val movie = topHit.first
                if (movie.id in snapshot.downloadedMediaIds) {
                    val alreadyMsg = "\"${movie.title}\" is already in your Downloads! Say \"Open ${movie.title}\" to watch it now."
                    return NeliAiCommandResponse(
                        intent = "DOWNLOAD_ALREADY_EXISTS",
                        confidence = 0.96f,
                        replyText = alreadyMsg,
                        replySpeech = "${movie.title} is already downloaded. Say open ${movie.title} to watch it.",
                        sourceTag = "Neli Download Guard",
                        cards = listOf(NeliAiCardItem.MediaCard(movie))
                    )
                }
                return NeliAiCommandResponse(
                    intent = "DOWNLOAD_SPECIFIC_MOVIE",
                    confidence = 0.96f,
                    replyText = "Starting background download for \"${movie.title}\" right now!",
                    replySpeech = "Downloading ${movie.title} for you now.",
                    sourceTag = "Neli Offline Downloader",
                    action = NeliAiAction.DownloadSpecificMovies(listOf(movie)),
                    cards = listOf(NeliAiCardItem.MediaCard(movie))
                )
            }
        }

        val count = extractRequestedCount(lower, defaultCount = 5).coerceIn(1, 10)
        val freshMovies = pickFreshRandomMovies(
            snapshot = snapshot,
            count = count,
            genreFilter = matchedGenre,
            swahiliOnly = swahiliOnly,
            requireDownloadableStream = true,
            strictExcludeDownloadedAndWatched30Days = true
        )

        if (freshMovies.isEmpty()) {
            return NeliAiCommandResponse(
                intent = "DOWNLOAD_NONE_FRESH",
                confidence = 0.90f,
                replyText = "All matching movies are already downloaded or watched in the last 30 days! I'll let you know as soon as new movies arrive.",
                replySpeech = "All matching movies have already been downloaded or watched this month.",
                sourceTag = "Neli Smart Download Guard"
            )
        }

        val names = freshMovies.take(4).joinToString(", ") { it.title }
        val extra = if (freshMovies.size > 4) " and ${freshMovies.size - 4} more" else ""
        val reply = "Downloading ${freshMovies.size} fresh unwatched movies for you (skipping already downloaded and last 30 days watched): $names$extra!"
        val speech = "Got it! Starting downloads for ${freshMovies.size} fresh movies: $names$extra."

        return NeliAiCommandResponse(
            intent = "DOWNLOAD_RANDOM_MOVIES",
            confidence = 0.97f,
            replyText = reply,
            replySpeech = speech,
            sourceTag = "Neli Smart Downloader",
            action = NeliAiAction.DownloadRandomMovies(
                requestedCount = count,
                movies = freshMovies,
                genreFilter = matchedGenre,
                swahiliOnly = swahiliOnly
            ),
            cards = freshMovies.map { NeliAiCardItem.MediaCard(it) },
            quickReplies = listOf("Open downloads", "Suggest more movies")
        )
    }

    /**
     * Picks fresh random movies strictly excluding:
     * 1. Adult content
     * 2. Already downloaded movies (`snapshot.downloadedMediaIds`)
     * 3. Movies watched within the last 30 days (`snapshot.watchedWithin30DaysIds` + `NeliAiMemoryStore.getWatchedMovieIdsWithin30Days()`)
     */
    internal fun pickFreshRandomMovies(
        snapshot: NeliAiContextSnapshot,
        count: Int,
        genreFilter: String? = null,
        swahiliOnly: Boolean = false,
        preferTopRated: Boolean = false,
        requireDownloadableStream: Boolean = false,
        strictExcludeDownloadedAndWatched30Days: Boolean = false
    ): List<MediaContent> {
        val watched30 = snapshot.watchedWithin30DaysIds + NeliAiMemoryStore.getWatchedMovieIdsWithin30Days()
        val excludedIds = snapshot.downloadedMediaIds + watched30

        val base = snapshot.mediaCatalog.filter { item ->
            !item.isAdult &&
                (!requireDownloadableStream || item.streamUrl.isNotBlank()) &&
                (!swahiliOnly || item.isSwahiliNarratedMovie || item.narrated) &&
                (genreFilter == null || item.genre.contains(genreFilter, ignoreCase = true) ||
                    item.subGenres.any { it.contains(genreFilter, ignoreCase = true) })
        }

        val fresh = base.filter { it.id !in excludedIds }
        val pool = when {
            fresh.isNotEmpty() -> fresh
            strictExcludeDownloadedAndWatched30Days -> emptyList()
            else -> base
        }

        if (pool.isEmpty()) return emptyList()
        return if (preferTopRated) {
            pool.sortedByDescending {
                (if (it.isTrending) 40 else 0) +
                    (if (it.featured) 25 else 0) +
                    ((it.rating.toFloatOrNull() ?: 0f) * 10f).toInt()
            }.take(count)
        } else {
            pool.shuffled().take(count)
        }
    }

    private fun buildMovieInfoResponse(
        cleaned: String,
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        val queryWithoutInfoWords = lower
            .replace(Regex("\\b(tell me about|info about|movie info|info on|about movie|search for|search movie|find movie|details of|details about|what is|taarifa za|habari za|kuhusu)\\b"), "")
            .trim()
        val searchTarget = queryWithoutInfoWords.ifBlank { cleaned }
        val matches = findBestMediaMatches(searchTarget, snapshot.mediaCatalog, 4)
        val top = matches.firstOrNull()?.first ?: return null

        val synopsisSnippet = top.synopsis
            .trim()
            .take(180)
            .let { if (it.isBlank()) "Available now on NeliTv." else it }
        val meta = buildString {
            if (top.releaseYear.isNotBlank()) append("${top.releaseYear} • ")
            append(top.primaryGenre.ifBlank { top.genre.ifBlank { "Movie" } })
            if (top.rating.isNotBlank()) append(" • Rating ${top.rating}")
            if (top.isSwahiliNarratedMovie) append(" • Swahili Narrated")
        }
        val reply = "${top.title} ($meta): $synopsisSnippet"
        val speech = "${top.title}, released ${top.releaseYear.ifBlank { "recently" }}, is a ${top.primaryGenre.ifBlank { "featured" }} title on NeliTv. $synopsisSnippet. Say open or download to get it!"

        return NeliAiCommandResponse(
            intent = "MOVIE_INFO",
            confidence = 0.94f,
            replyText = reply,
            replySpeech = speech,
            sourceTag = "Neli Movie Encyclopedia",
            action = NeliAiAction.ShowMovieInfo(top),
            cards = matches.map { NeliAiCardItem.MediaCard(it.first) },
            quickReplies = listOf("Open ${top.title}", "Download ${top.title}", "Suggest similar movies")
        )
    }

    private fun detectBrightnessIntent(lower: String): NeliAiCommandResponse? {
        if (containsAny(lower, listOf("max brightness", "full brightness", "mwanga mwisho", "brightest"))) {
            return brightnessResponse(NeliAiBrightnessDirection.MAX, "Maximum brightness set.")
        }
        if (containsAny(lower, listOf("low brightness", "minimum brightness", "night brightness", "mwanga mdogo sana"))) {
            return brightnessResponse(NeliAiBrightnessDirection.LOW, "Low night brightness set.")
        }
        if (containsAny(lower, listOf("auto brightness", "mwanga auto"))) {
            return brightnessResponse(NeliAiBrightnessDirection.AUTO, "Automatic brightness enabled.")
        }
        if (containsAny(lower, listOf("increase brightness", "brightness up", "brighter", "more brightness", "ongeza mwanga", "pandisha mwanga", "mwanga juu"))) {
            return brightnessResponse(NeliAiBrightnessDirection.UP, "Increasing brightness.")
        }
        if (containsAny(lower, listOf("decrease brightness", "brightness down", "dim screen", "less brightness", "darker", "punguza mwanga", "shusha mwanga", "mwanga chini"))) {
            return brightnessResponse(NeliAiBrightnessDirection.DOWN, "Decreasing brightness.")
        }
        return null
    }

    private fun brightnessResponse(dir: NeliAiBrightnessDirection, msg: String): NeliAiCommandResponse {
        return NeliAiCommandResponse(
            intent = "ADJUST_BRIGHTNESS",
            confidence = 0.96f,
            replyText = msg,
            replySpeech = msg,
            sourceTag = "Neli Player & Screen Control",
            action = NeliAiAction.AdjustBrightness(dir)
        )
    }

    private fun detectPlayerControlIntent(
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        if (containsAny(lower, listOf("inayofuata", "next channel", "channel mbele", "badili channel", "skip channel", "chaneli inayofuata", "open different channel", "change channel"))) {
            return NeliAiCommandResponse(
                intent = "NEXT_CHANNEL",
                confidence = 0.96f,
                replyText = "Switching to the next channel now!",
                replySpeech = "Next channel.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.SwitchNextChannel,
                quickReplies = listOf("Previous channel", "Volume up", "Auto quality")
            )
        }
        if (containsAny(lower, listOf("iliyopita", "previous channel", "channel nyuma", "rudi channel", "chaneli iliyopita", "go back channel"))) {
            return NeliAiCommandResponse(
                intent = "PREV_CHANNEL",
                confidence = 0.96f,
                replyText = "Going back to the previous channel.",
                replySpeech = "Previous channel.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.SwitchPreviousChannel,
                quickReplies = listOf("Next channel", "Auto quality")
            )
        }
        if (containsAny(lower, listOf("ongeza sauti", "volume up", "increase volume", "increase voice", "raise volume", "turn up volume", "pandisha sauti", "sauti juu", "louder"))) {
            return NeliAiCommandResponse(
                intent = "VOLUME_UP",
                confidence = 0.96f,
                replyText = "Turning the volume up.",
                replySpeech = "Volume up.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.AdjustVolume(NeliAiVolumeDirection.UP)
            )
        }
        if (containsAny(lower, listOf("punguza sauti", "volume down", "decrease volume", "decrease voice", "lower volume", "turn down volume", "shusha sauti", "sauti chini", "quieter"))) {
            return NeliAiCommandResponse(
                intent = "VOLUME_DOWN",
                confidence = 0.96f,
                replyText = "Turning the volume down.",
                replySpeech = "Volume down.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.AdjustVolume(NeliAiVolumeDirection.DOWN)
            )
        }
        if (containsAny(lower, listOf("zima sauti", "mute", "nyamazisha", "ondoa sauti", "silence"))) {
            return NeliAiCommandResponse(
                intent = "VOLUME_MUTE",
                confidence = 0.95f,
                replyText = "Audio muted.",
                replySpeech = "",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.AdjustVolume(NeliAiVolumeDirection.MUTE)
            )
        }
        if (containsAny(lower, listOf("washa sauti", "unmute", "rudisha sauti", "restore sound"))) {
            return NeliAiCommandResponse(
                intent = "VOLUME_UNMUTE",
                confidence = 0.95f,
                replyText = "Audio unmuted.",
                replySpeech = "Sound is back on.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.AdjustVolume(NeliAiVolumeDirection.UNMUTE)
            )
        }
        if (containsAny(lower, listOf("240p", "bando kidogo", "low bando", "ultra saver"))) {
            return qualityResponse(NetworkQualityMode.ULTRA_LOW_BANDO_SAVER, "240p Low Data Saver")
        }
        if (containsAny(lower, listOf("360p", "data saver", "okoa bando", "punguza bando", "punguza quality", "decrease quality", "lower quality"))) {
            return qualityResponse(NetworkQualityMode.WEAK_NETWORK_SAVER, "360p Data Saver")
        }
        if (containsAny(lower, listOf("480p", "normal quality", "standard quality", "ubora wa kawaida"))) {
            return qualityResponse(NetworkQualityMode.STANDARD_480P, "480p Standard")
        }
        if (containsAny(lower, listOf("1080p", "720p", "full hd", "weka hd", "high quality", "increase quality", "higher quality", "ongeza quality", "best quality", "ubora wa juu"))) {
            return qualityResponse(NetworkQualityMode.STRONG_NETWORK_HD, "Full HD 1080p")
        }
        if (containsAny(lower, listOf("auto quality", "ubora automatic", "auto hd", "automatic quality"))) {
            return qualityResponse(NetworkQualityMode.AUTO_ADAPTIVE, "Auto Adaptive Quality")
        }
        if (snapshot.activePlayerChannel != null && containsAny(lower, listOf("simamisha", "pause", "tulia", "endelea", "resume"))) {
            return NeliAiCommandResponse(
                intent = "TOGGLE_PLAY_PAUSE",
                confidence = 0.93f,
                replyText = "Playback state toggled.",
                replySpeech = "Done.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.TogglePlayPause
            )
        }
        if (snapshot.activePlayerChannel != null && containsAny(lower, listOf("funga video", "toka player", "close player", "exit player", "rudi nyumbani", "stop watching"))) {
            return NeliAiCommandResponse(
                intent = "CLOSE_PLAYER",
                confidence = 0.93f,
                replyText = "Closing the player and returning home.",
                replySpeech = "Closing player.",
                sourceTag = "Neli Player Control",
                action = NeliAiAction.ClosePlayer
            )
        }
        return null
    }

    private fun qualityResponse(mode: NetworkQualityMode, label: String): NeliAiCommandResponse {
        return NeliAiCommandResponse(
            intent = "SET_QUALITY",
            confidence = 0.96f,
            replyText = "Video quality set to $label smoothly in the background.",
            replySpeech = "Quality set to $label.",
            sourceTag = "Neli Player Control",
            action = NeliAiAction.SetQuality(mode),
            quickReplies = listOf("Auto quality", "480p", "Full HD")
        )
    }

    private fun detectMetaOrFaqIntent(
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        val nonAdultMovies = snapshot.mediaCatalog.count { !it.isAdult && it.isMovie }
        val nonAdultSeries = snapshot.mediaCatalog.count { !it.isAdult && it.isSeries }

        if (containsAny(lower, listOf("how are you", "habari yako", "ukoje", "unaendeleaje"))) {
            val reply = NeliAiCommandCatalog.pickVariedReply("HOW_ARE_YOU")
                ?: "I'm doing great! What would you like to watch?"
            return NeliAiCommandResponse("SMALLTALK_HOW_ARE_YOU", 0.96f, reply, reply, "Neli AI Voice")
        }
        if (containsAny(lower, listOf("thank you", "thanks", "asante", "shukrani"))) {
            val reply = NeliAiCommandCatalog.pickVariedReply("THANKS") ?: "You're welcome!"
            return NeliAiCommandResponse("SMALLTALK_THANKS", 0.96f, reply, reply, "Neli AI Voice")
        }
        if (containsAny(lower, listOf("who made you", "who created you", "nani alikujenga"))) {
            val reply = NeliAiCommandCatalog.pickVariedReply("WHO_MADE_YOU") ?: "I was built by the NeliTv team!"
            return NeliAiCommandResponse("SMALLTALK_CREATOR", 0.96f, reply, reply, "Neli AI Brain")
        }
        if (containsAny(lower, listOf("joke", "chekesha", "funny"))) {
            val reply = NeliAiCommandCatalog.pickVariedReply("JOKES_FUN") ?: "Want me to suggest a comedy movie?"
            return NeliAiCommandResponse("SMALLTALK_JOKE", 0.95f, reply, reply, "Neli AI Voice")
        }
        if (containsAny(lower, listOf("what time", "saa ngapi", "current time"))) {
            val nowTime = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            val reply = "It is currently $nowTime."
            return NeliAiCommandResponse("SYSTEM_TIME", 0.97f, reply, reply, "Neli System")
        }
        if (containsAny(lower, listOf("who am i", "my profile", "my name", "jina langu"))) {
            val name = snapshot.userName.ifBlank { "NeliTv Guest" }
            val vip = if (snapshot.isSmartTv) "Free Smart TV Edition" else if (snapshot.isPremiumActive) "Active VIP Member" else "Free Member"
            val reply = "You are signed in as $name ($vip). You have ${snapshot.downloadedMediaIds.size} offline downloads ready."
            return NeliAiCommandResponse("PERSONAL_PROFILE", 0.96f, reply, reply, "Neli Personal Data")
        }
        if (containsAny(lower, listOf("watch history", "favorite channels", "historia yangu", "what did i watch"))) {
            val top = NeliAiMemoryStore.topWatchedChannels(3).joinToString(", ") { it.first }
            val watchedCount = (snapshot.watchedWithin30DaysIds + NeliAiMemoryStore.getWatchedMovieIdsWithin30Days()).size
            val reply = if (top.isNotBlank()) {
                "Your top channels are $top, and you've watched $watchedCount movies in the last 30 days."
            } else {
                "You've watched $watchedCount movies in the last 30 days. Tell me any channel or movie to build your favorites!"
            }
            return NeliAiCommandResponse("PERSONAL_HISTORY", 0.95f, reply, reply, "Neli Memory")
        }
        if (containsAny(lower, listOf("channel health", "check channels", "repair channels", "kagua chaneli"))) {
            val report = ChannelHealthGuardian.healthReport.value
            val reply = if (report.totalChecked > 0) {
                "Channel Keep-Alive Guardian checked ${report.totalChecked} live channels: ${report.healthyCount} healthy and ${report.autoHealedCount} auto-repaired onto backup streams!"
            } else {
                "Channel Keep-Alive Guardian is active and monitoring all ${snapshot.liveChannels.size} live channels with automatic backup failover."
            }
            return NeliAiCommandResponse("CHANNEL_HEALTH", 0.96f, reply, reply, "Channel Keep-Alive Guardian")
        }
        if (containsAny(lower, listOf("nelitv online ina movie", "nelitv ina movie", "movie zinatoka wapi", "chanzo cha movie", "firebase", "where do movies come from"))) {
            val reply = "Ndiyo najua vizuri! Seva ya nelitv.online inaleta chaneli ${snapshot.liveChannels.size} za Live TV na EPG, wakati Movie ($nonAdultMovies) na Series ($nonAdultSeries) zinatoka kwenye Firebase Catalog ya NeliTv. Mimi Neli AI nimeunganishwa na zote mbili!"
            return NeliAiCommandResponse(
                intent = "DATA_SOURCE_KNOWLEDGE",
                confidence = 0.97f,
                replyText = reply,
                replySpeech = "Live TV comes from nelitv.online, and movies and series come from Firebase. I read both in real time!",
                sourceTag = "Neli Dual-Catalog Brain"
            )
        }
        if (containsAny(lower, listOf("wewe ni nani", "who are you", "unafanya nini", "what can you do", "msaada", "help"))) {
            val reply = "I'm Neli AI, your hands-free voice assistant for NeliTv! I understand over ${NeliAiCommandCatalog.totalCommandCount} commands: I can open ${snapshot.liveChannels.size} live channels, suggest & download fresh movies, check movie info, adjust volume, brightness & quality, or lock your screen!"
            return NeliAiCommandResponse(
                intent = "IDENTITY_HELP",
                confidence = 0.98f,
                replyText = reply,
                replySpeech = "I'm Neli AI, your hands-free assistant for NeliTv. Say suggest me movies, download 5 random movies, open any channel, or lock my screen!",
                sourceTag = "Neli AI Brain"
            )
        }
        if (containsAny(lower, listOf("chaneli ngapi", "channel ngapi", "how many channels", "movie ngapi", "how many movies"))) {
            val reply = "Right now NeliTv has ${snapshot.liveChannels.size} Live TV channels, $nonAdultMovies movies, $nonAdultSeries series, and ${snapshot.downloadedMediaIds.size} offline downloads on your device."
            return NeliAiCommandResponse(
                intent = "CATALOG_STATS",
                confidence = 0.96f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "Live + Firebase Telemetry"
            )
        }
        if (containsAny(lower, listOf("smart tv", "kwenye tv", "bure tv", "vip", "bei", "malipo", "subscription", "am i vip"))) {
            val reply = if (snapshot.isSmartTv) {
                "On Smart TV, NeliTv is 100% free — no login, no VIP locks, and no ads!"
            } else if (snapshot.isPremiumActive) {
                "Your account is an Active VIP Member! All live channels and movies are unlocked."
            } else {
                "You can watch free channels right away, or upgrade to VIP with M-Pesa, Tigo, Airtel, or Halopesa to unlock all premium channels."
            }
            return NeliAiCommandResponse("SUBSCRIPTION_INFO", 0.95f, reply, reply, "NeliTv System Info")
        }
        return null
    }

    private fun detectNavigationIntent(lower: String): NeliAiCommandResponse? {
        return when {
            containsAny(lower, listOf("fungua download", "open download", "my download", "nilizopakua", "offline videos", "downloaded movies")) ->
                NeliAiCommandResponse(
                    intent = "NAV_DOWNLOADS",
                    confidence = 0.95f,
                    replyText = "Opening your Downloads.",
                    replySpeech = "Opening Downloads.",
                    sourceTag = "Neli Navigation",
                    action = NeliAiAction.NavigateTab(BottomNavTab.DOWNLOAD)
                )
            containsAny(lower, listOf("fungua discovery", "ukurasa wa movie", "open movies tab", "browse movies", "fungua filamu")) ->
                NeliAiCommandResponse(
                    intent = "NAV_DISCOVERY",
                    confidence = 0.93f,
                    replyText = "Opening Discovery for movies and series.",
                    replySpeech = "Opening Discovery.",
                    sourceTag = "Neli Navigation",
                    action = NeliAiAction.NavigateTab(BottomNavTab.DISCOVERY)
                )
            containsAny(lower, listOf("fungua home", "open home", "go home", "ukurasa wa mwanzo", "live tv tab")) ->
                NeliAiCommandResponse(
                    intent = "NAV_HOME",
                    confidence = 0.93f,
                    replyText = "Returning to Live TV Home.",
                    replySpeech = "Going Home.",
                    sourceTag = "Neli Navigation",
                    action = NeliAiAction.NavigateTab(BottomNavTab.HOME)
                )
            containsAny(lower, listOf("open account", "my account", "fungua akaunti", "akaunti yangu")) ->
                NeliAiCommandResponse(
                    intent = "NAV_ACCOUNT",
                    confidence = 0.93f,
                    replyText = "Opening your Account page.",
                    replySpeech = "Opening Account.",
                    sourceTag = "Neli Navigation",
                    action = NeliAiAction.NavigateTab(BottomNavTab.ACCOUNT)
                )
            else -> null
        }
    }

    private fun buildEpgNowPlayingResponse(
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse {
        val specific = findBestChannelMatches(lower, snapshot.liveChannels, 1).firstOrNull()
        if (specific != null && specific.second >= 55) {
            val ch = specific.first
            val nowProg = ch.currentProgram.ifBlank { "Live Broadcast" }
            val nextProg = if (ch.nextProgram.isNotBlank()) " Up next: ${ch.nextProgram}." else ""
            val reply = "On ${ch.name} right now: $nowProg.$nextProg"
            return NeliAiCommandResponse(
                intent = "EPG_CHANNEL",
                confidence = 0.95f,
                replyText = reply,
                replySpeech = reply,
                sourceTag = "NeliTV EPG Live",
                cards = listOf(NeliAiCardItem.ChannelCard(ch)),
                quickReplies = listOf("Open ${ch.name}", "What's on now?")
            )
        }

        val withEpg = snapshot.liveChannels.filter { it.currentProgram.isNotBlank() }.take(5)
        val displayList = withEpg.ifEmpty { snapshot.liveChannels.take(5) }
        val summary = if (withEpg.isNotEmpty()) {
            val highlights = withEpg.take(3).joinToString("; ") { "${it.name}: ${it.currentProgram}" }
            "Live right now on NeliTv: $highlights."
        } else {
            "${snapshot.liveChannels.size} live channels are streaming right now! Say any channel name to watch."
        }
        return NeliAiCommandResponse(
            intent = "EPG_OVERVIEW",
            confidence = 0.93f,
            replyText = summary,
            replySpeech = summary,
            sourceTag = "NeliTV Live EPG",
            cards = displayList.map { NeliAiCardItem.ChannelCard(it) },
            quickReplies = displayList.take(3).map { "Open ${it.name}" }
        )
    }

    private fun buildMovieOrSeriesResponse(
        cleaned: String,
        lower: String,
        snapshot: NeliAiContextSnapshot,
        context: Context?
    ): NeliAiCommandResponse {
        val safeCatalog = snapshot.mediaCatalog.filter { !it.isAdult }
        if (safeCatalog.isEmpty()) {
            val movieChannels = filterChannelsByCategory(snapshot.liveChannels, "movies").take(4)
            return NeliAiCommandResponse(
                intent = "MOVIE_FALLBACK_CHANNELS",
                confidence = 0.80f,
                replyText = "Movies are syncing right now — meanwhile, try live movie channels like Sinema Zetu!",
                replySpeech = "Movies are still loading. You can watch Sinema Zetu live right now.",
                sourceTag = "NeliTV Live + Firebase",
                cards = movieChannels.map { NeliAiCardItem.ChannelCard(it) }
            )
        }

        val titleMatches = findBestMediaMatches(cleaned, safeCatalog, 5)
        if ((titleMatches.firstOrNull()?.second ?: 0) >= 62) {
            return buildMovieMatchReply(titleMatches, lower, snapshot)
        }

        return buildMovieSuggestionResponse(lower, snapshot, context)
    }

    private fun buildMovieMatchReply(
        matches: List<Pair<MediaContent, Int>>,
        lower: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse {
        val top = matches.first().first
        val wantsToOpenOrPlay = containsAny(lower, listOf("weka", "cheza", "play", "fungua", "open", "watch"))
        val autoPlay = wantsToOpenOrPlay && top.streamUrl.isNotBlank() && !top.isSeries
        val reply = if (wantsToOpenOrPlay) {
            "Opening \"${top.title}\" (${top.primaryGenre.ifBlank { top.genre }}) for you now!"
        } else {
            "Found \"${top.title}\" (${top.primaryGenre} • ${top.releaseYear}). Say \"Open ${top.title}\" or \"Download ${top.title}\"!"
        }
        return NeliAiCommandResponse(
            intent = "OPEN_MEDIA",
            confidence = (matches.first().second / 100f).coerceIn(0.68f, 0.99f),
            replyText = reply,
            replySpeech = if (wantsToOpenOrPlay) "Opening ${top.title}." else "Found ${top.title}. Say open or download.",
            sourceTag = "Firebase VOD Catalog",
            action = if (wantsToOpenOrPlay) NeliAiAction.OpenMedia(top, autoPlay = autoPlay) else NeliAiAction.ShowMovieInfo(top),
            cards = matches.map { NeliAiCardItem.MediaCard(it.first) },
            quickReplies = listOf("Open ${top.title}", "Download ${top.title}", "Suggest more movies")
        )
    }

    private fun buildWakeGreetingResponse(snapshot: NeliAiContextSnapshot): NeliAiCommandResponse {
        val greeting = NeliAiCommandCatalog.pickWakeGreeting()
        return NeliAiCommandResponse(
            intent = "WAKE_GREETING",
            confidence = 1f,
            replyText = greeting,
            replySpeech = greeting,
            sourceTag = "Neli AI Ready",
            action = NeliAiAction.WakeGreeting,
            cards = snapshot.liveChannels.take(3).map { NeliAiCardItem.ChannelCard(it) },
            quickReplies = listOf("Suggest me movies", "Download 5 random movies", "Open Azam Sports 1")
        )
    }

    private fun isLockScreenIntent(lower: String): Boolean {
        return containsAny(
            lower,
            listOf(
                "lock my screen", "lock screen", "lock the screen", "lock phone", "lock my phone",
                "lock device", "lock my device", "turn off screen", "funga kioo", "zima kioo",
                "loki simu", "funga simu"
            )
        )
    }

    private fun isOpenNeliTvAppOnlyIntent(lower: String): Boolean {
        return lower == "open nelitv" ||
            lower == "open nelitv app" ||
            lower == "launch nelitv" ||
            lower == "start nelitv" ||
            lower == "fungua nelitv" ||
            lower == "fungua app ya nelitv" ||
            lower == "ingia nelitv" ||
            lower == "open app"
    }

    private fun isDownloadIntent(lower: String): Boolean {
        return containsAny(lower, listOf("download", "pakua", "nipakulie", "save movie", "save movies", "get me movies for later"))
    }

    private fun isAutoPlayRandomMovieIntent(lower: String): Boolean {
        if (containsAny(lower, listOf("suggest", "recommend", "list", "show me", "download", "pakua"))) {
            return false
        }
        return containsAny(
            lower,
            listOf(
                "random movie", "any movie", "surprise movie", "surprise me with a movie",
                "play a random", "put a random", "watch a random", "pick a random movie",
                "choose a random movie", "next random movie",
                "weka movie yoyote", "cheza movie yoyote", "nipigie movie yoyote",
                "filamu yoyote", "movie yoyote", "niwekee movie yoyote"
            )
        )
    }

    private fun isMovieSuggestionIntent(lower: String): Boolean {
        val hasSuggestWord = containsAny(
            lower,
            listOf("suggest", "recommend", "pick me", "nipendekezee", "pendekeza", "best movie", "good movie", "what movie", "which movie", "niangalie nini")
        )
        val mentionsMovieOrGeneral = containsAny(lower, listOf("movie", "movies", "filamu", "film", "watch", "kuangalia", "series", "something"))
        return hasSuggestWord && mentionsMovieOrGeneral
    }

    private fun isMovieInfoOrSearchIntent(lower: String): Boolean {
        return containsAny(
            lower,
            listOf(
                "tell me about", "info about", "movie info", "details about", "details of",
                "search movie", "search for movie", "search for", "what is the plot", "synopsis",
                "taarifa za", "kuhusu movie", "kuhusu filamu"
            )
        )
    }

    internal fun extractRequestedCount(lower: String, defaultCount: Int): Int {
        Regex("\\b(\\d{1,2})\\b").find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        val wordToNum = mapOf(
            "one" to 1, "moja" to 1,
            "two" to 2, "mbili" to 2,
            "three" to 3, "tatu" to 3,
            "four" to 4, "nne" to 4,
            "five" to 5, "tano" to 5,
            "six" to 6, "sita" to 6,
            "seven" to 7, "saba" to 7,
            "eight" to 8, "nane" to 8,
            "nine" to 9, "tisa" to 9,
            "ten" to 10, "kumi" to 10
        )
        val tokens = lower.split(" ")
        for (t in tokens) {
            wordToNum[t]?.let { return it }
        }
        return defaultCount
    }

    internal fun parseTeachCommand(cleaned: String): Pair<String, String>? {
        val patterns = listOf(
            Regex("^(?:fundisha|teach|kumbuka|remember|set alias)\\s+(.+?)\\s*(?:=|->|ni|means|iwe)\\s*(.+)$", RegexOption.IGNORE_CASE),
            Regex("^nikisema\\s+(.+?)\\s*(?:weka|fungua|cheza|ni)\\s*(.+)$", RegexOption.IGNORE_CASE)
        )
        for (regex in patterns) {
            val m = regex.find(cleaned.trim())
            if (m != null) {
                val trigger = m.groupValues[1].trim().trim('"', '\'')
                val target = m.groupValues[2].trim().trim('"', '\'')
                if (trigger.length >= 2 && target.length >= 2) {
                    return trigger to target
                }
            }
        }
        return null
    }

    internal fun findBestChannelMatches(
        rawQuery: String,
        channels: List<LiveChannel>,
        limit: Int = 5
    ): List<Pair<LiveChannel, Int>> {
        val stripped = stripCommandVerbs(rawQuery)
        if (stripped.isBlank()) return emptyList()
        val queryTokens = stripped.split(" ").filter { it.length >= 2 || it.all(Char::isDigit) }

        return channels.mapNotNull { ch ->
            val normName = normalizeText(ch.name)
            val normDesc = normalizeText(ch.currentProgram + " " + ch.description)
            var score = 0

            if (normName == stripped) score = 100
            else if (normName.contains(stripped) && stripped.length >= 3) score = 92
            else if (stripped.contains(normName) && normName.length >= 3) score = 90

            builtInChannelSynonyms.forEach { (canonical, variants) ->
                if (normName.contains(canonical) && variants.any { stripped.contains(it) || it == stripped || levenshtein(stripped, it) <= 2 }) {
                    score = maxOf(score, 94)
                }
            }

            if (queryTokens.isNotEmpty()) {
                val nameTokens = normName.split(" ").filter { it.isNotBlank() }
                var matchedTokens = 0
                queryTokens.forEach { qt ->
                    val hit = nameTokens.any { nt ->
                        nt == qt ||
                            (qt.length >= 3 && (nt.contains(qt) || levenshtein(nt, qt) <= 2))
                    }
                    if (hit) matchedTokens++
                }
                if (matchedTokens > 0) {
                    val ratio = (matchedTokens * 84) / queryTokens.size
                    score = maxOf(score, ratio)
                }
            }

            if (normDesc.isNotBlank() && stripped.length >= 4 && normDesc.contains(stripped)) {
                score = maxOf(score, 68)
            }

            if (score >= 42) ch to score else null
        }.sortedWith(
            compareByDescending<Pair<LiveChannel, Int>> { it.second }
                .thenByDescending { it.first.priorityTier }
        ).take(limit)
    }

    internal fun findBestMediaMatches(
        rawQuery: String,
        catalog: List<MediaContent>,
        limit: Int = 5
    ): List<Pair<MediaContent, Int>> {
        val stripped = stripCommandVerbs(rawQuery)
        if (stripped.length < 2) return emptyList()
        val queryTokens = stripped.split(" ").filter { it.length >= 3 }

        return catalog.filter { !it.isAdult }.mapNotNull { item ->
            val normTitle = normalizeText(item.title)
            val normGenre = normalizeText(item.genre + " " + item.subGenres.joinToString(" "))
            var score = 0
            if (normTitle == stripped) score = 100
            else if (normTitle.contains(stripped) && stripped.length >= 3) score = 88
            else if (stripped.contains(normTitle) && normTitle.length >= 4) score = 85

            if (queryTokens.isNotEmpty()) {
                val titleTokens = normTitle.split(" ").filter { it.length >= 2 }
                val hits = queryTokens.count { qt ->
                    titleTokens.any { tt -> tt == qt || tt.contains(qt) || levenshtein(tt, qt) <= 2 }
                }
                if (hits > 0) {
                    score = maxOf(score, (hits * 80) / queryTokens.size)
                }
                if (queryTokens.any { normGenre.contains(it) }) {
                    score = maxOf(score, 42)
                }
            }
            if (score >= 38) item to score else null
        }.sortedByDescending { it.second }.take(limit)
    }

    private fun detectLiveCategoryFromText(lower: String): String? {
        return genreKeywords.entries.firstOrNull { (_, words) ->
            words.any { lower.contains(it) }
        }?.key?.replaceFirstChar { it.uppercase() }
    }

    private fun filterChannelsByCategory(channels: List<LiveChannel>, category: String): List<LiveChannel> {
        val cat = category.lowercase(Locale.ROOT)
        return channels.filter { ch ->
            val cats = ch.categories.joinToString(" ").lowercase(Locale.ROOT)
            val name = ch.name.lowercase(Locale.ROOT)
            when (cat) {
                "sports" -> cats.contains("sport") || name.contains("sport") || name.contains("wwe") || name.contains("epl") || name.contains("bein") || name.contains("espn")
                "kids" -> cats.contains("kid") || cats.contains("cartoon") || name.contains("tom and jerry") || name.contains("pop") || name.contains("nick") || name.contains("bean")
                "movies" -> cats.contains("movie") || cats.contains("cinema") || name.contains("sinema") || name.contains("movie") || name.contains("mbc 2") || name.contains("zee")
                "music" -> cats.contains("music") || name.contains("wasafi") || name.contains("trace") || name.contains("crown")
                "news" -> cats.contains("news") || name.contains("utv") || name.contains("tbc") || name.contains("zbc") || name.contains("global")
                else -> cats.contains(cat) || name.contains(cat)
            }
        }
    }

    private fun stripCommandVerbs(raw: String): String {
        val stopWords = setOf(
            "weka", "niwekee", "fungua", "nifungulie", "cheza", "play", "open", "watch", "switch",
            "change", "download", "pakua", "nipakulie", "search", "find", "tell", "me", "about",
            "info", "details", "to", "on", "the", "a", "an", "for", "channel", "chaneli", "tv",
            "tafadhali", "please", "naomba", "nataka", "kuangalia", "kutazama", "movie", "movies",
            "filamu", "ya", "za", "wa", "la", "kwenye"
        )
        return normalizeText(raw)
            .split(" ")
            .filter { it.isNotBlank() && it !in stopWords }
            .joinToString(" ")
            .trim()
    }

    private fun normalizeText(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }

    internal fun buildNeliAiCenterPayload(
        query: String,
        inputMode: String,
        snapshot: NeliAiContextSnapshot
    ): JSONObject {
        return JSONObject().apply {
            put("app_id", if (snapshot.isSmartTv) "nelitv-tv" else "nelitv-android")
            put("platform", if (snapshot.isSmartTv) "android-tv" else "android")
            put("capabilities", JSONArray(listOf("live_tv", "movies", "series", "player_controls", "downloads", "brightness", "lock_screen")))
            put("input_mode", if (inputMode.equals("text", ignoreCase = true)) "text" else "voice")
            put("input_text", query)
            put("context", JSONObject().apply {
                put("active_channel_id", snapshot.activePlayerChannel?.id.orEmpty())
                put("active_channel_name", snapshot.activePlayerChannel?.name.orEmpty())
                put("is_premium", snapshot.isPremiumActive)
                put("is_outside_app", snapshot.isOutsideApp)
                put("live_channel_count", snapshot.liveChannels.size)
                put("media_catalog_count", snapshot.mediaCatalog.size)
                put("downloads_count", snapshot.downloadedMediaIds.size)
            })
        }
    }

    internal fun parseNeliAiCenterResponse(
        body: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? {
        if (body.isBlank()) return null
        return try {
            val json = JSONObject(body)
            val speechEn = json.optString("speech_text_en")
                .ifBlank { json.optString("speech_text") }
                .ifBlank { json.optString("replySpeech") }
            val displayText = json.optString("display_text")
                .ifBlank { json.optString("reply_text") }
                .ifBlank { json.optString("replyText") }
                .ifBlank { speechEn }
                .ifBlank { json.optString("message") }
            if (displayText.isBlank() && speechEn.isBlank()) return null

            val intent = json.optString("intent", "NELI_AI_CENTER")
            val confidence = json.optDouble("confidence", 0.95).toFloat()
            val actionObj = json.optJSONObject("action")
            val mappedAction = mapRemoteAction(actionObj, snapshot)
            val cards = when (mappedAction) {
                is NeliAiAction.PlayChannel -> listOf(NeliAiCardItem.ChannelCard(mappedAction.channel))
                is NeliAiAction.OpenMedia -> listOf(NeliAiCardItem.MediaCard(mappedAction.media))
                else -> emptyList()
            }
            NeliAiCommandResponse(
                intent = intent,
                confidence = confidence,
                replyText = displayText.ifBlank { speechEn },
                replySpeech = speechEn.ifBlank { displayText },
                sourceTag = "Neli AI Center",
                action = mappedAction,
                cards = cards,
                quickReplies = listOf("Next channel", "Volume up", "Suggest movies")
            )
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun tryQueryCustomRemoteAi(
        endpointUrl: String,
        apiKey: String,
        query: String,
        inputMode: String,
        snapshot: NeliAiContextSnapshot
    ): NeliAiCommandResponse? = withContext(Dispatchers.IO) {
        try {
            val payload = buildNeliAiCenterPayload(query, inputMode, snapshot)
            val request = Request.Builder()
                .url(endpointUrl)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("X-API-Key", apiKey)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                parseNeliAiCenterResponse(body, snapshot)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapRemoteAction(actionObj: JSONObject?, snapshot: NeliAiContextSnapshot): NeliAiAction {
        if (actionObj == null) return NeliAiAction.None
        return when (actionObj.optString("type").uppercase(Locale.ROOT)) {
            "PLAY_CHANNEL", "OPEN_CHANNEL", "SWITCH_CHANNEL" -> {
                val targetId = actionObj.optString("channel_id").ifBlank { actionObj.optString("channelId") }
                val targetName = actionObj.optString("channel_name")
                    .ifBlank { actionObj.optString("channelName") }
                    .ifBlank { actionObj.optString("target") }
                    .ifBlank { actionObj.optString("name") }
                val ch = snapshot.liveChannels.firstOrNull {
                    (targetId.isNotBlank() && it.id.equals(targetId, true)) ||
                        (targetName.isNotBlank() && it.name.equals(targetName, true))
                } ?: targetName.takeIf { it.isNotBlank() }?.let {
                    findBestChannelMatches(it, snapshot.liveChannels, 1).firstOrNull()?.first
                }
                if (ch != null) NeliAiAction.PlayChannel(ch) else NeliAiAction.None
            }
            "OPEN_MOVIE", "PLAY_MOVIE", "OPEN_SERIES", "PLAY_MEDIA" -> {
                val movieId = actionObj.optString("movie_id")
                    .ifBlank { actionObj.optString("movieId") }
                    .ifBlank { actionObj.optString("media_id") }
                val title = actionObj.optString("title")
                    .ifBlank { actionObj.optString("target") }
                    .ifBlank { actionObj.optString("name") }
                val media = snapshot.mediaCatalog.firstOrNull {
                    (movieId.isNotBlank() && it.id.equals(movieId, true)) ||
                        (title.isNotBlank() && it.title.equals(title, true))
                } ?: title.takeIf { it.isNotBlank() }?.let {
                    findBestMediaMatches(it, snapshot.mediaCatalog, 1).firstOrNull()?.first
                }
                if (media != null) {
                    val autoPlay = !actionObj.optString("type").equals("OPEN_SERIES", ignoreCase = true)
                    NeliAiAction.OpenMedia(media, autoPlay = autoPlay)
                } else {
                    NeliAiAction.None
                }
            }
            "NEXT_CHANNEL" -> NeliAiAction.SwitchNextChannel
            "PREV_CHANNEL", "PREVIOUS_CHANNEL" -> NeliAiAction.SwitchPreviousChannel
            "VOLUME_UP" -> NeliAiAction.AdjustVolume(NeliAiVolumeDirection.UP)
            "VOLUME_DOWN" -> NeliAiAction.AdjustVolume(NeliAiVolumeDirection.DOWN)
            "MUTE", "VOLUME_MUTE" -> NeliAiAction.AdjustVolume(NeliAiVolumeDirection.MUTE)
            "UNMUTE", "VOLUME_UNMUTE" -> NeliAiAction.AdjustVolume(NeliAiVolumeDirection.UNMUTE)
            "BRIGHTNESS_UP" -> NeliAiAction.AdjustBrightness(NeliAiBrightnessDirection.UP)
            "BRIGHTNESS_DOWN" -> NeliAiAction.AdjustBrightness(NeliAiBrightnessDirection.DOWN)
            "LOCK_SCREEN" -> NeliAiAction.LockDeviceScreen
            "OPEN_APP" -> NeliAiAction.OpenNeliTvApp
            "SET_QUALITY" -> {
                val q = actionObj.optString("quality").ifBlank { actionObj.optString("target") }.lowercase(Locale.ROOT)
                val mode = when {
                    q.contains("240") -> NetworkQualityMode.ULTRA_LOW_BANDO_SAVER
                    q.contains("360") -> NetworkQualityMode.WEAK_NETWORK_SAVER
                    q.contains("480") -> NetworkQualityMode.STANDARD_480P
                    q.contains("720") || q.contains("1080") || q.contains("hd") -> NetworkQualityMode.STRONG_NETWORK_HD
                    else -> NetworkQualityMode.AUTO_ADAPTIVE
                }
                NeliAiAction.SetQuality(mode)
            }
            "PAUSE", "PLAY", "TOGGLE_PLAY_PAUSE" -> NeliAiAction.TogglePlayPause
            "CLOSE_PLAYER", "EXIT_PLAYER", "STOP" -> NeliAiAction.ClosePlayer
            "NAVIGATE", "OPEN_TAB" -> {
                val tabName = actionObj.optString("tab").ifBlank { actionObj.optString("target") }.lowercase(Locale.ROOT)
                when {
                    tabName.contains("download") -> NeliAiAction.NavigateTab(BottomNavTab.DOWNLOAD)
                    tabName.contains("discovery") || tabName.contains("movie") -> NeliAiAction.NavigateTab(BottomNavTab.DISCOVERY)
                    tabName.contains("search") -> NeliAiAction.NavigateTab(BottomNavTab.SEARCH)
                    tabName.contains("account") -> NeliAiAction.NavigateTab(BottomNavTab.ACCOUNT)
                    else -> NeliAiAction.NavigateTab(BottomNavTab.HOME)
                }
            }
            "FILTER_CATEGORY" -> {
                val cat = actionObj.optString("category").ifBlank { actionObj.optString("target") }
                if (cat.isNotBlank()) NeliAiAction.FilterLiveCategory(cat) else NeliAiAction.None
            }
            else -> NeliAiAction.None
        }
    }
}
