package com.gyro.app.platform.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.gyro.app.BuildConfig
import com.gyro.app.core.update.AppVersion
import com.gyro.app.core.update.ReleaseInfo
import com.gyro.app.core.update.ReleaseNotes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/** Outcome of asking GitHub for the latest release. */
sealed interface UpdateCheck {
    data class Available(val release: ReleaseInfo) : UpdateCheck
    data class UpToDate(val latestVersion: String) : UpdateCheck
    data object Failed : UpdateCheck
}

/**
 * In-app updater backed by GitHub Releases (github.com/nyphrux/Gyro/releases/latest).
 *
 * Checking uses the REST API, which lists the APK asset and release notes. When that fails
 * (no network to api.github.com, or the 60-requests-an-hour anonymous limit), it falls back
 * to the public /releases/latest redirect, which needs no API quota.
 *
 * Network access only happens when the user taps the update button in Settings.
 */
class GithubUpdater(private val context: Context) {
    val currentVersion: String = BuildConfig.VERSION_NAME

    private val userAgent = "Gyro/$currentVersion (Android)"
    private val updatesDir get() = File(context.cacheDir, UPDATES_DIR)

    suspend fun checkLatest(): UpdateCheck = withContext(Dispatchers.IO) {
        clearDownloads() // an APK left over from an earlier, already-installed update
        val release = runCatching { fetchFromApi() }.getOrNull()
            ?: runCatching { fetchFromRedirect() }.getOrNull()
            ?: return@withContext UpdateCheck.Failed
        if (AppVersion.isNewer(release.version, currentVersion)) {
            UpdateCheck.Available(release)
        } else {
            UpdateCheck.UpToDate(release.version)
        }
    }

    /**
     * Downloads the release APK into the app cache. [onProgress] receives 0..1, or null while
     * the size is unknown. Throws on failure; a cancelled or failed download leaves no file.
     */
    suspend fun download(release: ReleaseInfo, onProgress: (Float?) -> Unit): File = withContext(Dispatchers.IO) {
        val url = requireNotNull(release.apkUrl) { "Release has no APK" }
        clearDownloads()
        val target = File(updatesDir.apply { mkdirs() }, "Gyro-${release.version}.apk")
        val connection = open(url, followRedirects = true, timeoutMs = 30_000)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            onProgress(if (total != null) 0f else null)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReported = -1
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total != null) {
                            // Report whole percents only, so the UI is not flooded with updates.
                            val percent = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent / 100f)
                            }
                        }
                    }
                }
            }
            if (total != null && target.length() != total) throw IOException("Incomplete download")
            target
        } catch (t: Throwable) {
            target.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    /** Whether Android will let Gyro open the package installer ("Install unknown apps"). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens Gyro's "Install unknown apps" toggle in system settings. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Hands the downloaded APK to the system package installer. */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun releasePageIntent(release: ReleaseInfo?): Intent =
        Intent(Intent.ACTION_VIEW, (release?.pageUrl ?: LATEST_PAGE).toUri())

    fun clearDownloads() {
        updatesDir.listFiles()?.forEach { it.delete() }
    }

    // region GitHub

    private fun fetchFromApi(): ReleaseInfo {
        val connection = open(API_LATEST).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name").trim()
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url").ifBlank { null }
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }
            return ReleaseInfo(
                tag = tag,
                version = AppVersion.clean(tag),
                name = json.optString("name").ifBlank { tag },
                notes = ReleaseNotes.toPlainText(json.optString("body")),
                apkUrl = apkUrl,
                apkSize = apkSize,
                pageUrl = json.optString("html_url").ifBlank { "$REPO_URL/releases/tag/$tag" },
            )
        } finally {
            connection.disconnect()
        }
    }

    /** /releases/latest answers with a redirect to /releases/tag/<tag>, without using API quota. */
    private fun fetchFromRedirect(): ReleaseInfo {
        val connection = open(LATEST_PAGE, followRedirects = false)
        val tag = try {
            val location = connection.getHeaderField("Location") ?: throw IOException("No redirect")
            URLDecoder.decode(location.substringAfter("/releases/tag/", "").substringBefore('?'), "UTF-8")
        } finally {
            connection.disconnect()
        }
        if (tag.isBlank()) throw IOException("No release tag")
        // The asset list lives on a separate fragment page; find the first .apk link in it.
        val apkPath = runCatching {
            val assets = open("$REPO_URL/releases/expanded_assets/$tag")
            try {
                val html = assets.inputStream.bufferedReader().use { it.readText() }
                Regex("""href="(/[^"]+?\.apk)"""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
            } finally {
                assets.disconnect()
            }
        }.getOrNull()
        return ReleaseInfo(
            tag = tag,
            version = AppVersion.clean(tag),
            name = tag,
            notes = "",
            apkUrl = apkPath?.let { "https://github.com$it" },
            apkSize = 0L,
            pageUrl = "$REPO_URL/releases/tag/$tag",
        )
    }

    private fun open(url: String, followRedirects: Boolean = true, timeoutMs: Int = 10_000): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = followRedirects
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", userAgent)
        }

    // endregion

    private companion object {
        const val REPO = "nyphrux/Gyro"
        const val REPO_URL = "https://github.com/$REPO"
        const val LATEST_PAGE = "$REPO_URL/releases/latest"
        const val API_LATEST = "https://api.github.com/repos/$REPO/releases/latest"
        const val UPDATES_DIR = "updates"
        const val APK_MIME = "application/vnd.android.package-archive"
    }
}
