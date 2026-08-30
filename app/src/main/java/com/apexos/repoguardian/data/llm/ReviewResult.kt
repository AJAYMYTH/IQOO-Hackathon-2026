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
    BUG("Bug / Error"),
    CONFLICT("Merge Conflict"),
    SYNTAX_ERROR("Syntax Error"),
    LOGIC_ERROR("Logic Error"),
    PERFORMANCE("Performance"),
    MAINTAINABILITY("Maintainability"),
    STYLE("Style"),
    TESTING("Testing"),
    UNKNOWN("General")
}

object SeverityMapper {
    fun map(raw: String?): Severity {
        if (raw.isNullOrBlank()) return Severity.UNKNOWN
        val clean = raw.trim().lowercase()
        return when {
            clean.contains("critical") || clean.contains("blocker") || clean.contains("fatal") || clean.contains("urgent") -> Severity.CRITICAL
            clean.contains("warning") || clean.contains("warn") || clean.contains("high") || clean.contains("medium") || clean.contains("major") -> Severity.WARNING
            clean.contains("info") || clean.contains("informational") || clean.contains("low") || clean.contains("minor") || clean.contains("style") || clean.contains("suggestion") || clean.contains("note") -> Severity.INFO
            else -> Severity.UNKNOWN
        }
    }

    fun mapCategory(raw: String?): IssueCategory {
        if (raw.isNullOrBlank()) return IssueCategory.UNKNOWN
        val clean = raw.trim().uppercase()
        return when {
            clean.contains("CONFLICT") || clean.contains("MERGE") || clean.contains("REBASE") || clean.contains("BRANCH") -> IssueCategory.CONFLICT
            clean.contains("SECURITY") || clean.contains("VULNERABILITY") || clean.contains("SECRET") || clean.contains("AUTH") || clean.contains("INJECTION") || clean.contains("CVE") -> IssueCategory.SECURITY
            clean.contains("SYNTAX") || clean.contains("PARSE") || clean.contains("COMPILE") || clean.contains("COMPILATION") -> IssueCategory.SYNTAX_ERROR
            clean.contains("LOGIC") || clean.contains("EDGE_CASE") || clean.contains("OFF_BY_ONE") || clean.contains("CONDITION") -> IssueCategory.LOGIC_ERROR
            clean.contains("BUG") || clean.contains("ERROR") || clean.contains("CRASH") || clean.contains("DEFECT") || clean.contains("NULL") || clean.contains("EXCEPTION") -> IssueCategory.BUG
            clean.contains("PERFORMANCE") || clean.contains("PERF") || clean.contains("OPTIMIZATION") || clean.contains("MEMORY_LEAK") || clean.contains("LEAK") || clean.contains("SLOW") -> IssueCategory.PERFORMANCE
            clean.contains("MAINTAINABILITY") || clean.contains("REFACTOR") || clean.contains("CLEAN_CODE") || clean.contains("COMPLEXITY") || clean.contains("DEAD_CODE") -> IssueCategory.MAINTAINABILITY
            clean.contains("STYLE") || clean.contains("FORMATTING") || clean.contains("LINT") || clean.contains("NAMING") -> IssueCategory.STYLE
            clean.contains("TESTING") || clean.contains("TEST") || clean.contains("COVERAGE") -> IssueCategory.TESTING
            else -> IssueCategory.UNKNOWN
        }
    }
}

enum class RiskLevel(val label: String, val badgeColorHex: Long) {
    LOW("Low Risk", 0xFF10B981),
    MEDIUM("Medium Risk", 0xFFF59E0B),
    HIGH("High Risk", 0xFFEF4444),
    CRITICAL("Critical Risk", 0xFFDC2626)
}

