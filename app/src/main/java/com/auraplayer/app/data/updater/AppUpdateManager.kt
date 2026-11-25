package com.auraplayer.app.data.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.auraplayer.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

data class AppUpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseName: String,
    val publishedAt: String,
    val releaseNotes: ChangelogRelease,
    val apkDownloadUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestRelease: AppUpdateInfo?) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class AppUpdateManager(
    private val context: Context,
    private val repositoryOwner: String = DEFAULT_REPO_OWNER,
    private val repositoryName: String = DEFAULT_REPO_NAME
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun loadBundledChangelog(): List<ChangelogRelease> {
        return runCatching {
            context.assets.open("CHANGELOG.md").bufferedReader(Charsets.UTF_8).use { reader ->
                ChangelogParser.parseChangelog(reader.readText())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$repositoryOwner/$repositoryName/releases/latest"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "AuraPlayer/$currentVersion")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext UpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    latestRelease = null
                )
            }
            if (code !in 200..299) {
                return@withContext UpdateCheckResult.Error("GitHub returned HTTP $code")
            }

            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val dto = json.decodeFromString<GitHubReleaseDto>(payload)
            if (dto.tagName.isBlank()) {
                return@withContext UpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    latestRelease = null
                )
            }

            val remoteVersion = dto.tagName.removePrefix("v").trim()
            val apkAsset = dto.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) &&
                    asset.name.contains("arm64-v8a", ignoreCase = true)
            } ?: dto.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true)
            }

            val info = AppUpdateInfo(
                versionName = remoteVersion,
                tagName = dto.tagName,
                releaseName = dto.name.ifBlank { "Aura Player v$remoteVersion" },
                publishedAt = dto.publishedAt,
                releaseNotes = ChangelogParser.parseReleaseNotes(
                    version = remoteVersion,
                    body = dto.body,
                    publishedDate = dto.publishedAt
                ),
                apkDownloadUrl = apkAsset?.browserDownloadUrl?.takeIf { it.isNotBlank() },
                apkName = apkAsset?.name,
                apkSizeBytes = apkAsset?.size ?: 0L,
                htmlUrl = dto.htmlUrl
            )

            if (compareSemanticVersions(remoteVersion, currentVersion) > 0) {
                UpdateCheckResult.UpdateAvailable(info)
            } else {
                UpdateCheckResult.UpToDate(currentVersion = currentVersion, latestRelease = info)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: "Unable to reach GitHub Releases")
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadUpdateApk(
        info: AppUpdateInfo,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val downloadUrl = info.apkDownloadUrl
            ?: error("Release v${info.versionName} does not contain an APK asset.")
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Clean stale APKs so cache never grows unbounded
        updatesDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }

        val safeFileName = (info.apkName ?: "aura-player-v${info.versionName}-arm64-v8a.apk")
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        val partFile = File(updatesDir, "$safeFileName.part")
        val targetFile = File(updatesDir, safeFileName)

        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(downloadUrl, BuildConfig.VERSION_NAME)
            val code = connection.responseCode
            if (code !in 200..299) {
                error("Download failed (HTTP $code)")
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0L } ?: info.apkSizeBytes
            var downloadedBytes = 0L
            val buffer = ByteArray(16 * 1024)

            connection.inputStream.use { input ->
                partFile.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            onProgress((downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                    output.flush()
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!partFile.renameTo(targetFile)) {
                partFile.copyTo(targetFile, overwrite = true)
                partFile.delete()
            }
            onProgress(1f)
            targetFile
        } catch (e: Exception) {
            partFile.delete()
            throw e
        } finally {
            connection?.disconnect()
        }
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun createInstallIntent(apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun openFollowingRedirects(urlString: String, currentVersion: String): HttpURLConnection {
        var currentUrl = urlString
        repeat(5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "AuraPlayer/$currentVersion")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val nextLocation = conn.getHeaderField("Location")
                conn.disconnect()
                if (nextLocation.isNullOrBlank()) {
                    error("Redirect location missing")
                }
                currentUrl = nextLocation
            } else {
                return conn
            }
        }
        error("Too many redirects while downloading APK")
    }

    companion object {
        const val DEFAULT_REPO_OWNER = "sanixmon"
        const val DEFAULT_REPO_NAME = "auraplayer"

        /**
         * Compares two semantic version strings (e.g. "v1.3.0" vs "1.2.10").
         * Returns > 0 if [remote] is newer than [local], 0 if equal, < 0 if older.
         */
        fun compareSemanticVersions(remote: String, local: String): Int {
            val remoteParts = parseNumericParts(remote)
            val localParts = parseNumericParts(local)
            val maxLen = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r != l) return r.compareTo(l)
            }
            return 0
        }

        private fun parseNumericParts(version: String): List<Int> {
            val core = version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            return core.split('.').map { token ->
                token.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            }
        }
    }
}
