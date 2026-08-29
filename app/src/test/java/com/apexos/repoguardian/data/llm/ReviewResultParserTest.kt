package com.apexos.repoguardian.data.llm

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReviewResultParserTest {

    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun `parse valid JSON review result with issues`() {
        val json = """
            {
                "has_issue": true,
                "summary": "Found potential null pointer and thread block in repository diff",
                "issues": [
                    {
                        "file": "MainActivity.kt",
                        "line": 42,
                        "severity": "critical",
                        "description": "Unsafe forced non-null assertion",
                        "fix": "Use safe call ?. or check nullability"
                    },
                    {
                        "file": "NetworkService.kt",
                        "line": 88,
                        "severity": "warning",
                        "description": "Thread.sleep called inside coroutine context",
                        "fix": "Use delay() instead of blocking sleep"
                    }
                ],
                "fixed_code": "val safeData = data ?: return"
            }
        """.trimIndent()

        val adapter = moshi.adapter(ReviewResult::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertTrue(result!!.hasIssue)
        assertEquals("Found potential null pointer and thread block in repository diff", result.summary)
        assertEquals(2, result.issues.size)

        val firstIssue = result.issues[0]
        assertEquals("MainActivity.kt", firstIssue.file)
        assertEquals(42, firstIssue.line)
        assertEquals("critical", firstIssue.severity)
        assertEquals("Unsafe forced non-null assertion", firstIssue.description)
        assertEquals("Use safe call ?. or check nullability", firstIssue.fix)

        val secondIssue = result.issues[1]
        assertEquals("warning", secondIssue.severity)
        assertEquals("Thread.sleep called inside coroutine context", secondIssue.description)
        assertEquals("val safeData = data ?: return", result.fixedCode)
    }

    @Test
    fun `parse clean review result with zero issues`() {
        val json = """
            {
                "has_issue": false,
                "summary": "All changes look clean and adhere to coding guidelines",
                "issues": [],
                "fixed_code": null
            }
        """.trimIndent()

        val adapter = moshi.adapter(ReviewResult::class.java)
        val result = adapter.fromJson(json)

        assertNotNull(result)
        assertFalse(result!!.hasIssue)
        assertEquals("All changes look clean and adhere to coding guidelines", result.summary)
        assertTrue(result.issues.isEmpty())
        assertEquals(null, result.fixedCode)
    }
}
