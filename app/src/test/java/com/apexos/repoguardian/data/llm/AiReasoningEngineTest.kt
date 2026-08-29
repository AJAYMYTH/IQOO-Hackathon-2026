package com.apexos.repoguardian.data.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiReasoningEngineTest {

    private lateinit var reasoningEngine: AiReasoningEngine

    @Before
    fun setup() {
        reasoningEngine = AiReasoningEngine()
    }

    private val sampleKotlinSystemContext = """
        Active Repository: apexos/repoguardian
        Description: On-Device AI Code Reviewer for Android
        Language: Kotlin
        Default Branch: main
        Root Files: build.gradle.kts, settings.gradle.kts, app, gradlew
        README Excerpt: Repo Guardian leverages llama.cpp for local offline analysis.
        Recent Commits:
        - 1a2b3c4: feat: add think mode toggle (by ApexOS)
        - 5d6e7f8: fix: handle storage pre-check (by ApexOS)
    """.trimIndent()

    private val sampleNodeSystemContext = """
        Active Repository: myorg/web-dashboard
        Description: Next.js Frontend Dashboard
        Language: TypeScript
        Default Branch: main
        Root Files: package.json, tsconfig.json, next.config.js, src
        README Excerpt: Production-ready web dashboard.
        Recent Commits:
        - 9a8b7c6: chore: update packages (by Developer)
    """.trimIndent()

    @Test
    fun `generateReasonedResponse includes thinking block when isThinkMode is true`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Explain this repository architecture",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = true
        )

        assertTrue("Response should contain opening <think> tag", response.contains("<think>"))
        assertTrue("Response should contain closing </think> tag", response.contains("</think>"))
        assertTrue("Thinking block should reference target repository", response.contains("apexos/repoguardian"))
    }

    @Test
    fun `generateReasonedResponse excludes thinking block when isThinkMode is false`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Explain this repository architecture",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = false
        )

        assertFalse("Response should not contain <think> tag", response.contains("<think>"))
        assertFalse("Response should not contain </think> tag", response.contains("</think>"))
        assertTrue("Response should contain repository overview", response.contains("Repository Overview"))
    }

    @Test
    fun `generateReasonedResponse detects Kotlin Android stack for CI CD generation`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Generate CI/CD pipeline",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = false
        )

        assertTrue("Response should contain GitHub Actions YAML", response.contains("name: Android / Kotlin CI"))
        assertTrue("Response should contain gradle test step", response.contains("./gradlew testDebugUnitTest"))
        assertTrue("Response should target main branch", response.contains("branches: [ main ]"))
    }

    @Test
    fun `generateReasonedResponse detects Node TypeScript stack for CI CD generation`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Generate CI/CD pipeline",
            systemContext = sampleNodeSystemContext,
            isThinkMode = false
        )

        assertTrue("Response should contain Node CI", response.contains("name: Node.js / JavaScript CI"))
        assertTrue("Response should contain npm test step", response.contains("npm test"))
    }

    @Test
    fun `generateReasonedResponse reviews commits accurately`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Review recent commits for security and bugs",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = false
        )

        assertTrue("Response should contain commit review section", response.contains("Commit & Security Review"))
        assertTrue("Response should evaluate commit shas", response.contains("1a2b3c4"))
        assertTrue("Response should evaluate credential hygiene", response.contains("Credential Hygiene"))
    }

    @Test
    fun `generateReasonedResponse generates unit test specifications`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "Write unit tests for this repo",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = false
        )

        assertTrue("Response should contain unit test section", response.contains("Unit Test Specification"))
        assertTrue("Response should contain Kotlin test snippet", response.contains("@Test"))
    }

    @Test
    fun `generateReasonedResponse handles custom queries with context`() {
        val response = reasoningEngine.generateReasonedResponse(
            userPrompt = "How do we improve memory footprint?",
            systemContext = sampleKotlinSystemContext,
            isThinkMode = false
        )

        assertNotNull(response)
        assertTrue(response.isNotBlank())
    }
}
