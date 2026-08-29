package com.apexos.repoguardian.data.huggingface

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HfModelSearchResult(
    val id: String, // e.g. "TheBloke/Qwen2.5-Coder-3B-Instruct-GGUF"
    val author: String? = null,
    @Json(name = "lastModified") val lastModified: String? = null,
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    @Json(name = "modelId") val modelId: String? = null
)

@JsonClass(generateAdapter = true)
data class HfModelFile(
    @Json(name = "rfilename") val filename: String,
    val size: Long? = null // bytes
) {
    val sizeInMb: Long get() = (size ?: 0) / (1024 * 1024)
    val sizeInGb: Double get() = (size ?: 0).toDouble() / (1024.0 * 1024.0 * 1024.0)
    val isGguf: Boolean get() = filename.endsWith(".gguf", ignoreCase = true)
    val quantType: String? get() {
        val regex = Regex("(?i)(q[0-9]_[a-z0-9_]+|f16|f32|q[0-9]+)", RegexOption.IGNORE_CASE)
        return regex.find(filename.lowercase())?.value?.uppercase()
    }
}

data class DownloadProgress(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progressPercent: Float get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
