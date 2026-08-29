package com.apexos.repoguardian.data.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun `buildReviewPrompt contains system instructions and diff`() {
        val diff = "+ fun hello() = Unit"
        val prompt = PromptBuilder.buildReviewPrompt(diff)

        assertTrue("Prompt should contain system tags", prompt.contains("<|im_start|>system"))
        assertTrue("Prompt should specify JSON format", prompt.contains("JSON format:"))
        assertTrue("Prompt should contain the diff code", prompt.contains("+ fun hello() = Unit"))
        assertTrue("Prompt should end with assistant tag", prompt.endsWith("<|im_start|>assistant"))
    }

    @Test
    fun `buildReviewPrompt truncates diffs longer than 3000 chars`() {
        val longDiff = "A".repeat(5000)
        val prompt = PromptBuilder.buildReviewPrompt(longDiff)

        assertTrue("Prompt should mark diff as truncated", prompt.contains("... (diff truncated for speed)"))
        assertFalse("Prompt should not contain full 5000 character diff", prompt.contains("A".repeat(3500)))
    }

    @Test
    fun `buildReviewPrompt includes custom review rules when provided`() {
        val diff = "+ val x = 10"
        val customRules = "- Avoid hardcoded magic numbers\n- Check thread safety"
        val prompt = PromptBuilder.buildReviewPrompt(diff, customRules)

        assertTrue("Prompt should contain custom rules section", prompt.contains("Additional review rules:"))
        assertTrue("Prompt should contain specific rules", prompt.contains("- Avoid hardcoded magic numbers"))
    }

    @Test
    fun `buildCiCdPrompt formats properly for language`() {
        val prompt = PromptBuilder.buildCiCdPrompt("Kotlin", "RepoGuardian")

        assertTrue("Prompt should request YAML content", prompt.contains("Generate a GitHub Actions workflow YAML file"))
        assertTrue("Prompt should specify target language and repo", prompt.contains("Kotlin project named \"RepoGuardian\""))
    }

    @Test
    fun `buildChatPrompt includes thinking instructions when think mode is enabled`() {
        val thinkPrompt = PromptBuilder.buildChatPrompt(
            userMessage = "Explain architecture",
            systemContext = "Active Repository: test/repo",
            isThinkMode = true
        )

        assertTrue("Prompt should include <think> instructions", thinkPrompt.contains("<think>...</think>"))
        assertTrue("Prompt should include user message", thinkPrompt.contains("Explain architecture"))
    }

    @Test
    fun `buildChatPrompt omits thinking instructions when think mode is disabled`() {
        val directPrompt = PromptBuilder.buildChatPrompt(
            userMessage = "Explain architecture",
            systemContext = "Active Repository: test/repo",
            isThinkMode = false
        )

        assertFalse("Prompt should omit <think> instructions", directPrompt.contains("<think>...</think>"))
        assertTrue("Prompt should request direct solution", directPrompt.contains("Provide a direct, concise"))
    }
}