@JsonClass(generateAdapter = true)
data class CommitRiskScore(
    val overallScore: Int = 100,
    val securityScore: Int = 100,
    val reliabilityScore: Int = 100,
    val performanceScore: Int = 100,
    val maintainabilityScore: Int = 100,
    val riskLevel: RiskLevel = RiskLevel.LOW
) {
    val riskLabel: String get() = riskLevel.label

    companion object {
        fun calculate(issues: List<CodeIssue>): CommitRiskScore {
            if (issues.isEmpty()) {
                return CommitRiskScore(
                    overallScore = 98,
                    securityScore = 100,
                    reliabilityScore = 97,
                    performanceScore = 99,
                    maintainabilityScore = 96,
                    riskLevel = RiskLevel.LOW
                )
            }

            var secPenalty = 0
            var relPenalty = 0
            var perfPenalty = 0
            var maintPenalty = 0

            issues.forEach { issue ->
                val weight = when (issue.severityEnum) {
                    Severity.CRITICAL -> 35
                    Severity.WARNING -> 15
                    Severity.INFO, Severity.UNKNOWN -> 5
                }

                when (issue.categoryEnum) {
                    IssueCategory.SECURITY -> secPenalty += weight
                    IssueCategory.BUG, IssueCategory.LOGIC_ERROR, IssueCategory.SYNTAX_ERROR, IssueCategory.CONFLICT -> relPenalty += weight
                    IssueCategory.PERFORMANCE -> perfPenalty += weight
                    IssueCategory.MAINTAINABILITY, IssueCategory.STYLE, IssueCategory.TESTING -> maintPenalty += weight
                    IssueCategory.UNKNOWN -> {
                        relPenalty += (weight / 2)
                        maintPenalty += (weight / 2)
                    }
                }
            }

            val securityScore = (100 - secPenalty).coerceIn(0, 100)
            val reliabilityScore = (100 - relPenalty).coerceIn(0, 100)
            val performanceScore = (100 - perfPenalty).coerceIn(0, 100)
            val maintainabilityScore = (100 - maintPenalty).coerceIn(0, 100)

            // Weighted overall health calculation (Security: 40%, Reliability: 30%, Performance: 15%, Maintainability: 15%)
            val overall = (securityScore * 0.40 + reliabilityScore * 0.30 + performanceScore * 0.15 + maintainabilityScore * 0.15).toInt().coerceIn(0, 100)

            val level = when {
                overall >= 80 -> RiskLevel.LOW
                overall >= 55 -> RiskLevel.MEDIUM
                overall >= 30 -> RiskLevel.HIGH
                else -> RiskLevel.CRITICAL
            }

            return CommitRiskScore(
                overallScore = overall,
                securityScore = securityScore,
                reliabilityScore = reliabilityScore,
                performanceScore = performanceScore,
                maintainabilityScore = maintainabilityScore,
                riskLevel = level
            )
        }
    }
}

@JsonClass(generateAdapter = true)
data class PrivacyIndicatorInfo(
    val isPrivateMode: Boolean = true,
    val bytesUploaded: Long = 0L,
    val inferenceLocation: String = "100% On-Device Neural Engine",
    val privacyGuarantee: String = "0 bytes transmitted to external AI cloud"
)

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
    val metrics: InferenceMetrics? = null,
    val riskScore: CommitRiskScore? = null,
    val privacyInfo: PrivacyIndicatorInfo = PrivacyIndicatorInfo()
) {
    val criticalCount: Int get() = issues.count { it.severityEnum == Severity.CRITICAL }
    val warningCount: Int get() = issues.count { it.severityEnum == Severity.WARNING }
    val infoCount: Int get() = issues.count { it.severityEnum == Severity.INFO || it.severityEnum == Severity.UNKNOWN }
    val totalCount: Int get() = issues.size

    val computedRiskScore: CommitRiskScore get() = riskScore ?: CommitRiskScore.calculate(issues)

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
    val confidence: Float = 0.5f,
    val aiSolution: String? = null,
    val verificationTest: String? = null,
    val isAiSolving: Boolean = false,
    val isGeneratingTest: Boolean = false,
    val isFixed: Boolean = false
) {
    val severityEnum: Severity get() = SeverityMapper.map(severity)
    val categoryEnum: IssueCategory get() = SeverityMapper.mapCategory(category)
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: (if (description.length > 60) description.take(60) + "..." else description).ifBlank { "Potential issue detected" }
    val displayFix: String? get() = aiSolution ?: fix ?: suggestion
    val needsManualVerification: Boolean get() = severityEnum == Severity.CRITICAL && confidence < 0.5f
    val confidenceLevel: String get() = when {
        confidence >= 0.7f -> "High"
        confidence >= 0.4f -> "Medium"
        else -> "Low"
    }
}
