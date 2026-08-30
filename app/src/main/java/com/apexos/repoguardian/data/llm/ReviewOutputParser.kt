package com.apexos.repoguardian.data.llm

import com.apexos.repoguardian.core.logging.AppLogger
import com.squareup.moshi.Moshi
import org.json.JSONArray
import org.json.JSONObject

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

        val raw = response.trim()

        // 1. Sanitize raw LLM text into clean JSON string
        val sanitizedJson = sanitizeJsonText(raw)

        // 2. Try parsing with Moshi (Lenient)
        try {
            val adapter = moshi.adapter(ReviewResult::class.java).lenient()
            val parsed = adapter.fromJson(sanitizedJson)
            if (parsed != null && (parsed.issues.isNotEmpty() || parsed.summary.isNotBlank())) {
                val cleanedIssues = sanitizeIssues(parsed.issues)
                return ReviewResult(
                    hasIssue = cleanedIssues.isNotEmpty() || parsed.hasIssue,
                    summary = parsed.summary.ifBlank {
                        if (cleanedIssues.isNotEmpty()) "Found ${cleanedIssues.size} issue(s) in the analyzed changes."
                        else "No issues detected. Code changes look clean and well-structured."
                    },
                    issues = cleanedIssues,
                    fixedCode = parsed.fixedCode?.takeIf { it.isNotBlank() && it != "null" },
                    metrics = metrics
                )
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Moshi lenient parsing failed: ${e.message}, trying manual org.json fallback")
        }

        // 3. Try parsing with org.json.JSONObject (built-in lenient Android JSON parser)
        try {
            val firstBrace = sanitizedJson.indexOf('{')
            val lastBrace = sanitizedJson.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                val jsonBlock = sanitizedJson.substring(firstBrace, lastBrace + 1)
                val jsonObject = JSONObject(jsonBlock)
                val issuesList = mutableListOf<CodeIssue>()

                val issuesArray = jsonObject.optJSONArray("issues")
                if (issuesArray != null) {
                    for (i in 0 until issuesArray.length()) {
                        val item = issuesArray.optJSONObject(i) ?: continue
                        val file = item.optString("file", "").takeIf { it.isNotBlank() && it != "null" }
                        val line = if (item.has("line") && !item.isNull("line")) item.optInt("line") else null
                        val severity = item.optString("severity", "info")
                        val category = item.optString("category", "UNKNOWN")
                        val title = item.optString("title", "").takeIf { it.isNotBlank() && it != "null" }
                        val description = item.optString("description", "")
                        val fix = item.optString("fix", "").takeIf { it.isNotBlank() && it != "null" }
                        val suggestion = item.optString("suggestion", "").takeIf { it.isNotBlank() && it != "null" }
                        val patch = item.optString("patch", "").takeIf { it.isNotBlank() && it != "null" }
                        val ruleId = item.optString("rule_id", "").takeIf { it.isNotBlank() && it != "null" }
                        val confidence = item.optDouble("confidence", 0.85).toFloat().coerceIn(0.0f, 1.0f)

                        issuesList.add(
                            CodeIssue(
                                file = file,
                                line = line,
                                severity = severity,
                                category = category,
                                title = title,
                                description = description,
                                fix = fix ?: suggestion,
                                suggestion = suggestion,
                                patch = patch,
                                ruleId = ruleId,
                                confidence = confidence
                            )
                        )
                    }
                }

                val sanitizedIssues = sanitizeIssues(issuesList)
                val summary = jsonObject.optString("summary", "").ifBlank {
                    if (sanitizedIssues.isNotEmpty()) "Found ${sanitizedIssues.size} issue(s) across analyzed code."
                    else "No issues detected. Code changes look clean."
                }
                val fixedCode = jsonObject.optString("fixed_code", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: jsonObject.optString("fixedCode", "").takeIf { it.isNotBlank() && it != "null" }

                return ReviewResult(
                    hasIssue = sanitizedIssues.isNotEmpty() || jsonObject.optBoolean("has_issue", false),
                    summary = summary,
                    issues = sanitizedIssues,
                    fixedCode = fixedCode,
                    metrics = metrics
                )
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "org.json parsing failed: ${e.message}, trying regex issue extraction")
        }

        // 4. Multi-issue Regex Extractor (in case of malformed outer JSON wrapper)
        val extractedIssues = extractIssuesWithRegex(sanitizedJson)
        if (extractedIssues.isNotEmpty()) {
            val sanitized = sanitizeIssues(extractedIssues)
            return ReviewResult(
                hasIssue = true,
                summary = "Identified ${sanitized.size} code issue(s) during AI scan.",
                issues = sanitized,
                fixedCode = extractFixedCodeSnippet(raw),
                metrics = metrics
            )
        }

        // 5. Markdown / Bullet Point Fallback Parser
        val markdownIssues = parseMarkdownReview(raw)
        if (markdownIssues.isNotEmpty()) {
            return ReviewResult(
                hasIssue = true,
                summary = "AI code scan findings extracted from review notes.",
                issues = sanitizeIssues(markdownIssues),
                fixedCode = extractFixedCodeSnippet(raw),
                metrics = metrics
            )
        }

        // 6. Graceful clean presentation fallback
        val cleanSummary = extractCleanSummary(raw)
        return ReviewResult(
            hasIssue = true,
            summary = cleanSummary,
            issues = listOf(
                CodeIssue(
                    file = null,
                    line = null,
                    severity = "info",
                    category = "STYLE",
                    title = "Unstructured Review Output",
                    description = cleanSummary,
                    fix = "Review the identified code changes, apply manual adjustments, or generate an automated fix.",
                    confidence = 0.8f
                )
            ),
            fixedCode = extractFixedCodeSnippet(raw),
            metrics = metrics
        )
    }

    private fun sanitizeJsonText(raw: String): String {
        var text = raw.trim()

        // Strip markdown code fences
        if (text.startsWith("```json")) {
            text = text.removePrefix("```json").trim()
        } else if (text.startsWith("```")) {
            text = text.removePrefix("```").trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }

        // Strip single line comments // ... (unless inside a url like http:// or https://)
        val lines = text.lines().map { line ->
            val commentIdx = line.indexOf("//")
            if (commentIdx != -1) {
                val before = line.substring(0, commentIdx)
                val quoteCount = before.count { it == '"' }
                if (quoteCount % 2 == 0) {
                    before.trimEnd()
                } else {
                    line
                }
            } else {
                line
            }
        }
        text = lines.joinToString("\n")

        // Strip multi-line comments /* ... */
        text = text.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

        // Remove trailing commas before } or ]
        text = text.replace(Regex(",\\s*([}\\]])"), "$1")

        // Fix unquoted keys like fixed_code: null -> "fixed_code": null
        text = text.replace(Regex("(?<=\\{|,|\\n)\\s*([a-zA-Z0-9_]+)\\s*:"), "\"$1\":")

        // Fix pipe severity placeholders e.g. "CRITICAL|WARNING|INFO" -> "CRITICAL"
        text = text.replace(Regex("\"CRITICAL\\|WARNING\\|INFO\"", RegexOption.IGNORE_CASE), "\"CRITICAL\"")
        text = text.replace(Regex("\"SECURITY\\|BUG\\|PERFORMANCE[^\"]*\"", RegexOption.IGNORE_CASE), "\"SECURITY\"")

        return text
    }

    private fun sanitizeIssues(issues: List<CodeIssue>): List<CodeIssue> {
        return issues
            .map { issue ->
                val normalizedSeverity = SeverityMapper.map(issue.severity).name.lowercase()
                val normalizedCategory = SeverityMapper.mapCategory(issue.category).name
                val safeConfidence = issue.confidence.coerceIn(0.0f, 1.0f)
                val safeTitle = issue.title?.trim()?.ifBlank { null }
                    ?: issue.description.lines().firstOrNull { it.isNotBlank() }?.take(60)
                    ?: "Potential issue detected"
                val safeDesc = issue.description.ifBlank { "The AI detected a possible issue in this hunk." }
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
    }

    private fun extractIssuesWithRegex(text: String): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        val blockRegex = Regex("\\{[^{}]*\"(?:title|description|severity|file)\"[^{}]*\\}", RegexOption.DOT_MATCHES_ALL)
        val matches = blockRegex.findAll(text)

        for (m in matches) {
            try {
                val block = m.value
                val obj = JSONObject(block)
                val title = obj.optString("title", "").takeIf { it.isNotBlank() }
                val desc = obj.optString("description", "").takeIf { it.isNotBlank() } ?: title ?: continue
                val file = obj.optString("file", "").takeIf { it.isNotBlank() && it != "null" }
                val line = if (obj.has("line") && !obj.isNull("line")) obj.optInt("line") else null
                val severity = obj.optString("severity", "info")
                val category = obj.optString("category", "UNKNOWN")
                val fix = obj.optString("fix", "").takeIf { it.isNotBlank() && it != "null" }
                    ?: obj.optString("suggestion", "").takeIf { it.isNotBlank() && it != "null" }

                issues.add(
                    CodeIssue(
                        file = file,
                        line = line,
                        severity = severity,
                        category = category,
                        title = title ?: desc.take(60),
                        description = desc,
                        fix = fix,
                        confidence = 0.85f
                    )
                )
            } catch (ignored: Exception) {}
        }
        return issues
    }

    private fun parseMarkdownReview(text: String): List<CodeIssue> {
        val issues = mutableListOf<CodeIssue>()
        val lines = text.lines()
        var currentTitle: String? = null
        var currentDesc = StringBuilder()
        var currentCategory = "UNKNOWN"
        var currentSeverity = "info"
        var currentFile: String? = null
        var currentLine: Int? = null
        var currentFix: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.startsWith("- **") || trimmed.startsWith("* **") || trimmed.matches(Regex("^\\d+\\.\\s+\\*\\*.*"))) {
                if (currentTitle != null) {
                    issues.add(
                        CodeIssue(
                            file = currentFile,
                            line = currentLine,
                            severity = currentSeverity,
                            category = currentCategory,
                            title = currentTitle,
                            description = currentDesc.toString().trim().ifBlank { currentTitle },
                            fix = currentFix,
                            confidence = 0.8f
                        )
                    )
                    currentDesc = StringBuilder()
                    currentFix = null
                }
                currentTitle = trimmed.removePrefix("#").removePrefix("##").removePrefix("###").removePrefix("-").removePrefix("*").replace("**", "").trim()
                currentCategory = SeverityMapper.mapCategory(currentTitle).name
                currentSeverity = SeverityMapper.map(currentTitle).name.lowercase()
            } else if (trimmed.startsWith("File:", ignoreCase = true) || trimmed.startsWith("Path:", ignoreCase = true)) {
                val loc = trimmed.substringAfter(':').trim()
                if (loc.contains(':')) {
                    currentFile = loc.substringBefore(':').trim()
                    currentLine = loc.substringAfter(':').trim().toIntOrNull()
                } else {
                    currentFile = loc
                }
            } else if (trimmed.startsWith("Fix:", ignoreCase = true) || trimmed.startsWith("Remediation:", ignoreCase = true) || trimmed.startsWith("Solution:", ignoreCase = true)) {
                currentFix = trimmed.substringAfter(':').trim()
            } else if (trimmed.startsWith("Severity:", ignoreCase = true)) {
                currentSeverity = SeverityMapper.map(trimmed.substringAfter(':').trim()).name.lowercase()
            } else if (trimmed.isNotBlank()) {
                currentDesc.appendLine(trimmed)
            }
        }

        if (currentTitle != null) {
            issues.add(
                CodeIssue(
                    file = currentFile,
                    line = currentLine,
                    severity = currentSeverity,
                    category = currentCategory,
                    title = currentTitle,
                    description = currentDesc.toString().trim().ifBlank { currentTitle },
                    fix = currentFix,
                    confidence = 0.8f
                )
            )
        }

        return issues
    }

    private fun extractCleanSummary(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() && !it.trim().startsWith("{") && !it.trim().startsWith("}") && !it.trim().startsWith("\"") }
        if (lines.isNotEmpty()) {
            return lines.take(5).joinToString(" ").take(300)
        }
        return "Review analysis completed. Potential issues found in the code changes."
    }

    private fun extractFixedCodeSnippet(raw: String): String? {
        val codeBlockRegex = Regex("```(?:[a-zA-Z0-9]+)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val matches = codeBlockRegex.findAll(raw).map { it.groupValues[1].trim() }.filter { !it.startsWith("{") && it.length > 20 }
        return matches.firstOrNull()
    }
}
