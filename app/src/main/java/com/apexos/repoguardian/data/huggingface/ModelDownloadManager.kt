package com.apexos.repoguardian.data.huggingface

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val huggingFaceApi: HuggingFaceApi,
    @Named("huggingfaceClient") private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "ModelDownloadManager"
        private const val MODELS_DIR = "models"
        // Max model size: 4GB (suitable for phone)
        const val MAX_MODEL_SIZE_BYTES = 4L * 1024 * 1024 * 1024
    }

    private val _currentDownloadingFilename = MutableStateFlow<String?>(null)
    val currentDownloadingFilename: StateFlow<String?> = _currentDownloadingFilename

    private val _currentProgress = MutableStateFlow<DownloadProgress?>(null)
    val currentProgress: StateFlow<DownloadProgress?> = _currentProgress

    private var activeCall: okhttp3.Call? = null

    fun getModelsDirectory(): File {
        val dir = File(context.filesDir, MODELS_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadedModels(): List<File> {
        return getModelsDirectory().listFiles()?.filter { it.extension == "gguf" }?.toList() ?: emptyList()
    }

    fun isModelDownloaded(filename: String): Boolean {
        val file = File(getModelsDirectory(), filename)
        return file.exists() && file.length() > 0
    }

    fun getDownloadedModelFile(filename: String): File? {
        val file = File(getModelsDirectory(), filename)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun getAvailableStorageBytes(): Long {
        return context.filesDir.usableSpace
    }

    suspend fun searchModels(query: String): List<HfModelSearchResult> {
        return try {
            huggingFaceApi.searchModels(search = query)
        } catch (e: Exception) {
            Log.e(TAG, "Search failed", e)
            emptyList()
        }
    }

    suspend fun listGgufFiles(modelId: String): List<HfModelFile> {
        return try {
            huggingFaceApi.listModelFiles(modelId)
                .filter { it.isGguf }
                .filter { (it.size ?: 0) <= MAX_MODEL_SIZE_BYTES }
                .sortedBy { it.size }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list files for $modelId", e)
            emptyList()
        }
    }

    fun downloadModel(modelId: String, filename: String): Flow<DownloadProgress> = flow {
        var input: InputStream? = null
        var output: OutputStream? = null
        val tempFile = File(getModelsDirectory(), "$filename.tmp")
        val outputFile = File(getModelsDirectory(), filename)

        try {
            _currentDownloadingFilename.value = filename
            val url = "https://huggingface.co/$modelId/resolve/main/$filename"
            Log.d(TAG, "Starting download: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "RepoGuardian-Android/1.0")
                .build()

            val call = okHttpClient.newCall(request)
            activeCall = call
            val response = call.execute()

            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    404 -> "Model file not found on Hugging Face (404)"
                    429 -> "Rate limit reached. Please wait a few moments (429)"
                    500, 502, 503 -> "Hugging Face server temporarily unavailable (${response.code})"
                    else -> "Download failed with HTTP error ${response.code}"
                }
                val errorProgress = DownloadProgress(error = errorMsg)
                _currentProgress.value = errorProgress
                emit(errorProgress)
                return@flow
            }

            val body = response.body ?: throw RuntimeException("Empty response body from server")
            val totalBytes = body.contentLength()

            // Pre-check available storage
            val available = getAvailableStorageBytes()
            if (totalBytes > 0 && available < totalBytes + (100L * 1024 * 1024)) {
                val neededMb = totalBytes / (1024 * 1024)
                val availMb = available / (1024 * 1024)
                val errorMsg = "Insufficient storage space: Need ${neededMb}MB, only ${availMb}MB available on device."
                val errProg = DownloadProgress(error = errorMsg)
                _currentProgress.value = errProg
                emit(errProg)
                return@flow
            }

            input = body.byteStream()
            output = tempFile.outputStream()

            val buffer = ByteArray(64 * 1024) // 64KB buffer
            var bytesDownloaded = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            var read: Int

            val initialProgress = DownloadProgress(0, totalBytes)
            _currentProgress.value = initialProgress
            emit(initialProgress)

            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesDownloaded += read

                val now = System.currentTimeMillis()
                val elapsed = now - lastProgressTime
                if (elapsed >= 300) { // Update every 300ms
                    val bytesSinceLast = bytesDownloaded - lastDownloadedBytes
                    val speed = if (elapsed > 0) (bytesSinceLast * 1000) / elapsed else 0L
                    val remainingBytes = if (totalBytes > bytesDownloaded) totalBytes - bytesDownloaded else 0L
                    val eta = if (speed > 0) remainingBytes / speed else 0L

                    val prog = DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    )
                    _currentProgress.value = prog
                    emit(prog)

                    lastProgressTime = now
                    lastDownloadedBytes = bytesDownloaded
                }
            }

            output.flush()
            output.close()
            output = null

            input.close()
            input = null

            // Swap temp file to destination
            if (outputFile.exists()) outputFile.delete()
            tempFile.renameTo(outputFile)

            val finalProgress = DownloadProgress(
                bytesDownloaded = if (totalBytes > 0) totalBytes else bytesDownloaded,
                totalBytes = if (totalBytes > 0) totalBytes else bytesDownloaded,
                isComplete = true
            )
            _currentProgress.value = finalProgress
            _currentDownloadingFilename.value = null
            emit(finalProgress)
            Log.d(TAG, "Download finished successfully: ${outputFile.absolutePath}")
        } catch (e: UnknownHostException) {
            Log.e(TAG, "No internet connection", e)
            val errProg = DownloadProgress(error = "No internet connection. Please check your network and retry.")
            _currentProgress.value = errProg
            _currentDownloadingFilename.value = null
            emit(errProg)
        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Download timed out", e)
            val errProg = DownloadProgress(error = "Download timed out. Network connection was too slow or dropped.")
            _currentProgress.value = errProg
            _currentDownloadingFilename.value = null
            emit(errProg)
        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)
            if (activeCall?.isCanceled() == true) {
                val cancelProg = DownloadProgress(error = "Download cancelled")
                _currentProgress.value = null
                _currentDownloadingFilename.value = null
                emit(cancelProg)
            } else {
                val errProg = DownloadProgress(error = "Download error: ${e.localizedMessage ?: e.message}")
                _currentProgress.value = errProg
                _currentDownloadingFilename.value = null
                emit(errProg)
            }
        } finally {
            activeCall = null
            try { output?.close() } catch (ignored: Throwable) {}
            try { input?.close() } catch (ignored: Throwable) {}
            if (tempFile.exists() && _currentProgress.value?.isComplete != true) {
                tempFile.delete()
            }
        }
    }.flowOn(Dispatchers.IO)

    fun cancelActiveDownload() {
        activeCall?.cancel()
        _currentDownloadingFilename.value = null
        _currentProgress.value = null
        val tempFiles = getModelsDirectory().listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        tempFiles.forEach { it.delete() }
    }

    fun deleteModel(filename: String): Boolean {
        val file = File(getModelsDirectory(), filename)
        return if (file.exists()) file.delete() else false
    }
}
