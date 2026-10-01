package com.example.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateReleaseInfo(
    val versionTag: String = NeliAppUpdateManager.CURRENT_APP_VERSION_TAG,
    val releaseTitle: String = "Neli TV v1.0.0 Official Release",
    val apkDownloadUrl: String = NeliAppUpdateManager.DEFAULT_APK_DOWNLOAD_URL,
    val releasePageUrl: String = NeliAppUpdateManager.DEFAULT_GITHUB_RELEASE_TAG_URL,
    val whatsNewNotes: String = NeliAppUpdateManager.DEFAULT_WHATS_NEW_NOTES,
    val publishedAt: String = "Latest Official Build",
    val isNewUpdateAvailable: Boolean = false,
    val lastCheckedTimestamp: Long = 0L,
    val statusMessage: String = "Auto-Update Active • Connected to GitHub Releases (v1.0.0)"
)

object NeliAppUpdateManager {

    const val CURRENT_APP_VERSION_TAG = "v1.0.0"
    const val CURRENT_APP_VERSION_NAME = "1.0.0"

    const val DEFAULT_APK_DOWNLOAD_URL =
        "https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk"

    const val DEFAULT_GITHUB_RELEASE_TAG_URL =
        "https://github.com/baynith1/Nelitv/releases/tag/v1.0.0"

    const val GITHUB_LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/baynith1/Nelitv/releases/latest"

    const val GITHUB_TAG_V1_API_URL =
        "https://api.github.com/repos/baynith1/Nelitv/releases/tags/v1.0.0"

    const val GITHUB_ALL_RELEASES_API_URL =
        "https://api.github.com/repos/baynith1/Nelitv/releases"

    const val DEFAULT_WHATS_NEW_NOTES =
        "• Live TV Streaming: Azam Sports 1–4 HD, Azam One, Azam Two, Sinema Zetu, ZBC2 & East Africa Live Channels\n" +
            "• Swahili Narrated Movies & Series: Latest 2025/2026 Cinema with 5m 30s Auto Intro Skip\n" +
            "• Low Bando Data Saver: Smooth streaming on 3G/4G/5G mobile data\n" +
            "• True Offline Storage: Download Movies & Series Episodes to watch without internet\n" +
            "• Auto Cloud Token Sync, Mandatory EAT Live Alerts & Auto-Pinned Home Widget"

    private const val PREFS_NAME = "neli_app_update_prefs"
    private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
    private const val KEY_LAST_AUTO_DOWNLOADED_TAG = "last_auto_downloaded_tag"
    private const val KEY_INSTALLED_VERSION_TAG = "installed_version_tag"
    private const val KEY_INSTALLED_RELEASE_FINGERPRINT = "installed_release_fingerprint"
    private const val KEY_PENDING_UPDATE_TAG = "pending_update_tag"
    private const val KEY_PENDING_UPDATE_FINGERPRINT = "pending_update_fingerprint"
    private const val KEY_LAST_PACKAGE_UPDATE_TIME_MS = "last_package_update_time_ms"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val _releaseInfo = MutableStateFlow(AppUpdateReleaseInfo())
    val releaseInfo: StateFlow<AppUpdateReleaseInfo> = _releaseInfo.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    private val _autoUpdateEnabled = MutableStateFlow(true)
    val autoUpdateEnabled: StateFlow<Boolean> = _autoUpdateEnabled.asStateFlow()

    private val _apkDownloadStatusMessage = MutableStateFlow<String?>(null)
    val apkDownloadStatusMessage: StateFlow<String?> = _apkDownloadStatusMessage.asStateFlow()

    private val _showHomepageUpdatePopup = MutableStateFlow(false)
    val showHomepageUpdatePopup: StateFlow<Boolean> = _showHomepageUpdatePopup.asStateFlow()

    @Volatile
    private var dismissedPopupTagInSession: String? = null

    @Volatile
    private var lastAutoTriggeredTagInSession: String? = null

    @Volatile
    private var effectiveInstalledVersionTag: String = CURRENT_APP_VERSION_TAG

    @Volatile
    private var installedReleaseFingerprint: String = ""

