package com.example.ai

import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

enum class NeliCommandCategory {
    CHANNEL_AND_MOVIE,
    APP_AND_PERSONAL,
    SUGGEST_SEARCH_DOWNLOAD
}

data class NeliCommandTemplate(
    val canonicalQuery: String,
    val intentKey: String,
    val category: NeliCommandCategory
)

data class ClosestCommandMatch(
    val template: NeliCommandTemplate,
    val similarityScore: Float
)

/**
 * Production Neli AI Grammar & Command Knowledge Base:
 * - 55+ natural wake-word greetings ("Hey Neli" / "Neli").
 * - 1,500+ compositional voice commands across:
 *   1. Channel & Movie playback / discovery (150+ commands)
 *   2. App, Smart TV, Device & Personal Data controls (150+ commands)
 *   3. Suggestions, Search, Movie Info, Offline & Smart Batch Downloads (1,200+ commands)
 * - Fast fuzzy closest-command matcher so misspellings & Swahili/English variations still work.
 */
object NeliAiCommandCatalog {

    val wakeGreetings: List<String> = listOf(
        "I'm here to help! What do you need to watch?",
        "Yes! I'm listening. What would you like to watch?",
        "Hey! Neli is right here. Name a channel or movie!",
        "I'm ready! Want live TV, a movie, or a quick download?",
        "Right here for you! What should we play today?",
        "Hi there! Tell me what you feel like watching.",
        "Listening! Say a channel name, movie, or ask for suggestions.",
        "I'm here! Ready for football, movies, or live TV?",
        "Hey friend! How can I help you right now?",
        "At your service! What do you need to watch?",
        "Neli is awake! Just say the word.",
        "Hello! Want me to suggest some awesome movies?",
        "I'm all ears! Which channel or movie shall we open?",
        "Yes, I'm here! Need live sports or a fresh movie?",
        "Hey! Tell me what to play, download, or search.",
        "Ready when you are! What's on your mind?",
        "Hi! Looking for something fun to watch?",
        "Neli here! Say play, open, suggest, or download.",
        "I'm listening closely! What can I do for you?",
        "Here to help! Want to switch channels or grab movies for later?",
        "Hey there! Let's find something great to stream.",
        "Yes! Tell me the channel, movie, or control you want.",
        "I'm on it! What would you like to see?",
        "Hello! Live TV or movies today?",
        "Neli is listening! Go ahead with your request.",
        "Always ready! What can I pull up for you?",
        "Hey! Want me to recommend top movies or open a live channel?",
        "I'm right here! Speak your command.",
        "Hi! Need volume, brightness, a channel, or a movie?",
        "Ready to roll! What are we watching?",
        "Yes, I hear you! What do you need?",
        "Hey! Ask me for movie suggestions or live sports.",
        "Neli at your command! What shall we do?",
        "I'm here! Want me to download movies for offline viewing?",
        "Hello there! Which show or channel should I find?",
        "Listening now! Say any channel or movie title.",
        "Hey! I can open channels, suggest movies, or adjust your player.",
        "Right here! What do you feel like streaming?",
        "Yes! Go ahead, I'm listening.",
        "Hi! Ready for some entertainment?",
        "Neli is here! Tell me what you want to watch or download.",
        "All set! What's your command?",
        "Hey! Want to catch live TV or discover a new film?",
        "I'm listening! Ask me anything about NeliTv.",
        "Here for you! Should we play a channel or pick a movie?",
        "Hello! Tell me what you'd like me to do.",
        "Neli ready! Want action, comedy, Swahili movies, or sports?",
        "Yes! Just tell me what to open or download.",
        "Hey there! I'm tuned in and ready to help.",
        "Hi! Need a quick recommendation or live channel?",
        "I'm right here! What's next on your watchlist?",
        "Listening! Say the name of any movie or live channel.",
        "Hey! Ready to make your viewing effortless.",
        "Yes! How can Neli help you right now?",
        "Hello! Say open, play, suggest, download, or search!"
    )

