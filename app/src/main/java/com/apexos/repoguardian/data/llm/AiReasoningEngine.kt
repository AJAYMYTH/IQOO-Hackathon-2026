package com.apexos.repoguardian.data.llm

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiReasoningEngine @Inject constructor() {

    fun generateReasonedResponse(
        userPrompt: String,
        systemContext: String,
        isThinkMode: Boolean = true
    ): String {
        val repoInfo = parseLiveRepoContext(systemContext)

        val thinkingProcess = if (isThinkMode) {
            """
- Target Repository: ${repoInfo.fullName} (${repoInfo.language.ifBlank { "Multi-Language" }})
- Query: "$userPrompt"
- Root Manifest: ${repoInfo.rootFiles.take(8).joinToString(", ").ifBlank { "Live repository tree" }}
- Status: Awaiting active on-device GGUF model in memory to execute local neural inference.
            """.trimIndent()
        } else ""

        val responseBody = """
### 🤖 On-Device AI Status

**Repository:** `${repoInfo.fullName}` | **Stack:** ${repoInfo.detectTechStack()}

No GGUF model is currently loaded into memory to execute neural inference for your prompt:
> *"$userPrompt"*

**To enable 100% real on-device AI generation:**
1. Tap the **AI Models** button in the top bar.
2. Download a mobile model (e.g. **Qwen2.5-Coder 0.5B** ~398MB or **1.5B** ~986MB).
3. Once downloaded, the model will auto-load and answer your prompts with real neural tokens.
        """.trimIndent()

        return if (thinkingProcess.isNotBlank()) {
            "<think>\n$thinkingProcess\n</think>\n\n$responseBody"
        } else {
            responseBody
        }
    }

    private fun generateThoughtProcess(userPrompt: String, query: String, repo: ParsedRepoContext): String {
        val techStack = repo.detectTechStack()
        return """
- Target Repository: ${repo.fullName} (${repo.language.ifBlank { "Multi-Language" }})
- Detected Stack & Framework: $techStack
- User Prompt: "$userPrompt"
- Root Files Detected: ${repo.rootFiles.take(8).joinToString(", ").ifBlank { "Direct tree scan" }}
- Recent Commits Evaluated: ${repo.recentCommits.size} commits
- Context: Ingested live repository metadata, description, file manifest, and commit logs directly from GitHub API.
        """.trimIndent()
    }


    private fun parseLiveRepoContext(systemContext: String): ParsedRepoContext {
        var owner = ""
        var name = ""
        var description = ""
        var language = ""
        var defaultBranch = "main"
        val rootFiles = mutableListOf<String>()
        val recentCommits = mutableListOf<String>()
        var readmeExcerpt = ""

        systemContext.lines().forEach { line ->
            when {
                line.startsWith("Active Repository:") -> {
                    val full = line.removePrefix("Active Repository:").trim()
                    val parts = full.split("/")
                    if (parts.size >= 2) {
                        owner = parts[0].trim()
                        name = parts[1].trim()
                    } else {
                        name = full
                    }
                }
                line.startsWith("Description:") -> {
                    description = line.removePrefix("Description:").trim()
                }
                line.startsWith("Language:") -> {
                    language = line.removePrefix("Language:").trim()
                }
                line.startsWith("Default Branch:") -> {
                    defaultBranch = line.removePrefix("Default Branch:").trim()
                }
                line.startsWith("Root Files:") -> {
                    val files = line.removePrefix("Root Files:").trim().split(",")
                    rootFiles.addAll(files.map { it.trim() }.filter { it.isNotBlank() })
                }
                line.startsWith("README Excerpt:") -> {
                    readmeExcerpt = line.removePrefix("README Excerpt:").trim()
                }
                line.startsWith("- ") && (line.contains(":") || line.contains("by")) -> {
                    recentCommits.add(line.removePrefix("- ").trim())
                }
            }
        }

        return ParsedRepoContext(
            owner = owner,
            name = name,
            description = description,
            language = language,
            defaultBranch = defaultBranch,
            rootFiles = rootFiles,
            recentCommits = recentCommits,
            readmeExcerpt = readmeExcerpt
        )
    }

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

data class ParsedRepoContext(
    val owner: String,
    val name: String,
    val description: String,
    val language: String,
    val defaultBranch: String,
    val rootFiles: List<String>,
    val recentCommits: List<String>,
    val readmeExcerpt: String
) {
    val fullName: String get() = if (owner.isNotBlank()) "$owner/$name" else name

    fun hasFile(fileName: String): Boolean =
        rootFiles.any { it.equals(fileName, ignoreCase = true) }

    fun detectTechStack(): String {
        val lang = language.lowercase()
        return when {
            hasFile("build.gradle.kts") || hasFile("build.gradle") -> "Android / Kotlin Gradle Project"
            hasFile("pom.xml") -> "Java / Maven Project"
            hasFile("package.json") -> "Node.js / TypeScript / Web Project"
            hasFile("requirements.txt") || hasFile("pyproject.toml") || lang.contains("python") -> "Python Project"
            hasFile("Cargo.toml") || lang.contains("rust") -> "Rust Cargo Project"
            hasFile("go.mod") || lang.contains("go") -> "Go Module Project"
            lang.isNotBlank() -> "$language Project"
            else -> "Software Repository"
        }
    }
}
