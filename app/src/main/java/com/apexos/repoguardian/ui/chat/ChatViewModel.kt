package com.apexos.repoguardian.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apexos.repoguardian.data.github.ApiResult
import com.apexos.repoguardian.data.github.GitHubRepository
import com.apexos.repoguardian.data.github.models.Commit
import com.apexos.repoguardian.data.github.models.Repo
import com.apexos.repoguardian.data.llm.LlamaService
import com.apexos.repoguardian.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val repoContext: String? = null
)

data class QuickPrompt(
    val title: String,
    val icon: String,
    val prompt: String
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val repoOwner: String = "",
    val repoName: String = "",
    val availableRepos: List<Repo> = emptyList(),
    val isRepoDropdownOpen: Boolean = false,
    val recentCommits: List<Commit> = emptyList(),
    val activeModelName: String = "On-Device LLM",
    val error: String? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llamaService: LlamaService,
    private val gitHubRepository: GitHubRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    val quickPrompts = listOf(
        QuickPrompt(
            title = "Review Commits",
            icon = "🔍",
            prompt = "Review the recent commits in this repository for potential logic errors, null pointer dereferences, security vulnerabilities, and memory leaks. Provide detailed explanations and actionable code fixes."
        ),
        QuickPrompt(
            title = "Generate CI/CD",
            icon = "🚀",
            prompt = "Generate a production-ready GitHub Actions CI/CD workflow (.github/workflows/ci.yml) tailored for this repository with automated testing, lint checks, and artifact packaging."
        ),
        QuickPrompt(
            title = "Write Unit Tests",
            icon = "🧪",
            prompt = "Write comprehensive unit tests with MockK, JUnit 5, and Kotlin Coroutines Test for the primary ViewModel and Repository components of this repository."
        ),
        QuickPrompt(
            title = "Release Pipeline",
            icon = "🚢",
            prompt = "Create an automated GitHub Actions release workflow (.github/workflows/release.yml) triggered on version tags (v*) that compiles release APKs, generates SHA-256 checksums, and publishes a GitHub Release with changelogs."
        ),
        QuickPrompt(
            title = "Security Audit",
            icon = "🛡️",
            prompt = "Perform a security audit on this repository. Check AndroidManifest permissions, API token handling, and networking configurations."
        ),
        QuickPrompt(
            title = "Optimize Performance",
            icon = "⚡",
            prompt = "Analyze potential performance bottlenecks in Compose recompositions, coroutine lifecycle scopes, and background threads, and suggest optimization strategies."
        )
    )

    init {
        loadInitialContext()
    }

    fun loadInitialContext() {
        viewModelScope.launch {
            val selectedRepo = preferencesManager.getSelectedRepo()
            val modelPath = preferencesManager.getModelPath()
            val modelName = if (!modelPath.isNullOrBlank()) {
                java.io.File(modelPath).nameWithoutExtension
            } else {
                "Qwen2.5-Coder (Default)"
            }

            if (selectedRepo != null) {
                _uiState.value = _uiState.value.copy(
                    repoOwner = selectedRepo.first,
                    repoName = selectedRepo.second,
                    activeModelName = modelName
                )
                loadRepoCommits(selectedRepo.first, selectedRepo.second)
            } else {
                _uiState.value = _uiState.value.copy(activeModelName = modelName)
            }

            // Load user repos for switcher
            when (val result = gitHubRepository.listRepos()) {
                is ApiResult.Success<List<Repo>> -> {
                    _uiState.value = _uiState.value.copy(availableRepos = result.data)
                }
                else -> {}
            }

            // Add initial welcome message if empty
            if (_uiState.value.messages.isEmpty()) {
                val owner = _uiState.value.repoOwner.ifBlank { "AJAYMYTH" }
                val repo = _uiState.value.repoName.ifBlank { "Repository" }
                val welcome = """
### 👋 Welcome to Repo Guardian AI Assistant!

I am your on-device AI coding partner. I can review your commits, write unit tests, and generate automated CI/CD and release pipelines.

**Active Repository:** `$owner/$repo`
**Active Model:** `$modelName`

Tap any quick action below or ask a question to get started!
                """.trimIndent()
                _uiState.value = _uiState.value.copy(
                    messages = listOf(ChatMessage(content = welcome, isUser = false))
                )
            }
        }
    }

    private fun loadRepoCommits(owner: String, name: String) {
        viewModelScope.launch {
            when (val result = gitHubRepository.listCommits(owner, name)) {
                is ApiResult.Success<List<Commit>> -> {
                    _uiState.value = _uiState.value.copy(recentCommits = result.data.take(5))
                }
                else -> {}
            }
        }
    }

    fun setRepoDropdownOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isRepoDropdownOpen = open)
    }

    fun switchRepo(repo: Repo) {
        viewModelScope.launch {
            preferencesManager.saveSelectedRepo(repo.owner.login, repo.name)
            _uiState.value = _uiState.value.copy(
                repoOwner = repo.owner.login,
                repoName = repo.name,
                isRepoDropdownOpen = false
            )
            loadRepoCommits(repo.owner.login, repo.name)

            val switchNotice = "Switched repository context to **${repo.fullName}**."
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatMessage(content = switchNotice, isUser = false)
            )
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _uiState.value.isGenerating) return

        val userMessage = ChatMessage(content = userText.trim(), isUser = true)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + userMessage,
            isGenerating = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val commitsSummary = if (_uiState.value.recentCommits.isNotEmpty()) {
                    _uiState.value.recentCommits.joinToString("\n") {
                        val msg = it.commit.message.lines().firstOrNull() ?: ""
                        val author = it.commit.author?.name ?: "Author"
                        "- ${it.sha.take(7)}: $msg (by $author)"
                    }
                } else {
                    "No recent commits loaded"
                }

                val systemPrompt = """
You are Repo Guardian, an expert on-device AI software engineer, code reviewer, and CI/CD architect.
Active Repository: ${_uiState.value.repoOwner}/${_uiState.value.repoName}
Recent Commits in Context:
$commitsSummary

Provide clear, structured, and production-ready responses with complete code snippets, markdown formatting, and best practices.
                """.trimIndent()

                val response = llamaService.chat(userText.trim(), systemPrompt)
                val aiMessage = ChatMessage(content = response, isUser = false)

                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + aiMessage,
                    isGenerating = false
                )
            } catch (e: Exception) {
                val errorMsg = "AI generation error: ${e.localizedMessage ?: e.message}"
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatMessage(content = "⚠️ $errorMsg", isUser = false),
                    isGenerating = false,
                    error = errorMsg
                )
            }
        }
    }

    fun clearChat() {
        val welcome = "Chat history cleared. How can I assist you with **${_uiState.value.repoOwner}/${_uiState.value.repoName}**?"
        _uiState.value = _uiState.value.copy(
            messages = listOf(ChatMessage(content = welcome, isUser = false)),
            error = null
        )
    }
}
