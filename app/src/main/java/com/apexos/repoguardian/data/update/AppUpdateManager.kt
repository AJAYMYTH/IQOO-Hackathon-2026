package com.apexos.repoguardian.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.apexos.repoguardian.BuildConfig
import com.apexos.repoguardian.core.logging.AppLogger
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.GitHubRelease
import com.apexos.repoguardian.data.github.models.ReleaseAsset
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpToDate(
        val currentVersion: String,
        val checkedAt: String
    ) : UpdateUiState()
    data class UpdateAvailable(
        val release: GitHubRelease,
        val currentVersion: String,
        val newVersion: String,
        val apkAsset: ReleaseAsset?
    ) : UpdateUiState()
    data class Downloading(
        val progressPercent: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedFormatted: String,
        val fileName: String
    ) : UpdateUiState()
    data class DownloadReady(
        val apkFile: File,
        val version: String
    ) : UpdateUiState()
    data class Error(
        val message: String
    ) : UpdateUiState()
}

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gitHubRepository: GitHubRepository
) {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "AppUpdateManager"
        const val DEFAULT_REPO_OWNER = "AJAYMYTH"
        const val DEFAULT_REPO_NAME = "IQOO-Hackathon-2026"
    }

    suspend fun checkForUpdates(
        owner: String = DEFAULT_REPO_OWNER,
        repo: String = DEFAULT_REPO_NAME
    ): UpdateUiState = withContext(Dispatchers.IO) {
        val currentVersion = BuildConfig.VERSION_NAME
        AppLogger.i(TAG, "Checking for updates against $owner/$repo (Current: v$currentVersion)")

        when (val result = gitHubRepository.getLatestRelease(owner, repo)) {
            is ApiResult.Success -> {
                val release = result.data
                val latestTag = release.tagName.trim()
                val latestVersion = release.versionName.trim()

                AppLogger.i(TAG, "Latest release tag found: $latestTag, version: $latestVersion")

                val isNewer = isVersionNewer(latestVersion, currentVersion)
                if (isNewer) {
                    UpdateUiState.UpdateAvailable(
                        release = release,
                        currentVersion = currentVersion,
                        newVersion = latestVersion.ifBlank { latestTag },
                        apkAsset = release.apkAsset
                    )
                } else {
                    UpdateUiState.UpToDate(
                        currentVersion = currentVersion,
                        checkedAt = "Just now"
                    )
                }
            }
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Update check API error: ${result.message} (code: ${result.code})")
                if (result.code == 404) {
                    // No GitHub releases published yet in this repository
                    UpdateUiState.UpToDate(
                        currentVersion = currentVersion,
                        checkedAt = "Just now (No newer releases published)"
                    )
                } else {
                    UpdateUiState.Error(
                        message = "Could not check for updates: ${result.message}"
                    )
                }
            }
        }
    }

    fun downloadApk(asset: ReleaseAsset, newVersion: String): Flow<UpdateUiState> = flow {
        val url = asset.browserDownloadUrl
        val fileName = if (asset.name.isNotBlank()) asset.name else "RepoGuardian-v$newVersion.apk"
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val outputFile = File(updatesDir, fileName)
        val tempFile = File(updatesDir, "$fileName.tmp")

        emit(
            UpdateUiState.Downloading(
                progressPercent = 0.01f,
                downloadedBytes = 0L,
                totalBytes = asset.size,
                speedFormatted = "Connecting...",
                fileName = fileName
            )
        )

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            AppLogger.i(TAG, "Starting APK download from $url -> ${tempFile.absolutePath}")
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "RepoGuardian-Android/${BuildConfig.VERSION_NAME}")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(UpdateUiState.Error("Failed to download APK: HTTP ${response.code}"))
                return@flow
            }

            val body = response.body ?: throw RuntimeException("Empty response body from update server")
            val totalBytes = if (body.contentLength() > 0) body.contentLength() else asset.size
            inputStream = body.byteStream()
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(64 * 1024)
            var bytesRead: Int
            var totalRead = 0L
            var lastEmittedTime = System.currentTimeMillis()
            var bytesSinceLastCalc = 0L
            val df = DecimalFormat("#0.0")

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead
                bytesSinceLastCalc += bytesRead

                val now = System.currentTimeMillis()
                val delta = now - lastEmittedTime
                if (delta >= 300) {
                    val speedBytesPerSec = (bytesSinceLastCalc * 1000.0) / delta
                    val speedMbPerSec = speedBytesPerSec / (1024.0 * 1024.0)
                    val speedFormatted = if (speedMbPerSec >= 0.1) "${df.format(speedMbPerSec)} MB/s" else "${(speedBytesPerSec / 1024).toInt()} KB/s"
                    val progress = if (totalBytes > 0) (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0.5f

                    emit(
                        UpdateUiState.Downloading(
                            progressPercent = progress,
                            downloadedBytes = totalRead,
                            totalBytes = totalBytes,
                            speedFormatted = speedFormatted,
                            fileName = fileName
                        )
                    )

                    lastEmittedTime = now
                    bytesSinceLastCalc = 0L
                }
            }

            outputStream.flush()
            outputStream.close()
            outputStream = null
            inputStream.close()
            inputStream = null

            if (tempFile.exists()) {
                if (outputFile.exists()) outputFile.delete()
                tempFile.renameTo(outputFile)
            }

            AppLogger.i(TAG, "APK download complete: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            emit(UpdateUiState.DownloadReady(apkFile = outputFile, version = newVersion))

            // Trigger system installer
            installApk(outputFile)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error downloading update APK", e)
            tempFile.delete()
            emit(UpdateUiState.Error("Download interrupted: ${e.localizedMessage ?: e.message}"))
        } finally {
            try { outputStream?.close() } catch (ignored: Throwable) {}
            try { inputStream?.close() } catch (ignored: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    fun installApk(apkFile: File): Boolean {
        return try {
            AppLogger.i(TAG, "Triggering PackageInstaller for ${apkFile.absolutePath}")
            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to launch PackageInstaller", e)
            false
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        if (latest.isBlank()) return false
        if (latest.equals(current, ignoreCase = true)) return false

        try {
            val latestParts = latest.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (e: Exception) {
            return latest != current
        }
        return false
    }
}
