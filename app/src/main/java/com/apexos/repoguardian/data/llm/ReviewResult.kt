package com.apexos.repoguardian.data.llm

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class Severity(val priority: Int, val uiLabel: String) {
    CRITICAL(0, "Critical"),
    WARNING(1, "Warning"),
    INFO(2, "Info"),
    UNKNOWN(3, "Info")
}

enum class IssueCategory(val uiLabel: String) {
    SECURITY("Security"),
    BUG("Bug"),
    PERFORMANCE("Performance"),
    MAINTAINABILITY("Maintainability"),
    STYLE("Style"),
    TESTING("Testing"),
    UNKNOWN("General")
}

object SeverityMapper {
    fun map(raw: String?): Severity {
        if (raw.isNullOrBlank()) return Severity.UNKNOWN
        return when (raw.trim().lowercase()) {
            "critical", "blocker", "fatal", "urgent" -> Severity.CRITICAL
            "warning", "high", "medium", "warn", "major" -> Severity.WARNING
            "info", "informational", "low", "minor", "style", "suggestion", "note" -> Severity.INFO
            else -> Severity.UNKNOWN
        }
    }

    fun mapCategory(raw: String?): IssueCategory {
        if (raw.isNullOrBlank()) return IssueCategory.UNKNOWN
        return when (raw.trim().uppercase()) {
            "SECURITY", "VULNERABILITY", "SECRET_LEAK", "AUTH" -> IssueCategory.SECURITY
            "BUG", "ERROR", "CRASH", "DEFECT" -> IssueCategory.BUG
            "PERFORMANCE", "PERF", "OPTIMIZATION", "MEMORY_LEAK" -> IssueCategory.PERFORMANCE
            "MAINTAINABILITY", "REFACTOR", "CLEAN_CODE", "COMPLEXITY" -> IssueCategory.MAINTAINABILITY
            "STYLE", "FORMATTING", "LINT", "NAMING" -> IssueCategory.STYLE
            "TESTING", "TEST", "COVERAGE" -> IssueCategory.TESTING
            else -> IssueCategory.UNKNOWN
        }
    }
}

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
) {
    val criticalCount: Int get() = issues.count { it.severityEnum == Severity.CRITICAL }
    val warningCount: Int get() = issues.count { it.severityEnum == Severity.WARNING }
    val infoCount: Int get() = issues.count { it.severityEnum == Severity.INFO || it.severityEnum == Severity.UNKNOWN }
    val totalCount: Int get() = issues.size

    val sortedIssues: List<CodeIssue> get() = issues.sortedWith(
        compareBy<CodeIssue> { it.severityEnum.priority }
            .thenByDescending { it.confidence }
            .thenBy { it.file ?: "" }
            .thenBy { it.line ?: 0 }
    )
}

@JsonClass(generateAdapter = true)
data class CodeIssue(
    val file: String? = null,
    val line: Int? = null,
    val severity: String = "info", // critical, warning, info
    val category: String? = null,
    val title: String? = null,
    val description: String = "",
    val fix: String? = null,
    val suggestion: String? = null,
    val patch: String? = null,
    @Json(name = "rule_id") val ruleId: String? = null,
    val confidence: Float = 0.5f
) {
    val severityEnum: Severity get() = SeverityMapper.map(severity)
    val categoryEnum: IssueCategory get() = SeverityMapper.mapCategory(category)
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: (if (description.length > 60) description.take(60) + "..." else description).ifBlank { "Potential issue detected" }
    val displayFix: String? get() = fix ?: suggestion
    val needsManualVerification: Boolean get() = severityEnum == Severity.CRITICAL && confidence < 0.5f
    val confidenceLevel: String get() = when {
        confidence >= 0.7f -> "High"
        confidence >= 0.4f -> "Medium"
        else -> "Low"
    }
}
