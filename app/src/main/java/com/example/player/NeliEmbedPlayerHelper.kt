package com.example.player

import java.util.Locale

/**
 * Helper for detecting YouTube / NeliPlay white-label embed URLs and IDs configured in Movie Studio,
 * converting any YouTube link or video ID into the branding-free `baynith-tv.base44.app/embed-neli/<VIDEO_ID>`
 * embed player without YouTube logos, titles, watermarks, or PiP.
 */
object NeliEmbedPlayerHelper {

    const val NELI_EMBED_BASE_URL = "https://baynith-tv.base44.app/embed-neli/"
    const val NELI_EMBED_ORIGIN = "https://baynith-tv.base44.app"

    private val YOUTUBE_ID_REGEX = Regex(
        """(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtube-nocookie\.com/embed/|baynith-tv\.base44\.app/embed-neli/|[?&]v=)([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val SHARE_ID_REGEX = Regex(
        """(?:baynith-tv\.base44\.app/embed-neli/|^)(BTV-[A-Za-z0-9_-]+)(?![A-Za-z0-9_-])""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_YOUTUBE_ID_REGEX = Regex("""^[A-Za-z0-9_-]{11}$""")

    /**
     * Extracts the bare 11-character YouTube video ID (or `BTV-...` NeliPlay share ID)
     * from any YouTube URL, `embed-neli` URL, or bare ID string.
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
     * Returns true if [rawUrl] points to YouTube or to the NeliPlay `/embed-neli/` player.
     */
    fun isYoutubeOrEmbedNeliUrl(rawUrl: String?): Boolean {
        val clean = rawUrl?.trim()?.lowercase(Locale.US).orEmpty()
        if (clean.isEmpty()) return false
        return clean.contains("youtube.com/") ||
            clean.contains("youtube.com\\") ||
            clean.contains("youtu.be/") ||
            clean.contains("youtube-nocookie.com/") ||
            clean.contains("baynith-tv.base44.app/embed-neli/") ||
            clean.contains("/embed-neli/")
    }

    /**
     * Determines whether a Studio media record uses YouTube / NeliPlay embed playback.
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
        if (normType in setOf("youtube", "yt", "embed-neli", "neli-embed", "youtube_embed")) {
            return true
        }
        return false
    }

    /**
     * Builds the canonical `https://baynith-tv.base44.app/embed-neli/<VIDEO_ID>` URL.
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
            return "$NELI_EMBED_BASE_URL$extracted"
        }

        val cleanStream = streamUrl.trim()
        if (cleanStream.contains("baynith-tv.base44.app/embed", ignoreCase = true)) {
            return cleanStream
        }
        val cleanYt = youtubeUrl.trim()
        return cleanYt.ifBlank { cleanStream }
    }

    /**
     * Builds the exact 16:9 white-label iframe wrapper snippet matching the NeliPlay embed specification.
     */
    fun buildNeliEmbedHtml(videoIdOrUrl: String): String {
        val videoId = extractYoutubeVideoId(videoIdOrUrl)
        val embedSrc = if (videoId.isNotBlank()) {
            "$NELI_EMBED_BASE_URL$videoId"
        } else {
            resolveNeliEmbedUrl(streamUrl = videoIdOrUrl)
        }
        return """
            <div style="position:relative;width:100%;padding-top:56.25%;border-radius:14px;overflow:hidden;background:#000">
            <iframe
            src="$embedSrc"
            title="Video player"
            style="position:absolute;top:0;left:0;width:100%;height:100%;border:0"
            frameborder="0"
            allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
            allowfullscreen
            referrerpolicy="no-referrer-when-downgrade">
            </iframe>
            </div>
        """.trimIndent()
    }

    /**
     * Builds a 100% full-viewport iframe wrapper for Android WebView so the NeliPlay embed player
     * fills the entire player surface identically to live sports channels, while preserving exact
     * website-style `<iframe>` framing (`origin`, `allow="autoplay; fullscreen; ..."`).
     */
    fun buildFullScreenNeliEmbedHtml(videoIdOrUrl: String): String {
        val videoId = extractYoutubeVideoId(videoIdOrUrl)
        val embedSrc = if (videoId.isNotBlank()) {
            "$NELI_EMBED_BASE_URL$videoId"
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
                    * { box-sizing: border-box; }
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background: #000000;
                        overflow: hidden;
                    }
                    iframe {
                        position: fixed;
                        inset: 0;
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
                <iframe
                    id="neli-player-frame"
                    src="$embedSrc"
                    title="Video player"
                    allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
                    allowfullscreen
                    referrerpolicy="no-referrer-when-downgrade">
                </iframe>
                <script>
                    (function() {
                        function kickAutoplay() {
                            try {
                                var f = document.getElementById('neli-player-frame');
                                if (f) {
                                    f.focus();
                                    if (f.contentWindow) {
                                        f.contentWindow.postMessage('{"event":"command","func":"playVideo","args":[]}', '*');
                                    }
                                }
                            } catch (e) {}
                        }
                        var f = document.getElementById('neli-player-frame');
                        if (f) {
                            f.addEventListener('load', function() {
                                setTimeout(kickAutoplay, 250);
                                setTimeout(kickAutoplay, 900);
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
