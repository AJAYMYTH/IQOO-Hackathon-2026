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

data class FeaturedModel(
    val id: String,
    val name: String,
    val description: String,
    val filename: String,
    val sizeBytes: Long,
    val quant: String,
    val recommendedFor: String
) {
    val sizeFormatted: String get() {
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.0f MB", mb)
    }
}

val FEATURED_MODELS = listOf(
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF",
        name = "Qwen2.5-Coder 0.5B (Ultra Fast)",
        description = "Lightweight 0.5B coding model. Instant token generation, minimal battery usage.",
        filename = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        sizeBytes = 398L * 1024 * 1024,
        quant = "Q4_K_M",
        recommendedFor = "Fastest reviews, low RAM usage (under 600MB)"
    ),
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
        name = "Qwen2.5-Coder 1.5B (Balanced)",
        description = "Great balance between reasoning capabilities and speed on mobile devices.",
        filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 986L * 1024 * 1024,
        quant = "Q4_K_M",
        recommendedFor = "Recommended default for daily mobile reviews"
    ),
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-3B-Instruct-GGUF",
        name = "Qwen2.5-Coder 3B (Pro Quality)",
        description = "State-of-the-art code analysis and vulnerability detection model.",
        filename = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        sizeBytes = 1950L * 1024 * 1024,
        quant = "Q4_K_M",
        recommendedFor = "Deep vulnerability checks & complex diffs"
    ),
    FeaturedModel(
        id = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
        name = "DeepSeek-R1 Distill 1.5B (Reasoning)",
        description = "Reinforcement-learning reasoned code logic model.",
        filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        sizeBytes = 1120L * 1024 * 1024,
        quant = "Q4_K_M",
        recommendedFor = "Step-by-step logical reasoning on diffs"
    )
)

data class DownloadProgress(
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progressPercent: Float get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

    val speedFormatted: String get() {
        val mbPerSec = speedBytesPerSec.toDouble() / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) String.format("%.2f MB/s", mbPerSec)
        else String.format("%.1f KB/s", (speedBytesPerSec / 1024.0).coerceAtLeast(0.0))
    }

    val etaFormatted: String get() {
        if (etaSeconds <= 0) return "Calculating..."
        val mins = etaSeconds / 60
        val secs = etaSeconds % 60
        return if (mins > 0) "${mins}m ${secs}s remaining" else "${secs}s remaining"
    }

    val downloadedFormatted: String get() {
        val curMb = bytesDownloaded.toDouble() / (1024.0 * 1024.0)
        val totMb = totalBytes.toDouble() / (1024.0 * 1024.0)
        return if (totMb >= 1024.0) {
            String.format("%.2f GB / %.2f GB", curMb / 1024.0, totMb / 1024.0)
        } else {
            String.format("%.1f MB / %.1f MB", curMb, totMb)
        }
    }
}
