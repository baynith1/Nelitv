package com.example.model

/**
 * Represents a Live TV channel in Neli TV.
 *
 * Supports both built-in priority channels and Cloud Firestore `tvChannels` documents:
 * - Tier 3 (Highest): Initial primary channels (`isAzamPriority == true`)
 * - Tier 2 (Second): Channels from Tanzania (`isTanzaniaChannel == true`)
 * - Tier 1 (Third): Featured channels (`featured == true`)
 * - Tier 0 (Lowest): Other Live TV channels
 */
data class LiveChannel(
    val id: String,
    val name: String,
    val description: String,
    val streamUrl: String,
    val streamFormat: String, // "dash", "hls", or "mp4"
    val thumbnailUrl: String,
    val categories: List<String> = emptyList(),
    val language: String = "en",
    val encryptionType: String = "none", // "clearkey" or "none"
    val clearKeys: Map<String, String> = emptyMap(),
    val clearKeyId: String? = clearKeys.keys.firstOrNull(),
    val clearKey: String? = clearKeys.values.firstOrNull(),
    val isLiveBroadcast: Boolean = true,
    val country: String = "",
    val featured: Boolean = false,
    val enabled: Boolean = true,
    val published: Boolean = true
) {
    val category: String
        get() = categories.firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "Live TV"

    val isDash: Boolean
        get() = streamFormat.equals("dash", ignoreCase = true) || streamUrl.contains(".mpd", ignoreCase = true)

    val isHls: Boolean
        get() = streamFormat.equals("hls", ignoreCase = true) ||
                streamFormat.equals("m3u8", ignoreCase = true) ||
                streamUrl.contains(".m3u8", ignoreCase = true)

    val isMp4: Boolean
        get() = streamFormat.equals("mp4", ignoreCase = true) ||
                (!isDash && !isHls && streamUrl.substringBefore("?").endsWith(".mp4", ignoreCase = true))

    val isClearKey: Boolean
        get() = encryptionType.equals("clearkey", ignoreCase = true) && clearKeys.isNotEmpty()

    val isAzamPriority: Boolean
        get() = streamUrl.contains("azamtvltd.co.tz", ignoreCase = true) ||
                name.contains("Azam", ignoreCase = true) ||
                name.contains("Sinema Zetu", ignoreCase = true) ||
                name.contains("UTV", ignoreCase = true) ||
                name.contains("ZBC", ignoreCase = true) ||
                name.contains("Crown", ignoreCase = true) ||
                name.contains("Wasafi", ignoreCase = true) ||
                name.contains("KIX", ignoreCase = true)

    val isTanzaniaChannel: Boolean
        get() = isAzamPriority ||
                country.equals("Tanzania", ignoreCase = true) ||
                categories.any { it.equals("tanzania", ignoreCase = true) } ||
                language.equals("sw", ignoreCase = true)

    /**
     * Priority score used to sort Live TV channels:
     * 3 = Primary initial channels (Azam TV bouquet)
     * 2 = Other Tanzania Live TV channels (`country == "Tanzania"`)
     * 1 = Featured international channels
     * 0 = Other Live TV channels (lowest priority)
     */
    val priorityTier: Int
        get() = when {
            isAzamPriority -> 3
            isTanzaniaChannel -> 2
            featured -> 1
            else -> 0
        }
}