    val variedQuestionResponses: Map<String, List<String>> = mapOf(
        "HOW_ARE_YOU" to listOf(
            "I'm doing awesome and ready to stream with you!",
            "Feeling super fast today! Ready to find your next favorite movie or channel.",
            "I'm great, thank you for asking! What would you like to watch?",
            "Doing wonderful! All NeliTv channels and movies are ready for you."
        ),
        "THANKS" to listOf(
            "You're very welcome! Enjoy watching on NeliTv.",
            "Anytime! Just say Hey Neli whenever you need me.",
            "My pleasure! Have a great time watching.",
            "Happy to help! Call me anytime you want another movie or channel."
        ),
        "WHO_MADE_YOU" to listOf(
            "I was built by the NeliTv team as your hands-free voice assistant for mobile and Smart TV!",
            "I'm Neli AI, created specially for NeliTv so you can control live TV, movies, and downloads just with your voice."
        ),
        "JOKES_FUN" to listOf(
            "Why did the movie go to the gym? Because it wanted to become a blockbuster! Now, want me to suggest a comedy?",
            "I tried to pause live football once, and the referee gave me a yellow card! Want to open sports instead?"
        )
    )

    fun pickWakeGreeting(seed: Int = Random.nextInt()): String {
        val idx = (seed and Int.MAX_VALUE) % wakeGreetings.size
        return wakeGreetings[idx]
    }

    fun pickVariedReply(key: String): String? {
        val list = variedQuestionResponses[key] ?: return null
        return list[Random.nextInt(list.size)]
    }

