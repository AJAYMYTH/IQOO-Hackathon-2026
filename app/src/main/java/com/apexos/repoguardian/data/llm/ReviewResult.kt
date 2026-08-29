package com.apexos.repoguardian.data.llm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InferenceMetrics(
    val totalTimeMs: Long = 0L,
    val promptEvalTimeMs: Long = 0L,
    val tokenCount: Int = 0,
    val tokensPerSecond: Double = 0.0,
    val backend: String = "CPU (ARM NEON)"
) {
    val formattedSpeed: String get() = String.format("%.1f tok/s", tokensPerSecond)
    val formattedDuration: String get() = if (totalTimeMs >= 1000) String.format("%.2fs", totalTimeMs / 1000.0) else "${totalTimeMs}ms"
    val summaryBadge: String get() = "⚡ $formattedDuration • $formattedSpeed • $backend"
}

@JsonClass(generateAdapter = true)
data class ReviewResult(
    @Json(name = "has_issue") val hasIssue: Boolean = false,
    val summary: String = "",
    val issues: List<CodeIssue> = emptyList(),
    @Json(name = "fixed_code") val fixedCode: String? = null,
    val metrics: InferenceMetrics? = null
)

@JsonClass(generateAdapter = true)
data class CodeIssue(
    val file: String? = null,
    val line: Int? = null,
    val severity: String = "info", // critical, warning, info
    val description: String = "",
    val fix: String? = null
)
