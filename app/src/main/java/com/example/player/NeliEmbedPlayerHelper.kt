package com.example.player

import java.util.Locale

/**
 * Helper for detecting YouTube and Baynith TV embed URLs and IDs configured in Movie Studio,
 * converting any YouTube link, video ID, Baynith TV share ID (`BTV-...`), or legacy `/embed-neli/`
 * URL into the official Baynith TV `/embed-video/<VIDEO_ID>` player:
 * `https://baynith-tv.base44.app/embed-video/<VIDEO_ID>`
 */
object NeliEmbedPlayerHelper {

    const val BAYNITH_EMBED_VIDEO_BASE_URL = "https://baynith-tv.base44.app/embed-video/"
    const val NELI_EMBED_BASE_URL = BAYNITH_EMBED_VIDEO_BASE_URL
    const val NELI_EMBED_ORIGIN = "https://baynith-tv.base44.app"

    private val YOUTUBE_ID_REGEX = Regex(
        """(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtube-nocookie\.com/embed/|baynith-tv\.base44\.app/(?:embed-video|embed-neli|video)/|[?&]v=)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val SHARE_ID_REGEX = Regex(
        """(?:baynith-tv\.base44\.app/(?:embed-video|embed-neli|video)/|^)(BTV-[A-Za-z0-9_-]+)(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val BAYNITH_RECORD_ID_REGEX = Regex(
        """baynith-tv\.base44\.app/(?:embed-video|embed-neli|video)/([A-Za-z0-9_-]{6,64})(?:[/?#]|$)""",
        RegexOption.IGNORE_CASE
    )

    private val PLAYLIST_ID_REGEX = Regex(
        """[?&]list=([A-Za-z0-9_-]{12,64})""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_YOUTUBE_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * Extracts the bare 11-character YouTube video ID, `BTV-...` Baynith TV share ID,
     * or Baynith TV record ID from any YouTube URL, `/embed-video/` URL, legacy `/embed-neli/` URL,
     * iframe snippet, or bare ID string.
     */
    fun extractYoutubeVideoId(rawInput: String?): String {
        val clean = rawInput?.trim().orEmpty()
        if (clean.isEmpty()) return ""

        SHARE_ID_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { matched ->
            if (matched.isNotBlank()) return matched
        }

        YOUTUBE_ID_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { matched ->
            if (matched.isNotBlank()) return matched
        }

        BAYNITH_RECORD_ID_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { matched ->
            if (matched.isNotBlank()) return matched
        }

        if (!clean.contains("://") && !clean.contains("/") && BARE_YOUTUBE_ID_REGEX.matches(clean)) {
            return clean
        }

        // Handle iframe HTML snippets pasted directly into Studio URL fields
        if (clean.contains("<iframe", ignoreCase = true)) {
            val srcMatch = Regex("""src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(clean)
            val srcVal = srcMatch?.groupValues?.getOrNull(1).orEmpty()
            if (srcVal.isNotBlank()) {
                return extractYoutubeVideoId(srcVal)
            }
        }

        return ""
    }

    /**
     * Extracts a YouTube playlist ID (`PL...`, `UU...`, `OLAK...`) from a URL or bare playlist ID.
     */
    fun extractYoutubePlaylistId(rawInput: String?): String {
        val clean = rawInput?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        PLAYLIST_ID_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { matched ->
            if (matched.isNotBlank()) return matched
        }
        if (!clean.contains("://") && !clean.contains("/") &&
            (clean.startsWith("PL") || clean.startsWith("UU") || clean.startsWith("OLAK")) &&
            clean.length >= 12
        ) {
            return clean
        }
        return ""
    }

    /**
     * Returns true if [rawUrl] points to YouTube or to the Baynith TV `/embed-video/` (or legacy `/embed-neli/`) player.
     */
    fun isYoutubeOrEmbedNeliUrl(rawUrl: String?): Boolean {
        val clean = rawUrl?.trim()?.lowercase(Locale.US).orEmpty()
        if (clean.isEmpty()) return false
        return clean.contains("youtube.com/") ||
            clean.contains("youtube.com\\") ||
            clean.contains("youtu.be/") ||
            clean.contains("youtube-nocookie.com/") ||
            clean.contains("baynith-tv.base44.app/embed-video/") ||
            clean.contains("baynith-tv.base44.app/embed-neli/") ||
            clean.contains("baynith-tv.base44.app/video/") ||
            clean.contains("/embed-video/") ||
            clean.contains("/embed-neli/")
    }

    /**
     * Determines whether a Studio media record uses YouTube / Baynith TV embed video playback.
     */
    fun isYoutubeOrEmbedNeliSource(
        streamUrl: String = "",
        youtubeVideoId: String = "",
        youtubeUrl: String = "",
        playbackType: String = ""
    ): Boolean {
        if (youtubeVideoId.isNotBlank()) return true
        if (isYoutubeOrEmbedNeliUrl(streamUrl) || isYoutubeOrEmbedNeliUrl(youtubeUrl)) return true
        val normType = playbackType.trim().lowercase(Locale.US)
        if (normType in setOf("youtube", "yt", "embed-video", "embed-neli", "neli-embed", "baynith-video", "youtube_embed")) {
            return true
        }
        return false
    }

    /**
     * Builds the canonical `https://baynith-tv.base44.app/embed-video/<VIDEO_ID>` URL.
     * Automatically upgrades any legacy `/embed-neli/<ID>` URL to `/embed-video/<ID>`.
     */
    fun resolveNeliEmbedUrl(
        streamUrl: String = "",
        youtubeVideoId: String = "",
        youtubeUrl: String = ""
    ): String {
        val extracted = extractYoutubeVideoId(youtubeVideoId)
            .ifBlank { extractYoutubeVideoId(youtubeUrl) }
            .ifBlank { extractYoutubeVideoId(streamUrl) }

        if (extracted.isNotBlank()) {
            return "$BAYNITH_EMBED_VIDEO_BASE_URL$extracted"
        }

        val cleanStream = streamUrl.trim()
        if (cleanStream.contains("baynith-tv.base44.app/embed-neli/", ignoreCase = true)) {
            return cleanStream.replace("/embed-neli/", "/embed-video/", ignoreCase = true)
        }
        if (cleanStream.contains("baynith-tv.base44.app/embed", ignoreCase = true)) {
            return cleanStream
        }
        val cleanYt = youtubeUrl.trim()
        return cleanYt.ifBlank { cleanStream }
    }

    /**
     * Builds the exact responsive 16:9 Baynith TV `/embed-video/<VIDEO_ID>` iframe snippet.
     */
    fun buildNeliEmbedHtml(videoIdOrUrl: String): String {
        val videoId = extractYoutubeVideoId(videoIdOrUrl)
        val embedSrc = if (videoId.isNotBlank()) {
            "$BAYNITH_EMBED_VIDEO_BASE_URL$videoId"
        } else {
            resolveNeliEmbedUrl(streamUrl = videoIdOrUrl)
        }
        return """
            <div style="position:relative; width:100%; padding-top:56.25%; border-radius:14px; overflow:hidden; background:#000;">
              <iframe
                src="$embedSrc"
                title="Baynith TV video"
                style="position:absolute; top:0; left:0; width:100%; height:100%; border:0;"
                frameborder="0"
                allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                allowfullscreen
                referrerpolicy="no-referrer-when-downgrade">
              </iframe>
            </div>
        """.trimIndent()
    }

    /**
     * Builds a 100% full-viewport single-iframe wrapper for Android WebView hosting the
     * Baynith TV `/embed-video/<VIDEO_ID>` player:
     * - Fills 100% width and height cleanly in portrait, landscape, and fullscreen with full touch/timeline scrubbing.
     * - Attempts clean one-shot autoplay on initial load without ever fighting user pause or seek controls.
     * - Exposes `window.__neliPauseVideo()`, `window.__neliResumeVideo()`, and `window.__neliLoadVideo(url)`
     *   for automatic pause when leaving the app and seamless resume at the exact timeline position on return.
     */
    fun buildFullScreenNeliEmbedHtml(videoIdOrUrl: String): String {
        val videoId = extractYoutubeVideoId(videoIdOrUrl)
        val embedSrc = if (videoId.isNotBlank()) {
            "$BAYNITH_EMBED_VIDEO_BASE_URL$videoId"
        } else {
            resolveNeliEmbedUrl(streamUrl = videoIdOrUrl)
        }
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
                <style>
                    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background: #000000;
                        overflow: hidden;
                    }
                    #baynith-wrapper {
                        position: fixed;
                        inset: 0;
                        width: 100%;
                        height: 100%;
                        background: #000000;
                        overflow: hidden;
                    }
                    iframe {
                        position: absolute;
                        top: 0;
                        left: 0;
                        width: 100%;
                        height: 100%;
                        border: 0;
                        display: block;
                        background: #000000;
                    }
                </style>
            </head>
            <body>
                <div id="baynith-wrapper">
                    <iframe
                        id="baynith-player-frame"
                        src="$embedSrc"
                        title="Baynith TV video"
                        frameborder="0"
                        allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                        allowfullscreen
                        referrerpolicy="no-referrer-when-downgrade">
                    </iframe>
                </div>
                <script>
                    (function() {
                        var didInitialAutoplay = false;
                        function getFrame() {
                            return document.getElementById('baynith-player-frame') || document.querySelector('iframe');
                        }
                        function sendFrameCmd(ytFunc, simpleType) {
                            try {
                                var f = getFrame();
                                if (f && f.contentWindow) {
                                    f.contentWindow.postMessage(JSON.stringify({ event: 'command', func: ytFunc, args: [] }), '*');
                                    f.contentWindow.postMessage(JSON.stringify({ type: simpleType }), '*');
                                }
                            } catch (e) {}
                        }
                        window.__neliAutoplayOnce = function() {
                            if (didInitialAutoplay) return;
                            didInitialAutoplay = true;
                            try {
                                var f = getFrame();
                                if (f) { f.focus(); }
                                document.querySelectorAll('video').forEach(function(v) {
                                    v.muted = false;
                                    var p = v.play();
                                    if (p && p.catch) { p.catch(function() {}); }
                                });
                            } catch (e) {}
                            sendFrameCmd('playVideo', 'play');
                        };
                        window.__neliPauseVideo = function() {
                            try {
                                document.querySelectorAll('video,audio').forEach(function(m) {
                                    try { m.pause(); } catch (e) {}
                                });
                            } catch (e) {}
                            sendFrameCmd('pauseVideo', 'pause');
                        };
                        window.__neliResumeVideo = function() {
                            try {
                                var f = getFrame();
                                if (f) { f.focus(); }
                            } catch (e) {}
                        };
                        window.__neliLoadVideo = function(nextEmbedUrl) {
                            try {
                                var f = getFrame();
                                if (f && nextEmbedUrl && f.src !== nextEmbedUrl) {
                                    didInitialAutoplay = false;
                                    f.src = nextEmbedUrl;
                                }
                            } catch (e) {}
                        };
                        var frame = getFrame();
                        if (frame) {
                            frame.addEventListener('load', function() {
                                setTimeout(function() { window.__neliAutoplayOnce(); }, 350);
                            });
                        }
                    })();
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Returns a guaranteed high-quality YouTube thumbnail URL when Studio doesn't provide a custom poster.
     */
    fun buildYoutubeThumbnailUrl(videoIdOrUrl: String): String {
        val id = extractYoutubeVideoId(videoIdOrUrl)
        if (id.isBlank() || id.startsWith("BTV-", ignoreCase = true)) return ""
        return "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    }
}
