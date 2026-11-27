package com.example.ui.tv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.ai.NeliAiEngine
import com.example.model.LiveChannel
import com.example.model.MediaContent
import com.example.ui.theme.NeliGenreCyan
import com.example.ui.theme.NeliMagenta
import com.example.ui.theme.NeliTextPrimary
import com.example.ui.theme.NeliTextSecondary
import java.util.Locale

enum class TvVoiceSearchFilter(val label: String) {
    ALL("All"),
    LIVE_TV("Live TV"),
    MOVIES("Movies"),
    SERIES("Series"),
    SWAHILI("DJ Swahili"),
    SPORTS("Sports"),
    KIDS("Kids")
}

data class TvVoiceSearchResults(
    val cleanedQuery: String,
    val channels: List<LiveChannel>,
    val media: List<MediaContent>
) {
    val totalCount: Int get() = channels.size + media.size
}

/**
 * Pure-Kotlin Android TV Voice Search matcher supporting natural spoken phrases in English & Swahili
 * (e.g., "search for azam sport one", "find action movies", "tafuta movie za kiswahili", "play mission zanzibar").
 */
object NeliTvVoiceSearchMatcher {

    private val spokenLeadingFillers = listOf(
        "hey neli ", "neli ", "search for ", "search ", "find me ", "find ",
        "show me ", "show ", "play me ", "play ", "watch ", "open ", "put on ",
        "tafuta ", "nitafutie ", "fungua ", "cheza ", "niwekee ", "nataka kuangalia ",
        "nataka ", "channel ya ", "chaneli ya ", "movie ya ", "muvi ya "
    )

    fun normalizeSpokenQuery(raw: String): String {
        var cleaned = raw.trim().replace(Regex("\\s+"), " ")
        var changed = true
        while (changed) {
            changed = false
            val lower = cleaned.lowercase(Locale.US)
            for (prefix in spokenLeadingFillers) {
                if (lower.startsWith(prefix) && cleaned.length > prefix.length) {
                    cleaned = cleaned.substring(prefix.length).trim()
                    changed = true
                    break
                }
            }
        }
        return cleaned
    }

    fun search(
        rawQuery: String,
        channels: List<LiveChannel>,
        mediaCatalog: List<MediaContent>,
        filter: TvVoiceSearchFilter = TvVoiceSearchFilter.ALL
    ): TvVoiceSearchResults {
        val cleaned = normalizeSpokenQuery(rawQuery)
        val qLower = cleaned.lowercase(Locale.US)
        val safeMedia = mediaCatalog.filter { !it.isAdult }

        val allowChannels = filter == TvVoiceSearchFilter.ALL ||
            filter == TvVoiceSearchFilter.LIVE_TV ||
            filter == TvVoiceSearchFilter.SPORTS ||
            filter == TvVoiceSearchFilter.KIDS
        val allowMedia = filter != TvVoiceSearchFilter.LIVE_TV

        val filteredChannelsPool = when (filter) {
            TvVoiceSearchFilter.SPORTS -> channels.filter { ch ->
                ch.category.contains("sport", true) ||
                    ch.name.contains("sport", true) ||
                    ch.categories.any { it.contains("sport", true) }
            }
            TvVoiceSearchFilter.KIDS -> channels.filter { ch ->
                ch.category.contains("kid", true) ||
                    ch.category.contains("cartoon", true) ||
                    ch.name.contains("kid", true) ||
                    ch.name.contains("disney", true) ||
                    ch.name.contains("nick", true)
            }
            else -> channels
        }

        val filteredMediaPool = when (filter) {
            TvVoiceSearchFilter.MOVIES -> safeMedia.filter { it.isMovie }
            TvVoiceSearchFilter.SERIES -> safeMedia.filter { it.isSeries }
            TvVoiceSearchFilter.SWAHILI -> safeMedia.filter {
                it.isSwahiliNarratedMovie ||
                    it.genre.contains("swahili", true) ||
                    it.genre.contains("tafsiri", true) ||
                    it.title.contains("dj", true)
            }
            TvVoiceSearchFilter.SPORTS -> safeMedia.filter {
                it.genre.contains("sport", true) || it.title.contains("sport", true)
            }
            TvVoiceSearchFilter.KIDS -> safeMedia.filter {
                it.genre.contains("kid", true) ||
                    it.genre.contains("animation", true) ||
                    it.genre.contains("family", true) ||
                    it.genre.contains("cartoon", true)
            }
            else -> safeMedia
        }

        if (qLower.isBlank()) {
            return TvVoiceSearchResults(
                cleanedQuery = "",
                channels = if (allowChannels) filteredChannelsPool.take(16) else emptyList(),
                media = if (allowMedia) filteredMediaPool.take(24) else emptyList()
            )
        }

        val matchedChannels: List<LiveChannel> = if (allowChannels) {
            val directMatches = filteredChannelsPool.filter { ch ->
                ch.name.contains(qLower, ignoreCase = true) ||
                    ch.category.contains(qLower, ignoreCase = true) ||
                    ch.currentProgram.contains(qLower, ignoreCase = true) ||
                    ch.categories.any { it.contains(qLower, ignoreCase = true) }
            }
            val fuzzyMatches = NeliAiEngine.findBestChannelMatches(
                rawQuery = cleaned,
                channels = filteredChannelsPool,
                limit = 12
            ).map { it.first }
            (directMatches + fuzzyMatches).distinctBy { it.id }.take(16)
        } else {
            emptyList()
        }

        val matchedMedia: List<MediaContent> = if (allowMedia) {
            val tokens = qLower.split(" ").filter { it.length >= 2 }
            val directMatches = filteredMediaPool.filter { m ->
                m.title.contains(qLower, ignoreCase = true) ||
                    m.genre.contains(qLower, ignoreCase = true) ||
                    m.synopsis.contains(qLower, ignoreCase = true) ||
                    m.cast.any { c -> c.name.contains(qLower, ignoreCase = true) } ||
                    (tokens.isNotEmpty() && tokens.all { t ->
                        m.title.contains(t, true) || m.genre.contains(t, true)
                    })
            }
            val fuzzyMatches = NeliAiEngine.findBestMediaMatches(
                rawQuery = cleaned,
                catalog = filteredMediaPool,
                limit = 16
            ).map { it.first }
            (directMatches + fuzzyMatches).distinctBy { it.id }.take(24)
        } else {
            emptyList()
        }

        return TvVoiceSearchResults(
            cleanedQuery = cleaned,
            channels = matchedChannels,
            media = matchedMedia
        )
    }
}

