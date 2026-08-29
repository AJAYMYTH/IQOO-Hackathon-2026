package com.apexos.repoguardian.data.huggingface

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
        // Max model size: 4GB (to keep suitable for phone)
        const val MAX_MODEL_SIZE_BYTES = 4L * 1024 * 1024 * 1024
    }

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
        try {
            val url = "https://huggingface.co/$modelId/resolve/main/$filename"
            Log.d(TAG, "Starting download: $url")

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "RepoGuardian-Android/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(DownloadProgress(error = "Download failed (HTTP ${response.code}: ${response.message})"))
                return@flow
            }

            val body = response.body ?: throw RuntimeException("Empty response body from Hugging Face")
            val totalBytes = body.contentLength()
            val outputFile = File(getModelsDirectory(), filename)
            val tempFile = File(getModelsDirectory(), "$filename.tmp")

            input = body.byteStream()
            output = tempFile.outputStream()

            val buffer = ByteArray(64 * 1024) // 64KB buffer for optimal mobile throughput
            var bytesDownloaded = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            var read: Int

            emit(DownloadProgress(0, totalBytes))

            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                bytesDownloaded += read

                val now = System.currentTimeMillis()
                val elapsed = now - lastProgressTime
                if (elapsed >= 300) { // Update UI every 300ms
                    val bytesSinceLast = bytesDownloaded - lastDownloadedBytes
                    val speed = if (elapsed > 0) (bytesSinceLast * 1000) / elapsed else 0L
                    val remainingBytes = if (totalBytes > bytesDownloaded) totalBytes - bytesDownloaded else 0L
                    val eta = if (speed > 0) remainingBytes / speed else 0L

                    emit(DownloadProgress(
                        bytesDownloaded = bytesDownloaded,
                        totalBytes = totalBytes,
                        speedBytesPerSec = speed,
                        etaSeconds = eta
                    ))

                    lastProgressTime = now
                    lastDownloadedBytes = bytesDownloaded
                }
            }

            output.flush()
            output.close()
            output = null

            input.close()
            input = null

            // Safely swap temp file to destination
            if (outputFile.exists()) outputFile.delete()
            tempFile.renameTo(outputFile)

            emit(DownloadProgress(
                bytesDownloaded = if (totalBytes > 0) totalBytes else bytesDownloaded,
                totalBytes = if (totalBytes > 0) totalBytes else bytesDownloaded,
                isComplete = true
            ))
            Log.d(TAG, "Download finished successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Download exception", e)
            emit(DownloadProgress(error = "Download failed: ${e.localizedMessage ?: e.message}"))
        } finally {
            try { output?.close() } catch (ignored: Throwable) {}
            try { input?.close() } catch (ignored: Throwable) {}
        }
    }.flowOn(Dispatchers.IO)

    fun deleteModel(filename: String): Boolean {
        val file = File(getModelsDirectory(), filename)
        return if (file.exists()) file.delete() else false
    }
}
