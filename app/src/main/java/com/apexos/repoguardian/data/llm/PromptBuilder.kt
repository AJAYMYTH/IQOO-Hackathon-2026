package com.apexos.repoguardian.data.llm

object PromptBuilder {
    /**
     * Build prompt for code review using real repository diff and context.
     */
    fun buildReviewPrompt(diff: String, customRules: String = "", repoContext: String = ""): String {
        val truncatedDiff = if (diff.length > 10000) {
            val cut = diff.take(10000)
            val lastNewline = cut.lastIndexOf('\n')
            if (lastNewline > 8000) {
                cut.substring(0, lastNewline) + "\n... (diff truncated at hunk boundary for token window)"
            } else {
                cut + "\n... (diff truncated for token window)"
            }
        } else {
            diff
        }

        val contextSection = if (repoContext.isNotBlank()) {
            "\nRepository Context:\n$repoContext\n"
        } else ""

        val rulesSection = if (customRules.isNotBlank()) {
            "\nAdditional review rules:\n$customRules\n"
        } else ""

        return """
            <|im_start|>system
            You are a code reviewer. Analyze the real git diff and source code from the repository. Respond ONLY with valid JSON.
            JSON format: {"has_issue": bool, "summary": "brief summary", "issues": [{"file": "filename", "line": number_or_null, "severity": "critical|warning|info", "description": "what's wrong", "fix": "how to fix"}], "fixed_code": "corrected code or null"}
            Be concise. Focus on bugs, security issues, null safety, and bad practices.$contextSection$rulesSection
            <|im_end|>
            <|im_start|>user
            Review this repository diff:
            ```
            $truncatedDiff
            ```
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Build prompt for CI/CD YAML generation using real build manifests and repo structure.
     */
    fun buildCiCdPrompt(language: String?, repoName: String, buildManifestContext: String = ""): String {
        val buildSection = if (buildManifestContext.isNotBlank()) {
            "\nActual repository build configuration and structure:\n$buildManifestContext\n"
        } else ""

        return """
            <|im_start|>system
            You are a DevOps engineer. Generate a production-ready GitHub Actions workflow YAML file based on the real repository data and build configuration.
            Output ONLY the YAML content, no markdown fences, no explanations.
            The workflow should build and test the project on push/PR to the repository default branch.$buildSection
            <|im_end|>
            <|im_start|>user
            Generate a CI/CD workflow for the ${language ?: "software"} repository "$repoName".
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Build prompt for conversational AI Assistant with real GitHub repository context.
     */
    fun buildChatPrompt(userMessage: String, systemContext: String, isThinkingModel: Boolean): String {
        val thinkingInstruction = if (isThinkingModel) {
            "\nYou are a reasoning AI model. Analyze the retrieved real GitHub repository data carefully. Write your internal analysis inside <think>...</think> tags, and then provide your clear, verified answer."
        } else {
            ""
        }

        return """
            <|im_start|>system
            You are Repo Guardian, an advanced on-device AI code assistant and software engineer.
            You have direct access to real repository data, source files, commits, and tree structure retrieved via the GitHub REST API.
            Use this real repository data as the absolute ground truth to answer the user's questions and perform tasks.

            $systemContext$thinkingInstruction
            <|im_end|>
            <|im_start|>user
            $userMessage
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}

