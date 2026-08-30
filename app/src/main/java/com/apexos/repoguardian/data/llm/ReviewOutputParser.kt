package com.apexos.repoguardian.data.llm

import com.apexos.repoguardian.core.logging.AppLogger
import com.squareup.moshi.Moshi

sealed class ParseResult {
    data class Success(val result: ReviewResult) : ParseResult()
    data class Partial(val result: ReviewResult, val warnings: List<String>) : ParseResult()
    data class Failure(val rawOutput: String, val error: String) : ParseResult()
}

object ReviewOutputParser {
    private const val TAG = "ReviewOutputParser"
    private const val MAX_ISSUES = 50

    fun parse(
        response: String,
        moshi: Moshi,
        metrics: InferenceMetrics? = null
    ): ReviewResult {
        if (response.isBlank()) {
            return ReviewResult(
                hasIssue = false,
                summary = "No issues detected in the analyzed diff.",
                issues = emptyList(),
                metrics = metrics
            )
        }

        try {
            // 1. Strip markdown fences if present
            var cleaned = response.trim()
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.removePrefix("```json").trim()
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.removePrefix("```").trim()
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }

            // 2. Extract JSON payload between { and }
            val firstBrace = cleaned.indexOf('{')
            val lastBrace = cleaned.lastIndexOf('}')
            val jsonToParse = if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                cleaned.substring(firstBrace, lastBrace + 1)
            } else {
                cleaned
            }

            val adapter = moshi.adapter(ReviewResult::class.java)
            val parsed = adapter.fromJson(jsonToParse)

            if (parsed != null) {
                // Sanitize and deduplicate issues
                val sanitizedIssues = parsed.issues
                    .map { issue ->
                        val normalizedSeverity = SeverityMapper.map(issue.severity).name.lowercase()
                        val normalizedCategory = SeverityMapper.mapCategory(issue.category).name
                        val safeConfidence = issue.confidence.coerceIn(0.0f, 1.0f)
                        val safeTitle = issue.title?.trim()?.ifBlank { null }
                            ?: issue.description.lines().firstOrNull { it.isNotBlank() }?.take(60)
                            ?: "Potential issue detected"
                        val safeDesc = issue.description.ifBlank { "The AI detected a possible issue but did not provide details." }
                        val safeFix = issue.fix ?: issue.suggestion

                        issue.copy(
                            severity = normalizedSeverity,
                            category = normalizedCategory,
                            title = safeTitle,
                            description = safeDesc,
                            fix = safeFix,
                            suggestion = safeFix,
                            confidence = safeConfidence
                        )
                    }
                    .distinctBy { "${it.file ?: ""}:${it.line ?: 0}:${it.displayTitle}" }
                    .take(MAX_ISSUES)

                val hasIssues = sanitizedIssues.isNotEmpty()
                val summaryText = parsed.summary.ifBlank {
                    if (hasIssues) {
                        "Found ${sanitizedIssues.size} issue(s) across analyzed files."
                    } else {
                        "No issues detected. Code changes look clean and well-structured."
                    }
                }

                return ReviewResult(
                    hasIssue = hasIssues,
                    summary = summaryText,
                    issues = sanitizedIssues,
                    fixedCode = parsed.fixedCode,
                    metrics = metrics
                )
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to parse structured JSON from AI review response", e)
        }

        // Defensive fallback: Parse unstructured response into a single safe INFO issue without crashing
        return ReviewResult(
            hasIssue = true,
            summary = "Review output could not be fully structured. Please review details below.",
            issues = listOf(
                CodeIssue(
                    file = null,
                    line = null,
                    severity = "info",
                    category = "UNKNOWN",
                    title = "Unstructured Review Output",
                    description = response.trim().take(1000),
                    fix = "Review AI suggestions and apply changes manually.",
                    confidence = 0.5f
                )
            ),
            metrics = metrics
        )
    }
}
