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
            You are Repo Guardian, an offline AI code review and bug detection agent.
            Analyze the real git diff and source code from the repository for bugs, runtime errors, git/merge conflicts, security vulnerabilities, syntax/type errors, memory leaks, and performance problems.
            Respond ONLY with valid JSON. Do not include markdown commentary outside JSON. Do not put comments (like //) inside the JSON.

            Use ONE of these severity values for each issue:
            - "CRITICAL": security vulnerabilities, fatal crashes, data loss, secret leaks, breaking bugs
            - "WARNING": potential bugs, merge/diff conflicts, missing validation, resource leaks, syntax/type warnings
            - "INFO": code style, optimizations, suggestions, refactoring

            Use ONE of these category values:
            - "BUG", "CONFLICT", "SECURITY", "SYNTAX_ERROR", "LOGIC_ERROR", "PERFORMANCE", "MAINTAINABILITY", "STYLE", "TESTING"

            JSON structure example:
            {
              "has_issue": true,
              "summary": "Detected security vulnerability and potential null pointer error in auth flow",
              "issues": [
                {
                  "severity": "CRITICAL",
                  "category": "SECURITY",
                  "title": "Unsanitized environment variable lookup",
                  "description": "Environment variables are read directly without validation or fallback handling.",
                  "file": "src/config/env.ts",
                  "line": 8,
                  "confidence": 0.95,
                  "fix": "Use a validated config schema or provide secure fallback defaults.",
                  "suggestion": "Wrap env lookup in a typed config validator",
                  "rule_id": "SEC_ENV_01"
                }
              ],
              "fixed_code": "export const config = { ... };"
            }$contextSection$rulesSection
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
     * Build prompt for AI to solve a specific issue in a file/diff.
     */
    fun buildSolveSingleIssuePrompt(
        issueTitle: String,
        issueDescription: String,
        filePath: String?,
        lineNumber: Int?,
        codeContext: String,
        repoContext: String = ""
    ): String {
        val locationStr = if (filePath != null) "File: $filePath${if (lineNumber != null) ":$lineNumber" else ""}" else "Target code"
        val repoSection = if (repoContext.isNotBlank()) "\nRepository Context:\n$repoContext\n" else ""

        return """
            <|im_start|>system
            You are Repo Guardian's AI Code Solution Specialist.
            Your task is to provide an exact, production-ready fix for the specific issue described below.
            
            Guidelines:
            1. Provide a concise explanation of what caused the bug/conflict/error and how your fix resolves it.
            2. Provide the complete corrected replacement code snippet or file content inside a markdown code block (e.g. ```typescript ... ``` or ```kotlin ... ```).
            3. Ensure the fix is safe, handles edge cases, and maintains original code style.$repoSection
            <|im_end|>
            <|im_start|>user
            Please solve this issue:
            - **Issue Title:** $issueTitle
            - **Location:** $locationStr
            - **Issue Details:** $issueDescription

            Relevant Code / Context:
            ```
            $codeContext
            ```

            Provide the detailed explanation and the exact corrected code fix.
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
            Provide a clear, structured, and complete answer once. Do not repeat sections or loop.

            $systemContext$thinkingInstruction
            <|im_end|>
            <|im_start|>user
            $userMessage
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Build prompt for generating automated unit tests to verify a fix before applying.
     */
    fun buildVerifyTestPrompt(
        issueTitle: String,
        issueDescription: String,
        filePath: String?,
        fixCode: String,
        repoContext: String = ""
    ): String {
        val locationStr = if (filePath != null) "File: $filePath" else "Target codebase"
        val repoSection = if (repoContext.isNotBlank()) "\nRepository Context:\n$repoContext\n" else ""

        return """
            <|im_start|>system
            You are Repo Guardian's Quality & Automated Test Engineer.
            Generate a concise, self-contained unit test (JUnit, Jest, PyTest, or language-appropriate framework) that specifically verifies this fix, reproduces the edge case, and guarantees regression prevention.
            Include test assertions that pass with the fix and would fail without it.
            Output your test code inside a clean markdown code fence.$repoSection
            <|im_end|>
            <|im_start|>user
            Please generate an automated verification unit test for:
            - **Defect:** $issueTitle
            - **Target:** $locationStr
            - **Defect Description:** $issueDescription
            
            **Applied Remediation Fix:**
            ```
            $fixCode
            ```

            Provide the automated test case and brief verification instructions.
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }

    /**
     * Build prompt for summarizing repository changes since yesterday / recent delta.
     */
    fun buildDeltaDigestPrompt(
        repoName: String,
        commitsSummary: String,
        filesChanged: String = ""
    ): String {
        return """
            <|im_start|>system
            You are Repo Guardian, an autonomous mobile engineering agent.
            Analyze the recent repository commits and deltas since yesterday.
            Generate a high-impact, executive engineering digest covering:
            1. 🚀 **Key Highlights & Features:** What was added or updated.
            2. 🛡️ **Risk & Security Flags:** Any risky areas or suspicious commits.
            3. 📋 **Actionable Recommendations:** What the engineer should review first.
            Keep it structured, punchy, and mobile-friendly with bullet points.
            <|im_end|>
            <|im_start|>user
            Summarize what changed since yesterday in repository "$repoName":
            
            Recent Commits:
            $commitsSummary
            ${if (filesChanged.isNotBlank()) "\nFiles Impacted:\n$filesChanged" else ""}
            <|im_end|>
            <|im_start|>assistant
        """.trimIndent()
    }
}

