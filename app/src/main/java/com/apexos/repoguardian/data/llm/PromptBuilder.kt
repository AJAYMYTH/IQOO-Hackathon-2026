package com.apexos.repoguardian.data.llm

object PromptBuilder {
    /**
     * Build prompt for code review. Keeps it short — diff only, not full files.
     * This is critical for on-device inference speed.
     */
    fun buildReviewPrompt(diff: String, customRules: String = ""): String {
        val truncatedDiff = if (diff.length > 3000) {
            diff.take(3000) + "\n... (diff truncated for speed)"
        } else {
            diff
        }

        val rulesSection = if (customRules.isNotBlank()) {
            "\nAdditional review rules:\n$customRules\n"
        } else ""

        return """
            <|im_start|>system
            You are a code reviewer. Analyze the git diff and respond ONLY with valid JSON.
            JSON format: {"has_issue": bool, "summary": "brief summary", "issues": [{"file": "filename", "line": number_or_null, "severity": "critical|warning|info", "description": "what's wrong", "fix": "how to fix"}], "fixed_code": "corrected code or null"}
            Be concise. Focus on bugs, security issues, and bad practices.$rulesSection
            <|im_end|>
            <|im_start|>user
            Review this diff:
            ```
            $truncatedDiff
            ```
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Build prompt for CI/CD YAML generation.
     */
    fun buildCiCdPrompt(language: String?, repoName: String): String {
        return """
            <|im_start|>system
            You are a DevOps engineer. Generate a GitHub Actions workflow YAML file.
            Output ONLY the YAML content, no markdown fences, no explanations.
            The workflow should build and test the project on push/PR to main.
            <|im_end|>
            <|im_start|>user
            Generate a CI/CD workflow for a ${language ?: "unknown language"} project named "$repoName".
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}
