package com.apexos.repoguardian

import com.apexos.repoguardian.data.llm.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SeverityBasedIssueDetectionTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `test SeverityMapper maps direct and synonym values correctly`() {
        assertEquals(Severity.CRITICAL, SeverityMapper.map("critical"))
        assertEquals(Severity.CRITICAL, SeverityMapper.map("CRITICAL"))
        assertEquals(Severity.CRITICAL, SeverityMapper.map("blocker"))
        assertEquals(Severity.CRITICAL, SeverityMapper.map("fatal"))
        assertEquals(Severity.CRITICAL, SeverityMapper.map("urgent"))

        assertEquals(Severity.WARNING, SeverityMapper.map("warning"))
        assertEquals(Severity.WARNING, SeverityMapper.map("WARNING"))
        assertEquals(Severity.WARNING, SeverityMapper.map("high"))
        assertEquals(Severity.WARNING, SeverityMapper.map("medium"))
        assertEquals(Severity.WARNING, SeverityMapper.map("warn"))

        assertEquals(Severity.INFO, SeverityMapper.map("info"))
        assertEquals(Severity.INFO, SeverityMapper.map("INFO"))
        assertEquals(Severity.INFO, SeverityMapper.map("informational"))
        assertEquals(Severity.INFO, SeverityMapper.map("low"))
        assertEquals(Severity.INFO, SeverityMapper.map("style"))
        assertEquals(Severity.INFO, SeverityMapper.map("suggestion"))

        assertEquals(Severity.UNKNOWN, SeverityMapper.map(""))
        assertEquals(Severity.UNKNOWN, SeverityMapper.map(null))
        assertEquals(Severity.UNKNOWN, SeverityMapper.map("some_unknown_severity"))
    }

    @Test
    fun `test SeverityMapper maps issue categories correctly`() {
        assertEquals(IssueCategory.SECURITY, SeverityMapper.mapCategory("SECURITY"))
        assertEquals(IssueCategory.SECURITY, SeverityMapper.mapCategory("vulnerability"))
        assertEquals(IssueCategory.SECURITY, SeverityMapper.mapCategory("SECRET_LEAK"))

        assertEquals(IssueCategory.BUG, SeverityMapper.mapCategory("BUG"))
        assertEquals(IssueCategory.BUG, SeverityMapper.mapCategory("crash"))

        assertEquals(IssueCategory.PERFORMANCE, SeverityMapper.mapCategory("PERFORMANCE"))
        assertEquals(IssueCategory.PERFORMANCE, SeverityMapper.mapCategory("memory_leak"))

        assertEquals(IssueCategory.MAINTAINABILITY, SeverityMapper.mapCategory("MAINTAINABILITY"))
        assertEquals(IssueCategory.STYLE, SeverityMapper.mapCategory("STYLE"))
        assertEquals(IssueCategory.TESTING, SeverityMapper.mapCategory("TESTING"))
        assertEquals(IssueCategory.UNKNOWN, SeverityMapper.mapCategory("unknown"))
        assertEquals(IssueCategory.UNKNOWN, SeverityMapper.mapCategory(null))
    }

    @Test
    fun `test ReviewOutputParser parses valid JSON with multiple severities`() {
        val rawJson = """
            {
              "has_issue": true,
              "summary": "Found 3 security and performance issues.",
              "issues": [
                {
                  "severity": "CRITICAL",
                  "category": "SECURITY",
                  "title": "Hardcoded API Token",
                  "description": "Found raw API token in ApiClient.kt",
                  "file": "app/ApiClient.kt",
                  "line": 42,
                  "confidence": 0.95,
                  "fix": "Move key to BuildConfig or secure storage."
                },
                {
                  "severity": "WARNING",
                  "category": "PERFORMANCE",
                  "title": "Unclosed InputStream",
                  "description": "InputStream should be wrapped in use block.",
                  "file": "app/FileLoader.kt",
                  "line": 18,
                  "confidence": 0.8,
                  "fix": "Use .use { } block."
                },
                {
                  "severity": "INFO",
                  "category": "STYLE",
                  "title": "Unused import",
                  "description": "Unused import android.util.Log",
                  "file": "app/Utils.kt",
                  "line": 3,
                  "confidence": 0.6,
                  "fix": "Remove unused import."
                }
              ]
            }
        """.trimIndent()

        val result = ReviewOutputParser.parse(rawJson, moshi)

        assertTrue(result.hasIssue)
        assertEquals(3, result.issues.size)
        assertEquals(1, result.criticalCount)
        assertEquals(1, result.warningCount)
        assertEquals(1, result.infoCount)

        val critical = result.issues[0]
        assertEquals(Severity.CRITICAL, critical.severityEnum)
        assertEquals(IssueCategory.SECURITY, critical.categoryEnum)
        assertEquals("Hardcoded API Token", critical.displayTitle)
        assertEquals(42, critical.line)
        assertEquals("app/ApiClient.kt", critical.file)
        assertFalse(critical.needsManualVerification)
    }

    @Test
    fun `test ReviewOutputParser strips markdown fences gracefully`() {
        val markdownJson = """
            ```json
            {
              "has_issue": true,
              "summary": "Review complete with 1 issue.",
              "issues": [
                {
                  "severity": "blocker",
                  "title": "SQL Injection Risk",
                  "description": "Unsanitized raw query string.",
                  "file": "Database.kt",
                  "line": 105,
                  "confidence": 0.35,
                  "fix": "Use parameterized queries."
                }
              ]
            }
            ```
        """.trimIndent()

        val result = ReviewOutputParser.parse(markdownJson, moshi)

        assertTrue(result.hasIssue)
        assertEquals(1, result.issues.size)
        val issue = result.issues.first()
        assertEquals(Severity.CRITICAL, issue.severityEnum)
        assertEquals("SQL Injection Risk", issue.displayTitle)
        // Low confidence critical issue should flag for manual verification
        assertTrue(issue.needsManualVerification)
    }

    @Test
    fun `test ReviewOutputParser handles unstructured malformed output without crashing`() {
        val malformed = "I reviewed the code and everything looks okay except for some minor typos in comments."

        val result = ReviewOutputParser.parse(malformed, moshi)

        assertTrue(result.hasIssue)
        assertEquals(1, result.issues.size)
        assertEquals(Severity.INFO, result.issues.first().severityEnum)
        assertEquals("Unstructured Review Output", result.issues.first().displayTitle)
    }

    @Test
    fun `test ReviewResult sortedIssues orders by severity priority and confidence`() {
        val issues = listOf(
            CodeIssue(title = "Info issue", severity = "info", confidence = 0.9f),
            CodeIssue(title = "Critical issue 2", severity = "critical", confidence = 0.6f),
            CodeIssue(title = "Critical issue 1", severity = "critical", confidence = 0.95f),
            CodeIssue(title = "Warning issue", severity = "warning", confidence = 0.8f)
        )

        val review = ReviewResult(hasIssue = true, issues = issues)
        val sorted = review.sortedIssues

        assertEquals("Critical issue 1", sorted[0].displayTitle)
        assertEquals("Critical issue 2", sorted[1].displayTitle)
        assertEquals("Warning issue", sorted[2].displayTitle)
        assertEquals("Info issue", sorted[3].displayTitle)
    }
}
