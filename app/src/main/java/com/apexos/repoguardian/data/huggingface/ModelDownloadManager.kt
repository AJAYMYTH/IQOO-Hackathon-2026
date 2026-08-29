package com.apexos.repoguardian.data.huggingface

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val huggingFaceApi: HuggingFaceApi
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
        try {
            val url = "https://huggingface.co/$modelId/resolve/main/$filename"
            Log.d(TAG, "Downloading: $url")

            val response = huggingFaceApi.downloadFile(url)
            val totalBytes = response.contentLength()
            val outputFile = File(getModelsDirectory(), filename)

            emit(DownloadProgress(0, totalBytes))

            response.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesDownloaded += read

                        // Emit progress every 256KB
                        if (bytesDownloaded % (256 * 1024) < 8192) {
                            emit(DownloadProgress(bytesDownloaded, totalBytes))
                        }
                    }
                }
            }

            emit(DownloadProgress(totalBytes, totalBytes, isComplete = true))
            Log.d(TAG, "Download complete: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            emit(DownloadProgress(error = "Download failed: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    fun deleteModel(filename: String): Boolean {
        val file = File(getModelsDirectory(), filename)
        return if (file.exists()) file.delete() else false
    }
}
