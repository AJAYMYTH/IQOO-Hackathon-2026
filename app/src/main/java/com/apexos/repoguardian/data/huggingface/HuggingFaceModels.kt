package com.apexos.repoguardian.data.huggingface

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HfModelSearchResult(
    val id: String, // e.g. "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF"
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
    val categoryBadge: String,
    val recommendedFor: String
) {
    val sizeFormatted: String get() {
        val mb = sizeBytes.toDouble() / (1024.0 * 1024.0)
        return if (mb >= 1024.0) String.format("%.2f GB", mb / 1024.0) else String.format("%.0f MB", mb)
    }
}

val FEATURED_MODELS = listOf(
    FeaturedModel(
        id = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
        name = "DeepSeek-R1 Distill 1.5B (Deep Reasoning)",
        description = "Advanced reinforcement-learning reasoning model specialized in step-by-step chain-of-thought, mathematical deduction, and deep bug discovery.",
        filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        sizeBytes = 1120L * 1024 * 1024,
        quant = "Q4_K_M",
        categoryBadge = "Deep Reasoning & Thinking",
        recommendedFor = "Chain-of-thought reasoning, multi-step problem solving & logic verification"
    ),
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF",
        name = "Qwen2.5-Coder 1.5B (Code Specialist)",
        description = "Alibaba's premier code intelligence model fine-tuned for syntax verification, automated refactoring, and unit test generation.",
        filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        sizeBytes = 986L * 1024 * 1024,
        quant = "Q4_K_M",
        categoryBadge = "Code Generation & Review",
        recommendedFor = "Recommended default for daily mobile code reviews, diff fixes & test generation"
    ),
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF",
        name = "Qwen2.5-Coder 0.5B (Ultra Fast Mobile)",
        description = "Ultra-compact 0.5B coding engine. Near-instant token generation with under 500MB RAM footprint.",
        filename = "qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        sizeBytes = 398L * 1024 * 1024,
        quant = "Q4_K_M",
        categoryBadge = "Fast Code Inspection",
        recommendedFor = "Instant commit diff inspections with minimal battery and RAM usage"
    ),
    FeaturedModel(
        id = "Qwen/Qwen2.5-Coder-3B-Instruct-GGUF",
        name = "Qwen2.5-Coder 3B (Pro Reasoning & Audit)",
        description = "Comprehensive 3B code reasoning model capable of multi-file vulnerability analysis, architecture synthesis, and security audits.",
        filename = "qwen2.5-coder-3b-instruct-q4_k_m.gguf",
        sizeBytes = 1950L * 1024 * 1024,
        quant = "Q4_K_M",
        categoryBadge = "Pro Code Reasoning",
        recommendedFor = "Deep security audits, architecture breakdowns & complex CI/CD synthesis"
    ),
    FeaturedModel(
        id = "bartowski/Phi-3.5-mini-instruct-GGUF",
        name = "Phi-3.5 Mini 3.8B (Logical Reasoning)",
        description = "Microsoft's high-reasoning small language model with advanced multilingual, reasoning, and multi-turn logic capabilities.",
        filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
        sizeBytes = 2150L * 1024 * 1024,
        quant = "Q4_K_M",
        categoryBadge = "Logic & Mathematics",
        recommendedFor = "Multi-step logic reasoning, algorithmic analysis, and complex code explanation"
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
        if (etaSeconds <= 0) return "--"
        val mins = etaSeconds / 60
        val secs = etaSeconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    val downloadedFormatted: String get() {
        val dlMb = bytesDownloaded.toDouble() / (1024.0 * 1024.0)
        val totalMb = totalBytes.toDouble() / (1024.0 * 1024.0)
        return String.format("%.1f / %.1f MB (%.0f%%)", dlMb, totalMb, progressPercent * 100)
    }

    val progressText: String get() = downloadedFormatted
}