    /**
     * Compositional catalog of 1,500+ concrete command phrases covering channels, movies,
     * app/device/personal commands, and suggestion/search/download/outside-app flows.
     */
    val allCommands: List<NeliCommandTemplate> by lazy {
        buildList {
            // 1. Channel & Movie Commands (180+ entries)
            val channelOpenVerbs = listOf("open", "play", "watch", "switch to", "put on", "fungua", "weka", "cheza", "niwekee")
            val popularChannels = listOf(
                "azam one", "azam two", "azam sports 1", "azam sports 2", "azam sports 3", "azam sports 4",
                "sinema zetu", "wasafi tv", "utv", "zbc", "zbc2", "tbc 1", "tom and jerry",
                "wwe", "supersport", "epl", "espn", "bein sports", "mbc 2", "movie box"
            )
            channelOpenVerbs.forEach { verb ->
                popularChannels.forEach { ch ->
                    add(NeliCommandTemplate("$verb $ch", "PLAY_CHANNEL", NeliCommandCategory.CHANNEL_AND_MOVIE))
                }
            }
            val playerNavPhrases = listOf(
                "next channel" to "NEXT_CHANNEL",
                "change channel" to "NEXT_CHANNEL",
                "channel inayofuata" to "NEXT_CHANNEL",
                "badili channel" to "NEXT_CHANNEL",
                "open different channel" to "NEXT_CHANNEL",
                "previous channel" to "PREV_CHANNEL",
                "channel iliyopita" to "PREV_CHANNEL",
                "go back channel" to "PREV_CHANNEL",
                "whats on now" to "EPG_OVERVIEW",
                "sasa hivi kuna nini" to "EPG_OVERVIEW",
                "live schedule" to "EPG_OVERVIEW",
                "show sports channels" to "CATEGORY_SPORTS",
                "channel za mpira" to "CATEGORY_SPORTS",
                "show kids channels" to "CATEGORY_KIDS",
                "channel za katuni" to "CATEGORY_KIDS",
                "show news channels" to "CATEGORY_NEWS",
                "show music channels" to "CATEGORY_MUSIC",
                "show movie channels" to "CATEGORY_MOVIES",
                "change to best movie" to "PLAY_BEST_MOVIE",
                "play best movie" to "PLAY_BEST_MOVIE"
            )
            playerNavPhrases.forEach { (phrase, intent) ->
                add(NeliCommandTemplate(phrase, intent, NeliCommandCategory.CHANNEL_AND_MOVIE))
            }

            // 2. App, Device, Smart TV & Personal Data Commands (160+ entries)
            val appDevicePairs = listOf(
                // Volume
                "volume up", "increase volume", "turn up volume", "raise volume", "louder", "increase voice",
                "ongeza sauti", "pandisha sauti", "sauti juu",
                "volume down", "decrease volume", "turn down volume", "lower volume", "quieter", "decrease voice",
                "punguza sauti", "shusha sauti", "sauti chini",
                "mute", "mute audio", "silence", "zima sauti", "nyamazisha",
                "unmute", "restore sound", "washa sauti", "rudisha sauti",
                // Brightness
                "brightness up", "increase brightness", "brighter", "more brightness", "screen brighter",
                "ongeza mwanga", "pandisha mwanga", "mwanga juu",
                "brightness down", "decrease brightness", "dim screen", "less brightness", "darker",
                "punguza mwanga", "shusha mwanga", "mwanga chini",
                "max brightness", "full brightness", "mwanga mwisho",
                "low brightness", "minimum brightness", "night brightness", "auto brightness",
                // Quality
                "set quality 240p", "240p", "low bando", "ultra saver",
                "set quality 360p", "360p", "data saver", "okoa bando", "decrease quality", "lower quality", "punguza quality",
                "set quality 480p", "480p", "normal quality", "standard quality",
                "set quality 720p", "720p", "hd quality", "increase quality", "higher quality", "ongeza quality",
                "set quality 1080p", "1080p", "full hd", "best quality", "high quality",
                "auto quality", "automatic quality", "youtube quality",
                // Player controls
                "pause", "pause video", "simamisha", "resume", "continue", "endelea",
                "close player", "exit player", "stop watching", "funga video", "toka player",
                // Lock screen
                "lock my screen", "lock screen", "lock phone", "lock device", "turn off screen",
                "funga kioo", "zima kioo", "loki simu", "funga simu",
                // Open NeliTv only
                "open nelitv", "open nelitv app", "launch nelitv", "start nelitv", "go to nelitv",
                "fungua nelitv", "fungua app ya nelitv", "ingia nelitv",
                // Navigation tabs
                "open downloads", "my downloads", "downloaded movies", "fungua downloads", "nilizopakua",
                // Delete downloads
                "delete all downloads", "delete all my downloads", "remove all downloads", "clear all downloads",
                "clear my downloads", "delete a download", "remove a download",
                "futa downloads zote", "futa download zote", "ondoa downloads zote", "safisha downloads", "futa download",
                "open discovery", "open movies", "browse movies", "fungua discovery", "fungua filamu",
                "open home", "go home", "live tv home", "ukurasa wa mwanzo",
                "open account", "my account", "my profile", "akaunti yangu",
                "open search", "search nelitv", "tafuta",
                // Personal data & app status
                "who am i", "my profile info", "jina langu nani", "my name",
                "my vip status", "am i vip", "check subscription", "hali ya vip", "malipo yangu",
                "my watch history", "what did i watch", "my favorite channels", "historia yangu",
                "how many downloads do i have", "my downloaded count", "downloads ngapi",
                "how many channels", "chaneli ngapi", "how many movies", "movie ngapi",
                "channel health", "check channels", "repair channels", "kagua chaneli",
                "who are you", "what can you do", "help", "wewe ni nani", "msaada",
                "how are you", "habari yako", "thank you", "asante", "tell me a joke",
                "what time is it", "saa ngapi", "am i online", "network status",
                "smart tv info", "nelitv online movies", "where do movies come from",
                "enable hands free", "disable hands free", "voice settings", "neli ai settings"
            )
            // Expand with polite prefixes to reach 160+ app/personal commands
            appDevicePairs.forEach { phrase ->
                val intentKey = when {
                    phrase.contains("brightness") || phrase.contains("mwanga") || phrase.contains("dim") || phrase.contains("brighter") || phrase.contains("darker") -> "ADJUST_BRIGHTNESS"
                    phrase.contains("volume") || phrase.contains("sauti") || phrase.contains("louder") || phrase.contains("quieter") || phrase.contains("mute") || phrase.contains("voice") -> "ADJUST_VOLUME"
                    phrase.contains("quality") || phrase.contains("240p") || phrase.contains("360p") || phrase.contains("480p") || phrase.contains("720p") || phrase.contains("1080p") || phrase.contains("hd") || phrase.contains("bando") -> "SET_QUALITY"
                    phrase.contains("lock") || phrase.contains("kioo") || phrase.contains("loki") || phrase.contains("funga simu") -> "LOCK_SCREEN"
                    phrase.contains("nelitv") && (phrase.contains("open") || phrase.contains("launch") || phrase.contains("start") || phrase.contains("fungua") || phrase.contains("ingia") || phrase.contains("go to")) -> "OPEN_NELITV"
                    phrase.contains("delete") || phrase.contains("remove") || phrase.contains("clear") || phrase.contains("futa") || phrase.contains("ondoa") || phrase.contains("safisha") -> "DELETE_DOWNLOADS"
                    phrase.contains("download") || phrase.contains("nilizopakua") -> "NAV_DOWNLOADS"
                    phrase.contains("vip") || phrase.contains("subscription") || phrase.contains("malipo") -> "VIP_STATUS"
                    phrase.contains("history") || phrase.contains("favorite") || phrase.contains("historia") -> "PERSONAL_STATS"
                    phrase.contains("who am i") || phrase.contains("profile") || phrase.contains("jina langu") -> "PERSONAL_PROFILE"
                    phrase.contains("health") || phrase.contains("kagua") || phrase.contains("repair") -> "CHANNEL_HEALTH"
                    else -> "APP_CONTROL"
                }
                add(NeliCommandTemplate(phrase, intentKey, NeliCommandCategory.APP_AND_PERSONAL))
            }
            listOf("please ", "can you ", "tafadhali ").forEach { prefix ->
                appDevicePairs.take(25).forEach { phrase ->
                    add(NeliCommandTemplate("$prefix$phrase", "APP_CONTROL", NeliCommandCategory.APP_AND_PERSONAL))
                }
            }

            // 3. Suggestion, Search, Movie Info & Smart Batch Download Commands (1,200+ entries!)
            val suggestVerbs = listOf(
                "suggest", "suggest me", "recommend", "recommend me", "pick", "pick me",
                "find", "find me", "show", "show me", "give me", "nipendekezee", "pendekeza", "nitafutie"
            )
            val suggestModifiers = listOf(
                "best", "top", "good", "random", "trending", "new", "2026", "swahili",
                "action", "comedy", "horror", "romance", "drama", "thriller", "sci fi", "animation"
            )
            val suggestNouns = listOf(
                "movie", "movies", "movie to watch", "movies to watch", "film", "filamu", "series"
            )
            // 14 * 16 * 7 = 1,568 rich suggestion & discovery patterns -> take a balanced 700+ subset
            suggestVerbs.forEach { v ->
                suggestModifiers.forEach { m ->
                    suggestNouns.take(4).forEach { n ->
                        add(NeliCommandTemplate("$v $m $n", "SUGGEST_MOVIES", NeliCommandCategory.SUGGEST_SEARCH_DOWNLOAD))
                    }
                }
            }

            // Smart batch download commands: 6 verbs * 10 counts * 9 modifiers = 540+ download patterns!
            val downloadVerbs = listOf("download", "download me", "save", "get me", "pakua", "nipakulie")
            val downloadCounts = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
            val downloadStyles = listOf(
                "random movies", "movies for watching later", "new movies", "best movies",
                "swahili movies", "action movies", "comedy movies", "horror movies", "unwatched movies"
            )
            downloadVerbs.forEach { dv ->
                downloadCounts.forEach { cnt ->
                    downloadStyles.forEach { style ->
                        add(NeliCommandTemplate("$dv $cnt $style", "DOWNLOAD_RANDOM_MOVIES", NeliCommandCategory.SUGGEST_SEARCH_DOWNLOAD))
                    }
                }
            }

            // Movie search, info & sequential playback patterns (60+ entries)
            val infoAndQueuePhrases = listOf(
                "search movie", "search for movie", "find movie info", "tell me about movie",
                "movie info", "movie details", "what is this movie about", "taarifa za movie",
                "play selected movies", "play them in sequence", "play in sequence", "cheza kwa mpangilio",
                "download selected movies", "download all recommended", "pakua zote",
                "suggest movies for later", "download movies while locked", "download best movie",
                "suggest me something to watch", "what should i watch tonight", "niangalie nini leo"
            )
            infoAndQueuePhrases.forEach { p ->
                val intent = when {
                    p.contains("sequence") || p.contains("mpangilio") -> "PLAY_SEQUENCE"
                    p.contains("download") || p.contains("pakua") -> "DOWNLOAD_RANDOM_MOVIES"
                    p.contains("info") || p.contains("about") || p.contains("details") || p.contains("taarifa") || p.contains("search") -> "SEARCH_MOVIE_INFO"
                    else -> "SUGGEST_MOVIES"
                }
                add(NeliCommandTemplate(p, intent, NeliCommandCategory.SUGGEST_SEARCH_DOWNLOAD))
            }
        }
    }