/**
 * Dedicated Android TV 10-Foot Voice Search Interface tailored for TV remotes (D-Pad + Remote Mic):
 * - Prominent D-Pad focusable Voice Mic Orb (auto-focused on launch)
 * - Uses Android TV's native [RecognizerIntent.ACTION_RECOGNIZE_SPEECH] with automatic
 *   in-app [SpeechRecognizer] fallback when no system speech dialog is installed
 * - Spoken quick-phrase chips, category filter tabs, and D-Pad focusable Live TV & Movie results
 */
@Composable
fun NeliTvVoiceSearchScreen(
    channels: List<LiveChannel>,
    mediaCatalog: List<MediaContent>,
    initialQuery: String = "",
    autoTriggerVoiceTick: Int = 0,
    onQueryChanged: (String) -> Unit = {},
    onChannelSelected: (LiveChannel) -> Unit,
    onMediaSelected: (MediaContent) -> Unit,
    onPlayMediaDirectly: (MediaContent) -> Unit = onMediaSelected
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val micFocusRequester = remember { FocusRequester() }

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var selectedFilter by remember { mutableStateOf(TvVoiceSearchFilter.ALL) }
    var isListeningDirect by remember { mutableStateOf(false) }
    var livePartialTranscript by remember { mutableStateOf("") }
    var voiceStatusMessage by remember {
        mutableStateOf("Press OK on your TV remote mic button and say any channel or movie")
    }
    var directRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun updateQuery(newText: String) {
        query = newText
        onQueryChanged(newText)
    }

    fun stopDirectListening() {
        runCatching {
            directRecognizer?.stopListening()
            directRecognizer?.destroy()
        }
        directRecognizer = null
        isListeningDirect = false
        livePartialTranscript = ""
    }

    fun startDirectSpeechFallback() {
        val externalSpeechComponent = com.example.ai.NeliAiVoiceController.resolveExternalSpeechComponent(context)
        if (externalSpeechComponent == null) {
            voiceStatusMessage = "Voice service unavailable on this TV — use quick voice chips or type below"
            return
        }
        stopDirectListening()
        runCatching {
            val sr = SpeechRecognizer.createSpeechRecognizer(context, externalSpeechComponent)
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningDirect = true
                    voiceStatusMessage = "Listening to your TV remote... Speak now"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    voiceStatusMessage = "Finding channels & movies..."
                }

                override fun onError(error: Int) {
                    isListeningDirect = false
                    livePartialTranscript = ""
                    voiceStatusMessage = "Didn't catch that — press OK on the mic to try again"
                }

                override fun onResults(results: Bundle?) {
                    isListeningDirect = false
                    livePartialTranscript = ""
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        updateQuery(spoken)
                        voiceStatusMessage = "Showing results for \"$spoken\""
                    } else {
                        voiceStatusMessage = "No voice input detected — press OK to speak again"
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        livePartialTranscript = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            directRecognizer = sr
            isListeningDirect = true
            sr.startListening(intent)
        }.onFailure {
            isListeningDirect = false
            voiceStatusMessage = "Unable to start TV microphone — please check microphone permission"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startDirectSpeechFallback()
        } else {
            voiceStatusMessage = "Microphone permission is needed for TV voice search"
        }
    }

    val tvSpeechIntentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotBlank()) {
                updateQuery(spoken)
                voiceStatusMessage = "Voice search: \"$spoken\""
            }
        }
    }

    fun launchRemoteVoiceSearch() {
        if (isListeningDirect) {
            stopDirectListening()
            voiceStatusMessage = "Press OK on the mic to speak again"
            return
        }
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak a channel or movie name on NeliTv")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
        val canResolveSystemVoice = speechIntent.resolveActivity(context.packageManager) != null
        if (canResolveSystemVoice) {
            val launched = runCatching {
                tvSpeechIntentLauncher.launch(speechIntent)
                true
            }.getOrDefault(false)
            if (launched) return
        }
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            startDirectSpeechFallback()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopDirectListening() }
    }

    LaunchedEffect(Unit) {
        runCatching { micFocusRequester.requestFocus() }
    }

    LaunchedEffect(autoTriggerVoiceTick) {
        if (autoTriggerVoiceTick > 0) {
            launchRemoteVoiceSearch()
        }
    }

    val results = remember(query, channels, mediaCatalog, selectedFilter) {
        NeliTvVoiceSearchMatcher.search(
            rawQuery = query,
            channels = channels,
            mediaCatalog = mediaCatalog,
            filter = selectedFilter
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "tv_voice_pulse")
    val micPulseScale by pulseTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListeningDirect) 1.14f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListeningDirect) 520 else 1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tv_mic_scale"
    )

    val quickVoicePrompts = remember {
        listOf(
            "Azam Sports 1",
            "Azam Two",
            "Swahili Movies",
            "Action",
            "Comedy",
            "Kids",
            "Series",
            "News"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060919))
            .testTag("tv_voice_search_screen"),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Hero 10-Foot Remote Voice Search Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF121833), Color(0xFF1C1030), Color(0xFF0D1729))
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(NeliGenreCyan, NeliMagenta)),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    // Large D-Pad Focusable Remote Voice Mic Button
                    Box(
                        modifier = Modifier
                            .focusRequester(micFocusRequester)
                            .scale(micPulseScale)
                            .tvGeminiFocusable(
                                shape = CircleShape,
                                focusScale = 1.12f,
                                borderWidth = 3.dp,
                                onClick = { launchRemoteVoiceSearch() }
                            )
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    if (isListeningDirect) {
                                        listOf(Color(0xFF38BDF8), Color(0xFF2563EB))
                                    } else {
                                        listOf(NeliMagenta, Color(0xFF7C3AED))
                                    }
                                )
                            )
                            .testTag("tv_voice_search_mic_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isListeningDirect) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Speak with TV Remote",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (isListeningDirect) "Listening to TV Remote..." else "Android TV Voice Search",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Surface(
                                color = if (isListeningDirect) Color(0xFF0284C7) else Color(0x3300D4FF),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = if (isListeningDirect) "MIC LIVE" else "REMOTE MIC READY",
                                    color = if (isListeningDirect) Color.White else NeliGenreCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = if (livePartialTranscript.isNotBlank()) {
                                "\"$livePartialTranscript\""
                            } else {
                                voiceStatusMessage
                            },
                            color = if (livePartialTranscript.isNotBlank()) NeliGenreCyan else Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Optional Remote / On-Screen Keyboard Search Input
                        OutlinedTextField(
                            value = query,
                            onValueChange = { updateQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tv_voice_search_input"),
                            placeholder = {
                                Text(
                                    text = "Say or type channel, movie, DJ Swahili, or sport...",
                                    color = NeliTextSecondary,
                                    fontSize = 14.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = NeliGenreCyan
                                )
                            },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = { updateQuery("") },
                                        modifier = Modifier.testTag("tv_voice_search_clear_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = Color.White
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeliGenreCyan,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0A0F1F),
                                unfocusedContainerColor = Color(0xFF0A0F1F),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
            }
        }

        // 2. Spoken Quick-Phrase Suggestions (1-Click Remote OK)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TRY SAYING OR SELECTING",
                    color = NeliGenreCyan,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(quickVoicePrompts) { phrase ->
                        val isSelected = query.equals(phrase, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .tvGeminiFocusable(
                                    shape = CircleShape,
                                    focusScale = 1.08f,
                                    borderWidth = 2.dp,
                                    onClick = {
                                        updateQuery(phrase)
                                        voiceStatusMessage = "Showing results for \"$phrase\""
                                    }
                                )
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) NeliMagenta.copy(alpha = 0.35f) else Color(0xFF14192B)
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) NeliMagenta else Color(0xFF2D3748),
                                    CircleShape
                                )
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("tv_voice_chip_${phrase.lowercase().replace(" ", "_")}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "\"$phrase\"",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 3. Category Filter Pills
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(TvVoiceSearchFilter.entries) { filter ->
                    val active = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .tvGeminiFocusable(
                                shape = RoundedCornerShape(12.dp),
                                focusScale = 1.07f,
                                borderWidth = 2.dp,
                                onClick = { selectedFilter = filter }
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) NeliGenreCyan else Color(0xFF121626))
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                            .testTag("tv_voice_filter_${filter.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = filter.label,
                            color = if (active) Color.Black else Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 4. Live TV Channels Results Row
        if (results.channels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = NeliMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (results.cleanedQuery.isBlank()) {
                                "Popular Live TV Channels (${results.channels.size})"
                            } else {
                                "Matching Live TV Channels (${results.channels.size})"
                            },
                            color = NeliTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        items(results.channels, key = { "tv_ch_${it.id}" }) { ch ->
                            TvVoiceChannelCard(
                                channel = ch,
                                onClick = { onChannelSelected(ch) }
                            )
                        }
                    }
                }
            }
        }

        // 5. Movies & Series Results Grid (Chunked Rows for Smooth 10-Foot D-Pad Navigation)
        if (results.media.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = NeliGenreCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (results.cleanedQuery.isBlank()) {
                            "Featured Movies & Series (${results.media.size})"
                        } else {
                            "Matching Movies & Series (${results.media.size})"
                        },
                        color = NeliTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            val mediaRows = results.media.chunked(5)
            items(mediaRows.size) { rowIdx ->
                val rowItems = mediaRows[rowIdx]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { media ->
                        TvVoiceMediaCard(
                            media = media,
                            onClick = {
                                if (media.isMovie && media.streamUrl.isNotBlank()) {
                                    onPlayMediaDirectly(media)
                                } else {
                                    onMediaSelected(media)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat((5 - rowItems.size).coerceAtLeast(0)) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 6. Empty State with 1-Click Voice Retry
        if (results.totalCount == 0) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF101528))
                        .border(1.dp, Color(0xFF232C47), RoundedCornerShape(20.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "No channels or movies matched \"${results.cleanedQuery}\"",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Try speaking a shorter name like \"Azam Sports 1\", \"Action\", or \"Swahili Movies\".",
                            color = NeliTextSecondary,
                            fontSize = 13.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .tvGeminiFocusable(
                                    shape = CircleShape,
                                    focusScale = 1.08f,
                                    borderWidth = 2.dp,
                                    onClick = { launchRemoteVoiceSearch() }
                                )
                                .clip(CircleShape)
                                .background(NeliGenreCyan)
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .testTag("tv_voice_retry_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                Text("Speak Again", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TvVoiceChannelCard(
    channel: LiveChannel,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(210.dp)
            .tvGeminiFocusable(
                shape = RoundedCornerShape(16.dp),
                focusScale = 1.07f,
                borderWidth = 2.5.dp,
                onClick = onClick
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13192C))
            .border(1.dp, Color(0xFF25304F), RoundedCornerShape(16.dp))
            .testTag("tv_voice_channel_card_${channel.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
                    .background(Color(0xFF0A0E1A))
            ) {
                if (channel.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = channel.thumbnailUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = Color(0xFFE11D48),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xCC000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${channel.name}",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = channel.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = channel.category.ifBlank { "Live TV" },
                    color = NeliTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TvVoiceMediaCard(
    media: MediaContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .tvGeminiFocusable(
                shape = RoundedCornerShape(16.dp),
                focusScale = 1.07f,
                borderWidth = 2.5.dp,
                onClick = onClick
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF13192C))
            .border(1.dp, Color(0xFF25304F), RoundedCornerShape(16.dp))
            .testTag("tv_voice_media_card_${media.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(Color(0xFF0A0E1A))
            ) {
                val poster = media.posterUrl.ifBlank { media.backdropUrl }
                if (poster.isNotBlank()) {
                    AsyncImage(
                        model = poster,
                        contentDescription = media.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val badge = when {
                    media.isSwahiliNarratedMovie -> "TAFSIRI"
                    media.isSeries -> "SERIES"
                    else -> media.releaseYear.ifBlank { "HD" }
                }
                Surface(
                    color = Color(0xCC0B1020),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = badge,
                        color = NeliGenreCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = media.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = media.primaryGenre.ifBlank { media.genre.ifBlank { "Movie" } },
                    color = NeliTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
