package com.apexos.repoguardian.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun `parseMarkdown extracts think block correctly`() {
        val markdown = """
            <think>
            Step 1: Check repository files
            Step 2: Synthesize architecture overview
            </think>

            # Repository Overview
            This is a production app.
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        val thinkBlock = blocks.filterIsInstance<MarkdownBlock.Think>().firstOrNull()

        assertNotNull("Think block should be extracted", thinkBlock)
        assertTrue("Think block should contain step 1", thinkBlock?.thought?.contains("Step 1") == true)
        assertTrue("Think block should contain step 2", thinkBlock?.thought?.contains("Step 2") == true)

        val headerBlock = blocks.filterIsInstance<MarkdownBlock.Header>().firstOrNull()
        assertNotNull("Header block should follow think block", headerBlock)
        assertEquals("Repository Overview", headerBlock?.text)
        assertEquals(1, headerBlock?.level)
    }

    @Test
    fun `parseMarkdown extracts code block with language`() {
        val markdown = """
            ```kotlin
            fun main() {
                println("Hello World")
            }
            ```
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        val codeBlock = blocks.filterIsInstance<MarkdownBlock.Code>().firstOrNull()

        assertNotNull("Code block should be extracted", codeBlock)
        assertEquals("kotlin", codeBlock?.language)
        assertTrue("Code block should contain println", codeBlock?.code?.contains("Hello World") == true)
    }

    @Test
    fun `parseMarkdown extracts headers with varying levels`() {
        val markdown = """
            # Level 1
            ## Level 2
            ### Level 3
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        val headers = blocks.filterIsInstance<MarkdownBlock.Header>()

        assertEquals(3, headers.size)
        assertEquals(1, headers[0].level)
        assertEquals("Level 1", headers[0].text)
        assertEquals(2, headers[1].level)
        assertEquals("Level 2", headers[1].text)
        assertEquals(3, headers[2].level)
        assertEquals("Level 3", headers[2].text)
    }

    @Test
    fun `parseMarkdown extracts bullet points and numbered lists`() {
        val markdown = """
            - First bullet
            * Second bullet
            1. First item
            2. Second item
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        val bullets = blocks.filterIsInstance<MarkdownBlock.Bullet>()
        val numbered = blocks.filterIsInstance<MarkdownBlock.Numbered>()

        assertEquals(2, bullets.size)
        assertEquals("First bullet", bullets[0].text)
        assertEquals("Second bullet", bullets[1].text)

        assertEquals(2, numbered.size)
        assertEquals("1.", numbered[0].number)
        assertEquals("First item", numbered[0].text)
        assertEquals("2.", numbered[1].number)
        assertEquals("Second item", numbered[1].text)
    }

    @Test
    fun `parseMarkdown extracts blockquotes and paragraphs`() {
        val markdown = """
            > Important security notice

            Standard body paragraph text.
        """.trimIndent()

        val blocks = parseMarkdown(markdown)
        val quotes = blocks.filterIsInstance<MarkdownBlock.Blockquote>()
        val paragraphs = blocks.filterIsInstance<MarkdownBlock.Paragraph>()

        assertEquals(1, quotes.size)
        assertEquals("Important security notice", quotes[0].text)

        assertEquals(1, paragraphs.size)
        assertEquals("Standard body paragraph text.", paragraphs[0].text)
    }
}