    val totalCommandCount: Int
        get() = allCommands.size

    fun countByCategory(category: NeliCommandCategory): Int =
        allCommands.count { it.category == category }

    /**
     * Finds the closest known command when a user's spoken sentence has misspellings,
     * ASR artifacts, or unusual phrasing.
     */
    fun findClosestCommand(rawCleanedQuery: String): ClosestCommandMatch? {
        val norm = normalize(rawCleanedQuery)
        if (norm.length < 3) return null
        val queryTokens = norm.split(" ").filter { it.length >= 2 }
        if (queryTokens.isEmpty()) return null

        var bestTemplate: NeliCommandTemplate? = null
        var bestScore = 0f

        for (template in allCommands) {
            val cand = template.canonicalQuery
            if (cand == norm) {
                return ClosestCommandMatch(template, 1.0f)
            }
            val candTokens = cand.split(" ").filter { it.length >= 2 }
            if (candTokens.isEmpty()) continue

            var matched = 0f
            for (qt in queryTokens) {
                var tokenBest = 0f
                for (ct in candTokens) {
                    if (qt == ct) {
                        tokenBest = 1f
                        break
                    } else if (qt.length >= 3 && ct.length >= 3) {
                        val dist = levenshtein(qt, ct)
                        val maxLen = maxOf(qt.length, ct.length)
                        val sim = 1f - (dist.toFloat() / maxLen.toFloat())
                        if (sim >= 0.72f && sim > tokenBest) {
                            tokenBest = sim
                        }
                    }
                }
                matched += tokenBest
            }
            val unionSize = (queryTokens.size + candTokens.size - matched).coerceAtLeast(1f)
            val jaccardLike = matched / unionSize
            val coverage = matched / queryTokens.size.toFloat()
            val score = (jaccardLike * 0.55f) + (coverage * 0.45f)
            if (score > bestScore) {
                bestScore = score
                bestTemplate = template
            }
        }

        return if (bestTemplate != null && bestScore >= 0.52f) {
            ClosestCommandMatch(bestTemplate, bestScore)
        } else {
            null
        }
    }

    private fun normalize(input: String): String {
        return input.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
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
}
