package com.apexos.repoguardian.data.huggingface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceModelsTest {

    @Test
    fun `HfModelFile computes size in MB and GB correctly`() {
        val file = HfModelFile(
            filename = "qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
            size = 1024L * 1024L * 1024L // 1 GB
        )

        assertTrue("File should be identified as GGUF", file.isGguf)
        assertEquals(1024L, file.sizeInMb)
        assertEquals(1.0, file.sizeInGb, 0.01)
        assertEquals("Q4_K_M", file.quantType)
    }

    @Test
    fun `HfModelFile identifies non-GGUF file`() {
        val file = HfModelFile(
            filename = "README.md",
            size = 2048L
        )

        assertFalse("File should not be identified as GGUF", file.isGguf)
        assertEquals(null, file.quantType)
    }

    @Test
    fun `FEATURED_MODELS contains essential mobile models`() {
        assertTrue("Featured models list should not be empty", FEATURED_MODELS.isNotEmpty())

        val deepSeek = FEATURED_MODELS.firstOrNull { it.id.contains("DeepSeek-R1", ignoreCase = true) }
        assertNotNull("DeepSeek-R1 Distill model must be featured", deepSeek)
        assertEquals("Q4_K_M", deepSeek?.quant)

        val qwenCoder = FEATURED_MODELS.firstOrNull { it.id.contains("Qwen2.5-Coder-1.5B", ignoreCase = true) }
        assertNotNull("Qwen2.5-Coder 1.5B must be featured", qwenCoder)
        assertTrue("Size formatted should include MB or GB", qwenCoder?.sizeFormatted?.contains("MB") == true || qwenCoder?.sizeFormatted?.contains("GB") == true)
    }

    @Test
    fun `DownloadProgress calculates percentage and eta formatted`() {
        val progress = DownloadProgress(
            bytesDownloaded = 500L * 1024 * 1024,
            totalBytes = 1000L * 1024 * 1024,
            speedBytesPerSec = 50L * 1024 * 1024, // 50 MB/s
            etaSeconds = 10L
        )

        assertEquals(0.5f, progress.progressPercent, 0.01f)
        assertTrue("Speed formatted should contain MB/s", progress.speedFormatted.contains("MB/s"))
        assertEquals("10s", progress.etaFormatted)
    }
}