    fun getPackageLastUpdateTimeMs(context: Context): Long {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.lastUpdateTime.coerceAtLeast(pkgInfo.firstInstallTime)
        } catch (_: Exception) {
            0L
        }
    }

    fun getPackageVersionTag(context: Context): String {
        return try {
            val rawVersion = context.packageManager.getPackageInfo(context.packageName, 0)
                .versionName
                ?.trim()
                .orEmpty()
            if (rawVersion.isBlank()) {
                CURRENT_APP_VERSION_TAG
            } else {
                val normalized = if (rawVersion.count { it == '.' } == 1) "$rawVersion.0" else rawVersion
                if (normalized.startsWith("v", ignoreCase = true)) normalized else "v$normalized"
            }
        } catch (_: Exception) {
            CURRENT_APP_VERSION_TAG
        }
    }

    fun getEffectiveInstalledVersionTag(context: Context? = null): String {
        var bestTag = effectiveInstalledVersionTag.ifBlank { CURRENT_APP_VERSION_TAG }
        if (context != null) {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val savedInstalled = prefs.getString(KEY_INSTALLED_VERSION_TAG, null)?.trim().orEmpty()
                if (savedInstalled.isNotBlank() && isRemoteVersionNewer(bestTag, savedInstalled)) {
                    bestTag = savedInstalled
                }
                val pkgTag = getPackageVersionTag(context)
                if (pkgTag.isNotBlank() && isRemoteVersionNewer(bestTag, pkgTag)) {
                    bestTag = pkgTag
                }
            } catch (_: Exception) {
            }
        }
        effectiveInstalledVersionTag = bestTag
        return bestTag
    }

    fun buildReleaseFingerprint(versionTag: String, publishedAt: String, apkUrl: String): String {
        return "${versionTag.trim().lowercase()}|${publishedAt.trim()}|${apkUrl.trim().lowercase()}"
    }

    fun parseIsoTimestampMs(rawIso: String): Long {
        val clean = rawIso.trim()
        if (clean.isBlank()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd"
        )
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val parsedDate = sdf.parse(clean)
                if (parsedDate != null && parsedDate.time > 0L) {
                    return parsedDate.time
                }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    /**
     * Marks a specific GitHub release tag & fingerprint as installed on this device so the updated
     * app immediately shows "Up to Date" and does not prompt for the same update again.
     */
    fun markReleaseAsInstalled(
        context: Context,
        versionTag: String,
        publishedAt: String = _releaseInfo.value.publishedAt,
        apkUrl: String = _releaseInfo.value.apkDownloadUrl
    ) {
        val cleanTag = versionTag.trim().ifBlank { CURRENT_APP_VERSION_TAG }
        val fp = buildReleaseFingerprint(cleanTag, publishedAt, apkUrl)
        effectiveInstalledVersionTag = cleanTag
        installedReleaseFingerprint = fp
        try {
            val pkgUpdateTime = getPackageLastUpdateTimeMs(context)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_INSTALLED_VERSION_TAG, cleanTag)
                .putString(KEY_INSTALLED_RELEASE_FINGERPRINT, fp)
                .putString(KEY_LAST_AUTO_DOWNLOADED_TAG, cleanTag)
                .remove(KEY_PENDING_UPDATE_TAG)
                .remove(KEY_PENDING_UPDATE_FINGERPRINT)
                .apply {
                    if (pkgUpdateTime > 0L) {
                        putLong(KEY_LAST_PACKAGE_UPDATE_TIME_MS, pkgUpdateTime)
                    }
                }
                .apply()
        } catch (_: Exception) {
        }

        val currentInfo = _releaseInfo.value
        if (currentInfo.versionTag.equals(cleanTag, ignoreCase = true) ||
            !isRemoteVersionNewer(cleanTag, currentInfo.versionTag)
        ) {
            _showHomepageUpdatePopup.value = false
            _releaseInfo.value = currentInfo.copy(
                versionTag = cleanTag,
                isNewUpdateAvailable = false,
                statusMessage = "You are on the latest version ($cleanTag) • Auto-Update Active"
            )
        }
    }

    fun dismissHomepageUpdatePopup(versionTag: String = _releaseInfo.value.versionTag) {
        dismissedPopupTagInSession = versionTag.trim()
        _showHomepageUpdatePopup.value = false
    }

    fun triggerHomepageUpdatePopupForNewRelease(versionTag: String = _releaseInfo.value.versionTag) {
        if (dismissedPopupTagInSession == null ||
            !dismissedPopupTagInSession.equals(versionTag.trim(), ignoreCase = true)
        ) {
            _showHomepageUpdatePopup.value = true
        }
    }

    /**
     * Reconciles Android PackageManager's `lastUpdateTime` and pending update state on app startup/resume.
     * If the APK package was updated or a pending update was installed, immediately promotes that version
     * to the installed state and clears `isNewUpdateAvailable`.
     */
    fun reconcileInstalledPackageState(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            _autoUpdateEnabled.value = prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)

            val savedInstalledTag = prefs.getString(KEY_INSTALLED_VERSION_TAG, null)?.trim().orEmpty()
            val savedInstalledFp = prefs.getString(KEY_INSTALLED_RELEASE_FINGERPRINT, null)?.trim().orEmpty()
            if (savedInstalledTag.isNotBlank()) {
                effectiveInstalledVersionTag = savedInstalledTag
            }
            if (savedInstalledFp.isNotBlank()) {
                installedReleaseFingerprint = savedInstalledFp
            }

            val currentPkgUpdateTime = getPackageLastUpdateTimeMs(context)
            val savedPkgUpdateTime = prefs.getLong(KEY_LAST_PACKAGE_UPDATE_TIME_MS, 0L)
            val pendingTag = prefs.getString(KEY_PENDING_UPDATE_TAG, null)?.trim().orEmpty()
            val pendingFp = prefs.getString(KEY_PENDING_UPDATE_FINGERPRINT, null)?.trim().orEmpty()

            if (pendingTag.isNotBlank() && (savedPkgUpdateTime == 0L || currentPkgUpdateTime >= savedPkgUpdateTime)) {
                effectiveInstalledVersionTag = pendingTag
                if (pendingFp.isNotBlank()) {
                    installedReleaseFingerprint = pendingFp
                }
                prefs.edit()
                    .putString(KEY_INSTALLED_VERSION_TAG, pendingTag)
                    .putString(KEY_INSTALLED_RELEASE_FINGERPRINT, pendingFp)
                    .remove(KEY_PENDING_UPDATE_TAG)
                    .remove(KEY_PENDING_UPDATE_FINGERPRINT)
                    .putLong(KEY_LAST_PACKAGE_UPDATE_TIME_MS, currentPkgUpdateTime)
                    .apply()
            } else if (currentPkgUpdateTime > 0L && savedPkgUpdateTime == 0L) {
                prefs.edit().putLong(KEY_LAST_PACKAGE_UPDATE_TIME_MS, currentPkgUpdateTime).apply()
            }

            val activeInstalledTag = getEffectiveInstalledVersionTag(context)
            val currentInfo = _releaseInfo.value
            if (!isRemoteVersionNewer(activeInstalledTag, currentInfo.versionTag)) {
                _releaseInfo.value = currentInfo.copy(
                    versionTag = activeInstalledTag,
                    isNewUpdateAvailable = false,
                    statusMessage = "You are on the latest version ($activeInstalledTag) • Auto-Update Active"
                )
            }
        } catch (_: Exception) {
        }
    }

    fun initialize(context: Context) {
        reconcileInstalledPackageState(context)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        _autoUpdateEnabled.value = enabled
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled)
                .apply()
        } catch (_: Exception) {
        }
        if (enabled && _releaseInfo.value.isNewUpdateAvailable) {
            downloadAndInstallApk(
                context = context,
                apkUrl = _releaseInfo.value.apkDownloadUrl,
                versionTag = _releaseInfo.value.versionTag,
                isAutoUpdate = true
            )
        }
    }

    /**
     * Compares two semantic version tags (e.g. "v1.0.0" vs "v1.0.1" or "v2.0.0").
     * Returns true strictly when [remoteTag] is newer than [currentTag].
     */
    fun isRemoteVersionNewer(currentTag: String, remoteTag: String): Boolean {
        val cleanCurrent = currentTag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V").substringBefore("-")
        if (cleanRemote.isBlank() || cleanCurrent.equals(cleanRemote, ignoreCase = true)) return false

        val currentParts = cleanCurrent.split(".").map { it.toIntOrNull() ?: 0 }
        val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val c = currentParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Parses a GitHub Release JSON object or JSON array of releases and updates [_releaseInfo].
     */
    fun parseGitHubReleaseJson(
        rawJson: String,
        currentVersionTag: String = effectiveInstalledVersionTag,
        installedPackageUpdateTimeMs: Long = 0L,
        alreadyInstalledFingerprint: String = installedReleaseFingerprint
    ): AppUpdateReleaseInfo? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null

        return try {
            val releaseObj: JSONObject = when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) return null
                    arr.getJSONObject(0)
                }
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> return null
            }

            val tagName = releaseObj.optString("tag_name", "").trim()
                .ifBlank { CURRENT_APP_VERSION_TAG }
            val releaseName = releaseObj.optString("name", "").trim()
                .ifBlank { "Neli TV $tagName Official Release" }
            val htmlUrl = releaseObj.optString("html_url", "").trim()
                .ifBlank { DEFAULT_GITHUB_RELEASE_TAG_URL }
            val bodyNotes = releaseObj.optString("body", "").trim()
                .ifBlank { DEFAULT_WHATS_NEW_NOTES }
            val publishedRaw = releaseObj.optString("published_at", "").trim()
            val publishedDate = if (publishedRaw.contains("T")) {
                publishedRaw.substringBefore("T")
            } else {
                publishedRaw.ifBlank { "Official GitHub Release" }
            }

            // Look for an .apk asset in the release assets array
            var resolvedApkUrl = ""
            var assetUpdatedAtRaw = ""
            val assetsArr = releaseObj.optJSONArray("assets")
            if (assetsArr != null) {
                for (i in 0 until assetsArr.length()) {
                    val asset = assetsArr.optJSONObject(i) ?: continue
                    val downloadUrl = asset.optString("browser_download_url", "").trim()
                    val assetName = asset.optString("name", "").trim()
                    if (downloadUrl.endsWith(".apk", ignoreCase = true) ||
                        assetName.endsWith(".apk", ignoreCase = true)
                    ) {
                        resolvedApkUrl = downloadUrl
                        assetUpdatedAtRaw = asset.optString("updated_at", "").trim()
                            .ifBlank { asset.optString("created_at", "").trim() }
                        break
                    }
                }
            }

            if (resolvedApkUrl.isBlank()) {
                resolvedApkUrl = if (tagName.equals(CURRENT_APP_VERSION_TAG, ignoreCase = true)) {
                    DEFAULT_APK_DOWNLOAD_URL
                } else {
                    "https://github.com/baynith1/Nelitv/releases/download/$tagName/Nelitv.apk"
                }
            }

            val releaseFingerprint = buildReleaseFingerprint(tagName, publishedDate, resolvedApkUrl)
            val releasePublishedEpochMs = maxOf(
                parseIsoTimestampMs(publishedRaw),
                parseIsoTimestampMs(assetUpdatedAtRaw)
            )

            // Determine if this device has ALREADY installed this release (via in-app update or APK install timestamp)
            val isAlreadyInstalledByFingerprint = alreadyInstalledFingerprint.isNotBlank() &&
                    alreadyInstalledFingerprint.equals(releaseFingerprint, ignoreCase = true)
            val isPackageInstalledAfterReleasePublished = installedPackageUpdateTimeMs > 0L &&
                    releasePublishedEpochMs > 0L &&
                    installedPackageUpdateTimeMs >= (releasePublishedEpochMs - 2 * 60_000L)

            val isNewer = if (isAlreadyInstalledByFingerprint || isPackageInstalledAfterReleasePublished) {
                false
            } else {
                isRemoteVersionNewer(currentVersionTag, tagName)
            }

            val statusMsg = if (isNewer) {
                "New Update Available ($tagName)! Tap Update or let Auto-Update install."
            } else {
                "You are on the latest version ($tagName) • Auto-Update Active"
            }

            val parsed = AppUpdateReleaseInfo(
                versionTag = tagName,
                releaseTitle = releaseName,
                apkDownloadUrl = resolvedApkUrl,
                releasePageUrl = htmlUrl,
                whatsNewNotes = bodyNotes,
                publishedAt = publishedDate,
                isNewUpdateAvailable = isNewer,
                lastCheckedTimestamp = System.currentTimeMillis(),
                statusMessage = statusMsg
            )
            _releaseInfo.value = parsed
            if (isNewer) {
                triggerHomepageUpdatePopupForNewRelease(tagName)
            } else {
                _showHomepageUpdatePopup.value = false
            }
            parsed
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks GitHub Releases (`baynith1/Nelitv`) for the latest version & "What's New" notes.
     * Automatically triggers APK download if a newer version appears and Auto-Update is enabled.
     */
    suspend fun checkForUpdates(
        context: Context,
        triggeredByUser: Boolean = false
    ): AppUpdateReleaseInfo = withContext(Dispatchers.IO) {
        reconcileInstalledPackageState(context)
        _isCheckingUpdate.value = true
        if (triggeredByUser) {
            _apkDownloadStatusMessage.value = "Checking GitHub Releases (baynith1/Nelitv) for updates..."
        }

        val currentInstalledTag = getEffectiveInstalledVersionTag(context)
        val pkgUpdateTimeMs = getPackageLastUpdateTimeMs(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedInstalledFp = prefs.getString(KEY_INSTALLED_RELEASE_FINGERPRINT, "").orEmpty()

        val endpoints = listOf(
            GITHUB_LATEST_RELEASE_API_URL,
            GITHUB_ALL_RELEASES_API_URL,
            GITHUB_TAG_V1_API_URL
        )

        var updatedInfo: AppUpdateReleaseInfo? = null
        for (url in endpoints) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "NeliTV-Android-App/1.0.0")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        val parsed = parseGitHubReleaseJson(
                            rawJson = body,
                            currentVersionTag = currentInstalledTag,
                            installedPackageUpdateTimeMs = pkgUpdateTimeMs,
                            alreadyInstalledFingerprint = savedInstalledFp
                        )
                        if (parsed != null) {
                            updatedInfo = parsed
                        }
                    }
                }
                if (updatedInfo != null) break
            } catch (_: Exception) {
            }
        }

        _isCheckingUpdate.value = false

        val finalInfo = updatedInfo ?: _releaseInfo.value.copy(
            versionTag = currentInstalledTag,
            isNewUpdateAvailable = false,
            lastCheckedTimestamp = System.currentTimeMillis(),
            statusMessage = "Latest Release $currentInstalledTag Ready • Auto-Update Active"
        )

        // If the device APK is already up to date with this GitHub release, persist the tag & fingerprint
        if (!finalInfo.isNewUpdateAvailable) {
            _showHomepageUpdatePopup.value = false
            markReleaseAsInstalled(
                context = context,
                versionTag = finalInfo.versionTag,
                publishedAt = finalInfo.publishedAt,
                apkUrl = finalInfo.apkDownloadUrl
            )
        } else {
            _releaseInfo.value = finalInfo
            triggerHomepageUpdatePopupForNewRelease(finalInfo.versionTag)
        }

        if (triggeredByUser) {
            _apkDownloadStatusMessage.value = if (finalInfo.isNewUpdateAvailable) {
                "New version ${finalInfo.versionTag} found on GitHub! Downloading update..."
            } else {
                "Checked GitHub (${finalInfo.versionTag}): App is up to date!"
            }
        }

        // If a newer update appeared on GitHub and Auto-Update is enabled, download it automatically
        if (finalInfo.isNewUpdateAvailable && _autoUpdateEnabled.value) {
            val alreadyDownloadedTag = prefs.getString(KEY_LAST_AUTO_DOWNLOADED_TAG, null)
            if (lastAutoTriggeredTagInSession != finalInfo.versionTag &&
                alreadyDownloadedTag != finalInfo.versionTag
            ) {
                lastAutoTriggeredTagInSession = finalInfo.versionTag
                prefs.edit().putString(KEY_LAST_AUTO_DOWNLOADED_TAG, finalInfo.versionTag).apply()
                withContext(Dispatchers.Main) {
                    downloadAndInstallApk(
                        context = context,
                        apkUrl = finalInfo.apkDownloadUrl,
                        versionTag = finalInfo.versionTag,
                        isAutoUpdate = true
                    )
                }
            }
        }

        _releaseInfo.value
    }

    /**
     * Downloads the APK from GitHub (`https://github.com/baynith1/Nelitv/releases/download/v1.0.0/Nelitv.apk`
     * or newer tag) using Android's system DownloadManager and launches package installation when complete.
     */
    fun downloadAndInstallApk(
        context: Context,
        apkUrl: String = _releaseInfo.value.apkDownloadUrl,
        versionTag: String = _releaseInfo.value.versionTag,
        isAutoUpdate: Boolean = false
    ) {
        val cleanUrl = apkUrl.ifBlank { DEFAULT_APK_DOWNLOAD_URL }
        val cleanTag = versionTag.trim().ifBlank { CURRENT_APP_VERSION_TAG }
        val pendingFp = buildReleaseFingerprint(cleanTag, _releaseInfo.value.publishedAt, cleanUrl)

        // Record the target update tag & fingerprint immediately so once the user installs the APK
        // (or reopens the updated app), the updated app knows it is already on this release!
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_UPDATE_TAG, cleanTag)
                .putString(KEY_PENDING_UPDATE_FINGERPRINT, pendingFp)
                .putString(KEY_INSTALLED_VERSION_TAG, cleanTag)
                .putString(KEY_INSTALLED_RELEASE_FINGERPRINT, pendingFp)
                .putString(KEY_LAST_AUTO_DOWNLOADED_TAG, cleanTag)
                .apply()
            effectiveInstalledVersionTag = cleanTag
            installedReleaseFingerprint = pendingFp
        } catch (_: Exception) {
        }

        _apkDownloadStatusMessage.value = if (isAutoUpdate) {
            "Auto-Update: Downloading Neli TV ($cleanTag) APK from GitHub..."
        } else {
            "Downloading Nelitv.apk ($cleanTag) to your phone's Downloads folder..."
        }

        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                val fileName = "Nelitv-${cleanTag.removePrefix("v")}.apk"
                val request = DownloadManager.Request(Uri.parse(cleanUrl))
                    .setTitle("Neli TV ($cleanTag) Official APK")
                    .setDescription("Downloading Neli TV update from GitHub Releases")
                    .setMimeType("application/vnd.android.package-archive")
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

                val downloadId = dm.enqueue(request)
                _apkDownloadStatusMessage.value =
                    "Downloading $fileName... Check top notification bar or Downloads folder to install."

                // Register receiver to prompt APK installation once download completes
                val appCtx = context.applicationContext
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        val completedId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                        if (completedId == downloadId) {
                            markReleaseAsInstalled(
                                context = appCtx,
                                versionTag = cleanTag,
                                publishedAt = _releaseInfo.value.publishedAt,
                                apkUrl = cleanUrl
                            )
                            _apkDownloadStatusMessage.value =
                                "Download complete ($fileName)! Opening installer..."
                            try {
                                val apkUri = dm.getUriForDownloadedFile(downloadId)
                                if (apkUri != null) {
                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    appCtx.startActivity(installIntent)
                                }
                            } catch (_: Exception) {
                            }
                            try {
                                appCtx.unregisterReceiver(this)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appCtx.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    @Suppress("UnspecifiedRegisterReceiverFlag")
                    appCtx.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    )
                }
                return
            }
        } catch (_: Exception) {
        }

        // Fallback: mark target release and open direct APK download link in browser
        markReleaseAsInstalled(
            context = context,
            versionTag = cleanTag,
            publishedAt = _releaseInfo.value.publishedAt,
            apkUrl = cleanUrl
        )
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Shares the Neli TV APK direct download link and GitHub "What's New" release page via Android Share Sheet.
     */
    fun shareApkByLink(context: Context) {
        val info = _releaseInfo.value
        val shareText = buildString {
            appendLine("Download Neli TV (NeliPlay) Official Android APK!")
            appendLine("Watch Azam TV Live Sports, Swahili Narrated Movies, Series & Offline Downloads:")
            appendLine()
            appendLine("Direct APK Download Link:")
            appendLine(info.apkDownloadUrl)
            appendLine()
            appendLine("What's New & GitHub Release Page (${info.versionTag}):")
            append(info.releasePageUrl)
        }

        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Download Neli TV (${info.versionTag}) APK")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Neli TV APK Link").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
        }
    }

    /**
     * Copies the direct APK download URL to the device clipboard.
     */
    fun copyApkDownloadLink(context: Context): String {
        val url = _releaseInfo.value.apkDownloadUrl.ifBlank { DEFAULT_APK_DOWNLOAD_URL }
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Neli TV APK Download Link", url))
            _apkDownloadStatusMessage.value = "Copied APK link: $url"
        } catch (_: Exception) {
        }
        return url
    }

    /**
     * Opens the GitHub Release & What's New page (`https://github.com/baynith1/Nelitv/releases/tag/v1.0.0`).
     */
    fun openGitHubWhatsNewPage(context: Context) {
        val url = _releaseInfo.value.releasePageUrl.ifBlank { DEFAULT_GITHUB_RELEASE_TAG_URL }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
        }
    }
}
